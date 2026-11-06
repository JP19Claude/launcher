#!/usr/bin/env bash
# Glimmer für Linux einrichten – ohne Paketbau, nur für deinen Benutzer (nichts außerhalb deines Home-Ordners wird verändert,
# außer den Programmpaketen, falls sie fehlen).
#
#   ./install.sh            Pakete prüfen, Glimmer nach ~/.local/bin legen, beim Anmelden starten lassen, jetzt starten
#   ./install.sh pkg        stattdessen ein echtes Arch-Paket bauen und installieren (makepkg -si)
#   ./install.sh demo       Glimmer einmal mit allen Ereignissen vorführen (Strg+C beendet sie)
#   ./install.sh stop       Glimmer beenden
#   ./install.sh uninstall  Glimmer wieder entfernen
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$HOME/.local/bin/glimmer"
AUTOSTART="${XDG_CONFIG_HOME:-$HOME/.config}/autostart/glimmer.desktop"
MENU="${XDG_DATA_HOME:-$HOME/.local/share}/applications/glimmer.desktop"

say()  { printf '\033[1;35m▸\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m✓\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m✗\033[0m %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }
# Beendet nur Glimmers eigenen Prozess (das Muster passt auf genau diese Kommandozeile).
# (Auch eine Vorführung aus diesem Ordner: jeder python3-Prozess, dessen Programm glimmer oder glimmer.py heißt.)
stop_glimmer() { pkill -f "^/usr/bin/python3[0-9.]* [^ ]*glimmer(\.py)?( |\$)" 2>/dev/null; }

py_ok() { python3 -c "import gi; gi.require_version('Gtk','4.0'); gi.require_version('PangoCairo','1.0'); import cairo" 2>/dev/null; }
shell_ok() { python3 -c "import gi; gi.require_version('Gtk4LayerShell','1.0')" 2>/dev/null; }

check_packages() {
  have python3 || die "python3 fehlt: sudo pacman -S python"
  if ! py_ok; then
    say "Es fehlen Pakete (GTK 4, PyGObject, pycairo) – ich installiere sie …"
    have pacman || die "Installiere GTK 4, PyGObject und pycairo mit der Paketverwaltung deiner Distribution."
    sudo pacman -S --needed python python-gobject python-cairo gtk4
  fi
  if ! shell_ok; then
    say "gtk4-layer-shell fehlt – ich installiere es …"
    if have pacman && pacman -Si gtk4-layer-shell >/dev/null 2>&1; then
      sudo pacman -S --needed gtk4-layer-shell
    elif have paru; then paru -S --needed gtk4-layer-shell
    elif have yay;  then yay -S --needed gtk4-layer-shell
    else warn "gtk4-layer-shell nicht gefunden (Arch: extra oder AUR). Ohne das läuft Glimmer nur als normales Fenster."; fi
  fi
  have pactl || have wpctl || warn "Weder pactl noch wpctl gefunden – ohne eines von beiden gibt es keine Lautstärke-Anzeige (sudo pacman -S libpulse)."
  if [ "${XDG_SESSION_TYPE:-}" != "wayland" ]; then
    warn "Du bist nicht in einer Wayland-Sitzung (XDG_SESSION_TYPE=${XDG_SESSION_TYPE:-leer}). Glimmer braucht Wayland, um am Rand zu sitzen"
    warn "(KDE Plasma: beim Anmelden unten links „Plasma (Wayland)“ wählen)."
  fi
}

case "${1:-install}" in
  install)
    check_packages
    install -Dm755 "$HERE/glimmer.py" "$BIN"
    mkdir -p "$(dirname "$AUTOSTART")" "$(dirname "$MENU")"
    sed "s|^Exec=.*|Exec=$BIN --background|" "$HERE/glimmer-autostart.desktop" > "$AUTOSTART"
    sed "s|^Exec=.*|Exec=$BIN --settings|" "$HERE/glimmer.desktop" > "$MENU"
    ok "Glimmer liegt in $BIN und startet bei jeder Anmeldung."
    case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *) warn "$HOME/.local/bin ist nicht in deinem PATH – zum Starten im Terminal: $BIN";; esac
    stop_glimmer || true
    sleep 0.3
    LOG="${XDG_CACHE_HOME:-$HOME/.cache}/glimmer.log"
    mkdir -p "$(dirname "$LOG")"
    setsid -f "$BIN" --settings >"$LOG" 2>&1 || true
    sleep 2
    if pgrep -f "^/usr/bin/python3[0-9.]* ${BIN}( |\$)" >/dev/null 2>&1; then
      ok "Glimmer läuft jetzt: oben in der Mitte erscheint kurz „Glimmer läuft“, und das Einstellungsfenster öffnet sich."
      say "Die Insel selbst ist unsichtbar, solange nichts passiert – sie erscheint bei Musik, Laden, Lautstärke und Benachrichtigungen."
      say "Das Einstellungsfenster öffnest du jederzeit im Anwendungsmenü unter „Glimmer“ (oder mit: $BIN)."
      say "Darin startest und beendest du auch die Vorführung und beendest Glimmer."
    else
      warn "Glimmer ist gleich wieder beendet worden. Meldung:"
      cat "$LOG" >&2 || true
    fi
    ;;
  pkg)
    have makepkg || die "makepkg fehlt: sudo pacman -S base-devel"
    cd "$HERE" && makepkg -si
    ;;
  demo)
    check_packages
    exec /usr/bin/python3 "$HERE/glimmer.py" --demo
    ;;
  stop)
    stop_glimmer && ok "Glimmer beendet." || warn "Glimmer lief nicht."
    ;;
  uninstall)
    stop_glimmer || true
    rm -f "$BIN" "$AUTOSTART" "$MENU"
    ok "Glimmer ist entfernt (die Einstellungen in ~/.config/glimmer bleiben)."
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
