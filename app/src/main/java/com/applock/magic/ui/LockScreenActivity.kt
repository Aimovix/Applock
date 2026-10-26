package com.applock.magic.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
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

    private lateinit var imgTargetAppIcon: ImageView
    private lateinit var tvTargetAppName: TextView
    private lateinit var tvLockSubtitle: TextView
    private lateinit var btnUnlock: Button
    private lateinit var btnHome: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Screenshot- und Recent-Task-Schutz aktivieren (Schutz vor Inhalts-Leaks)
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
        updateTargetAppInfo(targetPackage)

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
            updateTargetAppInfo(newTarget)
            if (!isAuthenticating) {
                startBiometricAuthentication()
            }
        }
    }

    private fun initViews() {
        imgTargetAppIcon = findViewById(R.id.img_target_app_icon)
        tvTargetAppName = findViewById(R.id.tv_target_app_name)
        tvLockSubtitle = findViewById(R.id.tv_lock_subtitle)
        btnUnlock = findViewById(R.id.btn_unlock_biometric)
        btnHome = findViewById(R.id.btn_go_home)

        btnUnlock.setOnClickListener {
            if (!isAuthenticating) {
                startBiometricAuthentication()
            }
        }

        btnHome.setOnClickListener {
            exitToHomeScreen()
        }
    }

    private fun updateTargetAppInfo(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            tvTargetAppName.text = getString(R.string.lock_screen_title)
            imgTargetAppIcon.setImageResource(android.R.drawable.ic_lock_lock)
            tvLockSubtitle.text = getString(R.string.lock_screen_subtitle)
            return
        }

        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val appIcon = pm.getApplicationIcon(appInfo)

            tvTargetAppName.text = appLabel
            imgTargetAppIcon.setImageDrawable(appIcon)
            tvLockSubtitle.text = "$appLabel ist durch ShieldLock Pro geschützt"
        } catch (_: Exception) {
            tvTargetAppName.text = getString(R.string.lock_screen_title)
            imgTargetAppIcon.setImageResource(android.R.drawable.ic_lock_lock)
            tvLockSubtitle.text = getString(R.string.lock_screen_subtitle)
        }
    }

    private fun startBiometricAuthentication() {
        if (isAuthenticating) return
        isAuthenticating = true

        val appLabel = targetPackage?.let { pkg ->
            try {
                val pm = packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                null
            }
        } ?: getString(R.string.lock_screen_title)

        biometricAuthManager.authenticate(
            title = "$appLabel entsperren",
            subtitle = "Mit Fingerabdruck, 3D-Gesicht oder PIN bestätigen",
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

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            LockStateManager.isLockScreenShowing.set(false)
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
