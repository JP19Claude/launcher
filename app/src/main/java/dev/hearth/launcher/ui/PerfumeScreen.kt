package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.Perfume
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
    val ai by ClaudeAssistant.settings.collectAsState()
    val hasKey = ai.hasKey
    var text by remember { mutableStateOf("") }
    var cityText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    BackHandler { onClose() }
    LaunchedEffect(shelf.size) { PerfumeShelf.refreshToday() }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            message = "Clawd schaut sich ${if (uris.size == 1) "das Foto" else "die ${minOf(uris.size, 6)} Fotos"} an …"
            val images = withContext(Dispatchers.IO) { uris.take(6).mapNotNull { PerfumeShelf.readPhoto(context, it) } }
            val result = PerfumeShelf.addFromPhotos(images, text)
            message = result.message
            if (result.added.isNotEmpty()) text = ""
            working = false
            PerfumeShelf.refreshToday()
        }
    }
    val addText: () -> Unit = {
        if (!working && text.isNotBlank()) {
            scope.launch {
                working = true
                message = "Clawd schaut sich das an …"
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
            item { VaultTopBar(onClose, title = "CLAWD · DUFT") }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Clawd(Modifier.size(width = 84.dp, height = 72.dp), mood = if (working || choosing) ClawdMood.Thinking else ClawdMood.Idle)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Clawds Duftschrank", color = VaultText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                StudioPanel("Heute") {
                    val chosen = pick?.let { p -> shelf.firstOrNull { it.id == p.perfumeId } }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        when {
                            shelf.isEmpty() -> Text("Noch kein Parfüm im Schrank. Schreib mir unten, welche du hast – oder schick mir ein Foto.", color = VaultDim, fontSize = 14.sp, lineHeight = 19.sp)
                            shelf.none { it.taste != PerfumeTaste.Dislike } -> Text("Alle deine Parfüms stehen auf „Mag ich nicht“. Such mir wenigstens eins aus, das du magst.", color = VaultDim, fontSize = 14.sp, lineHeight = 19.sp)
                            chosen == null -> {
                                Text(if (choosing) "Clawd überlegt …" else "Für heute gibt es noch keinen Vorschlag.", color = VaultDim, fontSize = 14.sp)
                                if (!choosing) {
                                    Spacer(Modifier.height(10.dp))
                                    VaultButton("Vorschlag holen", Modifier.fillMaxWidth()) { scope.launch { PerfumeShelf.refreshToday(force = true) } }
                                }
                            }
                            else -> {
                                Text(chosen.title, color = VaultText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                if (chosen.family.isNotBlank()) Text(chosen.family, color = PerfumeLove, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(pick?.reason.orEmpty(), color = VaultText, fontSize = 15.sp, lineHeight = 21.sp)
                                pick?.weather?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = VaultDim, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    VaultButton("Trage ich heute", Modifier.weight(1f)) {
                                        PerfumeShelf.markWorn(chosen.id)
                                        message = "Schön! Clawd merkt es sich und wechselt morgen ab."
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
                    "Schreib die Namen (mit Komma) oder schick Fotos von einem oder mehreren Flakons – Clawd erkennt sie, merkt sie sich und beschreibt jedes." +
                        if (hasKey) " Fotos klappen mit Claude oder Gemini." else " Für Analyse, Fotos und kluge Tipps trag deinen API-Schlüssel bei „Frag Clawd“ ein – ohne ihn merkt sich Clawd nur die Namen.",
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        PerfumeField(text, { text = it }, "z. B. Bleu de Chanel, Sauvage, Black Opium", Modifier.fillMaxWidth().heightIn(min = 76.dp), singleLine = false)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            VaultButton("Clawd, schau dir das an", Modifier.weight(1f), enabled = !working && text.isNotBlank(), onClick = addText)
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
                StudioPanel("Wetter", "Für den Tagesvorschlag. Ohne Ort rät Clawd nach der Jahreszeit.") {
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
                    "Deine Sammlung · ${shelf.size}",
                    "Tippe auf ein Parfüm für die Einzelheiten. „Mag ich“ bevorzugt Clawd, „Mag ich nicht“ schlägt er nie vor.",
                ) {
                    if (shelf.isEmpty()) {
                        Text("Noch leer.", color = VaultDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    shelf.sortedWith(compareBy({ it.taste == PerfumeTaste.Dislike }, { it.title.lowercase() })).forEach { perfume ->
                        PerfumeRow(
                            perfume = perfume,
                            expanded = open == perfume.id,
                            onToggle = { open = if (open == perfume.id) null else perfume.id },
                            onTaste = { PerfumeShelf.setTaste(perfume.id, it) },
                            onDelete = { PerfumeShelf.remove(perfume.id) },
                        )
                    }
                    if (shelf.any { !it.analysed } && hasKey) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            VaultButton("Unbekannte genauer ansehen", Modifier.fillMaxWidth(), primary = false, enabled = !working) {
                                scope.launch {
                                    working = true
                                    message = "Clawd schaut genauer hin …"
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
    onDone: () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
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
) {
    val accent = when (perfume.taste) {
        PerfumeTaste.Love -> PerfumeLove
        PerfumeTaste.Dislike -> PerfumeDislike
        PerfumeTaste.Neutral -> Color.White.copy(alpha = 0.25f)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
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
        }
    }
}

@Composable
private fun PerfumeFact(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(label, color = VaultDim, fontSize = 12.sp, modifier = Modifier.width(104.dp))
        Text(value, color = VaultText, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
