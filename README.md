# Handyzeitvertreib

Native Android-App zur Selbstregulierung der Handynutzung: Bildschirmzeit sehen,
Tageslimits für einzelne Apps und App-Gruppen setzen, ruhige Hinweise beim Erreichen
eines Limits und bewusste Verlängerung per Fingerabdruck bzw. Android-Biometrie.
Alle Nutzungsdaten bleiben lokal auf dem Gerät; die App hat keine Internet-Berechtigung.

Status: **MVP implementiert, auf der JVM getestet und visuell geprüft, noch nicht auf einem Gerät validiert.**
Details: [`docs/verification-report.md`](docs/verification-report.md).

## Funktionen

- Onboarding mit Erklärungen zu Datenschutz, Nutzungszugriff, Benachrichtigungen,
  optionalem Bedienungshilfen-Dienst, Konto, Start-Limits und Verlängerungsregeln
- „Heute“: Gesamtzeit, Vergleich mit gestern zur gleichen Uhrzeit, aktive Limits,
  meistgenutzte Apps, „zuletzt aktualisiert“
- Apps: Suche, Sortierung (Zeit, Name, Öffnungen), Filter; App-Details mit 7-Tage-Verlauf
- Limits: App-Limits und Gruppenlimits anlegen, bearbeiten, pausieren, löschen;
  das strengere Limit greift, keine Doppelzählung
- Hinweis-Bildschirm bei erreichtem Limit mit Verlängerung über `BiometricPrompt`
  (Dauer 5/10/15 Min., 1–3× pro Tag und Limit, optional PIN/Muster/Passwort)
- Einblicke: Tag/Woche, App-Aufschlüsselung; Tage ohne Daten werden nie als „0“ gezeigt
- Einstellungen: Berechtigungen und aktuelle Wirkungsstufe, Konto (nur lokal),
  Datenschutz und Aufbewahrung, Benachrichtigungen, Design (hell/dunkel/System,
  dynamische Farben, Bewegung reduzieren), Export und Löschen, Hilfe und Grenzen
- Sprachen: Deutsch (Standard) und Englisch

## Was Android erlaubt – und was nicht

Eine normale App kann andere Apps nicht garantiert sperren. Handyzeitvertreib nutzt nur
offizielle Wege; die Stärke hängt von den erteilten Berechtigungen ab:

| Stufe | Voraussetzung | Wirkung |
| --- | --- | --- |
| Keine | Nutzungszugriff fehlt | Nichts messbar; die App sagt das offen |
| Nur in der App | Nutzungszugriff | Erreichte Limits beim Öffnen der App |
| Benachrichtigung | + Benachrichtigungen | Hinweis meist innerhalb von ca. 15 Minuten |
| Hinweis-Bildschirm | + optionaler Bedienungshilfen-Dienst | Bildschirm erscheint beim Öffnen einer begrenzten App |

Mehr dazu: [`docs/android-capability-matrix.md`](docs/android-capability-matrix.md).

## Bauen

Voraussetzungen: JDK 17 oder neuer (getestet mit 21), Android SDK mit Plattform 37.
`local.properties` mit `sdk.dir=...` anlegen oder `ANDROID_HOME` setzen.

```bash
./gradlew :app:assembleDebug          # installierbare Debug-APK
./gradlew :app:testDebugUnitTest      # Unit-, Integrations- und UI-Tests (Robolectric)
./gradlew :app:lintDebug spotlessCheck
./gradlew :app:assembleRelease        # minifiziert; ohne Schlüssel unsigniert
./scripts/osv-scan.sh                 # Abhängigkeiten gegen die OSV-Schwachstellendatenbank prüfen
./gradlew :app:testDebugUnitTest --tests '*ScreenshotRenderTest' -Phzv.screenshots=/tmp/shots   # Screens als PNG rendern
```

Debug-APK: `app/build/outputs/apk/debug/app-debug.apk` – per USB mit
`adb install -r app/build/outputs/apk/debug/app-debug.apk` oder Datei aufs Handy
kopieren und „Installation aus unbekannten Quellen“ für den Dateimanager erlauben.

### Signierte Release-APK

Den Schlüssel erstellst und verwahrst du selbst; er gehört nie ins Repository.

```bash
keytool -genkeypair -v -keystore handyzeitvertreib.jks -keyalg RSA -keysize 4096 -validity 10000 -alias hzv
```

Dann im Projektstamm eine (git-ignorierte) Datei `keystore.properties` anlegen:

```properties
storeFile=handyzeitvertreib.jks
storePassword=...
keyAlias=hzv
keyPassword=...
```

`./gradlew :app:assembleRelease` erzeugt danach `app-release.apk`. Updates müssen immer
mit demselben Schlüssel signiert werden.

## Projektstruktur

Ein Modul (`:app`) mit klaren Paketgrenzen, siehe [`docs/architecture.md`](docs/architecture.md).

| Pfad | Inhalt |
| --- | --- |
| `app/src/main/java/.../core` | Modelle, Tagesgrenzen, Formatierung |
| `.../usage`, `.../limits`, `.../regulation` | Messung, Limit-Auswertung, Regulierung |
| `.../enforcement` | Worker, Benachrichtigung, Bedienungshilfen-Dienst |
| `.../data` | Room, DataStore, Export/Löschen |
| `.../ui` | Designsystem und Screens |
| `app/schemas` | Exportiertes Room-Schema |
| `docs/` | Audit, Architektur, Datenschutz, Capability-Matrix, Roadmap, Verifikation |

## Dokumentation

- [`docs/implementation-audit.md`](docs/implementation-audit.md) – Ausgangslage und Entscheidungen
- [`docs/privacy-and-data-model.md`](docs/privacy-and-data-model.md) – gespeicherte Daten, Löschung, Aufbewahrung
- [`docs/roadmap.md`](docs/roadmap.md) – bewusst zurückgestellte Punkte

## Offene Entscheidungen

- Konto-Anbieter (derzeit nur Schnittstelle, keine Anmeldung)
- Rechtlich geprüfte Datenschutzerklärung (derzeit Platzhalter)
