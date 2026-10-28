# AppLock — Modernisierte & Optimierte Android-Sicherheits-App

Eine von Grund auf bereinigte, leistungsstarke und mit **Material Design 3 (Jetpack Compose)** modernisierte Weiterentwicklung des Open-Source-Projekts **AppLock** (`PranavPurwar/AppLock` / `Sami112xkhan/AppLock`), die installierte Android-Apps zuverlässig vor unbefugtem Zugriff schützt, **Endlosschleifen bei der Fingerabdruck-/Biometrie-Abfrage vollständig eliminiert** und den Funktionsumfang sinnvoll erweitert.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Bestätigte Schwerpunkte & Kritischer Schutz vor Biometrie-Endlosschleifen:**
> - **Bestätigte Entscheidung 1 (Kein Fingerabdruck-Loop / Anti-Deadlock-Garantie)**: Explizite Absicherung gegen den bekannten Bug, bei dem der Fingerabdruck-Dialog (`BiometricPrompt`) in einer Endlosschleife immer wieder aufpoppt und jede Bedienung blockiert:
>   1. **One-Shot Biometric Trigger (`hasAutoTriggeredBiometric`)**: Der Fingerabdruck-Dialog darf pro Sperr-Sitzung **maximal ein einziges Mal** automatisch beim Öffnen angefragt werden – niemals ungefiltert in `onResume()` oder bei Recomposition, da das Schließen des System-Biometrie-Dialogs selbst wieder `onResume()` auslöst.
>   2. **Sofortiger manueller PIN-Fallback & „Biometrie pausieren“**: Sobald der Nutzer den Fingerabdruck-Dialog einmal abbricht (`ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON`, `ERROR_CANCELED`), wechselt der Sperrbildschirm sofort dauerhaft in den ruhigen PIN-Modus. Ein erneuter Fingerabdruck-Scan erfolgt nur noch, wenn der Nutzer aktiv auf den **„Mit Biometrie entsperren“-Button** tippt.
>   3. **SystemUI- & eigene App-Whitelist im Watcher**: Fenster-Events von `com.android.systemui`, `android`, `com.google.android.permissioncontroller`, Biometrie-Systemdialogen sowie dem eigenen AppLock-Paket werden strikt ignoriert, damit der Fingerabdruck-Dialog selbst niemals als „App-Wechsel“ fehlinterpretiert wird.
>   4. **Session-Unlock-Token & Cooldown-Sperre**: Nach erfolgreichem Entsperren erhält das Ziel-Paket ein aktives Sitzungs-Token (`unlockedSessionPackages`), sodass beim Schließen des Sperrbildschirms kein erneuter Lock-Trigger ausgelöst wird.
> - **Bestätigte Entscheidung 2 (Code-Vereinfachung & Architektur)**: Ablösung fragmentierter Service-/SharedPreferences-Logik durch eine einheitliche, reaktive **MVVM + Repository + Room Database**-Architektur mit klaren Zustandsautomaten (`Sealed UI States`).
> - **Bestätigte Entscheidung 3 (Performance & Bugfixes)**: Behebung des *Overlay-Timing-Bugs* durch einen O(1) In-Memory `ConcurrentHashMap`-Lock-Cache und wegfallende Main-Thread-Blockaden beim Laden von App-Icons.
> - **Bestätigte Entscheidung 4 (Funktions-Erweiterungen & Material 3 UI)**: Einführung von **Sicherheitsprofilen**, **Zufalls-Tastatur (Scramble PIN Pad)** gegen „Shoulder Surfing“, konfigurierbaren **Re-Lock-Timeouts**, **Sicherheits-Audit-Protokoll** sowie einem sicheren **Lock-Screen-Live-Simulator**.

---

### 1. Overview & Core Concept

