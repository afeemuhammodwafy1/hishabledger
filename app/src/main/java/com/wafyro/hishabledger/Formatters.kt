package com.wafyro.hishabledger

import java.text.SimpleDateFormat
import java.util.*

/** Formats a timestamp as a complete local date and time for history and backup screens. */
fun formatFull(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

/** Returns a compact relative label for activity timestamps. */
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

/** Formats a Taka amount using Bangladeshi lakh/crore digit grouping, e.g. 1234567 -> ৳12,34,567 */
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

fun localizedNumber(value: Int): String {
    val digits = value.toString()
    return if (AppLanguage.isBangla) digits.map { ch -> if (ch in '0'..'9') ('০'.code + (ch - '0')).toChar() else ch }.joinToString("") else digits
}

private fun appLocale(): Locale = if (AppLanguage.isBangla) Locale("bn", "BD") else Locale.getDefault()

/** The date a transaction belongs to, e.g. "20 Oct 2026" (Bangla digits and month names in Bangla mode). */
fun formatDay(timestamp: Long): String = SimpleDateFormat("dd MMM yyyy", appLocale()).format(Date(timestamp))

/** Month heading used to group the entry list, e.g. "October 2026". */
fun formatMonth(timestamp: Long): String = SimpleDateFormat("MMMM yyyy", appLocale()).format(Date(timestamp))
