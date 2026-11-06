#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
Glimmer für Linux – die Insel (oder der Notch) am oberen Bildschirmrand, als echtes Programm für den Desktop.

Sie zeigt, was gerade passiert:  Musik (jeder MPRIS-Player: Spotify, Firefox, VLC, mpv …), Laden / Akku, Lautstärke und
Benachrichtigungen.  Ein Mausklick auf die Insel steuert die Musik, Rechtsklick schließt sie.

Gebaut für Wayland-Desktops mit Layer-Shell (KDE Plasma, Hyprland, Sway, labwc …): GTK 4 + gtk4-layer-shell, gezeichnet mit Cairo.
GNOME kennt Layer-Shell nicht – dort läuft Glimmer nur als normales Fenster.

    glimmer                 starten
    glimmer --demo          zeigt der Reihe nach alle Ereignisse (zum Ausprobieren)
    glimmer --mode island   schwebende Insel statt Notch;   --scale 1.3  größer
    glimmer --snapshot x.png  zeichnet alle Zustände in ein Bild (braucht kein Fenster)

Einstellungen: ~/.config/glimmer/config.json
"""

import os
import sys

APP_VERSION = "22.0"


def _preload_layer_shell():
    """gtk4-layer-shell muss vor libwayland geladen sein; bei Python geht das nur über LD_PRELOAD – also starten wir uns einmal neu."""
    if os.environ.get("_GLIMMER_REEXEC") or "--snapshot" in sys.argv or "--version" in sys.argv or "--no-layer-shell" in sys.argv:
        return
    if not os.environ.get("WAYLAND_DISPLAY"):
        return
    if "libgtk4-layer-shell" in os.environ.get("LD_PRELOAD", ""):
        return
    found = []
    for d in ("/usr/lib", "/usr/lib64", "/usr/local/lib", "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu"):
        try:
            found += [os.path.join(d, n) for n in os.listdir(d) if n.startswith("libgtk4-layer-shell.so")]
        except OSError:
            pass
    if not found:
        return
    found.sort(key=lambda p: (len(p), p))
    env = dict(os.environ)
    env["_GLIMMER_REEXEC"] = "1"
    env["LD_PRELOAD"] = found[0] + ((" " + env["LD_PRELOAD"]) if env.get("LD_PRELOAD") else "")
    env.setdefault("GDK_BACKEND", "wayland")
    try:
        os.execve(sys.executable, [sys.executable] + sys.argv, env)
    except OSError:
        pass


_preload_layer_shell()

import argparse  # noqa: E402
import html  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402
import re  # noqa: E402
import shutil  # noqa: E402
import signal  # noqa: E402
import subprocess  # noqa: E402
import threading  # noqa: E402
import time  # noqa: E402
import urllib.request  # noqa: E402

import gi  # noqa: E402

gi.require_version("Gtk", "4.0")
gi.require_version("Gdk", "4.0")
gi.require_version("Pango", "1.0")
gi.require_version("PangoCairo", "1.0")
gi.require_version("GdkPixbuf", "2.0")
from gi.repository import Gdk, GdkPixbuf, Gio, GLib, Gtk, Pango, PangoCairo  # noqa: E402

import cairo  # noqa: E402

try:
    gi.require_version("Gtk4LayerShell", "1.0")
    from gi.repository import Gtk4LayerShell as LayerShell  # noqa: E402
except (ValueError, ImportError):
    LayerShell = None

DEBUG = bool(os.environ.get("GLIMMER_DEBUG"))
# Programs we start must not inherit the preload (it would load GTK into every pactl call).
CLEAN_ENV = {k: v for k, v in os.environ.items() if k != "LD_PRELOAD"}
CLEAN_ENV["LC_ALL"] = "C"


def log(*a):
    if DEBUG:
        print("[glimmer]", *a, file=sys.stderr, flush=True)


# ------------------------------------------------------------------------------------------------------------------ settings

DEFAULTS = {
    "mode": "notch",            # "notch": am Rand angewachsen, mit Ohren · "island": schwebende Insel
    "scale": 1.0,
    "monitor": 0,               # Nummer des Bildschirms
    "layer": "overlay",         # "overlay": auch über Vollbild · "top"
    "language": "auto",         # "auto" | "de" | "en"
    "music": True,
    "music_persistent": True,   # kleine Insel, solange Musik läuft
    "track_popups": True,       # beim Titelwechsel kurz Titel und Interpret zeigen
    "hover_reveal": True,       # Maus an den oberen Rand in der Mitte: Musiksteuerung erscheint
    "charging": True,
    "low_battery": True,
    "volume": True,
    "notifications": True,
    "accent": "#FF7A9C",
}

CONFIG_PATH = os.path.join(os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config"), "glimmer", "config.json")


def load_config(path=None):
    cfg = dict(DEFAULTS)
    path = path or CONFIG_PATH
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        for k, v in data.items():
            if k in cfg and isinstance(v, type(DEFAULTS[k])) or (k in cfg and isinstance(DEFAULTS[k], float) and isinstance(v, int)):
                cfg[k] = v
    except FileNotFoundError:
        if path == CONFIG_PATH:
            try:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(DEFAULTS, f, indent=2, ensure_ascii=False)
                    f.write("\n")
            except OSError:
                pass
    except (OSError, ValueError) as e:
        print("Glimmer: die Einstellungen konnten nicht gelesen werden (%s) – ich nehme die Standardwerte." % e, file=sys.stderr)
    cfg["scale"] = max(0.6, min(2.5, float(cfg["scale"])))
    if cfg["mode"] not in ("notch", "island"):
        cfg["mode"] = "notch"
    return cfg


STRINGS = {
    "de": {"charging": "Lädt", "full": "Voll geladen", "unplugged": "Akkubetrieb", "low": "Akku schwach",
           "nothing": "Keine Wiedergabe", "muted": "Stumm", "notification": "Benachrichtigung"},
    "en": {"charging": "Charging", "full": "Fully charged", "unplugged": "On battery", "low": "Battery low",
           "nothing": "Nothing playing", "muted": "Muted", "notification": "Notification"},
}
_lang = "de"


def set_language(choice):
    global _lang
    if choice in ("de", "en"):
        _lang = choice
        return
    env = os.environ.get("LC_ALL") or os.environ.get("LC_MESSAGES") or os.environ.get("LANG") or ""
    _lang = "de" if env.lower().startswith("de") else "en"


def tr(key):
    return STRINGS[_lang].get(key, key)


def parse_color(text, fallback=(1.0, 0.48, 0.61)):
    m = re.fullmatch(r"#?([0-9a-fA-F]{6})", str(text).strip())
    if not m:
        return fallback
    v = int(m.group(1), 16)
    return (((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0)


# ------------------------------------------------------------------------------------------------------------------ geometry

# Content sizes in dp (scaled by the setting "scale").
LAYOUTS = {
    "music": (236, 36),
    "music_open": (420, 152),
    "track": (344, 64),
    "volume": (248, 40),
    "power": (236, 40),
    "notify": (372, 88),
}
EAR = 13.0       # the notch's flares where it meets the edge of the screen
MARGIN = 10.0    # the floating island's distance from the top
SHADOW = 26.0    # room under the island for its shadow


def window_size(scale):
    w = (LAYOUTS["music_open"][0] + 2 * EAR + 30) * scale
    h = (LAYOUTS["music_open"][1] + MARGIN + SHADOW) * scale
    return int(math.ceil(w)), int(math.ceil(h))


def clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v


def _corner(r, smoothing, budget):
    """One corner of an iOS-style 'continuous' rectangle (Figma's corner smoothing), worked out for the top right one:
    the corner at the origin, the edge arriving along the negative x axis, the next one leaving down the y axis."""
    if r <= 0.5:
        return None
    xi = smoothing
    p = (1.0 + xi) * r
    if p > budget:
        xi = max(0.0, budget / r - 1.0)
        p = budget
    arc_measure = 90.0 * (1.0 - xi)
    arc_len = math.sin(math.radians(arc_measure / 2.0)) * r * math.sqrt(2.0)
    alpha = (90.0 - arc_measure) / 2.0
    p3p4 = r * math.tan(math.radians(alpha / 2.0))
    beta = 45.0 * xi
    c = p3p4 * math.cos(math.radians(beta))
    d = c * math.tan(math.radians(beta))
    b = (p - arc_len - c - d) / 3.0
    a = 2.0 * b
    sx, sy = -p + a + b + c, d
    al = math.radians(alpha)
    theta = math.radians(90.0 - 2.0 * alpha)
    k = 4.0 / 3.0 * math.tan(theta / 4.0)
    ex, ey = sx + arc_len, sy + arc_len
    return {
        "start": (-p, 0.0),
        "segs": [
            ((-p + a, 0.0), (-p + a + b, 0.0), (sx, sy)),
            ((sx + k * r * math.cos(al), sy + k * r * math.sin(al)), (ex - k * r * math.sin(al), ey - k * r * math.cos(al)), (ex, ey)),
            ((ex + d, ey + c), (ex + d, ey + b + c), (ex + d, ey + a + b + c)),
        ],
    }


def _add_corner(cr, r, smoothing, budget, ox, oy, quarter, first):
    def mp(pt):
        x, y = pt
        if quarter == 0:
            return ox + x, oy + y
        if quarter == 1:
            return ox - y, oy + x
        if quarter == 2:
            return ox - x, oy - y
        return ox + y, oy - x

    c = _corner(r, smoothing, budget)
    if c is None:
        (cr.move_to if first else cr.line_to)(ox, oy)
        return
    sx, sy = mp(c["start"])
    (cr.move_to if first else cr.line_to)(sx, sy)
    for c1, c2, e in c["segs"]:
        cr.curve_to(*mp(c1), *mp(c2), *mp(e))


def body_path(cr, x, y, w, h, rt, rb, ear=0.0, smoothing=0.6):
    """The island's outline. rt/rb: radii of the upper and lower corners (0 on top: it hangs from the screen's edge);
    ear > 0: the concave flares left and right of the top, as on the notch of a laptop."""
    budget = min(w, h) / 2.0
    rt = clamp(rt, 0.0, budget)
    rb = clamp(rb, 0.0, budget)
    cr.new_path()
    if ear > 0.5:
        cr.move_to(x - ear, y)
        cr.line_to(x + w + ear, y)
        cr.arc_negative(x + w + ear, y + ear, ear, -math.pi / 2, -math.pi)
        _add_corner(cr, rb, smoothing, budget, x + w, y + h, 1, False)
        _add_corner(cr, rb, smoothing, budget, x, y + h, 2, False)
        cr.line_to(x, y + ear)
        cr.arc_negative(x - ear, y + ear, ear, 0.0, -math.pi / 2)
        cr.close_path()
        return
    _add_corner(cr, rt, smoothing, budget, x + w, y, 0, True)
    _add_corner(cr, rb, smoothing, budget, x + w, y + h, 1, False)
    _add_corner(cr, rb, smoothing, budget, x, y + h, 2, False)
    _add_corner(cr, rt, smoothing, budget, x, y, 3, False)
    cr.close_path()


def rrect(cr, x, y, w, h, r):
    r = min(r, w / 2.0, h / 2.0)
    cr.new_sub_path()
    cr.arc(x + w - r, y + r, r, -math.pi / 2, 0)
    cr.arc(x + w - r, y + h - r, r, 0, math.pi / 2)
    cr.arc(x + r, y + h - r, r, math.pi / 2, math.pi)
    cr.arc(x + r, y + r, r, math.pi, 1.5 * math.pi)
    cr.close_path()


# ------------------------------------------------------------------------------------------------------------------ drawing helpers

WHITE = (1.0, 1.0, 1.0, 1.0)
DIM = (1.0, 1.0, 1.0, 0.62)
FAINT = (1.0, 1.0, 1.0, 0.20)
GREEN = (0.30, 0.90, 0.55)
RED = (1.0, 0.36, 0.34)


def draw_text(cr, text, x, y, size, rgba, bold=False, max_w=None, align="left"):
    """Text with its vertical centre at y. Returns the width."""
    if not text:
        return 0
    layout = PangoCairo.create_layout(cr)
    fd = Pango.FontDescription()
    fd.set_family("Sans")
    fd.set_weight(Pango.Weight.SEMIBOLD if bold else Pango.Weight.NORMAL)
    fd.set_absolute_size(size * Pango.SCALE)
    layout.set_font_description(fd)
    layout.set_text(text, -1)
    if max_w is not None:
        layout.set_width(int(max(max_w, 1) * Pango.SCALE))
        layout.set_ellipsize(Pango.EllipsizeMode.END)
    w, h = layout.get_pixel_size()
    ox = x if align == "left" else (x - w if align == "right" else x - w / 2.0)
    cr.save()
    cr.move_to(ox, y - h / 2.0)
    cr.set_source_rgba(*rgba)
    PangoCairo.show_layout(cr, layout)
    cr.restore()
    return w


def draw_text_block(cr, text, x, y, size, rgba, max_w, lines=2):
    """A few lines of wrapped text starting at y (the top)."""
    if not text:
        return
    layout = PangoCairo.create_layout(cr)
    fd = Pango.FontDescription()
    fd.set_family("Sans")
    fd.set_absolute_size(size * Pango.SCALE)
    layout.set_font_description(fd)
    layout.set_text(text, -1)
    layout.set_width(int(max_w * Pango.SCALE))
    layout.set_wrap(Pango.WrapMode.WORD_CHAR)
    layout.set_ellipsize(Pango.EllipsizeMode.END)
    layout.set_height(-lines)
    cr.save()
    cr.move_to(x, y)
    cr.set_source_rgba(*rgba)
    PangoCairo.show_layout(cr, layout)
    cr.restore()


def fmt_time(us):
    s = max(0, int(us // 1_000_000))
    return "%d:%02d" % (s // 60, s % 60)


def icon_play(cr, cx, cy, z):
    cr.move_to(cx - 0.32 * z, cy - 0.5 * z)
    cr.line_to(cx + 0.52 * z, cy)
    cr.line_to(cx - 0.32 * z, cy + 0.5 * z)
    cr.close_path()
    cr.fill()


def icon_pause(cr, cx, cy, z):
    for dx in (-0.25, 0.25):
        rrect(cr, cx + dx * z - 0.13 * z, cy - 0.45 * z, 0.26 * z, 0.9 * z, 0.07 * z)
        cr.fill()


def icon_skip(cr, cx, cy, z, forward):
    cr.save()
    cr.translate(cx, cy)
    if not forward:
        cr.scale(-1, 1)
    cr.move_to(-0.42 * z, -0.42 * z)
    cr.line_to(0.28 * z, 0)
    cr.line_to(-0.42 * z, 0.42 * z)
    cr.close_path()
    cr.fill()
    rrect(cr, 0.30 * z, -0.42 * z, 0.13 * z, 0.84 * z, 0.05 * z)
    cr.fill()
    cr.restore()


def icon_speaker(cr, cx, cy, z, level, muted, rgba):
    x0 = cx - 0.5 * z
    cr.set_source_rgba(*rgba)
    cr.move_to(x0, cy - 0.2 * z)
    cr.line_to(x0 + 0.22 * z, cy - 0.2 * z)
    cr.line_to(x0 + 0.55 * z, cy - 0.48 * z)
    cr.line_to(x0 + 0.55 * z, cy + 0.48 * z)
    cr.line_to(x0 + 0.22 * z, cy + 0.2 * z)
    cr.line_to(x0, cy + 0.2 * z)
    cr.close_path()
    cr.fill()
    cr.set_line_width(0.09 * z)
    cr.set_line_cap(cairo.LINE_CAP_ROUND)
    if muted:
        cr.move_to(x0 + 0.75 * z, cy - 0.22 * z)
        cr.line_to(x0 + 1.07 * z, cy + 0.22 * z)
        cr.move_to(x0 + 1.07 * z, cy - 0.22 * z)
        cr.line_to(x0 + 0.75 * z, cy + 0.22 * z)
        cr.stroke()
        return
    for i, rr in enumerate((0.3, 0.58)):
        if level > (0.02 if i == 0 else 0.5):
            cr.new_sub_path()
            cr.arc(x0 + 0.55 * z, cy, rr * z, -0.72, 0.72)
            cr.stroke()


def icon_bolt(cr, cx, cy, z):
    pts = [(0.12, -0.5), (-0.28, 0.08), (-0.02, 0.08), (-0.12, 0.5), (0.28, -0.08), (0.02, -0.08)]
    cr.move_to(cx + pts[0][0] * z, cy + pts[0][1] * z)
    for px, py in pts[1:]:
        cr.line_to(cx + px * z, cy + py * z)
    cr.close_path()
    cr.fill()


def icon_battery(cr, cx, cy, z, pct, color):
    bw, bh = 1.0 * z, 0.54 * z
    x, y = cx - bw / 2 - 0.03 * z, cy - bh / 2
    cr.set_source_rgba(*color, 0.95)
    cr.set_line_width(0.07 * z)
    rrect(cr, x, y, bw, bh, 0.14 * z)
    cr.stroke()
    rrect(cr, x + bw + 0.02 * z, cy - 0.1 * z, 0.07 * z, 0.2 * z, 0.03 * z)
    cr.fill()
    inner = (bw - 0.2 * z) * clamp(pct / 100.0, 0.04, 1.0)
    rrect(cr, x + 0.1 * z, y + 0.1 * z, inner, bh - 0.2 * z, 0.06 * z)
    cr.fill()


def icon_bell(cr, cx, cy, z):
    cr.new_path()
    cr.arc(cx, cy - 0.05 * z, 0.32 * z, math.pi, 0)
    cr.curve_to(cx + 0.32 * z, cy + 0.12 * z, cx + 0.46 * z, cy + 0.22 * z, cx + 0.46 * z, cy + 0.3 * z)
    cr.line_to(cx - 0.46 * z, cy + 0.3 * z)
    cr.curve_to(cx - 0.46 * z, cy + 0.22 * z, cx - 0.32 * z, cy + 0.12 * z, cx - 0.32 * z, cy - 0.05 * z)
    cr.close_path()
    cr.fill()
    cr.new_path()
    cr.arc(cx, cy + 0.4 * z, 0.09 * z, 0, math.pi)
    cr.fill()


def icon_note(cr, cx, cy, z):
    cr.new_path()
    cr.arc(cx - 0.18 * z, cy + 0.3 * z, 0.17 * z, 0, 2 * math.pi)
    cr.fill()
    cr.rectangle(cx - 0.02 * z, cy - 0.38 * z, 0.08 * z, 0.7 * z)
    cr.fill()
    cr.move_to(cx - 0.02 * z, cy - 0.38 * z)
    cr.curve_to(cx + 0.18 * z, cy - 0.3 * z, cx + 0.3 * z, cy - 0.15 * z, cx + 0.26 * z, cy + 0.02 * z)
    cr.curve_to(cx + 0.2 * z, cy - 0.12 * z, cx + 0.1 * z, cy - 0.15 * z, cx + 0.06 * z, cy - 0.16 * z)
    cr.close_path()
    cr.fill()


def draw_eq(cr, x, cy, h, t, playing, color):
    for i in range(4):
        amp = 0.28 + 0.72 * abs(math.sin(t * (2.3 + i * 0.85) + i * 1.9)) if playing else 0.2
        bh = max(3.0, h * amp)
        rrect(cr, x + i * 5.2, cy - bh / 2, 3.2, bh, 1.6)
        cr.set_source_rgba(*color, 0.95)
        cr.fill()


# ------------------------------------------------------------------------------------------------------------------ animation

class Spring:
    """A damped spring: the island's size, opacity and content move like something physical, not along a fixed curve."""

    def __init__(self, value=0.0, k=520.0, ratio=0.9, eps=0.05):
        self.x = self.target = value
        self.v = 0.0
        self.k = k
        self.c = 2.0 * ratio * math.sqrt(k)
        self.eps = eps

    def step(self, dt):
        n = max(1, int(dt / 0.004))
        h = dt / n
        for _ in range(n):
            a = -self.k * (self.x - self.target) - self.c * self.v
            self.v += a * h
            self.x += self.v * h

    @property
    def settled(self):
        return abs(self.x - self.target) < self.eps and abs(self.v) < self.eps * 8

    def snap(self):
        self.x = self.target
        self.v = 0.0


