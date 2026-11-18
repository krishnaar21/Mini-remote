package com.krishna.miniremote.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.krishna.miniremote.Link
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlin.random.Random

/** The four places in the floating pill at the bottom. */
/** The app's two pages. Settings live in a sheet, behind the gear in the TV sheet. */
enum class Tab(val label: String, val ico: Ico) {
    HOME("Remote", Ico.HOME), CHANNELS("Apps and channels", Ico.GRID)
}

/** Height of the floating tab pill; screens leave this much room (plus a gap) at the bottom. */
val NAV_H = 34.dp

/** Raised capsule the tab pill and the control switcher sit on. */
val NAV_FACE = Face(listOf(Color(0xFF303033), Color(0xFF1E1E21)), listOf(
    Sh(0f, 0f, Color.Black.copy(.6f), spread = 1f), Sh(10f, 24f, Color.Black.copy(.55f)),
    Sh(1f, 0f, Color.White.copy(.10f), inset = true), Sh(-2f, 3f, Color.Black.copy(.3f), inset = true)))

private fun grain(): ImageBitmap {
    val n = 96
    val px = IntArray(n * n)
    val r = Random(7)
    for (i in px.indices) px[i] = (r.nextInt(0, 22) shl 24) or 0xFFFFFF
    return android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The icon the dial's well shows above a key's name. */
fun keyIcon(k: RemoteKey, muted: Boolean, playing: Boolean = false): Ico? = when (k) {
    RemoteKey.UP -> Ico.UP; RemoteKey.DOWN -> Ico.DOWN; RemoteKey.LEFT -> Ico.LEFT; RemoteKey.RIGHT -> Ico.RIGHT
    RemoteKey.BACK -> Ico.CHEVRON_L; RemoteKey.HOME -> Ico.HOME; RemoteKey.PLAY_PAUSE -> if (playing) Ico.PLAY_ONLY else Ico.PAUSE; RemoteKey.POWER -> Ico.POWER
    RemoteKey.VOL_UP -> Ico.PLUS; RemoteKey.VOL_DOWN -> Ico.MINUS; RemoteKey.VOICE -> Ico.MIC
    RemoteKey.MUTE -> if (muted) Ico.SPEAKER_MUTED else Ico.SPEAKER
    RemoteKey.REWIND -> Ico.REWIND; RemoteKey.FAST_FORWARD -> Ico.FFWD
    RemoteKey.CH_UP -> Ico.UP; RemoteKey.CH_DOWN -> Ico.DOWN
    RemoteKey.GUIDE -> Ico.GUIDE; RemoteKey.CH_LIST -> Ico.LIST; RemoteKey.INFO -> Ico.INFO
    RemoteKey.INPUT -> Ico.INPUT; RemoteKey.MENU -> Ico.DISPLAY; RemoteKey.AUDIO -> Ico.AUDIO
    else -> null
}

/** Remote, Apps and Settings as pages you swipe between, plus the shared sheets. */
@Composable
fun AppShell(vm: RemoteVM) {
    val accent = accentColor(vm.settings.accentHex)
    /** The apps screen, slid up over the remote (from All apps on Home's third page). */
    var apps by remember { mutableStateOf(false) }
    /** Opened from a "+" place: tapping an app adds it to quick access. */
    var picking by remember { mutableStateOf(false) }
    /** Home's pages of circles: their dots sit at the bottom of the screen. */
    val keyPager = rememberPagerState { if (vm.settings.appsIn == "more") 2 else 4 }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Page?>(null) }
    var keys by remember { mutableStateOf(false) }
    var keysFromTv by remember { mutableStateOf(false) }
    var keysVoice by remember { mutableStateOf(false) }
    // Voice: ask for the microphone when it's needed, then start listening.
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.voiceStart() else vm.say("Microphone is off for Mini Remote. Turn it on in Android Settings › Apps › Mini Remote › Permissions.")
    }
    LaunchedEffect(vm.micAsk) { if (vm.micAsk > 0) askMic.launch(android.Manifest.permission.RECORD_AUDIO) }
    // Bluetooth remote: permission, then make the phone visible so the TV can find "Mini Remote".
    val askBt = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.btPermissionResult(r.values.all { it })
    }
    LaunchedEffect(vm.btAsk) {
        if (vm.btAsk > 0 && android.os.Build.VERSION.SDK_INT >= 31)
            askBt.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE))
    }
    val discover = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_CANCELED) { vm.btCancelPairing(); vm.say("The TV can't find the phone unless it's visible") }
    }
    LaunchedEffect(vm.btDiscover) {
        if (vm.btDiscover > 0) runCatching {
            discover.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180))
        }
    }
    // The TV didn't take the phone's voice: open Search in Voice mode (speech typed on the TV).
    LaunchedEffect(vm.voiceTyping) { if (vm.voiceTyping > 0) { keysVoice = true; keysFromTv = false; keys = true } }
    var more by remember { mutableStateOf(false) }
    // The TV focused a text box (login, search): open the typing sheet, without pressing Search again.
    LaunchedEffect(vm.keyboardAsk) { if (vm.keyboardAsk > 0 && !keys) { keysFromTv = true; keys = true } }
    val style = vm.settings.controlStyle
    // Dial or trackpad is a setting; the tray's 123 key swaps in the number pad for a while.
    var numpad by remember { mutableStateOf(false) }
    val deck = if (numpad) Deck.NUMPAD else if (style == "trackpad") Deck.TRACKPAD else Deck.DIAL
    var tray by remember { mutableStateOf(false) }
    val onRemote = !apps
    // The tray tucks itself away a few seconds after the last command, and when you leave the page.
    LaunchedEffect(tray, vm.txCount) { if (tray) { kotlinx.coroutines.delay(7000); tray = false } }
    // Closing the tray puts the dial (or trackpad) back.
    LaunchedEffect(tray) { if (!tray) numpad = false }
    val layout = "strip"
    LaunchedEffect(layout) { numpad = false; tray = false }
    // The number pad belongs to live TV: put the dial back when an app takes over.
    LaunchedEffect(vm.nowOn?.live) { if (vm.nowOn?.live != true) numpad = false }
    fun go(t: Tab) { apps = t == Tab.CHANNELS }
    // Once a TV finishes connecting, the TV sheet has done its job: close it.
    var connecting by remember { mutableStateOf(false) }
    LaunchedEffect(vm.link) {
        when (vm.link) {
            Link.CONNECTING -> connecting = true
            Link.READY -> if (connecting) { connecting = false; if (sheet != null) { kotlinx.coroutines.delay(900); sheet = null } }
            else -> connecting = false
        }
    }
    val tilt = rememberTilt(false) // the flat Material faces have no sheen to tilt
    BackHandler(enabled = !onRemote) { go(Tab.HOME) }

    CompositionLocalProvider(LocalTilt provides tilt) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (layout == "strip") Color.Black else T.Bg)) {
            val k = (maxWidth.value - 32f).coerceAtMost(420f) / 920f
            CompositionLocalProvider(LocalK provides k) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    run {
                        run {
                            HomeScreen(
                                vm, accent, deck,
                                openDevices = { sheet = Page.Devices },
                                openKeys = { keys = true },
                                tray = tray, numpad = numpad, onNumpad = { numpad = !numpad },
                                onTray = { open -> if (open != tray) vm.feel.fire(com.krishna.miniremote.Feel.TICK); tray = open },
                                openMore = { more = true },
                                openApps = { pick -> picking = pick; apps = true },
                                keyPager = keyPager,
                            )
                        }
                    }
                    // Which page of circles: a pill for the one showing, dots for the others.
                    if (!apps) PagePills(keyPager.pageCount, keyPager.currentPage, accent,
                        listOf("Main keys", "More keys", "Apps", "More apps"), Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) { i ->
                        vm.feel.fire(com.krishna.miniremote.Feel.TICK); scope.launch { keyPager.animateScrollToPage(i) }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        apps,
                        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320)) { it },
                        exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(260)) { it },
                    ) {
                        // Pull down from the top bar to close: the screen follows your finger, and
                        // goes back up if you let go early.
                        var pull by remember { mutableStateOf(0f) }
                        val shown by androidx.compose.animation.core.animateFloatAsState(pull, label = "pull")
                        val close = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
                        // Pulling down anywhere once the list is at its top drags the screen too.
                        val pullDown = remember {
                            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                    // Pulled down and now pushing up: shrink the pull first.
                                    if (pull > 0f && available.y < 0f) { val used = maxOf(available.y, -pull); pull += used; return androidx.compose.ui.geometry.Offset(0f, used) }
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }
                                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                    if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) { pull += available.y; return androidx.compose.ui.geometry.Offset(0f, available.y) }
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }
                                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                    if (pull <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                                    if (pull > close || available.y > 2500f) apps = false
                                    pull = 0f
                                    return available
                                }
                            }
                        }
                        Column(Modifier.fillMaxSize().graphicsLayer { translationY = shown }.background(T.Bg)
                            .nestedScroll(pullDown)) {
                            Box(Modifier.fillMaxWidth().height(52.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { if (pull > close) apps = false; pull = 0f },
                                        onDragCancel = { pull = 0f },
                                    ) { c, dy -> c.consume(); pull = (pull + dy).coerceAtLeast(0f) }
                                }
                                .clickable(onClickLabel = "back to the remote") { apps = false }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                            }
                            Box(Modifier.weight(1f)) { ChannelsScreen(vm, accent, openDevices = { sheet = Page.Devices }, picking = picking, onFull = { apps = false; vm.say("Quick access is full") }) }
                        }
                    }
                    // The dial shows messages in its well; everywhere else they float near the bottom.
                    // Only quick key feedback sits in the dial; messages and errors go to the toast.
                    val inWell = onRemote && deck == Deck.DIAL && !tray && layout != "cards" && vm.toastKey != null
                    // On the remote it sits under the top bar (clear of the keys); elsewhere near the bottom.
                    ToastChip(if (inWell) null else vm.toast,
                        if (onRemote) Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 24.dp, end = 24.dp)
                        else Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp))
                    when (val u = vm.update) {
                        is RemoteVM.Update.Downloading, is RemoteVM.Update.Ready, is RemoteVM.Update.Manual ->
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).height(32.dp)) {
                                UpdatePill(u, accent, onInstall = { if (u is RemoteVM.Update.Manual) vm.openReleasePage() else vm.installUpdate() }, onDismiss = { vm.dismissUpdate() })
                            }
                        else -> {}
                    }
                }
            }
        }
        sheet?.let { start -> RemoteSheet(vm, accent, start) { sheet = null } }
        if (keys) KeyboardSheet(vm, accent, openSearch = !keysFromTv && !keysVoice, startVoice = keysVoice) { keys = false; keysFromTv = false; keysVoice = false }
        if (more) MoreSheet(vm, accent, openApps = { pick -> picking = pick; apps = true }) { more = false }
        if (sheet == null) vm.pairPrompt?.let { PairCodeDialog(vm, it, accent) }
    }
}

