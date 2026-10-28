package com.applock.magic.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

data class PresetIcon(
    val id: String,
    val name: String,
    val defaultLabel: String,
    val bitmap: Bitmap
)

object IconCustomizerManager {

    private const val PREFS_NAME = "app_lock_custom_icons"
    private const val ICONS_DIR = "custom_app_icons"

    private fun getIconsDir(context: Context): File {
        val dir = File(context.filesDir, ICONS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveCustomIcon(context: Context, packageName: String, bitmap: Bitmap) {
        val file = File(getIconsDir(context), "$packageName.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {}
    }

    fun getCustomIcon(context: Context, packageName: String): Bitmap? {
        val file = File(getIconsDir(context), "$packageName.png")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomLabel(context: Context, packageName: String, label: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("label_$packageName", label).apply()
    }

    fun getCustomLabel(context: Context, packageName: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("label_$packageName", null)
    }

    fun removeCustomization(context: Context, packageName: String) {
        val file = File(getIconsDir(context), "$packageName.png")
        if (file.exists()) file.delete()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("label_$packageName").apply()
    }

    /**
     * Lädt ein Foto/Bild aus der Galerie oder Dateien und skaliert es quadratisch auf 192x192
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val size = 192
                val scaled = Bitmap.createScaledBitmap(original, size, size, true)

                // Optional abrunden für ein sauberes App-Icon
                val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
                canvas.drawRoundRect(rectF, 40f, 40f, paint)
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                output
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Erstellt einen Pinned Shortcut auf dem MagicOS / Android Startbildschirm
     */
    fun createShortcut(
        context: Context,
        targetPackage: String,
        label: String,
        icon: Bitmap
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage) ?: return false
            launchIntent.action = Intent.ACTION_MAIN
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

            val shortcutId = "app_lock_icon_${targetPackage}_${System.currentTimeMillis()}"

            val pinShortcutInfo = ShortcutInfo.Builder(context, shortcutId)
                .setIcon(Icon.createWithBitmap(icon))
                .setShortLabel(label.ifBlank { targetPackage })
                .setLongLabel(label.ifBlank { targetPackage })
                .setIntent(launchIntent)
                .build()

            return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        }
        return false
    }

    /**
     * Erzeugt programmgesteuert 8 gestochen scharfe Vektor-Tarn-Icons (Rechner, Notizen, Wetter, etc.)
     */
    fun getPresetIcons(): List<PresetIcon> {
        return listOf(
            PresetIcon("calculator", "Rechner", "Rechner", drawCalculatorIcon()),
            PresetIcon("notes", "Notizen", "Notizblock", drawNotesIcon()),
            PresetIcon("weather", "Wetter", "Wetter", drawWeatherIcon()),
            PresetIcon("clock", "Uhr", "Uhr", drawClockIcon()),
            PresetIcon("camera", "Kamera", "Kamera", drawCameraIcon()),
            PresetIcon("compass", "Kompass", "Kompass", drawCompassIcon()),
            PresetIcon("music", "Musik", "Musik", drawMusicIcon()),
            PresetIcon("files", "Dateien", "Dateimanager", drawFilesIcon())
        )
    }

    private fun drawBaseIcon(bgColor: Int): Pair<Bitmap, Canvas> {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rectF, 44f, 44f, paint)
        return Pair(bitmap, canvas)
    }

    private fun drawCalculatorIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#2563EB"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        // Gitterlinien
        canvas.drawLine(96f, 36f, 96f, 156f, paint)
        canvas.drawLine(36f, 96f, 156f, 96f, paint)

        // Plus
        canvas.drawLine(66f, 54f, 66f, 78f, paint)
        canvas.drawLine(54f, 66f, 78f, 66f, paint)

        // Minus
        canvas.drawLine(114f, 66f, 138f, 66f, paint)

        // Mal (x)
        canvas.drawLine(56f, 116f, 76f, 136f, paint)
        canvas.drawLine(76f, 116f, 56f, 136f, paint)

        // Gleich (=)
        canvas.drawLine(114f, 120f, 138f, 120f, paint)
        canvas.drawLine(114f, 132f, 138f, 132f, paint)
        return bitmap
    }

    private fun drawNotesIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#D97706"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        // Blatt Umriss
        canvas.drawRoundRect(RectF(48f, 36f, 144f, 156f), 12f, 12f, paint)
        // Zeilen
        canvas.drawLine(68f, 66f, 124f, 66f, paint)
        canvas.drawLine(68f, 96f, 124f, 96f, paint)
        canvas.drawLine(68f, 126f, 104f, 126f, paint)
        return bitmap
    }

    private fun drawWeatherIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#0284C7"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE047")
            style = Paint.Style.FILL
        }
        // Sonne
        canvas.drawCircle(80f, 75f, 26f, paint)
        // Wolke
        paint.color = Color.WHITE
        canvas.drawCircle(85f, 115f, 24f, paint)
        canvas.drawCircle(115f, 110f, 30f, paint)
        canvas.drawCircle(140f, 120f, 20f, paint)
        canvas.drawRect(85f, 115f, 140f, 140f, paint)
        return bitmap
    }

    private fun drawClockIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#475569"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        // Zifferblatt
        canvas.drawCircle(96f, 96f, 54f, paint)
        // Zeiger (10 vor 2)
        canvas.drawLine(96f, 96f, 96f, 62f, paint)
        canvas.drawLine(96f, 96f, 122f, 96f, paint)
        return bitmap
    }

    private fun drawCameraIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#334155"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        // Gehäuse
        canvas.drawRoundRect(RectF(40f, 56f, 152f, 144f), 16f, 16f, paint)
        // Sucher-Buckel
        canvas.drawRoundRect(RectF(76f, 44f, 116f, 60f), 6f, 6f, paint)
        // Linse
        canvas.drawCircle(96f, 100f, 24f, paint)
        return bitmap
    }

    private fun drawCompassIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#0D9488"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        canvas.drawCircle(96f, 96f, 54f, paint)
        // Nadel
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#EF4444")
        val north = Path().apply {
            moveTo(96f, 54f)
            lineTo(86f, 96f)
            lineTo(106f, 96f)
            close()
        }
        canvas.drawPath(north, paint)

        paint.color = Color.WHITE
        val south = Path().apply {
            moveTo(96f, 138f)
            lineTo(86f, 96f)
            lineTo(106f, 96f)
            close()
        }
        canvas.drawPath(south, paint)
        return bitmap
    }

    private fun drawMusicIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#7C3AED"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Notenköpfe
        canvas.drawCircle(68f, 130f, 16f, paint)
        canvas.drawCircle(128f, 115f, 16f, paint)
        // Hälse
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawLine(82f, 130f, 82f, 60f, paint)
        canvas.drawLine(142f, 115f, 142f, 45f, paint)
        // Balken
        paint.strokeWidth = 14f
        canvas.drawLine(78f, 60f, 146f, 45f, paint)
        return bitmap
    }

    private fun drawFilesIcon(): Bitmap {
        val (bitmap, canvas) = drawBaseIcon(Color.parseColor("#4F46E5"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        // Ordner Tab
        canvas.drawRoundRect(RectF(42f, 52f, 84f, 72f), 8f, 8f, paint)
        // Ordner Körper
        canvas.drawRoundRect(RectF(42f, 66f, 150f, 144f), 12f, 12f, paint)
        return bitmap
    }
}
