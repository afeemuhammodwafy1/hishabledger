package com.wafyro.hishabledger

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxDialog(type: TxType, close: () -> Unit, save: (Long, String, Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var transactionDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val payment = type == TxType.PAYMENT
    val suggestions = if (payment) listOf(1000L, 2000L, 3000L) else listOf(100L, 500L, 1000L)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = hishabTopBarColors(), 
            title = { Text(if (payment) "Add Payment" else "Add Due") },
            navigationIcon = { IconButton(close) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { val v = amount.toLongOrNull() ?: 0L; if (v > 0) save(v, note, transactionDate) }, enabled = (amount.toLongOrNull() ?: 0L) > 0) { Icon(Icons.Outlined.CheckCircle, "Save", tint = MaterialTheme.colorScheme.primary) } },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (payment) "Payment details" else "Due details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), leadingIcon = { Icon(Icons.Outlined.Payments, null) })
                    Text("Quick amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { suggestion -> SuggestionChip(onClick = { amount = ((amount.toLongOrNull() ?: 0L) + suggestion).toString() }, label = { Text("+${String.format("%,d", suggestion)}") }) }
                    }
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (AppLanguage.isBangla) "লেনদেনের তারিখ" else "Transaction date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(SimpleDateFormat("dd MMMM yyyy", if (AppLanguage.isBangla) Locale("bn", "BD") else Locale.getDefault()).format(Date(transactionDate)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(Icons.Outlined.EditCalendar, contentDescription = if (AppLanguage.isBangla) "তারিখ বাছাই" else "Choose date", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (AppLanguage.isBangla) "নোট (ঐচ্ছিক)" else "Note (Optional)") }, minLines = 2)
                    Button(onClick = { val v = amount.toLongOrNull() ?: 0L; if (v > 0) save(v, note, transactionDate) }, modifier = Modifier.fillMaxWidth(), enabled = (amount.toLongOrNull() ?: 0L) > 0, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text(if (payment) "Save Payment" else "Save Due") }
                }
            }
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = transactionDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { dateState.selectedDateMillis?.let { picked ->
                val pickedCalendar = Calendar.getInstance().apply { timeInMillis = picked; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                transactionDate = pickedCalendar.timeInMillis
            }; showDatePicker = false }) { Text(if (AppLanguage.isBangla) "নির্বাচন" else "Select") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (AppLanguage.isBangla) "বাতিল" else "Cancel") } }) {
            DatePicker(state = dateState, title = { Text(if (AppLanguage.isBangla) "লেনদেনের তারিখ বেছে নিন" else "Choose transaction date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}