class Island:
    """What the island looks like right now: the size springs, what it shows and how it draws it. Knows nothing of GTK."""

    def __init__(self, cfg):
        self.cfg = cfg
        self.s = cfg["scale"]
        self.notch = cfg["mode"] == "notch"
        self.accent = parse_color(cfg["accent"])
        self.w = Spring(0, 560, 0.88, 0.1)
        self.h = Spring(0, 560, 0.88, 0.1)
        self.alpha = Spring(0, 900, 1.0, 0.01)
        self.content = Spring(0, 620, 1.0, 0.01)
        self.kind = None
        self.data = None
        self.open = False
        self.visible = False
        self.hits = []
        self.ox = self.oy = 0.0

    # --- what to show

    def size_for(self):
        k = self.kind
        if k == "music":
            return LAYOUTS["music_open" if self.open else "music"]
        if k == "notify":
            w, h = LAYOUTS["notify"]
            return (w, h if (self.data or {}).get("body") else h - 24)
        return LAYOUTS.get(k, (200, 36))

    def show(self, kind, data, open_=False):
        was_hidden = (not self.visible) or self.kind is None
        changed = kind != self.kind or open_ != self.open or (kind == "notify" and data is not self.data)
        self.kind, self.data, self.open = kind, data, open_
        self.visible = True
        tw, th = self.size_for()
        s = self.s
        if was_hidden:
            self.w.x = tw * 0.5 * s
            self.h.x = 1.0
            self.w.v = self.h.v = 0.0
            changed = True
        self.w.target, self.h.target = tw * s, th * s
        self.alpha.target = 1.0
        if changed:
            self.content.x = -0.25
            self.content.v = 0.0
            self.content.target = 1.0

    def hide(self):
        if not self.visible:
            return
        self.visible = False
        self.w.target = max(70 * self.s, self.w.x * 0.55)
        self.h.target = 1.0
        self.alpha.target = 0.0

    def snap(self):
        for sp in (self.w, self.h, self.alpha, self.content):
            sp.snap()
        self.content.x = 1.0

    def step(self, dt):
        """Advances the springs; returns (still moving, something ambient – equaliser, charging sweep – is animating)."""
        moved = False
        for sp in (self.w, self.h, self.alpha, self.content):
            sp.step(dt)
            moved = moved or not sp.settled
        if not self.visible and self.alpha.x < 0.01 and self.alpha.settled:
            self.alpha.x = 0.0
            self.kind = None
            moved = False
        ambient = False
        if self.kind in ("music", "track") and self.visible:
            ambient = getattr(self.data, "status", "") == "Playing"
        elif self.kind == "power" and self.visible:
            ambient = (self.data or {}).get("state") == "charging"
        return moved, ambient

    # --- geometry

    def geometry(self, W):
        s = self.s
        w, h = max(self.w.x, 0.0), max(self.h.x, 0.0)
        ear = min(EAR * s, h * 0.5) if self.notch else 0.0
        return (W - w) / 2.0, (0.0 if self.notch else MARGIN * s), w, h, ear

    def input_rect(self, W, sensor):
        if self.kind is not None and self.visible and self.alpha.x > 0.05:
            bx, by, w, h, ear = self.geometry(W)
            return int(bx - ear) - 1, int(by), int(w + 2 * ear) + 2, int(h) + 1
        if sensor:
            return int(W / 2 - 90 * self.s), 0, int(180 * self.s), 2
        return None

    def hit(self, x, y):
        lx, ly = (x - self.ox) / self.s, (y - self.oy) / self.s
        for x0, y0, x1, y1, action in reversed(self.hits):
            if x0 <= lx <= x1 and y0 <= ly <= y1:
                if action == "seek":
                    return action, clamp((lx - x0) / max(x1 - x0, 1), 0.0, 1.0)
                return action, None
        return None

    # --- drawing

    def draw(self, cr, W, H, t):
        self.hits = []
        if self.kind is None or self.alpha.x < 0.01:
            return
        s = self.s
        bx, by, w, h, ear = self.geometry(W)
        if w < 2 or h < 1:
            return
        a = clamp(self.alpha.x, 0.0, 1.0)
        r = min(h / 2.0, (22 if self.notch else 26) * s)
        rt = 0.0 if self.notch else r

        def path():
            body_path(cr, bx, by, w, h, rt, r, ear)

        if not self.notch:
            for i in range(1, 6):
                cr.save()
                cr.translate(0, 4 * s)
                path()
                cr.set_line_width(i * 3.2 * s)
                cr.set_source_rgba(0, 0, 0, 0.06 * a)
                cr.stroke()
                cr.restore()
        path()
        cr.set_source_rgba(0, 0, 0, a)
        cr.fill()

        cr.save()
        cr.rectangle(0, 1 if self.notch else 0, W, H)
        cr.clip()
        path()
        cr.set_line_width(1.0)
        cr.set_source_rgba(1, 1, 1, 0.075 * a)
        cr.stroke()
        if self.kind == "power" and (self.data or {}).get("state") == "charging":
            path()
            cr.set_line_width(2.0 * s)
            cr.set_source_rgba(*GREEN, 0.25 * a)
            cr.stroke()
            phase = (t * 0.85) % 1.0
            gx = bx - w * 0.3 + phase * w * 1.6
            grad = cairo.LinearGradient(gx - w * 0.3, 0, gx + w * 0.3, 0)
            grad.add_color_stop_rgba(0.0, *GREEN, 0.0)
            grad.add_color_stop_rgba(0.5, *GREEN, 0.95 * a)
            grad.add_color_stop_rgba(1.0, *GREEN, 0.0)
            path()
            cr.set_line_width(2.6 * s)
            cr.set_source(grad)
            cr.stroke()
        cr.restore()

        tw, th = self.size_for()
        self.ox, self.oy = bx + (w - tw * s) / 2.0, by
        ca = clamp(self.content.x, 0.0, 1.0) * a
        if ca < 0.01:
            return
        cr.save()
        path()
        cr.clip()
        cr.push_group()
        cr.save()
        cr.translate(self.ox, self.oy)
        cr.scale(s, s)
        k = self.kind
        if k in ("music", "track"):
            self._music(cr, tw, th, t, k == "track")
        elif k == "volume":
            self._volume(cr, tw, th)
        elif k == "power":
            self._power(cr, tw, th)
        elif k == "notify":
            self._notify(cr, tw, th)
        cr.restore()
        cr.pop_group_to_source()
        cr.paint_with_alpha(ca)
        cr.restore()

    def _art(self, cr, p, x, y, size):
        cr.save()
        body_path(cr, x, y, size, size, size * 0.22, size * 0.22)
        cr.clip()
        art = getattr(p, "art", None)
        acc = getattr(p, "accent", None) or self.accent
        if art is not None:
            cr.translate(x, y)
            cr.scale(size / art.get_width(), size / art.get_height())
            cr.set_source_surface(art, 0, 0)
            cr.paint()
        else:
            g = cairo.LinearGradient(x, y, x + size, y + size)
            g.add_color_stop_rgb(0, *[c * 0.9 for c in acc])
            g.add_color_stop_rgb(1, *[c * 0.35 for c in acc])
            cr.set_source(g)
            cr.paint()
            cr.set_source_rgba(1, 1, 1, 0.85)
            icon_note(cr, x + size / 2, y + size / 2, size * 0.5)
        cr.restore()

    def _music(self, cr, tw, th, t, track):
        p = self.data
        acc = getattr(p, "accent", None) or self.accent
        playing = getattr(p, "status", "") == "Playing"
        title = getattr(p, "title", "") or tr("nothing")
        artist = getattr(p, "artist", "")
        if track:
            self._art(cr, p, 10, 10, 44)
            draw_text(cr, title, 64, 24, 14, WHITE, bold=True, max_w=tw - 64 - 44)
            draw_text(cr, artist, 64, 43, 12, DIM, max_w=tw - 64 - 44)
            draw_eq(cr, tw - 30, 32, 20, t, playing, acc)
            self.hits.append((0, 0, tw, th, "raise"))
            return
        if not self.open:
            self._art(cr, p, 8, 6, 24)
            draw_text(cr, title, 40, th / 2, 12.5, WHITE, max_w=tw - 40 - 36)
            draw_eq(cr, tw - 28, th / 2, 14, t, playing, acc)
            return
        self._art(cr, p, 18, 18, 88)
        self.hits.append((18, 18, 106, 106, "raise"))
        x0, x1 = 124, tw - 20
        draw_text(cr, title, x0, 30, 15.5, WHITE, bold=True, max_w=x1 - x0)
        draw_text(cr, artist, x0, 51, 13, DIM, max_w=x1 - x0)
        length = getattr(p, "length", 0) or 0
        pos = p.position() if hasattr(p, "position") else 0
        frac = clamp(pos / length, 0.0, 1.0) if length > 0 else 0.0
        rrect(cr, x0, 78, x1 - x0, 4, 2)
        cr.set_source_rgba(*FAINT)
        cr.fill()
        if frac > 0:
            rrect(cr, x0, 78, max(4.0, (x1 - x0) * frac), 4, 2)
            cr.set_source_rgba(*acc, 1)
            cr.fill()
        draw_text(cr, fmt_time(pos), x0, 94, 10.5, DIM)
        if length > 0:
            draw_text(cr, fmt_time(length), x1, 94, 10.5, DIM, align="right")
        self.hits.append((x0 - 4, 70, x1 + 4, 100, "seek"))
        cx, cy = (x0 + x1) / 2.0, 124
        cr.set_source_rgba(*WHITE)
        icon_skip(cr, cx - 58, cy, 17, False)
        icon_skip(cr, cx + 58, cy, 17, True)
        cr.set_source_rgba(*WHITE)
        (icon_pause if playing else icon_play)(cr, cx, cy, 24)
        self.hits.append((cx - 80, cy - 20, cx - 36, cy + 20, "prev"))
        self.hits.append((cx - 24, cy - 20, cx + 24, cy + 20, "playpause"))
        self.hits.append((cx + 36, cy - 20, cx + 80, cy + 20, "next"))

    def _volume(self, cr, tw, th):
        d = self.data or {}
        pct, muted = d.get("pct", 0), d.get("muted", False)
        icon_speaker(cr, 28, th / 2, 17, pct / 100.0, muted, WHITE if not muted else DIM)
        if muted:
            draw_text(cr, tr("muted"), 54, th / 2, 13, DIM, bold=True)
            return
        x0, x1 = 50, tw - 56
        rrect(cr, x0, th / 2 - 2.5, x1 - x0, 5, 2.5)
        cr.set_source_rgba(*FAINT)
        cr.fill()
        fill = clamp(pct / 100.0, 0.0, 1.0)
        if fill > 0:
            rrect(cr, x0, th / 2 - 2.5, max(5.0, (x1 - x0) * fill), 5, 2.5)
            cr.set_source_rgba(1, 1, 1, 0.95)
            cr.fill()
        draw_text(cr, "%d" % pct, tw - 18, th / 2, 13, WHITE, bold=True, align="right")

    def _power(self, cr, tw, th):
        d = self.data or {}
        state, pct = d.get("state", "unplugged"), d.get("pct", 0)
        color = GREEN if state in ("charging", "full") else RED if state == "low" else (1.0, 1.0, 1.0)
        icon_battery(cr, 30, th / 2, 20, pct, color)
        if state == "charging":
            cr.set_source_rgba(0.04, 0.12, 0.07, 1)
            icon_bolt(cr, 29, th / 2, 11)
        draw_text(cr, tr(state), 54, th / 2, 13, WHITE, bold=True, max_w=tw - 54 - 62)
        draw_text(cr, "%d %%" % pct, tw - 18, th / 2, 14, color + (1.0,), bold=True, align="right")

    def _notify(self, cr, tw, th):
        d = self.data or {}
        icon = d.get("icon")
        cr.set_source_rgba(1, 1, 1, 0.92)
        if icon is not None:
            cr.save()
            body_path(cr, 14, 14, 36, 36, 9, 9)
            cr.clip()
            cr.translate(14, 14)
            cr.scale(36.0 / icon.get_width(), 36.0 / icon.get_height())
            cr.set_source_surface(icon, 0, 0)
            cr.paint()
            cr.restore()
        else:
            rrect(cr, 14, 14, 36, 36, 9)
            cr.set_source_rgba(1, 1, 1, 0.12)
            cr.fill()
            cr.set_source_rgba(1, 1, 1, 0.9)
            icon_bell(cr, 32, 31, 17)
        app = (d.get("app") or tr("notification")).upper()
        x0, mw = 62, tw - 62 - 16
        draw_text(cr, app, x0, 17, 10, DIM, max_w=mw)
        draw_text(cr, d.get("summary", ""), x0, 34, 14, WHITE, bold=True, max_w=mw)
        if d.get("body"):
            draw_text_block(cr, d["body"], x0, 46, 12.5, DIM, mw, lines=2)


