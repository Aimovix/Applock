package com.applock.magic.util

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.applock.magic.data.AppLockPreferences
import com.applock.magic.receiver.AppLockDeviceAdminReceiver
import java.io.BufferedReader
import java.io.InputStreamReader

enum class HiderEngineMode {
    DEVICE_OWNER,
    SHIZUKU_OR_ROOT,
    NOT_AVAILABLE
}

object AppHiderManager {

    const val ADB_DEVICE_OWNER_COMMAND =
        "adb shell dpm set-device-owner com.applock.magic/.receiver.AppLockDeviceAdminReceiver"

    fun getEngineMode(context: Context): HiderEngineMode {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)) {
            return HiderEngineMode.DEVICE_OWNER
        }
        if (isRootAvailable()) {
            return HiderEngineMode.SHIZUKU_OR_ROOT
        }
        return HiderEngineMode.NOT_AVAILABLE
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.destroy()
            line != null && line.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Versteckt eine App vollständig vom Startbildschirm/Launcher oder stellt sie wieder her.
     */
    fun setAppHidden(
        context: Context,
        prefs: AppLockPreferences,
        packageName: String,
        hide: Boolean
    ): Result<Boolean> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AppLockDeviceAdminReceiver::class.java)

        // 1. Device Owner API (Offizieller Android Enterprise Standard)
        if (dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)) {
            return try {
                val success = dpm.setApplicationHidden(admin, packageName, hide)
                if (success) {
                    prefs.setPackageHidden(packageName, hide)
                    Result.success(true)
                } else {
                    Result.failure(Exception("DevicePolicyManager konnte den Status für $packageName nicht ändern."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // 2. Root / Shell Fallback
        if (isRootAvailable()) {
            return try {
                val cmd = if (hide) "pm disable-user --user 0 $packageName" else "pm enable $packageName"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    prefs.setPackageHidden(packageName, hide)
                    Result.success(true)
                } else {
                    Result.failure(Exception("Root-Befehl fehlgeschlagen mit Exit-Code $exitCode"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        return Result.failure(
            IllegalStateException(
                "Keine Berechtigung zum Ausblenden anderer Apps. Aktiviere Device-Owner oder nutze Shizuku/Root."
            )
        )
    }

    fun copyAdbCommandToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Applock Device Owner Command", ADB_DEVICE_OWNER_COMMAND)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "ADB-Befehl in Zwischenablage kopiert!", Toast.LENGTH_SHORT).show()
    }
}
