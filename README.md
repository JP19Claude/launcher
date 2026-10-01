# Hearth Launcher

Ein schlanker Android-Launcher in Kotlin und Jetpack Compose, mit iOS-Ideen und Liquid Glass.

## Funktionen

- Look „ColorOS × Claude“ (Standard): ColorOS-Uhr, abgerundete Icons, Claude-Farben
  (Terrakotta, Elfenbein, Schiefer), Serifen-Akzente, animierter Funke und
  eine „Frag Claude“-Glaskarte; weitere Vorlagen: iOS Liquid Glass, Hearth Klassik
- Eigenes Kontrollzentrum aus Liquid Glass (nach unten wischen; links: Mitteilungen):
  - Helligkeit und Lautstärke als große Glas-Regler
  - Taschenlampe, Nicht stören, Vibration, Drehen, Auto-Helligkeit, Standort
  - WLAN, Mobile Daten, Bluetooth, Flugmodus (öffnen die System-Panels)
  - Medien-Tasten, Kamera, Wecker, Rechner, Claude
  - Helligkeit/Drehung brauchen „Systemeinstellungen ändern“, Nicht stören den
    Zugriff auf „Bitte nicht stören“
- Kontrollzentrum in allen Apps (Bedienungshilfe „Hearth Kontrollzentrum“ einschalten):
  ein unsichtbarer Streifen über dem rechten Teil der Statusleiste fängt das Wischen
  nach unten ab und öffnet das Glas-Kontrollzentrum über der App (ab Android 12 mit
  echter Unschärfe dahinter). Links öffnet weiter die normale Mitteilungsleiste.
  Bereich einstellbar: rechte Hälfte, rechtes Drittel, ganze Breite. Ganz entfernen
  lässt sich das System-Kontrollzentrum nur mit Root.
- Suche: „Claude fragen“ übergibt die Frage an die Claude-App oder claude.ai
- App-Mediathek wie bei iOS (hinter der letzten Seite): Glas-Ordner nach Kategorie,
  „Vorschläge“ aus deinen meistgenutzten Apps, „Neu hinzugefügt“; die kleinen Icons
  in der Ecke öffnen den ganzen Ordner
- Kontrollzentrum zeigt, was gerade läuft: Cover, Titel, Interpret, Fortschritt,
  Glas in der Farbe des Covers (braucht einmal „Benachrichtigungszugriff“)
- „System-Kontrollzentrum ersetzen“: geht das Panel von One UI/ColorOS doch auf,
  schließt Hearth es und zeigt seins (braucht den Bedienungshilfe-Dienst)
- „Vom Startbildschirm entfernen“: App verschwindet vom Homescreen, bleibt aber
  in App-Mediathek und Suche (einzeln im Menü oder mehrere per „Auswählen“)
- Glimmer, Hearths „Dynamic Island“ um die Frontkamera, in jeder App: Musik mit Cover
  und tanzenden Balken, Anrufe und Timer mit laufender Zeit, Navigation, Downloads mit
  Ring, neue Nachrichten, Laden, Lautlos, niedriger Akku; zwei Aktivitäten gleichzeitig
  als Pille + Blase; Tippen klappt auf (mit den Knöpfen der App), lange drücken öffnet
  die App; Schwarz oder Liquid Glass (braucht Bedienungshilfe + Benachrichtigungszugriff)
- Foto-Widget: eigene Bilder als Diashow mit langsamem Zoom auf Glas, für Homescreen
  und Widget-Seite
- Apps frei anordnen: Icon lange drücken und ziehen (Menü verschwindet beim Ziehen),
  Glas-Feld zeigt den Zielplatz, belegte Plätze tauschen, am Rand blättert die Seite,
  ins Dock ziehen und aus dem Dock heraus; „Anordnung zurücksetzen“ in den Einstellungen
- Schneller nach dem Zurückkehren: Icons werden zwischengespeichert und brauchen weniger
  Speicher, damit Android den Launcher seltener schließt und er sofort wieder da ist
- Widgets direkt auf dem Startbildschirm: lange auf eine freie Stelle → „Widget hierher“;
  Widgets belegen Rasterfelder auf Glas, Apps fließen drumherum; Widget lange drücken
  für Größe/Seite/Entfernen, halten und ziehen zum Verschieben
- Animationen: Zoom beim Zurückkehren, Seitenwechsel „Tiefe“ oder „Würfel“, rollende
  Uhrziffern, gestaffelt erscheinende Karten und Ordner, gleitende Icons beim Umordnen,
  Seitenpunkte als Kapsel, Glas-Hinweise mit „Rückgängig“
- Mehr Glas: Glanz auf jedem Icon (folgt dem Kippen), Suchergebnisse auf einer Glasplatte
- Widgets: eigene Widget-Seite links neben dem Homescreen (wie die Heute-Ansicht),
  jedes Widget auf einer Glas-Karte; „Bearbeiten“ zum Verschieben, Vergrößern, Entfernen
- Mehrere Apps markieren: lange drücken → „Auswählen“ (Icons wackeln, Haken antippen),
  dann „Ausblenden“; oder in den Einstellungen „Apps auswählen …“ als Liste
- Schneller zurück in die nächste App: Rückkehr aus einer App scrollt nicht mehr zur
  ersten Seite (das hat den ersten Tipp verschluckt), Apps starten mit Zoom aus dem Icon
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