# ------------------------------------------------------------------------------------------------------------------ media players

PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
MPRIS_PREFIX = "org.mpris.MediaPlayer2."


class Player:
    """One MPRIS player (Spotify, a browser tab, VLC, mpv …)."""

    def __init__(self, name, owner):
        self.name, self.owner = name, owner
        self.title = self.artist = ""
        self.status = "Stopped"
        self.length = 0
        self.art_url = ""
        self.art = None
        self.accent = None
        self.trackid = ""
        self._pos = 0
        self._stamp = time.monotonic()
        self.last_active = 0.0
        self.last_change = 0.0
        self.known = False

    def position(self):
        pos = self._pos
        if self.status == "Playing":
            pos += (time.monotonic() - self._stamp) * 1_000_000
        return int(clamp(pos, 0, self.length) if self.length > 0 else max(pos, 0))

    def set_position(self, us):
        self._pos, self._stamp = int(us), time.monotonic()

    def apply(self, props):
        """Takes changed properties; returns what changed: {'status', 'track', 'art'}."""
        changed = set()
        now = time.monotonic()
        if "PlaybackStatus" in props:
            new = str(props["PlaybackStatus"])
            if new != self.status:
                self._pos = self.position()
                self.status = new
                self._stamp = now
                self.last_change = now
                if new == "Playing":
                    self.last_active = now
                changed.add("status")
        if "Metadata" in props and isinstance(props["Metadata"], dict):
            m = props["Metadata"]
            title = str(m.get("xesam:title") or "")
            artist = m.get("xesam:artist")
            artist = ", ".join(str(a) for a in artist) if isinstance(artist, (list, tuple)) else str(artist or "")
            if title != self.title or artist != self.artist:
                self.title, self.artist = title, artist
                self._pos, self._stamp = 0, now
                changed.add("track")
            try:
                self.length = int(m.get("mpris:length") or 0)
            except (TypeError, ValueError):
                self.length = 0
            self.trackid = str(m.get("mpris:trackid") or "")
            url = str(m.get("mpris:artUrl") or "")
            if url != self.art_url:
                self.art_url = url
                if not url:
                    self.art, self.accent = None, None
                changed.add("art")
        if "Position" in props:
            try:
                self.set_position(int(props["Position"]))
            except (TypeError, ValueError):
                pass
        return changed


