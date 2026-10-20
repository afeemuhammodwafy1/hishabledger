package com.wafyro.hishabledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    title: String = "Unlock Hishab Ledger"
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errString.toString())
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

/**
 * The PIN screen. "Forgot PIN?" proves the person owns the phone with its own screen lock
 * (fingerprint, face, pattern or phone PIN) and then lets them choose a new PIN, keeping all data.
 * Only when the phone has no screen lock at all is the last-resort reset offered.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onResetData: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockoutRemaining by remember { mutableLongStateOf(SecurityStore.lockoutRemainingMs(context)) }
    var showNewPin by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val biometricEnabled = remember { SecurityStore.isBiometricEnabled(context) && SecurityStore.canUseBiometric(context) }

    // Back on the lock screen sends the app to the background instead of revealing what is behind it.
    BackHandler { activity?.moveTaskToBack(true) }

    LaunchedEffect(Unit) {
        if (biometricEnabled && activity != null) showBiometricPrompt(activity, onSuccess = onUnlocked, onError = { })
    }
    // Counts the wait down on screen while the PIN is temporarily blocked.
    LaunchedEffect(lockoutRemaining) {
        if (lockoutRemaining > 0L) { delay(1000); lockoutRemaining = SecurityStore.lockoutRemainingMs(context) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(tr("Hishab Ledger is locked", "Hishab Ledger লক করা আছে"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false; lockoutRemaining = SecurityStore.lockoutRemainingMs(context) },
                label = { Text(tr("Enter PIN", "PIN দিন")) },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            if (lockoutRemaining > 0L) {
                val seconds = (lockoutRemaining + 999) / 1000
                Text(
                    tr("Too many incorrect attempts. Try again in $seconds seconds.", "অনেকবার ভুল PIN দেওয়া হয়েছে। ${localizedNumber(seconds.toInt())} সেকেন্ড পরে আবার চেষ্টা করুন।"),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
            } else if (error) {
                Text(tr("Incorrect PIN", "PIN ভুল হয়েছে"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button({
                lockoutRemaining = SecurityStore.lockoutRemainingMs(context)
                if (lockoutRemaining > 0L) return@Button
                if (SecurityStore.verifyPin(context, pin)) {
                    SecurityStore.registerSuccessfulUnlock(context)
                    onUnlocked()
                } else {
                    SecurityStore.registerFailedAttempt(context)
                    lockoutRemaining = SecurityStore.lockoutRemainingMs(context)
                    error = lockoutRemaining == 0L
                    pin = ""
                }
            }, Modifier.fillMaxWidth(), enabled = lockoutRemaining == 0L) { Text(tr("Unlock", "আনলক")) }
            if (biometricEnabled && activity != null) {
                TextButton({ showBiometricPrompt(activity, onSuccess = onUnlocked, onError = { }) }) {
                    Icon(Icons.Outlined.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text(tr("Use biometric", "ফিঙ্গারপ্রিন্ট/ফেস দিয়ে খুলুন"))
                }
            }
            TextButton({
                if (activity != null && SecurityStore.canUseBiometric(context)) {
                    showBiometricPrompt(
                        activity,
                        onSuccess = { showNewPin = true },
                        onError = { },
                        title = tr("Confirm it's you", "আপনার পরিচয় নিশ্চিত করুন")
                    )
                } else showReset = true
            }) { Text(tr("Forgot PIN?", "PIN ভুলে গেছেন?")) }
        }
    }

    if (showNewPin) SetPinDialog(
        onDismiss = { showNewPin = false },
        onConfirm = { newPin -> SecurityStore.setPin(context, newPin); showNewPin = false; onUnlocked() }
    )
    if (showReset) CountdownConfirmDialog(
        title = tr("Erase all data?", "সব ডেটা মুছবেন?"),
        text = tr(
            "This phone has no screen lock, so the PIN cannot be recovered. You can reset the app, but ALL accounts and entries on this phone will be permanently erased. You can bring them back only from a backup file.",
            "এই ফোনে স্ক্রিন লক নেই, তাই PIN ফিরিয়ে আনা যাবে না। অ্যাপ রিসেট করা যাবে, কিন্তু এই ফোনের সব হিসাব ও এন্ট্রি স্থায়ীভাবে মুছে যাবে। ব্যাকআপ ফাইল থাকলে তবেই ফিরিয়ে আনা সম্ভব।"
        ),
        confirmLabel = tr("Erase everything", "সব মুছে ফেলুন"),
        onConfirm = { showReset = false; onResetData() },
        onDismiss = { showReset = false }
    )
}
