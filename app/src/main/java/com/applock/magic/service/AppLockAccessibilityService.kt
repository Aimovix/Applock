package com.applock.magic.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.data.LockStateManager
import com.applock.magic.overlay.OverlayShieldManager
import com.applock.magic.security.PipAndAudioGuard
import com.applock.magic.ui.LockScreenActivity

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppLockPreferences
    private lateinit var pipAndAudioGuard: PipAndAudioGuard
    private var lastPackage: String = ""
    private var lastTriggerTime: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Bei Display-Aus alle temporären Freigaben widerrufen
                prefs.lockAllImmediately()
                lastPackage = ""
                lastTriggerTime = 0L
                OverlayShieldManager.dismissBlackout(this@AppLockAccessibilityService)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppLockPreferences(this)
        pipAndAudioGuard = PipAndAudioGuard(this, prefs)
        pipAndAudioGuard.start()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Multi-Window & Floating Window Überwachung (MagicOS Mini-Fenster)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            checkFloatingWindows()
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // 2. Eigene App-Events ignorieren
        if (packageName == applicationContext.packageName) return

        // 3. Transiente System-Fenster ignorieren (Tastaturen, SystemUI, Fingerabdruck-Dialog, Magic Portal)
        if (LockStateManager.isTransientPackage(packageName)) return

        // 4. Wenn die App freigegeben ist: Auf keinen Fall sperren!
        if (!prefs.isPackageLocked(packageName)) {
            lastPackage = packageName
            prefs.handleAppSwitch(packageName)
            return
        }

        // 5. Wenn der Sperrbildschirm bereits sichtbar ist:
        if (LockStateManager.isLockScreenShowing.get()) {
            // Wenn es dieselbe App ist, für die der Dialog bereits läuft: nichts tun
            if (LockStateManager.authenticatingPackage == packageName) return

            // Wenn zu einer ANDEREN gesperrten App gewechselt wurde:
            // Intent an aktive LockScreenActivity weiterleiten (via onNewIntent)
            LockStateManager.authenticatingPackage = packageName
            triggerLock(packageName)
            return
        }

        // 6. Wenn bereits eine Authentifizierung für dieses Paket läuft, nicht erneut triggern
        if (LockStateManager.authenticatingPackage == packageName) return

        // 7. App-Wechsel an den State-Manager melden (verwaltet UNLOCK_HANDOFF -> UNLOCKED_ACTIVE)
        prefs.handleAppSwitch(packageName)
        lastPackage = packageName

        // 8. Prüfen, ob die gewechselte Ziel-App gesperrt ist
        if (prefs.isPackageLocked(packageName)) {
            val now = SystemClock.elapsedRealtime()
            // Debounce: Verhindert Mehrfachstarts bei schnellen Window-State-Schwankungen (unter 150ms)
            if (now - lastTriggerTime > 150L) {
                lastTriggerTime = now
                LockStateManager.authenticatingPackage = packageName
                triggerLock(packageName)
            }
        }
    }

    /**
     * Scannt alle aktiven Fenster nach schwebenden Mini-Fenstern (Freeform / Multi-Window),
     * damit gesperrte Apps im Kleinformat nicht die Vollbild-Erkennung umgehen.
     */
    private fun checkFloatingWindows() {
        val winList = try {
            windows
        } catch (_: Exception) {
            null
        } ?: return

        for (win in winList) {
            if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = win.root ?: continue
            val pkg = root.packageName?.toString() ?: continue

            if (pkg != packageName && !LockStateManager.isTransientPackage(pkg) && prefs.isPackageLocked(pkg)) {
                if (!LockStateManager.isLockScreenShowing.get() && LockStateManager.authenticatingPackage != pkg) {
                    LockStateManager.authenticatingPackage = pkg
                    triggerLock(pkg)
                    break
                }
            }
        }
    }

    private fun triggerLock(packageName: String) {
        // 1. Synchrones Blackout-Overlay einhängen (verleiht unserem Prozess unter Android 17 / MagicOS 11 sofort "Visible"-Status)
        OverlayShieldManager.showInstantBlackout(this)

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }

        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    // Modus 2 = MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE (Android 15+)
                    val mode = if (Build.VERSION.SDK_INT >= 35) 2 else 1
                    setPendingIntentBackgroundActivityStartMode(mode)
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
            }
        }
        startActivity(lockIntent, options.toBundle())
    }

    override fun onInterrupt() {
        // Nicht implementiert
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        try {
            pipAndAudioGuard.stop()
        } catch (_: Exception) {}
        OverlayShieldManager.dismissBlackout(this)
        LockStateManager.authenticatingPackage = null
    }
}
