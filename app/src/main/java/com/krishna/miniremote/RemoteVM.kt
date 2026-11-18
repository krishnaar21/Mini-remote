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
import com.krishna.miniremote.net.TvApp
import com.krishna.miniremote.net.NowOn
import com.krishna.miniremote.net.Unsupported
import com.krishna.miniremote.net.UserFacing
import com.krishna.miniremote.net.adapterFor
import com.krishna.miniremote.net.isCertChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

class RemoteVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val feel = FeelEngine(app)

    var settings by mutableStateOf(store.loadSettings()); private set
    var devices by mutableStateOf(store.loadDevices()); private set
    var activeId by mutableStateOf(store.activeId()); private set
    var link by mutableStateOf(Link.IDLE); private set
    var toast by mutableStateOf<String?>(null); private set
    /** The key behind the current toast, so the well can show its icon above the name. */
    var toastKey by mutableStateOf<RemoteKey?>(null); private set
    var scanning by mutableStateOf(false); private set
    /** TVs found by the last scan that aren't saved yet. */
    var found by mutableStateOf<List<Device>>(emptyList()); private set
    /** Why the last connection attempt failed, worded for the user. */
    var lastError by mutableStateOf<String?>(null); private set
    /** Bumps every time a command goes out; the transmit light in the TV pill flashes on it. */
    private var txN by mutableIntStateOf(0)
    /** When this app last sent something to the TV (to tell its text boxes from the physical remote's). */
    private var lastTxAt = 0L
    var txCount: Int
        get() = txN
        private set(v) { txN = v; lastTxAt = System.currentTimeMillis() }
    /** Mute is a toggle on every TV we support, so we track it locally to colour the button. */
    var muted by mutableStateOf(false); private set
    /** Play / pause is a toggle too: tracked locally so the key shows one icon at a time. */
    var playing by mutableStateOf(false); private set
    /** The TV's own volume, when it tells us (Android TV, LG, most Samsung). Null = unknown. */
    var tvVolume by mutableStateOf<com.krishna.miniremote.net.TvVolume?>(null); private set
    private var volJob: Job? = null
    /** Non-null while an Android / Google TV is showing a pairing code and we're waiting for it. */
    var pairPrompt by mutableStateOf<PairPrompt?>(null); private set
    private var codeAnswer: CompletableDeferred<String?>? = null

    val active: Device? get() = devices.firstOrNull { it.id == activeId }
    val localSeen: Boolean get() = store.localSeen()

    private var adapter: RemoteAdapter? = null
    private var connectJob: Job? = null
    private var toastJob: Job? = null
    private val queue = Channel<RemoteKey>(64, BufferOverflow.DROP_OLDEST)
    /** Live typing, in order: text to type, or "\b" for one backspace. */
    private val textOps = Channel<String>(Channel.UNLIMITED)
    private var typeWarned = false
    private var autoJob: Job? = null

    /** What the TV shows (Roku and LG can tell), and whether the dial is in Watch mode. */
    var nowOn by mutableStateOf<NowOn?>(null); private set
    /** The user's own choice; cleared whenever the TV moves between live TV and apps. */
    private var watchChoice by mutableStateOf<Boolean?>(null)
    val watching: Boolean get() = watchChoice ?: (nowOn?.live == true)
    private var nowJob: Job? = null
    private var nowFor: String? = null

    fun setWatching(on: Boolean) { watchChoice = on; feel.fire(Feel.DETENT); say(if (on) "Watch: dial = volume & channels" else "Navigate: dial moves around") }

    /** Polls what's on screen every few seconds while Home is showing ([on]). Stops if the TV can't tell. */
    fun followNowOn(on: Boolean) {
        nowJob?.cancel(); nowJob = null
        if (!on) return
        nowJob = viewModelScope.launch {
            while (true) {
                val a = adapter
                if (a != null && link == Link.READY) {
                    try {
                        val n = a.nowOn()
                        if (n.live != nowOn?.live) watchChoice = null
                        nowOn = n
                    } catch (e: Unsupported) { nowOn = null; return@launch
                    } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e
                    } catch (e: Exception) { /* try again next round */ }
                }
                delay(3500)
            }
        }
    }

    /* Channels tab state. Declared before init, which may connect (and reset tvApps). */
    var shortcuts by mutableStateOf(store.loadShortcuts()); private set
    /** Apps the connected TV reported, and which TV they belong to. */
    var tvApps by mutableStateOf<List<TvApp>>(emptyList()); private set
    var appsLoading by mutableStateOf(false); private set
    private var appsFor: String? = null

    /* ------------------------------------------------------------ updates */

    sealed interface Update {
        data object Idle : Update
        data object Checking : Update
        data object UpToDate : Update
        data class Downloading(val info: Updater.Info, val progress: Float) : Update
        data class Ready(val info: Updater.Info, val apk: java.io.File) : Update
        data class Failed(val message: String) : Update
        /** Newer build, but signed with a different key: get it from the release page instead. */
        data class Manual(val info: Updater.Info) : Update
    }
    var update by mutableStateOf<Update>(Update.Idle); private set
    private var updateJob: Job? = null

    /** Looks for a newer build and downloads it. [manual] = the user asked (so "up to date" is shown). */
    private var lastCheck = 0L
    /** Set when the "update ready" notification was tapped before the download finished. */
    private var installWhenReady = false

    /** Checks again when the app comes back, at most every 30 minutes (auto updates only). */
    fun maybeCheckForUpdate() {
        val u = update
        if (!settings.autoUpdate || u is Update.Ready || u is Update.Downloading || u is Update.Manual) return
        if (System.currentTimeMillis() - lastCheck > 30 * 60_000L) checkForUpdate()
    }

    /** The update notification was tapped: install now, or as soon as the download is done. */
    fun installFromNotification() { if (update is Update.Ready) installUpdate() else { installWhenReady = true; checkForUpdate() } }

    fun checkForUpdate(manual: Boolean = false) {
        if (updateJob?.isActive == true) return
        val ctx = getApplication<Application>()
        lastCheck = System.currentTimeMillis()
        updateJob = viewModelScope.launch {
            update = Update.Checking
            update = runCatching<Update> {
                val info = Updater.check(ctx) ?: return@runCatching Update.UpToDate
                update = Update.Downloading(info, 0f)
                var last = 0f
                val apk = Updater.download(ctx, info) { p ->
                    // Only repaint every 2% so the download doesn't flood the UI thread.
                    if (p - last >= 0.02f || p >= 1f) { last = p; update = Update.Downloading(info, p) }
                }
                if (!Updater.sameSigner(ctx, apk)) {
                    // Android would reject it over this app; point to the release page instead.
                    Updater.notify(ctx, "Mini Remote ${info.name} is available", "Tap to download it. It needs a reinstall this time.",
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Updater.RELEASE_PAGE)))
                    return@runCatching Update.Manual(info)
                }
                Updater.notify(ctx, "Update ${info.name} is ready", "Tap to install the new Mini Remote.",
                    android.content.Intent(ctx, MainActivity::class.java).putExtra(Updater.EXTRA_INSTALL, true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
                Update.Ready(info, apk)
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (manual) Update.Failed(e.message ?: "Couldn't check for updates") else Update.Idle
            }
            if (!manual && (update == Update.UpToDate || update is Update.Failed)) update = Update.Idle
            if (installWhenReady && update is Update.Ready) { installWhenReady = false; installUpdate() }
        }
    }

    /** Starts Android's installer, or first sends the user to allow installs from this app. */
    fun installUpdate() {
        val r = update as? Update.Ready ?: return
        val ctx = getApplication<Application>()
        runCatching {
            if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); say("Allow installs, then tap Install again") }
            else Updater.install(ctx, r.apk)
        }.onFailure { say("Couldn't open the installer") }
    }

    fun dismissUpdate() { if (update !is Update.Downloading) update = Update.Idle }

    /** Opens the GitHub release page (for builds that can't install over this one). */
    fun openReleasePage() {
        val ctx = getApplication<Application>()
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Updater.RELEASE_PAGE)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    init {
        if (settings.autoUpdate) checkForUpdate()
        applyFeel(settings)
        viewModelScope.launch {
            for (k in queue) {
                val a = adapter
                if (link != Link.READY || a == null) {
                    // No Wi-Fi link: a TV paired over Bluetooth still gets the key.
                    if (btReady && runCatching { bt?.send(k) == true }.getOrDefault(false)) txCount++
                    continue
                }
                try { a.send(k); txCount++ } catch (e: TimeoutCancellationException) {
                    feel.fire(Feel.ERROR); say("The TV didn't answer")
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (e is Unsupported) { feel.fire(Feel.ERROR); say(e.message ?: "Not supported") }
                    else active?.let { d ->
                        // The socket died under us: reconnect quietly, then send this key again.
                        adapter = null; redials = 0
                        connect(d, quiet = true)
                        val ok = withTimeoutOrNull(12_000) { while (link != Link.READY) delay(100); true }
                        val b = adapter
                        if (ok == true && b != null) runCatching { b.send(k); txCount++ }
                        else { feel.fire(Feel.ERROR); say("Lost connection") }
                    }
                }
            }
        }
        autoConnect()
        viewModelScope.launch {
            for (op in textOps) {
                val a = adapter
                if (link != Link.READY || a == null) continue
                try {
                    when {
                        op.startsWith("\u0001") -> a.setText(op.substring(1))
                        op == "\b" -> a.backspace()
                        op == "\u0002" -> a.submit()
                        op.startsWith("\u0003") -> a.clearBox(op.substring(1).toIntOrNull() ?: 0)
                        else -> a.typeText(op)
                    }
                    txCount++
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (!typeWarned) { typeWarned = true; say(if (e is UserFacing) e.message ?: "Couldn't type" else "Couldn't type") }
                }
            }
        }
    }

    /** Runs the search: Roku searches for [text]; other TVs press Enter in their box (after the typing). */
    fun submitSearch(text: String) {
        if (active == null) { say("Choose a TV first"); return }
        if (active?.brand == Brand.ROKU) { search(text); return }
        textOps.trySend("\u0002")
    }

    /** Clears the TV's box: all of it, not only what was typed from here. */
    fun clearTvBox(typed: Int) {
        if (active == null) return
        textOps.trySend("\u0003$typed")
    }

    /** Mirrors an edit of the search box on the TV: deletes what changed, types what's new. */
    fun liveType(old: String, new: String) {
        userAt = System.currentTimeMillis(); menuUsed()
        if (active == null) { say("Choose a TV first"); return }
        // Android TV takes the whole box at once (any characters, no key-by-key typing).
        if (adapter?.replacesText == true) { textOps.trySend("\u0001" + new); return }
        var common = 0
        while (common < old.length && common < new.length && old[common] == new[common]) common++
        repeat(old.length - common) { textOps.trySend("\b") }
        if (new.length > common) textOps.trySend(new.substring(common))
    }

    /** Whether a saved TV has its pairing done, so connecting needs nothing from the user. */
    private fun paired(d: Device) = when (d.brand) {
        Brand.ROKU -> true
        Brand.SAMSUNG -> d.token != null || d.pin != null
        Brand.LG -> d.token != null
        Brand.ANDROID_TV -> d.pin != null
    }

    /** Quick check that the TV answers on its remote port (no pairing, nothing sent). */
    private suspend fun reachable(d: Device): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!HostValidator.isPrivateIPv4(d.host)) return@withContext false
        val port = when (d.brand) { Brand.ROKU -> 8060; Brand.SAMSUNG -> 8002; Brand.LG -> if (d.pin != null) 3001 else 3000; Brand.ANDROID_TV -> 6466 }
        runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(d.host, port), 800); true } }.getOrDefault(false)
    }

    /**
     * Connects by itself to a paired TV that's on the same Wi-Fi: the last one used first, then
     * the others. Runs on launch and when the app comes back. Never starts a new pairing.
     */
    fun autoConnect() {
        if (link == Link.READY || link == Link.CONNECTING || autoJob?.isActive == true) return
        val list = (listOfNotNull(active) + devices.filter { it.id != activeId }).filter(::paired)
        if (list.isEmpty()) { active?.let { if (link == Link.IDLE) connect(it) }; return }
        autoJob = viewModelScope.launch {
            for (d in list) {
                if (reachable(d)) {
                    if (link == Link.READY || link == Link.CONNECTING) return@launch
                    if (d.id == activeId) connect(d) else select(d)
                    return@launch
                }
            }
            // None answered: try the chosen one anyway, so its status and error show.
            active?.let { if (link == Link.IDLE || link == Link.ERROR) connect(it) }
        }
    }

    fun press(key: RemoteKey, quiet: Boolean = false) {
        if (active == null) { feel.fire(Feel.ERROR); say("Choose a TV first"); return }
        if (link == Link.ERROR) active?.let { connect(it) }
        if (link != Link.READY) active?.bt?.let { a -> bt?.connect(a) }
        feel.fire(if (key == RemoteKey.POWER) Feel.HEAVY else Feel.PRESS)
        if (key == RemoteKey.MUTE) muted = !muted
        if (key == RemoteKey.PLAY_PAUSE) playing = !playing
        if (key == RemoteKey.VOL_UP) muted = false
        nudgeVolume(key)
        if (!quiet) say(if (key == RemoteKey.PLAY_PAUSE) (if (playing) "Play" else "Pause") else key.word, key)
        // Android TV players hold a rewind / fast-forward in a seek preview until it's confirmed
        // (they ignore Play / Pause meanwhile). Within 10 s of seeking, Play / Pause sends OK,
        // which confirms the new position and plays from it.
        userAt = System.currentTimeMillis()
        watchMenu(key)
        val now = System.currentTimeMillis()
        if (key == RemoteKey.REWIND || key == RemoteKey.FAST_FORWARD) seekAt = now
        if (key == RemoteKey.PLAY_PAUSE && active?.brand == Brand.ANDROID_TV && now - seekAt < 10_000) {
            seekAt = 0L; playing = true; queue.trySend(RemoteKey.OK); return
        }
        queue.trySend(key)
    }

    private var seekAt = 0L
    /** When the user last pressed, turned or typed anything here (not background traffic like volume checks). */
    @Volatile private var userAt = 0L

    /** Keys that open a menu or panel over what's on the TV (settings, quick settings, inputs…). */
    private val menuKeys = setOf(RemoteKey.SETTINGS, RemoteKey.INPUT, RemoteKey.MENU, RemoteKey.INFO, RemoteKey.GUIDE,
        RemoteKey.CH_LIST, RemoteKey.SUBTITLES, RemoteKey.AUDIO)
    /** Keys that leave a menu by themselves. */
    private val menuExits = setOf(RemoteKey.BACK, RemoteKey.HOME, RemoteKey.POWER)
    private val menuIdle = 10_000L
    /** A menu opened from here is up, and when it closes unless used again. */
    @Volatile private var menuUntil = 0L
    private var menuBehind: String? = null
    private var menuJob: Job? = null

    /**
     * Idle TV menus close themselves. Opening one (Settings, Input, Menu, Info, Guide, Channels,
     * Subtitles, Audio) starts a 10 s clock; anything you do here while it's up (a key, the dial
     * or trackpad, typing, voice) starts it again. When it runs out, Back is pressed once.
     * Back, Home or Power end it; so does the screen behind changing (an input or app was picked
     * and the menu went with it). One watcher checks four times a second, so it's on time.
     */
    private fun watchMenu(key: RemoteKey) {
        val now = System.currentTimeMillis()
        when {
            !settings.autoClose || key in menuExits -> { menuUntil = 0L; menuJob?.cancel(); menuJob = null }
            key in menuKeys -> {
                menuBehind = nowOn?.name
                menuUntil = now + menuIdle
                if (menuJob?.isActive != true) menuJob = viewModelScope.launch {
                    while (menuUntil != 0L) {
                        delay(250)
                        val behind = menuBehind
                        val shown = nowOn?.name
                        if (behind != null && shown != null && shown != behind) { menuUntil = 0L; break }
                        if (menuUntil != 0L && System.currentTimeMillis() >= menuUntil) {
                            menuUntil = 0L
                            if (link == Link.READY) { queue.trySend(RemoteKey.BACK); say("Closed the TV's menu (unused for 10 s)") }
                        }
                    }
                }
            }
            menuUntil != 0L -> menuUntil = now + menuIdle // used: keep it open
        }
    }

    /** Dial turns, trackpad moves and typing keep an open menu alive too. */
    private fun menuUsed() { if (menuUntil != 0L) menuUntil = System.currentTimeMillis() + menuIdle }

    fun scrubTick(key: RemoteKey) {
        userAt = System.currentTimeMillis(); menuUsed(); feel.fire(Feel.TICK); nudgeVolume(key); queue.trySend(key) }

    /** Moves the shown volume right away, then asks the TV for the real value a moment later. */
    private fun nudgeVolume(key: RemoteKey) {
        if (key != RemoteKey.VOL_UP && key != RemoteKey.VOL_DOWN && key != RemoteKey.MUTE) return
        tvVolume?.let { v ->
            tvVolume = when (key) {
                RemoteKey.VOL_UP -> v.copy(level = (v.level + 1).coerceAtMost(v.max), muted = false)
                RemoteKey.VOL_DOWN -> v.copy(level = (v.level - 1).coerceAtLeast(0))
                else -> v.copy(muted = !v.muted)
            }
        }
        refreshVolume(700)
    }

    private fun refreshVolume(after: Long) {
        volJob?.cancel()
        volJob = viewModelScope.launch {
            delay(after)
            val a = adapter ?: return@launch
            val v = try { a.readVolume() } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return@launch
            tvVolume = v; muted = v.muted
        }
    }

    fun say(t: String, key: RemoteKey? = null) {
        toast = t
        toastKey = key
        toastJob?.cancel()
        // Longer messages stay up longer so they can be read.
        toastJob = viewModelScope.launch { delay(1100L + 45L * t.length.coerceAtMost(80)); toast = null; toastKey = null }
    }

    private var redials = 0

    /** The link broke by itself: reconnect quietly (a few tries, waiting longer each time). */
    private fun dropped(d: Device, a: com.krishna.miniremote.net.RemoteAdapter) {
        viewModelScope.launch {
            if (adapter !== a || activeId != d.id) return@launch
            adapter = null
            if (redials >= 4) { link = Link.ERROR; say("Lost connection to ${d.name}"); return@launch }
            delay(listOf(300L, 1500L, 4000L, 8000L)[redials])
            redials++
            if (activeId == d.id && adapter == null) connect(d, quiet = true)
        }
    }

    fun connect(d: Device) = connect(d, quiet = false)

    private fun connect(d0: Device, quiet: Boolean) {
        // Always the saved copy: a reconnect must carry the pin and token saved since (or the TV asks to pair again).
        val d = devices.firstOrNull { it.id == d0.id } ?: d0
        if (d.id != appsFor) { tvApps = emptyList(); appsFor = null }
        if (d.id != nowFor) { nowOn = null; watchChoice = null; nowFor = d.id }
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
                tvVolume = null
                a.onVolume = { v -> viewModelScope.launch { tvVolume = v; muted = v.muted } }
                a.onDropped = { dropped(d, a) }
                // A text box got focus on the TV. If the phone is what's driving it (something sent
                // from here in the last 20 s), type from the phone; if the physical remote is,
                // leave the TV's own keyboard alone.
                a.onKeyboard = { viewModelScope.launch {
                    if (settings.autoKeyboard && System.currentTimeMillis() - lastTxAt < 20_000) keyboardAsk++
                } }
                a.connect()
                adapter = a
                refreshVolume(0)
                link = Link.READY
                fetchName(d) // a saved TV still called "Google TV" / "LG TV" gets its own name
                redials = 0
                // Remember the TV's network address so it can be woken later.
                if (d.mac == null) viewModelScope.launch { runCatching { a.macAddress() }.getOrNull()?.let { m -> updateDevice(d.id) { it.copy(mac = m) } } }
                if (!quiet) feel.fire(Feel.SUCCESS)
            } catch (e: CancellationException) {
                // A timeout inside connect is a failure to show, not this job being cancelled.
                if (e !is TimeoutCancellationException) throw e
                link = Link.ERROR
                val msg = "${d.name} didn't answer in time"
                lastError = msg
                say(msg)
                feel.fire(Feel.ERROR)
            } catch (e: Exception) {
                if (quiet && redials < 4 && !e.isCertChanged() && e !is UserFacing) {
                    delay(listOf(300L, 1500L, 4000L, 8000L)[redials]); redials++
                    if (activeId == d.id) { connect(d, quiet = true); return@launch }
                }
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
        val d = Device("${brand.name.lowercase()}-$h", brand.label, h, brand)
        addAndSelect(d)
        fetchName(d)
        return null to d
    }

    /** Replaces a placeholder name ("LG TV", "Google TV"…) with the name set on the TV, when it says. */
    private fun fetchName(d: Device) {
        if (!Discovery.generic(d.name)) return
        viewModelScope.launch {
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Discovery.nameOf(d.host, d.brand) } ?: return@launch
            val cur = devices.firstOrNull { it.id == d.id } ?: return@launch
            if (Discovery.generic(cur.name)) updateDevice(d.id) { it.copy(name = n) }
        }
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
                // Each TV shows up as soon as it's found, not when the whole scan ends.
                val all = Discovery.scan { d ->
                    if (d.host !in hosts) viewModelScope.launch { if (found.none { it.host == d.host }) found = found + d }
                }
                found = all.filter { it.host !in hosts }
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

    /* --------------------------------------------------- channels & apps */


    /** Runs [block] on the connected TV, reporting problems as a toast. */
    private fun act(fail: String, block: suspend (RemoteAdapter) -> Unit) {
        val d = active ?: run { feel.fire(Feel.ERROR); say("Choose a TV first"); return }
        val a = adapter
        if (a == null || link != Link.READY) {
            feel.fire(Feel.ERROR)
            if (link == Link.CONNECTING) say("Still connecting to ${d.name}") else { say("Reconnecting to ${d.name}"); connect(d) }
            return
        }
        viewModelScope.launch {
            try { block(a); txCount++ } catch (e: TimeoutCancellationException) {
                feel.fire(Feel.ERROR); say("The TV didn't answer")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                feel.fire(Feel.ERROR); say(if (e is UserFacing) e.message ?: fail else fail)
            }
        }
    }

    /** Loads the TV's app list once per TV ([force] reloads it). Silent when the TV can't list apps. */
    fun refreshApps(force: Boolean = false) {
        // A manual refresh always answers, even when there's nothing to load.
        val a = adapter
        val id = activeId
        if (a == null || id == null || link != Link.READY) {
            if (force) say("Connect a TV first")
            return
        }
        if (appsLoading) { if (force) say("Loading…"); return }
        if (!force && appsFor == id) return
        appsLoading = true
        val before = tvApps
        viewModelScope.launch {
            try {
                tvApps = a.listApps().sortedBy { it.name.lowercase() }
                appsFor = id
                if (force) say(if (tvApps == before) "Up to date" else "${tvApps.size} apps")
            } catch (e: TimeoutCancellationException) {
                if (force) say("TV didn't answer")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                appsFor = id
                if (force) say(if (e is Unsupported) "Up to date" else "Couldn't load apps")
            } finally { appsLoading = false }
        }
    }

    fun launchApp(name: String, id: String) {
        feel.fire(Feel.PRESS); say(name)
        act("Couldn't open $name") { it.launchApp(id) }
    }

    /** A popular app: this TV's id from the catalog, or the TV's own app with the same name. */
    fun launchCatalog(c: CatalogApp) {
        val brand = active?.brand ?: run { feel.fire(Feel.ERROR); say("Choose a TV first"); return }
        val id = (if (brand == Brand.ANDROID_TV) c.link else null) ?: c.ids[brand]
            ?: tvApps.firstOrNull { it.name.equals(c.name, true) || it.name.contains(c.name, true) }?.id
        if (id == null) { feel.fire(Feel.ERROR); say("${c.name} isn't set up for this TV. Add it with its app ID."); return }
        launchApp(c.name, id)
    }

    /** The app's page in the TV's store (Roku, Android / Google TV): install or remove it there. */
    fun openStore(name: String, id: String) {
        feel.fire(Feel.PRESS); say("Opening $name in the TV's store")
        act("Couldn't open the store") { it.openStore(id) }
    }

    /** Whether the TV has this app: true / false when the TV lists its apps, null when it can't tell. */
    fun installed(c: CatalogApp): Boolean? {
        val brand = active?.brand ?: return null
        if (tvApps.isEmpty()) return null
        val id = c.ids[brand]
        return tvApps.any { it.id == id || it.name.equals(c.name, true) }
    }

    fun tune(name: String, number: String) {
        feel.fire(Feel.PRESS); say("$number · $name")
        act("Couldn't change the channel") { it.tuneChannel(number) }
    }

    fun launch(s: Shortcut) {
        when {
            s.kind == ShortcutKind.CHANNEL -> tune(s.name, s.value)
            s.brand == null -> Catalog.byKey(s.value)?.let { launchCatalog(it) } ?: say("${s.name} isn't available any more")
            active?.brand != s.brand -> { feel.fire(Feel.ERROR); say("${s.name} was saved from a ${s.brand?.label ?: "different"} TV") }
            else -> launchApp(s.name, s.value)
        }
    }

    /** Opens the TV's search screen when the search panel opens. Quiet: no message if the TV can't. */
    fun openTvSearch() {
        val a = adapter ?: return
        if (link != Link.READY) return
        // Google TV's Search key starts the voice assistant, not a text box: leave the screen as
        // it is and type into whatever box is selected (or search an app from the panel).
        if (a is com.krishna.miniremote.net.AndroidTvAdapter) return
        viewModelScope.launch { runCatching { a.openSearch(); txCount++ } }
    }

    /**
     * Sends what was typed or spoken in the search panel: on Roku straight to its search
     * results, elsewhere typed into the TV's (now open) search box.
     */
    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (active?.brand == Brand.ROKU) search(t) else typeText(t)
    }

    /** Android TV: search an app straight from the phone (no TV keyboard): YouTube or the Play Store. */
    val searchesApps: Boolean get() = active?.brand == Brand.ANDROID_TV

    fun searchIn(where: String, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val e = java.net.URLEncoder.encode(q, "UTF-8")
        val link = when (where) {
            "youtube" -> "https://www.youtube.com/results?search_query=$e"
            else -> "market://search?q=$e&c=apps"
        }
        say("Searching ${if (where == "youtube") "YouTube" else "Play Store"} for $q")
        act("Couldn't search") { it.launchApp(link) }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        say("Searching for $q")
        act("Couldn't search") { it.search(q) }
    }

    private fun saveShortcuts(l: List<Shortcut>) { shortcuts = l; store.saveShortcuts(l) }

    fun addShortcut(kind: ShortcutKind, name: String, value: String, pin0: Boolean) {
        val pin = pin0 && (shortcuts.count { it.pinned } < maxPinned)
        if (pin0 && !pin) say("Quick access is full ($maxPinned); added without pinning")
        val s = Shortcut(
            id = "s${System.currentTimeMillis()}", kind = kind, name = name.trim().take(30), value = value.trim(),
            brand = if (kind == ShortcutKind.APP) active?.brand else null, pinned = pin, added = true,
        )
        saveShortcuts(shortcuts + s)
        feel.fire(Feel.SUCCESS); say("Added ${s.name}")
    }

    fun pinnedCatalog(c: CatalogApp): Shortcut? = shortcuts.firstOrNull { it.kind == ShortcutKind.APP && it.brand == null && it.value == c.key }
    fun pinnedTvApp(app: TvApp): Shortcut? = shortcuts.firstOrNull { it.kind == ShortcutKind.APP && it.brand == active?.brand && it.value == app.id }

    /** Quick access holds at most this many. */
    val maxPinned = 15
    private fun roomToPin(): Boolean {
        if (shortcuts.count { it.pinned } < maxPinned) return true
        feel.fire(Feel.ERROR); say("Quick access is full ($maxPinned). Remove one first.")
        return false
    }

    fun pinCatalog(c: CatalogApp) {
        if (pinnedCatalog(c) == null && !roomToPin()) return
        if (pinnedCatalog(c) == null) saveShortcuts(shortcuts + Shortcut("c${System.currentTimeMillis()}", ShortcutKind.APP, c.name, c.key, pinned = true))
        say("${c.name} added to quick access")
    }

    fun pinTvApp(app: TvApp) {
        val brand = active?.brand ?: return
        if (pinnedTvApp(app) == null && !roomToPin()) return
        if (pinnedTvApp(app) == null) {
            saveShortcuts(shortcuts + Shortcut("t${System.currentTimeMillis()}", ShortcutKind.APP, app.name, app.id, brand, app.icon, pinned = true))
        }
        say("${app.name} added to quick access")
    }

    /** Unpinning something that was only pinned (not added) forgets it. */
    fun setPinned(s: Shortcut, pinned: Boolean) {
        if (pinned && !s.pinned && !roomToPin()) return
        saveShortcuts(if (!pinned && !s.added) shortcuts.filter { it.id != s.id } else shortcuts.map { if (it.id == s.id) it.copy(pinned = pinned) else it })
        say(if (pinned) "${s.name} added to quick access" else "${s.name} removed from quick access")
    }

    fun removeShortcut(s: Shortcut) { saveShortcuts(shortcuts.filter { it.id != s.id }); say("Removed ${s.name}") }

    /** Moves a quick-access item one place left (-1) or right (+1). */
    fun movePinned(s: Shortcut, by: Int) {
        val pinned = shortcuts.filter { it.pinned }
        val i = pinned.indexOfFirst { it.id == s.id }
        val j = i + by
        if (i < 0 || j !in pinned.indices) return
        val order = pinned.toMutableList().apply { add(j, removeAt(i)) }
        saveShortcuts(order + shortcuts.filter { !it.pinned })
    }

    /** Forget every TV: their addresses, pairing tokens and pins, and this phone's Android TV key. */
    fun forgetAllTvs() {
        disconnect()
        devices = emptyList(); store.saveDevices(devices)
        activeId = null; store.saveActive(null)
        tvApps = emptyList(); appsFor = null; nowOn = null
        com.krishna.miniremote.net.ClientCert.reset()
        say("All TVs forgotten")
    }

    /** Erase all app data: TVs, keys, quick access, channels and settings. */
    fun eraseAll() {
        forgetAllTvs()
        store.clearAll()
        Vault.destroy()
        shortcuts = emptyList()
        settings = Settings(); applyFeel(settings)
        runCatching { java.io.File(getApplication<Application>().cacheDir, "updates").deleteRecursively() }
        Logos.clear(getApplication())
        say("All data erased")
    }

    override fun onCleared() { adapter?.close(); bt?.stop() }

    /* ---------------------------------------------------- wake, voice, more */

    /** Bumps when the TV asks for a keyboard (a text box got focus): the Search sheet opens. */
    var keyboardAsk by mutableIntStateOf(0); private set

    /** Turns the TV on: Wake-on-LAN with its saved network address (and Roku's PowerOn), then connects. */
    fun wake() {
        val d = active ?: run { say("Choose a TV first"); return }
        feel.fire(Feel.HEAVY)
        viewModelScope.launch {
            val a = adapter
            if (a != null && link == Link.READY && a.powerOn()) { say("Turning on ${d.name}"); return@launch }
            val mac = d.mac
            if (mac == null) { say("${d.name} can't be turned on from here yet. Connect once while it's on."); return@launch }
            if (!com.krishna.miniremote.net.Wol.wake(mac, d.host)) { say("Couldn't reach the network"); return@launch }
            say("Waking ${d.name}…")
            // TVs take a few seconds to join the network.
            repeat(6) {
                delay(2500)
                if (link == Link.READY) return@launch
                if (link != Link.CONNECTING) connect(d)
            }
        }
    }

    /** Hold OK on an Android TV: the phone's mic goes to the TV's Assistant while held. */
    private var voiceJob: Job? = null
    @Volatile private var voiceOn = false

    /** Bumps when the microphone permission is needed: the screen asks for it, then voice starts. */
    var micAsk by mutableIntStateOf(0); private set
    /** Bumps when the TV doesn't take the phone's voice: Search opens in Voice mode instead (speech typed on the TV). */
    var voiceTyping by mutableIntStateOf(0); private set
    /** True while the phone's mic is going to the TV. */
    var listening by mutableStateOf(false); private set

    fun voiceStart(): Boolean {
        if (active == null || link != Link.READY) { say("Choose a TV first"); return false }
        val a = adapter as? com.krishna.miniremote.net.AndroidTvAdapter
        if (a == null) { press(RemoteKey.VOICE); return false }
        val ctx = getApplication<Application>()
        val mic = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!mic) { micAsk++; return false }
        if (voiceOn) return true
        voiceOn = true
        listening = true
        voiceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { streamVoice(a) }
        return true
    }

    /**
     * Talk to the TV's Assistant, as Google's own remote does: Search key → the TV opens a voice
     * session (RemoteVoiceBegin with its id) → we answer it → the phone's mic goes to the TV as
     * 16-bit mono PCM at 8 kHz in 20 KB messages → the TV hears the end of speech (it sends a
     * voice message back) or you stop → RemoteVoiceEnd. AudioRecord works the same on every
     * Android version the app supports (8.0+).
     */
    private suspend fun streamVoice(a: com.krishna.miniremote.net.AndroidTvAdapter) {
        val id = try { a.voiceBegin() } catch (e: Exception) { null }
        if (id == null) {
            // The TV didn't open a voice session for the phone: recognise speech on the phone and type it.
            voiceOn = false
            withContext(kotlinx.coroutines.Dispatchers.Main) { listening = false; voiceTyping++ }
            return
        }
        a.onVoiceDone = { voiceOn = false }
        withContext(kotlinx.coroutines.Dispatchers.Main) { say("Listening…", RemoteKey.VOICE) }
        val rate = 8000
        val fmt = android.media.AudioFormat.ENCODING_PCM_16BIT
        val ch = android.media.AudioFormat.CHANNEL_IN_MONO
        val min = android.media.AudioRecord.getMinBufferSize(rate, ch, fmt).coerceAtLeast(4096)
        @Suppress("MissingPermission")
        val rec = runCatching {
            // VOICE_RECOGNITION: tuned for speech (no noise suppression that clips words); MIC as a fallback.
            android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, ch, fmt, min * 4)
                .takeIf { it.state == android.media.AudioRecord.STATE_INITIALIZED }
                ?: android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate, ch, fmt, min * 4)
        }.getOrNull()
        val pending = java.io.ByteArrayOutputStream()
        try {
            if (rec == null || rec.state != android.media.AudioRecord.STATE_INITIALIZED) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { say("Couldn't use the microphone") }
                return
            }
            rec.startRecording()
            val buf = ByteArray(1600) // 100 ms
            val started = System.currentTimeMillis()
            var heard = false
            var quietSince = 0L
            while (voiceOn && System.currentTimeMillis() - started < 12_000) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                pending.write(buf, 0, n)
                // TVs only recognise 20 KB messages (about 1.3 s of speech each).
                if (pending.size() >= com.krishna.miniremote.net.VOICE_CHUNK) {
                    val all = pending.toByteArray()
                    a.voiceChunk(id, all.copyOf(com.krishna.miniremote.net.VOICE_CHUNK))
                    pending.reset(); pending.write(all, com.krishna.miniremote.net.VOICE_CHUNK, all.size - com.krishna.miniremote.net.VOICE_CHUNK)
                }
                // A backstop if the TV doesn't end it: 2.5 s of quiet after speaking.
                var sum = 0.0
                var i = 0
                while (i + 1 < n) { val v = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toDouble(); sum += v * v; i += 2 }
                val rms = kotlin.math.sqrt(sum / (n / 2).coerceAtLeast(1))
                val now = System.currentTimeMillis()
                if (rms > 700) { heard = true; quietSince = 0L } else if (heard) { if (quietSince == 0L) quietSince = now else if (now - quietSince > 2500) break }
            }
            // The rest of the speech (padded to the 8 KB minimum by voiceChunk).
            if (pending.size() > 0) a.voiceChunk(id, pending.toByteArray())
        } catch (e: Exception) {
        } finally {
            runCatching { rec?.stop() }; rec?.release()
            a.onVoiceDone = null
            a.voiceEnd(id)
            voiceOn = false
            withContext(kotlinx.coroutines.Dispatchers.Main) { listening = false }
        }
    }

    fun voiceStop() { voiceOn = false }

    /** Inputs of the TV, for the More sheet. */
    var inputs by mutableStateOf<List<com.krishna.miniremote.net.TvInput>>(emptyList()); private set

    fun loadInputs() {
        val a = adapter ?: return
        if (link != Link.READY) return
        viewModelScope.launch { inputs = runCatching { a.listInputs() }.getOrDefault(emptyList()) }
    }

    fun switchInput(i: com.krishna.miniremote.net.TvInput) {
        val a = adapter ?: run { say("Choose a TV first"); return }
        feel.fire(Feel.PRESS); say(i.name)
        viewModelScope.launch {
            try { a.switchInput(i.id); txCount++ } catch (e: CancellationException) { throw e } catch (e: Exception) {
                say((e as? UserFacing)?.message ?: "Couldn't switch input")
            }
        }
    }

    /** Sleep timer: turns the TV off after the chosen minutes (while the app is running). */
    var sleepAt by mutableStateOf<Long?>(null); private set
    private var sleepJob: Job? = null

    fun setSleep(minutes: Int?) {
        sleepJob?.cancel(); sleepJob = null
        if (minutes == null) { sleepAt = null; say("Sleep timer off"); return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        sleepAt = at
        say("TV turns off in $minutes min")
        sleepJob = viewModelScope.launch {
            while (System.currentTimeMillis() < at) delay(1000)
            sleepAt = null
            if (active != null) { if (link != Link.READY) active?.let { connect(it) }; delay(1500); press(RemoteKey.POWER, quiet = true) }
        }
    }

    /** Sends a key from outside the app's screen (notification, tile): connects first if needed. */
    fun remoteKey(k: RemoteKey) {
        val d = active ?: return
        if (link != Link.READY) { connect(d); viewModelScope.launch { repeat(60) { if (link == Link.READY) { press(k, quiet = true); return@launch }; delay(100) } } }
        else press(k, quiet = true)
    }

    /** A launcher shortcut: opens that Quick access app once the TV is connected. */
    fun launchWhenReady(id: String) {
        val s = shortcuts.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            if (link != Link.READY) { active?.let { if (link != Link.CONNECTING) connect(it) } }
            repeat(100) { if (link == Link.READY) { launch(s); return@launch }; delay(100) }
            say("Couldn't reach the TV")
        }
    }

    /** What the TV is showing, as far as it tells us. */
    enum class Screen { HOME, APP, LIVE, UNKNOWN }

    val screen: Screen get() {
        val n = nowOn ?: return Screen.UNKNOWN
        return when {
            n.name.isEmpty() -> Screen.UNKNOWN
            n.live -> Screen.LIVE
            n.name == "Home" -> Screen.HOME
            else -> Screen.APP
        }
    }

    /**
     * Whether [k] does something right now: the TV must have the key, and it must fit what's on
     * screen (no playback keys on the home screen, no channel keys in apps). Unknown = allowed.
     */
    fun can(k: RemoteKey): Boolean {
        val a = adapter
        if (a != null && link == Link.READY && !a.supports(k)) return false
        return whyNot(k) == null
    }

    private val playbackKeys = setOf(RemoteKey.PLAY_PAUSE, RemoteKey.REWIND, RemoteKey.FAST_FORWARD)
    private val liveKeys = setOf(RemoteKey.CH_UP, RemoteKey.CH_DOWN, RemoteKey.GUIDE, RemoteKey.CH_LIST)
    private val colourKeys = setOf(RemoteKey.RED, RemoteKey.GREEN, RemoteKey.YELLOW, RemoteKey.BLUE)

    /** Why [k] isn't available right now, in a few words for a toast; null when it is. */
    fun whyNot(k: RemoteKey): String? {
        // Nothing works until a TV is connected.
        val d = active ?: return "Choose a TV first"
        if (link != Link.READY && btReady) return if (bt?.supports(k) == true) null else "${k.word} needs ${d.name} on Wi-Fi"
        if (link == Link.CONNECTING) return "Connecting to ${d.name}…"
        if (link != Link.READY) return "${d.name} isn't connected. Tap the TV at the top to connect."
        val a = adapter
        if (a != null && link == Link.READY && !a.supports(k)) return "${active?.name ?: "This TV"} doesn't have ${k.word}"
        return when (screen) {
            Screen.HOME -> when (k) {
                in playbackKeys -> "Playback keys work while something's playing"
                RemoteKey.SUBTITLES, RemoteKey.AUDIO -> "${k.word} works while something's playing"
                RemoteKey.INFO -> "Info shows programme details on live TV"
                in liveKeys -> "Channel keys work on live TV"
                in colourKeys -> "Colour keys work on live TV"
                else -> null
            }
            Screen.APP -> when (k) {
                in liveKeys -> "Channel keys work on live TV"
                // Google TV apps ignore the Info key; it shows programme details on live TV.
                RemoteKey.INFO -> if (active?.brand == Brand.ANDROID_TV) "Info shows programme details on live TV" else null
                in colourKeys -> "Colour keys work on live TV"
                else -> null
            }
            else -> null
        }
    }

    /** Whether the TV has [k] at all (whatever is on screen). */
    fun adapterHas(k: RemoteKey): Boolean { val a = adapter; return a == null || link != Link.READY || a.supports(k) }

    /** A dimmed key was tapped: say why. */
    fun explain(k: RemoteKey) { whyNot(k)?.let { feel.fire(Feel.ERROR); say(it) } }

    /* ------------------------------------------------------------- profile */

    /**
     * The profile as a file: your name, every setting, Quick access and the app icon. Paired
     * TVs aren't in it: their pairing keys belong to this phone (pair again on another one).
     */
    fun exportProfile(appIcon: String): String {
        val st = settings
        val o = org.json.JSONObject()
            .put("app", "Mini Remote").put("kind", "profile").put("version", 1)
            .put("name", st.profileName).put("email", st.profileEmail).put("appIcon", appIcon)
            .put("settings", org.json.JSONObject()
                .put("haptics", st.haptics).put("hapticStrength", st.hapticStrength.toDouble())
                .put("sound", st.sound).put("soundLevel", st.soundLevel.toDouble())
                .put("accentHex", st.accentHex).put("scrubStepDp", st.scrubStepDp.toDouble())
                .put("keepAwake", st.keepAwake).put("autoUpdate", st.autoUpdate)
                .put("controlStyle", st.controlStyle).put("layout", st.layout).put("appsIn", st.appsIn)
                .put("volumeKeys", st.volumeKeys).put("logos", st.logos)
                .put("autoKeyboard", st.autoKeyboard).put("notifRemote", st.notifRemote).put("autoClose", st.autoClose).put("theme", st.theme))
            .put("shortcuts", org.json.JSONArray(Shortcut.listToJson(shortcuts)))
        return o.toString(2)
    }

    /** Applies a profile file; returns its app icon (or null if the file isn't a profile). */
    fun importProfile(text: String): String? = runCatching {
        val o = org.json.JSONObject(text)
        require(o.optString("kind") == "profile" && o.optString("app") == "Mini Remote")
        val j = o.optJSONObject("settings") ?: org.json.JSONObject()
        val d = settings
        fun f(k: String, def: Float) = if (j.has(k)) j.getDouble(k).toFloat() else def
        val next = d.copy(
            profileName = o.optString("name", d.profileName).take(30),
            profileEmail = o.optString("email", d.profileEmail).take(80),
            haptics = j.optBoolean("haptics", d.haptics),
            hapticStrength = f("hapticStrength", d.hapticStrength).let { v -> HAPTIC_STRENGTHS.firstOrNull { it.first == v }?.first ?: d.hapticStrength },
            sound = j.optBoolean("sound", d.sound),
            soundLevel = f("soundLevel", d.soundLevel).let { v -> SOUND_LEVELS.firstOrNull { it.first == v }?.first ?: d.soundLevel },
            accentHex = j.optString("accentHex", d.accentHex).let { h -> ACCENTS.firstOrNull { it.second.equals(h, true) }?.second ?: d.accentHex },
            scrubStepDp = f("scrubStepDp", d.scrubStepDp).coerceIn(24f, 60f),
            keepAwake = j.optBoolean("keepAwake", d.keepAwake),
            autoUpdate = j.optBoolean("autoUpdate", d.autoUpdate),
            controlStyle = j.optString("controlStyle", d.controlStyle).takeIf { it == "dial" || it == "trackpad" } ?: d.controlStyle,
            layout = j.optString("layout", d.layout).takeIf { it == "strip" || it == "cards" } ?: d.layout,
            appsIn = j.optString("appsIn", d.appsIn).takeIf { it == "home" || it == "more" } ?: d.appsIn,
            volumeKeys = j.optBoolean("volumeKeys", d.volumeKeys),
            logos = j.optBoolean("logos", d.logos),
            autoKeyboard = j.optBoolean("autoKeyboard", d.autoKeyboard),
            notifRemote = j.optBoolean("notifRemote", d.notifRemote),
            autoClose = j.optBoolean("autoClose", d.autoClose),
            theme = j.optString("theme", d.theme).takeIf { it in setOf("dark", "light", "navy", "retro") } ?: d.theme,
        )
        update(next)
        o.optJSONArray("shortcuts")?.let { saveShortcuts(Shortcut.listFromJson(it.toString()).take(60)) }
        say("Profile loaded")
        o.optString("appIcon").ifEmpty { "dial" }
    }.getOrElse { say("That file isn't a Mini Remote profile"); null }

    /* ----------------------------------------------------------- Bluetooth */

    /** The phone as a Bluetooth remote (Android 9+); null on older phones. */
    private val bt: com.krishna.miniremote.net.BtRemote? =
        if (android.os.Build.VERSION.SDK_INT >= 28) com.krishna.miniremote.net.BtRemote(app) { viewModelScope.launch { btTick++ } } else null
    /** Bumps when the Bluetooth state changes (for the screens). */
    var btTick by mutableIntStateOf(0); private set
    /** Bumps when Bluetooth permission is needed, and when the phone should become visible to the TV. */
    var btAsk by mutableIntStateOf(0); private set
    var btDiscover by mutableIntStateOf(0); private set
    /** The TV waiting to pair over Bluetooth. */
    var btPairing by mutableStateOf<String?>(null); private set

    val btAvailable: Boolean get() = bt?.available == true
    /** Keys can go over Bluetooth to the TV in use right now. */
    val btReady: Boolean get() { btTick; val d = active ?: return false; val b = bt ?: return false; return d.bt != null && b.connected && b.host?.address == d.bt }

    private fun btPermitted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        val c = getApplication<Application>()
        return androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Starts the Bluetooth remote (if any TV uses it) and reconnects to the TV in use. */
    fun btResume() {
        val b = bt ?: return
        if (devices.none { it.bt != null } && btPairing == null) return
        if (!btPermitted() || !b.enabled) return
        b.onPaired = { addr, _ -> viewModelScope.launch { btPaired(addr) } }
        b.start()
        viewModelScope.launch { delay(800); active?.bt?.let { b.connect(it) } }
    }

    /** Pair [d] over Bluetooth: the phone becomes visible as "Mini Remote"; pick it on the TV. */
    fun btPair(d: Device) {
        val b = bt ?: run { say("Bluetooth remote needs Android 9 or newer"); return }
        if (!b.available) { say("This phone has no Bluetooth"); return }
        btPairing = d.id
        if (!btPermitted()) { btAsk++; return }
        if (!b.enabled) { say("Turn on Bluetooth, then try again"); btPairing = null; return }
        b.onPaired = { addr, _ -> viewModelScope.launch { btPaired(addr) } }
        b.start()
        btDiscover++
    }

    /** After the permission prompt. */
    fun btPermissionResult(ok: Boolean) {
        if (!ok) { btPairing = null; say("Bluetooth permission is off for Mini Remote"); return }
        btPairing?.let { id -> devices.firstOrNull { it.id == id }?.let { btPair(it) } } ?: btResume()
    }

    private fun btPaired(address: String) {
        val id = btPairing ?: return
        btPairing = null
        updateDevice(id) { it.copy(bt = address) }
        feel.fire(Feel.SUCCESS)
        say("Paired over Bluetooth")
    }

    fun btCancelPairing() { btPairing = null }

    fun btForget(d: Device) { updateDevice(d.id) { it.copy(bt = null) }; say("Bluetooth forgotten. Remove Mini Remote on the TV too.") }

}
