package dev.hearth.launcher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.ChatItem
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClaudeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dev.hearth.launcher.data.AiProvider
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClaudeFix
import dev.hearth.launcher.data.ClaudeModel

private val Terracotta = Color(0xFFD97757)
private val SheetTop = Color(0xF21B1A1D)
private val SheetBottom = Color(0xF70F0F11)
private val Ok = Color(0xFF6FD08C)
private val Warn = Color(0xFFFFB35C)

/** Things to try, before anything is typed. */
private val OfflineSuggestions = listOf(
    "Taschenlampe an",
    "Wecker halb 7",
    "Timer 10 Minuten",
    "Ruf Mama an",
    "Was ist 17 mal 23?",
    "Wie voll ist mein Akku?",
    "Mach den Bildschirm heller",
    "Nicht stören an",
    "Navigiere nach Hause",
)

private val ClaudeSuggestions = listOf(
    "Stell mir einen Wecker für morgen 6:45 und mach Nicht stören an",
    "Schreib Mama per WhatsApp, dass ich später komme",
    "Trag mir morgen 15 Uhr Zahnarzt ein",
    "Navigiere zum nächsten Supermarkt",
    "Mach das Handy leise und den Bildschirm dunkler",
    "Was ist auf meinem Handy gerade an?",
) + OfflineSuggestions.take(3)

