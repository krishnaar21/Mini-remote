package com.krishna.miniremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlin.math.abs
import kotlin.math.atan2

/** The Home layouts you can pick in Settings. */
enum class HomeLayout(val key: String, val label: String, val about: String) {
    CLASSIC("classic", "Swipe", "Swipe up under the keys for volume and channels."),
    RING("ring", "Ring", "Turn your finger around the ring for volume."),
    CARDS("cards", "Cards", "Swipe the dots for the volume, channel and number cards."),
    STRIP("strip", "Black", "Black screen, volume ring, two rows of keys."),
    SLIDERS("sliders", "Sliders", "Drag along the volume and channel bars.");
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: RING }
}

/** Width of the volume ring around the dial. */
val RING_BAND = 26.dp
/** Degrees of turn per volume step. */
private const val RING_STEP = 18f

/**
 * The dial with a volume ring around it, like an iPod wheel: turn clockwise for louder.
 * While turning, an accent arc follows the finger. Touches inside the dial go to [content].
 */
@Composable
fun RingDial(dial: Dp, accent: Color, onStep: (up: Boolean) -> Unit, content: @Composable () -> Unit) {
    val step by rememberUpdatedState(onStep)
    var start by remember { mutableFloatStateOf(0f) }
    var sweep by remember { mutableFloatStateOf(0f) }
    var turning by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (turning) 1f else 0f, tween(if (turning) 90 else 600), label = "ring")
    Box(
        Modifier.size(dial + RING_BAND * 2)
            .semantics {
                contentDescription = "Volume ring"
                customActions = listOf(
                    CustomAccessibilityAction("Volume up") { step(true); true },
                    CustomAccessibilityAction("Volume down") { step(false); true },
                )
            }
            .pointerInput(dial) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    if ((down.position - c).getDistance() < dial.toPx() / 2f) return@awaitEachGesture
                    down.consume()
                    fun ang(p: Offset) = Math.toDegrees(atan2((p - c).y, (p - c).x).toDouble()).toFloat()
                    var last = ang(down.position)
                    var acc = 0f
                    start = last; sweep = 0f; turning = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val a = ang(ch.position)
                        var d = a - last
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        last = a; acc += d; sweep = (sweep + d).coerceIn(-350f, 350f)
                        while (acc >= RING_STEP) { acc -= RING_STEP; step(true) }
                        while (acc <= -RING_STEP) { acc += RING_STEP; step(false) }
                        ch.consume()
                    }
                    turning = false
                }
            }
            .drawBehind {
                val r = dial.toPx() / 2f + RING_BAND.toPx() / 2f
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(M.Highest.copy(alpha = 0.55f), r, style = Stroke(5.dp.toPx()))
                if (glow > 0f && sweep != 0f) drawArc(accent.copy(alpha = glow), start, sweep, false, tl, sz,
                    style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A round key that fires on release (so it can sit in a scrolling strip). Icon or a short word, with an optional caption. */
@Composable
fun StripKey(accent: Color, label: String, size: Dp, active: Boolean = false, ico: Ico? = null, word: String? = null, caption: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (active) accent.copy(alpha = 0.25f) else Color(0xFF1C1C1E))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val c = if (active) accent else T.Ink
            if (ico != null) LineIcon(ico, c, if (caption != null) 18.dp else 21.dp)
            if (word != null) Text(word, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (caption != null) Text(caption, color = T.Dim, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Two rows of round keys: Back, Home, Play, Search; then channel down / up, numbers and input. Volume is the ring; mute is at the top. */
@Composable
fun KeyRows(vm: RemoteVM, accent: Color, numpad: Boolean, onNumpad: () -> Unit, openKeys: () -> Unit) {
    val k = 60.dp
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StripKey(accent, "Back", k, ico = Ico.CHEVRON_L) { vm.press(RemoteKey.BACK) }
            StripKey(accent, "Home", k, ico = Ico.TV) { vm.press(RemoteKey.HOME) }
            StripKey(accent, if (vm.playing) "Pause" else "Play", k, ico = if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY) { vm.press(RemoteKey.PLAY_PAUSE) }
            StripKey(accent, "Search", k, ico = Ico.SEARCH, onClick = openKeys)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StripKey(accent, "Channel down", k, ico = Ico.MINUS, caption = "CH") { vm.press(RemoteKey.CH_DOWN) }
            StripKey(accent, "Channel up", k, ico = Ico.PLUS, caption = "CH") { vm.press(RemoteKey.CH_UP) }
            StripKey(accent, "Number pad", k, active = numpad, word = "123", onClick = onNumpad)
            StripKey(accent, "Input", k, ico = Ico.INPUT) { vm.press(RemoteKey.INPUT) }
        }
    }
}

/**
 * A wide bar you drag along: each [stepDp] is one step (right = up). A tap on the left or
 * right half is one step down or up. Shows how many steps you've moved while dragging.
 */
@Composable
fun DragBar(label: String, accent: Color, modifier: Modifier = Modifier, upLabel: String, downLabel: String, onStep: (up: Boolean) -> Unit) {
    val step by rememberUpdatedState(onStep)
    var x by remember { mutableStateOf<Float?>(null) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(0) }
    val fill by animateFloatAsState(if (x != null) 1f else 0f, tween(if (x != null) 80 else 500), label = "bar")
    LaunchedEffect(x, count) { if (x == null && count != 0) { kotlinx.coroutines.delay(1200); count = 0 } }
    Box(
        modifier.height(56.dp).clip(CircleShape).background(M.High)
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(upLabel) { step(true); true },
                    CustomAccessibilityAction(downLabel) { step(false); true },
                )
            }
            .pointerInput(Unit) {
                val stepPx = 22.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    x = down.position.x; lastX = down.position.x; count = 0
                    var acc = 0f
                    var last = down.position.x
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val p = ch.position.x
                        acc += p - last; last = p
                        if (abs(p - down.position.x) > viewConfiguration.touchSlop) moved = true
                        while (acc >= stepPx) { acc -= stepPx; count++; step(true) }
                        while (acc <= -stepPx) { acc += stepPx; count--; step(false) }
                        x = p.coerceIn(0f, size.width.toFloat()); lastX = x!!
                        ch.consume()
                    }
                    if (!moved) { val up = down.position.x > size.width / 2f; count += if (up) 1 else -1; step(up) }
                    x = null
                }
            }
            .drawBehind { if (fill > 0f) drawRect(M.Highest.copy(alpha = fill), size = Size(lastX, size.height)) },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = T.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (count != 0) Text(if (count > 0) "+$count" else "−${-count}", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            else Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Ico.LEFT, T.Dim, 14.dp, stroke = 2.2f); LineIcon(Ico.RIGHT, T.Dim, 14.dp, stroke = 2.2f)
            }
        }
    }
}