- **Was die App leistet**: AppLock schützt ausgewählte Apps auf dem Gerät mit einer mehrschichtigen Authentifizierungsschranke (PIN-Code und optionaler Hardware-Biometrie via `BiometricPrompt`). Sobald eine geschützte App in den Vordergrund wechselt, legt sich verzögerungsfrei ein geschützter Sperrbildschirm (`FLAG_SECURE`) darüber – ohne jemals in eine Endlosschleife zu geraten.
- **Zielgruppe / Nutzungskontext**: Datenschutzbewusste Android-Nutzer, die bestimmte System- oder Drittanbieter-Apps (Galerie, Banking, Messenger, Einstellungen) lokal und 100 % offline absichern möchten – ohne Telemetrie, Cloud-Zwang oder Akku-Drain.
- **Zentraler Mehrwert durch die Überarbeitung**:
  1. **Garantierte Schleifenfreiheit (Zero-Loop State Machine)**: Strenge Trennung zwischen *Fenster-Erkennung*, *aktivem Sperr-Dialog* und *Biometrie-Lebenszyklus* verhindert das endlose Aufpoppen des Fingerabdruck-Prompts.
  2. **Drastisch vereinfachte Codebasis**: Eine zentrale `SecurityEngine` verwaltet die gesamte Sperr-, Timeout-, Sitzungs- und Authentifizierungslogik.
  3. **Spürbar höhere Performance**: App-Listen mit Paket-Icons werden asynchron geladen und gecacht; Vordergrund-Prüfungen laufen in unter 1 Millisekunde über ein im RAM gehaltenes `Set<String>`.
  4. **Sinnvolle Funktions-Upgrades**: Schnellwechsel-Profile (1-Tap-Aktivierung ganzer App-Gruppen), Anti-Spy-Zufallstastenfeld, Fehlversuchs-Historie und ein interaktiver Berechtigungs-Gesundheitscheck.

---

### 2. User Experience & Visual Design

- **Wichtige Nutzerflüsse (Key User Flows)**:
  1. **Onboarding & Initial-PIN-Setup**: Beim ersten Start legt der Nutzer einen 4- oder 6-stelligen Master-PIN fest (mit kryptografischem SHA-256 + Salt Hashing); Biometrie ist optional zuschaltbar und lässt sich jederzeit mit 1 Tap deaktivieren.
  2. **Dashboard & App-Tresor (Home)**: Übersichtliche Status-Hero-Card (Schutz aktiv/pausiert, Notfall-Pause-Schalter, Anzahl gesperrter Apps, aktives Profil, Berechtigungs-Indikator) gefolgt von einer blitzschnellen Such- und Filterleiste (*Alle Apps*, *Gesperrt*, *Entsperrt*, *System-Apps*) zum 1-Tap-Sperren und Entsperren.
  3. **Sicherheitsprofile & Schnellgruppen**: Erstellen und Aktivieren von Vorlagen (z. B. *„Fokus & Arbeit“*, *„Kindersicherung“*, *„Öffentliches WLAN / Unterwegs“*), die mit einem Fingertipp mehrere Apps gleichzeitig sperren oder freigeben.
  4. **Sicherheits-Log & Tresor-Statistik**: Chronologische Übersicht über erfolgreiche Entsperrungen und abgewehrte Fehlversuche (inkl. betroffener App, Zeitpunkt und Authentifizierungsmethode).
  5. **Schleifenfreies Authentifizierungs-Gate & Live-Vorschau**: Vollbild-Sperrbildschirm mit ruhigem PIN-Ziffernblock, optionalem *Scramble Keypad*, explizitem Biometrie-Button (ohne Zwangs-Loop bei Abbruch) und einem „Abbrechen / Zurück zum Startbildschirm“-Notausgang.

- **Visuelle Identität & Theme**:
  - *Ästhetische Richtung*: **„Tactical Obsidian & Emerald Shield“** – ein präzises, vertrauenerweckendes Cyber-Security-Design mit klarer visueller Hierarchie, hohem Kontrast und feinen Tonal-Abstufungen.
  - *Farbpalette & Stimmung (Material 3 Tokens)*:
    - `primary`: Smaragd-Sicherheitstürkis (`#10B981` im Dark Mode / `#047857` im Light Mode) für aktiven Schutzstatus und primäre Aktionen.
    - `secondary`: Kühles Schieferblau (`#38BDF8` / `#0284C7`) für Profile, Filter-Pills und Diagnose-Badges.
    - `tertiary` & `error`: Bernstein-Warnfarbe (`#F59E0B`) für ausstehende Berechtigungen und Korallenrot (`#EF4444`) für abgewehrte Zugriffsversuche.
    - `background` & `surface`: Tiefes Obsidian-Schiefer (`#0B0F17` und abgestufte `surfaceContainer`-Ebenen `#151C28`) im Dark Theme sowie klares Alabaster-Weiß (`#F8FAFC`) im Light Theme.
  - *Typografie & Hierarchie*:
    - **Display / Überschriften & PIN-Ziffern**: *Space Grotesk* (lokal installiert) für markante Zahlen auf dem PIN-Pad, Statistiken und Header.
    - **Fließtext & App-Listen**: *Plus Jakarta Sans* (lokal installiert) für optimale Lesbarkeit bei Paketnamen, Berechtigungshinweisen und Einstellungen.
  - *Komponenten-Styling & Layout*:
    - Asymmetrische **Security Shield Hero-Card** mit radialem Farbverlauf und Echtzeit-Schutzindikator.
    - Adaptive Layouts (kompakte Bottom-Navigation auf Smartphones, `NavigationRail` und zweispaltiges Layout auf Tablets/Foldables).
    - Mindestens `48.dp` Touch-Targets auf allen Switches, Filter-Chips und PIN-Tasten.

