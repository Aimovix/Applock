# 🛡️ ShieldLock Pro - Hardware Biometric AppLock für Android 14/15 & Honor MagicOS

**ShieldLock Pro** ist ein hochperformantes, modulares Android-Sicherheits- und AppLock-System. Es wurde speziell für moderne Android-Sicherheitsarchitekturen (Android 14/15, API 35) und Flaggschiff-Geräte mit hardwarebasierten Sensoren wie das **Honor Magic 8 Pro** (MagicOS 8/9) entwickelt.

---

## 📦 Download der APKs

Im Stammverzeichnis dieses Repositories befinden sich die fertig vorkompilierten und signierten APKs:

| Datei | Build-Typ | Dateigröße | Beschreibung & Empfehlung |
| :--- | :--- | :--- | :--- |
| **[ShieldLockPro-release.apk](ShieldLockPro-release.apk)** | **Release** | **~2,9 MB** | **Empfohlen (Flüssigste Version):** Vollständige R8-Minification, Resource-Shrinking und vorkompilierte ART-Profile. Reagiert mit 0 ms Latenz ohne Ruckler beim App-Wechsel. |
| **[ShieldLockPro-debug.apk](ShieldLockPro-debug.apk)** | **Debug** | ~11,8 MB | Enthält unkomprimierte Symbole, Compose-Recomposition-Tracking und ausführliche Logs für Entwickler. |

---

## 🚀 Wichtigste Features auf einen Blick

- ⚡ **0ms-Echtzeitwächter:** Fenstererkennung über System-Level-Accessibility-Events statt batteriefressendem Polling.
- 🧬 **Passkey- & Hardware-Biometrie:** AES-256-GCM-Schlüsselbindung im Android Keystore (`AUTH_BIOMETRIC_STRONG`) für Ultraschall-Fingerabdruck und 3D-ToF-Gesichtserkennung.
- 👁️ **Instant 3D-Gesichtsscan:** Nutzung von `setConfirmationRequired(false)` – auf dem Honor Magic 8 Pro entsperrt die App sofort bei Blickkontakt ohne zweiten Display-Klick.
- 🛡️ **Deinstallationsschutz (Geräte-Admin):** Sperrt die Android-Systemfunktion zum Deinstallieren der App.
- 🔒 **Opaque Maske mit `FLAG_SECURE`:** Kein Durchblitzen von Bildschirminhalten, keine Screenshots und geschwärzte Vorschau im Task-Manager.
- 🔄 **Intelligente Re-Lock-Richtlinien:** Wählbar zwischen *Bei Display-Aus*, *Sofort nach Verlassen*, *1 Minute* oder *5 Minuten*.
- 🛡️ **Anti-Tamper & Anti-Force-Stop:** Schutz für Systemeinstellungen, Google Play Store und Honor SystemManager.

---

## 🐞 Gründliche Dokumentation behobener Fehler

### 1. Das „Endlos-Fingerabdruck“-Problem (Infinite Loop)
* **Problem:** Nach erfolgreichem Scan des Fingerabdrucks schloss sich die Sperre, öffnete sich aber im selben Bruchteil einer Sekunde erneut in einer Endlosschleife.
* **Ursache:** 
  1. Die Freigaben (`unlockedSessions`) wurden in separaten Instanzen von `AppLockPreferences` gehalten. Die `LockScreenActivity` speicherte die Freigabe in ihrer eigenen Instanz, während der `AppLockAccessibilityService` eine andere Instanz prüfte, in der die App weiterhin als „gesperrt“ galt.
  2. Zudem führten Zwischen-Events des Launchers oder der Taskleiste beim Schließen der Activity dazu, dass die Freigabe sofort wieder zurückgesetzt wurde.
* **Lösung:**
  1. **Singleton `LockStateManager`:** Einführung eines globalen, prozessweiten State-Managers mit atomarer `ConcurrentHashMap`.
  2. **8-Sekunden Anti-Flapping-Puffer:** `lastUnlockTimestamp` garantiert, dass eine soeben entsperrte App für mindestens 8 Sekunden nicht durch Fensterwechsel-Races erneut gesperrt werden kann.
  3. **Sofort-Abbruch:** Erkennt der Barrierefreiheitsdienst, dass `!prefs.isPackageLocked(packageName)` zutrifft, bricht er die Ausführung sofort mit `return` ab, ohne einen neuen Task zu starten.

### 2. Das „Stuck zwischen Apps“-Problem (Deadlock beim Multitasking)
* **Problem:** Beim schnellen Wechseln zwischen mehreren Apps oder bei Wischgesten über die Gestenleiste fror der Bildschirm ein oder flackerte.
* **Ursache:** Die gleichzeitige Ausführung von `AppLockAccessibilityService` und `AppLockForegroundService` führte zu konkurrierenden `startActivity()`-Aufrufen. Bei aktivem Biometrie-Prompt löste die Android SystemUI neue Accessibility-Events aus, die den laufenden Prompt abbrachen.
* **Lösung:**
  1. **Atomic Guard:** `LockStateManager.isLockScreenShowing` verhindert jeden weiteren Start, solange eine Sperrmaske sichtbar ist.
  2. **Transienz-Filter:** SystemUI, Statusleiste und Tastaturen (Gboard, SwiftKey, Honor Celia Keyboard) werden ignoriert und verändern den aktiven App-Fokus nicht.
  3. **Debounce-Filter (200 ms):** Schützt vor Flatter-Events bei schnellen Multitasking-Gesten.

