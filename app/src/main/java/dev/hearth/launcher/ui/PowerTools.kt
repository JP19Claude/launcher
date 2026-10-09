package dev.hearth.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * Hearth UI's power tools – what One UI and ColorOS don't offer, or hide: a turbo that frees
 * memory and storage in one tap, removing preinstalled apps (and bringing them back), a
 * privacy check that takes permissions back, a background lock for battery hogs, and a few
 * display extras. Most need Shizuku; the privacy check shows its findings without it too.
 */

private fun freeBytes(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)

private fun megabytes(bytes: Long): String =
    if (bytes >= 1_073_741_824L) String.format(Locale.GERMAN, "%.1f GB", bytes / 1_073_741_824.0)
    else String.format(Locale.GERMAN, "%.0f MB", bytes / 1_048_576.0)

private fun toast(context: Context, text: String) {
    runCatching { android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show() }
}

/** Apps whose background running Hearth forbade (so the settings can list them). */
object BackgroundLock {
    private fun prefs(context: Context) = context.getSharedPreferences("hearth_power", Context.MODE_PRIVATE)

    fun locked(context: Context): Set<String> = prefs(context).getStringSet("background", emptySet()).orEmpty()

    fun isLocked(context: Context, packageName: String) = packageName in locked(context)

    /** Forbids or allows background running through Shizuku; remembers it when it worked. */
    suspend fun set(context: Context, packageName: String, lock: Boolean): Boolean {
        val ok = ShizukuBridge.restrictBackground(packageName, lock)
        if (ok) {
            val next = if (lock) locked(context) + packageName else locked(context) - packageName
            prefs(context).edit().putStringSet("background", next).apply()
        }
        return ok
    }
}

/** Preinstalled apps that can safely go: services and stubs most people never open. */
private val Removable = listOf(
    "com.facebook.appmanager" to "Facebook App Manager",
    "com.facebook.services" to "Facebook Services",
    "com.facebook.system" to "Facebook App Installer",
    "com.facebook.katana" to "Facebook (vorinstalliert)",
    "com.microsoft.skydrive" to "OneDrive",
    "com.linkedin.android" to "LinkedIn",
    "com.microsoft.office.officehubrow" to "Microsoft 365",
    "com.netflix.partner.activation" to "Netflix-Aktivierung",
    "com.samsung.android.app.spage" to "Samsung Free",
    "com.samsung.android.arzone" to "AR-Zone",
    "com.samsung.android.ardrawing" to "AR Doodle",
    "com.samsung.android.app.tips" to "Samsung Tipps",
    "com.samsung.sree" to "Samsung Global Goals",
    "com.samsung.android.kidsinstaller" to "Kids-Installer",
    "com.samsung.android.tvplus" to "Samsung TV Plus",
    "com.google.android.apps.tachyon" to "Google Meet",
    "com.google.android.videos" to "Google TV",
    "com.google.android.apps.subscriptions.red" to "Google One",
)

private fun label(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
}.getOrDefault(packageName)

