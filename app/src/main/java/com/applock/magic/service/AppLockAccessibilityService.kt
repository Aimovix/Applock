package com.applock.magic.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.overlay.OverlayLockManager
import com.applock.magic.ui.LockScreenActivity

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppLockPreferences
    private lateinit var overlayLockManager: OverlayLockManager
    private var lastPackage: String = ""

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Sobald das Display ausgeht, verfallen alle temporären Freigaben
                prefs.lockAllImmediately()
                lastPackage = ""
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppLockPreferences(this)
        overlayLockManager = OverlayLockManager(this)

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Eigene App ignorieren
        if (packageName == applicationContext.packageName) return

        // Wenn sich das aktive Paket nicht geändert hat, Event überspringen
        if (packageName == lastPackage && !prefs.isPackageLocked(packageName)) return
        lastPackage = packageName

        // Prüfen, ob die gewechselte App gesperrt ist
        if (prefs.isPackageLocked(packageName)) {
            triggerLock(packageName)
        }
    }

    private fun triggerLock(packageName: String) {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        startActivity(lockIntent)
    }

    override fun onInterrupt() {
        // Dienst unterbrochen
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
    }
}
