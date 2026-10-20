package com.wafyro.hishabledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    vm: HishabViewModel, shop: Shop, back: () -> Unit, onDeleted: (Tx) -> Unit, toast: (String) -> Unit,
    onEdit: () -> Unit = {}, quickAdd: TxType? = null, onQuickAddHandled: () -> Unit = {}
) {
    var addDialog by remember { mutableStateOf<TxType?>(null) }
    // Opened from the launcher shortcut: jump straight into the Add Due / Add Payment form.
    LaunchedEffect(quickAdd) { if (quickAdd != null) { addDialog = quickAdd; onQuickAddHandled() } }
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
    val history = vm.activeTxs(shop.id).sortedWith(compareByDescending<Tx> { it.transactionDate }.thenByDescending { it.timestamp })
    val latestDueId = remember(history) { history.filter { it.type == TxType.DUE }.maxByOrNull { it.transactionDate }?.id }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(colors = hishabTopBarColors(), title = { Text(shop.name, maxLines = 1) }, navigationIcon = { IconButton(back) { Icon(Icons.Outlined.ArrowBack, "Back") } }, actions = { if (shop.phone.isNotBlank()) IconButton({ showPhoneActions = true }) { Icon(Icons.Outlined.Phone, "Phone") }; IconButton(onEdit) { Icon(Icons.Outlined.Edit, if (AppLanguage.isBangla) "এডিট" else "Edit") }; IconButton({ deleteShop = true }) { Icon(Icons.Outlined.Delete, "Delete") } }, windowInsets = WindowInsets(0, 0, 0, 0)) } ) { p ->
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
                    AssistChip(onClick = { shareStatement = true }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) })
                    AssistChip(onClick = { csvLauncher.launch("HishabLedger_${shop.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.csv") }, label = { Text("CSV") }, leadingIcon = { Icon(Icons.Outlined.Download, null) })
                    AssistChip(onClick = { pdfLauncher.launch("HishabLedger_${shop.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_Statement.pdf") }, label = { Text("PDF") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) })
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
                var currentMonth = ""
                history.forEach { t ->
                    // A small month heading whenever the month changes, so long histories are easy to scan.
                    val month = formatMonth(t.transactionDate)
                    if (month != currentMonth) {
                        currentMonth = month
                        item(key = "month_$month") {
                            Text(month, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                    }
                    item(key = t.id) {
                        val positive = t.type != TxType.PAYMENT
                        val label = when {
                            t.type == TxType.PAYMENT -> tr("Payment", "পেমেন্ট")
                            t.type == TxType.OPENING_BALANCE -> tr("Previous Due", "আগের বাকি")
                            t.id == latestDueId -> tr("New Due", "নতুন বাকি")
                            else -> tr("Due", "বাকি")
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    // The date the person chose for the entry; the exact time it was typed in is shown smaller below.
                                    Text(formatDay(t.transactionDate), style = MaterialTheme.typography.bodyMedium)
                                    Text(tr("Entered: ", "এন্ট্রি: ") + formatFull(t.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${if (positive) "+" else "-"}${money(t.amount)}", color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                IconButton({ deleteTx = t }) { Icon(Icons.Outlined.Delete, "Delete transaction", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    addDialog?.let { type -> AddTxDialog(type, { addDialog = null }) { amount, note, transactionDate -> vm.addTx(shop.id, amount, type, note, transactionDate); addDialog = null; toast(if (type == TxType.PAYMENT) "Payment added" else "Due added") } }
    deleteTx?.let { t -> AlertDialog(onDismissRequest = { deleteTx = null }, title = { Text("Delete ${if (t.type == TxType.PAYMENT) "payment" else "due"}?") }, text = { Text("This moves it to Recently Deleted for 30 days before it's gone for good. You can restore it anytime before that from Settings.") }, confirmButton = { TextButton({ val deleted = vm.softDeleteTx(t.id); deleteTx = null; deleted?.let(onDeleted) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleteTx = null }) { Text("Cancel") } }) }
    if (deleteShop) AlertDialog(onDismissRequest = { deleteShop = false }, title = { Text("Delete ${shop.name}?") }, text = { Text("This moves it to Recently Deleted for 30 days, after which it's permanently removed. You can restore it anytime before that from Settings.") }, confirmButton = { TextButton({ vm.softDeleteShop(shop.id); deleteShop = false; back(); toast("Moved to Recently Deleted") }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleteShop = false }) { Text("Cancel") } })
    if (showSummary) SummaryDialog(vm, shop, history) { showSummary = false }
    if (shareStatement) { ShareStatementDialog(vm, shop) { shareStatement = false } }
    if (showPhoneActions) AlertDialog(onDismissRequest = { showPhoneActions = false }, title = { Text(shop.phone) }, text = { Text("Call or copy this number.") }, confirmButton = { TextButton({ context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(shop.phone)}"))); showPhoneActions = false }) { Text("Call") } }, dismissButton = { TextButton({ val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clip.setPrimaryClip(android.content.ClipData.newPlainText("Phone", shop.phone)); showPhoneActions = false }) { Text("Copy") } })
}

@Composable
fun SummaryDialog(vm: HishabViewModel, shop: Shop, history: List<Tx>, close: () -> Unit) {
    val today = history.filter { isToday(it.transactionDate) }
    val due = today.filter { it.type != TxType.PAYMENT }.sumOf { it.amount }
    val paid = today.filter { it.type == TxType.PAYMENT }.sumOf { it.amount }
    AlertDialog(onDismissRequest = close, title = { Text("Today") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(shop.name, fontWeight = FontWeight.Bold); Text("Added Due: ${money(due)}"); Text("Payments: ${money(paid)}"); Text("Net Change: ${money(due - paid)}"); Text("Current Due: ${money(vm.balance(shop.id))}") } }, confirmButton = { TextButton(close) { Text("Close") } })
}
