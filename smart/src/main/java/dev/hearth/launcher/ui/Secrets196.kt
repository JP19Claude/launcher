package dev.hearth.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.StrikeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 19.6: seven more hidden menus behind codes typed into the finder – a terminal, the rain of
 * characters, a snake, painting with light, an oracle, a reflex test and the credits. All in the
 * list at *#0000#.
 */

/** ZENITH 19.6: the way to a hidden menu from somewhere in the system (a long press, quick taps, the clock). */
internal object SecretRoutes {
    fun open(context: Context, menu: SecretMenu) {
        runCatching {
            context.startActivity(
                Intent(context, dev.hearth.launcher.SecretActivity::class.java)
                    .putExtra(dev.hearth.launcher.SecretActivity.EXTRA, menu.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun version(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

// ---------------------------------------------------------------------------------------------
// The rain of characters
// ---------------------------------------------------------------------------------------------

private const val RainGlyphs = "ZENITH0123456789ΩΣΔΞΨ<>/=+*#"

/** Characters falling in columns, [color] bright at the head and fading behind it. */
@Composable
internal fun MatrixRain(color: Color, modifier: Modifier = Modifier, speed: Float = 1f) {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { time = it } }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(modifier) {
        val cell = 18.dp.toPx()
        paint.textSize = cell
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val tick = (time / 140L).toInt()
        drawIntoCanvas { canvas ->
            for (col in 0 until cols) {
                val seed = col * 7919L
                val length = 8 + ((seed * 31) % 14).toInt()
                val pace = (0.5f + ((seed * 17) % 100) / 100f) * speed
                val head = ((time / 1000f * 9f * pace + (seed % 97)) % (rows + length)).toInt()
                for (k in 0 until length) {
                    val row = head - k
                    if (row < 0 || row > rows) continue
                    val fade = 1f - k / length.toFloat()
                    paint.color = (if (k == 0) Color.White else color).copy(alpha = fade * 0.9f).toArgb()
                    val glyph = RainGlyphs[(col * 31 + row * 17 + (if (k % 3 == 0) tick else 0)).mod(RainGlyphs.length)]
                    canvas.nativeCanvas.drawText(glyph.toString(), col * cell, row * cell, paint)
                }
            }
        }
    }
}

/** *#6366#: the rain, in the Z-Angriff's colors; a tap changes the pill. */
@Composable
internal fun NeonMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "neon") }
    val colors = StrikeColor.entries
    var pick by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) }
    val lines = remember { listOf("Wach auf …", "Das Z hat dich.", "Folge dem weißen Kaninchen.", "Tipp aufs Bild, um die Pille zu wechseln.") }
    LaunchedEffect(Unit) {
        for (i in 1 until lines.size) {
            delay(2800)
            phase = i
        }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color.Black, Color(0xFF020A05))),
        "Neon",
        "Tippe, um die Farbe zu wechseln · ${colors[pick].label}",
    ) {
        MatrixRain(
            colors[pick].color,
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null) { pick = (pick + 1) % colors.size },
        )
        Text(
            lines[phase],
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 30.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// *#8376# – the terminal
// ---------------------------------------------------------------------------------------------

private val ConsoleFortunes = listOf(
    "Ein Launcher, der Easter Eggs versteckt, ist nie nur ein Launcher.",
    "Es gibt 10 Arten von Menschen: die, die Binär verstehen, und die anderen.",
    "Wer *#0000# tippt, findet mehr.",
    "Das Z ist der letzte Buchstabe. Und der erste, den man sieht.",
    "Clawd sagt: Pausen sind auch Commits.",
    "Kein Bug, ein Feature mit Charakter.",
)

private val ConsoleClawd = listOf(
    " ▐▛███▜▌",
    "▝▜█████▛▘",
    "  ▘▘ ▝▝",
)

private fun uptimeText(millis: Long): String {
    val minutes = millis / 60_000
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    return (if (days > 0) "$days T " else "") + "$hours Std ${minutes % 60} Min"
}

/**
 * The Z-Konsole: a terminal that answers a few commands – and a few that aren't in its list.
 * `help` says which.
 */
@Composable
internal fun ZConsole(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "console") }
    val lines = remember { mutableStateListOf("ZENITH Z-Konsole · tippe help", "") }
    var input by remember { mutableStateOf("") }
    var strike by remember { mutableStateOf(false) }
    var rain by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(lines.size) {
        delay(40)
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun execute(raw: String) {
        val text = raw.trim()
        lines.add("❯ $text")
        val parts = text.split(' ').filter { it.isNotEmpty() }
        val head = parts.firstOrNull()?.lowercase().orEmpty()
        when {
            text.isEmpty() -> Unit
            head == "help" -> lines.addAll(
                listOf(
                    "help  status  version  whoami  eggs  date  ls  cat  fortune",
                    "echo  strike  matrix  clear  exit",
                    "… und ein paar, die nicht in der Liste stehen.",
                ),
            )
            head == "status" -> {
                val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
                val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
                val memory = ActivityManager.MemoryInfo()
                runCatching { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory) }
                val usedMb = (memory.totalMem - memory.availMem) / 1_048_576L
                val totalMb = memory.totalMem / 1_048_576L
                lines.add("akku     ${if (level < 0) "?" else "${level * 100 / scale} %"}")
                lines.add("speicher $usedMb / $totalMb MB")
                lines.add("läuft    ${uptimeText(SystemClock.elapsedRealtime())}")
            }
            head == "version" -> lines.add("ZENITH ${version(context)} · ${ClaudeOs.full} „${ClaudeOs.CODENAME}“")
            head == "whoami" -> lines.add("du – und Clawd schaut zu.")
            head == "eggs" -> lines.add("${EasterEggs.found.value.size} von ${EasterEggs.TOTAL} Eggs gefunden. Der Rest versteckt sich gut.")
            head == "date" -> lines.add(SimpleDateFormat("EEEE, d. MMMM yyyy · HH:mm:ss", Locale.GERMAN).format(Date()))
            head == "ls" -> lines.add("eggs  geheim.txt  tresor/  z-angriff  kamin")
            head == "cat" -> lines.add(
                if (parts.getOrNull(1) == "geheim.txt") "Keine Geheimnisse hier. Die richtigen findest du mit Suchen." else "cat: ${parts.getOrNull(1) ?: "?"}: Datei nicht gefunden",
            )
            head == "fortune" -> lines.add(ConsoleFortunes.random())
            head == "echo" -> lines.add(text.removePrefix(parts[0]).trim())
            head == "strike" -> {
                lines.add("Z-Angriff …")
                strike = true
            }
            head == "matrix" -> {
                rain = !rain
                lines.add(if (rain) "Folge dem weißen Kaninchen." else "Zurück in die Wirklichkeit.")
            }
            head == "clear" -> lines.clear()
            head == "exit" -> onClose()
            head == "sudo" -> {
                lines.addAll(listOf("[sudo] Passwort für du: ••••••", "du ist nicht in der sudoers-Datei. Dieser Vorfall wird gemeldet.", "(Clawd wurde benachrichtigt. Er lacht.)"))
                EasterEggs.find(context, "sudo")
            }
            head == "rm" -> lines.add("Dafür bin ich zu jung. Und zu grün.")
            head == "hearth" -> lines.add("Hearth · 11 – 15.5 · ein Kamin voller Funken. Such im Finder nach dem Wort.")
            head == "omega" -> lines.add("Ω · 16 – 18.5 · das letzte Zeichen des Alphabets – und trotzdem nicht das Ende.")
            head == "zenith" -> lines.add("ZENITH · der höchste Punkt. Von da geht es nur noch um die Aussicht.")
            head == "clawd" -> lines.addAll(ConsoleClawd)
            else -> lines.add("zsh: command not found: $head · tippe help")
        }
    }

    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF020604))
            .clickable(remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() },
    ) {
        if (rain) MatrixRain(HudGreen.copy(alpha = 0.5f), Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                lines.forEach { line ->
                    Text(
                        line,
                        color = if (line.startsWith("❯")) HudGreen else Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❯ ", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(HudGreen),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val typed = input
                            input = ""
                            execute(typed)
                        },
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        }
        CloseDot(onClose, Modifier.align(Alignment.TopEnd))
        if (strike) ZenithStrikeOverlay { strike = false }
    }
}

