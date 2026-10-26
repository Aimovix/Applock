package com.applock.magic.ui

import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import com.applock.magic.util.IconCustomizerManager

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

    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Screenshot- und Recent-Task-Schutz aktivieren (Schutz vor Inhalts-Leaks)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. Zustand sofort registrieren, um Doppelstarts durch Event-Races zu verhindern
        LockStateManager.isLockScreenShowing.set(true)

        // 3. Opaque Schutzmaske laden (fester Vollton gegen MagicOS 11 Liquid Glass Blur-Bugs)
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

    override fun onResume() {
        super.onResume()
        // Unter Android 17 / MagicOS 11 hat die sichtbare Activity vollen While-In-Use (WIU) Status.
        // Erst hier greift requestAudioFocus garantiert gegen PiP-Hintergrundaudio!
        suppressAudioPlayback()
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        // Verhindert, dass der Honor Magic 8 Pro AI-Button / Magic Portal Metadaten abgreift
        outContent?.structuredData = null
        outContent?.webUri = null
    }

    override fun onProvideAssistData(data: Bundle?) {
        super.onProvideAssistData(data)
        data?.clear()
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
        if (packageName.isNullOrBlank() || prefs.isHideAppDetailsOnLock()) {
            tvTargetAppName.text = getString(R.string.lock_screen_title)
            imgTargetAppIcon.setImageResource(android.R.drawable.ic_lock_lock)
            tvLockSubtitle.text = getString(R.string.lock_screen_subtitle)
            return
        }

        try {
            val customBitmap = IconCustomizerManager.getCustomIcon(this, packageName)
            val customLabel = IconCustomizerManager.getCustomLabel(this, packageName)

            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val defaultLabel = pm.getApplicationLabel(appInfo).toString()
            val defaultIcon = pm.getApplicationIcon(appInfo)

            val effectiveLabel = customLabel?.ifBlank { null } ?: defaultLabel
            tvTargetAppName.text = effectiveLabel
            if (customBitmap != null) {
                imgTargetAppIcon.setImageBitmap(customBitmap)
            } else {
                imgTargetAppIcon.setImageDrawable(defaultIcon)
            }
            tvLockSubtitle.text = getString(R.string.lock_screen_subtitle_with_app, effectiveLabel)
        } catch (_: Exception) {
            tvTargetAppName.text = getString(R.string.lock_screen_title)
            imgTargetAppIcon.setImageResource(android.R.drawable.ic_lock_lock)
            tvLockSubtitle.text = getString(R.string.lock_screen_subtitle)
        }
    }

    private fun startBiometricAuthentication() {
        if (isAuthenticating) return
        isAuthenticating = true

        val appLabel = if (prefs.isHideAppDetailsOnLock()) {
            getString(R.string.lock_screen_title)
        } else {
            val customLabel = targetPackage?.let { IconCustomizerManager.getCustomLabel(this, it) }
            customLabel?.ifBlank { null } ?: targetPackage?.let { pkg ->
                try {
                    val pm = packageManager
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    null
                }
            } ?: getString(R.string.lock_screen_title)
        }

        biometricAuthManager.authenticate(
            title = "$appLabel entsperren",
            subtitle = getString(R.string.biometric_subtitle),
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

    private fun suppressAudioPlayback() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
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

    private fun releaseAudioSuppression() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun closeLockScreen(unlocked: Boolean) {
        releaseAudioSuppression()
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
        releaseAudioSuppression()
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
        releaseAudioSuppression()
        LockStateManager.isLockScreenShowing.set(false)
        if (LockStateManager.authenticatingPackage == targetPackage) {
            LockStateManager.authenticatingPackage = null
        }
    }
}
