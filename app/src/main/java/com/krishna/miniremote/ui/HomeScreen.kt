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
    tray: Boolean,
    numpad: Boolean,
    onNumpad: () -> Unit,
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
    val setTray by rememberUpdatedState(onTray)
    val swipe = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 40.dp.toPx()) setTray(total < 0) },
        ) { change, dy -> change.consume(); total += dy }
    }
    // Ask the TV what's on screen while Home is open (Roku and LG answer).
    androidx.compose.runtime.DisposableEffect(vm.activeId, vm.link) {
        vm.followNowOn(vm.link == Link.READY)
        onDispose { vm.followNowOn(false) }
    }
    val offline = vm.active == null || vm.link == Link.ERROR

    Column(Modifier.fillMaxSize().then(swipe), horizontalAlignment = Alignment.CenterHorizontally) {
      Column(Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top, as on the Apple TV Remote: mute · TV picker · power, inset from the screen edges.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyButton(accent, "Mute", Modifier.size(52.dp), state = if (vm.muted) "Muted" else null, pressedScale = 1f, onPress = { vm.press(RemoteKey.MUTE) }) { c, _ ->
                LineIcon(if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) accent else c, 22.dp)
            }
            TvPicker(vm, Modifier.weight(1f), openDevices)
            KeyButton(accent, "Power", Modifier.size(52.dp), restColor = accent, pressedScale = 1f, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 22.dp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            val w = maxWidth
            val h = maxHeight
            Box(
                Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Mute") { vm.press(RemoteKey.MUTE); true },
                        CustomAccessibilityAction("Volume and channels") { setTray(true); true },
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
                                    toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted, vm.playing) },
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

      }
        BottomPanel(vm, accent, tray, numpad, onNumpad, openKeys, setTray)
    }
}

/**
 * The bottom of the remote, full width. Collapsed: a small chevron and the four keys
 * (Back, Home, Play / pause, Search). Swipe up (or tap the chevron) and it grows into a
 * sheet with volume, channels and the number pad key under those four. Swipe down to fold.
 */
@Composable
private fun BottomPanel(
    vm: RemoteVM, accent: Color, open: Boolean, numpad: Boolean,
    onNumpad: () -> Unit, openKeys: () -> Unit, onTray: (Boolean) -> Unit,
) {
    val set by rememberUpdatedState(onTray)
    Column(
        Modifier.fillMaxWidth()
            .background(if (open) M.Container else Color.Transparent, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if (total < -24.dp.toPx()) set(true) else if (total > 24.dp.toPx()) set(false)
                }) { c, dy -> c.consume(); total += dy }
            }
            .padding(start = 16.dp, end = 16.dp, bottom = NAV_H + 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp, 32.dp).clip(CircleShape)
                .clickable(onClickLabel = if (open) "hide volume and channels" else "show volume and channels", role = Role.Button) { set(!open) },
            contentAlignment = Alignment.Center,
        ) { LineIcon(if (open) Ico.DOWN else Ico.UP, T.Dim, 22.dp, stroke = 2.4f) }
        val k = Modifier.size(62.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyButton(accent, "Back", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.CHEVRON_L, c, 24.dp, stroke = 2.2f) }
            KeyButton(accent, "Home", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.TV, c, 22.dp) }
            KeyButton(accent, if (vm.playing) "Pause" else "Play", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ ->
                LineIcon(if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, c, if (vm.playing) 22.dp else 20.dp, stroke = if (vm.playing) 2.4f else 1.8f)
            }
            KeyButton(accent, "Search", k, actionLabel = "search or type on TV", pressedScale = 1f, onPress = openKeys) { c, _ -> LineIcon(Ico.SEARCH, c, 21.dp) }
        }
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically() + fadeIn(), exit = androidx.compose.animation.shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 18.dp).width(308.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

/** The TV picker: one slim line in a pill, as tall as the mute and power keys. Dot = link state. */
@Composable
private fun TvPicker(vm: RemoteVM, modifier: Modifier, openDevices: () -> Unit) {
    val d = vm.active
    val dot = when {
        d == null -> null
        vm.link == Link.READY -> Color(0xFF6DD58C)
        vm.link == Link.CONNECTING -> Color(0xFFFDD663)
        else -> Color(0xFFF2B8B5)
    }
    Box(modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(M.High)
                .clickable(onClickLabel = "choose a TV", role = Role.Button, onClick = openDevices)
                .semantics(mergeDescendants = true) {
                    contentDescription = d?.let { "TV: ${it.name}" } ?: "Choose a TV"
                    if (d != null) stateDescription = linkWord(vm.link)
                }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
            Text(d?.let { shortName(it.name, it.brand.label) } ?: "Choose a TV", color = T.Ink, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            LineIcon(Ico.DOWN, T.Dim, 15.dp, stroke = 2.2f)
        }
    }
}

/** A TV's name for the top bar: without its IP address, and at most 18 characters. */
fun shortName(name: String, fallback: String): String {
    val n = name.replace(Regex("\\(?\\b\\d{1,3}(\\.\\d{1,3}){3}\\b\\)?"), "").replace(Regex("\\s+"), " ").trim().ifEmpty { fallback }
    return if (n.length > 18) n.take(17).trimEnd() + "…" else n
}
