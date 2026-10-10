package dev.hearth.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.hearth.launcher.data.ZenithCrypto
import dev.hearth.launcher.data.ZenithVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import javax.crypto.Cipher
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the vault's screen needs from its activity: staying open for a picker, the fingerprint. */
interface VaultHost {
    /** The next time the screen leaves (a picker, another app), the vault stays open. */
    fun stayOpen()
    fun fingerprintAvailable(): Boolean
    /** Asks for the fingerprint; [done] gets the unlocked cipher, or null and why (null when simply cancelled). */
    fun askFingerprint(cipher: Cipher, title: String, done: (Cipher?, String?) -> Unit)
}

internal val VaultBg = Color(0xFF060809)
internal val VaultPanel = Color(0xFF111518)
internal val VaultPanelHigh = Color(0xFF181D21)
internal val VaultText = Color(0xFFE9EEF0)
internal val VaultDim = Color(0xFF8D979B)
internal val VaultRed = Color(0xFFFF5A5F)

/** Any file chosen to be written, of any type (the type known only when asked). */
internal class CreateTyped : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Work going on: what, and how far (below 0: unknown). */
private class VaultWork(val title: String, val fraction: Float)

/**
 * The ZENITH-Tresor: setting it up, unlocking it (password or fingerprint) and what's inside –
 * photos, videos and files, encrypted on this phone.
 */
@Composable
fun VaultScreen(
    host: VaultHost,
    onClose: () -> Unit,
    incoming: List<Uri> = emptyList(),
    onIncomingTaken: () -> Unit = {},
) {
    val context = LocalContext.current
    val unlocked by ZenithVault.unlocked.collectAsState()
    var exists by remember { mutableStateOf(ZenithVault.exists(context)) }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(VaultBg)) {
        VaultBackdrop()
        when {
            !exists -> VaultSetup(host, onBack = onClose, onReady = { exists = ZenithVault.exists(context) })
            !unlocked -> VaultLocked(host, onBack = onClose)
            else -> VaultOpen(host, onBack = onClose, onGone = { exists = ZenithVault.exists(context) }, incoming = incoming, onIncomingTaken = onIncomingTaken)
        }
        // ZENITH 19.1: shared into the vault from another app – waiting until it's open.
        if (incoming.isNotEmpty() && !unlocked) {
            val shape = ZenithCutShape(10.dp)
            Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    if (incoming.size == 1) "1 Datei wartet – sie wird verschlüsselt, sobald der Tresor offen ist." else "${incoming.size} Dateien warten – sie werden verschlüsselt, sobald der Tresor offen ist.",
                    color = VaultText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(shape)
                        .background(VaultPanelHigh)
                        .border(0.8.dp, ZenithGreen.copy(alpha = 0.5f), shape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ---- Look ----

@Composable
internal fun VaultBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(ZenithGreen.copy(alpha = 0.13f), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.width * 0.15f),
                radius = size.width * 1.05f,
            ),
        )
        // Fine hexagons, as on a Zygarde core.
        val r = size.width / 9f
        val dx = r * 1.732f
        var row = 0
        var y = 0f
        while (y < size.height * 0.5f) {
            val fade = (1f - y / (size.height * 0.5f)).coerceIn(0f, 1f)
            var x = if (row % 2 == 0) 0f else dx / 2f
            while (x < size.width + dx) {
                drawPath(hexPath(Offset(x, y), r * 0.96f), ZenithGreen.copy(alpha = 0.05f * fade), style = Stroke(1f))
                x += dx
            }
            y += r * 1.5f
            row++
        }
    }
}

private fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(60.0 * i - 90.0)
        val x = c.x + (cos(a) * r).toFloat()
        val y = c.y + (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The vault's sign: the metal Z in a hexagon of light; it glows when [open], turns when [busy]. */
@Composable
private fun VaultEmblem(size: Dp, open: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "vault")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "turn")
    val pulse by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(if (open) 1f else 0.35f, tween(700), label = "glow")
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.22f * glow + 0.08f * pulse), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
        val outer = hexPath(c, r * 0.84f)
        drawPath(outer, Brush.verticalGradient(listOf(Color(0xFF1A1F22), Color(0xFF0A0D0E)), startY = c.y - r, endY = c.y + r))
        drawPath(
            outer,
            Brush.linearGradient(
                listOf(Color(0xFFE2E8EB).copy(alpha = 0.75f), Color(0xFF41474B), ZenithGreen.copy(alpha = 0.35f + 0.65f * glow)),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r),
            ),
            style = Stroke(r * 0.035f),
        )
        drawPath(hexPath(c, r * 0.68f), ZenithGreen.copy(alpha = 0.14f + 0.3f * glow), style = Stroke(r * 0.012f))
        if (busy) {
            rotate(turn, c) {
                val box = Size(r * 1.9f, r * 1.9f)
                val at = Offset(c.x - r * 0.95f, c.y - r * 0.95f)
                drawArc(ZenithGreen, 0f, 64f, false, at, box, style = Stroke(r * 0.03f, cap = StrokeCap.Round))
                drawArc(ZenithGreen.copy(alpha = 0.5f), 180f, 64f, false, at, box, style = Stroke(r * 0.03f, cap = StrokeCap.Round))
            }
        }
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90.0)
            val p = Offset(c.x + (cos(a) * r * 0.84f).toFloat(), c.y + (sin(a) * r * 0.84f).toFloat())
            drawCircle(ZenithGreen.copy(alpha = 0.35f + 0.65f * glow), r * 0.032f, p)
        }
        val zw = r * 0.98f
        drawZenithZ(c.x - zw / 2f, c.y - zw * ZenithZAspect / 2f, zw, light = glow, charge = if (busy) pulse * 0.7f else 0f)
    }
}