/** The Shizuku power tools, shown once Shizuku is ready. */
@Composable
internal fun ShizukuPowerTools() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var everything by remember { mutableStateOf<Set<String>>(emptySet()) }
    var locked by remember { mutableStateOf(BackgroundLock.locked(context)) }
    var stayAwake by remember { mutableStateOf(readGlobal(context, "stay_on_while_plugged_in", 0) != 0) }
    var popups by remember { mutableStateOf(readGlobal(context, "heads_up_notifications_enabled", 1) != 0) }
    var extraDim by remember { mutableStateOf(readSecure(context, "reduce_bright_colors_activated", 0) != 0) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    suspend fun reloadPackages() {
        installed = ShizukuBridge.packages()
        everything = ShizukuBridge.packages("-u")
    }
    LaunchedEffect(Unit) { reloadPackages() }

    Section("Turbo") {
        SystemPageRow(
            SystemPage(
                if (busy) "Einen Moment …" else "Turbo",
                "Beendet Hintergrund-Apps und leert alle App-Caches – ein Tipp",
                Color(0xFFFF9F0A),
                emptyList(),
                icon = Icons.Rounded.Star,
            ),
        ) {
            if (busy) return@SystemPageRow
            busy = true
            scope.launch {
                val before = freeBytes()
                ShizukuBridge.killBackgroundApps()
                ShizukuBridge.trimCaches()
                val freed = (freeBytes() - before).coerceAtLeast(0L)
                busy = false
                toast(context, "Turbo fertig · ${megabytes(freed)} Speicher frei geworden")
            }
        }
        RowDivider()
        SystemPageRow(
            SystemPage("Nur Caches leeren", "Speicher frei machen, ohne Apps zu beenden", Color(0xFF30B0C7), emptyList(), icon = Icons.Rounded.Delete),
        ) {
            scope.launch {
                val before = freeBytes()
                val ok = ShizukuBridge.trimCaches()
                toast(context, if (ok) "${megabytes((freeBytes() - before).coerceAtLeast(0L))} frei geworden" else "Ging nicht")
            }
        }
    }

    Section("Anzeige-Extras") {
        SwitchRow("Bildschirm beim Laden anlassen", stayAwake, "Ideal als Nachttisch-Uhr oder beim Navigieren") { v ->
            scope.launch { if (ShizukuBridge.putGlobal("stay_on_while_plugged_in", if (v) "7" else "0")) stayAwake = v }
        }
        SwitchRow("Pop-up-Benachrichtigungen", popups, "Aus: keine Banner mehr oben – Mitteilungen landen still im Bereich") { v ->
            scope.launch { if (ShizukuBridge.putGlobal("heads_up_notifications_enabled", if (v) "1" else "0")) popups = v }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            SwitchRow("Extra dunkel", extraDim, "Dunkler als die kleinste Helligkeit – fürs Lesen im Bett") { v ->
                scope.launch { if (ShizukuBridge.putSecure("reduce_bright_colors_activated", if (v) "1" else "0")) extraDim = v }
            }
        }
    }

    Section("Entrümpeln") {
        Note("Vorinstallierte Apps, die du nicht brauchst, entfernen – ohne Root. Sie bleiben im System und lassen sich jederzeit zurückholen.")
        val present = Removable.filter { (pkg, _) -> pkg in installed }
        val removed = Removable.filter { (pkg, _) -> pkg in everything && pkg !in installed }
        if (present.isEmpty() && removed.isEmpty()) {
            Note("Auf diesem Handy ist nichts davon installiert – schon aufgeräumt.")
        }
        present.forEach { (pkg, name) ->
            SystemPageRow(
                SystemPage(
                    if (confirmRemove == pkg) "Nochmal tippen: $name entfernen" else name,
                    "Installiert · tippen zum Entfernen",
                    Color(0xFFFF453A),
                    emptyList(),
                    icon = Icons.Rounded.Delete,
                ),
            ) {
                if (confirmRemove != pkg) {
                    confirmRemove = pkg
                } else {
                    confirmRemove = null
                    scope.launch {
                        toast(context, if (ShizukuBridge.removeForUser(pkg)) "$name entfernt" else "Ging nicht")
                        reloadPackages()
                    }
                }
            }
        }
        removed.forEach { (pkg, name) ->
            SystemPageRow(
                SystemPage(name, "Entfernt · tippen zum Zurückholen", Color(0xFF34C759), emptyList(), icon = Icons.Rounded.Refresh),
            ) {
                scope.launch {
                    toast(context, if (ShizukuBridge.restore(pkg)) "$name ist zurück" else "Ging nicht")
                    reloadPackages()
                }
            }
        }
    }

    Section("Hintergrund-Sperre") {
        Note("Lange auf eine App drücken → „Im Hintergrund verbieten“: Die App läuft nur noch, wenn du sie offen hast. Spart Akku bei Stromfressern.")
        locked.forEach { pkg ->
            SystemPageRow(
                SystemPage(label(context, pkg), "Im Hintergrund gesperrt · tippen zum Erlauben", Color(0xFF8E6BFF), emptyList(), glyph = Glyph.Battery),
            ) {
                scope.launch {
                    if (BackgroundLock.set(context, pkg, false)) {
                        locked = BackgroundLock.locked(context)
                        toast(context, "${label(context, pkg)} darf wieder im Hintergrund laufen")
                    }
                }
            }
        }
    }
}

