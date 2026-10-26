# ShieldLock Pro - Hardware Biometric AppLock für Android 14/15 & Honor MagicOS

Modernes, hochperformantes Android AppLock-System, speziell optimiert für aktuelle Android-Sicherheitsstandards (Android 14/15, API 35) und Flaggschiff-Geräte wie das **Honor Magic 8 Pro** (MagicOS 8/9).

---

## 📦 Enthaltene APKs (Aktualisiert & Gehärtet)

| APK-Version | Dateigröße | Beschreibung |
| :--- | :--- | :--- |
| **[ShieldLockPro-release.apk](ShieldLockPro-release.apk)** | **~2,9 MB** | **Flüssige Version:** Vollständige R8-Optimierung, Resource-Shrinking und vorkompilierte ART-Profile für 0ms Latenz |
| **[ShieldLockPro-debug.apk](ShieldLockPro-debug.apk)** | ~11,8 MB | Debug-Version mit vollen Protokollierungs- und Logging-Hooks |

---

## 🛡️ Behobene Fehler & Deadlock-Beseitigung ("Stuck zwischen Apps")

1. **Zentraler prozessweiter `LockStateManager`:**
   * **Ursache des Fehlers:** Zuvor verwaltete jede Komponente (`AppLockAccessibilityService`, `AppLockForegroundService`, `LockScreenActivity`) eine eigene in-memory Instanz von Freigaben. Eine Freigabe in der Activity war für den Hintergrunddienst unsichtbar, was beim Schließen der Activity sofort einen erneuten Sperrbildschirm erzwang (Endlosschleife / Einfrieren).
   * **Lösung:** Alle Freigaben, aktiven Sitzungen und Sperrzustände laufen nun atomar über den Singleton `LockStateManager`.

2. **Relaunch- & Flatter-Schutz (Debouncing):**
   * Wenn `isLockScreenShowing` aktiv ist, werden keine konkurrierenden `startActivity`-Aufrufe mehr abgesetzt.
   * Ein 150ms-Debounce-Filter fängt schnelle Fensterwechsel (z. B. Gesten-Wischleiste auf dem Honor Magic 8 Pro) sauber ab.

3. **Intelligente Transienz-Whitelist:**
   * System-UI (`com.android.systemui`), Biometrie-Dialoge und Tastaturen (Gboard, SwiftKey, Honor Celia Keyboard) werden erkannt und führen **nicht** fälschlicherweise zum Re-Locking der aktiven App während der Eingabe.

---

## 🔒 Behobene Umgehungs-Methoden (Anti-Bypass Matrix)

| Umgehungs-Vektor | Angriffsmethode | Implementierter Schutz in ShieldLock Pro |
| :--- | :--- | :--- |
| **Deinstallation** | App wird im Launcher auf „Deinstallieren“ gezogen | **Geräteadministrator (`DeviceAdminReceiver`):** Android sperrt die Deinstallation systemweit, solange der Admin aktiv ist. |
| **Stoppen erzwingen / Daten löschen** | Nutzer öffnet Systemeinstellungen und löscht App-Daten | **Erweiterte System-Sperrliste:** `com.android.settings`, Honor Package Installer, Google Play Store und Honor SystemManager sind standardmäßig verriegelt. |
| **Zeitumstellung** | Nutzer stellt die Systemuhr zurück, um Freigabefristen zu manipulieren | **Monotone Zeitstempel (`SystemClock.elapsedRealtime()`):** Uhrzeit-Änderungen in Android haben keinerlei Einfluss auf die Sperrfristen. |
| **Task-Manager Snooping** | Blick auf App-Inhalte in der Recent-Apps-Vorschau | **`FLAG_SECURE` & Opaque Maske:** Task-Thumbnails und Bildschirmaufnahmen werden auf Systemebene geschwärzt. |
| **Frame-Leck beim Start** | Kurzes Aufblitzen vor dem Erscheinen des Biometrie-Prompts | **Feste Opaque-Schutzmaske:** [`LockScreenActivity`](app/src/main/java/com/applock/magic/ui/LockScreenActivity.kt) blockiert sofort alle Berührungen und zeigt keine transparenten Lücken. |

---

## ⚙️ Wählbare Re-Lock-Richtlinien

In der App kann das Sperrverhalten flexibel konfiguriert werden:
* **Sofort sperren (Höchste Sicherheit):** Die App wird im selben Augenblick wieder verriegelt, in dem der Nutzer zu einer anderen App oder zum Launcher wechselt.
* **Bei Display-Aus sperren:** Bleibt nach dem Entsperren aktiv, bis das Display ausgeschaltet wird.
* **1 Minute / 5 Minuten Gnadenfrist:** Erlaubt Multitasking und schnelles Wechseln zwischen Apps ohne erneuten Fingerabdruckscan.

---

## 📱 Wichtige Einrichtung auf dem Honor Magic 8 Pro (MagicOS)

1. **Eingeschränkte Einstellungen aufheben (Android 14 Sideload-Schutz):**
   * `Einstellungen` ➔ `Apps` ➔ `ShieldLock Pro` ➔ oben rechts auf die **3 Punkte** tippen ➔ **„Eingeschränkte Einstellungen zulassen“**.
2. **Deinstallationsschutz aktivieren:**
   * In ShieldLock Pro unter „Sicherheitsrechte“ bei **„Deinstallationsschutz (Geräte-Admin)“** auf *Erteilen* tippen.
3. **MagicOS App-Start (Auto-Launch):**
   * In der App auf **„MagicOS App-Start öffnen“** tippen (oder `Einstellungen` ➔ `Akku` ➔ `App-Start`).
   * Bei *ShieldLock Pro* die Option **„Automatisch verwalten“ ausschalten**.
   * Alle 3 Optionen aktivieren (*Auto-Start*, *Sekundärer Start*, *Im Hintergrund ausführen*).
4. **Schloss in den Recent Apps:**
   * Task-Manager öffnen, Karte von *ShieldLock Pro* leicht nach unten ziehen, bis das **Schloss-Symbol** sichtbar einrastet.

---

## 🛠️ Build & Entwicklung

* **Gradle:** 9.1.0
* **Android Gradle Plugin (AGP):** 9.0.1
* **Kotlin:** 2.0.21
* **Target SDK:** 35 (Android 15)
* **Min SDK:** 26 (Android 8.0)
* **UI:** Jetpack Compose & Material 3

```bash
# Debug-Build
./gradlew assembleDebug

# Optimierter Release-Build
./gradlew assembleRelease
```
