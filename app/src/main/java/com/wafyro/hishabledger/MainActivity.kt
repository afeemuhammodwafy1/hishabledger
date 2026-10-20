package com.wafyro.hishabledger

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.provider.ContactsContract
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

val Green = Color(0xFF176B52)
val GreenDark = Color(0xFF0F5942)
val GreenSoft = Color(0xFFE8F3EE)
val Page = Color(0xFFF7FAF8)
val Danger = Color(0xFFB3261E)

object AppLanguage {
    var isBangla by mutableStateOf(false)
    fun load(context: Context) { isBangla = context.getSharedPreferences("hishab_language", Context.MODE_PRIVATE).getString("language", "en") == "bn" }
    fun set(context: Context, bangla: Boolean) { isBangla = bangla; context.getSharedPreferences("hishab_language", Context.MODE_PRIVATE).edit().putString("language", if (bangla) "bn" else "en").apply() }
}

private const val CREATOR = "Wafyro Software"
private const val APP_VERSION = "1.0.0"
private const val SOFT_DELETE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/** Formats a Taka amount using Bangladeshi lakh/crore digit grouping, e.g. 1234567 -> ৳12,34,567 */
/** Formats a timestamp as a complete local date and time for history and backup screens. */
fun formatFull(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

/** Returns a compact relative label for activity and reminder timestamps. */
fun relativeDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val delta = timestamp - now
    val absDelta = kotlin.math.abs(delta)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        absDelta < minute -> "just now"
        absDelta < hour -> {
            val n = (absDelta / minute).coerceAtLeast(1)
            if (delta > 0) "in $n min" else "$n min ago"
        }
        absDelta < day -> {
            val n = (absDelta / hour).coerceAtLeast(1)
            if (delta > 0) "in $n hr" else "$n hr ago"
        }
        absDelta < 7 * day -> {
            val n = (absDelta / day).coerceAtLeast(1)
            if (delta > 0) "in $n day${if (n == 1L) "" else "s"}" else "$n day${if (n == 1L) "" else "s"} ago"
        }
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Compares local calendar dates, rather than comparing raw millisecond values. */
fun isToday(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val todayYear = calendar.get(Calendar.YEAR)
    val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
    calendar.timeInMillis = timestamp
    return calendar.get(Calendar.YEAR) == todayYear && calendar.get(Calendar.DAY_OF_YEAR) == todayDay
}

fun money(value: Long): String {
    val neg = value < 0
    val digits = kotlin.math.abs(value).toString()
    val grouped = if (digits.length <= 3) digits else {
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val sb = StringBuilder()
        while (rest.length > 2) {
            sb.insert(0, "," + rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        sb.insert(0, rest)
        "$sb,$last3"
    }
    return "৳${if (neg) "-" else ""}$grouped"
}

enum class PartyType { SHOP, PERSON }

data class Shop(
    val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val type: PartyType = PartyType.SHOP,
    val deletedAt: Long = 0L
)

enum class TxType { DUE, PAYMENT, OPENING_BALANCE }

data class Tx(
    val id: String,
    val shopId: String,
    val amount: Long,
    val type: TxType,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reminderAt: Long = 0L,
    val deletedAt: Long = 0L,
    /** Date the transaction belongs to; defaults to the legacy timestamp for old backups. */
    val transactionDate: Long = timestamp
)

class LocalStore(private val context: Context) {
    private val dataFile get() = context.filesDir.resolve("baki_data.json")
    private val prefs get() = context.getSharedPreferences("baki_settings", Context.MODE_PRIVATE)

    fun read(): Pair<List<Shop>, List<Tx>> {
        if (!dataFile.exists()) return emptyList<Shop>() to emptyList()
        return try {
            val root = JSONObject(dataFile.readText(Charsets.UTF_8))
            val shops = buildList {
                val a = root.optJSONArray("shops") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val type = runCatching { PartyType.valueOf(o.optString("type", "SHOP")) }.getOrDefault(PartyType.SHOP)
                    add(Shop(o.getString("id"), o.getString("name"), o.optString("phone"), o.optString("address"), o.optLong("createdAt"), type, o.optLong("deletedAt", 0L)))
                }
            }
            val txs = buildList {
                val a = root.optJSONArray("transactions") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(Tx(o.getString("id"), o.getString("shopId"), o.getLong("amount"), TxType.valueOf(o.getString("type")), o.optString("note"), o.optLong("timestamp"), o.optLong("reminderAt", 0L), o.optLong("deletedAt", 0L), o.optLong("transactionDate", o.optLong("timestamp"))))
                }
            }
            shops to txs
        } catch (_: Exception) {
            emptyList<Shop>() to emptyList()
        }
    }

    fun encode(shops: List<Shop>, txs: List<Tx>): String {
        val root = JSONObject().put("format", "baki-backup").put("version", 2).put("backupCreatedAt", System.currentTimeMillis())
        root.put("shops", JSONArray().apply {
            shops.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("phone", s.phone); put("address", s.address)
                put("createdAt", s.createdAt); put("type", s.type.name); put("deletedAt", s.deletedAt)
            }) }
        })
        root.put("transactions", JSONArray().apply {
            txs.forEach { t -> put(JSONObject().apply {
                put("id", t.id); put("shopId", t.shopId); put("amount", t.amount); put("type", t.type.name)
                put("note", t.note); put("timestamp", t.timestamp); put("transactionDate", t.transactionDate); put("reminderAt", t.reminderAt); put("deletedAt", t.deletedAt)
            }) }
        })
        return root.toString(2)
    }

    fun save(shops: List<Shop>, txs: List<Tx>) {
        val tmp = context.filesDir.resolve("baki_data.tmp")
        // Write and fsync a complete temporary file before replacing the current data.
        java.io.FileOutputStream(tmp).use { stream ->
            stream.write(encode(shops, txs).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            // Never fall back to truncating the live data file. If atomic replacement is not
            // available, keep the previous data and report failure to the caller.
            tmp.delete()
            throw java.io.IOException("Could not safely save ledger data")
        }
    }

    fun validateAndDecode(json: String): Pair<List<Shop>, List<Tx>> {
        val root = JSONObject(json)
        require(root.optString("format") == "baki-backup") { "Invalid Hishab Ledger backup file." }
        val shops = buildList {
            val a = root.optJSONArray("shops") ?: JSONArray()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val type = runCatching { PartyType.valueOf(o.optString("type", "SHOP")) }.getOrDefault(PartyType.SHOP)
                add(Shop(o.getString("id"), o.getString("name"), o.optString("phone"), o.optString("address"), o.optLong("createdAt"), type, o.optLong("deletedAt", 0L)))
            }
        }
        val txs = buildList {
            val a = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(Tx(o.getString("id"), o.getString("shopId"), o.getLong("amount"), TxType.valueOf(o.getString("type")), o.optString("note"), o.optLong("timestamp"), o.optLong("reminderAt", 0L), o.optLong("deletedAt", 0L), o.optLong("transactionDate", o.optLong("timestamp"))))
            }
        }
        val ids = shops.map { it.id }.toSet()
        require(txs.all { it.shopId in ids }) { "Backup contains an unknown shop reference." }
        require(txs.all { it.amount > 0 }) { "Backup contains an invalid amount." }
        return shops to txs
    }

    fun lastBackup(): Long = prefs.getLong("last_backup", 0L)
    fun markBackup() { prefs.edit().putLong("last_backup", System.currentTimeMillis()).apply() }
}

