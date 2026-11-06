# Glimmer für Linux

Die **Insel** (oder der **Notch**) am oberen Bildschirmrand – als echtes Programm für deinen Desktop, kein Android.
Sie zeigt, was gerade passiert:

| Ereignis | Was du siehst |
| --- | --- |
| **Musik** (jeder Player mit MPRIS: Spotify, Firefox, Chromium, VLC, mpv, Elisa …) | kleine Insel mit Cover und Equalizer, solange Musik läuft · Titelwechsel zeigt kurz Titel und Interpret · Maus drauf: große Insel mit Cover, Fortschritt, ⏮ ⏯ ⏭ |
| **Laden** | grüner Lichtstreifen um die Insel, Prozentzahl · „Voll geladen" · „Akku schwach" bei 20 % und 10 % |
| **Lautstärke** | Balken mit Prozent, auch „Stumm" |
| **Benachrichtigungen** | App, Titel und Text – Glimmer liest nur mit, die normalen Benachrichtigungen bleiben, wie sie sind |

Bedienung: **Klick** auf ⏮ ⏯ ⏭ steuert den Player, Klick auf den Fortschrittsbalken spult, Klick auf das Cover holt das Player-Fenster nach vorn,
**Rechtsklick** schließt die Insel. Ist gerade nichts los, ist sie unsichtbar und fängt keine Mausklicks ab.
Bleibt die Maus einen Moment ganz oben in der Mitte am Bildschirmrand, erscheint die Musiksteuerung (auch bei Pause).

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

Beim Ausprobieren: `glimmer --demo` zeigt der Reihe nach Musik, Titelwechsel, Lautstärke, Laden, eine Benachrichtigung, Akku schwach und die große Musik-Insel.

## Einstellungen

`~/.config/glimmer/config.json` (wird beim ersten Start angelegt; nach einer Änderung Glimmer neu starten: `./install.sh install` oder abmelden):

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `mode` | `"notch"` | `"notch"`: am Rand angewachsen, mit den geschwungenen „Ohren" · `"island"`: schwebende Insel mit Schatten |
| `scale` | `1.0` | Größe (0.6 – 2.5) |
| `monitor` | `0` | Nummer des Bildschirms |
| `layer` | `"overlay"` | `"overlay"`: auch über Vollbild-Fenstern · `"top"`: darunter |
| `language` | `"auto"` | `"de"`, `"en"` oder nach der Systemsprache |
| `music`, `music_persistent` | `true` | Musik zeigen · kleine Insel, solange Musik läuft |
| `track_popups` | `true` | beim Titelwechsel kurz Titel und Interpret |
| `hover_reveal` | `true` | Maus oben in der Mitte öffnet die Musiksteuerung |
| `charging`, `low_battery` | `true` | Laden · Akku schwach |
| `volume` | `true` | Lautstärke (KDE zeigt dazu seine eigene Anzeige – wer nur eine will, setzt hier `false`) |
| `notifications` | `true` | Benachrichtigungen |
| `accent` | `"#FF7A9C"` | Farbe, wenn ein Cover keine liefert |

Auch ohne Einrichtung geht: `glimmer --mode island --scale 1.3`.

## Wenn etwas nicht geht

* **Glimmer erscheint als normales Fenster in der Ecke** – dein Desktop kennt kein Layer-Shell (GNOME) oder `gtk4-layer-shell` fehlt. Unter KDE: Anmelden mit „Plasma (Wayland)".
* **„gtk4-layer-shell nicht gefunden"** – `sudo pacman -S gtk4-layer-shell` (extra), sonst `yay -S gtk4-layer-shell` (AUR).
* **Keine Lautstärke-Anzeige** – `pactl` oder `wpctl` fehlt (`sudo pacman -S libpulse`).
* **Keine Benachrichtigungen** – Glimmer liest sie über den Nachrichtenbus mit; bei einem Fehler steht der Grund im Terminal (`glimmer` dort starten, `GLIMMER_DEBUG=1 glimmer` zeigt mehr).
* **Ein Bild aller Zustände** (zum Ansehen, ohne Fenster): `glimmer --snapshot zustaende.png`

## Technik in drei Zeilen

Python 3 + GTK 4 + `gtk4-layer-shell`, gezeichnet mit Cairo (die weichen Ecken sind dieselben „kontinuierlichen" Ecken wie bei iOS), Bewegung über Federn.
Musik: MPRIS über D-Bus · Laden: `/sys/class/power_supply` · Lautstärke: `pactl subscribe` · Benachrichtigungen: D-Bus-Monitor (nur lesend).
Dieselbe Idee wie Glimmer in ZENITH, nur für den Desktop.
