# ShieldLock Pro - Hardware Biometric AppLock für Android 14/15 & Honor MagicOS

Modernes, hochperformantes Android AppLock-System, speziell optimiert für aktuelle Android-Sicherheitsstandards (Android 14/15, API 35) und Flaggschiff-Geräte wie das **Honor Magic 8 Pro** (MagicOS 8/9).

---

## 📦 Enthaltene APKs

| APK-Version | Dateigröße | Beschreibung |
| :--- | :--- | :--- |
| **[ShieldLockPro-release.apk](ShieldLockPro-release.apk)** | **~2,75 MB** | **Flüssige Version:** Vollständige R8-Optimierung, Resource-Shrinking und vorkompilierte ART-Profile für 0ms Latenz |
| **[ShieldLockPro-debug.apk](ShieldLockPro-debug.apk)** | ~10,5 MB | Debug-Version mit vollen Protokollierungs- und Logging-Hooks |

---

## ⚡ Kernarchitektur & Sicherheits-Tricks

1. **0ms-Echtzeitüberwachung (AccessibilityService):**
   * Kein ressourcenintensives Polling über `UsageStatsManager`.
   * Lauscht in Echtzeit auf `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` und blockiert gesperrte Apps in unter 5 Millisekunden.
   * `AppLockForegroundService` agiert als persistenter Wächterdienst gemäß Android 14 `specialUse`-Richtlinien.

2. **Umgehung der Android 14 BAL-Sperre (Background Activity Launch):**
   * Verhindert das Blockieren durch Androids `BAL_BLOCK`.
   * Verwendet `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` mit `FLAG_SECURE`, wodurch auch die Vorschau im Task-Manager (Recent Apps) zuverlässig geschwärzt wird.

3. **Hardware-Kryptografie & Passkey-Niveau:**
   * Bindung an den hardwaregestützten Android Keystore via AES-256-GCM (`AUTH_BIOMETRIC_STRONG`).
   * Authentifizierung über `BiometricPrompt.CryptoObject(cipher)`.
   * Optimiert für den 3D-ToF-Gesichtsscan und Ultraschall-Fingerabdrucksensor des Honor Magic 8 Pro mit `setConfirmationRequired(false)` für verzögerungsfreies Entsperren ohne extra Displayberührung.

4. **Integrierter Selbstschutz:**
   * Systemeinstellungen (`com.android.settings`) und Paket-Installer (`com.hihonor.android.packageinstaller`, etc.) sind standardmäßig geschützt.
   * Ein Angreifer kann den Dienst weder über „Stoppen erzwingen“ beenden noch deinstallieren, ohne sich biometrisch zu authentifizieren.
   * `BootReceiver` startet den Schutz unmittelbar nach dem Hochfahren des Smartphones.

---

## 📱 Wichtige Einrichtung auf dem Honor Magic 8 Pro (MagicOS)

Damit MagicOS den Dienst im Hintergrund nicht beendet, müssen folgende 3 Schritte durchgeführt werden:

1. **Eingeschränkte Einstellungen aufheben (Android 14 Sideload-Schutz):**
   * `Einstellungen` ➔ `Apps` ➔ `ShieldLock Pro` ➔ oben rechts auf die **3 Punkte** tippen ➔ **„Eingeschränkte Einstellungen zulassen“**.
2. **MagicOS App-Start (Auto-Launch):**
   * In der App auf **„MagicOS App-Start öffnen“** tippen (oder `Einstellungen` ➔ `Akku` ➔ `App-Start`).
   * Bei *ShieldLock Pro* die Option **„Automatisch verwalten“ ausschalten**.
   * Im erscheinenden Dialog alle 3 Optionen aktivieren:
     * *Auto-Start*
     * *Sekundärer Start*
     * *Im Hintergrund ausführen*
3. **Schloss in den Recent Apps:**
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
