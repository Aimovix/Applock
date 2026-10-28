package com.example.ui.lockgate

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.example.data.AppLockDatabase
import com.example.data.AppLockRepository
import com.example.data.SecurityPreferences
import com.example.security.SecurityEngine
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Dedicated full-screen security gate launched when AppLockAccessibilityService detects a locked
 * package entering the foreground.
 *
 * Anti-Loop Guarantees:
 * 1. Does NOT trigger BiometricPrompt in onResume() (which would cause an infinite loop whenever
 *    the system biometric dialog closes).
 * 2. Uses singleInstance launchMode and SecurityEngine's gate mutex.
 * 3. Provides a clean "Exit to Home Launcher" action so the user can always back out safely.
 */
class LockOverlayGateActivity : ComponentActivity() {

    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var repository: AppLockRepository
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shield window from Recent Apps thumbnails and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        securityPreferences = SecurityPreferences(this)
        val db = AppLockDatabase.getInstance(this)
        repository = AppLockRepository(
            db.lockedAppDao(),
            db.securityProfileDao(),
            db.auditLogDao()
        )

        targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty().ifEmpty {
            "com.android.settings"
        }
        val appLabel = resolveFriendlyAppLabel(targetPackage)

        setContent {
            val settings by securityPreferences.settingsState.collectAsState()
            MyApplicationTheme(darkTheme = settings.darkThemeEnabled) {
                LockGateScreen(
                    targetPackageName = targetPackage,
                    targetAppName = appLabel,
                    settings = settings,
                    demoPinHint = securityPreferences.getCurrentDemoPinHint(),
                    isSimulationPreview = false,
                    onVerifyPin = { candidate ->
                        securityPreferences.verifyPin(candidate)
                    },
                    onUnlockSuccess = { method ->
                        SecurityEngine.markPackageUnlockedForSession(
                            targetPackage,
                            settings.relockPolicy.gracePeriodMs
                        )
                        lifecycleScope.launch {
                            repository.recordAuditEvent(
                                packageName = targetPackage,
                                appName = appLabel,
                                eventType = "UNLOCK_SUCCESS",
                                authMethod = method,
                                details = "App entsperrt (${settings.relockPolicy.titleDe})"
                            )
                        }
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    },
                    onFailedAttempt = { attempt ->
                        lifecycleScope.launch {
                            repository.recordAuditEvent(
                                packageName = targetPackage,
                                appName = appLabel,
                                eventType = "FAILED_ATTEMPT",
                                authMethod = "PIN",
                                details = "Fehlversuch #$attempt bei $appLabel"
                            )
                        }
                    },
                    onExitToHomeOrCancel = {
                        SecurityEngine.onLockGateClosed()
                        navigateToSystemHomeLauncher()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty().ifEmpty { targetPackage }
    }

    override fun onDestroy() {
        SecurityEngine.onLockGateClosed()
        super.onDestroy()
    }

    private fun resolveFriendlyAppLabel(pkg: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    private fun navigateToSystemHomeLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(homeIntent) }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
