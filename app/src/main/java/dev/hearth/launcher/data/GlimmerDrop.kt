package dev.hearth.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.random.Random

/**
 * Glimmer Drop – like NameDrop and AirDrop: hold the tops of two phones with Glimmer together
 * (Hearth UI, or just the Glimmer app)
 * and share your contact card, photos, videos, files, links and text.
 *
 * How the phones find each other: each one sends a tiny Bluetooth beacon (at the lowest power,
 * so it's only strong when the phones touch) with a random token. When a beacon is that strong,
 * the two phones open a fast connection with Google's Nearby Connections (Bluetooth and
 * Wi-Fi Direct) – found by the same token – and swap what each wants to share. Nothing is kept
 * until the other side taps "Annehmen".
 */
object GlimmerDrop {

    private const val SERVICE_ID = "dev.hearth.glimmerdrop"
    private val BEACON: ParcelUuid = ParcelUuid.fromString("00004854-0000-1000-8000-00805f9b34fb")

    /** Beacons this strong (dBm, at the lowest sending power) mean: the phones touch. */
    private const val TOUCH_RSSI = -52

    /** Who this phone is for the other ones; new every start of the app. */
    private val token: String = String.format(Locale.ROOT, "%08x", Random.nextInt())

    // -----------------------------------------------------------------------------------------
    // What is shared
    // -----------------------------------------------------------------------------------------

    data class Profile(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val instagram: String = "",
        val website: String = "",
    ) {
        val isEmpty: Boolean get() = name.isBlank() && phone.isBlank() && email.isBlank() && instagram.isBlank() && website.isBlank()

        fun toJson(): JSONObject = JSONObject()
            .put("name", name).put("phone", phone).put("email", email)
            .put("instagram", instagram).put("website", website)

        companion object {
            fun from(o: JSONObject): Profile = Profile(
                name = o.optString("name"),
                phone = o.optString("phone"),
                email = o.optString("email"),
                instagram = o.optString("instagram"),
                website = o.optString("website"),
            )
        }
    }

    /** A file to send (photo, video, document …). */
    data class OutFile(val uri: Uri, val name: String, val mime: String, val size: Long)

    data class FileInfo(val name: String, val mime: String, val size: Long)

    /** What one side shares. */
    data class Offer(val profile: Profile?, val texts: List<String>, val files: List<FileInfo>) {
        val isEmpty: Boolean get() = profile == null && texts.isEmpty() && files.isEmpty()

        fun summary(): String {
            val parts = mutableListOf<String>()
            if (profile != null) parts += "Kontaktkarte"
            val photos = files.count { it.mime.startsWith("image/") }
            val videos = files.count { it.mime.startsWith("video/") }
            val other = files.size - photos - videos
            if (photos > 0) parts += if (photos == 1) "1 Foto" else "$photos Fotos"
            if (videos > 0) parts += if (videos == 1) "1 Video" else "$videos Videos"
            if (other > 0) parts += if (other == 1) "1 Datei" else "$other Dateien"
            val links = texts.count { isLink(it) }
            val notes = texts.size - links
            if (links > 0) parts += if (links == 1) "1 Link" else "$links Links"
            if (notes > 0) parts += if (notes == 1) "1 Text" else "$notes Texte"
            return parts.joinToString(", ")
        }
    }

    data class ReceivedFile(val name: String, val mime: String, val uri: Uri?)

    data class Received(
        val profile: Profile? = null,
        val texts: List<String> = emptyList(),
        val files: List<ReceivedFile> = emptyList(),
    )

    /** One side of an exchange. */
    enum class Step { None, Asking, Running, Done, Declined }

    data class Exchange(
        val peer: String,
        /** What they share; null until their hello arrived. */
        val offer: Offer? = null,
        /** [Step.Asking]: waiting for my answer. */
        val incoming: Step = Step.None,
        val inProgress: Float = 0f,
        /** [Step.Asking]: waiting for their answer. */
        val outgoing: Step = Step.None,
        val outProgress: Float = 0f,
        val received: Received = Received(),
    )

