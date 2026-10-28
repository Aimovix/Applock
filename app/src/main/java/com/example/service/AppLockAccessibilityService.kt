package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.security.SecurityEngine
import com.example.ui.lockgate.LockOverlayGateActivity

/**
 * Ultra-lightweight, event-driven foreground app watcher.
 * Uses SecurityEngine's 4-layer anti-loop filter to guarantee:
 * 1. SystemUI and biometric prompts are never intercepted.
 * 2. AppLock's own activities are never intercepted.
 * 3. Duplicate LockOverlayGateActivity instances are never spawned while one is visible.
 * 4. Recently unlocked packages enjoy a grace window so focus transitions never re-lock.
 */
class AppLockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (SecurityEngine.shouldInterceptPackage(packageName)) {
            SecurityEngine.onLockGateOpened()
            val lockIntent = Intent(this, LockOverlayGateActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(LockOverlayGateActivity.EXTRA_TARGET_PACKAGE, packageName)
            }
            runCatching {
                startActivity(lockIntent)
            }.onFailure {
                SecurityEngine.onLockGateClosed()
            }
        }
    }

    override fun onInterrupt() {
        // Release gate lock safely if service is interrupted by the OS
        SecurityEngine.onLockGateClosed()
    }
}
