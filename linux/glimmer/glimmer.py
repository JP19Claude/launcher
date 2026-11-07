#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
Glimmer für Linux – die Notch von ZENITH, als echtes Programm für den Desktop.

Dieselbe Notch wie die MacBook-Notch in der Glimmer-App von ZENITH: schwarz, am oberen Bildschirmrand angewachsen, immer da – und sie wächst
bei Ereignissen daraus heraus: Musik (jeder MPRIS-Player: Spotify, Firefox, VLC, mpv …), Laden / Akku, Lautstärke, Benachrichtigungen.
Tippen klappt sie auf und zu, nach unten / oben ziehen auch, seitlich ziehen springt bei Musik zum nächsten Titel.

Gebaut für Wayland-Desktops mit Layer-Shell (KDE Plasma, Hyprland, Sway, labwc …): GTK 4 + gtk4-layer-shell, gezeichnet mit Cairo.
GNOME kennt Layer-Shell nicht – dort läuft Glimmer nur als normales Fenster.

    glimmer                 starten (läuft es schon: öffnet das Einstellungsfenster)
    glimmer --settings      mit dem Einstellungsfenster starten
    glimmer --demo          zeigt der Reihe nach alle Ereignisse (zum Ausprobieren)
    glimmer --mode island   die kleine Insel statt der Notch;   --scale 1.3  größer
    glimmer --snapshot x.png  zeichnet alle Zustände in ein Bild (braucht kein Fenster)

Einstellungen: ~/.config/glimmer/config.json (oder im Einstellungsfenster)
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
# Glimmer draws everything itself; the built-in theme keeps a broken system theme from filling the terminal with warnings.
os.environ.setdefault("GTK_THEME", "Adwaita:dark")

import argparse  # noqa: E402
import html  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402
import random  # noqa: E402
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

import warnings  # noqa: E402


def _signal_add(sig, handler):
    """Calls handler(…) in the main loop when the signal arrives (the older GLib function – it works everywhere, only the newest ones warn)."""
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        return GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, sig, handler)

FONT_FAMILY = "Roboto,Inter,Noto Sans,Cantarell,Sans"   # (ZENITH's own font first; Pango takes the first one the system has)
DEBUG = bool(os.environ.get("GLIMMER_DEBUG"))
# Programs we start must not inherit the preload (it would load GTK into every pactl call).
CLEAN_ENV = {k: v for k, v in os.environ.items() if k != "LD_PRELOAD"}
CLEAN_ENV["LC_ALL"] = "C"


def log(*a):
    if DEBUG:
        print("[glimmer]", *a, file=sys.stderr, flush=True)


# ------------------------------------------------------------------------------------------------------------------ settings

DEFAULTS = {
    # Glimmer
    "enabled": True,
    "mode": "notch",            # "notch": MacBook-Notch · "island": MacBook-Insel · "glimmer": die Pille wie auf dem Handy · "dynamic_island": wie beim iPhone
    "style": "black",           # "black" | "glass" (Liquid Glass) | "tinted" (farbig getönt) | "fluid" (AI Fluid)
    "music_style": "hyper",     # "hyper" (Hyper Island) | "classic" | "fluid_cloud" (OPPO/OnePlus)
    "idle_pill": True,          # die kleine Pille, wenn nichts läuft (bei der Notch immer da)
    "idle_clock": False,        # in der leeren Notch/Pille die Uhrzeit
    "dynamic": False,           # Aussehen und Bedienung der Dynamic Island des iPhones auf der Notch ("Dynamic Notch") oder der MacBook-Insel
    # Clawd
    "clawd": False,
    "clawd_idle": True,
    "clawd_side": "left",
    "clawd_spot": 0.55,
    "clawd_size": 1.0,
    "clawd_skin": "terracotta",
    "clawd_greeting": True,
    "clawd_greet_daily": False,
    # Gestaltung
    "glow": True,
    "glow_color": "activity",   # "activity" | "accent" | "white" | "rainbow"
    "ai_fluid": True,
    "breathe": False,
    "outline": "off",           # "off" | "subtle" | "colored"
    "motion": "normal",         # "calm" | "normal" | "playful"
    # Größe & Position
    "small": False,             # 85 %
    "scale": 1.0,
    "width": 1.0,
    "notch_width": 190,         # Größe der Notch in dp (wie in ZENITH)
    "notch_height": 34,
    "island_width": 128,
    "island_height": 32,
    "offset_x": 0,
    "offset_y": 0,
    "expand_width": 1.0,        # wie breit die aufgeklappte Insel/Notch ist (gegenüber der üblichen Größe)
    "expand_height": 1.0,       # und wie hoch
    "active_width": 1.0,        # wie breit die kleine Insel ist, solange etwas läuft (Musik mit Cover und Wellen, Laden …)
    # Bedienung
    "tap_opens": False,
    "double_tap": "off",        # "off" | "play_pause" | "oracle" | "coin" | "dice"
    "auto_collapse": 9,         # aufgeklappt nach so vielen Sekunden wieder zu (0: bleibt)
    "swipe_tracks": True,
    "hover_reveal": False,      # Maus auf der Notch klappt die Musik auf
    # Was Glimmer zeigt
    "music": True,
    "music_persistent": True,
    "notifications": True,
    "codes": True,
    "screenshots": True,
    "privacy": True,
    "alerts": True,
    "charging": True,           # beim Anstecken kurz
    "charging_live": False,     # solange es lädt
    "low_battery": True,
    "volume": True,
    # Linux
    "monitor": 0,               # Nummer des Bildschirms
    "layer": "overlay",         # "overlay": auch über Vollbild · "top"
    "language": "auto",         # "auto" | "de" | "en"
    "accent": "#FF7A9C",
}

CHOICES = {
    "mode": ["notch", "island", "glimmer", "dynamic_island"],
    "style": ["black", "glass", "tinted", "fluid"],
    "music_style": ["hyper", "classic", "fluid_cloud"],
    "glow_color": ["activity", "accent", "white", "rainbow"],
    "outline": ["off", "subtle", "colored"],
    "motion": ["calm", "normal", "playful"],
    "double_tap": ["off", "play_pause", "oracle", "coin", "dice"],
    "clawd_side": ["left", "right"],
    "clawd_skin": ["terracotta", "ocean", "mint", "lavender", "gold", "rose", "snow", "midnight", "rainbow", "galaxy"],
    "layer": ["overlay", "top"],
    "language": ["auto", "de", "en"],
}
RANGES = {
    "scale": (0.6, 2.5), "width": (0.8, 1.3), "clawd_spot": (0.0, 1.0), "clawd_size": (0.7, 1.4),
    "notch_width": (80, 420), "notch_height": (16, 90), "island_width": (60, 420), "island_height": (16, 90),
    "offset_x": (-40, 40), "offset_y": (-10, 40), "auto_collapse": (0, 120), "monitor": (0, 16),
    "expand_width": (0.8, 1.3), "expand_height": (0.8, 1.5), "active_width": (0.7, 1.6),
}
LINUX_ONLY = ("monitor", "layer", "language")

CONFIG_PATH = os.path.join(os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config"), "glimmer", "config.json")


def load_config(path=None):
    cfg = dict(DEFAULTS)
    path = path or CONFIG_PATH
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):
            if "notch_width" not in data:
                # (A file from before the notch was rebuilt after ZENITH's: those two choices go back to ZENITH's way, once.)
                data.pop("idle", None)
                data.pop("hover_reveal", None)
            if "idle" in data and "idle_pill" not in data:
                # (From the first version: "idle" was "pill", "clock" or "off".)
                data["idle_pill"] = data["idle"] != "off"
                data["idle_clock"] = data["idle"] == "clock"
            for k, v in data.items():
                if k not in cfg:
                    continue
                want = type(DEFAULTS[k])
                if isinstance(DEFAULTS[k], bool):
                    ok = isinstance(v, bool)
                elif isinstance(DEFAULTS[k], float):
                    ok = isinstance(v, (int, float)) and not isinstance(v, bool)
                elif isinstance(DEFAULTS[k], int):
                    ok = isinstance(v, (int, float)) and not isinstance(v, bool)
                else:
                    ok = isinstance(v, want)
                if ok:
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
    return sanitize_config(cfg)


def sanitize_config(cfg):
    """Keeps every setting inside what it can be (a file typed by hand, or an older version's)."""
    for key, allowed in CHOICES.items():
        if cfg[key] not in allowed:
            cfg[key] = DEFAULTS[key]
    for key, (lo, hi) in RANGES.items():
        v = clamp(float(cfg[key]), lo, hi)
        cfg[key] = int(round(v)) if isinstance(DEFAULTS[key], int) else float(v)
    return cfg


def is_dynamic(cfg):
    """Glimmer in the iPhone's way: the Dynamic Island mode, or the "dynamic" choice on the notch or the MacBook island (the shape stays the
    notch's or the island's own; look and handling are the iPhone's – with the notch it is the "Dynamic Notch")."""
    return cfg["mode"] == "dynamic_island" or bool(cfg.get("dynamic")) and cfg["mode"] in ("notch", "island")


def effective_config(cfg):
    """The settings Glimmer actually runs with (ZENITH's forGlimmer): as chosen, or in the notch the plain black one; in the iPhone's way
    (the Dynamic Island mode, or "dynamic" on the notch or the island) black, its proportions, tap opens, none of Glimmer's extras. The own
    choices stay saved."""
    c = dict(cfg)
    if cfg["mode"] == "notch":
        c.update(style="black", idle_pill=True, glow=False, outline="off", width=1.0, breathe=False, ai_fluid=False)
    if is_dynamic(cfg):
        c.update(style="black", music_style="classic", idle_pill=True, glow=False, outline="off", width=1.0, motion="normal", tap_opens=True,
                 double_tap="off", swipe_tracks=False, notifications=False, charging_live=False, codes=False, screenshots=False, breathe=False,
                 ai_fluid=False)
    return c


MODE_NAMES = {"notch": "MacBook-Notch", "island": "MacBook-Insel", "glimmer": "Glimmer (Pille)", "dynamic_island": "Dynamic Island 1:1"}