def pixbuf_to_surface(pb):
    """A GdkPixbuf as a Cairo surface, and its average colour lifted to a bright accent."""
    w, h, rs, nc = pb.get_width(), pb.get_height(), pb.get_rowstride(), pb.get_n_channels()
    data = pb.get_pixels()
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    stride = surf.get_stride()
    surf.flush()
    buf = surf.get_data()
    sr = sg = sb = n = 0
    for y in range(h):
        row, out = y * rs, y * stride
        for x in range(w):
            i = row + x * nc
            r, g, b = data[i], data[i + 1], data[i + 2]
            a = data[i + 3] if nc == 4 else 255
            if a != 255:
                r, g, b = r * a // 255, g * a // 255, b * a // 255
            o = out + x * 4
            buf[o], buf[o + 1], buf[o + 2], buf[o + 3] = b, g, r, a
            if (x + y) % 3 == 0:
                sr, sg, sb, n = sr + r, sg + g, sb + b, n + 1
    surf.mark_dirty()
    if n == 0:
        return surf, None
    r, g, b = sr / n / 255.0, sg / n / 255.0, sb / n / 255.0
    mx, mn = max(r, g, b), min(r, g, b)
    if mx - mn < 0.08:
        return surf, (0.9, 0.9, 0.95)
    # Stretch it: strong colour, bright enough to read on black.
    k = 0.92 / mx
    r, g, b = r * k, g * k, b * k
    mean = (r + g + b) / 3
    r, g, b = [clamp(mean + (c - mean) * 1.35, 0.0, 1.0) for c in (r, g, b)]
    return surf, (r, g, b)


