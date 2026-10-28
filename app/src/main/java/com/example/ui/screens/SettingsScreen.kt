package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BiometricTriggerMode
import com.example.data.PermissionHealthStatus
import com.example.data.RelockPolicy
import com.example.data.SecuritySettingsState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary

@Composable
fun SettingsScreen(
    settings: SecuritySettingsState,
    currentPinHint: String,
    permissionHealth: PermissionHealthStatus,
    onUpdateMasterPin: (String) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onSelectBiometricTriggerMode: (BiometricTriggerMode) -> Unit,
    onToggleScrambleKeypad: (Boolean) -> Unit,
    onToggleStealthRipple: (Boolean) -> Unit,
    onSelectRelockPolicy: (RelockPolicy) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showChangePinDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Sicherheit & Anti-Loop Schutz",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Konfiguriere Biometrie-Schleifenschutz, Master-PIN, Re-Lock-Zeiten und System-Dienste",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. CRITICAL SECTION: Biometric & Anti-Infinite-Loop Configuration
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometrie & Anti-Loop",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fingerabdruck & Endlosschleifen-Schutz",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verhindert dauerhaftes Aufpoppen des Fingerabdruck-Dialogs",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldPrimary
                            )
                        }
                        Switch(
                            checked = settings.biometricEnabled,
                            onCheckedChange = onToggleBiometric,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF022C22),
                                checkedTrackColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("biometric_enable_switch")
                        )
                    }

                    if (settings.biometricEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        BiometricTriggerMode.entries.forEach { mode ->
                            val isSelected = settings.biometricTriggerMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBiometricTriggerMode(mode) }
                                    .padding(vertical = 6.dp)
                                    .testTag("biometric_mode_${mode.name.lowercase()}"),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectBiometricTriggerMode(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = mode.titleDe,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = mode.descriptionDe,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Master PIN & Anti-Spy Keypad Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Master-PIN",
                                tint = CyanSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Master-PIN (${settings.pinLength}-stellig)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Aktueller Code: $currentPinHint (SHA-256 + Salt)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { showChangePinDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanSecondary,
                                contentColor = Color(0xFF082F49)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("change_pin_button")
                        ) {
                            Text("PIN ändern", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scramble Keypad Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = "Zufalls-Tastatur",
                                tint = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Zufalls-Tastatur (Scramble Keypad)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mischt die Ziffern 0–9 bei jedem Sperrbildschirm gegen Blicke von der Seite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.scrambleKeypad,
                            onCheckedChange = onToggleScrambleKeypad,
                            modifier = Modifier.testTag("scramble_keypad_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stealth Key Ripple Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Unsichtbarer Tastenanschlag",
                                tint = CyanSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Stealth-Tasten (Kein Aufleuchten)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Unterdrückt den Ripple-Effekt beim Tippen der PIN-Ziffern",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.stealthHideKeyRipple,
                            onCheckedChange = onToggleStealthRipple,
                            modifier = Modifier.testTag("stealth_ripple_switch")
                        )
                    }
                }
            }
        }

        // 3. Re-Lock Timeout Policy
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Re-Lock Richtlinie",
                            tint = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Erneute Sperrung (Re-Lock Intervall)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inklusive mindestens 4 Sekunden Anti-Loop-Übergangsschutz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    RelockPolicy.entries.forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRelockPolicy(policy) }
                                .padding(vertical = 6.dp)
                                .testTag("relock_policy_${policy.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.relockPolicy == policy,
                                onClick = { onSelectRelockPolicy(policy) },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = policy.titleDe,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = policy.subtitleDe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. System Permission Health & Appearance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "System-Berechtigungen",
                                tint = CyanSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Android System-Dienste & Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = onRefreshPermissions) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Prüfen",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prüfen")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionActionRow(
                        title = "Bedienungshilfen-Watcher (Ereignisgesteuert)",
                        isGranted = permissionHealth.isAccessibilityEnabled,
                        onOpenSettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionActionRow(
                        title = "Nutzungszugriff (UsageStats Fallback)",
                        isGranted = permissionHealth.isUsageStatsGranted,
                        onOpenSettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Tactical Obsidian Dark Mode",
                                tint = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Tactical Obsidian Dark Theme",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Kontrastreiches Cyber-Security Farbschema",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.darkThemeEnabled,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }
                }
            }
        }
    }

    if (showChangePinDialog) {
        ChangeMasterPinDialog(
            onDismiss = { showChangePinDialog = false },
            onSaveNewPin = { newPin ->
                onUpdateMasterPin(newPin)
                showChangePinDialog = false
            }
        )
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isGranted) "Systemdienst aktiv" else "Optional über Android-Einstellungen aktivierbar",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGranted) EmeraldPrimary else AmberWarning
            )
        }
        OutlinedButton(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text(if (isGranted) "Aktiv" else "Öffnen")
        }
    }
}

@Composable
private fun ChangeMasterPinDialog(
    onDismiss: () -> Unit,
    onSaveNewPin: (String) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var useSixDigits by remember { mutableStateOf(false) }
    val targetLen = if (useSixDigits) 6 else 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Master-PIN ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("6-stellige PIN verwenden", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = useSixDigits,
                        onCheckedChange = {
                            useSixDigits = it
                            pinInput = ""
                        }
                    )
                }

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(targetLen)
                        pinInput = digits
                    },
                    label = { Text("Neue $targetLen-stellige PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_pin_textfield")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveNewPin(pinInput) },
                enabled = pinInput.length == targetLen,
                modifier = Modifier.testTag("save_new_pin_button")
            ) {
                Text("PIN speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
