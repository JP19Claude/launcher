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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Share
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
    const val NAME = "ZENITH"

    /** The big version the welcome tour is about; it shows once after reaching it. */
    const val INTRO_VERSION = 19

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

    /** Once: "OMEGA UI is now ZENITH" – the sun rising to the zenith. */
    fun omegaSeen(context: Context): Boolean = prefs(context).getBoolean("zenith19Seen", false)

    fun markOmegaSeen(context: Context) {
        prefs(context).edit().putBoolean("zenith19Seen", true).apply()
    }

    /** OMEGA Labs: show the welcome tour again next time. */
    fun resetIntro(context: Context) {
        prefs(context).edit().remove("introSeen").apply()
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
            version = "19.0",
            name = "ZENITH",
            summary = "Aus OMEGA UI wird ZENITH – die Sonne im höchsten Punkt: ein neuer Name, ein neues Logo, eine neue Farbe, eine neue Uhr. Alles fühlt sich neu an, und die alten Easter Eggs bleiben.",
            sections = listOf(
                ChangeSection(
                    "ZENITH", Icons.Rounded.Star, Green,
                    listOf(
                        "OMEGA UI heißt jetzt ZENITH – wie die Sonne, wenn sie im Zenit steht, ganz oben am Himmel.",
                        "Ein neues Logo: ein weißes Z aus Licht, die 19 in Grün quer hindurch und darüber die Sonne – in den Einstellungen, unter Über das Telefon, im Software-Update und in der Tour.",
                        "Beim ersten Start: Das Ω verblasst, die Sonne steigt an ihrem Bogen aus der Nacht bis in den Zenit, der Himmel wird hell – und oben entsteht ZENITH.",
                        "Eine neue Tour durch alles, was neu ist.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Logo gedrückt halten", Icons.Rounded.Favorite, Orange,
                    listOf(
                        "Wie bei ColorOS: Hältst du das ZENITH-Logo gedrückt, lädt es sich auf – die Sonne klettert nach oben, die Strahlen drehen sich und werden länger, das Logo hebt sich und leuchtet.",
                        "Ist es voll, klopft es spürbar; lässt du los, federt es zurück.",
                        "Das Geheimnis bleibt: So oft antippen, wie ZENITH Nummer hat – 19-mal –, und du bist bei den Clawd Illuminati.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Zenith-Grün", Icons.Rounded.Refresh, Blue,
                    listOf(
                        "Eine neue Systemfarbe: Zenith – Grün, Sonnengelb und Himmelblau fließen durch Glas, Ränder, Menüs, Widgets, Glimmer und das Always On Display.",
                        "Neu: Akzent „Zenith-Grün“ und Glas-Tönung „Sonnenglas“ (Glas mit einem Hauch Grün).",
                        "Farben nach Sonnenstand: Zenith folgt dem Tag – morgens Gold und Pfirsich, mittags Grün und Himmel, abends Glut, nachts Mondblau.",
                        "Liquid Glass im Zenith-Licht: klar wie bei Apple, mit einem Glanz aus Sonne und Grün.",
                        "Galaxy × Claude bekommt das ZENITH-Design; wer OMEGA Glass in Rubin hatte, wechselt zu Zenith – Rubin und Galaxy Omega bleiben wählbar.",
                        "Die Einstellungen haben einen eigenen Bereich „ZENITH“.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Sonnenbogen", Icons.Rounded.Home, Green,
                    listOf(
                        "Eine neue Uhr für den Startbildschirm: Über der Uhrzeit spannt sich der Bogen der Sonne, und die Sonne steht darauf, wo sie gerade am Himmel steht – nachts wandert der Mond.",
                        "Darunter, was die Sonne gerade tut: „Zenit in 2 Std.“, „Sonne im Zenit“, „Sonnenuntergang in 45 Min.“, „Nadir – tiefste Nacht“.",
                        "Den Sonnenbogen gibt es auch für das große Always On Display (Clawd Illuminati → Always On Display → Uhr).",
                        "Mit Ziffern aus Glas, wenn „Uhren aus Glas“ an ist.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Face, Violet,
                    listOf(
                        "ZENITH 19: der Sonnenlauf – schieb die Sonne ihren Bogen hinauf bis in den Zenit; jeder der 19 Clawds am Horizont, den das Licht erreicht, wacht auf.",
                        "Das Egg-Museum: Im Software-Update unter Update-Verlauf lange auf eine alte Version drücken – ihr Easter Egg kommt wieder. Rubin, Laterne, Schmiede, Lupe, Glühwürmchen, Tresor, Kometen, Prisma … alle von Hearth und OMEGA UI bleiben.",
                        "Rubin, Ω, OMEGA-Insel, OMEGA Cloud, die Clawd Illuminati und Claude Mythos sind weiter da.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "18.5",
            name = "Liquid",
            summary = "Das große Glas-Update: Liquid Glass wie bei Apple für das ganze System – und Glas an jeder Stelle, bis zu den Schaltern.",
            sections = listOf(
                ChangeSection(
                    "Glas-Stil", Icons.Rounded.Star, Blue,
                    listOf(
                        "Neuer Glas-Stil für das ganze System: OMEGA Glass oder Liquid Glass wie bei Apple – unter Design → OMEGA und in den Clawd Illuminati.",
                        "Liquid Glass: klar statt getönt, eine tiefere Linse, mehr Glanz und Apples heller Innenrand rundum; die Fluid-Ränder leuchten weiß mit einem Hauch Regenbogen.",
                        "Finder, App-Drawer und Glasflächen bleiben dabei neutral und hell – keine Färbung über dem Bildschirm.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Schalter aus Glas", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Alle Schalter im System sind jetzt aus Liquid Glass: Beim Festhalten wird der Knopf zur klaren Glaslinse, beim Umschalten dehnt er sich wie ein Tropfen.",
                        "Alle Schieberegler ebenso – der Knopf wird beim Ziehen zur Linse.",
                        "In den Einstellungen, den Clawd Illuminati, den versteckten Menüs, Glimmer Drop, dem Menü des App-Drawers und der Tour.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Glas überall", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Uhren aus Glas: jede Uhr auf dem Startbildschirm (One UI, Groß, ColorOS, gestapelt) mit Ziffern aus Glas – abschaltbar.",
                        "Statusleiste aus Glas: ein weicher Streifen Milchglas hinter der Statusleiste auf dem Startbildschirm, im App-Drawer und in den Einstellungen.",
                        "Clawds Sprechblase, die Easter-Egg-Hinweise und „Apps ausblenden“ sind jetzt aus Glas.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Easter Egg", Icons.Rounded.Search, Violet,
                    listOf("OMEGA UI 18.5: die Lupe – schieb eine Linse aus Liquid Glass über ein Feld aus Lichtern und finde sechs winzige Clawds."),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "18.3",
            name = "Liquid Glass",
            summary = "OMEGA Glass klar wie Apples Liquid Glass – und ein deutlich flüssigeres System.",
            sections = listOf(
                ChangeSection(
                    "Liquid Glass", Icons.Rounded.Star, Blue,
                    listOf(
                        "Neue Farbe für OMEGA Glass: „Liquid Glass“ – klares, fast farbloses Glas wie bei Apple, mit tieferer Linse, mehr Glanz an den Kanten und OMEGA Fluid in Weiß mit einem Hauch Regenbogen.",
                        "Unter Design → OMEGA → Farbe von OMEGA Glass; Akzent und Glas-Tönung wechseln mit.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Flüssiger", Icons.Rounded.Refresh, Green,
                    listOf(
                        "Der Startbildschirm steht jetzt still, wenn nichts passiert: OMEGA-Beleuchtung, die Ränder und das Fluid der Suchleisten fließen nicht mehr dauernd (nur beim Tippen oder mit Ω-Overdrive) – vorher wurde dadurch jedes Glas Bild für Bild neu berechnet.",
                        "Alle Clawds laufen an einer gemeinsamen, ruhigeren Uhr statt jeder mit eigenen 120 Bildern pro Sekunde, und halten beim Scrollen kurz still.",
                        "Claude Mythos ist leichter: kein Overdrive und kein Farbfilter mehr über dem ganzen Bildschirm, die vielen Clawds stehen still.",
                        "Einstellungen werden nach einer Änderung nur noch einmal neu gelesen statt einmal pro Eintrag.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "18.2",
            name = "Dein AOD",
            summary = "Ein eigenes Bild für das große Always On Display.",
            sections = listOf(
                ChangeSection(
                    "Always On Display", Icons.Rounded.Star, Violet,
                    listOf(
                        "Eigenes Bild fürs große AOD: in den Clawd Illuminati ein Bild aus der Galerie auswählen – es wird dein großes Always On Display, statt des Hintergrundbilds.",
                        "Das Bild wird verkleinert in der App gespeichert, mit Vorschau; „Anderes Bild“ tauscht es, „Entfernen“ zeigt wieder das Hintergrundbild.",
                        "Beim Auswählen eines Bildes geht das große AOD gleich mit an.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "18.1",
            name = "Always On",
            summary = "Ein volles Always On Display wie beim Galaxy S24 Ultra und viele neue Einstellungen für den Sperrbildschirm – in den Clawd Illuminati.",
            sections = listOf(
                ChangeSection(
                    "Always On Display", Icons.Rounded.Star, Violet,
                    listOf(
                        "Volles Always On Display für jedes Handy – auch für Geräte, die es nicht haben, wie das S22 Ultra: Das Hintergrundbild bleibt gedimmt sichtbar, davor eine große Uhr (so wie es das S24 Ultra eingebaut hat).",
                        "Dafür muss das AOD des Handys an sein und auf „Immer anzeigen“ stehen (Einstellungen → Sperrbildschirm → Always On Display) – OMEGA legt sein großes AOD dann darüber.",
                        "Uhr in vier Stilen (OMEGA, Groß, Dünn, In Worten), in Weiß, OMEGA oder Gold; Helligkeit des Hintergrunds in drei Stufen.",
                        "Dazu wählbar: Datum, Akku, wartende Mitteilungen, ein schlafender Clawd und ein feiner OMEGA-Rand.",
                        "Alles wandert jede Minute ein kleines Stück, damit nichts einbrennt.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Sperrbildschirm", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "Clawd auf dem Sperrbildschirm (winkt, beim Laden tanzt er), ein leuchtender OMEGA-Rand, Lade-Licht mit großem Akkustand, Begrüßung nach Tageszeit, Akku-Chip und das Ω.",
                        "Eigene Nachricht auf dem Sperrbildschirm.",
                        "Die Atmosphäre-Vorteile (Schnee, Sterne, Glühwürmchen …) auf Wunsch auch auf dem Sperrbildschirm.",
                        "Alles in den Clawd Illuminati; Glimmer zeichnet es über Sperrbildschirm und AOD, ohne eine Berührung abzufangen.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "18.0",
            name = "Mythos",
            summary = "Das große Update auf neuer Basis: OMEGA UI 18 auf ClaudeOS 5.0 „Mythos“ – mit Claude Mythos, 25 neuen Vorteilen und einer Farbwelt, die jetzt wirklich alles färbt.",
            sections = listOf(
                ChangeSection(
                    "ClaudeOS 5.0 „Mythos“", Icons.Rounded.Star, Orange,
                    listOf(
                        "Neue Basis: ClaudeOS 5.0 mit dem Codenamen „Mythos“ – die Clawd Illuminati, die Vorteile und Claude Mythos gehören jetzt zum Fundament.",
                        "Neues ClaudeOS-Easter-Egg: den Finger auf das Dunkel halten, bis das goldene Claude-Zeichen erwacht und der Rat der sechs Clawds erscheint.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Claude Mythos", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "Der große Schalter ganz oben in den Clawd Illuminati: Solange Mythos an ist, übernehmen die Clawds das ganze System.",
                        "Alles auf Höchststufe: OMEGA Glass und Fluid im Overdrive, Hyperglas, Heiligenscheine, Parade, allsehendes Auge, Sternenhimmel, Aurora, Glühwürmchen, Sternschnuppen, Parallax, Funkenspur, Leuchtfarben, Riesen-Uhr, Clawd-Schwarm und -Gruß.",
                        "Ein riesiger goldener Clawd wacht über dem Startbildschirm, oben meldet ein goldenes Banner: „Die Clawds haben übernommen“.",
                        "Ausgeschaltet ist alles wieder so wie vorher.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Claude Mythos: viel mehr Clawds, überall – riesige hinter dem Startbildschirm, kleine, die vom Rand hereinschauen und auf dem Dock sitzen.",
                        "Die Clawds übernehmen auch Widgets und Menüs: auf jedem Widget sitzt eine kleine Gruppe, im App-Menü, im OMEGA-Menü und über jeder Gruppe der Einstellungen ebenfalls, auf den Glasflächen von Drawer und Finder noch mehr.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "25 neue Vorteile", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Atmosphäre: Schneefall, Glühwürmchen, Seifenblasen, Herbstlaub, Sternenhimmel, Matrix-Regen, Rubin-Regen, Sternschnuppen.",
                        "Licht: Vignette, Filmkorn, Augenschutz (warmes Licht im ganzen System), Nachtruhe, Aurora-Himmel, Tageszeit-Licht, Akku-Aura, Ω-Wasserzeichen.",
                        "Bewegung: Parallax beim Neigen, Riesen-Uhr, Icon-Puls, Funkenspur unter dem Finger.",
                        "Farbe: Noir (Schwarz-Weiß), Sepia, Leuchtfarben – im ganzen System.",
                        "Clawd: Clawd-Schwarm und Clawd-Gruß.",
                        "Alle einzeln an- und ausschaltbar in den Clawd Illuminati; „Den Rat auflösen“ schaltet alles auf einmal aus.",
                        "Die Clawd Illuminati bleiben geheim: Sie sind nur noch über den Ω-Rubin erreichbar (Software-Update, Telefoninfo oder der Rubin oben in den Einstellungen) – kein Eintrag mehr in den Einstellungen oder im OMEGA-Menü.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Farbwelt", Icons.Rounded.Settings, Violet,
                    listOf("Die Farbwelt färbt jetzt auch auf dem Startbildschirm alles – OMEGA Glass, OMEGA Fluid, Widgets, Dock, Icons und Menüs. Vorher griff sie dort nicht."),
                    hearth,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Search, Blue,
                    listOf(
                        "OMEGA UI 18: die Schmiede – mit 18 Hammerschlägen wird aus dem Stein der Ω-Rubin.",
                        "Beim ersten Start zeigt OMEGA UI 18 wieder die große Übergangsanimation.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.6",
            name = "Illuminati",
            summary = "Aus der Clawd Illumination werden die Clawd Illuminati: der geheime Rat der sechs Clawds – und das allsehende Auge.",
            sections = listOf(
                ChangeSection(
                    "Clawd Illuminati", Icons.Rounded.Star, Orange,
                    listOf(
                        "Oben tagt jetzt der geheime Rat: sechs Clawds in Kapuzenroben an einem dreieckigen Tisch, nur von einer Kerze beleuchtet – darüber das Auge in der Pyramide, das blinzelt und sich umschaut.",
                        "Das ganze Menü im versteckten Illuminati-Stil: dunkle Karten mit abgeschnittenen Ecken und Goldrand, vor jeder Überschrift ein Auge im Dreieck, jeder Schalter in seiner eigenen Pyramide, Augen-Pyramiden in der Wand.",
                        "Neu: „Allsehendes Auge“ – oben auf dem Startbildschirm wacht das Auge in seiner Pyramide.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.5",
            name = "Illumination",
            summary = "Das große OMEGA-Update: Ω und Clawd überall im System, ein neues Easter Egg – und ein Geheimnis im Ω-Kristall.",
            sections = listOf(
                ChangeSection(
                    "Ω überall", Icons.Rounded.Star, Pink,
                    listOf(
                        "Das Ω führt die Seitenpunkte auf dem Startbildschirm an und steht am Ende der Suchleiste.",
                        "App-Drawer und Finder: ein großes Ω ist ins Glas geätzt, die Überschriften tragen ein Ω.",
                        "Einstellungen: jede Gruppe mit Ω, die Überschriften in den Farben von OMEGA Fluid.",
                        "App-Menü: OMEGA-Glas, ein Ω im Kopf und eine Linie aus OMEGA Fluid. Das OMEGA-Menü heißt „Ω OMEGA-Menü“.",
                        "Now Brief mit Ω im Kopf.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Clawd überall", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "Clawd sitzt jetzt auf dem Rand der großen Glasflächen im App-Drawer und im Finder und lässt die Beine baumeln.",
                        "Clawd kann einen leuchtenden Heiligenschein tragen – überall, wo er auftaucht.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Ein Geheimnis", Icons.Rounded.Favorite, Violet,
                    listOf(
                        "Im Ω-Kristall wohnt etwas. Wer oft genug klopft, kommt in die Clawd Illumination – wie oft, verrät die Nummer von OMEGA UI. 😉",
                        "Einmal gefunden, steht sie in den Einstellungen und im OMEGA-Menü.",
                        "Neues Easter Egg Nr. 32.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Easter Egg", Icons.Rounded.Search, Blue,
                    listOf("OMEGA UI 17.5 hat ein neues Versions-Easter-Egg: die Laterne. Mit Clawds Licht im Dunkeln fünf versteckte Ω finden."),
                    hearth,
                ),
                ChangeSection(
                    "System", Icons.Rounded.Settings, Slate,
                    listOf("Änderungen aus anderen Bildschirmen der App (Software-Update, Telefoninfo) kommen jetzt sofort auf dem Startbildschirm an, ohne Neustart."),
                    everyone,
                ),
                ChangeSection(
                    "Hotfix", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "OMEGA-Beleuchtung: Auf dem Startbildschirm leuchtet OMEGA Fluid jetzt zwischen Hintergrundbild und Apps und Widgets – aus der Ecke hinter der Uhr, durch die Mitte und unten hinter dem Dock. Schalter unter Design → OMEGA.",
                        "Der Dark Mode greift jetzt auch im Finder und im App-Drawer: viel dunkler, die Farben glühen nur noch, die großen Glasflächen werden tiefer.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.4",
            name = "Glas-Uhr",
            summary = "Viel mehr OMEGA Glass im App-Drawer und im Now Brief – und die One-UI-Uhr mit Ziffern aus Glas.",
            sections = listOf(
                ChangeSection(
                    "Uhr aus Glas", Icons.Rounded.Star, Pink,
                    listOf(
                        "Mit OMEGA Glass sind die Ziffern der One-UI-Uhr aus Glas: Durch sie scheint das milchige Hintergrundbild, heller und in OMEGA getönt, oben fängt sich Licht.",
                        "Um jede Ziffer läuft ein Rand aus OMEGA Fluid, ein weicher Schatten gibt ihnen Tiefe – und sie rollen weiter wie vorher.",
                        "Datum und Akku stehen auf Glas-Chips; ein Tipp öffnet Kalender oder Akku.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "App-Drawer", Icons.Rounded.Menu, Blue,
                    listOf(
                        "Der Drawer ist jetzt OMEGA Glass: heller, in OMEGA getönt, mit OMEGA Fluid im Hintergrund.",
                        "Alle Apps liegen auf einer großen Glasfläche mit Fluid darin und am Rand – die Seiten wischen über das Glas.",
                        "Die vorgeschlagenen Apps bekommen ihr eigenes Glas, die Seitenpunkte eine Glas-Kapsel.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Now Brief", Icons.Rounded.Favorite, Terracotta,
                    listOf(
                        "Viel mehr Glas: OMEGA-Glas statt Blau, tiefere Linse, Licht an der Oberkante, OMEGA Fluid im Glas und am Rand.",
                        "Die Chips für Wecker, Akku und Datum sind OMEGA Glass.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.3",
            name = "Fluid Finder",
            summary = "Galaxy Omega in Blau und Rubin statt Lila, und der Finder und alle Suchleisten ganz aus OMEGA Glass und OMEGA Fluid.",
            sections = listOf(
                ChangeSection(
                    "Galaxy Omega", Icons.Rounded.Star, Blue,
                    listOf(
                        "Galaxy Omega ist jetzt Galaxy-Blau mit OMEGA-Rubin statt Lila: tiefblaues Glas, OMEGA Fluid in Blau, Rubin, Himmelblau und Eisweiß.",
                        "Akzentfarbe und Glas-Tönung „Galaxy Omega“ sind mit umgezogen – wer Galaxy Omega nutzt, bekommt die neuen Farben sofort.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Finder", Icons.Rounded.Search, Pink,
                    listOf(
                        "Der Finder ist jetzt richtiges Glas: heller, mit OMEGA-Tönung und kräftigerem OMEGA Fluid im Hintergrund.",
                        "Alle Ergebnisse liegen auf einer großen Glasfläche, in der OMEGA Fluid treibt und um deren Rand es leuchtet.",
                        "Die Suchleiste im Finder ist OMEGA Glass mit fließendem OMEGA-Fluid-Rand und Fluid im Glas – beim Tippen noch heller.",
                        "Auch „Im Web suchen“ ist jetzt OMEGA Glass.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Suchleisten", Icons.Rounded.Favorite, Violet,
                    listOf("Die Suchleiste auf dem Homescreen, im App-Drawer und in der App-Mediathek: OMEGA Glass, OMEGA Fluid fließt um den Rand und treibt im Glas."),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.2",
            name = "Galaxy Omega",
            summary = "Mehr OMEGA Glass und OMEGA Fluid überall, die neue Farbe Galaxy Omega, ein größeres Ω und Clawd in Galaxy.",
            sections = listOf(
                ChangeSection(
                    "OMEGA Glass", Icons.Rounded.Star, Pink,
                    listOf(
                        "OMEGA Glass ist jetzt von Anfang an an – und kräftiger: deutlich getöntes Glas, OMEGA Fluid auf jedem Rand in voller Farbe, stärkere Fluid-Hintergründe und Leuchten.",
                        "Neue Systemfarbe „Galaxy Omega“: violettes Glas mit Rubinschein, OMEGA Fluid in Rubin, Violett, Tiefblau und Magenta. Unter Design → OMEGA → Farbe von OMEGA Glass – Akzent und Glas wechseln mit.",
                        "„Galaxy Omega“ auch als Akzentfarbe und Glas-Tönung.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Logo", Icons.Rounded.Favorite, Pink,
                    listOf("Das Ω im Logo ist viel größer – fast so breit wie der Rubin."),
                    everyone,
                ),
                ChangeSection(
                    "Clawd", Icons.Rounded.Face, Terracotta,
                    listOf("Neue Farbe „Galaxy“: Indigo, Violett und Magenta fließen ineinander, dazu kleine Sterne auf Clawd."),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.1",
            name = "OMEGA Glass",
            summary = "OMEGA Glass im ganzen System, ein flüssigeres OMEGA UI, ein klares Logo und neue Kleidung für Clawd.",
            sections = listOf(
                ChangeSection(
                    "Flüssiger", Icons.Rounded.Refresh, Green,
                    listOf(
                        "Neuer Flüssig-Modus (an): Farbflüsse laufen mit ruhigerer Bildrate, die Glas-Linse nur noch auf großen Flächen – deutlich weniger Last und Ruckeln.",
                        "Das Logo ist ein stilles Bild statt einer Dauer-Animation.",
                        "Die OMEGA-Uhr zeichnet nur noch ihre Sekundenlinie jede Sekunde neu, nicht die ganze Uhr.",
                        "Icons werden nur so groß geladen, wie sie angezeigt werden.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "OMEGA Glass", Icons.Rounded.Star, Pink,
                    listOf(
                        "Neue Option „OMEGA Glass“ (Design → OMEGA): Rubin-Glas im ganzen System, und überall fließt OMEGA Fluid – Rubin, Rosé und Bernstein statt Claudes Farben. Auf dem Startbildschirm, in Menüs, Einstellungen, im Finder und in Glimmer.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Logo", Icons.Rounded.Favorite, Pink,
                    listOf("Das Logo ist jetzt ein klares Zeichen: der Rubin flach in vier Rottönen mit sauberer Kante – ohne Glühen und Strahlen – und einem großen weißen Ω."),
                    everyone,
                ),
                ChangeSection(
                    "Clawd", Icons.Rounded.Face, Terracotta,
                    listOf(
                        "Neue Kleidung: Mantel, Hoodie, Regenjacke und Latzhose.",
                        "Farbe der Kleidung frei wählbar: Rubin, Marine, Schwarz, Tanne, Camel, Himmel, Flieder, Creme – oder die Originalfarben.",
                        "OMEGA-Abzeichen: ein kleiner Rubin mit dem Ω auf Clawds Brust.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Star, Orange,
                    listOf(
                        "Am Ende der OMEGA-UI-Eggs erscheint das OMEGA-Logo.",
                        "Am Ende jedes ClaudeOS-Eggs erscheint eine eigene Variante des Claude-Logos: Ember (Glut), Blaze (Feuer), Nova (Sternenlicht) und Aurora (Nordlicht).",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "17.0",
            name = "OMEGA UI 17",
            summary = "Aus Hearth UI wird OMEGA UI: ein neues Logo, ein neuer Startbildschirm, neue Uhren, Docks, Menüs und Fenster, OMEGA Cloud und viele neue Designs.",
            sections = listOf(
                ChangeSection(
                    "OMEGA UI", Icons.Rounded.Star, Pink,
                    listOf(
                        "Neues Logo: ein roter Rubin aus Glas mit dem Ω darin, darunter die Systemversion – im Software-Update, in den Einstellungen und in „Über das Telefon“.",
                        "Übergangsanimation beim Wechsel: Hearth UI zerfällt in Licht, das Licht wirbelt zum Rubin zusammen, eine rote Welle läuft über den Bildschirm – und OMEGA UI 17 steigt auf.",
                        "Die Versionsnummer springt auf 17. Neue Willkommens-Tour.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Startbildschirm & Uhren", Icons.Rounded.Home, Terracotta,
                    listOf(
                        "Vier neue Uhren: „OMEGA“ (dünne Ziffern im Rubinlicht, Sekunden als Linie), „Analog-Glas“ (Zifferblatt mit Rubin bei zwölf), „In Worten“ („Viertel nach drei“) und „Geteilt“.",
                        "Neue Uhr-Widgets: OMEGA-Uhr, Rubin-Analoguhr und eine Weltuhr (hier, New York, London, Tokio – mit Tag und Nacht).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Docks", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "„OMEGA-Insel“: dunkles Glas nur um die Icons, schwebend auf rotem Licht mit leuchtendem Rand.",
                        "„Rubin-Glas“: die ganze Leiste in Rubin getönt.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Menüs & Fenster", Icons.Rounded.Menu, Violet,
                    listOf(
                        "Neue Menüs beim langen Drücken: Kopfzeile mit App-Icon, Name und Akzentlinie; Aktionen als Glas-Kacheln in deiner Akzentfarbe.",
                        "Apps im Fenster: „Im Fenster öffnen“ startet jede App als schwebendes Fenster (mit Shizuku).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "OMEGA Cloud", Icons.Rounded.Share, Blue,
                    listOf(
                        "Neue Kategorie „OMEGA Cloud“: Einstellungen, Startbildschirm, Widgets, Clawd, Glimmer und Easter Eggs über dein Google-Konto sichern – verschlüsselt, mit Shizuku sofort.",
                        "Status der Sicherung, Wiederherstellen auf neuen Handys und Sichern als Datei.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Design", Icons.Rounded.Favorite, Orange,
                    listOf(
                        "OMEGA UI ist Teil von Galaxy × Claude: Rubin-Akzente, Rubin-Glas, OMEGA-Uhr und OMEGA-Insel – mit allem von One UI.",
                        "Neue Akzentfarbe und Glas-Tönung „Rubin“.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Easter Egg", Icons.Rounded.Face, Orange,
                    listOf("OMEGA UI 17: „Rubin“ – fünfmal auf die Version tippen und den Rubin in 17 Schnitten schleifen."),
                    everyone,
                ),
                ChangeSection(
                    "Hotfix", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Glimmer Drop lässt sich jetzt ganz ausschalten (Einstellungen → Glimmer → Glimmer Drop): kein Bluetooth-Signal mehr, kein Eintrag im Teilen-Menü und keine Kachel im OMEGA-Menü.",
                        "Das Logo sieht jetzt aus wie der Stein aus Omega Rubin: oben spitz, ein breites Band in der Mitte, rosa-rotes Glas mit einem orange leuchtenden Ring innen, ein deutliches dunkles Ω und Lichtstrahlen dahinter.",
                        "Icons lassen sich jetzt bis 96 dp groß stellen (Design → Icons → Größe) und werden dafür schärfer geladen; das Dock bleibt passend.",
                        "OMEGA UI ist keine eigene Vorlage mehr, sondern Teil von Galaxy × Claude – was dort noch Galaxys Standard war, wird Rubin (selbst Gewähltes bleibt).",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "16.1",
            name = "OMEGA UI",
            summary = "Hearth UI heißt jetzt OMEGA UI. Gleiches System, neuer Name – alles bleibt, wie es ist.",
            sections = listOf(
                ChangeSection(
                    "Ein neuer Name", Icons.Rounded.Star, Violet,
                    listOf(
                        "Hearth UI heißt jetzt OMEGA UI – mit dem Ω als Zeichen. Von Alpha bis Omega: alles in einem System.",
                        "Einmalige Begrüßung nach dem Update; das Ω groß im Software-Update.",
                        "OMEGA-Menü und OMEGA Labs tragen den neuen Namen.",
                        "Deine Einstellungen, Widgets, Apps und Easter Eggs bleiben, Updates kommen wie gewohnt.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Easter Egg", Icons.Rounded.Face, Orange,
                    listOf(
                        "Ein neues Egg rund ums Ω – wo? Ein Tipp: Der Finder kennt den neuen Namen … (jetzt 31 Eggs)",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "16.0",
            name = "Hearth UI 16",
            summary = "Ein großes Update auf neuer Basis: ClaudeOS 4.0 „Aurora“ – mit Glimmer Drop, vielen neuen Menüs und dunklem Glas. Alles in Hearth UI, keine neue App.",
            sections = listOf(
                ChangeSection(
                    "ClaudeOS 4.0 „Aurora“", Icons.Rounded.Star, Violet,
                    listOf(
                        "Neue Basis unter Hearth UI, Glimmer und Clawd: Handys, die sich finden, dunkles Glas und versteckte Menüs gehören jetzt zum Fundament.",
                        "Willkommens-Tour beim ersten Start: alles Neue in Hearth UI 16 auf einen Blick.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Glimmer Drop", Icons.Rounded.Favorite, Blue,
                    listOf(
                        "Halte die Oberkanten zweier Handys mit Glimmer aneinander: Glimmers Insel leuchtet auf, Licht fließt hinüber – beide Handys gehen sofort auf.",
                        "Teile deine Kontaktkarte, Fotos, Videos, Dateien, Links und Text – mit Vorschau von allem, schon bevor das andere Handy annimmt.",
                        "Direkt über Bluetooth und Wi-Fi Direct, ohne Internet – dieselbe Technik wie Quick Share. Für Handys ohne Glimmer: „Über Quick Share senden“.",
                        "Sofort bereit, wenn entsperrt; außerdem im OMEGA-Menü, in den Einstellungen und in jeder App unter Teilen → „Glimmer Drop“.",
                        "Empfangene Fotos und Videos in der Galerie, Dateien unter Downloads, Kontakte mit einem Tipp gespeichert.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Viel mehr Menüs", Icons.Rounded.Menu, Terracotta,
                    listOf(
                        "OMEGA-Menü: ein Glas-Blatt mit allem griffbereit – Einstellungen, Finder, alle Apps, Clawd, Glimmer Drop, Widgets, Hintergrund, Schnelleinstellungen, Mitteilungen, Taschenlampe, Dunkelmodus und Ein/Aus.",
                        "Ein/Aus-Menü aus Glas: Bildschirm aus, Neustart, Ausschalten (mit Shizuku).",
                        "Neuer Bereich „Menüs“ in den Einstellungen; Doppeltippen kann das Hearth- oder das Ein/Aus-Menü öffnen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Versteckte Menüs", Icons.Rounded.Search, Slate,
                    listOf(
                        "Geheimcodes im Finder öffnen versteckte Menüs: Hardware-Test, Versionen, Akku-Status, Diagnose, Clawd-Studio und mehr.",
                        "OMEGA Labs: versteckte Experimente – Bildrate, Fingertipps, Layout-Grenzen, Zeitlupe. Wo? Das bleibt geheim … 🤫",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Dunkelmodus", Icons.Rounded.Star, Violet,
                    listOf(
                        "Dunkles Glas für Dock, Widgets, Menüs, Suchleiste, Ordner, Knöpfe und Glimmer – an, aus, wie Android oder nach Uhrzeit.",
                        "Hintergrundbild und App-Icons bleiben, wie sie sind.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Face, Orange,
                    listOf(
                        "Hearth UI 16: „Glühwürmchen“ – Finger auflegen und sechzehn Glühwürmchen zu dir locken.",
                        "ClaudeOS 4: „Aurora“ – Nordlichter an den Himmel malen.",
                        "Jetzt 30 Easter Eggs – die versteckte Karte zeigt, wo.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.9",
            name = "Glimmer Drop mit Vorschau",
            summary = "Beide Handys reagieren zuverlässig – und du siehst immer, was geteilt wird.",
            sections = listOf(
                ChangeSection(
                    "Zuverlässiger", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Spürt nur eins der beiden Handys die Berührung, ruft es das andere über sein Glimmer-Signal auf – beide gehen auf.",
                        "Der Verbindungsaufbau probiert es von beiden Seiten und bis zu dreimal neu, statt stehen zu bleiben.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Vorschau", Icons.Rounded.Search, Blue,
                    listOf(
                        "Alles Geteilte mit Vorschau: Fotos und Videos als kleine Bilder, Links als Karte mit der Website, Text als Ausschnitt.",
                        "Schon vor „Annehmen“ siehst du die Fotos und Videos, die kommen.",
                        "Empfangenes als Bilder-Raster – tippen zum Öffnen.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Quick Share", Icons.Rounded.Favorite, Violet,
                    listOf(
                        "Glimmer Drop überträgt mit derselben Google-Technik wie Quick Share (Nearby Connections) – direkt, schnell, ohne Internet.",
                        "Neu: „Über Quick Share senden“ – für Handys ohne Glimmer, mit dem schon Ausgewählten.",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.8",
            name = "Glimmer Drop verbindet",
            summary = "Die Handys erkennen sich – jetzt verbinden sie sich auch.",
            sections = listOf(
                ChangeSection(
                    "Behoben", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Glimmer Drop: „konnte nicht suchen (8034)“ – Googles Nearby braucht zum Suchen des anderen Handys den Standort, auch auf neuem Android. Glimmer Drop fragt jetzt danach (ungefähr reicht).",
                        "Wurde eine Erlaubnis zweimal abgelehnt, führt „App-Einstellungen“ direkt dorthin; zurück in Glimmer Drop geht es sofort weiter.",
                        "Verständliche Meldungen statt Fehlercodes.",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.7",
            name = "Glimmer Drop repariert",
            summary = "Zwei Handys aneinanderhalten startet Glimmer Drop jetzt wirklich.",
            sections = listOf(
                ChangeSection(
                    "Behoben", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Glimmer Drop: Android 12 und neuer gab Glimmer die Bluetooth-Signale des anderen Handys nicht weiter – deshalb passierte beim Aneinanderhalten nichts. Glimmer hört jetzt (ohne Standort-Erlaubnis).",
                        "Die Handys werden schon erkannt, wenn sie sich fast berühren.",
                        "„Geräte in der Nähe“ wird einmal von selbst gefragt.",
                        "Glimmer Drop zeigt jetzt, ob Glimmer sendet und hört und wie stark das andere Handy ankommt – und was fehlt, falls etwas nicht geht.",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.6",
            name = "Glimmer Drop sofort",
            summary = "Handys aneinanderhalten – Glimmer Drop geht sofort auf. Jetzt auch in der Glimmer-App.",
            sections = listOf(
                ChangeSection(
                    "Glimmer Drop", Icons.Rounded.Favorite, Blue,
                    listOf(
                        "Sofort: Glimmer spürt ein Handy, das an deins gehalten wird, viel schneller und öffnet Glimmer Drop gleich mit der Verbindung zum anderen Handy – ohne nochmal suchen.",
                        "Auch nur mit der Glimmer-App: Glimmer Drop gibt es jetzt auf jedem Handy mit Glimmer, auch ohne Hearth UI – beide verstehen sich.",
                        "In den Glimmer-Einstellungen: Glimmer Drop öffnen, „Geräte in der Nähe“ erlauben und „Sofort bereit, wenn entsperrt“.",
                        "Glimmer hört wieder hin, sobald Bluetooth eingeschaltet wird.",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.5",
            name = "Glimmer Drop",
            summary = "Zwei Handys aneinanderhalten und teilen – wie NameDrop und AirDrop.",
            sections = listOf(
                ChangeSection(
                    "Glimmer Drop", Icons.Rounded.Favorite, Blue,
                    listOf(
                        "Halte die Oberkanten zweier Handys mit Hearth UI aneinander: Glimmers Insel leuchtet auf, wächst zum anderen Handy hin, und Licht fließt über den Bildschirm.",
                        "Teilen lässt sich alles Mögliche: deine Kontaktkarte (Name, Telefon, E-Mail, Instagram, Website), Fotos, Videos, Dateien, Links und Text.",
                        "Das andere Handy sieht, was kommt, und tippt auf „Annehmen“ oder „Ablehnen“ – vorher wird nichts gespeichert.",
                        "Schnell über eine direkte Verbindung (Bluetooth und Wi-Fi Direct), ganz ohne Internet.",
                        "Empfangene Fotos und Videos landen in der Galerie, Dateien unter Downloads (Ordner „Glimmer Drop“); Kontakte mit einem Tipp in die Kontakte, Links öffnen oder kopieren.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Überall erreichbar", Icons.Rounded.Menu, Violet,
                    listOf(
                        "Bereit, wenn entsperrt: Glimmer merkt im Hintergrund, wenn ein Handy an deins gehalten wird, und öffnet Glimmer Drop von selbst.",
                        "Aus jeder App: Teilen → „Glimmer Drop“.",
                        "Im OMEGA-Menü und unter Einstellungen → Glimmer → Glimmer Drop.",
                        "Die Insel zeigt mit, was passiert: erkannt, senden, empfangen.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Star, Orange,
                    listOf(
                        "Hearth UI 15.5: „Funken“ – zieh zwei Glimmer-Inseln zusammen und lass es fünfmal funken.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.1",
            name = "Dunkelmodus",
            summary = "Der Nachtmodus wird zum Dunkelmodus: dunkles Glas überall.",
            sections = listOf(
                ChangeSection(
                    "Dunkelmodus", Icons.Rounded.Star, Violet,
                    listOf(
                        "Der Nachtmodus heißt jetzt Dunkelmodus (Design → Dunkelmodus): An, Aus, wie Android oder nach Uhrzeit.",
                        "Im Dunkelmodus wird das Glas dunkel wie ein dunkles Widget – Dock, Widgets, Menüs (auch die drei Punkte im Drawer), Suchleiste, Ordner, Knöpfe und Glimmer.",
                        "Hintergrundbild und App-Icons bleiben, wie sie sind; kein warmer Schleier mehr über dem Bildschirm.",
                        "„Android mitschalten“ schaltet mit Shizuku Androids Dunkelmodus mit.",
                    ),
                    withGlimmer,
                ),
            ),
        ),
        ChangeRelease(
            version = "15.0",
            name = "Menüs & Geheimnisse",
            summary = "Viel mehr Menüs überall – und versteckte Menüs, die man erst finden muss.",
            sections = listOf(
                ChangeSection(
                    "OMEGA-Menü", Icons.Rounded.Menu, Blue,
                    listOf(
                        "Neues OMEGA-Menü: ein Glas-Blatt von unten mit allem griffbereit – Einstellungen, Finder, alle Apps, Clawd, Widgets, Hintergrund, Schnelleinstellungen, Mitteilungen, Taschenlampe, Nachtmodus, Apps auswählen und Ein/Aus.",
                        "Öffnet sich über „OMEGA-Menü“ in der Bearbeiten-Leiste (lange auf den Startbildschirm drücken), im Startbildschirm-Menü oder per Doppeltippen (Startbildschirm → Doppeltippen → „OMEGA-Menü“).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Ein/Aus-Menü", Icons.Rounded.Refresh, Terracotta,
                    listOf(
                        "Ein eigenes Ein/Aus-Menü aus Glas: Bildschirm aus, Neustart und Ausschalten (mit Shizuku; Neustart und Ausschalten fragen mit einem zweiten Tipp nach).",
                        "Erreichbar im OMEGA-Menü, in den Einstellungen unter „Menüs“ oder per Doppeltippen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Versteckte Menüs", Icons.Rounded.Search, Violet,
                    listOf(
                        "Wie die Servicecodes eines Galaxy: Der Finder kennt jetzt Geheimcodes, die mit *# anfangen und versteckte Menüs öffnen – Hardware-Test, Versionen, Akku-Status, Diagnose, Clawd-Studio und mehr. Welche das sind? Findest du selbst heraus … 🤫",
                        "Hardware-Test: Display-Farben, Touch-Test zum Malen, Vibrationsmuster und alle Sensoren.",
                        "Akku-Status und Diagnose zeigen live Strom, Temperatur, Spannung, Arbeitsspeicher, Speicher und Laufzeit.",
                        "Clawd-Studio: Clawd in jeder Stimmung ansehen – und auf den Startbildschirm schicken.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "OMEGA Labs", Icons.Rounded.Build, Green,
                    listOf(
                        "Wie Androids Entwickleroptionen: OMEGA Labs versteckt sich hinter einigen Tipps an einer bekannten Stelle in „Über das Telefon“.",
                        "Darin: Bildrate-Anzeige (FPS) auf dem Startbildschirm, Fingertipps und Zeigerposition zeigen, Layout-Grenzen, Animationstempo bis zur Zeitlupe, Willkommens-Tour wiederholen und Hearth neu starten.",
                        "Einmal freigeschaltet, steht OMEGA Labs auch in den Einstellungen unter „Menüs“ und in „Über das Telefon“.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Easter Eggs", Icons.Rounded.Star, Orange,
                    listOf(
                        "Hearth UI 15: „Tresor“ – das Update der versteckten Menüs hat einen versteckten Tresor. Die Kombination steckt in der Version …",
                        "Zwei neue Eggs zum Finden – jetzt sind es 30.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "14.9",
            name = "Glimmer bei Nacht",
            summary = "Der Nachtmodus erreicht Glimmer – und ein geheimes Menü verrät alle Easter Eggs.",
            sections = listOf(
                ChangeSection(
                    "Nachtmodus", Icons.Rounded.Star, Violet,
                    listOf(
                        "Glimmer wird im Nachtmodus dunkler: gedämpfte Insel, ruhiges Glas, dezente Fluid-Ränder.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Geheim", Icons.Rounded.Face, Orange,
                    listOf(
                        "Ein verstecktes Menü zeigt, wo sich alle Easter Eggs befinden und welche du schon gefunden hast. Wie man es öffnet? Das bleibt geheim … 🤫",
                    ),
                    everyone,
                ),
            ),
        ),
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
        // OMEGA UI: the Ω big and faint behind the version.
        if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) {
            OmegaMark((height.value * 0.95f).toInt(), fade = 0.16f)
        }
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

/**
 * "Update-Verlauf": the versions before, each opening to its changelog. ZENITH 19: held, a
 * version opens its own easter egg again ([onEgg]) – the egg museum of Hearth, OMEGA UI and ZENITH.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun UpdateHistoryCard(releases: List<ChangeRelease>, app: ChangeApp, accent: Color, card: Color, onEgg: ((String) -> Unit)? = null) {
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
                    .then(
                        if (onEgg != null) {
                            Modifier.combinedClickable(
                                onClick = { open = if (isOpen) null else release.version },
                                onLongClick = { onEgg(release.version) },
                            )
                        } else {
                            Modifier.clickable { open = if (isOpen) null else release.version }
                        },
                    )
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
        if (onEgg != null) {
            Text(
                "Egg-Museum: lange auf eine Version drücken – ihr Easter Egg kommt wieder.",
                color = accent.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
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
            .background(Brush.verticalGradient(listOf(Color(0xFF071A22), Color(0xFF0C2A2E), Color(0xFF040807))))
            // Nothing underneath gets a touch while the tour is open.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        FluidBackdrop(ZenithFluidColors.take(3), strength = 0.6f)
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
                            title = "Ein neuer Morgen",
                            text = "Neue Farbe, neue Uhr, neues Glas – über deinem Startbildschirm geht die Sonne auf.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Home, ZenithSun, "Sonnenbogen", "Die neue Uhr: Die Sonne steht auf ihrem Bogen, wo sie gerade am Himmel steht – nachts der Mond. Mittags: „Sonne im Zenit“."),
                                TourPoint(Icons.Rounded.Star, ZenithGreen, "Zenith-Grün", "Die neue Systemfarbe: Grün, Sonne und Himmel fließen durch Glas, Menüs, Widgets und Glimmer."),
                                TourPoint(Icons.Rounded.Refresh, ZenithSky, "Sonnenglas", "Glas mit einem Hauch Grün – oder klar wie Apples Liquid Glass, mit Sonnenglanz."),
                            ),
                        ) {
                            TourSwitch(
                                title = "ZENITH-Design verwenden",
                                text = "Galaxy × Claude mit Zenith-Grün, Sonnenglas, der Sonnenbogen-Uhr und der OMEGA-Insel als Dock – jederzeit änderbar.",
                                checked = newLook,
                            ) { newLook = it }
                        }
                        2 -> TourPage(
                            title = "Halt es gedrückt",
                            text = "Wie bei ColorOS: Das Logo lebt.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Favorite, ZenithGreen, "Das ZENITH-Logo", "In den Einstellungen, unter Über das Telefon und im Software-Update: gedrückt halten – die Sonne steigt, die Strahlen drehen sich, und wenn sie voll ist, klopft es."),
                                TourPoint(Icons.Rounded.Menu, Color(0xFFD4AF37), "Das Geheimnis bleibt", "Tippst du das Logo so oft an, wie ZENITH Nummer hat, kommst du zu den Clawd Illuminati."),
                                TourPoint(Icons.Rounded.Add, ZenithSun, "Ein neuer Übergang", "Das Ω verblasst, die Sonne klettert in den Zenit, und dort oben entsteht ZENITH."),
                            ),
                        )
                        else -> TourPage(
                            title = "Was bleibt",
                            text = "OMEGA UI und Hearth sind nicht weg – sie leben in ZENITH weiter.",
                            points = listOf(
                                TourPoint(Icons.Rounded.Face, Color(0xFFE5243F), "Das Egg-Museum", "Software-Update → Update-Verlauf: lange auf eine alte Version drücken, und ihr Easter Egg kommt wieder – Rubin, Laterne, Schmiede, Lupe, Glühwürmchen …"),
                                TourPoint(Icons.Rounded.Share, ZenithSun, "Ein neues Easter Egg", "Zur 19: fünfmal auf die Version tippen und die Sonne bis in den Zenit schieben – 19 Clawds wachen auf."),
                                TourPoint(Icons.Rounded.Star, Color(0xFF8E6BFF), "Alles von OMEGA", "OMEGA Cloud, OMEGA-Insel, Rubin als Farbe, die Illuminati und Claude Mythos – alles noch da."),
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
                    .background(Brush.horizontalGradient(listOf(ZenithGreenDeep, ZenithGreen, ZenithSky)))
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
        // ZENITH 19: its mark – hold it, it charges up.
        ZenithLogo(size = 180.dp, caption = "ZENITH 19")
        Spacer(Modifier.height(8.dp))
        Text(
            "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
            style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Willkommen bei ZENITH – die Sonne im höchsten Punkt. Ein neues Logo, eine neue Farbe, eine neue Uhr: alles frisch. Halt das Logo gedrückt – und dann wisch weiter.",
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
        // OMEGA UI 18.5: a switch of liquid glass (the row is what is tapped).
        GlassSwitch(checked = checked, onCheckedChange = null, onColor = Color(0xFF3E91FF))
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