/**
 * Claude, over whatever is on screen: the conversation, what Claude did on the phone (✓ lines),
 * typing or speaking, and a way to give Claude the key or a permission it is missing. Used by
 * the launcher (over the home screen) and by the assist screen (over any app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaudeAssistantSheet(
    onDismiss: () -> Unit,
    /** Hearth's settings; null where they can't be shown (the assist screen opens them instead). */
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val items by ClaudeAssistant.items.collectAsStateWithLifecycle()
    val busy by ClaudeAssistant.busy.collectAsStateWithLifecycle()
    val settings by ClaudeAssistant.settings.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var keyEntry by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Claude opened an app or a system screen: make way.
    LaunchedEffect(Unit) { ClaudeAssistant.openedScreen.collect { onDismiss() } }

    // The newest line stays in view.
    LaunchedEffect(items.size, busy) {
        val last = items.size + (if (busy) 1 else 0) - 1
        if (last >= 0) runCatching { listState.animateScrollToItem(last) }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) ClaudeAssistant.ask(heard, spoken = true)
    }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ClaudeAssistant.retry()
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll Claude für dich tun?")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            ClaudeAssistant.notice("Auf diesem Handy gibt es keine Spracheingabe. Tipp deine Frage einfach ein.")
        } catch (e: Exception) {
            // Some phones refuse while switching apps; nothing to do.
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        ClaudeAssistant.ask(text)
    }

    fun fix(f: ClaudeFix) {
        when (f) {
            ClaudeFix.ApiKey -> keyEntry = true
            ClaudeFix.Contacts -> runCatching { contacts.launch(Manifest.permission.READ_CONTACTS) }
            ClaudeFix.WriteSettings -> runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            ClaudeFix.DoNotDisturb -> runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ClaudeFix.Accessibility -> runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.86f
        AnimatedVisibility(visibleState = appear, enter = fadeIn(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it / 2 } + fadeIn(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(SheetTop, SheetBottom)))
                    .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp))
                    // Taps on the sheet stay on the sheet.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                // Claude's glow along the top edge.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Terracotta.copy(alpha = 0.20f), Color.Transparent))),
                ) {
                    val mood by ClaudeAssistant.clawdMood.collectAsStateWithLifecycle()
                    Header(
                        title = "Clawd",
                        mood = if (busy) ClawdMood.Thinking else mood,
                        subtitle = if (settings.hasKey) settings.modelLabel else "Offline-Befehle",
                        canClear = items.isNotEmpty(),
                        onClear = ClaudeAssistant::clear,
                        onSettings = onOpenSettings?.let { open -> { onDismiss(); open() } },
                        onClose = onDismiss,
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (items.isEmpty()) {
                        item(key = "hello") {
                            Welcome(
                                suggestions = if (settings.hasKey) ClaudeSuggestions else OfflineSuggestions,
                                offline = !settings.hasKey,
                                onPick = { ClaudeAssistant.ask(it) },
                                onAddKey = { keyEntry = true },
                            )
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        when (item) {
                            is ChatItem.User -> UserLine(item.text)
                            is ChatItem.Reply -> ReplyLine(item.text)
                            is ChatItem.Action -> ActionLine(item.label, item.ok)
                            is ChatItem.Problem -> ProblemLine(
                                item.text,
                                item.fix,
                                onFix = ::fix,
                                onHandOver = { ClaudeAssistant.handOverToApp(context) },
                            )
                        }
                    }
                    if (busy) {
                        item(key = "thinking") { Thinking(onStop = ClaudeAssistant::stop) }
                    }
                }

                if (keyEntry) {
                    ClaudeKeyField(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        onSaved = { keyEntry = false },
                    )
                }

                InputRow(
                    value = input,
                    onValueChange = { input = it },
                    onSend = ::send,
                    onListen = ::listen,
                )
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    mood: ClawdMood,
    subtitle: String,
    canClear: Boolean,
    onClear: () -> Unit,
    onSettings: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clawd himself: tap him five times and he flips (easter egg).
        val taps = remember { longArrayOf(0L, 0L) }
        Clawd(
            Modifier
                .size(40.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val now = System.currentTimeMillis()
                    taps[0] = if (now - taps[1] < 700) taps[0] + 1 else 1
                    taps[1] = now
                    if (taps[0] >= 5) {
                        taps[0] = 0
                        ClaudeAssistant.tickled()
                    }
                },
            mood = mood,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canClear) HeaderButton(Icons.Rounded.Refresh, "Neuer Chat", onClear)
        if (onSettings != null) HeaderButton(Icons.Rounded.Settings, "Einstellungen", onSettings)
        HeaderButton(Icons.Rounded.Close, "Schließen", onClose)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(21.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(suggestions: List<String>, offline: Boolean, onPick: (String) -> Unit, onAddKey: () -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            "Hey, ich bin Clawd! Was soll ich für dich erledigen?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            if (offline) {
                "Kostenlos und ohne Internet: Licht, Ton, Helligkeit, Wecker, Timer, Anrufe und Nachrichten an Kontakte, Navigation, Rechnen, Apps und Einstellungen. Alles andere gebe ich an die Claude-App weiter (dein normales Claude-Konto, keine API-Kosten)."
            } else {
                "Ich kann Dinge auf deinem Handy tun: Wecker, Timer, Termine, Nachrichten, Anrufe, Navigation, Apps, Einstellungen, Musik, Licht und Ton. Sag es einfach."
            },
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.size(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { s ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Clawd(Modifier.size(16.dp), animate = false)
                    Spacer(Modifier.width(6.dp))
                    Text(s, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
        if (offline) {
            Spacer(Modifier.size(12.dp))
            Text(
                "API-Schlüssel eintragen →",
                color = Terracotta,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddKey)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun UserLine(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ReplyLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(end = 24.dp), verticalAlignment = Alignment.Top) {
        Clawd(Modifier.padding(top = 2.dp).size(20.dp), animate = false)
        Spacer(Modifier.width(9.dp))
        Text(text, color = Color.White.copy(alpha = 0.95f), fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun ActionLine(label: String, ok: Boolean) {
    Row(
        Modifier
            .padding(start = 25.dp)
            .clip(RoundedCornerShape(12.dp))
            .background((if (ok) Ok else Warn).copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (ok) Ok else Warn,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@Composable
private fun ProblemLine(text: String, fix: ClaudeFix?, onFix: (ClaudeFix) -> Unit, onHandOver: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Warn.copy(alpha = 0.10f))
            .border(0.8.dp, Warn.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 19.sp)
        val label = when (fix) {
            ClaudeFix.ApiKey -> "API-Schlüssel eintragen"
            ClaudeFix.Contacts -> "Kontakte erlauben"
            ClaudeFix.WriteSettings -> "Systemeinstellungen erlauben"
            ClaudeFix.DoNotDisturb -> "„Nicht stören“ erlauben"
            ClaudeFix.Accessibility -> "Bedienungshilfen öffnen"
            null -> null
        }
        if (fix != null && label != null) {
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    label,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable { onFix(fix) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                // Without a key, questions can still go to the Claude app.
                if (fix == ClaudeFix.ApiKey) {
                    Text(
                        "In Claude-App fragen",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onHandOver)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Claude at work: three dots in terracotta, and a way to stop. */
@Composable
private fun Thinking(onStop: () -> Unit) {
    val t = rememberInfiniteTransition(label = "thinking")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "dots")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Clawd(Modifier.size(22.dp), mood = ClawdMood.Thinking)
        Spacer(Modifier.width(9.dp))
        Canvas(Modifier.size(width = 34.dp, height = 10.dp)) {
            for (i in 0 until 3) {
                val lit = (phase - i).let { if (it < 0) it + 3 else it } < 1f
                drawCircle(
                    color = Terracotta.copy(alpha = if (lit) 1f else 0.35f),
                    radius = size.height / 2.4f,
                    center = Offset(size.height / 2 + i * size.width / 3, size.height / 2),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("Clawd erledigt das …", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "Stopp",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onStop)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, onListen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Terracotta),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty()) {
                        Text("Sag Clawd, was er tun soll …", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        val typing = value.isNotBlank()
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Terracotta)
                .clickable(onClick = if (typing) onSend else onListen),
            contentAlignment = Alignment.Center,
        ) {
            if (typing) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Senden", tint = Color.White, modifier = Modifier.size(21.dp))
            } else {
                MicGlyph(Color.White, Modifier.size(22.dp))
            }
        }
    }
}

/** A microphone (the core icon set has none). */
@Composable
private fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, h * 0.06f),
            size = Size(w * 0.32f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.16f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.55f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2, h * 0.76f), Offset(w / 2, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Entering the chosen service's API key; it stays on the phone, in Hearth's own storage. */
@Composable
internal fun ClaudeKeyField(modifier: Modifier = Modifier, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val s by ClaudeAssistant.settings.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = key,
                onValueChange = { key = it.trim() },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Terracotta),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (key.isNotBlank()) {
                        ClaudeAssistant.updateSettings { it.withKey(key) }
                        key = ""
                        onSaved()
                    }
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(0.8.dp, Terracotta.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (key.isEmpty()) Text(s.provider.keyHint, color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Speichern",
                color = if (key.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (key.isNotBlank()) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f))
                    .clickable(enabled = key.isNotBlank()) {
                        ClaudeAssistant.updateSettings { it.withKey(key) }
                        key = ""
                        onSaved()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (s.provider.keyUrl.isNotEmpty()) Text(
            "Schlüssel holen: " + Uri.parse(s.provider.keyUrl).host.orEmpty(),
            color = Terracotta,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(s.provider.keyUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(vertical = 4.dp),
        )
    }
}

/** "Verfügbare Modelle laden": the service's current chat models, best first, to tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(s: ClaudeSettings) {
    val scope = rememberCoroutineScope()
    var models by remember(s.provider, s.apiKey) { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember(s.provider, s.apiKey) { mutableStateOf<String?>(null) }
    if (s.apiKey.isBlank()) return
    ActionRow(
        label = if (loading) "Lade Modelle …" else "Verfügbare Modelle laden",
        description = "Zeigt, welche Modelle ${s.provider.label} gerade anbietet (die besten für Hearth zuerst)",
    ) {
        if (loading) return@ActionRow
        loading = true
        error = null
        scope.launch {
            try {
                models = ClaudeAssistant.availableModels(s)
                if (models.isNullOrEmpty()) error = "Keine passenden Modelle gefunden."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Die Liste ließ sich nicht laden."
            } finally {
                loading = false
            }
        }
    }
    error?.let { Note(it) }
    val list = models
    if (!list.isNullOrEmpty()) {
        FlowRow(
            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.take(40).forEach { id ->
                Chip(text = id, selected = id == s.modelId, swatch = null) {
                    ClaudeAssistant.updateSettings { it.copy(models = it.models + (it.provider to id)) }
                }
            }
        }
    }
}

/** A one-line text setting, saved when done (or when leaving the field). */
@Composable
private fun SettingsTextField(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Terracotta),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text != value) onSave(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && text != value) onSave(text) },
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                inner()
            }
        },
    )
}

/** The assistant's part of Hearth's settings. */
@Composable
internal fun ClaudeAssistantSettings() {
    val context = LocalContext.current
    val s by ClaudeAssistant.settings.collectAsStateWithLifecycle()
    Section("Claude-Assistent") {
        SwitchRow(
            label = "Claude im System",
            description = "„Frag Claude“, die Suche, Now Brief, das Kontrollzentrum und Glimmer öffnen Claude, das Dinge auf dem Handy für dich erledigt (sonst die Claude-App)",
            checked = s.enabled,
        ) { v -> ClaudeAssistant.updateSettings { it.copy(enabled = v) } }
        RowDivider()
        ChoiceRow(
            label = "KI-Anbieter",
            options = AiProvider.entries,
            selected = s.provider,
            optionLabel = { it.label },
            onSelect = { p -> ClaudeAssistant.updateSettings { it.copy(provider = p) } },
        )
        Note(
            if (s.isClaude) {
                "Claude von Anthropic. Die API wird getrennt vom Claude-Abo abgerechnet."
            } else {
                "${s.provider.label} spricht die OpenAI-Schnittstelle. Viele Anbieter haben ein kostenloses Kontingent (Bedingungen ändern sich). Das Modell muss Werkzeuge (Function Calling) können, sonst kann es auf dem Handy nichts tun."
            },
        )
        if (s.provider == AiProvider.Custom) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("Adresse (OpenAI-kompatibel, endet meist auf /v1)", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.size(8.dp))
                SettingsTextField(
                    value = s.customUrl,
                    placeholder = "https://…/v1",
                    keyboardType = KeyboardType.Uri,
                ) { v -> ClaudeAssistant.updateSettings { it.copy(customUrl = v.trim()) } }
            }
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(
                if (s.apiKey.isNotBlank()) "API-Schlüssel für ${s.provider.label}: gespeichert (…${s.apiKey.takeLast(4)})" else "API-Schlüssel für ${s.provider.label}: keiner (kostenlos: Offline-Befehle, der Rest geht an die Claude-App)",
                color = Color.White,
                fontSize = 15.sp,
            )
            Spacer(Modifier.size(8.dp))
            ClaudeKeyField()
        }
        if (s.apiKey.isNotBlank()) {
            ActionRow(label = "Schlüssel entfernen") { ClaudeAssistant.updateSettings { it.withKey("") } }
        }
        RowDivider()
        if (s.isClaude) {
            ChoiceRow(
                label = "Modell",
                options = ClaudeModel.entries,
                selected = s.model,
                optionLabel = { it.label },
                onSelect = { m -> ClaudeAssistant.updateSettings { it.copy(model = m) } },
            )
        } else {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Modell", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.size(8.dp))
                SettingsTextField(
                    value = s.models[s.provider].orEmpty(),
                    placeholder = s.provider.defaultModel.ifEmpty { "Modellname" },
                ) { v -> ClaudeAssistant.updateSettings { it.copy(models = it.models + (it.provider to v.trim())) } }
                Text(
                    "Leer lassen für ${s.provider.defaultModel.ifEmpty { "automatische Wahl" }}. Gibt es ein Modell nicht mehr, nimmt Hearth von selbst das nächste passende.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ModelPicker(s)
        }
        SwitchRow(
            label = "Sparmodus",
            description = "Alles, was Hearth selbst versteht (Licht, Wecker, Anrufe, Rechnen …), läuft kostenlos ohne API. Nur der Rest geht an die KI",
            checked = s.saver,
        ) { v -> ClaudeAssistant.updateSettings { it.copy(saver = v) } }
        SwitchRow(
            label = "Antworten vorlesen",
            description = "Wenn du per Sprache fragst",
            checked = s.speak,
        ) { v -> ClaudeAssistant.updateSettings { it.copy(speak = v) } }
        RowDivider()
        ActionRow(
            label = "Als digitalen Assistenten festlegen",
            description = "Dann öffnet langes Drücken auf Home (oder die Seitentaste, je nach Handy) Claude – über jeder App. Wähle dort „Hearth“.",
        ) {
            val opened = listOf(
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            ).any { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
            if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        ActionRow(label = "Verlauf löschen", onClick = ClaudeAssistant::clear)
        Note("Weitere Wege zu Claude: die Kachel „Claude“ in den Schnelleinstellungen (Bearbeiten → hinzufügen) und Doppeltippen auf Glimmer (Glimmer → Bedienung). Ohne Schlüssel entstehen keine Kosten: Hearth erledigt einfache Befehle selbst und gibt alles andere an die Claude-App (dein Claude-Konto) weiter. Mit Schlüssel bleibt er nur auf diesem Handy; die API wird getrennt vom Claude-Abo abgerechnet. Anrufe, Nachrichten und Termine bestätigst du immer selbst.")
    }
}
