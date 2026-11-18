package com.krishna.miniremote.net

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.krishna.miniremote.RemoteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.security.auth.x500.X500Principal

/*
 * Android TV / Google TV remote, protocol v2 (what the Google TV phone app uses).
 *
 *  - Pairing on port 6467: mutual TLS + small protobuf messages. The TV shows a 6-character
 *    hex code; we prove we saw it by hashing both TLS public keys with the code.
 *  - Control on port 6466: mutual TLS with the same client certificate, key presses as protobuf.
 *
 * The client certificate lives in the Android Keystore, so the private key never leaves
 * the phone's secure hardware and isn't readable by the app. Protobuf is hand-encoded
 * (only a handful of fields) to avoid a dependency.
 */

/* ------------------------------------------------------------ tiny protobuf */

internal class Pb {
    private val out = ByteArrayOutputStream()
    private fun raw(v: Long) {
        var x = v
        while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
    }
    fun int(field: Int, v: Long) { raw((field shl 3).toLong()); raw(v) }
    fun bytes(field: Int, b: ByteArray) { raw(((field shl 3) or 2).toLong()); raw(b.size.toLong()); out.write(b) }
    fun str(field: Int, s: String) = bytes(field, s.toByteArray())
    fun msg(field: Int, block: Pb.() -> Unit) = bytes(field, Pb().apply(block).toBytes())
    fun toBytes(): ByteArray = out.toByteArray()

    companion object {
        /** Decodes one message: field number -> values (Long for varints, ByteArray for length-delimited). */
        fun parse(b: ByteArray): Map<Int, List<Any>> {
            val m = HashMap<Int, MutableList<Any>>()
            var i = 0
            fun varint(): Long {
                var r = 0L; var s = 0
                while (true) {
                    require(i < b.size && s < 64) { "Bad message" }
                    val x = b[i++].toInt() and 0xFF
                    r = r or ((x and 0x7F).toLong() shl s)
                    if (x and 0x80 == 0) return r
                    s += 7
                }
            }
            while (i < b.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                val v: Any = when ((tag and 7).toInt()) {
                    0 -> varint()
                    2 -> {
                        val n = varint().toInt()
                        require(n >= 0 && i + n <= b.size) { "Bad message" }
                        b.copyOfRange(i, i + n).also { i += n }
                    }
                    1 -> { require(i + 8 <= b.size) { "Bad message" }; i += 8; 0L }
                    5 -> { require(i + 4 <= b.size) { "Bad message" }; i += 4; 0L }
                    else -> error("Bad message")
                }
                m.getOrPut(field) { mutableListOf() }.add(v)
            }
            return m
        }
    }
}

private fun Map<Int, List<Any>>.long(f: Int): Long? = this[f]?.firstOrNull() as? Long
private fun Map<Int, List<Any>>.text(f: Int): String? = (this[f]?.firstOrNull() as? ByteArray)?.decodeToString()
private fun Map<Int, List<Any>>.sub(f: Int): Map<Int, List<Any>>? = (this[f]?.firstOrNull() as? ByteArray)?.let { Pb.parse(it) }

/** Messages on both ports are prefixed with their length as a varint. */
private class Framed(input: InputStream, private val output: OutputStream) {
    private val inp = DataInputStream(input)

    fun read(): ByteArray {
        var n = 0; var s = 0
        while (true) {
            val x = inp.read()
            if (x < 0) error("TV closed the connection")
            n = n or ((x and 0x7F) shl s)
            if (x and 0x80 == 0) break
            s += 7
            require(s < 28) { "Bad message" }
        }
        require(n in 0..65_536) { "Bad message" }
        return ByteArray(n).also { inp.readFully(it) }
    }

    @Synchronized fun send(b: ByteArray) {
        val len = ByteArrayOutputStream()
        var x = b.size
        while (x >= 0x80) { len.write((x and 0x7F) or 0x80); x = x ushr 7 }
        len.write(x)
        output.write(len.toByteArray() + b)
        output.flush()
    }
}

/* ---------------------------------------------------- client certificate */

internal object ClientCert {
    private const val ALIAS = "mini-remote-atv-client"

    @Synchronized private fun ensure(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setRandomizedEncryptionRequired(false)
                .setCertificateSubject(X500Principal("CN=Mini Remote"))
                .setCertificateSerialNumber(BigInteger(63, SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(Date(now - 86_400_000L))
                .setCertificateNotAfter(Date(now + 20L * 365 * 86_400_000L))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                .apply { initialize(spec) }.generateKeyPair()
        }
        return ks
    }

    /** Forgets this phone's Android TV identity (TVs will ask to pair again). */
    fun reset() { runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } }

