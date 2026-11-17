package com.krishna.miniremote.net

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Only talk to devices on the local network. Blocks public IPs and hostnames. */
object HostValidator {
    fun isPrivateIPv4(host: String): Boolean {
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matchEntire(host.trim()) ?: return false
        val o = m.groupValues.drop(1).map { it.toInt() }
        if (o.any { it > 255 }) return false
        return o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)
    }
}

object Net {
    /** Normal client (Roku, LG ws://). */
    val plain: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            // A TV must not be able to bounce us to some other (possibly public) address.
            .followRedirects(false).followSslRedirects(false)
            .build()
    }

    /** Short timeouts for subnet scanning. */
    val probe: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(600, TimeUnit.MILLISECONDS)
            .readTimeout(900, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .build()
    }

    /**
     * Samsung and LG TVs use self-signed certificates on their local wss ports, so normal CA
     * checks can't work. Instead we pin: the first successful pairing records the TV's
     * certificate fingerprint ([TlsPin.seen]), and every later connection must present the
     * same one or the handshake fails with [CertChanged]. Only use after
     * HostValidator.isPrivateIPv4(host) passed. One client per adapter, so TLS sessions are
     * never shared between TVs.
     */
    fun pinnedTls(pin: TlsPin): OkHttpClient {
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pin), SecureRandom()) }
        return plain.newBuilder()
            // Long-lived TV sockets: no read timeout, and no pings (several TVs never answer them,
            // so OkHttp would drop a healthy connection after a minute). Drops are noticed by
            // the socket failing, and the app reconnects.
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .sslSocketFactory(ctx.socketFactory, pin)
            // TVs are addressed by IP and their certs don't name it; the pin replaces this check.
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}

/** Thrown when a paired TV presents a different certificate than the one we pinned. */
class CertChanged : CertificateException(
    "This TV's security certificate changed. If you reset or replaced it, remove it and add it again."
)

/**
 * Trust manager that accepts exactly one certificate: [expected] (SHA-256 of the leaf, base64),
 * or — when nothing is pinned yet — whatever the TV presents first, remembered in [seen].
 */
class TlsPin(private val expected: String?) : X509TrustManager {
    @Volatile var seen: String? = null; private set

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("TV sent no certificate")
        val fp = fingerprint(leaf)
        if (expected != null && !MessageDigest.isEqual(fp.toByteArray(), expected.toByteArray())) throw CertChanged()
        seen = fp
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("Client certificates aren't accepted")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun fingerprint(c: X509Certificate): String =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(c.encoded))
    }
}

/** True if [t] or anything that caused it is a [CertChanged]. */
fun Throwable.isCertChanged(): Boolean = generateSequence(this) { it.cause }.take(8).any { it is CertChanged }
