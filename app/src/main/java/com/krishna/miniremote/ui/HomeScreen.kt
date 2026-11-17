package com.krishna.miniremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.Feel
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What fills the middle of Home. Dial or trackpad comes from Settings; numbers from the tray. */
enum class Deck(val label: String, val ico: Ico) {
    DIAL("Dial", Ico.DIAL), TRACKPAD("Trackpad", Ico.TRACKPAD), NUMPAD("Number pad", Ico.NUMPAD)
}

/** How long OK waits for a second tap (double tap = mute). */
private const val DOUBLE_TAP_MS = 260L

/**
 * Home, after the Apple TV Remote: mute, the TV picker (with what's on screen) and power on
 * top; one big touch surface (or the dial); four round keys below.
 *
 * One finger navigates; two fingers slide for volume (up / down) and channels (sideways).
 * OK: tap = OK, double tap = mute, hold = voice. Swipe up for the volume / channel tray.
 */
@Composable
fun HomeScreen(
    vm: RemoteVM,
    accent: Color,
    deck: Deck,
    openDevices: () -> Unit,
    openKeys: () -> Unit,
    onTray: (open: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pendingOk = remember { arrayOfNulls<Job>(1) }
    fun okTap() {
        val waiting = pendingOk[0]
        if (waiting?.isActive == true) {
            waiting.cancel(); pendingOk[0] = null
            vm.press(RemoteKey.MUTE)
        } else {
            pendingOk[0] = scope.launch { delay(DOUBLE_TAP_MS); pendingOk[0] = null; vm.press(RemoteKey.OK, quiet = true) }
        }
    }
    val onKey: (RemoteKey) -> Unit = { k -> if (k == RemoteKey.OK) okTap() else vm.press(k) }
    val onTick: (RemoteKey) -> Unit = { k -> vm.scrubTick(k) }
    // One surface does both: one finger navigates, two fingers change volume and channels.
    val onTwo: (RemoteKey) -> Unit = { k -> vm.scrubTick(k); if (k == RemoteKey.CH_UP || k == RemoteKey.CH_DOWN) vm.say(k.word, k) }
    val tray by rememberUpdatedState(onTray)
    val swipe = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 40.dp.toPx()) tray(total < 0) },
        ) { change, dy -> change.consume(); total += dy }
    }
    // Ask the TV what's on screen while Home is open (Roku and LG answer).
    androidx.compose.runtime.DisposableEffect(vm.activeId, vm.link) {
        vm.followNowOn(vm.link == Link.READY)
        onDispose { vm.followNowOn(false) }
    }
    val offline = vm.active == null || vm.link == Link.ERROR

    Column(
        Modifier.fillMaxSize().then(swipe).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = NAV_H + 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top, as on the Apple TV Remote: mute · TV picker · power.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KeyButton(accent, "Mute", Modifier.size(44.dp), state = if (vm.muted) "Muted" else null, pressedScale = 1f, onPress = { vm.press(RemoteKey.MUTE) }) { c, _ ->
                LineIcon(if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) accent else c, 19.dp)
            }
            TvPicker(vm, Modifier.weight(1f), openDevices)
            KeyButton(accent, "Power", Modifier.size(44.dp), restColor = accent, pressedScale = 1f, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 19.dp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            val w = maxWidth
            val h = maxHeight
            Box(
                Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Mute") { vm.press(RemoteKey.MUTE); true },
                        CustomAccessibilityAction("Volume and channels") { tray(true); true },
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(deck, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "deck") { d ->
                    when (d) {
                        Deck.NUMPAD -> {
                            val s = minOf(w, h, 340.dp)
                            CompositionLocalProvider(LocalK provides s.value / 780f) { Numpad(vm, accent, Modifier.size(s)) }
                        }
                        Deck.DIAL -> {
                            val s = minOf(w - 8.dp, h, 340.dp)
                            CompositionLocalProvider(LocalK provides s.value / 780f) {
                                Clickpad(
                                    size = s, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                                    toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                                    onKey = onKey, onTick = onTick,
                                    onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                    offline = offline, onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                    onTwoFinger = onTwo,
                                )
                            }
                        }
                        Deck.TRACKPAD -> {
                            // Apple-style: the full width, a little taller than wide where there's room.
                            val tw = w
                            val th = minOf(h, tw * 1.25f)
                            CompositionLocalProvider(LocalK provides tw.value / 780f) {
                                Trackpad(
                                    size = tw, height = th, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                                    onKey = onKey, onTick = onTick,
                                    onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                    onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                    onTwoFinger = onTwo,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Four round keys under the surface.
        val k = Modifier.size(62.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyButton(accent, "Back", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.CHEVRON_L, c, 24.dp, stroke = 2.2f) }
            KeyButton(accent, "Home", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.TV, c, 22.dp) }
            KeyButton(accent, "Play or pause", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, 21.dp) }
            KeyButton(accent, "Search", k, actionLabel = "search or type on TV", pressedScale = 1f, onPress = openKeys) { c, _ -> LineIcon(Ico.SEARCH, c, 21.dp) }
        }
    }
}

/** "Living room TV ▾" centred, with what's on screen (Roku / LG) or the link state under it. */
@Composable
private fun TvPicker(vm: RemoteVM, modifier: Modifier, openDevices: () -> Unit) {
    val d = vm.active
    val on = d != null && vm.link == Link.READY
    val sub = when {
        d == null -> "Tap to add a TV"
        !on -> linkWord(vm.link).ifEmpty { "Not connected" }
        else -> vm.nowOn?.name ?: "Connected"
    }
    val subCol = when {
        d == null -> T.Dim
        on -> Color(0xFF6DD58C)
        vm.link == Link.CONNECTING -> Color(0xFFFDD663)
        else -> Color(0xFFF2B8B5)
    }
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "choose a TV", role = Role.Button, onClick = openDevices)
            .semantics(mergeDescendants = true) {
                contentDescription = d?.let { "TV: ${it.name}" } ?: "Choose a TV"
                stateDescription = sub
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d?.let { shortName(it.name, it.brand.label) } ?: "Choose a TV", color = T.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            LineIcon(Ico.DOWN, T.Dim, 15.dp, stroke = 2.2f)
        }
        Text(sub, color = subCol, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Volume and channels, tucked into the tab bar: it grows out of the pill when you swipe up
 * on Home or on the pill, or tap Home again. Swipe down, or wait, and it tucks away.
 */
@Composable
fun ControlTray(vm: RemoteVM, accent: Color, numpad: Boolean, onNumpad: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.width(320.dp).shadow(6.dp, RoundedCornerShape(28.dp)).background(M.Container, RoundedCornerShape(28.dp))
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(22.dp).clickable(onClickLabel = "hide volume and channels", role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(Color(0xFF7C7C82))) }
        VolumePill(
            accent, vm.muted, Modifier.fillMaxWidth().height(54.dp),
            onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) },
            onMute = { vm.press(RemoteKey.MUTE) },
            onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) },
        )
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelPill(accent, Modifier.weight(1f).fillMaxHeight(),
                onDown = { vm.press(RemoteKey.CH_DOWN) }, onUp = { vm.press(RemoteKey.CH_UP) })
            KeyButton(accent, "Number pad", Modifier.size(54.dp), state = if (numpad) "Showing" else null, pressedScale = 1f,
                restColor = if (numpad) accent else T.Ink, onPress = onNumpad) { c, _ ->
                Text("123", color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}


/** A TV's name for the top bar: without its IP address, and at most 18 characters. */
fun shortName(name: String, fallback: String): String {
    val n = name.replace(Regex("\\(?\\b\\d{1,3}(\\.\\d{1,3}){3}\\b\\)?"), "").replace(Regex("\\s+"), " ").trim().ifEmpty { fallback }
    return if (n.length > 18) n.take(17).trimEnd() + "…" else n
}
