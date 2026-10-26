package com.applock.magic.data

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zentraler, prozessweiter Zustandsmanager zur Vermeidung von Race Conditions,
 * Re-Launch-Schleifen ("Stuck zwischen Apps") und Deadlocks.
 */
object LockStateManager {

    // Verhindert das mehrfache gleichzeitige Öffnen der LockScreenActivity
    val isLockScreenShowing = AtomicBoolean(false)

    // Paketname, für den gerade eine biometrische Authentifizierung läuft
    @Volatile
    var authenticatingPackage: String? = null

    // Zuletzt aktives echtes Anwendungspaket (kein Launcher/Keyboard)
    @Volatile
    var currentForegroundPackage: String? = null

    // Temporäre Freigaben (Paketname -> Ablaufzeitpunkt in monotoner SystemClock.elapsedRealtime())
    private val unlockedSessions = ConcurrentHashMap<String, Long>()

    // Whitelist von Systempaketen, die den Fokus kurzzeitig stehlen können,
    // ohne dass die geschützte App als "verlassen" gilt (z. B. Tastaturen, SystemUI, Biometrie-Dialoge)
    private val TRANSIENT_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin", // Gboard
        "com.touchtype.swiftkey",               // SwiftKey
        "com.huawei.ohos.inputmethod",          // Honor/Huawei Celia Keyboard
        "com.hihonor.inputmethod",              // Honor Tastatur
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.hihonor.android.internal.app"      // MagicOS Systemdialoge
    )

    // Bekannte Launcher / Home-Screens
    private val LAUNCHER_PACKAGES = setOf(
        "com.hihonor.android.launcher",
        "com.huawei.android.launcher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.teslacoilsw.launcher"
    )

    fun isTransientPackage(packageName: String): Boolean {
        if (TRANSIENT_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("ime")
    }

    fun isLauncherPackage(packageName: String): Boolean {
        return LAUNCHER_PACKAGES.contains(packageName)
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val expiry = unlockedSessions[packageName] ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now <= expiry) {
            return true
        }
        unlockedSessions.remove(packageName)
        return false
    }

    fun grantUnlock(packageName: String, durationMs: Long) {
        val expiry = if (durationMs <= 0) {
            Long.MAX_VALUE // Gültig bis Display-Aus oder explizites Sperren
        } else {
            SystemClock.elapsedRealtime() + durationMs
        }
        unlockedSessions[packageName] = expiry
        authenticatingPackage = null
    }

    fun revokeUnlock(packageName: String) {
        unlockedSessions.remove(packageName)
        if (authenticatingPackage == packageName) {
            authenticatingPackage = null
        }
    }

    fun lockAllImmediately() {
        unlockedSessions.clear()
        authenticatingPackage = null
        currentForegroundPackage = null
    }
}
