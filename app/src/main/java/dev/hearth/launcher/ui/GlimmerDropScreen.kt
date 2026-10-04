package dev.hearth.launcher.ui

import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.GlimmerDrop
import kotlin.math.sin

private val DropBlue = Color(0xFF5E9BFF)
private val DropColors = listOf(Color(0xFF5E9BFF), Color(0xFF8E6BFF), Color(0xFFFF6FB5), Color(0xFFD97757))

/**
 * Glimmer Drop: Glimmer's island at the top glows and reaches for the other phone; hold the
 * tops of two phones together and what each shares flows across – contact card, photos,
 * videos, files, links and text.
 */
@Composable
fun GlimmerDropScreen(onPicking: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val phase by GlimmerDrop.phase.collectAsState()
    val exchange by GlimmerDrop.exchange.collectAsState()
    val nearby by GlimmerDrop.nearby.collectAsState()
    val closeness by GlimmerDrop.closeness.collectAsState()
    val files by GlimmerDrop.files.collectAsState()
    val texts by GlimmerDrop.texts.collectAsState()
    val shareProfile by GlimmerDrop.shareProfile.collectAsState()
    val ready by GlimmerDrop.ready.collectAsState()
    val signal by GlimmerDrop.signal.collectAsState()
    var profile by remember { mutableStateOf(GlimmerDrop.profile(context)) }
    var editing by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        GlimmerDrop.loadReady(context)
        // First time: a contact card to share starts with the phone's name.
        if (profile.isEmpty) editing = true
    }
    LaunchedEffect(hint) {
        if (hint != null) {
            kotlinx.coroutines.delay(2200)
            hint = null
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        GlimmerDrop.restart(context)
    }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        GlimmerDrop.restart(context)
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        GlimmerDrop.addFiles(context, uris)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        GlimmerDrop.addFiles(context, uris)
    }
    // Ask for "devices nearby" right away; without it nothing can be found.
    LaunchedEffect(Unit) {
        if (!GlimmerDrop.hasPermissions(context)) {
            onPicking()
            permissions.launch(GlimmerDrop.neededPermissions())
        }
    }

    BackHandler(enabled = !editing && !writing, onBack = onClose)

    val peer = when (val p = phase) {
        is GlimmerDrop.Phase.Connecting -> p.peer
        GlimmerDrop.Phase.Connected -> exchange?.peer.orEmpty()
        else -> null
    }
    val linked = phase is GlimmerDrop.Phase.Connected || phase is GlimmerDrop.Phase.Connecting

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070912), Color(0xFF0B0A1A), Color(0xFF050506)))),
    ) {
        FluidBackdrop(DropColors.take(3), strength = 0.4f + 0.6f * if (linked) 1f else closeness)
        DropWave(active = linked, closeness = closeness)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DropIsland(peer = peer, closeness = closeness, linked = linked)
            Spacer(Modifier.height(18.dp))
            Text(
                "Glimmer Drop",
                style = TextStyle(brush = Brush.linearGradient(DropColors), fontSize = 32.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (val p = phase) {
                    GlimmerDrop.Phase.Off, GlimmerDrop.Phase.Searching -> "Halte die Oberkante deines Handys an ein anderes Handy mit Glimmer – oder tippe unten auf ein Handy in der Nähe."
                    is GlimmerDrop.Phase.Connecting -> "Verbinde mit ${p.peer} …"
                    GlimmerDrop.Phase.Connected -> "Verbunden mit ${exchange?.peer.orEmpty()}"
                    is GlimmerDrop.Phase.Failed -> p.message
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            if (phase is GlimmerDrop.Phase.Searching) {
                Spacer(Modifier.height(18.dp))
                TouchPhones(closeness)
                Spacer(Modifier.height(8.dp))
                ClosenessBar(closeness)
                Spacer(Modifier.height(6.dp))
                SignalLine(signal)
            }
            (phase as? GlimmerDrop.Phase.Failed)?.let {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!GlimmerDrop.hasPermissions(context)) {
                        DropButton("Erlaubnis geben", DropBlue) {
                            onPicking()
                            permissions.launch(GlimmerDrop.neededPermissions())
                        }
                        // Once said no twice, Android doesn't ask again: then in the app's settings.
                        DropButton("App-Einstellungen", Color.White.copy(alpha = 0.14f)) {
                            onPicking()
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    } else if (!GlimmerDrop.bluetoothOn(context)) {
                        DropButton("Bluetooth einschalten", DropBlue) {
                            onPicking()
                            runCatching { bluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        }
                    } else {
                        DropButton("Nochmal versuchen", DropBlue) { GlimmerDrop.restart(context) }
                    }
                }
            }
            if (phase is GlimmerDrop.Phase.Searching && !GlimmerDrop.bluetoothOn(context)) {
                Spacer(Modifier.height(10.dp))
                DropButton("Bluetooth einschalten", DropBlue) {
                    onPicking()
                    runCatching { bluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }
            }

            exchange?.let { ex ->
                Spacer(Modifier.height(16.dp))
                ExchangeCard(ex)
            }

            DropSection("Das teilst du") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(profile.name, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Meine Kontaktkarte", color = Color.White, fontSize = 15.sp)
                        Text(
                            profile.name.ifBlank { "Noch leer – tippen zum Ausfüllen" },
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = shareProfile,
                        onCheckedChange = { GlimmerDrop.shareProfile.value = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = DropBlue),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DropChip("Fotos", Modifier.weight(1f)) {
                        onPicking()
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    DropChip("Dateien", Modifier.weight(1f)) {
                        onPicking()
                        documents.launch(arrayOf("*/*"))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DropChip("Link einfügen", Modifier.weight(1f)) {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (text.isNullOrBlank()) hint = "Die Zwischenablage ist leer" else GlimmerDrop.addText(text)
                    }
                    DropChip("Text", Modifier.weight(1f)) { writing = true }
                }
                // A preview of everything that goes: pictures of photos and videos, link cards, text.
                PreviewGrid(files.size) { i, mod ->
                    val f = files[i]
                    PreviewTile(rememberThumb(f.uri), f.mime, f.name, mod, onRemove = { GlimmerDrop.removeFile(f) })
                }
                texts.forEach { t ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        TextPreview(t, onRemove = { GlimmerDrop.removeText(t) })
                    }
                }
                if (files.isNotEmpty() || texts.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable {
                                onPicking()
                                if (!GlimmerDrop.quickShare(context)) hint = "Quick Share ist nicht da"
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚡", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Über Quick Share senden", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Für Handys ohne Glimmer", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                }
                if (linked) {
                    Text(
                        "Neu Ausgewähltes geht beim nächsten Aneinanderhalten mit.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            if (nearby.isNotEmpty() && phase is GlimmerDrop.Phase.Searching) {
                DropSection("In der Nähe") {
                    nearby.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { GlimmerDrop.connect(p) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p.name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("Verbinden", color = DropBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            DropSection("Glimmer") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { GlimmerDrop.setReady(context, !ready) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bereit, wenn entsperrt", color = Color.White, fontSize = 15.sp)
                        Text(
                            "Glimmer merkt im Hintergrund, wenn ein Handy mit Glimmer an deins gehalten wird, und öffnet Glimmer Drop sofort.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = ready,
                        onCheckedChange = { GlimmerDrop.setReady(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = DropBlue),
                    )
                }
            }
            Text(
                "Funktioniert zwischen Handys mit Glimmer – mit OMEGA UI oder nur der Glimmer-App (Bluetooth an). Empfangenes landet in der Galerie bzw. unter Downloads im Ordner „Glimmer Drop“.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
        }

        GlassCircle(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }

        hint?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 30.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A30).copy(alpha = 0.95f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(editing, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            ProfileEditor(
                start = profile,
                onSave = {
                    GlimmerDrop.saveProfile(context, it)
                    profile = GlimmerDrop.profile(context)
                    editing = false
                },
                onCancel = { editing = false },
            )
        }
        AnimatedVisibility(writing, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            TextWriter(
                onDone = {
                    GlimmerDrop.addText(it)
                    writing = false
                },
                onCancel = { writing = false },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Glimmer: the island at the top, and the light flowing to the other phone
// ---------------------------------------------------------------------------------------------

@Composable
private fun DropIsland(peer: String?, closeness: Float, linked: Boolean) {
    val pulse by rememberInfiniteTransition(label = "island").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val width by animateDpAsState(
        if (linked) 300.dp else 120.dp + 80.dp * closeness,
        spring(dampingRatio = 0.7f, stiffness = 260f),
        label = "w",
    )
    val height by animateDpAsState(if (linked) 72.dp else 36.dp, spring(dampingRatio = 0.7f, stiffness = 260f), label = "h")
    Box(
        Modifier
            .padding(top = 10.dp)
            .size(width, height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.Black)
            .aiFluidEdge(height / 2, strength = 0.6f + 0.4f * pulse, width = 2.dp, flowing = true),
        contentAlignment = Alignment.Center,
    ) {
        if (linked && peer != null) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(peer, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Glimmer Drop", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(peer, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Clawd(Modifier.size(width = 40.dp, height = 34.dp), mood = ClawdMood.Wave)
            }
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(DropBlue.copy(alpha = 0.4f + 0.6f * pulse)),
            )
        }
    }
}

/** NameDrop's moment: light flowing down from the island while the phones are linked. */
@Composable
private fun DropWave(active: Boolean, closeness: Float) {
    val strength by animateFloatAsState(if (active) 1f else closeness * 0.5f, tween(600), label = "wave")
    val flow by rememberInfiniteTransition(label = "flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "f",
    )
    if (strength <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        val top = Offset(size.width / 2f, 40.dp.toPx())
        for (i in 0 until 3) {
            val t = (flow + i / 3f) % 1f
            val r = size.height * (0.1f + 0.9f * t)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, DropColors[i % DropColors.size].copy(alpha = 0.35f * strength * (1f - t)), Color.Transparent),
                    center = top,
                    radius = r,
                ),
                radius = r,
                center = top,
            )
        }
    }
}

/** Two phones meeting at the top, closer the closer the real one is. */
@Composable
private fun TouchPhones(closeness: Float) {
    val wobble by rememberInfiniteTransition(label = "phones").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "wob",
    )
    val gap by animateFloatAsState(1f - closeness, tween(400), label = "gap")
    Canvas(Modifier.size(width = 220.dp, height = 170.dp)) {
        val w = 70.dp.toPx()
        val h = 140.dp.toPx()
        val r = CornerRadius(14.dp.toPx())
        val apart = (8.dp.toPx() + gap * 40.dp.toPx()) * (0.85f + 0.15f * wobble)
        val cx = size.width / 2f
        val y = size.height - h
        // Left phone, tilted towards the right one.
        rotate(-12f, Offset(cx - apart, size.height)) {
            drawRoundRect(Color(0xFF1C1C24), Offset(cx - apart - w, y), Size(w, h), r)
            drawRoundRect(DropBlue.copy(alpha = 0.7f), Offset(cx - apart - w, y), Size(w, h), r, style = Stroke(2.dp.toPx()))
            drawRoundRect(Color.Black, Offset(cx - apart - w * 0.7f, y + 6.dp.toPx()), Size(w * 0.4f, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
        rotate(12f, Offset(cx + apart, size.height)) {
            drawRoundRect(Color(0xFF1C1C24), Offset(cx + apart, y), Size(w, h), r)
            drawRoundRect(Color(0xFFFF6FB5).copy(alpha = 0.7f), Offset(cx + apart, y), Size(w, h), r, style = Stroke(2.dp.toPx()))
            drawRoundRect(Color.Black, Offset(cx + apart + w * 0.3f, y + 6.dp.toPx()), Size(w * 0.4f, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
        // The spark where the tops meet.
        val glow = closeness * (0.6f + 0.4f * sin(wobble * 3.14f))
        if (glow > 0.05f) {
            val c = Offset(cx, y + 10.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = glow), DropBlue.copy(alpha = glow * 0.5f), Color.Transparent), center = c, radius = 50.dp.toPx()),
                radius = 50.dp.toPx(),
                center = c,
            )
        }
    }
}

/** What Glimmer's Bluetooth signal does right now – so a problem shows instead of nothing. */
@Composable
private fun SignalLine(signal: GlimmerDrop.Signal) {
    val text = signal.problem ?: buildString {
        append(if (signal.sending == true) "Sendet ✓" else "Sendet …")
        append("  ·  ")
        append(if (signal.listening == true) "Hört ✓" else "Hört …")
        append("  ·  ")
        append(signal.strongest?.let { "anderes Handy: $it dBm" } ?: "noch kein Handy gehört")
    }
    Text(
        text,
        color = if (signal.problem != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ClosenessBar(closeness: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(closeness.coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(DropColors)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                closeness >= 0.95f -> "Berührt!"
                closeness > 0.5f -> "Fast …"
                closeness > 0.05f -> "Ein Handy ist in der Nähe – näher ran"
                else -> "Suche …"
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The exchange
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExchangeCard(ex: GlimmerDrop.Exchange) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF16161A).copy(alpha = 0.88f))
            .glassSheen(28.dp)
            .aiFluidEdge(28.dp, strength = 0.9f, width = 1.5.dp, flowing = ex.incoming == GlimmerDrop.Step.Running || ex.outgoing == GlimmerDrop.Step.Running)
            .padding(18.dp),
    ) {
        val offer = ex.offer
        when (ex.incoming) {
            GlimmerDrop.Step.Asking -> if (offer != null) {
                Text("${ex.peer} möchte teilen", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(offer.summary(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                offer.profile?.let { Spacer(Modifier.height(10.dp)); ContactCard(it, saveable = false) }
                // What comes, before saying yes: their photos and videos small, links and text.
                if (offer.files.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PreviewGrid(offer.files.size, padding = 0.dp) { i, mod ->
                        val f = offer.files[i]
                        PreviewTile(ex.thumbs[i], f.mime, f.name, mod)
                    }
                }
                offer.texts.forEach { t ->
                    Spacer(Modifier.height(6.dp))
                    TextPreview(t)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropButton("Ablehnen", Color.White.copy(alpha = 0.14f), Modifier.weight(1f)) { GlimmerDrop.answer(false) }
                    DropButton("Annehmen", DropBlue, Modifier.weight(1f)) { GlimmerDrop.answer(true) }
                }
            }
            GlimmerDrop.Step.Running -> {
                Text("Empfange von ${ex.peer} …", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Progress(ex.inProgress)
            }
            GlimmerDrop.Step.Done -> {
                Text("Von ${ex.peer} empfangen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                ex.received.profile?.let { Spacer(Modifier.height(10.dp)); ContactCard(it, saveable = true) }
                ex.received.texts.forEach { t -> ReceivedText(t) }
                if (ex.received.files.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PreviewGrid(ex.received.files.size, padding = 0.dp) { i, mod ->
                        val f = ex.received.files[i]
                        PreviewTile(
                            rememberThumb(f.uri) ?: ex.thumbs[f.index],
                            f.mime,
                            f.name,
                            mod,
                            onClick = f.uri?.let { uri -> { open(context, uri, f.mime) } },
                        )
                    }
                    Text("Tippen zum Öffnen", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            GlimmerDrop.Step.Declined -> Text("Abgelehnt", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            GlimmerDrop.Step.None -> if (offer == null) {
                Text("Verbinde …", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            } else if (ex.outgoing == GlimmerDrop.Step.None) {
                Text("${ex.peer} teilt gerade nichts – und du auch nicht. Wähle unten etwas aus.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
        val out = when (ex.outgoing) {
            GlimmerDrop.Step.Asking -> "Warte, bis ${ex.peer} annimmt …"
            GlimmerDrop.Step.Running -> "Sende an ${ex.peer} …"
            GlimmerDrop.Step.Done -> "Gesendet ✓"
            GlimmerDrop.Step.Declined -> "${ex.peer} hat abgelehnt"
            GlimmerDrop.Step.None -> null
        }
        if (out != null) {
            if (ex.incoming != GlimmerDrop.Step.None) Spacer(Modifier.height(14.dp))
            Text(out, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            if (ex.outgoing == GlimmerDrop.Step.Running) Progress(ex.outProgress)
        }
    }
}

@Composable
private fun Progress(value: Float) {
    val shown by animateFloatAsState(value, tween(250), label = "progress")
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown.coerceIn(0.02f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(DropColors)),
        )
    }
    Text("${(value * 100).toInt()} %", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ContactCard(p: GlimmerDrop.Profile, saveable: Boolean) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF26304F), Color(0xFF3A2A55))))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { "Ohne Namen" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (p.phone.isNotBlank()) Text(p.phone, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            }
        }
        if (p.email.isNotBlank()) Text("✉️  ${p.email}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (p.instagram.isNotBlank()) Text("📸  @${p.instagram.removePrefix("@")}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (p.website.isNotBlank()) Text("🌐  ${p.website}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (saveable) {
            Spacer(Modifier.height(12.dp))
            DropButton("Zu Kontakten hinzufügen", DropBlue, Modifier.fillMaxWidth()) { saveContact(context, p) }
        }
    }
}

@Composable
private fun ReceivedText(text: String) {
    val context = LocalContext.current
    val link = GlimmerDrop.isLink(text)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        PreviewContent(text)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (link) {
                Text(
                    "Öffnen",
                    color = DropBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val url = if (text.trim().startsWith("www.")) "https://${text.trim()}" else text.trim()
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
            Text(
                "Kopieren",
                color = DropBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Glimmer Drop", text))
                },
            )
        }
    }
}

private fun open(context: Context, uri: Uri, mime: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Android's "new contact" screen, already filled in. */
private fun saveContact(context: Context, p: GlimmerDrop.Profile) {
    val notes = listOfNotNull(
        p.instagram.takeIf { it.isNotBlank() }?.let { "Instagram: @${it.removePrefix("@")}" },
        p.website.takeIf { it.isNotBlank() }?.let { "Web: $it" },
        "Über Glimmer Drop",
    ).joinToString("\n")
    val intent = Intent(ContactsContract.Intents.Insert.ACTION)
        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(ContactsContract.Intents.Insert.NAME, p.name)
        .putExtra(ContactsContract.Intents.Insert.PHONE, p.phone)
        .putExtra(ContactsContract.Intents.Insert.EMAIL, p.email)
        .putExtra(ContactsContract.Intents.Insert.NOTES, notes)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1_048_576 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.GERMAN, "%.1f MB", bytes / 1_048_576.0)
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(DropBlue, Color(0xFF8E6BFF), Color(0xFFFF6FB5)))),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isBlank()) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        } else {
            Text(initials, color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DropSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = Color(0xFF7FB2FF),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 18.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF16161A).copy(alpha = 0.82f))
            .glassSheen(24.dp)
            .aiFluidEdge(24.dp, strength = 0.4f, width = 1.dp)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun DropChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = DropBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun SharedRow(icon: String, title: String, detail: String?, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Entfernen", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DropButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(DropBlue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** A sheet over the screen for the contact card or a text. */
@Composable
private fun Sheet(title: String, onCancel: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF15141F))
                .aiFluidEdge(30.dp, strength = 0.6f, width = 1.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = DropBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProfileEditor(start: GlimmerDrop.Profile, onSave: (GlimmerDrop.Profile) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(start.name.ifBlank { GlimmerDrop.displayName(context) }) }
    var phone by remember { mutableStateOf(start.phone) }
    var email by remember { mutableStateOf(start.email) }
    var instagram by remember { mutableStateOf(start.instagram) }
    var website by remember { mutableStateOf(start.website) }
    Sheet("Meine Kontaktkarte", onCancel) {
        Text(
            "Das bekommt das andere Handy, wenn du deine Kontaktkarte teilst. Leere Felder bleiben weg.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Field("Name", name) { name = it }
        Field("Telefon", phone, KeyboardType.Phone) { phone = it }
        Field("E-Mail", email, KeyboardType.Email) { email = it }
        Field("Instagram", instagram) { instagram = it }
        Field("Website", website, KeyboardType.Uri) { website = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropButton("Abbrechen", Color.White.copy(alpha = 0.14f), Modifier.weight(1f), onCancel)
            DropButton("Sichern", DropBlue, Modifier.weight(1f)) {
                onSave(GlimmerDrop.Profile(name, phone, email, instagram, website))
            }
        }
    }
}

@Composable
private fun TextWriter(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Sheet("Text oder Link", onCancel) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(DropBlue),
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropButton("Abbrechen", Color.White.copy(alpha = 0.14f), Modifier.weight(1f), onCancel)
            DropButton("Hinzufügen", DropBlue, Modifier.weight(1f)) { if (text.isNotBlank()) onDone(text) else onCancel() }
        }
    }
}

/**
 * Glimmer Drop in the settings – of Hearth UI and of the Glimmer app: open it, allow
 * "devices nearby" (so Glimmer can feel a phone right away), and "ready when unlocked".
 */
@Composable
internal fun GlimmerDropSettings() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(GlimmerDrop.hasPermissions(context)) }
    var bluetoothOn by remember { mutableStateOf(GlimmerDrop.bluetoothOn(context)) }
    val ready by GlimmerDrop.ready.collectAsState()
    LaunchedEffect(Unit) {
        GlimmerDrop.loadReady(context)
        while (true) {
            allowed = GlimmerDrop.hasPermissions(context)
            bluetoothOn = GlimmerDrop.bluetoothOn(context)
            kotlinx.coroutines.delay(1500)
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = GlimmerDrop.hasPermissions(context)
        // Glimmer starts listening right away once it may.
        GlimmerDrop.setReady(context, GlimmerDrop.ready.value)
    }
    // Ready but not allowed yet: ask once by itself, so holding two phones together just works.
    LaunchedEffect(Unit) {
        GlimmerDrop.loadReady(context)
        if (GlimmerDrop.enabled.value && GlimmerDrop.ready.value && !GlimmerDrop.hasPermissions(context) && GlimmerDrop.askOnce(context)) {
            ask.launch(GlimmerDrop.neededPermissions())
        }
    }
    // The main switch: off is off – no Bluetooth signal, not in the share sheet, nowhere.
    val dropOn by GlimmerDrop.enabled.collectAsState()
    SwitchRow(
        label = "Glimmer Drop",
        description = if (dropOn) "An" else "Ganz aus: kein Bluetooth-Signal, nicht im Teilen-Menü, nicht im OMEGA-Menü",
        checked = dropOn,
    ) { v -> GlimmerDrop.setEnabled(context, v) }
    if (!dropOn) return
    RowDivider()
    ActionRow(
        label = "Glimmer Drop öffnen",
        description = "Zwei Handys mit Glimmer aneinanderhalten und Kontaktkarte, Fotos, Videos, Dateien, Links und Text teilen",
        onClick = {
            runCatching {
                context.startActivity(Intent(context, dev.hearth.launcher.DropActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
    if (!allowed) {
        ActionRow(
            label = "Geräte in der Nähe erlauben",
            description = "Nötig, damit Glimmer ein anderes Handy sofort spürt",
            onClick = { ask.launch(GlimmerDrop.neededPermissions()) },
        )
    }
    if (!bluetoothOn) {
        ActionRow(
            label = "Bluetooth einschalten",
            description = "Glimmer Drop findet das andere Handy über Bluetooth",
            onClick = {
                runCatching {
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
    SwitchRow(
        label = "Sofort bereit, wenn entsperrt",
        description = "Handys aneinanderhalten – Glimmer Drop geht sofort auf, ohne etwas zu öffnen",
        checked = ready,
    ) { v ->
        GlimmerDrop.setReady(context, v)
        if (v && !GlimmerDrop.hasPermissions(context)) ask.launch(GlimmerDrop.neededPermissions())
    }
    Note("Funktioniert zwischen Handys mit OMEGA UI oder nur der Glimmer-App. Auch aus jeder App: Teilen → „Glimmer Drop“. Empfangenes landet in der Galerie bzw. unter Downloads im Ordner „Glimmer Drop“.")
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

/** A photo's or video's small picture, loaded once. */
@Composable
private fun rememberThumb(uri: Uri?): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { GlimmerDrop.thumbnail(context, it, 320) } }
    }
    return bitmap
}

@Composable
private fun PreviewGrid(count: Int, padding: androidx.compose.ui.unit.Dp = 12.dp, tile: @Composable (Int, Modifier) -> Unit) {
    (0 until count).chunked(3).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { i -> tile(i, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PreviewTile(
    bitmap: Bitmap?,
    mime: String,
    name: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (mime.startsWith("image/")) "🖼" else if (mime.startsWith("video/")) "🎬" else if (mime.startsWith("audio/")) "🎵" else "📄", fontSize = 26.sp)
                Spacer(Modifier.height(4.dp))
                Text(name, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (mime.startsWith("video/") && bitmap != null) {
            Box(
                Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, fontSize = 15.sp)
            }
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Entfernen", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A link as a card (its site big, the address small), or a text as a quote. */
@Composable
private fun PreviewContent(text: String) {
    val t = text.trim()
    if (GlimmerDrop.isLink(t)) {
        val url = if (t.startsWith("www.")) "https://$t" else t
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.").orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(DropBlue, Color(0xFF8E6BFF)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(host.take(1).uppercase().ifBlank { "🔗" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(host.ifBlank { "Link" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Row {
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(DropBlue))
            Spacer(Modifier.width(10.dp))
            Text(t, color = Color.White, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TextPreview(text: String, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { PreviewContent(text) }
        if (onRemove != null) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Entfernen", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
