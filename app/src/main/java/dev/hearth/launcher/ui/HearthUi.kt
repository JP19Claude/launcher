package dev.hearth.launcher.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppUpdater
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.FluidRims
import dev.hearth.launcher.data.LauncherSettings
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Hearth UI: Hearth with Glimmer and Clawd built in – once called "Hearth One", now named
 * like One UI. Version 14 is the big one: a new name, ClaudeOS 3.0 "Nova" underneath, a new
 * look, the software update as on ColorOS with a full changelog, and a tour of what's new.
 */

/** Hearth UI's name and the things that go with its versions. */
object HearthUi {
    const val NAME = "Hearth UI"

    /** The big version the welcome tour is about; it shows once after reaching it. */
    const val INTRO_VERSION = 14

    /** "14.0" → "14", "13.5" → "13.5": how the version is written big. */
    fun major(version: String): String {
        val parts = version.split('.')
        val first = parts.firstOrNull().orEmpty().ifEmpty { version }
        val second = parts.getOrNull(1)
        return if (second == null || second == "0") first else "$first.$second"
    }

    private fun prefs(context: Context) = context.getSharedPreferences("hearth_ui", Context.MODE_PRIVATE)

    fun introSeen(context: Context): Boolean = prefs(context).getInt("introSeen", 0) >= INTRO_VERSION

    fun markIntroSeen(context: Context) {
        prefs(context).edit().putInt("introSeen", INTRO_VERSION).apply()
    }
}

/** Hearth UI 14's look: the stacked clock and calm rims (the tour offers it). */
fun LauncherSettings.hearthUi14Look(): LauncherSettings =
    copy(clockStyle = ClockStyle.Stacked, fluidRims = FluidRims.Calm)

/** Which app of the family a change is about. */
internal enum class ChangeApp { Hearth, Glimmer, Clawd }

/** One part of a changelog: what changed in one place. */
@Immutable
internal class ChangeSection(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<String>,
    val apps: Set<ChangeApp>,
)

/** One version's changelog. */
@Immutable
internal class ChangeRelease(
    val version: String,
    val name: String,
    val summary: String,
    val sections: List<ChangeSection>,
)

/**
 * Everything that changed, version by version – the software update screen shows the
 * running version's part in full and the ones before it as its history.
 */
internal object HearthChangelog {
    private val hearth = setOf(ChangeApp.Hearth)
    private val everyone = setOf(ChangeApp.Hearth, ChangeApp.Glimmer, ChangeApp.Clawd)
    private val withGlimmer = setOf(ChangeApp.Hearth, ChangeApp.Glimmer)

    private val Blue = Color(0xFF3E91FF)
    private val Violet = Color(0xFF8E6BFF)
    private val Terracotta = Color(0xFFD97757)
    private val Pink = Color(0xFFFF6FB5)
    private val Green = Color(0xFF30C26B)
    private val Orange = Color(0xFFFF9F0A)
    private val Slate = Color(0xFF5E6C84)

