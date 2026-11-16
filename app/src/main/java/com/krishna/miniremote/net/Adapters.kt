package com.krishna.miniremote.net

import android.util.Base64
import com.krishna.miniremote.Brand
import com.krishna.miniremote.Device
import com.krishna.miniremote.RemoteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class Unsupported(msg: String) : Exception(msg)

interface RemoteAdapter {
    suspend fun connect()
    suspend fun send(key: RemoteKey)
    suspend fun typeText(text: String) { throw Unsupported("Typing isn't supported on this TV yet") }
    suspend fun backspace() { throw Unsupported("Typing isn't supported on this TV yet") }
    fun close()
}

fun adapterFor(d: Device, onToken: (String) -> Unit): RemoteAdapter {
    require(HostValidator.isPrivateIPv4(d.host)) { "Only local network addresses are allowed" }
    return when (d.brand) {
        Brand.ROKU -> RokuAdapter(d.host)
        Brand.SAMSUNG -> SamsungAdapter(d.host, d.token, onToken)
        Brand.LG -> LgAdapter(d.host, d.token, onToken)
        Brand.ANDROID_TV -> object : RemoteAdapter {
            override suspend fun connect() { throw Unsupported("Android / Fire TV pairing isn't built yet") }
            override suspend fun send(key: RemoteKey) { throw Unsupported("Android / Fire TV pairing isn't built yet") }
            override fun close() {}
        }
    }
}

/* ---------------------------------------------------------------- Roku (ECP) */

class RokuAdapter(private val host: String) : RemoteAdapter {
    private val keys = mapOf(
        RemoteKey.UP to "Up", RemoteKey.DOWN to "Down", RemoteKey.LEFT to "Left", RemoteKey.RIGHT to "Right",
        RemoteKey.OK to "Select", RemoteKey.BACK to "Back", RemoteKey.HOME to "Home",
        RemoteKey.PLAY_PAUSE to "Play", RemoteKey.POWER to "Power",
        RemoteKey.VOL_UP to "VolumeUp", RemoteKey.VOL_DOWN to "VolumeDown", RemoteKey.MUTE to "VolumeMute",
        RemoteKey.VOICE to "Search",
    )

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val r = Request.Builder().url("http://$host:8060/query/device-info").build()
            Net.plain.newCall(r).execute().use { if (!it.isSuccessful) error("Roku didn't answer (${it.code})") }
        }
    }

    private suspend fun post(path: String) = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("http://$host:8060/$path").post(ByteArray(0).toRequestBody()).build()
        Net.plain.newCall(r).execute().use { res: Response -> if (!res.isSuccessful) error("Roku error ${res.code}") }
    }

    override suspend fun send(key: RemoteKey) { post("keypress/${keys.getValue(key)}") }

    override suspend fun typeText(text: String) {
        for (ch in text) post("keypress/Lit_" + URLEncoder.encode(ch.toString(), "UTF-8"))
    }

    override suspend fun backspace() { post("keypress/Backspace") }
    override fun close() {}
}

/* ------------------------------------------------------------ Samsung (Tizen) */

class SamsungAdapter(
    private val host: String,
    private var token: String?,
    private val onToken: (String) -> Unit,
) : RemoteAdapter {
    private var ws: WebSocket? = null
    private val keys = mapOf(
        RemoteKey.UP to "KEY_UP", RemoteKey.DOWN to "KEY_DOWN", RemoteKey.LEFT to "KEY_LEFT", RemoteKey.RIGHT to "KEY_RIGHT",
        RemoteKey.OK to "KEY_ENTER", RemoteKey.BACK to "KEY_RETURN", RemoteKey.HOME to "KEY_HOME",
        RemoteKey.PLAY_PAUSE to "KEY_PLAY_BACK", RemoteKey.POWER to "KEY_POWER",
        RemoteKey.VOL_UP to "KEY_VOLUP", RemoteKey.VOL_DOWN to "KEY_VOLDOWN", RemoteKey.MUTE to "KEY_MUTE",
        RemoteKey.VOICE to "KEY_VOICE",
    )

    override suspend fun connect() {
        close()
        val name = Base64.encodeToString("Mini Remote".toByteArray(), Base64.NO_WRAP)
        val url = "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name" +
            (token?.let { "&token=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        val ready = CompletableDeferred<Unit>()
        ws = Net.lanTls.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (j.optString("event") == "ms.channel.connect") {
                    j.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { token = it; onToken(it) }
                    ready.complete(Unit)
                } else if (j.optString("event") == "ms.channel.timeOut") {
                    ready.completeExceptionally(IllegalStateException("Pairing prompt timed out on the TV"))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready.completeExceptionally(t)
            }
        })
        // The TV may show an "Allow" prompt the first time, so wait longer.
        try { withTimeout(25_000) { ready.await() } } catch (e: Exception) { close(); throw e }
    }

    override suspend fun send(key: RemoteKey) {
        val s = ws ?: error("Not connected")
        val msg = JSONObject().put("method", "ms.remote.control").put(
            "params", JSONObject().put("Cmd", "Click").put("DataOfCmd", keys.getValue(key))
                .put("Option", "false").put("TypeOfRemote", "SendRemoteKey")
        )
        if (!s.send(msg.toString())) error("Connection lost")
    }

    override fun close() { ws?.close(1000, null); ws = null }
}

