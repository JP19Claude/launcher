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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
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
    fun omegaSeen(context: Context): Boolean = prefs(context).getBoolean("zenith19LogoSeen", false)

    fun markOmegaSeen(context: Context) {
        prefs(context).edit().putBoolean("zenith19LogoSeen", true).apply()
    }

    /** ZENITH 20: the jubilee's ceremony has been shown. */
    fun jubileeSeen(context: Context): Boolean = prefs(context).getBoolean("zenith20Jubilee", false)

    fun markJubileeSeen(context: Context) {
        prefs(context).edit().putBoolean("zenith20Jubilee", true).apply()
    }

    /** ZENITH 21: asked once whether the jubilee's theme stays. */
    fun themeAsked(context: Context): Boolean = prefs(context).getBoolean("zenith21ThemeAsked", false)

    fun markThemeAsked(context: Context) {
        prefs(context).edit().putBoolean("zenith21ThemeAsked", true).apply()
    }

    /** ZENITH Smart: the welcome that helps you move over from Smart Launcher has been shown. */
    fun smartWelcomeSeen(context: Context): Boolean = prefs(context).getBoolean("smartWelcome", false)

    fun markSmartWelcomeSeen(context: Context) {
        prefs(context).edit().putBoolean("smartWelcome", true).apply()
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
            version = "21.5",
            name = "Comet",
            summary = "ZENITH 21.5: „Be Comet“ – im Clawd Illuminati, neben „Be Smarter“, baut ein zweiter Modus den Launcher um wie bei CometOS: klares Liquid Glass, eine große Uhr aus Glas, das Dock als gläserne Kapsel, die App-Bibliothek, das Kontrollzentrum von oben rechts – und ZENITH und ClaudeOS im Kern der Einstellungen.",
            sections = listOf(
                ChangeSection(
                    "Be Comet – der Launcher wie CometOS", Icons.Rounded.Star, Blue,
                    listOf(
                        "Der Schalter „Be Comet“ steht im Clawd Illuminati gleich unter „Be Smarter“ (und in den Smart-Einstellungen unter ZENITH → Aussehen). Beide Looks sind für denselben Bildschirm gedacht, darum schaltet der eine den anderen aus. Aus: alles wie vorher, nichts geht verloren.",
                        "Liquid Glass oder solide Flächen: klares Glas, das den Hintergrund bricht und Licht an seinem Rand sammelt – mit den Reglern „Stärke“ und „Deckkraft der Flächen“ – oder ruhige, flache Flächen ohne Linse, die ältere Telefone schonen.",
                        "Die Flüssig-Uhr: große weiche Ziffern aus Glas auf dem Hintergrund, das Datum klein darüber mit dem Wetter daneben, der nächste Wecker darunter. Wahlweise in Serif oder als Analog-Uhr, mit eigener Schrift und Deckkraft.",
                        "Das Dock als schwebende Kapsel aus Glas, App-Namen unter den Icons (auszuschalten), Icon-Form und -Darstellung frei, die App-Bibliothek als Seite nach der letzten, die Suche mit Claude statt mit Comet AI.",
                        "Das Kontrollzentrum zieht man von oben rechts herunter, die Mitteilungen von oben links; Akzentfarbe und Abdunkelung des Hintergrunds sind einstellbar.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "ZENITH im Kern der Comet-Einstellungen", Icons.Rounded.Settings, Pink,
                    listOf(
                        "Mit „Be Comet“ werden die Launcher-Einstellungen zu den Comet-Einstellungen: dunkler Grund wie bei Comet, Karten aus klarem Glas, geordnet nach Hintergrund & Stil, Liquid Glass, Startbildschirm, Dock & App-Bibliothek.",
                        "Vorneweg, unter der Systemkarte mit dem „Aim Higher.“: ClaudeOS (Claude & KI, Clawd, Glimmer, Software-Update, Über das Telefon) und ZENITH (Zentrale, Design, Illuminati, Datenschutz, System, das Theme). Was keine eigene Seite hat – Claudes Schlüssel, der Sperrbildschirm von Glimmer, der Tresor –, öffnet sich von dort in den Einstellungen von ZENITH selbst.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Super-Wallpaper – Hintergründe, die sich bewegen", Icons.Rounded.Star, Blue,
                    listOf(
                        "Neu unter Einstellungen → Super-Wallpaper (auch aus den Smart- und Comet-Einstellungen zu öffnen): sechs bewegte Hintergründe, wie manche Telefone sie als „Super-Wallpaper“ haben – Saturn mit seinen Ringen, Jupiter mit dem roten Fleck, die Erde mit Wolken und Dunst, Mars mit Polkappen, der Mond voller Krater und die Geometrie, ein Ikosaeder aus Licht.",
                        "Die Seite zeigt die Szene live, lässt sie wählen und macht sie mit „Als Hintergrund setzen“ zum Hintergrund des Telefons: ein echtes Live-Wallpaper, das hinter ZENITH läuft (auch hinter anderen Launchern und, wo Android es zulässt, auf dem Sperrbildschirm). Auch in Androids eigener Hintergrund-Auswahl heißt es „ZENITH Super-Wallpaper“.",
                        "Es bewegt sich: Die Planeten drehen sich, mit der Neigung des Telefons wandert die Szene, mit den Seiten des Startbildschirms rückt sie zur Seite, und beim Entsperren kommt sie näher. Neigung, Entsperr-Flug, Sterne und Geschwindigkeit sind einstellbar.",
                        "Das Glas in ZENITH bricht und weichzeichnet ein Standbild der Szene, passend zu den Farben des Planeten; es bewegt sich nur der Hintergrund selbst. Ehrlich dazu: Alles ist selbst gezeichnet – aus Rauschen und Farbverläufen beim ersten Start, keine Bilddateien und nicht die Super-Wallpaper eines Herstellers –, und ich konnte es auf keinem Handy ausprobieren.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: neue Easter Eggs für ZENITH 21, 21.5 und ClaudeOS 8", Icons.Rounded.Star, Orange,
                    listOf(
                        "ZENITH 21 „Horizont“: Nacht über drei Bergrücken, einundzwanzig blasse Lichter hängen im Dunkeln. Zieh den Horizont mit dem Finger zur Seite – die nahen Berge wandern weiter als die fernen –, tipp die Lichter an, und mit jedem kommt der Morgen näher. Beim einundzwanzigsten geht hinter den Bergen die Sonne auf.",
                        "ZENITH 21.5 „Comet“: Blasen aus Liquid Glass steigen aus dem Dunkel auf, klar, mit hellem Rand und einem Glanzpunkt. Tipp sie an, und sie platzen; ins Leere getippt, bläst du eine neue. Einundzwanzig Blasen – dann kommt ZENITHs Zeichen.",
                        "ClaudeOS 8 „Horizon“: Trag das Claude-Zeichen mit dem Finger von links nach rechts über den Horizont – der Himmel geht durch Dämmerung, Tag und Abend, am Ende kommen die Sterne wieder. Zum Schluss erscheint das Zeichen von ClaudeOS 8.",
                        "Bisher spielten diese Versionen noch die Eggs von ZENITH 20 und ClaudeOS 7. Jetzt haben sie ihre eigenen – in der Ruhmeshalle und der ClaudeOS-Ruhmeshalle (Version halten), in der Zentrale (Jubiläums-Galerie) und im Abspann stehen sie auch.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Wallpaper-Galerie – echte NASA-Fotos, sehr viele vom Mond", Icons.Rounded.Star, Violet,
                    listOf(
                        "Neu unter Einstellungen → Wallpaper-Galerie (auch aus den Smart- und Comet-Einstellungen zu öffnen): Hintergründe zum Auswählen und Herunterladen. Oben echte Fotos der NASA, direkt aus der Bilderbibliothek der NASA (images.nasa.gov) geladen – in den Gruppen Mond, Erde, Planeten und Weltraum – und dazu 26 Bilder, die ZENITH selbst zeichnet (Weltraum, Landschaft, Abstraktes, ZENITH). Mond und Planeten gibt es nur noch als echte Fotos: Gezeichnet wirkten sie neben ihnen künstlich.",
                        "Viel mehr Fotos: Jedes Thema lädt gleich bis zu 200 Bilder, „Mehr zeigen“ lädt weitere. Der Mond hat 28 Themen – Alle Mond-Fotos, Flagge auf dem Mond, Vollmond, Astronauten auf dem Mond, Mondlandung, Sichel, Mondphasen, Mondfinsternis, Blutmond, Supermond, Mond bei Nacht, Mond und Wolken, Mondaufgang, Mond am Horizont, Erdaufgang, Mond von der ISS, Apollo (auch 8, 11 und 17), Mondfahrzeug, Krater, LRO-Aufnahmen, Südpol, Rückseite, Artemis, Mondlandschaft, Mond-Mosaik und Erde und Mond. Dazu Erde (auch US-Flagge), Planeten (Saturn bis Sonne) und Weltraum (Hubble, James Webb, Galaxien, Nebel …).",
                        "Die Großansicht zeigt genau das, was das Hintergrundbild wird: mit dem Finger verschieben, mit zwei Fingern zoomen – oder „Ganzes Foto“ wählen, dann steht das ganze Bild auf einem unscharfen Rand aus sich selbst (so ist zum Beispiel die ganze Flagge zu sehen, auch wenn das Foto quer ist).",
                        "Von dort: „Als Hintergrund“ (Startbildschirm, Sperrbildschirm oder beide), „Als Live-Wallpaper“ (siehe unten), „Herunterladen“ (als Bild in Bilder/ZENITH) und „Teilen“. Das Foto in voller Größe wird erst dafür geladen.",
                        "Ehrlich dazu: Die Fotos brauchen Internet. NASA-Bilder sind in der Regel gemeinfrei; bei einzelnen können andere Rechte gelten – Titel und Nummer stehen in der Großansicht. Ich konnte die NASA-Bibliothek von meiner Arbeitsumgebung aus nicht erreichen und es deshalb nicht ausprobieren; wenn etwas nicht lädt, hilft eine Meldung mit dem Thema.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Super-Wallpaper mit dem echten Mond und echten Planeten", Icons.Rounded.Star, Blue,
                    listOf(
                        "Die gezeichneten Planeten und der gezeichnete Mond sind weg – sie sahen künstlich aus. Statt ihrer: der echte Mond und die Erde als Aufnahmen der NASA (Apollo), und Mars, Venus, Jupiter, Saturn, Neptun, Pluto sowie die Monde Io, Europa und Ganymed, gedreht auf den echten Karten der NASA. Alle elf stehen in der Szenenwahl des Super-Wallpapers.",
                        "Die Bilder sind im App-Paket (frei, ohne Copyright, aus „NASA 3D Resources“; Quelle in der Datei CREDITS.txt), also ohne Internet und ohne Wartezeit da. Der Mond ist jetzt die Voreinstellung.",
                        "Und jede dieser Szenen gibt es auch als eigene kleine App, als einzelne APK: „ZENITH-Hintergrund-Mond.apk“, „-Erde“, „-Mars“, „-Venus“, „-Jupiter“, „-Saturn“, „-Neptun“, „-Pluto“, „-Io“, „-Europa“, „-Ganymed“ und „-Geometrie“ – jede ein Live-Wallpaper mit eigenem Symbol und eigenen Einstellungen (Neigung, Zoom, Sterne, Geschwindigkeit), nur ein bis zwei Megabyte groß, einzeln zu installieren, zu behalten und weiterzugeben. Download: Einstellungen → Super-Wallpaper → „Als einzelne App herunterladen“ oder die Seite „ZENITH Hintergründe“ bei den Downloads.",
                        "Dazu „Eigenes Foto“: jedes Foto aus der Wallpaper-Galerie als Live-Wallpaper („Als Live-Wallpaper“, auch mit deinem Zuschnitt und Zoom). Alle bewegen sich mit der Neigung des Telefons und rücken mit den Seiten; beim Entsperren kommen sie näher (siehe unten).",
                        "Ehrlich dazu: Mond und Erde sind Fotos – sie drehen sich nicht; die Karten der anderen Himmelskörper sind teils niedrig aufgelöst (Jupiter, Saturn, Neptun, Pluto), also etwas weich. Uranus, Merkur und die Sonne fehlen, weil die NASA-Sammlung dafür keine Karte hat. Auf einem Handy habe ich es nicht ausprobiert.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Absturzschutz – ZENITH fällt nicht mehr still um", Icons.Rounded.CheckCircle, Terracotta,
                    listOf(
                        "Stürzt ZENITH ab, schreibt es jetzt auf, was kaputt gegangen ist (Fehler, Stelle, Telefon, Version) und zeigt es auf einem eigenen Bildschirm, der in einem getrennten Prozess läuft und nur aus einfachen Ansichten besteht – damit er selbst nicht mit abstürzen kann. Der Bericht lässt sich kopieren oder teilen.",
                        "Drei Abstürze innerhalb von zwei Minuten starten ZENITH im abgesicherten Modus: Be Smarter, Be Comet, der ZENITH-Hintergrund, Polarlicht, Omega, Hyperglas, Farbwelt und die Zeremonien nach einem Update bleiben aus – deine Einstellungen werden dafür nicht verändert. Ist das Super-Wallpaper der Hintergrund des Telefons, wird es abgenommen und Android zeigt wieder seinen normalen.",
                        "Auf dem Bildschirm nach dem Absturz: „Abgesichert starten“, „Normal starten“ und – zweimal tippen – „Einstellungen von ZENITH zurücksetzen“ als letzter Ausweg (Apps, Widgets und Daten bleiben). Beim nächsten Start steht der Bericht noch einmal im Launcher, dort lässt sich der abgesicherte Modus auch wieder beenden.",
                        "Das Super-Wallpaper fängt Fehler jetzt selbst ab: Geht beim Zeichnen etwas schief, bleibt es schwarz stehen, und ZENITH läuft weiter.",
                        "Ehrlich dazu: Gebaut habe ich das, ohne die Ursache zu kennen – der erste Bericht, der damit aufkam, hat sie dann gezeigt (siehe „Absturz beim Start behoben“). Der Schutz bleibt für alles, was noch kommen kann.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Absturz beim Start behoben", Icons.Rounded.CheckCircle, Orange,
                    listOf(
                        "Seit dem Super-Wallpaper-Hotfix (21.5, Version 191) ließ sich ZENITH nicht mehr öffnen: Android lehnte beim Laden eine Klasse des Launchers ab („VerifyError: expected 3 argument registers“). Grund: Die Einstellungen von ZENITH sind eine Klasse mit inzwischen 249 Feldern, und Androids Code-Format erlaubt für das Kopieren einer solchen Klasse höchstens 255 Register – mit den fünf neuen Feldern des Super-Wallpapers waren es 259.",
                        "Jetzt liegen die Wahlen von „Be Comet“ (13 Felder) und die des Super-Wallpapers (5 Felder) je in einer eigenen kleinen Klasse; die Einstellungen haben 233 Felder, 243 Register. Für dich ändert sich nichts: Alle Einstellungen und ihre gespeicherten Werte bleiben, wie sie waren.",
                        "Damit das nicht noch einmal passiert, prüft der Bau ab jetzt bei jedem Lauf, wie groß diese Klassen sind, und bricht ab, bevor eine zu groß wird.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Der Mond fliegt ein wie bei Xiaomi HyperOS – hoch aufgelöst", Icons.Rounded.Star, Blue,
                    listOf(
                        "Rückmeldung zum Mond: Die Bilder waren nicht hoch genug aufgelöst, und der Ablauf sollte anders sein. Jetzt: Auf dem Sperrbildschirm fliegt der Mond von oben links in die Mitte des Bildschirms (gut zwei Sekunden) und dreht sich dort langsam. Beim Entsperren fliegt er etwas nach oben, dreht sich weiter, und dann fährt die Kamera heran – näher als vorher: Der Mond füllt den ganzen Bildschirm, einzelne Krater, Kraterketten und Strahlen sind zu sehen (der ganze Ablauf nach dem Entsperren dauert knapp fünf Sekunden). Beim Sperren geht es zurück.",
                        "Hoch aufgelöst: Die Karte des Mondes hat jetzt 8192 × 4096 Pixel (vorher 2048 × 1024) und ist die echte, schattenfreie Albedo der Raumsonde LRO. Dazu kommen die echten Geländenormalen des Laser-Höhenmessers LOLA (4096 × 2048): Bei flachem Sonnenlicht werfen Kraterränder, Kuppen und Gebirge Schatten, und der Mond sieht beim Drehen von jeder Seite plastisch aus. Beim Heranzoomen wird das Bild leicht nachgeschärft und mit feinem Staubrauschen versehen.",
                        "Der Mond ist dafür eine echte, drehende Kugel (OpenGL ES 3, eigener Zeichen-Thread, im Takt des Bildschirms – der Launcher wird dabei nicht aufgehalten). Mars, Venus, Jupiter, Saturn (mit Ringen), Neptun, Pluto, Io, Europa und Ganymed fliegen genauso ein und drehen sich auf ihren Karten; die sind aber nur 1024 Pixel breit, deshalb kommen sie weniger nah heran. Wo ein Telefon OpenGL nicht kann oder die Grafikeinheit nichts zeichnet, zeichnet der Prozessor wie bisher (mit dem Foto des Mondes, ohne Drehen und Krater im Streiflicht).",
                        "Auf dem Startbildschirm dreht sich der Körper mit jedem Wisch ein Stück weiter, folgt der Neigung des Telefons und schrumpft mit, wenn der Launcher den Hintergrund herauszoomt (App-Bibliothek, Ordner). Ist das Hintergrundbild nur für den Startbildschirm gesetzt, fliegt der Mond nach dem Einschalten des Bildschirms von der Mitte nach oben und zoomt heran. Der Schalter „Entsperr-Flug und Seitenwechsel“ schaltet Flug und Seitenbewegung ab.",
                        "Die Vorschau in den Einstellungen und in den einzelnen Hintergrund-Apps (jetzt Version 1.3) zeigt beides in einer Schleife – den Einflug auf dem Sperrbildschirm und den Flug nach dem Entsperren –, auch ohne dass du sperren musst.",
                        "Ehrlich dazu: Das Super-Wallpaper von Xiaomi habe ich nicht gesehen. Ich habe den Ablauf nach deiner Beschreibung gebaut; Bilder und Code von Xiaomi gehören Xiaomi. Die Dauer und die Wege sind meine Schätzung. Die Erde bleibt ein Foto (sie fliegt ein, dreht sich aber nicht). Auf einem Handy habe ich nichts ausprobiert: Der Grafik-Teil lief bisher nur in einer Software-Simulation am Computer. Bleibt der Hintergrund schwarz oder sieht er falsch aus, hilft ein Bildschirmfoto oder -video.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Neuer Hintergrund „Iwo Jima“ – mit der US-Flagge im Mittelpunkt", Icons.Rounded.Star, Blue,
                    listOf(
                        "Rückmeldung: Zu dem Foto mit dem Iwo-Jima-Denkmal und dem Vollmond soll es auch so einen Hintergrund geben, und man soll die US-Flagge mehr sehen. Neu: die Szene „Iwo Jima“ im Super-Wallpaper (Einstellungen → Super-Wallpaper → Szene) und als eigene App „ZENITH-Hintergrund-Iwo-Jima.apk“. Das Foto ist fest im Paket, es braucht kein Internet.",
                        "Der Ablauf wie beim Mond: Auf dem Sperrbildschirm beginnt die Kamera dicht an der Flagge oben links und fliegt zurück auf einen Ausschnitt mit Flagge, Mast, Vollmond und den Marines. Beim Entsperren fährt sie etwas nach oben und dann nah an Flagge und Mond heran – die Flagge steht dabei groß am linken Rand. Dazu liegt ein sanftes Licht auf der Flagge, damit Rot, Weiß und Blau im dunklen Foto zu sehen sind.",
                        "Auch „Eigenes Foto“ (ein Foto aus der Wallpaper-Galerie als Live-Wallpaper) fliegt jetzt so ein: von der Ecke oben links zum Bild, beim Entsperren etwas näher heran. Die Hintergrund-Apps sind Version 1.4.",
                        "Ehrlich dazu: Das Foto habe ich nur so, wie du es mir gezeigt hast – der linke Teil der Flagge ist darin abgeschnitten. Mehr von der Flagge, als im Foto ist, kann ich nicht zeigen; ich zeige das Vorhandene größer. Wenn du das ganze Foto mit der ganzen Flagge hast, schick es mir, dann tausche ich es aus. Fotograf und Bildnummer kenne ich nicht; ich gehe davon aus, dass es aus der NASA-Bildbibliothek stammt (wie die Fotos der Wallpaper-Galerie) – sicher bin ich nicht. Auf einem Handy habe ich es nicht ausprobiert.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Größe des Planeten einstellbar", Icons.Rounded.Star, Blue,
                    listOf(
                        "Rückmeldung: Man soll den gewählten Planeten oder Mond in der Größe verstellen können. Neu: der Regler „Größe“ unter Einstellungen → Super-Wallpaper (50 % bis 160 %, Voreinstellung 100 %) – die Vorschau oben folgt sofort. Er gilt für den Mond, die Erde, alle Planeten und Monde und die Geometrie, auf dem Sperrbildschirm wie auf dem Startbildschirm und auch beim Heranzoomen; bei „Iwo Jima“ und „Eigenes Foto“ gibt es ihn nicht, weil dort ein Foto das Bild ist.",
                        "Auch jede einzelne Hintergrund-App (ZENITH-Hintergrund-Mond usw.) hat jetzt den Regler „Größe“ in ihrer Oberfläche; die Hintergrund-Apps sind Version 1.5.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Ehrlich dazu", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Das ist ein Nachbau mit eigenem Code, kein Comet Launcher: Ich habe die hochgeladene Datei (Comet Launcher 3.28.1) nur gelesen – ihre Farben, Namen und die Reihenfolge der Einstellungen – und nichts davon kopiert oder verändert.",
                        "Nicht dabei: Comet AI (hier antwortet Claude), Comets eigene Widgets (Weltuhr, Notiz) und sein eigener Sperrbildschirm (den macht weiter Glimmer). Und: Ich konnte es nicht auf einem Handy ausprobieren – wo etwas anders aussieht als bei Comet, hilft ein Bildschirmfoto.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "21.0",
            name = "Horizont",
            summary = "ZENITH 21: ein Smart-Modus, der Leute vom Smart Launcher überzeugen soll – eigene Kategorien, dreizehn Icon-Formen, Farbwähler, Smart-Widgets und ein Wechsel-Assistent – und ein neues Theme „Horizont“. Das Jubiläums-Theme von ZENITH 20 behältst du mit einem Schalter. Darunter ClaudeOS 8.0 „Horizon“.",
            sections = listOf(
                ChangeSection(
                    "Neues Theme „Horizont“ – das Jubiläum bleibt, wenn du willst", Icons.Rounded.Star, Blue,
                    listOf(
                        "ZENITH 21 hat eine neue Karte: tiefes Blau ins Licht des Horizonts, eisblaue Akzente – auf der Versionskarte, der Systemkarte der Einstellungen und am Z.",
                        "Nach dem Update fragt ZENITH einmal: das neue Theme – oder das Jubiläum behalten? Wer das Jubiläum wählt, behält die goldene Karte und die goldenen Akzente von ZENITH 20, auch mit 21.",
                        "Ändern lässt es sich jederzeit: Einstellungen → ZENITH → Theme (in den Smart-Einstellungen auf der Seite „ZENITH“: der Schalter „Jubiläums-Theme behalten“). „Theme auf das ganze System anwenden“ setzt dazu Polarlicht, Glas, Z-Angriff und ZENITH-Hintergrund passend – Eisblau oder das Jubiläums-Gold –, einzeln änderbar wie immer.",
                        "Darunter ClaudeOS 8.0 „Horizon“.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der Smart-Modus, viel besser", Icons.Rounded.Settings, Pink,
                    listOf(
                        "Kategorien selbst bauen: In den Einstellungen unter App-Raster → Kategorien bearbeiten: hinzufügen, umbenennen, ein Zeichen wählen (fünfzehn zur Auswahl), nach oben oder unten schieben, entfernen. Sind es mehr als sieben, scrollt die Leiste. Apps ordnen sich von selbst ein; in eine andere legst du eine App im Menü (Halten).",
                        "Dreizehn Formen für die Icons – dazu Sechseck, Achteck, Raute, Stern, Blume und Schild, alle frei.",
                        "Eigene Farbe mit Farbwähler: Farbfeld, Farbton und der Wert als #RRGGBB, dazu die acht Farben der Liste zum Starten – für Ambient Design.",
                        "Die Suche rechnet um: „5 km in mi“, „20 c in f“, „3 lb nach kg“ – Länge, Gewicht, Volumen, Tempo, Daten, Zeit und Temperatur, gleich im Suchfeld (Rechnen konnte sie schon).",
                        "Zwei Smart-Widgets: der Smart-Akku – ein Balken, der sich mit dem Akku füllt, die Prozent groß (blau, beim Laden grün, fast leer rot) – und die Smart-Termine, die nächsten drei Termine aus dem Kalender auf einer Karte.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Vom Smart Launcher wechseln", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "„Be Smarter“ hat einen Wechsel-Assistenten in fünf Schritten: das Thema, die Icon-Form, was unter der Uhr steht, der Standard-Launcher. Zu öffnen ist er in den Smart-Einstellungen („Wechsel-Assistent“).",
                        "Dazu die Seite „Warum ZENITH Smart“: was hier frei ist und was Smart Launcher laut eigenen Texten nur mit Pro hat – Kategorien bearbeiten, mehr Formen, keine Werbung, Schutz für Apps.",
                        "Ehrlich dazu: Das Layout des Smart Launchers lässt sich nicht übernehmen, das Format ist ein anderes – aber alle Apps ordnet ZENITH Smart sofort selbst in Kategorien. Und ZENITH Smart ist ein Nachbau mit eigenem Code, kein Smart Launcher.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hotfix: Karte, Spruch, Update, Smart", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Eine Karte, die wirklich anders aussieht: Horizont zeigt einen klaren Himmel, eine Sonne, die hinter drei Bergrücken aufgeht, und ein paar blasse Sterne – statt Bögen und Feuerwerk. Dasselbe Bild trägt die Systemkarte oben in den Einstellungen; mit dem Jubiläums-Theme bleibt sie, wie sie war.",
                        "„Aim Higher.“ ruhiger: eine feine Schrift in einer Farbe, ohne Glühen, Farbverlauf und Lichtband.",
                        "Der Update-Fehler „INSTALL_FAILED_INVALID_APK“: Die App lädt nur noch eine ganz angekommene Datei, installiert sie als das Paket, das sie ist, und zeigt bei einem Fehler Androids ganze Meldung mit dem Grund auf der Seite des Software-Updates.",
                        "ZENITH Smart ist wieder auf dem Stand von 20.5, mit eigenem Code und eigener Versionslinie; „Be Smarter“ in ZENITH bleibt, wie es mit 21.0 ist.",
                        "Klare Dateinamen in den Downloads: ZENITH.apk (die Hauptapp, früher HearthUI.apk), ZENITH-Smart.apk, Duftschrank.apk (früher Duft.apk) und Hearth-alt.apk – der alte Launcher allein, mit demselben Paket wie ZENITH und nicht nötig.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "20.5",
            name = "Be Smarter",
            summary = "Das große Update: Im Illuminati gibt es jetzt „Be Smarter“ – ein Modus, der aus dem Launcher Smart Launcher 6 macht, nach dessen Vorbild: seine Uhr mit Datum, Wetter und Wecker, Icons ohne Namen, die Smart-Suche, der Drawer mit der Leiste der sechs Kategorien, seine fünf Themes bis zum Liquid Glass mit Reglern, Apps ohne Verzögerung und Glimmer als Teil des ganzen Systems.",
            sections = listOf(
                ChangeSection(
                    "ZENITH Smart – eine eigene App (Hotfix)", Icons.Rounded.Star, Blue,
                    listOf(
                        "Neu neben ZENITH: „ZENITH Smart“, eine App im Look des Smart Launchers 6 von der ersten Sekunde an. „Be Smarter“ ist an, die Uhr, der Drawer mit den Kategorien, die Smart-Suche, die Themes bis zum Liquid Glass – und die Einstellungen sind die des Smart Launchers nachgebaut: dunkler Grund, große Titel unter dem Zurück-Pfeil, Zeilen mit Linien-Symbol und Pfeil, blaue Schalter, Regler mit ihrem Wert und dem Knopf zum Zurücksetzen, Karten, das Thema mit seiner Vorschau und der Knopf „Thema Bearbeiten“ mit dem Regenbogenrand.",
                        "Im Kern der Einstellungen stehen ClaudeOS und ZENITH, gleich unter der Systemkarte mit dem großen „Aim Higher.“: ClaudeOS mit Claude & KI, Clawd, Glimmer, Software-Update und Über das Telefon; ZENITH mit der Zentrale, Design & Liquid Glass, dem Clawd Illuminati, Warum ZENITH, ZENITH Cloud, Datenschutz, Allgemein, System und dem Spruch. Alles, was keine eigene Seite im Smart-Stil hat, öffnet sich von dort in den Einstellungen von ZENITH selbst; zurück führt wieder in die Smart-Einstellungen.",
                        "Eine App für sich: ZENITH Smart hat ein eigenes Paket und ist kein Update für ZENITH. Beide können nebeneinander auf dem Handy sein, jede mit ihren eigenen Einstellungen und Daten; eine Sicherung aus ZENITH („Einstellungen sichern“) lässt sich in ZENITH Smart wiederherstellen. Sie hat eine eigene Update-Linie („smart“). Auch in ZENITH zeigt „Be Smarter“ ab jetzt diese Einstellungen; ausgeschaltet kommen ZENITHs eigene zurück.",
                        "Ehrlich dazu: Das ist kein Smart Launcher, sondern ein Nachbau mit eigenem Code nach den Bildschirmfotos, den Maßen und den Namen der Einstellungen. Die Originaldatei zu verändern und weiterzugeben geht nicht; wo etwas anders aussieht als beim Original, hilft ein Bildschirmfoto.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Be Smarter nach der Smart-Launcher-APK (Hotfix)", Icons.Rounded.Star, Blue,
                    listOf(
                        "Be Smarter ist nach der echten Smart-Launcher-6.6-Datei umgebaut, die du hochgeladen hast. Ich konnte sie nicht laufen lassen, aber ihre Dateien lesen: Maße, Farben, Zeiten und die Namen ihrer Einstellungen. Nachgebaut ist alles mit eigenem Code; übernommen habe ich nur Zahlen und Namen, keine Grafiken und keinen Programmcode.",
                        "Maße aus der Datei: die Leiste des Drawers ist 64 dp hoch mit 48-dp-Knöpfen, der Name der Kategorie bis 28 sp, die Ränder 8 dp; das Blatt des Startbildschirms hat 24 dp Rundung und eine feine Umrandung.",
                        "Apps öffnen sich wie dort: 125 Millisekunden, die App wächst aus 90 Prozent ein und blendet ein, der Startbildschirm tritt auf 110 Prozent zurück – ohne die 50 Millisekunden Warten davor.",
                        "Der Drawer hat jetzt seine eigene Suche: Die Lupe macht aus dem Namen der Kategorie ein Suchfeld (48 dp hoch, 28 dp rund, feine Umrandung), gefiltert wird über alle Kategorien; Enter startet die einzige übrige App, ein Kreuz schließt die Suche.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Neue Einstellungen aus der Smart-Launcher-APK (Hotfix)", Icons.Rounded.Settings, Pink,
                    listOf(
                        "Drawer: „Kategorieleiste“ (aus: eine Liste mit allen Apps), „Kategorientitel“ (der Name unter dem Zeichen), „Letzte Kategorie geöffnet“, „Übergangseffekt der Kategorien“ (Schieben, Überblenden, Zoomen, Drehen, 3D-Wendung, Strecken) und „Abstand zwischen den Icons“.",
                        "Startbildschirm: „Zeige Indikator“ für die Seitenpunkte, „Lesbarkeit verbessern“ (ein dunklerer Schleier über hellen Hintergründen) und der „Eckenradius der Widgets“.",
                        "Uhr: „Stundenformat“ (wie im System, 24 oder 12 Stunden), „Temperatur in Fahrenheit“ und „Ganztägige Ereignisse ausblenden“.",
                        "Gezeichnet ist das meiste im Smart Launcher in seinem Programmcode, nicht in Dateien – wo etwas noch anders aussieht, hilft ein Bildschirmfoto.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "„Aim Higher.“ groß auf der Systemkarte (Hotfix)", Icons.Rounded.Star, Orange,
                    listOf(
                        "Oben in den Einstellungen, auf der Karte mit dem Z, deinem Handy und der Versionsnummer, steht jetzt groß „Aim Higher.“ – in schweren, schrägen Buchstaben wie das Z, in der Farbe der Karte (Gold im Jubiläum), mit einem Lichtband, das darüberläuft.",
                        "Dasselbe auf der Versionskarte in „Über das Telefon“ und im Software-Update: groß unter dem Z; die Karte ist dafür ein Stück höher.",
                        "Und überall sonst mehr zu sehen: die stille Zeile ist größer und heller (auf dem Startbildschirm, dem Sperrbildschirm, der Z-Nacht, im Blatt des Startbildschirms), im ZENITH-Hintergrund und in Projekt Zenith steht er fast halb so groß wie das Z breit ist, in der Zentrale und in „Über das Telefon“ deutlich größer, in den Einstellungen unter der Überschrift gut lesbar.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Be Smarter, weiter (Hotfix)", Icons.Rounded.Settings, Pink,
                    listOf(
                        "Der nächste Termin an der Uhr: „Termin · 11:00 Zahnarzt“, „Morgen · 09:30 …“, „Jetzt · …“ oder „Heute · …“ bei ganztägigen. Ein-/ausschalten im Illuminati unter „Be Smarter · Uhr“; Android fragt dich dabei einmal nach dem Zugriff auf den Kalender (nichts wird ohne dein Okay freigegeben). Ein Tipp öffnet den Kalender.",
                        "Apps selbst einsortieren: Im Menü einer App (Halten) steht jetzt „In ‚Spiele‘ verschieben“ – jeder Druck schiebt sie in die nächste Kategorie des Smart-Drawers –, und „Kategorie zurücksetzen“ bringt sie dorthin zurück, wo sie von selbst hingehört.",
                        "Die Ecken der Widgets: ein Regler unter „Be Smarter · Startbildschirm“ von eckig bis sehr rund (0 bis 44 dp).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "„Aim Higher.“ überall im System (Hotfix)", Icons.Rounded.Star, Orange,
                    listOf(
                        "Der Spruch von ZENITH steht jetzt dort, wo im System Platz für ihn ist – als stille Zeile in weit gesetzten Großbuchstaben:",
                        "Auf dem Startbildschirm über den Seitenpunkten, und im Hintergrund selbst: der ZENITH-Hintergrund und der von Projekt Zenith tragen ihn unter dem Z beziehungsweise am Horizont.",
                        "Auf dem Sperrbildschirm und dem Always-On-Display unter dem Gruß, in den Einstellungen unter der Überschrift, in der Z-Nacht über dem Hinweis, im Illuminati unter dem Wahlspruch der Ordnung und im Blatt, das beim Halten des Startbildschirms aufgeht.",
                        "Und beim Z-Angriff: Nach dem Ausbruch steigt der Spruch im Licht des Z auf und geht mit dem Dunkel. Wie bisher am Start nach dem Neustart, in „Über das Telefon“, im Software-Update, in der Willkommens-Tour, in der Zentrale und am Ende der Jubiläums-Zeremonie.",
                        "Ausschalten lässt er sich unter Einstellungen → ZENITH: „Der Spruch ‚Aim Higher.‘ überall“.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Noch einmal gegen die Wartezeit (Hotfix)", Icons.Rounded.ThumbUp, Orange,
                    listOf(
                        "Jede Anfrage von Glimmer an den Launcher – bei jedem Öffnen und Schließen einer App mehrere – legte auf dem Hauptthread einen neuen Einstellungs-Leser an, der alle gut zweihundert Werte las und sich bis zur nächsten Speicherbereinigung an die Einstellungen hängte; jede Änderung ließ dann jeden dieser Leser noch einmal alles lesen. Jetzt gibt es keinen solchen Leser mehr, und die Antwort wird anderthalb Sekunden lang wiederverwendet.",
                        "Die Suche nach den alten Apps (Hearth One) lief bei jedem Zurückkommen auf dem Hauptthread; jetzt einmal pro Start.",
                        "Beim Zurückkommen aus einer App zeichnen Glas und fließende Farben die ersten knapp sieben Zehntelsekunden den leichten Weg, solange der Grafikspeicher wieder gefüllt wird – und die Linse kommt danach zurück.",
                        "Ehrlich: Gemessen habe ich auch das nicht. Wenn es hängt, sag mir bitte: nach der Wischgeste oder nach Zurück, mit Be Smarter an oder aus, und ob „Glimmer ins System binden“ an ist.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Kein Warten mehr nach dem Schließen (Hotfix)", Icons.Rounded.ThumbUp, Orange,
                    listOf(
                        "Nach dem Schließen einer App musste man kurz warten, bis die nächste aufging. Gesucht habe ich dort, wo der Launcher in genau diesem Moment arbeitet, und alles abgestellt, was in Frage kommt:",
                        "Die Widgets bleiben angeschlossen. Bisher trennte der Launcher sie nach 15 Sekunden, und beim Zurückkommen mussten alle auf einmal neu gezeichnet werden – genau dann, wenn man die nächste App antippt. Jetzt trennt er sie erst nach einer halben Stunde.",
                        "Be Smarter spielt keine eigene Schließ-Animation und macht keine Bilder der App mehr, solange „Glimmer ins System binden“ aus ist – wie beim Smart Launcher, der die des Systems zeigt. Mit Glimmer ist es die leichte „Smart Launcher“-Animation.",
                        "Die flachen Themes (Ambient Design, Material You, Frostet, Opak) zeichnen ihr Glas ohne den Linsen-Shader; nur Liquid Glass behält ihn.",
                        "Apps werden nicht mehr auf dem Hauptthread gestartet: Braucht das System einen Moment, wartet nicht der Bildschirm mit.",
                        "Ehrlich dazu: Ich konnte es nicht auf dem Handy messen. Hängt es danach noch, sag mir bitte, ob es nach der Wischgeste oder nach Zurück ist, und ob Widgets auf dem Startbildschirm liegen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Mehr einstellen", Icons.Rounded.Settings, Pink,
                    listOf(
                        "Neu im Illuminati unter „Be Smarter“: die Form der Icons (jetzt sieben: Squircle, Kreis, Abgerundet, Quadrat, Tropfen, Blatt, Kante), Größe, Spalten und Reihen, die Smart-Suche an oder aus.",
                        "Die Uhr: Größe, Schrift, Anordnung (zentriert oder kompakt mit der Zeit links und dem Wetter rechts), Wetter und Wecker einzeln ein- oder ausschaltbar. Das Datum steht jetzt wie im Smart Launcher: „So., 11. Oktober ⛅ 10°C“.",
                        "Der Drawer: Spalten (drei bis sechs), die Namen unter den Icons und deren Größe.",
                        "Die Farbe der Flächen im Ambient Design: nach dem Hintergrundbild oder eine von acht Farben – wie „Thema bearbeiten → Farbe“.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Be Smarter", Icons.Rounded.Star, Blue,
                    listOf(
                        "Zu finden im Clawd Illuminati (Versionsnummer in „Über das Telefon“ antippen): ganz oben die Karte „Be Smarter“. Ein Tipp, und ZENITH sieht aus wie Smart Launcher 6 – und ein Tipp zurück, und alles ist wieder wie vorher. Der Modus legt sich nur über das, was du gewählt hast; geschrieben wird nichts.",
                        "Solange er an ist, treten ZENITHs eigene Looks und Effekte – Metall, Polarlicht, Fluid, Z-Regen, Parade und die anderen – zurück.",
                        "Gebaut nach Bildschirmfotos von Smart Launcher 6 (Startbildschirm, Drawer, Einstellungen, Themes). Eine Annäherung, kein Abpausen: Wo etwas abweicht, lässt es sich mit einem weiteren Bildschirmfoto angleichen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der Startbildschirm", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Die Uhr steht in der Mitte: die Zeit groß, darunter „Datum · Wetter“ mit Bild und Temperatur, darunter „Wecker · 07:00“ (mit dem Tag, wenn er nicht heute klingelt). Ein Tipp aufs Datum öffnet den Kalender, aufs Wetter die Zentrale, auf den Wecker die Wecker. Das Wetter braucht den Ort, den du einmal in der Zentrale einträgst.",
                        "Die Icons stehen ohne Namen auf dem Startbildschirm, als abgerundete Quadrate (im Liquid Glass als runde Glas-Kacheln). Das Dock hat keine eigene Leiste mehr; darunter ganz unten die Pille „Smart-Suche“.",
                        "Halten auf dem Startbildschirm öffnet ein Blatt von unten: „Startbildschirm“ mit Widget hinzufügen, Apps auswählen und Suche öffnen, dazu Hintergrundbild, Allgemeine Darstellung und Startbildschirm – und oben „Alle Einstellungen anzeigen“.",
                        "Wischen nach unten bringt die Suche mit den Apps, die du als Nächstes brauchst.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der Drawer mit sechs Kategorien", Icons.Rounded.Home, Green,
                    listOf(
                        "Nach oben wischen auf dem Startbildschirm öffnet den Drawer (auch per Doppeltipp „Drawer“ und über „Alle Apps“ im ZENITH-Menü). Wie in Smart Launcher sitzt alles tief, wo der Daumen hinkommt: oben groß der Name der Kategorie, daneben Suche, Store und Menü, darunter die Apps in vier Spalten mit ihren Namen.",
                        "Unten die Leiste mit sechs Kategorien auf einer Pille: Kommunikation, Social, Spiele, Medien, Dienstprogramme und Einstellungen – die gewählte mit einem kurzen Strich unter dem Symbol. Seitlich wischen oder die Leiste antippen wechselt; der Drawer öffnet wieder bei der zuletzt gewählten.",
                        "Welche App wohin kommt, entscheidet, was sie von sich sagt (Telefon, Nachrichten und Mail zu Kommunikation, Netzwerke zu Social, Musik und Video zu Medien …) – nach Namen und Art, nicht nach Smart Launchers eigener Liste.",
                        "Der Startbildschirm tritt zurück, wenn der Drawer aufsteigt; nach unten ziehen (oder Zurück) lässt ihn los.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Die fünf Themes", Icons.Rounded.Info, Violet,
                    listOf(
                        "Wie in „Allgemeine Darstellung“ des Smart Launchers: Ambient Design (durchscheinende Flächen in den Farben deines Hintergrundbildes), Material You (die Farben des Telefons, ab Android 12), Frostetes Glas, Opakisches Glas (am festesten, immer lesbar) und Liquid Glass (durchscheinende Flächen mit fließenden Lichtreflexen).",
                        "Dazu die Regler „Oberfläche“: Unschärferadius, Lebendigkeit verwischen, Brechung, Tiefe und Streuung wie dort, außerdem Glanz und Tönung. Die Wahl eines Themes stellt sie auf dessen Werte, danach sind sie frei.",
                        "Die Glas-Einstellungen von ZENITH selbst bleiben unberührt – beim Ausschalten ist alles wie vorher.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Ohne Verzögerung", Icons.Rounded.ThumbUp, Orange,
                    listOf(
                        "Apps öffnen sich im Moment des Tippens: Der Fluid-Spritzer unter dem Finger fällt weg, die Feder am Icon ebenso, und die Öffnen-Animation ist nur noch ein kurzes Einblenden (etwa eine Siebtelsekunde).",
                        "Wer es wie beim Smart Launcher mag, schaltet „Ohne Verzögerung“ aus: Dann klappt die App aus ihrem Icon auf.",
                        "Die Dauer richtet sich nach dem Fensteranimationsmaßstab in den Entwickleroptionen des Telefons – steht er auf 1×, ist es das, was du siehst.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Glimmer im ganzen System", Icons.Rounded.Phone, Pink,
                    listOf(
                        "Neuer Schalter „Glimmer ins System binden“: Er schaltet Glimmer, sein Schließ-Fly-in für alle Launcher und das Verdecken der Launcher-Animation zusammen an.",
                        "Neuer Stil „Smart Launcher“ für das Schließen (Glimmer-Einstellungen → „So fliegt sie hinein“): Die App behält ihre Form und schrumpft in einer weichen Bewegung zurück in ihr Icon – das Bild der App wird dabei zum Icon. Bei einem Start vom Startbildschirm oder Dock aus landet sie genau auf dem angetippten Icon; mit einem anderen Launcher unten im Dock.",
                        "Die Öffnen-Animation eines anderen Launchers kann Glimmer nicht ersetzen – die macht der Launcher selbst. In ZENITH ist sie die des Smart-Looks.",
                        "Glimmer braucht dafür seinen Dienst in den Bedienungshilfen; die Karte zeigt, ob er läuft, und führt dorthin.",
                    ),
                    withGlimmer,
                ),
                ChangeSection(
                    "Noch nicht dabei", Icons.Rounded.Edit, Slate,
                    listOf(
                        "Aus Smart Launcher 6 fehlen noch: „Your Feed“ (Nachrichten), der Private Space, das Widget-Paket mit Stapeln und Symbolgruppen, das Dock mit mehreren Reihen und Seiten, die Auswahl aus über zwanzig Symbolformen (hier drei) und „Thema bearbeiten“ mit freien Farben.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "20.0",
            name = "Jubiläum",
            summary = "Zwanzig Versionen ZENITH – das größte Update bisher: die Zentrale für den ganzen Tag, ein Wetter mit dem Himmel als Bild, das Polarlicht auf dem Startbildschirm, die Z-Nacht fürs Nachttischchen und eine Zeremonie zum Jubiläum. Darunter ClaudeOS 7.0 „Apex“.",
            sections = listOf(
                ChangeSection(
                    "Die Jubiläums-Zeremonie", Icons.Rounded.Star, Orange,
                    listOf(
                        "Einmal nach dem Update: Im Dunkeln zählen die Zahlen von 1 bis 20, die 20 rastet in Gold ein, eine Druckwelle läuft über den Bildschirm – und ein Feuerwerk in den Farben des Z-Angriffs beginnt. „Zwanzig Versionen. Danke, dass du von Anfang an dabei bist.“ Ein Tipp geht weiter.",
                        "Sie ersetzt beim Update den Z-Angriff. Ansehen kannst du sie jederzeit wieder: in der Zentrale bei „Schnellzugriff → Jubiläum“.",
                        "Neues Egg hinter der Versionsnummer (ZENITH 20): Feuerwerk – zwanzig Tipps, zwanzig Feuerwerke, eins für jede Version. Dann leuchtet die 20 in Gold.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der Spruch des Systems", Icons.Rounded.ThumbUp, Blue,
                    listOf(
                        "ZENITH hat jetzt einen Spruch, wie OnePlus „Never Settle“: „Aim Higher.“ – ziel höher, denn der Zenit ist der höchste Punkt, den es gibt.",
                        "Er steht beim Start nach dem Neustart, in „Über das Telefon“, im Software-Update, in der Willkommens-Tour, in der Zentrale und am Ende der Jubiläums-Zeremonie.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Eine Karte, die nur 20 hat", Icons.Rounded.Info, Orange,
                    listOf(
                        "Die Versionskarte (Software-Update, Über das Telefon) sieht in ZENITH 20 anders aus als in 19 – wie bei ColorOS, wo jede Version ihre eigene Karte hat: Mitternachtsblau läuft in Gold, das Polarlicht zieht darüber, Sterne funkeln, drei Feuerwerke stehen eingefroren am Himmel, und die 20 und das Randlicht des Z leuchten in Gold, darunter „J U B I L Ä U M“.",
                        "Die Karte von ZENITH 19 bleibt grün. Jede kommende Version bekommt eine eigene.",
                        "Hältst du auf der Karte das Z gedrückt, kommt weiterhin der Z-Angriff (Core Enforcer) – und auf der Karte von 20 steigt dazu ein Feuerwerk auf, wie bei ColorOS 16.",
                        "Überall im System, wo das Z mit seiner Nummer steht (Illuminati, Willkommens-Tour, Jubiläums-Feuerwerk, Einstellungen), steht jetzt die 20 statt der 19.",
                        "Vorschau vor dem Installieren, wie bei ColorOS: Wird im Software-Update eine neuere Version angeboten, zeigt es schon ihre Karte – die neue Version bringt Farben und Beschriftung ihrer Karte in der Update-Datei mit. Das gilt ab ZENITH 20 für jedes künftige Update (eine ältere App wie 19.8 kennt es noch nicht und malt ihre grüne Karte).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Jubiläums-Edition für jedes Egg", Icons.Rounded.Star, Orange,
                    listOf(
                        "Jedes Egg hat jetzt eine Jubiläums-Edition: Beim ersten Mal in ZENITH 20 – auch wenn du es schon kennst – setzt es ein goldenes Feuerwerk über den Bildschirm. Auf der Easter-Egg-Karte bekommt es dafür ein goldenes „✦ 20“ und einen goldenen Kreis.",
                        "Die Eggs der Versionen vor 20, die der ClaudeOS vor 7 und alle Geheimmenüs liegen jetzt in Gold: goldenes Polarlicht, aufsteigender Goldstaub und ein Abzeichen „Jubiläums-Edition“.",
                        "Neu in der Zentrale: die Jubiläums-Galerie – alle Versionen von Hearth 11 bis ZENITH 20 und alle sieben ClaudeOS auf einmal, jedes Egg zum Antippen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Zwölf neue Jubiläums-Eggs", Icons.Rounded.CheckCircle, Blue,
                    listOf(
                        "Das Jubiläum steckt in der Uhr zu einer bestimmten Minute und in einem Wort im Finder. Im Polarlicht in allen fünf Farben. In der Z-Nacht nachts und nach zwanzig Minuten, in der Zentrale früh am Morgen, an einem 20. des Monats, bei 20 Grad und im Gruß – und bei genau 20 % Akku.",
                        "Zwei kommen von allein: bei zwanzig gefundenen Eggs und bei vierzig in der Jubiläums-Edition. Wo die anderen stecken, verrät die Easter-Egg-Karte.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "ZENITH-Farbe im ganzen System", Icons.Rounded.Edit, Green,
                    listOf(
                        "Das Gold der Jubiläumskarte gibt es jetzt für das ganze System – und nicht nur Gold: Einstellungen → ZENITH → „ZENITH-Farbe“ (auch im Core Enforcer Studio und in der Zentrale) tauscht das Zenith-Grün gegen Jubiläums-Gold, Eisblau, Violett oder Rubin.",
                        "Die Farbe gilt überall, wo ZENITH grün leuchtet: das Randlicht des Z, die Zahl im Z, Karten, Rahmen, Tresor, Schutzschild, Monitor und die Texte im HUD-Stil. Das Polarlicht, der Z-Angriff, der Akzent und das Glas werden passend gesetzt und lassen sich danach einzeln ändern.",
                        "Die Farbe wird beim Würfeln im Studio mitgewürfelt und im Profil gespeichert.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Hintergrundbeleuchtung einstellbar", Icons.Rounded.Settings, Orange,
                    listOf(
                        "Das ZENITH-Licht auf dem Startbildschirm hat jetzt Regler: Stärke (von aus bis doppelt so hell), Farbe (wie die ZENITH-Farbe oder eine eigene) und drei Teile zum Ein- und Ausschalten – der Lichtstrahl entlang der Z-Diagonale, das Funkeln oben rechts und das Bodenlicht hinter dem Dock. Zu finden unter Einstellungen → ZENITH und im Core Enforcer Studio.",
                        "Das Polarlicht hat Stärke und Tempo zum Einstellen.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Jubiläums-Look", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Ein Tipp im Core Enforcer Studio (oder in der Zentrale): ZENITH-Farbe Jubiläums-Gold mit goldenem Polarlicht, Akzent, Glas und Z-Angriff, dazu der ZENITH-Hintergrund. Beim Würfel geht es mit „Zurück“ wieder weg.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "ZENITH-Zentrale", Icons.Rounded.Home, Green,
                    listOf(
                        "Neu: der ganze Tag auf einem Bildschirm, unter dem Polarlicht. Oben der Gruß und die Uhrzeit groß, mit Sekunden und Datum.",
                        "Der Tagesbrief sagt, was das Wetter für heute heißt („Nimm einen Schirm mit: 70 % Regenwahrscheinlichkeit“), welches Parfüm der Duftschrank für heute gewählt hat, wann der nächste Wecker klingelt und wie es um Akku und Schutz steht.",
                        "Auf Wunsch fasst Claude den Brief in eigene Worte – höchstens vier Sätze, nur aus dem, was das Telefon weiß. Dafür braucht er seinen Schlüssel (Frag Clawd); ohne Schlüssel bleibt der Brief, wie er ist.",
                        "Darunter das Wetter, der Zustand des Telefons (Akku mit Temperatur, Arbeitsspeicher, Speicher, Laufzeit), die Farben des Polarlichts und Abkürzungen zu Z-Nacht, Tresor, Schutzschild, Core Enforcer, Duftschrank, Z-Angriff und der Zeremonie.",
                        "Erreichbar über: ZENITH-Menü → „ZENITH-Zentrale“, Einstellungen → ZENITH, per Doppeltipp auf den Startbildschirm (Einstellungen → Startbildschirm → „Z-Zentrale“) oder antippen des Wetter-Widgets.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Wetter mit dem Himmel als Bild", Icons.Rounded.Info, Blue,
                    listOf(
                        "Neues Widget „ZENITH-Wetter“: die Temperatur groß, Höchst- und Tiefstwert, Regenwahrscheinlichkeit, und rechts der Himmel als bewegtes Bild – Sonne mit drehenden Strahlen, Mond mit funkelnden Sternen, Wolken, Regen, Schnee, Nebel, Gewitter mit Blitz. Darunter die nächsten Stunden.",
                        "In der Zentrale zusätzlich: gefühlte Temperatur, Wind, Luftfeuchte, alle zwölf Stunden zum Wischen und sechs Tage voraus mit Balken vom Tiefst- zum Höchstwert.",
                        "Den Ort tippst du einmal ein (Zentrale → Wetter). Er gilt auch für den Duftschrank. Das Wetter kommt von Open-Meteo, ohne Anmeldung; gesendet werden nur die Koordinaten des Ortes.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Polarlicht", Icons.Rounded.Favorite, Violet,
                    listOf(
                        "Neu: Lichtvorhänge ziehen langsam über den Startbildschirm, hinter den Apps und Widgets – wellenförmig, mit funkelnden Sternen und einem Leuchten wie von einer Lichtquelle.",
                        "Fünf Farbwelten: Zenith-Grün, Jubiläums-Gold, Eisblau, Violett und Rubin. Einstellungen → ZENITH → „Polarlicht“ (auch im Core Enforcer Studio und in der Zentrale).",
                        "Das Polarlicht wird beim Würfeln im Studio mitgewürfelt und mit dem Look als Profil gespeichert. Mit Animationen aus steht es still.",
                        "Neues Widget „ZENITH-Polarlicht“: das Polarlicht als lebendes Bild mit Uhrzeit und Datum. Antippen öffnet die Z-Nacht.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Z-Nacht", Icons.Rounded.Phone, Slate,
                    listOf(
                        "Neu: der Nachttisch. Eine große, dünne Uhr unter dem Polarlicht, dazu Datum, Wetter, nächster Wecker und Akku. Der Bildschirm bleibt an und ist gedimmt, damit er das Zimmer nicht erleuchtet.",
                        "Ein Tipp macht ihn heller, noch einer wieder dunkel. Die Uhr wandert jede Minute ein paar Pixel, damit nichts einbrennt. Zurück beendet.",
                        "Erreichbar über: ZENITH-Menü → „Z-Nacht“, Einstellungen → ZENITH, per Doppeltipp auf den Startbildschirm oder über das Polarlicht-Widget.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "ClaudeOS 7.0 „Apex“", Icons.Rounded.Star, Terracotta,
                    listOf(
                        "Der Gipfel: Die Basis unter ZENITH ist jetzt ClaudeOS 7.0 mit dem Codenamen „Apex“. Zentrale, Wetter, Polarlicht und Z-Nacht gehören zu ihrem Fundament.",
                        "Neues Egg: das Claude-Zeichen wartet im Tal. Zieh es mit dem Finger die Berge hinauf – je höher, desto mehr sinken die Berge, der Himmel wird hell und das Polarlicht wacht auf. Oben ist die Höhe 8.848 m erreicht.",
                        "Das ClaudeOS-Museum kennt Apex und ZENITH 20 mit ihren Eggs.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Neue Gesten und Widgets", Icons.Rounded.Add, Pink,
                    listOf(
                        "Doppeltipp auf den Startbildschirm kann jetzt auch die Zentrale und die Z-Nacht öffnen.",
                        "Das ZENITH-Menü hat Kacheln für die Zentrale und die Z-Nacht.",
                        "Zwei neue Widgets: „ZENITH-Wetter“ und „ZENITH-Polarlicht“ (Widget hinzufügen).",
                        "„Warum ZENITH“ nennt vier neue Vorteile: Zentrale, Wetter, Polarlicht und Z-Nacht.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Jetzt 69 Easter Eggs", Icons.Rounded.CheckCircle, Blue,
                    listOf(
                        "Fünfzehn neue, insgesamt 69: In der Z-Nacht gibt es einen Mond mit einem Geheimnis. In der Zentrale versteckt sich etwas hinter dem Wetterbild – und hinter der Uhr. Dazu die zwölf Jubiläums-Eggs. Die Easter-Egg-Karte verrät, wo.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.8",
            name = "Duftschrank",
            summary = "Der Parfüm-Berater ist jetzt auch eine App für sich: der Duftschrank – mit eigenem Widget für jeden Launcher. Im Widget steht der ganze Name des Parfüms, und Clawd und das ZENITH-Z lassen sich ausschalten.",
            sections = listOf(
                ChangeSection(
                    "Der Duftschrank als eigene App", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Neu: die App „Duftschrank“ (Clawds Parfüm-Berater, ohne den Rest des Launchers) zum Herunterladen bei den Releases. Sie kann alles, was der Berater in ZENITH kann: Parfüms aufschreiben oder fotografieren, echte Flakon-Bilder, Mag ich / Mag ich nicht, jeden Tag das passende nach Wetter.",
                        "Dazu ein Widget für jeden Launcher (auch Samsungs One UI Home): heutiges Parfüm mit Bild, ganzem Namen und Begründung. Antippen öffnet den Duftschrank.",
                        "Den API-Schlüssel trägst du im Duftschrank selbst ein (Claude, Gemini und andere); in ZENITH gilt derselbe Schlüssel wie bei Clawd.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Lesbarer, und Clawd oder ZENITH weg", Icons.Rounded.Star, Green,
                    listOf(
                        "Der Name des Parfüms steht jetzt ganz da, über bis zu drei Zeilen, mit „Heute“ darüber – nicht mehr abgeschnitten. Doppelte Markennamen („Emporio Armani Armani …“) werden zusammengefasst.",
                        "Clawd und das ZENITH-Z lassen sich einzeln ein- und ausschalten. Beide aus gibt dem Namen und der Begründung am meisten Platz.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.7",
            name = "Duft",
            summary = "Clawd kennt jetzt deine Parfüms und sucht dir jeden Tag das passende aus – nach Wetter und Geschmack. Dazu: Das vierte Icon im Dock ist nicht mehr zu klein.",
            sections = listOf(
                ChangeSection(
                    "Clawd-Parfüm", Icons.Rounded.Favorite, Pink,
                    listOf(
                        "Neues Widget „Clawd-Parfüm“ (Widget hinzufügen → Clawd): Clawd sagt dir, welches Parfüm du heute tragen solltest, und warum.",
                        "Antippen öffnet Clawds Duftschrank. Dort schreibst du auf, welche Parfüms du hast – oder du schickst ein Foto von einem oder mehreren Flakons. Clawd erkennt jedes, beschreibt es (Duftfamilie, Noten, Jahreszeit, passende Temperaturen, Intensität) und merkt es sich.",
                        "Bei jedem Parfüm stellst du ein: Mag ich, Okay oder Mag ich nicht. Clawd bevorzugt, was du magst, und schlägt nie vor, was dir nicht gefällt.",
                        "Der Tagesvorschlag nimmt das Wetter deines Ortes (Open-Meteo, ohne Anmeldung), die Jahreszeit und den Wochentag dazu und wechselt ab, damit nicht jeden Tag dasselbe kommt. „Etwas anderes“ zeigt einen weiteren, „Trage ich heute“ merkt sich das Parfüm.",
                        "Analyse und Fotos laufen über deinen API-Schlüssel bei „Frag Clawd“ (Fotos mit Claude oder Gemini). Ohne Schlüssel merkt sich Clawd die Namen und wählt nach Temperatur und Geschmack allein.",
                        "Echte Bilder der Flakons: Clawd sucht zu jedem Parfüm von selbst ein Produktbild im Netz (Websuche, Open Beauty Facts, Wikipedia – es geht nur der Name des Parfüms hinaus) und zeigt es neben dem Vorschlag, im Widget und im Duftschrank, und in der Sammlung. Schickst du ein Foto, schneidet Clawd stattdessen deinen Flakon aus.",
                        "Passt ein Bild nicht, tippst du beim Parfüm auf „Bild suchen“ und wählst ein anderes aus den Treffern – oder nimmst „Eigenes Foto“. Nur wenn gar nichts gefunden wird, steht ein gezeichneter Flakon da.",
                        "Wer berät dich? Im Duftschrank wählst du, ob Clawd neben dem Vorschlag steht oder das ZENITH-Z – im Widget, im Duftschrank und in den Texten.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Das Dock", Icons.Rounded.Home, Blue,
                    listOf(
                        "Das vierte Icon im Dock war kleiner als die anderen, weil vier große Icons nicht in die Leiste passten. Jetzt passen sich alle Icons der Breite der Leiste an – gleich groß, egal wie viele es sind.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.6",
            name = "Verstecke",
            summary = "Viel mehr zu finden: sieben neue Geheimmenüs, Verstecke aus den Hearth-, OMEGA- und ZENITH-Jahren und kleine Funde überall im System – jetzt 54 Easter Eggs.",
            sections = listOf(
                ChangeSection(
                    "Sieben neue Geheimmenüs", Icons.Rounded.Search, Violet,
                    listOf(
                        "*#8376# Z-Konsole: ein Terminal mit Befehlen – tippe help. Es gibt auch welche, die nicht in der Liste stehen.",
                        "*#6366# Neon: der Regen aus Zeichen, in allen Farben des Z-Angriffs.",
                        "*#7625# Funken-Schlange: wischen, Funken fressen, lang werden – mit Bestwert.",
                        "*#3729# Lichtmalerei: mit dem Finger Licht malen, das langsam verblasst.",
                        "*#6725# Ω-Orakel: eine Ja-Nein-Frage, ein Tipp auf die Kugel, eine Antwort.",
                        "*#9277# Blitz-Test: wer tippt schneller, wenn es grün wird?",
                        "*#2733# Abspann: alle Zeiten des Launchers, alle Namen – und wie viele Eggs du gefunden hast.",
                        "Alle stehen in der Liste der Geheimcodes (*#0000#).",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Im System selbst zu finden", Icons.Rounded.Star, Green,
                    listOf(
                        "Die Geheimmenüs öffnen sich nicht nur über Codes: Du findest sie, indem du das System anfasst.",
                        "Im ZENITH-Menü lange auf Kacheln drücken. In „Über das Telefon“ bestimmte Zeilen fünfmal schnell antippen oder die „made by“-Zeile. Auf dem Startbildschirm die Uhr zur richtigen Minute antippen. In der Ruhmeshalle eine Zeit lange drücken. In den Einstellungen die Zeile ganz unten lange drücken. Im Core Enforcer Studio den Titel lange drücken.",
                        "Die Easter-Egg-Karte verrät bei jedem Egg, wo es steckt.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Verstecke, die ein Wort brauchen", Icons.Rounded.Favorite, Terracotta,
                    listOf(
                        "Sieben Verstecke stammen aus den Hearth-, OMEGA- und ZENITH-Jahren: ein Kamin, ein alter Code, fließendes Licht, ein paar Scherze und mehr. Sie öffnen sich im System – und auch mit dem richtigen Wort im Finder.",
                        "Wo sie sind, verrät nur die Easter-Egg-Karte (Über das Telefon → „made by …“ lange drücken).",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Kleine Funde überall", Icons.Rounded.Star, Green,
                    listOf(
                        "Im ZENITH-Monitor, im Schutzschild, im Core Enforcer Studio und auf der Uhr des Startbildschirms stecken kleine Eggs – manche zeigen sich nur zu einer bestimmten Zeit oder bei einer bestimmten Zahl.",
                        "Und wer den Schutzschild auf 100 von 100 bringt, bekommt dafür auch etwas.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Jetzt 54 Easter Eggs", Icons.Rounded.CheckCircle, Blue,
                    listOf(
                        "Zweiundzwanzig neue, insgesamt 54. Die Karte zeigt, welche du schon hast – und wo die anderen sich verstecken.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.5",
            name = "Z-Sturm",
            summary = "Das große Update: Der Z-Angriff kommt jetzt überall im System – nach Updates, beim Tresor, beim Würfeln, beim Laden und per Doppeltipp. Dazu zwei Wächter, die dich warnen, elf neue Vorteile in „Warum ZENITH“ und ein neues Easter Egg.",
            sections = listOf(
                ChangeSection(
                    "Der Z-Angriff, überall im System", Icons.Rounded.Star, Green,
                    listOf(
                        "Nach jedem ZENITH-Update begrüßt dich der Z-Angriff einmal – in deiner Farbe und deinem Tempo aus dem Core Enforcer Studio.",
                        "Der ZENITH-Tresor geht mit dem Z-Angriff auf, sobald Passwort oder Fingerabdruck stimmen.",
                        "Neu beim Doppeltippen auf den Startbildschirm: „Z-Angriff“ (Einstellungen → Startbildschirm) – und eine eigene Kachel „Z-Angriff“ im ZENITH-Menü.",
                        "Würfelst du im Studio einen neuen Look, kommt er mit dem Z-Angriff in seiner neuen Farbe.",
                        "Mit Glimmer auch über allen Apps: beim Einstecken des Ladekabels und wenn der Akku voll ist.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Die Versionskarte in „Über das Telefon“", Icons.Rounded.Star, Green,
                    listOf(
                        "Tippst du dort einmal auf die ZENITH-Karte, wird sie größer – so hoch wie die Versionskarte bei OxygenOS, mit dem Z weiter unten im Bild.",
                        "Ein Tipp auf die Karte neben dem Z macht sie wieder klein. Tippst du auf das Z selbst, bleibt sie groß – so zählen die schnellen Tipps zu den Clawd Illuminati weiter, ohne dass die Karte zuckt.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Z-Momente im Core Enforcer Studio", Icons.Rounded.Edit, Green,
                    listOf(
                        "Neues Feld „Z-Momente“: Du bestimmst, wann der Z-Angriff von selbst kommt – nach Updates, beim Tresor, beim Würfeln, beim Einstecken und bei vollem Akku. Laden und voller Akku sind anfangs aus.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Die Wächter", Icons.Rounded.Lock, Orange,
                    listOf(
                        "Neue-App-Wächter: Nach jeder Installation sagt dir die Glimmer-Insel, was die neue App will – Kamera, Mikrofon, Standort, Kontakte, SMS … oder dass sie nichts Heikles will.",
                        "Akku-Wächter: Erreicht der Akku beim Laden deine Grenze (70–95 %), sagt die Glimmer-Insel einmal Bescheid. Früher abziehen schont den Akku auf Dauer.",
                        "Beide im Schutzschild unter „Wächter“. Sie schauen nur und sagen Bescheid – ändern tun sie nichts.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Warum ZENITH: jetzt 26 Vorteile", Icons.Rounded.CheckCircle, Blue,
                    listOf(
                        "Die Seite „Warum ZENITH“ in den Einstellungen zeigt jetzt 26 Dinge, die One UI und ColorOS so nicht können. Neu dabei: ZENITH-Tresor, In den Tresor teilen, Schutzschild, Neue-App-Wächter, Akku-Wächter, Sicherungen mit Passwort, Core Enforcer Studio, Z-Angriff, Projekt Zenith, ZENITH-Monitor und ein Spiel in jeder Version.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Ein neues Easter Egg: Z-Sturm", Icons.Rounded.Favorite, Violet,
                    listOf(
                        "Fünfmal schnell auf die Version tippen: Fünf Kerne schweben im Dunkeln, jeder in einer Farbe des Z-Angriffs. Tippe sie an – jeder schlägt in seiner Farbe zu, und zum Schluss kommt der weiße Z-Sturm.",
                        "Auch in der Ruhmeshalle (Version lange drücken).",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.3",
            name = "Core Enforcer",
            summary = "Das Core Enforcer Studio: die Werkstatt, in der du dein System umbaust – Look würfeln, Profile speichern, den Z-Angriff in Farbe und Tempo gestalten und alles vom Icon bis zum Glas an einem Ort einstellen.",
            sections = listOf(
                ChangeSection(
                    "ColorOS-Look: Uhr und Dock frei", Icons.Rounded.Edit, Blue,
                    listOf(
                        "Im ColorOS-17.1-Look lassen sich Uhr und Dock jetzt frei wählen – in den Illuminati direkt unter dem Schalter, in den Einstellungen und im Core Enforcer Studio.",
                        "Der ColorOS-Look merkt sich seine Uhr und sein Dock getrennt: Schaltest du ihn aus, sind deine üblichen wieder da.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Der Z-Angriff, mächtiger", Icons.Rounded.Star, Green,
                    listOf(
                        "Der Strahl brennt jetzt das echte ZENITH-Z in den Boden – Balken für Balken, so breit und schwer wie im Logo, statt dünner Linien.",
                        "Dann steigt das Metall-Z riesig aus dem Boden auf, fast so breit wie der Bildschirm, mit grünem Licht an den Kanten; ein Lichtband läuft darüber, Schockwellen in Z-Form breiten sich aus, und am Ende schwillt es an und verglüht.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Die Versionskarte", Icons.Rounded.Star, Green,
                    listOf(
                        "Wo die Version steht (Software-Update, Telefoninfo), liegt das Z jetzt auf einer großen Karte wie bei OxygenOS und ColorOS: ein Verlauf aus Tiefgrün in Smaragd mit Lichtbögen, oben das riesige Z mit der Version, darunter „ZENITH“, unten „Version aktuell“.",
                        "Das Z ist breiter und größer als vorher; hältst du es gedrückt, schwillt es in die Breite, die Karte leuchtet auf – und beim Loslassen federt es zurück.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Core Enforcer Studio", Icons.Rounded.Edit, Green,
                    listOf(
                        "Neu: das Studio, im ZENITH-Menü (Kachel „Core Enforcer Studio“) und in den Einstellungen unter Design → ZENITH. Alles, was du dort einstellst, gilt im ganzen System sofort.",
                        "Würfel: wirft Akzent, Icon-Form und -Stil, Uhr, Dock, Seitenwechsel, Glas und Z-Angriff neu zusammen. Gefällt es dir nicht, holt „Zurück“ den Look davor wieder.",
                        "Profile: speichere deinen ganzen Look unter einem Namen und hol ihn mit einem Tipp zurück. Gespeichert werden nur Optik-Einstellungen – nie App-Sperren, Glimmer oder Geheimes.",
                        "Z-Angriff gestalten: Farbe (Zenith-Grün, Eisblau, Violett, Rubin, Gold, Weiß) und Tempo (schnell, normal, langsam), mit „Probe“ zum Ansehen. Gilt auch für den Angriff beim Entsperren und im ZENITH-Start.",
                        "Alles an einem Ort: Icon-Form, -Stil und -Größe, Namen und ihre Größe, Uhr und ihre Schrift, Dock, Seitenwechsel, Begrüßung, Suchleiste, Akzent, Glas-Stil und -Qualität, Lichtbrechung, Unschärfe, Farbsäume, Glanz, Tönung, Glasfarbe, ZENITH-Hintergrund und Flüssig-Modus.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.2",
            name = "ColorOS-Look",
            summary = "Projekt Zenith bekommt einen weiteren Look: das ganze System im Stil von ColorOS 17.1 – und das Glas dabei frei einstellbar.",
            sections = listOf(
                ChangeSection(
                    "ColorOS-17.1-Look", Icons.Rounded.Star, Blue,
                    listOf(
                        "Neue Option in Projekt Zenith (Illuminati → Projekt Zenith → ColorOS-17.1-Look): Das ganze System bekommt den Stil von ColorOS – Milchglas statt Hologramm, runde Squircle-Icons, die ColorOS-Uhr, das ColorOS-Schnellmenü, ein schwebendes Dock und dein eigener Hintergrund statt des Gitter-Horizonts.",
                        "Das Glas stellst du frei ein, gleich dort unter „Glas einstellen“: Lichtbrechung, Unschärfe, Farbsäume, Glanz, Tönung und Glasfarbe – dazu fünf Looks zum Antippen (Dezent, ColorOS, Klar, Milchglas, Kristall). Dieselben Regler gibt es in den Einstellungen unter Liquid Glass.",
                        "Die Einstellungen behalten im ColorOS-Look das Aussehen von Galaxy × Zenith: One UIs Aufbau mit den Kategorie-Karten, dazu ZENITHs Metall.",
                        "Nichts wird überschrieben: Der Look liegt nur über deinen Einstellungen. Schaltest du ihn aus, ist alles wieder genau so, wie du es hattest.",
                        "Gibt es nur in den Illuminati, wenn Projekt Zenith an ist; Glas-Modus und ColorOS-Look schließen sich aus.",
                        "Es ist ein Nachbau des Stils: ZENITH benutzt dafür keine Logos, Bilder oder Schriften von ColorOS.",
                    ),
                    everyone,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.1",
            name = "Zenith Glas",
            summary = "Das erste richtige Update nach ZENITH 19: Projekt Zenith aus echtem Flüssigglas, Fotos aus der Galerie direkt in den Tresor teilen, ein Tresor zum Durchwischen mit Diashow – und zwei neue Widgets, der ZENITH-Monitor und der Schutzschild.",
            sections = listOf(
                ChangeSection(
                    "Projekt Zenith Glas", Icons.Rounded.Star, Green,
                    listOf(
                        "Projekt Zenith bekommt einen Glas-Modus: Statt Hologramm-Flächen echtes Flüssigglas, das den Gitter-Horizont dahinter biegt und Farben spaltet – grün getönt, mit hellem Rand wie bei Apple.",
                        "Die grünen Linien des HUD liegen im Glas: ein grüner Rand, der beim Antippen aufleuchtet, feine Scanlinien, grünes Licht von unten, Strichmarken oben.",
                        "Auch das Dock wird eine Platte aus grünem Flüssigglas.",
                        "Zu finden dort, wo Projekt Zenith wohnt: in den Illuminati, sobald Projekt Zenith an ist.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "In den Tresor teilen", Icons.Rounded.Share, Green,
                    listOf(
                        "In jeder App unter „Teilen“: ZENITH-Tresor. Fotos, Videos und Dateien gehen direkt verschlüsselt in den Tresor – erst nach deinem Passwort oder Fingerabdruck.",
                        "Danach fragt Android selbst, ob die Originale aus der Galerie gelöscht werden sollen – dann liegen sie nur noch im Tresor.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der Tresor zum Durchwischen", Icons.Rounded.Lock, Green,
                    listOf(
                        "Im Tresor von einem Foto zum nächsten wischen, mit „1 von 12“ oben.",
                        "Diashow: Die Fotos laufen von selbst weiter, immer im Kreis.",
                        "Zoomen mit zwei Fingern oder Doppeltippen – mit einem Finger wird weitergewischt.",
                        "Teilen direkt aus dem Tresor (als Kopie – im Tresor bleibt alles verschlüsselt).",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Neue Widgets", Icons.Rounded.Add, Green,
                    listOf(
                        "ZENITH-Monitor: das Innere des Handys als HUD – Akku mit Temperatur und Spannung, Arbeitsspeicher, Speicher und Laufzeit, live mit grünen Balken.",
                        "Schutzschild: deine Schutz-Punkte als Bogen um das Metall-Z; antippen öffnet den Schutzschild.",
                    ),
                    hearth,
                ),
            ),
        ),
        ChangeRelease(
            version = "19.0",
            name = "ZENITH",
            summary = "Aus OMEGA UI wird ZENITH – die Sonne im höchsten Punkt, auf der neuen Basis ClaudeOS 6.0 „Helios“: ein neuer Name, ein neues Logo, eine neue Farbe, eine neue Uhr. Alles fühlt sich neu an, und die alten Easter Eggs bleiben.",
            sections = listOf(
                ChangeSection(
                    "ZENITH-Schutzschild", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "Neu: der Schutzschild zeigt, wie gut dein Handy geschützt ist – eine Anzeige um das Metall-Z mit Punkten von 0 bis 100.",
                        "Geprüft werden Bildschirmsperre, Alter des Sicherheitsupdates, Entwickleroptionen und USB-Debugging, Verschlüsselung, ZENITH-Tresor, App-Sperre und Sicherung – jeweils mit dem Weg, es zu beheben.",
                        "„Wer darf was“: welche Apps Kamera, Mikrofon, Standort (auch immer), Kontakte, SMS, Anrufliste, Kalender und mehr dürfen – nach Recht gefiltert, ein Tipp führt zur App, um ihr etwas wegzunehmen.",
                        "Der Schutzschild schaut nur nach; ZENITH ändert nichts von selbst. Im ZENITH-Menü, unter Design → ZENITH und unter Datenschutz.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "ZENITH-Tresor", Icons.Rounded.Lock, Green,
                    listOf(
                        "Neu: der ZENITH-Tresor für Fotos, Videos und Dateien – verschlüsselt mit AES-256 und einem Passwort, das nur du kennst. Im ZENITH-Menü, unter Design → ZENITH und unter Datenschutz.",
                        "Jede Datei wird Stück für Stück einzeln versiegelt; auch die Liste, was drin ist, und die Vorschaubilder sind verschlüsselt. Alles bleibt auf dem Handy.",
                        "Originale löschen erst, wenn die verschlüsselte Kopie gelesen und mit dem Original verglichen wurde.",
                        "Auf Wunsch auch mit Fingerabdruck – über den Hardware-Schlüsselspeicher des Handys; das Passwort bleibt der Generalschlüssel.",
                        "Keine Screenshots, unsichtbar in den letzten Apps, sperrt sich beim Verlassen; nach falschen Passwörtern wird gewartet.",
                        "Fotos direkt im Tresor ansehen (mit Zoom), alles andere öffnen oder exportieren.",
                        "Den ganzen Tresor sichern: eine verschlüsselte Datei mit demselben Passwort – für die Cloud oder ein neues Handy.",
                        "Notizen im Tresor: schreiben, ändern, löschen – verschlüsselt wie alles andere.",
                        "Die Tresor-Kamera: Ein Foto geht direkt verschlüsselt in den Tresor und liegt nirgends sonst – nicht in der Galerie.",
                        "Doppeltippen auf den Startbildschirm kann jetzt den Tresor oder den Schutzschild öffnen (Einstellungen → Homescreen → Doppeltippen).",
                        "Wichtig: Wer das Passwort vergisst, verliert den Inhalt. Niemand kann es zurückholen – auch ZENITH nicht.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Sicherung mit Passwort", Icons.Rounded.Lock, Blue,
                    listOf(
                        "Einstellungen sichern (Einstellungen und ZENITH Cloud → Als Datei) geht jetzt auch mit Passwort: die Datei wird mit AES-256 verschlüsselt.",
                        "Beim Wiederherstellen fragt ZENITH nach dem Passwort, wenn die Sicherung eins hat.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Der neue Kern-Vollstrecker", Icons.Rounded.Star, Green,
                    listOf(
                        "Der Z-Angriff ganz neu, näher an Zygardes Kern-Vollstrecker: Es wird dunkel, ein Boden aus Sechsecken leuchtet auf.",
                        "Oben lädt sich ein sechseckiger Kern auf und zieht Lichtpunkte an – dann feuert er einen Strahl auf den Boden.",
                        "Der Strahl brennt ein riesiges Z in den Boden, Strich für Strich, Funken sprühen.",
                        "Das Z glüht weiß auf und bricht aus: grüne Lichtflammen schießen entlang des Z nach oben, Sechseck-Splitter fliegen, eine Schockwelle läuft über den Boden.",
                        "Mit Klopfen bei jedem Strich und einem kräftigen beim Ausbruch – auch systemweit beim Entsperren und im ZENITH-Start.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Projekt Zenith", Icons.Rounded.Star, Green,
                    listOf(
                        "Ein geheimer Modus, der das ganze System futuristisch macht – nur in den Illuminati zu finden und nur mit einem geheimen Passwort einzuschalten (mit Z-Angriff, wenn es stimmt). Ausschalten geht jederzeit.",
                        "Jede Fläche wird ein Hologramm: dunkles Glas mit grünem Hauch, Scanlinien, ein feiner grüner Rahmen mit hellen Klammern an den Ecken und Strichmarken oben.",
                        "Alle App-Icons werden grüne Hologramme, Texte stehen in Computerschrift.",
                        "Der Hintergrund: ein leuchtender Horizont mit einem Gitterboden, darüber das Z als Drahtgitter-Hologramm, Lichtpunkte, Scanlinien.",
                        "Die HUD-Uhr: „ZENITH // ZEIT“, die Zeit groß in Computerziffern mit grünem Glühen, die Sekunden daneben, darunter Datum, Akku und Sonnenstand – in Klammern.",
                        "Auf dem Startbildschirm oben eine Anzeige mit Datum und Akku, und ab und zu fährt eine Scanlinie über alles – ohne Klammern in den Ecken.",
                        "Das Dock wird eine Hologramm-Platte.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Hochwertiger", Icons.Rounded.CheckCircle, Green,
                    listOf(
                        "ZENITH Metall ist ruhiger und edler: keine grünen Leuchtflecken mehr, keine Kratzer – eine feine silberne Linie oben, eine einzige klare grüne Linie unten, die zu den Enden ausläuft; kleine Knöpfe und Chips nur mit der silbernen Linie.",
                        "Licht fällt von oben auf das Metall, ein feiner Glanz liegt schräg darüber; angetippt glüht es grün.",
                        "Nur noch Grün und Silber: kein Gold und Orange mehr in Rändern, Linien und Zeichen; das Z ist überall aus Chrom.",
                        "Das ZENITH-Dock ist glatter, ohne Kratzer und Flecken.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Hotfixes", Icons.Rounded.Refresh, Blue,
                    listOf(
                        "Das Software-Update findet jetzt auch Hotfixes: gleiche Version, neuere Build-Nummer – „Hotfix verfügbar (Build …)“, herunterladen und installieren wie ein Update.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Z-Angriff", Icons.Rounded.Star, Green,
                    listOf(
                        "Das Z erscheint wie Zygardes Kern-Vollstrecker: Grüne Energiestrahlen ziehen ein riesiges Z über den Bildschirm, Strich für Strich, Funken fliegen – das Z blitzt weiß auf und zerbirst in Ringen aus grünem Licht. Mit Klopfen bei jedem Strich.",
                        "Es passiert, wenn ein ZENITH-Z voll geladen ist: das Logo, das Z an Seitenpunkten, Suchleiste und Now Brief, das ZENITH-Z-Widget, das Z im ZENITH-Menü und in den Illuminati.",
                        "Systemweit: Auf Wunsch zieht das Z bei jedem Entsperren über allen Apps (Design → ZENITH → Z-Angriff systemweit, oder in den Illuminati) – über Glimmer.",
                        "Der ZENITH-Start nach dem Neustart beginnt jetzt mit dem Z-Angriff, aus dessen Licht das Metall-Z entsteht.",
                        "Wo die Version steht – Über das Telefon und Software-Update – zeigt ZENITH sie wie ColorOS 17: das Metall-Z groß über die ganze Breite. In den Einstellungen ist das Z oben größer.",
                        "Die 19 ist ins Z eingebaut wie in der ersten Skizze: grün, schräg mit der Diagonale, in das Metall geschnitten und leuchtend – im Logo überall im System.",
                        "Das Z im Z-Angriff ist breiter: fast von Rand zu Rand, mit dickeren Strahlen.",
                        "Clawd · ZENITH: In ZENITH Metall ist Claudes Fenster eine Platte aus ZENITHs Metall mit abgeschnittenen Ecken, grüner Kante und dem Z im Hintergrund; alles Orange wird Zenith-Grün.",
                        "ZENITH Illuminati: grünes Licht statt Gold, zwischen den Augen das Z, darüber das Metall-Z des Ordens – „Novus Ordo Zenithorum“.",
                        "Icons aus Icon-Packs bringen ihre eigene Kachel mit und stehen wieder so da – ohne doppelten Rahmen.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Ein neues Produkt", Icons.Rounded.Star, Green,
                    listOf(
                        "ZENITHs eigene Form: Jede Fläche aus Metall – Widgets, Karten, App-Icons, Menüs, Knöpfe, Einstellungen – hat jetzt zwei schräg abgeschnittene Ecken, oben links und unten rechts, wie die Enden der Balken im Z.",
                        "ZENITHs eigener Hintergrund: fast schwarz mit einem Hauch Grün, gebürstetes Metall, groß das Metall-Z mit seinem grünen Licht und grünes Licht am unteren Rand – das Metall darüber verschwimmt mit ihm. Abschaltbar unter Design → ZENITH → ZENITH-Hintergrund.",
                        "ZENITH-Start: Nach jedem Neustart des Handys kommt das Metall-Z aus dem Dunkel, seine grünen Kanten leuchten nacheinander auf, dann der Startbildschirm. (Die echte Boot-Animation von Samsung lässt sich ohne Root nicht ändern.)",
                        "Das ZENITH-Dock ist flacher, so breit wie zuvor.",
                        "Die Ω-Ziffern bleiben: In den Illuminati wird jede Null in den Uhren weiter zum Ω.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "Das Z zum Anfassen", Icons.Rounded.Favorite, Green,
                    listOf(
                        "Das ZENITH-Z antwortet jetzt überall: Drückst du es, sammelt sich grünes Licht dahinter und es wächst; kurz tippen macht eins, halten, bis es geladen ist – ein Klopfen und eine grüne Welle in Form des Z –, macht etwas anderes.",
                        "Das Z vor den Seitenpunkten: tippen öffnet das ZENITH-Menü, halten Claude.",
                        "Das Z an der Suchleiste: tippen öffnet Claude, halten das ZENITH-Menü.",
                        "Das Z im Now Brief: tippen öffnet das ZENITH-Menü, halten Claude.",
                        "Das ZENITH-Menü hat oben das Metall-Z: halten, bis es geladen ist, öffnet die geheimen Codes; 19-mal tippen die Clawd Illuminati.",
                        "Neues Widget „ZENITH-Z“: das Metall-Z groß auf dem Startbildschirm – sein grünes Licht brennt so stark, wie der Akku voll ist; antippen lädt es auf, halten öffnet Claude, 19-mal tippen die Illuminati.",
                        "Das ZENITH-Dock ist größer: fast von Rand zu Rand, höher, und die Icons bis zu 78 dp groß – alle gleich.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "ZENITH Metall", Icons.Rounded.Home, Green,
                    listOf(
                        "Schluss mit Glas: Der neue Stil „ZENITH Metall“ macht jede Fläche im System zu dunklem, gebürstetem Metall wie das Logo – Widgets, Now Brief, App-Icons, Menüs, Einstellungen, Suche, App-Drawer, Schalterfelder und Fenster.",
                        "Oben eine silberne Kante, unten brennt ZENITHs grünes Licht an der Kante entlang und in die Ecken hinauf; große Flächen tragen die Kratzer des Metalls, angetippt glüht es grün.",
                        "Statt bunter Farben, die durch Ränder und Karten laufen, steigt grünes Licht von unten auf – wie im Logo.",
                        "Ohne Linsen-Effekt ist ZENITH Metall auch deutlich leichter für das Handy.",
                        "Galaxy × Claude schaltet ZENITH Metall ein; Glas gibt es weiter unter Design → ZENITH → Glas-Stil.",
                        "Das ZENITH-Dock macht alle Icons gleich groß – vorher wurde das letzte (z. B. die Kamera) zusammengedrückt. Icons mit eigener Form sitzen jetzt auch auf einer Metallplatte.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "ZENITH, wie es aussehen soll", Icons.Rounded.Star, Green,
                    listOf(
                        "Ein neues Logo: ein schräges Z aus dunklem, gezeichnetem Metall mit schräg geschnittenen Enden, silberner Kante und grünem Licht an seinen unteren Kanten – darunter „ZENITH“ in weiten Chrom-Buchstaben.",
                        "Gedrückt halten: Das grüne Licht brennt heller und flutet das Metall, ein Lichtband läuft darüber – voll geladen klopft es und eine grüne Welle in Form des Z geht davon aus.",
                        "Alles, was OMEGA hieß, heißt jetzt ZENITH: ZENITH Labs, ZENITH Cloud, ZENITH-Menü, ZENITH-Insel, ZENITH Glass, ZENITH-Uhr, ZENITH-Rand, ZENITH-Abzeichen – und wo „Hearth“ stand, steht ZENITH.",
                        "Die Ω-Zeichen im System sind jetzt das ZENITH-Z: in Menüs, vor den Seitenpunkten, an der Suchleiste, auf dem Sperrbildschirm, als Wasserzeichen, auf Clawds Brust, im Z-Regen und bei der Clawd-Parade. Nur die Ω-Ziffern bleiben: In den Illuminati wird jede Null weiter zum Ω (1Ω:Ω5).",
                        "Ein neuer Startbildschirm: das ZENITH-Dock – eine schräge Platte aus dem Metall des Z mit grünem Licht an ihrem Fuß –, ZENITHs eigenes Licht (dunkler Himmel, ein grüner Strahl entlang der Diagonale, grünes Licht von unten), Seitenstriche schräg wie die Balken des Z und die Uhr in Chrom mit grünem Glühen.",
                        "Der Übergang beim ersten Start zeigt das neue Logo: Aus der Nacht steigt ein grünes Licht, und das Metall-Z leuchtet Kante für Kante auf.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "ClaudeOS 6.0 „Helios“", Icons.Rounded.Build, Orange,
                    listOf(
                        "Ein neues System braucht eine neue Basis: ZENITH läuft auf ClaudeOS 6.0 mit dem Codenamen „Helios“ – nach der Sonne.",
                        "ZENITHs Sonne, die Farben, die dem Tag folgen, und der Sonnenbogen gehören jetzt zum Fundament.",
                        "Ein eigenes Claude-Zeichen in Weiß, Sonnengold und Zenith-Grün.",
                        "Neues ClaudeOS-Easter-Egg: die Sonnenfinsternis – fünfmal auf die ClaudeOS-Version tippen und den Mond von der Sonne schieben.",
                    ),
                    everyone,
                ),
                ChangeSection(
                    "ZENITH", Icons.Rounded.Star, Green,
                    listOf(
                        "OMEGA UI heißt jetzt ZENITH – wie die Sonne, wenn sie im Zenit steht, ganz oben am Himmel.",
                        "Ein neues Logo – in den Einstellungen, unter Über das Telefon, im Software-Update und in der Tour.",
                        "Beim ersten Start: Das Ω verblasst, ein grünes Licht steigt an seinem Bogen aus der Nacht bis in den Zenit – und dort entsteht ZENITH.",
                        "Eine neue Tour durch alles, was neu ist.",
                    ),
                    hearth,
                ),
                ChangeSection(
                    "Logo gedrückt halten", Icons.Rounded.Favorite, Orange,
                    listOf(
                        "Wie bei ColorOS: Hältst du das ZENITH-Logo gedrückt, lädt es sich auf; ist es voll, klopft es spürbar; lässt du los, federt es zurück.",
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
                        "Galaxy × Claude bekommt das ZENITH-Design; wer getöntes Glas in Rubin hatte, wechselt zu Zenith – Rubin und Galaxy Zenith bleiben wählbar.",
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
                        "Das Egg-Museum: Im Software-Update unter Update-Verlauf lange auf eine alte Version drücken – ihr Easter Egg kommt wieder. Rubin, Laterne, Schmiede, Lupe, Glühwürmchen, Tresor, Kometen, Prisma … alle von ${dev.hearth.launcher.data.Brand.name} und ZENITH bleiben.",
                        "Die Ruhmeshalle: lange auf die ZENITH-Version drücken (Software-Update oder Über das Telefon) – jede große Version von Hearth 11 bis ZENITH 19, die ganzen und die .5er, jede mit ihrem Easter Egg.",
                        "Die ClaudeOS-Ruhmeshalle: lange auf die ClaudeOS-Version drücken – alle sechs ClaudeOS-Eggs von „Ember“ bis „Helios“, jedes mit seinem eigenen Namen.",
                        "Rubin, die ZENITH-Insel, ZENITH Cloud, ZENITH Labs, die Clawd Illuminati und Claude Mythos sind weiter da – alles, was OMEGA hieß, heißt jetzt ZENITH.",
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
        // ZENITH: its Z big and faint behind the version (the Ω before).
        if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) {
            ZenithMark(Color.White.copy(alpha = 0.12f), height * 1.25f)
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
                "Egg-Museum: lange auf eine Version drücken – ihr Easter Egg kommt wieder. Lange auf die Systemversion: die Ruhmeshalle; auf die ClaudeOS-Version: die ClaudeOS-Ruhmeshalle.",
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
                                text = "Galaxy × Claude mit Zenith-Grün, Sonnenglas, der Sonnenbogen-Uhr und dem ZENITH-Dock – jederzeit änderbar.",
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
                                TourPoint(Icons.Rounded.Star, Color(0xFF8E6BFF), "Alles, was OMEGA hieß", "heißt jetzt ZENITH: ZENITH Cloud, ZENITH-Insel, ZENITH Labs – dazu Rubin als Farbe, die Illuminati und Claude Mythos."),
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
        ZenithLogo(size = 200.dp, caption = "ZENITH")
        Spacer(Modifier.height(8.dp))
        Text(
            "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
            style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            dev.hearth.launcher.data.ZenithSlogan.UPPER,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 5.sp,
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
