package dev.hearth.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AiProvider
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.Perfume
import dev.hearth.launcher.data.PerfumeImageHit
import dev.hearth.launcher.data.PerfumeImages
import dev.hearth.launcher.data.PerfumeShelf
import dev.hearth.launcher.data.PerfumeTaste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PerfumeLove = Color(0xFF2FD27A)
private val PerfumeDislike = Color(0xFFFF5A5F)

/**
 * ZENITH 19.7, Clawd's perfume shelf: tell him which perfumes you have – written down or as a
 * photo of one or several – and he looks at each, remembers it, and every day suggests the one
 * for the weather. Mark which you like and which not; he never picks one you don't.
 */
@Composable
fun PerfumeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { PerfumeShelf.init(context) }
    val shelf by PerfumeShelf.shelf.collectAsState()
    val pick by PerfumeShelf.pick.collectAsState()
    val city by PerfumeShelf.city.collectAsState()
    val choosing by PerfumeShelf.busy.collectAsState()
    val look by PerfumeShelf.look.collectAsState()
    val fetching by PerfumeShelf.fetching.collectAsState()
    val ai by ClaudeAssistant.settings.collectAsState()
    val hasKey = ai.hasKey
    var text by remember { mutableStateOf("") }
    var cityText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    var keyText by remember(ai.provider) { mutableStateOf("") }
    // The pictures the web found for one perfume, to choose from.
    var findFor by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<PerfumeImageHit>>(emptyList()) }
    var finding by remember { mutableStateOf(false) }
    BackHandler { onClose() }
    LaunchedEffect(shelf.size) {
        PerfumeShelf.refreshToday()
        PerfumeShelf.searchPictures()
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            message = "${look.display} schaut sich ${if (uris.size == 1) "das Foto" else "die ${minOf(uris.size, 6)} Fotos"} an …"
            val images = withContext(Dispatchers.IO) { uris.take(6).mapNotNull { PerfumeShelf.readPhoto(context, it) } }
            val result = PerfumeShelf.addFromPhotos(images, text)
            message = result.message
            if (result.added.isNotEmpty()) text = ""
            working = false
            PerfumeShelf.refreshToday()
        }
    }
    // A picture of the owner's own for one perfume.
    val onePhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = photoFor
        if (uri != null && id != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { PerfumeShelf.readPhoto(context, uri) }
                message = if (bytes != null && PerfumeShelf.setPhoto(id, bytes)) "Das Bild ist gespeichert." else "Das Bild konnte ich nicht öffnen."
            }
        }
    }
    val startFind: (Perfume) -> Unit = { perfume ->
        findFor = perfume.id
        hits = emptyList()
        finding = true
        scope.launch {
            hits = PerfumeImages.search(perfume.title)
            finding = false
        }
    }
    val applyHit: (String, PerfumeImageHit) -> Unit = { id, hit ->
        scope.launch {
            message = "Das Bild wird geladen …"
            val bytes = withContext(Dispatchers.IO) { PerfumeImages.download(hit.full) ?: PerfumeImages.download(hit.thumb) }
            val saved = bytes != null && PerfumeShelf.setPhoto(id, bytes, web = true)
            message = if (saved) "Das Bild ist gespeichert." else "Dieses Bild ging nicht – nimm ein anderes."
            if (saved) findFor = null
        }
    }
    val addText: () -> Unit = {
        if (!working && text.isNotBlank()) {
            scope.launch {
                working = true
                message = "${look.display} schaut sich das an …"
                val result = PerfumeShelf.addFromText(text)
                message = result.message
                if (result.added.isNotEmpty()) text = ""
                working = false
                PerfumeShelf.refreshToday()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(VaultBg)) {
        VaultBackdrop()
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { VaultTopBar(onClose, title = "${look.display.uppercase()} · DUFT") }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (look.clawd || look.zenith) {
                        PerfumeHelperFigure(
                            look,
                            if (working || choosing) ClawdMood.Thinking else ClawdMood.Idle,
                            Modifier.size(width = (84 * ((if (look.clawd) 1 else 0) + (if (look.zenith) 1 else 0))).dp, height = 72.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text(look.cupboard, color = VaultText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Sag ihm, welche Parfüms du hast – er sucht dir jeden Tag das passende aus.",
                            color = VaultDim,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }

            item {
                StudioPanel("Wer steht daneben?", "Clawd und das ZENITH-Z lassen sich einzeln ein- und ausschalten – im Widget und hier. Beide aus gibt dem Namen am meisten Platz.") {
                    SwitchRow(label = "Clawd zeigen", checked = look.clawd) { PerfumeShelf.setLook(it, look.zenith) }
                    SwitchRow(label = "ZENITH-Z zeigen", checked = look.zenith) { PerfumeShelf.setLook(look.clawd, it) }
                }
            }

            item {
                StudioPanel("Heute") {
                    val chosen = pick?.let { p -> shelf.firstOrNull { it.id == p.perfumeId } }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        when {
                            shelf.isEmpty() -> Text("Noch kein Parfüm im Schrank. Schreib mir unten, welche du hast – oder schick mir ein Foto.", color = VaultDim, fontSize = 14.sp, lineHeight = 19.sp)
                            shelf.none { it.taste != PerfumeTaste.Dislike } -> Text("Alle deine Parfüms stehen auf „Mag ich nicht“. Such mir wenigstens eins aus, das du magst.", color = VaultDim, fontSize = 14.sp, lineHeight = 19.sp)
                            chosen == null -> {
                                Text(if (choosing) "${look.display} überlegt …" else "Für heute gibt es noch keinen Vorschlag.", color = VaultDim, fontSize = 14.sp)
                                if (!choosing) {
                                    Spacer(Modifier.height(10.dp))
                                    VaultButton("Vorschlag holen", Modifier.fillMaxWidth()) { scope.launch { PerfumeShelf.refreshToday(force = true) } }
                                }
                            }
                            else -> {
                                Row(Modifier.fillMaxWidth()) {
                                    PerfumePicture(chosen, Modifier.size(112.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(chosen.title, color = VaultText, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                                        if (chosen.family.isNotBlank()) Text(chosen.family, color = PerfumeLove, fontSize = 13.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(pick?.reason.orEmpty(), color = VaultText, fontSize = 14.sp, lineHeight = 20.sp)
                                    }
                                }
                                pick?.weather?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = VaultDim, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    VaultButton("Trage ich heute", Modifier.weight(1f)) {
                                        PerfumeShelf.markWorn(chosen.id)
                                        message = "Schön! ${look.display} merkt es sich und wechselt morgen ab."
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    VaultButton("Etwas anderes", Modifier.weight(1f), primary = false, enabled = !choosing) {
                                        scope.launch { PerfumeShelf.refreshToday(force = true, exclude = setOf(chosen.id)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                StudioPanel(
                    "Parfüms hinzufügen",
                    "Schreib die Namen (mit Komma) oder schick Fotos von einem oder mehreren Flakons – ${look.display} erkennt sie, merkt sie sich, beschreibt jedes und nimmt das Bild vom Flakon dazu." +
                        if (hasKey) " Fotos klappen mit Claude oder Gemini." else " Für Analyse, Fotos und kluge Tipps trag deinen API-Schlüssel bei „Frag Clawd“ ein – ohne ihn merkt sich ${look.display} nur die Namen.",
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        PerfumeField(text, { text = it }, "z. B. Bleu de Chanel, Sauvage, Black Opium", Modifier.fillMaxWidth().heightIn(min = 76.dp), singleLine = false)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            VaultButton("${look.display}, schau dir das an", Modifier.weight(1f), enabled = !working && text.isNotBlank(), onClick = addText)
                            Spacer(Modifier.width(8.dp))
                            VaultButton("Foto schicken", Modifier.weight(1f), primary = false, enabled = !working && hasKey) { photos.launch("image/*") }
                        }
                        message?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = PerfumeLove, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            item {
                StudioPanel("Wetter", "Für den Tagesvorschlag. Ohne Ort rät ${look.display} nach der Jahreszeit.") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        if (city.isNotBlank()) Text("Aktuell: $city", color = VaultText, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PerfumeField(cityText, { cityText = it }, "Dein Ort, z. B. Köln", Modifier.weight(1f), singleLine = true) {
                                scope.launch {
                                    message = PerfumeShelf.setCity(cityText) ?: "Das Wetter kommt jetzt aus ${PerfumeShelf.city.value}."
                                    PerfumeShelf.refreshToday(force = true)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            VaultButton("Speichern", enabled = cityText.isNotBlank()) {
                                scope.launch {
                                    message = PerfumeShelf.setCity(cityText) ?: "Das Wetter kommt jetzt aus ${PerfumeShelf.city.value}."
                                    PerfumeShelf.refreshToday(force = true)
                                }
                            }
                        }
                    }
                }
            }

            item {
                StudioPanel(
                    "KI für Analyse und Fotos",
                    "Der Schlüssel bleibt auf diesem Handy. Fotos von Flakons brauchen Claude oder Gemini. Derselbe Schlüssel gilt für Clawd im ZENITH-Launcher.",
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiProvider.entries.filter { it != AiProvider.Custom }.forEach { option ->
                                TasteChip(option.label, ai.provider == option, ZenithGreen) { ClaudeAssistant.updateSettings { s -> s.copy(provider = option) } }
                            }
                        }
                        Text(
                            if (hasKey) "Schlüssel für ${ai.provider.label}: gespeichert (…${ai.apiKey.takeLast(4)})" else "Für ${ai.provider.label} ist noch kein Schlüssel gespeichert.",
                            color = if (hasKey) PerfumeLove else VaultDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PerfumeField(keyText, { keyText = it }, ai.provider.keyHint, Modifier.weight(1f), singleLine = true, secret = true) {
                                if (keyText.isNotBlank()) {
                                    ClaudeAssistant.updateSettings { s -> s.withKey(keyText) }
                                    keyText = ""
                                    message = "Der Schlüssel ist gespeichert."
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            VaultButton("Speichern", enabled = keyText.isNotBlank()) {
                                ClaudeAssistant.updateSettings { s -> s.withKey(keyText) }
                                keyText = ""
                                message = "Der Schlüssel ist gespeichert."
                            }
                        }
                        if (ai.provider.keyUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            VaultButton("Schlüssel besorgen", Modifier.fillMaxWidth(), primary = false) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ai.provider.keyUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        }
                    }
                }
            }

            item {
                StudioPanel(
                    "Deine Sammlung · ${shelf.size}",
                    "Tippe auf ein Parfüm für die Einzelheiten. „Mag ich“ bevorzugt ${look.display}, „Mag ich nicht“ schlägt er nie vor.",
                ) {
                    if (shelf.isEmpty()) {
                        Text("Noch leer.", color = VaultDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    if (fetching) {
                        Text("${look.display} sucht die Bilder der Flakons im Netz …", color = PerfumeLove, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    } else if (shelf.any { !it.photo }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            VaultButton("Fehlende Bilder suchen", Modifier.fillMaxWidth(), primary = false) { PerfumeShelf.searchPictures(again = true) }
                        }
                    }
                    shelf.sortedWith(compareBy({ it.taste == PerfumeTaste.Dislike }, { it.title.lowercase() })).forEach { perfume ->
                        PerfumeRow(
                            perfume = perfume,
                            expanded = open == perfume.id,
                            onToggle = { open = if (open == perfume.id) null else perfume.id },
                            onTaste = { PerfumeShelf.setTaste(perfume.id, it) },
                            onDelete = { PerfumeShelf.remove(perfume.id) },
                            onPhoto = {
                                photoFor = perfume.id
                                onePhoto.launch("image/*")
                            },
                            candidates = if (findFor == perfume.id) hits else null,
                            searching = finding && findFor == perfume.id,
                            onFind = { startFind(perfume) },
                            onPick = { hit -> applyHit(perfume.id, hit) },
                        )
                    }
                    if (shelf.any { !it.analysed } && hasKey) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            VaultButton("Unbekannte genauer ansehen", Modifier.fillMaxWidth(), primary = false, enabled = !working) {
                                scope.launch {
                                    working = true
                                    message = "${look.display} schaut genauer hin …"
                                    message = PerfumeShelf.analyseNames().message
                                    working = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerfumeField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean,
    secret: Boolean = false,
    onDone: () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        onValueChange = { onChange(it.take(600)) },
        singleLine = singleLine,
        textStyle = TextStyle(color = VaultText, fontSize = 15.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(PerfumeLove),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(VaultBg)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = VaultDim.copy(alpha = 0.7f), fontSize = 15.sp)
                inner()
            }
        },
    )
}

@Composable
private fun TasteChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else VaultText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun PerfumeRow(
    perfume: Perfume,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTaste: (PerfumeTaste) -> Unit,
    onDelete: () -> Unit,
    onPhoto: () -> Unit,
    candidates: List<PerfumeImageHit>?,
    searching: Boolean,
    onFind: () -> Unit,
    onPick: (PerfumeImageHit) -> Unit,
) {
    val accent = when (perfume.taste) {
        PerfumeTaste.Love -> PerfumeLove
        PerfumeTaste.Dislike -> PerfumeDislike
        PerfumeTaste.Neutral -> Color.White.copy(alpha = 0.25f)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            // Its picture, with a dot in the colour of how you feel about it.
            Box(Modifier.size(56.dp)) {
                PerfumePicture(perfume, Modifier.fillMaxSize(), corner = 14.dp)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.5.dp, VaultPanel, CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    perfume.title,
                    color = if (perfume.taste == PerfumeTaste.Dislike) VaultDim else VaultText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (perfume.analysed) perfume.family + (if (perfume.notes.isNotEmpty()) " · " + perfume.notes.take(3).joinToString(", ") else "") else "Noch nicht genauer angesehen",
                    color = VaultDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Delete, contentDescription = "Entfernen", tint = VaultDim, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TasteChip("Mag ich", perfume.taste == PerfumeTaste.Love, PerfumeLove) { onTaste(PerfumeTaste.Love) }
            TasteChip("Okay", perfume.taste == PerfumeTaste.Neutral, Color.White.copy(alpha = 0.7f)) { onTaste(PerfumeTaste.Neutral) }
            TasteChip("Mag ich nicht", perfume.taste == PerfumeTaste.Dislike, PerfumeDislike) { onTaste(PerfumeTaste.Dislike) }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (perfume.summary.isNotBlank()) Text(perfume.summary, color = VaultText, fontSize = 13.sp, lineHeight = 18.sp)
            if (perfume.notes.isNotEmpty()) PerfumeFact("Noten", perfume.notes.joinToString(", "))
            if (perfume.seasons.isNotEmpty()) PerfumeFact("Jahreszeit", perfume.seasons.joinToString(", "))
            if (perfume.analysed) PerfumeFact("Passt bei", "${perfume.tempMin} bis ${perfume.tempMax} °C")
            if (perfume.occasions.isNotEmpty()) PerfumeFact("Anlass", perfume.occasions.joinToString(", "))
            if (perfume.analysed) PerfumeFact("Intensität", "●".repeat(perfume.strength) + "○".repeat(5 - perfume.strength))
            if (perfume.lastWorn > 0L) {
                PerfumeFact("Zuletzt getragen", SimpleDateFormat("d. MMMM", Locale.GERMAN).format(Date(perfume.lastWorn)))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultButton("Bild suchen", Modifier.weight(1f), primary = false, onClick = onFind)
                VaultButton("Eigenes Foto", Modifier.weight(1f), primary = false, onClick = onPhoto)
            }
            if (candidates != null) {
                when {
                    searching -> Text("Die Suche läuft …", color = VaultDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    candidates.isEmpty() -> Text("Nichts gefunden. Nimm ein eigenes Foto.", color = VaultDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    else -> {
                        Text("Tippe auf das richtige:", color = VaultDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidates.forEach { hit -> RemoteThumb(hit.thumb, Modifier.size(88.dp)) { onPick(hit) } }
                        }
                    }
                }
            }
        }
    }
}

/** A picture from the web, loaded as it's shown; a tap chooses it. */
@Composable
private fun RemoteThumb(url: String, modifier: Modifier, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { PerfumeImages.bitmap(url)?.asImageBitmap() }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F1EC)).clickable(onClick = onClick)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun PerfumeFact(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(label, color = VaultDim, fontSize = 12.sp, modifier = Modifier.width(104.dp))
        Text(value, color = VaultText, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