class BakiViewModel(private val app: Context) : ViewModel() {
    private val store = LocalStore(app)
    var shops by mutableStateOf<List<Shop>>(emptyList()); private set
    var txs by mutableStateOf<List<Tx>>(emptyList()); private set

    init { reload() }

    fun reload() {
        val (s, t) = store.read()
        val cutoff = System.currentTimeMillis() - SOFT_DELETE_RETENTION_MS
        val keptShops = s.filter { it.deletedAt == 0L || it.deletedAt > cutoff }
        val keptTxs = t.filter { it.deletedAt == 0L || it.deletedAt > cutoff }
        if (keptShops.size != s.size || keptTxs.size != t.size) store.save(keptShops, keptTxs)
        shops = keptShops; txs = keptTxs
    }

    val activeShops: List<Shop> get() = shops.filter { it.deletedAt == 0L }
    val deletedShops: List<Shop> get() = shops.filter { it.deletedAt != 0L }
    fun activeTxs(shopId: String? = null): List<Tx> = txs.filter { it.deletedAt == 0L && (shopId == null || it.shopId == shopId) }
    fun deletedTxs(): List<Tx> = txs.filter { it.deletedAt != 0L }

    fun balance(shopId: String): Long = txs.asSequence().filter { it.shopId == shopId && it.deletedAt == 0L }.sumOf { if (it.type == TxType.PAYMENT) -it.amount else it.amount }
    fun totalDue(): Long = activeShops.sumOf { balance(it.id) }
    fun totalReceivable(): Long = activeShops.sumOf { balance(it.id).coerceAtLeast(0L) }
    fun totalPayable(): Long = activeShops.sumOf { balance(it.id).coerceAtLeast(0L) }
    fun netBalance(): Long = totalReceivable() - totalPayable()
    fun totalDueAdded(shopId: String): Long = txs.filter { it.shopId == shopId && it.deletedAt == 0L && it.type != TxType.PAYMENT }.sumOf { it.amount }
    fun totalPaid(shopId: String): Long = txs.filter { it.shopId == shopId && it.deletedAt == 0L && it.type == TxType.PAYMENT }.sumOf { it.amount }

    fun addShop(name: String, phone: String, address: String, opening: Long, type: PartyType) {
        require(name.isNotBlank())
        val shop = Shop(UUID.randomUUID().toString(), name.trim(), phone.trim(), address.trim(), type = type)
        val openingTx = if (opening > 0) Tx(UUID.randomUUID().toString(), shop.id, opening, TxType.OPENING_BALANCE, "Previous Due") else null
        val ns = shops + shop
        val nt = if (openingTx == null) txs else txs + openingTx
        store.save(ns, nt); shops = ns; txs = nt
    }

    fun addTx(shopId: String, amount: Long, type: TxType, note: String, reminderAt: Long = 0L, transactionDate: Long = System.currentTimeMillis()) {
        require(amount > 0)
        val tx = Tx(UUID.randomUUID().toString(), shopId, amount, type, note.trim(), timestamp = System.currentTimeMillis(), reminderAt = reminderAt, transactionDate = transactionDate)
        val nt = txs + tx
        store.save(shops, nt); txs = nt
        if (type == TxType.DUE && reminderAt > System.currentTimeMillis()) {
            val shopName = shops.firstOrNull { it.id == shopId }?.name ?: ""
            ReminderScheduler.schedule(app, tx.id, shopName, amount, reminderAt)
        }
    }

    fun softDeleteTx(id: String): Tx? {
        val target = txs.firstOrNull { it.id == id && it.deletedAt == 0L } ?: return null
        if (target.reminderAt > 0) ReminderScheduler.cancel(app, target.id)
        val updated = target.copy(deletedAt = System.currentTimeMillis())
        val nt = txs.map { if (it.id == id) updated else it }
        store.save(shops, nt); txs = nt
        return updated
    }

    fun restoreTx(id: String) {
        val target = txs.firstOrNull { it.id == id } ?: return
        val restored = target.copy(deletedAt = 0L)
        val nt = txs.map { if (it.id == id) restored else it }
        store.save(shops, nt); txs = nt
        if (restored.type == TxType.DUE && restored.reminderAt > System.currentTimeMillis()) {
            val shopName = shops.firstOrNull { it.id == restored.shopId }?.name ?: ""
            ReminderScheduler.schedule(app, restored.id, shopName, restored.amount, restored.reminderAt)
        }
    }

    fun purgeTxForever(id: String) {
        val target = txs.firstOrNull { it.id == id }
        if (target != null && target.reminderAt > 0) ReminderScheduler.cancel(app, target.id)
        val nt = txs.filterNot { it.id == id }
        store.save(shops, nt); txs = nt
    }

    fun softDeleteShop(id: String) {
        val target = shops.firstOrNull { it.id == id && it.deletedAt == 0L } ?: return
        val updated = target.copy(deletedAt = System.currentTimeMillis())
        val ns = shops.map { if (it.id == id) updated else it }
        txs.filter { it.shopId == id && it.reminderAt > 0 }.forEach { ReminderScheduler.cancel(app, it.id) }
        store.save(ns, txs); shops = ns
    }

    fun restoreShop(id: String) {
        val ns = shops.map { if (it.id == id) it.copy(deletedAt = 0L) else it }
        store.save(ns, txs); shops = ns
    }

    fun purgeShopForever(id: String) {
        val ns = shops.filterNot { it.id == id }
        val nt = txs.filterNot { it.shopId == id }
        store.save(ns, nt); shops = ns; txs = nt
    }

    fun backup(): String = store.encode(shops, txs)
    fun markBackup() = store.markBackup()
    fun lastBackup() = store.lastBackup()

