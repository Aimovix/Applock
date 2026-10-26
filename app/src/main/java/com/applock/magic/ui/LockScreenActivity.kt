package com.applock.magic.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.security.BiometricAuthManager

class LockScreenActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_target_package"
    }

    private lateinit var prefs: AppLockPreferences
    private lateinit var biometricAuthManager: BiometricAuthManager
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verhindert Screenshots und Vorschauen im Task-Manager
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        prefs = AppLockPreferences(this)
        biometricAuthManager = BiometricAuthManager(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)

        // Falls die Zurück-Taste gedrückt wird: Sofort zum Startbildschirm, Ziel-App bleibt geschützt
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHomeScreen()
            }
        })

        startBiometricAuthentication()
    }

    private fun startBiometricAuthentication() {
        biometricAuthManager.authenticateWithCrypto(
            title = "App gesperrt",
            subtitle = "Entsperren mit Fingerabdruck oder Passkey",
            onSuccess = {
                // Temporäre Freigabe erteilen (z. B. 60 Sekunden oder bis Bildschirm aus)
                targetPackage?.let { pkg ->
                    prefs.unlockPackageTemporarily(pkg, 60_000L)
                }
                finish()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            },
            onError = { _, _ ->
                // Bei Abbruch oder zu vielen Fehlversuchen: Zurück zum Home-Screen
                exitToHomeScreen()
            },
            onFailed = {
                // Falscher Finger -> BiometricPrompt bleibt meist aktiv für nächsten Versuch
            }
        )
    }

    private fun exitToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
