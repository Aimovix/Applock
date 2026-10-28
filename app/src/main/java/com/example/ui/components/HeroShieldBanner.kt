package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BiometricTriggerMode
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianCardElevated

@Composable
fun HeroShieldBanner(
    masterProtectionEnabled: Boolean,
    lockedAppsCount: Int,
    totalAppsCount: Int,
    blockedAttemptsCount: Int,
    activeProfileName: String,
    biometricTriggerMode: BiometricTriggerMode,
    onToggleMasterProtection: (Boolean) -> Unit,
    onTestLockScreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (masterProtectionEnabled) {
                        listOf(EmeraldPrimary.copy(alpha = 0.65f), CyanSecondary.copy(alpha = 0.35f))
                    } else {
                        listOf(AmberWarning.copy(alpha = 0.6f), Color(0xFF334155))
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Custom Cyber-Shield Vector Canvas Artwork in background
            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {
                val centerX = size.width * 0.85f
                val centerY = size.height * 0.35f
                val accentColor = if (masterProtectionEnabled) EmeraldPrimary else AmberWarning

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            CyanSecondary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(centerX, centerY),
                    radius = size.width * 0.55f
                )

                // Concentric tactical radar rings
                drawCircle(
                    color = accentColor.copy(alpha = 0.14f),
                    center = Offset(centerX, centerY),
                    radius = 72.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = CyanSecondary.copy(alpha = 0.10f),
                    center = Offset(centerX, centerY),
                    radius = 108.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Geometric shield outline
                val sWidth = 56.dp.toPx()
                val sHeight = 68.dp.toPx()
                val shieldPath = Path().apply {
                    moveTo(centerX, centerY - sHeight / 2f)
                    lineTo(centerX + sWidth / 2f, centerY - sHeight * 0.28f)
                    lineTo(centerX + sWidth / 2f, centerY + sHeight * 0.10f)
                    quadraticTo(
                        centerX + sWidth * 0.35f,
                        centerY + sHeight * 0.42f,
                        centerX,
                        centerY + sHeight / 2f
                    )
                    quadraticTo(
                        centerX - sWidth * 0.35f,
                        centerY + sHeight * 0.42f,
                        centerX - sWidth / 2f,
                        centerY + sHeight * 0.10f
                    )
                    lineTo(centerX - sWidth / 2f, centerY - sHeight * 0.28f)
                    close()
                }
                drawPath(
                    path = shieldPath,
                    color = accentColor.copy(alpha = 0.16f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (masterProtectionEnabled) {
                                        EmeraldPrimary.copy(alpha = 0.2f)
                                    } else {
                                        AmberWarning.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (masterProtectionEnabled) {
                                    Icons.Default.VerifiedUser
                                } else {
                                    Icons.Default.Security
                                },
                                contentDescription = "Schutzstatus",
                                tint = if (masterProtectionEnabled) EmeraldPrimary else AmberWarning,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (masterProtectionEnabled) {
                                    "Echtzeit-Schutz Aktiv"
                                } else {
                                    "Schutz Pausiert"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Profil: $activeProfileName",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Switch(
                        checked = masterProtectionEnabled,
                        onCheckedChange = onToggleMasterProtection,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF022C22),
                            checkedTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("master_protection_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Anti-Loop Guarantee Pill
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        CyanSecondary.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Loop-Guard",
                            tint = CyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (biometricTriggerMode == BiometricTriggerMode.MANUAL_BUTTON_ONLY) {
                                "Anti-Loop Guard: Fingerabdruck nur auf Knopfdruck (Kein Dauer-Popup)"
                            } else {
                                "Anti-Loop Guard: Max. 1x Biometrie-Abfrage, danach ruhiger PIN-Modus"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0F2FE)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3 Stat Tiles + Test Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMiniCard(
                        value = "$lockedAppsCount / $totalAppsCount",
                        label = "Apps gesperrt",
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        value = blockedAttemptsCount.toString(),
                        label = "Abgewehrt",
                        accentColor = CyanSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        value = "O(1) <1ms",
                        label = "Lock-Cache",
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onTestLockScreenClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color(0xFF022C22)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_lock_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Sperrbildschirm testen"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sperrbildschirm & Anti-Loop jetzt live testen",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0B0F17).copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
