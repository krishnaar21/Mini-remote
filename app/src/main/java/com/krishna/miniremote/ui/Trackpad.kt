package com.krishna.miniremote.ui

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
) {
    val density = LocalDensity.current
    val stepPx = with(density) { stepDp.dp.toPx() }
    val slop = with(density) { 8.dp.toPx() }
    val isOffline by rememberUpdatedState(offline)
    val scope = rememberCoroutineScope()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var lit by remember { mutableStateOf<RemoteKey?>(null) }
    val corner = size * 0.12f

    Box(
        Modifier.size(size)
            .softFace(PAD, corner = corner)
            .drawWithContent {
                val u = this.size.width
                val gap = u / 11f
                val r = 1.3.dp.toPx()
                for (i in 1..10) for (j in 1..10) drawCircle(Color.White.copy(alpha = 0.09f), r, Offset(i * gap, j * gap))
                touch?.let { t ->
                    drawCircle(Brush.radialGradient(0f to Color.White.copy(0.13f), 0.7f to Color.Transparent, center = t, radius = u * 0.3f), u * 0.3f, t)
                }
                drawContent()
            }
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (isOffline) { waitForUpOrCancellation(); onOffline(); return@awaitEachGesture }
                    touch = down.position
                    var acc = Offset.Zero
                    var moved = false
                    var held = false
                    val hold = scope.launch { delay(450); if (!moved) { held = true; onVoice() } }
                    while (true) {
                        val ev = awaitPointerEvent()
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
                    if (!moved && !held) onKey(RemoteKey.OK)
                    touch = null; lit = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (offline) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineIcon(Ico.TV, Color(0xFF6F6F75), 40.dp, stroke = 1.5f)
                Text("No TV connected", color = Color(0xFF6F6F75), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to choose a TV", color = Color(0xFF55555B), fontSize = 12.sp)
            }
            return@Box
        }
        val e = size * 0.05f
        val cs = size * 0.09f
        fun col(k: RemoteKey) = if (lit == k) accent else Color(0xFF5A5A60)
        LineIcon(Ico.UP, col(RemoteKey.UP), cs, Modifier.align(Alignment.TopCenter).padding(top = e), stroke = 2.2f)
        LineIcon(Ico.DOWN, col(RemoteKey.DOWN), cs, Modifier.align(Alignment.BottomCenter).padding(bottom = e), stroke = 2.2f)
        LineIcon(Ico.LEFT, col(RemoteKey.LEFT), cs, Modifier.align(Alignment.CenterStart).padding(start = e), stroke = 2.2f)
        LineIcon(Ico.RIGHT, col(RemoteKey.RIGHT), cs, Modifier.align(Alignment.CenterEnd).padding(end = e), stroke = 2.2f)
        if (touch == null) Text("Tap for OK · slide to move", color = Color(0xFF5A5A60), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.16f))

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

