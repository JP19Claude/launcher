package dev.hearth.launcher.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * ZENITH 20.5, "Be Smarter": the next event of the calendar for the clock, as Smart Launcher 6 shows it
 * under the date. It only reads when the calendar's permission was given (asked for once, by Android's own
 * dialog, from the switch in the Illuminati) and only the next day and a half.
 */

internal class SmartEvent(val title: String, val begin: Long, val allDay: Boolean)

/** The event that is on now or comes next (an all-day event of today only when nothing with a time is left). */
internal fun nextCalendarEvent(context: Context, hideAllDay: Boolean = false, now: Long = System.currentTimeMillis()): SmartEvent? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now)
        ContentUris.appendId(it, now + 36L * 60L * 60L * 1000L)
    }.build()
    val today = LocalDate.now(ZoneId.systemDefault())
    return runCatching {
        var allDay: SmartEvent? = null
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            ),
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Termin"
                val begin = c.getLong(1)
                val end = c.getLong(2)
                if (c.getInt(3) == 1) {
                    if (hideAllDay) continue
                    // All-day events are kept in UTC: look at their days, not their hours.
                    val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    val last = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                    if (allDay == null && !today.isBefore(first) && !today.isAfter(last)) allDay = SmartEvent(title, now, true)
                } else if (end > now) {
                    return@runCatching SmartEvent(title, begin, false)
                }
            }
        }
        allDay
    }.getOrNull()
}

/** What the clock says of it: "Termin · 11:00 Zahnarzt", "Morgen · 09:30 …", "Jetzt · …" or "Heute · …". */
internal fun SmartEvent.line(now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    if (allDay) return "Heute · $title"
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(begin).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        begin <= now -> "Jetzt · $title"
        start.toLocalDate() == today -> "Termin · ${start.format(DateTimeFormatter.ofPattern("HH:mm", locale))} $title"
        start.toLocalDate() == today.plusDays(1) -> "Morgen · ${start.format(DateTimeFormatter.ofPattern("HH:mm", locale))} $title"
        else -> "${start.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))} $title"
    }
}

/** The next event while [enabled]; looked up again every five minutes ([slot] changes) off the main thread. */
@Composable
internal fun rememberNextEvent(enabled: Boolean, hideAllDay: Boolean, slot: Long): State<SmartEvent?> {
    val context = LocalContext.current.applicationContext
    return produceState<SmartEvent?>(null, enabled, hideAllDay, slot) {
        value = if (enabled) withContext(Dispatchers.IO) { nextCalendarEvent(context, hideAllDay) } else null
    }
}