/** The round close button at the top corner of a hidden menu. */
@Composable
private fun CloseDot(onClose: () -> Unit, modifier: Modifier = Modifier) {
    GlassCircle(
        onClick = onClose,
        modifier = modifier.statusBarsPadding().padding(12.dp),
        size = 44.dp,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
    }
}

// ---------------------------------------------------------------------------------------------
// *#7625# – the snake
// ---------------------------------------------------------------------------------------------

/**
 * The Funken-Schlange: swipe to steer, eat the sparks, get long. Walls and your own tail end it.
 * Twelve sparks is an egg.
 */
@Composable
internal fun SnakeMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val cols = 16
    val rows = 22
    val prefs = remember { context.getSharedPreferences("hearth_secret", Context.MODE_PRIVATE) }
    var best by remember { mutableIntStateOf(prefs.getInt("snakeBest", 0)) }
    var snake by remember { mutableStateOf(listOf(8 to 11, 7 to 11, 6 to 11)) }
    var dir by remember { mutableStateOf(1 to 0) }
    var turn by remember { mutableStateOf(1 to 0) }
    var food by remember { mutableStateOf(12 to 11) }
    var alive by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val score = snake.size - 3
    LaunchedEffect(Unit) { EasterEggs.find(context, "snake") }
    LaunchedEffect(round) {
        snake = listOf(8 to 11, 7 to 11, 6 to 11)
        dir = 1 to 0
        turn = 1 to 0
        food = 12 to 11
        alive = true
        started = false
        while (!started) delay(50)
        while (alive) {
            delay(maxOf(70L, 160L - (snake.size - 3) * 5L))
            dir = turn
            val head = snake.first()
            val next = (head.first + dir.first) to (head.second + dir.second)
            val ate = next == food
            val body = if (ate) snake else snake.dropLast(1)
            if (next.first !in 0 until cols || next.second !in 0 until rows || next in body) {
                alive = false
                val result = snake.size - 3
                if (result > best) {
                    best = result
                    prefs.edit().putInt("snakeBest", result).apply()
                }
                break
            }
            snake = listOf(next) + body
            if (ate) {
                var spot: Pair<Int, Int>
                do {
                    spot = Random.nextInt(cols) to Random.nextInt(rows)
                } while (spot in snake)
                food = spot
                if (snake.size - 3 >= 12) EasterEggs.find(context, "snake12")
            }
        }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF0B0604), Color(0xFF1A0E08), Color(0xFF080403))),
        "Funken-Schlange",
        when {
            !alive -> "Aus! $score Funken · Bestwert $best – tippe für eine neue Runde"
            !started -> "Wische, um loszulegen · Bestwert $best"
            else -> "$score Funken · Bestwert $best"
        },
    ) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 18.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(cols / rows.toFloat())
                    .pointerInput(Unit) {
                        var drag = Offset.Zero
                        detectDragGestures(onDragStart = { drag = Offset.Zero }) { change, delta ->
                            change.consume()
                            drag += delta
                            if (drag.getDistance() > 24.dp.toPx()) {
                                val horizontal = abs(drag.x) > abs(drag.y)
                                val want = if (horizontal) {
                                    if (drag.x > 0f) 1 to 0 else -1 to 0
                                } else {
                                    if (drag.y > 0f) 0 to 1 else 0 to -1
                                }
                                if (want.first != -dir.first || want.second != -dir.second) turn = want
                                started = true
                                drag = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(alive) { detectTapGestures { if (!alive) round++ } },
            ) {
                val cell = size.width / cols
                drawRoundRect(Color.White.copy(alpha = 0.05f), Offset.Zero, size, CornerRadius(14.dp.toPx()))
                for (x in 0 until cols) {
                    for (y in 0 until rows) {
                        if ((x + y) % 2 == 0) drawRect(Color.White.copy(alpha = 0.025f), Offset(x * cell, y * cell), Size(cell, cell))
                    }
                }
                // The spark.
                val f = Offset((food.first + 0.5f) * cell, (food.second + 0.5f) * cell)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.6f), Color.Transparent), center = f, radius = cell * 1.2f), cell * 1.2f, f)
                drawCircle(Color(0xFFFFD34D), cell * 0.32f, f)
                // The snake: bright head, a body that cools towards the tail.
                snake.forEachIndexed { i, part ->
                    val t = i / snake.size.toFloat()
                    val color = if (i == 0) Color(0xFFFFF1B0) else snakeShade(Color(0xFFFF9A3D), Color(0xFF8E2A10), t)
                    drawRoundRect(
                        color.copy(alpha = if (alive) 1f else 0.5f),
                        Offset(part.first * cell + cell * 0.08f, part.second * cell + cell * 0.08f),
                        Size(cell * 0.84f, cell * 0.84f),
                        CornerRadius(cell * 0.3f),
                    )
                }
            }
        }
    }
}

