# ZENITH für Arch Linux

ZENITH ist eine Android-App (ein Launcher mit Glimmer, der Insel um die Kamera). Ein Linux-Rechner führt Android-Apps nicht von allein
aus – darum läuft ZENITH hier in **Waydroid**: Android in einem Container, dessen Fenster direkt auf deinem Wayland-Desktop erscheinen.
`zenith-arch` richtet das ein, holt die neueste ZENITH und startet sie.

## Einrichten

```bash
tar xzf ZENITH-Arch.tar.gz
cd zenith-arch
./zenith-arch install        # fragt für sudo nach dem Passwort; lädt das Android-Abbild (etwa 1 GB)
```

Oder als Paket: `makepkg -si` in diesem Ordner, dann `zenith-arch install`. Danach steht „ZENITH“ im Anwendungsmenü.

| Befehl | Was es tut |
|---|---|
| `zenith-arch install` | Waydroid einrichten, ZENITH holen und installieren, ZENITH als Startbildschirm setzen |
| `zenith-arch start` | Waydroid starten und ZENITH zeigen |
| `zenith-arch update` | die neueste ZENITH holen und darüber installieren (Daten bleiben) |
| `zenith-arch stop` | Waydroid beenden |
| `zenith-arch status` | zeigt, was schon eingerichtet ist |
| `zenith-arch remove` | ZENITH aus Waydroid entfernen |

## Was du brauchst

- **Eine Wayland-Sitzung** (GNOME, KDE Plasma, Hyprland, Sway …). Unter X11 geht es in einem kleinen Wayland-Fenster mit `cage` oder `weston`.
- **Einen Kernel mit „binder“.** Linux-Zen hat ihn; beim Standard-Kernel prüft `zenith-arch` und versucht `modprobe binder_linux`, sonst
  `binder_linux-dkms` aus dem AUR oder `linux-zen`.
- **Waydroid** aus den Paketquellen oder dem AUR (`zenith-arch install` installiert es, wenn es kann, sonst sagt es dir, woher).
- `dnsmasq` und `nftables` für das Netzwerk in Android (werden mit installiert).

## Ehrlich dazu

- **Nicht auf Arch Linux ausprobiert.** Ich konnte `zenith-arch` und das PKGBUILD hier nur auf Syntax prüfen und das Laden der APK testen.
  Wenn ein Schritt hakt, sag mir, welcher und was er ausgibt (`zenith-arch status`, `~/.cache/zenith-arch/session.log`).
- Es ist **kein eigenes Linux-Programm**, sondern dieselbe App wie auf dem Handy, in Waydroid. Alles, was an Android-Hardware hängt, fehlt oder
  geht anders: kein Bluetooth-Signal und keine Nearby-Verbindung (Glimmer Drop geht über das WLAN, wenn das Netz die Geräte lässt),
  keine Kamera-Insel (Glimmer läuft als MacBook-Notch oder -Insel, einzustellen unter Einstellungen → Glimmer → Modus),
  Zugriffsrechte wie „Zugriff auf alle Dateien“ und die Bedienungshilfe für Glimmer gibst du in Android selbst.
- Die APK ist die Debug-Version der neuesten Veröffentlichung (alle Prozessor-Arten sind drin, x86_64 und arm64).