    fun publicKey(): RSAPublicKey = ensure().getCertificate(ALIAS).publicKey as RSAPublicKey

    /** Opens a mutual-TLS socket: our keystore cert as the client, [pin] checks the TV. */
    fun socket(host: String, port: Int, pin: TlsPin): SSLSocket {
        require(HostValidator.isPrivateIPv4(host)) { "Only local network addresses are allowed" }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ensure(), null) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(pin), SecureRandom()) }
        val raw = java.net.Socket().apply { connect(InetSocketAddress(host, port), 4_000); soTimeout = 15_000 }
        return try {
            (ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket).apply { startHandshake() }
        } catch (e: Exception) { runCatching { raw.close() }; throw e }
    }
}

/* -------------------------------------------------------------- adapter */

/**
 * [askCode] shows the pairing dialog and returns what the user typed, or null if they cancelled.
 * Its argument is an error to show from the previous attempt (null on the first try).
 */
/** Voice message sizes Google TV accepts: 20 KB normally, never under 8 KB. */
const val VOICE_CHUNK = 20 * 1024
const val VOICE_MIN = 8 * 1024

class AndroidTvAdapter(
    private val host: String,
    private val pin: String?,
    private val onPin: (String) -> Unit,
    private val askCode: suspend (error: String?) -> String?,
) : RemoteAdapter {
    @Volatile private var sock: SSLSocket? = null
    @Volatile private var io: Framed? = null
    private var scope: CoroutineScope? = null
    override var onVolume: ((TvVolume) -> Unit)? = null
    override var onDropped: (() -> Unit)? = null
    @Volatile private var closing = false
    /** The TV's keyboard link: counters it last sent, and the app in front. */
    @Volatile private var imeCounter = 0L
    @Volatile private var fieldCounter = 0L
    @Volatile private var currentApp: String? = null
    /** Counters came from a RemoteImeBatchEdit (the source androidtvremote2 uses). */
    @Volatile private var fromBatch = false
    /** When the TV last told us about a text box, and a wait for the next one. */
    @Volatile private var fieldAt = 0L
    @Volatile private var fieldWait: CompletableDeferred<Unit>? = null
    @Volatile private var ourEditAt = 0L
    @Volatile private var awaitBox = false
    /** The TV decided the speech is over (sent back a voice payload). */
    @Volatile var onVoiceDone: (() -> Unit)? = null
    override var onKeyboard: (() -> Unit)? = null
    /** Voice: the session id the TV hands out after Search is pressed. */
    @Volatile private var voiceWait: CompletableDeferred<Long>? = null

    private val keys = mapOf(
        RemoteKey.UP to 19, RemoteKey.DOWN to 20, RemoteKey.LEFT to 21, RemoteKey.RIGHT to 22,
        RemoteKey.OK to 23, RemoteKey.BACK to 4, RemoteKey.HOME to 3,
        RemoteKey.PLAY_PAUSE to 85, RemoteKey.POWER to 26,
        RemoteKey.VOL_UP to 24, RemoteKey.VOL_DOWN to 25, RemoteKey.MUTE to 164,
        RemoteKey.VOICE to 84, // SEARCH: opens the TV's search, which offers voice
        RemoteKey.REWIND to 89, RemoteKey.FAST_FORWARD to 90,
        RemoteKey.CH_UP to 166, RemoteKey.CH_DOWN to 167,
        RemoteKey.GUIDE to 172, RemoteKey.CH_LIST to 172, RemoteKey.INFO to 165,
        RemoteKey.INPUT to 178, RemoteKey.MENU to 82, RemoteKey.AUDIO to 222,
        RemoteKey.SUBTITLES to 175, RemoteKey.SETTINGS to 176,
        RemoteKey.RED to 183, RemoteKey.GREEN to 184, RemoteKey.YELLOW to 185, RemoteKey.BLUE to 186,
    ) + RemoteKey.entries.filter { it.digit != null }.associateWith { 7 + (it.digit!! - '0') }

    override suspend fun connect() {
        close()
        if (pin == null) pair()
        openRemote()
    }

    /* ---- pairing (port 6467) */

    private fun outer(block: Pb.() -> Unit) = Pb().apply { int(1, 2); int(2, 200); block() }.toBytes()

    private fun Framed.expect(field: Int): Map<Int, List<Any>> {
        val m = Pb.parse(read())
        when (m.long(2)) {
            200L -> {}
            402L -> throw UserFacing("That code didn't match. Try connecting again.")
            else -> throw UserFacing("The TV stopped pairing. Try again.")
        }
        if (m[field] == null) throw UserFacing("The TV answered unexpectedly. Try again.")
        return m
    }

    private suspend fun pair() {
        // The code check below is what authenticates the TV during pairing.
        val s = withContext(Dispatchers.IO) { ClientCert.socket(host, 6467, TlsPin(null)) }
        try {
            val f = Framed(s.inputStream, s.outputStream)
            val hex: Pb.() -> Unit = { int(1, 3); int(2, 6) } // HEXADECIMAL, 6 symbols
            withContext(Dispatchers.IO) {
                f.send(outer { msg(10) { str(1, "com.krishna.miniremote"); str(2, "Mini Remote") } }); f.expect(11)
                f.send(outer { msg(20) { msg(1, hex); int(3, 1) } }); f.expect(20)
                f.send(outer { msg(30) { msg(1, hex); int(2, 1) } }); f.expect(31)
            }
            // The TV is now showing the code.
            val server = s.session.peerCertificates.first().publicKey as RSAPublicKey
            val client = withContext(Dispatchers.IO) { ClientCert.publicKey() }
            var err: String? = null
            for (attempt in 1..3) {
                val code = askCode(err)?.trim()?.uppercase() ?: throw UserFacing("Pairing cancelled")
                if (!Regex("^[0-9A-F]{6}$").matches(code)) { err = "Enter the 6 characters shown on the TV"; continue }
                val secret = MessageDigest.getInstance("SHA-256").run {
                    update(unsigned(client.modulus)); update(unsigned(client.publicExponent))
                    update(unsigned(server.modulus)); update(unsigned(server.publicExponent))
                    update(code.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                    digest()
                }
                // The first two characters are a checksum of the rest, so typos are caught here.
                if ((secret[0].toInt() and 0xFF) != code.substring(0, 2).toInt(16)) {
                    err = "That code doesn't match. Check it and try again."; continue
                }
                withContext(Dispatchers.IO) { f.send(outer { msg(40) { bytes(1, secret) } }); f.expect(41) }
                return
            }
            throw UserFacing("That code didn't match. Try connecting again.")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { s.close() } }
        }
    }

    private fun unsigned(n: BigInteger): ByteArray = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()

    /* ---- control (port 6466) */

    private suspend fun openRemote() {
        val tls = TlsPin(pin)
        val s = withContext(Dispatchers.IO) {
            try { ClientCert.socket(host, 6466, tls) } catch (e: Exception) {
                if (e.isCertChanged() || pin == null) throw e
                throw UserFacing("The TV no longer recognises this phone. Remove it and add it again to re-pair.")
            }
        }
        val f = Framed(s.inputStream, s.outputStream)
        s.soTimeout = 0 // the TV pings every few seconds; the reader below answers
        sock = s; io = f
        val ready = CompletableDeferred<Unit>()
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        sc.launch {
            try {
                while (true) {
                    val m = Pb.parse(f.read())
                    when {
                        m[1] != null -> f.send(Pb().apply {
                            msg(1) {
                                int(1, 622)
                                msg(2) {
                                    str(1, Build.MODEL ?: "Phone"); str(2, Build.MANUFACTURER ?: "Android")
                                    int(3, 1); str(4, "1"); str(5, "com.krishna.miniremote"); str(6, "0.1.0")
                                }
                            }
                        }.toBytes())
                        m[2] != null -> { f.send(Pb().apply { msg(2) { int(1, 622) } }.toBytes()); ready.complete(Unit) }
                        m[8] != null -> {
                            val v = m.sub(8)?.long(1) ?: 0L
                            f.send(Pb().apply { msg(9) { int(1, v) } }.toBytes())
                        }
                        // RemoteSetVolumeLevel: the TV's volume (max 6, level 7, muted 8), sent whenever it changes.
                        // RemoteImeKeyInject: which app is in front (app_info.app_package), sent as it changes.
                        // RemoteImeKeyInject: the app in front (app_info.app_package) and the keyboard
                        // counters our edits must carry (app_info.counter, text_field_status.counter_field).
                        m[20] != null -> m.sub(20)?.let { k ->
                            k.sub(1)?.let { info ->
                                info.text(12)?.takeIf { it.isNotEmpty() }?.let { currentApp = it }
                                if (!fromBatch) info.long(1)?.let { imeCounter = it }
                            }
                            if (!fromBatch) k.sub(2)?.long(1)?.let { fieldCounter = it }
                        }
                        // RemoteImeBatchEdit from the TV: a text box is active; these are the counters
                        // our edits must carry. A new box (none for a while, and not an echo of our
                        // own typing) also offers the phone keyboard.
                        m[21] != null -> m.sub(21)?.let { b ->
                            b.long(1)?.let { imeCounter = it }
                            b.long(2)?.let { fieldCounter = it }
                            fromBatch = true
                            val now = System.currentTimeMillis()
                            val fresh = now - fieldAt > 4000 && now - ourEditAt > 4000
                            fieldAt = now
                            fieldWait?.complete(Unit)
                            if (fresh) onKeyboard?.invoke()
                        }
                        // RemoteImeShowRequest: a text box got focus.
                        m[22] != null -> {
                            m.sub(22)?.sub(2)?.long(1)?.let { fieldCounter = it }
                            val now = System.currentTimeMillis()
                            val fresh = now - fieldAt > 4000
                            fieldAt = now; fieldWait?.complete(Unit)
                            if (fresh) onKeyboard?.invoke()
                        }
                        // RemoteVoiceBegin: the TV opened a voice session for us.
                        m[30] != null -> voiceWait?.complete(m.sub(30)?.long(1) ?: 0L)
                        // RemoteVoicePayload back from the TV: it heard the end of speech.
                        m[31] != null -> onVoiceDone?.invoke()
                        m[50] != null -> m.sub(50)?.let { v ->
                            val max = v.long(6)?.toInt() ?: 0
                            if (max > 0) onVolume?.invoke(TvVolume((v.long(7) ?: 0L).toInt(), max, (v.long(8) ?: 0L) != 0L))
                        }
                        // RemoteError: the TV didn't like one message (e.g. a key it lacks). Not fatal.
                        m[3] != null -> {}
                    }
                }
            } catch (e: Exception) {
                val wasUp = ready.isCompleted
                ready.completeExceptionally(e)
                if (io === f) { io = null; if (wasUp && !closing) onDropped?.invoke() }
            }
        }
        try { withTimeout(10_000) { ready.await() } } catch (e: Exception) { close(); throw e }
        if (pin == null) tls.seen?.let(onPin)
    }

    private suspend fun key(code: Int) = withContext(Dispatchers.IO) {
        val f = io ?: error("Not connected")
        f.send(Pb().apply { msg(10) { int(1, code.toLong()); int(2, 3) } }.toBytes()) // 3 = SHORT press
    }

    override suspend fun send(key: RemoteKey) = key(keys[key] ?: missing(key, "this TV"))
    override fun supports(key: RemoteKey) = key in keys

    private fun isLauncher(p: String) = "launcher" in p.lowercase() || p == "com.google.android.apps.tv.launcherx" || p == "com.google.android.tvlauncher"

    /** True when the TV shows its home screen (or we don't know yet). */
    val onHome: Boolean get() = currentApp?.let(::isLauncher) ?: true

    /**
     * Opens search: in an app, that app's own search (a full Search key press: down, then up);
     * on the home screen, the TV's search. Some TVs start voice search from the key: that
     * session is answered and closed right away, which leaves the search screen with its keyboard.
     */
    override suspend fun openSearch() {
        fieldAt = 0L; awaitBox = true // the search box will be a new text box: typing waits for it
        withContext(Dispatchers.IO) {
            val f = io ?: error("Not connected")
            f.send(Pb().apply { msg(10) { int(1, 84); int(2, 1) } }.toBytes()) // down
            f.send(Pb().apply { msg(10) { int(1, 84); int(2, 2) } }.toBytes()) // up
        }
        val w = CompletableDeferred<Long>().also { voiceWait = it }
        val id = kotlinx.coroutines.withTimeoutOrNull(1500) { w.await() }
        voiceWait = null
        if (id != null) { withContext(Dispatchers.IO) { io?.send(Pb().apply { msg(30) { int(1, id) } }.toBytes()) }; voiceEnd(id) }
    }

    /** Opens an app: a package name (launched through the Play Store link) or a full app link. */
    /** The app's Play Store page on the TV (install, open or uninstall there). */
    override suspend fun openStore(id: String) = launchApp("market://details?id=${id.substringAfter("id=")}")

    override suspend fun launchApp(id: String) = withContext(Dispatchers.IO) {
        val f = io ?: error("Not connected")
        val link = if ("://" in id) id else "market://launch?id=$id"
        f.send(Pb().apply { msg(90) { str(1, link) } }.toBytes())
    }

    /** Types letters, digits and a few symbols as key presses (the TV's IME protocol is far heavier). */
    /**
     * Voice to the TV's Assistant: presses Search, waits for the TV's voice session, and
     * answers it. Returns the session id, or null if the TV didn't open one (then the search
     * screen is simply open). Audio then goes with [voiceChunk] (16-bit mono PCM, 8 kHz).
     */
    suspend fun voiceBegin(): Long? {
        val w = CompletableDeferred<Long>().also { voiceWait = it }
        key(84)
        val id = kotlinx.coroutines.withTimeoutOrNull(2500) { w.await() } ?: return null
        voiceWait = null
        withContext(Dispatchers.IO) { (io ?: error("Not connected")).send(Pb().apply { msg(30) { int(1, id) } }.toBytes()) }
        return id
    }

    /** One voice message: 16-bit mono PCM at 8 kHz, at least 8 KB (padded with silence), at most 20 KB. TVs ignore smaller ones. */
    suspend fun voiceChunk(id: Long, pcm: ByteArray) = withContext(Dispatchers.IO) {
        val f = io ?: error("Not connected")
        var i = 0
        while (i < pcm.size) {
            var part = pcm.copyOfRange(i, minOf(pcm.size, i + VOICE_CHUNK))
            if (part.size < VOICE_MIN) part = part.copyOf(VOICE_MIN)
            f.send(Pb().apply { msg(31) { int(1, id); bytes(2, part) } }.toBytes())
            i += VOICE_CHUNK
        }
    }

    suspend fun voiceEnd(id: Long) = withContext(Dispatchers.IO) {
        runCatching { (io ?: error("Not connected")).send(Pb().apply { msg(32) { int(1, id) } }.toBytes()) }
        Unit
    }

    /**
     * Inserts [text] at the cursor of the TV's focused box through its keyboard link (any
     * characters): a RemoteImeBatchEdit with start = end = the text's length, as Google's remote
     * does. Live typing sends what's new, and deletions as Delete key presses.
     */
    override suspend fun setText(text: String) = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext
        val f = io ?: error("Not connected")
        // No text box seen yet (search still opening): wait up to 3 s for the TV to report it,
        // so the letters land in the box with its current counters.
        if (awaitBox && fieldAt == 0L) {
            awaitBox = false
            val w = CompletableDeferred<Unit>().also { fieldWait = it }
            kotlinx.coroutines.withTimeoutOrNull(3000) { w.await() }
            fieldWait = null
        }
        ourEditAt = System.currentTimeMillis()
        // As androidtvremote2's send_text: insert, with start = end = length - 1.
        val n = (text.length - 1).toLong()
        f.send(Pb().apply {
            msg(21) {
                int(1, imeCounter); int(2, fieldCounter)
                msg(3) { int(1, 1); msg(2) { int(1, n); int(2, n); str(3, text) } }
            }
        }.toBytes())
    }

    /** Live TV apps (tuners) by package name. */
    private fun isLive(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "com.google.android.tv" || p == "com.android.tv" || p.endsWith(".tvinput") || "tvcenter" in p ||
            "livetv" in p || "live_tv" in p || ".dtv" in p || "tvplayer" in p || ".tuner" in p || "channels" in p ||
            // India's TV services on Android TV: Tata Play, Airtel Xstream, Dish TV / d2h (Watcho), JioTV, Sun Direct.
            "tatasky" in p || "tataplay" in p || "airtel" in p || "dishtv" in p || "d2h" in p || "watcho" in p || "jiotv" in p || "jioplay" in p || "sundirect" in p
    }

    override suspend fun nowOn(): NowOn {
        val app = currentApp ?: return NowOn("", false)
        return NowOn(if (isLauncher(app)) "Home" else app, isLive(app))
    }

    override suspend fun typeText(text: String) = setText(text)

    @Suppress("unused")
    private suspend fun typeKeys(text: String) {
        val codes = text.lowercase().map { c ->
            when (c) {
                in 'a'..'z' -> 29 + (c - 'a')
                in '0'..'9' -> 7 + (c - '0')
                ' ' -> 62
                '.' -> 56
                '@' -> 77
                '-' -> 69
                else -> throw Unsupported("Only letters, numbers and spaces can be typed on this TV")
            }
        }
        codes.forEach { key(it) }
    }

    override suspend fun backspace() { ourEditAt = System.currentTimeMillis(); key(67) }
    /** Enter: the TV's keyboard runs the search. */
    override suspend fun submit() { ourEditAt = System.currentTimeMillis(); key(66) }
    /** To the end of the box (Move end), then delete everything: what we typed and what was there. */
    override suspend fun clearBox(known: Int) {
        ourEditAt = System.currentTimeMillis()
        key(123)
        repeat(known + 40) { key(67) }
    }

    override fun close() {
        closing = true
        scope?.cancel(); scope = null
        io = null
        runCatching { sock?.close() }; sock = null
        closing = false
    }
}
