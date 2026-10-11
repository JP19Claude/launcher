package dev.hearth.launcher.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * ZENITH 20, the day's brief: what to say about the day from what the phone knows – the weather,
 * the battery, the protection, today's perfume, the next alarm. Written without the AI; the
 * Zentrale can have Claude word it in its own way from the same facts.
 */
object ZenithBrief {

    /** "Guten Morgen" … by the hour. */
    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..16 -> "Guten Tag"
        in 17..21 -> "Guten Abend"
        else -> "Gute Nacht"
    }

    /** What the weather means for the day, in one sentence. */
    fun weatherTip(report: WeatherReport): String {
        val storm = report.hours.any { it.code in 95..99 }
        val snow = report.code in 71..77 || report.code in 85..86 || report.hours.any { it.code in 71..77 }
        return when {
            storm -> "Heute kann es gewittern – bleib nicht lange draußen."
            snow -> "Es schneit oder wird schneien – zieh dich warm an."
            report.rain >= 60 -> "Nimm einen Schirm mit: ${report.rain} % Regenwahrscheinlichkeit."
            report.rain >= 30 -> "Vielleicht wird es nass – ein Schirm schadet nicht."
            report.max >= 28 -> "Es wird heiß, bis ${report.max} °C – trink genug."
            report.max <= 3 -> "Es bleibt kalt, nicht über ${report.max} °C – Mütze und Handschuhe."
            report.now < 10 -> "Es ist frisch, eine Jacke schadet nicht."
            report.night -> "Ruhige Nacht, ${report.sky}."
            else -> "Angenehmes Wetter, ${report.sky}."
        }
    }

    /** The facts for the AI, as plain lines it can word however it likes. */
    fun facts(
        hour: Int,
        city: String,
        report: WeatherReport?,
        battery: Int,
        charging: Boolean,
        shield: Int?,
        perfume: String?,
        alarm: String?,
    ): String {
        val today = LocalDate.now()
        val lines = mutableListOf<String>()
        lines += "Datum: ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)}, ${today.dayOfMonth}. ${today.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}, $hour Uhr"
        if (city.isNotBlank()) lines += "Wohnort: $city"
        if (report != null) {
            lines += "Wetter jetzt: ${report.summary}, gefühlt ${report.feels.toInt()} °C, Wind ${report.wind.toInt()} km/h"
            lines += "Heute: höchstens ${report.max} °C, mindestens ${report.min} °C, Regenwahrscheinlichkeit ${report.rain} %"
            report.days.drop(1).take(2).forEach { lines += "${it.name}: ${it.min} bis ${it.max} °C, ${ZenithWeather.skyOf(it.code)}" }
        }
        if (battery >= 0) lines += "Akku: $battery %" + if (charging) " (lädt)" else ""
        if (shield != null) lines += "Schutz-Punkte des Telefons: $shield von 100"
        if (perfume != null) lines += "Parfüm für heute: $perfume"
        if (alarm != null) lines += "Nächster Wecker: $alarm"
        return lines.joinToString("\n")
    }

    const val AI_SYSTEM = "Du bist ZENITH, die Stimme dieses Telefons. Schreib aus den Fakten einen kurzen Tagesbrief auf Deutsch: höchstens vier Sätze, in der Du-Form, freundlich, konkret und ohne Aufzählung. Erwähne nur, was wirklich wichtig für den Tag ist (Wetter-Tipp, Akku nur wenn er knapp ist, Wecker, Parfüm wenn eins da ist). Erfinde nichts dazu."
}