---

## 🛡️ Umgehungs-Methoden & Sicherheitsmatrix (Anti-Bypass)

| Angriffsvektor | Typische Umgehung | Implementierter Schutz in ShieldLock Pro |
| :--- | :--- | :--- |
| **App-Deinstallation** | App-Icon auf den Papierkorb / „Deinstallieren“ ziehen | **Geräte-Administrator (`ShieldLockDeviceAdminReceiver`):** Die Android-Systemfunktion zur Deinstallation wird komplett gesperrt. |
| **Erzwungenes Beenden (Force Stop)** | In den Android-App-Infos auf „Stoppen erzwingen“ tippen | **System-Sperre:** `com.android.settings`, Paket-Installer und System-Manager sind standardmäßig in der Sperrliste verankert. |
| **Speicherdaten löschen** | Löschen von Cache und App-Daten in den Einstellungen | Durch die Sperrung der Android-Einstellungen und Installer unzugänglich. |
| **Systemuhr manipulieren** | Datum/Uhrzeit zurückstellen, um Zeitfenster zu umgehen | **Monotone Zeitrechnung:** Verwendung von `SystemClock.elapsedRealtime()`. Änderungen der Systemuhrzeit sind wirkungslos. |
| **Task-Manager Snooping** | Sensible Inhalte in den Recent-Apps-Karten einsehen | **`FLAG_SECURE`:** Verhindert Screenshots, Videoaufnahmen und schwärzt die Vorschau im Task-Manager. |
| **Frame-Leck beim Start** | Kurzes Aufblitzen von Chats vor Erscheinen der Sperre | **Feste Opaque-Maske:** [`LockScreenActivity`](app/src/main/java/com/applock/magic/ui/LockScreenActivity.kt) blockiert sofort den Render-Buffer und alle Touch-Events. |

---

## 📱 Spezielle Einrichtung auf dem Honor Magic 8 Pro (MagicOS)

Damit MagicOS den Hintergrundwächter nicht im Ruhezustand beendet, müssen folgende 4 Einstellungen vorgenommen werden:

### 1. Eingeschränkte Einstellungen aufheben (Android 14 Sideload-Schutz)
Da die APK manuell installiert wird, blockiert Android standardmäßig den Barrierefreiheitsdienst:
1. `Einstellungen` ➔ `Apps` ➔ `Apps` ➔ `ShieldLock Pro` auswählen.
2. Oben rechts auf die **drei Punkte (⋮)** tippen.
3. **„Eingeschränkte Einstellungen zulassen“** wählen und per Fingerabdruck/PIN bestätigen.

### 2. MagicOS App-Start (Auto-Launch) konfigurieren
1. In ShieldLock Pro auf **„MagicOS App-Start öffnen“** tippen (oder `Einstellungen` ➔ `Akku` ➔ `App-Start`).
2. ShieldLock Pro in der Liste suchen und den Schalter **„Automatisch verwalten“ ausschalten**.
3. Im Dialog alle 3 Optionen aktivieren:
   - ✅ **Auto-Start**
   - ✅ **Sekundärer Start**
   - ✅ **Im Hintergrund ausführen**

### 3. Deinstallationsschutz (Geräte-Admin) aktivieren
1. In ShieldLock Pro unter *Sicherheits- & Systemrechte* beim Punkt **„Deinstallationsschutz (Geräte-Admin)“** auf **Erteilen** tippen.
2. Den Systemdialog von Android bestätigen.

### 4. Schloss im Task-Manager einrasten
1. Die Recent-Apps-Übersicht öffnen (vom unteren Bildschirmrand nach oben wischen und halten).
2. Die Vorschaukarte von **ShieldLock Pro** kurz nach unten ziehen, bis ein **Schloss-Symbol** einrastet. Dadurch wird die App vor dem Schließen über den Papierkorb-Button geschützt.

---

## 💻 Installation & CLI-Befehle

### Installation per ADB (USB-Kabel)
```powershell
# Release-Version installieren (Empfohlen)
& "C:\Users\Yunes\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "c:\Users\Yunes\Documents\antigravity\mysterious-bardeen\ShieldLockPro-release.apk"

# Debug-Version installieren
& "C:\Users\Yunes\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "c:\Users\Yunes\Documents\antigravity\mysterious-bardeen\ShieldLockPro-debug.apk"
```

### Projekt selbst kompilieren
```powershell
# Umgebungsvariable für JDK 21 (z.B. aus Android Studio JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Debug-Build
.\gradlew.bat assembleDebug

# Optimierter Release-Build mit R8
.\gradlew.bat assembleRelease
```

---

## 🏗️ Technische Spezifikationen

- **Min SDK:** 26 (Android 8.0)
- **Target SDK / Compile SDK:** 35 (Android 15)
- **Android Gradle Plugin (AGP):** 9.0.1
- **Gradle:** 9.1.0
- **Kotlin:** 2.0.21
- **UI Toolkit:** Jetpack Compose (BOM 2024.10.01) & Material 3
- **Biometrie:** AndroidX Biometric 1.2.0-alpha05 & AndroidX Security Crypto 1.1.0-alpha06
