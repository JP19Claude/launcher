package dev.hearth.launcher.clawd

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdOracle
import dev.hearth.launcher.data.ClawdPet
import dev.hearth.launcher.data.ClawdTalk
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.clawdAsleep
import dev.hearth.launcher.data.clawdGreeting
import dev.hearth.launcher.ui.Clawd
import dev.hearth.launcher.ui.ClawdSettings
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.theme.HearthTheme
import java.time.LocalDateTime

/**
 * The Clawd app: talk to him, look after him (his pet side), put his widgets on the home
 * screen of any launcher, and dress him up. With Hearth, his look comes from Hearth.
 */
class ClawdAppActivity : ComponentActivity() {

    private var askFocus by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        if (intent.getBooleanExtra(EXTRA_ASK, false)) askFocus++
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalSettings provides settings) {
                HearthTheme(dark = true) {
                    ClawdAppScreen(
                        settings = settings,
                        askFocus = askFocus,
                        update = { change ->
                            repo.update(change)
                            ClawdWidgetProvider.refreshAll(this)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ASK, false)) askFocus++
    }

    companion object {
        const val EXTRA_ASK = "ask"
    }
}

/** One of Clawd's widgets in the gallery: its name, what it does, and Clawd in its pose. */
private class WidgetEntry(val provider: Class<out ClawdWidgetProvider>, val title: String, val text: String, val mood: ClawdMood)

private val Entries = listOf(
    WidgetEntry(ClawdPictureWidget::class.java, "Clawd-Bild", "Clawd als Bild, sechs Hintergründe über ✦, antippen für die nächste Pose", ClawdMood.Wave),
    WidgetEntry(ClawdClockWidget::class.java, "Clawd-Uhr", "Uhrzeit und Datum mit Clawd und Gruß, nachts schläft er", ClawdMood.Idle),
    WidgetEntry(ClawdAskWidget::class.java, "Frag Clawd", "Ein Tipp und du sprichst mit ihm", ClawdMood.Thinking),
    WidgetEntry(ClawdPetWidget::class.java, "Clawd-Tamagotchi", "Füttern, spielen, streicheln – er wird hungrig", ClawdMood.Love),
    WidgetEntry(ClawdJokeWidget::class.java, "Clawd-Witz", "Jeden Tag ein Witz, antippen für den nächsten", ClawdMood.Dance),
    WidgetEntry(ClawdBatteryWidget::class.java, "Clawd-Akku", "Tanzt beim Laden, wird müde bei leerem Akku", ClawdMood.Sleep),
    WidgetEntry(ClawdOracleWidget::class.java, "Clawd-Orakel", "Ja/Nein-Frage denken, antippen, Antwort lesen", ClawdMood.Flip),
    WidgetEntry(ClawdStickerWidget::class.java, "Clawd-Sticker", "Nur er, 1×1, antippen für die nächste Pose", ClawdMood.Wave),
    WidgetEntry(ClawdFashionWidget::class.java, "Clawd-Modenschau", "Er probiert Outfits und Hüte an, antippen für den nächsten Look", ClawdMood.Flip),
    WidgetEntry(ClawdWaterWidget::class.java, "Clawd-Wasser", "Gläser Wasser heute, antippen für eins mehr", ClawdMood.Love),
    WidgetEntry(ClawdDiceWidget::class.java, "Clawd-Würfel", "Antippen und er würfelt", ClawdMood.Dance),
    WidgetEntry(ClawdMotivationWidget::class.java, "Clawd-Motivation", "Jeden Tag ein liebes Wort", ClawdMood.Wave),
    WidgetEntry(ClawdWeekendWidget::class.java, "Clawd-Wochenende", "Wie lange noch bis zum Wochenende", ClawdMood.Thinking),
    WidgetEntry(ClawdWorldWidget::class.java, "Clawd-Welt", "Er in seiner Pixel-Landschaft mit Tag und Nacht, antippen und er läuft woanders hin", ClawdMood.Thinking),
    WidgetEntry(ClawdFocusWidget::class.java, "Clawd-Fokus", "25-Minuten-Timer, Clawd arbeitet mit und feiert am Ende", ClawdMood.Thinking),
    WidgetEntry(ClawdGameWidget::class.java, "Clawd-Spiel", "Clawd Jump mit einem Tipp starten, Rekord im Blick", ClawdMood.Dance),
    WidgetEntry(ClawdBadgesWidget::class.java, "Clawd-Abzeichen", "Deine gesammelten Abzeichen und das nächste Ziel", ClawdMood.Love),
    WidgetEntry(ClawdDiaryWidget::class.java, "Clawd-Tagebuch", "Ein Gesicht pro Tag, antippen für das nächste – die Woche im Blick", ClawdMood.Love),
    WidgetEntry(ClawdCoinWidget::class.java, "Clawd-Münze", "Kopf oder Zahl, antippen und er wirft", ClawdMood.Flip),
    WidgetEntry(ClawdBreathWidget::class.java, "Clawd-Atmen", "Atmet ruhig mit dir: 4 Sekunden ein, 4 aus", ClawdMood.Idle),
    WidgetEntry(ClawdCountdownWidget::class.java, "Clawd-Countdown", "Wie lange noch bis Weihnachten, Silvester, Ostern … – antippen für den nächsten", ClawdMood.Wave),
    WidgetEntry(ClawdRiddleWidget::class.java, "Clawd-Rätsel", "Antippen für die Lösung, nochmal für das nächste Rätsel", ClawdMood.Thinking),
    WidgetEntry(ClawdRpsWidget::class.java, "Schnick-Schnack-Schnuck", "Schere, Stein, Papier gegen Clawd, mit Punktestand", ClawdMood.Dance),
)

