package com.krishna.miniremote.net

import android.util.Base64
import com.krishna.miniremote.Brand
import com.krishna.miniremote.Device
import com.krishna.miniremote.RemoteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** An error whose message is written for the user and can be shown as is. */
open class UserFacing(msg: String) : Exception(msg)

class Unsupported(msg: String) : UserFacing(msg)

/** What the TV is showing: an app or input by name, and whether it's live TV / an input. */
data class NowOn(val name: String, val live: Boolean)

/** An app installed on the TV, as the TV lists it. [icon] is a URL on the TV, if it has one. */
/** The TV's own volume: [level] of [max], and whether it's muted. */
data class TvVolume(val level: Int, val max: Int, val muted: Boolean)

data class TvApp(val id: String, val name: String, val icon: String? = null, val version: String? = null)

interface RemoteAdapter {
    /** Called when the TV reports its volume by itself (Android TV, LG). */
    var onVolume: ((TvVolume) -> Unit)?
        get() = null
        set(_) {}

    /** Asks the TV for its volume; null when this TV can't tell. */
    suspend fun readVolume(): TvVolume? = null

    suspend fun connect()
    suspend fun send(key: RemoteKey)
    suspend fun typeText(text: String) { throw Unsupported("Typing isn't supported on this TV yet") }
    suspend fun backspace() { throw Unsupported("Typing isn't supported on this TV yet") }

    /** Apps installed on the TV. */
    suspend fun listApps(): List<TvApp> { throw Unsupported("This TV can't list its apps") }

    /** What's on screen now, for Watch / Navigate. Brands that can't tell throw [Unsupported]. */
    suspend fun nowOn(): NowOn { throw Unsupported("This TV can't say what's on screen") }

    /** Opens the app's page in the TV's own app store, where it can be installed or removed. */
    suspend fun openStore(id: String) { throw Unsupported("Install and remove apps from the TV's app store") }

    /** Opens an app by this TV's own id for it. */
    suspend fun launchApp(id: String) { throw Unsupported("Opening apps isn't supported on this TV yet") }

    /** Tunes live TV: types the number's digits; the TV switches after a moment. */
    suspend fun tuneChannel(number: String) {
        val keys = number.mapNotNull { RemoteKey.forDigit(it) }
        if (keys.isEmpty()) throw UserFacing("That isn't a channel number")
        for (k in keys) { send(k); delay(180) }
    }

    /** Opens the TV's own search screen (where the TV has a key for it). */
    suspend fun openSearch() { send(RemoteKey.VOICE) }

    /** Opens the TV's search and types [query] into it. */
    suspend fun search(query: String) {
        send(RemoteKey.VOICE)
        delay(1500)
        typeText(query)
    }

    fun close()
}

/** Every key a brand doesn't have says so in words. */
internal fun missing(key: RemoteKey, brand: String): Nothing = throw Unsupported("${key.word} isn't available on $brand")