def load_art_async(url, done):
    """Fetches cover art off the main thread; done(surface, accent) runs on it (through idle_add)."""
    def work():
        try:
            if url.startswith("file://"):
                with open(GLib.filename_from_uri(url)[0], "rb") as f:
                    raw = f.read(8_000_000)
            elif url.startswith(("http://", "https://")):
                req = urllib.request.Request(url, headers={"User-Agent": "Glimmer/" + APP_VERSION})
                with urllib.request.urlopen(req, timeout=5) as resp:
                    raw = resp.read(8_000_000)
            else:
                return
            loader = GdkPixbuf.PixbufLoader()
            loader.write(raw)
            loader.close()
            pb = loader.get_pixbuf()
            if pb is None:
                return
            w, h = pb.get_width(), pb.get_height()
            side = min(w, h)
            pb = pb.new_subpixbuf((w - side) // 2, (h - side) // 2, side, side).scale_simple(120, 120, GdkPixbuf.InterpType.BILINEAR)
            surf, accent = pixbuf_to_surface(pb)
            GLib.idle_add(done, surf, accent)
        except Exception as e:  # noqa: BLE001 – a missing cover is never a reason to stop
            log("cover:", e)
    threading.Thread(target=work, daemon=True).start()


class Mpris:
    def __init__(self, app):
        self.app = app
        self.bus = None
        self.players = {}  # unique bus name -> Player

    def start(self):
        try:
            self.bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
        except GLib.Error as e:
            log("kein Session-Bus:", e.message)
            return
        self.bus.signal_subscribe("org.freedesktop.DBus", "org.freedesktop.DBus", "NameOwnerChanged", "/org/freedesktop/DBus",
                                  None, Gio.DBusSignalFlags.NONE, self._on_owner)
        self.bus.signal_subscribe(None, "org.freedesktop.DBus.Properties", "PropertiesChanged", "/org/mpris/MediaPlayer2",
                                  None, Gio.DBusSignalFlags.NONE, self._on_props)
        self.bus.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames", None,
                      GLib.VariantType("(as)"), Gio.DBusCallFlags.NONE, 3000, None, self._on_names)

    def _on_names(self, bus, res, *rest):
        try:
            names = bus.call_finish(res).unpack()[0]
        except GLib.Error:
            return
        for n in names:
            if n.startswith(MPRIS_PREFIX):
                self._add(n)

    def _on_owner(self, conn, sender, path, iface, sig, params, *rest):
        name, old, new = params.unpack()
        if not name.startswith(MPRIS_PREFIX):
            return
        if new:
            self._add(name, new)
        elif old in self.players:
            del self.players[old]
            self.app.refresh()

    def _add(self, name, owner=None):
        if owner:
            self._add_known(name, owner)
            return

        def got(bus, res, *rest):
            try:
                self._add_known(name, bus.call_finish(res).unpack()[0])
            except GLib.Error:
                pass
        self.bus.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "GetNameOwner",
                      GLib.Variant("(s)", (name,)), GLib.VariantType("(s)"), Gio.DBusCallFlags.NONE, 2000, None, got)

    def _add_known(self, name, owner):
        if owner in self.players:
            return
        p = Player(name, owner)
        self.players[owner] = p

        def got(bus, res, *rest):
            try:
                props = bus.call_finish(res).unpack()[0]
            except GLib.Error:
                return
            changed = p.apply(props)
            p.known = True
            self.app.on_player(p, changed, initial=True)
        self.bus.call(owner, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "GetAll",
                      GLib.Variant("(s)", (PLAYER_IFACE,)), GLib.VariantType("(a{sv})"), Gio.DBusCallFlags.NONE, 2000, None, got)

    def _on_props(self, conn, sender, path, iface, sig, params, *rest):
        iface_name, changed, _ = params.unpack()
        if iface_name != PLAYER_IFACE:
            return
        p = self.players.get(sender)
        if p is None or not p.known:
            return
        self.app.on_player(p, p.apply(changed), initial=False)

    def call(self, p, method, params=None):
        if self.bus is None or p.owner.startswith(":demo"):
            return
        self.bus.call(p.owner, "/org/mpris/MediaPlayer2", PLAYER_IFACE, method, params, None, Gio.DBusCallFlags.NONE, 2000, None, None)

    def raise_player(self, p):
        if self.bus is None or p.owner.startswith(":demo"):
            return
        self.bus.call(p.owner, "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2", "Raise", None, None, Gio.DBusCallFlags.NONE, 2000, None, None)

    def seek_to(self, p, frac):
        if p.length <= 0:
            return
        us = int(frac * p.length)
        p.set_position(us)
        if re.fullmatch(r"/([A-Za-z0-9_]+(/[A-Za-z0-9_]+)*)?", p.trackid or ""):
            self.call(p, "SetPosition", GLib.Variant("(ox)", (p.trackid, us)))

    def poll_position(self, p):
        if self.bus is None or p.owner.startswith(":demo"):
            return

        def got(bus, res, *rest):
            try:
                v = bus.call_finish(res).unpack()[0]
                p.set_position(int(v))
            except (GLib.Error, TypeError, ValueError):
                pass
        self.bus.call(p.owner, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "Get",
                      GLib.Variant("(ss)", (PLAYER_IFACE, "Position")), GLib.VariantType("(v)"), Gio.DBusCallFlags.NONE, 800, None, got)


# ------------------------------------------------------------------------------------------------------------------ the program

