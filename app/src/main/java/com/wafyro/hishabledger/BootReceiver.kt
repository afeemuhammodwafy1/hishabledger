package com.wafyro.hishabledger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are cleared on reboot; this puts still-pending due-date reminders back on the clock. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val store = LocalStore(appContext)
        val (shops, txs) = store.read()
        val shopNames = shops.filter { it.deletedAt == 0L }.associate { it.id to it.name }
        val now = System.currentTimeMillis()
        txs.filter { it.deletedAt == 0L && it.type == TxType.DUE && it.reminderAt > now }
            .forEach { tx ->
                val shopName = shopNames[tx.shopId] ?: return@forEach
                ReminderScheduler.schedule(appContext, tx.id, shopName, tx.amount, tx.reminderAt)
            }
    }
}