    fun restore(json: String, replace: Boolean): Pair<Int, Int> {
        val (incomingS, incomingT) = store.validateAndDecode(json)
        val result = if (replace) incomingS to incomingT else {
            val existingShopIds = shops.map { it.id }.toSet()
            val mergedS = shops + incomingS.filterNot { it.id in existingShopIds }
            val existingTxIds = txs.map { it.id }.toSet()
            val mergedT = txs + incomingT.filterNot { it.id in existingTxIds }
            mergedS to mergedT
        }
        store.save(result.first, result.second)
        shops = result.first; txs = result.second
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

object AppThemePreference {
    var mode by mutableStateOf("system")
    fun load(context: Context) { mode = context.getSharedPreferences("hishab_appearance", Context.MODE_PRIVATE).getString("mode", "system") ?: "system" }
    fun set(context: Context, value: String) {
        mode = value
        context.getSharedPreferences("hishab_appearance", Context.MODE_PRIVATE).edit().putString("mode", value).apply()
    }
}


val LocalDarkTheme = staticCompositionLocalOf { false }

private val BakiLightColors = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDC), onPrimaryContainer = Color(0xFF0A3B2C),
    secondary = GreenDark, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EFE7), onSecondaryContainer = Color(0xFF14372A),
    tertiary = Color(0xFF3A6B7E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E9F1), onTertiaryContainer = Color(0xFF0B3441),
    background = Page, onBackground = Color(0xFF1D3028),
    surface = Color.White, onSurface = Color(0xFF1D3028),
    surfaceVariant = Color(0xFFE3EDE7), onSurfaceVariant = Color(0xFF4A5C53),
    surfaceTint = Green,
    inverseSurface = Color(0xFF2E3A34), inverseOnSurface = Color(0xFFEEF4F0), inversePrimary = Color(0xFF72D6AE),
    error = Danger, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7B8C82), outlineVariant = Color(0xFFC9D8CF),
    scrim = Color.Black,
    surfaceBright = Color.White, surfaceDim = Color(0xFFD8E0DB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F7F4),
    surfaceContainer = Color(0xFFEDF3EF), surfaceContainerHigh = Color(0xFFE7EEEA),
    surfaceContainerHighest = Color(0xFFE1E9E4)
)

private val BakiDarkColors = darkColorScheme(
    primary = Color(0xFF72D6AE), onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF0F5942), onPrimaryContainer = Color(0xFFC2F0DB),
    secondary = Color(0xFF9ADFC0), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF244237), onSecondaryContainer = Color(0xFFC5EBD9),
    tertiary = Color(0xFF9CCBDB), onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF1F4D5C), onTertiaryContainer = Color(0xFFC0E9F7),
    background = Color(0xFF101713), onBackground = Color(0xFFE2EAE4),
    surface = Color(0xFF18211C), onSurface = Color(0xFFE2EAE4),
    surfaceVariant = Color(0xFF26332C), onSurfaceVariant = Color(0xFFB8C8BD),
    surfaceTint = Color(0xFF72D6AE),
    inverseSurface = Color(0xFFE2EAE4), inverseOnSurface = Color(0xFF1D3028), inversePrimary = Green,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6F8178), outlineVariant = Color(0xFF4A5D52),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2F3B35), surfaceDim = Color(0xFF0D130F),
    surfaceContainerLowest = Color(0xFF0B110D), surfaceContainerLow = Color(0xFF141C17),
    surfaceContainer = Color(0xFF18211C), surfaceContainerHigh = Color(0xFF202B25),
    surfaceContainerHighest = Color(0xFF26332C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bakiTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onBackground
)

@Composable
fun BakiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppThemePreference.load(context) }
    val dark = when (AppThemePreference.mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) BakiDarkColors else BakiLightColors

    // Keep the status bar, navigation bar and window background in sync with the chosen theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
                @Suppress("DEPRECATION")
                window.statusBarColor = colors.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors) {
            // Root surface: guarantees a themed background and a readable default text colour everywhere.
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground, content = content)
        }
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { BakiTheme { BakiApp() } }
    }
}