class App(Gtk.Application):
    def __init__(self, cfg, demo=False, layer_shell=True):
        super().__init__(application_id="dev.hearth.glimmer.linux")
        self.cfg = cfg
        self.demo = demo
        self.use_layer = layer_shell
        self.island = Island(cfg)
        self.W, self.H = window_size(cfg["scale"])
        self.win = None
        self.area = None
        self.surface = None
        self.layered = False
        self.mpris = Mpris(self)
        self.transient = None
        self.pending = []
        self._expire_id = 0
        self.hover_open = False
        self.pinned = False
        self._leave_id = 0
        self._dwell_id = 0
        self._inside = False
        self._tick_id = 0
        self._last_frame = None
        self._last_draw = 0.0
        self._rect = None
        self._pos_timer = 0
        self._power = None
        self.t0 = time.monotonic()

    # --- window

    def do_activate(self):
        if self.win is not None:
            return
        display = Gdk.Display.get_default()
        css = Gtk.CssProvider()
        text = ".glimmer-window, .glimmer-window > * { background: transparent; box-shadow: none; border: none; }"
        try:
            css.load_from_string(text)
        except AttributeError:
            css.load_from_data(text.encode(), -1)
        Gtk.StyleContext.add_provider_for_display(display, css, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)

        win = Gtk.Window(application=self)
        win.set_title("Glimmer")
        win.set_decorated(False)
        win.set_resizable(False)
        win.set_default_size(self.W, self.H)
        win.add_css_class("glimmer-window")
        self.win = win
        self._init_layer_shell(win, display)

        area = Gtk.DrawingArea()
        area.set_content_width(self.W)
        area.set_content_height(self.H)
        area.set_draw_func(self.on_draw)
        win.set_child(area)
        self.area = area

        motion = Gtk.EventControllerMotion()
        motion.connect("enter", self.on_enter)
        motion.connect("leave", self.on_leave)
        area.add_controller(motion)
        left = Gtk.GestureClick()
        left.set_button(1)
        left.connect("pressed", self.on_click)
        area.add_controller(left)
        right = Gtk.GestureClick()
        right.set_button(3)
        right.connect("pressed", self.on_right_click)
        area.add_controller(right)

        win.connect("map", self.on_map)
        win.present()
        self.start_sources()

    def _init_layer_shell(self, win, display):
        if not self.use_layer or LayerShell is None:
            print("Glimmer: gtk4-layer-shell fehlt – ich laufe als normales Fenster (nur zum Ausprobieren).", file=sys.stderr)
            return
        try:
            if hasattr(LayerShell, "is_supported") and not LayerShell.is_supported():
                print("Glimmer: dieser Desktop kennt kein Layer-Shell (GNOME?) – ich laufe als normales Fenster.", file=sys.stderr)
                return
            LayerShell.init_for_window(win)
            LayerShell.set_namespace(win, "glimmer")
            LayerShell.set_layer(win, LayerShell.Layer.TOP if self.cfg["layer"] == "top" else LayerShell.Layer.OVERLAY)
            LayerShell.set_anchor(win, LayerShell.Edge.TOP, True)
            LayerShell.set_keyboard_mode(win, LayerShell.KeyboardMode.NONE)
            LayerShell.set_exclusive_zone(win, -1)
            monitors = display.get_monitors()
            idx = int(self.cfg["monitor"])
            if 0 <= idx < monitors.get_n_items():
                LayerShell.set_monitor(win, monitors.get_item(idx))
            self.layered = True
        except Exception as e:  # noqa: BLE001
            print("Glimmer: Layer-Shell ließ sich nicht einrichten (%s) – normales Fenster." % e, file=sys.stderr)

    def on_map(self, win):
        self.surface = win.get_surface()
        self.update_input(force=True)
        # (GTK may set an input region of its own when the window first appears – we take it back, so that the empty part of
        # the window never swallows clicks meant for what is underneath.)
        for ms in (300, 1200, 3000):
            GLib.timeout_add(ms, lambda: (self.update_input(force=True), False)[1])

    def update_input(self, force=False):
        if self.surface is None:
            return
        sensor = self.cfg["hover_reveal"] and self.cfg["music"] and self.active() is not None and self.layered
        rect = self.island.input_rect(self.W, sensor)
        if rect == self._rect and not force:
            return
        self._rect = rect
        if rect is None:
            region = cairo.Region()
        else:
            region = cairo.Region(cairo.RectangleInt(rect[0], rect[1], max(rect[2], 1), max(rect[3], 1)))
        try:
            self.surface.set_input_region(region)
        except Exception as e:  # noqa: BLE001
            log("input region:", e)

    # --- frames

    def on_draw(self, area, cr, w, h, *rest):
        self.island.draw(cr, self.W, self.H, time.monotonic() - self.t0)

    def kick(self):
        if not self._tick_id and self.area is not None:
            self._last_frame = None
            self._tick_id = self.area.add_tick_callback(self.on_tick)

    def on_tick(self, widget, clock, *rest):
        now = clock.get_frame_time() / 1e6
        dt = min(now - self._last_frame, 0.05) if self._last_frame else 1 / 60
        self._last_frame = now
        moved, ambient = self.island.step(dt)
        if moved or now - self._last_draw >= 1 / 24:
            self._last_draw = now
            self.area.queue_draw()
        self.update_input()
        if not moved and not ambient:
            self.area.queue_draw()
            self._tick_id = 0
            self._last_frame = None
            return GLib.SOURCE_REMOVE
        return GLib.SOURCE_CONTINUE

    # --- what the island shows

    def active(self):
        players = [p for p in self.mpris.players.values()
                   if (p.known or p.owner.startswith(":demo")) and (p.title or p.status != "Stopped")]
        playing = [p for p in players if p.status == "Playing"]
        if playing:
            return max(playing, key=lambda p: p.last_active)
        recent = [p for p in players if p.last_active > 0]
        return max(recent, key=lambda p: p.last_active) if recent else (players[0] if players else None)

    def music_visible(self):
        if not self.cfg["music"] or not self.cfg["music_persistent"]:
            return False
        p = self.active()
        if p is None:
            return False
        if p.status == "Playing":
            return True
        return p.status == "Paused" and time.monotonic() - p.last_change < 4.0

    def refresh(self):
        isl = self.island
        p = self.active()
        if self.transient is not None:
            t = self.transient
            isl.show(t["kind"], t["data"], False)
        elif (self.hover_open or self.pinned) and p is not None and self.cfg["music"]:
            isl.show("music", p, True)
            self._start_pos_timer()
        elif self.music_visible():
            isl.show("music", p, False)
        else:
            isl.hide()
        self.update_input()
        self.kick()

    def _start_pos_timer(self):
        if self._pos_timer:
            return
        p = self.active()
        if p is not None:
            self.mpris.poll_position(p)

        def tick():
            p2 = self.active()
            if not (self.island.kind == "music" and self.island.open and p2 is not None):
                self._pos_timer = 0
                return False
            self.mpris.poll_position(p2)
            return True
        self._pos_timer = GLib.timeout_add(1000, tick)

    def post(self, kind, data, duration, prio):
        now = time.monotonic()
        cur = self.transient
        ev = {"kind": kind, "data": data, "dur": duration, "prio": prio, "until": now + duration, "born": now}
        if cur is None or cur["until"] <= now:
            self._start(ev)
        elif cur["kind"] == kind:
            if kind in ("volume", "power"):
                cur["data"].update(data)
            else:
                cur["data"] = data
            cur["until"] = ev["until"]
            self._arm()
            self.refresh()
        elif prio >= cur["prio"]:
            self._start(ev)
        else:
            self.pending.append(ev)
            del self.pending[:-4]

    def _start(self, ev):
        self.transient = ev
        self._arm()
        self.refresh()

    def _arm(self):
        if self._expire_id:
            GLib.source_remove(self._expire_id)
        ms = max(50, int((self.transient["until"] - time.monotonic()) * 1000))
        self._expire_id = GLib.timeout_add(ms, self._expire)

    def _expire(self):
        self._expire_id = 0
        now = time.monotonic()
        if self.transient and self.transient["until"] > now + 0.05:
            self._arm()
            return False
        self.transient = None
        while self.pending:
            ev = self.pending.pop(0)
            if now - ev["born"] < 8:
                ev["until"] = now + ev["dur"]
                self._start(ev)
                return False
        self.refresh()
        return False

    def dismiss(self):
        if self._expire_id:
            GLib.source_remove(self._expire_id)
            self._expire_id = 0
        self.transient = None
        self.pending.clear()
        self.pinned = False
        self.hover_open = False
        self.refresh()

    # --- mouse

    def on_enter(self, ctrl, x, y, *rest):
        self._inside = True
        if self._leave_id:
            GLib.source_remove(self._leave_id)
            self._leave_id = 0
        if self.transient is not None or not self.cfg["music"] or self.active() is None:
            return
        if self.island.kind is None:
            # Only the thin strip at the screen's edge is there: the island opens once the pointer stays a moment,
            # so that passing by on the way to a window's title bar does not open it.
            if not self._dwell_id:
                self._dwell_id = GLib.timeout_add(320, self._dwell)
            return
        self.hover_open = True
        self.refresh()

    def _dwell(self):
        self._dwell_id = 0
        if self._inside and self.transient is None and self.active() is not None:
            self.hover_open = True
            self.refresh()
        return False

    def on_leave(self, ctrl, *rest):
        self._inside = False
        if self._dwell_id:
            GLib.source_remove(self._dwell_id)
            self._dwell_id = 0
        if self._leave_id:
            GLib.source_remove(self._leave_id)

        def later():
            self._leave_id = 0
            if not self.pinned:
                self.hover_open = False
                self.refresh()
            return False
        self._leave_id = GLib.timeout_add(450, later)

    def on_click(self, gesture, n, x, y, *rest):
        isl = self.island
        p = self.active()
        act = isl.hit(x, y)
        if act and p is not None:
            action, value = act
            if action == "prev":
                self.mpris.call(p, "Previous")
            elif action == "next":
                self.mpris.call(p, "Next")
            elif action == "playpause":
                self.mpris.call(p, "PlayPause")
                if p.owner.startswith(":demo"):
                    p.apply({"PlaybackStatus": "Paused" if p.status == "Playing" else "Playing"})
                    self.refresh()
            elif action == "raise":
                self.mpris.raise_player(p)
            elif action == "seek":
                self.mpris.seek_to(p, value)
            self.kick()
            return
        if isl.kind == "music" and p is not None:
            self.pinned = not (self.pinned and isl.open)
            self.hover_open = True
            self.refresh()
        else:
            self.dismiss()

    def on_right_click(self, gesture, n, x, y, *rest):
        self.dismiss()

    # --- events

    def on_player(self, p, changed, initial=False):
        if "art" in changed and p.art_url:
            url = p.art_url

            def done(surf, accent):
                if p.art_url == url:
                    p.art, p.accent = surf, accent
                    self.kick()
                    if self.area is not None:
                        self.area.queue_draw()
                return False
            load_art_async(url, done)
        if "status" in changed and p.status == "Paused":
            GLib.timeout_add(4200, lambda: (self.refresh(), False)[1])
        if ("track" in changed and not initial and p is self.active() and p.status == "Playing" and p.title
                and self.cfg["music"] and self.cfg["track_popups"] and not (self.island.kind == "music" and self.island.open)):
            self.post("track", p, 3.6, 1)
            return
        self.refresh()

    def on_volume(self, pct, muted):
        if self.cfg["volume"]:
            self.post("volume", {"pct": pct, "muted": muted}, 1.7, 1)
        return False

    def on_power(self, info):
        if not self.cfg["charging"] and not self.cfg["low_battery"]:
            return False
        prev, self._power = self._power, info
        if info is None or prev is None:
            return False
        pct = info["pct"]
        if self.cfg["charging"]:
            if info["plugged"] and not prev["plugged"]:
                self.post("power", {"state": "full" if info["full"] else "charging", "pct": pct}, 2.6, 2)
                return False
            if not info["plugged"] and prev["plugged"]:
                self.post("power", {"state": "unplugged", "pct": pct}, 1.8, 2)
                return False
            if info["plugged"] and info["full"] and not prev["full"]:
                self.post("power", {"state": "full", "pct": pct}, 2.6, 2)
                return False
        if self.cfg["low_battery"] and not info["plugged"]:
            for limit in (10, 20):
                if pct <= limit < prev["pct"]:
                    self.post("power", {"state": "low", "pct": pct}, 3.4, 3)
                    break
        return False

    def on_notification(self, body):
        if not self.cfg["notifications"]:
            return False
        try:
            app, _rid, icon, summary, text, _actions, hints, timeout = body
        except (TypeError, ValueError):
            return False
        if "x-canonical-private-synchronous" in hints or (not summary and not text):
            return False
        text = html.unescape(re.sub(r"<[^>]+>", "", text or "")).strip()
        dur = clamp(timeout / 1000.0, 3.5, 8.0) if timeout and timeout > 0 else 5.0
        self.post("notify", {"app": app, "summary": summary, "body": text, "icon": self.load_icon(icon)}, dur, 3)
        return False

    def load_icon(self, name):
        if not name:
            return None
        try:
            path = None
            if name.startswith("file://"):
                path = GLib.filename_from_uri(name)[0]
            elif name.startswith("/"):
                path = name
            else:
                theme = Gtk.IconTheme.get_for_display(Gdk.Display.get_default())
                paintable = theme.lookup_icon(name, None, 48, self.win.get_scale_factor() if self.win else 1, Gtk.TextDirection.NONE, 0)
                f = paintable.get_file() if paintable is not None else None
                path = f.get_path() if f is not None else None
            if not path:
                return None
            pb = GdkPixbuf.Pixbuf.new_from_file_at_size(path, 72, 72)
            return pixbuf_to_surface(pb)[0]
        except Exception as e:  # noqa: BLE001
            log("icon:", name, e)
            return None

    # --- the sources of events

    def start_sources(self):
        if self.cfg["music"] or self.demo:
            self.mpris.start()
        if self.cfg["volume"]:
            threading.Thread(target=self._volume_loop, daemon=True).start()
        if self.cfg["charging"] or self.cfg["low_battery"]:
            self._power_tick()
            GLib.timeout_add_seconds(2, self._power_tick)
        if self.cfg["notifications"]:
            self._start_notifications()
        if self.demo:
            self._run_demo()

    def _volume_loop(self):
        pactl, wpctl = shutil.which("pactl"), shutil.which("wpctl")
        if not pactl and not wpctl:
            log("weder pactl noch wpctl – keine Lautstärke-Anzeige")
            return

        def run(cmd):
            try:
                return subprocess.run(cmd, capture_output=True, text=True, timeout=2, env=CLEAN_ENV).stdout
            except (OSError, subprocess.SubprocessError):
                return ""

        def read():
            if pactl:
                m = re.search(r"(\d+)%", run([pactl, "get-sink-volume", "@DEFAULT_SINK@"]))
                muted = "yes" in run([pactl, "get-sink-mute", "@DEFAULT_SINK@"]).lower()
                return (int(m.group(1)), muted) if m else None
            out = run([wpctl, "get-volume", "@DEFAULT_AUDIO_SINK@"])
            m = re.search(r"([0-9.]+)", out)
            return (int(round(float(m.group(1)) * 100)), "MUTED" in out) if m else None

        last = read()
        while True:
            if pactl:
                try:
                    proc = subprocess.Popen([pactl, "subscribe"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, env=CLEAN_ENV)
                    for line in proc.stdout:
                        if re.search(r"'change' on (sink|server)", line):
                            cur = read()
                            if cur and cur != last:
                                if last is not None:
                                    GLib.idle_add(self.on_volume, *cur)
                                last = cur
                            time.sleep(0.03)
                    proc.wait()
                except OSError:
                    pass
                time.sleep(2.0)
            else:
                time.sleep(0.35)
                cur = read()
                if cur and cur != last:
                    if last is not None:
                        GLib.idle_add(self.on_volume, *cur)
                    last = cur

    def _power_tick(self):
        GLib.idle_add(self.on_power, read_power())
        return True

    def _start_notifications(self):
        """Listens to the notifications other programs send (the bus's monitor mode: we only read, nothing is taken away)."""
        try:
            addr = Gio.dbus_address_get_for_bus_sync(Gio.BusType.SESSION, None)
            conn = Gio.DBusConnection.new_for_address_sync(
                addr, Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT | Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION, None, None)
            conn.add_filter(self._notification_filter)
            rule = "type='method_call',interface='org.freedesktop.Notifications',member='Notify'"
            conn.call_sync("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.Monitoring", "BecomeMonitor",
                           GLib.Variant("(asu)", ([rule], 0)), None, Gio.DBusCallFlags.NONE, 3000, None)
            self._notif_conn = conn
        except Exception as e:  # noqa: BLE001
            print("Glimmer: Benachrichtigungen lassen sich nicht mitlesen (%s)." % e, file=sys.stderr)

    def _notification_filter(self, conn, msg, incoming, *rest):
        # Method calls are what we read – and are then dropped (None): a monitor must never answer one, the bus would cut it off.
        # Replies and signals (the answer to BecomeMonitor itself) pass through.
        try:
            if incoming and msg.get_message_type() == Gio.DBusMessageType.METHOD_CALL:
                if msg.get_interface() == "org.freedesktop.Notifications" and msg.get_member() == "Notify":
                    GLib.idle_add(self.on_notification, msg.get_body().unpack())
                return None
        except Exception as e:  # noqa: BLE001
            log("filter:", e)
            return None
        return msg

    # --- demo

    def _run_demo(self):
        p = demo_player(self.cfg)
        self.mpris.players[":demo"] = p
        self.refresh()
        steps = [
            (3.0, lambda: self.post("track", p, 3.6, 1)),
            (7.0, lambda: [GLib.timeout_add(int(i * 380), (lambda v: lambda: (self.on_volume(v, False), False)[1])(35 + i * 12)) for i in range(4)]),
            (10.5, lambda: self.post("power", {"state": "charging", "pct": 78}, 2.6, 2)),
            (14.0, lambda: self.post("notify", {"app": "Signal", "summary": "Mia", "body": "Kommst du später noch vorbei? Ich bring Kuchen mit.", "icon": None}, 4.5, 3)),
            (19.0, lambda: self.post("power", {"state": "low", "pct": 19}, 3.4, 3)),
            (23.0, lambda: setattr(self, "pinned", True) or self.refresh()),
            (28.0, lambda: setattr(self, "pinned", False) or setattr(self, "hover_open", False) or self.refresh()),
        ]
        for delay, fn in steps:
            GLib.timeout_add(int(delay * 1000), (lambda f: lambda: (f(), False)[1])(fn))
        GLib.timeout_add(32000, lambda: (self._run_demo_again(), False)[1])

    def _run_demo_again(self):
        self._run_demo()


def read_power():
    """The battery and the power supply from /sys/class/power_supply: {pct, plugged, full} or None (a desktop PC has none)."""
    base = os.environ.get("GLIMMER_POWER_DIR", "/sys/class/power_supply")

    def rd(path):
        try:
            with open(path, "r") as f:
                return f.read().strip()
        except OSError:
            return ""

    try:
        names = sorted(os.listdir(base))
    except OSError:
        return None
    caps, statuses, mains, online = [], [], False, False
    for n in names:
        d = os.path.join(base, n)
        kind = rd(os.path.join(d, "type"))
        if kind == "Battery":
            if rd(os.path.join(d, "scope")) == "Device":
                continue  # a mouse's or headset's battery
            try:
                caps.append(int(rd(os.path.join(d, "capacity"))))
            except ValueError:
                continue
            statuses.append(rd(os.path.join(d, "status")))
        elif kind in ("Mains", "USB", "USB_C", "USB_PD", "USB_PD_DRP", "USB_DCP", "USB_CDP", "Wireless"):
            mains = True
            online = online or rd(os.path.join(d, "online")) == "1"
    if not caps:
        return None
    pct = int(round(sum(caps) / len(caps)))
    plugged = online if mains else any(s in ("Charging", "Full") for s in statuses)
    full = any(s == "Full" for s in statuses) or (plugged and pct >= 100)
    return {"pct": pct, "plugged": plugged, "full": full}


# ------------------------------------------------------------------------------------------------------------------ demo and snapshot

def demo_art():
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, 120, 120)
    cr = cairo.Context(surf)
    g = cairo.LinearGradient(0, 0, 120, 120)
    g.add_color_stop_rgb(0, 0.23, 0.06, 0.35)
    g.add_color_stop_rgb(1, 0.95, 0.35, 0.45)
    cr.set_source(g)
    cr.paint()
    rg = cairo.RadialGradient(78, 40, 4, 78, 40, 50)
    rg.add_color_stop_rgb(0, 1.0, 0.82, 0.55)
    rg.add_color_stop_rgb(1, 1.0, 0.45, 0.55)
    cr.set_source(rg)
    cr.arc(78, 40, 26, 0, 2 * math.pi)
    cr.fill()
    cr.set_source_rgba(0.1, 0.02, 0.2, 0.55)
    cr.arc(34, 108, 58, 0, 2 * math.pi)
    cr.fill()
    return surf