- **Interaktives Feedback & Motion**:
  - **Spring-Animationen (`spring()`)** beim Sperren/Entsperren einer App.
  - **Shake-Animation & Haptik-Impuls** bei falscher PIN-Eingabe sowie animierte Punkte für den PIN-Fortschritt.
  - **Sanfte Staggered-Transitions** beim Wechsel zwischen App-Kategorien und Sicherheitsprofilen.

---

### 3. Key Product Decisions & Trade-Offs

- **Entscheidung 1: Mehrschichtiger Schutz gegen Biometrie- & Overlay-Endlosschleifen**
  - *Gewählter Ansatz*: Eine strikte 4-Stufen-Sicherung in der `SecurityEngine` und dem `LockScreen`:
    1. **Kein `onResume`-Biometrie-Trigger**: Da das Öffnen und Schließen des Android-System-Fingerabdruckdialogs `onPause()` -> `onResume()` der unterliegenden Activity aufruft, löst ein `onResume()`-Aufruf in fehlerhaften AppLock-Repos eine unendliche Biometrie-Schleife aus. Wir kapseln den Start stattdessen in ein zustandsgesteuertes `BiometricSessionState` (`IDLE` -> `PROMPT_SHOWN` -> `CANCELED_OR_FAILED_FALLBACK_TO_PIN` -> `AUTHENTICATED`), das pro Sitzung maximal einmal automatisch startet.
    2. **Biometrie-Cooldown & Sofort-Abbruch**: Nach einem Abbruch oder 3 fehlgeschlagenen Biometrie-Scans bleibt der Fingerabdruck-Prompt geschlossen, bis der Nutzer entweder die PIN eingibt oder aktiv auf das Fingerabdruck-Icon tippt. Zudem gibt es eine Einstellung *„Fingerabdruck nur auf Knopfdruck (Kein Auto-Popup)“*.
    3. **System-Paket-Ignore-Liste & Selbstschutz-Sperre**: Pakete wie `com.android.systemui`, `android`, Launcher-Apps sowie das eigene AppLock-Paket werden auf Ebene des `AccessibilityService` herausgefiltert. Außerdem wird geprüft, ob das Lock-Gate (`isGateCurrentlyShowing`) bereits offen ist – solange es sichtbar ist, werden keine neuen Lock-Activities gestartet (`FLAG_ACTIVITY_SINGLE_TOP`).
    4. **Notausgang („Zum Startbildschirm verlassen“)**: Auf jedem Sperrbildschirm gibt es einen klaren Button, der die gesperrte App sauber schließt (`Intent.ACTION_MAIN` + `CATEGORY_HOME`), sodass man niemals auf einem Sperrbildschirm gefangen sein kann.
  - *Warum*: Behebt die häufigste Ursache für eingefrorene Geräte und unbedienbare AppLock-Installationen dauerhaft.

- **Entscheidung 2: Lokale Persistenz mit Room + Gehashten Zugangsdaten (100 % Offline)**
  - *Gewählter Ansatz*: Lokale SQLite-Speicherung über **Room Database (KSP)** für gesperrte Pakete, Sicherheitsprofile und das Zugriffs-Protokoll, kombiniert mit `SHA-256` Salted Hashing für den PIN-Code.
  - *Warum*: Funktioniert 100 % offline, verzögerungsfrei und schützt sensible Sperrregeln lokal auf dem Gerät.

- **Entscheidung 3: Intelligente Re-Lock-Richtlinien & Anti-Shoulder-Surfing**
  - *Gewählter Ansatz*: Unterstützung von 4 Re-Lock-Modi (*Nach Verlassen + Gnadenfrist von 3 Sek.*, *Nach 1 Minute*, *Nach 5 Minuten*, *Erst nach Bildschirm-Aus*) sowie optionaler **Zufalls-Tastatur (Scramble PIN)**.
  - *Warum*: Selbst im Modus „Sofort“ verhindert eine minimale 3-Sekunden-Übergangs-Gnadenfrist nach dem Entsperren, dass verzögerte Android-Window-Events dieselbe App im selben Augenblick noch einmal sperren.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architektur- & Komponenten-Diagramm (inkl. Anti-Loop State Guard)**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     UI LAYER (Jetpack Compose + M3)                        │
