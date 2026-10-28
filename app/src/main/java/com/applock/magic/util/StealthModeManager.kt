package com.applock.magic.util

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object StealthModeManager {

    private const val ALIAS_CLASS_NAME = "com.applock.magic.ui.LauncherAlias"
    const val TRIGGER_FILE_HTML = "Applock_Oeffnen.html"
    const val TRIGGER_FILE_CUSTOM = "Applock_Oeffnen.applock"

    fun isStealthModeActive(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val component = ComponentName(context, ALIAS_CLASS_NAME)
            val state = pm.getComponentEnabledSetting(component)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) {
            false
        }
    }

    fun setStealthMode(context: Context, hideFromLauncher: Boolean): Boolean {
        return try {
            val pm = context.packageManager
            val component = ComponentName(context, ALIAS_CLASS_NAME)
            val newState = if (hideFromLauncher) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            pm.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
            if (hideFromLauncher) {
                createTriggerFiles(context)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createTriggerFiles(context: Context): Boolean {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="de">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Applock Öffnen</title>
                <style>
                    body {
                        background-color: #0f172a;
                        color: #f8fafc;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                        padding: 20px;
                        box-sizing: border-box;
                    }
                    .card {
                        background: #1e293b;
                        border-radius: 20px;
                        padding: 32px 24px;
                        max-width: 420px;
                        width: 100%;
                        text-align: center;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.6);
                        border: 1px solid #334155;
                    }
                    .icon { font-size: 56px; margin-bottom: 12px; }
                    h1 { font-size: 24px; margin: 0 0 10px 0; color: #38bdf8; }
                    p { font-size: 14px; color: #94a3b8; line-height: 1.6; margin: 0 0 24px 0; }
                    .btn {
                        display: block;
                        background: #0284c7;
                        color: #ffffff;
                        text-decoration: none;
                        font-weight: bold;
                        font-size: 16px;
                        padding: 16px 24px;
                        border-radius: 12px;
                        box-shadow: 0 4px 14px rgba(2, 132, 199, 0.4);
                    }
                    .footer {
                        margin-top: 24px;
                        font-size: 12px;
                        color: #64748b;
                        line-height: 1.5;
                        border-top: 1px solid #334155;
                        padding-top: 16px;
                        text-align: left;
                    }
                </style>
                <script>
                    window.onload = function() {
                        setTimeout(function() {
                            window.location.href = "applock://open";
                        }, 250);
                    };
                </script>
            </head>
            <body>
                <div class="card">
                    <div class="icon">🛡️</div>
                    <h1>Applock</h1>
                    <p>Die App befindet sich im unsichtbaren Stealth-Modus.<br>Tippe auf den Button, um das Sicherheits-Dashboard zu öffnen:</p>
                    <a class="btn" href="applock://open">Applock starten</a>
                    <div class="footer">
                        <b>Alternative Wege zum Öffnen:</b><br>
                        • <b>Einstellungen:</b> Android-Einstellungen ➔ Apps ➔ Applock ➔ Öffnen<br>
                        • <b>Telefon-Tastatur:</b> Code <b>*#*#744353#*#*</b> wählen
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val customFileContent = "APPLOCK_TRIGGER\nscheme=applock://open\n"

        val ok1 = writeDownloadFile(context, TRIGGER_FILE_HTML, "text/html", htmlContent.toByteArray(Charsets.UTF_8))
        val ok2 = writeDownloadFile(context, TRIGGER_FILE_CUSTOM, "application/octet-stream", customFileContent.toByteArray(Charsets.UTF_8))
        return ok1 || ok2
    }

    private fun writeDownloadFile(context: Context, filename: String, mimeType: String, data: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(data) }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    true
                } else false
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val target = File(downloadsDir, filename)
                FileOutputStream(target).use { it.write(data) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
