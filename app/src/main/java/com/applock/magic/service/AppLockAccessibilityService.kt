package com.applock.magic.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.data.LockStateManager
import com.applock.magic.ui.LockScreenActivity

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppLockPreferences
    private var lastPackage: String = ""
    private var lastTriggerTime: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Bei Display-Aus alle temporären Freigaben widerrufen
                prefs.lockAllImmediately()
                lastPackage = ""
                lastTriggerTime = 0L
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppLockPreferences(this)

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Eigene App-Events ignorieren
        if (packageName == applicationContext.packageName) return

        // 2. Transiente System-Fenster ignorieren (Tastaturen, SystemUI, Fingerabdruck-Dialog)
        if (LockStateManager.isTransientPackage(packageName)) return

        // 3. DEADLOCK-VERHINDERUNG ("Stuck zwischen Apps"):
        // Wenn der Sperrbildschirm bereits sichtbar ist, KEINE neue Instanz starten!
        if (LockStateManager.isLockScreenShowing.get()) return

        // 4. Wenn bereits eine Authentifizierung für dieses Paket läuft, nicht erneut triggern
        if (LockStateManager.authenticatingPackage == packageName) return

        // 5. Wenn sich das Paket nicht geändert hat und die App freigegeben ist, überspringen
        if (packageName == lastPackage && !prefs.isPackageLocked(packageName)) return

        // 6. App-Wechsel an den State-Manager melden (prüft Re-Lock-Richtlinien)
        prefs.handleAppSwitch(packageName)
        lastPackage = packageName

        // 7. Prüfen, ob die gewechselte Ziel-App gesperrt ist
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

    private fun triggerLock(packageName: String) {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        startActivity(lockIntent)
    }

    override fun onInterrupt() {
        // Nicht implementiert
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        LockStateManager.authenticatingPackage = null
    }
}
