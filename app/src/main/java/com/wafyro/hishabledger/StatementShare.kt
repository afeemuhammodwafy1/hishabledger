package com.wafyro.hishabledger

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import android.widget.Toast

/** Plain-text statement: the totals plus the newest entries, ready to paste into WhatsApp or SMS. */
fun buildStatementText(vm: HishabViewModel, shop: Shop, maxEntries: Int = 15): String {
    val all = vm.activeTxs(shop.id).sortedWith(compareByDescending<Tx> { it.transactionDate }.thenByDescending { it.timestamp })
    val recent = all.take(maxEntries)
    return buildString {
        append(shop.name).append('\n')
        append(tr("Current Due: ", "বর্তমান বাকি: ")).append(money(vm.balance(shop.id))).append('\n')
        append(tr("Total Due Added: ", "মোট বাকি যোগ: ")).append(money(vm.totalDueAdded(shop.id))).append('\n')
        append(tr("Total Paid: ", "মোট পরিশোধ: ")).append(money(vm.totalPaid(shop.id)))
        if (recent.isNotEmpty()) {
            append("\n\n").append(tr("Recent entries:", "সর্বশেষ লেনদেন:")).append('\n')
            recent.forEach { t ->
                val kind = when (t.type) {
                    TxType.PAYMENT -> tr("Payment", "পেমেন্ট")
                    TxType.OPENING_BALANCE -> tr("Previous Due", "আগের বাকি")
                    TxType.DUE -> tr("Due", "বাকি")
                }
                val sign = if (t.type == TxType.PAYMENT) "-" else "+"
                append(formatDay(t.transactionDate)).append(" • ").append(kind).append(" • ").append(sign).append(money(t.amount))
                if (t.note.isNotBlank()) append(" • ").append(t.note.replace('\n', ' ').trim().take(40))
                append('\n')
            }
            val more = all.size - recent.size
            if (more > 0) append(tr("…and $more earlier entries (full list in the PDF statement)", "…আরও ${localizedNumber(more)}টি আগের লেনদেন (পুরো হিসাব PDF স্টেটমেন্টে)")).append('\n')
        }
        append("\n— Hishab Ledger")
    }.trimEnd()
}

/** Builds the full PDF statement in the app's private cache and hands it to the share sheet. */
fun shareStatementPdf(context: Context, vm: HishabViewModel, shop: Shop) {
    val dir = context.cacheDir.resolve("statements").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val safeName = shop.name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifBlank { "Account" }
    val file = dir.resolve("HishabLedger_${safeName}_Statement.pdf")
    file.writeBytes(buildShopPdf(vm, shop))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${shop.name} statement")
        clipData = android.content.ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, tr("Share PDF statement", "PDF স্টেটমেন্ট শেয়ার করুন")))
}

@Composable
fun ShareStatementDialog(vm: HishabViewModel, shop: Shop, close: () -> Unit) {
    val context = LocalContext.current
    val text = buildStatementText(vm, shop)
    AlertDialog(
        onDismissRequest = close,
        title = { Text(tr("Share Statement", "স্টেটমেন্ট শেয়ার")) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({
                    runCatching { shareStatementPdf(context, vm, shop) }
                        .onFailure { Toast.makeText(context, tr("Could not create the PDF", "PDF তৈরি করা যায়নি"), Toast.LENGTH_SHORT).show() }
                    close()
                }) { Text("PDF") }
                TextButton({
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "${shop.name} statement"); putExtra(Intent.EXTRA_TEXT, text)
                    }, tr("Share with", "শেয়ার করুন")))
                    close()
                }) { Text(tr("Share text", "টেক্সট শেয়ার")) }
            }
        },
        dismissButton = { TextButton(close) { Text(tr("Cancel", "বাতিল")) } }
    )
}
