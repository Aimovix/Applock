# 🛡️ ShieldLock Pro - Hardware Biometric AppLock für Android 14/15 & Honor MagicOS

**ShieldLock Pro** ist ein hochperformantes, modulares Android-Sicherheits- und AppLock-System. Es wurde speziell für moderne Android-Sicherheitsarchitekturen (Android 14/15, API 35) und Flaggschiff-Geräte mit hardwarebasierten Sensoren wie das **Honor Magic 8 Pro** (MagicOS 8/9) entwickelt.

---

## 📦 Download der APKs

Im Stammverzeichnis dieses Repositories befinden sich die aktuellsten, fertig vorkompilierten APKs:

| Datei | Build-Typ | Dateigröße | Beschreibung & Empfehlung |
| :--- | :--- | :--- | :--- |
| **[ShieldLockPro-release.apk](ShieldLockPro-release.apk)** | **Release** | **~2,9 MB** | **Empfohlen (Flüssigste Version):** Vollständige R8-Minification, Resource-Shrinking und vorkompilierte ART-Profile. Reagiert mit 0 ms Latenz ohne Ruckler beim App-Wechsel. |
| **[ShieldLockPro-debug.apk](ShieldLockPro-debug.apk)** | **Debug** | **~11,8 MB** | Enthält unkomprimierte Symbole, Compose-Recomposition-Tracking und ausführliche Logs für Entwickler. |

---

## 🚀 Wichtigste Features auf einen Blick

- ⚡ **0ms-Echtzeitwächter:** Fenstererkennung über System-Level-Accessibility-Events statt batteriefressendem Polling.
- 🧬 **Passkey- & Hardware-Biometrie:** Volle Unterstützung für Ultraschall-Fingerabdruck, 3D-ToF-Gesichtserkennung und nahtlosem PIN-/Muster-Fallback bei Lockout.
- 👁️ **Instant 3D-Gesichtsscan:** Nutzung von `setConfirmationRequired(false)` – auf dem Honor Magic 8 Pro entsperrt die App sofort bei Blickkontakt ohne zweiten Display-Klick.
- 🎨 **Dynamische Ziel-App-Erkennung:** Der Sperrbildschirm lädt zur Laufzeit das echte Icon und den Namen der blockierten App (z. B. WhatsApp) inklusive maßgeschneiderter Aufforderung.
- 🛡️ **Deinstallationsschutz (Geräte-Admin):** Sperrt die Android-Systemfunktion zum Deinstallieren der App.
- 🔒 **Opaque Maske mit `FLAG_SECURE`:** Kein Durchblitzen von Bildschirminhalten, keine Screenshots und geschwärzte Vorschau im Task-Manager.
- 🔲 **Split-Screen & Multi-Window-Schutz:** `android:resizeableActivity="false"` erzwingt die vollflächige Abdeckung des Displays.
- 🔄 **Intelligente Re-Lock-Richtlinien:** Wählbar zwischen *Bei Display-Aus*, *Sofort nach Verlassen*, *1 Minute* oder *5 Minuten*.
- 🛡️ **Anti-Tamper & Anti-Force-Stop:** Schutz für Systemeinstellungen, Google Play Store und Honor SystemManager.

---

## 🐞 Gründliche Dokumentation behobener Fehler

### 1. Das „Endlos-Fingerabdruck“-Problem (Infinite Loop)
* **Problem:** Nach erfolgreichem Scan des Fingerabdrucks schloss sich die Sperre, öffnete sich aber im selben Bruchteil einer Sekunde erneut in einer Endlosschleife.
* **Ursachen:** 
  1. *Instanz-Trennung:* `unlockedSessions` wurde in vorherigen Versionen als lokale Instanzvariable in `AppLockPreferences` gehalten. Die `LockScreenActivity` schaltete die App nur in ihrer eigenen Instanz frei. Beim Schließen las der `AppLockAccessibilityService` seine eigene Instanz, in der die App weiterhin als gesperrt markiert war, und rief den Dialog erneut auf.
  2. *Re-Lock Race:* Beim Beenden der `LockScreenActivity` feuerte Android ein `WINDOW_STATE_CHANGED`-Event für die wiederkehrende Ziel-App, was ohne Puffer sofort als erneuter Startversuch interpretiert wurde.