private val Terracotta = Color(0xFFD97757)

@Composable
private fun ClawdAppScreen(
    settings: LauncherSettings,
    askFocus: Int,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    val context = LocalContext.current
    val hour = remember { LocalDateTime.now().hour }
    var said by remember { mutableStateOf(clawdGreeting(hour) + " Ich bin Clawd. Sprich mit mir!") }
    var mood by remember { mutableStateOf(if (clawdAsleep(hour)) ClawdMood.Sleep else ClawdMood.Wave) }
    var input by remember { mutableStateOf("") }
    var taps by remember { mutableIntStateOf(0) }
    val tapCounter = remember { dev.hearth.launcher.data.ClawdTapCounter() }
    var pet by remember { mutableStateOf(ClawdPet.state(context)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(askFocus) { if (askFocus > 0) runCatching { focus.requestFocus() } }

    fun say(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        input = ""
        val answer = ClawdTalk.reply(q)
        if (answer != null) {
            said = answer.text
            mood = answer.mood ?: ClawdMood.Wave
            answer.egg?.let { dev.hearth.launcher.data.EasterEggs.find(context, it) }
            if (answer.game) dev.hearth.launcher.data.ClawdGameScore.open(context)
            return
        }
        if (q.lowercase().startsWith("orakel")) {
            said = ClawdOracle.ask()
            mood = ClawdMood.Thinking
            return
        }
        // Everything else goes to Claude: Hearth's assistant if it's there, else the Claude app.
        mood = ClawdMood.Thinking
        val inHearth = ClaudeAssistant.openAssistant(context, q)
        said = if (inHearth) "Ich frag das Claude in Hearth für dich …" else "Ich geb das an die Claude-App weiter …"
        if (!inHearth) ClaudeAssistant.openClaudeApp(context, q)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1F1A), Color(0xFF141216), Color(0xFF0E0D10)))),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding().imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Clawd, big, with what he just said.
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Clawd(
                        Modifier
                            .size(width = 220.dp, height = 190.dp)
                            .clickable {
                                taps++
                                val reaction = if (tapCounter.tap() >= 6) {
                                    dev.hearth.launcher.data.ClawdReactions.dizzy
                                } else {
                                    dev.hearth.launcher.data.ClawdReactions.tap()
                                }
                                mood = reaction.mood
                                said = reaction.line
                            },
                        mood = mood,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        said,
                        color = Color(0xFF2B2A27),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFAF9F5))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            // Talking to him.
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) Text("Sag was zu Clawd …", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Terracotta),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { say(input) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Terracotta)
                            .clickable { say(input) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Hallo!", "Erzähl einen Witz", "Lass uns spielen", "Erzähl mir was", "Tanz mal", "Wie spät ist es?", "Was kannst du?", "Orakel: Wird heute gut?", "Ich hab dich lieb")) { chip ->
                        Text(
                            chip,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Terracotta.copy(alpha = 0.28f))
                                .clickable { say(chip) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // His game.
            item {
                Card("Clawd Jump") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Spring über die Bugs, sammle Herzen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Rekord: ${dev.hearth.launcher.data.ClawdGameScore.best(context)}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                            )
                        }
                        Button("▶ Spielen") { dev.hearth.launcher.data.ClawdGameScore.open(context) }
                    }
                }
            }

            // His pet side.
            item {
                Card("Clawd-Tamagotchi") {
                    Text("Level ${pet.level} · ${pet.status}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Bar("Satt", pet.food, Color(0xFFFFB04A))
                    Spacer(Modifier.height(6.dp))
                    Bar("Laune", pet.joy, Color(0xFFFF6FA8))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button("🍕 Füttern", Modifier.weight(1f)) {
                            pet = ClawdPet.feed(context)
                            mood = ClawdMood.Dance
                            said = "Mmmh, lecker! Danke!"
                            ClawdWidgetProvider.refreshAll(context)
                        }
                        Button("⚽ Spielen", Modifier.weight(1f)) {
                            pet = ClawdPet.play(context)
                            mood = ClawdMood.Flip
                            said = "Juhu! Nochmal!"
                            ClawdWidgetProvider.refreshAll(context)
                        }
                        Button("🤚 Streicheln", Modifier.weight(1f)) {
                            pet = ClawdPet.pet(context)
                            mood = ClawdMood.Love
                            said = "Aww 🧡"
                            ClawdWidgetProvider.refreshAll(context)
                        }
                    }
                }
            }

            // His widgets for the home screen, whichever launcher it is.
            item {
                Column {
                Text(
                    "Widgets für deinen Startbildschirm",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                Text(
                    "Funktionieren mit jedem Launcher, auch mit dem von Samsung. In Hearth sind sie schon eingebaut.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
                }
            }
            items(Entries, key = { it.provider.name }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Clawd(Modifier.size(width = 52.dp, height = 46.dp), mood = entry.mood)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(entry.text, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button("Hinzufügen") { pin(context, entry.provider) }
                }
            }

            // How he looks.
            item {
                Card(null) {
                    ClawdSettings(settings, update, inLauncher = false)
                    Text(
                        "Mit Hearth übernimmt Clawd Farbe und Hut aus Hearth – dort kannst du ihn auch über den Startbildschirm laufen lassen.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Asks the launcher to place the widget (Samsung's, Pixel's … can); otherwise explains how. */
private fun pin(context: android.content.Context, provider: Class<out ClawdWidgetProvider>) {
    val manager = AppWidgetManager.getInstance(context)
    val asked = runCatching {
        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }.getOrDefault(false)
    if (!asked) {
        Toast.makeText(context, "Halte den Startbildschirm gedrückt → Widgets → Clawd", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Card(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(if (title != null) 18.dp else 4.dp),
    ) {
        if (title != null) {
            Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

@Composable
private fun Bar(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0, 100) / 100f)
                    .height(10.dp)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Button(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Terracotta.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
