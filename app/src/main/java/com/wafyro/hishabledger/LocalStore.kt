package com.wafyro.hishabledger

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(private val context: Context) {
    private val dataFile get() = context.filesDir.resolve("hishab_data.json")
    private val prefs get() = context.getSharedPreferences("hishab_settings", Context.MODE_PRIVATE)

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
                    add(Tx(o.getString("id"), o.getString("shopId"), o.getLong("amount"), TxType.valueOf(o.getString("type")), o.optString("note"), o.optLong("timestamp"), o.optLong("deletedAt", 0L), o.optLong("transactionDate", o.optLong("timestamp"))))
                }
            }
            shops to txs
        } catch (_: Exception) {
            emptyList<Shop>() to emptyList()
        }
    }

    fun encode(shops: List<Shop>, txs: List<Tx>): String {
        val root = JSONObject().put("format", BACKUP_FORMAT).put("version", 2).put("backupCreatedAt", System.currentTimeMillis())
        root.put("shops", JSONArray().apply {
            shops.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("phone", s.phone); put("address", s.address)
                put("createdAt", s.createdAt); put("type", s.type.name); put("deletedAt", s.deletedAt)
            }) }
        })
        root.put("transactions", JSONArray().apply {
            txs.forEach { t -> put(JSONObject().apply {
                put("id", t.id); put("shopId", t.shopId); put("amount", t.amount); put("type", t.type.name)
                put("note", t.note); put("timestamp", t.timestamp); put("transactionDate", t.transactionDate); put("deletedAt", t.deletedAt)
            }) }
        })
        return root.toString(2)
    }

    fun save(shops: List<Shop>, txs: List<Tx>) {
        val tmp = context.filesDir.resolve("hishab_data.tmp")
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
        require(root.optString("format") in setOf(BACKUP_FORMAT, LEGACY_BACKUP_FORMAT)) { "Invalid Hishab Ledger backup file." }
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
                add(Tx(o.getString("id"), o.getString("shopId"), o.getLong("amount"), TxType.valueOf(o.getString("type")), o.optString("note"), o.optLong("timestamp"), o.optLong("deletedAt", 0L), o.optLong("transactionDate", o.optLong("timestamp"))))
            }
        }
        val ids = shops.map { it.id }.toSet()
        require(txs.all { it.shopId in ids }) { "Backup contains an unknown shop reference." }
        require(txs.all { it.amount > 0 }) { "Backup contains an invalid amount." }
        return shops to txs
    }

    fun lastBackup(): Long = prefs.getLong("last_backup", 0L)
    fun markBackup() { prefs.edit().putLong("last_backup", System.currentTimeMillis()).apply() }

    /** Deletes the ledger file and the backup timestamp. */
    fun deleteAll() {
        dataFile.delete()
        context.filesDir.resolve("hishab_data.tmp").delete()
        prefs.edit().clear().apply()
    }
}

/**
 * One-time move of data saved under the app's earlier internal names, so people who already
 * used the app keep their ledger, PIN and settings. Safe to delete if no earlier build was ever released.
 */
fun migrateLegacyStorage(context: Context) {
    val oldData = context.filesDir.resolve("baki_data.json")
    val newData = context.filesDir.resolve("hishab_data.json")
    if (oldData.exists() && !newData.exists()) oldData.renameTo(newData)
    listOf("baki_security" to "hishab_security", "baki_settings" to "hishab_settings").forEach { (old, new) ->
        val o = context.getSharedPreferences(old, Context.MODE_PRIVATE)
        if (o.all.isEmpty()) return@forEach
        val n = context.getSharedPreferences(new, Context.MODE_PRIVATE)
        if (n.all.isEmpty()) {
            val e = n.edit()
            o.all.forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is String -> e.putString(k, v)
                }
            }
            e.commit()
        }
        o.edit().clear().commit()
    }
}
