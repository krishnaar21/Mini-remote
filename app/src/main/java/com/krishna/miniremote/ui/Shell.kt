package com.krishna.miniremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
enum class Tab(val label: String, val ico: Ico) {
    HOME("Home", Ico.HOME), CHANNELS("Channels", Ico.GRID), SETTINGS("Settings", Ico.GEAR), PROFILE("Profile", Ico.USER)
}

/** Height of the floating tab pill; screens leave this much room (plus a gap) at the bottom. */
val NAV_H = 64.dp

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
fun keyIcon(k: RemoteKey, muted: Boolean): Ico? = when (k) {
    RemoteKey.UP -> Ico.UP; RemoteKey.DOWN -> Ico.DOWN; RemoteKey.LEFT -> Ico.LEFT; RemoteKey.RIGHT -> Ico.RIGHT
    RemoteKey.BACK -> Ico.BACK; RemoteKey.HOME -> Ico.HOME; RemoteKey.PLAY_PAUSE -> Ico.PLAY; RemoteKey.POWER -> Ico.POWER
    RemoteKey.VOL_UP -> Ico.PLUS; RemoteKey.VOL_DOWN -> Ico.MINUS; RemoteKey.VOICE -> Ico.MIC
    RemoteKey.MUTE -> if (muted) Ico.SPEAKER_MUTED else Ico.SPEAKER
    RemoteKey.REWIND -> Ico.REWIND; RemoteKey.FAST_FORWARD -> Ico.FFWD
    RemoteKey.CH_UP -> Ico.UP; RemoteKey.CH_DOWN -> Ico.DOWN
    RemoteKey.GUIDE -> Ico.GUIDE; RemoteKey.CH_LIST -> Ico.LIST; RemoteKey.INFO -> Ico.INFO
    RemoteKey.INPUT -> Ico.INPUT; RemoteKey.MENU -> Ico.DISPLAY; RemoteKey.AUDIO -> Ico.AUDIO
    else -> null
}

/** Home, Channels, Settings and Profile under one floating pill, plus the shared sheets. */
@Composable
fun AppShell(vm: RemoteVM) {
    val accent = accentColor(vm.settings.accentHex)
    val grainBrush = remember { ShaderBrush(ImageShader(grain(), TileMode.Repeated, TileMode.Repeated)) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sheet by remember { mutableStateOf<Page?>(null) }
    var keys by remember { mutableStateOf<Boolean?>(null) } // true = search, false = type
    val style = vm.settings.controlStyle
    var deck by remember { mutableStateOf(if (style == "trackpad") Deck.TRACKPAD else Deck.DIAL) }
    // The Settings choice is the permanent one: changing it switches Home right away.
    LaunchedEffect(style) { deck = if (style == "trackpad") Deck.TRACKPAD else Deck.DIAL }
    val tilt = rememberTilt(vm.settings.gyro)
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    CompositionLocalProvider(LocalTilt provides tilt) {
        BoxWithConstraints(Modifier.fillMaxSize().background(T.Bg).drawBehind { drawRect(grainBrush, alpha = 0.30f) }) {
            val k = (maxWidth.value - 32f).coerceAtMost(420f) / 920f
            CompositionLocalProvider(LocalK provides k) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    Crossfade(tab, label = "tab") { t ->
                        when (t) {
                            Tab.HOME -> HomeScreen(
                                vm, accent, deck, onDeck = { deck = it },
                                openDevices = { sheet = Page.Devices },
                                openKeys = { keys = false }, openSearch = { keys = true },
                                openApps = { tab = Tab.CHANNELS },
                            )
                            Tab.CHANNELS -> ChannelsScreen(vm, accent, openDevices = { sheet = Page.Devices })
                            Tab.SETTINGS -> SettingsScreen(vm, accent, manage = { sheet = Page.Devices })
                            Tab.PROFILE -> ProfileScreen(vm, accent, open = { sheet = it })
                        }
                    }
                    // The dial shows messages in its well; everywhere else they float above the pill.
                    val inWell = tab == Tab.HOME && deck == Deck.DIAL
                    ToastChip(if (inWell) null else vm.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = NAV_H + 30.dp))
                    when (val u = vm.update) {
                        is RemoteVM.Update.Downloading, is RemoteVM.Update.Ready ->
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).height(32.dp)) {
                                UpdatePill(u, accent, onInstall = { vm.installUpdate() }, onDismiss = { vm.dismissUpdate() })
                            }
                        else -> {}
                    }
                    NavPill(tab, accent, initials(vm.settings.profileName), Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                        if (it != tab) vm.feel.fire(com.krishna.miniremote.Feel.TICK)
                        tab = it
                    }
                }
            }
        }
        sheet?.let { start -> RemoteSheet(vm, accent, start) { sheet = null } }
        keys?.let { search -> KeyboardSheet(vm, accent, search = search) { keys = null } }
        if (sheet == null) vm.pairPrompt?.let { PairCodeDialog(vm, it, accent) }
    }
}

/** One floating, fully round pill: icon over label, the current tab sits in a recessed well. */
@Composable
fun NavPill(tab: Tab, accent: Color, initial: String, modifier: Modifier = Modifier, onPick: (Tab) -> Unit) {
    Row(
        modifier.height(NAV_H).softFace(NAV_FACE, dpUnits = true).padding(6.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { t ->
            val sel = t == tab
            Column(
                Modifier.width(74.dp).fillMaxHeight()
                    .then(if (sel) Modifier.softFace(HOLLOW, sheen = false, dpUnits = true) else Modifier)
                    .clip(CircleShape)
                    .selectable(sel, role = Role.Tab) { onPick(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (t == Tab.PROFILE) Avatar(initial, accent, 22.dp, dim = !sel)
                else LineIcon(t.ico, if (sel) accent else T.Dim, 21.dp)
                Text(t.label, color = if (sel) T.Ink else T.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 3.dp))
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
    val ready = u as? RemoteVM.Update.Ready
    val text = when (u) {
        is RemoteVM.Update.Ready -> "Update ${u.info.name} ready · Install"
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
