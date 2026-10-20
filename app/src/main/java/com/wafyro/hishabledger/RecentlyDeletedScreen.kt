package com.wafyro.hishabledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(vm: HishabViewModel, back: () -> Unit, toast: (String) -> Unit) {
    val deletedShops = vm.deletedShops
    val deletedTxs = vm.deletedTxs()
    var showClearAll by remember { mutableStateOf(false) }
    if (showClearAll) DeleteAllConfirmDialog(
        count = deletedShops.size + deletedTxs.size,
        onConfirm = { vm.purgeAllDeleted(); showClearAll = false; toast(if (AppLanguage.isBangla) "সব স্থায়ীভাবে মুছে ফেলা হয়েছে" else "Everything permanently deleted") },
        onDismiss = { showClearAll = false }
    )
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(colors = hishabTopBarColors(), title = { Text(if (AppLanguage.isBangla) "সম্প্রতি মুছে ফেলা" else "Recently Deleted") }, navigationIcon = { IconButton(back) { Icon(Icons.Outlined.ArrowBack, "Back") } }, actions = {
        if (deletedShops.isNotEmpty() || deletedTxs.isNotEmpty()) TextButton({ showClearAll = true }) {
            Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp))
            Text(if (AppLanguage.isBangla) "সব মুছুন" else "Delete all", color = MaterialTheme.colorScheme.error)
        }
    }, windowInsets = WindowInsets(0, 0, 0, 0)) }) { p ->
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
                                IconButton({ vm.purgeShopForever(s.id); toast("Permanently deleted") }) { Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
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
                                IconButton({ vm.purgeTxForever(t.id); toast("Permanently deleted") }) { Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}
