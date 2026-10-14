package dev.hearth.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/**
 * Tiny calculator for the search field: + - * / ^ %, brackets, comma or dot as decimal mark.
 * Returns null if the text isn't a calculation.
 */
fun calculate(input: String): String? {
    // ZENITH 21: "5 km in mi", "20 c in f", "3 lb nach kg" – units, too.
    convertUnits(input)?.let { return it }
    val text = input.replace(" ", "").replace(',', '.').replace('×', '*').replace('÷', '/').replace(':', '/')
    if (text.length < 3 || text.none { it.isDigit() } || text.none { it in "+-*/^%" }) return null
    if (text.any { !(it.isDigit() || it in "+-*/^%.()") }) return null
    val parser = object {
        var pos = 0
        fun peek(): Char? = text.getOrNull(pos)
        fun eat(c: Char): Boolean = if (peek() == c) { pos++; true } else false

        fun expression(): Double {
            var value = term()
            while (true) {
                value = when {
                    eat('+') -> value + term()
                    eat('-') -> value - term()
                    else -> return value
                }
            }
        }

        fun term(): Double {
            var value = factor()
            while (true) {
                value = when {
                    eat('*') -> value * factor()
                    eat('/') -> value / factor()
                    eat('%') -> value / 100.0
                    else -> return value
                }
            }
        }

        fun factor(): Double {
            if (eat('-')) return -factor()
            if (eat('+')) return factor()
            var value = when {
                eat('(') -> expression().also { if (!eat(')')) error("bracket") }
                else -> number()
            }
            if (eat('^')) value = value.pow(factor())
            return value
        }

        fun number(): Double {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            return text.substring(start, pos).toDouble()
        }
    }
    return runCatching {
        val result = parser.expression()
        if (parser.pos != text.length || result.isNaN() || result.isInfinite()) return null
        val rounded = BigDecimal(result).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        rounded.toPlainString().replace('.', ',')
    }.getOrNull()
}

/** A unit the search can convert: what it measures and how many base units one of it is. */
private class UnitDef(val kind: String, val factor: Double)

private val UnitTable: Map<String, UnitDef> = buildMap {
    fun add(kind: String, factor: Double, vararg names: String) {
        names.forEach { put(it, UnitDef(kind, factor)) }
    }
    // Length (meters)
    add("length", 0.001, "mm")
    add("length", 0.01, "cm")
    add("length", 1.0, "m", "meter")
    add("length", 1000.0, "km", "kilometer")
    add("length", 0.0254, "in", "zoll", "inch")
    add("length", 0.3048, "ft", "fuß", "fuss", "foot", "feet")
    add("length", 0.9144, "yd", "yard")
    add("length", 1609.344, "mi", "meile", "meilen", "mile", "miles")
    // Mass (kilograms)
    add("mass", 0.000001, "mg")
    add("mass", 0.001, "g", "gramm")
    add("mass", 1.0, "kg", "kilo")
    add("mass", 1000.0, "t", "tonne", "tonnen")
    add("mass", 0.028349523125, "oz", "unze", "unzen")
    add("mass", 0.45359237, "lb", "lbs", "pfund", "pound", "pounds")
    // Volume (liters)
    add("volume", 0.001, "ml")
    add("volume", 0.01, "cl")
    add("volume", 1.0, "l", "liter")
    add("volume", 3.785411784, "gal", "gallone", "gallonen")
    add("volume", 0.0295735295625, "floz")
    // Speed (meters per second)
    add("speed", 1.0, "m/s")
    add("speed", 1.0 / 3.6, "km/h", "kmh", "kph")
    add("speed", 0.44704, "mph")
    add("speed", 0.514444, "kn", "knoten", "kt")
    // Data (bytes)
    add("data", 1.0, "byte", "bytes")
    add("data", 1_000.0, "kb")
    add("data", 1_000_000.0, "mb")
    add("data", 1_000_000_000.0, "gb")
    add("data", 1_000_000_000_000.0, "tb")
    // Time (seconds)
    add("time", 1.0, "s", "sek", "sec")
    add("time", 60.0, "min")
    add("time", 3600.0, "h", "std")
    add("time", 86400.0, "d", "tag", "tage", "tagen")
    // Temperature has its own rule
    add("temp", 0.0, "c", "f", "k")
}

private val ConversionPattern =
    Regex("""^(-?\d+(?:[.,]\d+)?)\s*([\p{L}°/²]+)\s*(?:in|to|nach|zu|=|->|→)\s*([\p{L}°/²]+)$""", RegexOption.IGNORE_CASE)

