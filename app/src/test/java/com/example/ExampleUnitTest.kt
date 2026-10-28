package com.example

import com.example.data.CredentialHasher
import com.example.security.SecurityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    @Before
    fun resetSecurityEngine() {
        SecurityEngine.isMasterProtectionActive = true
        SecurityEngine.currentRelockGracePeriodMs = 4_000L
        SecurityEngine.onLockGateClosed()
        SecurityEngine.clearAllSessionUnlockTokens()
        SecurityEngine.updateLockedPackagesCache(setOf("com.android.settings", "com.example.vault.banking"))
    }

    @Test
    fun antiLoop_ignoresSystemUiAndOwnPackageEvents() {
        val futureNow = System.currentTimeMillis() + 5_000L
        assertFalse(SecurityEngine.shouldInterceptPackage("com.android.systemui", futureNow))
        assertFalse(SecurityEngine.shouldInterceptPackage("android", futureNow))
        assertFalse(SecurityEngine.shouldInterceptPackage("com.aistudio.applock.shldvx", futureNow))
        assertFalse(SecurityEngine.shouldInterceptPackage("com.android.settings.biometrics", futureNow))
    }

    @Test
    fun antiLoop_oneShotAutoBiometricAllowanceFiresAtMostOncePerSession() {
        SecurityEngine.onLockGateOpened()
        assertTrue(SecurityEngine.consumeOneShotAutoBiometricAllowance())
        // Subsequent calls during the same lock gate session (e.g. onResume after biometric cancel) must return false
        assertFalse(SecurityEngine.consumeOneShotAutoBiometricAllowance())
        assertFalse(SecurityEngine.consumeOneShotAutoBiometricAllowance())
    }

    @Test
    fun antiLoop_sessionUnlockTokenPreventsImmediateRelockLoop() {
        val baseTime = System.currentTimeMillis() + 5_000L
        assertTrue(SecurityEngine.shouldInterceptPackage("com.android.settings", baseTime))

        // Unlock package -> grants at least 4s grace window
        SecurityEngine.markPackageUnlockedForSession("com.android.settings", 4_000L)
        assertFalse(
            SecurityEngine.shouldInterceptPackage(
                "com.android.settings",
                System.currentTimeMillis() + 2_000L
            )
        )
    }

    @Test
    fun credentialHasher_saltedSha256VerifiesDeterministically() {
        val salt = CredentialHasher.generateSalt()
        val hash1 = CredentialHasher.hashPin("1234", salt)
        val hash2 = CredentialHasher.hashPin("1234", salt)
        val wrongHash = CredentialHasher.hashPin("9999", salt)
        assertEquals(hash1, hash2)
        assertFalse(hash1 == wrongHash)
    }
}