@Composable
fun BakiApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLanguage.load(context) }
    var locked by remember { mutableStateOf(SecurityStore.isLockEnabled(context)) }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && SecurityStore.isLockEnabled(context)) locked = true
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    if (locked) {
        LockScreen(onUnlocked = { locked = false })
        return
    }

    val vm: BakiViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(c: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BakiViewModel(context.applicationContext) as T
        }
    })
    var shop by remember { mutableStateOf<Shop?>(null) }
    var settings by remember { mutableStateOf(false) }
    var recentlyDeleted by remember { mutableStateOf(false) }
    var addShop by remember { mutableStateOf(false) }
    var undoTx by remember { mutableStateOf<Tx?>(null) }
    var showGuide by remember { mutableStateOf(!OnboardingStore.isSeen(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackState.showSnackbar(it); message = null }
    }

    BackHandler(enabled = addShop || recentlyDeleted || settings || shop != null) {
        when {
            addShop -> addShop = false
            recentlyDeleted -> recentlyDeleted = false
            settings -> settings = false
            else -> shop = null
        }
    }

    if (showGuide) {
        OnboardingScreen(onFinish = { OnboardingStore.markSeen(context); showGuide = false })
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackState) }) { outer ->
        // AddShopDialog owns its own Scaffold and system-bar handling. Do not
        // apply the parent Scaffold insets to it a second time, or its content
        // is pushed down compared with the other screens.
        Box(if (addShop) Modifier else Modifier.padding(outer)) {
            val destination = when {
                recentlyDeleted -> "recently_deleted"
                settings -> "settings"
                shop != null -> "shop:${shop!!.id}"
                else -> "home"
            }
            Crossfade(targetState = destination, animationSpec = tween(durationMillis = 120), label = "main_page_transition") { target ->
                when {
                    target == "recently_deleted" -> RecentlyDeletedScreen(vm, { recentlyDeleted = false }, { message = it })
                    target == "settings" -> SettingsScreen(vm, { settings = false }, { message = it }, { recentlyDeleted = true }, { showGuide = true })
                    target.startsWith("shop:") && shop != null -> ShopScreen(vm, shop!!, { shop = null }, { deleted -> undoTx = deleted }, { message = it })
                    else -> HomeScreen(vm, { shop = it }, { addShop = true }, { settings = true })
                }
            }
        }
    }

    if (undoTx != null) {
        LaunchedEffect(undoTx) {
            val deleted = undoTx
            if (deleted != null) {
                val result = snackState.showSnackbar("Transaction deleted", "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) {
                    vm.restoreTx(deleted.id)
                    snackState.showSnackbar("Transaction restored", duration = SnackbarDuration.Short)
                }
            }
            undoTx = null
        }
    }

    if (addShop) AddShopDialog(onDismiss = { addShop = false }, onSave = { n, p, a, o, t -> vm.addShop(n, p, a, o, t); addShop = false; message = "Added" })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: BakiViewModel, open: (Shop) -> Unit, add: () -> Unit, settings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("name") }
    var showSort by remember { mutableStateOf(false) }
    val isBn = AppLanguage.isBangla
    val lastBackupAt by remember { mutableLongStateOf(vm.lastBackup()) }
    val backupReminder = lastBackupAt == 0L || System.currentTimeMillis() - lastBackupAt >= 7L * 24 * 60 * 60 * 1000
    val list = vm.activeShops.filter { it.name.contains(query, true) || it.phone.contains(query, true) }.let { matches ->
        when (sortMode) {
            "due" -> matches.sortedByDescending { vm.balance(it.id) }
            "recent" -> matches.sortedByDescending { vm.activeTxs(it.id).maxOfOrNull { tx -> tx.timestamp } ?: it.createdAt }
            else -> matches.sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = bakiTopBarColors(), 
                title = { Text("Hishab Ledger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 2.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(28.dp), shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth().height(66.dp).align(Alignment.BottomCenter)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { /* Home is the current destination */ }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Home, contentDescription = if (isBn) "হোম" else "Home", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                                Text(if (isBn) "হোম" else "Home", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(58.dp))
                        TextButton(onClick = settings, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Settings, contentDescription = if (isBn) "সেটিংস" else "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
                                Text(if (isBn) "সেটিংস" else "Settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                FloatingActionButton(onClick = add, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(50), elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp), modifier = Modifier.align(Alignment.TopCenter).offset(y = (-7).dp).size(58.dp)) {
                    Icon(Icons.Default.Add, contentDescription = if (isBn) "নতুন হিসাব" else "Add account", modifier = Modifier.size(30.dp))
                }
            }
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = if (isBn) "খুঁজুন" else "Search") },
                placeholder = { Text(if (isBn) "নাম বা ফোন নম্বর দিয়ে খুঁজুন" else "Search by name or phone number") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            if (backupReminder) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { settings() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (isBn) "আপনার হিসাবের ব্যাকআপ নিন" else "Keep your ledger backed up", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isBn) { if (lastBackupAt == 0L) "এখনও ব্যাকআপ নেওয়া হয়নি। সংরক্ষণ করতে ট্যাপ করুন।" else "৭ দিনের বেশি সময় ধরে ব্যাকআপ নেওয়া হয়নি।" } else { if (lastBackupAt == 0L) "No backup yet. Tap to save a copy." else "It has been over 7 days since your last backup." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(if (isBn) "মোট দেনা" else "Total Payable", money(vm.totalPayable()), Modifier.weight(1f), Icons.Default.AccountBalanceWallet, Color(0xFFE6F5ED), Color(0xFF1E3A2E))
                SummaryCard(if (isBn) "মোট হিসাব" else "Total Accounts", localizedNumber(vm.activeShops.size), Modifier.weight(1f), Icons.Default.People, Color(0xFFE8F2FF), Color(0xFF1C2E44))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (isBn) "আপনার দোকান ও ব্যক্তির হিসাব" else "Your Shops & People", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isBn) when (sortMode) { "due" -> "বেশি বাকি আগে"; "recent" -> "সাম্প্রতিক আগে"; else -> "নাম অনুযায়ী" } else when (sortMode) { "due" -> "Highest due"; "recent" -> "Recent activity"; else -> "Name" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { showSort = true }) { Icon(Icons.Default.Tune, contentDescription = if (isBn) "সর্ট ও ফিল্টার" else "Sort and filter", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Text(if (query.isBlank()) { if (isBn) "এখনো কোনো হিসাব নেই" else "Nothing added yet" } else { if (isBn) "কোনো মিল পাওয়া যায়নি" else "No matches found" }, fontWeight = FontWeight.SemiBold)
                        Text(if (query.isBlank()) { if (isBn) "বাকি হিসাব রাখতে দোকান বা ব্যক্তি যোগ করুন।" else "Add a shop or a person to start tracking dues." } else { if (isBn) "অন্য নাম দিয়ে চেষ্টা করুন।" else "Try a different name." }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.id }) { s ->
                    val balance = vm.balance(s.id)
                    val last = vm.activeTxs(s.id).maxByOrNull { it.timestamp }
                    Card(
                        Modifier.fillMaxWidth().clickable { open(s) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (s.type == PartyType.PERSON) Icons.Default.Person else Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                Text(if (isBn) "বর্তমান বাকি" else "Current Due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(balance), fontWeight = FontWeight.Bold, color = if (balance > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                                if (last != null) Text((if (isBn) "সর্বশেষ এন্ট্রি • " else "Last activity • ") + relativeDate(last.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = if (isBn) "খুলুন" else "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isBn) "সর্ট ও ফিল্টার" else "Sort & filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (isBn) "হিসাব সাজানোর পদ্ধতি বেছে নিন" else "Choose how accounts are arranged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("name" to (if (isBn) "নাম অনুযায়ী" else "Name (A–Z)"), "due" to (if (isBn) "বেশি বাকি আগে" else "Highest due first"), "recent" to (if (isBn) "সর্বশেষ লেনদেন আগে" else "Most recent activity")).forEach { (key, label) ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { sortMode = key; showSort = false }, shape = RoundedCornerShape(14.dp), color = if (sortMode == key) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = if (sortMode == key) FontWeight.SemiBold else FontWeight.Normal)
                            if (sortMode == key) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OutlinedButton(onClick = { sortMode = "name"; query = ""; showSort = false }, modifier = Modifier.fillMaxWidth()) { Text(if (isBn) "ফিল্টার রিসেট" else "Reset search & sort") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, cardColor: Color, darkCardColor: Color) {
    val dark = LocalDarkTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = if (dark) darkCardColor else cardColor),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.heightIn(min = 126.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

private fun localizedNumber(value: Int): String {
    val digits = value.toString()
    return if (AppLanguage.isBangla) digits.map { ch -> if (ch in '0'..'9') ('০'.code + (ch - '0')).toChar() else ch }.joinToString("") else digits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(vm: BakiViewModel, shop: Shop, back: () -> Unit, onDeleted: (Tx) -> Unit, toast: (String) -> Unit) {
    var addDialog by remember { mutableStateOf<TxType?>(null) }
    // Consume the system Back press while the transaction form is open so it
    // closes the form first and leaves the user on Shop Details.
    BackHandler(enabled = addDialog != null) { addDialog = null }
    var deleteTx by remember { mutableStateOf<Tx?>(null) }
    var deleteShop by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var shareStatement by remember { mutableStateOf(false) }
    var showPhoneActions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.csv(shop.id).toByteArray(Charsets.UTF_8)) }; toast("Shop CSV exported") }
            .onFailure { toast("Could not export CSV") }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(buildShopPdf(vm, shop)) }; toast("PDF statement exported") }
            .onFailure { toast("Could not export PDF") }
    }
    val history = vm.activeTxs(shop.id).sortedByDescending { it.transactionDate }
    val latestDueId = remember(history) { history.filter { it.type == TxType.DUE }.maxByOrNull { it.transactionDate }?.id }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(colors = bakiTopBarColors(), title = { Text(shop.name, maxLines = 1) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { if (shop.phone.isNotBlank()) IconButton({ showPhoneActions = true }) { Icon(Icons.Default.Phone, "Phone") }; IconButton({ deleteShop = true }) { Icon(Icons.Default.Delete, "Delete") } }, windowInsets = WindowInsets(0, 0, 0, 0)) } ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Text("Current Due", style = MaterialTheme.typography.labelLarge)
                val balance = vm.balance(shop.id)
                Text(money(balance), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (balance == 0L) Text("All payments settled", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                if (shop.phone.isNotBlank()) Text(shop.phone, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { showPhoneActions = true })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ addDialog = TxType.DUE }, Modifier.weight(1f)) { Text(if (AppLanguage.isBangla) "বাকি যোগ করুন" else "Add Due") }
                    OutlinedButton({ addDialog = TxType.PAYMENT }, Modifier.weight(1f)) { Text(if (AppLanguage.isBangla) "পেমেন্ট যোগ করুন" else "Add Payment") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton({ showSummary = true }, Modifier.fillMaxWidth()) { Text(if (AppLanguage.isBangla) "সারাংশ দেখুন" else "View Summary") }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { shareStatement = true }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                    AssistChip(onClick = { csvLauncher.launch("HishabLedger_${shop.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.csv") }, label = { Text("CSV") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    AssistChip(onClick = { pdfLauncher.launch("HishabLedger_${shop.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_Statement.pdf") }, label = { Text("PDF") }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) })
                }
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Total Due Added", style = MaterialTheme.typography.labelSmall); Text(money(vm.totalDueAdded(shop.id)), fontWeight = FontWeight.SemiBold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Total Paid", style = MaterialTheme.typography.labelSmall); Text(money(vm.totalPaid(shop.id)), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            HorizontalDivider()
            Text(if (AppLanguage.isBangla) "এন্ট্রি" else "Entries", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            if (history.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transactions yet") }
            else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { t ->
                    val positive = t.type != TxType.PAYMENT
                    val label = when {
                        t.type == TxType.PAYMENT -> "Payment"
                        t.type == TxType.OPENING_BALANCE -> "Previous Due"
                        t.id == latestDueId -> "New Due"
                        else -> "Due"
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                Text("Entered: ${formatFull(t.timestamp)}", style = MaterialTheme.typography.labelSmall)
                                if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${if (positive) "+" else "-"}${money(t.amount)}", color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            IconButton({ deleteTx = t }) { Icon(Icons.Default.Delete, "Delete transaction", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    addDialog?.let { type -> AddTxDialog(type, { addDialog = null }) { amount, note, reminderAt, transactionDate -> vm.addTx(shop.id, amount, type, note, reminderAt, transactionDate); addDialog = null; toast(if (type == TxType.PAYMENT) "Payment added" else "Due added") } }
    deleteTx?.let { t -> AlertDialog(onDismissRequest = { deleteTx = null }, title = { Text("Delete ${if (t.type == TxType.PAYMENT) "payment" else "due"}?") }, text = { Text("This moves it to Recently Deleted for 30 days before it's gone for good. You can restore it anytime before that from Settings.") }, confirmButton = { TextButton({ val deleted = vm.softDeleteTx(t.id); deleteTx = null; deleted?.let(onDeleted) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleteTx = null }) { Text("Cancel") } }) }
    if (deleteShop) AlertDialog(onDismissRequest = { deleteShop = false }, title = { Text("Delete ${shop.name}?") }, text = { Text("This moves it to Recently Deleted for 30 days, after which it's permanently removed. You can restore it anytime before that from Settings.") }, confirmButton = { TextButton({ vm.softDeleteShop(shop.id); deleteShop = false; back(); toast("Moved to Recently Deleted") }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleteShop = false }) { Text("Cancel") } })
    if (showSummary) SummaryDialog(vm, shop, history) { showSummary = false }
    if (shareStatement) { ShareStatementDialog(vm, shop) { shareStatement = false } }
    if (showPhoneActions) AlertDialog(onDismissRequest = { showPhoneActions = false }, title = { Text(shop.phone) }, text = { Text("Call or copy this number.") }, confirmButton = { TextButton({ context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(shop.phone)}"))); showPhoneActions = false }) { Text("Call") } }, dismissButton = { TextButton({ val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clip.setPrimaryClip(android.content.ClipData.newPlainText("Phone", shop.phone)); showPhoneActions = false }) { Text("Copy") } })
}

@Composable
fun ShareStatementDialog(vm: BakiViewModel, shop: Shop, close: () -> Unit) {
    val context = LocalContext.current
    val text = buildString {
        append(shop.name).append('\n')
        append("Current Due: ").append(money(vm.balance(shop.id))).append('\n')
        append("Total Due Added: ").append(money(vm.totalDueAdded(shop.id))).append('\n')
        append("Total Paid: ").append(money(vm.totalPaid(shop.id)))
    }
    AlertDialog(onDismissRequest = close, title = { Text("Share Statement") }, text = { Text(text) }, confirmButton = { TextButton({ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "${shop.name} statement"); putExtra(Intent.EXTRA_TEXT, text) }, "Share with")); close() }) { Text("Share") } }, dismissButton = { TextButton(close) { Text("Cancel") } })
}

@Composable
fun SummaryDialog(vm: BakiViewModel, shop: Shop, history: List<Tx>, close: () -> Unit) {
    val today = history.filter { isToday(it.transactionDate) }
    val due = today.filter { it.type != TxType.PAYMENT }.sumOf { it.amount }
    val paid = today.filter { it.type == TxType.PAYMENT }.sumOf { it.amount }
    AlertDialog(onDismissRequest = close, title = { Text("Today") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(shop.name, fontWeight = FontWeight.Bold); Text("Added Due: ${money(due)}"); Text("Payments: ${money(paid)}"); Text("Net Change: ${money(due - paid)}"); Text("Current Due: ${money(vm.balance(shop.id))}") } }, confirmButton = { TextButton(close) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShopDialog(onDismiss: () -> Unit, onSave: (String, String, String, Long, PartyType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PartyType.SHOP) }
    val emerald = MaterialTheme.colorScheme.primary
    val emeraldDark = MaterialTheme.colorScheme.primary
    val mint = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty()
                    val selectedPhone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()
                    if (name.isBlank()) name = displayName
                    phone = selectedPhone
                }
            }
        }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = emerald,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = emerald,
        cursorColor = emerald,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Add New", fontWeight = FontWeight.Bold, color = ink)
                        Text("Create a ledger account", style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.85f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)
                    ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { onSave(name.trim(), phone.trim(), address.trim(), opening.toLongOrNull() ?: 0L, type) },
                        modifier = Modifier.weight(1.3f).height(54.dp),
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = emerald, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .imePadding().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Soft emerald header card adds hierarchy without changing the existing workflow.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = mint),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(54.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.MenuBook, contentDescription = null, tint = emeraldDark, modifier = Modifier.size(29.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("A fresh start", fontWeight = FontWeight.Bold, color = ink, style = MaterialTheme.typography.titleMedium)
                        Text("Keep names and balances organised in one place.", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Account details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                        Text("Choose the account type and add the details below.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shopSelected = type == PartyType.SHOP
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { type = PartyType.SHOP },
                            shape = RoundedCornerShape(12.dp),
                            color = if (shopSelected) mint else Color.Transparent,
                            border = if (shopSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                        ) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = if (shopSelected) emeraldDark else muted, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Shop", color = if (shopSelected) emeraldDark else muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        val personSelected = type == PartyType.PERSON
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { type = PartyType.PERSON },
                            shape = RoundedCornerShape(12.dp),
                            color = if (personSelected) mint else Color.Transparent,
                            border = if (personSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                        ) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = if (personSelected) emeraldDark else muted, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Person", color = if (personSelected) emeraldDark else muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (type == PartyType.PERSON) "Person's name *" else "Shop name *") },
                        placeholder = { Text(if (type == PartyType.PERSON) "Enter person's name" else "Enter shop name", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(if (type == PartyType.PERSON) Icons.Default.PersonOutline else Icons.Default.Storefront, contentDescription = null, tint = muted) },
                        singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone (Optional)") }, placeholder = { Text("Enter phone number", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = emerald) },
                        trailingIcon = { IconButton(onClick = { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) { Icon(Icons.Default.ContactPhone, contentDescription = "Choose from contacts", tint = emerald) } },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    OutlinedTextField(
                        value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Address (Optional)") }, placeholder = { Text("Enter address", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = muted) },
                        singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    OutlinedTextField(
                        value = opening, onValueChange = { opening = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Previous due (Optional)") }, placeholder = { Text("৳ 0", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = emerald) },
                        supportingText = { Text("Amount already due before using Hishab Ledger.", color = muted) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxDialog(type: TxType, close: () -> Unit, save: (Long, String, Long, Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val reminderAt = 0L
    var transactionDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val payment = type == TxType.PAYMENT
    val suggestions = if (payment) listOf(1000L, 2000L, 3000L) else listOf(100L, 500L, 1000L)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = bakiTopBarColors(), 
            title = { Text(if (payment) "Add Payment" else "Add Due") },
            navigationIcon = { IconButton(close) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { val v = amount.toLongOrNull() ?: 0L; if (v > 0) save(v, note, reminderAt, transactionDate) }, enabled = (amount.toLongOrNull() ?: 0L) > 0) { Icon(Icons.Default.CheckCircle, "Save", tint = MaterialTheme.colorScheme.primary) } },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (payment) "Payment details" else "Due details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), leadingIcon = { Icon(Icons.Default.Payments, null) })
                    Text("Quick amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { suggestion -> SuggestionChip(onClick = { amount = ((amount.toLongOrNull() ?: 0L) + suggestion).toString() }, label = { Text("+${String.format("%,d", suggestion)}") }) }
                    }
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (AppLanguage.isBangla) "লেনদেনের তারিখ" else "Transaction date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(SimpleDateFormat("dd MMMM yyyy", if (AppLanguage.isBangla) Locale("bn", "BD") else Locale.getDefault()).format(Date(transactionDate)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(Icons.Default.EditCalendar, contentDescription = if (AppLanguage.isBangla) "তারিখ বাছাই" else "Choose date", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (AppLanguage.isBangla) "নোট (ঐচ্ছিক)" else "Note (Optional)") }, minLines = 2)
                    Button(onClick = { val v = amount.toLongOrNull() ?: 0L; if (v > 0) save(v, note, reminderAt, transactionDate) }, modifier = Modifier.fillMaxWidth(), enabled = (amount.toLongOrNull() ?: 0L) > 0, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(if (payment) "Save Payment" else "Save Due") }
                }
            }
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = transactionDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { dateState.selectedDateMillis?.let { picked ->
                val pickedCalendar = Calendar.getInstance().apply { timeInMillis = picked; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                transactionDate = pickedCalendar.timeInMillis
            }; showDatePicker = false }) { Text(if (AppLanguage.isBangla) "নির্বাচন" else "Select") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (AppLanguage.isBangla) "বাতিল" else "Cancel") } }) {
            DatePicker(state = dateState, title = { Text(if (AppLanguage.isBangla) "লেনদেনের তারিখ বেছে নিন" else "Choose transaction date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BakiViewModel, back: () -> Unit, toast: (String) -> Unit, openRecentlyDeleted: () -> Unit, openGuide: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("main") }
    var importPreview by remember { mutableStateOf<String?>(null) }
    var importJson by remember { mutableStateOf<String?>(null) }
    var lastBackup by remember { mutableLongStateOf(vm.lastBackup()) }
    var lockEnabled by remember { mutableStateOf(SecurityStore.isLockEnabled(context)) }
    var showSetPin by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }
    val bn = AppLanguage.isBangla

    BackHandler(enabled = page != "main") { page = "main" }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open backup destination"); output.use { it.write(vm.backup().toByteArray(Charsets.UTF_8)) }; vm.markBackup(); lastBackup = vm.lastBackup(); toast(if (AppLanguage.isBangla) "ব্যাকআপ এক্সপোর্ট হয়েছে" else "Backup exported") }
            .onFailure { toast(if (AppLanguage.isBangla) "ব্যাকআপ এক্সপোর্ট করা যায়নি" else "Could not export backup") }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(vm.csv()) } }; toast(if (AppLanguage.isBangla) "CSV এক্সপোর্ট হয়েছে" else "CSV exported") }
            .onFailure { toast(if (AppLanguage.isBangla) "CSV এক্সপোর্ট করা যায়নি" else "Could not export CSV") }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { val json = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }; val (shops, txs) = LocalStore(context).validateAndDecode(json); val backupAt = JSONObject(json).optLong("backupCreatedAt", 0L); importJson = json; importPreview = "Shops: ${shops.size}\nTransactions: ${txs.size}\nBackup date: ${if (backupAt > 0) formatFull(backupAt) else "Unknown"}" }
            .onFailure { toast(if (AppLanguage.isBangla) "সঠিক Hishab Ledger ব্যাকআপ নয়" else "Invalid Hishab Ledger backup") }
    }

    val title = when (page) {
        "language" -> if (bn) "ভাষা" else "Language"
        "appearance" -> if (bn) "থিম ও চেহারা" else "Appearance & theme"
        "backup" -> "Backup & Data"
        "lock" -> "App Lock"
        "deleted" -> if (bn) "সম্প্রতি মুছে ফেলা" else "Recently Deleted"
        "about" -> "About"
        else -> if (bn) "সেটিংস" else "Settings"
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = bakiTopBarColors(), title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
            IconButton(onClick = { if (page == "main") back() else page = "main" }) { Icon(Icons.Default.ArrowBack, contentDescription = if (bn) "ফিরে যান" else "Back") }
        }, windowInsets = WindowInsets(0, 0, 0, 0))
    }) { padding ->
        when (page) {
            "main" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "পছন্দ ও অ্যাপ ব্যবস্থাপনা" else "Preferences & app management", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
                SettingsOptionCard(Icons.Default.Language, if (bn) "ভাষা" else "Language", if (bn) "অ্যাপের ভাষা বেছে নিন" else "Choose your app language", { page = "language" })
                SettingsOptionCard(Icons.Default.DarkMode, if (bn) "থিম ও চেহারা" else "Appearance & theme", if (bn) "Light, Dark অথবা ফোনের থিম" else "Light, Dark or follow system", { page = "appearance" })
                SettingsOptionCard(Icons.Default.Lightbulb, if (bn) "ব্যবহার গাইড" else "How to use", if (bn) "শুরু করার ছোট নির্দেশিকা আবার দেখুন" else "Watch the quick start guide again", { openGuide() })
                SettingsOptionCard(Icons.Default.CloudSync, "Backup & Data", if (bn) "ব্যাকআপ, রিস্টোর ও CSV ফাইল" else "Backup, restore and CSV files", { page = "backup" })
                SettingsOptionCard(Icons.Default.Lock, "App Lock", if (bn) "PIN ও বায়োমেট্রিক নিরাপত্তা" else "PIN and biometric security", { page = "lock" })
                SettingsOptionCard(Icons.Default.RestoreFromTrash, if (bn) "সম্প্রতি মুছে ফেলা" else "Recently Deleted", if (bn) "মুছে ফেলা হিসাব পুনরুদ্ধার করুন" else "Restore deleted accounts and entries", { openRecentlyDeleted() }, "${vm.deletedShops.size + vm.deletedTxs().size}")
                SettingsOptionCard(Icons.Default.Info, if (bn) "পরিচিতি" else "About", if (bn) "অ্যাপ ও নির্মাতা সম্পর্কে" else "App and developer information", { page = "about" })
            }
            "language" -> Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "অ্যাপের ভাষা" else "App language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsChoiceRow("English", "English", selected = !AppLanguage.isBangla) { AppLanguage.set(context, false) }
                SettingsChoiceRow("বাংলা", "বাংলা", selected = AppLanguage.isBangla) { AppLanguage.set(context, true) }
                Text(if (bn) "ভাষা বদলালে সেটিংসের নামও সেই ভাষায় দেখাবে।" else "Settings labels follow the language you select.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "appearance" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "আপনার পছন্দের থিম বেছে নিন" else "Choose your preferred theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (bn) "অ্যাপের সব স্ক্রিনে এই পছন্দ প্রয়োগ হবে।" else "Your choice applies across the app and is saved on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsChoiceRow(if (bn) "হালকা থিম" else "Light", if (bn) "সাদা ব্যাকগ্রাউন্ড ও সবুজ অ্যাকসেন্ট" else "Bright surfaces and green accents", selected = AppThemePreference.mode == "light") { AppThemePreference.set(context, "light") }
                SettingsChoiceRow(if (bn) "ডার্ক থিম" else "Dark", if (bn) "গাঢ় ব্যাকগ্রাউন্ড ও চোখে আরামদায়ক রং" else "Charcoal surfaces with soft emerald accents", selected = AppThemePreference.mode == "dark") { AppThemePreference.set(context, "dark") }
                SettingsChoiceRow(if (bn) "ফোনের থিম" else "System default", if (bn) "ফোনের Light/Dark সেটিং অনুসরণ করবে" else "Follow your phone's appearance setting", selected = AppThemePreference.mode == "system") { AppThemePreference.set(context, "system") }
            }
            "backup" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsOptionCard(Icons.Default.Download, if (bn) "ব্যাকআপ এক্সপোর্ট" else "Export Backup", if (bn) "সব হিসাব JSON ফাইলে সংরক্ষণ করুন" else "Save your ledger to a JSON backup file", { createJson.launch("HishabLedger_Backup.json") })
                SettingsOptionCard(Icons.Default.Upload, if (bn) "ব্যাকআপ ইমপোর্ট" else "Import Backup", if (bn) "আগের ব্যাকআপ থেকে হিসাব ফিরিয়ে আনুন" else "Restore data from an existing backup", { openJson.launch(arrayOf("application/json", "text/plain")) })
                SettingsOptionCard(Icons.Default.TableChart, if (bn) "CSV এক্সপোর্ট" else "Export CSV", if (bn) "স্প্রেডশিটে ব্যবহারের জন্য এন্ট্রি এক্সপোর্ট" else "Export transactions for spreadsheet apps", { createCsv.launch("HishabLedger_Transactions.csv") })
                if (lastBackup > 0) Text((if (bn) "সর্বশেষ ব্যাকআপ: " else "Last backup: ") + formatFull(lastBackup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            "lock" -> Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsIcon(Icons.Default.Lock)
                        Column(Modifier.weight(1f)) {
                            Text(if (bn) "PIN দিয়ে অ্যাপ লক" else "Require PIN to open app", fontWeight = FontWeight.SemiBold)
                            Text(if (lockEnabled) { if (bn) "অ্যাপ লক চালু আছে" else "App lock is enabled" } else { if (bn) "অ্যাপ লক বন্ধ আছে" else "Off by default; enable it anytime" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockEnabled, onCheckedChange = { checked -> if (checked) showSetPin = true else showDisableConfirm = true })
                    }
                }
                if (lockEnabled && SecurityStore.canUseBiometric(context)) {
                    var bioEnabled by remember { mutableStateOf(SecurityStore.isBiometricEnabled(context)) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(Icons.Default.Fingerprint)
                            Column(Modifier.weight(1f)) { Text(if (bn) "ফিঙ্গারপ্রিন্ট/ফেস আনলক" else "Fingerprint / face unlock", fontWeight = FontWeight.SemiBold); Text(if (bn) "সমর্থিত হলে ব্যবহার করুন" else "Allow biometric unlock when available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = bioEnabled, onCheckedChange = { bioEnabled = it; SecurityStore.setBiometricEnabled(context, it) })
                        }
                    }
                }
                if (lockEnabled) OutlinedButton(onClick = { showSetPin = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text(if (bn) "PIN পরিবর্তন করুন" else "Change PIN") }
            }
            "about" -> Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(26.dp))
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(92.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) } }
                Text("Hishab Ledger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Text(if (bn) "Designed & developed by" else "Designed & developed by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CREATOR, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Version $APP_VERSION", style = MaterialTheme.typography.bodyMedium)
                Text(if (bn) "অফলাইন • লোকাল ডেটা • অ্যাকাউন্ট প্রয়োজন নেই" else "Offline • Local data • No account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Spacer(Modifier.padding(padding))
        }
    }

    if (importPreview != null && importJson != null) AlertDialog(onDismissRequest = { importPreview = null; importJson = null }, title = { Text(if (bn) "ব্যাকআপ রিস্টোর" else "Restore Backup") }, text = { Text("$importPreview\n\n${if (bn) "কীভাবে রিস্টোর করবেন বেছে নিন।" else "Choose how to restore this backup."}") }, confirmButton = { TextButton({ runCatching { vm.restore(importJson!!, true); toast(if (bn) "ব্যাকআপ রিস্টোর হয়েছে" else "Backup restored") }.onFailure { toast(if (bn) "রিস্টোর ব্যর্থ হয়েছে" else "Restore failed") }; importPreview = null; importJson = null }) { Text(if (bn) "Replace" else "Replace") } }, dismissButton = { Row { TextButton({ runCatching { vm.restore(importJson!!, false); toast(if (bn) "ব্যাকআপ মার্জ হয়েছে" else "Backup merged") }.onFailure { toast(if (bn) "মার্জ ব্যর্থ হয়েছে" else "Merge failed") }; importPreview = null; importJson = null }) { Text("Merge") }; TextButton({ importPreview = null; importJson = null }) { Text(if (bn) "বাতিল" else "Cancel") } } })

    if (showSetPin) {
        var pin1 by remember { mutableStateOf("") }; var pin2 by remember { mutableStateOf("") }; var mismatch by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showSetPin = false }, title = { Text(if (bn) "PIN সেট করুন" else "Set a PIN") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pin1, { pin1 = it.filter(Char::isDigit).take(6); mismatch = false }, label = { Text(if (bn) "নতুন PIN (৪-৬ সংখ্যা)" else "New PIN (4-6 digits)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(6); mismatch = false }, label = { Text(if (bn) "PIN নিশ্চিত করুন" else "Confirm PIN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
            if (mismatch) Text(if (bn) "PIN মেলেনি বা খুব ছোট" else "PINs don't match or are too short", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({ if (pin1.length in 4..6 && pin1 == pin2) { SecurityStore.setPin(context, pin1); lockEnabled = true; showSetPin = false; toast(if (bn) "অ্যাপ লক চালু হয়েছে" else "App lock enabled") } else mismatch = true }) { Text(if (bn) "সংরক্ষণ" else "Save") } }, dismissButton = { TextButton({ showSetPin = false }) { Text(if (bn) "বাতিল" else "Cancel") } })
    }
    if (showDisableConfirm) AlertDialog(onDismissRequest = { showDisableConfirm = false }, title = { Text(if (bn) "অ্যাপ লক বন্ধ করবেন?" else "Turn off app lock?") }, text = { Text(if (bn) "PIN ছাড়াই যে কেউ আপনার হিসাব দেখতে পারবে।" else "Anyone who opens Hishab Ledger will be able to see your data without a PIN.") }, confirmButton = { TextButton({ SecurityStore.disableLock(context); lockEnabled = false; showDisableConfirm = false; toast(if (bn) "অ্যাপ লক বন্ধ হয়েছে" else "App lock disabled") }) { Text(if (bn) "বন্ধ করুন" else "Turn off", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ showDisableConfirm = false }) { Text(if (bn) "বাতিল" else "Cancel") } })
}

@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)) }
    }
}