/** "Vol · Ch" pill: tap (or swipe up) for the volume and channel tray; tap again or swipe down to hide. */
@Composable
fun TrayPill(open: Boolean, accent: Color, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(if (open) accent.copy(alpha = 0.22f) else M.High)
            .clickable(onClickLabel = if (open) "hide volume and channels" else "show volume and channels", role = Role.Button) { onToggle(!open) }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if (total < -16.dp.toPx()) onToggle(true) else if (total > 16.dp.toPx()) onToggle(false)
                }) { c, dy -> c.consume(); total += dy }
            }
            .semantics { contentDescription = "Volume and channels" }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Ico.SPEAKER, if (open) accent else T.Ink, 18.dp)
        Text("Vol · Ch", color = if (open) accent else T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        LineIcon(if (open) Ico.DOWN else Ico.UP, T.Dim, 14.dp, stroke = 2.2f)
    }
}

/** Page dots: a pill in the accent colour for the page showing, dots for the others; tap to go. */
@Composable
fun PagePills(n: Int, page: Int, accent: Color, names: List<String>, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Row(modifier.height(40.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        repeat(n) { i ->
            val sel = i == page
            val w by androidx.compose.animation.core.animateDpAsState(if (sel) 18.dp else 6.dp, label = "pill")
            Box(
                Modifier.height(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(i) }
                    .semantics { contentDescription = names.getOrElse(i) { "Page ${i + 1}" } }.padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(w, 6.dp).clip(CircleShape).background(if (sel) accent else Color(0xFF7C7C82))) }
        }
    }
}

