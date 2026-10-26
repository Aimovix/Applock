# 🛡️ ShieldLock Pro - Hardware Biometric AppLock für Android 14/15/17 & Honor MagicOS

**ShieldLock Pro** ist ein hochperformantes, modulares Android-Sicherheits- und AppLock-System. Es wurde speziell für moderne Android-Sicherheitsarchitekturen (Android 14/15/17, API 35+) und Flaggschiff-Geräte mit hardwarebasierten Sensoren wie das **Honor Magic 8 Pro** (MagicOS 8/9/11, Firmware `11.0.0.130(C431)`) entwickelt.

---

## 📦 Download der APKs

Im Stammverzeichnis dieses Repositories befinden sich die aktuellsten, fertig vorkompilierten APKs:

| Datei | Build-Typ | Dateigröße | Beschreibung & Empfehlung |
| :--- | :--- | :--- | :--- |
| **[ShieldLockPro-release.apk](ShieldLockPro-release.apk)** | **Release** | **~2,9 MB** | **Empfohlen (Flüssigste Version):** Vollständige R8-Minification, Resource-Shrinking und vorkompilierte ART-Profile. Reagiert mit 0 ms Latenz ohne Ruckler beim App-Wechsel. |
| **[ShieldLockPro-debug.apk](ShieldLockPro-debug.apk)** | **Debug** | **~11,8 MB** | Enthält unkomprimierte Symbole, Compose-Recomposition-Tracking und ausführliche Logs für Entwickler. |

---

## 🚀 Wichtigste Features & Architektur-Highlights

- ⚡ **0ms-Echtzeitwächter:** Fenstererkennung über System-Level-Accessibility-Events (`TYPE_WINDOW_STATE_CHANGED` & `TYPE_WINDOWS_CHANGED`) statt batteriefressendem Polling.
- 🧬 **Passkey- & Hardware-Biometrie:** Volle Unterstützung für Ultraschall-Fingerabdruck, 3D-ToF-Gesichtserkennung und nahtlosem PIN-/Muster-Fallback bei Lockout.
- 👁️ **Instant 3D-Gesichtsscan:** Nutzung von `setConfirmationRequired(false)` – auf dem Honor Magic 8 Pro entsperrt die App sofort bei Blickkontakt ohne zweiten Display-Klick.
- 🎨 **Sichtbare native Sperrmaske mit Ziel-App-Branding:** Zeigt direkt das echte Icon, den Namen der blockierten App und die Entsperr-Schaltflächen ohne störenden Overlay-Schleier.
- 🔄 **Deterministische Handoff-State-Machine:** Ersetzt pauschale Zeittimer durch eine präzise Zustandslogik (`UNLOCK_HANDOFF` ➔ `UNLOCKED_ACTIVE`). Verhindert Re-Lock-Schleifen beim Schließen der Maske vollständig, verriegelt die App aber sofort beim Verlassen.
- 🪟 **Floating-Window & MagicOS Mini-Fenster-Schutz:** Scannt den gesamten Fenstertree (`flagRetrieveInteractiveWindows`), um zu verhindern, dass geschützte Apps über die MagicOS Smart-Seitenleiste oder schwebende Fenster entsperrt werden.
- 🤖 **AI-Button & Magic Portal 2.0 Schutz:** `onProvideAssistContent()` und `onProvideAssistData()` verhindern das automatische Sniffen von App-Inhalten durch den Honor AI-Button oder Knuckle-Gesten.
- 📺 **PiP & AudioFocus-Killer (WIU Status):** [`PipAndAudioGuard`](app/src/main/java/com/applock/magic/security/PipAndAudioGuard.kt) und [`LockScreenActivity`](app/src/main/java/com/applock/magic/ui/LockScreenActivity.kt) erzwingen `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` im While-In-Use Status und stoppen Ton-Lecks bei gesperrten PiP-Videos.
- 🔐 **Direct-Boot-Aware & Device-Protected Storage:** Einstellungen und Sperrlisten liegen im hardwareverschlüsselten DPS (`createDeviceProtectedStorageContext()`), sodass der Schutz auch nach einem Neustart vor der ersten PIN-Eingabe (`LOCKED_BOOT_COMPLETED`) aktiv ist.
- 🛡️ **Advanced Protection / Sideloading-Konformität:** `android:isAccessibilityTool="true"` im Accessibility-XML verhindert automatische Abschaltungen bei aktiviertem Google Advanced Protection Mode.
- 🧹 **Zero-Recents-Footprint:** `android:noHistory="true"`, `android:excludeFromRecents="true"` und `finishAndRemoveTask()` schützen vor dem automatischen Beenden durch den MagicOS Task-Manager.
- 🛡️ **Deinstallationsschutz (Geräte-Admin):** Sperrt die Android-Systemfunktion zum Deinstallieren der App.
- 🔒 **Opaque Maske mit `FLAG_SECURE`:** Kein Durchblitzen von Bildschirminhalten, keine Screenshots und geschwärzte Vorschau im Task-Manager.
- 🔲 **Split-Screen-Schutz:** `android:resizeableActivity="false"` erzwingt die vollflächige Abdeckung des Displays.

---