/** Undoes the five XML entities (Roku app names). */
private fun xmlText(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&").trim()

/**
 * [onToken] saves a pairing token; [onPin] saves the TV's certificate fingerprint the first
 * time a secure connection succeeds (see [TlsPin]); [askCode] asks the user for a pairing code.
 */
fun adapterFor(
    d: Device,
    onToken: (String) -> Unit,
    onPin: (String) -> Unit,
    askCode: suspend (error: String?) -> String?,
): RemoteAdapter {
    require(HostValidator.isPrivateIPv4(d.host)) { "Only local network addresses are allowed" }
    return when (d.brand) {
        Brand.ROKU -> RokuAdapter(d.host)
        Brand.SAMSUNG -> SamsungAdapter(d.host, d.token, d.pin, onToken, onPin)
        Brand.LG -> LgAdapter(d.host, d.token, d.pin, onToken, onPin)
        Brand.ANDROID_TV -> AndroidTvAdapter(d.host, d.pin, onPin, askCode)
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
        RemoteKey.REWIND to "Rev", RemoteKey.FAST_FORWARD to "Fwd",
        RemoteKey.CH_UP to "ChannelUp", RemoteKey.CH_DOWN to "ChannelDown", RemoteKey.INFO to "Info",
    ) + RemoteKey.entries.filter { it.digit != null }.associateWith { "Lit_${it.digit}" }

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

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("http://$host:8060/$path").build()
        Net.plain.newCall(r).execute().use { res -> if (!res.isSuccessful) error("Roku error ${res.code}"); res.body?.string().orEmpty() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override suspend fun send(key: RemoteKey) { post("keypress/${keys[key] ?: missing(key, "Roku")}") }

    override suspend fun typeText(text: String) {
        for (ch in text) post("keypress/Lit_" + enc(ch.toString()))
    }

    /** Channels (type appl) and, on Roku TVs, inputs like HDMI 1 (type tvin). Icons come from the Roku. */
    override suspend fun listApps(): List<TvApp> =
        Regex("<app id=\"([^\"]+)\"([^>]*)>([^<]*)</app>").findAll(get("query/apps")).map { m ->
            val id = m.groupValues[1]
            val version = Regex("version=\"([^\"]*)\"").find(m.groupValues[2])?.groupValues?.get(1)?.ifEmpty { null }
            TvApp(id, xmlText(m.groupValues[3]).ifEmpty { id }, "http://$host:8060/query/icon/${enc(id)}", version)
        }.toList()

    /** ECP install: opens the channel's store page on the Roku (add or remove it there). */
    override suspend fun openStore(id: String) { post("install/${enc(id)}") }

    override suspend fun launchApp(id: String) { post("launch/${enc(id)}") }

    /** active-app: an <app> with an id (tvinput.* = antenna or HDMI), or the bare "Roku" home screen. */
    override suspend fun nowOn(): NowOn {
        val m = Regex("<app( id=\"([^\"]*)\")?[^>]*>([^<]*)</app>").find(get("query/active-app"))
            ?: return NowOn("Home", false)
        val id = m.groupValues[2]
        if (id.isEmpty()) return NowOn("Home", false)
        return NowOn(xmlText(m.groupValues[3]).ifEmpty { id }, id.startsWith("tvinput."))
    }

    /** Roku TVs tune live TV through the antenna input with a channel number like 7 or 7.1. */
    override suspend fun tuneChannel(number: String) { post("launch/tvinput.dtv?ch=${enc(number)}") }

    override suspend fun search(query: String) { post("search/browse?keyword=${enc(query)}") }

    override suspend fun backspace() { post("keypress/Backspace") }
    override fun close() {}
}

/* ------------------------------------------------------------ Samsung (Tizen) */

class SamsungAdapter(
    private val host: String,
    private var token: String?,
    private val pin: String?,
    private val onToken: (String) -> Unit,
    private val onPin: (String) -> Unit,
) : RemoteAdapter {
    private var ws: WebSocket? = null
    private val tls = TlsPin(pin)
    private val client = Net.pinnedTls(tls)
    private val keys = mapOf(
        RemoteKey.UP to "KEY_UP", RemoteKey.DOWN to "KEY_DOWN", RemoteKey.LEFT to "KEY_LEFT", RemoteKey.RIGHT to "KEY_RIGHT",
        RemoteKey.OK to "KEY_ENTER", RemoteKey.BACK to "KEY_RETURN", RemoteKey.HOME to "KEY_HOME",
        RemoteKey.PLAY_PAUSE to "KEY_PLAY_BACK", RemoteKey.POWER to "KEY_POWER",
        RemoteKey.VOL_UP to "KEY_VOLUP", RemoteKey.VOL_DOWN to "KEY_VOLDOWN", RemoteKey.MUTE to "KEY_MUTE",
        RemoteKey.VOICE to "KEY_VOICE",
        RemoteKey.REWIND to "KEY_REWIND", RemoteKey.FAST_FORWARD to "KEY_FF",
        RemoteKey.CH_UP to "KEY_CHUP", RemoteKey.CH_DOWN to "KEY_CHDOWN",
        RemoteKey.GUIDE to "KEY_GUIDE", RemoteKey.CH_LIST to "KEY_CH_LIST", RemoteKey.INFO to "KEY_INFO",
        RemoteKey.INPUT to "KEY_SOURCE", RemoteKey.MENU to "KEY_MENU", RemoteKey.AUDIO to "KEY_MTS",
    ) + RemoteKey.entries.filter { it.digit != null }.associateWith { "KEY_${it.digit}" }

    /** Volume through UPnP RenderingControl (port 9197), which most Samsung TVs since 2016 answer. */
    override suspend fun readVolume(): TvVolume? = withContext(Dispatchers.IO) {
        fun ask(action: String, tag: String): String? = runCatching {
            val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1"><InstanceID>0</InstanceID><Channel>Master</Channel></u:$action></s:Body></s:Envelope>"""
            val req = Request.Builder().url("http://$host:9197/upnp/control/RenderingControl1")
                .header("SOAPACTION", "\"urn:schemas-upnp-org:service:RenderingControl:1#$action\"")
                .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType())).build()
            Net.plain.newCall(req).execute().use { r -> if (!r.isSuccessful) null else Regex("<$tag>([^<]*)</$tag>").find(r.body?.string().orEmpty())?.groupValues?.get(1) }
        }.getOrNull()
        val v = ask("GetVolume", "CurrentVolume")?.trim()?.toIntOrNull() ?: return@withContext null
        val m = ask("GetMute", "CurrentMute")?.trim()
        TvVolume(v, 100, m == "1" || m.equals("true", true))
    }

    /** Waiting for the TV's answer to "which apps are installed". */
    @Volatile private var appsReply: CompletableDeferred<JSONArray>? = null

    override suspend fun connect() {
        close()
        val name = Base64.encodeToString("Mini Remote".toByteArray(), Base64.NO_WRAP)
        val url = "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name" +
            (token?.let { "&token=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        val ready = CompletableDeferred<Unit>()
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (j.optString("event") == "ms.channel.connect") {
                    j.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { token = it; onToken(it) }
                    ready.complete(Unit)
                } else if (j.optString("event") == "ms.channel.timeOut") {
                    ready.completeExceptionally(UserFacing("The TV's Allow prompt timed out. Connect again and choose Allow."))
                } else if (j.optString("event") == "ms.channel.unauthorized") {
                    ready.completeExceptionally(UserFacing("The TV denied access. Connect again and choose Allow on the TV."))
                } else if (j.optString("event") == "ed.installedApp.get") {
                    appsReply?.complete(j.optJSONObject("data")?.optJSONArray("data") ?: JSONArray())
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready.completeExceptionally(t)
            }
        })
        // The TV may show an "Allow" prompt the first time, so wait longer.
        try { withTimeout(25_000) { ready.await() } } catch (e: Exception) { close(); throw e }
        if (pin == null) tls.seen?.let(onPin)
    }

    override suspend fun send(key: RemoteKey) {
        val s = ws ?: error("Not connected")
        val msg = JSONObject().put("method", "ms.remote.control").put(
            "params", JSONObject().put("Cmd", "Click").put("DataOfCmd", keys[key] ?: missing(key, "Samsung"))
                .put("Option", "false").put("TypeOfRemote", "SendRemoteKey")
        )
        if (!s.send(msg.toString())) error("Connection lost")
    }

    private fun emit(event: String, data: JSONObject? = null) {
        val s = ws ?: error("Not connected")
        val params = JSONObject().put("event", event).put("to", "host").also { p -> data?.let { p.put("data", it) } }
        if (!s.send(JSONObject().put("method", "ms.channel.emit").put("params", params).toString())) error("Connection lost")
    }

    override suspend fun listApps(): List<TvApp> {
        val d = CompletableDeferred<JSONArray>()
        appsReply = d
        val arr = try {
            emit("ed.installedApp.get")
            withTimeoutOrNull(6_000) { d.await() } ?: throw UserFacing("The TV didn't send its app list")
        } finally { if (appsReply === d) appsReply = null }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("appId").ifEmpty { return@mapNotNull null }
            TvApp(id, o.optString("name").ifEmpty { id })
        }
    }

    /** The REST launcher works on most models; the channel event covers the rest. */
    override suspend fun launchApp(id: String) {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val r = Request.Builder().url("http://$host:8001/api/v2/applications/${URLEncoder.encode(id, "UTF-8")}")
                    .post(ByteArray(0).toRequestBody()).build()
                Net.plain.newCall(r).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        if (!ok) emit("ed.apps.launch", JSONObject().put("appId", id).put("action_type", "DEEP_LINK"))
    }

    override suspend fun search(query: String) {
        throw Unsupported("Search isn't supported on Samsung TVs yet")
    }

    // KEY_VOICE starts Bixby listening rather than a search box, so don't press it here.
    override suspend fun openSearch() {}

    override fun close() { ws?.close(1000, null); ws = null }
}

