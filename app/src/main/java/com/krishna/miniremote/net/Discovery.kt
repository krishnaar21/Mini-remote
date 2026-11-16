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

    suspend fun scan(): List<Device> = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext emptyList()
        val gate = Semaphore(48)
        coroutineScope {
            (1..254).map { n ->
                async { gate.withPermit { probe("$prefix.$n") } }
            }.awaitAll().filterNotNull()
        }
    }

    private fun get(url: String): String? = runCatching {
        Net.probe.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    private fun open(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 500); true }
    }.getOrDefault(false)

    private fun probe(host: String): Device? {
        get("http://$host:8060/query/device-info")?.let { xml ->
            val name = Regex("<user-device-name>(.*?)</user-device-name>").find(xml)?.groupValues?.get(1)
                ?: Regex("<friendly-device-name>(.*?)</friendly-device-name>").find(xml)?.groupValues?.get(1)
                ?: "Roku"
            return Device("roku-$host", name.ifBlank { "Roku" }, host, Brand.ROKU)
        }
        get("http://$host:8001/api/v2/")?.let { json ->
            val name = runCatching { JSONObject(json).getJSONObject("device").optString("name") }.getOrNull()
            return Device("samsung-$host", name?.ifBlank { null } ?: "Samsung TV", host, Brand.SAMSUNG)
        }
        if (open(host, 3000) && open(host, 3001)) return Device("lg-$host", "LG TV ($host)", host, Brand.LG)
        if (open(host, 6466) && open(host, 6467)) return Device("android_tv-$host", "Google TV ($host)", host, Brand.ANDROID_TV)
        return null
    }
}