/* --------------------------------------------------------------- LG (webOS) */

class LgAdapter(
    private val host: String,
    private var token: String?,
    private val onToken: (String) -> Unit,
) : RemoteAdapter {
    private var ws: WebSocket? = null
    private var pointer: WebSocket? = null
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var muted = false

    private val manifest = JSONObject().put("manifestVersion", 1).put("appVersion", "1.1").put(
        "permissions", org.json.JSONArray(
            listOf(
                "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TEXT",
                "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_MEDIA_PLAYBACK",
            )
        )
    )

    private fun listener(registered: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val j = runCatching { JSONObject(text) }.getOrNull() ?: return
            val type = j.optString("type")
            if (type == "registered") {
                j.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() }?.let { token = it; onToken(it) }
                registered.complete(Unit)
            } else if (type == "error" && j.optString("id") == "register") {
                registered.completeExceptionally(IllegalStateException(j.optString("error", "Pairing refused")))
            }
            pending.remove(j.optString("id"))?.complete(j)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            registered.completeExceptionally(t)
        }
    }

    private suspend fun tryConnect(url: String, client: okhttp3.OkHttpClient) {
        val registered = CompletableDeferred<Unit>()
        val s = client.newWebSocket(Request.Builder().url(url).build(), listener(registered))
        ws = s
        val reg = JSONObject().put("type", "register").put("id", "register").put(
            "payload", JSONObject().put("forcePairing", false).put("pairingType", "PROMPT")
                .put("manifest", manifest).also { p -> token?.let { p.put("client-key", it) } }
        )
        s.send(reg.toString())
        try { withTimeout(30_000) { registered.await() } } catch (e: Exception) { s.cancel(); ws = null; throw e }
    }

    override suspend fun connect() {
        close()
        try {
            tryConnect("ws://$host:3000", Net.plain)
        } catch (e: Exception) {
            // Newer webOS versions only accept the secure port.
            tryConnect("wss://$host:3001", Net.lanTls)
        }
    }

    private suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject {
        val s = ws ?: error("Not connected")
        val id = "r" + ids.getAndIncrement()
        val d = CompletableDeferred<JSONObject>()
        pending[id] = d
        s.send(JSONObject().put("type", "request").put("id", id).put("uri", uri).put("payload", payload).toString())
        return try { withTimeout(6_000) { d.await() } } finally { pending.remove(id) }
    }

    private suspend fun ensurePointer(): WebSocket {
        pointer?.let { return it }
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            .optJSONObject("payload")?.optString("socketPath").orEmpty()
        if (path.isEmpty()) error("TV refused the input channel")
        val up = CompletableDeferred<Unit>()
        val client = if (path.startsWith("wss")) Net.lanTls else Net.plain
        val s = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { up.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                up.completeExceptionally(t); pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { pointer = null }
        })
        withTimeout(5_000) { up.await() }
        pointer = s
        return s
    }

    private suspend fun button(name: String) {
        ensurePointer().send("type:button\nname:$name\n\n")
    }

    override suspend fun send(key: RemoteKey) {
        when (key) {
            RemoteKey.UP -> button("UP")
            RemoteKey.DOWN -> button("DOWN")
            RemoteKey.LEFT -> button("LEFT")
            RemoteKey.RIGHT -> button("RIGHT")
            RemoteKey.OK -> button("ENTER")
            RemoteKey.BACK -> button("BACK")
            RemoteKey.HOME -> button("HOME")
            RemoteKey.PLAY_PAUSE -> request("ssap://media.controls/play")
            RemoteKey.POWER -> request("ssap://system/turnOff")
            RemoteKey.VOL_UP -> request("ssap://audio/volumeUp")
            RemoteKey.VOL_DOWN -> request("ssap://audio/volumeDown")
            RemoteKey.MUTE -> { muted = !muted; request("ssap://audio/setMute", JSONObject().put("mute", muted)) }
            RemoteKey.VOICE -> throw Unsupported("Voice isn't available on LG over WiFi")
        }
    }

    override suspend fun typeText(text: String) {
        request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", text).put("replace", 0))
    }

    override suspend fun backspace() {
        request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1))
    }

    override fun close() {
        pointer?.close(1000, null); pointer = null
        ws?.close(1000, null); ws = null
        pending.clear()
    }
}