private fun readGlobal(context: Context, key: String, default: Int): Int =
    runCatching { Settings.Global.getInt(context.contentResolver, key, default) }.getOrDefault(default)

private fun readSecure(context: Context, key: String, default: Int): Int =
    runCatching { Settings.Secure.getInt(context.contentResolver, key, default) }.getOrDefault(default)

/** A kind of access an app can have. */
private class Access(val title: String, val permissions: List<String>, val color: Color, val glyph: Glyph? = null, val icon: ImageVector? = null)

private val Accesses = listOf(
    Access("Standort", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, "android.permission.ACCESS_BACKGROUND_LOCATION"), Color(0xFF34C759), glyph = Glyph.Location),
    Access("Kamera", listOf(Manifest.permission.CAMERA), Color(0xFF3E91FF), glyph = Glyph.Camera),
    Access("Mikrofon", listOf(Manifest.permission.RECORD_AUDIO), Color(0xFFFF453A), glyph = Glyph.Headphones),
    Access("Kontakte", listOf(Manifest.permission.READ_CONTACTS), Color(0xFFFF9F0A), icon = Icons.Rounded.Person),
    Access("SMS", listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), Color(0xFF30B0C7), icon = Icons.Rounded.Email),
    Access("Anrufliste", listOf(Manifest.permission.READ_CALL_LOG), Color(0xFF8E6BFF), icon = Icons.Rounded.Call),
    Access("Körpersensoren", listOf(Manifest.permission.BODY_SENSORS), Color(0xFFFF6FB5), icon = Icons.Rounded.Favorite),
)

/** An app and which of an access's permissions it holds. */
private class Holder(val packageName: String, val label: String, val permissions: List<String>)

/** Which of your own apps (not the system's) hold which access. */
private fun scanAccess(context: Context): Map<Access, List<Holder>> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val packages: List<PackageInfo> = runCatching { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) }.getOrDefault(emptyList())
    val mine = packages.filter { info ->
        val app = info.applicationInfo ?: return@filter false
        info.packageName != context.packageName && (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    }
    return Accesses.associateWith { access ->
        mine.mapNotNull { info ->
            val requested = info.requestedPermissions ?: return@mapNotNull null
            val flags = info.requestedPermissionsFlags ?: return@mapNotNull null
            val granted = requested.indices
                .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 && requested[it] in access.permissions }
                .map { requested[it] }
            if (granted.isEmpty()) null else Holder(info.packageName, info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName, granted)
        }.sortedBy { it.label.lowercase() }
    }
}

/**
 * Datenschutz-Check: who may see where you are, use the camera or the microphone, read your
 * contacts or messages – each with one tap to take it back (Shizuku) or open the app's info.
 */
