package com.example.data

import com.example.security.SecurityEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class AppLockRepository(
    private val lockedAppDao: LockedAppDao,
    private val profileDao: SecurityProfileDao,
    private val auditLogDao: AuditLogDao
) {
    val lockedAppRecordsFlow: Flow<List<LockedAppEntity>> =
        lockedAppDao.observeAllLockedAppRecords().onEach { list ->
            val lockedSet = list.asSequence().filter { it.isLocked }.map { it.packageName }.toSet()
            SecurityEngine.updateLockedPackagesCache(lockedSet)
        }

    val profilesFlow: Flow<List<SecurityProfileEntity>> = profileDao.observeProfiles()
    val recentLogsFlow: Flow<List<AuditLogEntity>> = auditLogDao.observeRecentLogs()

    suspend fun ensureInitialSeedData() {
        val currentProfiles = profileDao.getProfilesSnapshot()
        if (currentProfiles.isEmpty()) {
            val defaultProfiles = listOf(
                SecurityProfileEntity(
                    name = "Basis-Schutz (Empfohlen)",
                    iconKey = "SHIELD",
                    description = "Schützt Systemeinstellungen, Galerie/Fotos, Nachrichten und Banking-Apps.",
                    packageNamesCsv = "com.android.settings,com.google.android.apps.photos,com.google.android.apps.messaging,com.android.vending,com.example.vault.banking",
                    isActive = true
                ),
                SecurityProfileEntity(
                    name = "Fokus & Arbeit",
                    iconKey = "WORK",
                    description = "Sperrt Social Media, Browser, Spiele und Medien während Arbeitsphasen.",
                    packageNamesCsv = "com.android.chrome,com.google.android.youtube,com.google.android.apps.photos,com.example.social.chat",
                    isActive = false
                ),
                SecurityProfileEntity(
                    name = "Öffentliches WLAN / Reise",
                    iconKey = "LOCK",
                    description = "Maximale Sicherheitsstufe: Sperrt alle Finanz-, E-Mail-, Datei- und System-Apps.",
                    packageNamesCsv = "com.android.settings,com.google.android.gm,com.google.android.apps.nbu.files,com.android.documentsui,com.example.vault.banking,com.example.vault.crypto",
                    isActive = false
                ),
                SecurityProfileEntity(
                    name = "Gast- & Kindersicherung",
                    iconKey = "FAMILY",
                    description = "Verhindert Käufe im Play Store, Systemänderungen und Zugriff auf private Chats.",
                    packageNamesCsv = "com.android.settings,com.android.vending,com.google.android.apps.messaging,com.google.android.dialer",
                    isActive = false
                )
            )
            profileDao.insertAllProfiles(defaultProfiles)
        }

        if (lockedAppDao.getRecordCount() == 0) {
            val initialLocked = listOf(
                LockedAppEntity(
                    packageName = "com.android.settings",
                    appName = "Einstellungen",
                    category = AppCategory.SYSTEM.name,
                    isLocked = true
                ),
                LockedAppEntity(
                    packageName = "com.google.android.apps.photos",
                    appName = "Fotos & Galerie",
                    category = AppCategory.MEDIA.name,
                    isLocked = true
                ),
                LockedAppEntity(
                    packageName = "com.google.android.apps.messaging",
                    appName = "Nachrichten (SMS/RCS)",
                    category = AppCategory.SOCIAL.name,
                    isLocked = true
                ),
                LockedAppEntity(
                    packageName = "com.example.vault.banking",
                    appName = "Online Banking & TAN",
                    category = AppCategory.FINANCE.name,
                    isLocked = true
                )
            )
            lockedAppDao.upsertAllLockedApps(initialLocked)
            SecurityEngine.updateLockedPackagesCache(initialLocked.map { it.packageName }.toSet())
        } else {
            val snapshot = lockedAppDao.getCurrentlyLockedAppsSnapshot()
            SecurityEngine.updateLockedPackagesCache(snapshot.map { it.packageName }.toSet())
        }

        if (auditLogDao.getLogCount() == 0) {
            val now = System.currentTimeMillis()
            auditLogDao.insertLog(
                AuditLogEntity(
                    packageName = "com.android.settings",
                    appName = "Einstellungen",
                    eventType = "UNLOCK_SUCCESS",
                    authMethod = "BIOMETRIC",
                    details = "Entsperrt via One-Shot Biometrie-Guard (4s Loop-Schutz aktiv)",
                    timestamp = now - 1000L * 60 * 18
                )
            )
            auditLogDao.insertLog(
                AuditLogEntity(
                    packageName = "com.example.vault.banking",
                    appName = "Online Banking & TAN",
                    eventType = "FAILED_ATTEMPT",
                    authMethod = "PIN",
                    details = "Ungültige PIN-Eingabe abgewehrt (Versuch 1/5)",
                    timestamp = now - 1000L * 60 * 45
                )
            )
            auditLogDao.insertLog(
                AuditLogEntity(
                    packageName = "system.applock.profile",
                    appName = "Basis-Schutz",
                    eventType = "PROFILE_APPLIED",
                    authMethod = "SYSTEM",
                    details = "Sicherheitsprofil 'Basis-Schutz' aktiviert (4 Apps geschützt)",
                    timestamp = now - 1000L * 60 * 120
                )
            )
        }
    }

    suspend fun setAppLockState(
        packageName: String,
        appName: String,
        category: AppCategory,
        isLocked: Boolean
    ) {
        lockedAppDao.upsertLockedApp(
            LockedAppEntity(
                packageName = packageName,
                appName = appName,
                category = category.name,
                isLocked = isLocked
            )
        )
    }

    suspend fun batchUpdateApps(
        apps: List<InstalledAppUiModel>,
        lockState: Boolean
    ) {
        val entities = apps.map { app ->
            LockedAppEntity(
                packageName = app.packageName,
                appName = app.appName,
                category = app.category.name,
                isLocked = lockState
            )
        }
        lockedAppDao.upsertAllLockedApps(entities)
    }

    suspend fun applySecurityProfile(
        profile: SecurityProfileEntity,
        allAvailableApps: List<InstalledAppUiModel>
    ) {
        val targetPackages = profile.packageNamesCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val updatedEntities = allAvailableApps.map { app ->
            LockedAppEntity(
                packageName = app.packageName,
                appName = app.appName,
                category = app.category.name,
                isLocked = targetPackages.contains(app.packageName)
            )
        }
        lockedAppDao.upsertAllLockedApps(updatedEntities)
        profileDao.setActiveProfile(profile.id)
        auditLogDao.insertLog(
            AuditLogEntity(
                packageName = "profile.${profile.id}",
                appName = profile.name,
                eventType = "PROFILE_APPLIED",
                authMethod = "SYSTEM",
                details = "Profil '${profile.name}' aktiviert (${targetPackages.size} Ziel-Pakete)",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun createCustomProfile(
        name: String,
        description: String,
        iconKey: String,
        selectedPackages: Set<String>
    ) {
        profileDao.insertProfile(
            SecurityProfileEntity(
                name = name.trim(),
                iconKey = iconKey,
                description = description.trim().ifEmpty { "Benutzerdefiniertes Profil (${selectedPackages.size} Apps)" },
                packageNamesCsv = selectedPackages.joinToString(","),
                isActive = false
            )
        )
    }

    suspend fun deleteProfile(profileId: Int) {
        profileDao.deleteProfile(profileId)
    }

    suspend fun recordAuditEvent(
        packageName: String,
        appName: String,
        eventType: String,
        authMethod: String,
        details: String
    ) {
        auditLogDao.insertLog(
            AuditLogEntity(
                packageName = packageName,
                appName = appName,
                eventType = eventType,
                authMethod = authMethod,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearAuditLogs() {
        auditLogDao.clearAllLogs()
    }
}