STRINGS = {
    "de": {"charging": "Lädt", "full": "Vollständig geladen", "unplugged": "Ladekabel getrennt", "low": "Akku schwach",
           "volume": "Lautstärke", "nothing": "Keine Wiedergabe", "muted": "Stumm", "notification": "Benachrichtigung",
           "running": "Glimmer läuft", "hint": "Tippe auf die Notch, um sie aufzuklappen"},
    "en": {"charging": "Charging", "full": "Fully charged", "unplugged": "Unplugged", "low": "Battery low",
           "volume": "Volume", "nothing": "Nothing playing", "muted": "Muted", "notification": "Notification",
           "running": "Glimmer is running", "hint": "Click the notch to unfold it"},
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

# The sizes are those of the Android Glimmer in ZENITH (in dp; the setting "scale" scales all of it).
EAR = 7.0                    # the notch's shoulders, where it meets the screen's edge
ISLAND_GAP = 6.0             # how far the floating MacBook island hangs below the edge
EXPANDED_TOP = 14.0          # where unfolded content starts (plus the room below the camera)
CAMERA_MARGIN = 5.0
SCREEN_W = 412.0             # the width a phone's island is sized for
ISLAND_H = 30.0              # the pill's height
ROOMS = {"music": 84, "power": 96, "volume": 96, "alert": 96, "charging": 96, "code": 116, "notify": 72, "screenshot": 72}   # beside the notch
BODY_HEIGHT = {"music": 178, "notify": 124, "power": 96, "volume": 96, "alert": 96, "charging": 96, "code": 112, "screenshot": 132}   # unfolded


def window_size(scale):
    """The window holds the biggest island there can be, moved as far as it can be moved, and its glow."""
    w = (520 + 2 * (40 + 22)) * scale                # (the widest unfolded island: 396 dp × 1.3)
    h = (46 + 27 + 330 + 24) * scale                 # (the tallest: a long notification at 150 %)
    return int(math.ceil(w)), int(math.ceil(h))


CLAWD_ORANGE = (0.851, 0.467, 0.341)
ORACLE = ["Ja, auf jeden Fall!", "Meine Pixel sagen: ja.", "Sieht gut aus!", "Ohne Zweifel.", "Frag mich später nochmal.", "Hmm … schwer zu sagen.",
          "Konzentrier dich und frag nochmal.", "Eher nicht.", "Meine Antwort ist nein.", "Darauf würde ich nicht wetten.", "Absolut!",
          "Vielleicht – wenn du ganz fest dran glaubst.", "Die Zeichen stehen auf ja.", "Lieber nicht heute.", "Ja – aber erst nach einem Snack.",
          "42. Äh, ich meine: ja."]


CODE_WORDS = re.compile(r"(code|otp|\bpin\b|\btan\b|verif|bestätig|sicherheit|passwort|kennwort|einmal|login|anmeld|2fa|authent|confirm|token)", re.I)


def extract_code(text):
    """A one-time code in a message ("Dein Code ist 482913"): digits, only when the message talks about a code."""
    if not CODE_WORDS.search(text):
        return None
    m = re.search(r"(?<![\d.,:])(\d{3}[ -]\d{3}|\d{4,8})(?!\d|[.,:]\d)", text)  # (a full stop after it ends the sentence; "12.50" is a price)
    return re.sub(r"[ -]", "", m.group(1)) if m else None


def clawd_hello(hour, first_today):
    """What Clawd says when Glimmer starts: the first time today a greeting for the time of day, later a varied hello."""
    if first_today:
        if 5 <= hour < 11:
            return "Guten Morgen ☀️"
        if hour < 14:
            return "Guten Tag!"
        if hour < 18:
            return "Schönen Nachmittag!"
        if hour < 23:
            return "Guten Abend 🌙"
        return "Noch wach? 🦉"
    pool = ["Psst … ist schon spät 🌙", "Noch wach? Ich auch!", "Hallo, Nachteule! 🦉"] if (hour >= 23 or hour < 5) else [
        "Hallo! 👋", "Da bist du ja!", "Willkommen zurück!", "Schön, dich zu sehen!", "Hey du!", "Na, was machen wir?",
        "Ich hab auf dich gewartet!", "Hallöchen!"]
    return random.choice(pool)


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
GREEN = (0.204, 0.780, 0.349)    # the colours of ZENITH's Glimmer
ORANGE = (1.0, 0.624, 0.039)
RED = (1.0, 0.271, 0.227)
YELLOW = (1.0, 0.839, 0.039)


def draw_text(cr, text, x, y, size, rgba, bold=False, max_w=None, align="left", spacing=0.0, tnum=False):
    """Text with its vertical centre at y. Returns the width."""
    if not text:
        return 0
    layout = PangoCairo.create_layout(cr)
    fd = Pango.FontDescription()
    fd.set_family(FONT_FAMILY)
    fd.set_weight(Pango.Weight.SEMIBOLD if bold else Pango.Weight.NORMAL)
    fd.set_absolute_size(size * Pango.SCALE)
    layout.set_font_description(fd)
    layout.set_text(text, -1)
    if spacing or tnum:
        attrs = Pango.AttrList()
        if spacing:
            attrs.insert(Pango.attr_letter_spacing_new(int(spacing * Pango.SCALE)))
        if tnum:
            attrs.insert(Pango.attr_font_features_new("tnum=1"))   # (equally wide digits: a running clock does not wobble)
        layout.set_attributes(attrs)
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


_measure = {"cr": None, "cache": {}}


def text_line_count(text, size, max_w, bold=False):
    """How many lines [text] takes wrapped to [max_w] (wrapped the way draw_text_block wraps it)."""
    if not text:
        return 0
    key = (text, size, int(max_w), bold)
    got = _measure["cache"].get(key)
    if got is not None:
        return got
    if _measure["cr"] is None:
        _measure["cr"] = cairo.Context(cairo.ImageSurface(cairo.FORMAT_ARGB32, 1, 1))
    layout = PangoCairo.create_layout(_measure["cr"])
    fd = Pango.FontDescription()
    fd.set_family(FONT_FAMILY)
    fd.set_weight(Pango.Weight.SEMIBOLD if bold else Pango.Weight.NORMAL)
    fd.set_absolute_size(size * Pango.SCALE)
    layout.set_font_description(fd)
    layout.set_text(text, -1)
    layout.set_width(int(max(max_w, 1) * Pango.SCALE))
    layout.set_wrap(Pango.WrapMode.WORD_CHAR)
    n = layout.get_line_count()
    if len(_measure["cache"]) > 200:
        _measure["cache"].clear()
    _measure["cache"][key] = n
    return n


def draw_text_block(cr, text, x, y, size, rgba, max_w, lines=2, bold=False):
    """A few lines of wrapped text starting at y (the top). Returns the height it took."""
    if not text:
        return 0
    layout = PangoCairo.create_layout(cr)
    fd = Pango.FontDescription()
    fd.set_family(FONT_FAMILY)
    fd.set_weight(Pango.Weight.SEMIBOLD if bold else Pango.Weight.NORMAL)
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
    return layout.get_pixel_size()[1]


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
    """A damped spring: the island's size and its little movements work like something physical, not along a fixed curve."""

    def __init__(self, value=0.0, k=520.0, ratio=0.9, eps=0.05):
        self.x = self.target = value
        self.v = 0.0
        self.eps = eps
        self.tune(k, ratio)

    def tune(self, k, ratio):
        self.k = k
        self.c = 2.0 * ratio * math.sqrt(k)

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


def spring_curve(t, k, ratio):
    """How far a spring that starts at rest has come after t seconds: 0 → 1 (past it, if it is not fully damped)."""
    if t <= 0:
        return 0.0
    wn = math.sqrt(k)
    if ratio >= 1.0:
        return 1.0 - math.exp(-wn * t) * (1.0 + wn * t)
    wd = wn * math.sqrt(1.0 - ratio * ratio)
    return 1.0 - math.exp(-ratio * wn * t) * (math.cos(wd * t) + ratio * wn / wd * math.sin(wd * t))


def ease(t):
    """FastOutSlowIn, roughly."""
    t = clamp(t, 0.0, 1.0)
    return 1.0 - (1.0 - t) ** 2.6


def mix(a, b, f):
    return tuple(a[i] + (b[i] - a[i]) * f for i in range(3))


def hexc(text):
    v = int(text, 16)
    return ((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0


WHITE3 = (1.0, 1.0, 1.0)
CLAUDE_COLORS = [hexc("D97757"), hexc("FF6FB5"), hexc("8E6BFF"), hexc("3E91FF"), hexc("FFB494")]   # (ZENITH's AI Fluid)
RAINBOW = [hexc(c) for c in ("FF453A", "FF9F0A", "FFD60A", "30D158", "0A84FF", "BF5AF2", "FF453A")]
CLAWD_SKINS = {
    "terracotta": ("Terrakotta", hexc("D97757")), "ocean": ("Ozean", hexc("5B8DEF")), "mint": ("Minze", hexc("4CB782")),
    "lavender": ("Lavendel", hexc("9B7BE0")), "gold": ("Gold", hexc("E5B53A")), "rose": ("Rosa", hexc("F07AA8")),
    "snow": ("Schnee", hexc("F2F0EA")), "midnight": ("Mitternacht", hexc("5A6A8C")), "rainbow": ("Regenbogen", hexc("D97757")),
    "galaxy": ("Galaxy", hexc("6A4BE0")),
}


def hsv(h, s, v):
    h = (h % 360.0) / 60.0
    i = int(h)
    f = h - i
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    return [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i % 6]


def clawd_color(skin, now):
    clock = (now % 2.4) / 2.4
    if skin == "rainbow":
        return hsv(clock * 360.0, 0.55, 0.95)
    if skin == "galaxy":
        stops = [hexc(c) for c in ("3B3FC4", "6A4BE0", "B04CC8", "4B5FE8", "3B3FC4")]
        x = clock * (len(stops) - 1)
        i = min(int(x), len(stops) - 2)
        return mix(stops[i], stops[i + 1], x - i)
    return CLAWD_SKINS.get(skin, CLAWD_SKINS["terracotta"])[1]


def clawd_asleep(hour):
    return hour >= 23 or hour < 6


def draw_clawd(cr, x, y, w, h, mood, color, now, stars=False):
    """Clawd, Claude's little pixel creature (the same drawing as in ZENITH): a square body, two eye slits, arms and four legs."""
    cols, rows = 14, 9
    clock = (now % 2.4) / 2.4
    phase = clock * 2.0 * math.pi
    blink = False
    bob = leg = arm = spin = hearts = zzz = 0.0
    dizzy = wide = False
    if mood == "idle":
        blink, bob = 0.92 <= clock <= 0.97, math.sin(phase) * 0.25
    elif mood == "thinking":
        blink, bob, leg = 0.92 <= clock <= 0.97, abs(math.sin(phase * 4)) * -0.4, clock * 8
    elif mood == "dance":
        bob, leg, arm = abs(math.sin(phase * 3)) * -1.4, clock * 12, math.sin(phase * 3)
    elif mood == "flip":
        blink, bob, arm, spin = True, -abs(math.sin(phase)) * 1.2, 1.0, clock * 360.0
    elif mood == "love":
        blink, bob, arm, hearts = True, math.sin(phase) * 0.3, 0.5, clock
    elif mood == "wave":
        blink, arm = 0.92 <= clock <= 0.97, math.sin(phase * 4)
    elif mood == "sleep":
        blink, bob, arm, zzz = True, math.sin(phase) * 0.15, -0.4, clock
    elif mood == "jump":
        bob = -abs(math.sin(phase * 2)) * 2.4
        leg = 1.0 if abs(math.sin(phase * 2)) > 0.3 else 0.0
        arm = abs(math.sin(phase * 2))
    elif mood == "dizzy":
        bob, arm, spin, dizzy = math.sin(phase * 2) * 0.3, math.sin(phase * 3) * 0.6, math.sin(phase * 3) * 14.0, True
    elif mood == "surprised":
        bob, arm, wide = -abs(math.sin(phase * 4)) * 0.6, 1.0, True
    px = min(w / cols, h / (rows + 2))
    left = x + (w - px * cols) / 2.0
    top = y + (h - px * rows) / 2.0 + px * 0.5
    cr.save()
    cr.translate(left + cols * px / 2.0, top + rows * px / 2.0 + bob * px)
    cr.rotate(math.radians(spin))
    cr.translate(-(left + cols * px / 2.0), -(top + rows * px / 2.0))

    def cell(c, r, cw=1.0, ch=1.0, dy=0.0, col=None):
        cr.set_source_rgb(*(col or color))
        cr.rectangle(left + c * px, top + (r + dy) * px, cw * px + 0.5, ch * px + 0.5)
        cr.fill()
    cell(2, 0, 10, 7)
    cell(0, 3, 2, 2)
    cell(12, 3, 2, 2, dy=-clamp(arm, -1, 1) * 1.2)
    walking = leg != 0
    first = walking and int(leg) % 2 == 0
    lift_a = 0.5 if (walking and first) else 0.0
    lift_b = 0.5 if (walking and not first) else 0.0
    cell(3, 7, 1, 2, dy=-lift_a)
    cell(5, 7, 1, 2, dy=-lift_b)
    cell(8, 7, 1, 2, dy=-lift_a)
    cell(10, 7, 1, 2, dy=-lift_b)
    eye = (0.106, 0.102, 0.122)
    if dizzy:
        for c in (3.0, 10.0):
            cr.set_source_rgb(*eye)
            cr.rectangle(left + (c - 0.3) * px, top + 1.6 * px, px * 1.6, px * 0.45)
            cr.rectangle(left + (c + 0.28) * px, top + 1.0 * px, px * 0.45, px * 1.6)
            cr.fill()
    elif blink:
        cell(3, 2.1, 1, 0.35, col=eye)
        cell(10, 2.1, 1, 0.35, col=eye)
    elif wide:
        cell(2.85, 0.6, 1.3, 2.6, col=eye)
        cell(9.85, 0.6, 1.3, 2.6, col=eye)
        cr.set_source_rgba(1, 1, 1, 0.8)
        cr.rectangle(left + 3.15 * px, top + 0.9 * px, px * 0.4, px * 0.4)
        cr.rectangle(left + 10.15 * px, top + 0.9 * px, px * 0.4, px * 0.4)
        cr.fill()
    else:
        cell(3, 1, 1, 2, col=eye)
        cell(10, 1, 1, 2, col=eye)
    cr.restore()
    if zzz > 0:
        cr.set_source_rgba(0.72, 0.77, 0.84, clamp(1 - zzz, 0, 1) * 0.9)
        for zx, zy, sz in ((left + cols * px * 0.78, top - zzz * px * 2.0, px * 0.45), (left + cols * px * 0.9, top - zzz * px * 2.0 - px * 1.6, px * 0.34)):
            cr.rectangle(zx, zy, sz * 3, sz)
            cr.rectangle(zx + sz, zy + sz, sz, sz)
            cr.rectangle(zx, zy + sz * 2, sz * 3, sz)
        cr.fill()
    if hearts > 0:
        hh = px * 0.9
        hx, hy = left + cols * px * 0.75, top - hearts * px * 2.5
        cr.set_source_rgba(1.0, 0.353, 0.478, clamp(1 - hearts, 0, 1))
        cr.rectangle(hx, hy, hh, hh)
        cr.rectangle(hx + hh * 1.2, hy, hh, hh)
        cr.rectangle(hx - hh * 0.1, hy + hh * 0.8, hh * 2.4, hh)
        cr.rectangle(hx + hh * 0.6, hy + hh * 1.6, hh, hh)
        cr.fill()


def clawd_mood_for(kind, data, hour):
    """How Clawd feels about what the island shows."""
    if kind == "music":
        return "dance" if getattr(data, "status", "") == "Playing" else "idle"
    if kind == "notify":
        return "love"
    if kind in ("charging",):
        return "dance"
    if kind == "code":
        return "love" if (data or {}).get("copied") else "thinking"
    if kind in ("screenshot", "alert", "volume", "power"):
        return "wave"
    return "sleep" if clawd_asleep(hour) else "idle"


def clawd_line(kind, data):
    """What Clawd says beside the notch when it is unfolded."""
    if kind == "music":
        return "Clawd tanzt mit ♪" if getattr(data, "status", "") == "Playing" else "Clawd wartet auf Musik"
    return {"notify": "Clawd hat Post für dich", "charging": "Clawd tankt mit ⚡", "power": "Clawd sagt Bescheid", "volume": "Clawd sagt Bescheid",
            "alert": "Clawd sagt Bescheid", "screenshot": "Schönes Bild!"}.get(kind) or (
        ("Kopiert!" if (data or {}).get("copied") else "Clawd hält den Code bereit") if kind == "code" else "Clawd ist da")


def glow_color_of(kind, data, accent):
    """The colour the island shimmers in for an activity."""
    if kind in ("volume", "power", "alert", "charging"):
        return alert_of(kind, data)["color"]
    if kind == "notify":
        return accent
    if kind == "music":
        pal = getattr(data, "palette", None)
        return pal[0] if pal else WHITE3
    if kind == "code":
        return GREEN
    return WHITE3


def palette_of(p, fallback=(1.0, 1.0, 1.0)):
    """The cover's colours (at least two), for the glow and the waves."""
    pal = list(getattr(p, "palette", None) or [])
    if not pal:
        pal = [fallback]
    return pal + pal if len(pal) == 1 else pal


def alert_of(kind, data):
    """A short system moment (charging, battery, volume, anything with a symbol) as the island shows it: symbol, title, value, colour, level."""
    d = data or {}
    if kind == "alert":
        return {"glyph": d.get("glyph", "spark"), "title": d.get("title", ""), "value": d.get("value", ""), "color": d.get("color", WHITE3),
                "level": d.get("level"), "charging": False}
    if kind == "charging":
        pct = d.get("pct", 0)
        return {"glyph": "spark", "title": tr("charging"), "value": "%d %%" % pct, "color": GREEN, "level": pct / 100.0, "charging": True}
    if kind == "volume":
        if d.get("muted"):
            return {"glyph": "speaker_muted", "title": tr("volume"), "value": tr("muted"), "color": ORANGE, "level": None, "charging": False}
        pct = d.get("pct", 0)
        return {"glyph": "speaker", "title": tr("volume"), "value": "%d %%" % pct, "color": WHITE3, "level": pct / 100.0, "charging": False}
    state, pct = d.get("state", "unplugged"), d.get("pct", 0)
    if state == "charging":
        return {"glyph": "spark", "title": tr("charging"), "value": "%d %%" % pct, "color": GREEN, "level": pct / 100.0, "charging": True}
    if state == "full":
        return {"glyph": "battery_full", "title": tr("full"), "value": "100 %", "color": GREEN, "level": 1.0, "charging": False}
    if state == "low":
        return {"glyph": "battery", "title": tr("low"), "value": "%d %%" % pct, "color": RED, "level": pct / 100.0, "charging": False}
    return {"glyph": "battery", "title": tr("unplugged"), "value": "%d %%" % pct, "color": WHITE3, "level": pct / 100.0, "charging": False}


def text_width(cr, text, size, bold=False, spacing=0.0):
    layout = PangoCairo.create_layout(cr)
    fd = Pango.FontDescription()
    fd.set_family(FONT_FAMILY)
    fd.set_weight(Pango.Weight.SEMIBOLD if bold else Pango.Weight.NORMAL)
    fd.set_absolute_size(size * Pango.SCALE)
    layout.set_font_description(fd)
    layout.set_text(text, -1)
    if spacing:
        attrs = Pango.AttrList()
        attrs.insert(Pango.attr_letter_spacing_new(int(spacing * Pango.SCALE)))
        layout.set_attributes(attrs)
    return layout.get_pixel_size()[0]


def draw_glyph(cr, name, x, y, size, color, alpha=1.0):
    """The island's little icons (the same drawings as in ZENITH), in a square [size] wide with its corner at (x, y)."""
    cr.save()
    cr.translate(x, y)
    cr.scale(size, size)
    cr.set_source_rgba(*color[:3], alpha)
    cr.set_line_width(0.09)
    cr.set_line_cap(cairo.LINE_CAP_ROUND)
    cr.set_line_join(cairo.LINE_JOIN_ROUND)

    def poly(*pts):
        cr.new_path()
        cr.move_to(*pts[0])
        for pt in pts[1:]:
            cr.line_to(*pt)
        cr.close_path()
        cr.fill()

    def box(bx, by, bw, bh, r, stroke=False):
        rrect(cr, bx, by, bw, bh, r)
        (cr.stroke if stroke else cr.fill)()

    def line(x1, y1, x2, y2):
        cr.move_to(x1, y1)
        cr.line_to(x2, y2)
        cr.stroke()

    if name == "play":
        poly((0.3, 0.2), (0.8, 0.5), (0.3, 0.8))
    elif name == "pause":
        box(0.26, 0.2, 0.16, 0.6, 0.04)
        box(0.58, 0.2, 0.16, 0.6, 0.04)
    elif name == "next":
        poly((0.2, 0.22), (0.62, 0.5), (0.2, 0.78))
        box(0.66, 0.22, 0.12, 0.56, 0.03)
    elif name == "previous":
        poly((0.8, 0.22), (0.38, 0.5), (0.8, 0.78))
        box(0.22, 0.22, 0.12, 0.56, 0.03)
    elif name == "spark":
        # a four-pointed sparkle with soft, concave sides (the charging symbol of ZENITH)
        p = 0.12
        cr.new_path()
        cr.move_to(0.5, 0.0)
        cr.curve_to(0.5 + p, 0.5 - p, 0.5 + p, 0.5 - p, 1.0, 0.5)
        cr.curve_to(0.5 + p, 0.5 + p, 0.5 + p, 0.5 + p, 0.5, 1.0)
        cr.curve_to(0.5 - p, 0.5 + p, 0.5 - p, 0.5 + p, 0.0, 0.5)
        cr.curve_to(0.5 - p, 0.5 - p, 0.5 - p, 0.5 - p, 0.5, 0.0)
        cr.close_path()
        cr.fill()
    elif name in ("battery", "battery_full"):
        box(0.3, 0.18, 0.4, 0.68, 0.07, stroke=True)
        box(0.42, 0.1, 0.16, 0.07, 0.02)
        box(0.37, 0.5, 0.26, 0.29, 0.03)
    elif name in ("speaker", "speaker_muted"):
        poly((0.12, 0.38), (0.3, 0.38), (0.5, 0.2), (0.5, 0.8), (0.3, 0.62), (0.12, 0.62))
        if name == "speaker":
            for r in (0.17, 0.33):
                cr.new_sub_path()
                cr.arc(0.5, 0.5, r, -math.pi / 4, math.pi / 4)
                cr.stroke()
        else:
            line(0.62, 0.38, 0.88, 0.62)
            line(0.88, 0.38, 0.62, 0.62)
    elif name == "bell":
        cr.new_path()
        cr.move_to(0.2, 0.72)
        cr.line_to(0.8, 0.72)
        cr.line_to(0.71, 0.6)
        cr.line_to(0.71, 0.42)
        cr.curve_to(0.71, 0.18, 0.29, 0.18, 0.29, 0.42)
        cr.line_to(0.29, 0.6)
        cr.close_path()
        cr.stroke()
        cr.new_path()
        cr.arc(0.5, 0.84, 0.065, 0, 2 * math.pi)
        cr.fill()
    elif name == "moon":
        cr.push_group()
        cr.set_source_rgba(*color[:3], 1.0)
        cr.arc(0.5, 0.5, 0.34, 0, 2 * math.pi)
        cr.fill()
        cr.set_operator(cairo.OPERATOR_CLEAR)
        cr.arc(0.67, 0.33, 0.29, 0, 2 * math.pi)
        cr.fill()
        cr.pop_group_to_source()
        cr.paint_with_alpha(alpha)
    elif name == "headphones":
        cr.new_sub_path()
        cr.arc(0.5, 0.55, 0.32, math.pi, 2 * math.pi)
        cr.stroke()
        box(0.14, 0.52, 0.16, 0.3, 0.06)
        box(0.7, 0.52, 0.16, 0.3, 0.06)
    elif name == "lock":
        cr.new_sub_path()
        cr.arc(0.5, 0.42, 0.17, math.pi, 2 * math.pi)
        cr.stroke()
        line(0.33, 0.42, 0.33, 0.48)
        line(0.67, 0.42, 0.67, 0.48)
        box(0.24, 0.46, 0.52, 0.38, 0.08)
    elif name == "screenshot":
        for pts in (((0.16, 0.32), (0.16, 0.16), (0.32, 0.16)), ((0.68, 0.16), (0.84, 0.16), (0.84, 0.32)),
                    ((0.84, 0.68), (0.84, 0.84), (0.68, 0.84)), ((0.32, 0.84), (0.16, 0.84), (0.16, 0.68))):
            cr.move_to(*pts[0])
            cr.line_to(*pts[1])
            cr.line_to(*pts[2])
            cr.stroke()
        cr.new_path()
        cr.arc(0.5, 0.5, 0.1, 0, 2 * math.pi)
        cr.fill()
    cr.restore()


def draw_equalizer(cr, x, y, w, h, color, playing, now):
    """Four bars dancing to the music (still while paused), in the cover's colour."""
    tint = WHITE3 if (0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]) < 0.3 else color
    gap = w * 0.12
    bw = (w - gap * 3) / 4.0
    for i, dur in enumerate((0.38, 0.52, 0.30, 0.45)):
        if playing:
            t = ((now - i * 0.04) % (2 * dur)) / dur
            u = t if t <= 1 else 2 - t
            level = 0.25 + 0.75 * (0.5 - 0.5 * math.cos(math.pi * u))
        else:
            level = 0.25
        bh = h * level
        rrect(cr, x + i * (bw + gap), y + (h - bh) / 2.0, bw, bh, bw / 2.0)
        cr.set_source_rgb(*tint)
        cr.fill()


class Island:
    """What the island looks like right now – a port of the Glimmer island of ZENITH (its four modes, looks and music styles): the size
    springs, what it shows and how it draws it. Knows nothing of GTK. [cfg] is what Glimmer actually runs with (see effective_config)."""

    def __init__(self, cfg):
        self.clock = time.monotonic   # (replaceable, to film the motion frame by frame)
        self.configure(cfg)
        self.w = Spring(0.0, 380, 0.95, 0.1)
        self.h = Spring(0.0, 520, 0.95, 0.1)
        self.cap = Spring(14.0 if self.hang else 60.0, 280, 1.0, 0.1)   # how round the corners may be (dp)
        self.press = Spring(1.0, 700, 0.7, 0.0005)
        self.nudge = Spring(0.0, 380, 0.95, 0.002)
        self.kind = None
        self.data = None
        self.expanded = False
        self.visible = False
        self.prev = None            # (kind, data, expanded, since): the content that is fading out
        self.changed_at = -100.0
        self.key_at = -100.0        # when something new came (not just unfolded)
        self.sweep_at = -100.0
        self._sweep_key = None
        self._key = None
        self.seek_drag = None       # the progress being dragged (0..1)
        self.seek_rect = None
        self.privacy = None         # "mic" | "camera": something is using it
        self.tint = (0.0, 0.0, 0.0)
        self.hits = []
        self.ox = self.oy = 0.0

    @property
    def open(self):
        return self.expanded

    def configure(self, cfg):
        """Takes the settings Glimmer runs with (see effective_config); can be called again while it runs: it moves to the new shape."""
        self.cfg = cfg
        self.s = cfg["scale"]
        self.mode = cfg["mode"]
        self.hang = self.mode == "notch"
        self.mac = self.mode in ("notch", "island")
        self.di = self.mode == "dynamic_island"     # (the iPhone's proportions)
        self.dyn = is_dynamic(cfg)                  # (the iPhone's look: with the notch or the island too)
        if self.mode == "notch":
            self.nw, self.nh = float(cfg["notch_width"]), float(cfg["notch_height"])
        elif self.mode == "island":
            self.nw, self.nh = float(cfg["island_width"]), float(cfg["island_height"])
        else:
            self.nw = self.nh = None
        self.narrow = 0.85 if cfg["small"] else 1.0
        self.wscale = float(cfg["width"])
        self.accent = parse_color(cfg["accent"])
        if getattr(self, "kind", None) is not None:
            self.show(self.kind, self.data, self.expanded)

    # --- sizes (the same arithmetic as islandSize in ZENITH)

    def top_inset(self):
        oy = self.cfg["offset_y"]
        if self.hang:
            return float(max(oy, 0))
        if self.mode == "island":
            return float(max(ISLAND_GAP + oy, 0))
        return 14.0 + oy

    def hole(self):
        """The camera (or the notch standing in for it): its half width and how far down it reaches."""
        if self.mode == "notch":
            return self.nw / 2.0, self.nh
        if self.mode == "island":
            return 7.0, self.nh / 2.0 + 7.0
        return 7.0, 15.0 + 7.0

    def hole_half(self):
        return self.hole()[0] + CAMERA_MARGIN

    def clearance(self):
        """How much lower unfolded content starts, so that it begins below the camera."""
        return max(0.0, self.hole()[1] + CAMERA_MARGIN + 2.0 - EXPANDED_TOP)

    def size_for(self, kind, data, expanded):
        full = min(SCREEN_W - 22.0, 440.0) if self.di else min(SCREEN_W - 16.0, 380.0 if self.mac else 420.0)
        full = min(full * float(self.cfg["expand_width"]), 520.0)
        if self.di:
            compact_base = min(ISLAND_H * 6.2, SCREEN_W - 60.0) * self.narrow
            idle = ISLAND_H * 3.375 * self.narrow
        else:
            compact_base = min(clamp(SCREEN_W * 0.44, 150.0, 190.0) * self.wscale * self.narrow, SCREEN_W - 60.0)
            idle = clamp(SCREEN_W * 0.27, 86.0, 110.0) * self.wscale * self.narrow
        if kind is None:
            return 0.0, 0.0
        if kind == "idle":
            return (self.nw, self.nh) if self.mac else (idle, ISLAND_H)
        if not expanded:
            room = ROOMS.get(kind, 72) if self.hang else 72
            aw = float(self.cfg["active_width"])   # (wider or narrower while something runs: the notch keeps its width, the room beside it grows)
            compact = (self.nw + room * aw) if self.mac else min(compact_base * aw, 480.0)
            return compact, (max(ISLAND_H, self.nh) if self.mac else ISLAND_H)
        body = float(BODY_HEIGHT.get(kind, 96))
        if kind == "notify":
            # A notification's words are not cut off: the card grows with a long title or text (the title over two lines, the text over six).
            d = data or {}
            room = full - 36.0 - 52.0
            title_lines = clamp(text_line_count(d.get("summary") or d.get("app") or tr("notification"), 16, room, True), 1, 2)
            text_lines = clamp(text_line_count(d.get("body") or "", 13, room), 0, 6)
            body += (title_lines - 1) * 20.0 + max(text_lines - 2, 0) * 17.0
        return full, self.clearance() + body * float(self.cfg["expand_height"])

    def _damp(self, r):
        """(Hanging from the screen's edge it settles without a bounce: a tab swinging past its size looks like a glitch.)"""
        if self.hang:
            return max(r, 0.95)
        springy = {"calm": 0.22, "normal": 0.0, "playful": -0.2}.get(self.cfg["motion"], 0.0)
        return clamp(r + springy, 0.25, 1.0)

    def _tune(self):
        if not self.visible:
            self.w.tune(520, 1.0)
            self.h.tune(520, 1.0)
        elif self.expanded:
            self.w.tune(330, self._damp(0.68))
            self.h.tune(240, self._damp(0.72))
        else:
            self.w.tune(380, self._damp(0.78))
            self.h.tune(520, self._damp(0.86))

    # --- what to show

    @staticmethod
    def _key_of(kind, data):
        if kind == "alert":
            return ("alert", (data or {}).get("title"))
        if kind == "notify":
            return ("notify", id(data))
        return (kind, (data or {}).get("state") if isinstance(data, dict) and kind == "power" else None)

    def show(self, kind, data, expanded=False):
        now = self.clock()
        changed = kind != self.kind or expanded != self.expanded or (kind in ("notify", "alert") and data is not self.data)
        if changed and self.kind is not None and self.visible:
            self.prev = (self.kind, self.data, self.expanded, now)
        key = self._key_of(kind, data)
        if key != self._key:
            self._key = key
            if self.kind is not None and kind != "idle":
                self.key_at = now  # something new: a hop and a shimmer
            if key == ("power", "charging") or kind == "charging" and self.kind != "charging":
                self.sweep_at = now  # plugging in: a streak of green light runs round the island
        self.kind, self.data, self.expanded = kind, data, expanded
        self.visible = True
        tw, th = self.size_for(kind, data, expanded)
        self._tune()
        self.w.target, self.h.target = tw * self.s, th * self.s
        self.cap.target = (30.0 if self.hang else 42.0 if self.dyn else 44.0) if expanded else (14.0 if self.hang else 60.0)
        if changed:
            self.changed_at = now

    def hide(self):
        if not self.visible:
            return
        self.visible = False
        self.expanded = False
        self._tune()
        self.w.target = 0.0  # (it shrinks away into the edge)
        self.h.target = 0.0

    def set_pressed(self, down):
        if self.kind in (None, "idle"):
            return
        if down:
            self.press.tune(700, 0.7)
            self.press.target = (1.015 if self.expanded else 1.02) if self.hang else 1.06
        else:
            self.press.tune(520, 0.42)
            self.press.target = 1.0

    def snap(self):
        for sp in (self.w, self.h, self.cap, self.press, self.nudge):
            sp.snap()
        self.changed_at = self.key_at = self.sweep_at = -100.0
        self.prev = None
        if self.kind:
            self.tint = glow_color_of(self.kind, self.data, self.accent)

    def passive(self):
        return self.kind in (None, "idle")

    def step(self, dt):
        """Advances the springs; returns (still moving, something ambient – the waves, the pulse, the glow – is animating)."""
        now = self.clock()
        moved = False
        for sp in (self.w, self.h, self.cap, self.press, self.nudge):
            sp.step(dt)
            moved = moved or not sp.settled
        if not self.visible and self.w.x < 1.0 and self.h.x < 1.0:
            self.kind = None
            self.prev = None
            self.w.x = self.h.x = 0.0
            self.w.v = self.h.v = 0.0
            moved = False
        if self.kind:
            target = glow_color_of(self.kind, self.data, self.accent)
            self.tint = mix(self.tint, target, min(1.0, dt * 6.0))
        if (now - self.changed_at < 0.9 or (self.prev is not None and now - self.prev[3] < 0.2) or now - self.sweep_at < 1.25
                or now - self.key_at < 1.8):
            moved = True
        ambient = False
        if self.visible and not self.passive():
            ambient = True
        elif self.visible and self.kind == "idle":
            ambient = bool(self.cfg["clawd"] and self.cfg["clawd_idle"]) or bool(self.cfg["ai_fluid"] and not self.hang)
        return moved, ambient

    # --- geometry

    def geometry(self, W):
        s = self.s
        bw, bh = max(self.w.x, 0.0), max(self.h.x, 0.0)
        ear = min(EAR * s, bh / 2.0) if self.hang else 0.0
        return (W - bw) / 2.0 + self.cfg["offset_x"] * s, self.top_inset() * s, bw, bh, ear

    def input_rect(self, W, sensor):
        if self.kind is not None and self.visible:
            bx, top, bw, bh, ear = self.geometry(W)
            return int(bx - ear) - 1, int(top), int(bw + 2 * ear) + 2, int(bh) + 1
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

    def seek_fraction(self, x):
        if not self.seek_rect:
            return 0.0
        x0, x1 = self.seek_rect
        return clamp(((x - self.ox) / self.s - x0) / max(x1 - x0, 1), 0.0, 1.0)

    # --- drawing

    def draw(self, cr, W, H, t):
        self.hits = []
        self.seek_rect = None
        if self.kind is None:
            return
        s = self.s
        bx, top, bw, bh, ear = self.geometry(W)
        if bw < 1 or bh < 1:
            return
        now = self.clock()
        cfg = self.cfg
        fade = 1.0 if self.visible else clamp(bw / (40 * s), 0.0, 1.0)
        corner = min(bh / 2.0, max(self.cap.x, 0.0) * s)
        rt = 0.0 if self.hang else corner
        passive = self.passive()
        glow_col = self._glow_color(now)

        def path(grow=0.0):
            # (the island's outline, grown by [grow] on every free side: the rings of light round it)
            body_path(cr, bx - grow, top if self.hang else top - grow, bw + 2 * grow, bh + grow * (1 if self.hang else 2), rt + grow if not self.hang else 0.0,
                      corner + grow, ear)

        cr.save()
        # Pressing it, and something new arriving: it swells a little (the hanging notch neither squeezes nor swells).
        sc = self.press.x
        if not self.hang and cfg["motion"] is not None:
            age = now - self.key_at
            if 0.0 <= age < 1.5 and not passive:
                hop = 1.0 - 0.07 * min(age / 0.09, 1.0) if age < 0.09 else 0.93 + 0.07 * spring_curve(age - 0.09, 420, self._damp(0.42))
                sc *= hop
        if abs(sc - 1.0) > 1e-4:
            ccx = bx + bw / 2.0
            cr.translate(ccx, top)
            cr.scale(sc, sc)
            cr.translate(-ccx, -top)

        # The glow round the island: shimmer when something new comes, breathing while something runs.
        shimmer = 0.0
        age = now - self.key_at
        if cfg["glow"] and not self.expanded and 0.0 <= age < 1.66 and not passive:
            shimmer = ease(age / 0.26) if age < 0.26 else 1.0 - ease((age - 0.26) / 1.4)
        breath = 0.0
        if cfg["breathe"] and not passive and not self.expanded and self.visible:
            breath = 0.25 + 0.75 * (0.5 - 0.5 * math.cos(2.0 * math.pi * now / 3.2))
        if (shimmer > 0.01 or breath > 0.01) and fade > 0.5:
            rainbow = cfg["glow_color"] == "rainbow"
            for i in range(1, 4):
                if breath > 0.01:
                    path(i * 2.2 * s * (0.6 + 0.4 * breath))
                    cr.set_source_rgba(*glow_col, breath * 0.22 / i)
                    cr.fill()
            for i in range(1, 5):
                if shimmer > 0.01:
                    path(i * 1.6 * s)
                    if rainbow:
                        g = cairo.LinearGradient(bx, 0, bx + bw, 0)
                        for j, c in enumerate(RAINBOW):
                            g.add_color_stop_rgba(j / (len(RAINBOW) - 1), *c, shimmer * 0.4 / i)
                        cr.set_source(g)
                    else:
                        cr.set_source_rgba(*glow_col, shimmer * 0.32 / i)
                    cr.fill()

        # The body, in the chosen look.
        style = cfg["style"]
        if style == "black" and cfg["ai_fluid"] and self.expanded and not self.hang:
            style = "fluid"  # AI Fluid: unfolded, Claude's colours drift inside the black island too
        path()
        if style == "tinted":
            g = cairo.LinearGradient(0, top, 0, top + bh)
            g.add_color_stop_rgba(0.0, *mix((0, 0, 0), self.tint, 0.42), fade)
            g.add_color_stop_rgba(1.0, *mix((0, 0, 0), self.tint, 0.16), fade)
            cr.set_source(g)
            cr.fill()
        elif style == "glass":
            g = cairo.LinearGradient(0, top, 0, top + bh)
            g.add_color_stop_rgba(0.0, 0.14, 0.14, 0.17, 0.80 * fade)
            g.add_color_stop_rgba(1.0, 0.02, 0.02, 0.04, 0.72 * fade)
            cr.set_source(g)
            cr.fill()
            path()
            cr.set_line_width(1.0)
            sg = cairo.LinearGradient(0, top, 0, top + bh)
            sg.add_color_stop_rgba(0.0, 1, 1, 1, 0.34 * fade)
            sg.add_color_stop_rgba(1.0, 1, 1, 1, 0.08 * fade)
            cr.set_source(sg)
            cr.stroke()
        else:
            cr.set_source_rgba(0, 0, 0, fade)
            cr.fill()
        if style == "fluid":
            cr.save()
            path()
            cr.clip()
            drift = 2.0 * math.pi * now / 9.0
            for i, c in enumerate(CLAUDE_COLORS[:3]):
                ph = i * 2.1
                cx = bx + bw * (0.5 + 0.42 * math.cos(drift + ph))
                cy = top + bh * (0.5 + 0.5 * math.sin(drift * 1.3 + ph))
                r = max(bw, bh) * 0.45
                rg = cairo.RadialGradient(cx, cy, 0, cx, cy, r)
                rg.add_color_stop_rgba(0.0, *c, 0.34 * fade)
                rg.add_color_stop_rgba(1.0, *c, 0.0)
                cr.set_source(rg)
                cr.arc(cx, cy, r, 0, 2 * math.pi)
                cr.fill()
            cr.restore()

        # The edge: a fine line, or in the activity's colour, and Claude's colours flowing round it.
        outline = cfg["outline"]
        if outline != "off" and fade > 0.3:
            path()
            if outline == "subtle":
                cr.set_line_width(1.0 * s)
                cr.set_source_rgba(1, 1, 1, 0.16 * fade)
            elif cfg["glow_color"] == "rainbow":
                cr.set_line_width(1.5 * s)
                g = cairo.LinearGradient(bx, 0, bx + bw, 0)
                for j, c in enumerate(RAINBOW):
                    g.add_color_stop_rgba(j / (len(RAINBOW) - 1), *c, fade)
                cr.set_source(g)
            else:
                cr.set_line_width(1.5 * s)
                cr.set_source_rgba(*glow_color_of(self.kind, self.data, self.accent), 0.75 * fade)
            cr.stroke()
        if cfg["ai_fluid"] and not self.hang and fade > 0.3:
            flowing = not passive
            strength = (0.3 if passive else 0.75) + 0.8 * shimmer
            ang = 2.0 * math.pi * now / 4.2 if flowing else 0.8
            half = max(bw, bh) / 2.0
            ccx, ccy = bx + bw / 2.0, top + bh / 2.0
            g = cairo.LinearGradient(ccx + math.cos(ang) * half, ccy + math.sin(ang) * half, ccx - math.cos(ang) * half, ccy - math.sin(ang) * half)
            n = len(CLAUDE_COLORS)
            for j, c in enumerate(CLAUDE_COLORS):
                g.add_color_stop_rgba(j / (n - 1), *c, clamp(strength * 0.85, 0.0, 1.0) * fade)
            g.set_extend(cairo.EXTEND_REFLECT)
            path()
            cr.set_line_width(1.5 * s)
            cr.set_source(g)
            cr.stroke()

        # Plugging in: a streak of green light runs down one side, along the foot and up the other.
        p = (now - self.sweep_at) / 1.2
        if 0.0 < p < 1.0:
            self._streak(cr, bx, top, bw, bh, corner, ease(p), min(p, 1.0 - p) * 6.0)

        ox, oy = bx, top
        self.ox, self.oy = ox, oy
        layers = []
        if self.prev is not None:
            out = 1.0 - clamp((now - self.prev[3]) / 0.09, 0.0, 1.0)
            if out > 0.005:
                layers.append((self.prev[0], self.prev[1], self.prev[2], out * fade, False))
        layers.append((self.kind, self.data, self.expanded, clamp((now - self.changed_at - 0.07) / 0.17, 0.0, 1.0) * fade, True))
        cr.save()
        path()
        cr.clip()
        for kind, data, expanded, a, live in layers:
            if a <= 0.005:
                continue
            if expanded and kind != "idle":
                # (an unfolded card is laid out at its full size, centred, and uncovered as the island grows – it does not squeeze
                # itself into the size the island has got to so far)
                lw, lh = self.size_for(kind, data, True)
            else:
                lw, lh = bw / s, bh / s
            lx = ox + (bw - lw * s) / 2.0
            if live:
                self.ox = lx
            cr.push_group()
            cr.save()
            cr.translate(lx + self.nudge.x * 14 * s, oy)
            cr.scale(s, s)
            self._content(cr, kind, data, expanded, lw, lh, now, live)
            cr.restore()
            cr.pop_group_to_source()
            cr.paint_with_alpha(a)
        cr.restore()
        cr.restore()

    def _glow_color(self, now):
        mode = self.cfg["glow_color"]
        if mode == "accent":
            return self.accent
        if mode == "white":
            return WHITE3
        if mode == "rainbow":
            return hsv(now * 90.0, 0.8, 1.0)
        return glow_color_of(self.kind, self.data, self.accent)

    def _streak(self, cr, bx, top, bw, bh, corner, progress, fade):
        """The outline without its top (that is the screen's edge), as a polyline, and a bright streak running along it."""
        inset = 1.5 * self.s
        budget = min(bw, bh) / 2.0
        cr.new_path()
        cr.move_to(bx + bw - inset, top + (0 if self.hang else corner))
        cr.line_to(bx + bw - inset, top + bh - corner)
        _add_corner(cr, corner, 0.6, budget, bx + bw - inset, top + bh - inset, 1, False)
        _add_corner(cr, corner, 0.6, budget, bx + inset, top + bh - inset, 2, False)
        cr.line_to(bx + inset, top + (0 if self.hang else corner))
        pts, cum = [], [0.0]
        for typ, coords in cr.copy_path_flat():
            if typ in (cairo.PATH_MOVE_TO, cairo.PATH_LINE_TO):
                if pts:
                    cum.append(cum[-1] + math.hypot(coords[0] - pts[-1][0], coords[1] - pts[-1][1]))
                pts.append(coords)
        cr.new_path()
        total = cum[-1]
        if total < 1 or len(pts) < 2:
            return
        trail = 0.42 * total
        head = progress * (total + trail)
        steps = 48
        cr.set_line_width(2.0 * self.s)
        cr.set_line_cap(cairo.LINE_CAP_ROUND)
        for i in range(steps):
            a0 = head - trail * (1.0 - i / steps)
            a1 = head - trail * (1.0 - (i + 1) / steps)
            if a1 < 0 or a0 > total:
                continue
            a0, a1 = max(a0, 0.0), min(a1, total)
            if a1 <= a0:
                continue
            k = (i + 1) / steps
            col = tuple(GREEN[j] + (1.0 - GREEN[j]) * clamp((k - 0.8) / 0.2, 0.0, 1.0) for j in range(3))
            self._polyline(cr, pts, cum, a0, a1)
            cr.set_source_rgba(*col, clamp(fade, 0.0, 1.0) * k * k)
            cr.stroke()

    @staticmethod
    def _polyline(cr, pts, cum, a0, a1):
        def at(d):
            lo = 0
            while lo < len(cum) - 2 and cum[lo + 1] < d:
                lo += 1
            seg = cum[lo + 1] - cum[lo]
            f = 0.0 if seg <= 0 else clamp((d - cum[lo]) / seg, 0.0, 1.0)
            return pts[lo][0] + (pts[lo + 1][0] - pts[lo][0]) * f, pts[lo][1] + (pts[lo + 1][1] - pts[lo][1]) * f
        cr.new_path()
        cr.move_to(*at(a0))
        cr.line_to(*at((a0 + a1) / 2.0))
        cr.line_to(*at(a1))

    # --- content

    def _content(self, cr, kind, data, expanded, bw, bh, now, live):
        age = (now - self.changed_at) if live else 10.0
        if kind == "idle":
            self._idle(cr, bw, bh, now)
            if self.privacy and live:
                self._privacy_dot(cr, bw, bh)
            return
        if expanded:
            if kind == "music":
                if self.cfg["music_style"] == "hyper":
                    self._exp_music_hyper(cr, data, bw, bh, now, live)
                else:
                    self._exp_music_classic(cr, data, bw, bh, now, live)
            elif kind == "notify":
                self._exp_notify(cr, data, bw, bh, live)
            elif kind == "code":
                self._exp_code(cr, data, bw, bh, live)
            elif kind == "screenshot":
                self._exp_screenshot(cr, data, bw, bh, live)
            else:
                self._exp_alert(cr, kind, data, bw, bh, now, age, live)
            if self.cfg["clawd"]:
                self._exp_clawd(cr, kind, data, bw, bh, now)
        else:
            self._compact(cr, kind, data, bw, bh, now, age)
            if self.privacy and live:
                self._privacy_dot(cr, bw, bh)

    def _privacy_dot(self, cr, bw, bh):
        """The camera (green) or the microphone (orange) is in use: a dot at the right end, as on the iPhone."""
        cr.set_source_rgb(*(GREEN if self.privacy == "camera" else ORANGE))
        cr.arc(bw - 12.0, bh / 2.0, 3.0, 0, 2 * math.pi)
        cr.fill()

    def _idle(self, cr, bw, bh, now):
        cfg = self.cfg
        if cfg["clawd"] and cfg["clawd_idle"]:
            hour = time.localtime().tm_hour
            self._around(cr, bw, bh, lambda c, x, cy: 0.0, lambda c, x, cy: 0.0, edge=9.0, rider="sleep" if clawd_asleep(hour) else "idle", now=now)
        elif cfg["idle_clock"]:
            draw_text(cr, time.strftime("%H:%M"), bw / 2.0, bh / 2.0, 13, (1.0, 1.0, 1.0, 0.92), bold=True, align="center")

    def _around(self, cr, bw, bh, left, right, edge=6.0, rider=None, now=0.0):
        """Compact content split round the notch (or camera): something small on the left, the notch in the middle, something on the right.
        What does not fit is cut at the notch's edge instead of running under it. Clawd rides on his side, where he has been put."""
        cfg = self.cfg
        half = self.hole_half()
        hs = clamp(bw / 2.0 - half, 0.0, bw)
        he = clamp(bw / 2.0 + half, hs, bw)
        end_edge = edge + (10.0 if self.privacy else 0.0)
        k = clamp(cfg["clawd_size"], 0.7, 1.4)
        cw, ch = 20.0 * k, 17.0 * k
        spot = clamp(cfg["clawd_spot"], 0.0, 1.0)
        on_left = cfg["clawd_side"] == "left"
        color = clawd_color(cfg["clawd_skin"], now)
        cr.save()
        cr.rectangle(0, 0, hs, bh)
        cr.clip()
        x0 = min(edge, hs)
        lw = left(cr, x0, bh / 2.0) or 0.0
        if rider and on_left:
            spare = max(hs - (x0 + lw) - cw - 6.0, 0.0)
            draw_clawd(cr, x0 + lw + spare * spot + 3.0, bh / 2.0 - ch / 2.0, cw, ch, rider, color, now)
        cr.restore()
        cr.save()
        cr.rectangle(he, 0, bw - he, bh)
        cr.clip()
        xr = bw - min(end_edge, bw - he)
        rw = right(cr, xr, bh / 2.0) or 0.0
        if rider and not on_left:
            spare = max((xr - rw) - he - cw - 6.0, 0.0)
            draw_clawd(cr, he + spare * (1.0 - spot) + 3.0, bh / 2.0 - ch / 2.0, cw, ch, rider, color, now)
        cr.restore()

    def _rider(self, kind, data):
        if not self.cfg["clawd"]:
            return None
        return clawd_mood_for(kind, data, time.localtime().tm_hour)

    def _cover(self, cr, p, x, y, size, radius, pal, circle=False):
        cr.save()
        if circle:
            cr.new_path()
            cr.arc(x + size / 2.0, y + size / 2.0, size / 2.0, 0, 2 * math.pi)
        else:
            rrect(cr, x, y, size, size, radius)
        cr.clip()
        art = getattr(p, "art", None)
        if art is not None:
            cr.translate(x, y)
            cr.scale(size / art.get_width(), size / art.get_height())
            cr.set_source_surface(art, 0, 0)
            cr.paint()
        else:
            g = cairo.LinearGradient(x, y, x + size, y + size)
            for i, c in enumerate(pal):
                g.add_color_stop_rgb(i / max(len(pal) - 1, 1), *c[:3])
            cr.set_source(g)
            cr.paint()
        cr.restore()

    def _wave(self, cr, x, y, w, h, pal, playing, now):
        """Seven slim bars rising and falling like a sound wave, painted across the cover's colours."""
        g = cairo.LinearGradient(x, 0, x + w, 0)
        for i, c in enumerate(pal):
            g.add_color_stop_rgb(i / max(len(pal) - 1, 1), *c[:3])
        gap = w * 0.07
        bwid = (w - gap * 6) / 7.0
        phase = 2.0 * math.pi * now / 1.4
        for i in range(7):
            if playing:
                a = abs(math.sin(phase * (1.0 + i * 0.17) + i * 0.9))
                b = 0.6 + 0.4 * math.sin(phase * 0.5 + i * 1.3)
                level = clamp(0.22 + 0.78 * a * b, 0.18, 1.0)
            else:
                level = 0.2
            bar_h = h * level
            rrect(cr, x + i * (bwid + gap), y + (h - bar_h) / 2.0, bwid, bar_h, bwid / 2.0)
            cr.set_source(g)
            cr.fill()

    def _compact(self, cr, kind, data, bw, bh, now, age):
        rider = self._rider(kind, data)
        if kind == "music":
            p = data
            pal = palette_of(p)
            playing = getattr(p, "status", "") == "Playing"
            style = self.cfg["music_style"]
            if style == "hyper":
                # The cover's colours glow softly through the black from both ends.
                breath = 0.55 + 0.45 * (0.5 - 0.5 * math.cos(2.0 * math.pi * now / 3.6)) if playing else 0.45
                g = cairo.LinearGradient(0, 0, bw, 0)
                g.add_color_stop_rgba(0.0, *pal[0][:3], 0.34 * breath)
                g.add_color_stop_rgba(0.38, *pal[0][:3], 0.0)
                g.add_color_stop_rgba(0.62, *pal[-1][:3], 0.0)
                g.add_color_stop_rgba(1.0, *pal[-1][:3], 0.30 * breath)
                cr.rectangle(0, 0, bw, bh)
                cr.set_source(g)
                cr.fill()

                def left(c, x, cy):
                    self._cover(c, p, x, cy - 10.5, 21, 7, pal)
                    return 21.0

                def right(c, x, cy):
                    if playing:
                        self._wave(c, x - 4 - 30, cy - 7.5, 30, 15, pal, True, now)
                        return 34.0
                    draw_glyph(c, "play", x - 4 - 15, cy - 7.5, 15, pal[0])
                    return 19.0
                self._around(cr, bw, bh, left, right, rider=rider, now=now)
            elif style == "fluid_cloud":
                self._fluid_cloud(cr, p, pal, playing, bw, bh, now, rider)
            else:
                def left(c, x, cy):
                    if getattr(p, "art", None) is not None:
                        # (the iPhone shows the cover as a small rounded square, Glimmer as a circle)
                        self._cover(c, p, x, cy - 10, 20, 4.8, pal, circle=not self.dyn)
                    else:
                        draw_glyph(c, "speaker", x, cy - 10, 20, WHITE3)
                    return 20.0

                def right(c, x, cy):
                    if playing:
                        draw_equalizer(c, x - 2 - 20, cy - 7, 20, 14, pal[0], True, now)
                        return 22.0
                    draw_glyph(c, "play", x - 2 - 16, cy - 8, 16, WHITE3, 0.85)
                    return 18.0
                self._around(cr, bw, bh, left, right, rider=rider, now=now)
            return
        if kind == "notify":
            d = data or {}

            def left(c, x, cy):
                pop = 0.4 + 0.6 * spring_curve(age, 420, 0.45)
                a = clamp((pop - 0.4) / 0.5, 0.0, 1.0)
                c.save()
                c.translate(x + 10, cy)
                c.scale(pop, pop)
                c.translate(-10, -10)
                self._app_icon(c, d.get("icon"), 0, 0, 20, a)
                c.restore()
                return 20.0

            def right(c, x, cy):
                k = 0.6 + 0.4 * (0.5 - 0.5 * math.cos(2.0 * math.pi * now / 1.4))
                c.set_source_rgba(*self.accent[:3], 1.0)
                c.arc(x - 5, cy, 5 * k, 0, 2 * math.pi)
                c.fill()
                return 10.0
            self._around(cr, bw, bh, left, right, rider=rider, now=now)
            return
        if kind == "code":
            d = data or {}

            def left(c, x, cy):
                draw_glyph(c, "lock", x, cy - 10, 20, GREEN)
                return 20.0

            def right(c, x, cy):
                if d.get("copied"):
                    return draw_text(c, "Kopiert", x - 2, cy, 12, (*GREEN, 1.0), bold=True, align="right") + 2
                code = d.get("code", "")
                size = 11 if len(code) > 6 else 13
                return draw_text(c, code, x - 2, cy, size, WHITE, bold=True, align="right", spacing=0 if len(code) > 6 else 1, tnum=True) + 2
            self._around(cr, bw, bh, left, right, rider=rider, now=now)
            return
        if kind == "screenshot":
            d = data or {}

            def left(c, x, cy):
                thumb = d.get("thumb")
                if thumb is not None:
                    self._app_icon(c, thumb, x, cy - 10, 20)
                else:
                    draw_glyph(c, "screenshot", x, cy - 10, 20, WHITE3)
                return 20.0

            def right(c, x, cy):
                draw_glyph(c, "screenshot", x - 2 - 16, cy - 8, 16, WHITE3, 0.85)
                return 18.0
            self._around(cr, bw, bh, left, right, rider=rider, now=now)
            return
        al = alert_of(kind, data)

        def left(c, x, cy):
            self._alert_glyph(c, al, x, cy - 10, 20, now, age)
            return 20.0

        def right(c, x, cy):
            used = 0.0
            if al["level"] is not None and not self.hang:
                self._level_bar(c, x - 20, cy - 5.5, 20, 11, al["level"], al["color"], al["charging"], now, age)
                used = 24.0
            w = draw_text(c, al["value"], x - used - 2, cy, 12, (*al["color"][:3], 1.0), bold=True, max_w=96, align="right")
            return used + w + 2
        self._around(cr, bw, bh, left, right, rider=rider, now=now)

    def _fluid_cloud(self, cr, p, pal, playing, bw, bh, now, rider):
        """OPPO's and OnePlus' Fluid Cloud: a spinning cover with a progress ring, the time left."""
        length = getattr(p, "length", 0) or 0
        pos = p.position() if hasattr(p, "position") else 0
        frac = clamp(pos / length, 0.0, 1.0) if length > 0 else 0.0
        ring = pal[0]

        def left(c, x, cy):
            cx = x + 11.0
            c.set_source_rgba(1, 1, 1, 0.18)
            c.set_line_width(2.0)
            c.new_path()
            c.arc(cx, cy, 10.0, 0, 2 * math.pi)
            c.stroke()
            c.set_source_rgb(*ring)
            c.set_line_cap(cairo.LINE_CAP_ROUND)
            c.new_path()
            c.arc(cx, cy, 10.0, -math.pi / 2, -math.pi / 2 + 2 * math.pi * frac)
            c.stroke()
            c.save()
            c.translate(cx, cy)
            c.rotate((now % 8.0) / 8.0 * 2 * math.pi if playing else 0.0)
            c.new_path()
            c.arc(0, 0, 8.0, 0, 2 * math.pi)
            c.set_source_rgb(0.165, 0.165, 0.18)
            c.fill_preserve()
            c.clip()
            art = getattr(p, "art", None)
            if art is not None:
                c.translate(-8, -8)
                c.scale(16.0 / art.get_width(), 16.0 / art.get_height())
                c.set_source_surface(art, 0, 0)
                c.paint()
            c.restore()
            return 22.0

        def right(c, x, cy):
            if length > 0:
                return draw_text(c, "-" + fmt_time(length - pos), x - 2, cy, 12, (1, 1, 1, 0.9 if playing else 0.55), bold=True, align="right", tnum=True) + 2
            draw_equalizer(c, x - 22, cy - 7, 20, 14, ring, playing, now)
            return 22.0
        self._around(cr, bw, bh, left, right, edge=5.0, rider=rider, now=now)

    def _app_icon(self, cr, icon, x, y, size, alpha=1.0):
        if icon is not None:
            cr.save()
            rrect(cr, x, y, size, size, size * 0.28)
            cr.clip()
            cr.translate(x, y)
            cr.scale(size / icon.get_width(), size / icon.get_height())
            cr.set_source_surface(icon, 0, 0)
            cr.paint_with_alpha(alpha)
            cr.restore()
        else:
            draw_glyph(cr, "bell", x, y, size, (1.0, 1.0, 1.0), alpha)

    def _alert_glyph(self, cr, al, x, y, size, now, age):
        """Each little system moment gets its own motion: the sparkle zaps in with a flash, the full battery sparkles twice."""
        cx, cy = x + size / 2.0, y + size / 2.0
        if al["glyph"] == "spark":
            if age < 0.15:
                z = 1.35 * max(age, 0.0) / 0.15
            else:
                z = 1.0 + 0.35 * (1.0 - spring_curve(age - 0.15, 420, 0.35))
            f = max(0.0, 1.0 - age / 0.6)
            if f > 0.01:
                cr.set_source_rgba(*al["color"][:3], f * 0.45)
                cr.arc(cx, cy, size * (0.35 + 0.4 * (1.0 - f)), 0, 2 * math.pi)
                cr.fill()
            alpha = 0.875 + 0.125 * math.cos(2.0 * math.pi * now / 1.8) if al["charging"] else 1.0
            cr.save()
            cr.translate(cx, cy)
            cr.scale(z, z)
            cr.translate(-cx, -cy)
            draw_glyph(cr, "spark", x, y, size, al["color"], alpha)
            cr.restore()
            return
        if al["glyph"] == "battery_full":
            if age < 0.14:
                g = 1.0 + 0.18 * age / 0.14
            elif age < 0.26:
                g = 1.18 - 0.23 * (age - 0.14) / 0.12
            elif age < 0.38:
                g = 0.95 + 0.17 * (age - 0.26) / 0.12
            else:
                g = 1.12 - 0.12 * spring_curve(age - 0.38, 300, 0.5)
        else:
            g = 0.6 + 0.4 * spring_curve(age, 380, 0.45)
        cr.save()
        cr.translate(cx, cy)
        cr.scale(g, g)
        cr.translate(-cx, -cy)
        draw_glyph(cr, al["glyph"], x, y, size, al["color"], clamp(g, 0.0, 1.0) if al["glyph"] != "battery_full" else 1.0)
        cr.restore()

    # --- unfolded

    def _exp_origin(self, bw, bh):
        """The padded area unfolded content lives in: left, right, top (below the notch) and bottom."""
        return 18.0, bw - 18.0, EXPANDED_TOP + self.clearance(), bh - 14.0

    def _exp_clawd(self, cr, kind, data, bw, bh, now):
        """Clawd in the unfolded island: beside the camera, with a word about what is going on."""
        cfg = self.cfg
        band = EXPANDED_TOP + self.clearance()
        right = cfg["clawd_side"] == "right"
        radius = self.hole()[0]
        room = max((bw - bw / 2.0 - radius - CAMERA_MARGIN - 18.0) if right else (bw / 2.0 - radius - CAMERA_MARGIN - 18.0), 0.0)
        k = clamp(cfg["clawd_size"], 0.7, 1.4)
        h = min(clamp(band - 6.0, 12.0, 20.0) * k, band)
        mood = clawd_mood_for(kind, data, time.localtime().tm_hour)
        color = clawd_color(cfg["clawd_skin"], now)
        cr.save()
        x0 = bw - 18.0 - room if right else 18.0
        cr.rectangle(x0, 0, room, band)
        cr.clip()
        text = clawd_line(kind, data)
        tw = text_width(cr, text, 11)
        if right:
            draw_text(cr, text, bw - 18.0 - h * 1.2 - 6.0, band / 2.0, 11, (1, 1, 1, 0.7), align="right", max_w=max(room - h * 1.2 - 6, 1))
            draw_clawd(cr, bw - 18.0 - h * 1.2, band / 2.0 - h / 2.0, h * 1.2, h, mood, color, now)
        else:
            draw_clawd(cr, 18.0, band / 2.0 - h / 2.0, h * 1.2, h, mood, color, now)
            draw_text(cr, text, 18.0 + h * 1.2 + 6.0, band / 2.0, 11, (1, 1, 1, 0.7), max_w=max(room - h * 1.2 - 6, 1))
        del tw
        cr.restore()

    def _seek_row(self, cr, p, x0, x1, row_mid, color, dim, now, live, bar_color=None):
        length = getattr(p, "length", 0) or 0
        if length <= 0:
            return
        pos = p.position() if hasattr(p, "position") else 0
        frac = clamp(pos / length, 0.0, 1.0) if self.seek_drag is None else self.seek_drag
        shown = pos if self.seek_drag is None else self.seek_drag * length
        left_t = fmt_time(shown)
        right_t = "-" + fmt_time(length - shown)
        lw = text_width(cr, left_t, 11)
        rw = text_width(cr, right_t, 11)
        draw_text(cr, left_t, x0, row_mid, 11, (1, 1, 1, dim))
        draw_text(cr, right_t, x1, row_mid, 11, (1, 1, 1, dim), align="right")
        bx0, bx1 = x0 + lw + 8, x1 - rw - 8
        thick = 8.0 if self.seek_drag is not None else 4.0
        cr.save()
        rrect(cr, bx0, row_mid - thick / 2, bx1 - bx0, thick, thick / 2)
        cr.clip()
        cr.set_source_rgba(1, 1, 1, 0.2)
        cr.paint()
        cr.rectangle(bx0, row_mid - thick / 2, (bx1 - bx0) * frac, thick)
        cr.set_source_rgb(*(bar_color or color))
        cr.fill()
        cr.restore()
        if live:
            self.seek_rect = (bx0, bx1)
            self.hits.append((bx0, row_mid - 11, bx1, row_mid + 11, "seek"))

    def _buttons(self, cr, p, x0, x1, cy, live):
        playing = getattr(p, "status", "") == "Playing"
        sizes = [44.0, 52.0, 44.0]
        gap = ((x1 - x0) - sum(sizes)) / 4.0
        names = ["previous", "pause" if playing else "play", "next"]
        acts = ["prev", "playpause", "next"]
        px = x0 + gap
        for i, (sz, nm, act) in enumerate(zip(sizes, names, acts)):
            bcx = px + sz / 2.0
            if i == 1:
                cr.set_source_rgba(1, 1, 1, 0.16)
                cr.arc(bcx, cy, sz / 2.0, 0, 2 * math.pi)
                cr.fill()
            icon = 26.0 if i == 1 else 22.0
            draw_glyph(cr, nm, bcx - icon / 2, cy - icon / 2, icon, WHITE3)
            if live:
                self.hits.append((bcx - sz / 2, cy - sz / 2, bcx + sz / 2, cy + sz / 2, act))
            px += sz + gap

    def _exp_music_hyper(self, cr, p, bw, bh, now, live):
        pal = palette_of(p)
        playing = getattr(p, "status", "") == "Playing"
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        # The cover's colours drift as soft light behind everything, as if the cover spilled into the island.
        drift = 2.0 * math.pi * now / 9.0
        cw, ch = x1 - x0, max(y1 - y0, 1.0)
        strength = 0.5 if playing else 0.3
        for cx, cy, col in (
            (x0 + cw * (0.18 + 0.08 * math.sin(drift)), y0 + ch * (0.3 + 0.2 * math.cos(drift)), pal[0]),
            (x0 + cw * (0.82 + 0.08 * math.cos(drift * 1.3)), y0 + ch * (0.7 + 0.2 * math.sin(drift * 0.8)), pal[1 % len(pal)]),
            (x0 + cw * (0.5 + 0.25 * math.sin(drift * 0.7)), y0 + ch * (0.15 + 0.1 * math.cos(drift * 1.1)), pal[2 % len(pal)]),
        ):
            rg = cairo.RadialGradient(cx, cy, 0, cx, cy, cw * 0.55)
            rg.add_color_stop_rgba(0.0, *col[:3], strength)
            rg.add_color_stop_rgba(1.0, *col[:3], 0.0)
            cr.set_source(rg)
            cr.arc(cx, cy, cw * 0.55, 0, 2 * math.pi)
            cr.fill()
        ccx, ccy = x0 + 30, y0 + 30
        rg = cairo.RadialGradient(ccx, ccy, 0, ccx, ccy, 54)
        rg.add_color_stop_rgba(0.0, *pal[0][:3], 0.55)
        rg.add_color_stop_rgba(1.0, *pal[0][:3], 0.0)
        cr.set_source(rg)
        cr.arc(ccx, ccy, 54, 0, 2 * math.pi)
        cr.fill()
        self._cover(cr, p, x0, y0, 60, 16, pal)
        tx = x0 + 74
        mw = x1 - 34 - tx
        draw_text(cr, getattr(p, "title", "") or tr("nothing"), tx, y0 + 21, 17, WHITE, bold=True, max_w=mw)
        draw_text(cr, getattr(p, "artist", ""), tx, y0 + 42, 14, (1, 1, 1, 0.7), max_w=mw)
        self._wave(cr, x1 - 34, y0 + 20, 34, 20, pal, playing, now)
        if live:
            self.hits.append((x0, y0, x1, y0 + 60, "raise"))
        row_mid = y1 - 52 - 6 - 11
        self._seek_row(cr, p, x0, x1, row_mid, pal[0], 0.65, now, live)
        self._buttons(cr, p, x0, x1, y1 - 26, live)

    def _exp_music_classic(self, cr, p, bw, bh, now, live):
        pal = palette_of(p)
        playing = getattr(p, "status", "") == "Playing"
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        self._cover(cr, p, x0, y0 + 3, 54, 14, [(1, 1, 1), (0.6, 0.6, 0.6)] if getattr(p, "art", None) is None else pal)
        tx = x0 + 66
        mw = x1 - 28 - tx - 6
        draw_text(cr, getattr(p, "title", "") or tr("nothing"), tx, y0 + 19, 16, WHITE, bold=True, max_w=mw)
        draw_text(cr, getattr(p, "artist", ""), tx, y0 + 40, 14, (1, 1, 1, 0.65), max_w=mw)
        draw_equalizer(cr, x1 - 28, y0 + 14, 28, 22, pal[0], playing, now)
        if live:
            self.hits.append((x0, y0, x1, y0 + 60, "raise"))
        row_mid = y1 - 52 - 8 - 11
        self._seek_row(cr, p, x0, x1, row_mid, WHITE3, 0.6, now, live)
        self._buttons(cr, p, x0, x1, y1 - 26, live)

    def _exp_notify(self, cr, data, bw, bh, live):
        d = data or {}
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        self._app_icon(cr, d.get("icon"), x0, y0 + 5, 40)
        tx = x0 + 52
        mw = x1 - tx
        title = d.get("summary") or d.get("app") or tr("notification")
        th = draw_text_block(cr, title, tx, y0 + 0.5, 16, WHITE, mw, lines=2, bold=True)
        if d.get("body"):
            draw_text_block(cr, d["body"], tx, y0 + 0.5 + th + 2.0, 13, (1, 1, 1, 0.7), mw, lines=6)
        elif d.get("app"):
            draw_text(cr, d["app"], tx, y0 + 0.5 + th + 9.0, 13, (1, 1, 1, 0.7), max_w=mw)

    def _button(self, cr, x, y, w, h, label, fill, text_color, bold=True):
        rrect(cr, x, y, w, h, h / 2.0)
        cr.set_source_rgba(*fill)
        cr.fill()
        draw_text(cr, label, x + w / 2.0, y + h / 2.0, 14, text_color, bold=bold, align="center")

    def _exp_code(self, cr, data, bw, bh, live):
        d = data or {}
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        cy = (y0 + y1) / 2.0
        draw_glyph(cr, "lock", x0, cy - 18, 36, GREEN)
        label = "Kopiert" if d.get("copied") else "Kopieren"
        bwid = text_width(cr, label, 14, True) + 32
        tx = x0 + 50
        draw_text(cr, "Code · %s" % (d.get("app") or ""), tx, cy - 17, 13, (1, 1, 1, 0.7), max_w=x1 - tx - bwid - 12)
        draw_text(cr, d.get("code", ""), tx, cy + 8, 30, WHITE, bold=True, spacing=3, tnum=True)
        self._button(cr, x1 - bwid, cy - 18, bwid, 36, label, (1, 1, 1, 0.16) if d.get("copied") else (*GREEN, 1.0), (*GREEN, 1.0) if d.get("copied") else (0, 0, 0, 1))
        if live:
            self.hits.append((x1 - bwid, cy - 18, x1, cy + 18, "copy"))

    def _exp_screenshot(self, cr, data, bw, bh, live):
        d = data or {}
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        cy = (y0 + y1) / 2.0
        thumb = d.get("thumb")
        rrect(cr, x0, cy - 48, 62, 96, 12)
        cr.set_source_rgba(1, 1, 1, 0.1)
        cr.fill()
        if thumb is not None:
            cr.save()
            rrect(cr, x0, cy - 48, 62, 96, 12)
            cr.clip()
            sc = max(62.0 / thumb.get_width(), 96.0 / thumb.get_height())
            cr.translate(x0 + (62 - thumb.get_width() * sc) / 2.0, cy - 48 + (96 - thumb.get_height() * sc) / 2.0)
            cr.scale(sc, sc)
            cr.set_source_surface(thumb, 0, 0)
            cr.paint()
            cr.restore()
        if live:
            self.hits.append((x0, cy - 48, x0 + 62, cy + 48, "view"))
        tx = x0 + 76
        draw_text(cr, "Bildschirmfoto", tx, cy - 26, 17, WHITE, bold=True)
        draw_text(cr, "Gespeichert", tx, cy - 6, 13, (1, 1, 1, 0.6))
        bx = tx
        for label, act, fill, col in (("Kopieren", "copy", (1, 1, 1, 0.16), WHITE), ("Ansehen", "view", (1, 1, 1, 0.9), (0, 0, 0, 1))):
            wb = text_width(cr, label, 13, True) + 28
            self._button(cr, bx, cy + 10, wb, 32, label, fill, col)
            if live:
                self.hits.append((bx, cy + 10, bx + wb, cy + 42, act))
            bx += wb + 8

    def _exp_alert(self, cr, kind, data, bw, bh, now, age, live):
        al = alert_of(kind, data)
        x0, x1, y0, y1 = self._exp_origin(bw, bh)
        cy = (y0 + y1) / 2.0
        self._alert_glyph(cr, al, x0, cy - 20, 40, now, age)
        draw_text(cr, al["title"], x0 + 54, cy - 11, 18, WHITE, bold=True, max_w=x1 - x0 - 54 - (62 if al["level"] is not None else 0))
        draw_text(cr, al["value"], x0 + 54, cy + 11, 15, (*al["color"][:3], 1.0), max_w=x1 - x0 - 54 - (62 if al["level"] is not None else 0))
        if al["level"] is not None:
            self._level_bar(cr, x1 - 52, cy - 12, 52, 24, al["level"], al["color"], al["charging"], now, age)

    def _level_bar(self, cr, x, y, w, h, level, color, charging, now, age):
        """A small battery that fills up from empty when it appears; the charging one has a rocking edge, a gleam and a bolt."""
        target = clamp(level, 0.0, 1.0)
        fill = target * clamp(spring_curve(age - 0.1, 90, 0.75), 0.0, 1.05)
        low = (not charging) and target <= 0.2
        cr.save()
        cr.translate(x, y)
        nub = w * 0.08
        body_w = w - nub
        r = h * 0.3
        cr.set_source_rgba(1, 1, 1, 0.35)
        cr.set_line_width(h * 0.1)
        rrect(cr, h * 0.05, h * 0.05, body_w - h * 0.1, h - h * 0.1, r)
        cr.stroke()
        rrect(cr, body_w + nub * 0.25, h * 0.32, nub * 0.75, h * 0.36, nub)
        cr.fill()
        pad = h * 0.18
        iw, ih = body_w - 2 * pad, h - 2 * pad
        fw = iw * clamp(fill, 0.0, 1.0)
        if fw > 0.5:
            alpha = 0.45 + 0.55 * (0.5 - 0.5 * math.cos(2.0 * math.pi * now / 1.3)) if low else 1.0
            cr.save()
            rrect(cr, pad, pad, iw, ih, r)
            cr.clip()
            amp = ih * 0.12 if charging else 0.0
            phase = 2.0 * math.pi * now / 1.1
            cr.new_path()
            cr.move_to(pad, pad)
            for i in range(9):
                yy = pad + ih * i / 8.0
                cr.line_to(max(pad + fw + amp * math.sin(phase + i * 0.9), pad), yy)
            cr.line_to(pad, pad + ih)
            cr.close_path()
            cr.set_source_rgba(*color[:3], alpha)
            cr.fill()
            if charging:
                gl = -0.4 + 1.8 * ((now % 1.7) / 1.7)
                gx = pad + iw * gl
                g = cairo.LinearGradient(gx - iw * 0.25, 0, gx + iw * 0.25, 0)
                g.add_color_stop_rgba(0.0, 1, 1, 1, 0.0)
                g.add_color_stop_rgba(0.5, 1, 1, 1, 0.55)
                g.add_color_stop_rgba(1.0, 1, 1, 1, 0.0)
                cr.rectangle(pad, pad, fw, ih)
                cr.set_source(g)
                cr.fill()
            cr.restore()
        if charging:
            bcx, bcy, bh2 = body_w / 2.0, h / 2.0, h * 0.62
            cr.new_path()
            for i, (px, py) in enumerate(((0.12, -0.5), (-0.22, 0.06), (0.02, 0.06), (-0.12, 0.5), (0.22, -0.06), (-0.02, -0.06))):
                (cr.move_to if i == 0 else cr.line_to)(bcx + bh2 * px, bcy + bh2 * py)
            cr.close_path()
            cr.set_source_rgba(0, 0, 0, 0.55)
            cr.set_line_width(h * 0.08)
            cr.set_line_join(cairo.LINE_JOIN_ROUND)
            cr.stroke_preserve()
            cr.set_source_rgb(1, 1, 1)
            cr.fill()
        cr.restore()


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
        self.palette = None
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
                    self.art, self.palette = None, None
                changed.add("art")
        if "Position" in props:
            try:
                self.set_position(int(props["Position"]))
            except (TypeError, ValueError):
                pass
        return changed


def pixbuf_to_surface(pb):
    """A GdkPixbuf as a Cairo surface, and the cover's colours (up to three, bright enough to read on black)."""
    w, h, rs, nc = pb.get_width(), pb.get_height(), pb.get_rowstride(), pb.get_n_channels()
    data = pb.get_pixels()
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    stride = surf.get_stride()
    surf.flush()
    buf = surf.get_data()
    bins = {}   # hue bin -> [weight, r, g, b]
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
            if (x + y) % 2 == 0 and a > 128:
                mx, mn = max(r, g, b), min(r, g, b)
                sat = 0.0 if mx == 0 else (mx - mn) / mx
                val = mx / 255.0
                if sat > 0.28 and val > 0.28:
                    if mx == mn:
                        hue = 0.0
                    elif mx == r:
                        hue = ((g - b) / (mx - mn)) % 6
                    elif mx == g:
                        hue = (b - r) / (mx - mn) + 2
                    else:
                        hue = (r - g) / (mx - mn) + 4
                    slot = bins.setdefault(int(hue * 2) % 12, [0.0, 0.0, 0.0, 0.0])
                    wgt = sat * val
                    slot[0] += wgt
                    slot[1] += r * wgt
                    slot[2] += g * wgt
                    slot[3] += b * wgt
    surf.mark_dirty()
    colours = []
    for slot_id, slot in sorted(bins.items(), key=lambda kv: -kv[1][0]):
        if slot[0] <= 0:
            continue
        if any(min(abs(slot_id - o), 12 - abs(slot_id - o)) < 2 for o in [c[1] for c in colours]):
            continue
        r, g, b = slot[1] / slot[0] / 255.0, slot[2] / slot[0] / 255.0, slot[3] / slot[0] / 255.0
        k = 0.95 / max(r, g, b, 0.01)
        colours.append(((min(r * k, 1.0), min(g * k, 1.0), min(b * k, 1.0)), slot_id))
        if len(colours) == 3:
            break
    palette = [c[0] for c in colours] or [(0.9, 0.9, 0.95)]
    return surf, palette


def load_art_async(url, done):
    """Fetches cover art off the main thread; done(surface, palette) runs on it (through idle_add)."""
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
            surf, palette = pixbuf_to_surface(pb)
            GLib.idle_add(done, surf, palette)
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
        super().__init__(application_id="dev.hearth.glimmer.linux",
                         flags=Gio.ApplicationFlags.NON_UNIQUE if (demo or not layer_shell) else Gio.ApplicationFlags(0))
        self.cfg = cfg
        self.demo = demo
        self.use_layer = layer_shell
        self.island = Island(effective_config(cfg))
        self.W, self.H = window_size(cfg["scale"])
        self.win = None
        self.area = None
        self.surface = None
        self.layered = False
        self.mpris = Mpris(self)
        self.transient = None
        self.pending = []
        self._expire_id = 0
        self.expanded = False       # unfolded (a tap): the big card instead of the small one
        self._collapse_id = 0
        self._hover_expanded = False
        self._d0 = None
        self._dseek = False
        self._leave_id = 0
        self._dwell_id = 0
        self._inside = False
        self.settings = None
        self.open_settings_at_start = False
        self.config_path = CONFIG_PATH
        self.demo_button = None
        self._demo_ids = set()
        self._demo_on = False
        self._restart_id = 0
        self._clock_id = 0
        self._clock_minute = ""
        self.privacy_state = None
        self._last_tap = 0.0
        self._single_id = 0
        self._screen_monitors = []
        self._screen_seen = set()
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
            self.open_settings()  # started a second time (from the menu): the settings window
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
        drag = Gtk.GestureDrag()
        drag.set_button(1)
        drag.connect("drag-begin", self.on_drag_begin)
        drag.connect("drag-update", self.on_drag_update)
        drag.connect("drag-end", self.on_drag_end)
        area.add_controller(drag)
        right = Gtk.GestureClick()
        right.set_button(3)
        right.connect("pressed", self.on_right_click)
        area.add_controller(right)

        win.connect("map", self.on_map)
        win.present()
        self.start_sources()
        if self.open_settings_at_start:
            self.open_settings()

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

    def eff(self):
        return effective_config(self.cfg)

    def refresh(self):
        isl = self.island
        p = self.active()
        cfg, eff = self.cfg, self.eff()
        isl.privacy = self.privacy_state if eff["privacy"] else None
        if not cfg["enabled"]:
            self._fold()
            isl.hide()
        elif self.transient is not None:
            t = self.transient
            isl.show(t["kind"], t["data"], self.expanded)
        elif p is not None and cfg["music"] and (self.expanded or self.music_visible()):
            isl.show("music", p, self.expanded)
            if self.expanded:
                self._start_pos_timer()
        elif eff["charging_live"] and self._power is not None and self._power["plugged"]:
            isl.show("charging", {"pct": self._power["pct"]}, self.expanded)
        elif eff["idle_pill"] or isl.privacy:
            self._fold()
            isl.show("idle", None, False)  # (with the camera or the microphone in use the island is there for its dot, whatever was chosen)
        else:
            self._fold()
            isl.hide()
        self._clock_sync()
        self.update_input()
        self.kick()

    def _clock_sync(self):
        """While the idle island shows the time, look once a second whether the minute changed (nothing else wakes up)."""
        want = self.island.kind == "idle" and self.island.visible and self.cfg["idle_clock"]
        if want and not self._clock_id:
            self._clock_minute = time.strftime("%H:%M")

            def tick():
                if not (self.island.kind == "idle" and self.island.visible and self.cfg["idle_clock"]):
                    self._clock_id = 0
                    return False
                now = time.strftime("%H:%M")
                if now != self._clock_minute:
                    self._clock_minute = now
                    if self.area is not None:
                        self.area.queue_draw()
                return True
            self._clock_id = GLib.timeout_add_seconds(1, tick)

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
        self._fold()
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
        self._fold()
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
        self._fold()
        self.refresh()

    # --- unfolding

    def _fold(self):
        """Folded again (without the refresh): what was unfolded is over."""
        self.expanded = False
        self._hover_expanded = False
        if self._collapse_id:
            GLib.source_remove(self._collapse_id)
            self._collapse_id = 0

    def set_expanded(self, on):
        if on and self.island.kind in (None, "idle"):
            return
        if on == self.expanded:
            return
        self.expanded = on
        if self._collapse_id:
            GLib.source_remove(self._collapse_id)
            self._collapse_id = 0
        secs = int(self.cfg["auto_collapse"])
        if on:
            if secs > 0:
                self._collapse_id = GLib.timeout_add(secs * 1000, self._auto_collapse)
            if self.transient is not None:  # (what is unfolded stays until it is folded)
                self.transient["until"] = max(self.transient["until"], time.monotonic() + max(secs, 3) + 1.0)
                self._arm()
        else:
            self._hover_expanded = False
            if self.transient is not None:
                self.transient["until"] = min(self.transient["until"], time.monotonic() + 1.2)
                self._arm()
        self.refresh()

    def _auto_collapse(self):
        self._collapse_id = 0
        self.set_expanded(False)
        return False

    # --- mouse: a tap unfolds and folds, a pull down unfolds, a pull up folds, sideways over music skips the track

    def on_enter(self, ctrl, x, y, *rest):
        self._inside = True
        if self._leave_id:
            GLib.source_remove(self._leave_id)
            self._leave_id = 0
        if not self.cfg["hover_reveal"] or self.transient is not None or not self.cfg["music"] or self.active() is None:
            return
        if not self._dwell_id:  # (a moment's wait, so that passing by on the way to a window's title bar does not unfold it)
            self._dwell_id = GLib.timeout_add(320, self._dwell)

    def _dwell(self):
        self._dwell_id = 0
        if self._inside and self.transient is None and self.active() is not None and not self.expanded:
            self._hover_expanded = True
            self.expanded = True
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
            if self._hover_expanded:
                self.set_expanded(False)
            return False
        self._leave_id = GLib.timeout_add(450, later)

    def on_drag_begin(self, gesture, x, y):
        isl = self.island
        self._d0 = (x, y)
        act = isl.hit(x, y)
        self._dseek = bool(act and act[0] == "seek")
        if self._dseek:
            isl.seek_drag = act[1]
        isl.set_pressed(True)  # it swells a little under the pointer
        self.kick()

    def on_drag_update(self, gesture, ox, oy):
        if self._dseek and self._d0 is not None:
            self.island.seek_drag = self.island.seek_fraction(self._d0[0] + ox)
            self.kick()

    def on_drag_end(self, gesture, ox, oy):
        isl = self.island
        isl.set_pressed(False)
        self.kick()
        if self._d0 is None:
            return
        x0, y0 = self._d0
        self._d0 = None
        p = self.active()
        if self._dseek:
            self._dseek = False
            frac = isl.seek_fraction(x0 + ox)
            isl.seek_drag = None
            if p is not None:
                self.mpris.seek_to(p, frac)
            return
        if math.hypot(ox, oy) < 6:
            self._tap(x0, y0)
            return
        if isl.kind in (None, "idle"):
            return
        threshold = 18 * isl.s
        vertical = abs(oy) > abs(ox)
        if vertical and oy > threshold and not self.expanded:
            self.set_expanded(True)
        elif vertical and oy < -threshold and self.expanded:
            self.set_expanded(False)
        elif (not vertical and abs(ox) > threshold * 2 and not self.expanded and isl.kind == "music" and p is not None
              and self.eff()["swipe_tracks"]):
            forward = ox < 0
            self.mpris.call(p, "Next" if forward else "Previous")
            isl.nudge.x = -1.0 if forward else 1.0  # the content leans the way the track goes
            isl.nudge.v = 0.0
            isl.nudge.target = 0.0

    def _tap(self, x, y):
        """A tap: with a double tap set, it waits a moment for the second one (without, taps are not held back)."""
        now = time.monotonic()
        isl = self.island
        if isl.kind == "idle":
            self.open_settings()  # (the empty notch is the app's door)
            return
        if isl.kind is None:
            return
        if self.eff()["double_tap"] != "off" and not (self.expanded and isl.hit(x, y)):
            if now - self._last_tap < 0.32:
                self._last_tap = 0.0
                if self._single_id:
                    GLib.source_remove(self._single_id)
                    self._single_id = 0
                self._double_tap_action()
                return
            self._last_tap = now
            if self._single_id:
                GLib.source_remove(self._single_id)
            self._single_id = GLib.timeout_add(330, lambda: (self._single_tap(x, y), False)[1])
            return
        self._single_tap(x, y)

    def _single_tap(self, x, y):
        self._single_id = 0
        isl = self.island
        p = self.active()
        if isl.kind is None or isl.kind == "idle":
            return
        act = isl.hit(x, y) if self.expanded else None
        if act:
            action, value = act
            if action in ("prev", "next", "playpause") and p is not None and isl.kind == "music":
                self.mpris.call(p, {"prev": "Previous", "next": "Next", "playpause": "PlayPause"}[action])
                if action == "playpause" and p.owner.startswith(":demo"):
                    p.apply({"PlaybackStatus": "Paused" if p.status == "Playing" else "Playing"})
                    self.refresh()
            elif action == "raise" and p is not None:
                self.mpris.raise_player(p)
            elif action == "copy":
                self.copy_content()
            elif action == "view":
                self.open_content()
            self.kick()
            return
        # A code is copied with one tap, without unfolding first; with "tap opens the app" the app opens, holding unfolds.
        if not self.expanded and (self.eff()["tap_opens"] or isl.kind == "code"):
            self.open_content()
            return
        self.set_expanded(not self.expanded)

    def _hold(self):
        """What holding does on the phone (here: the right mouse button): unfolds when a tap opens the app, otherwise opens the app."""
        isl = self.island
        if isl.kind in (None, "idle"):
            return
        if self.eff()["tap_opens"] and not self.expanded:
            self.set_expanded(True)
        else:
            self.open_content()

    def open_content(self):
        """Opens the app behind what is shown: the player for music, the picture for a screenshot, copies a code; the rest goes away."""
        isl = self.island
        p = self.active()
        if isl.kind == "music" and p is not None:
            self.mpris.raise_player(p)
        elif isl.kind == "screenshot":
            path = (isl.data or {}).get("path")
            if path:
                self._run(["xdg-open", path])
            self.dismiss()
        elif isl.kind == "code":
            self.copy_content()
        elif isl.kind in ("volume", "power", "notify", "alert", "charging"):
            self.dismiss()

    def copy_content(self):
        """Copies the code (or the screenshot's picture) – to the clipboard, through GTK and, where there is one, wl-copy."""
        isl = self.island
        d = isl.data or {}
        if isl.kind == "code":
            self._to_clipboard(text=d.get("code", ""))
            d["copied"] = True
            if self.transient is not None:
                self.transient["until"] = max(self.transient["until"], time.monotonic() + 2.0)
                self._arm()
            self.refresh()
        elif isl.kind == "screenshot" and d.get("path"):
            self._to_clipboard(path=d["path"])

    def _to_clipboard(self, text=None, path=None):
        try:
            clip = Gdk.Display.get_default().get_clipboard()
            if text is not None:
                clip.set(text)
            elif path:
                clip.set_texture(Gdk.Texture.new_from_filename(path))
        except Exception as e:  # noqa: BLE001
            log("clipboard:", e)
        wl = shutil.which("wl-copy")
        if wl:
            try:
                if text is not None:
                    subprocess.Popen([wl, "--", text], env=CLEAN_ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                elif path:
                    subprocess.Popen([wl], stdin=open(path, "rb"), env=CLEAN_ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except OSError as e:
                log("wl-copy:", e)

    @staticmethod
    def _run(cmd):
        try:
            subprocess.Popen(cmd, env=CLEAN_ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        except OSError as e:
            log("run:", cmd, e)

    def _double_tap_action(self):
        """What a double tap does (set in the settings): Play/Pause, or Clawd answers right in the island."""
        what = self.eff()["double_tap"]
        self.set_expanded(False)
        p = self.active()
        if what == "play_pause" and p is not None:
            self.mpris.call(p, "PlayPause")
            if p.owner.startswith(":demo"):
                p.apply({"PlaybackStatus": "Paused" if p.status == "Playing" else "Playing"})
                self.refresh()
        elif what == "oracle":
            self.flash("spark", random.choice(ORACLE), "Clawd-Orakel", CLAWD_ORANGE)
        elif what == "coin":
            side = random.choice(["Kopf", "Zahl"]) if random.randint(1, 250) != 1 else "Kante!"
            self.flash("spark", side, "Münze 🪙", (0.949, 0.757, 0.259))
        elif what == "dice":
            self.flash("spark", "🎲 %d" % random.randint(1, 6), "Clawd würfelt", CLAWD_ORANGE)

    def flash(self, glyph, title, value, color, level=None, seconds=3.5):
        """A short moment with a symbol, a title and a value (headphones, Do Not Disturb, Clawd's hello …)."""
        self.post("alert", {"glyph": glyph, "title": title, "value": value, "color": color, "level": level}, seconds, 3)

    def on_right_click(self, gesture, n, x, y, *rest):
        self._hold()

    # --- events

    def on_player(self, p, changed, initial=False):
        if "art" in changed and p.art_url:
            url = p.art_url

            def done(surf, palette):
                if p.art_url == url:
                    p.art, p.palette = surf, palette
                    self.kick()
                    if self.area is not None:
                        self.area.queue_draw()
                return False
            load_art_async(url, done)
        if "status" in changed and p.status == "Paused":
            GLib.timeout_add(4200, lambda: (self.refresh(), False)[1])
        self.refresh()

    def on_volume(self, pct, muted):
        if self.cfg["volume"]:
            self.post("volume", {"pct": pct, "muted": muted}, 1.7, 1)
        return False

    def on_power(self, info):
        prev, self._power = self._power, info
        cfg = self.cfg
        if info is None:
            return False
        if cfg["charging_live"] and (prev is None or prev["plugged"] != info["plugged"] or prev["pct"] != info["pct"]):
            self.refresh()  # (the charge stays in the island while it is plugged in)
        if prev is None:
            return False
        pct = info["pct"]
        if cfg["charging"]:
            if info["plugged"] and not prev["plugged"]:
                self.post("power", {"state": "full" if info["full"] else "charging", "pct": pct}, 2.6, 2)
                return False
            if not info["plugged"] and prev["plugged"]:
                self.post("power", {"state": "unplugged", "pct": pct}, 1.8, 2)
                return False
        if cfg["alerts"] and info["plugged"] and info["full"] and not prev["full"]:
            self.post("power", {"state": "full", "pct": pct}, 2.6, 2)
            return False
        if cfg["low_battery"] and not info["plugged"]:
            for limit in (10, 20):
                if pct <= limit < prev["pct"]:
                    self.post("power", {"state": "low", "pct": pct}, 3.4, 3)
                    break
        return False

    def on_notification(self, body):
        eff = self.eff()
        if not eff["notifications"] and not eff["codes"]:
            return False
        try:
            app, _rid, icon, summary, text, _actions, hints, timeout = body
        except (TypeError, ValueError):
            return False
        if "x-canonical-private-synchronous" in hints or (not summary and not text):
            return False
        text = html.unescape(re.sub(r"<[^>]+>", "", text or "")).strip()
        dur = clamp(timeout / 1000.0, 3.5, 8.0) if timeout and timeout > 0 else 5.0
        picture = self.load_icon(icon, hints, app)
        # A confirmation code (bank, login) sits in the island, ready to copy – as on vivo and OPPO.
        code = extract_code("%s %s" % (summary or "", text)) if eff["codes"] else None
        if code:
            self.post("code", {"code": code, "app": app, "icon": picture, "copied": False}, max(dur, 8.0), 3)
        elif eff["notifications"]:
            self.post("notify", {"app": app, "summary": summary, "body": text, "icon": picture}, dur, 3)
        return False

    def load_icon(self, name, hints=None, app_name=""):
        """The app's picture for a notification: a picture sent with it, a file, a named icon of the theme, or the one of the program's
        desktop entry. None if there is none (then a bell is drawn)."""
        hints = hints or {}
        size = 64
        try:
            data = hints.get("image-data") or hints.get("image_data") or hints.get("icon_data")
            if data and len(data) >= 7:
                w, h, rowstride, alpha, bits, channels, raw = data[:7]
                pb = GdkPixbuf.Pixbuf.new_from_bytes(GLib.Bytes.new(bytes(raw)), GdkPixbuf.Colorspace.RGB, bool(alpha), bits, w, h, rowstride)
                return pixbuf_to_surface(pb.scale_simple(size, size, GdkPixbuf.InterpType.BILINEAR))[0]
        except Exception as e:  # noqa: BLE001
            log("image-data:", e)
        names = []
        for key in ("image-path", "image_path"):
            if hints.get(key):
                names.append(str(hints[key]))
        if name:
            names.append(name)
        entry = hints.get("desktop-entry")
        if entry:
            try:
                info = Gio.DesktopAppInfo.new(str(entry) + ".desktop")
                gicon = info.get_icon() if info is not None else None
                if gicon is not None and hasattr(gicon, "get_names"):
                    names += list(gicon.get_names())
                elif gicon is not None and hasattr(gicon, "get_file"):
                    names.append(gicon.get_file().get_path())
            except Exception as e:  # noqa: BLE001
                log("desktop entry:", e)
        if app_name:
            names.append(app_name.lower())
        for n in names:
            surf = self._icon_surface(n, size)
            if surf is not None:
                return surf
        return None

    def _icon_surface(self, name, size):
        try:
            path = None
            if name.startswith("file://"):
                path = GLib.filename_from_uri(name)[0]
            elif name.startswith("/"):
                path = name
            if path:
                return pixbuf_to_surface(GdkPixbuf.Pixbuf.new_from_file_at_size(path, size, size))[0]
            theme = Gtk.IconTheme.get_for_display(Gdk.Display.get_default())
            if not theme.has_icon(name):
                return None
            paintable = theme.lookup_icon(name, None, size, 1, Gtk.TextDirection.NONE, 0)
            # (The theme draws it itself, so svg and every other kind work as they do everywhere else on the desktop.)
            snapshot = Gtk.Snapshot()
            paintable.snapshot(snapshot, size, size)
            node = snapshot.to_node()
            if node is None:
                return None
            surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
            node.draw(cairo.Context(surf))
            return surf
        except Exception as e:  # noqa: BLE001
            log("icon:", name, e)
            return None

    # --- the sources of events

    def start_sources(self):
        # (All sources run; what is shown is decided by the settings, so they can be switched on and off while Glimmer runs.)
        self.mpris.start()
        threading.Thread(target=self._volume_loop, daemon=True).start()
        threading.Thread(target=self._privacy_loop, daemon=True).start()
        self._power_tick()
        GLib.timeout_add_seconds(2, self._power_tick)
        self._start_notifications()
        self._start_screenshots()
        self._start_dnd()
        self._start_bluetooth()
        self.refresh()  # (the island is there from the first moment, not only after the first event)
        os.environ.pop("_GLIMMER_GREET", None)
        if self.demo:
            self.start_demo()
        else:
            self._clawd_hello()

    def _clawd_hello(self):
        """Clawd says hello (on the first start of the day a greeting for the time of day), if he is switched on and may."""
        cfg = self.cfg
        if not (cfg["clawd"] and cfg["clawd_greeting"]):
            return
        now = time.localtime()
        today = time.strftime("%Y-%m-%d")
        marker = os.path.join(os.environ.get("XDG_CACHE_HOME") or os.path.expanduser("~/.cache"), "glimmer-greeted")
        try:
            with open(marker, "r") as f:
                seen = f.read().strip()
        except OSError:
            seen = ""
        first_today = seen != today and now.tm_hour >= 5
        if cfg["clawd_greet_daily"] and not first_today:
            return
        if first_today:
            try:
                with open(marker, "w") as f:
                    f.write(today)
            except OSError:
                pass
        line = clawd_hello(now.tm_hour, first_today)
        GLib.timeout_add(1600, lambda: (self.flash("spark", line, "Clawd", CLAWD_ORANGE), False)[1])

    # --- settings window, options

    def open_settings(self):
        if self.settings is None:
            self.settings = build_settings(self)
        self.settings.present()

    def set_option(self, key, value):
        if self.cfg.get(key) == value:
            return
        self.cfg[key] = value
        self._options_changed(key)

    def _options_changed(self, key=None):
        save_config(self.cfg, self.config_path)
        if key == "language":
            set_language(self.cfg["language"])
        elif key in ("scale", "layer", "monitor"):
            self._schedule_restart()  # the size of the window is fixed at the start
        self.island.configure(self.eff())   # (the island moves to the new shape, look and size by itself)
        self.refresh()
        if self.area is not None:
            self.area.queue_draw()
        if self.settings is not None and hasattr(self.settings, "refresh_preview"):
            self.settings.refresh_preview()  # (the preview in the window follows at once)

    def reset_options(self, keys=None):
        """Everything back as it came (or just the [keys]); the window is rebuilt to show it."""
        old_scale = self.cfg["scale"]
        for k, v in DEFAULTS.items():
            if k in LINUX_ONLY or (keys is not None and k not in keys):
                continue
            self.cfg[k] = v
        set_language(self.cfg["language"])
        self._options_changed("scale" if self.cfg["scale"] != old_scale else None)  # (only a different size needs the restart)
        old, self.settings = self.settings, None
        if old is not None:
            old.destroy()
            self.demo_button = None
            self.open_settings()

    def _schedule_restart(self):
        if self._restart_id:
            GLib.source_remove(self._restart_id)
        self._restart_id = GLib.timeout_add(900, self._restart)

    def _restart(self):
        self._restart_id = 0
        env = dict(os.environ)
        env["_GLIMMER_GREET"] = "1"
        args, skip = [sys.executable], False
        for a in sys.argv:
            if skip:
                skip = False
            elif a in ("--mode", "--scale"):
                skip = True  # (what was typed on the command line must not beat what was just chosen)
            elif not a.startswith(("--mode=", "--scale=")):
                args.append(a)
        if "--settings" not in args:
            args.append("--settings")
        try:
            os.execve(sys.executable, args, env)
        except OSError as e:
            print("Glimmer: Neustart nicht möglich (%s) – bitte Glimmer selbst beenden und neu starten." % e, file=sys.stderr)
        return False

    def quit_app(self):
        self.quit()
        GLib.timeout_add(2000, lambda: os._exit(0))  # (should something hold on, it ends anyway)

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

    # --- screenshots: a new picture in the screenshots folder appears in the island as a small picture

    def _start_screenshots(self):
        pics = GLib.get_user_special_dir(GLib.UserDirectory.DIRECTORY_PICTURES) or os.path.expanduser("~/Pictures")
        dirs = []
        for d in (os.path.join(pics, "Screenshots"), pics, os.path.expanduser("~/Pictures/Screenshots"), os.path.expanduser("~/Bilder/Bildschirmfotos"),
                  os.path.expanduser("~/Bilder/Screenshots")):
            if os.path.isdir(d) and d not in dirs:
                dirs.append(d)
        for d in dirs:
            try:
                mon = Gio.File.new_for_path(d).monitor_directory(Gio.FileMonitorFlags.NONE, None)
                mon.connect("changed", self._on_file_changed)
                self._screen_monitors.append(mon)
            except GLib.Error as e:
                log("monitor:", d, e.message)

    def _on_file_changed(self, monitor, file, other, event):
        if event != Gio.FileMonitorEvent.CHANGES_DONE_HINT:
            return
        path = file.get_path()
        name = os.path.basename(path or "").lower()
        if not path or name.startswith(".") or not name.endswith((".png", ".jpg", ".jpeg", ".webp")) or path in self._screen_seen:
            return
        try:
            if time.time() - os.path.getmtime(path) > 10:
                return
        except OSError:
            return
        self._screen_seen.add(path)
        if not self.eff()["screenshots"]:
            return
        thumb = None
        try:
            thumb = pixbuf_to_surface(GdkPixbuf.Pixbuf.new_from_file_at_size(path, 192, 192))[0]
        except Exception as e:  # noqa: BLE001
            log("screenshot thumb:", e)
        self.post("screenshot", {"path": path, "thumb": thumb}, 6.0, 3)

    # --- privacy: a green dot while a program uses the camera, an orange one for the microphone

    def _privacy_loop(self):
        last = None
        while True:
            time.sleep(2.5)
            if not self.cfg["privacy"]:
                state = None
            elif camera_in_use():
                state = "camera"
            elif microphone_in_use():
                state = "mic"
            else:
                state = None
            if state != last:
                last = state
                GLib.idle_add(self.on_privacy, state)

    def on_privacy(self, state):
        self.privacy_state = state
        self.refresh()
        return False

    # --- Do Not Disturb (KDE and others tell the bus), and Bluetooth headphones

    def _start_dnd(self):
        try:
            bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
            bus.signal_subscribe(None, "org.freedesktop.DBus.Properties", "PropertiesChanged", "/org/freedesktop/Notifications", None,
                                 Gio.DBusSignalFlags.NONE, self._on_dnd)
        except GLib.Error as e:
            log("dnd:", e.message)

    def _on_dnd(self, conn, sender, path, iface, sig, params, *rest):
        try:
            name, changed, _ = params.unpack()
        except (TypeError, ValueError):
            return
        if name == "org.freedesktop.Notifications" and "Inhibited" in changed and self.cfg["alerts"]:
            self.flash("moon", "Nicht stören", "An" if changed["Inhibited"] else "Aus", (0.749, 0.353, 0.949))

    def _start_bluetooth(self):
        try:
            bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
            bus.signal_subscribe("org.bluez", "org.freedesktop.DBus.Properties", "PropertiesChanged", None, "org.bluez.Device1",
                                 Gio.DBusSignalFlags.NONE, self._on_bluetooth)
            self._bt_bus = bus
        except GLib.Error as e:
            log("bluetooth:", e.message)

    def _on_bluetooth(self, conn, sender, path, iface, sig, params, *rest):
        try:
            name, changed, _ = params.unpack()
        except (TypeError, ValueError):
            return
        if name == "org.bluez.Device1" and changed.get("Connected") is True and self.cfg["alerts"]:
            GLib.timeout_add(1500, lambda: (self._announce_headphones(path), False)[1])  # (the battery is told a moment after connecting)

    def _announce_headphones(self, path):
        bus = getattr(self, "_bt_bus", None)
        if bus is None:
            return

        def device(b, res, *rest):
            try:
                props = b.call_finish(res).unpack()[0]
            except GLib.Error:
                return
            if "audio" not in str(props.get("Icon", "")):
                return  # (a mouse or a keyboard is not headphones)
            alias = str(props.get("Alias") or props.get("Name") or "Kopfhörer")

            def battery(b2, res2, *rest2):
                level = None
                try:
                    level = b2.call_finish(res2).unpack()[0].get("Percentage")
                except GLib.Error:
                    pass
                if level is None:
                    self.flash("headphones", alias, "Verbunden", GREEN)
                else:
                    self.flash("headphones", alias, "%d %%" % level, RED if level <= 20 else GREEN, level / 100.0)
            b.call("org.bluez", path, "org.freedesktop.DBus.Properties", "GetAll", GLib.Variant("(s)", ("org.bluez.Battery1",)),
                   GLib.VariantType("(a{sv})"), Gio.DBusCallFlags.NONE, 1500, None, battery)
        bus.call("org.bluez", path, "org.freedesktop.DBus.Properties", "GetAll", GLib.Variant("(s)", ("org.bluez.Device1",)),
                 GLib.VariantType("(a{sv})"), Gio.DBusCallFlags.NONE, 1500, None, device)

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

    def demo_running(self):
        return self._demo_on

    def _demo_after(self, ms, fn):
        holder = []

        def run():
            self._demo_ids.discard(holder[0])
            fn()
            return False
        holder.append(GLib.timeout_add(ms, run))
        self._demo_ids.add(holder[0])

    def start_demo(self):
        if self._demo_on:
            return
        self._demo_on = True
        self._demo_cycle()
        self._sync_demo_button()

    def stop_demo(self):
        if not self._demo_on:
            return
        self._demo_on = False
        for i in list(self._demo_ids):
            GLib.source_remove(i)
        self._demo_ids.clear()
        self.mpris.players.pop(":demo", None)
        self.dismiss()
        self._sync_demo_button()

    def _sync_demo_button(self):
        if self.demo_button is not None:
            self.demo_button.set_label("Vorführung beenden" if self._demo_on else "Vorführung starten")

    def _demo_cycle(self):
        if not self._demo_on:
            return
        p = demo_player(self.cfg)
        self.mpris.players[":demo"] = p
        self.refresh()
        steps = [
            (3.0, lambda: self.set_expanded(True)),
            (8.5, lambda: self.set_expanded(False)),
            (10.5, lambda: [self._demo_after(int(i * 380), (lambda v: lambda: self.on_volume(v, False))(35 + i * 12)) for i in range(4)]),
            (14.0, lambda: self.post("power", {"state": "charging", "pct": 78}, 2.6, 2)),
            (18.0, lambda: self.post("notify", {"app": "Signal", "summary": "Mia", "body": "Kommst du später noch vorbei? Ich bring Kuchen mit.", "icon": None}, 4.0, 3)),
            (20.0, lambda: self.set_expanded(True)),
            (24.5, lambda: self.set_expanded(False)),
            (27.0, lambda: self.post("power", {"state": "low", "pct": 19}, 3.4, 3)),
            (31.0, lambda: self.post("code", {"code": "482913", "app": "Bank", "icon": None, "copied": False}, 7.0, 3)),
            (35.5, lambda: self.set_expanded(True)),
            (39.5, lambda: self.set_expanded(False)),
            (41.0, lambda: self.flash("headphones", "AirPods", "82 %", GREEN, 0.82)),
            (45.0, lambda: self.post("screenshot", {"path": "", "thumb": demo_art()}, 5.0, 3)),
            (46.5, lambda: self.set_expanded(True)),
        ]
        for delay, fn in steps:
            self._demo_after(int(delay * 1000), fn)
        self._demo_after(52000, self._demo_cycle)


# ------------------------------------------------------------------------------------------------------------------ the settings window

AUTOSTART_FILE = os.path.join(os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config"), "autostart", "glimmer.desktop")


def save_config(cfg, path=None):
    path = os.path.abspath(path or CONFIG_PATH)
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump({k: cfg[k] for k in DEFAULTS}, f, indent=2, ensure_ascii=False)
            f.write("\n")
    except OSError as e:
        print("Glimmer: die Einstellungen ließen sich nicht speichern (%s)." % e, file=sys.stderr)


def set_autostart(on):
    try:
        if on:
            exe = shutil.which("glimmer") or os.path.abspath(sys.argv[0])
            os.makedirs(os.path.dirname(AUTOSTART_FILE), exist_ok=True)
            with open(AUTOSTART_FILE, "w", encoding="utf-8") as f:
                f.write("[Desktop Entry]\nType=Application\nName=Glimmer\nComment=Die Insel am oberen Bildschirmrand\n"
                        "Exec=%s --background\nIcon=preferences-desktop-notification\nTerminal=false\nNoDisplay=true\n"
                        "X-KDE-autostart-after=panel\n" % exe)
        elif os.path.exists(AUTOSTART_FILE):
            os.remove(AUTOSTART_FILE)
    except OSError as e:
        print("Glimmer: der Autostart ließ sich nicht ändern (%s)." % e, file=sys.stderr)


APP_CSS = """
window.glimmer-app { background: #09090b; color: #ffffff; font-family: "Roboto", "Inter", "Noto Sans", "Cantarell", sans-serif; }
.glimmer-app scrolledwindow, .glimmer-app viewport { background: transparent; }
.glimmer-app undershoot, .glimmer-app overshoot { background: none; }
.glimmer-app .g-title { font-size: 32px; font-weight: 600; color: #ffffff; }
.glimmer-app .g-subtitle { font-size: 14px; color: rgba(255,255,255,0.65); }
.glimmer-app .g-section { font-size: 13px; font-weight: 500; letter-spacing: 1px; color: rgba(255,255,255,0.65); margin: 20px 0 8px 10px; }
.glimmer-app .g-card { background: rgba(255,255,255,0.085); border: 1px solid rgba(255,255,255,0.15); border-radius: 26px; padding: 6px 0; }
.glimmer-app .g-row { padding: 11px 18px; }
.glimmer-app .g-label { font-size: 15px; color: #ffffff; }
.glimmer-app .g-desc { font-size: 13px; color: rgba(255,255,255,0.65); }
.glimmer-app .g-divider { background: rgba(255,255,255,0.14); min-height: 1px; margin: 0 18px; }
.glimmer-app .g-note { font-size: 13px; color: rgba(255,255,255,0.65); padding: 10px 18px 12px 18px; }
.glimmer-app .g-caption { font-size: 12px; color: rgba(255,255,255,0.5); margin: 6px 0 4px 0; }
.glimmer-app .g-footer { font-size: 12px; color: rgba(255,255,255,0.5); }
.glimmer-app .g-on { color: #34c759; }
.glimmer-app .g-off { color: #ff9f0a; }
.glimmer-app button.g-action { background: transparent; border: none; border-radius: 0; box-shadow: none; padding: 0; min-height: 0; color: #ffffff; }
.glimmer-app button.g-action:hover { background: rgba(255,255,255,0.07); }
.glimmer-app button.g-action:active { background: rgba(255,255,255,0.12); }
.glimmer-app .g-chevron { font-size: 20px; color: rgba(255,255,255,0.45); }
.glimmer-app .g-danger { color: #ff453a; }
.glimmer-app flowbox, .glimmer-app flowboxchild { background: transparent; padding: 0; margin: 0; }
.glimmer-app button.g-chip { background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.16); border-radius: 18px; box-shadow: none; padding: 6px 14px; min-height: 0; min-width: 0; color: #ffffff; font-size: 13px; }
.glimmer-app button.g-chip:hover { background: rgba(255,255,255,0.2); }
.glimmer-app button.g-chip.selected { background: #ffffff; border-color: #ffffff; color: #111111; }
.glimmer-app switch { min-width: 54px; min-height: 32px; border-radius: 16px; background: rgba(255,255,255,0.16); border: 1px solid rgba(255,255,255,0.28); box-shadow: none; outline: none; }
.glimmer-app switch:checked { background: #34c759; border-color: rgba(255,255,255,0.35); }
.glimmer-app switch slider { min-width: 26px; min-height: 26px; margin: 2px; border-radius: 13px; background: #ffffff; border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.4); }
.glimmer-app switch image, .glimmer-app switch label { color: transparent; }
.glimmer-app scale trough { min-height: 6px; background: rgba(255,255,255,0.2); border-radius: 3px; border: none; margin: 0; padding: 0; }
.glimmer-app scale highlight { background: #ffffff; border-radius: 3px; border: none; margin: 0; padding: 0; min-width: 0; min-height: 0; }
.glimmer-app scale slider { min-width: 22px; min-height: 22px; margin: -8px; background: #ffffff; border-radius: 11px; border: none; box-shadow: 0 1px 4px rgba(0,0,0,0.45); }
.glimmer-app scale value { font-size: 14px; color: rgba(255,255,255,0.65); }
.glimmer-app scrollbar slider { background: rgba(255,255,255,0.25); }
"""

MODE_NOTES = {
    "notch": ("MacBook-Notch: für einen Bildschirm ohne Loch für die Kamera. Glimmer ist immer da – als schwarze Kerbe, die oben in der Mitte am "
              "Bildschirmrand hängt, wie bei den neuen MacBooks. Größe und Platz stellst du unten bei „Größe & Position“ ein. Musik, "
              "Benachrichtigungen, Laden und mehr klappen aus der Kerbe heraus; Leuchten, Rand und fließende Farben sind aus (die gehören zur Pille), "
              "alles andere von Glimmer bleibt."),
    "island": ("MacBook-Insel: wie bei den neueren MacBooks – Glimmer als kleine Pille, die oben in der Mitte etwas unter dem Rand schwebt. Musik, "
               "Benachrichtigungen und mehr klappen aus der Pille heraus. Aussehen, Leuchten und Rand wählst du wie bei Glimmer; Größe und Platz "
               "unten bei „Größe & Position“."),
    "glimmer": ("Die Pille oben in der Mitte, in jeder App: Musik, Laden, Lautstärke, Benachrichtigungen und mehr. Nach unten ziehen klappt sie auf, "
                "nach oben schließt sie, zur Seite wechselt bei Musik den Titel."),
    "dynamic_island": ("Dynamic Island 1:1: Glimmer verhält sich wie die Insel des iPhones – reines Schwarz, ihre Proportionen (Pille, kompakte "
                       "Aktivitäten, aufgeklappt fast breit), Cover klassisch. Antippen öffnet die App, gedrückt halten (rechte Maustaste) klappt auf. "
                       "Glimmers Extras (Leuchten, Rand, Nachrichten, Doppeltippen, Titel wischen, Akku beim Laden) sind aus. Deine eigenen "
                       "Einstellungen bleiben gespeichert und kommen zurück, wenn du wieder „Glimmer“ wählst."),
}


def mode_note(c):
    if c["mode"] in ("notch", "island") and c.get("dynamic"):
        shape = "Notch" if c["mode"] == "notch" else "Insel"
        return ("Dynamic %s: Glimmer in der Form der MacBook-%s, aber mit Aussehen und Bedienung der Dynamic Island des iPhones – reines Schwarz, "
                "das Cover als abgerundetes Quadrat, aufgeklappt eine große, runde Karte; Antippen öffnet die App, gedrückt halten (rechte Maustaste) "
                "klappt auf. Größe und Platz stellst du unten bei „Größe & Position“ ein. Leuchten, Rand, Nachrichten, Doppeltippen, Titel wischen "
                "und Akku beim Laden sind aus; deine eigenen Einstellungen kommen zurück, wenn du „Dynamic“ ausschaltest." % (shape, shape))
    return MODE_NOTES[c["mode"]]


def build_settings(app):
    """The app's window, like the Glimmer app of ZENITH: dark, with Glimmer's colours drifting behind it, a little island as its logo, a live
    preview you can tap, and every setting of the island in cards of glass."""
    cfg = app.cfg
    display = Gdk.Display.get_default()
    provider = Gtk.CssProvider()
    try:
        provider.load_from_string(APP_CSS)
    except AttributeError:
        provider.load_from_data(APP_CSS.encode(), -1)
    Gtk.StyleContext.add_provider_for_display(display, provider, Gtk.STYLE_PROVIDER_PRIORITY_USER)

    win = Gtk.Window(application=app)
    win.set_title("Glimmer")
    win.set_default_size(480, 900)
    win.add_css_class("glimmer-app")
    win.connect("close-request", lambda w: (w.set_visible(False), True)[1])  # closing the window does not end Glimmer

    overlay = Gtk.Overlay()
    win.set_child(overlay)
    syncs = []   # functions that bring a control in line with the settings (and show or hide it)

    # --- behind everything: the dark gradient, and the colours drifting through it (as ZENITH's Fluid backdrop)
    def draw_backdrop(area, cr, w, h, *rest):
        t = (time.monotonic() % 24.0) / 24.0 * 2.0 * math.pi
        g = cairo.LinearGradient(0, 0, 0, h)
        g.add_color_stop_rgb(0.0, 0x1B / 255, 0x17 / 255, 0x20 / 255)
        g.add_color_stop_rgb(1.0, 0x09 / 255, 0x09 / 255, 0x0B / 255)
        cr.set_source(g)
        cr.paint()
        colours = [(1.0, 0.624, 0.039), (0.557, 0.42, 1.0), (0.851, 0.467, 0.341)]
        for i, c in enumerate(colours):
            phase = i * 2.1
            cx = w * (0.5 + 0.38 * math.cos(t + phase))
            cy = h * (0.32 + 0.26 * math.sin(t * 0.8 + phase * 1.3) + 0.22 * i / len(colours))
            r = w * (0.55 + 0.12 * math.sin(t * 1.3 + phase))
            rg = cairo.RadialGradient(cx, cy, 0, cx, cy, r)
            rg.add_color_stop_rgba(0.0, *c, 0.26 * 0.8)
            rg.add_color_stop_rgba(1.0, *c, 0.0)
            cr.set_source(rg)
            cr.arc(cx, cy, r, 0, 2 * math.pi)
            cr.fill()
    backdrop = Gtk.DrawingArea()
    backdrop.set_hexpand(True)
    backdrop.set_vexpand(True)
    backdrop.set_draw_func(draw_backdrop)
    overlay.set_child(backdrop)

    scroll = Gtk.ScrolledWindow()
    scroll.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
    scroll.set_hexpand(True)
    scroll.set_vexpand(True)
    overlay.add_overlay(scroll)
    page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
    page.set_margin_start(16)
    page.set_margin_end(16)
    page.set_margin_top(12)
    page.set_margin_bottom(24)
    scroll.set_child(page)

    def lbl(text, css, wrap=False, xalign=0.0):
        label = Gtk.Label(xalign=xalign)
        label.set_text(text)
        label.add_css_class(css)
        if wrap:
            label.set_wrap(True)
            label.set_max_width_chars(46)
        return label

    # --- the logo: a little island that breathes
    def draw_logo(area, cr, w, h, *rest):
        k = 0.5 - 0.5 * math.cos(2.0 * math.pi * time.monotonic() / 4.4)
        pw, ph = 120 + 48 * k, 34
        x, y = (w - pw) / 2.0, (h - ph) / 2.0
        body_path(cr, x, y, pw, ph, ph / 2.0, ph / 2.0)
        cr.set_source_rgb(0, 0, 0)
        cr.fill()
        draw_clawd(cr, x + 8, y + 6, 26, 22, "wave", CLAWD_ORANGE, time.monotonic())
        rrect(cr, x + pw - 24, y + 11, 12, 12, 6)
        cr.set_source_rgb(0x1C / 255, 0x1C / 255, 0x22 / 255)
        cr.fill()
    logo = Gtk.DrawingArea()
    logo.set_content_width(260)
    logo.set_content_height(62)
    logo.set_draw_func(draw_logo)
    logo.set_halign(Gtk.Align.CENTER)
    logo.set_margin_top(14)
    page.append(logo)
    title = lbl("Glimmer", "g-title", xalign=0.5)
    title.set_margin_top(6)
    page.append(title)
    page.append(lbl("Die Insel auf deinem Desktop, in jeder App", "g-subtitle", xalign=0.5))

    # --- the preview: tap it to unfold, like the real one
    demo_player_obj = demo_player(cfg)
    preview_state = {"isl": None, "index": 0, "last": time.monotonic()}

    def demos():
        return [
            ("idle", None),
            ("music", demo_player_obj),
            ("power", {"state": "charging", "pct": 72}),
            ("notify", {"app": "Signal", "summary": "Mia", "body": "Kommst du später noch vorbei? Ich bring Kuchen mit – und die Fotos vom Wochenende habe ich auch dabei.", "icon": None}),
            ("code", {"code": "482913", "app": "Bank", "icon": None, "copied": False}),
            ("volume", {"pct": 64, "muted": False}),
            ("alert", {"glyph": "headphones", "title": "AirPods", "value": "82 %", "color": GREEN, "level": 0.82}),
            ("screenshot", {"path": "", "thumb": demo_art()}),
            ("power", {"state": "low", "pct": 19}),
        ]

    def preview_config():
        return effective_config(dict(app.cfg, scale=1.0))

    def new_preview():
        isl = Island(preview_config())
        preview_state["isl"] = isl
        kind, data = demos()[preview_state["index"]]
        if kind == "idle" and not isl.cfg["idle_pill"] and not isl.mac:
            preview_state["index"] = 1
            kind, data = demos()[1]
        isl.show(kind, data, False)
        isl.snap()
    new_preview()

    def draw_preview(area, cr, w, h, *rest):
        cr.save()
        rrect(cr, 0, 0, w, h, 28)
        cr.clip()
        g = cairo.LinearGradient(0, 0, 0, h)
        g.add_color_stop_rgb(0.0, 0x3A / 255, 0x2F / 255, 0x4A / 255)
        g.add_color_stop_rgb(1.0, 0x15 / 255, 0x12 / 255, 0x1B / 255)
        cr.set_source(g)
        cr.paint()
        preview_state["isl"].draw(cr, w, h, time.monotonic())
        cr.restore()
    preview = Gtk.DrawingArea()
    preview.set_content_height(150)
    preview.set_hexpand(True)
    preview.set_draw_func(draw_preview)
    preview.set_margin_top(18)
    page.append(preview)
    caption = lbl("", "g-caption", xalign=0.5)
    page.append(caption)

    def preview_tap(gesture, n, x, y):
        isl = preview_state["isl"]
        if isl.kind in (None, "idle"):
            return
        isl.show(isl.kind, isl.data, not isl.expanded)
        preview_state["last"] = time.monotonic()
    click = Gtk.GestureClick()
    click.connect("pressed", preview_tap)
    preview.add_controller(click)

    # --- the building blocks of the cards
    def section(text, visible=None):
        head = lbl(text.upper(), "g-section")
        page.append(head)
        card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        card.add_css_class("g-card")
        card.set_overflow(Gtk.Overflow.HIDDEN)
        page.append(card)
        if visible:
            def sync_section():
                v = bool(visible())
                head.set_visible(v)
                card.set_visible(v)
            syncs.append(sync_section)
        return card

    def texts(label, desc):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        box.set_hexpand(True)
        name = lbl(label, "g-label")
        box.append(name)
        d = None
        if desc is not None:
            d = lbl(desc, "g-desc", wrap=True)
            box.append(d)
        return box, name, d

    card_rows = {}

    def place(card, widget, visible=None):
        """Puts a row into the card with a line above it. Rows that are hidden for the chosen mode take their line with them, and the
        first row showing has no line above it."""
        sep = Gtk.Box()
        sep.add_css_class("g-divider")
        card.append(sep)
        card.append(widget)
        rows = card_rows.setdefault(card, [])
        rows.append((sep, widget, visible))
        if len(rows) == 1:
            syncs.append(lambda c=card: sync_card(c))
        sync_card(card)

    def sync_card(card):
        seen = False
        for sep, widget, visible in card_rows[card]:
            v = True if visible is None else bool(visible())
            widget.set_visible(v)
            sep.set_visible(v and seen)
            seen = seen or v

    def add_row(card, label, desc, control, visible=None):
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        row.add_css_class("g-row")
        box, name, d = texts(label, desc)
        control.set_valign(Gtk.Align.CENTER)
        row.append(box)
        row.append(control)
        place(card, row, visible)
        return name, d

    def add_note(card, text_fn, visible=None):
        note = lbl(text_fn() if callable(text_fn) else text_fn, "g-note", wrap=True)
        note.set_hexpand(True)
        place(card, note, visible)
        if callable(text_fn):
            syncs.append(lambda: note.set_text(text_fn()))

    def add_switch(card, key, label, desc=None, visible=None, desc_fn=None, label_fn=None):
        sw = Gtk.Switch()
        sw.set_active(bool(cfg[key]))
        sw.connect("notify::active", lambda w, _p: app.set_option(key, w.get_active()))
        name, d = add_row(card, label, desc if desc_fn is None else desc_fn(), sw, visible)

        def sync_sw():
            if sw.get_active() != bool(cfg[key]):
                sw.set_active(bool(cfg[key]))
            if desc_fn is not None and d is not None:
                d.set_text(desc_fn())
            if label_fn is not None:
                name.set_text(label_fn())
        syncs.append(sync_sw)

    def add_slider(card, key, label, desc, lo, hi, step, digits=0, as_int=True, visible=None, fmt=None, range_fn=None):
        sc = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, lo, hi, step)
        sc.set_value(float(cfg[key]))
        sc.set_digits(digits)
        sc.set_draw_value(True)
        sc.set_value_pos(Gtk.PositionType.RIGHT)
        sc.set_size_request(170, -1)
        if fmt:
            sc.set_format_value_func(lambda scale, v: fmt(v))
        sc.connect("value-changed", lambda w: app.set_option(key, int(round(w.get_value())) if as_int else round(w.get_value(), 2)))
        add_row(card, label, desc, sc, visible)

        def sync_sc():
            if range_fn:
                rlo, rhi = range_fn()
                sc.set_range(rlo, rhi)
            if abs(sc.get_value() - float(cfg[key])) > 1e-6:
                sc.set_value(float(cfg[key]))
        syncs.append(sync_sc)

    def add_chips(card, key, label, values, names, desc=None, visible=None, desc_fn=None):
        row = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        row.add_css_class("g-row")
        row.append(lbl(label, "g-label"))
        d = None
        if desc is not None or desc_fn is not None:
            d = lbl(desc if desc_fn is None else desc_fn(), "g-desc", wrap=True)
            row.append(d)
        flow = Gtk.FlowBox()
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_min_children_per_line(1)
        flow.set_max_children_per_line(8)
        flow.set_column_spacing(6)
        flow.set_row_spacing(6)
        flow.set_homogeneous(False)
        buttons = []
        for val, name in zip(values, names):
            b = Gtk.Button(label=name)
            b.add_css_class("g-chip")
            b.connect("clicked", lambda _b, v=val: app.set_option(key, v))
            flow.append(b)
            buttons.append((val, b))
        row.append(flow)
        place(card, row, visible)

        def sync_chips():
            for val, b in buttons:
                if val == cfg[key]:
                    b.add_css_class("selected")
                else:
                    b.remove_css_class("selected")
            if desc_fn is not None and d is not None:
                d.set_text(desc_fn())
        syncs.append(sync_chips)

    def add_action(card, label, desc=None, on_click=None, danger=False, chevron=True, visible=None):
        button = Gtk.Button()
        button.add_css_class("g-action")
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        row.add_css_class("g-row")
        box, name, d = texts(label, desc)
        if danger:
            name.add_css_class("g-danger")
        row.append(box)
        if chevron:
            arrow = lbl("›", "g-chevron")
            arrow.set_valign(Gtk.Align.CENTER)
            row.append(arrow)
        button.set_child(row)
        if on_click:
            button.connect("clicked", lambda _b: on_click())
        place(card, button, visible)
        return name, d

    mode = lambda: app.cfg["mode"]                        # noqa: E731
    not_di = lambda: not is_dynamic(app.cfg)  # noqa: E731   (in the iPhone's way these rows step aside)
    pill_look = lambda: app.cfg["mode"] in ("glimmer", "island") and not is_dynamic(app.cfg)  # noqa: E731

    # --- Einrichten: what Glimmer found on this desktop
    card = section("Einrichten")
    status_labels = {}

    def status_row(key, label):
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        row.add_css_class("g-row")
        box, name, d = texts(label, "")
        state = lbl("", "g-label", xalign=1.0)
        state.set_valign(Gtk.Align.CENTER)
        row.append(box)
        row.append(state)
        place(card, row)
        status_labels[key] = (state, d)

    for key, label in (("layer", "Layer-Shell"), ("music", "Musik (MPRIS)"), ("notify", "Benachrichtigungen"), ("volume", "Lautstärke"),
                       ("battery", "Akku"), ("shots", "Bildschirmfotos"), ("privacy", "Kamera und Mikrofon")):
        status_row(key, label)

    def refresh_status():
        def put(key, on, text, desc):
            state, d = status_labels[key]
            state.set_text("An" if on else "Aus")
            state.remove_css_class("g-on")
            state.remove_css_class("g-off")
            state.add_css_class("g-on" if on else "g-off")
            d.set_text(text if on else desc)
        put("layer", app.layered, "Die Insel sitzt oben am Bildschirmrand.", "Ohne Layer-Shell (GNOME, X11) läuft sie nur als Fenster in der Ecke.")
        names = [p.name.replace(MPRIS_PREFIX, "") for p in app.mpris.players.values() if p.known and not p.owner.startswith(":demo")]
        put("music", bool(names), ", ".join(sorted(set(names))), "Kein Player läuft: Spotify, Firefox, VLC … melden sich von selbst.")
        put("notify", getattr(app, "_notif_conn", None) is not None, "Glimmer liest mit; die normalen Benachrichtigungen bleiben.",
            "Nicht erreichbar: der Nachrichtenbus erlaubt das Mitlesen nicht.")
        tool = shutil.which("pactl") or shutil.which("wpctl")
        put("volume", tool is not None, os.path.basename(tool) if tool else "", "Weder pactl noch wpctl gefunden (sudo pacman -S libpulse).")
        info = read_power()
        put("battery", info is not None, ("%d %%%s" % (info["pct"], ", am Strom" if info["plugged"] else "")) if info else "",
            "Kein Akku gefunden (ein Desktop-PC hat keinen).")
        put("shots", bool(app._screen_monitors), "Neue Bilder im Ordner „Screenshots“ erscheinen in der Insel.", "Kein Ordner „Bilder/Screenshots“ gefunden.")
        put("privacy", shutil.which("pactl") is not None, "Grüner Punkt bei der Kamera, oranger beim Mikrofon.", "Für das Mikrofon fehlt pactl (sudo pacman -S libpulse).")
    refresh_status()
    win.connect("map", lambda *_: refresh_status())
    add_note(card, "Glimmer läuft mit jedem Desktop mit Layer-Shell, auch mit mehreren Bildschirmen (Einstellung „monitor“ in der Datei).")

    # --- Clawd
    card = section("Clawd")
    clawd_box = Gtk.DrawingArea()
    clawd_box.set_content_width(84)
    clawd_box.set_content_height(72)
    clawd_box.set_halign(Gtk.Align.CENTER)
    clawd_box.set_margin_top(8)

    def draw_clawd_preview(area, cr, w, h, *rest):
        now = time.monotonic()
        draw_clawd(cr, 0, 0, w, h, "dance" if app.cfg["clawd"] else "sleep", clawd_color(app.cfg["clawd_skin"], now), now)
    clawd_box.set_draw_func(draw_clawd_preview)
    place(card, clawd_box)
    add_switch(card, "clawd", "Clawd in Glimmer", desc_fn=lambda: (
        "An: Clawd ist bei allem dabei – Pille, Musik (er tanzt mit), Laden, Codes, Bildschirmfotos und Meldungen, auch aufgeklappt"
        if app.cfg["clawd"] else "Aus: Glimmer ohne Clawd"))
    clawd_on = lambda: app.cfg["clawd"]  # noqa: E731
    add_switch(card, "clawd_idle", "Clawd, wenn nichts los ist", visible=clawd_on, desc_fn=lambda: (
        "An: er sitzt auch in der leeren Insel (nachts schläft er dort)" if app.cfg["clawd_idle"]
        else "Aus: die leere Insel bleibt schwarz – Clawd kommt erst, wenn etwas passiert"))
    add_chips(card, "clawd_side", "Seite", ["left", "right"], ["Links", "Rechts"], visible=clawd_on)
    add_slider(card, "clawd_spot", "Position", None, 0.0, 1.0, 0.05, digits=2, as_int=False, visible=clawd_on,
               fmt=lambda v: "Am Inhalt" if v < 0.2 else "An der Kamera" if v > 0.8 else "Dazwischen")
    add_slider(card, "clawd_size", "Größe", None, 0.7, 1.4, 0.05, digits=2, as_int=False, visible=clawd_on, fmt=lambda v: "%d %%" % round(v * 100))
    add_chips(card, "clawd_skin", "Aussehen", CHOICES["clawd_skin"], [CLAWD_SKINS[k][0] for k in CHOICES["clawd_skin"]], visible=clawd_on)
    add_switch(card, "clawd_greeting", "Clawd sagt Hallo beim Start", "Beim Anmelden grüßt er in der Insel – morgens mit „Guten Morgen“, sonst immer ein bisschen anders",
               visible=clawd_on)
    add_switch(card, "clawd_greet_daily", "Nur einmal am Tag", "Statt bei jedem Start nur beim ersten des Tages",
               visible=lambda: app.cfg["clawd"] and app.cfg["clawd_greeting"])
    add_note(card, "Er schläft nachts in der Pille. Seite, Position und Größe siehst du oben in der Vorschau.")

    # --- Glimmer
    card = section("Glimmer")
    add_note(card, "Die Insel auf deinem Desktop, in jeder App: Musik, Laden, Lautstärke, Benachrichtigungen, Codes, Bildschirmfotos und mehr. "
                   "Tippen oder nach unten ziehen klappt sie auf, nach oben ziehen schließt sie.")
    add_switch(card, "enabled", "Glimmer anzeigen")
    add_chips(card, "mode", "Modus", CHOICES["mode"], [MODE_NAMES[m] for m in CHOICES["mode"]])
    add_note(card, lambda: mode_note(app.cfg))
    # The iPhone's way on the notch or the MacBook island: its look and handling, the shape stays your own.
    add_switch(card, "dynamic", "Dynamic Notch (1:1)", visible=lambda: app.cfg["mode"] in ("notch", "island"),
               label_fn=lambda: "Dynamic Notch (1:1)" if app.cfg["mode"] == "notch" else "Dynamic-Island-Stil (1:1)",
               desc_fn=lambda: (
                   "An: so wie die echte Dynamic Island – reines Schwarz, das Cover als abgerundetes Quadrat, Antippen öffnet die App und gedrückt halten "
                   "(rechte Maustaste) klappt auf. Die Form bleibt deine; Leuchten, Rand, Nachrichten, Doppeltippen, Titel wischen und Akku beim Laden sind aus"
                   if app.cfg["dynamic"] else
                   "Aus: %s mit Glimmers Extras. An: Aussehen und Bedienung der Dynamic Island des iPhones, in der Form der %s" % (
                       ("die Kerbe", "MacBook-Notch") if app.cfg["mode"] == "notch" else ("die Insel", "MacBook-Insel"))))
    add_chips(card, "style", "Aussehen", ["black", "glass", "tinted", "fluid"], ["Schwarz wie die Kamera", "Liquid Glass", "Farbig getönt", "AI Fluid"],
              visible=pill_look)
    add_chips(card, "music_style", "Musik", CHOICES["music_style"], ["Hyper Island (Xiaomi)", "Klassisch", "Fluid Cloud (OPPO/OnePlus)"], visible=not_di)
    add_switch(card, "idle_pill", "Kleine Pille, wenn nichts läuft", visible=pill_look, desc_fn=lambda: (
        "Aus: die Insel zeigt sich nur, wenn etwas läuft. An: sie ist immer da" if app.cfg["mode"] == "island" else "Wie beim iPhone"))
    add_switch(card, "idle_clock", "Uhrzeit in der leeren Insel", "Statt leer bleiben: die Uhrzeit (Clawd hat Vorrang, wenn er dort sitzt)")

    # --- Gestaltung
    card = section("Gestaltung", visible=not_di)
    add_switch(card, "glow", "Leuchten bei Neuem", "Glimmer schimmert kurz, wenn etwas Neues kommt")
    add_chips(card, "glow_color", "Leuchtfarbe", CHOICES["glow_color"], ["Farbe der Aktivität", "Akzentfarbe", "Weiß", "Regenbogen"],
              visible=lambda: app.cfg["glow"])
    add_switch(card, "ai_fluid", "AI-Fluid-Rand", "Solange etwas läuft, fließen Claudes Farben um den Rand der Insel – und leuchten kurz auf, wenn etwas Neues kommt. "
                                                  "Aufgeklappt treiben die Farben im Schwarz")
    add_switch(card, "breathe", "Atmendes Leuchten", "Solange etwas läuft, atmet ein weiches Licht in seiner Farbe um die Insel – wie OPPOs Fluid Cloud")
    add_chips(card, "outline", "Rand", CHOICES["outline"], ["Aus", "Dezent", "In Farbe der Aktivität"])
    add_chips(card, "motion", "Bewegung", CHOICES["motion"], ["Ruhig", "Normal", "Verspielt"])
    add_note(card, "In der MacBook-Notch sind Leuchten, Rand, fließende Farben und das Atmen aus, und sie federt ohne Wippen – das gehört zur Pille.",
             visible=lambda: app.cfg["mode"] == "notch" and not is_dynamic(app.cfg))

    # --- Größe & Position
    card = section("Größe & Position")
    add_switch(card, "small", "Kleiner (85 %)", "Die kleine Insel ist 15 % schmaler; aufgeklappt bleibt Glimmer so groß wie bei 100 %. Höhe, Inhalte und Schrift bleiben gleich")
    add_slider(card, "active_width", "Breite bei Musik, Laden & Co.", "Wie breit die kleine Insel ist, solange etwas läuft (Cover links, Wellen rechts).", 0.7, 1.6, 0.05, digits=2, as_int=False,
               fmt=lambda v: "%d %%" % round(v * 100))
    add_slider(card, "expand_width", "Aufgeklappt: Breite", "Wie breit die aufgeklappte Insel oder Notch ist.", 0.8, 1.3, 0.05, digits=2, as_int=False,
               fmt=lambda v: "%d %%" % round(v * 100))
    add_slider(card, "expand_height", "Aufgeklappt: Höhe", "Wie hoch sie ist; eine lange Benachrichtigung macht sie von selbst höher.", 0.8, 1.5, 0.05, digits=2, as_int=False,
               fmt=lambda v: "%d %%" % round(v * 100))
    add_slider(card, "scale", "Gesamtgröße", "Alles zusammen, vom Bildschirm unabhängig; Glimmer startet kurz neu.", 0.6, 1.6, 0.05, digits=2, as_int=False,
               fmt=lambda v: "%d %%" % round(v * 100))
    add_slider(card, "notch_width", "Breite der Kerbe", None, 80, 420, 5, visible=lambda: app.cfg["mode"] == "notch", fmt=lambda v: "%d dp" % v)
    add_slider(card, "notch_height", "Höhe der Kerbe", None, 16, 90, 1, visible=lambda: app.cfg["mode"] == "notch", fmt=lambda v: "%d dp" % v)
    add_slider(card, "island_width", "Breite der Pille", None, 60, 420, 5, visible=lambda: app.cfg["mode"] == "island", fmt=lambda v: "%d dp" % v)
    add_slider(card, "island_height", "Höhe der Pille", None, 16, 90, 1, visible=lambda: app.cfg["mode"] == "island", fmt=lambda v: "%d dp" % v)
    add_slider(card, "width", "Breite", None, 0.8, 1.3, 0.05, digits=2, as_int=False, visible=lambda: app.cfg["mode"] == "glimmer",
               fmt=lambda v: "%d %%" % round(v * 100))
    add_slider(card, "offset_x", "Nach links / rechts", None, -40, 40, 1, fmt=lambda v: "%d dp" % v)

    def y_range():
        m = app.cfg["mode"]
        return (0, 40) if m == "notch" else (-6, 40) if m == "island" else (-10, 24)
    add_slider(card, "offset_y", "Nach oben / unten", None, -10, 40, 1, fmt=lambda v: "%d dp" % v, range_fn=y_range)
    add_note(card, lambda: ("Die Kerbe hängt am oberen Rand (0 dp). Nach unten geschoben schwebt sie als Pille mit etwas Abstand. Die Änderung siehst du sofort."
                            if app.cfg["mode"] == "notch" else
                            "Die Pille schwebt 6 dp unter dem oberen Rand; mit „Nach oben / unten“ rückst du sie näher an den Rand (-6) oder weiter weg. Die Änderung siehst du sofort."
                            if app.cfg["mode"] == "island" else
                            "Sitzt Glimmer nicht, wo du ihn willst, schieb ihn hier zurecht. Die Änderung siehst du sofort."))
    size_keys = ("small", "width", "notch_width", "notch_height", "island_width", "island_height", "offset_x", "offset_y")
    add_action(card, "Größe & Position zurücksetzen", "Breite 100 %, Kerbe 190 × 34 dp, Pille 128 × 32 dp, nicht verschoben",
               lambda: app.reset_options(size_keys))

    # --- Bedienung
    card = section("Bedienung")
    add_switch(card, "tap_opens", "Antippen öffnet die App", visible=not_di, desc_fn=lambda: (
        "Wie beim iPhone; die rechte Maustaste klappt auf" if app.cfg["tap_opens"] else "Aus: Antippen klappt auf, die rechte Maustaste öffnet die App"))
    add_chips(card, "double_tap", "Doppeltippen", CHOICES["double_tap"], ["Nichts", "Musik Play/Pause", "Clawd-Orakel", "Münze werfen", "Würfeln"], visible=not_di)
    add_chips(card, "auto_collapse", "Von selbst zuklappen", [5, 9, 15, 30, 0], ["nach 5 s", "nach 9 s", "nach 15 s", "nach 30 s", "Nie"])
    add_switch(card, "swipe_tracks", "Zur Seite ziehen wechselt den Titel", "Läuft nur Musik: über Glimmer nach links für den nächsten, nach rechts für den vorherigen Titel",
               visible=not_di)
    add_switch(card, "hover_reveal", "Maus auf der Insel klappt die Musik auf", "Zusätzlich zum Tippen; die Maus einen Moment dort lassen.")

    # --- Was Glimmer zeigt
    card = section("Was Glimmer zeigt")
    add_switch(card, "music", "Musik zeigen", "Jeder Player, der sich meldet (MPRIS): Spotify, Firefox, VLC, mpv …")
    add_switch(card, "music_persistent", "Kleine Insel mit Cover und Wellen", "Solange Musik läuft; aus: nur auf Tippen")
    add_switch(card, "codes", "Bestätigungscodes zum Kopieren", "Kommt ein Code per Benachrichtigung (Bank, Login), sitzt er in Glimmer; antippen kopiert ihn – wie bei vivo und OPPO",
               visible=not_di)
    add_switch(card, "screenshots", "Bildschirmfotos in der Insel", "Ein neues Bildschirmfoto erscheint als kleines Bild: antippen öffnet es, aufgeklappt auch Kopieren – wie vivos Origin Island",
               visible=not_di)
    add_switch(card, "notifications", "Neue Nachrichten kurz zeigen", "App-Symbol und ein pulsierender Punkt; tippen klappt den Text auf", visible=not_di)
    add_switch(card, "privacy", "Kamera und Mikrofon in Benutzung", "Ein grüner Punkt in Glimmer, solange ein Programm die Kamera benutzt, ein oranger beim Mikrofon – wie beim iPhone")
    add_switch(card, "alerts", "Kopfhörer, Nicht stören, Akku voll", "Kurz anzeigen, wenn Bluetooth-Kopfhörer sich verbinden, „Nicht stören“ umschaltet oder der Akku voll ist")
    add_switch(card, "charging", "Laden und Kabel", "Beim Anstecken ein grüner Funke und ein Lichtstreifen, beim Abziehen der Stand")
    add_switch(card, "low_battery", "Akku schwach", "Bei 20 % und 10 %")
    add_switch(card, "charging_live", "Akku beim Laden zeigen", "Solange der Laptop lädt, bleibt der Akkustand in Glimmer", visible=not_di)
    add_switch(card, "volume", "Lautstärke", "KDE zeigt dazu auch seine eigene Anzeige")

    # --- Linux
    card = section("Linux")
    auto = Gtk.Switch()
    auto.set_active(os.path.exists(AUTOSTART_FILE))
    auto.connect("notify::active", lambda w, _p: set_autostart(w.get_active()))
    add_row(card, "Bei der Anmeldung starten", None, auto)
    add_chips(card, "language", "Sprache", CHOICES["language"], ["Automatisch", "Deutsch", "English"])
    add_chips(card, "layer", "Ebene", CHOICES["layer"], ["Über allem, auch Vollbild", "Unter Vollbild-Fenstern"], desc="Glimmer startet kurz neu.")
    add_slider(card, "monitor", "Bildschirm", "Nummer des Bildschirms (0 ist der erste); Glimmer startet kurz neu.", 0, 8, 1,
               fmt=lambda v: "Nr. %d" % v)

    card = section("Ausprobieren")
    name, _d = add_action(card, "Vorführung starten", "Zeigt der Reihe nach alle Ereignisse.", lambda: app.stop_demo() if app.demo_running() else app.start_demo())
    app.demo_button = name
    app._sync_demo_button()
    add_action(card, "Test-Benachrichtigung", "So sieht eine Benachrichtigung aus.", lambda: app.post(
        "notify", {"app": "Glimmer", "summary": "So sieht eine Benachrichtigung aus", "body": "Mit zwei Zeilen Text darunter.", "icon": None}, 4.0, 3))
    add_action(card, "Glimmer beenden", "Das Fenster zu schließen beendet Glimmer nicht, die Insel läuft weiter.", app.quit_app, danger=True, chevron=False)

    card = section("Zurücksetzen")
    confirm = {"on": False, "id": 0}
    reset_name = [None]

    def ask_reset():
        if confirm["on"]:
            confirm["on"] = False
            app.reset_options()
            return
        confirm["on"] = True
        reset_name[0].set_text("Wirklich alles zurücksetzen? Nochmal tippen")

        def timeout():
            confirm["on"] = False
            confirm["id"] = 0
            if reset_name[0] is not None:
                reset_name[0].set_text("Alle Glimmer-Einstellungen zurücksetzen")
            return False
        confirm["id"] = GLib.timeout_add(4000, timeout)
    reset_name[0], _ = add_action(card, "Alle Glimmer-Einstellungen zurücksetzen",
                                  "Aussehen, Größe, Position, Farben, Bewegung und Funktionen wie am Anfang; Glimmer bleibt an", ask_reset)

    footer = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
    footer.set_halign(Gtk.Align.CENTER)
    footer.set_margin_top(22)

    def draw_spark(area, cr, w, h, *rest):
        draw_glyph(cr, "spark", 0, 0, 14, app.island.accent)
    spark = Gtk.DrawingArea()
    spark.set_content_width(14)
    spark.set_content_height(14)
    spark.set_draw_func(draw_spark)
    spark.set_valign(Gtk.Align.CENTER)
    footer.append(spark)
    footer.append(lbl("Glimmer für Linux %s · Teil von ZENITH" % APP_VERSION, "g-footer"))
    page.append(footer)

    # --- life: the backdrop drifts, the logo breathes, the preview goes through its moments (30 frames a second, only while it is on screen)
    state = {"id": 0, "last": time.monotonic(), "n": 0, "ph": 150.0, "shown": 150.0}

    def tick():
        if not win.get_mapped():
            state["id"] = 0
            return False
        now = time.monotonic()
        dt = min(now - state["last"], 0.05)
        state["last"] = now
        state["n"] += 1
        isl = preview_state["isl"]
        isl.step(dt)
        # (moves on by itself while folded; stays put while you look at it unfolded)
        if not isl.expanded and now - preview_state["last"] > 2.6:
            preview_state["last"] = now
            n = len(demos())
            preview_state["index"] = (preview_state["index"] + 1) % n
            kind, data = demos()[preview_state["index"]]
            if kind == "idle" and not isl.cfg["idle_pill"] and not isl.mac:
                preview_state["index"] = (preview_state["index"] + 1) % n
                kind, data = demos()[preview_state["index"]]
            isl.show(kind, data, False)
        want = clamp(isl.h.x + isl.top_inset() * isl.s + 44, 150.0, 420.0)
        state["ph"] += (want - state["ph"]) * min(1.0, dt * 12.0)
        if abs(state["ph"] - state["shown"]) >= 1.0:
            state["shown"] = state["ph"]
            preview.set_content_height(int(state["ph"]))
        preview.queue_draw()
        logo.queue_draw()
        if app.cfg["clawd"] or state["n"] % 2 == 0:
            clawd_box.queue_draw()
        if state["n"] % 3 == 0:
            backdrop.queue_draw()
        return True

    def start_ticks(*_):
        if not state["id"]:
            state["last"] = time.monotonic()
            state["id"] = GLib.timeout_add(33, tick)
    win.connect("map", start_ticks)

    def sync_all():
        if state.get("syncing"):
            return
        state["syncing"] = True
        try:
            for fn in list(syncs):
                fn()
        finally:
            state["syncing"] = False
        caption.set_text("Vorschau · antippen zum Aufklappen" if app.cfg["enabled"] else "Glimmer ist aus")
        isl = preview_state["isl"]
        isl.configure(preview_config())
    sync_all()

    def refresh_preview():
        sync_all()
    win.refresh_preview = refresh_preview
    return win


def camera_in_use():
    """True while a program of this user has a camera open (a look at /proc – no root needed for one's own programs)."""
    try:
        for pid in os.listdir("/proc"):
            if not pid.isdigit():
                continue
            fd_dir = "/proc/%s/fd" % pid
            try:
                for fd in os.listdir(fd_dir):
                    if os.readlink(os.path.join(fd_dir, fd)).startswith("/dev/video"):
                        return True
            except OSError:
                continue
    except OSError:
        pass
    return False


MIC_IGNORE = ("peak detect", "pavucontrol", "pulseaudio volume control", "plasma-pa", "org.kde.plasma.pulseaudio", "glimmer", "kmix", "audio-recorder-volume")


def microphone_in_use():
    """True while a program records (pactl lists the recording streams; level meters of mixers do not count)."""
    pactl = shutil.which("pactl")
    if not pactl:
        return False
    try:
        out = subprocess.run([pactl, "list", "source-outputs"], capture_output=True, text=True, timeout=2, env=CLEAN_ENV).stdout
    except (OSError, subprocess.SubprocessError):
        return False
    for block in out.split("Source Output #")[1:]:
        low = block.lower()
        if not any(word in low for word in MIC_IGNORE):
            return True
    return False


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
    p.art, p.palette = demo_art(), [(1.0, 0.47, 0.56), (0.72, 0.30, 0.82), (1.0, 0.72, 0.45)]
    p.known, p.last_active, p.last_change = True, time.monotonic(), time.monotonic()
    return p


SNAPSHOT_MODES = (("notch", "notch", False), ("island", "island", False), ("glimmer", "glimmer", False), ("dynamic_island", "dynamic_island", False),
                  ("dynamic notch", "notch", True), ("dynamic insel", "island", True))


def render_snapshot(cfg, path, modes=SNAPSHOT_MODES):
    """Draws every state of the island, in every mode, into one picture – no window, no display needed."""
    player = demo_player(cfg)
    notice = {"app": "Signal", "summary": "Mia", "body": "Kommst du später noch vorbei? Ich bring Kuchen mit – und die Fotos vom Wochenende habe ich auch dabei.", "icon": None}
    code = {"code": "482913", "app": "Bank", "icon": None, "copied": False}
    shot = {"path": "", "thumb": demo_art()}
    phones = {"glyph": "headphones", "title": "AirPods", "value": "82 %", "color": GREEN, "level": 0.82}
    scenes = [  # (label, kind, data, unfolded, privacy)
        ("Leerlauf", "idle", None, False, None),
        ("Kamera in Benutzung", "idle", None, False, "camera"),
        ("Musik", "music", player, False, None),
        ("Lautstärke", "volume", {"pct": 64, "muted": False}, False, None),
        ("Lädt", "power", {"state": "charging", "pct": 78}, False, None),
        ("Akku schwach", "power", {"state": "low", "pct": 14}, False, None),
        ("Benachrichtigung", "notify", notice, False, None),
        ("Code", "code", code, False, None),
        ("Bildschirmfoto", "screenshot", shot, False, None),
        ("Kopfhörer", "alert", phones, False, None),
        ("Musik, aufgeklappt", "music", player, True, None),
        ("Lädt, aufgeklappt", "power", {"state": "charging", "pct": 78}, True, None),
        ("Benachrichtigung, aufgeklappt", "notify", notice, True, None),
        ("Code, aufgeklappt", "code", code, True, None),
        ("Bildschirmfoto, aufgeklappt", "screenshot", shot, True, None),
        ("Kopfhörer, aufgeklappt", "alert", phones, True, None),
    ]
    s = cfg["scale"]
    W, _H = window_size(s)
    pad = int(14 * s)
    small, big = int(84 * s), int(300 * s)
    heights = [big if scene[3] else small for scene in scenes]
    total = sum(heights) + (len(scenes) + 1) * pad // 2
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, len(modes) * W + (len(modes) + 1) * pad, total)
    cr = cairo.Context(surf)
    cr.set_source_rgb(0.08, 0.08, 0.1)
    cr.paint()
    y = pad // 2
    for (label, kind, data, unfolded, privacy), cell_h in zip(scenes, heights):
        for col, (mode_label, mode, dynamic) in enumerate(modes):
            isl = Island(effective_config(dict(cfg, mode=mode, dynamic=dynamic)))
            isl.privacy = privacy
            isl.show(kind, data, unfolded)
            isl.snap()
            x = pad + col * (W + pad)
            cr.save()
            cr.translate(x, y)
            cr.rectangle(0, 0, W, cell_h)
            cr.clip()
            g = cairo.LinearGradient(0, 0, W, cell_h)
            g.add_color_stop_rgb(0, 0.16, 0.26, 0.46)
            g.add_color_stop_rgb(1, 0.46, 0.22, 0.52)
            cr.set_source(g)
            cr.paint()
            isl.draw(cr, W, cell_h, 1.3)
            draw_text(cr, "%s · %s" % (label, mode_label), 8, cell_h - 9, 10.5, (1, 1, 1, 0.7))
            cr.restore()
        y += cell_h + pad // 2
    surf.write_to_png(path)
    print("Bild geschrieben:", path)


def main():
    ap = argparse.ArgumentParser(prog="glimmer", description="Glimmer für Linux – die Insel am oberen Bildschirmrand.")
    ap.add_argument("--mode", choices=CHOICES["mode"], help="notch (MacBook-Notch), island (MacBook-Insel), glimmer (Pille), dynamic_island")
    ap.add_argument("--scale", type=float, help="Größe, 1.0 ist normal")
    ap.add_argument("--config", help="Pfad der Einstellungen (Standard: ~/.config/glimmer/config.json)")
    ap.add_argument("--demo", action="store_true", help="zeigt der Reihe nach alle Ereignisse")
    ap.add_argument("--settings", action="store_true", help="das Einstellungsfenster öffnen")
    ap.add_argument("--background", action="store_true", help="nur im Hintergrund starten (Autostart); läuft schon eine Instanz, nichts tun")
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
    app.config_path = args.config or CONFIG_PATH
    app.open_settings_at_start = args.settings

    def on_signal(*a):
        app.quit_app()
        return False
    _signal_add(signal.SIGINT, on_signal)
    _signal_add(signal.SIGTERM, on_signal)
    try:
        app.register(None)
        if app.get_is_remote() and args.background:
            return 0  # (it runs already – a second start from the menu opens the settings, the autostart does nothing)
    except GLib.Error:
        pass
    return app.run([sys.argv[0]])


if __name__ == "__main__":
    sys.exit(main())