def demo_player(cfg):
    p = Player("org.mpris.MediaPlayer2.demo", ":demo")
    p.title, p.artist, p.status = "Meridian", "ClaudeOS", "Playing"
    p.length = 214_000_000
    p.set_position(83_000_000)
    p.art, p.accent = demo_art(), (1.0, 0.47, 0.56)
    p.known, p.last_active, p.last_change = True, time.monotonic(), time.monotonic()
    return p


def render_snapshot(cfg, path):
    """Draws every state of the island, in both modes, into one picture – no window, no display needed."""
    scenes = [
        ("Musik, klein", "music", False),
        ("Musik, geöffnet", "music", True),
        ("Titelwechsel", "track", False),
        ("Lautstärke", "volume", False),
        ("Stumm", "volume", False),
        ("Lädt", "power", False),
        ("Voll", "power", False),
        ("Akku schwach", "power", False),
        ("Benachrichtigung", "notify", False),
        ("Benachrichtigung, kurz", "notify", False),
    ]
    s = cfg["scale"]
    W, H = window_size(s)
    pad = int(14 * s)
    cols = 2
    rows = len(scenes)
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, cols * W + (cols + 1) * pad, rows * (H - int(SHADOW * s) + 16) + pad, )
    cr = cairo.Context(surf)
    cr.set_source_rgb(0.08, 0.08, 0.1)
    cr.paint()
    player = demo_player(cfg)
    cell_h = H - int(SHADOW * s) + 16
    for row, (label, kind, open_) in enumerate(scenes):
        for col, mode in enumerate(("notch", "island")):
            c2 = dict(cfg, mode=mode)
            isl = Island(c2)
            data = {
                "Musik, klein": player, "Musik, geöffnet": player, "Titelwechsel": player,
                "Lautstärke": {"pct": 64, "muted": False}, "Stumm": {"pct": 64, "muted": True},
                "Lädt": {"state": "charging", "pct": 78}, "Voll": {"state": "full", "pct": 100},
                "Akku schwach": {"state": "low", "pct": 14},
                "Benachrichtigung": {"app": "Signal", "summary": "Mia", "body": "Kommst du später noch vorbei? Ich bring Kuchen mit.", "icon": None},
                "Benachrichtigung, kurz": {"app": "Kalender", "summary": "Zahnarzt in 10 Minuten", "body": "", "icon": None},
            }[label]
            isl.show(kind, data, open_)
            isl.snap()
            x = pad + col * (W + pad)
            y = pad // 2 + row * cell_h
            cr.save()
            cr.translate(x, y)
            cr.rectangle(0, 0, W, cell_h - 4)
            cr.clip()
            g = cairo.LinearGradient(0, 0, W, cell_h)
            g.add_color_stop_rgb(0, 0.16, 0.26, 0.46)
            g.add_color_stop_rgb(1, 0.46, 0.22, 0.52)
            cr.set_source(g)
            cr.paint()
            isl.draw(cr, W, H, 1.3)
            draw_text(cr, "%s · %s" % (label, mode), 8, cell_h - 14, 10.5, (1, 1, 1, 0.7))
            cr.restore()
    surf.write_to_png(path)
    print("Bild geschrieben:", path)


