package com.example.security

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Explicit state machine for biometric authentication during a single Lock Gate session.
 * Prevents the infamous AppLock bug where dismissing or failing the system BiometricPrompt
 * triggers onResume() / TYPE_WINDOW_STATE_CHANGED and re-opens the fingerprint dialog infinitely.
 */
enum class BiometricGateSessionState {
    /** Waiting for user to tap the fingerprint button (or before one-shot auto-trigger). */
    READY_FOR_MANUAL_TAP,

    /** BiometricPrompt is currently active on screen. Do NOT launch another prompt. */
    PROMPT_CURRENTLY_SHOWING,

    /**
     * User canceled, dismissed, or failed the biometric prompt.
     * Auto-trigger is permanently disabled for this session; calm PIN pad is active.
     */
    FALLBACK_TO_PIN_ONLY,

    /** User successfully authenticated. */
    AUTHENTICATED
}

object SecurityEngine {
    // O(1) lock lookup set updated reactively from Room database
    private val lockedPackagesSet = ConcurrentHashMap.newKeySet<String>()

    // Map of packageName -> unlockExpirationTimestampMillis
    private val unlockedSessionTokens = ConcurrentHashMap<String, Long>()

    // Mutex preventing duplicate LockOverlayGateActivity launches when BiometricPrompt opens/closes
    private val isLockGateCurrentlyVisible = AtomicBoolean(false)

    // Tracks whether one-shot auto-biometric has already fired in the current lock session
    private val hasAutoBiometricFiredInSession = AtomicBoolean(false)

    // Timestamp when the last lock gate was dismissed/unlocked to prevent window-focus bounce loops
    private val lastGateDismissTimestampMs = AtomicLong(0L)

    @Volatile
    var isMasterProtectionActive: Boolean = true

    @Volatile
    var currentRelockGracePeriodMs: Long = 4_000L

    @Volatile
    private var lastForegroundPackage: String = ""

    /**
     * Packages that MUST NEVER be intercepted by the AccessibilityService.
     * Filtering out SystemUI, Biometric dialogs, Permission Controllers, and our own package
     * breaks any possibility of a self-triggering fingerprint loop.
     */
    private val SYSTEM_WHITELIST_PREFIXES = listOf(
        "com.aistudio.applock",
        "com.example",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.settings.biometrics",
        "com.samsung.android.biometrics",
        "com.miui.securitycenter"
    )

    private val SYSTEM_EXACT_WHITELIST = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    fun updateLockedPackagesCache(packages: Set<String>) {
        lockedPackagesSet.clear()
        lockedPackagesSet.addAll(packages)
    }

    fun isPackageLockedInCache(packageName: String): Boolean {
        return lockedPackagesSet.contains(packageName)
    }

    fun isWhitelistedSystemOrSelfPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (SYSTEM_EXACT_WHITELIST.contains(packageName)) return true
        return SYSTEM_WHITELIST_PREFIXES.any { prefix -> packageName.startsWith(prefix) }
    }

    /**
     * Called when LockGateScreen opens (either via Activity or In-App Simulator).
     * Resets the one-shot biometric guard for this new session and engages the gate mutex.
     */
    fun onLockGateOpened() {
        isLockGateCurrentlyVisible.set(true)
        hasAutoBiometricFiredInSession.set(false)
    }

    /**
     * Called when LockGateScreen closes or succeeds.
     * Releases the gate mutex and records a post-dismissal bounce guard timestamp.
     */
    fun onLockGateClosed() {
        isLockGateCurrentlyVisible.set(false)
        hasAutoBiometricFiredInSession.set(false)
        lastGateDismissTimestampMs.set(System.currentTimeMillis())
    }

    /**
     * Atomically checks and consumes the single allowed auto-biometric trigger for this session.
     * Even if Compose recomposes or Activity lifecycle onResume() fires 10 times, this returns
     * `true` at most ONCE per lock session.
     */
    fun consumeOneShotAutoBiometricAllowance(): Boolean {
        return hasAutoBiometricFiredInSession.compareAndSet(false, true)
    }

    /**
     * Grants a temporary unlock token to [packageName] after successful PIN or Biometric auth.
     * Enforces a minimum 4-second anti-loop grace window even if policy is "Immediate".
     */
    fun markPackageUnlockedForSession(packageName: String, customGraceMs: Long? = null) {
        val effectiveGrace = maxOf(customGraceMs ?: currentRelockGracePeriodMs, 4_000L)
        val expiresAt = System.currentTimeMillis() + effectiveGrace
        unlockedSessionTokens[packageName] = expiresAt
        lastGateDismissTimestampMs.set(System.currentTimeMillis())
        isLockGateCurrentlyVisible.set(false)
    }

    fun isPackageCurrentlyUnlockedInSession(packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = unlockedSessionTokens[packageName] ?: return false
        if (nowMs < expiresAt) {
            return true
        }
        unlockedSessionTokens.remove(packageName)
        return false
    }

    fun clearAllSessionUnlockTokens() {
        unlockedSessionTokens.clear()
    }

    /**
     * O(1) decision engine invoked by AppLockAccessibilityService when a window state changes.
     * Guaranteed free of DB queries, blocking calls, or recursive loop triggers.
     */
    fun shouldInterceptPackage(
        packageName: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Master switch check
        if (!isMasterProtectionActive) return false

        // 2. Ignore SystemUI, biometric dialogs, launchers, and our own package
        if (isWhitelistedSystemOrSelfPackage(packageName)) return false

        // 3. If Lock Gate is already visible, NEVER launch a second Lock Gate on top
        if (isLockGateCurrentlyVisible.get()) return false

        // 4. Global 1500ms post-dismissal cooldown to prevent window focus bounce loops
        if (nowMs - lastGateDismissTimestampMs.get() < 1_500L) return false

        // 5. Check if user switched away from a previously unlocked package under IMMEDIATE mode
        if (lastForegroundPackage.isNotEmpty() && lastForegroundPackage != packageName) {
            lastForegroundPackage = packageName
        } else {
            lastForegroundPackage = packageName
        }

        // 6. Check if package is in our O(1) locked set
        if (!lockedPackagesSet.contains(packageName)) return false

        // 7. Check if package already holds a valid session unlock token
        if (isPackageCurrentlyUnlockedInSession(packageName, nowMs)) return false

        return true
    }
}
