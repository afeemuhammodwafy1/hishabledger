package com.wafyro.hishabledger

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Shown right after the system splash: app icon, name and maker, in the active theme (no black frame). */
@Composable
fun SplashContent() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(550)) + scaleIn(initialScale = 0.86f, animationSpec = tween(650))
        ) {
            Image(
                painterResource(R.drawable.hishab_icon),
                contentDescription = "Hishab Ledger",
                modifier = Modifier.size(148.dp).clip(RoundedCornerShape(32.dp))
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            enter = fadeIn(tween(650, delayMillis = 180)) + slideInVertically(initialOffsetY = { it / 5 }, animationSpec = tween(650, delayMillis = 180))
        ) {
            Text("A Product of Wafyro Software", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HishabApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLanguage.load(context) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (showSplash) { delay(1400); showSplash = false } }
    if (showSplash) { SplashContent(); return }

    // Created before the lock check so "Forgot PIN" can reach the data. Nothing from it is shown while locked.
    val vm: HishabViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(c: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HishabViewModel(context.applicationContext) as T
        }
    })

    if (LockGate.locked) {
        LockScreen(
            onUnlocked = { LockGate.unlock() },
            onResetData = { vm.wipeAllData(); SecurityStore.disableLock(context); LockGate.unlock() }
        )
        return
    }

    var shop by remember { mutableStateOf<Shop?>(null) }
    var settings by remember { mutableStateOf(false) }
    var recentlyDeleted by remember { mutableStateOf(false) }
    var addShop by remember { mutableStateOf(false) }
    var undoTx by remember { mutableStateOf<Tx?>(null) }
    var editShop by remember { mutableStateOf<Shop?>(null) }
    var quickAdd by remember { mutableStateOf<TxType?>(null) }
    var showGuide by remember { mutableStateOf(!OnboardingStore.isSeen(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackState.showSnackbar(it); message = null }
    }
    // Saving runs in the background; if it ever fails the person must hear about it.
    LaunchedEffect(vm.saveFailed) {
        if (vm.saveFailed) {
            message = tr("Could not save your data. Free up some phone storage and try again.", "ডেটা সংরক্ষণ করা যায়নি। ফোনে জায়গা খালি করে আবার চেষ্টা করুন।")
            vm.saveFailed = false
        }
    }

    BackHandler(enabled = editShop != null || addShop || recentlyDeleted || settings || shop != null) {
        when {
            editShop != null -> editShop = null
            addShop -> addShop = false
            recentlyDeleted -> recentlyDeleted = false
            settings -> settings = false
            else -> shop = null
        }
    }

    if (showGuide) {
        OnboardingScreen(onFinish = { OnboardingStore.markSeen(context); showGuide = false })
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackState) }) { outer ->
        // AddShopDialog owns its own Scaffold and system-bar handling. Do not
        // apply the parent Scaffold insets to it a second time, or its content
        // is pushed down compared with the other screens.
        Box(if (addShop || editShop != null) Modifier else Modifier.padding(outer)) {
            val destination = when {
                recentlyDeleted -> "recently_deleted"
                settings -> "settings"
                shop != null -> "shop:${shop!!.id}"
                else -> "home"
            }
            Crossfade(targetState = destination, animationSpec = tween(durationMillis = 120), label = "main_page_transition") { target ->
                when {
                    target == "recently_deleted" -> RecentlyDeletedScreen(vm, { recentlyDeleted = false }, { message = it })
                    target == "settings" -> SettingsScreen(vm, { settings = false }, { message = it }, { recentlyDeleted = true }, { showGuide = true })
                    target.startsWith("shop:") && shop != null -> ShopScreen(
                        vm, vm.shops.firstOrNull { it.id == shop!!.id } ?: shop!!, { shop = null }, { deleted -> undoTx = deleted }, { message = it },
                        onEdit = { editShop = vm.shops.firstOrNull { it.id == shop!!.id } ?: shop },
                        quickAdd = quickAdd, onQuickAddHandled = { quickAdd = null }
                    )
                    else -> HomeScreen(vm, { shop = it }, { addShop = true }, { settings = true })
                }
            }
        }
    }

    if (undoTx != null) {
        LaunchedEffect(undoTx) {
            val deleted = undoTx
            if (deleted != null) {
                val result = snackState.showSnackbar(tr("Transaction deleted", "লেনদেন মুছে ফেলা হয়েছে"), tr("Undo", "ফিরিয়ে আনুন"), duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) {
                    vm.restoreTx(deleted.id)
                    snackState.showSnackbar(tr("Transaction restored", "লেনদেন ফিরে এসেছে"), duration = SnackbarDuration.Short)
                }
            }
            undoTx = null
        }
    }

    val pendingQuick = ShortcutRouter.pending
    LaunchedEffect(pendingQuick) {
        if (pendingQuick != null && vm.activeShops.isEmpty()) {
            message = tr("Create an account first", "আগে একটি হিসাব খুলুন")
            ShortcutRouter.pending = null
        }
    }
    if (pendingQuick != null && vm.activeShops.isNotEmpty() && !addShop && editShop == null) {
        QuickAccountPicker(vm, pendingQuick, onDismiss = { ShortcutRouter.pending = null }, onPick = { picked ->
            ShortcutRouter.pending = null; settings = false; recentlyDeleted = false; shop = picked; quickAdd = pendingQuick
        })
    }

    editShop?.let { target ->
        AddShopDialog(
            onDismiss = { editShop = null },
            editing = target,
            similar = { n, p -> vm.findSimilar(n, p, excludeId = target.id) },
            balanceOf = { vm.balance(it) },
            onOpenExisting = { picked -> editShop = null; shop = picked },
            onSave = { n, p, a, _, t ->
                vm.updateShop(target.id, n, p, a, t); editShop = null
                message = tr("Account updated", "হিসাব আপডেট হয়েছে")
            }
        )
    }

    if (addShop) AddShopDialog(
        onDismiss = { addShop = false },
        similar = { n, p -> vm.findSimilar(n, p) },
        balanceOf = { vm.balance(it) },
        onOpenExisting = { picked -> addShop = false; shop = picked },
        onSave = { n, p, a, o, t -> vm.addShop(n, p, a, o, t); addShop = false; message = tr("Account added", "হিসাব যোগ হয়েছে") }
    )
}

/** Lets the launcher shortcut ("Add Due" / "Add Payment") pick which account the entry belongs to. */
@Composable
private fun QuickAccountPicker(vm: HishabViewModel, type: TxType, onDismiss: () -> Unit, onPick: (Shop) -> Unit) {
    val bn = AppLanguage.isBangla
    var query by remember { mutableStateOf("") }
    val list = vm.activeShops.filter { it.name.contains(query, true) || it.phone.contains(query, true) }.sortedBy { it.name.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TxType.PAYMENT) { if (bn) "পেমেন্ট যোগ করুন" else "Add Payment" } else { if (bn) "বাকি যোগ করুন" else "Add Due" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (bn) "কোন হিসাবে যোগ করবেন?" else "Choose an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.activeShops.size > 5) OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (bn) "খুঁজুন" else "Search") })
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s.type == PartyType.PERSON) Icons.Outlined.Person else Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(money(vm.balance(s.id)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(if (bn) "বাতিল" else "Cancel") } }
    )
}