@Composable
internal fun VaultTopBar(onBack: () -> Unit, title: String = "TRESOR", actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = VaultText)
        }
        Spacer(Modifier.width(6.dp))
        ZenithMark(color = ZenithGreen, width = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, color = VaultText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun VaultButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = ZenithCutShape(10.dp)
    val fill = when {
        !enabled -> Brush.linearGradient(listOf(Color(0xFF1A1F22), Color(0xFF15191C)))
        danger -> Brush.linearGradient(listOf(Color(0xFFB8323A), Color(0xFF7E1C22)))
        primary -> Brush.linearGradient(listOf(Color(0xFF3FE08C), ZenithGreenDeep))
        else -> Brush.linearGradient(listOf(Color(0xFF22282C), Color(0xFF15191C)))
    }
    Row(
        modifier
            .clip(shape)
            .background(fill)
            .border(0.8.dp, Color.White.copy(alpha = if (primary && enabled) 0.3f else 0.12f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = when {
            !enabled -> VaultDim.copy(alpha = 0.6f)
            primary && !danger -> Color(0xFF04140B)
            else -> VaultText
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    var show by remember { mutableStateOf(false) }
    val shape = ZenithCutShape(9.dp)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(128)) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = VaultText, fontSize = 17.sp, letterSpacing = 1.sp),
        cursorBrush = SolidColor(ZenithGreen),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(VaultPanel)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 15.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = VaultDim.copy(alpha = 0.7f), fontSize = 16.sp)
                    inner()
                }
                Text(
                    if (show) "Verbergen" else "Zeigen",
                    color = ZenithGreen,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).clickable { show = !show }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        },
    )
}

/** How hard a password is to guess, 0..4. */
private fun strength(pw: String): Int {
    if (pw.length < ZenithCrypto.MIN_PASSWORD) return 0
    var score = 1
    if (pw.length >= 10) score++
    if (pw.any { it.isDigit() } && pw.any { it.isLetter() }) score++
    if (pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return score.coerceAtMost(4)
}

@Composable
internal fun StrengthBar(pw: String) {
    val s = strength(pw)
    val label = listOf("zu kurz", "schwach", "okay", "gut", "stark")[s]
    val color = listOf(VaultRed, VaultRed, ZenithSun, ZenithGreen, ZenithGreen)[s]
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i < s) color else Color.White.copy(alpha = 0.1f)),
            )
            if (i < 3) Spacer(Modifier.width(5.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (pw.isEmpty()) VaultDim else color, fontSize = 12.sp, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun Feature(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(ZenithGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = ZenithGreen, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = VaultText.copy(alpha = 0.86f), fontSize = 14.sp)
    }
}

@Composable
private fun WarningCard(text: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    val shape = ZenithCutShape(10.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VaultRed.copy(alpha = 0.1f))
            .border(0.8.dp, VaultRed.copy(alpha = 0.4f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = VaultRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = VaultText, fontSize = 14.sp, lineHeight = 19.sp)
        }
        content()
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(ZenithCutShape(6.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(ZenithCutShape(5.dp))
                .background(if (checked) ZenithGreen else Color.Transparent)
                .border(1.dp, if (checked) ZenithGreen else VaultDim, ZenithCutShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF04140B), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = VaultText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A dialog in ZENITH's metal. */
@Composable
private fun VaultDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = ZenithCutShape(18.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(VaultPanelHigh, VaultPanel)))
                .drawBehind { drawZenithOutlineEdge(shape.createOutline(size, layoutDirection, this)) }
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(title, color = VaultText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun WorkOverlay(work: VaultWork) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            VaultEmblem(120.dp, open = true, busy = true)
            Spacer(Modifier.height(18.dp))
            Text(work.title, color = VaultText, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(220.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                if (work.fraction >= 0f) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(work.fraction.coerceIn(0.02f, 1f)).background(ZenithGreen))
                } else {
                    val loop = rememberInfiniteTransition(label = "work")
                    val x by loop.animateFloat(-0.3f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "x")
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.3f)
                            .graphicsLayer { translationX = x * 220.dp.toPx() }
                            .background(ZenithGreen),
                    )
                }
            }
        }
    }
}

