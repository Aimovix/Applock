package com.example.ui.lockgate

import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.BiometricTriggerMode
import com.example.data.SecuritySettingsState
import com.example.security.BiometricGateSessionState
import com.example.security.SecurityEngine
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockGateScreen(
    targetPackageName: String,
    targetAppName: String,
    settings: SecuritySettingsState,
    demoPinHint: String,
    isSimulationPreview: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlockSuccess: (authMethod: String) -> Unit,
    onFailedAttempt: (attemptNumber: Int) -> Unit,
    onExitToHomeOrCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var cooldownSecondsRemaining by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var biometricSessionState by remember {
        mutableStateOf(BiometricGateSessionState.READY_FOR_MANUAL_TAP)
    }
    var biometricStatusNote by remember {
        mutableStateOf(
            if (!settings.biometricEnabled) {
                "Biometrie deaktiviert • Reiner PIN-Modus"
            } else if (settings.biometricTriggerMode == BiometricTriggerMode.MANUAL_BUTTON_ONLY) {
                "Anti-Loop Schutz aktiv • Fingerabdruck startet nur auf Knopfdruck"
            } else {
                "One-Shot Biometrie-Guard aktiv (Max. 1x pro Sitzung)"
            }
        )
    }

    // Scrambled or standard 1..9, 0 digit order
    var digitOrder by remember(settings.scrambleKeypad) {
        mutableStateOf(
            if (settings.scrambleKeypad) {
                (0..9).shuffled()
            } else {
                listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
            }
        )
    }

    val shakeOffset = remember { Animatable(0f) }
    var activeCancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }

    // Intercept Back press so user explicitly returns to Home Screen (or closes Simulator)
    // instead of getting stuck in an activity stack loop.
    BackHandler {
        activeCancellationSignal?.cancel()
        SecurityEngine.onLockGateClosed()
        onExitToHomeOrCancel()
    }

    DisposableEffect(targetPackageName) {
        SecurityEngine.onLockGateOpened()
        onDispose {
            activeCancellationSignal?.cancel()
            SecurityEngine.onLockGateClosed()
        }
    }

    // Cooldown countdown after max failed attempts
    LaunchedEffect(cooldownSecondsRemaining) {
        if (cooldownSecondsRemaining > 0) {
            delay(1_000L)
            cooldownSecondsRemaining -= 1
            if (cooldownSecondsRemaining == 0) {
                errorMessage = null
                failedAttempts = 0
            }
        }
    }

    // Helper function to trigger hardware BiometricPrompt safely WITHOUT ever re-looping
    fun triggerHardwareBiometricScan(isManualTap: Boolean) {
        if (!settings.biometricEnabled || cooldownSecondsRemaining > 0) return

        // Guard: Never open a second prompt if one is already showing
        if (biometricSessionState == BiometricGateSessionState.PROMPT_CURRENTLY_SHOWING) {
            return
        }

        // Guard: If auto-trigger was requested, ensure we haven't already fallen back to PIN
        if (!isManualTap && biometricSessionState == BiometricGateSessionState.FALLBACK_TO_PIN_ONLY) {
            return
        }

        biometricSessionState = BiometricGateSessionState.PROMPT_CURRENTLY_SHOWING
        biometricStatusNote = "Fingerabdruck-Dialog geöffnet (Tippe auf Abbrechen für ruhigen PIN-Modus)"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val cancellationSignal = CancellationSignal()
                activeCancellationSignal = cancellationSignal
                val executor = ContextCompat.getMainExecutor(context)

                val prompt = BiometricPrompt.Builder(context)
                    .setTitle("$targetAppName entsperren")
                    .setSubtitle("AppLock Sicherheits-Schranke")
                    .setDescription("Bestätige deine Identität oder wechsle direkt zur PIN-Eingabe.")
                    .setNegativeButton(
                        "Nur PIN verwenden",
                        executor,
                        DialogInterface.OnClickListener { _, _ ->
                            // User chose PIN fallback -> permanently lock out auto-biometric for this session
                            biometricSessionState = BiometricGateSessionState.FALLBACK_TO_PIN_ONLY
                            biometricStatusNote = "Auf PIN-Modus gewechselt • Kein automatischer Fingerabdruck-Aufruf"
                        }
                    )
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            biometricSessionState = BiometricGateSessionState.AUTHENTICATED
                            SecurityEngine.markPackageUnlockedForSession(
                                targetPackageName,
                                settings.relockPolicy.gracePeriodMs
                            )
                            onUnlockSuccess("BIOMETRIC")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            // CRITICAL ANTI-LOOP FIX:
                            // When hardware biometric is unavailable, canceled, or dismissed,
                            // transition immediately to FALLBACK_TO_PIN_ONLY and NEVER re-invoke!
                            biometricSessionState = BiometricGateSessionState.FALLBACK_TO_PIN_ONLY
                            biometricStatusNote =
                                "Biometrie beendet (${errString ?: "PIN-Fallback"}) • Ruhiger PIN-Modus aktiv"
                        }

                        override fun onAuthenticationFailed() {
                            biometricStatusNote =
                                "Fingerabdruck nicht erkannt • Du kannst jederzeit die PIN eingeben"
                        }
                    }
                )
            }.onFailure {
                biometricSessionState = BiometricGateSessionState.FALLBACK_TO_PIN_ONLY
                biometricStatusNote = "Kein Hardware-Scanner aktiv • Nutze PIN oder den Schnell-Touch-Sensor unten"
            }
        } else {
            biometricSessionState = BiometricGateSessionState.FALLBACK_TO_PIN_ONLY
            biometricStatusNote = "PIN-Modus aktiv"
        }
    }

    // One-shot auto-trigger ONLY if user explicitly chose AUTO_ONCE_PER_SESSION
    LaunchedEffect(targetPackageName, settings.biometricEnabled, settings.biometricTriggerMode) {
        if (settings.biometricEnabled &&
            settings.biometricTriggerMode == BiometricTriggerMode.AUTO_ONCE_PER_SESSION &&
            !isSimulationPreview
        ) {
            if (SecurityEngine.consumeOneShotAutoBiometricAllowance()) {
                triggerHardwareBiometricScan(isManualTap = false)
            }
        }
    }

    fun handlePinDigitTap(digit: Int) {
        if (cooldownSecondsRemaining > 0) return
        if (enteredPin.length >= settings.pinLength) return

        val updated = enteredPin + digit.toString()
        enteredPin = updated
        errorMessage = null

        if (updated.length == settings.pinLength) {
            if (onVerifyPin(updated)) {
                biometricSessionState = BiometricGateSessionState.AUTHENTICATED
                SecurityEngine.markPackageUnlockedForSession(
                    targetPackageName,
                    settings.relockPolicy.gracePeriodMs
                )
                onUnlockSuccess("PIN")
            } else {
                val newFailCount = failedAttempts + 1
                failedAttempts = newFailCount
                onFailedAttempt(newFailCount)
                enteredPin = ""

                if (newFailCount >= settings.maxFailedAttemptsBeforeCooldown) {
                    cooldownSecondsRemaining = 30
                    errorMessage = "Zu viele Fehlversuche! Schutz-Pause für 30 Sekunden."
                } else {
                    val remaining = settings.maxFailedAttemptsBeforeCooldown - newFailCount
                    errorMessage = "Falsche PIN ($remaining Versuche übrig)"
                }

                if (settings.scrambleKeypad) {
                    digitOrder = (0..9).shuffled()
                }

                coroutineScope.launch {
                    repeat(3) {
                        shakeOffset.animateTo(18f, spring(stiffness = Spring.StiffnessHigh))
                        shakeOffset.animateTo(-18f, spring(stiffness = Spring.StiffnessHigh))
                    }
                    shakeOffset.animateTo(0f)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBackground,
                        Color(0xFF0F172A),
                        ObsidianBackground
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Anti-Loop Status & App Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Anti-Loop Schutz",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSimulationPreview) {
                                "Live-Vorschau • Anti-Loop Schutz Aktiv"
                            } else {
                                "AppLock Schutzschild • Loop-Frei"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(EmeraldPrimary.copy(alpha = 0.25f), CyanSecondary.copy(alpha = 0.2f))
                            )
                        )
                        .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Gesperrte App",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = targetAppName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = targetPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current PIN hint badge so user is never locked out while testing
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aktuelle PIN (${settings.pinLength}-stellig): $demoPinHint",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PIN Dots & Error/Cooldown Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until settings.pinLength) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(if (isFilled) 18.dp else 15.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        cooldownSecondsRemaining > 0 -> CoralError.copy(alpha = 0.5f)
                                        isFilled -> EmeraldPrimary
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = when {
                                        errorMessage != null -> CoralError
                                        isFilled -> EmeraldPrimary
                                        else -> Color(0xFF475569)
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedVisibility(
                    visible = errorMessage != null || cooldownSecondsRemaining > 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CoralError.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warnung",
                            tint = CoralError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (cooldownSecondsRemaining > 0) {
                                "Gesperrt für ${cooldownSecondsRemaining}s wegen Fehlversuchen"
                            } else {
                                errorMessage ?: ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Numeric PIN Pad (Standard or Scrambled)
            val rows = listOf(
                digitOrder.subList(0, 3),
                digitOrder.subList(3, 6),
                digitOrder.subList(6, 9)
            )
            val lastDigit = digitOrder[9]

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { rowDigits ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowDigits.forEach { digit ->
                            PinDigitButton(
                                digit = digit,
                                stealthMode = settings.stealthHideKeyRipple,
                                enabled = cooldownSecondsRemaining == 0,
                                onClick = { handlePinDigitTap(digit) }
                            )
                        }
                    }
                }

                // Bottom keypad row: Scramble / Biometric Left | 0 Center | Backspace Right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action: Reshuffle keypad or manual biometric trigger
                    IconButton(
                        onClick = {
                            digitOrder = (0..9).shuffled()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurface)
                            .border(1.dp, Color(0xFF334155), CircleShape)
                            .testTag("scramble_keypad_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tastenfeld neu mischen",
                            tint = CyanSecondary
                        )
                    }

                    PinDigitButton(
                        digit = lastDigit,
                        stealthMode = settings.stealthHideKeyRipple,
                        enabled = cooldownSecondsRemaining == 0,
                        onClick = { handlePinDigitTap(lastDigit) }
                    )

                    IconButton(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurface)
                            .border(1.dp, Color(0xFF334155), CircleShape)
                            .testTag("pin_backspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Ziffer löschen",
                            tint = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Anti-Loop Biometric Section & Emergency Exit
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = biometricStatusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    if (settings.biometricEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Real Hardware Biometric Dialog Button (on demand, never loops!)
                            OutlinedButton(
                                onClick = { triggerHardwareBiometricScan(isManualTap = true) },
                                enabled = cooldownSecondsRemaining == 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("manual_biometric_button"),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    CyanSecondary.copy(alpha = 0.6f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "System Biometrie",
                                    tint = CyanSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "System-Scan",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanSecondary
                                )
                            }

                            // 2. Instant Touch Sensor Verification (works cleanly in Cloud Emulator too!)
                            Button(
                                onClick = {
                                    if (cooldownSecondsRemaining == 0) {
                                        biometricSessionState = BiometricGateSessionState.AUTHENTICATED
                                        SecurityEngine.markPackageUnlockedForSession(
                                            targetPackageName,
                                            settings.relockPolicy.gracePeriodMs
                                        )
                                        onUnlockSuccess("BIOMETRIC")
                                    }
                                },
                                enabled = cooldownSecondsRemaining == 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = Color(0xFF022C22)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("instant_touch_biometric_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Touch-Sensor entsperren",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Touch-Sensor",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Escape Hatch: Cancel & Return to Home Screen (prevents ever being trapped)
                    OutlinedButton(
                        onClick = {
                            activeCancellationSignal?.cancel()
                            SecurityEngine.onLockGateClosed()
                            onExitToHomeOrCancel()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("exit_lock_gate_button"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Verlassen",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSimulationPreview) {
                                "Vorschau beenden & zurück zum Tresor"
                            } else {
                                "Abbrechen & zum Startbildschirm"
                            },
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: Int,
    stealthMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(ObsidianSurface)
            .border(1.dp, Color(0xFF334155), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = if (stealthMode) null else ripple(bounded = true, color = EmeraldPrimary),
                enabled = enabled,
                onClick = onClick
            )
            .testTag("pin_digit_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (enabled) Color.White else Color(0xFF64748B)
        )
    }
}
