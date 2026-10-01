# Hearth Launcher

Ein schlanker Android-Launcher in Kotlin und Jetpack Compose, mit iOS-Ideen und Liquid Glass.

## Funktionen

- Liquid Glass im Stil von Apple, fast überall: Icons, Dock, Uhr, Seitenpunkte,
  Such-Pille, Suche, Kontextmenüs und Einstellungen
  - Linsenwölbung am Rand, leichte Vergrößerung in der Mitte, Farbsäume (Android 13+)
  - Glanzlichter auf der Kante, die beim Kippen des Handys mitwandern
  - Glas wölbt sich unter dem Finger und leuchtet an der Berührungsstelle
  - Unschärfe und verstärkte Farben des Hintergrundbilds
- Icon-Stile: Original, Glas (Symbol auf Glas), Klar (weiß auf Glas), Getönt
- Icon-Formen: Squircle, Kreis, abgerundetes Quadrat
- Icon-Packs im ADW/Nova-Format, mit Rahmen für Apps, die das Pack nicht abdeckt
- Einstellungen: lange auf eine freie Stelle drücken, oder Zahnrad in der Suche
  - Glas: Lichtbrechung, Unschärfe, Farbsäume, Glanz, Tönung, Glasfarbe, Voreinstellungen
  - Icons: Stil, Form, Farbe, Größe, Beschriftung, Icon-Pack
  - Homescreen: Spalten, Reihen, Uhr (Glas-Karte, groß, aus), Begrüßung, Akku,
    Abdunkeln, Such-Pille, Wischgeste
  - Dock: Anzahl Apps, zurücksetzen
  - Suchmaschine, Design der Suche, Vibration, ausgeblendete Apps
- Lange drücken auf ein Icon: Glas-Menü mit App-Info, Dock hinzufügen/entfernen/verschieben,
  Ausblenden, Deinstallieren
- Suche: nach oben oder unten wischen oder auf "Suchen" tippen
  - Enter startet den ersten Treffer, sonst Websuche
- Home-Taste schließt Suche, Menü und Einstellungen und springt zur ersten Seite

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
2. GitHub baut die APK bei jedem Push automatisch (Tab "Actions")
3. Jeder erfolgreiche Build erscheint als Release (`build-<Nummer>`) mit `Hearth.apk`
4. Neueste APK direkt laden: `https://github.com/<Besitzer>/<Repo>/releases/latest/download/Hearth.apk`

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