def main():
    ap = argparse.ArgumentParser(prog="glimmer", description="Glimmer für Linux – die Insel am oberen Bildschirmrand.")
    ap.add_argument("--mode", choices=["notch", "island"], help="Notch (am Rand) oder schwebende Insel")
    ap.add_argument("--scale", type=float, help="Größe, 1.0 ist normal")
    ap.add_argument("--config", help="Pfad der Einstellungen (Standard: ~/.config/glimmer/config.json)")
    ap.add_argument("--demo", action="store_true", help="zeigt der Reihe nach alle Ereignisse")
    ap.add_argument("--snapshot", metavar="BILD.png", help="zeichnet alle Zustände in ein Bild und beendet sich")
    ap.add_argument("--no-layer-shell", action="store_true", help="als normales Fenster laufen (Test)")
    ap.add_argument("--version", action="version", version="Glimmer für Linux " + APP_VERSION)
    args = ap.parse_args()

    cfg = load_config(args.config)
    if args.mode:
        cfg["mode"] = args.mode
    if args.scale:
        cfg["scale"] = max(0.6, min(2.5, args.scale))
    set_language(cfg["language"])

    if args.snapshot:
        render_snapshot(cfg, args.snapshot)
        return 0

    app = App(cfg, demo=args.demo, layer_shell=not args.no_layer_shell)
    GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGINT, lambda *a: (app.quit(), False)[1])
    GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGTERM, lambda *a: (app.quit(), False)[1])
    return app.run([sys.argv[0]])


if __name__ == "__main__":
    sys.exit(main())