private fun snakeShade(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)

// ---------------------------------------------------------------------------------------------
// *#3729# – painting with light
// ---------------------------------------------------------------------------------------------

private class LightStroke(val color: Color, val points: MutableList<Offset>, var last: Long)

/** Lichtmalerei: draw with a finger, and the light fades slowly. The dots change its color. */
@Composable
internal fun LightPaintMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        EasterEggs.find(context, "draw")
        while (true) withFrameMillis { now = it }
    }
    val strokes = remember { mutableStateListOf<LightStroke>() }
    val colors = StrikeColor.entries
    var pick by remember { mutableIntStateOf(0) }
    LaunchedEffect(now / 500L) { strokes.removeAll { now - it.last > 9000L } }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF020306), Color(0xFF05060C))),
        "Lichtmalerei",
        "Mal mit dem Finger – das Licht verblasst langsam",
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at -> strokes.add(LightStroke(colors[pick].color, mutableListOf(at), now)) },
                    ) { change, _ ->
                        change.consume()
                        strokes.lastOrNull()?.let {
                            it.points.add(change.position)
                            it.last = now
                        }
                    }
                },
        ) {
            val frame = now
            strokes.toList().forEach { s ->
                val alpha = (1f - (frame - s.last) / 7000f).coerceIn(0f, 1f)
                if (alpha <= 0f || s.points.isEmpty()) return@forEach
                val path = Path()
                val first = s.points.first()
                path.moveTo(first.x, first.y)
                for (i in 1 until s.points.size) {
                    val prev = s.points[i - 1]
                    val cur = s.points[i]
                    path.quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                }
                val tip = s.points.last()
                path.lineTo(tip.x, tip.y)
                drawPath(path, s.color.copy(alpha = 0.22f * alpha), style = Stroke(30.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, s.color.copy(alpha = 0.55f * alpha), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, Color.White.copy(alpha = 0.9f * alpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                if (s.points.size == 1) drawCircle(Color.White.copy(alpha = alpha), 6.dp.toPx(), first)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(if (i == pick) 34.dp else 26.dp)
                        .clip(CircleShape)
                        .background(c.color)
                        .border(BorderStroke(2.dp, Color.White.copy(alpha = if (i == pick) 0.95f else 0f)), CircleShape)
                        .clickable { pick = i },
                )
            }
            GlassCapsule(onClick = { strokes.clear() }) {
                Text("Löschen", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#6725# – the oracle
// ---------------------------------------------------------------------------------------------

private val OracleAnswers = listOf(
    "Ja – ohne Zweifel.",
    "Auf jeden Fall.",
    "Die Zeichen stehen auf Grün.",
    "Sehr wahrscheinlich.",
    "Frag Clawd. Er weiß mehr.",
    "Vielleicht. Ich denke noch nach.",
    "Konzentrier dich und frag nochmal.",
    "Das verrate ich dir jetzt lieber nicht.",
    "Eher nicht.",
    "Meine Quellen sagen nein.",
    "Sehr zweifelhaft.",
    "Nein. Aber du schaffst das trotzdem.",
    "Im Zenit des Möglichen: ja.",
    "Alles deutet auf Ω – Ende gut, alles gut.",
    "Das Orakel schläft. Tippe nochmal.",
)

/** Das Ω-Orakel: think of a yes-or-no question, tap the orb, get an answer. Seven questions is an egg. */
@Composable
internal fun OracleMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<String?>(null) }
    var asks by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        EasterEggs.find(context, "oracle")
        while (true) withFrameMillis { now = it }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF05040C), Color(0xFF140A2A), Color(0xFF030305))),
        "Ω-Orakel",
        "Stell eine Ja-Nein-Frage und tippe die Kugel · $asks ${if (asks == 1) "Frage" else "Fragen"}",
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (!busy) {
                            busy = true
                            answer = null
                            scope.launch {
                                spin.snapTo(0f)
                                spin.animateTo(1f, tween(1100))
                                answer = OracleAnswers.random()
                                asks++
                                busy = false
                                if (asks >= 7) EasterEggs.find(context, "oracle7")
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = size.minDimension / 2f * (0.9f + 0.03f * sin(now / 400f))
                    drawCircle(Brush.radialGradient(listOf(OmegaColors[2].copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 1.45f), r * 1.45f, c)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFF2E1B5C), Color(0xFF0A0618)),
                            center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                            radius = r * 1.3f,
                        ),
                        r,
                        c,
                    )
                    rotate(spin.value * 720f + now / 40f, c) {
                        for (k in 0 until 3) {
                            drawArc(
                                OmegaColors[k].copy(alpha = 0.55f),
                                k * 120f,
                                70f,
                                false,
                                Offset(c.x - r * 0.72f, c.y - r * 0.72f),
                                Size(r * 1.44f, r * 1.44f),
                                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                    drawCircle(Color.White.copy(alpha = 0.16f), r * 0.22f, Offset(c.x - r * 0.42f, c.y - r * 0.48f))
                }
                OmegaMark(84, Modifier.graphicsLayer { alpha = 0.95f - 0.6f * sin(spin.value * 3.1416f) })
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.height(72.dp).padding(horizontal = 28.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    answer ?: if (busy) "…" else "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#9277# – the reflex test
// ---------------------------------------------------------------------------------------------

/** Der Blitz-Test: wait for green, then tap as fast as you can. Under 250 ms is an egg. */
@Composable
internal fun ZappMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hearth_secret", Context.MODE_PRIVATE) }
    // 0 idle, 1 waiting, 2 go, 3 result, 4 too early
    var phase by remember { mutableIntStateOf(0) }
    var started by remember { mutableLongStateOf(0L) }
    var ms by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("zappBest", 0)) }
    LaunchedEffect(Unit) { EasterEggs.find(context, "zapp") }
    LaunchedEffect(phase) {
        if (phase == 1) {
            delay(1500L + Random.nextLong(2500L))
            started = SystemClock.elapsedRealtime()
            phase = 2
        }
    }
    val tint = when (phase) {
        1 -> Color(0xFF4A0F12)
        2 -> Color(0xFF0E7A40)
        4 -> Color(0xFF4A3306)
        else -> Color(0xFF0B1019)
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.85f))),
        "Blitz-Test",
        "Tippe, sobald es grün wird · Bestzeit ${if (best > 0) "$best ms" else "–"}",
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    when (phase) {
                        1 -> phase = 4
                        2 -> {
                            ms = (SystemClock.elapsedRealtime() - started).toInt()
                            phase = 3
                            if (best == 0 || ms < best) {
                                best = ms
                                prefs.edit().putInt("zappBest", ms).apply()
                            }
                            if (ms < 250) EasterEggs.find(context, "zapp250")
                        }
                        else -> phase = 1
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (phase) {
                        1 -> "Warte …"
                        2 -> "JETZT!"
                        3 -> "$ms ms"
                        4 -> "Zu früh!"
                        else -> "Bereit?"
                    },
                    color = Color.White,
                    fontSize = if (phase == 2) 64.sp else 44.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    when (phase) {
                        3 -> if (ms < 250) "Blitzschnell. ⚡" else if (ms < 400) "Ordentlich. Nochmal?" else "Noch ein bisschen Kaffee? Tippe für eine neue Runde"
                        4 -> "Tippe für eine neue Runde"
                        0 -> "Tippe zum Starten"
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#2733# – the credits
// ---------------------------------------------------------------------------------------------

@Composable
private fun CreditHeading(text: String) {
    Text(
        text.uppercase(),
        color = ZenithGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 44.dp, bottom = 10.dp),
    )
}

@Composable
private fun CreditLine(text: String, big: Boolean = false) {
    Text(
        text,
        color = Color.White.copy(alpha = if (big) 1f else 0.82f),
        fontSize = if (big) 24.sp else 16.sp,
        fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

/** Der Abspann: every era of the launcher, who built it, and how many eggs you have found. */
@Composable
internal fun CreditsMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "credits") }
    val found by EasterEggs.found.collectAsState()
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        delay(800)
        scroll.animateScrollTo(scroll.maxValue, tween(75_000, easing = LinearEasing))
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF020504), Color(0xFF05130C), Color(0xFF020504))),
        "Abspann",
        "Wische hoch oder lehn dich zurück",
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screen = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(screen))
                ZenithMark(color = ZenithGreen, width = 110.dp)
                Spacer(Modifier.height(14.dp))
                CreditLine("ZENITH", big = true)
                CreditLine("${version(context)} · ${ClaudeOs.full} „${ClaudeOs.CODENAME}“")
                CreditHeading("Gebaut von")
                CreditLine("Julius Pranner", big = true)
                CreditLine("und Claude Code")
                CreditHeading("Die Zeiten")
                SystemEras.forEach { era ->
                    CreditLine("${era.version} · ${era.system}", big = true)
                    CreditLine(era.title)
                    Spacer(Modifier.height(12.dp))
                }
                CreditHeading("Mit dabei")
                CreditLine("Clawd – als er selbst")
                CreditLine("Das Z, das Ω und der Kamin")
                CreditLine("Die Sonne im Zenit")
                CreditLine("Ein Handy, das nie stillhält")
                CreditHeading("Und du")
                CreditLine("fürs Suchen und Finden", big = true)
                CreditLine("${found.size} von ${EasterEggs.TOTAL} Easter Eggs")
                Spacer(Modifier.height(60.dp))
                ZenithMark(color = ZenithGreen, width = 56.dp)
                Spacer(Modifier.height(screen))
            }
        }
    }
}