/** Two page dots; tap one, or swipe across them, to change page. */
@Composable
fun PageDots(page: Int, accent: Color, modifier: Modifier = Modifier, onSwipe: (Int) -> Unit, onPick: (Tab) -> Unit) {
    Row(
        modifier.height(40.dp).pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                if (total < -16.dp.toPx()) onSwipe(1) else if (total > 16.dp.toPx()) onSwipe(-1)
            }) { c, dx -> c.consume(); total += dx }
        }.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val sel = t.ordinal == page
            Box(
                Modifier.height(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(t) }
                    .semantics { contentDescription = t.label }.padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (sel) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (sel) accent else Color(0xFF7C7C82)))
            }
        }
    }
}

/** First letters of the profile name ("K" or "KA"), or a person icon when there's no name. */
fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun Avatar(initial: String, accent: Color, size: Dp, dim: Boolean = false) {
    val fs = with(LocalDensity.current) { (size * 0.42f).toSp() }
    Box(
        Modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(mix(accent, 0.75f, Color.White), mix(accent, 0.7f, Color.Black)), Offset.Zero, Offset.Infinite),
            alpha = if (dim) 0.65f else 1f,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (initial.isEmpty()) LineIcon(Ico.USER, Color.White, size * 0.6f)
        else Text(initial, color = Color.White, fontSize = fs, fontWeight = FontWeight.SemiBold)
    }
}

