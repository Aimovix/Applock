package com.applock.magic.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthManager(private val activity: FragmentActivity) {

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Führt eine hochsichere Authentifizierung durch.
     * Unterstützt Biometrie (Fingerabdruck, 3D-Gesicht) mit nahtlosem Fallback
     * auf die Geräte-PIN/Muster, um Aussperrungen zu verhindern.
     */
    fun authenticate(
        title: String = "App geschützt",
        subtitle: String = "Entsperren mit Fingerabdruck, 3D-Gesicht oder PIN",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false) // Blitzschnell für Honor Magic 8 Pro 3D-ToF Gesichtsscan

        val biometricManager = BiometricManager.from(activity)
        val canUseDeviceCredential = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

        if (canUseDeviceCredential) {
            promptBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            promptBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG)
            promptBuilder.setNegativeButtonText("Abbrechen")
        }

        val promptInfo = promptBuilder.build()
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Kryptografische Authentifizierung mit Hardware Keystore Cipher
     */
    fun authenticateWithCrypto(
        title: String = "App gesperrt",
        subtitle: String = "Entsperren mit Fingerabdruck oder 3D-Gesicht",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Abbrechen")
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        try {
            val cipher = KeystoreHelper.createCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: KeyPermanentlyInvalidatedException) {
            KeystoreHelper.getOrCreateSecretKey()
            val fallbackCipher = KeystoreHelper.createCipher()
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(fallbackCipher))
        } catch (e: Exception) {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
