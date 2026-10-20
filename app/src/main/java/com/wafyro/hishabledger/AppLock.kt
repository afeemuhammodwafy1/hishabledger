package com.wafyro.hishabledger

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the app-lock PIN (salted + hashed, never in plaintext) and lock preferences locally. */
object SecurityStore {
    private fun prefs(context: Context) = context.getSharedPreferences("baki_security", Context.MODE_PRIVATE)

    fun isLockEnabled(context: Context): Boolean = prefs(context).getBoolean("lock_enabled", false)
    fun isBiometricEnabled(context: Context): Boolean = prefs(context).getBoolean("biometric_enabled", false)

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs(context).edit()
            .putString("pin_salt", salt.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) })
            .putString("pin_hash", hash)
            .putBoolean("lock_enabled", true)
            .remove("pin_lockout_until")
            .putInt("pin_failed_attempts", 0)
            .apply()
    }

    fun disableLock(context: Context) {
        prefs(context).edit()
            .putBoolean("lock_enabled", false)
            .putBoolean("biometric_enabled", false)
            .remove("pin_hash")
            .remove("pin_salt")
            .remove("pin_lockout_until")
            .remove("pin_failed_attempts")
            .apply()
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("biometric_enabled", enabled).apply()
    }

    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L

    fun lockoutRemainingMs(context: Context): Long =
        (prefs(context).getLong("pin_lockout_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun registerFailedAttempt(context: Context) {
        val p = prefs(context)
        if (lockoutRemainingMs(context) > 0L) return
        val attempts = p.getInt("pin_failed_attempts", 0) + 1
        val edit = p.edit().putInt("pin_failed_attempts", attempts)
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            edit.putLong("pin_lockout_until", System.currentTimeMillis() + LOCKOUT_MS)
                .putInt("pin_failed_attempts", 0)
        }
        edit.apply()
    }

    fun registerSuccessfulUnlock(context: Context) {
        prefs(context).edit().remove("pin_lockout_until").putInt("pin_failed_attempts", 0).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (lockoutRemainingMs(context) > 0L) return false
        val p = prefs(context)
        val saltHex = p.getString("pin_salt", null) ?: return false
        val storedHash = p.getString("pin_hash", null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return hash(pin, salt) == storedHash
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
    }

    fun canUseBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
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
        .setTitle("Unlock Hishab Ledger")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockoutRemaining by remember { mutableLongStateOf(SecurityStore.lockoutRemainingMs(context)) }
    val biometricEnabled = remember { SecurityStore.isBiometricEnabled(context) && SecurityStore.canUseBiometric(context) }

    LaunchedEffect(Unit) {
        if (biometricEnabled) {
            (context as? FragmentActivity)?.let { activity ->
                showBiometricPrompt(activity, onSuccess = onUnlocked, onError = { })
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("Hishab Ledger Locked", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false; lockoutRemaining = SecurityStore.lockoutRemainingMs(context) },
                label = { Text("Enter PIN") },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            if (lockoutRemaining > 0L) Text("Too many incorrect attempts. Try again in ${((lockoutRemaining + 999) / 1000)} seconds.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else if (error) Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            }, Modifier.fillMaxWidth(), enabled = lockoutRemaining == 0L) { Text("Unlock") }
            if (biometricEnabled) {
                TextButton({
                    (context as? FragmentActivity)?.let { activity -> showBiometricPrompt(activity, onSuccess = onUnlocked, onError = { }) }
                }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("Use biometric") }
            }
        }
    }
}
