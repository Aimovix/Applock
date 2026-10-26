package com.applock.magic.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

object HonorMagicOsHelper {

    fun isHonorDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("honor") || brand.contains("honor") ||
                manufacturer.contains("huawei") || brand.contains("huawei")
    }

    /**
     * Versucht, die spezifische MagicOS App-Start-Verwaltung (Auto-Start / Hintergrundausführung) zu öffnen.
     */
    fun openHonorAppLaunchSettings(context: Context): Boolean {
        val intents = listOf(
            // Honor MagicOS spezifische Komponenten
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.optimize.bootstart.BootStartActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.power.ui.HwPowerManagerActivity"
                )
            ),
            // Legacy Huawei / EMUI Fallbacks
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            )
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Nächsten Intent probieren
            }
        }

        // Falls die proprietäre Seite nicht aufgerufen werden kann: Standard App-Details öffnen
        return false
    }

    /**
     * Wichtige Anleitung für Honor Magic 8 Pro Nutzer
     */
    fun getHonorSurvivalInstructions(): List<String> {
        return listOf(
            "1. MagicOS App-Start: 'Automatisch verwalten' DEAKTIVIEREN.",
            "2. 'Auto-Start', 'Sekundärer Start' und 'Im Hintergrund ausführen' AKTIVIEREN.",
            "3. Task-Manager (Recent Apps): Wische die ShieldLock-Karte leicht nach unten, bis das Schloss-Symbol erscheint.",
            "4. Bei manuell installierter APK: In den Android App-Infos oben rechts auf die 3 Punkte tippen -> 'Eingeschränkte Einstellungen zulassen'."
        )
    }
}
