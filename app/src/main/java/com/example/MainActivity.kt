package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppLockNavTab
import com.example.ui.AppLockUiState
import com.example.ui.AppLockViewModel
import com.example.ui.lockgate.LockGateScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appLockViewModel: AppLockViewModel = viewModel()
            val uiState by appLockViewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = uiState.settings.darkThemeEnabled) {
                AppLockRootContent(
                    uiState = uiState,
                    viewModel = appLockViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockRootContent(
    uiState: AppLockUiState,
    viewModel: AppLockViewModel
) {
    val previewTarget = uiState.previewLockTargetApp

    // If user is on a secondary tab, Back button returns cleanly to the Vault tab
    if (previewTarget == null && uiState.selectedTab != AppLockNavTab.VAULT) {
        BackHandler {
            viewModel.selectTab(AppLockNavTab.VAULT)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "AppLock",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AppLock",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v2.0 • Anti-Loop Biometric Engine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.openLockGatePreview(null) }
                                .testTag("topbar_test_gate_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Lock-Screen testen",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Gate testen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    AppLockNavTab.entries.forEach { tab ->
                        val selected = uiState.selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppLockNavTab.VAULT -> if (selected) Icons.Filled.Lock else Icons.Outlined.Lock
                                        AppLockNavTab.PROFILES -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
                                        AppLockNavTab.LOGS -> if (selected) Icons.Filled.History else Icons.Outlined.History
                                        AppLockNavTab.SETTINGS -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
                                    },
                                    contentDescription = tab.labelDe
                                )
                            },
                            label = { Text(tab.labelDe) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF022C22),
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Non-intrusive Feedback Toast Banner
                AnimatedVisibility(
                    visible = uiState.statusBannerMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Hinweis",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.statusBannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissStatusBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                when (uiState.selectedTab) {
                    AppLockNavTab.VAULT -> VaultScreen(
                        uiState = uiState,
                        onToggleMasterProtection = viewModel::setMasterProtectionEnabled,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectFilterMode = viewModel::selectFilterMode,
                        onSelectCategory = viewModel::selectCategory,
                        onToggleAppLock = viewModel::toggleAppLock,
                        onBatchSetLock = viewModel::batchSetFilteredAppsLock,
                        onOpenLockPreview = viewModel::openLockGatePreview
                    )
                    AppLockNavTab.PROFILES -> ProfilesScreen(
                        profiles = uiState.profiles,
                        allApps = uiState.allApps,
                        onApplyProfile = viewModel::applyProfile,
                        onCreateProfile = viewModel::createProfile,
                        onDeleteProfile = viewModel::deleteProfile
                    )
                    AppLockNavTab.LOGS -> AuditLogScreen(
                        logs = uiState.auditLogs,
                        onClearLogs = viewModel::clearAllAuditLogs
                    )
                    AppLockNavTab.SETTINGS -> SettingsScreen(
                        settings = uiState.settings,
                        currentPinHint = uiState.currentPinHint,
                        permissionHealth = uiState.permissionHealth,
                        onUpdateMasterPin = viewModel::updateMasterPin,
                        onToggleBiometric = viewModel::setBiometricEnabled,
                        onSelectBiometricTriggerMode = viewModel::setBiometricTriggerMode,
                        onToggleScrambleKeypad = viewModel::setScrambleKeypad,
                        onToggleStealthRipple = viewModel::setStealthHideKeyRipple,
                        onSelectRelockPolicy = viewModel::setRelockPolicy,
                        onToggleDarkTheme = viewModel::setDarkThemeEnabled,
                        onRefreshPermissions = viewModel::refreshPermissionHealth
                    )
                }
            }
        }

        // Full-Screen Interactive Lock Gate Simulator / Live Overlay
        AnimatedVisibility(
            visible = previewTarget != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (previewTarget != null) {
                LockGateScreen(
                    targetPackageName = previewTarget.packageName,
                    targetAppName = previewTarget.appName,
                    settings = uiState.settings,
                    demoPinHint = uiState.currentPinHint,
                    isSimulationPreview = true,
                    onVerifyPin = viewModel::verifyCandidatePin,
                    onUnlockSuccess = { method ->
                        viewModel.onPreviewUnlockSuccess(previewTarget, method)
                    },
                    onFailedAttempt = { attempt ->
                        viewModel.onPreviewFailedAttempt(previewTarget, attempt)
                    },
                    onExitToHomeOrCancel = {
                        viewModel.closeLockGatePreview()
                    }
                )
            }
        }
    }
}
