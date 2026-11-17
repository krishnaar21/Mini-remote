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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

/** What fills the middle of Home. Swipe sideways, or tap the dots, to change. */
enum class Deck(val label: String, val ico: Ico) {
    DIAL("Dial", Ico.DIAL), TRACKPAD("Trackpad", Ico.TRACKPAD), NUMPAD("Number pad", Ico.NUMPAD)
}

/** How long OK waits for a second tap (double tap = mute). */
private const val DOUBLE_TAP_MS = 260L

/**
 * Minimal Home ("Edges"): greeting, search and power on top; the dial in the middle with
 * channels on a slider along the left edge and volume along the right; Back · Home · Play below.
 * OK: tap = OK, double tap = mute, hold = voice search.
 */
@Composable
fun HomeScreen(
    vm: RemoteVM,
    accent: Color,
    deck: Deck,
    onDeck: (Deck) -> Unit,
    openDevices: () -> Unit,
    openKeys: () -> Unit,
) {
    val current by rememberUpdatedState(deck)
    val scope = rememberCoroutineScope()
    val pendingOk = remember { arrayOfNulls<Job>(1) }
    /** A second OK within [DOUBLE_TAP_MS] mutes instead; otherwise OK goes out after that wait. */
    fun okTap() {
        val waiting = pendingOk[0]
        if (waiting?.isActive == true) {
            waiting.cancel(); pendingOk[0] = null
            vm.press(RemoteKey.MUTE)
        } else {
            pendingOk[0] = scope.launch { delay(DOUBLE_TAP_MS); pendingOk[0] = null; vm.press(RemoteKey.OK, quiet = true) }
        }
    }
    val onKey: (RemoteKey) -> Unit = { if (it == RemoteKey.OK) okTap() else vm.press(it) }
    fun swipe(by: Int) {
        val all = Deck.entries
        onDeck(all[(all.indexOf(current) + by + all.size) % all.size])
        vm.feel.fire(Feel.DETENT)
    }
    val swipeable = Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 48.dp.toPx()) swipe(if (total < 0) 1 else -1) },
        ) { change, dx -> change.consume(); total += dx }
    }
    val offline = vm.active == null || vm.link == Link.ERROR

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = NAV_H + 28.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                val first = vm.settings.profileName.trim().substringBefore(' ')
                Text(if (first.isEmpty()) "Hello" else "Hello, $first", color = T.Ink, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvLine(vm, openDevices)
            }
            KeyButton(accent, "Search and type", Modifier.size(48.dp), actionLabel = "search or type on TV", pressedScale = 1f, onPress = openKeys) { c, _ ->
                LineIcon(Ico.SEARCH, c, 20.dp)
            }
            KeyButton(accent, "Power", Modifier.size(48.dp), restColor = accent, pressedScale = 1f, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 20.dp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(swipeable), contentAlignment = Alignment.Center) {
            val strip = 44.dp
            val pad: Dp = minOf(maxWidth - strip * 2 - 28.dp, maxHeight - 150.dp).coerceIn(160.dp, 320.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EdgeStrip(
                        "CH", accent, "Channel up", "Channel down", Modifier.width(strip).height(pad * 0.82f), stepDp = 44f,
                        onStep = { up, drag -> val k = if (up) RemoteKey.CH_UP else RemoteKey.CH_DOWN; if (drag) vm.scrubTick(k) else vm.press(k) },
                    )
                    Box(
                        Modifier.size(pad).semantics {
                            customActions = listOf(CustomAccessibilityAction("Mute") { vm.press(RemoteKey.MUTE); true })
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            deck,
                            transitionSpec = {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                            },
                            label = "deck",
                        ) { d ->
                            CompositionLocalProvider(LocalK provides pad.value / 780f) {
                                when (d) {
                                    Deck.NUMPAD -> Numpad(vm, accent, Modifier.size(pad))
                                    Deck.DIAL -> Clickpad(
                                        size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                                        toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                                        onKey = onKey, onTick = { vm.scrubTick(it) },
                                        onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                        offline = offline, onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                    )
                                    Deck.TRACKPAD -> Trackpad(
                                        size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                                        onKey = onKey, onTick = { vm.scrubTick(it) },
                                        onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                        onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                    )
                                }
                            }
                        }
                    }
                    EdgeStrip(
                        if (vm.muted) "MUTE" else "VOL", accent, "Volume up", "Volume down", Modifier.width(strip).height(pad * 0.82f), stepDp = 24f,
                        highlight = vm.muted,
                        onStep = { up, drag -> val k = if (up) RemoteKey.VOL_UP else RemoteKey.VOL_DOWN; if (drag) vm.scrubTick(k) else vm.press(k, quiet = true) },
                    )
                }
                DeckDots(deck, accent, Modifier.padding(top = 10.dp)) { onDeck(it); vm.feel.fire(Feel.TICK) }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    KeyButton(accent, "Back", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.BACK, c, 22.dp) }
                    KeyButton(accent, "Home", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, 22.dp) }
                    KeyButton(accent, "Play or pause", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, 21.dp) }
                }
            }
        }
    }
}

