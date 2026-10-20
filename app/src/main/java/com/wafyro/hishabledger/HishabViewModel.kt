package com.wafyro.hishabledger

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Pre-computed numbers for one account, so list screens never have to rescan every entry. */
data class ShopStats(
    val balance: Long = 0L,
    val dueAdded: Long = 0L,
    val paid: Long = 0L,
    /** Entry time (not the chosen transaction date) of the newest active entry; 0 when there is none. */
    val lastActivity: Long = 0L
)

class HishabViewModel(context: Context) : ViewModel() {
    private val store = LocalStore(context)

    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var txs by mutableStateOf<List<Tx>>(emptyList())
        private set

    /** Set when a background save failed (for example, the phone is out of storage). The UI shows a message and clears it. */
    var saveFailed by mutableStateOf(false)

    private var stats by mutableStateOf<Map<String, ShopStats>>(emptyMap())
    private var payableCache by mutableLongStateOf(0L)

    // Saving happens off the main thread. Only the newest snapshot is written, so a burst of edits costs one write.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Any()
    @Volatile private var pendingSave: Pair<List<Shop>, List<Tx>>? = null

    init { reload() }

    fun reload() {
        val (s, t) = store.read()
        val cutoff = System.currentTimeMillis() - SOFT_DELETE_RETENTION_MS
        val keptShops = s.filter { it.deletedAt == 0L || it.deletedAt > cutoff }
        val keptTxs = t.filter { it.deletedAt == 0L || it.deletedAt > cutoff }
        if (keptShops.size != s.size || keptTxs.size != t.size) runCatching { store.save(keptShops, keptTxs) }
        shops = keptShops; txs = keptTxs
        recompute()
    }

    /** One pass over the entries; every balance and total the screens need comes from here. */
    private fun recompute() {
        val map = HashMap<String, ShopStats>()
        for (t in txs) {
            if (t.deletedAt != 0L) continue
            val cur = map[t.shopId] ?: ShopStats()
            val isPayment = t.type == TxType.PAYMENT
            map[t.shopId] = ShopStats(
                balance = cur.balance + if (isPayment) -t.amount else t.amount,
                dueAdded = cur.dueAdded + if (isPayment) 0L else t.amount,
                paid = cur.paid + if (isPayment) t.amount else 0L,
                lastActivity = maxOf(cur.lastActivity, t.timestamp)
            )
        }
        stats = map
        payableCache = shops.filter { it.deletedAt == 0L }.sumOf { (map[it.id]?.balance ?: 0L).coerceAtLeast(0L) }
    }

    /** Updates the screen immediately and writes the file in the background. */
    private fun commit(newShops: List<Shop>, newTxs: List<Tx>) {
        shops = newShops; txs = newTxs
        recompute()
        pendingSave = newShops to newTxs
        ioScope.launch {
            synchronized(saveLock) {
                val snapshot = pendingSave ?: return@synchronized
                pendingSave = null
                try { store.save(snapshot.first, snapshot.second) } catch (_: Exception) { saveFailed = true }
            }
        }
    }

    val activeShops: List<Shop> get() = shops.filter { it.deletedAt == 0L }
    val deletedShops: List<Shop> get() = shops.filter { it.deletedAt != 0L }
    fun activeTxs(shopId: String): List<Tx> = txs.filter { it.deletedAt == 0L && it.shopId == shopId }
    fun deletedTxs(): List<Tx> = txs.filter { it.deletedAt != 0L }

    fun balance(shopId: String): Long = stats[shopId]?.balance ?: 0L
    fun totalPayable(): Long = payableCache
    fun totalDueAdded(shopId: String): Long = stats[shopId]?.dueAdded ?: 0L
    fun totalPaid(shopId: String): Long = stats[shopId]?.paid ?: 0L
    fun lastActivity(shopId: String): Long = stats[shopId]?.lastActivity ?: 0L

    /** Active accounts that look like the one being saved: same name, or the same phone number. */
    fun findSimilar(name: String, phone: String, excludeId: String? = null): List<Shop> {
        val n = name.trim()
        val p = phone.filter(Char::isDigit).takeLast(10)
        return activeShops.filter { s ->
            s.id != excludeId && n.isNotEmpty() && (
                s.name.trim().equals(n, ignoreCase = true) ||
                    (p.length >= 7 && s.phone.filter(Char::isDigit).takeLast(10) == p)
                )
        }
    }