/** Big page title for the full-screen tabs, with room for round buttons on the right. */
@Composable
fun PageTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = T.Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/** Round soft-touch icon button for page headers. */
@Composable
fun RoundIcon(ico: Ico, label: String, onClick: () -> Unit) = CircleIcon(ico, label, onClick)

/** Short message that floats above the tab pill. */
@Composable
fun ToastChip(text: String?, modifier: Modifier = Modifier) {
    // Keep the last text while it fades out (no state write during composition).
    val last = remember { arrayOf("") }
    if (text != null) last[0] = text
    AnimatedVisibility(text != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        Text(
            last[0], color = T.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.softFace(CARD, dpUnits = true).padding(horizontal = 18.dp, vertical = 11.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** "Update 0.2.N ready · Install" (or download progress), with a close button. */
@Composable
private fun UpdatePill(u: RemoteVM.Update, accent: Color, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val ready: Any? = (u as? RemoteVM.Update.Ready) ?: (u as? RemoteVM.Update.Manual)
    val text = when (u) {
        is RemoteVM.Update.Ready -> "Update ${u.info.name} ready · Install"
        is RemoteVM.Update.Manual -> "${u.info.name} available · Download"
        is RemoteVM.Update.Downloading -> "Downloading update ${(u.progress * 100).toInt()}%"
        else -> ""
    }
    Row(
        Modifier.fillMaxHeight().clip(CircleShape).background(Color(0xFF2A2A2B))
            .then(if (ready != null) Modifier.clickable(onClickLabel = "install update", onClick = onInstall) else Modifier)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).background(accent, CircleShape))
        Text(text, color = T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (ready != null) Box(
            Modifier.size(28.dp).clip(CircleShape)
                .clickable(onClickLabel = "dismiss", onClick = onDismiss).semantics { contentDescription = "Dismiss update" },
            contentAlignment = Alignment.Center,
        ) { LineIcon(Ico.CLOSE, T.Dim, 14.dp) } else Box(Modifier.size(10.dp))
    }
}
