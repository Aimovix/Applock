package com.applock.magic.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.applock.magic.R
import com.applock.magic.ui.LockScreenActivity

class OverlayLockManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var currentLockedPackage: String? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun showLock(packageName: String) {
        if (isShowing && currentLockedPackage == packageName) return

        currentLockedPackage = packageName

        // Sofortige LockScreenActivity auslösen (nutzt BiometricPrompt mit Hardware-Schutz)
        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(lockIntent)
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
        currentLockedPackage = null
    }

    /**
     * Erzeugt ein natives WindowManager-Overlay mit 0ms Verzögerung,
     * welches den Bildschirm abdeckt, bevor der App-Inhalt gerendert werden kann.
     */
    fun showInstantHardwareOverlay(packageName: String, onUnlockRequested: () -> Unit) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SECURE, // Verhindert Screenshots & Task-Vorschau
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_overlay_lock, null)

        val btnUnlock = view.findViewById<Button>(R.id.btn_unlock_biometric)
        val btnHome = view.findViewById<Button>(R.id.btn_go_home)

        btnUnlock.setOnClickListener {
            onUnlockRequested()
        }

        btnHome.setOnClickListener {
            dismiss()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (_: Exception) {}
    }
}
