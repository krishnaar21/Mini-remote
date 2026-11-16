package com.krishna.miniremote

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.miniremote.net.HostValidator
import com.krishna.miniremote.net.RemoteAdapter
import com.krishna.miniremote.net.Unsupported
import com.krishna.miniremote.net.UserFacing
import kotlinx.coroutines.CompletableDeferred
import com.krishna.miniremote.net.adapterFor
import com.krishna.miniremote.net.isCertChanged
import com.krishna.miniremote.net.Discovery
import kotlinx.coroutines.CancellationException
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
    var scanNote by mutableStateOf<String?>(null); private set
    var mutedHint by mutableStateOf(false); private set
    /** Non-null while an Android / Google TV is showing a pairing code and we're waiting for it. */
    var pairPrompt by mutableStateOf<PairPrompt?>(null); private set
    private var codeAnswer: CompletableDeferred<String?>? = null

    val active: Device? get() = devices.firstOrNull { it.id == activeId }

    private var adapter: RemoteAdapter? = null
    private var connectJob: Job? = null
    private var toastJob: Job? = null
    private val queue = Channel<RemoteKey>(64, BufferOverflow.DROP_OLDEST)

    init {
        feel.haptics = settings.haptics; feel.sound = settings.sound
        viewModelScope.launch {
            for (k in queue) {
                val a = adapter
                if (link != Link.READY || a == null) continue
                try { a.send(k) } catch (e: CancellationException) { throw e } catch (e: Exception) {
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
        feel.fire(when (key) { RemoteKey.POWER -> Feel.HEAVY; RemoteKey.OK -> Feel.PRESS; else -> Feel.PRESS })
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
                say(when {
                    e is UserFacing -> e.message ?: "Not supported"
                    e.isCertChanged() -> "${d.name}'s certificate changed. Remove and add it again if you reset it."
                    else -> "Couldn't reach ${d.name}"
                })
                feel.fire(Feel.ERROR)
            }
        }
    }

    /** Shows the pairing dialog and suspends until the user enters a code (or cancels: null). */
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

    fun addManual(name: String, host: String, brand: Brand): String? {
        val h = host.trim()
        if (!HostValidator.isPrivateIPv4(h)) return "Enter a local address like 192.168.1.20"
        val d = Device("${brand.name.lowercase()}-$h", name.trim().ifBlank { brand.label }, h, brand)
        devices = devices.filter { it.id != d.id } + d
        store.saveDevices(devices)
        select(d)
        return null
    }

    fun remove(d: Device) {
        devices = devices.filter { it.id != d.id }
        store.saveDevices(devices)
        if (activeId == d.id) {
            adapter?.close(); adapter = null; activeId = null; store.saveActive(null); link = Link.IDLE
        }
    }

    fun scan() {
        if (scanning) return
        scanning = true; scanNote = null
        viewModelScope.launch {
            try {
                val found = Discovery.scan()
                val known = devices.associateBy { it.id }
                val merged = devices + found.filter { it.id !in known }
                devices = merged; store.saveDevices(merged)
                scanNote = if (found.isEmpty()) "No TVs found. Check that the phone and TV are on the same Wi-Fi, or add by IP."
                else "Found ${found.size} device${if (found.size == 1) "" else "s"}"
            } catch (e: Exception) { scanNote = "Scan failed" } finally { scanning = false }
        }
    }

    fun typeText(text: String) {
        val a = adapter ?: return say("Connect to a TV first")
        viewModelScope.launch {
            try { a.typeText(text); feel.fire(Feel.SUCCESS) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                feel.fire(Feel.ERROR); say(if (e is Unsupported) e.message ?: "Not supported" else "Couldn't type")
            }
        }
    }

    fun backspace() {
        val a = adapter ?: return
        viewModelScope.launch { runCatching { a.backspace() }.onFailure { say(it.message ?: "Not supported") } }
    }

    fun update(s: Settings) {
        settings = s; store.saveSettings(s)
        feel.haptics = s.haptics; feel.sound = s.sound
    }

    override fun onCleared() { adapter?.close() }
}