@Composable
internal fun PrivacyCheck(shizukuReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Map<Access, List<Holder>>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    suspend fun scan() {
        result = withContext(Dispatchers.Default) { scanAccess(context) }
    }
    LaunchedEffect(Unit) { scan() }

    Section("Datenschutz-Check") {
        val found = result
        if (found == null) {
            Note("Prüfe, welche Apps worauf zugreifen dürfen …")
            return@Section
        }
        Note(
            if (shizukuReady) "Tippe auf eine App, um ihr den Zugriff sofort zu entziehen (sie kann später wieder fragen)."
            else "Tippe auf eine App, um ihre Berechtigungen zu öffnen. Mit Shizuku entzieht Hearth den Zugriff direkt.",
        )
        Accesses.forEachIndexed { index, access ->
            val holders = found[access].orEmpty()
            if (index > 0) RowDivider()
            SystemPageRow(
                SystemPage(
                    access.title,
                    if (holders.isEmpty()) "Keine deiner Apps" else "${holders.size} ${if (holders.size == 1) "App" else "Apps"} dürfen das",
                    access.color,
                    emptyList(),
                    glyph = access.glyph,
                    icon = access.icon,
                ),
            ) { open = if (open == access.title) null else access.title }
            if (open == access.title) {
                holders.forEach { holder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (shizukuReady) {
                                    scope.launch {
                                        val ok = holder.permissions.map { ShizukuBridge.revoke(holder.packageName, it) }.any { it }
                                        toast(context, if (ok) "${access.title}: ${holder.label} entzogen" else "Ging nicht")
                                        scan()
                                    }
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${holder.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            }
                            .padding(start = 68.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(holder.label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (shizukuReady) "Entziehen" else "Öffnen",
                            color = access.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** One advantage of Hearth UI, and whether One UI / ColorOS have it. */
private class Advantage(val title: String, val text: String, val oneUi: Boolean = false, val colorOs: Boolean = false)

private val Advantages = listOf(
    Advantage("Werbeblocker fürs ganze Handy", "Ein Tipp, und AdGuard blockt Werbung und Tracker in allen Apps – ohne extra App."),
    Advantage("Vorinstallierte Apps entfernen", "Facebook-Dienste, Samsung Free, OneDrive & Co. weg – ohne Root, jederzeit zurückholbar."),
    Advantage("Turbo", "Hintergrund-Apps beenden und alle Caches leeren mit einem Tipp – samt Anzeige, wie viel frei wurde."),
    Advantage("Datenschutz-Check mit Sofort-Entzug", "Wer darf Standort, Kamera, Mikro, Kontakte, SMS? Ein Tipp nimmt den Zugriff weg."),
    Advantage("Hintergrund-Sperre pro App", "Stromfresser dürfen nur noch laufen, wenn du sie offen hast."),
    Advantage("Apps einfrieren", "Eine App schlafen legen, ohne sie zu löschen – und mit einem Tipp wieder wecken."),
    Advantage("Animations-Tempo fürs ganze System", "„Schnell“ halbiert alle Übergänge – ohne Entwickleroptionen."),
    Advantage("Clawd schaltet für dich", "„Mach WLAN aus“, „Räum den Speicher auf“ – Clawd erledigt es direkt, mit Claude als Hirn."),
    Advantage("Glimmer – die Insel um die Kamera", "Musik, Timer, Laden und Mitteilungen in einer lebendigen Insel – wie Dynamic Island."),
    Advantage("APK teilen", "Jede App als Datei weitergeben oder sichern – direkt aus dem App-Menü."),
    Advantage("Liquid Glass & One UI 10 Fluid", "Echtes, lichtbrechendes Glas und fließende Farben, die auf Berührung reagieren."),
    Advantage("Apps sperren und ausblenden", "Mit PIN oder Fingerabdruck – direkt im Launcher, ohne Sicheren Ordner.", oneUi = true),
    Advantage("Schalter, die Android Apps verbietet", "WLAN, Bluetooth, mobile Daten, Flugmodus, NFC, Dunkelmodus – direkt in Hearth.", oneUi = true, colorOs = true),
    Advantage("Bildschirm beim Laden anlassen", "Als Nachttisch-Uhr oder beim Navigieren – ohne Entwickleroptionen."),
    Advantage("Updates direkt aus der App", "Mit ausführlichem Änderungsprotokoll – und alle Neuerungen für jedes Handy, nicht nur für neue Modelle."),
)

/** "Warum Hearth UI": what it does that One UI and ColorOS don't. */
@Composable
internal fun HearthAdvantages() {
    Section("Warum ZENITH") {
        Note("Was ZENITH kann – und ob One UI oder ColorOS das auch haben.")
        Advantages.forEachIndexed { index, advantage ->
            if (index > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(advantage.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(advantage.text, color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp, lineHeight = 18.sp)
                    Text(
                        "One UI: ${if (advantage.oneUi) "teilweise" else "nein"} · ColorOS: ${if (advantage.colorOs) "teilweise" else "nein"}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
