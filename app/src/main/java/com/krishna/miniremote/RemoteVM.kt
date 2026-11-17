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
    var txCount by mutableIntStateOf(0); private set
    /** Mute is a toggle on every TV we support, so we track it locally to colour the button. */
    var muted by mutableStateOf(false); private set
    /** Play / pause is a toggle too: tracked locally so the key shows one icon at a time. */
    var playing by mutableStateOf(false); private set
    /** Non-null while an Android / Google TV is showing a pairing code and we're waiting for it. */
    var pairPrompt by mutableStateOf<PairPrompt?>(null); private set
    private var codeAnswer: CompletableDeferred<String?>? = null

    val active: Device? get() = devices.firstOrNull { it.id == activeId }
    val localSeen: Boolean get() = store.localSeen()

    private var adapter: RemoteAdapter? = null
    private var connectJob: Job? = null
    private var toastJob: Job? = null
    private val queue = Channel<RemoteKey>(64, BufferOverflow.DROP_OLDEST)

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
                if (link != Link.READY || a == null) continue
                try { a.send(k); txCount++ } catch (e: TimeoutCancellationException) {
                    feel.fire(Feel.ERROR); say("The TV didn't answer")
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
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
        if (key == RemoteKey.PLAY_PAUSE) playing = !playing
        if (key == RemoteKey.VOL_UP) muted = false
        if (!quiet) say(if (key == RemoteKey.PLAY_PAUSE) (if (playing) "Play" else "Pause") else key.word, key)
        queue.trySend(key)
    }

    fun scrubTick(key: RemoteKey) { feel.fire(Feel.TICK); queue.trySend(key) }

    fun say(t: String, key: RemoteKey? = null) {
        toast = t
        toastKey = key
        toastJob?.cancel()
        // Longer messages stay up longer so they can be read.
        toastJob = viewModelScope.launch { delay(1100L + 45L * t.length.coerceAtMost(80)); toast = null; toastKey = null }
    }

    fun connect(d: Device) {
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
                a.connect()
                adapter = a
                link = Link.READY
                feel.fire(Feel.SUCCESS)
            } catch (e: CancellationException) {
                // A timeout inside connect is a failure to show, not this job being cancelled.
                if (e !is TimeoutCancellationException) throw e
                link = Link.ERROR
                val msg = "${d.name} didn't answer in time"
                lastError = msg
                say(msg)
                feel.fire(Feel.ERROR)
            } catch (e: Exception) {
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
        val a = adapter ?: return
        val id = activeId ?: return
        if (link != Link.READY || appsLoading || (!force && appsFor == id)) return
        appsLoading = true
        viewModelScope.launch {
            try {
                tvApps = a.listApps().sortedBy { it.name.lowercase() }
                appsFor = id
            } catch (e: TimeoutCancellationException) {
                if (force) say("The TV didn't send its apps")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                appsFor = id
                if (force) say(if (e is UserFacing) e.message ?: "Couldn't load apps" else "Couldn't load apps")
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
        val id = c.ids[brand] ?: tvApps.firstOrNull { it.name.equals(c.name, true) || it.name.contains(c.name, true) }?.id
        if (id == null) { feel.fire(Feel.ERROR); say("${c.name} isn't set up for this TV. Add it with its app ID."); return }
        launchApp(c.name, id)
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

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        say("Searching for $q")
        act("Couldn't search") { it.search(q) }
    }

    private fun saveShortcuts(l: List<Shortcut>) { shortcuts = l; store.saveShortcuts(l) }

    fun addShortcut(kind: ShortcutKind, name: String, value: String, pin: Boolean) {
        val s = Shortcut(
            id = "s${System.currentTimeMillis()}", kind = kind, name = name.trim().take(30), value = value.trim(),
            brand = if (kind == ShortcutKind.APP) active?.brand else null, pinned = pin, added = true,
        )
        saveShortcuts(shortcuts + s)
        feel.fire(Feel.SUCCESS); say("Added ${s.name}")
    }

    fun pinnedCatalog(c: CatalogApp): Shortcut? = shortcuts.firstOrNull { it.kind == ShortcutKind.APP && it.brand == null && it.value == c.key }
    fun pinnedTvApp(app: TvApp): Shortcut? = shortcuts.firstOrNull { it.kind == ShortcutKind.APP && it.brand == active?.brand && it.value == app.id }

    fun pinCatalog(c: CatalogApp) {
        if (pinnedCatalog(c) == null) saveShortcuts(shortcuts + Shortcut("c${System.currentTimeMillis()}", ShortcutKind.APP, c.name, c.key, pinned = true))
        say("${c.name} added to quick access")
    }

    fun pinTvApp(app: TvApp) {
        val brand = active?.brand ?: return
        if (pinnedTvApp(app) == null) {
            saveShortcuts(shortcuts + Shortcut("t${System.currentTimeMillis()}", ShortcutKind.APP, app.name, app.id, brand, app.icon, pinned = true))
        }
        say("${app.name} added to quick access")
    }

    /** Unpinning something that was only pinned (not added) forgets it. */
    fun setPinned(s: Shortcut, pinned: Boolean) {
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

    override fun onCleared() { adapter?.close() }
}
