package com.applock.magic.data

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zentraler, prozessweiter Zustandsmanager mit deterministischer Handoff-State-Machine.
 * Verhindert Re-Launch-Schleifen, Flatter-Starts und Deadlocks beim Multitasking.
 */
object LockStateManager {

    enum class SessionState {
        LOCKED,
        AUTHENTICATING,
        UNLOCK_HANDOFF,   // Brücke zwischen Biometrie-Erfolg und Übernahme des Vordergrund-Fokus durch Ziel-App
        UNLOCKED_ACTIVE   // App ist aktiv im Vordergrund und entsperrt
    }

    data class PackageSession(
        val state: SessionState,
        val timestamp: Long,
        val expiryMs: Long // Long.MAX_VALUE bei Screen-Off-Policy, konkreter Zeitstempel bei Timeouts
    )

    // Verhindert das mehrfache gleichzeitige Öffnen der LockScreenActivity
    val isLockScreenShowing = AtomicBoolean(false)

    // Paketname, für den gerade eine biometrische Authentifizierung läuft
    @Volatile
    var authenticatingPackage: String? = null

    // Zuletzt aktives echtes Anwendungspaket (kein Launcher/Keyboard)
    @Volatile
    var currentForegroundPackage: String? = null

    // Paketname -> Aktive Session
    private val sessions = ConcurrentHashMap<String, PackageSession>()

    // Max. Dauer für den Activity-Übergang nach erfolgreicher Authentifizierung (Handoff-Toleranz)
    private const val HANDOFF_TIMEOUT_MS = 2000L

    // Whitelist von Systempaketen (Tastaturen, SystemUI, Magic Portal, Biometrie-Dialoge)
    private val TRANSIENT_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin", // Gboard
        "com.touchtype.swiftkey",               // SwiftKey
        "com.huawei.ohos.inputmethod",          // Honor/Huawei Celia Keyboard
        "com.hihonor.inputmethod",              // Honor Tastatur
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.hihonor.android.internal.app",     // MagicOS Systemdialoge
        "com.hihonor.magictouch",               // Honor Magic Portal / Kapsel
        "com.hihonor.viewassistant"             // Honor Sidebar
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

    /**
     * Prüft, ob das Paket temporär freigegeben ist.
     */
    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val session = sessions[packageName] ?: return false
        val now = SystemClock.elapsedRealtime()

        return when (session.state) {
            SessionState.UNLOCK_HANDOFF -> {
                // Befindet sich im Handoff-Fenster: Sicher freigegeben
                if (now - session.timestamp <= HANDOFF_TIMEOUT_MS) {
                    true
                } else {
                    // Handoff abgelaufen ohne Fokus -> verfallen
                    sessions.remove(packageName)
                    false
                }
            }
            SessionState.UNLOCKED_ACTIVE -> {
                if (now <= session.expiryMs) {
                    true
                } else {
                    sessions.remove(packageName)
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Wird direkt nach erfolgreicher Authentifizierung aufgerufen.
     * Setzt den Status auf UNLOCK_HANDOFF.
     */
    fun grantUnlockHandoff(packageName: String, expiryMs: Long) {
        val now = SystemClock.elapsedRealtime()
        sessions[packageName] = PackageSession(
            state = SessionState.UNLOCK_HANDOFF,
            timestamp = now,
            expiryMs = expiryMs
        )
        authenticatingPackage = null
    }

    /**
     * Meldet, dass eine Anwendung im Vordergrund fokussiert wurde.
     * Verwaltet den deterministischen Übergang von UNLOCK_HANDOFF -> UNLOCKED_ACTIVE
     * und das sofortige Sperren bei echtem App-Wechsel.
     */
    fun onPackageFocused(newPackage: String, lockPolicy: Int) {
        if (isTransientPackage(newPackage)) return

        val now = SystemClock.elapsedRealtime()
        val previous = currentForegroundPackage
        currentForegroundPackage = newPackage

        // 1. Wenn wir zur soeben entsperrten App wechseln/zurückkehren:
        val targetSession = sessions[newPackage]
        if (targetSession != null && targetSession.state == SessionState.UNLOCK_HANDOFF) {
            // Übergang perfekt vollzogen: Sofort scharf schalten auf UNLOCKED_ACTIVE
            sessions[newPackage] = targetSession.copy(
                state = SessionState.UNLOCKED_ACTIVE,
                timestamp = now
            )
        }

        // 2. Vorherige Sessions prüfen, wenn der Nutzer zu einer anderen App/Launcher gewechselt ist:
        if (previous != null && previous != newPackage) {
            val prevSession = sessions[previous]
            if (prevSession != null) {
                if (lockPolicy == AppLockPreferences.POLICY_IMMEDIATE) {
                    // Sofort sperren: Freigabe der verlassenen App sofort entziehen
                    // (außer sie befindet sich noch im aktiven Handoff-Bruchteil < 2s)
                    if (prevSession.state != SessionState.UNLOCK_HANDOFF || (now - prevSession.timestamp > HANDOFF_TIMEOUT_MS)) {
                        sessions.remove(previous)
                    }
                }
            }
        }
    }

    fun lockAllImmediately() {
        sessions.clear()
        authenticatingPackage = null
        currentForegroundPackage = null
        isLockScreenShowing.set(false)
    }
}