/** Small dots for the cards: tap one, or swipe across them. */
@Composable
fun CardDots(n: Int, page: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.height(36.dp).pointerInput(page) {
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                if (total < -16.dp.toPx()) onPick((page + 1).coerceAtMost(n - 1)) else if (total > 16.dp.toPx()) onPick((page - 1).coerceAtLeast(0))
            }) { c, dx -> c.consume(); total += dx }
        }.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val names = listOf("Remote", "Volume and channels", "Numbers")
        repeat(n) { i ->
            val sel = i == page
            Box(
                Modifier.height(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(i) }
                    .semantics { contentDescription = names.getOrElse(i) { "Card ${i + 1}" } }.padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(if (sel) 14.dp else 5.dp, 5.dp).clip(CircleShape).background(if (sel) T.Dim else Color(0xFF5A5A60))) }
        }
    }
}

/** The volume / channel card: two tall rockers and mute. */
@Composable
fun VolChCard(vm: RemoteVM, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            VerticalRocker(accent, "VOL", "Volume up", "Volume down", Ico.PLUS, Ico.MINUS, Modifier.width(86.dp).height(240.dp),
                onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) }, onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) })
            VerticalRocker(accent, "CH", "Channel up", "Channel down", Ico.PLUS, Ico.MINUS, Modifier.width(86.dp).height(240.dp),
                onUp = { vm.press(RemoteKey.CH_UP) }, onDown = { vm.press(RemoteKey.CH_DOWN) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KeyButton(accent, "Mute", Modifier.size(56.dp), state = if (vm.muted) "Muted" else null, pressedScale = 1f, onPress = { vm.press(RemoteKey.MUTE) }) { c, _ ->
                LineIcon(if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) accent else c, 20.dp)
            }
            KeyButton(accent, "Input", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.INPUT) }) { c, _ -> LineIcon(Ico.INPUT, c, 20.dp) }
            KeyButton(accent, "Guide", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.GUIDE) }) { c, _ -> LineIcon(Ico.GUIDE, c, 20.dp) }
        }
    }
}

/** Back, Home, Play / pause and Search. */
@Composable
fun FourKeys(vm: RemoteVM, accent: Color, openKeys: () -> Unit, size: Dp = 62.dp, gap: Dp = 20.dp) {
    val k = Modifier.size(size)
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        KeyButton(accent, "Back", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.CHEVRON_L, c, 24.dp, stroke = 2.2f) }
        KeyButton(accent, "Home", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.TV, c, 22.dp) }
        KeyButton(accent, if (vm.playing) "Pause" else "Play", k, pressedScale = 1f, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ ->
            LineIcon(if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, c, if (vm.playing) 22.dp else 20.dp, stroke = if (vm.playing) 2.4f else 1.8f)
        }
        KeyButton(accent, "Search", k, actionLabel = "search or type on TV", pressedScale = 1f, onPress = openKeys) { c, _ -> LineIcon(Ico.SEARCH, c, 21.dp) }
    }
}

/** Settings: which Home layout to use. */
@Composable
internal fun LayoutPicker(value: String, accent: Color, onPick: (String) -> Unit) {
    val cur = HomeLayout.of(value)
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 2.dp)) {
        Text("Layout", color = T.Ink, fontSize = 15.sp)
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeLayout.entries.forEach { l ->
                val sel = l == cur
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(if (sel) accent else M.Highest)
                        .selectable(sel, role = Role.RadioButton) { onPick(l.key) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(l.label, color = if (sel) Color.White else T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Text(cur.about, color = T.Dim, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp))
    }
    Divider()
}

