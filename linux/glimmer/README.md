# Glimmer für Linux

**Glimmer** von ZENITH, als echtes Programm für deinen Desktop (kein Android): dieselbe Insel mit denselben Modi, Aussehen, Musik-Stilen,
Leuchten, Clawd und Einstellungen wie die Glimmer-App – am oberen Bildschirmrand, über allen Fenstern. Sie ist **immer da** (leer, wenn
nichts passiert) und wächst bei Ereignissen daraus heraus.

## Die vier Modi – und die Dynamic Notch

| Modus | Aussehen |
| --- | --- |
| **MacBook-Notch** (Standard) | die schwarze Kerbe, 190 × 34 dp, am Rand angewachsen, mit den „Ohren" an den Schultern; Inhalte wachsen links und rechts daneben heraus. Leuchten, Rand und Fließfarben sind aus (die gehören zur Pille) |
| **MacBook-Insel** | wie die neueren MacBooks: eine kleine Pille (128 × 32 dp), 6 dp unter dem Rand |
| **Glimmer (Pille)** | die Pille wie auf dem Handy, mit allem: Aussehen *Schwarz*, *Liquid Glass*, *Farbig getönt*, *AI Fluid*; Leuchten, Rand, fließende Farben, Atmen |
| **Dynamic Island 1:1** | wie die Insel des iPhones: reines Schwarz, ihre Proportionen, Cover als abgerundetes Quadrat, Antippen öffnet die App, ohne Glimmers Extras. Deine eigenen Einstellungen bleiben gespeichert |

**Dynamic Notch:** Bei *MacBook-Notch* und *MacBook-Insel* gibt es den Schalter **„Dynamic Notch (1:1)"** (bei der Insel „Dynamic-Island-Stil (1:1)").
Er legt Aussehen und Bedienung der Dynamic Island des iPhones auf die Form der Notch bzw. der Insel: reines Schwarz, das Cover als abgerundetes
Quadrat, aufgeklappt eine große, runde Karte, Antippen öffnet die App, gedrückt halten (rechte Maustaste) klappt auf. Größe und Lage bleiben deine
(Breite und Höhe der Kerbe, nach oben/unten …); Leuchten, Rand, Nachrichten, Doppeltippen, Titel wischen und „Akku beim Laden" sind dann aus.
Deine eigenen Einstellungen bleiben gespeichert und kommen zurück, wenn du den Schalter ausschaltest.

## Was Glimmer zeigt

| Ereignis | Klein | Aufgeklappt |
| --- | --- | --- |
| **Musik** (jeder MPRIS-Player: Spotify, Firefox, VLC, mpv, Elisa …) | Cover, farbige Wellen; Stil *Hyper Island* (Xiaomi), *Klassisch* oder *Fluid Cloud* (OPPO/OnePlus) | Cover, Titel, Interpret, Fortschritt zum Ziehen, ⏮ ⏯ ⏭ |
| **Bestätigungscodes** | der Code in der Insel (Bank, Login, TAN …), ein Tipp kopiert ihn | großer Code, Knopf „Kopieren" |
| **Bildschirmfotos** | das Bild als Vorschau | Bild, „Kopieren", „Ansehen" |
| **Benachrichtigungen** | App-Symbol, pulsierender Punkt | App-Symbol, Titel, Text |
| **Laden** · **Kabel** · **Akku schwach** · **Voll** | Funke und Prozent, ein Lichtstreifen läuft um die Insel | Prozent, Akku, der sich füllt |
| **Lautstärke** | Lautsprecher, Prozent | Titel, Wert, Balken |
| **Kopfhörer** (Bluetooth) · **Nicht stören** | Symbol, Wert | wie oben |
| **Kamera / Mikrofon in Benutzung** | grüner / oranger Punkt | – |
| **Clawd** | Claudes kleines Pixelwesen sitzt in der Insel, tanzt zur Musik, schläft nachts, sagt beim Start Hallo | |

Bedienung wie auf dem Handy: **Tippen** klappt auf und zu (oder öffnet die App, wenn du das einstellst), **nach unten ziehen** klappt auf,
**nach oben ziehen** klappt zu, **seitlich ziehen** springt bei Musik zum nächsten oder vorigen Titel, **Doppeltippen** (einstellbar):
Play/Pause, Clawd-Orakel, Münze oder Würfel. Aufgeklappt hält der Fortschrittsbalken zum Ziehen zum Spulen. **Rechtsklick** ist das
Gedrückthalten vom Handy. Ein Tipp auf die leere Insel öffnet die App (das Einstellungsfenster). Außerhalb der Insel fängt Glimmer keine
Mausklicks ab.

## Die App: das Einstellungsfenster

