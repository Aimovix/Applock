package com.applock.magic.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Verwaltet ein synchrones TYPE_ACCESSIBILITY_OVERLAY.
 * 1. Verleiht unserem Prozess unter Android 17 sofort "Visible"-Status für BAL.
 * 2. Blockiert 16ms Frame-Lecks des MagicOS 11 "Dynamic Liquid Glass"-Compositors.
 * 3. Bleibt touch-durchlässig (FLAG_NOT_FOCUSABLE) für das darüberliegende Biometrie-Sheet
 *    und den Ultraschall-Fingerabdrucksensor.
 */
object OverlayShieldManager {

    private var blackoutView: View? = null

    fun showInstantBlackout(service: AccessibilityService) {
        if (blackoutView != null) return

        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val view = View(service).apply {
                setBackgroundColor(Color.parseColor("#0B0E14")) // Fester Vollton gegen MagicOS 11 Blur-Bugs
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.CENTER
                windowAnimations = 0
            }

            wm.addView(view, params)
            blackoutView = view
        } catch (_: Exception) {}
    }

    fun dismissBlackout(context: Context) {
        blackoutView?.let {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(it)
            } catch (_: Exception) {}
            blackoutView = null
        }
    }
}
