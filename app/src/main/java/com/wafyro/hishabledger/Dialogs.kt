package com.wafyro.hishabledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
/** Confirmation for "delete all": the destructive button unlocks only after a 3 second countdown. */
@Composable
internal fun DeleteAllConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val bn = AppLanguage.isBangla
    var secondsLeft by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
    val ready = secondsLeft == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bn) "সব স্থায়ীভাবে মুছবেন?" else "Delete everything forever?") },
        text = { Text(if (bn) "${localizedNumber(count)}টি আইটেম স্থায়ীভাবে মুছে যাবে। এটি আর ফিরিয়ে আনা যাবে না।" else "$count item${if (count == 1) "" else "s"} will be permanently deleted. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ready) {
                Text(
                    if (ready) { if (bn) "সব মুছুন" else "Delete all" } else { (if (bn) "সব মুছুন (" else "Delete all (") + localizedNumber(secondsLeft) + ")" },
                    color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(if (bn) "বাতিল" else "Cancel") } }
    )
}

/** A confirmation whose red button only becomes usable after a 3 second countdown. */
@Composable
fun CountdownConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
    val ready = secondsLeft == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ready) {
                Text(
                    if (ready) confirmLabel else "$confirmLabel (${localizedNumber(secondsLeft)})",
                    color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "বাতিল")) } }
    )
}

/** Asks for a new 4-6 digit PIN twice. Used when turning App Lock on, changing the PIN, and after "Forgot PIN?". */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Set a PIN", "PIN সেট করুন")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    pin1, { pin1 = it.filter(Char::isDigit).take(6); mismatch = false },
                    label = { Text(tr("New PIN (4-6 digits)", "নতুন PIN (৪-৬ সংখ্যা)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    pin2, { pin2 = it.filter(Char::isDigit).take(6); mismatch = false },
                    label = { Text(tr("Confirm PIN", "PIN নিশ্চিত করুন")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation()
                )
                if (mismatch) Text(tr("PINs don't match or are too short", "PIN মেলেনি বা খুব ছোট"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ if (pin1.length in 4..6 && pin1 == pin2) onConfirm(pin1) else mismatch = true }) { Text(tr("Save", "সংরক্ষণ")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "বাতিল")) } }
    )
}
