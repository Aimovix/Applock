package com.applock.magic.data

import android.content.Context
import android.content.SharedPreferences

class AppLockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield_lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_RELOCK_TIMEOUT = "relock_timeout_ms"
        private const val KEY_PROTECT_SETTINGS = "protect_system_settings"

        // Wichtige System-Pakete zum Schutz vor Deinstallation und Force-Stop
        val SYSTEM_CRITICAL_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.hihonor.android.packageinstaller",
            "com.android.vending"
        )
    }

    // Temporäre Freigaben im RAM (Paketname -> Ablaufzeitpunkt in Millisekunden)
    private val unlockedSessions = mutableMapOf<String, Long>()

    fun getLockedPackages(): Set<String> {
        val saved = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
        return if (isProtectSettingsEnabled()) {
            saved + SYSTEM_CRITICAL_PACKAGES
        } else {
            saved
        }
    }

    fun setPackageLocked(packageName: String, locked: Boolean) {
        val current = (prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    fun isPackageLocked(packageName: String): Boolean {
        if (!getLockedPackages().contains(packageName)) return false
        val expiry = unlockedSessions[packageName] ?: 0L
        return System.currentTimeMillis() > expiry
    }

    fun unlockPackageTemporarily(packageName: String, durationMs: Long = 30_000L) {
        unlockedSessions[packageName] = System.currentTimeMillis() + durationMs
    }

    fun lockAllImmediately() {
        unlockedSessions.clear()
    }

    fun isProtectSettingsEnabled(): Boolean {
        return prefs.getBoolean(KEY_PROTECT_SETTINGS, true)
    }

    fun setProtectSettingsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECT_SETTINGS, enabled).apply()
    }
}