    sealed interface Phase {
        data object Off : Phase
        data object Searching : Phase
        data class Connecting(val peer: String) : Phase
        data object Connected : Phase
        data class Failed(val message: String) : Phase
    }

    /** A phone with Glimmer Drop open nearby. */
    data class Peer(val endpointId: String, val token: String, val name: String)

    private val _phase = MutableStateFlow<Phase>(Phase.Off)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _exchange = MutableStateFlow<Exchange?>(null)
    val exchange: StateFlow<Exchange?> = _exchange.asStateFlow()

    private val _nearby = MutableStateFlow<List<Peer>>(emptyList())
    val nearby: StateFlow<List<Peer>> = _nearby.asStateFlow()

    /** How close the nearest Glimmer Drop phone is, 0 (far) … 1 (touching). */
    private val _closeness = MutableStateFlow(0f)
    val closeness: StateFlow<Float> = _closeness.asStateFlow()

    /** What I'm about to share. */
    val files = MutableStateFlow<List<OutFile>>(emptyList())
    val texts = MutableStateFlow<List<String>>(emptyList())
    val shareProfile = MutableStateFlow(true)

    /** Moments for Glimmer's island: "touching", "sending 40 %", "received". */
    class IslandMoment(val title: String, val value: String, val level: Float? = null)

    private val _island = MutableSharedFlow<IslandMoment>(extraBufferCapacity = 8)
    val island: SharedFlow<IslandMoment> = _island.asSharedFlow()

    // -----------------------------------------------------------------------------------------
    // Settings: my contact card, and whether Glimmer listens in the background
    // -----------------------------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences("glimmer_drop", Context.MODE_PRIVATE)

    fun profile(context: Context): Profile {
        val p = prefs(context)
        return Profile(
            name = p.getString("name", "").orEmpty(),
            phone = p.getString("phone", "").orEmpty(),
            email = p.getString("email", "").orEmpty(),
            instagram = p.getString("instagram", "").orEmpty(),
            website = p.getString("website", "").orEmpty(),
        )
    }

    fun saveProfile(context: Context, profile: Profile) {
        prefs(context).edit()
            .putString("name", profile.name.trim())
            .putString("phone", profile.phone.trim())
            .putString("email", profile.email.trim())
            .putString("instagram", profile.instagram.trim())
            .putString("website", profile.website.trim())
            .apply()
    }

    private val _ready = MutableStateFlow(true)