## 🐞 Gründliche Dokumentation behobener Fehler & Härtungen

### 1. Das „Endlos-Fingerabdruck“-Problem (Infinite Loop)
* **Problem:** Nach erfolgreichem Scan des Fingerabdrucks schloss sich die Sperre, öffnete sich aber im selben Bruchteil einer Sekunde erneut in einer Endlosschleife.
* **Ursachen:** 
  1. *Instanz-Trennung:* `unlockedSessions` wurde in vorherigen Versionen als lokale Instanzvariable in `AppLockPreferences` gehalten. Die `LockScreenActivity` schaltete die App nur in ihrer eigenen Instanz frei. Beim Schließen las der `AppLockAccessibilityService` seine eigene Instanz, in der die App weiterhin als gesperrt markiert war, und rief den Dialog erneut auf.
  2. *Re-Lock Race:* Beim Beenden der `LockScreenActivity` feuerte Android ein `WINDOW_STATE_CHANGED`-Event für die wiederkehrende Ziel-App, was ohne Puffer sofort als erneuter Startversuch interpretiert wurde.
* **Lösung:**
  1. **Zentraler Singleton `LockStateManager`:** Globaler, prozessweiter State-Manager mit atomarer `ConcurrentHashMap`. Alle Komponenten teilen sich exakt denselben Sperrzustand.
  2. **Deterministische Handoff-State-Machine:** Nach erfolgreicher Biometrie erhält die App den temporären Status `UNLOCK_HANDOFF`. Zwischen-Events von SystemUI und Launchern werden während des Schließens der Maske toleriert. Sobald die Ziel-App den Fokus übernimmt, schaltet der Status auf `UNLOCKED_ACTIVE`.
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
| **Split-Screen & Floating-Window** | Geschützte App in geteiltem Bildschirm oder Mini-Fenster öffnen | **`android:resizeableActivity="false"` + `TYPE_WINDOWS_CHANGED`-Scan:** Erzwingt Vollbild und erkennt auch schwebende Mini-Fenster. |
| **AI-Button / Magic Portal 2.0** | Bildschirminhalte über den Honor AI-Button oder Gesten scannen | **Assist-Sperre:** `onProvideAssistContent()` und `FLAG_SECURE` blockieren KI-Screen-Sniffing vollständig. |
| **PiP-Videobypass & Ton-Leak** | Videoanruf / Video in Picture-in-Picture weiterlaufen lassen | **`PipAndAudioGuard` + WIU Focus:** Entzieht sofort die Audioberechtigung (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) und verriegelt den Bildschirm. |
| **Neustart-Lücke (Reboot Bypass)** | Nach Reboot Apps öffnen, bevor der Schutzdienst initialisiert ist | **Direct-Boot-Aware:** `LOCKED_BOOT_COMPLETED` startet den Schutz im hardwareverschlüsselten DPS noch vor der ersten PIN-Eingabe. |
| **Systemuhr manipulieren** | Datum/Uhrzeit zurückstellen, um Zeitfenster zu umgehen | **Monotone Zeitrechnung:** Verwendung von `SystemClock.elapsedRealtime()`. Änderungen der Systemuhrzeit sind wirkungslos. |
| **Task-Manager Snooping** | Sensible Inhalte in den Recent-Apps-Karten einsehen | **`FLAG_SECURE`:** Verhindert Screenshots, Videoaufnahmen und schwärzt die Vorschau im Task-Manager. |
| **Frame-Leck beim Start (16ms Blur)** | Kurzes Aufblitzen von Inhalten im MagicOS 11 Liquid Glass Compositor | **Feste Opaque-Maske:** [`LockScreenActivity`](app/src/main/java/com/applock/magic/ui/LockScreenActivity.kt) blockiert mit Vollton (#0F172A) sofort den Render-Buffer und zeigt das saubere UI an. |

---

## 📱 Spezielle Einrichtung auf dem Honor Magic 8 Pro (MagicOS 11)

### 1. Eingeschränkte Einstellungen aufheben (Sideload-Schutz)
Da die APK manuell installiert wird, blockiert Android standardmäßig den Barrierefreiheitsdienst:
* **Option A (In den Einstellungen):**
  1. `Einstellungen` ➔ `Apps` ➔ `Apps` ➔ `ShieldLock Pro` auswählen.
  2. Oben rechts auf die **drei Punkte (⋮)** tippen.
  3. **„Eingeschränkte Einstellungen zulassen“** wählen und per Fingerabdruck/PIN bestätigen.
* **Option B (Schnellbefehl per ADB):**
  ```powershell
  & "C:\Users\Yunes\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell appops set com.applock.magic ACCESS_RESTRICTED_SETTINGS allow
  ```

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
- **Target SDK / Compile SDK:** 35 (Android 15 / kompatibel bis Android 17 / MagicOS 11)
- **Android Gradle Plugin (AGP):** 9.0.1
- **Gradle:** 9.1.0
- **Kotlin:** 2.0.21
- **UI Toolkit:** Jetpack Compose (BOM 2024.10.01) & Material 3
- **Biometrie:** AndroidX Biometric 1.2.0-alpha05 & AndroidX Security Crypto 1.1.0-alpha06
