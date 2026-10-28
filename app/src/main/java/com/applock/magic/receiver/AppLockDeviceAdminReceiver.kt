package com.applock.magic.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Geräte-Administrator-Empfänger zum Schutz vor unbefugter Deinstallation.
 * Solange aktiv, sperrt Android das Entfernen der App über den Launcher oder die Einstellungen.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Deinstallationsschutz aktiv", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Achtung: Durch Deaktivieren des Schutzes kann Applock von Fremden beendet oder deinstalliert werden."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Deinstallationsschutz deaktiviert", Toast.LENGTH_SHORT).show()
    }
}
