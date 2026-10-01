# Hearth Launcher

Ein schlanker Android-Launcher in Kotlin und Jetpack Compose, mit iOS-Ideen und Liquid Glass.

## Funktionen

- Liquid Glass für Dock, Such-Pille und Suche:
  - Unschärfe und verstärkte Farben des Hintergrundbilds
  - Lichtbrechung an den Kanten mit leichter Farbaufspaltung (Android 13+)
  - Glanz oben, Lichtkante am Rand, Such-Pille schwillt beim Drücken an
- Homescreen mit Seiten zum Wischen und Punkt-Anzeige
- Große Uhr mit Begrüßung und Datum auf der ersten Seite
- Dock mit Telefon, Nachrichten, Browser und Kamera (deine Standard-Apps)
- Squircle-Icons mit Feder-Animation beim Antippen
- Suche: nach oben oder unten wischen oder auf "Suchen" tippen
  - Enter startet den ersten Treffer, sonst Websuche
- Lange drücken auf ein Icon: App-Info oder Deinstallieren
- Home-Taste schließt die Suche und springt zur ersten Seite
- Hell- und Dunkelmodus folgen dem System

## Liquid Glass und die Berechtigung

Damit das Glas dein Hintergrundbild zeigen kann, muss der Launcher es lesen dürfen.
Ab Android 11 geht das nur über "Zugriff auf alle Dateien". Auf dem Homescreen
erscheint dafür ein Hinweis, ein Tipp öffnet die passende Einstellung.

Ohne Berechtigung oder mit Live-Hintergrund ist das Glas nur milchig getönt.

Effekt je nach Android-Version:
- Android 13+: Unschärfe, Lichtbrechung, Farbaufspaltung
- Android 12: Unschärfe
- Android 9 bis 11: weichgezeichnetes Hintergrundbild

## APK bauen

### Variante A: GitHub Actions (ohne Android Studio)

1. Neues GitHub-Repository anlegen und den Projektordner hochladen
2. GitHub baut die APK automatisch (Tab "Actions")
3. Im fertigen Lauf unter "Artifacts" `hearth-debug-apk` herunterladen
4. ZIP entpacken und `app-debug.apk` auf dem Handy installieren

### Variante B: Android Studio

1. Ordner öffnen (File > Open), Gradle-Sync abwarten
2. Run, oder Build > Build APK(s)

Danach Home-Taste drücken und "Hearth" als Standard-Launcher wählen,
oder: Einstellungen > Apps > Standard-Apps > Startbildschirm-App.

## Struktur

- `data/AppRepository.kt`: lädt Apps über `LauncherApps`, rendert Icons, startet Apps
- `data/WallpaperRepository.kt`: liest das Hintergrundbild für das Glas
- `LauncherViewModel.kt`: App-Liste, Dock, Hintergrund, Home-Events
- `ui/LiquidGlass.kt`: Glas-Effekt (AGSL-Shader für die Lichtbrechung)
- `ui/LauncherScreen.kt`: Homescreen, Seiten, Uhr, Such-Pille
- `ui/SearchOverlay.kt`: Suche
- `ui/AppIcon.kt`: Icon mit Squircle-Form und Long-Press-Menü
- `ui/Dock.kt`: Dock
- `ui/theme/Theme.kt`: Farben (Kalkstein, Walnuss, Ocker)