/** "5 km in mi" → "3,10686 mi"; null if the text isn't a conversion between two units of one kind. */
fun convertUnits(input: String): String? {
    val match = ConversionPattern.matchEntire(input.trim()) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val fromName = match.groupValues[2].lowercase().replace("°", "").replace(".", "")
    val toName = match.groupValues[3].lowercase().replace("°", "").replace(".", "")
    val from = UnitTable[fromName] ?: return null
    val to = UnitTable[toName] ?: return null
    if (from.kind != to.kind) return null
    val result = if (from.kind == "temp") {
        val celsius = when (fromName) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            else -> value - 273.15
        }
        when (toName) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            else -> celsius + 273.15
        }
    } else {
        value * from.factor / to.factor
    }
    if (result.isNaN() || result.isInfinite()) return null
    val shown = BigDecimal(result).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString().replace('.', ',')
    val unit = match.groupValues[3].replace(".", "").let { typed -> if (toName == "c" || toName == "f") "°" + typed.uppercase() else typed }
    return "$shown $unit"
}

/** A system setting the search can jump to. */
class SettingShortcut(val label: String, val words: List<String>, val action: String)

private val SettingShortcuts = listOf(
    SettingShortcut("WLAN", listOf("wlan", "wifi", "wi-fi", "internet"), Settings.ACTION_WIFI_SETTINGS),
    SettingShortcut("Bluetooth", listOf("bluetooth", "kopfhörer", "buds"), Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingShortcut("Mobile Daten", listOf("mobil", "daten", "sim", "netz"), Settings.ACTION_DATA_ROAMING_SETTINGS),
    SettingShortcut("Akku", listOf("akku", "batterie", "energie", "battery"), Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingShortcut("Töne & Vibration", listOf("ton", "töne", "vibration", "klingelton", "lautstärke", "sound"), Settings.ACTION_SOUND_SETTINGS),
    SettingShortcut("Anzeige", listOf("anzeige", "display", "helligkeit", "dunkel", "bildschirm"), Settings.ACTION_DISPLAY_SETTINGS),
    SettingShortcut("Benachrichtigungen", listOf("benachrichtigung", "mitteilung", "notification"), "android.settings.NOTIFICATION_SETTINGS"),
    SettingShortcut("Speicher", listOf("speicher", "storage", "platz"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingShortcut("Apps", listOf("apps", "anwendungen", "deinstall"), Settings.ACTION_APPLICATION_SETTINGS),
    SettingShortcut("Standort", listOf("standort", "gps", "location"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingShortcut("Datenschutz", listOf("datenschutz", "privatsphäre", "berechtigung"), Settings.ACTION_PRIVACY_SETTINGS),
    SettingShortcut("Sicherheit", listOf("sicherheit", "sperre", "fingerabdruck", "passwort"), Settings.ACTION_SECURITY_SETTINGS),
    SettingShortcut("Bedienungshilfen", listOf("bedienungshilfe", "barrierefrei", "accessibility"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingShortcut("Datum & Uhrzeit", listOf("datum", "uhrzeit", "zeit"), Settings.ACTION_DATE_SETTINGS),
    SettingShortcut("Sprache & Tastatur", listOf("sprache", "tastatur", "keyboard"), Settings.ACTION_LOCALE_SETTINGS),
    SettingShortcut("Hintergrundbild", listOf("hintergrund", "wallpaper"), Intent.ACTION_SET_WALLPAPER),
)

fun matchingSettings(query: String): List<SettingShortcut> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    return SettingShortcuts.filter { s -> s.words.any { it.startsWith(q) || q.startsWith(it) } || s.label.lowercase().startsWith(q) }
        .take(3)
}

private fun Context.openSetting(shortcut: SettingShortcut) {
    runCatching { startActivity(Intent(shortcut.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** Result row of the search calculator; tap copies the result. */
@Composable
fun CalculatorRow(expression: String, result: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    LiquidGlass(
        cornerRadius = 20.dp,
        interactive = true,
        tint = colors.surface.copy(alpha = 0.25f),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("Ergebnis", result)) }
                onDone()
            },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(expression, color = colors.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("= $result", color = colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text("Tippen zum Kopieren", color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingRow(shortcut: SettingShortcut, onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    LiquidGlass(
        cornerRadius = 20.dp,
        interactive = true,
        tint = colors.surface.copy(alpha = 0.25f),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                context.openSetting(shortcut)
                onDone()
            },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Einstellungen: ${shortcut.label}", color = colors.onSurface, fontSize = 16.sp)
        }
    }
}
