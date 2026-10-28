package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

data class SecuritySettingsState(
    val isPinConfigured: Boolean = true,
    val pinLength: Int = 4,
    val masterProtectionEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val biometricTriggerMode: BiometricTriggerMode = BiometricTriggerMode.MANUAL_BUTTON_ONLY,
    val scrambleKeypad: Boolean = false,
    val stealthHideKeyRipple: Boolean = false,
    val relockPolicy: RelockPolicy = RelockPolicy.IMMEDIATE_WITH_GRACE,
    val maxFailedAttemptsBeforeCooldown: Int = 5,
    val darkThemeEnabled: Boolean = true
)

object CredentialHasher {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$pin:applock_v2".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(input)
        return hashed.joinToString("") { "%02x".format(it) }
    }
}

class SecurityPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("applock_sec_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_PIN_HASH)) {
            // Initialize default PIN "1234" with cryptographic salt so the user can test right away
            // and can change it anytime in Settings or via the Quick-PIN Banner.
            val salt = CredentialHasher.generateSalt()
            val hash = CredentialHasher.hashPin("1234", salt)
            prefs.edit()
                .putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, hash)
                .putInt(KEY_PIN_LENGTH, 4)
                .putString(KEY_PIN_HINT, "1234")
                .apply()
        }
    }

    private val _settingsState = MutableStateFlow(readState())
    val settingsState: StateFlow<SecuritySettingsState> = _settingsState.asStateFlow()

    private fun readState(): SecuritySettingsState {
        return SecuritySettingsState(
            isPinConfigured = prefs.getString(KEY_PIN_HASH, null) != null,
            pinLength = prefs.getInt(KEY_PIN_LENGTH, 4),
            masterProtectionEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true),
            biometricTriggerMode = BiometricTriggerMode.fromKey(
                prefs.getString(KEY_BIOMETRIC_TRIGGER_MODE, BiometricTriggerMode.MANUAL_BUTTON_ONLY.key)
                    ?: BiometricTriggerMode.MANUAL_BUTTON_ONLY.key
            ),
            scrambleKeypad = prefs.getBoolean(KEY_SCRAMBLE_KEYPAD, false),
            stealthHideKeyRipple = prefs.getBoolean(KEY_STEALTH_RIPPLE, false),
            relockPolicy = RelockPolicy.fromKey(
                prefs.getString(KEY_RELOCK_POLICY, RelockPolicy.IMMEDIATE_WITH_GRACE.key)
                    ?: RelockPolicy.IMMEDIATE_WITH_GRACE.key
            ),
            maxFailedAttemptsBeforeCooldown = prefs.getInt(KEY_MAX_ATTEMPTS, 5),
            darkThemeEnabled = prefs.getBoolean(KEY_DARK_THEME, true)
        )
    }

    fun verifyPin(candidatePin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expectedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val actualHash = CredentialHasher.hashPin(candidatePin, salt)
        return actualHash == expectedHash
    }

    fun getCurrentDemoPinHint(): String {
        return prefs.getString(KEY_PIN_HINT, "••••") ?: "••••"
    }

    fun updateMasterPin(newPin: String) {
        val cleanPin = newPin.filter { it.isDigit() }
        if (cleanPin.length != 4 && cleanPin.length != 6) return
        val salt = CredentialHasher.generateSalt()
        val hash = CredentialHasher.hashPin(cleanPin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_PIN_LENGTH, cleanPin.length)
            .putString(KEY_PIN_HINT, cleanPin)
            .apply()
        _settingsState.value = readState()
    }

    fun setMasterProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _settingsState.value = readState()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _settingsState.value = readState()
    }

    fun setBiometricTriggerMode(mode: BiometricTriggerMode) {
        prefs.edit().putString(KEY_BIOMETRIC_TRIGGER_MODE, mode.key).apply()
        _settingsState.value = readState()
    }

    fun setScrambleKeypad(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCRAMBLE_KEYPAD, enabled).apply()
        _settingsState.value = readState()
    }

    fun setStealthHideKeyRipple(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STEALTH_RIPPLE, enabled).apply()
        _settingsState.value = readState()
    }

    fun setRelockPolicy(policy: RelockPolicy) {
        prefs.edit().putString(KEY_RELOCK_POLICY, policy.key).apply()
        _settingsState.value = readState()
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _settingsState.value = readState()
    }

    companion object {
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_PIN_HINT = "pin_hint"
        private const val KEY_MASTER_ENABLED = "master_protection_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_TRIGGER_MODE = "biometric_trigger_mode"
        private const val KEY_SCRAMBLE_KEYPAD = "scramble_keypad"
        private const val KEY_STEALTH_RIPPLE = "stealth_ripple"
        private const val KEY_RELOCK_POLICY = "relock_policy"
        private const val KEY_MAX_ATTEMPTS = "max_attempts"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