@Composable
private fun SettingsOptionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: String? = null) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (title != subtitle) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(vm: BakiViewModel, back: () -> Unit, toast: (String) -> Unit) {
    val deletedShops = vm.deletedShops
    val deletedTxs = vm.deletedTxs()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(colors = bakiTopBarColors(), title = { Text(if (AppLanguage.isBangla) "সম্প্রতি মুছে ফেলা" else "Recently Deleted") }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Back") } }, windowInsets = WindowInsets(0, 0, 0, 0)) }) { p ->
        if (deletedShops.isEmpty() && deletedTxs.isEmpty()) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing deleted in the last 30 days") }
        } else {
            LazyColumn(Modifier.padding(p).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (deletedShops.isNotEmpty()) {
                    item { Text("Shops & People", fontWeight = FontWeight.Bold) }
                    items(deletedShops, key = { "shop_" + it.id }) { s ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, fontWeight = FontWeight.SemiBold)
                                    Text("Deleted ${relativeDate(s.deletedAt)}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton({ vm.restoreShop(s.id); toast("Restored") }) { Text("Restore") }
                                IconButton({ vm.purgeShopForever(s.id); toast("Permanently deleted") }) { Icon(Icons.Default.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (deletedTxs.isNotEmpty()) {
                    item { Text("Entries", fontWeight = FontWeight.Bold) }
                    items(deletedTxs, key = { "tx_" + it.id }) { t ->
                        val shopName = vm.shops.firstOrNull { it.id == t.shopId }?.name ?: "Unknown"
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("$shopName • ${money(t.amount)}", fontWeight = FontWeight.SemiBold)
                                    Text("Deleted ${relativeDate(t.deletedAt)}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton({ vm.restoreTx(t.id); toast("Restored") }) { Text("Restore") }
                                IconButton({ vm.purgeTxForever(t.id); toast("Permanently deleted") }) { Icon(Icons.Default.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildShopPdf(vm: BakiViewModel, shop: Shop): ByteArray {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val left = 36f
    val right = 559f
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
    var canvas = page.canvas
    var y = 0f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(15, 89, 66); textSize = 22f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11f }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
    val history = vm.activeTxs(shop.id).sortedBy { it.transactionDate }

    fun header() {
        canvas.drawText("HishabLedger", left, 38f, titlePaint)
        titlePaint.textSize = 15f
        canvas.drawText(shop.name.take(42), left, 62f, titlePaint)
        titlePaint.textSize = 22f
        canvas.drawText("Account Statement", left, 84f, labelPaint)
        canvas.drawText("Generated: ${formatFull(System.currentTimeMillis())}", left, 101f, labelPaint)
        canvas.drawText("Current balance: ${money(vm.balance(shop.id))}", left, 124f, boldPaint)
        canvas.drawText("Total added: ${money(vm.totalDueAdded(shop.id))}", left, 143f, labelPaint)
        canvas.drawText("Total paid: ${money(vm.totalPaid(shop.id))}", left + 245f, 143f, labelPaint)
        canvas.drawLine(left, 156f, right, 156f, linePaint)
        canvas.drawText("Type / Note", 160f, 174f, boldPaint)
        canvas.drawText("Amount", 420f, 174f, boldPaint)
        canvas.drawText("Balance", 500f, 174f, boldPaint)
        canvas.drawLine(left, 181f, right, 181f, linePaint)
        y = 198f
    }
    fun startPage() {
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        page = document.startPage(info)
        canvas = page.canvas
        header()
    }
    fun finishPage() {
        canvas.drawLine(left, 812f, right, 812f, linePaint)
        canvas.drawText("Wafyro Software  •  Page ${pageNumber - 1}", left, 829f, labelPaint)
        document.finishPage(page)
    }
    header()
    var runningBalance = 0L
    history.forEach { t ->
        runningBalance += if (t.type == TxType.PAYMENT) -t.amount else t.amount
        val note = t.note.replace('\n', ' ').take(34)
        val type = when (t.type) { TxType.PAYMENT -> "Payment"; TxType.OPENING_BALANCE -> "Opening balance"; TxType.DUE -> "Due" }
        if (y > 785f) { finishPage(); startPage() }
        canvas.drawText(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t.transactionDate)), left, y, bodyPaint)
        canvas.drawText((type + if (note.isNotBlank()) " • $note" else "").take(40), 160f, y, bodyPaint)
        canvas.drawText(money(t.amount).take(15), 420f, y, bodyPaint)
        canvas.drawText(money(runningBalance).take(15), 500f, y, bodyPaint)
        y += 19f
        canvas.drawLine(left, y - 7f, right, y - 7f, linePaint)
    }
    if (history.isEmpty()) canvas.drawText("No transactions in this account yet.", left, y, bodyPaint)
    finishPage()
    val output = java.io.ByteArrayOutputStream()
    document.writeTo(output)
    document.close()
    return output.toByteArray()
}