    /** Glimmer listens for a phone held against this one while it's unlocked. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private var readyLoaded = false

    fun loadReady(context: Context) {
        if (readyLoaded) return
        readyLoaded = true
        _ready.value = prefs(context).getBoolean("ready", true)
    }

    fun setReady(context: Context, on: Boolean) {
        _ready.value = on
        prefs(context).edit().putBoolean("ready", on).apply()
        setBackground(context, backgroundRequested)
    }

    /** The name the other phone shows: from the contact card, else the phone's name. */
    fun displayName(context: Context): String =
        profile(context).name.takeIf { it.isNotBlank() }
            ?: runCatching { android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    fun isLink(text: String): Boolean {
        val t = text.trim()
        return !t.contains(' ') && (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www."))
    }

    // -----------------------------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------------------------

    fun neededPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    fun hasPermissions(context: Context): Boolean = neededPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    // -----------------------------------------------------------------------------------------
    // The session: the Glimmer Drop screen is open
    // -----------------------------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var app: Context? = null
    private var client: ConnectionsClient? = null
    private var sessionOpen = false
    private var connectedId: String? = null
    private var touchToken: String? = null
    private val rssi = HashMap<String, ArrayDeque<Int>>()

    /**
     * Opens Glimmer Drop: findable for others, and looking for a phone to touch. [touched] is
     * the phone already held against this one (Glimmer felt it): it's called right away.
     */
    fun open(context: Context, touched: String? = null) {
        val c = context.applicationContext
        app = c
        if (sessionOpen) return
        sessionOpen = true
        uiShown = false
        stopBeacon()
        _nearby.value = emptyList()
        _exchange.value = null
        touchToken = touched
        rssi.clear()
        if (!hasPermissions(c)) {
            _phase.value = Phase.Failed("Glimmer Drop braucht die Erlaubnis für Geräte in der Nähe.")
            return
        }
        _phase.value = Phase.Searching
        startBeacon(c, background = false)
        startNearby(c)
    }

    /** Closes Glimmer Drop; Glimmer goes back to listening in the background. */
    fun close() {
        if (!sessionOpen) return
        sessionOpen = false
        stopNearby()
        stopBeacon()
        _phase.value = Phase.Off
        _exchange.value = null
        _nearby.value = emptyList()
        _closeness.value = 0f
        files.value = emptyList()
        texts.value = emptyList()
        lastClosed = SystemClock.elapsedRealtime()
        closedWith = touchToken
        uiShown = false
        app?.let { if (backgroundWanted) startBeacon(it, background = true) }
    }

    /** Try again after a failure (permissions just granted, Bluetooth just on …). */
    fun restart(context: Context) {
        if (sessionOpen) {
            sessionOpen = false
            stopNearby()
            stopBeacon()
        }
        open(context)
    }

    @SuppressLint("MissingPermission")
    private fun startNearby(context: Context) {
        val c = runCatching { Nearby.getConnectionsClient(context) }.getOrNull()
        if (c == null) {
            _phase.value = Phase.Failed("Glimmer Drop braucht die Google Play-Dienste.")
            return
        }
        client = c
        val name = "$token|${displayName(context).take(40)}"
        c.startAdvertising(
            name, SERVICE_ID, lifecycle,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
        ).addOnFailureListener { e -> if (!alreadyRunning(e)) fail("Glimmer Drop konnte nicht starten (${e.message}).") }
        c.startDiscovery(
            SERVICE_ID, discovery,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
        ).addOnFailureListener { e -> if (!alreadyRunning(e)) fail("Glimmer Drop konnte nicht suchen (${e.message}).") }
    }

    private fun alreadyRunning(e: Exception): Boolean {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        return code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING || code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING
    }

    private fun stopNearby() {
        client?.let { c ->
            runCatching { c.stopAdvertising() }
            runCatching { c.stopDiscovery() }
            runCatching { c.stopAllEndpoints() }
        }
        connectedId = null
        outgoingIds.clear()
        incomingFiles.clear()
        incomingMeta.clear()
        incomingDone.clear()
        incomingProgress.clear()
    }

    private fun fail(message: String) {
        if (sessionOpen) _phase.value = Phase.Failed(message)
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val raw = info.endpointName
            val peerToken = raw.substringBefore('|', "")
            val name = raw.substringAfter('|', raw)
            _nearby.value = _nearby.value.filterNot { it.endpointId == endpointId } + Peer(endpointId, peerToken, name)
            tryTouchConnect()
        }

        override fun onEndpointLost(endpointId: String) {
            _nearby.value = _nearby.value.filterNot { it.endpointId == endpointId }
        }
    }

    /** The phone held against this one is found: the one with the smaller token calls. */
    private fun tryTouchConnect() {
        val t = touchToken ?: return
        if (_phase.value != Phase.Searching) return
        val peer = _nearby.value.firstOrNull { it.token == t } ?: return
        if (token < peer.token) connect(peer)
    }

    /** Connect to a phone nearby (a touch, or a tap in the list). */
    fun connect(peer: Peer) {
        val c = client ?: return
        val context = app ?: return
        if (connectedId != null) return
        _phase.value = Phase.Connecting(peer.name)
        c.requestConnection("$token|${displayName(context).take(40)}", peer.endpointId, lifecycle)
            .addOnFailureListener { e ->
                val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (code != ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT && connectedId == null && sessionOpen) {
                    _phase.value = Phase.Searching
                }
            }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (!sessionOpen || connectedId != null) {
                client?.rejectConnection(endpointId)
                return
            }
            val name = info.endpointName.substringAfter('|', info.endpointName)
            _phase.value = Phase.Connecting(name)
            _exchange.value = Exchange(peer = name)
            // Nothing is kept until the user says yes to what comes (see "answer").
            client?.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedId = endpointId
                _phase.value = Phase.Connected
                runCatching { client?.stopDiscovery() }
                runCatching { client?.stopAdvertising() }
                val peer = _exchange.value?.peer.orEmpty()
                _island.tryEmit(IslandMoment("Glimmer Drop", peer.ifBlank { "Verbunden" }))
                sendHello(endpointId)
            } else if (sessionOpen) {
                _exchange.value = null
                _phase.value = Phase.Searching
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId != connectedId) return
            connectedId = null
            val ex = _exchange.value
            val unfinished = ex != null && (ex.incoming == Step.Running || ex.outgoing == Step.Running)
            if (unfinished && sessionOpen) {
                _phase.value = Phase.Failed("Die Verbindung ist abgerissen. Haltet die Handys noch einmal aneinander.")
            } else if (sessionOpen) {
                _phase.value = Phase.Searching
                app?.let { startNearby(it) }
            }
            touchToken = null
        }
    }

    // -----------------------------------------------------------------------------------------
    // The exchange
    // -----------------------------------------------------------------------------------------

    private fun myOffer(context: Context): Offer = Offer(
        profile = profile(context).takeIf { shareProfile.value && !it.isEmpty },
        texts = texts.value,
        files = files.value.map { FileInfo(it.name, it.mime, it.size) },
    )

    private fun sendBytes(endpointId: String, json: JSONObject) {
        client?.sendPayload(endpointId, Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun sendHello(endpointId: String) {
        val context = app ?: return
        val offer = myOffer(context)
        val o = JSONObject().put("t", "hello")
        offer.profile?.let { o.put("profile", it.toJson()) }
        o.put("texts", JSONArray(offer.texts))
        o.put("files", JSONArray().apply {
            offer.files.forEach { put(JSONObject().put("name", it.name).put("mime", it.mime).put("size", it.size)) }
        })
        sendBytes(endpointId, o)
        _exchange.value = (_exchange.value ?: Exchange(peer = "")).copy(outgoing = if (offer.isEmpty) Step.None else Step.Asking)
    }

    /** My answer to what they share. */
    fun answer(accept: Boolean) {
        val id = connectedId ?: return
        val ex = _exchange.value ?: return
        val offer = ex.offer ?: return
        sendBytes(id, JSONObject().put("t", "answer").put("ok", accept))
        _exchange.value = if (accept) {
            ex.copy(
                incoming = if (offer.files.isEmpty()) Step.Done else Step.Running,
                received = ex.received.copy(profile = offer.profile, texts = offer.texts),
            )
        } else {
            ex.copy(incoming = Step.Declined)
        }
        if (accept && offer.files.isEmpty()) {
            _island.tryEmit(IslandMoment("Empfangen", offer.summary(), 1f))
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { onMessage(endpointId, String(it, Charsets.UTF_8)) }
                Payload.Type.FILE -> {
                    incomingFiles[payload.id] = payload
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            if (id in outgoingIds) {
                outgoingIds[id] = update.bytesTransferred to update.totalBytes
                val total = outgoingIds.values.sumOf { it.second }.coerceAtLeast(1L)
                val done = outgoingIds.values.sumOf { it.first }
                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                val finished = update.status == PayloadTransferUpdate.Status.SUCCESS && outgoingIds.values.all { it.first >= it.second }
                val ex = _exchange.value ?: return
                if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                    _exchange.value = ex.copy(outgoing = Step.Declined)
                    fail("Senden hat nicht geklappt.")
                    return
                }
                _exchange.value = ex.copy(outProgress = p, outgoing = if (finished) Step.Done else Step.Running)
                emitProgress("Senden", p, finished)
            } else if (id in incomingFiles || id in incomingMeta) {
                incomingProgress[id] = update.bytesTransferred to update.totalBytes
                val ex = _exchange.value ?: return
                if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                    _exchange.value = ex.copy(incoming = Step.Declined)
                    fail("Empfangen hat nicht geklappt.")
                    return
                }
                val total = incomingProgress.values.sumOf { it.second }.coerceAtLeast(1L)
                val done = incomingProgress.values.sumOf { it.first }
                _exchange.value = ex.copy(inProgress = (done.toFloat() / total).coerceIn(0f, 1f))
                if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                    incomingDone += id
                    finishFile(id)
                }
            }
        }
    }

    private val outgoingIds = HashMap<Long, Pair<Long, Long>>()
    private val incomingFiles = HashMap<Long, Payload>()
    private val incomingMeta = HashMap<Long, FileInfo>()
    private val incomingDone = HashSet<Long>()
    private val incomingProgress = HashMap<Long, Pair<Long, Long>>()
    private var lastIslandProgress = -1

    private fun emitProgress(title: String, p: Float, finished: Boolean) {
        val step = (p * 10).toInt()
        if (finished) {
            lastIslandProgress = -1
            _island.tryEmit(IslandMoment(if (title == "Senden") "Gesendet" else "Empfangen", _exchange.value?.peer.orEmpty(), 1f))
        } else if (step != lastIslandProgress) {
            lastIslandProgress = step
            _island.tryEmit(IslandMoment(title, "${(p * 100).toInt()} %", p))
        }
    }

    private fun onMessage(endpointId: String, text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        val ex = _exchange.value ?: Exchange(peer = "")
        when (o.optString("t")) {
            "hello" -> {
                val profile = o.optJSONObject("profile")?.let { Profile.from(it) }?.takeIf { !it.isEmpty }
                val texts = o.optJSONArray("texts")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                val files = o.optJSONArray("files")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        a.optJSONObject(i)?.let { f -> FileInfo(f.optString("name"), f.optString("mime"), f.optLong("size")) }
                    }
                }.orEmpty()
                val offer = Offer(profile, texts, files)
                _exchange.value = ex.copy(offer = offer, incoming = if (offer.isEmpty) Step.None else Step.Asking)
                if (!offer.isEmpty) _island.tryEmit(IslandMoment(ex.peer.ifBlank { "Glimmer Drop" }, offer.summary()))
            }
            "answer" -> {
                if (!o.optBoolean("ok")) {
                    _exchange.value = ex.copy(outgoing = Step.Declined)
                    return
                }
                val list = files.value
                if (list.isEmpty()) {
                    _exchange.value = ex.copy(outgoing = Step.Done, outProgress = 1f)
                    _island.tryEmit(IslandMoment("Gesendet", ex.peer, 1f))
                } else {
                    _exchange.value = ex.copy(outgoing = Step.Running, outProgress = 0f)
                    sendFiles(endpointId, list)
                }
            }
            "file" -> {
                val id = o.optLong("id")
                incomingMeta[id] = FileInfo(o.optString("name"), o.optString("mime"), o.optLong("size"))
                finishFile(id)
            }
        }
    }

    private fun sendFiles(endpointId: String, list: List<OutFile>) {
        val context = app ?: return
        val c = client ?: return
        list.forEach { f ->
            val pfd = runCatching { context.contentResolver.openFileDescriptor(f.uri, "r") }.getOrNull() ?: return@forEach
            val payload = Payload.fromFile(pfd)
            outgoingIds[payload.id] = 0L to f.size.coerceAtLeast(1L)
            sendBytes(endpointId, JSONObject().put("t", "file").put("id", payload.id).put("name", f.name).put("mime", f.mime).put("size", f.size))
            c.sendPayload(endpointId, payload)
        }
        if (outgoingIds.isEmpty()) _exchange.value = _exchange.value?.copy(outgoing = Step.Done, outProgress = 1f)
    }

    /** A file is complete once both its data and its name have arrived: into the gallery or downloads. */
    private fun finishFile(id: Long) {
        if (id !in incomingDone) return
        val meta = incomingMeta[id] ?: return
        val payload = incomingFiles.remove(id) ?: return
        val context = app ?: return
        scope.launch {
            val uri = withContext(Dispatchers.IO) { save(context, meta, payload) }
            val ex = _exchange.value ?: return@launch
            val got = ex.received.files + ReceivedFile(meta.name, meta.mime, uri)
            val expected = ex.offer?.files?.size ?: got.size
            val finished = got.size >= expected
            _exchange.value = ex.copy(
                received = ex.received.copy(files = got),
                incoming = if (finished) Step.Done else ex.incoming,
                inProgress = if (finished) 1f else ex.inProgress,
            )
            if (finished) _island.tryEmit(IslandMoment("Empfangen", ex.offer?.summary().orEmpty(), 1f))
        }
    }

    private fun save(context: Context, meta: FileInfo, payload: Payload): Uri? = runCatching {
        val file = payload.asFile() ?: return@runCatching null
        val source: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            file.asUri()
        } else {
            @Suppress("DEPRECATION")
            val javaFile = file.asJavaFile()
            javaFile?.let { Uri.fromFile(it) }
        }
        if (source == null) return@runCatching null
        val name = meta.name.ifBlank { "Glimmer Drop ${System.currentTimeMillis()}" }
        val mime = meta.mime.ifBlank { "application/octet-stream" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Older Android: into Hearth's own folder (no gallery entry).
            val dir = java.io.File(context.getExternalFilesDir(null), "Glimmer Drop").apply { mkdirs() }
            context.contentResolver.openInputStream(source)?.use { input ->
                java.io.File(dir, name).outputStream().use { input.copyTo(it) }
            }
            return@runCatching null
        }
        val (collection, folder) = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Glimmer Drop")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return@runCatching null
        resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        runCatching { resolver.delete(source, null, null) }
        target
    }.getOrNull()

    // -----------------------------------------------------------------------------------------
    // What to share
    // -----------------------------------------------------------------------------------------

    /** Adds files (from the photo picker, the file picker or another app's share). */
    fun addFiles(context: Context, uris: List<Uri>) {
        val added = uris.mapNotNull { uri ->
            runCatching {
                var name = uri.lastPathSegment ?: "Datei"
                var size = 0L
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        size = c.getLong(1)
                    }
                }
                OutFile(uri, name, context.contentResolver.getType(uri) ?: "application/octet-stream", size)
            }.getOrNull()
        }
        files.value = (files.value + added).distinctBy { it.uri }
    }

    fun removeFile(file: OutFile) {
        files.value = files.value - file
    }

    fun addText(text: String) {
        val t = text.trim()
        if (t.isNotEmpty() && t !in texts.value) texts.value = texts.value + t
    }

    fun removeText(text: String) {
        texts.value = texts.value - text
    }

    // -----------------------------------------------------------------------------------------
    // The beacon: how close the other phone is
    // -----------------------------------------------------------------------------------------

    private var advertising: AdvertiseCallback? = null
    private var scanning: ScanCallback? = null
    private var beaconBackground = false
    private var backgroundWanted = false
    /** Whether Glimmer asks to listen (the phone is unlocked); [ready] decides if it does. */
    private var backgroundRequested = false
    private var lastClosed = 0L
    /** The phone of the last Glimmer Drop: not opened again right away while still held together. */
    private var closedWith: String? = null
    /** The Glimmer Drop screen is up (a session Glimmer started without it ends by itself). */
    private var uiShown = false

    fun uiShown() {
        uiShown = true
    }
    private var lastTouchOpen = 0L

    /** Glimmer, while the phone is unlocked: listen for a phone held against it. */
    fun setBackground(context: Context, on: Boolean) {
        app = context.applicationContext
        loadReady(context)
        watchBluetooth(context.applicationContext)
        backgroundRequested = on
        val want = on && _ready.value
        if (want == backgroundWanted && (scanning != null || !want)) return
        backgroundWanted = want
        if (sessionOpen) return
        if (want) startBeacon(context.applicationContext, background = true) else stopBeacon()
    }

    private var bluetoothWatched = false

    /** Bluetooth switched on later: Glimmer starts listening then. */
    private fun watchBluetooth(context: Context) {
        if (bluetoothWatched) return
        bluetoothWatched = true
        runCatching {
            ContextCompat.registerReceiver(
                context,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                        if (state == android.bluetooth.BluetoothAdapter.STATE_ON && backgroundWanted && !sessionOpen) {
                            startBeacon(c.applicationContext, background = true)
                        }
                    }
                },
                android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBeacon(context: Context, background: Boolean) {
        if (!hasPermissions(context)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) return
        stopBeacon()
        beaconBackground = background
        val advertiser = adapter.bluetoothLeAdvertiser
        val scanner = adapter.bluetoothLeScanner
        if (advertiser == null || scanner == null) return
        val tokenBytes = ByteArray(4) { i -> token.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val adCallback = object : AdvertiseCallback() {}
        runCatching {
            advertiser.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                    .setConnectable(false)
                    .build(),
                AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(BEACON)
                    .addServiceData(BEACON, tokenBytes)
                    .build(),
                adCallback,
            )
            advertising = adCallback
        }
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onBeacon(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onBeacon(it) }
        }
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(BEACON).build()),
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback,
            )
            scanning = scanCallback
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBeacon() {
        val context = app ?: return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        advertising?.let { cb -> runCatching { adapter.bluetoothLeAdvertiser?.stopAdvertising(cb) } }
        scanning?.let { cb -> runCatching { adapter.bluetoothLeScanner?.stopScan(cb) } }
        advertising = null
        scanning = null
    }

    private fun onBeacon(result: ScanResult) {
        val data = result.scanRecord?.getServiceData(BEACON) ?: return
        if (data.size < 4) return
        val peer = data.take(4).joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xff) }
        if (peer == token) return
        val list = rssi.getOrPut(peer) { ArrayDeque() }
        list.addLast(result.rssi)
        while (list.size > 3) list.removeFirst()
        val strongest = rssi.values.maxOfOrNull { it.average() } ?: -100.0
        // -90 dBm (far) … TOUCH_RSSI (touching) as 0 … 1.
        _closeness.value = ((strongest + 90.0) / (TOUCH_RSSI + 90.0)).toFloat().coerceIn(0f, 1f)
        val touching = list.size >= 2 && list.average() >= TOUCH_RSSI
        if (!touching) return
        if (sessionOpen) {
            if (touchToken != peer) {
                touchToken = peer
                _island.tryEmit(IslandMoment("Glimmer Drop", "Handy erkannt"))
                tryTouchConnect()
            }
        } else if (beaconBackground) {
            touchedInBackground(peer)
        }
    }

    /**
     * In the background: a phone touches this one – Glimmer Drop starts at once (the
     * connection to that phone already on its way) and its screen opens.
     */
    private fun touchedInBackground(peer: String) {
        val context = app ?: return
        val now = SystemClock.elapsedRealtime()
        // Not again with the same phone right after closing (they're probably still together).
        if (peer == closedWith && now - lastClosed < 12_000) return
        if (now - lastTouchOpen < 4_000) return
        if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) return
        lastTouchOpen = now
        _island.tryEmit(IslandMoment("Glimmer Drop", "Handy erkannt"))
        open(context, touched = peer)
        runCatching {
            context.startActivity(
                Intent(context, dev.hearth.launcher.DropActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        // If the screen couldn't open, don't keep searching in the background.
        scope.launch {
            kotlinx.coroutines.delay(10_000)
            if (sessionOpen && !uiShown) close()
        }
    }
}
