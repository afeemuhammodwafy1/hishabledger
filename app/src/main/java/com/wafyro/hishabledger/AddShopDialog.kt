package com.wafyro.hishabledger

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShopDialog(
    onDismiss: () -> Unit,
    editing: Shop? = null,
    /** Accounts that look like the one being saved (same name or phone); empty means no warning is needed. */
    similar: (name: String, phone: String) -> List<Shop> = { _, _ -> emptyList() },
    balanceOf: (String) -> Long = { 0L },
    onOpenExisting: (Shop) -> Unit = {},
    onSave: (String, String, String, Long, PartyType) -> Unit
) {
    val bn = AppLanguage.isBangla
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var phone by remember { mutableStateOf(editing?.phone ?: "") }
    var address by remember { mutableStateOf(editing?.address ?: "") }
    var opening by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(editing?.type ?: PartyType.SHOP) }
    val emerald = MaterialTheme.colorScheme.primary
    val emeraldDark = MaterialTheme.colorScheme.primary
    val mint = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty()
                    val selectedPhone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()
                    if (name.isBlank()) name = displayName
                    phone = selectedPhone
                }
            }
        }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = emerald,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = emerald,
        cursorColor = emerald,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    var similarFound by remember { mutableStateOf<List<Shop>>(emptyList()) }
    fun doSave() = onSave(name.trim(), phone.trim(), address.trim(), opening.toLongOrNull() ?: 0L, type)
    // Same name or phone as an existing account? Ask first instead of silently creating a twin.
    fun attemptSave() {
        val matches = similar(name, phone)
        if (matches.isEmpty()) doSave() else similarFound = matches
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(if (editing != null) { if (bn) "হিসাব এডিট" else "Edit Account" } else "Add New", fontWeight = FontWeight.Bold, color = ink)
                        Text(if (editing != null) { if (bn) "নাম, ফোন ও ঠিকানা বদলান" else "Update name, phone and address" } else "Create a ledger account", style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.85f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)
                    ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { attemptSave() },
                        modifier = Modifier.weight(1.3f).height(54.dp),
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = emerald, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (editing != null) { if (bn) "পরিবর্তন সংরক্ষণ" else "Save Changes" } else "Save Account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .imePadding().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Soft emerald header card adds hierarchy without changing the existing workflow.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = mint),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(54.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = emeraldDark, modifier = Modifier.size(29.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (editing != null) { if (bn) "তথ্য ঠিক করুন" else "Edit details" } else "A fresh start", fontWeight = FontWeight.Bold, color = ink, style = MaterialTheme.typography.titleMedium)
                        Text(if (editing != null) { if (bn) "আগের বাকি এখানে এডিট করা যায় না।" else "Previous due can't be edited here." } else "Keep names and balances organised in one place.", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Account details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                        Text("Choose the account type and add the details below.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shopSelected = type == PartyType.SHOP
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { type = PartyType.SHOP },
                            shape = RoundedCornerShape(12.dp),
                            color = if (shopSelected) mint else Color.Transparent,
                            border = if (shopSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                        ) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Storefront, contentDescription = null, tint = if (shopSelected) emeraldDark else muted, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Shop", color = if (shopSelected) emeraldDark else muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        val personSelected = type == PartyType.PERSON
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { type = PartyType.PERSON },
                            shape = RoundedCornerShape(12.dp),
                            color = if (personSelected) mint else Color.Transparent,
                            border = if (personSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                        ) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = if (personSelected) emeraldDark else muted, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Person", color = if (personSelected) emeraldDark else muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (type == PartyType.PERSON) "Person's name *" else "Shop name *") },
                        placeholder = { Text(if (type == PartyType.PERSON) "Enter person's name" else "Enter shop name", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(if (type == PartyType.PERSON) Icons.Outlined.Person else Icons.Outlined.Storefront, contentDescription = null, tint = muted) },
                        singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone (Optional)") }, placeholder = { Text("Enter phone number", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = emerald) },
                        trailingIcon = { IconButton(onClick = { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) { Icon(Icons.Outlined.ContactPhone, contentDescription = "Choose from contacts", tint = emerald) } },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    OutlinedTextField(
                        value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Address (Optional)") }, placeholder = { Text("Enter address", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = muted) },
                        singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors
                    )
                    if (editing == null) OutlinedTextField(
                        value = opening, onValueChange = { opening = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Previous due (Optional)") }, placeholder = { Text("৳ 0", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = emerald) },
                        supportingText = { Text("Amount already due before using Hishab Ledger.", color = muted) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(15.dp), colors = fieldColors
                    ) else Text(if (bn) "আগের বাকি (Previous Due) এডিট করা যাবে না।" else "Previous Due can't be edited.", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (similarFound.isNotEmpty()) SimilarAccountDialog(
        matches = similarFound,
        balanceOf = balanceOf,
        onOpen = { similarFound = emptyList(); onOpenExisting(it) },
        onSaveAnyway = { similarFound = emptyList(); doSave() },
        onDismiss = { similarFound = emptyList() }
    )
}

/** Warns that an account with the same name or phone already exists, and lets the person open it or save anyway. */
@Composable
private fun SimilarAccountDialog(
    matches: List<Shop>,
    balanceOf: (String) -> Long,
    onOpen: (Shop) -> Unit,
    onSaveAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("This account may already exist", "এই হিসাব আগে থেকেই থাকতে পারে")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                matches.take(3).forEach { s ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(vertical = 4.dp)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        val extra = listOf(s.phone, s.address).filter { it.isNotBlank() }.joinToString(" • ")
                        if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Current Due: ", "বর্তমান বাকি: ") + money(balanceOf(s.id)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    tr("Tap one to open it, or save this as a separate account.", "আগেরটা খুলতে ট্যাপ করুন, অথবা এটাকে আলাদা হিসাব হিসেবে সেভ করুন।"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onSaveAnyway) { Text(tr("Save anyway", "তবুও সেভ করুন")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Go back", "ফিরে যান")) } }
    )
}
