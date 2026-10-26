package com.applock.magic.data

import android.content.Context
import android.content.SharedPreferences

class AppLockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield_lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_PROTECT_SETTINGS = "protect_system_settings"
        private const val KEY_LOCK_POLICY = "lock_policy_type"

        // Sperr-Richtlinien
        const val POLICY_SCREEN_OFF = 0     // Standard & Empfohlen: Bleibt aktiv, bis das Display ausgeschaltet wird
        const val POLICY_IMMEDIATE = 1      // Sofort bei App-Wechsel / Verlassen sperren
        const val POLICY_TIMEOUT_1MIN = 2   // 1 Minute Gnadenfrist nach Entsperrung
        const val POLICY_TIMEOUT_5MIN = 3   // 5 Minuten Gnadenfrist nach Entsperrung

        // Kritische System-Pakete zum Schutz vor Deinstallation, Datenlöschung und Force-Stop
        val SYSTEM_CRITICAL_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.hihonor.android.packageinstaller",
            "com.huawei.packageinstaller",
            "com.android.vending",
            "com.hihonor.systemmanager",
            "com.huawei.systemmanager",
            "com.hihonor.appmarket",
            "com.huawei.appmarket"
        )
    }

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
        // Nicht in der Sperrliste
        if (!getLockedPackages().contains(packageName)) return false

        // Temporäre Freigabe aktiv?
        return !LockStateManager.isTemporarilyUnlocked(packageName)
    }

    fun unlockPackageTemporarily(packageName: String) {
        val timeoutMs = when (getLockPolicy()) {
            POLICY_SCREEN_OFF -> -1L // Bleibt aktiv bis Display-Aus
            POLICY_IMMEDIATE -> 0L  // Bleibt aktiv solange im Vordergrund (mit 15s Mindestschutz)
            POLICY_TIMEOUT_1MIN -> 60_000L
            POLICY_TIMEOUT_5MIN -> 300_000L
            else -> -1L
        }
        LockStateManager.grantUnlock(packageName, timeoutMs)
    }

    fun handleAppSwitch(newPackage: String) {
        val previous = LockStateManager.currentForegroundPackage
        if (previous != null && previous != newPackage && !LockStateManager.isTransientPackage(newPackage)) {
            // Wenn die Richtlinie "Sofort sperren" ist, verfällt die Freigabe der verlassenen App,
            // sofern sie nicht gerade eben (unter 8s) entsperrt wurde
            if (getLockPolicy() == POLICY_IMMEDIATE && getLockedPackages().contains(previous)) {
                LockStateManager.revokeUnlock(previous, force = false)
            }
        }
        if (!LockStateManager.isTransientPackage(newPackage)) {
            LockStateManager.currentForegroundPackage = newPackage
        }
    }

    fun lockAllImmediately() {
        LockStateManager.lockAllImmediately()
    }

    fun getLockPolicy(): Int {
        return prefs.getInt(KEY_LOCK_POLICY, POLICY_SCREEN_OFF)
    }

    fun setLockPolicy(policy: Int) {
        prefs.edit().putInt(KEY_LOCK_POLICY, policy).apply()
    }

    fun isProtectSettingsEnabled(): Boolean {
        return prefs.getBoolean(KEY_PROTECT_SETTINGS, true)
    }

    fun setProtectSettingsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECT_SETTINGS, enabled).apply()
    }
}
