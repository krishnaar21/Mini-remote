package com.krishna.miniremote

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.miniremote.net.Discovery
import com.krishna.miniremote.net.HostValidator
import com.krishna.miniremote.net.RemoteAdapter
import com.krishna.miniremote.net.Unsupported
import com.krishna.miniremote.net.UserFacing
import com.krishna.miniremote.net.adapterFor
import com.krishna.miniremote.net.isCertChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RemoteVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val feel = FeelEngine(app)

    var settings by mutableStateOf(store.loadSettings()); private set
    var devices by mutableStateOf(store.loadDevices()); private set
    var activeId by mutableStateOf(store.activeId()); private set
    var link by mutableStateOf(Link.IDLE); private set
    var toast by mutableStateOf<String?>(null); private set
    var scanning by mutableStateOf(false); private set
    /** TVs found by the last scan that aren't saved yet. */
    var found by mutableStateOf<List<Device>>(emptyList()); private set
    /** Why the last connection attempt failed, worded for the user. */
    var lastError by mutableStateOf<String?>(null); private set
    /** Bumps every time a command goes out; the transmit light in the TV pill flashes on it. */
    var txCount by mutableIntStateOf(0); private set
    /** Mute is a toggle on every TV we support, so we track it locally to colour the button. */
    var muted by mutableStateOf(false); private set
    /** Non-null while an Android / Google TV is showing a pairing code and we're waiting for it. */
    var pairPrompt by mutableStateOf<PairPrompt?>(null); private set
    private var codeAnswer: CompletableDeferred<String?>? = null

    val active: Device? get() = devices.firstOrNull { it.id == activeId }
    val localSeen: Boolean get() = store.localSeen()

    private var adapter: RemoteAdapter? = null
    private var connectJob: Job? = null
    private var toastJob: Job? = null
    private val queue = Channel<RemoteKey>(64, BufferOverflow.DROP_OLDEST)

    init {
        applyFeel(settings)
        viewModelScope.launch {
            for (k in queue) {
                val a = adapter
                if (link != Link.READY || a == null) continue
                try { a.send(k); txCount++ } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    feel.fire(Feel.ERROR)
                    say(if (e is Unsupported) e.message ?: "Not supported" else "Lost connection")
                    if (e !is Unsupported) { link = Link.ERROR }
                }
            }
        }
        active?.let { connect(it) }
    }

    fun press(key: RemoteKey, quiet: Boolean = false) {
        if (active == null) { feel.fire(Feel.ERROR); say("Choose a TV first"); return }
        if (link == Link.ERROR) active?.let { connect(it) }
        feel.fire(if (key == RemoteKey.POWER) Feel.HEAVY else Feel.PRESS)
        if (key == RemoteKey.MUTE) muted = !muted
        if (key == RemoteKey.VOL_UP) muted = false
        if (!quiet) say(key.word)
        queue.trySend(key)
    }

    fun scrubTick(key: RemoteKey) { feel.fire(Feel.TICK); queue.trySend(key) }

    fun say(t: String) {
        toast = t
        toastJob?.cancel()
        // Longer messages stay up longer so they can be read.
        toastJob = viewModelScope.launch { delay(1100L + 45L * t.length.coerceAtMost(80)); toast = null }
    }

    fun connect(d: Device) {
        connectJob?.cancel()
        adapter?.close(); adapter = null
        link = Link.CONNECTING
        lastError = null
        connectJob = viewModelScope.launch {
            try {
                val a = adapterFor(d,
                    onToken = { tok -> updateDevice(d.id) { it.copy(token = tok) } },
                    onPin = { pin -> updateDevice(d.id) { it.copy(pin = pin) } },
                    askCode = { err -> askCode(d.name, err) },
                )
                a.connect()
                adapter = a
                link = Link.READY
                feel.fire(Feel.SUCCESS)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                link = Link.ERROR
                val msg = when {
                    e is UserFacing -> e.message ?: "Not supported"
                    e.isCertChanged() -> "${d.name}'s certificate changed. Remove and add it again if you reset it."
                    else -> "Couldn't reach ${d.name}"
                }
                lastError = msg
                say(msg)
                feel.fire(Feel.ERROR)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        adapter?.close(); adapter = null
        codeAnswer?.complete(null)
        link = Link.IDLE
    }

    /** Shows the pairing code entry and suspends until the user enters a code (or cancels: null). */
    private suspend fun askCode(tvName: String, error: String?): String? {
        val d = CompletableDeferred<String?>()
        codeAnswer?.complete(null)
        codeAnswer = d
        pairPrompt = PairPrompt(tvName, error)
        return try { d.await() } finally { if (codeAnswer === d) { codeAnswer = null; pairPrompt = null } }
    }

    fun answerCode(code: String?) { codeAnswer?.complete(code) }

    fun select(d: Device) {
        activeId = d.id; store.saveActive(d.id); connect(d)
    }

    /** Update the stored copy by id, so a token and a pin saved in one connect don't overwrite each other. */
    private fun updateDevice(id: String, change: (Device) -> Device) {
        devices = devices.map { if (it.id == id) change(it) else it }
        store.saveDevices(devices)
    }

    /** Saves a TV (replacing one with the same id) and connects to it. */
    fun addAndSelect(d: Device) {
        devices = devices.filter { it.id != d.id } + d
        store.saveDevices(devices)
        found = found.filter { it.host != d.host }
        select(d)
    }

    /** Returns an error to show, or the new device. */
    fun addManual(host: String, brand: Brand): Pair<String?, Device?> {
        val h = host.trim()
        if (!HostValidator.isPrivateIPv4(h)) return "Enter a home-network address like 192.168.1.20." to null
        val d = Device("${brand.name.lowercase()}-$h", "${brand.label} $h", h, brand)
        addAndSelect(d)
        return null to d
    }

    fun rename(id: String, name: String) {
        val n = name.trim().take(30)
        if (n.isNotEmpty()) updateDevice(id) { it.copy(name = n) }
    }

    fun remove(d: Device) {
        devices = devices.filter { it.id != d.id }
        store.saveDevices(devices)
        if (activeId == d.id) {
            disconnect(); activeId = null; store.saveActive(null)
        }
    }

    fun markLocalSeen() = store.setLocalSeen()

    fun scan() {
        if (scanning) return
        scanning = true; found = emptyList()
        viewModelScope.launch {
            try {
                val hosts = devices.map { it.host }.toSet()
                found = Discovery.scan().filter { it.host !in hosts }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                found = emptyList()
            } finally { scanning = false }
        }
    }

    fun typeText(text: String) {
        val a = adapter ?: return say("Connect to a TV first")
        viewModelScope.launch {
            try { a.typeText(text); txCount++; feel.fire(Feel.SUCCESS) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                feel.fire(Feel.ERROR); say(if (e is Unsupported) e.message ?: "Not supported" else "Couldn't type")
            }
        }
    }

    fun backspace() {
        val a = adapter ?: return
        viewModelScope.launch { runCatching { a.backspace(); txCount++ }.onFailure { say(it.message ?: "Not supported") } }
    }

    fun update(s: Settings) {
        settings = s; store.saveSettings(s)
        applyFeel(s)
    }

    private fun applyFeel(s: Settings) {
        feel.haptics = s.haptics; feel.sound = s.sound
        feel.strength = s.hapticStrength; feel.level = s.soundLevel
    }

    override fun onCleared() { adapter?.close() }
}