    val releases: List<ChangeRelease> = listOf(
        ChangeRelease(
            version = "14.8",
            name = "Nachtmodus",
            summary = "Ein Nachtmodus für ganz Hearth – und mit Shizuku fürs ganze Handy.",
            sections = listOf(
                ChangeSection(
                    "Nachtmodus", Icons.Rounded.Star, Violet,
                    listOf(
                        "Design → Nachtmodus: An, Aus oder nach Uhrzeit (Beginn und Ende frei wählbar).",
                        "Nachts wird Hearth dunkler und wärmer: dunkleres Hintergrundbild, dunkles ruhiges Glas, gedämpfte Fluid-Farben – auf Startbildschirm, im Drawer, im Finder und in den Einstellungen.",
                        "Mit Shizuku schalten Androids Dunkelmodus und der Augenkomfort (Blaulichtfilter) mit; zwischendurch von Hand Geändertes bleibt.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Behoben", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Einstellungen: Unten schien der Startbildschirm durch, wenn das Hintergrundbild zuletzt im Querformat gelesen wurde.",
                        "Einstellungen: Die Titelleiste beim Scrollen ist jetzt dunkel genug, dass nichts mehr durchscheint.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.7",
            name = "Downgrade verbessert",
            summary = "Der Downgrade versucht es jetzt auch ohne Shizuku – und zeigt sonst den Weg.",
            sections = listOf(
                ChangeSection(
                    "Downgrade", Icons.Rounded.Refresh, Blue,
                    listOf(
                        "Ohne Shizuku installiert Hearth die ältere Version jetzt selbst und bittet Android, den Downgrade zu erlauben.",
                        "Lehnt Android ab („App nicht installiert“), zeigt Hearth beide Wege: mit Shizuku (ein Tipp, alles bleibt) oder von Hand mit Sichern, Datei laden und Deinstallieren-Knopf.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.6",
            name = "Downgrade",
            summary = "Zurück zu jeder früheren Version – direkt im Software-Update.",
            sections = listOf(
                ChangeSection(
                    "Downgrade", Icons.Rounded.Refresh, Blue,
                    listOf(
                        "Ganz unten im Software-Update: alle früheren Versionen, eine antippen (zweimal zur Sicherheit) und zurückwechseln.",
                        "Mit Shizuku installiert Hearth die ältere Version direkt drüber – deine Einstellungen bleiben.",
                        "Ohne Shizuku lässt Android das nicht zu; dann lädt Hearth die Datei und erklärt den Weg von Hand.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.5",
            name = "Power-Update",
            summary = "15 Dinge, die One UI und ColorOS nicht können – damit sich Hearth UI lohnt.",
            sections = listOf(
                ChangeSection(
                    "Warum Hearth UI", Icons.Rounded.Star, Green,
                    listOf(
                        "Neue Seite in den Einstellungen: 15 Vorteile von Hearth UI gegenüber One UI und ColorOS auf einen Blick.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Turbo & Speicher", Icons.Rounded.Refresh, Orange,
                    listOf(
                        "Turbo: Hintergrund-Apps beenden und alle App-Caches leeren mit einem Tipp – samt Anzeige, wie viel frei wurde.",
                        "Nur Caches leeren, ohne Apps zu beenden.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Entrümpeln", Icons.Rounded.Build, Pink,
                    listOf(
                        "Vorinstallierte Apps entfernen (Facebook-Dienste, Samsung Free, OneDrive, AR-Zone, Samsung TV Plus …) – ohne Root.",
                        "Entfernte Apps jederzeit mit einem Tipp zurückholen. Entfernen fragt zur Sicherheit zweimal.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Datenschutz-Check", Icons.Rounded.Info, Blue,
                    listOf(
                        "Welche deiner Apps dürfen Standort, Kamera, Mikrofon, Kontakte, SMS, Anrufliste und Körpersensoren?",
                        "Mit Shizuku entzieht ein Tipp den Zugriff sofort; ohne öffnet er die Berechtigungen der App.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Akku & Anzeige", Icons.Rounded.Settings, Violet,
                    listOf(
                        "Hintergrund-Sperre: lange auf eine App drücken → „Im Hintergrund verbieten“ – Stromfresser laufen nur noch, wenn sie offen sind.",
                        "Bildschirm beim Laden anlassen, Pop-up-Benachrichtigungen aus, Extra dunkel.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Apps", Icons.Rounded.Menu, Slate,
                    listOf(
                        "APK teilen: jede App als Datei weitergeben oder sichern – direkt aus dem App-Menü (auch geteilte Apps).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Clawd", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "„Räum den Speicher auf“, „Beende die Hintergrund-Apps“, „Turbo“ – Clawd pflegt das Handy für dich (mit Shizuku).",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Easter Egg & Basis", Icons.Rounded.Favorite, Orange,
                    listOf(
                        "Hearth UI 14.5: „Kometen“ – zurückziehen, loslassen und fünf Kometen um den Hearth-Stern kreisen lassen.",
                        "ClaudeOS 3.1: Shizuku-Anbindung als Teil der Basis.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.3",
            name = "Mehr mit Shizuku",
            summary = "Tempo, Werbeblocker, Apps stoppen und einfrieren, Neustart – und Clawd schaltet für dich.",
            sections = listOf(
                ChangeSection(
                    "Shizuku", Icons.Rounded.Settings, Blue,
                    listOf(
                        "System-Animationen: Aus, Schnell (0,5×), Normal oder Langsam – „Schnell“ lässt das ganze Handy flotter wirken.",
                        "Werbeblocker fürs ganze Handy über Privates DNS: AdGuard, Cloudflare oder Quad9.",
                        "Hintergrund-Apps mit einem Tipp beenden.",
                        "Lange auf eine App drücken: „App stoppen“ und „Einfrieren“; eingefrorene Apps unter Einstellungen → System wieder auftauen.",
                        "Bildschirm aus, Neu starten und Ausschalten (mit Nachfrage).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Clawd", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "Clawd schaltet jetzt WLAN, Bluetooth, mobile Daten, Flugmodus, Standort, NFC, Dunkelmodus und Energiesparen selbst – „Clawd, mach das WLAN aus“ (mit Shizuku direkt, sonst öffnet er den Schalter).",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.2",
            name = "Shizuku",
            summary = "Hearth schaltet jetzt selbst, was Android normalen Apps verbietet.",
            sections = listOf(
                ChangeSection(
                    "Erweiterte Schalter mit Shizuku", Icons.Rounded.Settings, Blue,
                    listOf(
                        "Unter Einstellungen → System: mit der kostenlosen App Shizuku schaltet Hearth WLAN, Bluetooth, mobile Daten, Flugmodus, Standort, NFC, Dunkelmodus und Energiesparen selbst – ohne Root.",
                        "Hearth führt dich Schritt für Schritt hin: Shizuku installieren, starten (kabelloses Debugging), Hearth erlauben.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.1",
            name = "Systemeinstellungen",
            summary = "Die Einstellungen deines Handys jetzt auch in Hearth.",
            sections = listOf(
                ChangeSection(
                    "System in den Einstellungen", Icons.Rounded.Settings, Slate,
                    listOf(
                        "Neue Kategorie „System“ in den Hearth-Einstellungen.",
                        "Direkt hier einstellen: Helligkeit, automatische Helligkeit, Drehen, Bildschirm-Timeout, Taschenlampe, Nicht stören, Klingelmodus und alle Lautstärken.",
                        "Alles andere einen Tipp entfernt, gruppiert wie bei One UI: Verbindungen, Töne und Benachrichtigungen, Anzeige, Sicherheit, Akku, Apps und Konten, Allgemeine Verwaltung.",
                        "Die Einstellungssuche findet auch WLAN, Bluetooth, Akku & Co.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.0",
            name = "Hearth UI 14",
            summary = "Das größte Update bisher: ein neuer Name, eine neue Basis und ein neuer Look – vom Startbildschirm bis zum Software-Update.",
            sections = listOf(
                ChangeSection(
                    "Hearth UI 14", Icons.Rounded.Star, Violet,
                    listOf(
                        "Hearth One heißt jetzt Hearth UI – Hearth, Glimmer und Clawd in einer App, benannt wie One UI.",
                        "Neue Basis: ClaudeOS 3.0 „Nova“.",
                        "Willkommens-Tour beim ersten Start: alles Neue auf einen Blick, mit „Neuen Look verwenden“.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Software-Update wie bei ColorOS", Icons.Rounded.Refresh, Blue,
                    listOf(
                        "Großes Versions-Artwork mit Glanz und sanft treibendem Farbschein.",
                        "Ausführliches Änderungsprotokoll, nach Bereichen sortiert und zum Aufklappen.",
                        "Update-Verlauf: was jede bisherige Version gebracht hat.",
                        "Versionsdetails mit Build, Installationsdatum, letzter Prüfung und Quelle.",
                        "Statusanzeige oben, ein Knopf unten – beim Herunterladen läuft Clawd mit.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Startbildschirm", Icons.Rounded.Home, Terracotta,
                    listOf(
                        "Neue Uhr „Hearth UI“: Stunden über Minuten, groß und gestapelt.",
                        "Datum und Akku unter der Uhr als Glas-Chips – antippen öffnet Kalender bzw. Akku-Einstellungen.",
                        "Now Brief neu gestaltet: große Begrüßung zur Tageszeit, Wecker, Akku und Datum als Glas-Chips – gleich daneben Clawds Vorschläge.",
                        "Die Seitenpunkte fließen jetzt als Pille mit dem Wischen mit.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Fluid & Glas", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Neue Fluid-Ränder „Dezent“: ruhiges Glas mit nur einem Hauch von Claudes Farben – bunt wird es dort, wo gerade etwas passiert.",
                        "„Lebendig“ gibt es weiterhin: Design → Animationen → Fluid-Ränder.",
                        "Fluid-Wellen beim Öffnen einer App und beim Heimkommen sind ruhiger.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Einstellungen", Icons.Rounded.Settings, Slate,
                    listOf(
                        "Großer Titel wie bei One UI 9, der beim Scrollen sanft weggleitet.",
                        "Neue Hearth-UI-Karte ganz oben: dein Gerät mit Hearth UI- und ClaudeOS-Version.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Über das Telefon", Icons.Rounded.Phone, Blue,
                    listOf(
                        "Fünfmal auf die Hearth UI-Version tippen öffnet jetzt auch hier Hearth UIs Easter Egg – nicht nur das von ClaudeOS.",
                        "Die Versionen heißen jetzt Hearth UI und ClaudeOS 3.0 „Nova“.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Face, Orange,
                    listOf(
                        "Hearth UI 14: „Prisma“ – zieh das Licht durch ein Glasprisma, es fächert sich in Claudes Farben auf. Triff alle Kugeln.",
                        "ClaudeOS 3: „Nova“ – tippe den Stern an, bis er zur Supernova wird und sich zu Claudes Stern formt.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Glimmer", Icons.Rounded.Notifications, Orange,
                    listOf(
                        "Der Fluid-Rand der ruhenden Insel folgt dem neuen „Dezent“-Stil; läuft etwas, fließen die Farben wie gewohnt.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Behoben", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Bunte Punkte in den Icons und ein oranger Schein am Dock: hängengebliebene Fluid-Wellen verschwinden jetzt zuverlässig.",
                        "Verlässt du den Startbildschirm, werden laufende Wellen beendet – nichts bleibt halb gezeichnet stehen.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "13.5",
            name = "Flüssiges Glas",
            summary = "Finder nach unten, Drawer nach oben – und Glas, das wie ein Tropfen nachgibt.",
            sections = listOf(
                ChangeSection(
                    "Finder", Icons.Rounded.Search, Blue,
                    listOf(
                        "Nach unten wischen öffnet den Finder, nach oben den App-Drawer.",
                        "Neuer Finder: Milchglas mit Claudes Farben, Glas-Suchleiste mit fließendem Rand, Glas-Knöpfe, Clawd-Chips.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Glas", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Glas wabbelt beim Loslassen wie ein Tropfen; kleines Glas gibt mehr nach als große Flächen.",
                        "Mehr Glas im System: Chips, kleine Knöpfe, App-Menü-Aktionen.",
                        "Easter Egg „Tropfen“.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "13.1",
            name = "Kleines Update",
            summary = "Danksagung in der Telefoninfo, zuverlässigere Veröffentlichung.",
            sections = listOf(
                ChangeSection(
                    "Neu", Icons.Rounded.Info, Slate,
                    listOf(
                        "Telefoninfo: „made by Julius Pranner und Claude Code“.",
                        "Updates werden zuverlässiger veröffentlicht.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "13.0",
            name = "Butterweich",
            summary = "Keine Lags mehr, ein neuer App-Drawer und neue Animationen.",
            sections = listOf(
                ChangeSection(
                    "Leistung", Icons.Rounded.Build, Green,
                    listOf(
                        "Alle Fluid-Effekte laufen über eine gemeinsame Uhr und halten beim Scrollen still.",
                        "Startbildschirm, Icons und Glas ohne Dauer-Neuzeichnen.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "App-Drawer", Icons.Rounded.Menu, Violet,
                    listOf(
                        "Reihen steigen nacheinander auf, Seiten mit Tiefe, fließende Seitenpunkte.",
                        "Neues ⋮-Menü, das federnd aus dem Knopf wächst; Easter Egg „Seide“.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "12.5",
            name = "Fluid-Ozean",
            summary = "Fluid überall – bis in die Widgets.",
            sections = listOf(
                ChangeSection(
                    "Fluid", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Widgets mit Fluid-Farben und Fluid-Rahmen, auch Clawds Widgets für andere Launcher.",
                        "Fluid-Wellen beim Öffnen von Apps; Easter Eggs „Fluid-Ozean“ und ClaudeOS 2 „Blaze“.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "12.0",
            name = "ClaudeOS 2 „Blaze“",
            summary = "One UI 10 Fluid und Liquid Glass im ganzen System.",
            sections = listOf(
                ChangeSection(
                    "Neu", Icons.Rounded.Star, Violet,
                    listOf(
                        "One UI 10 Fluid überall, AI-Fluid-Ränder, Liquid Glass.",
                        "„Über das Telefon“ mit allen Daten deines Geräts; Hearth-Einstellungen als eigene App.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "11.0",
            name = "Alles in einer App",
            summary = "Hearth, Glimmer und Clawd in einer App – und Updates direkt aus der App.",
            sections = listOf(
                ChangeSection(
                    "Neu", Icons.Rounded.ThumbUp, Terracotta,
                    listOf(
                        "Die Ausgabe mit Glimmer und Clawd eingebaut (heute Hearth UI).",
                        "Jede App sucht selbst nach Updates und installiert sie.",
                    ),
                    everyone,
                ),
            ),
        ),
    )

    /** The changelog of [version] – or the newest one that isn't newer than it. */
    fun forVersion(version: String): ChangeRelease? =
        releases.firstOrNull { !AppUpdater.isNewer(it.version, version) }

    /** Everything before [version], newest first. */
    fun before(version: String): List<ChangeRelease> {
        val current = forVersion(version)
        return releases.filter { it !== current && !AppUpdater.isNewer(it.version, version) }
    }

    fun appOf(packageName: String): ChangeApp = when (packageName) {
        "dev.hearth.glimmer" -> ChangeApp.Glimmer
        "dev.hearth.clawd" -> ChangeApp.Clawd
        else -> ChangeApp.Hearth
    }

    /** The parts that are about [app]; Hearth UI has everything inside, so it shows them all. */
    fun sectionsFor(release: ChangeRelease, app: ChangeApp): List<ChangeSection> =
        if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) release.sections else release.sections.filter { app in it.apps }
}

/**
 * The version written big, as ColorOS does on its update screen: glossy, light from above,
 * a blurred glow underneath and a soft aura of [colors] drifting behind it.
 */
@Composable
fun VersionArtwork(number: String, colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val flow = flowPhase(18_000, LocalSettings.current.animations)
    val big = (height.value * (if (number.length > 2) 0.48f else 0.72f)).sp
    val face = Brush.verticalGradient(listOf(Color.White, colors.first(), colors.last()))
    val style = TextStyle(brush = face, fontSize = big, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val t = flow?.invoke() ?: 0.8f
            colors.forEachIndexed { i, c ->
                val a = t + i * 2.1f
                val at = Offset(
                    size.width / 2f + cos(a) * size.height * 0.3f,
                    size.height / 2f + sin(a * 1.3f) * size.height * 0.14f,
                )
                val r = size.height * 0.62f
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.34f), Color.Transparent), center = at, radius = r), radius = r, center = at)
            }
        }
        // The glow underneath (blur needs Android 12; without it the copy would just double).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                number,
                style = TextStyle(brush = Brush.linearGradient(colors), fontSize = big, fontWeight = FontWeight.Black, letterSpacing = (-4).sp),
                modifier = Modifier
                    .graphicsLayer {
                        translationY = 10.dp.toPx()
                        alpha = 0.6f
                    }
                    .blur(20.dp),
            )
        }
        Text(
            number,
            style = style,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Light across the top of the digits only.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.55f),
                            0.45f to Color.White.copy(alpha = 0.06f),
                            0.55f to Color.Transparent,
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}

/** One part of a changelog: a colored icon, its title and what changed. */
@Composable
internal fun ChangeSectionView(section: ChangeSection, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(lerp(section.color, Color.White, 0.25f), section.color))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(section.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        section.items.forEach { item ->
            Row(Modifier.padding(start = 44.dp, top = 7.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(section.color),
                )
                Spacer(Modifier.width(10.dp))
                Text(item, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/**
 * "Was ist neu": a version's whole changelog on glass – the first parts open, the rest a tap
 * away ("Alle … anzeigen").
 */
@Composable
internal fun ChangelogCard(
    release: ChangeRelease,
    app: ChangeApp,
    title: String,
    accent: Color,
    card: Color,
    modifier: Modifier = Modifier,
) {
    val sections = remember(release, app) { HearthChangelog.sectionsFor(release, app) }
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "more")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(card)
            .glassSheen(26.dp)
            .aiFluidEdge(26.dp, strength = 0.4f, width = 1.dp, enabled = LocalSettings.current.fluidDesign, flowing = false)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
            .padding(vertical = 14.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            release.summary,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
        )
        val shown = if (expanded) sections else sections.take(3)
        shown.forEach { ChangeSectionView(it) }
        if (sections.size > 3) {
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Weniger anzeigen" else "Alle ${sections.size} Bereiche anzeigen",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

/** "Update-Verlauf": the versions before, each opening to its changelog. */
@Composable
internal fun UpdateHistoryCard(releases: List<ChangeRelease>, app: ChangeApp, accent: Color, card: Color) {
    var open by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(card)
            .glassSheen(26.dp)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
            .padding(vertical = 6.dp),
    ) {
        releases.forEachIndexed { index, release ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
            }
            val isOpen = open == release.version
            val turn by animateFloatAsState(if (isOpen) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "history")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (isOpen) null else release.version }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${release.version} · ${release.name}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(release.summary, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp)
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
            if (isOpen) {
                HearthChangelog.sectionsFor(release, app).forEach { ChangeSectionView(it) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** A line of the tour: a colored icon, a title and a sentence. */
private class TourPoint(val icon: ImageVector, val color: Color, val title: String, val text: String)

/**
 * The tour after updating to Hearth UI 14, as phones show after a big system update: the
 * version big, then what's new page by page – and the choice to switch to the new look.
 */
@Composable
fun HearthUiIntro(onDone: (newLook: Boolean) -> Unit) {
    val pages = 4
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    var newLook by remember { mutableStateOf(true) }
    val animate = LocalSettings.current.animations
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 160f)) }
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone(newLook)
    }
    val last = pager.currentPage == pages - 1

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0C1C), Color(0xFF140D26), Color(0xFF050506))))
            // Nothing underneath gets a touch while the tour is open.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        FluidBackdrop(AiFluidColors.take(3), strength = 0.7f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Überspringen",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onDone(newLook) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - 0.6f * off
                            val s = 1f - 0.08f * off
                            scaleX = s
                            scaleY = s
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> TourHero { appear.value }
                        1 -> TourPage(
                            title = "Ein neuer Look",
                            text = "Ruhiger, klarer und mit mehr Tiefe – vom Startbildschirm bis in die Einstellungen.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Home, Color(0xFFD97757), "Neue Uhr", "Stunden über Minuten, groß und gestapelt. Datum und Akku als Glas-Chips darunter."),
                                TourPoint(Icons.Rounded.Star, Color(0xFF8E6BFF), "Now Brief, neu gestaltet", "Große Begrüßung zur Tageszeit, Wecker, Akku und Datum auf einen Blick."),
                                TourPoint(Icons.Rounded.Favorite, Color(0xFFFF6FB5), "Dezente Fluid-Ränder", "Ruhiges Glas mit einem Hauch von Claudes Farben – bunt wird es, wo etwas passiert."),
                                TourPoint(Icons.Rounded.Settings, Color(0xFF5E6C84), "Einstellungen wie One UI 9", "Ein großer Titel, der beim Scrollen weggleitet, und die neue Hearth-UI-Karte."),
                            ),
                        ) {
                            TourSwitch(
                                title = "Neuen Look verwenden",
                                text = "Uhr und Fluid-Ränder auf Hearth UI 14 umstellen – jederzeit in den Einstellungen änderbar.",
                                checked = newLook,
                            ) { newLook = it }
                        }
                        2 -> TourPage(
                            title = "Software-Update, neu gedacht",
                            text = "Wie bei ColorOS: oben die Version groß, darunter alles, was sich verändert hat.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Refresh, Color(0xFF3E91FF), "Großes Versions-Artwork", "Mit Glanz und einem Farbschein, der sanft dahinter treibt."),
                                TourPoint(Icons.Rounded.Menu, Color(0xFF8E6BFF), "Ausführliches Änderungsprotokoll", "Nach Bereichen sortiert und zum Aufklappen – dazu der Verlauf aller Versionen."),
                                TourPoint(Icons.Rounded.Info, Color(0xFF5E6C84), "Versionsdetails", "Hearth UI, ClaudeOS, Build und letzte Prüfung auf einen Blick."),
                            ),
                        )
                        else -> TourPage(
                            title = "ClaudeOS 3.0 „Nova“",
                            text = "Eine neue Basis unter Hearth UI, Glimmer und Clawd.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Star, Color(0xFFFFB494), "Neue Basis", "ClaudeOS 3.0 „Nova“ – der neue Stern unter Hearth UI 14."),
                                TourPoint(Icons.Rounded.Face, Color(0xFFFF9F0A), "Zwei neue Easter Eggs", "„Prisma“ und „Nova“: fünfmal auf die Version tippen – im Software-Update und in „Über das Telefon“."),
                                TourPoint(Icons.Rounded.CheckCircle, Color(0xFF30C26B), "Behoben", "Keine bunten Punkte mehr in den Icons und kein oranger Schein am Dock."),
                            ),
                        )
                    }
                }
            }
            TourDots(pager, pages, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFD97757), Color(0xFF8E6BFF), Color(0xFF3E91FF))))
                    .glassSheen(27.dp)
                    .clickable {
                        if (last) onDone(newLook) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (last) "Los geht's" else "Weiter", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The tour's first page: the version big, Clawd dancing, ClaudeOS underneath. */
@Composable
private fun TourHero(appear: () -> Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = appear()
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 60.dp.toPx()
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        VersionArtwork("14", listOf(Color(0xFFFFB494), Color(0xFFFF6FB5), Color(0xFF8E6BFF), Color(0xFF3E91FF)), height = 230.dp)
        val product = if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) HearthUi.NAME else "Hearth"
        Text("$product 14", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
            style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Das größte Update bisher. Ein neuer Name, eine neue Basis und ein neuer Look – wisch weiter und sieh, was sich alles verändert hat.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Clawd(Modifier.size(width = 96.dp, height = 82.dp), mood = ClawdMood.Dance)
    }
}

@Composable
private fun TourPage(title: String, text: String, points: List<TourPoint>, extra: @Composable () -> Unit = {}) {
    Spacer(Modifier.height(28.dp))
    Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .glassSheen(26.dp)
            .padding(vertical = 8.dp),
    ) {
        points.forEach { point ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(lerp(point.color, Color.White, 0.25f), point.color))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(point.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(point.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(point.text, color = Color.White.copy(alpha = 0.68f), fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    extra()
    Spacer(Modifier.height(20.dp))
}

/** A switch on glass for the tour. */
@Composable
private fun TourSwitch(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "tourSwitch")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .glassSheen(22.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(Color.White.copy(alpha = 0.22f), Color(0xFF3E91FF), knob.coerceIn(0f, 1f))) },
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(22.dp)
                    .graphicsLayer { translationX = knob * 20.dp.toPx() }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** The tour's page dots: the current one stretches to a pill and flows with the swipe. */
@Composable
private fun TourDots(state: PagerState, count: Int, modifier: Modifier = Modifier) {
    val dot = 7.dp
    val wide = 22.dp
    val gap = 8.dp
    Canvas(modifier.size(width = wide + (dot + gap) * (count - 1), height = dot)) {
        val at = state.currentPage + state.currentPageOffsetFraction
        val h = size.height
        var x = 0f
        for (i in 0 until count) {
            val near = (1f - abs(at - i)).coerceIn(0f, 1f)
            val w = dot.toPx() + (wide - dot).toPx() * near
            drawRoundRect(
                Color.White.copy(alpha = 0.35f + 0.6f * near),
                topLeft = Offset(x, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
            x += w + gap.toPx()
        }
    }
}
