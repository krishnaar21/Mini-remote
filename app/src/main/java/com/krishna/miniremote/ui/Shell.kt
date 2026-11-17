package com.krishna.miniremote.ui

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
    RemoteKey.BACK -> Ico.CHEVRON_L; RemoteKey.HOME -> Ico.TV; RemoteKey.PLAY_PAUSE -> if (playing) Ico.PLAY_ONLY else Ico.PAUSE; RemoteKey.POWER -> Ico.POWER
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
    val pager = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Page?>(null) }
    var keys by remember { mutableStateOf(false) }
    val style = vm.settings.controlStyle
    // Dial or trackpad is a setting; the tray's 123 key swaps in the number pad for a while.
    var numpad by remember { mutableStateOf(false) }
    val deck = if (numpad) Deck.NUMPAD else if (style == "trackpad") Deck.TRACKPAD else Deck.DIAL
    var tray by remember { mutableStateOf(false) }
    val onRemote = pager.currentPage == Tab.HOME.ordinal
    // The tray tucks itself away a few seconds after the last command, and when you leave the page.
    LaunchedEffect(tray, vm.txCount) { if (tray) { kotlinx.coroutines.delay(7000); tray = false } }
    // Closing the tray puts the dial (or trackpad) back.
    LaunchedEffect(tray) { if (!tray) numpad = false }
    val layout = vm.settings.layout
    LaunchedEffect(layout) { numpad = false; tray = false }
    fun go(t: Tab) { scope.launch { pager.animateScrollToPage(t.ordinal) } }
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
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
                        when (Tab.entries[p]) {
                            Tab.HOME -> HomeScreen(
                                vm, accent, deck,
                                openDevices = { sheet = Page.Devices },
                                openKeys = { keys = true },
                                tray = tray, numpad = numpad, onNumpad = { numpad = !numpad },
                                onTray = { open -> if (open != tray) vm.feel.fire(com.krishna.miniremote.Feel.TICK); tray = open },
                            )
                            Tab.CHANNELS -> ChannelsScreen(vm, accent, openDevices = { sheet = Page.Devices })
                        }
                    }
                    // The dial shows messages in its well; everywhere else they float near the bottom.
                    val inWell = onRemote && deck == Deck.DIAL && !tray && layout != "cards"
                    ToastChip(if (inWell) null else vm.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = if (!onRemote) 56.dp else if (tray) 300.dp else if (layout == "sliders") 250.dp else 170.dp))
                    when (val u = vm.update) {
                        is RemoteVM.Update.Downloading, is RemoteVM.Update.Ready, is RemoteVM.Update.Manual ->
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).height(32.dp)) {
                                UpdatePill(u, accent, onInstall = { if (u is RemoteVM.Update.Manual) vm.openReleasePage() else vm.installUpdate() }, onDismiss = { vm.dismissUpdate() })
                            }
                        else -> {}
                    }
                    PageDots(pager.currentPage, accent, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), onSwipe = { by ->
                        val to = (pager.currentPage + by).coerceIn(0, Tab.entries.lastIndex)
                        if (to != pager.currentPage) { vm.feel.fire(com.krishna.miniremote.Feel.TICK); go(Tab.entries[to]) }
                    }) { go(it) }
                }
            }
        }
        sheet?.let { start -> RemoteSheet(vm, accent, start) { sheet = null } }
        if (keys) KeyboardSheet(vm, accent) { keys = false }
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
