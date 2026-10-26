package com.applock.magic.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.data.InstalledApp
import com.applock.magic.service.AppLockForegroundService
import com.applock.magic.ui.theme.HonorAppLockTheme
import com.applock.magic.ui.theme.SecurityAccent
import com.applock.magic.ui.theme.SecurityDarkBackground
import com.applock.magic.ui.theme.SecuritySuccess
import com.applock.magic.ui.theme.SecuritySurface
import com.applock.magic.ui.theme.SecuritySurfaceVariant
import com.applock.magic.ui.theme.SecurityTextPrimary
import com.applock.magic.ui.theme.SecurityTextSecondary
import com.applock.magic.ui.theme.SecurityWarning
import com.applock.magic.util.HonorMagicOsHelper
import com.applock.magic.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppLockPreferences
    private val refreshTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppLockPreferences(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Verlässt die Activity und entfernt die Recents-Task-Karte (Zero-Recents-Footprint gegen MagicOS Task-Kills)
                finishAndRemoveTask()
            }
        })

        setContent {
            HonorAppLockTheme {
                MainScreen(prefs = prefs, refreshTrigger = refreshTrigger.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.value += 1
        if (PermissionHelper.hasOverlayPermission(this) &&
            PermissionHelper.hasUsageStatsPermission(this)
        ) {
            AppLockForegroundService.start(this)
        }
    }
}

@Composable
fun MainScreen(prefs: AppLockPreferences, refreshTrigger: Int) {
    val context = LocalContext.current
    var hasOverlay by remember(refreshTrigger) { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasUsage by remember(refreshTrigger) { mutableStateOf(PermissionHelper.hasUsageStatsPermission(context)) }
    var hasAccessibility by remember(refreshTrigger) { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isBatteryIgnored by remember(refreshTrigger) { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var isDeviceAdmin by remember(refreshTrigger) { mutableStateOf(PermissionHelper.isDeviceAdminActive(context)) }

    var lockPolicy by remember { mutableIntStateOf(prefs.getLockPolicy()) }

    var searchQuery by remember { mutableStateOf("") }
    var appList by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val isHonor = remember { HonorMagicOsHelper.isHonorDevice() }

    LaunchedEffect(Unit) {
        val lockedSet = prefs.getLockedPackages()
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val launchable = installed.filter {
                (pm.getLaunchIntentForPackage(it.packageName) != null) ||
                        AppLockPreferences.SYSTEM_CRITICAL_PACKAGES.contains(it.packageName)
            }

            launchable.map { appInfo ->
                InstalledApp(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = null,
                    isLocked = lockedSet.contains(appInfo.packageName),
                    isSystemCritical = AppLockPreferences.SYSTEM_CRITICAL_PACKAGES.contains(appInfo.packageName)
                )
            }.sortedWith(
                compareByDescending<InstalledApp> { it.isLocked }
                    .thenBy { it.name.lowercase() }
            )
        }
        appList = loaded
        isLoading = false
    }

    Scaffold(
        containerColor = SecurityDarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ShieldLock Pro",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecurityTextPrimary
                )
                Text(
                    text = if (isHonor) "Konfiguriert für Honor MagicOS (Magic 8 Pro)" else "Moderner Android Hardware-Schutz",
                    fontSize = 14.sp,
                    color = SecurityTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Berechtigungs- & Schutzstatus
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SecuritySurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sicherheits- & Systemrechte",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecurityTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionRow(
                            title = "Über andere Apps einblenden",
                            granted = hasOverlay,
                            onGrant = { PermissionHelper.requestOverlayPermission(context) }
                        )

                        PermissionRow(
                            title = "Barrierefreiheit (0ms Echtzeit)",
                            granted = hasAccessibility,
                            onGrant = { PermissionHelper.requestAccessibilityPermission(context) }
                        )

                        PermissionRow(
                            title = "Nutzungsdaten-Zugriff (Fallback)",
                            granted = hasUsage,
                            onGrant = { PermissionHelper.requestUsageStatsPermission(context) }
                        )

                        PermissionRow(
                            title = "Akku-Optimierung ignorieren",
                            granted = isBatteryIgnored,
                            onGrant = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                        )

                        PermissionRow(
                            title = "Deinstallationsschutz (Geräte-Admin)",
                            granted = isDeviceAdmin,
                            onGrant = { PermissionHelper.requestDeviceAdmin(context) }
                        )
                    }
                }
            }

            // Sperr-Richtlinie (Wann soll gesperrt werden)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SecuritySurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sperrverhalten & Re-Lock-Richtlinie",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecurityTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Definiert, wie schnell eine freigegebene App nach dem Wechseln wieder verriegelt wird.",
                            fontSize = 13.sp,
                            color = SecurityTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        PolicyOptionRow(
                            title = "Sofort sperren (Höchste Sicherheit)",
                            subtitle = "Verriegelt die App sofort beim Wechseln zu einer anderen App",
                            selected = lockPolicy == AppLockPreferences.POLICY_IMMEDIATE,
                            onClick = {
                                lockPolicy = AppLockPreferences.POLICY_IMMEDIATE
                                prefs.setLockPolicy(AppLockPreferences.POLICY_IMMEDIATE)
                            }
                        )

                        PolicyOptionRow(
                            title = "Bei Display-Aus sperren",
                            subtitle = "Bleibt entsperrt, bis der Bildschirm ausgeschaltet wird",
                            selected = lockPolicy == AppLockPreferences.POLICY_SCREEN_OFF,
                            onClick = {
                                lockPolicy = AppLockPreferences.POLICY_SCREEN_OFF
                                prefs.setLockPolicy(AppLockPreferences.POLICY_SCREEN_OFF)
                            }
                        )

                        PolicyOptionRow(
                            title = "1 Minute Gnadenfrist",
                            subtitle = "Erlaubt schnelles Multitasking für 60 Sekunden",
                            selected = lockPolicy == AppLockPreferences.POLICY_TIMEOUT_1MIN,
                            onClick = {
                                lockPolicy = AppLockPreferences.POLICY_TIMEOUT_1MIN
                                prefs.setLockPolicy(AppLockPreferences.POLICY_TIMEOUT_1MIN)
                            }
                        )

                        PolicyOptionRow(
                            title = "5 Minuten Gnadenfrist",
                            subtitle = "Verriegelt erst nach 5 Minuten Inaktivität",
                            selected = lockPolicy == AppLockPreferences.POLICY_TIMEOUT_5MIN,
                            onClick = {
                                lockPolicy = AppLockPreferences.POLICY_TIMEOUT_5MIN
                                prefs.setLockPolicy(AppLockPreferences.POLICY_TIMEOUT_5MIN)
                            }
                        )
                    }
                }
            }

            // Honor MagicOS Spezial-Konfiguration
            if (isHonor) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SecuritySurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Honor MagicOS Stabilitäts-Fix",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecurityWarning
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Damit MagicOS den Dienst nicht im Hintergrund beendet:\n" +
                                        "1. 'App-Start' öffnen\n" +
                                        "2. 'Automatisch verwalten' für ShieldLock deaktivieren\n" +
                                        "3. Alle drei Schalter (Auto-Start, Sekundärer Start, Hintergrund) einschalten.",
                                fontSize = 13.sp,
                                color = SecurityTextSecondary,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val opened = HonorMagicOsHelper.openHonorAppLaunchSettings(context)
                                    if (!opened) {
                                        PermissionHelper.openAppDetailsSettings(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecurityAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("MagicOS App-Start öffnen", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Suchfeld & Filter
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("App suchen...", color = SecurityTextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // App-Liste
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SecurityAccent)
                    }
                }
            } else {
                val filtered = appList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }

                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onLockToggled = { isLocked ->
                            prefs.setPackageLocked(app.packageName, isLocked)
                            appList = appList.map {
                                if (it.packageName == app.packageName) it.copy(isLocked = isLocked) else it
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PolicyOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) SecuritySuccess else SecurityTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SecurityTextSecondary
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .background(
                    color = if (selected) SecurityAccent else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (selected) "Aktiv" else "Wählen",
                fontSize = 11.sp,
                color = if (selected) Color.White else SecurityTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, color = SecurityTextPrimary)
            Text(
                text = if (granted) "Aktiviert" else "Erforderlich",
                fontSize = 12.sp,
                color = if (granted) SecuritySuccess else SecurityWarning,
                fontWeight = FontWeight.Medium
            )
        }
        if (!granted) {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = SecurityAccent),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Erteilen", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AppRow(
    app: InstalledApp,
    onLockToggled: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SecuritySurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = SecurityTextPrimary
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = SecurityTextSecondary
                )
                if (app.isSystemCritical) {
                    Text(
                        text = "System-Sicherheit (Schutz vor Deinstallation)",
                        fontSize = 10.sp,
                        color = SecurityWarning
                    )
                }
            }
            Switch(
                checked = app.isLocked,
                onCheckedChange = onLockToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SecurityAccent,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = SecuritySurfaceVariant
                )
            )
        }
    }
}
