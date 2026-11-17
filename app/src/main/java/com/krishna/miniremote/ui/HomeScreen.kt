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
 * OK: tap = OK, double tap = mute, hold = voice. Volume and channels depend on the layout chosen in Settings.
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
    val onTwo: (RemoteKey) -> Unit = { k -> vm.scrubTick(k) }
    // Two-finger double tap: swap dial and trackpad (saved, like the setting).
    val lastTwoTap = remember { longArrayOf(0L) }
    val onTwoTap: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTwoTap[0] < 450) {
            lastTwoTap[0] = 0L
            val next = if (vm.settings.controlStyle == "trackpad") "dial" else "trackpad"
            vm.update(vm.settings.copy(controlStyle = next))
            vm.feel.fire(Feel.DETENT) // just the switch: no message, no key sent
        } else lastTwoTap[0] = now
    }
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
    var askPower by remember { mutableStateOf(false) }
    if (askPower) vm.active?.let { tv ->
        ConfirmSheet(
            title = "Turn off ${shortName(tv.name, tv.brand.label)}?",
            text = "If the TV is already off, this turns it on.",
            action = "Turn off", danger = true,
            onConfirm = { vm.press(RemoteKey.POWER) }, onDismiss = { askPower = false },
        )
    }

    val layout = HomeLayout.of(vm.settings.layout)
    val voice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) }
    val offlineTap = { vm.feel.fire(Feel.PRESS); openDevices() }
    // The dial at a given size; [round] = sits inside the volume ring.
    val dialAt: @Composable (Dp, Boolean, String?) -> Unit = { s, round, center ->
        CompositionLocalProvider(LocalK provides s.value / 780f) {
            Clickpad(
                size = s, accent = accent, stepDp = vm.settings.scrubStepDp,
                toast = center ?: if (layout == HomeLayout.CARDS) null else vm.toast,
                toastIcon = if (center != null) (if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER) else vm.toastKey?.let { keyIcon(it, vm.muted, vm.playing) },
                onKey = onKey, onTick = onTick, onVoice = voice,
                offline = offline, onOffline = offlineTap,
                onTwoFinger = onTwo, onTwoFingerTap = onTwoTap, roundOnly = round,
            )
        }
    }
    val trackpadAt: @Composable (Dp) -> Unit = { s ->
        CompositionLocalProvider(LocalK provides s.value / 780f) {
            Trackpad(
                size = s, height = s, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                onKey = onKey, onTick = onTick, onVoice = voice, onOffline = offlineTap,
                onTwoFinger = onTwo, onTwoFingerTap = onTwoTap,
            )
        }
    }
    // Dial, trackpad or number pad, as big as [m] allows.
    val padArea: @Composable (Deck, Modifier) -> Unit = { d, m ->
        BoxWithConstraints(m, contentAlignment = Alignment.Center) {
            val w = maxWidth
            val h = maxHeight
            AnimatedContent(d, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "deck") { dd ->
                when (dd) {
                    Deck.NUMPAD -> {
                        val s = minOf(w, h, 340.dp)
                        CompositionLocalProvider(LocalK provides s.value / 780f) { Numpad(vm, accent, Modifier.size(s)) }
                    }
                    Deck.DIAL -> dialAt(minOf(w - 8.dp, h, 340.dp), false, null)
                    Deck.TRACKPAD -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Same place and size as the dial.
                        val tw = minOf(w - 8.dp, h, 340.dp)
                        trackpadAt(tw)
                    }
                }
            }
        }
    }
    // The dial inside the volume ring (the trackpad and number pad have no ring).
    val ringArea: @Composable (Modifier) -> Unit = { m ->
        if (deck == Deck.NUMPAD) padArea(deck, m)
        else BoxWithConstraints(m, contentAlignment = Alignment.Center) {
            val s = minOf(maxWidth - 8.dp - RING_BAND * 2, maxHeight - RING_BAND * 2, 300.dp)
            // The trackpad takes the dial's place, ring included: same centre, same size.
            val v = vm.tvVolume
            if (deck == Deck.TRACKPAD) trackpadAt(s + RING_BAND * 2) else
            RingDial(s, accent, level = v?.let { if (it.muted) 0f else it.level.toFloat() / it.max }, value = v?.let { if (it.muted) "Muted" else "${it.level}" }, onStep = { up -> vm.scrubTick(if (up) RemoteKey.VOL_UP else RemoteKey.VOL_DOWN) }) { turning -> dialAt(s, true, if (turning) v?.let { if (it.muted) "Muted" else "${it.level}" } ?: "Volume" else null) }
        }
    }
    val a11y = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction("Mute") { vm.press(RemoteKey.MUTE); true },
            CustomAccessibilityAction("Volume up") { vm.press(RemoteKey.VOL_UP); true },
            CustomAccessibilityAction("Volume down") { vm.press(RemoteKey.VOL_DOWN); true },
        )
    }
    val padOnly = if (deck == Deck.NUMPAD) (if (vm.settings.controlStyle == "trackpad") Deck.TRACKPAD else Deck.DIAL) else deck

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Column(Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top, as on the Apple TV Remote: mute · TV picker · power, inset from the screen edges.
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyButton(accent, "Mute", Modifier.size(52.dp), state = if (vm.muted) "Muted" else null, pressedScale = 1f, onPress = { vm.press(RemoteKey.MUTE) }) { c, _ ->
                LineIcon(if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) accent else c, 22.dp)
            }
            TvPicker(vm, Modifier.weight(1f), openDevices)
            KeyButton(accent, "Power", Modifier.size(52.dp), restColor = accent, pressedScale = 1f, onPress = {
                if (vm.active == null) openDevices() else askPower = true
            }) { c, _ ->
                LineIcon(Ico.POWER, c, 22.dp)
            }
        }

        when (layout) {
            HomeLayout.CARDS -> {
                val cards = androidx.compose.foundation.pager.rememberPagerState { 3 }
                androidx.compose.foundation.pager.HorizontalPager(
                    cards, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).then(a11y),
                    pageSpacing = 12.dp, beyondViewportPageCount = 1,
                ) { p ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (p) {
                            0 -> padArea(padOnly, Modifier.fillMaxSize())
                            1 -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(M.Container), contentAlignment = Alignment.Center) { VolChCard(vm, accent) }
                            else -> BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(M.Container).padding(18.dp), contentAlignment = Alignment.Center) {
                                val s = minOf(maxWidth, maxHeight, 340.dp)
                                CompositionLocalProvider(LocalK provides s.value / 780f) { Numpad(vm, accent, Modifier.size(s)) }
                            }
                        }
                    }
                }
                CardDots(3, cards.currentPage) { scope.launch { cards.animateScrollToPage(it) } }
                Spacer(Modifier.height(6.dp))
                FourKeys(vm, accent, openKeys)
            }

            HomeLayout.STRIP -> {
                ringArea(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp).then(a11y))
                Box(Modifier.padding(top = 10.dp)) { KeyRows(vm, accent, numpad, onNumpad, openKeys) }
            }

        }
      }
        Spacer(Modifier.height(NAV_H + 28.dp))
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
