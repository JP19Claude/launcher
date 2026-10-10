package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.ZenithShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private fun levelColor(level: ZenithShield.Level): Color = when (level) {
    ZenithShield.Level.Good -> ZenithGreen
    ZenithShield.Level.Warn -> ZenithSun
    ZenithShield.Level.Bad -> VaultRed
}

private fun scoreColor(score: Int): Color = when {
    score >= 85 -> ZenithGreen
    score >= 60 -> ZenithSun
    else -> VaultRed
}

/**
 * ZENITH 19, the Schutzschild: a gauge round the metal Z for how well the phone is protected,
 * every point checked with the way to fix it, and which apps may use what. Checked afresh
 * each time it comes back to the front. ZENITH 19.5: with the Wächter, which speak up on the
 * Glimmer island when an app is installed and when the battery has charged far enough.
 */
@Composable
fun ShieldScreen(onClose: () -> Unit, onUpdate: (((LauncherSettings) -> LauncherSettings) -> Unit)? = null) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<ZenithShield.Check>?>(null) }
    var apps by remember { mutableStateOf<List<ZenithShield.AppAccess>?>(null) }
    var filter by remember { mutableStateOf<ZenithShield.Access?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                checks = withContext(Dispatchers.IO) { ZenithShield.checks(context) }
                apps = withContext(Dispatchers.IO) { ZenithShield.apps(context) }
            }
        }
    }
    BackHandler { onClose() }

    val found = checks
    val score = found?.let { ZenithShield.score(it) }
    // ZENITH 19.6: ten quick taps on the gauge strike; a perfect score is its own find.
    val gaugeTaps = remember { longArrayOf(0L, 0L) }
    var strike by remember { mutableStateOf(false) }
    LaunchedEffect(score) { if (score == 100) EasterEggs.find(context, "shield100") }
    val all = apps.orEmpty()
    val only = filter
    val shown = all.filter { only == null || only in it.access }

    Box(Modifier.fillMaxSize().background(VaultBg)) {
        VaultBackdrop()
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            item { VaultTopBar(onClose, title = "SCHUTZSCHILD") }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                            val at = System.currentTimeMillis()
                            gaugeTaps[0] = if (at - gaugeTaps[1] < 600) gaugeTaps[0] + 1 else 1
                            gaugeTaps[1] = at
                            if (gaugeTaps[0] >= 10) {
                                gaugeTaps[0] = 0
                                EasterEggs.find(context, "shield10")
                                strike = true
                            }
                        },
                    ) { ShieldGauge(score) }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            score?.toString() ?: "–",
                            color = if (score == null) VaultDim else scoreColor(score),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(" / 100", color = VaultDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 9.dp))
                    }
                    Text(
                        when {
                            score == null -> "Wird geprüft …"
                            score >= 85 -> "Gut geschützt"
                            score >= 60 -> "Ein paar Punkte sind offen"
                            else -> "Hier ist etwas zu tun"
                        },
                        color = VaultText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            item { ShieldHeader("Prüfungen") }
            items(found.orEmpty()) { check -> CheckCard(check) }
            if (onUpdate != null && dev.hearth.launcher.BuildConfig.ALL_IN_ONE) {
                item {
                    val s = LocalSettings.current
                    Column(Modifier.padding(top = 14.dp)) {
                        StudioPanel(
                            "Wächter",
                            "Die Wächter melden sich auf der Glimmer-Insel – dafür muss Glimmer an sein. Sie schauen nur und sagen Bescheid; ändern tun sie nichts.",
                        ) {
                            SwitchRow(
                                label = "Neue-App-Wächter",
                                description = "Nach jeder Installation: was die neue App will – Kamera, Mikrofon, Standort …",
                                checked = s.newAppGuard,
                            ) { v -> onUpdate { it.copy(newAppGuard = v) } }
                            SwitchRow(
                                label = "Akku-Wächter",
                                description = "Sagt beim Laden Bescheid, wenn die Grenze erreicht ist – früher abziehen schont den Akku",
                                checked = s.chargeGuard,
                            ) { v -> onUpdate { it.copy(chargeGuard = v) } }
                            if (s.chargeGuard) {
                                IntSlider("Grenze", s.chargeLimit, 70..95, " %") { v -> onUpdate { it.copy(chargeLimit = v) } }
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    ShieldHeader("Wer darf was")
                    Text(
                        "Was Android den Apps erlaubt hat. Tippe auf eine App, um ihr etwas wegzunehmen.",
                        color = VaultDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)) {
                        val counts = ZenithShield.Access.entries.map { a -> a to all.count { a in it.access } }.filter { it.second > 0 }
                        AccessChip("Alle  ${all.size}", filter == null) { filter = null }
                        counts.forEach { (access, count) ->
                            AccessChip("${access.label}  $count", filter == access) { filter = if (filter == access) null else access }
                        }
                    }
                    if (apps == null) Text("Apps werden gelesen …", color = VaultDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            items(shown, key = { it.packageName }) { app -> AppAccessRow(app) }
            item {
                Text(
                    "Der Schutzschild schaut nur nach – ZENITH ändert hier nichts von selbst. Alles stellst du in Android selbst um.",
                    color = VaultDim.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
            }
        }
        if (strike) ZenithStrikeOverlay { strike = false }
    }
}

@Composable
private fun ShieldHeader(title: String) {
    Text(
        title.uppercase(),
        color = ZenithGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/** The gauge: an arc of light round the metal Z, filled as far as the [score] (null while checking). */
@Composable
private fun ShieldGauge(score: Int?) {
    val fill by animateFloatAsState((score ?: 0) / 100f, tween(1200), label = "shield")
    val color = if (score == null) VaultDim else scoreColor(score)
    Canvas(Modifier.size(210.dp)) {
        val c = center
        val r = size.minDimension / 2f * 0.86f
        val box = Size(r * 2f, r * 2f)
        val at = Offset(c.x - r, c.y - r)
        val stroke = size.minDimension * 0.045f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f * fill + 0.04f), Color.Transparent), center = c, radius = r * 1.15f), radius = r * 1.15f, center = c)
        drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fill > 0.005f) {
            drawArc(color.copy(alpha = 0.25f), 135f, 270f * fill, false, at, box, style = Stroke(stroke * 2.4f, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fill, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // Ticks round the outside, as on an instrument.
        for (i in 0..27) {
            val a = Math.toRadians(135.0 + i * 10.0)
            val inner = r * 1.1f
            val outer = r * (if (i % 3 == 0) 1.18f else 1.14f)
            val lit = i / 27f <= fill && score != null
            drawLine(
                if (lit) color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
                strokeWidth = size.minDimension * 0.008f,
            )
        }
        // A hexagon behind the Z.
        val hex = Path()
        for (k in 0 until 6) {
            val a = Math.toRadians(60.0 * k - 90.0)
            val x = c.x + (cos(a) * r * 0.62f).toFloat()
            val y = c.y + (sin(a) * r * 0.62f).toFloat()
            if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        drawPath(hex, Color(0xFF0E1214))
        drawPath(hex, color.copy(alpha = 0.5f), style = Stroke(size.minDimension * 0.006f))
        val zw = r * 0.82f
        drawZenithZ(c.x - zw / 2f, c.y - zw * ZenithZAspect / 2f, zw, light = 0.3f + 0.7f * fill)
    }
}

@Composable
private fun CheckCard(check: ZenithShield.Check) {
    val context = LocalContext.current
    val shape = ZenithCutShape(10.dp)
    val color = levelColor(check.level)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(VaultPanel)
            .border(0.6.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val hex = Path()
            for (k in 0 until 6) {
                val a = Math.toRadians(60.0 * k - 90.0)
                val x = center.x + (cos(a) * size.minDimension / 2f).toFloat()
                val y = center.y + (sin(a) * size.minDimension / 2f).toFloat()
                if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
            }
            hex.close()
            drawCircle(color.copy(alpha = 0.3f), radius = size.minDimension, center = center)
            drawPath(hex, color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, color = VaultText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(check.detail, color = VaultDim, fontSize = 13.sp, lineHeight = 17.sp)
        }
        val intent = check.intent
        if (check.action != null && intent != null) {
            Spacer(Modifier.width(10.dp))
            val chip = ZenithCutShape(6.dp)
            Text(
                check.action,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(chip)
                    .border(0.8.dp, color.copy(alpha = 0.6f), chip)
                    .clickable { runCatching { context.startActivity(intent) } }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun AccessChip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = ZenithCutShape(7.dp)
    Text(
        text,
        color = if (on) Color(0xFF04140B) else VaultText,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(shape)
            .background(if (on) ZenithGreen else VaultPanel)
            .border(0.8.dp, Color.White.copy(alpha = if (on) 0f else 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun AppAccessRow(app: ZenithShield.AppAccess) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ZenithCutShape(8.dp))
            .clickable { runCatching { context.startActivity(ZenithShield.appSettings(app.packageName)) } }
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = VaultText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.access.joinToString(" · ") { it.label },
                color = if (ZenithShield.Access.AlwaysLocation in app.access) ZenithSun else VaultDim,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${app.access.size}",
            color = ZenithGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
