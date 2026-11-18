package com.krishna.miniremote.ui

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Square trackpad with a dot grid. Tap = OK, hold = voice, slide = arrow steps in any
 * direction (one step per [stepDp] of travel, like the dial's scrub). Edge chevrons light up
 * in the accent colour with each step. TalkBack gets invisible Up/Left/OK/Right/Down buttons.
 */
@Composable
fun Trackpad(
    size: Dp,
    accent: Color,
    stepDp: Float,
    offline: Boolean,
    onKey: (RemoteKey) -> Unit,
    onTick: (RemoteKey) -> Unit,
    onVoice: () -> Unit,
    onOffline: () -> Unit,
    /** Taller than wide, like Apple's touch surface. */
    height: Dp = size,
    watch: Boolean = false,
    /** Two-finger slides: volume (up / down) and channel (sideways). */
    onTwoFinger: ((RemoteKey) -> Unit)? = null,
    /** Two fingers tapped (no slide); two of these quickly switch dial / trackpad. */
    onTwoFingerTap: () -> Unit = {},
    /** The finger lifted after a hold (voice ends). */
    onVoiceEnd: () -> Unit = {},
) {
    val twoStepPx = with(LocalDensity.current) { 30.dp.toPx() }
    val twoCb = androidx.compose.runtime.rememberUpdatedState(onTwoFinger)
    val twoTapCb = androidx.compose.runtime.rememberUpdatedState(onTwoFingerTap)
    var twoMode by remember { mutableStateOf(false) }
    var twoKey by remember { mutableStateOf<RemoteKey?>(null) }
    val density = LocalDensity.current
    val stepPx = with(density) { stepDp.dp.toPx() }
    val slop = with(density) { 8.dp.toPx() }
    val isOffline by rememberUpdatedState(offline)
    val scope = rememberCoroutineScope()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var lit by remember { mutableStateOf<RemoteKey?>(null) }
    val corner = 28.dp

    // The dots answer the finger: brighter, bigger and drawn a little towards it nearby; faint elsewhere.
    var lastTouch by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(touch) { touch?.let { lastTouch = it } }
    val glow by androidx.compose.animation.core.animateFloatAsState(
        if (touch != null) 1f else 0f, androidx.compose.animation.core.tween(if (touch != null) 120 else 500), label = "dots")
    Box(
        Modifier.size(size, height)
            .softFace(PAD, corner = corner)
            .drawWithContent {
                val u = this.size.width
                val gap = u / 11f
                val r = 1.3.dp.toPx()
                val rows = (this.size.height / gap).toInt()
                val t = touch ?: lastTouch
                val reach = u * 0.36f
                for (i in 1..10) for (j in 1 until rows) {
                    val p = Offset(i * gap, j * gap)
                    var f = 0f
                    if (t != null && glow > 0f) { val d = (p - t).getDistance(); f = (1f - d / reach).coerceIn(0f, 1f); f *= f * glow }
                    drawCircle(T.Ink.copy(alpha = 0.09f + 0.55f * f), r * (1f + 0.9f * f), p + (t?.let { (it - p) * (0.14f * f) } ?: Offset.Zero))
                }
                if (t != null && glow > 0f) {
                    drawCircle(Brush.radialGradient(0f to T.Ink.copy(0.10f * glow), 0.7f to Color.Transparent, center = t, radius = u * 0.3f), u * 0.3f, t)
                }
                drawContent()
            }
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (isOffline) {
                        // Only a real tap opens the TV list; a swipe (e.g. to the next page) doesn't.
                        if (tappedOnly(down.id, slop)) onOffline()
                        return@awaitEachGesture
                    }
                    touch = down.position
                    var acc = Offset.Zero
                    var moved = false
                    var held = false
                    val hold = scope.launch { delay(450); if (!moved) { held = true; onVoice() } }
                    val two = twoCb.value?.let { cb -> TwoFinger(twoStepPx) { k -> twoKey = k; cb(k) } }
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (two != null && two.feed(ev)) {
                            twoMode = true
                            if (!moved) { moved = true; hold.cancel() }
                            if (ev.changes.none { it.pressed }) break
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        acc += ch.position - ch.previousPosition
                        touch = ch.position
                        if (!moved && acc.getDistance() > slop) { moved = true; hold.cancel() }
                        if (moved && !held) {
                            if (abs(acc.x) >= stepPx && abs(acc.x) >= abs(acc.y)) {
                                val k = if (acc.x > 0) RemoteKey.RIGHT else RemoteKey.LEFT
                                onTick(k); lit = k
                                acc = Offset(acc.x - sign(acc.x) * stepPx, acc.y * 0.5f)
                            } else if (abs(acc.y) >= stepPx) {
                                val k = if (acc.y > 0) RemoteKey.DOWN else RemoteKey.UP
                                onTick(k); lit = k
                                acc = Offset(acc.x * 0.5f, acc.y - sign(acc.y) * stepPx)
                            }
                        }
                        ch.consume()
                    }
                    hold.cancel()
                    if (two?.tapped == true) twoTapCb.value()
                    if (held) onVoiceEnd()
                    if (!moved && !held) onKey(RemoteKey.OK)
                    touch = null; lit = null; twoMode = false; twoKey = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (offline) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineIcon(Ico.TV, T.Dim, 40.dp, stroke = 1.5f)
                Text("No TV connected", color = T.Dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to choose a TV", color = T.Sub, fontSize = 12.sp)
            }
            return@Box
        }
        // Two fingers: arrows and hint give way to the direction being slid.
        if (twoMode) {
            TwoFingerHint(twoKey, accent, 56.dp, 16.sp)
            return@Box
        }
        val e = size * 0.05f
        val cs = size * 0.09f
        // A clean surface at rest. While a finger is down, each arrow fades in as the finger
        // nears its edge, and lights up in the accent colour when a step goes out that way.
        val wPx = with(density) { size.toPx() }
        val hPx = with(density) { height.toPx() }
        fun near(k: RemoteKey): Float {
            if (lit == k) return 1f
            val t = touch ?: return 0f
            val dist = when (k) { RemoteKey.UP -> t.y; RemoteKey.DOWN -> hPx - t.y; RemoteKey.LEFT -> t.x; else -> wPx - t.x }
            return (1f - dist / (minOf(wPx, hPx) * 0.5f)).coerceIn(0f, 1f) * 0.85f
        }
        fun col(k: RemoteKey) = if (lit == k) T.Ink else T.Dim
        @Composable fun arrow(k: RemoteKey, ico: Ico, m: Modifier) {
            val a = near(k)
            if (a > 0.02f) LineIcon(ico, col(k), cs, m.graphicsLayer { alpha = a }, stroke = 2.2f)
        }
        arrow(RemoteKey.UP, if (watch) Ico.PLUS else Ico.UP, Modifier.align(Alignment.TopCenter).padding(top = e))
        arrow(RemoteKey.DOWN, if (watch) Ico.MINUS else Ico.DOWN, Modifier.align(Alignment.BottomCenter).padding(bottom = e))
        arrow(RemoteKey.LEFT, Ico.LEFT, Modifier.align(Alignment.CenterStart).padding(start = e))
        arrow(RemoteKey.RIGHT, Ico.RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = e))

        val zone = size * 0.28f
        PadZone("Up", Modifier.align(Alignment.TopCenter).size(zone)) { onKey(RemoteKey.UP) }
        PadZone("Left", Modifier.align(Alignment.CenterStart).size(zone)) { onKey(RemoteKey.LEFT) }
        PadZone("OK", Modifier.align(Alignment.Center).size(zone), longLabel = "voice search", onLong = onVoice) { onKey(RemoteKey.OK) }
        PadZone("Right", Modifier.align(Alignment.CenterEnd).size(zone)) { onKey(RemoteKey.RIGHT) }
        PadZone("Down", Modifier.align(Alignment.BottomCenter).size(zone)) { onKey(RemoteKey.DOWN) }
    }
}

/** 1–9, then channel list · 0 · OK. Digits go straight to the TV (live TV tunes after a moment). */
@Composable
fun Numpad(vm: RemoteVM, accent: Color, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789", "L0K")
    Column(modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                r.forEach { c ->
                    val m = Modifier.weight(1f).fillMaxHeight()
                    when (c) {
                        'L' -> KeyButton(accent, "Channel list", m, onPress = { vm.press(RemoteKey.CH_LIST) }) { col, _ -> LineIcon(Ico.LIST, col, 20.dp) }
                        'K' -> KeyButton(accent, "OK", m, onPress = { vm.press(RemoteKey.OK) }) { col, _ ->
                            Text("OK", color = col, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        }
                        else -> KeyButton(accent, c.toString(), m, onPress = { RemoteKey.forDigit(c)?.let { vm.press(it) } }) { col, _ ->
                            Text(c.toString(), color = col, fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

