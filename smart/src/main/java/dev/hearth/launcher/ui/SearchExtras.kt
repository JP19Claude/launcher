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
