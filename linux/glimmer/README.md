# Glimmer für Linux

Die **Notch** von ZENITH, als echtes Programm für deinen Desktop (kein Android): dieselbe schwarze Notch wie die MacBook-Notch in der
Glimmer-App – 190 × 34 dp groß, mit den Schultern am Bildschirmrand, mit den Bewegungen, Größen und Darstellungen aus ZENITH.
Sie ist **immer da** (leer, wenn nichts passiert) und wächst bei Ereignissen daraus heraus:

| Ereignis | Klein (neben der Notch) | Aufgeklappt (Tippen) |
| --- | --- | --- |
| **Musik** (jeder MPRIS-Player: Spotify, Firefox, VLC, mpv, Elisa …) | Cover links, farbige Wellen rechts, die Farben des Covers glühen durch das Schwarz | Cover mit Schein, Titel, Interpret, Fortschritt zum Ziehen, ⏮ ⏯ ⏭, Farben des Covers als Licht dahinter |
| **Laden** | grüner Funke links, Prozent rechts, ein grüner Lichtstreifen läuft einmal um die Notch | „Lädt", Prozent, Akku-Symbol, das sich füllt |
| **Lautstärke** | Lautsprecher links, Prozent rechts | Titel, Wert, Balken |
| **Benachrichtigungen** | App-Symbol links, pulsierender Punkt rechts | App-Symbol, Titel, Text |
| **Akku schwach · Voll · Kabel getrennt** | Symbol links, Wert rechts | wie Laden |

Bedienung wie auf dem Handy: **Tippen** klappt auf und zu, **nach unten ziehen** klappt auf, **nach oben ziehen** klappt zu,
**seitlich ziehen** springt bei Musik zum nächsten oder vorigen Titel, aufgeklappt hält der Fortschrittsbalken zum Ziehen zum Spulen, ⏮ ⏯ ⏭
steuern den Player, ein Tipp auf Cover und Titel holt das Player-Fenster nach vorn. **Rechtsklick** holt bei Musik den Player, sonst schließt er die
Meldung. Nach 9 Sekunden klappt sie von allein zu. Ein Tipp auf die leere Notch öffnet die App (das Einstellungsfenster).
Außerhalb der Notch fängt Glimmer keine Mausklicks ab.

## Die App: das Einstellungsfenster

Im Anwendungsmenü gibt es **„Glimmer"** (oder im Terminal `glimmer`, wenn es schon läuft). Das öffnet das Einstellungsfenster:
Form (Notch oder die kleine Insel darunter), Größe und Breite/Höhe der Notch, was im Leerlauf zu sehen ist, Sprache, jedes Ereignis einzeln ein- und ausschalten, Start bei der Anmeldung –
dazu die Knöpfe **Vorführung starten/beenden**, **Test-Benachrichtigung** und **Glimmer beenden**.
Das Fenster zu schließen beendet Glimmer nicht, die Insel läuft weiter. Änderungen gelten sofort (Form und Größe nach einem kurzen Neustart).

## Voraussetzungen

* ein **Wayland-Desktop mit Layer-Shell**: KDE Plasma (Wayland), Hyprland, Sway, labwc … – **nicht GNOME** (dort läuft Glimmer nur als normales Fenster)
* Pakete: `python python-gobject python-cairo gtk4 gtk4-layer-shell` (der Installer holt sie, wenn sie fehlen)
* optional für die Lautstärke: `libpulse` (`pactl`, auch mit PipeWire) oder `wireplumber` (`wpctl`)

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

Beim Ausprobieren: im Einstellungsfenster **Vorführung starten** (oder `glimmer --demo` im Terminal; **Strg+C** beendet sie) zeigt der Reihe nach Musik (klein und aufgeklappt), Lautstärke, Laden, eine Benachrichtigung (klein und aufgeklappt) und Akku schwach.
Beenden geht auch von Hand: `./install.sh stop`.

## Einstellungen in der Datei

Alles aus dem Einstellungsfenster steht in `~/.config/glimmer/config.json` (wird beim ersten Start angelegt und vom Fenster gespeichert):

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `mode` | `"notch"` | `"notch"`: die MacBook-Notch, am Rand angewachsen, mit den Schultern · `"island"`: die kleine Insel, 6 dp darunter |
| `scale` | `1.0` | Größe von allem (0.6 – 2.5) |
| `notch_width`, `notch_height` | `190`, `34` | Größe der leeren Notch in dp (wie in ZENITH) |
| `island_width`, `island_height` | `128`, `32` | dasselbe für die Insel |
| `idle` | `"pill"` | wenn nichts passiert: `"pill"` die leere Notch · `"clock"` mit Uhrzeit · `"off"` unsichtbar |
| `auto_collapse` | `9` | aufgeklappt nach so vielen Sekunden zuklappen (0: bleibt) |
| `monitor` | `0` | Nummer des Bildschirms |
| `layer` | `"overlay"` | `"overlay"`: auch über Vollbild-Fenstern · `"top"`: darunter |
| `language` | `"auto"` | `"de"`, `"en"` oder nach der Systemsprache |
| `music`, `music_persistent` | `true` | Musik zeigen · kleine Notch mit Cover und Wellen, solange Musik läuft |
| `hover_reveal` | `false` | Maus auf der Notch klappt die Musik auf (zusätzlich zum Tippen) |
| `greet` | `true` | beim Start kurz „Glimmer läuft" |
| `charging`, `low_battery` | `true` | Laden · Akku schwach |
| `volume` | `true` | Lautstärke (KDE zeigt dazu seine eigene Anzeige – wer nur eine will, setzt hier `false`) |
| `notifications` | `true` | Benachrichtigungen |
| `accent` | `"#FF7A9C"` | Farbe des Benachrichtigungspunkts |

Auch ohne Einrichtung geht: `glimmer --mode island --scale 1.3`.

## Wenn etwas nicht geht

* **Glimmer erscheint als normales Fenster in der Ecke** – dein Desktop kennt kein Layer-Shell (GNOME) oder `gtk4-layer-shell` fehlt. Unter KDE: Anmelden mit „Plasma (Wayland)".
* **„gtk4-layer-shell nicht gefunden"** – `sudo pacman -S gtk4-layer-shell` (extra), sonst `yay -S gtk4-layer-shell` (AUR).
* **Keine Lautstärke-Anzeige** – `pactl` oder `wpctl` fehlt (`sudo pacman -S libpulse`).
* **Keine Benachrichtigungen** – Glimmer liest sie über den Nachrichtenbus mit; bei einem Fehler steht der Grund im Terminal (`glimmer` dort starten, `GLIMMER_DEBUG=1 glimmer` zeigt mehr).
* **Ein Bild aller Zustände** (zum Ansehen, ohne Fenster): `glimmer --snapshot zustaende.png`

## Technik in drei Zeilen

Python 3 + GTK 4 + `gtk4-layer-shell`, gezeichnet mit Cairo: ein Nachbau der Notch aus ZENITHs Glimmer (Größen, Federn ohne Wippen am Rand, die weichen „kontinuierlichen" Ecken, Cover-Farben, Lichtstreifen beim Laden).
Musik: MPRIS über D-Bus · Laden: `/sys/class/power_supply` · Lautstärke: `pactl subscribe` · Benachrichtigungen: D-Bus-Monitor (nur lesend).
Dieselbe Idee wie Glimmer in ZENITH, nur für den Desktop.