/* --------------------------------------------------------------- LG (webOS) */

class LgAdapter(
    private val host: String,
    private var token: String?,
    private val pin: String?,
    private val onToken: (String) -> Unit,
    private val onPin: (String) -> Unit,
) : RemoteAdapter {
    private val tls = TlsPin(pin)
    private val secure = Net.pinnedTls(tls)
    private var ws: WebSocket? = null
    private var pointer: WebSocket? = null
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var muted = false
    /** webOS has no play/pause toggle, so we alternate between play and pause. */
    private var playing = false

    private val manifest = JSONObject().put("manifestVersion", 1).put("appVersion", "1.1").put(
        "permissions", org.json.JSONArray(
            listOf(
                "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TEXT",
                "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_MEDIA_PLAYBACK",
                // Lets the Channels tab list installed apps (the TV asks once to allow it).
                "READ_INSTALLED_APPS",
            )
        )
    )

    override var onVolume: ((TvVolume) -> Unit)? = null

    private fun parseVolume(p: JSONObject?): TvVolume? {
        if (p == null) return null
        val st = p.optJSONObject("volumeStatus")
        val level = st?.optInt("volume", -1) ?: p.optInt("volume", -1)
        if (level < 0) return null
        val max = (st?.optInt("maxVolume", 100) ?: 100).takeIf { it > 0 } ?: 100
        val mute = st?.optBoolean("muteStatus", false) ?: p.optBoolean("muted", p.optBoolean("mute", false))
        return TvVolume(level, max, mute)
    }

    override suspend fun readVolume(): TvVolume? = parseVolume(request("ssap://audio/getVolume").optJSONObject("payload"))

    private fun listener(registered: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val j = runCatching { JSONObject(text) }.getOrNull() ?: return
            val type = j.optString("type")
            if (j.optString("id") == "vol") { parseVolume(j.optJSONObject("payload"))?.let { v -> onVolume?.invoke(v) }; return }
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
        // Keep the volume in step: the TV sends it again whenever it changes.
        s.send(JSONObject().put("type", "subscribe").put("id", "vol").put("uri", "ssap://audio/getVolume").toString())
    }

    override suspend fun connect() {
        close()
        if (pin != null) {
            // Paired over TLS before: never drop back to the unencrypted port.
            tryConnect("wss://$host:3001", secure)
            return
        }
        // Encrypted port first, so the pairing key never crosses Wi-Fi in the clear when the TV supports it.
        try {
            tryConnect("wss://$host:3001", secure)
            tls.seen?.let(onPin)
        } catch (e: Exception) {
            if (e.isCertChanged() || e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            // Older webOS versions only have the plain port.
            tryConnect("ws://$host:3000", Net.plain)
        }
    }

    private suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject {
        val s = ws ?: error("Not connected")
        val id = "r" + ids.getAndIncrement()
        val d = CompletableDeferred<JSONObject>()
        pending[id] = d
        s.send(JSONObject().put("type", "request").put("id", id).put("uri", uri).put("payload", payload).toString())
        // withTimeoutOrNull, not withTimeout: a timeout must fail this request, not cancel the caller.
        val j = try { withTimeoutOrNull(6_000) { d.await() } } finally { pending.remove(id) }
            ?: throw IllegalStateException("The TV didn't answer")
        if (j.optString("type") == "error") throw UserFacing("The TV refused: ${j.optString("error").ifEmpty { "unknown error" }}")
        return j
    }

    private suspend fun ensurePointer(): WebSocket {
        pointer?.let { return it }
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            .optJSONObject("payload")?.optString("socketPath").orEmpty()
        if (path.isEmpty()) error("TV refused the input channel")
        val up = CompletableDeferred<Unit>()
        val client = if (path.startsWith("wss")) secure else Net.plain
        val s = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { up.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                up.completeExceptionally(t); pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { pointer = null }
        })
        withTimeoutOrNull(5_000) { up.await() } ?: run { s.cancel(); error("TV didn't open the input channel") }
        pointer = s
        return s
    }

    private suspend fun button(name: String) {
        ensurePointer().send("type:button\nname:$name\n\n")
    }

    override suspend fun send(key: RemoteKey) {
        key.digit?.let { button(it.toString()); return }
        when (key) {
            RemoteKey.UP -> button("UP")
            RemoteKey.DOWN -> button("DOWN")
            RemoteKey.LEFT -> button("LEFT")
            RemoteKey.RIGHT -> button("RIGHT")
            RemoteKey.OK -> button("ENTER")
            RemoteKey.BACK -> button("BACK")
            RemoteKey.HOME -> button("HOME")
            RemoteKey.PLAY_PAUSE -> { playing = !playing; request(if (playing) "ssap://media.controls/play" else "ssap://media.controls/pause") }
            RemoteKey.POWER -> request("ssap://system/turnOff")
            RemoteKey.VOL_UP -> request("ssap://audio/volumeUp")
            RemoteKey.VOL_DOWN -> request("ssap://audio/volumeDown")
            RemoteKey.MUTE -> { muted = !muted; request("ssap://audio/setMute", JSONObject().put("mute", muted)) }
            RemoteKey.REWIND -> request("ssap://media.controls/rewind")
            RemoteKey.FAST_FORWARD -> request("ssap://media.controls/fastForward")
            RemoteKey.CH_UP -> button("CHANNELUP")
            RemoteKey.CH_DOWN -> button("CHANNELDOWN")
            RemoteKey.GUIDE -> button("GUIDE")
            RemoteKey.CH_LIST -> button("LIST")
            RemoteKey.INFO -> button("INFO")
            RemoteKey.INPUT -> button("INPUT_HUB")
            RemoteKey.MENU -> button("MENU")
            RemoteKey.AUDIO -> button("SAP")
            RemoteKey.VOICE -> throw Unsupported("Voice isn't available on LG over WiFi")
            else -> missing(key, "LG")
        }
    }

    override suspend fun listApps(): List<TvApp> {
        val arr = request("ssap://com.webos.applicationManager/listLaunchPoints")
            .optJSONObject("payload")?.optJSONArray("launchPoints") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            val icon = o.optString("largeIcon").ifEmpty { o.optString("icon") }.takeIf { it.startsWith("http") }
            TvApp(id, o.optString("title").ifEmpty { id }, icon)
        }
    }

    override suspend fun launchApp(id: String) { request("ssap://system.launcher/launch", JSONObject().put("id", id)) }

    private val liveIds = Regex("livetv|hdmi|externalinput|tvinput|av[0-9]|component", RegexOption.IGNORE_CASE)

    override suspend fun nowOn(): NowOn {
        val id = request("ssap://com.webos.applicationManager/getForegroundAppInfo").optJSONObject("payload")?.optString("appId").orEmpty()
        val name = when {
            id.isEmpty() || id.contains("home", true) || id.contains("launcher", true) -> "Home"
            id.contains("livetv", true) -> "Live TV"
            id.contains("hdmi", true) -> "HDMI " + id.filter { it.isDigit() }
            else -> id.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
        return NowOn(name, liveIds.containsMatchIn(id))
    }

    override suspend fun search(query: String) {
        throw Unsupported("Open a search box on the LG, then use the keyboard to type")
    }

    override suspend fun openSearch() {}

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
