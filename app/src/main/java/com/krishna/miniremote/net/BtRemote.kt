package com.krishna.miniremote.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.krishna.miniremote.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The phone as a Bluetooth remote: a HID device with a keyboard (arrows, Enter, Escape,
 * letters) and consumer keys (Home, Back, volume, play, search, power). The TV pairs with it
 * like any Bluetooth remote or keyboard, from its accessories settings, and it then works
 * without Wi-Fi. Android 9+ phones only; iPhones can't act as a Bluetooth remote.
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT first
@RequiresApi(Build.VERSION_CODES.P)
class BtRemote(private val ctx: Context, private val onChange: () -> Unit) {
    private var hid: BluetoothHidDevice? = null
    @Volatile var registered = false; private set
    /** The TV it's connected to now, if any. */
    @Volatile var host: BluetoothDevice? = null; private set
    /** A TV that just paired (connected for the first time): its address and name. */
    var onPaired: ((address: String, name: String) -> Unit)? = null
    private val exec = Executors.newSingleThreadExecutor()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BtRemote.registered = registered
            onChange()
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                onPaired?.invoke(device.address, runCatching { device.name }.getOrNull() ?: "TV")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && host?.address == device.address) host = null
            onChange()
        }
    }

    private val adapter get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    /** Registers the phone as a remote / keyboard (call once Bluetooth permission is granted). */
    fun start() {
        if (hid != null || !enabled) return
        adapter?.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings("Mini Remote", "TV remote", "Mini Remote",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR)
                runCatching { h.registerApp(sdp, null, null, exec, callback) }
            }
            override fun onServiceDisconnected(profile: Int) { hid = null; registered = false; host = null; onChange() }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        runCatching { hid?.unregisterApp() }
        hid?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        hid = null; registered = false; host = null
    }

    /** Connects to a TV paired before (by its Bluetooth address). */
    fun connect(address: String): Boolean {
        val h = hid ?: return false
        if (host?.address == address) return true
        val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        return runCatching { h.connect(dev) }.getOrDefault(false)
    }

    val connected: Boolean get() = host != null

    fun supports(k: RemoteKey) = k in KEYS || k in CONSUMER || k.digit != null

    /** One key press (and release). */
    suspend fun send(k: RemoteKey): Boolean = withContext(Dispatchers.IO) {
        val h = hid ?: return@withContext false
        val d = host ?: return@withContext false
        val c = CONSUMER[k]
        if (c != null) {
            h.sendReport(d, 2, byteArrayOf((c and 0xFF).toByte(), (c shr 8).toByte()))
            delay(30)
            h.sendReport(d, 2, byteArrayOf(0, 0))
            return@withContext true
        }
        val code = KEYS[k] ?: k.digit?.let { digitCode(it) } ?: return@withContext false
        tap(h, d, 0, code)
        true
    }

    /** Types text as keyboard presses (letters, digits, space and a few symbols). */
    suspend fun type(text: String): Boolean = withContext(Dispatchers.IO) {
        val h = hid ?: return@withContext false
        val d = host ?: return@withContext false
        for (ch in text) {
            val (mod, code) = charCode(ch) ?: continue
            tap(h, d, mod, code)
        }
        true
    }

    suspend fun backspace(): Boolean = withContext(Dispatchers.IO) {
        val h = hid ?: return@withContext false
        val d = host ?: return@withContext false
        tap(h, d, 0, 0x2A); true
    }

    private suspend fun tap(h: BluetoothHidDevice, d: BluetoothDevice, mod: Int, code: Int) {
        h.sendReport(d, 1, byteArrayOf(mod.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0))
        delay(25)
        h.sendReport(d, 1, ByteArray(8))
        delay(10)
    }

    companion object {
        /** Keyboard (report 1, boot-style 8 bytes) and consumer control (report 2, one 16-bit usage). */
        private val DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
            0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
            0xC0.toByte(),
            0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x03, 0x19, 0x00, 0x2A, 0xFF.toByte(), 0x03, 0x75, 0x10, 0x95.toByte(), 0x01, 0x81.toByte(), 0x00,
            0xC0.toByte(),
        )

        private val KEYS = mapOf(
            RemoteKey.UP to 0x52, RemoteKey.DOWN to 0x51, RemoteKey.LEFT to 0x50, RemoteKey.RIGHT to 0x4F,
            RemoteKey.OK to 0x28,
        )
        private val CONSUMER = mapOf(
            RemoteKey.HOME to 0x223, RemoteKey.BACK to 0x224, RemoteKey.VOL_UP to 0xE9, RemoteKey.VOL_DOWN to 0xEA,
            RemoteKey.MUTE to 0xE2, RemoteKey.PLAY_PAUSE to 0xCD, RemoteKey.REWIND to 0xB4, RemoteKey.FAST_FORWARD to 0xB3,
            RemoteKey.VOICE to 0x221, RemoteKey.POWER to 0x30, RemoteKey.CH_UP to 0x9C, RemoteKey.CH_DOWN to 0x9D,
            RemoteKey.GUIDE to 0x8D, RemoteKey.MENU to 0x40, RemoteKey.SETTINGS to 0x40, RemoteKey.INFO to 0x60,
            RemoteKey.SUBTITLES to 0x61, RemoteKey.INPUT to 0x89,
        )

        private fun digitCode(c: Char) = if (c == '0') 0x27 else 0x1E + (c - '1')

        private fun charCode(c: Char): Pair<Int, Int>? = when (c) {
            in 'a'..'z' -> 0 to 0x04 + (c - 'a')
            in 'A'..'Z' -> 0x02 to 0x04 + (c - 'A')
            in '1'..'9' -> 0 to 0x1E + (c - '1')
            '0' -> 0 to 0x27
            ' ' -> 0 to 0x2C
            '.' -> 0 to 0x37
            ',' -> 0 to 0x36
            '-' -> 0 to 0x2D
            '\'' -> 0 to 0x34
            '@' -> 0x02 to 0x1F
            '?' -> 0x02 to 0x38
            '!' -> 0x02 to 0x1E
            else -> null
        }
    }
}
