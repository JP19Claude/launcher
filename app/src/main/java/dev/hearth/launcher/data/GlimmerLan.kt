package dev.hearth.launcher.data

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/*
 * Glimmer Drop over the local network (ZENITH 22): for the devices that have no Bluetooth beacon and no Google Play services –
 * an Android laptop or PC, a tablet – and for phones that are on the same Wi-Fi anyway.
 *
 * Finding each other: while Glimmer Drop is open, every device sends a tiny UDP message to the whole network every second and a half
 * (who it is, and on which port it listens) and listens for those of the others. Tapping a device opens a TCP connection to it;
 * on that connection the very same messages travel as over Nearby (hello, answer, thumbnails …), and the files follow as raw bytes.
 * Nothing is saved before the receiver taps "Annehmen", and a file is only taken from the connection after that, no bigger than
 * what the offer said.
 */
internal class GlimmerLan(
    context: Context,
    private val token: String,
    private val displayName: () -> String,
    /** The devices in the network that have Glimmer Drop open (called from a background thread). */
    private val onPeers: (List<Peer>) -> Unit,
    /** Someone connected to this device (called from a background thread). */
    private val onIncoming: (Link) -> Unit,
) {
    data class Peer(val host: String, val port: Int, val token: String, val name: String)

    private val app = context.applicationContext

    @Volatile
    private var running = false
    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private val seen = ConcurrentHashMap<String, Pair<Peer, Long>>()
    private var lastPublished: List<Peer> = emptyList()

    /** The port this device listens on (0 until it runs). */
    val port: Int get() = server?.localPort ?: 0

    val isRunning: Boolean get() = running

    fun start(): Boolean {
        if (running) return true
        // (The same number as the UDP port when it is free, so typing in only the other device's address is enough.)
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } }.getOrNull()
            ?: runCatching { ServerSocket(0) }.getOrNull()
            ?: return false
        server = s
        running = true
        // (Some devices drop broadcasts unless an app asks to hear them.)
        lock = runCatching {
            (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.createMulticastLock("glimmerdrop")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        thread(name = "drop-accept", isDaemon = true) { acceptLoop(s) }
        val listener = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
            }
        }.getOrNull()
        udp = listener
        if (listener != null) thread(name = "drop-listen", isDaemon = true) { listenLoop(listener) }
        thread(name = "drop-beacon", isDaemon = true) { beaconLoop() }
        return true
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        runCatching { udp?.close() }
        runCatching { lock?.release() }
        server = null
        udp = null
        lock = null
        seen.clear()
        lastPublished = emptyList()
    }

    /** Opens a connection to a device found (or typed in); call from a background thread. */
    fun connect(host: String, port: Int): Link? = runCatching {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), 4_000)
        Link(socket, app.cacheDir)
    }.getOrNull()

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val socket = runCatching { s.accept() }.getOrNull() ?: break
            runCatching {
                socket.tcpNoDelay = true
                onIncoming(Link(socket, app.cacheDir))
            }.onFailure { runCatching { socket.close() } }
        }
    }

    private fun listenLoop(socket: DatagramSocket) {
        val buffer = ByteArray(1024)
        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            if (runCatching { socket.receive(packet) }.isFailure) break
            runCatching {
                val o = JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                if (o.optString("g") != "gd") return@runCatching
                val peerToken = o.optString("t")
                if (peerToken.isEmpty() || peerToken == token) return@runCatching
                val peer = Peer(
                    host = packet.address.hostAddress.orEmpty(),
                    port = o.optInt("p"),
                    token = peerToken,
                    name = o.optString("n").ifBlank { "Gerät" },
                )
                if (peer.host.isEmpty() || peer.port <= 0) return@runCatching
                seen[peerToken] = peer to System.currentTimeMillis()
                publish()
            }
        }
    }

    private fun beaconLoop() {
        val sender = udp ?: runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return
        while (running) {
            val message = JSONObject()
                .put("g", "gd").put("v", 1).put("t", token).put("n", displayName()).put("p", port)
                .toString().toByteArray(Charsets.UTF_8)
            for (target in broadcastTargets()) {
                runCatching { sender.send(DatagramPacket(message, message.size, target, PORT)) }
            }
            // Devices that went quiet are gone from the list.
            val now = System.currentTimeMillis()
            if (seen.entries.removeAll { now - it.value.second > PEER_LIFETIME_MS }) publish()
            runCatching { Thread.sleep(BEACON_EVERY_MS) }
        }
        if (sender !== udp) runCatching { sender.close() }
    }

    private fun publish() {
        val list = seen.values.map { it.first }.sortedBy { it.name.lowercase() }
        if (list == lastPublished) return
        lastPublished = list
        onPeers(list)
    }

    private fun broadcastTargets(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching { list += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.interfaceAddresses.forEach { ia -> ia.broadcast?.let { list += it } }
            }
        }
        return list.distinct()
    }

    /**
     * One connection to another device. Messages: a byte 1, the length (4 bytes) and the JSON text – or a byte 2, the size (8 bytes)
     * and that many bytes of a file. Everything that is sent goes through one thread, in order, so a long file never blocks the screen.
     */
    class Link internal constructor(private val socket: Socket, private val cacheDir: File) {
        interface Events {
            /** A message arrived (on the reading thread). */
            fun json(text: String)

            /** A file of [size] bytes begins: the file to write it to, or null to refuse it (the connection is closed). */
            fun fileBegin(size: Long): File?

            fun fileProgress(done: Long, size: Long)

            fun fileEnd(file: File)

            fun closed()
        }

        private val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
        private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 64 * 1024))
        private val sender: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "drop-send").apply { isDaemon = true } }

        @Volatile
        var isClosed = false
            private set

        /** Where the other device is, for a name in the list. */
        val remote: String get() = socket.inetAddress?.hostAddress.orEmpty()

        fun start(events: Events) {
            thread(name = "drop-link", isDaemon = true) { read(events) }
        }

        private fun read(events: Events) {
            try {
                while (!isClosed) {
                    when (input.read()) {
                        -1 -> break
                        1 -> {
                            val length = input.readInt()
                            if (length < 0 || length > MAX_JSON) break
                            val bytes = ByteArray(length)
                            input.readFully(bytes)
                            events.json(String(bytes, Charsets.UTF_8))
                        }
                        2 -> {
                            val size = input.readLong()
                            val target = events.fileBegin(size) ?: break
                            var done = 0L
                            var tick = 0L
                            try {
                                target.outputStream().use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (done < size) {
                                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), size - done).toInt())
                                        if (n < 0) throw EOFException()
                                        out.write(buffer, 0, n)
                                        done += n
                                        val now = System.currentTimeMillis()
                                        if (now - tick > 150) {
                                            tick = now
                                            events.fileProgress(done, size)
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                target.delete()
                                throw t
                            }
                            events.fileEnd(target)
                        }
                        else -> break
                    }
                }
            } catch (_: Throwable) {
            } finally {
                close()
                events.closed()
            }
        }

        /** Sends a message; false if the connection is gone. */
        fun sendJson(text: String) {
            post {
                val bytes = text.toByteArray(Charsets.UTF_8)
                output.writeByte(1)
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }
        }

        /**
         * Sends the message [meta] about a file and then the file itself ([size] bytes from [source]); [progress] is told how much of it
         * has gone (from the sending thread), [finished] when it is through or when it failed (false).
         */
        fun sendFile(meta: String, source: InputStream, size: Long, progress: (Long) -> Unit, finished: (Boolean) -> Unit) {
            post({
                try {
                    val bytes = meta.toByteArray(Charsets.UTF_8)
                    output.writeByte(1)
                    output.writeInt(bytes.size)
                    output.write(bytes)
                    output.writeByte(2)
                    output.writeLong(size)
                    val buffer = ByteArray(64 * 1024)
                    var left = size
                    var done = 0L
                    var tick = 0L
                    source.use { stream ->
                        while (left > 0) {
                            val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                            if (n < 0) throw EOFException()
                            output.write(buffer, 0, n)
                            left -= n
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - tick > 150) {
                                tick = now
                                progress(done)
                            }
                        }
                    }
                    output.flush()
                    finished(true)
                } catch (t: Throwable) {
                    runCatching { source.close() }
                    finished(false)
                    throw t
                }
            })
        }

        private fun post(job: () -> Unit) {
            if (isClosed) return
            runCatching {
                sender.execute {
                    try {
                        job()
                    } catch (_: Throwable) {
                        close()
                    }
                }
            }
        }

        fun close() {
            if (isClosed) return
            isClosed = true
            runCatching { socket.close() }
            runCatching { sender.shutdownNow() }
        }

        private companion object {
            const val MAX_JSON = 1_500_000
        }
    }

    companion object {
        /** The port every device listens on for the others' messages. */
        const val PORT = 47831
        private const val BEACON_EVERY_MS = 1_500L
        private const val PEER_LIFETIME_MS = 6_000L

        /** This device's address in the local network, for typing it in on the other one. */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
