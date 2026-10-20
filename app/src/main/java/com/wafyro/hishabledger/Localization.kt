package com.wafyro.hishabledger

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf

object AppLanguage {
    var isBangla by mutableStateOf(false)
    fun load(context: Context) { isBangla = context.getSharedPreferences("hishab_language", Context.MODE_PRIVATE).getString("language", "en") == "bn" }
    fun set(context: Context, bangla: Boolean) { isBangla = bangla; context.getSharedPreferences("hishab_language", Context.MODE_PRIVATE).edit().putString("language", if (bangla) "bn" else "en").apply() }
}

/** Picks the English or the Bangla text, following the language chosen in Settings. */
fun tr(en: String, bn: String): String = if (AppLanguage.isBangla) bn else en