    fun addShop(name: String, phone: String, address: String, opening: Long, type: PartyType) {
        require(name.isNotBlank())
        val shop = Shop(UUID.randomUUID().toString(), name.trim(), phone.trim(), address.trim(), type = type)
        val openingTx = if (opening > 0) Tx(UUID.randomUUID().toString(), shop.id, opening, TxType.OPENING_BALANCE, "Previous Due") else null
        commit(shops + shop, if (openingTx == null) txs else txs + openingTx)
    }

    fun addTx(shopId: String, amount: Long, type: TxType, note: String, transactionDate: Long) {
        require(amount > 0)
        val tx = Tx(UUID.randomUUID().toString(), shopId, amount, type, note.trim(), timestamp = System.currentTimeMillis(), transactionDate = transactionDate)
        commit(shops, txs + tx)
    }

    fun softDeleteTx(id: String): Tx? {
        val target = txs.firstOrNull { it.id == id && it.deletedAt == 0L } ?: return null
        val updated = target.copy(deletedAt = System.currentTimeMillis())
        commit(shops, txs.map { if (it.id == id) updated else it })
        return updated
    }

    fun restoreTx(id: String) {
        val target = txs.firstOrNull { it.id == id } ?: return
        val restored = target.copy(deletedAt = 0L)
        commit(shops, txs.map { if (it.id == id) restored else it })
    }

    fun purgeTxForever(id: String) {
        commit(shops, txs.filterNot { it.id == id })
    }

    /** Edits the account details only. Previous Due lives in its own entry and is intentionally left alone. */
    fun updateShop(id: String, name: String, phone: String, address: String, type: PartyType) {
        require(name.isNotBlank())
        commit(shops.map { if (it.id == id) it.copy(name = name.trim(), phone = phone.trim(), address = address.trim(), type = type) else it }, txs)
    }

    fun purgeAllDeleted() {
        val goneShopIds = shops.filter { it.deletedAt != 0L }.map { it.id }.toSet()
        commit(shops.filter { it.deletedAt == 0L }, txs.filter { it.deletedAt == 0L && it.shopId !in goneShopIds })
    }

    fun softDeleteShop(id: String) {
        val target = shops.firstOrNull { it.id == id && it.deletedAt == 0L } ?: return
        val updated = target.copy(deletedAt = System.currentTimeMillis())
        commit(shops.map { if (it.id == id) updated else it }, txs)
    }

    fun restoreShop(id: String) {
        commit(shops.map { if (it.id == id) it.copy(deletedAt = 0L) else it }, txs)
    }

    fun purgeShopForever(id: String) {
        commit(shops.filterNot { it.id == id }, txs.filterNot { it.shopId == id })
    }

    /** Erases the whole ledger from this phone. Used only by the "forgot PIN, no screen lock" reset. */
    fun wipeAllData() {
        synchronized(saveLock) { pendingSave = null; store.deleteAll() }
        shops = emptyList(); txs = emptyList()
        recompute()
    }

    fun backup(): String = store.encode(shops, txs)
    fun markBackup() = store.markBackup()
    fun lastBackup() = store.lastBackup()

    /** Restores are saved right away (not in the background) so a failure can be reported to the person. */
    fun restore(json: String, replace: Boolean): Pair<Int, Int> {
        val (incomingS, incomingT) = store.validateAndDecode(json)
        val result = if (replace) incomingS to incomingT else {
            val existingShopIds = shops.map { it.id }.toSet()
            val mergedS = shops + incomingS.filterNot { it.id in existingShopIds }
            val existingTxIds = txs.map { it.id }.toSet()
            val mergedT = txs + incomingT.filterNot { it.id in existingTxIds }
            mergedS to mergedT
        }
        synchronized(saveLock) { pendingSave = null; store.save(result.first, result.second) }
        shops = result.first; txs = result.second
        recompute()
        return result.first.size to result.second.size
    }

    fun csv(shopId: String? = null): String {
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val selected = (if (shopId == null) txs else txs.filter { it.shopId == shopId }).filter { it.deletedAt == 0L }
        val sb = StringBuilder("Account,Entry Date,Entry Time,Type,Amount,Note\n")
        selected.sortedBy { it.transactionDate }.forEach { t ->
            val shop = shops.firstOrNull { it.id == t.shopId }?.name ?: ""
            val entryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(t.transactionDate))
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t.timestamp))
            sb.append(esc(shop)).append(',').append(entryDate).append(',').append(time).append(',').append(t.type.name).append(',').append(t.amount).append(',').append(esc(t.note)).append('\n')
        }
        return sb.toString()
    }
}
