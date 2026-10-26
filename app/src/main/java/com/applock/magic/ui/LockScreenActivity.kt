package com.applock.magic.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.applock.magic.R
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.data.LockStateManager
import com.applock.magic.security.BiometricAuthManager

class LockScreenActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_target_package"
    }

    private lateinit var prefs: AppLockPreferences
    private lateinit var biometricAuthManager: BiometricAuthManager
    private var targetPackage: String? = null
    private var isAuthenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Screenshot- und Recent-Task-Schutz aktivieren
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. Zustand sofort registrieren, um Doppelstarts durch Event-Races zu verhindern
        LockStateManager.isLockScreenShowing.set(true)

        // 3. Opaque Schutzmaske laden (verhindert Durchscheinen der Ziel-App)
        setContentView(R.layout.layout_overlay_lock)

        prefs = AppLockPreferences(this)
        biometricAuthManager = BiometricAuthManager(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        LockStateManager.authenticatingPackage = targetPackage

        initViews()

        // 4. Zurück-Taste: Immer sicher zum Launcher zurückkehren
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHomeScreen()
            }
        })

        // 5. Biometrischen Dialog automatisch starten
        startBiometricAuthentication()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTarget = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (newTarget != null && newTarget != targetPackage) {
            targetPackage = newTarget
            LockStateManager.authenticatingPackage = newTarget
            if (!isAuthenticating) {
                startBiometricAuthentication()
            }
        }
    }

    private fun initViews() {
        val btnUnlock = findViewById<Button>(R.id.btn_unlock_biometric)
        val btnHome = findViewById<Button>(R.id.btn_go_home)

        btnUnlock.setOnClickListener {
            if (!isAuthenticating) {
                startBiometricAuthentication()
            }
        }

        btnHome.setOnClickListener {
            exitToHomeScreen()
        }
    }

    private fun startBiometricAuthentication() {
        if (isAuthenticating) return
        isAuthenticating = true

        biometricAuthManager.authenticateWithCrypto(
            title = "App geschützt",
            subtitle = "Entsperren mit Fingerabdruck oder 3D-Gesicht",
            onSuccess = {
                isAuthenticating = false
                targetPackage?.let { pkg ->
                    prefs.unlockPackageTemporarily(pkg)
                }
                closeLockScreen(unlocked = true)
            },
            onError = { _, _ ->
                isAuthenticating = false
                // Bei manuellem Abbruch oder Fehler bleibt der Schutz aktiv
            },
            onFailed = {
                isAuthenticating = false
            }
        )
    }

    private fun closeLockScreen(unlocked: Boolean) {
        LockStateManager.isLockScreenShowing.set(false)
        LockStateManager.authenticatingPackage = null
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun exitToHomeScreen() {
        LockStateManager.isLockScreenShowing.set(false)
        LockStateManager.authenticatingPackage = null
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LockStateManager.isLockScreenShowing.set(false)
        if (LockStateManager.authenticatingPackage == targetPackage) {
            LockStateManager.authenticatingPackage = null
        }
    }
}
