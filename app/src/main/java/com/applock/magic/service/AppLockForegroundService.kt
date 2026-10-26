package com.applock.magic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.applock.magic.R
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.data.LockStateManager
import com.applock.magic.ui.LockScreenActivity
import com.applock.magic.ui.MainActivity
import com.applock.magic.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppLockForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "shield_lock_guard_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AppLockForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var prefs: AppLockPreferences
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var monitorJob: Job? = null
    private var lastObservedPackage: String = ""

    override fun onCreate() {
        super.onCreate()
        prefs = AppLockPreferences(this)
        createNotificationChannel()
        startStickyForeground()
        startFallbackMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startStickyForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Fallback-Wächter via UsageStatsManager:
     * Dient als Sicherheitsnetz für den Fall, dass der Barrierefreiheitsdienst
     * durch aggressive Energiesparmodi temporär pausiert wurde.
     */
    private fun startFallbackMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@launch

            while (isActive) {
                // Wenn der Barrierefreiheitsdienst aktiv ist, übernimmt er die 0ms-Echtzeiterkennung.
                // Der ForegroundService pausiert in dieser Zeit das CPU-lastige UsageStats-Polling,
                // um Race Conditions und unnötigen Akkuverbrauch vollständig zu verhindern.
                if (PermissionHelper.isAccessibilityServiceEnabled(this@AppLockForegroundService)) {
                    delay(2000L)
                    continue
                }

                // Nur wenn der AccessibilityService inaktiv/gekillt ist, springt der Fallback-Timer ein:
                delay(350L)

                // Wenn bereits ein Sperrbildschirm angezeigt wird: Keine Konkurrenz erzeugen!
                if (LockStateManager.isLockScreenShowing.get()) continue

                val currentPkg = getForegroundPackage(usageStatsManager)

                if (currentPkg != null &&
                    currentPkg != packageName &&
                    !LockStateManager.isTransientPackage(currentPkg) &&
                    currentPkg != lastObservedPackage
                ) {
                    lastObservedPackage = currentPkg
                    prefs.handleAppSwitch(currentPkg)

                    if (prefs.isPackageLocked(currentPkg) && LockStateManager.authenticatingPackage != currentPkg) {
                        LockStateManager.authenticatingPackage = currentPkg
                        launchLockScreen(currentPkg)
                    }
                }
            }
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 2500, time)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    private fun launchLockScreen(targetPkg: String) {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, targetPkg)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        startActivity(lockIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
    }
}