├──────────────────┬──────────────────┬──────────────────┬───────────────────┤
│  Dashboard &     │  Profiles &      │  Audit Log &     │  Security Settings│
│  App Vault Tab   │  Quick Groups    │  Analytics Tab   │  & Loop-Guards    │
└────────┬─────────┴────────┬─────────┴────────┬─────────┴─────────┬─────────┘
         │                  │                  │                   │
         ▼                  ▼                  ▼                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                AppLockViewModel & Anti-Loop SecurityEngine                 │
│   • BiometricSessionState (IDLE -> SHOWN_ONCE -> PIN_FALLBACK -> UNLOCKED) │
│   • SessionUnlockManager (Unlocked Tokens + Grace Period Timestamps)       │
│   • GateMutex (Prevents duplicate LockActivity launches / event storms)    │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    │                                    │
                    ▼                                    ▼
┌───────────────────────────────────────┐  ┌─────────────────────────────────┐
│       AppLockRepository (Room)        │  │   System & Security Services    │
│ • LockedAppDao (Flow<List<LockedApp>>)│  │ • PackageScanner (Async Icons)  │
│ • SecurityProfileDao (Preset Groups)  │  │ • CredentialHasher (SHA256+Salt)│
│ • AuditLogDao (Unlock / Intruder Log) │  │ • AppLockAccessibilityService   │
│ • In-Memory O(1) FastLockedSet Cache  │  │   (Ignores SystemUI & Self!)    │
└───────────────────────────────────────┘  └─────────────────────────────────┘
```

- **Datenmodell & State**:
  - `LockedAppEntity`: Speichert `packageName` (Primary Key), `appName`, `category`, `isLocked`, `customTimeoutMinutes` und `lockedAtTimestamp`.
  - `SecurityProfileEntity`: Speichert `id`, `name`, `iconName`, `description`, `packageNamesCsv` und `isActive`.
  - `AuditLogEntity`: Speichert `id`, `packageName`, `appName`, `eventType` (`UNLOCK_SUCCESS`, `FAILED_ATTEMPT`, `LOCKOUT_TRIGGERED`, `PROFILE_CHANGED`), `authMethod` (`PIN`, `BIOMETRIC`) und `timestamp`.
  - `SecurityPreferences`: Verwaltet `pinHash`, `pinSalt`, `pinLength` (4 oder 6), `biometricEnabled`, `autoTriggerBiometricOnce` (Standard: `false` oder strikt 1x pro Sitzung schaltbar), `scrambleKeypad`, `hidePinRipple`, `relockPolicy` und `masterProtectionEnabled`.

- **Interaktives Komponenten- & State-Mapping**:
  - **Anti-Loop Event Filter (`AppLockAccessibilityService`)**:
    1. Empfängt `TYPE_WINDOW_STATE_CHANGED` -> Prüft: Ist `packageName` in `SYSTEM_IGNORE_PACKAGES` (`com.android.systemui`, `android`, aktueller Launcher, AppLock selbst)? Wenn ja -> **Sofort verwerfen**.
    2. Prüft: Ist `GateMutex.isLockScreenVisible == true`? Wenn ja -> **Sofort verwerfen** (kein zweites Overlay starten).
    3. Prüft: Hat `SessionUnlockManager.isCurrentlyUnlocked(packageName)` ein gültiges Token oder läuft die 3-Sekunden-Post-Unlock-Gnadenfrist? Wenn ja -> **Durchlassen**.
    4. Nur wenn das Paket in `FastLockedSet` ist und kein Token besitzt -> Startet das Lock-Gate genau 1x.
  - **Biometrie-State-Machine (`LockGateScreen`)**:
    - Besitzt einen expliziten Zustand `biometricState: BiometricUiState` (`ReadyForManualTap`, `PromptActive`, `FallbackToPinOnly`, `Succeeded`).
    - Wenn der Fingerabdruck abgebrochen wird oder fehlschlägt, wechselt der Status sofort auf `FallbackToPinOnly` – der Dialog schließt sich sauber und öffnet sich **nicht** von selbst erneut.
