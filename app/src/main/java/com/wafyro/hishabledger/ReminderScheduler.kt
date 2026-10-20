package com.wafyro.hishabledger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules and cancels the local (no-internet-needed) notification that reminds the user
 * about a "Due" entry's repayment date. Uses an inexact alarm so no special permission
 * beyond POST_NOTIFICATIONS (requested at the point of use) is required.
 */
object ReminderScheduler {

    private fun pendingIntent(context: Context, txId: String, shopName: String, amount: Long): PendingIntent {
        val intent = Intent(context, DueReminderReceiver::class.java).apply {
            putExtra("txId", txId)
            putExtra("shopName", shopName)
            putExtra("amount", amount)
        }
        return PendingIntent.getBroadcast(
            context,
            txId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, txId: String, shopName: String, amount: Long, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, txId, shopName, amount))
        }
    }

    fun cancel(context: Context, txId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pendingIntent(context, txId, "", 0)) }
    }
}