/**
 * A slim slider along the dial's edge. Slide up or down to step (one step per [stepDp]),
 * tap the top or bottom half for one step, hold a half to repeat. [onStep] gets
 * (up, fromDrag). TalkBack sees two plain buttons.
 */
@Composable
private fun EdgeStrip(
    word: String,
    accent: Color,
    upLabel: String,
    downLabel: String,
    modifier: Modifier,
    stepDp: Float,
    highlight: Boolean = false,
    onStep: (up: Boolean, drag: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { stepDp.dp.toPx() }
    val slop = with(density) { 6.dp.toPx() }
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var touchY by remember { mutableStateOf<Float?>(null) }
    var lit by remember { mutableStateOf<Boolean?>(null) }

    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(M.Container)
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val upper = down.position.y < size.height / 2f
                    touchY = down.position.y
                    var acc = 0f
                    var moved = false
                    var held = false
                    val hold = scope.launch {
                        delay(380)
                        if (moved) return@launch
                        held = true
                        var d = 160L
                        while (true) { step(upper, true); lit = upper; delay(d); d = maxOf(70L, d - 12) }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        acc += ch.position.y - ch.previousPosition.y
                        touchY = ch.position.y
                        if (!moved && !held && abs(acc) > slop) { moved = true; hold.cancel() }
                        if (moved) while (abs(acc) >= stepPx) {
                            val up = acc < 0
                            step(up, true); lit = up
                            acc += if (up) stepPx else -stepPx
                        }
                        ch.consume()
                    }
                    hold.cancel()
                    if (!moved && !held) { step(upper, false); lit = upper }
                    touchY = null; lit = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 14.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            LineIcon(Ico.PLUS, if (lit == true) accent else T.Ink, 18.dp)
            Text(word, color = if (highlight) accent else T.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            LineIcon(Ico.MINUS, if (lit == false) accent else T.Ink, 18.dp)
        }
        // A soft accent marker follows the finger.
        touchY?.let { y ->
            val half = with(density) { 20.dp.toPx() }
            Box(
                Modifier.align(Alignment.TopCenter)
                    .offset { IntOffset(0, (y - half).roundToInt()) }
                    .size(32.dp, 40.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.28f)),
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().semantics { role = Role.Button; contentDescription = upLabel; onClick { step(true, false); true } })
            Box(Modifier.weight(1f).fillMaxWidth().semantics { role = Role.Button; contentDescription = downLabel; onClick { step(false, false); true } })
        }
    }
}

/** "📺 Living room TV ▾" in green when connected; opens the TV list. */
@Composable
private fun TvLine(vm: RemoteVM, openDevices: () -> Unit) {
    val d = vm.active
    val on = d != null && vm.link == Link.READY
    val col = when {
        d == null -> T.Dim
        on -> Color(0xFF6DD58C)
        vm.link == Link.CONNECTING -> Color(0xFFFDD663)
        else -> Color(0xFFF2B8B5)
    }
    Row(
        Modifier.padding(top = 2.dp).clip(CircleShape)
            .clickable(onClickLabel = "choose a TV", role = Role.Button, onClick = openDevices)
            .semantics(mergeDescendants = true) {
                contentDescription = d?.let { "TV: ${it.name}" } ?: "Choose a TV"
                if (d != null) stateDescription = linkWord(vm.link)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Ico.TV, col, 15.dp)
        Text(d?.name ?: "Choose a TV", color = col, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (d != null && !on) Text("· ${linkWord(vm.link)}", color = T.Dim, fontSize = 12.5.sp, maxLines = 1)
        LineIcon(Ico.DOWN, T.Dim, 14.dp)
    }
}

/** Three quiet dots under the control: which one is showing. Tap one to jump to it. */
@Composable
private fun DeckDots(deck: Deck, accent: Color, modifier: Modifier, onPick: (Deck) -> Unit) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Deck.entries.forEach { d ->
            val sel = d == deck
            Box(
                Modifier.size(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(d) }
                    .semantics { contentDescription = d.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (sel) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (sel) accent else Color(0xFF4A4A50)))
            }
        }
    }
}
