package com.applock.magic.security

import android.app.ActivityOptions
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.ui.LockScreenActivity

class PipAndAudioGuard(
    private val context: Context,
    private val prefs: AppLockPreferences
) {

    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val pipListener = AppOpsManager.OnOpActiveChangedListener { op, _, packageName, active ->
        if (op == AppOpsManager.OPSTR_PICTURE_IN_PICTURE && active) {
            val pkg = packageName ?: return@OnOpActiveChangedListener
            if (prefs.isPackageLocked(pkg)) {
                suppressAudioPlayback()
                triggerLock(pkg)
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && appOps != null) {
            try {
                appOps.startWatchingActive(
                    arrayOf(AppOpsManager.OPSTR_PICTURE_IN_PICTURE),
                    context.mainExecutor,
                    pipListener
                )
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (appOps != null) {
            try {
                appOps.stopWatchingActive(pipListener)
            } catch (_: Exception) {}
        }
        releaseAudioSuppression()
    }

    fun suppressAudioPlayback() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener {}
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    fun releaseAudioSuppression() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun triggerLock(packageName: String) {
        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
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
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
        }
        context.startActivity(lockIntent, options.toBundle())
    }
}