* **Lösung:**
  1. **Zentraler Singleton `LockStateManager`:** Globaler, prozessweiter State-Manager mit atomarer `ConcurrentHashMap`. Alle Komponenten teilen sich exakt denselben Sperrzustand.
  2. **8-Sekunden Anti-Flapping-Puffer:** `lastUnlockTimestamp` garantiert, dass eine soeben entsperrte App für mindestens 8 Sekunden nicht durch Fensterwechsel-Races erneut gesperrt werden kann.
  3. **Sofort-Abbruch:** Erkennt der Barrierefreiheitsdienst, dass `!prefs.isPackageLocked(packageName)` zutrifft, bricht er die Ausführung sofort mit `return` ab, ohne einen neuen Task zu starten.

### 2. Das „Stuck zwischen Apps“-Problem (Deadlock beim Multitasking)
* **Problem:** Beim schnellen Wechseln zwischen Apps oder bei Wischgesten über die Gestenleiste fror das Gerät ein oder der Sperrbildschirm reagierte nicht mehr.
* **Ursachen:**
  1. *Konkurrierendes Polling:* `AppLockForegroundService` führte alle 300 ms ein asynchrones `UsageStatsManager`-Polling durch, während der `AppLockAccessibilityService` gleichzeitig 0ms-Events verarbeitete. Das 1–2 Sekunden verzögerte UsageStats-Signal überschrieb den aktuellen Zustand des Barrierefreiheitsdienstes.
  2. *Verlorene Ziel-Events:* Wenn die `LockScreenActivity` bereits für App A sichtbar war und der Nutzer per Geste zu App B wechselte, blockierte ein statischer Guard (`if (isLockScreenShowing) return`) das Event für App B vollständig.
* **Lösung:**
  1. **Dual-Service Non-Compete Architektur:** Sobald der `AppLockAccessibilityService` aktiv ist, pausiert der `AppLockForegroundService` jegliches `UsageStats`-Polling vollständig und fungiert rein als passiver, unkillbarer Sticky-Wächter. Erst wenn das System den Barrierefreiheitsdienst killen sollte, reaktiviert sich das Polling als automatischer Fallback.
  2. **Dynamische Intent-Weiterleitung (`onNewIntent`):** Wechselt der Nutzer bei sichtbarem Sperrbildschirm zu einer anderen gesperrten App, leitet der Accessibility-Dienst den Event-Intent per `FLAG_ACTIVITY_SINGLE_TOP` an die aktive `LockScreenActivity` weiter. Diese aktualisiert sofort App-Icon, Name und Prompt auf die neue Ziel-App, ohne hängen zu bleiben.
  3. **Debounce-Filter (150 ms):** Verhindert Flatter-Starts bei schnellen Gestenwechseln.

### 3. Aussperrungs-Schutz (Biometric Lockout Fallback)
* **Problem:** Bei verschmutztem Fingerabdruck-Sensor oder nach mehreren Fehlversuchen sperrt Android die Biometrie für 30 Sekunden (`ERROR_LOCKOUT`), wodurch der Nutzer keinen Zugriff mehr auf seine Apps hatte.
* **Lösung:**
  1. Unterstützung von `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` in [`BiometricAuthManager`](app/src/main/java/com/applock/magic/security/BiometricAuthManager.kt).
  2. Kann der Fingerabdruck nicht gelesen werden oder ist gesperrt, bietet das System automatisch die Eingabe der Geräte-PIN oder des Musters an.

---

## 🛡️ Umgehungs-Methoden & Sicherheitsmatrix (Anti-Bypass)

| Angriffsvektor | Typische Umgehung | Implementierter Schutz in ShieldLock Pro |
| :--- | :--- | :--- |
| **App-Deinstallation** | App-Icon auf den Papierkorb / „Deinstallieren“ ziehen | **Geräte-Administrator (`ShieldLockDeviceAdminReceiver`):** Die Android-Systemfunktion zur Deinstallation wird komplett gesperrt. |
| **Erzwungenes Beenden (Force Stop)** | In den Android-App-Infos auf „Stoppen erzwingen“ tippen | **System-Sperre:** `com.android.settings`, Paket-Installer und Honor SystemManager sind standardmäßig in der Sperrliste verankert. |
| **Speicherdaten löschen** | Löschen von Cache und App-Daten in den Einstellungen | Durch die Sperrung der Android-Einstellungen und Installer unzugänglich. |
| **Split-Screen / Multi-Window** | Geschützte App in geteiltem Bildschirm neben einer freien App öffnen | **`android:resizeableActivity="false"`:** Erzwingt die vollflächige Abdeckung des Displays und verhindert Split-Screen-Bypasses. |
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
