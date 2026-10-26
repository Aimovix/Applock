package com.applock.magic.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthManager(private val activity: FragmentActivity) {

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

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
            .setConfirmationRequired(false) // Ermöglicht blitzschnelles Entsperren via 3D-Gesichtsscan (Honor Magic 8 Pro)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        try {
            val cipher = KeystoreHelper.createCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Neuer Fingerabdruck im System registriert -> Schlüssel neu generieren
            KeystoreHelper.getOrCreateSecretKey()
            val fallbackCipher = KeystoreHelper.createCipher()
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(fallbackCipher))
        } catch (e: Exception) {
            // Fallback auf reine Hardware-Prompt ohne CryptoObject falls Keystore temporär streikt
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
