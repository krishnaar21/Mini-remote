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
class AndroidTvAdapter(
    private val host: String,
    private val pin: String?,
    private val onPin: (String) -> Unit,
    private val askCode: suspend (error: String?) -> String?,
) : RemoteAdapter {
    @Volatile private var sock: SSLSocket? = null
    @Volatile private var io: Framed? = null
    private var scope: CoroutineScope? = null

    private val keys = mapOf(
        RemoteKey.UP to 19, RemoteKey.DOWN to 20, RemoteKey.LEFT to 21, RemoteKey.RIGHT to 22,
        RemoteKey.OK to 23, RemoteKey.BACK to 4, RemoteKey.HOME to 3,
        RemoteKey.PLAY_PAUSE to 85, RemoteKey.POWER to 26,
        RemoteKey.VOL_UP to 24, RemoteKey.VOL_DOWN to 25, RemoteKey.MUTE to 164,
        RemoteKey.VOICE to 84, // SEARCH: opens the TV's search, which offers voice
    )

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
                        m[3] != null -> error("TV reported an error")
                    }
                }
            } catch (e: Exception) {
                ready.completeExceptionally(e)
                if (io === f) io = null
            }
        }
        try { withTimeout(10_000) { ready.await() } } catch (e: Exception) { close(); throw e }
        if (pin == null) tls.seen?.let(onPin)
    }

    private suspend fun key(code: Int) = withContext(Dispatchers.IO) {
        val f = io ?: error("Not connected")
        f.send(Pb().apply { msg(10) { int(1, code.toLong()); int(2, 3) } }.toBytes()) // 3 = SHORT press
    }

    override suspend fun send(key: RemoteKey) = key(keys.getValue(key))

    /** Types letters, digits and a few symbols as key presses (the TV's IME protocol is far heavier). */
    override suspend fun typeText(text: String) {
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

    override suspend fun backspace() = key(67)

    override fun close() {
        scope?.cancel(); scope = null
        io = null
        runCatching { sock?.close() }; sock = null
    }
}
