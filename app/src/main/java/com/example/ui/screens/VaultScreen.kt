package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppCategory
import com.example.data.AppFilterMode
import com.example.data.InstalledAppUiModel
import com.example.ui.AppLockUiState
import com.example.ui.components.HeroShieldBanner
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary

@Composable
fun VaultScreen(
    uiState: AppLockUiState,
    onToggleMasterProtection: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilterMode: (AppFilterMode) -> Unit,
    onSelectCategory: (AppCategory) -> Unit,
    onToggleAppLock: (InstalledAppUiModel) -> Unit,
    onBatchSetLock: (Boolean) -> Unit,
    onOpenLockPreview: (InstalledAppUiModel?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Shield Card
        item(key = "hero_banner") {
            HeroShieldBanner(
                masterProtectionEnabled = uiState.settings.masterProtectionEnabled,
                lockedAppsCount = uiState.lockedAppsCount,
                totalAppsCount = uiState.allApps.size,
                blockedAttemptsCount = uiState.blockedAttemptsCount,
                activeProfileName = uiState.activeProfileName,
                biometricTriggerMode = uiState.settings.biometricTriggerMode,
                onToggleMasterProtection = onToggleMasterProtection,
                onTestLockScreenClick = { onOpenLockPreview(null) }
            )
        }

        // 2. Search & Filter Bar
        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("App-Name oder Paket suchen…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suchen"
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Suche löschen"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_apps_input")
                )

                // Filter Mode Chips (Alle, Gesperrt, Freigegeben, System-Apps)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppFilterMode.entries.forEach { mode ->
                        val selected = uiState.selectedFilterMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilterMode(mode) },
                            label = { Text(mode.labelDe) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("filter_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppCategory.entries.forEach { category ->
                        val selected = uiState.selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.labelDe) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanSecondary.copy(alpha = 0.22f),
                                selectedLabelColor = CyanSecondary
                            ),
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }

                // Batch Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.filteredApps.size} Apps angezeigt",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { onBatchSetLock(true) },
                            modifier = Modifier.testTag("batch_lock_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Alle sperren",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sichtbare sperren")
                        }
                        TextButton(
                            onClick = { onBatchSetLock(false) },
                            modifier = Modifier.testTag("batch_unlock_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Alle freigeben",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Freigeben")
                        }
                    }
                }
            }
        }

        // 3. Empty State or App Items
        if (uiState.filteredApps.isEmpty()) {
            item(key = "empty_vault_state") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Keine Apps gefunden",
                            tint = CyanSecondary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Keine passenden Apps für diesen Filter",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Setze die Suche oder den Kategorie-Filter zurück, um alle Apps zu sehen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                onSearchQueryChange("")
                                onSelectFilterMode(AppFilterMode.ALL)
                                onSelectCategory(AppCategory.ALL)
                            }
                        ) {
                            Text("Filter zurücksetzen")
                        }
                    }
                }
            }
        } else {
            items(
                items = uiState.filteredApps,
                key = { it.packageName }
            ) { app ->
                AppVaultItemCard(
                    app = app,
                    onToggleLock = { onToggleAppLock(app) },
                    onPreviewLock = { onOpenLockPreview(app) }
                )
            }
        }
    }
}

@Composable
private fun AppVaultItemCard(
    app: InstalledAppUiModel,
    onToggleLock: () -> Unit,
    onPreviewLock: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (app.isLocked) {
            EmeraldPrimary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "app_card_border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onToggleLock() }
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon or Category Vector Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (app.isLocked) {
                                EmeraldPrimary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = when (app.category) {
                                AppCategory.FINANCE -> Icons.Default.AccountBalance
                                AppCategory.SOCIAL -> Icons.Default.Chat
                                AppCategory.MEDIA -> Icons.Default.PhotoLibrary
                                AppCategory.SYSTEM -> Icons.Default.SettingsApplications
                                AppCategory.PRODUCTIVITY -> Icons.Default.Work
                                AppCategory.ALL -> Icons.Default.Apps
                            },
                            contentDescription = app.category.labelDe,
                            tint = if (app.isLocked) EmeraldPrimary else CyanSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isRecommendedSecurityTarget && !app.isLocked) {
                            Surface(
                                color = CyanSecondary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Empfohlen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${app.category.labelDe} • ${app.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (app.isLocked) {
                    IconButton(
                        onClick = onPreviewLock,
                        modifier = Modifier.testTag("test_app_lock_${app.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = "Sperrbildschirm für ${app.appName} testen",
                            tint = CyanSecondary
                        )
                    }
                }

                Switch(
                    checked = app.isLocked,
                    onCheckedChange = { onToggleLock() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF022C22),
                        checkedTrackColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("switch_lock_${app.packageName}")
                )
            }
        }
    }
}