Im Anwendungsmenü gibt es **„Glimmer"** (oder im Terminal `glimmer`, wenn es schon läuft). Das Fenster ist die Glimmer-App von ZENITH für den
Desktop: der dunkle Verlauf mit den driftenden Farben, die atmende Logo-Insel, eine **Vorschau zum Antippen** (sie geht durch Leerlauf, Musik,
Laden, Benachrichtigung, Code, Lautstärke, Kopfhörer, Bildschirmfoto und Akku schwach – und folgt jeder Änderung sofort) und die Einstellungen
in Glas-Karten, wie in der Android-App:

* **Einrichten** – was Glimmer auf deinem Desktop gefunden hat (Layer-Shell, Player, Benachrichtigungen, Lautstärke, Akku, Bildschirmfotos, Kamera/Mikrofon)
* **Clawd** – an/aus, auch im Leerlauf, Seite, Position, Größe, Aussehen (10 Farben bis *Regenbogen* und *Galaxy*), „Hallo" beim Start, nur einmal am Tag
* **Glimmer** – anzeigen, Modus, Aussehen, Musik-Stil, kleine Pille im Leerlauf, Uhrzeit in der leeren Insel
* **Gestaltung** – Leuchten bei Neuem und seine Farbe, AI-Fluid-Rand, atmendes Leuchten, Rand, Bewegung (ruhig · normal · verspielt)
* **Größe & Position** – „Kleiner (85 %)", Gesamtgröße, Breite und Höhe der Kerbe bzw. der Pille, Breite, nach links/rechts, nach oben/unten, Zurücksetzen
* **Bedienung** – Antippen öffnet die App, Doppeltippen, von selbst zuklappen (5 · 9 · 15 · 30 s · nie), Titel wischen, Maus auf der Insel klappt die Musik auf
* **Was Glimmer zeigt** – Musik, Cover-Insel, Codes, Bildschirmfotos, Nachrichten, Kamera/Mikrofon, Kopfhörer/Nicht stören/Akku voll, Laden, Akku schwach, Akku beim Laden, Lautstärke
* **Linux** – Start bei der Anmeldung, Sprache, Ebene (auch über Vollbild), Bildschirm
* **Ausprobieren** – Vorführung, Test-Benachrichtigung · **Zurücksetzen** (zweimal tippen)

Optionen, die zum gewählten Modus nicht gehören, blendet das Fenster aus – wie die Android-App. Das Fenster zu schließen beendet Glimmer nicht,
die Insel läuft weiter. Änderungen gelten sofort (Gesamtgröße, Ebene und Bildschirm nach einem kurzen Neustart).

## Voraussetzungen

* ein **Wayland-Desktop mit Layer-Shell**: KDE Plasma (Wayland), Hyprland, Sway, labwc … – **nicht GNOME** (dort läuft Glimmer nur als normales Fenster)
* Pakete: `python python-gobject python-cairo gtk4 gtk4-layer-shell` (der Installer holt sie, wenn sie fehlen)
* optional: `libpulse` (`pactl`, auch mit PipeWire) oder `wireplumber` (`wpctl`) für Lautstärke und Mikrofon-Punkt · `wl-clipboard` zum Kopieren von Codes und Bildern · BlueZ für Kopfhörer

## Einrichten

```bash
curl -L -o Glimmer-Linux.tar.gz https://github.com/JP19Claude/launcher/releases/latest/download/Glimmer-Linux.tar.gz
tar xzf Glimmer-Linux.tar.gz
cd glimmer-linux
./install.sh
```

(Das geht genauso in fish – die Befehle sind in jeder Shell gleich. Das Programm startet sofort und bei jeder Anmeldung wieder.)

* `./install.sh pkg` – baut stattdessen ein richtiges Arch-Paket (`glimmer-desktop`) und installiert es mit `makepkg -si`
* `./install.sh demo` – führt alle Ereignisse einmal vor, ohne etwas einzurichten
* `./install.sh stop` · `./install.sh uninstall` – beenden · entfernen

Beim Ausprobieren: im Einstellungsfenster **Vorführung starten** (oder `glimmer --demo` im Terminal; **Strg+C** beendet sie) zeigt der Reihe nach
Musik (klein und aufgeklappt), Lautstärke, Laden, eine Benachrichtigung, Akku schwach, einen Code, Kopfhörer und ein Bildschirmfoto.
Beenden geht auch von Hand: `./install.sh stop`.

## Einstellungen in der Datei

Alles aus dem Einstellungsfenster steht in `~/.config/glimmer/config.json` (wird beim ersten Start angelegt und vom Fenster gespeichert). Die
Schlüssel heißen wie in der Android-App:

