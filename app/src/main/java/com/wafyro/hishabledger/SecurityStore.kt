package com.wafyro.hishabledger

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.biometric.BiometricManager
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import java.security.SecureRandom
import android.os.SystemClock
import kotlinx.coroutines.launch

/** Stores the app-lock PIN (salted + hashed, never in plaintext) and lock preferences locally. */
object SecurityStore {
    private fun prefs(context: Context) = context.getSharedPreferences("hishab_security", Context.MODE_PRIVATE)

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

    /** How long the app may stay in the background before it asks for the PIN again (0 = immediately). */
    fun lockTimeoutMs(context: Context): Long = prefs(context).getLong("lock_timeout_ms", 30_000L)
    fun setLockTimeoutMs(context: Context, ms: Long) { prefs(context).edit().putLong("lock_timeout_ms", ms).apply() }

    fun canUseBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/**
 * Decides when the app should ask for the PIN. It lives for the whole process, so turning the phone
 * sideways or changing the theme does not lock the app, and a short trip to another app (for example the
 * contacts list or the file picker) stays inside the grace period chosen in Settings → App Lock.
 */
object LockGate {
    /** True while the PIN screen must be shown. */
    var locked by mutableStateOf(false)
        private set
    private var initialized = false
    private var backgroundedAt = 0L

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        // A fresh process (first launch, or Android closed the app in the background) always starts locked.
        locked = SecurityStore.isLockEnabled(app)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    if (SecurityStore.isLockEnabled(app) && backgroundedAt == 0L) backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    val since = backgroundedAt
                    backgroundedAt = 0L
                    if (since != 0L && SecurityStore.isLockEnabled(app) &&
                        SystemClock.elapsedRealtime() - since >= SecurityStore.lockTimeoutMs(app)
                    ) locked = true
                }
                else -> Unit
            }
        })
    }

    fun unlock() { locked = false }
}
