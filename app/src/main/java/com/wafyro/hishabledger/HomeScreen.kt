package com.wafyro.hishabledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HishabViewModel, open: (Shop) -> Unit, add: () -> Unit, settings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("name") }
    var showSort by remember { mutableStateOf(false) }
    val isBn = AppLanguage.isBangla
    val lastBackupAt by remember { mutableLongStateOf(vm.lastBackup()) }
    val backupReminder = lastBackupAt == 0L || System.currentTimeMillis() - lastBackupAt >= 7L * 24 * 60 * 60 * 1000
    // Two accounts with the same name: show phone / address under the name so they can be told apart.
    val duplicateNames = remember(vm.shops) { vm.activeShops.groupBy { it.name.trim().lowercase() }.filter { it.value.size > 1 }.keys }
    val list = vm.activeShops.filter { it.name.contains(query, true) || it.phone.contains(query, true) }.let { matches ->
        when (sortMode) {
            "due" -> matches.sortedByDescending { vm.balance(it.id) }
            "recent" -> matches.sortedByDescending { s -> vm.lastActivity(s.id).takeIf { it > 0L } ?: s.createdAt }
            else -> matches.sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = hishabTopBarColors(), 
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
                                Icon(Icons.Outlined.Home, contentDescription = if (isBn) "হোম" else "Home", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                                Text(if (isBn) "হোম" else "Home", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(58.dp))
                        TextButton(onClick = settings, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Settings, contentDescription = if (isBn) "সেটিংস" else "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
                                Text(if (isBn) "সেটিংস" else "Settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                FloatingActionButton(onClick = add, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(50), elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp), modifier = Modifier.align(Alignment.TopCenter).offset(y = (-7).dp).size(58.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = if (isBn) "নতুন হিসাব" else "Add account", modifier = Modifier.size(30.dp))
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
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = if (isBn) "খুঁজুন" else "Search") },
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
                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (isBn) "আপনার হিসাবের ব্যাকআপ নিন" else "Keep your ledger backed up", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isBn) { if (lastBackupAt == 0L) "এখনও ব্যাকআপ নেওয়া হয়নি। সংরক্ষণ করতে ট্যাপ করুন।" else "৭ দিনের বেশি সময় ধরে ব্যাকআপ নেওয়া হয়নি।" } else { if (lastBackupAt == 0L) "No backup yet. Tap to save a copy." else "It has been over 7 days since your last backup." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(if (isBn) "মোট দেনা" else "Total Payable", money(vm.totalPayable()), Modifier.weight(1f), Icons.Outlined.AccountBalanceWallet, Color(0xFFE6F5ED), Color(0xFF1E3A2E))
                SummaryCard(if (isBn) "মোট হিসাব" else "Total Accounts", localizedNumber(vm.activeShops.size), Modifier.weight(1f), Icons.Outlined.People, Color(0xFFE8F2FF), Color(0xFF1C2E44))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (isBn) "আপনার দোকান ও ব্যক্তির হিসাব" else "Your Shops & People", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isBn) when (sortMode) { "due" -> "বেশি বাকি আগে"; "recent" -> "সাম্প্রতিক আগে"; else -> "নাম অনুযায়ী" } else when (sortMode) { "due" -> "Highest due"; "recent" -> "Recent activity"; else -> "Name" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { showSort = true }) { Icon(Icons.Outlined.Tune, contentDescription = if (isBn) "সর্ট ও ফিল্টার" else "Sort and filter", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
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
                    val lastAt = vm.lastActivity(s.id)
                    Card(
                        Modifier.fillMaxWidth().clickable { open(s) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (s.type == PartyType.PERSON) Icons.Outlined.Person else Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                if (s.name.trim().lowercase() in duplicateNames) {
                                    val extra = listOf(s.phone, s.address).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (extra.isNotBlank()) Text(extra, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (isBn) "বর্তমান বাকি" else "Current Due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(balance), fontWeight = FontWeight.Bold, color = if (balance > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                                if (lastAt > 0L) Text((if (isBn) "সর্বশেষ এন্ট্রি • " else "Last activity • ") + relativeDate(lastAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = if (isBn) "খুলুন" else "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
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
                            if (sortMode == key) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
