package com.krishna.miniremote.net

import com.krishna.miniremote.Brand
import com.krishna.miniremote.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Finds TVs by probing every address on the phone's /24 Wi-Fi subnet.
 * Needs no multicast and no extra permissions. Roku and Samsung answer
 * identifiable HTTP endpoints; LG and Android / Google TV are detected by their open remote ports.
 */
object Discovery {
    fun localPrefix(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
            .firstOrNull { HostValidator.isPrivateIPv4(it) }
            ?.substringBeforeLast('.')

    /** The ports that give a TV away: Roku, Samsung, LG (plain and secure), Android / Google TV. */
    private val tvPorts = listOf(8060, 8001, 3000, 3001, 6466)

    /** Every port each brand's remote control uses, for checking a typed-in address. */
    val brandPorts = mapOf(
        Brand.ROKU to listOf(8060),
        Brand.SAMSUNG to listOf(8001, 8002),
        Brand.LG to listOf(3001, 3000),
        Brand.ANDROID_TV to listOf(6466, 6467),
    )

    /** What a typed-in address turned out to be. */
    sealed interface Check {
        /** Identified (or the chosen brand's ports answer). */
        data class Tv(val device: Device) : Check
        /** Nothing answered on any TV port: wrong address, TV off, or another Wi-Fi. */
        data object Silent : Check
        /** Something answered, but not like a supported TV (or not like the chosen brand). */
        data class Unknown(val open: Set<Int>) : Check
    }

    /**
     * Looks at one address the user typed. Slower knocks than [scan] (a TV on Wi-Fi can take a
     * moment to wake its radio), and LG counts with either of its ports, since a typed address is
     * meant to be a TV: older webOS has only 3000, and current firmware may have only 3001.
     * [brand] null = work it out; otherwise the TV is checked against that brand, and a clear
     * answer from another brand wins (a wrong pick shouldn't stop it working).
     */
    suspend fun check(host: String, brand: Brand?): Check = withContext(Dispatchers.IO) {
        if (!HostValidator.isPrivateIPv4(host)) return@withContext Check.Silent
        val ports = brandPorts.values.flatten().distinct()
        val open = coroutineScope { ports.map { p -> async { p.takeIf { knockSlow(host, it) } } }.awaitAll().filterNotNull().toSet() }
        if (open.isEmpty()) return@withContext Check.Silent
        probe(host, open, lenient = true)?.let { return@withContext Check.Tv(it) }
        if (brand != null && brandPorts.getValue(brand).any { it in open }) {
            return@withContext Check.Tv(Device("${brand.name.lowercase()}-$host", nameOf(host, brand) ?: brand.label, host, brand))
        }
        Check.Unknown(open)
    }

    private fun knockSlow(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 1500); true }
    }.getOrDefault(false)

    /**
     * Two passes, so empty addresses cost almost nothing: first a quick knock on the TV ports
     * of every address (all at once, 300 ms each), then a proper look only at the addresses
     * that answered. Each TV is reported through [onFound] the moment it's identified.
     */
    suspend fun scan(onFound: (Device) -> Unit = {}): List<Device> = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext emptyList()
        val gate = Semaphore(128)
        coroutineScope {
            (1..254).map { n ->
                async {
                    val host = "$prefix.$n"
                    val open = gate.withPermit {
                        coroutineScope { tvPorts.map { p -> async { p.takeIf { knock(host, it) } } }.awaitAll().filterNotNull().toSet() }
                    }
                    if (open.isEmpty()) null else probe(host, open)?.also(onFound)
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun knock(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 300); true }
    }.getOrDefault(false)

    private fun get(url: String): String? = runCatching {
        Net.probe.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    private fun open(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 500); true }
    }.getOrDefault(false)

    private fun probe(host: String, open: Set<Int> = tvPorts.toSet(), lenient: Boolean = false): Device? {
        if (8060 in open) get("http://$host:8060/query/device-info")?.let { xml ->
            val name = Regex("<user-device-name>(.*?)</user-device-name>").find(xml)?.groupValues?.get(1)
                ?: Regex("<friendly-device-name>(.*?)</friendly-device-name>").find(xml)?.groupValues?.get(1)
                ?: "Roku"
            return Device("roku-$host", name.ifBlank { "Roku" }, host, Brand.ROKU)
        }
        if (8001 in open) get("http://$host:8001/api/v2/")?.let { json ->
            val name = runCatching { JSONObject(json).getJSONObject("device").optString("name") }.getOrNull()
            return Device("samsung-$host", name?.ifBlank { null } ?: "Samsung TV", host, Brand.SAMSUNG)
        }
        val lg3000 = 3000 in open
        val lg3001 = 3001 in open || (lg3000 && open(host, 3001))
        // Both LG ports, or one of them and the TV says it's webOS. Typed addresses (lenient) need
        // just one: a scan shouldn't call any computer running a dev server on 3000 an LG TV.
        if ((lg3000 && lg3001) || ((lg3000 || lg3001) && (lenient || ssdpSaysWebOs(host)))) {
            return Device("lg-$host", nameOf(host, Brand.LG) ?: "LG TV", host, Brand.LG)
        }
        if (6466 in open && (6467 in open || open(host, 6467))) return Device("android_tv-$host", nameOf(host, Brand.ANDROID_TV) ?: "Google TV", host, Brand.ANDROID_TV)
        return null
    }

    /** Names a TV gets when we can't read its own. */
    fun generic(name: String) = name in setOf("LG TV", "Google TV", "Roku", "Samsung TV") || Brand.entries.any { it.label == name }

    /**
     * The name set on the TV itself (Settings > Device name), read without pairing:
     * Roku and Samsung from their info pages; Google TV from its Cast description (port 8008);
     * LG (and Google TV as a fallback) from its UPnP description, found by asking the TV
     * directly over SSDP. Null when it doesn't say.
     */
    fun nameOf(host: String, brand: Brand): String? {
        if (!HostValidator.isPrivateIPv4(host)) return null
        val n = when (brand) {
            Brand.ROKU -> get("http://$host:8060/query/device-info")?.let { tag(it, "user-device-name") ?: tag(it, "friendly-device-name") }
            Brand.SAMSUNG -> get("http://$host:8001/api/v2/")?.let { runCatching { JSONObject(it).getJSONObject("device").optString("name") }.getOrNull() }
            Brand.ANDROID_TV -> get("http://$host:8008/ssdp/device-desc.xml")?.let { tag(it, "friendlyName") }
                ?: get("http://$host:8008/setup/eureka_info?params=name")?.let { runCatching { JSONObject(it).optString("name") }.getOrNull() }
                ?: ssdpName(host)
            Brand.LG -> ssdpName(host)
        }
        return n?.trim()?.takeIf { it.isNotEmpty() }?.take(30)
    }

    private fun tag(xml: String, t: String): String? =
        Regex("<$t>(.*?)</$t>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotBlank() }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    /** Whether [host] answers SSDP as an LG webOS TV ("SERVER: WebOS/…" in its reply). */
    private fun ssdpSaysWebOs(host: String): Boolean = ssdpReplies(host).any {
        Regex("(?im)^server:.*(webos|lge)").containsMatchIn(it)
    }

    /** Raw SSDP replies from [host] alone (unicast, no multicast needed). */
    private fun ssdpReplies(host: String): List<String> = runCatching {
        java.net.DatagramSocket().use { s ->
            s.soTimeout = 700
            val ask = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n").toByteArray()
            val to = java.net.InetAddress.getByName(host)
            repeat(2) { s.send(java.net.DatagramPacket(ask, ask.size, to, 1900)) }
            val out = mutableListOf<String>()
            val buf = ByteArray(2048)
            val end = System.currentTimeMillis() + 1200
            while (System.currentTimeMillis() < end && out.size < 6) {
                val p = java.net.DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (e: java.net.SocketTimeoutException) { break }
                if (p.address.hostAddress == host) out += String(p.data, 0, p.length)
            }
            out
        }
    }.getOrDefault(emptyList())

    /** Asks [host] (unicast SSDP, no multicast needed) where its descriptions are, and reads a friendlyName. */
    private fun ssdpName(host: String): String? = runCatching {
        java.net.DatagramSocket().use { s ->
            s.soTimeout = 700
            val ask = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n").toByteArray()
            val to = java.net.InetAddress.getByName(host)
            repeat(2) { s.send(java.net.DatagramPacket(ask, ask.size, to, 1900)) }
            val seen = LinkedHashSet<String>()
            val buf = ByteArray(2048)
            val end = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < end && seen.size < 6) {
                val p = java.net.DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (e: java.net.SocketTimeoutException) { break }
                if (p.address.hostAddress != host) continue
                Regex("(?im)^location:\\s*(\\S+)").find(String(p.data, 0, p.length))?.groupValues?.get(1)
                    ?.takeIf { java.net.URI(it).host == host }?.let { seen += it }
            }
            seen.firstNotNullOfOrNull { url -> get(url)?.let { tag(it, "friendlyName") } }
        }
    }.getOrNull()
}
