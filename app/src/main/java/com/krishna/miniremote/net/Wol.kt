package com.krishna.miniremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN: the "magic packet" (6 × 0xFF, then the MAC 16 times) broadcast on the home
 * network. Samsung ("Power on with mobile"), LG ("Turn on via Wi-Fi / Mobile") and Roku TVs
 * (Fast TV start) wake with it. Sent only to local broadcast addresses.
 */
object Wol {
    fun parse(mac: String): ByteArray? {
        val hex = mac.filter { it.isLetterOrDigit() }
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    suspend fun wake(mac: String, host: String): Boolean = withContext(Dispatchers.IO) {
        val m = parse(mac) ?: return@withContext false
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (r in 0 until 16) System.arraycopy(m, 0, packet, 6 + r * 6, 6)
        // The /24 broadcast of the TV's own subnet, and the general one.
        val parts = host.split('.')
        val targets = listOfNotNull(
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else null,
            "255.255.255.255",
        )
        var sent = false
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                repeat(3) {
                    for (t in targets) for (port in listOf(9, 7)) {
                        runCatching { s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), port)); sent = true }
                    }
                    Thread.sleep(80)
                }
            }
        }
        sent
    }
}
