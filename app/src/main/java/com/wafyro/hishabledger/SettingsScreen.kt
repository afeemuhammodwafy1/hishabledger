package com.wafyro.hishabledger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.OutputStreamWriter
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: HishabViewModel, back: () -> Unit, toast: (String) -> Unit, openRecentlyDeleted: () -> Unit, openGuide: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("main") }
    var importPreview by remember { mutableStateOf<String?>(null) }
    var importJson by remember { mutableStateOf<String?>(null) }
    var lastBackup by remember { mutableLongStateOf(vm.lastBackup()) }
    var lockEnabled by remember { mutableStateOf(SecurityStore.isLockEnabled(context)) }
    var showSetPin by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }
    val bn = AppLanguage.isBangla

    BackHandler(enabled = page != "main") { page = "main" }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open backup destination"); output.use { it.write(vm.backup().toByteArray(Charsets.UTF_8)) }; vm.markBackup(); lastBackup = vm.lastBackup(); toast(if (AppLanguage.isBangla) "ব্যাকআপ এক্সপোর্ট হয়েছে" else "Backup exported") }
            .onFailure { toast(if (AppLanguage.isBangla) "ব্যাকআপ এক্সপোর্ট করা যায়নি" else "Could not export backup") }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(vm.csv()) } }; toast(if (AppLanguage.isBangla) "CSV এক্সপোর্ট হয়েছে" else "CSV exported") }
            .onFailure { toast(if (AppLanguage.isBangla) "CSV এক্সপোর্ট করা যায়নি" else "Could not export CSV") }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { val json = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }; val (shops, txs) = LocalStore(context).validateAndDecode(json); val backupAt = JSONObject(json).optLong("backupCreatedAt", 0L); importJson = json; importPreview = "Shops: ${shops.size}\nTransactions: ${txs.size}\nBackup date: ${if (backupAt > 0) formatFull(backupAt) else "Unknown"}" }
            .onFailure { toast(if (AppLanguage.isBangla) "সঠিক Hishab Ledger ব্যাকআপ নয়" else "Invalid Hishab Ledger backup") }
    }

    val title = when (page) {
        "language" -> if (bn) "ভাষা" else "Language"
        "appearance" -> if (bn) "থিম ও চেহারা" else "Appearance & theme"
        "backup" -> "Backup & Data"
        "lock" -> "App Lock"
        "about" -> "About"
        else -> if (bn) "সেটিংস" else "Settings"
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = hishabTopBarColors(), title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
            IconButton(onClick = { if (page == "main") back() else page = "main" }) { Icon(Icons.Outlined.ArrowBack, contentDescription = if (bn) "ফিরে যান" else "Back") }
        }, windowInsets = WindowInsets(0, 0, 0, 0))
    }) { padding ->
        when (page) {
            "main" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "পছন্দ ও অ্যাপ ব্যবস্থাপনা" else "Preferences & app management", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
                SettingsOptionCard(Icons.Outlined.Language, if (bn) "ভাষা" else "Language", if (bn) "অ্যাপের ভাষা বেছে নিন" else "Choose your app language", { page = "language" })
                SettingsOptionCard(Icons.Outlined.DarkMode, if (bn) "থিম ও চেহারা" else "Appearance & theme", if (bn) "Light, Dark অথবা ফোনের থিম" else "Light, Dark or follow system", { page = "appearance" })
                SettingsOptionCard(Icons.Outlined.Lightbulb, if (bn) "ব্যবহার গাইড" else "How to use", if (bn) "শুরু করার ছোট নির্দেশিকা আবার দেখুন" else "Watch the quick start guide again", { openGuide() })
                SettingsOptionCard(Icons.Outlined.CloudSync, "Backup & Data", if (bn) "ব্যাকআপ, রিস্টোর ও CSV ফাইল" else "Backup, restore and CSV files", { page = "backup" })
                SettingsOptionCard(Icons.Outlined.Lock, "App Lock", if (bn) "PIN ও বায়োমেট্রিক নিরাপত্তা" else "PIN and biometric security", { page = "lock" })
                SettingsOptionCard(Icons.Outlined.RestoreFromTrash, if (bn) "সম্প্রতি মুছে ফেলা" else "Recently Deleted", if (bn) "মুছে ফেলা হিসাব পুনরুদ্ধার করুন" else "Restore deleted accounts and entries", { openRecentlyDeleted() }, "${vm.deletedShops.size + vm.deletedTxs().size}")
                SettingsOptionCard(Icons.Outlined.Info, if (bn) "পরিচিতি" else "About", if (bn) "অ্যাপ ও নির্মাতা সম্পর্কে" else "App and developer information", { page = "about" })
            }
            "language" -> Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "অ্যাপের ভাষা" else "App language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsChoiceRow("English", "English", selected = !AppLanguage.isBangla) { AppLanguage.set(context, false) }
                SettingsChoiceRow("বাংলা", "বাংলা", selected = AppLanguage.isBangla) { AppLanguage.set(context, true) }
                Text(if (bn) "ভাষা বদলালে সেটিংসের নামও সেই ভাষায় দেখাবে।" else "Settings labels follow the language you select.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "appearance" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bn) "আপনার পছন্দের থিম বেছে নিন" else "Choose your preferred theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (bn) "অ্যাপের সব স্ক্রিনে এই পছন্দ প্রয়োগ হবে।" else "Your choice applies across the app and is saved on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsChoiceRow(if (bn) "হালকা থিম" else "Light", if (bn) "সাদা ব্যাকগ্রাউন্ড ও সবুজ অ্যাকসেন্ট" else "Bright surfaces and green accents", selected = AppThemePreference.mode == "light") { AppThemePreference.set(context, "light") }
                SettingsChoiceRow(if (bn) "ডার্ক থিম" else "Dark", if (bn) "গাঢ় ব্যাকগ্রাউন্ড ও চোখে আরামদায়ক রং" else "Charcoal surfaces with soft emerald accents", selected = AppThemePreference.mode == "dark") { AppThemePreference.set(context, "dark") }
                SettingsChoiceRow(if (bn) "ফোনের থিম" else "System default", if (bn) "ফোনের Light/Dark সেটিং অনুসরণ করবে" else "Follow your phone's appearance setting", selected = AppThemePreference.mode == "system") { AppThemePreference.set(context, "system") }
            }
            "backup" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DriveBackupCard(bn) {
                    runCatching {
                        val openedDrive = shareBackupToDrive(context, vm.backup())
                        vm.markBackup(); lastBackup = vm.lastBackup()
                        if (!openedDrive) toast(if (bn) "Google Drive অ্যাপ পাওয়া যায়নি। অন্য অ্যাপ বেছে নিন।" else "Google Drive app not found. Choose another app.")
                    }.onFailure { toast(if (bn) "ব্যাকআপ তৈরি করা যায়নি" else "Could not create backup") }
                }
                SettingsOptionCard(Icons.Outlined.Download, if (bn) "ব্যাকআপ এক্সপোর্ট" else "Export Backup", if (bn) "সব হিসাব JSON ফাইলে সংরক্ষণ করুন" else "Save your ledger to a JSON backup file", { createJson.launch("HishabLedger_Backup.json") })
                SettingsOptionCard(Icons.Outlined.Upload, if (bn) "ব্যাকআপ ইমপোর্ট" else "Import Backup", if (bn) "আগের ব্যাকআপ থেকে হিসাব ফিরিয়ে আনুন" else "Restore data from an existing backup", { openJson.launch(arrayOf("application/json", "text/plain")) })
                SettingsOptionCard(Icons.Outlined.TableChart, if (bn) "CSV এক্সপোর্ট" else "Export CSV", if (bn) "স্প্রেডশিটে ব্যবহারের জন্য এন্ট্রি এক্সপোর্ট" else "Export transactions for spreadsheet apps", { createCsv.launch("HishabLedger_Transactions.csv") })
                if (lastBackup > 0) Text((if (bn) "সর্বশেষ ব্যাকআপ: " else "Last backup: ") + formatFull(lastBackup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            "lock" -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsIcon(Icons.Outlined.Lock)
                        Column(Modifier.weight(1f)) {
                            Text(if (bn) "PIN দিয়ে অ্যাপ লক" else "Require PIN to open app", fontWeight = FontWeight.SemiBold)
                            Text(if (lockEnabled) { if (bn) "অ্যাপ লক চালু আছে" else "App lock is enabled" } else { if (bn) "অ্যাপ লক বন্ধ আছে" else "Off by default; enable it anytime" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockEnabled, onCheckedChange = { checked -> if (checked) showSetPin = true else showDisableConfirm = true })
                    }
                }
                if (lockEnabled && SecurityStore.canUseBiometric(context)) {
                    var bioEnabled by remember { mutableStateOf(SecurityStore.isBiometricEnabled(context)) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(Icons.Outlined.Fingerprint)
                            Column(Modifier.weight(1f)) { Text(if (bn) "ফিঙ্গারপ্রিন্ট/ফেস আনলক" else "Fingerprint / face unlock", fontWeight = FontWeight.SemiBold); Text(if (bn) "সমর্থিত হলে ব্যবহার করুন" else "Allow biometric unlock when available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = bioEnabled, onCheckedChange = { bioEnabled = it; SecurityStore.setBiometricEnabled(context, it) })
                        }
                    }
                }
                if (lockEnabled) {
                    var timeoutMs by remember { mutableLongStateOf(SecurityStore.lockTimeoutMs(context)) }
                    Text(tr("Ask for the PIN again after", "কতক্ষণ পরে আবার PIN চাইবে"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    listOf(
                        0L to tr("Immediately", "সাথে সাথে"),
                        30_000L to tr("30 seconds", "৩০ সেকেন্ড"),
                        60_000L to tr("1 minute", "১ মিনিট"),
                        300_000L to tr("5 minutes", "৫ মিনিট")
                    ).forEach { (ms, label) ->
                        SettingsChoiceRow(label, label, selected = timeoutMs == ms) { timeoutMs = ms; SecurityStore.setLockTimeoutMs(context, ms) }
                    }
                    Text(
                        tr("Picking a contact or a file leaves the app for a moment, so \"Immediately\" can lock the app during that.", "কন্টাক্ট বা ফাইল বাছাইয়ের সময় অ্যাপ কিছুক্ষণের জন্য বাইরে যায়, তাই \"সাথে সাথে\" বেছে নিলে তখনও অ্যাপ লক হয়ে যেতে পারে।"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lockEnabled) OutlinedButton(onClick = { showSetPin = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Password, null); Spacer(Modifier.width(8.dp)); Text(if (bn) "PIN পরিবর্তন করুন" else "Change PIN") }
            }
            "about" -> Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(26.dp))
                Image(painterResource(R.drawable.hishab_icon), contentDescription = "Hishab Ledger", modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                Text("Hishab Ledger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Text("Designed & developed by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CREATOR, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Version $APP_VERSION", style = MaterialTheme.typography.bodyMedium)
                Text(if (bn) "অফলাইন • লোকাল ডেটা • অ্যাকাউন্ট প্রয়োজন নেই" else "Offline • Local data • No account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { if (!openFeedbackPage(context)) toast(if (bn) "ব্রাউজার খোলা যায়নি" else "Could not open the browser") }) {
                    Icon(Icons.Outlined.Feedback, null); Spacer(Modifier.width(8.dp)); Text(if (bn) "মতামত পাঠান" else "Feedback")
                }
            }
            else -> Spacer(Modifier.padding(padding))
        }
    }

    if (importPreview != null && importJson != null) AlertDialog(onDismissRequest = { importPreview = null; importJson = null }, title = { Text(if (bn) "ব্যাকআপ রিস্টোর" else "Restore Backup") }, text = { Text("$importPreview\n\n${if (bn) "কীভাবে রিস্টোর করবেন বেছে নিন।" else "Choose how to restore this backup."}") }, confirmButton = { TextButton({ runCatching { vm.restore(importJson!!, true); toast(if (bn) "ব্যাকআপ রিস্টোর হয়েছে" else "Backup restored") }.onFailure { toast(if (bn) "রিস্টোর ব্যর্থ হয়েছে" else "Restore failed") }; importPreview = null; importJson = null }) { Text("Replace") } }, dismissButton = { Row { TextButton({ runCatching { vm.restore(importJson!!, false); toast(if (bn) "ব্যাকআপ মার্জ হয়েছে" else "Backup merged") }.onFailure { toast(if (bn) "মার্জ ব্যর্থ হয়েছে" else "Merge failed") }; importPreview = null; importJson = null }) { Text("Merge") }; TextButton({ importPreview = null; importJson = null }) { Text(if (bn) "বাতিল" else "Cancel") } } })

    if (showSetPin) SetPinDialog(
        onDismiss = { showSetPin = false },
        onConfirm = { newPin ->
            SecurityStore.setPin(context, newPin); lockEnabled = true; showSetPin = false
            toast(if (bn) "অ্যাপ লক চালু হয়েছে" else "App lock enabled")
        }
    )
    if (showDisableConfirm) AlertDialog(onDismissRequest = { showDisableConfirm = false }, title = { Text(if (bn) "অ্যাপ লক বন্ধ করবেন?" else "Turn off app lock?") }, text = { Text(if (bn) "PIN ছাড়াই যে কেউ আপনার হিসাব দেখতে পারবে।" else "Anyone who opens Hishab Ledger will be able to see your data without a PIN.") }, confirmButton = { TextButton({ SecurityStore.disableLock(context); lockEnabled = false; showDisableConfirm = false; toast(if (bn) "অ্যাপ লক বন্ধ হয়েছে" else "App lock disabled") }) { Text(if (bn) "বন্ধ করুন" else "Turn off", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ showDisableConfirm = false }) { Text(if (bn) "বাতিল" else "Cancel") } })
}

@Composable
private fun DriveBackupCard(bn: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIcon(Icons.Outlined.CloudUpload)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (bn) "Google Drive-এ ব্যাকআপ" else "Backup to Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                    Text(if (bn) "প্রস্তাবিত" else "Recommended", modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text(if (bn) "Drive খুলবে। ফোল্ডার বেছে \"Save\" চাপলেই আপলোড শেষ।" else "Opens Google Drive. Pick a folder and tap Save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)) }
    }
}

@Composable
private fun SettingsOptionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: String? = null) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (title != subtitle) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
