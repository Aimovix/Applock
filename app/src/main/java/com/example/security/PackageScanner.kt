package com.example.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.LruCache
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.AppCategory
import com.example.data.InstalledAppUiModel
import com.example.data.PermissionHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PackageScanner {
    private val iconCache = object : LruCache<String, ImageBitmap>(80) {}

    private data class CuratedAppTemplate(
        val packageName: String,
        val appName: String,
        val category: AppCategory,
        val isSystemApp: Boolean,
        val isRecommended: Boolean
    )

    // Curated fallback/system apps so even a stripped cloud emulator displays a realistic,
    // comprehensive set of target apps alongside all real installed packages.
    private val curatedDeviceApps = listOf(
        CuratedAppTemplate("com.android.settings", "Einstellungen", AppCategory.SYSTEM, true, true),
        CuratedAppTemplate("com.google.android.apps.photos", "Fotos & Galerie", AppCategory.MEDIA, false, true),
        CuratedAppTemplate("com.google.android.apps.messaging", "Nachrichten (SMS/RCS)", AppCategory.SOCIAL, false, true),
        CuratedAppTemplate("com.example.vault.banking", "Online Banking & TAN", AppCategory.FINANCE, false, true),
        CuratedAppTemplate("com.android.vending", "Google Play Store", AppCategory.SYSTEM, true, true),
        CuratedAppTemplate("com.google.android.gm", "Gmail Postfach", AppCategory.SOCIAL, false, true),
        CuratedAppTemplate("com.android.chrome", "Chrome Browser", AppCategory.PRODUCTIVITY, false, false),
        CuratedAppTemplate("com.google.android.youtube", "YouTube", AppCategory.MEDIA, false, false),
        CuratedAppTemplate("com.example.vault.crypto", "Hardware Wallet & Krypto", AppCategory.FINANCE, false, true),
        CuratedAppTemplate("com.example.social.chat", "Private Messenger", AppCategory.SOCIAL, false, true),
        CuratedAppTemplate("com.android.documentsui", "Dateimanager & Tresor", AppCategory.PRODUCTIVITY, true, false),
        CuratedAppTemplate("com.google.android.dialer", "Telefon & Kontakte", AppCategory.SOCIAL, true, false),
        CuratedAppTemplate("com.google.android.calendar", "Kalender & Termine", AppCategory.PRODUCTIVITY, false, false),
        CuratedAppTemplate("com.android.camera2", "Kamera", AppCategory.MEDIA, true, false)
    )

    suspend fun loadInstalledAndCuratedApps(
        context: Context,
        lockedPackageMap: Map<String, Boolean>
    ): List<InstalledAppUiModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val discoveredByPackage = LinkedHashMap<String, InstalledAppUiModel>()

        // 1. Scan real launchable packages on the device
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val pkgName = activityInfo.packageName ?: continue
                if (pkgName == ownPackage || pkgName.startsWith("com.aistudio.applock")) continue

                val label = runCatching {
                    resolveInfo.loadLabel(pm).toString().trim()
                }.getOrDefault(pkgName)

                val appInfo = activityInfo.applicationInfo
                val isSystem = appInfo != null &&
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                val iconBitmap = iconCache.get(pkgName) ?: runCatching {
                    val drawable = resolveInfo.loadIcon(pm)
                    drawableToImageBitmap(drawable)?.also { iconCache.put(pkgName, it) }
                }.getOrNull()

                val category = inferCategory(pkgName, label, isSystem)
                val isRecommended = category == AppCategory.FINANCE ||
                    category == AppCategory.SOCIAL ||
                    pkgName.contains("settings") ||
                    pkgName.contains("photos")

                discoveredByPackage[pkgName] = InstalledAppUiModel(
                    packageName = pkgName,
                    appName = label.ifEmpty { pkgName },
                    category = category,
                    isSystemApp = isSystem,
                    isLocked = lockedPackageMap[pkgName] ?: false,
                    iconBitmap = iconBitmap,
                    isRecommendedSecurityTarget = isRecommended
                )
            }
        } catch (_: Exception) {
            // Proceed gracefully with curated entries if PackageManager is restricted in unit tests
        }

        // 2. Ensure core curated apps are present so profiles and filters always have rich targets
        for (template in curatedDeviceApps) {
            val existing = discoveredByPackage[template.packageName]
            if (existing == null) {
                discoveredByPackage[template.packageName] = InstalledAppUiModel(
                    packageName = template.packageName,
                    appName = template.appName,
                    category = template.category,
                    isSystemApp = template.isSystemApp,
                    isLocked = lockedPackageMap[template.packageName] ?: false,
                    iconBitmap = null,
                    isRecommendedSecurityTarget = template.isRecommended
                )
            } else {
                // Keep real icon while preserving friendly German title/category if desired
                discoveredByPackage[template.packageName] = existing.copy(
                    isLocked = lockedPackageMap[template.packageName] ?: existing.isLocked,
                    isRecommendedSecurityTarget = template.isRecommended || existing.isRecommendedSecurityTarget
                )
            }
        }

        discoveredByPackage.values.sortedWith(
            compareByDescending<InstalledAppUiModel> { it.isLocked }
                .thenByDescending { it.isRecommendedSecurityTarget }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun inferCategory(packageName: String, label: String, isSystem: Boolean): AppCategory {
        val text = "${packageName.lowercase()} ${label.lowercase()}"
        return when {
            text.contains("bank") || text.contains("pay") || text.contains("wallet") ||
                text.contains("finanz") || text.contains("crypto") -> AppCategory.FINANCE
            text.contains("chat") || text.contains("messag") || text.contains("mail") ||
                text.contains("gm") || text.contains("dialer") || text.contains("phone") ||
                text.contains("contact") || text.contains("social") -> AppCategory.SOCIAL
            text.contains("photo") || text.contains("gallery") || text.contains("camera") ||
                text.contains("video") || text.contains("youtube") || text.contains("music") -> AppCategory.MEDIA
            text.contains("setting") || text.contains("vending") || text.contains("system") -> AppCategory.SYSTEM
            text.contains("doc") || text.contains("file") || text.contains("calendar") ||
                text.contains("chrome") || text.contains("browser") || text.contains("note") -> AppCategory.PRODUCTIVITY
            isSystem -> AppCategory.SYSTEM
            else -> AppCategory.PRODUCTIVITY
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(96).coerceAtMost(192)
        val height = drawable.intrinsicHeight.coerceAtLeast(96).coerceAtMost(192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    fun inspectPermissionHealth(context: Context): PermissionHealthStatus {
        val isAccessibilityOn = runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            enabledServices?.any {
                it.resolveInfo?.serviceInfo?.packageName == context.packageName
            } == true
        }.getOrDefault(false)

        val isUsageStatsOn = runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        val isOverlayOn = runCatching {
            Settings.canDrawOverlays(context)
        }.getOrDefault(false)

        val hasBiometricHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        }.getOrDefault(false)

        return PermissionHealthStatus(
            isAccessibilityEnabled = isAccessibilityOn,
            isUsageStatsGranted = isUsageStatsOn,
            isOverlayGranted = isOverlayOn,
            hasHardwareBiometrics = hasBiometricHardware
        )
    }
}
