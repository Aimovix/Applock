package com.example.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isLocked: Boolean,
    val customTimeoutMinutes: Int = 0,
    val lockedAtTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_profiles")
data class SecurityProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val iconKey: String,
    val description: String,
    val packageNamesCsv: String,
    val isActive: Boolean = false
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val eventType: String, // UNLOCK_SUCCESS, FAILED_ATTEMPT, BIOMETRIC_CANCELED, PROFILE_APPLIED, LOCKOUT_TRIGGERED
    val authMethod: String, // PIN, BIOMETRIC, SYSTEM
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AppCategory(val labelDe: String) {
    ALL("Alle"),
    SOCIAL("Kommunikation"),
    FINANCE("Finanzen & Zahlung"),
    MEDIA("Fotos & Medien"),
    SYSTEM("System & Einstellungen"),
    PRODUCTIVITY("Produktivität")
}

enum class AppFilterMode(val labelDe: String) {
    ALL("Alle Apps"),
    LOCKED("Gesperrt"),
    UNLOCKED("Freigegeben"),
    SYSTEM("System-Apps")
}

enum class RelockPolicy(
    val key: String,
    val titleDe: String,
    val subtitleDe: String,
    val gracePeriodMs: Long
) {
    IMMEDIATE_WITH_GRACE(
        key = "IMMEDIATE_WITH_GRACE",
        titleDe = "Sofort (+ 4 Sek. Anti-Loop-Schutz)",
        subtitleDe = "Sperrt beim Verlassen, verhindert aber doppelte Sperr-Schleifen beim App-Wechsel",
        gracePeriodMs = 4_000L
    ),
    ONE_MINUTE(
        key = "ONE_MINUTE",
        titleDe = "Nach 1 Minute",
        subtitleDe = "Erneute PIN/Biometrie erst nach 60 Sekunden Inaktivität",
        gracePeriodMs = 60_000L
    ),
    FIVE_MINUTES(
        key = "FIVE_MINUTES",
        titleDe = "Nach 5 Minuten",
        subtitleDe = "Komfortabler Wechsel zwischen Apps für 5 Minuten",
        gracePeriodMs = 300_000L
    ),
    SCREEN_OFF(
        key = "SCREEN_OFF",
        titleDe = "Erst nach Bildschirm-Aus",
        subtitleDe = "Bleibt für die gesamte aktuelle Sitzung entsperrt",
        gracePeriodMs = 3_600_000L
    );

    companion object {
        fun fromKey(key: String): RelockPolicy =
            entries.firstOrNull { it.key == key } ?: IMMEDIATE_WITH_GRACE
    }
}

/**
 * Defines how biometric prompts are triggered so the app NEVER enters an infinite fingerprint loop.
 */
enum class BiometricTriggerMode(
    val key: String,
    val titleDe: String,
    val descriptionDe: String
) {
    MANUAL_BUTTON_ONLY(
        key = "MANUAL_BUTTON_ONLY",
        titleDe = "Nur auf Knopfdruck (Empfohlen – 100 % Loop-sicher)",
        descriptionDe = "Der Fingerabdruck-Dialog öffnet sich nur, wenn du aktiv auf den Biometrie-Button tippst."
    ),
    AUTO_ONCE_PER_SESSION(
        key = "AUTO_ONCE_PER_SESSION",
        titleDe = "Maximal 1x beim Öffnen (One-Shot Guard)",
        descriptionDe = "Fragt beim Öffnen genau 1x nach dem Fingerabdruck. Bei Abbruch sofort ruhiger PIN-Modus ohne Wiederholung."
    );

    companion object {
        fun fromKey(key: String): BiometricTriggerMode =
            entries.firstOrNull { it.key == key } ?: MANUAL_BUTTON_ONLY
    }
}

data class InstalledAppUiModel(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isSystemApp: Boolean,
    val isLocked: Boolean,
    val iconBitmap: ImageBitmap? = null,
    val isRecommendedSecurityTarget: Boolean = false
)

data class PermissionHealthStatus(
    val isAccessibilityEnabled: Boolean,
    val isUsageStatsGranted: Boolean,
    val isOverlayGranted: Boolean,
    val hasHardwareBiometrics: Boolean
) {
    val activeEnginesCount: Int
        get() = listOf(isAccessibilityEnabled, isUsageStatsGranted).count { it }
}
