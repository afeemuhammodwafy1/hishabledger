package com.wafyro.hishabledger

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf

/** Receives the launcher shortcuts (long-press on the app icon) and remembers which entry type was asked for. */
object ShortcutRouter {
    const val ACTION_ADD_DUE = "com.wafyro.hishabledger.action.ADD_DUE"
    const val ACTION_ADD_PAYMENT = "com.wafyro.hishabledger.action.ADD_PAYMENT"
    var pending by mutableStateOf<TxType?>(null)
    fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_DUE -> pending = TxType.DUE
            ACTION_ADD_PAYMENT -> pending = TxType.PAYMENT
        }
    }
}