| Gruppe | Schlüssel (Standard) |
| --- | --- |
| Glimmer | `enabled` (`true`) · `mode` (`"notch"`, `"island"`, `"glimmer"`, `"dynamic_island"`) · `dynamic` (`false`; mit `"notch"`/`"island"`: die Dynamic Notch bzw. der Dynamic-Island-Stil) · `style` (`"black"`, `"glass"`, `"tinted"`, `"fluid"`) · `music_style` (`"hyper"`, `"classic"`, `"fluid_cloud"`) · `idle_pill` (`true`) · `idle_clock` (`false`) |
| Clawd | `clawd` (`false`) · `clawd_idle` (`true`) · `clawd_side` (`"left"`, `"right"`) · `clawd_spot` (0 – 1) · `clawd_size` (0.7 – 1.4) · `clawd_skin` (`"terracotta"`, `"ocean"`, `"mint"`, `"lavender"`, `"gold"`, `"rose"`, `"snow"`, `"midnight"`, `"rainbow"`, `"galaxy"`) · `clawd_greeting` (`true`) · `clawd_greet_daily` (`false`) |
| Gestaltung | `glow` (`true`) · `glow_color` (`"activity"`, `"accent"`, `"white"`, `"rainbow"`) · `ai_fluid` (`true`) · `breathe` (`false`) · `outline` (`"off"`, `"subtle"`, `"colored"`) · `motion` (`"calm"`, `"normal"`, `"playful"`) |
| Größe & Position | `small` (`false`, 85 %) · `scale` (1.0; 0.6 – 2.5) · `width` (1.0; 0.8 – 1.3) · `notch_width`, `notch_height` (190, 34) · `island_width`, `island_height` (128, 32) · `offset_x` (0) · `offset_y` (0) |
| Bedienung | `tap_opens` (`false`) · `double_tap` (`"off"`, `"play_pause"`, `"oracle"`, `"coin"`, `"dice"`) · `auto_collapse` (9 s; 0: bleibt) · `swipe_tracks` (`true`) · `hover_reveal` (`false`) |
| Was Glimmer zeigt | `music` · `music_persistent` · `notifications` · `codes` · `screenshots` · `privacy` · `alerts` · `charging` · `low_battery` · `volume` (alle `true`) · `charging_live` (`false`) |
| Linux | `monitor` (0) · `layer` (`"overlay"` über Vollbild, `"top"` darunter) · `language` (`"auto"`, `"de"`, `"en"`) · `accent` (`"#FF7A9C"`, Farbe des Benachrichtigungspunkts) |

Auch ohne Einrichtung geht: `glimmer --mode glimmer --scale 1.3`. Eine ältere Datei mit `idle` oder `greet` wird beim Lesen übernommen bzw. ignoriert.

## Wenn etwas nicht geht

* **Glimmer erscheint als normales Fenster in der Ecke** – dein Desktop kennt kein Layer-Shell (GNOME) oder `gtk4-layer-shell` fehlt. Unter KDE: Anmelden mit „Plasma (Wayland)".
* **„gtk4-layer-shell nicht gefunden"** – `sudo pacman -S gtk4-layer-shell` (extra), sonst `yay -S gtk4-layer-shell` (AUR).
* **Keine Lautstärke-Anzeige / kein Mikrofon-Punkt** – `pactl` oder `wpctl` fehlt (`sudo pacman -S libpulse`).
* **Keine Benachrichtigungen / Codes** – Glimmer liest sie über den Nachrichtenbus mit; bei einem Fehler steht der Grund im Terminal (`glimmer` dort starten, `GLIMMER_DEBUG=1 glimmer` zeigt mehr).
* **Codes oder Bilder lassen sich nicht kopieren** – `sudo pacman -S wl-clipboard`.
* **Bildschirmfotos erscheinen nicht** – Glimmer schaut in `Bilder/Screenshots` (und `Bilder`); lege deine Bildschirmfotos dort ab (Spectacle: Einstellungen → Speicherort).
* **Ein Bild aller Zustände** (zum Ansehen, ohne Fenster; alle Modi nebeneinander, auch Dynamic Notch und Dynamic-Island-Stil): `glimmer --snapshot zustaende.png`

## Was nicht dabei ist

Was es nur auf dem Handy gibt: der Einflug beim Entsperren, Sperrbildschirm/Always-on-Display (auch dessen Ausschalter), Face ID, Taschenlampe, Anrufe, Timer und Navigation
als Live-Aktivitäten, Hüte und Kleider für Clawd, Vibration, die Querformat-Ausrichtung.

## Technik in drei Zeilen

Python 3 + GTK 4 + `gtk4-layer-shell`, gezeichnet mit Cairo: ein Nachbau von ZENITHs Glimmer (Größenrechnung `islandSize`, Federn, die weichen „kontinuierlichen" Ecken, Cover-Farben, Lichtstreifen beim Laden, Clawd als Pixelbild).
Musik: MPRIS über D-Bus · Laden: `/sys/class/power_supply` · Lautstärke: `pactl subscribe` · Benachrichtigungen: D-Bus-Monitor (nur lesend) · Kopfhörer: BlueZ · Kamera: `/proc` · Mikrofon: `pactl`.
Dieselbe Idee wie Glimmer in ZENITH, nur für den Desktop.
