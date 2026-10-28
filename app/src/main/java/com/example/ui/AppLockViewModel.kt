package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppCategory
import com.example.data.AppFilterMode
import com.example.data.AppLockDatabase
import com.example.data.AppLockRepository
import com.example.data.AuditLogEntity
import com.example.data.BiometricTriggerMode
import com.example.data.InstalledAppUiModel
import com.example.data.PermissionHealthStatus
import com.example.data.RelockPolicy
import com.example.data.SecurityPreferences
import com.example.data.SecurityProfileEntity
import com.example.data.SecuritySettingsState
import com.example.security.PackageScanner
import com.example.security.SecurityEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppLockNavTab(val labelDe: String) {
    VAULT("App-Tresor"),
    PROFILES("Profile"),
    LOGS("Protokoll"),
    SETTINGS("Sicherheit")
}

data class AppLockUiState(
    val isLoading: Boolean = true,
    val selectedTab: AppLockNavTab = AppLockNavTab.VAULT,
    val searchQuery: String = "",
    val selectedFilterMode: AppFilterMode = AppFilterMode.ALL,
    val selectedCategory: AppCategory = AppCategory.ALL,
    val allApps: List<InstalledAppUiModel> = emptyList(),
    val filteredApps: List<InstalledAppUiModel> = emptyList(),
    val lockedAppsCount: Int = 0,
    val profiles: List<SecurityProfileEntity> = emptyList(),
    val activeProfileName: String = "Basis-Schutz",
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val blockedAttemptsCount: Int = 0,
    val settings: SecuritySettingsState = SecuritySettingsState(),
    val currentPinHint: String = "1234",
    val permissionHealth: PermissionHealthStatus = PermissionHealthStatus(
        isAccessibilityEnabled = false,
        isUsageStatsGranted = false,
        isOverlayGranted = false,
        hasHardwareBiometrics = true
    ),
    val previewLockTargetApp: InstalledAppUiModel? = null,
    val statusBannerMessage: String? = null
)

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppLockDatabase.getInstance(application)
    private val repository = AppLockRepository(
        db.lockedAppDao(),
        db.securityProfileDao(),
        db.auditLogDao()
    )
    private val securityPreferences = SecurityPreferences(application)

    private val _selectedTab = MutableStateFlow(AppLockNavTab.VAULT)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilterMode = MutableStateFlow(AppFilterMode.ALL)
    private val _selectedCategory = MutableStateFlow(AppCategory.ALL)
    private val _scannedBaseApps = MutableStateFlow<List<InstalledAppUiModel>>(emptyList())
    private val _permissionHealth = MutableStateFlow(
        PackageScanner.inspectPermissionHealth(application)
    )
    private val _previewLockTargetApp = MutableStateFlow<InstalledAppUiModel?>(null)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.ensureInitialSeedData()
            val initialApps = PackageScanner.loadInstalledAndCuratedApps(
                context = getApplication(),
                lockedPackageMap = emptyMap()
            )
            _scannedBaseApps.value = initialApps
        }
    }

    private data class FilterState(
        val tab: AppLockNavTab,
        val query: String,
        val filterMode: AppFilterMode,
        val category: AppCategory,
        val previewTarget: InstalledAppUiModel?,
        val bannerMessage: String?
    )

    private val filterStateFlow = combine(
        _selectedTab,
        _searchQuery,
        _selectedFilterMode,
        _selectedCategory,
        combine(_previewLockTargetApp, _statusBannerMessage) { p, b -> p to b }
    ) { tab, query, mode, category, previewAndBanner ->
        FilterState(
            tab = tab,
            query = query,
            filterMode = mode,
            category = category,
            previewTarget = previewAndBanner.first,
            bannerMessage = previewAndBanner.second
        )
    }

    private data class DataSourceBundle(
        val baseApps: List<InstalledAppUiModel>,
        val lockedRecords: List<com.example.data.LockedAppEntity>,
        val profiles: List<SecurityProfileEntity>,
        val logs: List<AuditLogEntity>,
        val settings: SecuritySettingsState
    )

    private val dataSourceBundleFlow = combine(
        _scannedBaseApps,
        repository.lockedAppRecordsFlow,
        repository.profilesFlow,
        repository.recentLogsFlow,
        securityPreferences.settingsState
    ) { baseApps, lockedRecords, profiles, logs, settings ->
        SecurityEngine.isMasterProtectionActive = settings.masterProtectionEnabled
        SecurityEngine.currentRelockGracePeriodMs = settings.relockPolicy.gracePeriodMs
        DataSourceBundle(baseApps, lockedRecords, profiles, logs, settings)
    }

    val uiState: StateFlow<AppLockUiState> = combine(
        dataSourceBundleFlow,
        filterStateFlow,
        _permissionHealth
    ) { data, filter, permHealth ->
        val lockedMap = data.lockedRecords.associate { it.packageName to it.isLocked }
        val mergedApps = data.baseApps.map { app ->
            app.copy(isLocked = lockedMap[app.packageName] ?: false)
        }.sortedWith(
            compareByDescending<InstalledAppUiModel> { it.isLocked }
                .thenByDescending { it.isRecommendedSecurityTarget }
                .thenBy { it.appName.lowercase() }
        )

        val q = filter.query.trim().lowercase()
        val filtered = mergedApps.filter { app ->
            val matchesQuery = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            val matchesMode = when (filter.filterMode) {
                AppFilterMode.ALL -> true
                AppFilterMode.LOCKED -> app.isLocked
                AppFilterMode.UNLOCKED -> !app.isLocked
                AppFilterMode.SYSTEM -> app.isSystemApp
            }
            val matchesCategory = filter.category == AppCategory.ALL || app.category == filter.category
            matchesQuery && matchesMode && matchesCategory
        }

        val lockedCount = mergedApps.count { it.isLocked }
        val activeProfile = data.profiles.firstOrNull { it.isActive }?.name ?: "Benutzerdefiniert"
        val failedCount = data.logs.count { it.eventType == "FAILED_ATTEMPT" }

        AppLockUiState(
            isLoading = data.baseApps.isEmpty(),
            selectedTab = filter.tab,
            searchQuery = filter.query,
            selectedFilterMode = filter.filterMode,
            selectedCategory = filter.category,
            allApps = mergedApps,
            filteredApps = filtered,
            lockedAppsCount = lockedCount,
            profiles = data.profiles,
            activeProfileName = activeProfile,
            auditLogs = data.logs,
            blockedAttemptsCount = failedCount,
            settings = data.settings,
            currentPinHint = securityPreferences.getCurrentDemoPinHint(),
            permissionHealth = permHealth,
            previewLockTargetApp = filter.previewTarget,
            statusBannerMessage = filter.bannerMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLockUiState()
    )

    fun refreshPermissionHealth() {
        _permissionHealth.value = PackageScanner.inspectPermissionHealth(getApplication())
    }

    fun selectTab(tab: AppLockNavTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilterMode(mode: AppFilterMode) {
        _selectedFilterMode.value = mode
    }

    fun selectCategory(category: AppCategory) {
        _selectedCategory.value = category
    }

    fun dismissStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun toggleAppLock(app: InstalledAppUiModel) {
        viewModelScope.launch {
            val newLockedState = !app.isLocked
            repository.setAppLockState(
                packageName = app.packageName,
                appName = app.appName,
                category = app.category,
                isLocked = newLockedState
            )
            _statusBannerMessage.value = if (newLockedState) {
                "${app.appName} ist jetzt mit PIN/Biometrie geschützt"
            } else {
                "${app.appName} wurde freigegeben"
            }
        }
    }

    fun batchSetFilteredAppsLock(lockState: Boolean) {
        val currentFiltered = uiState.value.filteredApps
        if (currentFiltered.isEmpty()) return
        viewModelScope.launch {
            repository.batchUpdateApps(currentFiltered, lockState)
            _statusBannerMessage.value = if (lockState) {
                "${currentFiltered.size} sichtbare Apps gesperrt"
            } else {
                "${currentFiltered.size} sichtbare Apps entsperrt"
            }
        }
    }

    fun applyProfile(profile: SecurityProfileEntity) {
        val allApps = uiState.value.allApps
        if (allApps.isEmpty()) return
        viewModelScope.launch {
            repository.applySecurityProfile(profile, allApps)
            _statusBannerMessage.value = "Sicherheitsprofil „${profile.name}“ aktiviert"
        }
    }

    fun createProfile(name: String, description: String, iconKey: String, selectedPackages: Set<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createCustomProfile(name, description, iconKey, selectedPackages)
            _statusBannerMessage.value = "Profil „${name.trim()}“ erstellt"
        }
    }

    fun deleteProfile(profileId: Int) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
        }
    }

    fun openLockGatePreview(targetApp: InstalledAppUiModel? = null) {
        val chosen = targetApp
            ?: uiState.value.allApps.firstOrNull { it.isLocked }
            ?: uiState.value.allApps.firstOrNull()
            ?: InstalledAppUiModel(
                packageName = "com.android.settings",
                appName = "Einstellungen",
                category = AppCategory.SYSTEM,
                isSystemApp = true,
                isLocked = true
            )
        _previewLockTargetApp.value = chosen
    }

    fun closeLockGatePreview() {
        _previewLockTargetApp.value = null
    }

    fun verifyCandidatePin(candidatePin: String): Boolean {
        return securityPreferences.verifyPin(candidatePin)
    }

    fun onPreviewUnlockSuccess(targetApp: InstalledAppUiModel, authMethod: String) {
        viewModelScope.launch {
            repository.recordAuditEvent(
                packageName = targetApp.packageName,
                appName = targetApp.appName,
                eventType = "UNLOCK_SUCCESS",
                authMethod = authMethod,
                details = "Entsperrt via $authMethod (${uiState.value.settings.relockPolicy.titleDe})"
            )
            _previewLockTargetApp.value = null
            _statusBannerMessage.value = "${targetApp.appName} erfolgreich via $authMethod entsperrt!"
        }
    }

    fun onPreviewFailedAttempt(targetApp: InstalledAppUiModel, attempt: Int) {
        viewModelScope.launch {
            repository.recordAuditEvent(
                packageName = targetApp.packageName,
                appName = targetApp.appName,
                eventType = "FAILED_ATTEMPT",
                authMethod = "PIN",
                details = "Abgewehrter PIN-Versuch #$attempt bei ${targetApp.appName}"
            )
        }
    }

    fun clearAllAuditLogs() {
        viewModelScope.launch {
            repository.clearAuditLogs()
            _statusBannerMessage.value = "Sicherheits-Protokoll bereinigt"
        }
    }

    fun setMasterProtectionEnabled(enabled: Boolean) {
        securityPreferences.setMasterProtectionEnabled(enabled)
        SecurityEngine.isMasterProtectionActive = enabled
        _statusBannerMessage.value = if (enabled) {
            "Echtzeit-Schutz aktiviert"
        } else {
            "Echtzeit-Schutz vorübergehend pausiert"
        }
    }

    fun updateMasterPin(newPin: String) {
        securityPreferences.updateMasterPin(newPin)
        _statusBannerMessage.value = "Neuer ${newPin.length}-stelliger Master-PIN gespeichert ($newPin)"
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityPreferences.setBiometricEnabled(enabled)
    }

    fun setBiometricTriggerMode(mode: BiometricTriggerMode) {
        securityPreferences.setBiometricTriggerMode(mode)
        _statusBannerMessage.value = "Biometrie-Modus: ${mode.titleDe}"
    }

    fun setScrambleKeypad(enabled: Boolean) {
        securityPreferences.setScrambleKeypad(enabled)
    }

    fun setStealthHideKeyRipple(enabled: Boolean) {
        securityPreferences.setStealthHideKeyRipple(enabled)
    }

    fun setRelockPolicy(policy: RelockPolicy) {
        securityPreferences.setRelockPolicy(policy)
        SecurityEngine.currentRelockGracePeriodMs = policy.gracePeriodMs
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        securityPreferences.setDarkThemeEnabled(enabled)
    }
}