private fun sizeText(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

// ---- Setting it up ----

@Composable
private fun VaultSetup(host: VaultHost, onBack: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = "Tresor wird geladen …"
            scope.launch {
                val result = withContext(Dispatchers.IO) { ZenithVault.importVault(context, uri) }
                busy = null
                when (result) {
                    ZenithVault.Import.Ok -> onReady()
                    ZenithVault.Import.NotAVault -> message = "Das ist keine ZENITH-Tresor-Sicherung."
                    ZenithVault.Import.Failed -> message = "Laden ging nicht."
                }
            }
        }
    }
    val ready = pw.length >= ZenithCrypto.MIN_PASSWORD && pw == again && understood && busy == null
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        VaultTopBar(onBack)
        Spacer(Modifier.height(10.dp))
        VaultEmblem(150.dp, open = false, busy = busy != null, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text(
            "ZENITH-Tresor",
            color = VaultText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Fotos, Videos und Dateien – verschlüsselt mit einem Passwort, das nur du kennst. Alles bleibt auf diesem Handy.",
            color = VaultDim,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        )
        Feature("AES-256, jede Datei einzeln versiegelt")
        Feature("Keine Screenshots, unsichtbar in den letzten Apps")
        Feature("Originale erst löschen, wenn die Kopie geprüft ist")
        Feature("Auf Wunsch mit Fingerabdruck")
        Spacer(Modifier.height(18.dp))
        PasswordField(pw, { pw = it }, "Passwort (mind. ${ZenithCrypto.MIN_PASSWORD} Zeichen)")
        StrengthBar(pw)
        Spacer(Modifier.height(10.dp))
        PasswordField(again, { again = it }, "Passwort wiederholen", last = true)
        if (again.isNotEmpty() && again != pw) {
            Text("Die Passwörter sind nicht gleich.", color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        WarningCard("Vergisst du das Passwort, ist alles im Tresor für immer verloren. Niemand kann es zurückholen – auch ZENITH nicht. Schreib es dir an einem sicheren Ort auf.") {
            CheckRow("Verstanden", understood) { understood = it }
        }
        Spacer(Modifier.height(18.dp))
        VaultButton("Tresor einrichten", Modifier.fillMaxWidth(), enabled = ready, icon = Icons.Rounded.Lock) {
            busy = "Schlüssel wird erzeugt …"
            val password = pw.toCharArray()
            scope.launch {
                val ok = withContext(Dispatchers.Default) { runCatching { ZenithVault.create(context, password) }.isSuccess }
                password.fill(' ')
                busy = null
                pw = ""
                again = ""
                if (ok) onReady() else message = "Einrichten ging nicht."
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultButton("Gesicherten Tresor laden", Modifier.fillMaxWidth(), primary = false, enabled = busy == null) {
            host.stayOpen()
            runCatching { importer.launch(arrayOf("*/*")) }
        }
        message?.let { Text(it, color = VaultRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(28.dp))
    }
    busy?.let { WorkOverlay(VaultWork(it, -1f)) }
}

// ---- Locked ----

@Composable
private fun VaultLocked(host: VaultHost, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val fingerprint = remember { host.fingerprintAvailable() && ZenithVault.fingerprintOn(context) }

    fun opened(result: ZenithVault.Unlock) {
        when (result) {
            ZenithVault.Unlock.Ok -> {
                pw = ""
                wrong = 0
            }
            ZenithVault.Unlock.WrongPassword -> {
                wrong++
                message = "Falsches Passwort."
                if (wrong >= 5) {
                    waitUntil = System.currentTimeMillis() + 30_000L * (wrong - 4)
                    message = "Zu viele Versuche – kurz warten."
                }
                scope.launch {
                    for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(55))
                }
            }
            ZenithVault.Unlock.Damaged -> message = "Der Tresor ist beschädigt und lässt sich nicht öffnen."
        }
    }

    fun submit() {
        if (pw.isEmpty() || busy) return
        if (System.currentTimeMillis() < waitUntil) {
            message = "Noch ${((waitUntil - System.currentTimeMillis()) / 1000L) + 1} s warten."
            return
        }
        busy = true
        message = null
        val password = pw.toCharArray()
        scope.launch {
            val result = withContext(Dispatchers.Default) { ZenithVault.unlock(context, password) }
            password.fill(' ')
            busy = false
            opened(result)
        }
    }

    fun askFingerprint() {
        val cipher = ZenithVault.fingerprintUnlockCipher(context)
        if (cipher == null) {
            message = "Der Fingerabdruck ist aus, weil sich die Fingerabdrücke des Handys geändert haben. Bitte das Passwort verwenden."
            return
        }
        host.askFingerprint(cipher, "ZENITH-Tresor entsperren") { done, error ->
            if (done != null) {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { ZenithVault.finishFingerprintUnlock(context, done) }
                    busy = false
                    opened(result)
                }
            } else if (error != null) {
                message = error
            }
        }
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (fingerprint) {
            // Only once the screen is really in front (it may have locked in the background).
            lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            delay(350)
            askFingerprint()
        } else {
            runCatching { focus.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VaultTopBar(onBack)
        Spacer(Modifier.height(36.dp))
        VaultEmblem(170.dp, open = false, busy = busy)
        Spacer(Modifier.height(20.dp))
        Text("Gesperrt", color = VaultText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (busy) "Wird entschlüsselt …" else "Gib dein Tresor-Passwort ein.",
            color = VaultDim,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
        )
        Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) {
            PasswordField(pw, { pw = it }, "Passwort", Modifier.focusRequester(focus), last = true, onDone = { submit() })
        }
        Spacer(Modifier.height(14.dp))
        VaultButton("Entsperren", Modifier.fillMaxWidth(), enabled = pw.isNotEmpty() && !busy) { submit() }
        if (fingerprint) {
            Spacer(Modifier.height(10.dp))
            VaultButton("Mit Fingerabdruck", Modifier.fillMaxWidth(), primary = false, enabled = !busy) { askFingerprint() }
        }
        message?.let {
            Text(it, color = VaultRed, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "Verschlüsselt mit AES-256 · nur auf diesem Handy",
            color = VaultDim.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ---- Open ----

private class AddedBatch(val originals: List<Uri>, val failed: Int)

@Composable
private fun VaultOpen(
    host: VaultHost,
    onBack: () -> Unit,
    onGone: () -> Unit,
    incoming: List<Uri> = emptyList(),
    onIncomingTaken: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by ZenithVault.entries.collectAsState()
    var filter by remember { mutableStateOf<ZenithVault.Kind?>(null) }
    var viewing by remember { mutableStateOf<ZenithVault.Entry?>(null) }
    var menu by remember { mutableStateOf(false) }
    var work by remember { mutableStateOf<VaultWork?>(null) }
    var batch by remember { mutableStateOf<AddedBatch?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<ZenithVault.Entry?>(null) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    // A note being written: the entry (null for a new one); writing at all.
    var noteEntry by remember { mutableStateOf<ZenithVault.Entry?>(null) }
    var writing by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(note) {
        if (note != null) {
            delay(4200)
            note = null
        }
    }

    fun addAll(uris: List<Uri>) {
        scope.launch {
            val results = mutableListOf<ZenithVault.Added>()
            uris.forEachIndexed { i, uri ->
                work = VaultWork("Verschlüssele ${i + 1} von ${uris.size} …", i.toFloat() / uris.size)
                var shown = -1
                results += withContext(Dispatchers.IO) {
                    ZenithVault.add(context, uri) { done, size ->
                        if (size > 0) {
                            val f = (i + done.toFloat() / size) / uris.size
                            val step = (f * 100).toInt()
                            if (step != shown) {
                                shown = step
                                work = VaultWork("Verschlüssele ${i + 1} von ${uris.size} …", f)
                            }
                        }
                    }
                }
            }
            work = null
            val ok = results.filter { it.entry != null }
            val failed = results.size - ok.size
            if (ok.isNotEmpty()) {
                batch = AddedBatch(ok.map { it.uri }, failed)
            } else {
                note = "Konnte nicht in den Tresor gelegt werden."
            }
        }
    }

    val addMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) addAll(uris)
    }
    // ZENITH 19.1: what was shared into the vault goes in as soon as it's open.
    LaunchedEffect(incoming) {
        if (incoming.isNotEmpty()) {
            val shared = incoming
            onIncomingTaken()
            addAll(shared)
        }
    }
    // Photos from the gallery: Android itself asks before they're deleted (Android 11+).
    var deletedFirst by remember { mutableStateOf(0) }
    val mediaDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        note = if (result.resultCode == Activity.RESULT_OK) {
            "Originale gelöscht – sie liegen jetzt nur noch verschlüsselt im Tresor."
        } else if (deletedFirst > 0) {
            "$deletedFirst gelöscht, die übrigen Originale bleiben."
        } else {
            "Die Originale bleiben, wo sie sind."
        }
    }

    // The vault's camera: the photo goes straight in, encrypted; the moment's copy is wiped.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = shot
        shot = null
        if (file != null) {
            if (taken && file.length() > 0L) {
                work = VaultWork("Foto wird verschlüsselt …", -1f)
                scope.launch {
                    val added = withContext(Dispatchers.IO) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        ZenithVault.add(context, uri).also { file.delete() }
                    }
                    work = null
                    note = if (added.entry != null) "Foto verschlüsselt im Tresor – es liegt nirgends sonst." else "Das Foto ging nicht in den Tresor."
                }
            } else {
                file.delete()
            }
        }
    }

    fun takePhoto() {
        val folder = ZenithVault.cameraDir(context).apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(Date())
        val file = java.io.File(folder, "ZENITH_$stamp.jpg")
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()
        if (uri == null) {
            note = "Die Kamera geht gerade nicht."
            return
        }
        shot = file
        host.stayOpen()
        runCatching { camera.launch(uri) }.onFailure {
            shot = null
            note = "Keine Kamera-App gefunden."
        }
    }
    val exportOne = rememberLauncherForActivityResult(CreateTyped()) { uri ->
        val entry = exporting
        exporting = null
        if (uri != null && entry != null) {
            work = VaultWork("Entschlüssele „${entry.name}“ …", -1f)
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ZenithVault.export(context, entry, uri) }
                work = null
                note = if (ok) "Exportiert – die Datei ist jetzt unverschlüsselt dort, wo du sie gespeichert hast." else "Exportieren ging nicht."
            }
        }
    }
    val exportVault = rememberLauncherForActivityResult(CreateTyped()) { uri ->
        if (uri != null) {
            work = VaultWork("Tresor wird gesichert …", 0f)
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ZenithVault.exportVault(context, uri) { done, whole ->
                        if (whole > 0) work = VaultWork("Tresor wird gesichert …", done.toFloat() / whole)
                    }
                }
                work = null
                note = if (ok) "Tresor gesichert – verschlüsselt, mit demselben Passwort." else "Sichern ging nicht."
            }
        }
    }

    fun openElsewhere(entry: ZenithVault.Entry) {
        work = VaultWork("Entschlüssele „${entry.name}“ …", -1f)
        scope.launch {
            val file = withContext(Dispatchers.IO) { ZenithVault.openCopy(context, entry) }
            work = null
            if (file == null) {
                note = "Öffnen ging nicht."
                return@launch
            }
            val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()
            if (uri == null) {
                note = "Öffnen ging nicht."
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, entry.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            host.stayOpen()
            runCatching { context.startActivity(intent) }.onFailure { note = "Keine App kann diese Datei öffnen." }
        }
    }

    val shown = entries.filter { filter == null || it.kind == filter }
    val total = entries.sumOf { it.size }

    fun shareCopy(entry: ZenithVault.Entry) {
        work = VaultWork("Entschlüssele „${entry.name}“ …", -1f)
        scope.launch {
            val file = withContext(Dispatchers.IO) { ZenithVault.openCopy(context, entry) }
            work = null
            val uri = file?.let { runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }.getOrNull() }
            if (uri == null) {
                note = "Teilen ging nicht."
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType(entry.mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .setClipData(android.content.ClipData.newRawUri(entry.name, uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            host.stayOpen()
            runCatching { context.startActivity(Intent.createChooser(send, "„${entry.name}“ teilen")) }
                .onSuccess { note = "Geteilt wird eine unverschlüsselte Kopie – im Tresor bleibt alles verschlüsselt." }
                .onFailure { note = "Keine App zum Teilen gefunden." }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.padding(horizontal = 14.dp)) {
            VaultTopBar(onBack) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { ZenithVault.lock(context) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Sperren", tint = ZenithGreen)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr", tint = VaultText)
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (entries.size == 1) "1 Element" else "${entries.size} Elemente",
                                color = VaultText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("${sizeText(context, total)} · AES-256 verschlüsselt", color = VaultDim, fontSize = 14.sp)
                        }
                        VaultEmblem(64.dp, open = true, busy = false)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        AddTile("Fotos", Modifier.weight(1f), primary = true, glyph = { GlyphIcon(Glyph.Tiles, it, Modifier.size(22.dp)) }) {
                            host.stayOpen()
                            runCatching { addMedia.launch(arrayOf("image/*", "video/*")) }
                        }
                        Spacer(Modifier.width(6.dp))
                        AddTile("Kamera", Modifier.weight(1f), glyph = { GlyphIcon(Glyph.Camera, it, Modifier.size(22.dp)) }) { takePhoto() }
                        Spacer(Modifier.width(6.dp))
                        AddTile("Notiz", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = it, modifier = Modifier.size(22.dp)) }) {
                            noteEntry = null
                            writing = true
                        }
                        Spacer(Modifier.width(6.dp))
                        AddTile("Dateien", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.Add, contentDescription = null, tint = it, modifier = Modifier.size(22.dp)) }) {
                            host.stayOpen()
                            runCatching { addMedia.launch(arrayOf("*/*")) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 8.dp),
                    ) {
                        val kinds = listOf(null, ZenithVault.Kind.Photo, ZenithVault.Kind.Video, ZenithVault.Kind.Note, ZenithVault.Kind.File)
                        kinds.forEach { kind ->
                            val count = entries.count { kind == null || it.kind == kind }
                            val label = when (kind) {
                                null -> "Alle"
                                ZenithVault.Kind.Photo -> "Fotos"
                                ZenithVault.Kind.Video -> "Videos"
                                ZenithVault.Kind.Note -> "Notizen"
                                ZenithVault.Kind.File -> "Dateien"
                            }
                            val on = filter == kind
                            val shape = ZenithCutShape(7.dp)
                            Text(
                                "$label  $count",
                                color = if (on) Color(0xFF04140B) else VaultText,
                                fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(shape)
                                    .background(if (on) ZenithGreen else VaultPanel)
                                    .border(0.8.dp, Color.White.copy(alpha = if (on) 0f else 0.12f), shape)
                                    .clickable { filter = kind }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { entry ->
                VaultTile(entry, thumbs) {
                    if (entry.kind == ZenithVault.Kind.Note) {
                        noteEntry = entry
                        writing = true
                    } else {
                        viewing = entry
                    }
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (entries.isEmpty()) "Noch leer. Leg Fotos, Videos oder Dateien hinein – sie werden hier verschlüsselt, die Originale kannst du danach löschen." else "Hier ist nichts von dieser Art.",
                        color = VaultDim,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 40.dp),
                    )
                }
            }
        }
    }

    if (writing) {
        NoteEditor(
            entry = noteEntry,
            onDone = { message ->
                writing = false
                noteEntry = null
                if (message != null) note = message
            },
        )
    }

    viewing?.let { first ->
        // Swiping goes through what's shown right now (notes open in their own editor).
        val pages = shown.filter { it.kind != ZenithVault.Kind.Note }
        VaultViewer(
            entries = pages,
            start = pages.indexOfFirst { it.id == first.id }.coerceAtLeast(0),
            thumbs = thumbs,
            onClose = { viewing = null },
            onOpen = { openElsewhere(it) },
            onShare = { shareCopy(it) },
            onExport = { entry ->
                exporting = entry
                host.stayOpen()
                runCatching { exportOne.launch(entry.mime to entry.name) }
            },
            onDelete = { entry ->
                runCatching { ZenithVault.remove(context, entry) }
                thumbs.remove(entry.id)
                if (pages.size <= 1) viewing = null
                note = "Aus dem Tresor gelöscht."
            },
        )
    }

    batch?.let { added ->
        val n = added.originals.size
        VaultDialog(if (n == 1) "1 Datei ist sicher im Tresor" else "$n Dateien sind sicher im Tresor", onDismiss = { batch = null }) {
            Text(
                "Verschlüsselt und Stück für Stück mit dem Original verglichen." +
                    (if (added.failed > 0) " ${added.failed} ging${if (added.failed == 1) "" else "en"} nicht." else "") +
                    "\n\nDie Originale liegen noch unverschlüsselt auf dem Handy. Jetzt löschen?",
                color = VaultDim,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            VaultButton("Originale löschen", Modifier.fillMaxWidth(), danger = true, icon = Icons.Rounded.Delete) {
                batch = null
                work = VaultWork("Originale werden gelöscht …", -1f)
                scope.launch {
                    // Files picked here go directly; photos shared from the gallery go after
                    // Android has asked.
                    val (documents, others) = added.originals.partition { DocumentsContract.isDocumentUri(context, it) }
                    val gone = withContext(Dispatchers.IO) { documents.count { ZenithVault.deleteOriginal(context, it) } }
                    val media = others.filter { it.authority == MediaStore.AUTHORITY }
                    work = null
                    if (media.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        deletedFirst = gone
                        val request = runCatching { MediaStore.createDeleteRequest(context.contentResolver, media) }.getOrNull()
                        if (request != null) {
                            host.stayOpen()
                            runCatching { mediaDelete.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
                            return@launch
                        }
                    }
                    val left = n - gone
                    note = if (left == 0) {
                        "Originale gelöscht. Schau auch im Papierkorb der Galerie nach."
                    } else {
                        "$gone gelöscht, $left nicht (diese App erlaubt es nicht) – lösch sie in der Galerie."
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            VaultButton("Behalten", Modifier.fillMaxWidth(), primary = false) { batch = null }
        }
    }

    if (menu) {
        VaultMenu(
            host = host,
            onDismiss = { menu = false },
            onNote = { note = it },
            onExportVault = {
                menu = false
                host.stayOpen()
                runCatching { exportVault.launch("application/octet-stream" to "ZENITH-Tresor.ztresor") }
            },
            onDestroyed = {
                menu = false
                onGone()
            },
        )
    }

    work?.let { WorkOverlay(it) }

    note?.let {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            val shape = ZenithCutShape(10.dp)
            Text(
                it,
                color = VaultText,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(shape)
                    .background(VaultPanelHigh)
                    .border(0.8.dp, ZenithGreen.copy(alpha = 0.5f), shape)
                    .clickable { note = null }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun VaultTile(entry: ZenithVault.Entry, thumbs: MutableMap<String, ImageBitmap>, onClick: () -> Unit) {
    val context = LocalContext.current
    val picture = thumbs[entry.id]
    LaunchedEffect(entry.id) {
        if (picture == null && entry.thumb) {
            val bitmap = withContext(Dispatchers.IO) {
                ZenithVault.thumbnail(context, entry)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) thumbs[entry.id] = bitmap.asImageBitmap()
        }
    }
    val shape = ZenithCutShape(10.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(VaultPanel)
            .border(0.6.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick),
    ) {
        if (picture != null) {
            Image(picture, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (entry.kind == ZenithVault.Kind.Note) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Text("NOTIZ", color = ZenithGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.name.removeSuffix(".txt"),
                    color = VaultText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    entry.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "DATEI" },
                    color = ZenithGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Text(entry.name, color = VaultDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (entry.kind == ZenithVault.Kind.Video) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---- Looking at them ----

/**
 * The vault's viewer: swipe from one to the next, pinch or double tap to zoom a photo, and a
 * slideshow that moves on by itself. What it shows is [entries], starting at [start].
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VaultViewer(
    entries: List<ZenithVault.Entry>,
    start: Int,
    thumbs: Map<String, ImageBitmap>,
    onClose: () -> Unit,
    onOpen: (ZenithVault.Entry) -> Unit,
    onShare: (ZenithVault.Entry) -> Unit,
    onExport: (ZenithVault.Entry) -> Unit,
    onDelete: (ZenithVault.Entry) -> Unit,
) {
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var confirm by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = start.coerceIn(0, entries.size - 1),
        pageCount = { entries.size },
    )
    BackHandler {
        when {
            confirm -> confirm = false
            playing -> playing = false
            else -> onClose()
        }
    }
    // The slideshow: on to the next every few seconds, round and round.
    LaunchedEffect(playing, entries.size) {
        while (playing && entries.size > 1) {
            delay(3200)
            pager.animateScrollToPage((pager.currentPage + 1) % entries.size)
        }
    }
    val current = entries[pager.currentPage.coerceIn(0, entries.size - 1)]
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { entries[it].id },
            beyondViewportPageCount = 1,
        ) { page ->
            ViewerPage(entries[page], thumbs[entries[page].id])
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(current.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entries.size > 1) {
                    Text("${pager.currentPage + 1} von ${entries.size}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            if (entries.size > 1) {
                val chip = ZenithCutShape(7.dp)
                Row(
                    Modifier
                        .clip(chip)
                        .background(if (playing) ZenithGreen else Color.White.copy(alpha = 0.1f))
                        .clickable { playing = !playing }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = if (playing) Color(0xFF04140B) else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (playing) "Stopp" else "Diashow",
                        color = if (playing) Color(0xFF04140B) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            AddTile("Öffnen", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = it, modifier = Modifier.size(20.dp)) }) {
                playing = false
                onOpen(current)
            }
            Spacer(Modifier.width(6.dp))
            AddTile("Teilen", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.Share, contentDescription = null, tint = it, modifier = Modifier.size(20.dp)) }) {
                playing = false
                onShare(current)
            }
            Spacer(Modifier.width(6.dp))
            AddTile("Export", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = it, modifier = Modifier.size(20.dp)) }) {
                playing = false
                onExport(current)
            }
            Spacer(Modifier.width(6.dp))
            AddTile("Löschen", Modifier.weight(1f), glyph = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = VaultRed, modifier = Modifier.size(20.dp)) }) {
                playing = false
                confirm = true
            }
        }
        if (confirm) {
            VaultDialog("Endgültig löschen?", onDismiss = { confirm = false }) {
                Text("„${current.name}“ wird aus dem Tresor gelöscht. Hast du kein Original mehr, ist es danach weg.", color = VaultDim, fontSize = 15.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                VaultButton("Löschen", Modifier.fillMaxWidth(), danger = true) {
                    confirm = false
                    onDelete(current)
                }
                Spacer(Modifier.height(8.dp))
                VaultButton("Abbrechen", Modifier.fillMaxWidth(), primary = false) { confirm = false }
            }
        }
    }
}

/** One page of the viewer: a photo to zoom, or what the file is. */
@Composable
private fun ViewerPage(entry: ZenithVault.Entry, thumb: ImageBitmap?) {
    val context = LocalContext.current
    val photo = entry.kind == ZenithVault.Kind.Photo && entry.size <= ZenithVault.VIEW_LIMIT
    val picture by produceState<Pair<Boolean, ImageBitmap?>>(false to null, entry.id) {
        value = if (!photo) {
            true to null
        } else {
            true to withContext(Dispatchers.IO) {
                ZenithVault.readAll(context, entry)?.let { ZenithVault.decode(it, 2560) }?.asImageBitmap()
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val shown = picture.second ?: thumb
    if (photo && shown != null) {
        Image(
            shown,
            contentDescription = entry.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // Two fingers zoom; one finger only moves a zoomed photo – otherwise it swipes on.
                .pointerInput(entry.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1 || scale > 1.01f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                pan = if (scale > 1.01f) pan + event.calculatePan() else Offset.Zero
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(entry.id) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1.1f) 1f else 2.5f
                        pan = Offset.Zero
                    })
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
        )
    } else {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (thumb != null) {
                Image(
                    thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(220.dp).clip(ZenithCutShape(16.dp)),
                )
            } else {
                VaultEmblem(120.dp, open = true, busy = !picture.first)
            }
            Spacer(Modifier.height(18.dp))
            Text(entry.name, color = VaultText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                "${sizeText(context, entry.size)} · im Tresor seit ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.added))}",
                color = VaultDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (photo && picture.first && picture.second == null) {
                Text("Das Foto lässt sich hier nicht zeigen – öffne es in einer App.", color = VaultDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

// ---- The menu ----

@Composable
private fun VaultMenu(
    host: VaultHost,
    onDismiss: () -> Unit,
    onNote: (String) -> Unit,
    onExportVault: () -> Unit,
    onDestroyed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("menu") }
    var fingerprint by remember { mutableStateOf(ZenithVault.fingerprintOn(context)) }
    val canFingerprint = remember { host.fingerprintAvailable() }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (page == "menu") onDismiss() else page = "menu" }

    when (page) {
        "menu" -> VaultDialog("Tresor", onDismiss) {
            if (canFingerprint) {
                MenuRow(
                    if (fingerprint) "Fingerabdruck ausschalten" else "Mit Fingerabdruck entsperren",
                    if (fingerprint) "Dann nur noch mit dem Passwort" else "Schneller öffnen – das Passwort bleibt der Generalschlüssel",
                ) {
                    if (fingerprint) {
                        ZenithVault.disableFingerprint(context)
                        fingerprint = false
                        onNote("Fingerabdruck ist aus.")
                    } else {
                        val cipher = ZenithVault.fingerprintEnableCipher()
                        if (cipher == null) {
                            onNote("Fingerabdruck geht auf diesem Handy nicht.")
                        } else {
                            host.askFingerprint(cipher, "Fingerabdruck für den Tresor") { done, why ->
                                if (done != null && ZenithVault.finishFingerprintEnable(context, done)) {
                                    fingerprint = true
                                    onNote("Der Tresor öffnet jetzt auch mit deinem Fingerabdruck.")
                                } else if (why != null) {
                                    onNote(why)
                                }
                            }
                        }
                    }
                }
            }
            MenuRow("Passwort ändern", "Der Inhalt bleibt, wie er ist") { page = "password" }
            MenuRow("Tresor sichern", "Eine verschlüsselte Datei mit allem – für die Cloud oder ein neues Handy", onClick = onExportVault)
            MenuRow("Tresor löschen", "Alles darin, endgültig", danger = true) { page = "destroy" }
            Spacer(Modifier.height(10.dp))
            Text(
                "AES-256-GCM · PBKDF2-SHA256, ${ZenithCrypto.ITERATIONS / 1000}k Runden · ${sizeText(context, ZenithVault.storedSize(context))} belegt",
                color = VaultDim.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        "password" -> VaultDialog("Passwort ändern", { page = "menu" }) {
            PasswordField(old, { old = it }, "Jetziges Passwort")
            Spacer(Modifier.height(10.dp))
            PasswordField(new, { new = it }, "Neues Passwort")
            StrengthBar(new)
            Spacer(Modifier.height(10.dp))
            PasswordField(again, { again = it }, "Neues Passwort wiederholen", last = true)
            error?.let { Text(it, color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            VaultButton(
                if (busy) "Einen Moment …" else "Ändern",
                Modifier.fillMaxWidth(),
                enabled = !busy && old.isNotEmpty() && new.length >= ZenithCrypto.MIN_PASSWORD && new == again,
            ) {
                busy = true
                error = null
                val a = old.toCharArray()
                val b = new.toCharArray()
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { runCatching { ZenithVault.changePassword(context, a, b) }.getOrDefault(false) }
                    a.fill(' ')
                    b.fill(' ')
                    busy = false
                    if (ok) {
                        onNote("Neues Passwort gilt ab jetzt. Ältere Tresor-Sicherungen öffnen weiter mit dem alten.")
                        onDismiss()
                    } else {
                        error = "Das jetzige Passwort stimmt nicht."
                    }
                }
            }
        }
        else -> VaultDialog("Tresor löschen?", { page = "menu" }) {
            WarningCard("Alles im Tresor wird endgültig gelöscht. Nur was du exportiert oder gesichert hast, bleibt.")
            Spacer(Modifier.height(12.dp))
            PasswordField(old, { old = it }, "Passwort zur Bestätigung", last = true)
            error?.let { Text(it, color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            VaultButton("Endgültig löschen", Modifier.fillMaxWidth(), danger = true, enabled = !busy && old.isNotEmpty()) {
                busy = true
                val a = old.toCharArray()
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { ZenithVault.verify(context, a) }
                    a.fill(' ')
                    if (ok) {
                        withContext(Dispatchers.IO) { ZenithVault.destroy(context) }
                        busy = false
                        onDestroyed()
                    } else {
                        busy = false
                        error = "Falsches Passwort."
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, description: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ZenithCutShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
    ) {
        Text(label, color = if (danger) VaultRed else VaultText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(description, color = VaultDim, fontSize = 13.sp)
    }
}

/** One of the ways to put something into the vault: a sign and a word. */
@Composable
private fun AddTile(label: String, modifier: Modifier = Modifier, primary: Boolean = false, glyph: @Composable (Color) -> Unit, onClick: () -> Unit) {
    val shape = ZenithCutShape(10.dp)
    val tint = if (primary) Color(0xFF04140B) else ZenithGreen
    Column(
        modifier
            .clip(shape)
            .background(
                if (primary) {
                    Brush.linearGradient(listOf(Color(0xFF3FE08C), ZenithGreenDeep))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF22282C), Color(0xFF15191C)))
                },
            )
            .border(0.8.dp, Color.White.copy(alpha = if (primary) 0.3f else 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        glyph(tint)
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (primary) tint else VaultText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---- Notes ----

/**
 * A note in the vault: written here, kept encrypted like everything else. [entry] is the note
 * to change, null for a new one; [onDone] gets what to tell (null: nothing happened).
 */
@Composable
private fun NoteEditor(entry: ZenithVault.Entry?, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fresh = remember { "Notiz " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) }
    var title by remember(entry?.id) { mutableStateOf(entry?.name?.removeSuffix(".txt") ?: "") }
    var body by remember(entry?.id) { mutableStateOf("") }
    // What the note said when opened (null while it's still being decrypted).
    var original by remember(entry?.id) { mutableStateOf<String?>(if (entry == null) "" else null) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(entry?.id) {
        if (entry != null) {
            val text = withContext(Dispatchers.IO) { ZenithVault.readText(context, entry) }
            body = text ?: ""
            original = text ?: ""
        }
    }

    fun close() {
        if (saving) return
        val loaded = original ?: return onDone(null)
        val name = title.trim().ifEmpty { fresh }.replace('/', '-').take(80) + ".txt"
        val changed = body != loaded || (entry != null && name != entry.name)
        if (!changed || (entry == null && body.isBlank() && title.isBlank())) {
            onDone(null)
            return
        }
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                ZenithVault.putBytes(context, name, "text/plain", body.toByteArray(Charsets.UTF_8), replacing = entry)
            }
            saving = false
            onDone(if (saved != null) "Notiz verschlüsselt gespeichert." else "Speichern ging nicht.")
        }
    }

    BackHandler { if (confirm) confirm = false else close() }
    Column(
        Modifier
            .fillMaxSize()
            .background(VaultBg)
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { close() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = VaultText)
            }
            Spacer(Modifier.width(6.dp))
            ZenithMark(color = ZenithGreen, width = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("NOTIZ", color = VaultText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            if (entry != null) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { confirm = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Löschen", tint = VaultRed)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { close() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = "Fertig", tint = ZenithGreen)
            }
        }
        BasicTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = VaultText, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(ZenithGreen),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) Text(fresh, color = VaultDim.copy(alpha = 0.6f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(ZenithGreen.copy(alpha = 0.7f), Color.Transparent))),
        )
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            enabled = original != null,
            textStyle = androidx.compose.ui.text.TextStyle(color = VaultText, fontSize = 17.sp, lineHeight = 25.sp),
            cursorBrush = SolidColor(ZenithGreen),
            modifier = Modifier.fillMaxWidth().weight(1f),
            decorationBox = { inner ->
                Box {
                    if (body.isEmpty()) {
                        Text(
                            if (original == null) "Wird entschlüsselt …" else "Schreib etwas – nur du kannst es lesen.",
                            color = VaultDim.copy(alpha = 0.6f),
                            fontSize = 17.sp,
                        )
                    }
                    inner()
                }
            },
        )
        Text(
            "Verschlüsselt mit AES-256 · nur in deinem Tresor",
            color = VaultDim.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
    if (confirm && entry != null) {
        VaultDialog("Notiz löschen?", onDismiss = { confirm = false }) {
            Text("„${entry.name.removeSuffix(".txt")}“ wird endgültig gelöscht.", color = VaultDim, fontSize = 15.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            VaultButton("Löschen", Modifier.fillMaxWidth(), danger = true) {
                confirm = false
                runCatching { ZenithVault.remove(context, entry) }
                onDone("Notiz gelöscht.")
            }
            Spacer(Modifier.height(8.dp))
            VaultButton("Abbrechen", Modifier.fillMaxWidth(), primary = false) { confirm = false }
        }
    }
    if (saving) WorkOverlay(VaultWork("Wird verschlüsselt …", -1f))
}
