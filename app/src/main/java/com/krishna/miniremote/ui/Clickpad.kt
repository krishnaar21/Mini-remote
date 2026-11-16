package com.krishna.miniremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Round clickpad. Where the finger lands picks the behaviour:
 *  - edge zone: tap = that arrow, hold = repeat, drag along that axis = scrub
 *  - centre well: tap = OK, hold = voice, drag = free scrub on the dominant axis
 *
 * Gestures can't be done with TalkBack on, so invisible Up/Left/OK/Right/Down buttons sit
 * over each zone. They carry semantics only (no pointer input), so finger gestures still
 * reach the pad underneath.
 */
@Composable
fun Clickpad(
    size: Dp,
    accent: Color,
    stepDp: Float,
    toast: String?,
    onKey: (RemoteKey) -> Unit,
    onTick: (RemoteKey) -> Unit,
    onVoice: () -> Unit,
    /** No TV connected: the pad dims, shows a hint, and any tap calls [onOffline]. */
    offline: Boolean = false,
    onOffline: () -> Unit = {},
) {
    val isOffline = androidx.compose.runtime.rememberUpdatedState(offline)
    val tilt = LocalTilt.current
    val density = LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val stepPx = with(density) { stepDp.dp.toPx() }
    val deadPx = with(density) { 8.dp.toPx() }
    var litDir by remember { mutableStateOf<RemoteKey?>(null) }
    var wellDown by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(size)
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isOffline.value) {
                        do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                        onOffline()
                        return@awaitEachGesture
                    }
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val wellR = this.size.width * 0.26f
                    val rel = down.position - c
                    val inWell = rel.getDistance() < wellR
                    val edgeHorizontal = abs(rel.x) > abs(rel.y)
                    val zoneKey = if (edgeHorizontal) (if (rel.x > 0) RemoteKey.RIGHT else RemoteKey.LEFT)
                    else (if (rel.y > 0) RemoteKey.DOWN else RemoteKey.UP)

                    var horizontal: Boolean? = if (inWell) null else edgeHorizontal
                    var acc = Offset.Zero
                    var moved = false
                    var holdFired = false
                    if (inWell) wellDown = true else litDir = zoneKey

                    val hold: Job = scope.launch {
                        delay(350)
                        if (moved) return@launch
                        holdFired = true
                        if (inWell) onVoice() else {
                            var d = 150L
                            while (true) { onTick(zoneKey); delay(d); d = maxOf(70L, d - 10) }
                        }
                    }

                    var last = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val delta = ch.position - last
                        last = ch.position
                        acc += delta
                        if (horizontal == null && acc.getDistance() > deadPx) {
                            horizontal = abs(acc.x) > abs(acc.y)
                            hold.cancel()
                            moved = true
                        }
                        val h = horizontal
                        if (h != null && !holdFired) {
                            val v = if (h) acc.x else acc.y
                            if (abs(v) >= stepPx) {
                                val key = if (h) (if (v > 0) RemoteKey.RIGHT else RemoteKey.LEFT)
                                else (if (v > 0) RemoteKey.DOWN else RemoteKey.UP)
                                moved = true
                                hold.cancel()
                                onTick(key)
                                litDir = key
                                if (h) acc = Offset(acc.x - (if (v > 0) stepPx else -stepPx), 0f)
                                else acc = Offset(0f, acc.y - (if (v > 0) stepPx else -stepPx))
                            }
                        }
                        ch.consume()
                    }
                    hold.cancel()
                    if (!moved && !holdFired) onKey(if (inWell) RemoteKey.OK else zoneKey)
                    litDir = null; wellDown = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2f
            val c = center
            // raised ring
            drawCircle(Color.Black.copy(0.55f), r + 2f, c.copy(y = c.y + 10f))
            drawCircle(Brush.linearGradient(listOf(Color(0xFF3D3D40), Color(0xFF2A2A2D), Color(0xFF202023)),
                Offset(0f, 0f), Offset(this.size.width, this.size.height)), r, c)
            drawCircle(Color.White.copy(0.10f), r - 1f, c, style = Stroke(2f))
            drawCircle(Color.Black.copy(0.6f), r, c, style = Stroke(2f))
            // gyro sheen on the raised ring
            val t = tilt.value
            val hc = Offset(this.size.width * (0.5f + t.x * 0.34f), this.size.height * (0.08f + t.y * 0.12f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.10f), Color.Transparent), hc, this.size.width * 0.55f), r, c)
            if (offline) return@Canvas
            // concave well
            val wr = this.size.width * 0.26f
            drawCircle(Brush.verticalGradient(
                listOf(if (wellDown) Color(0xFF0A0A0B) else T.Well, Color(0xFF262629)), c.y - wr, c.y + wr), wr, c)
            drawCircle(Color.Black.copy(0.8f), wr, c, style = Stroke(3f))
            if (wellDown) drawCircle(accent.copy(0.16f), wr, c)
            // arrows
            val off = r * 0.72f
            val len = r * 0.07f
            fun chev(dir: RemoteKey, p: Offset, a: Offset, b: Offset) {
                val col = if (litDir == dir) accent else Color(0xFF86868C)
                val st = Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p.x + a.x, p.y + a.y); lineTo(p.x, p.y); lineTo(p.x + b.x, p.y + b.y)
                }
                drawPath(path, col, style = st)
            }
            chev(RemoteKey.UP, Offset(c.x, c.y - off), Offset(-len * 1.6f, len), Offset(len * 1.6f, len))
            chev(RemoteKey.DOWN, Offset(c.x, c.y + off), Offset(-len * 1.6f, -len), Offset(len * 1.6f, -len))
            chev(RemoteKey.LEFT, Offset(c.x - off, c.y), Offset(len, -len * 1.6f), Offset(len, len * 1.6f))
            chev(RemoteKey.RIGHT, Offset(c.x + off, c.y), Offset(-len, -len * 1.6f), Offset(-len, len * 1.6f))
        }
        if (offline) {
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "No TV connected"
                    onClick(label = "choose a TV") { onOffline(); true }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Ico.TV, Color(0xFF6F6F75), 40.dp)
                Text("No TV connected", color = Color(0xFF6F6F75), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to choose a TV", color = Color(0xFF55555B), fontSize = 13.sp)
            }
            return@Box
        }
        Text(
            toast ?: "OK",
            // Announce toasts ("Lost connection", "Choose a TV first") without making "OK" a stop.
            modifier = Modifier.widthIn(max = size * 0.62f).clearAndSetSemantics {
                if (toast != null) { contentDescription = toast; liveRegion = LiveRegionMode.Polite }
            },
            color = if (toast != null) T.Ink else Color(0xFF55555B),
            fontSize = if (toast != null) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        val zone = size * 0.28f
        PadZone("Up", Modifier.align(Alignment.TopCenter).size(zone)) { onKey(RemoteKey.UP) }
        PadZone("Left", Modifier.align(Alignment.CenterStart).size(zone)) { onKey(RemoteKey.LEFT) }
        PadZone("OK", Modifier.align(Alignment.Center).size(size * 0.52f), longLabel = "voice search", onLong = onVoice) {
            onKey(RemoteKey.OK)
        }
        PadZone("Right", Modifier.align(Alignment.CenterEnd).size(zone)) { onKey(RemoteKey.RIGHT) }
        PadZone("Down", Modifier.align(Alignment.BottomCenter).size(zone)) { onKey(RemoteKey.DOWN) }
    }
}

/** Accessibility-only button over a clickpad zone. Draws nothing and takes no touches. */
@Composable
private fun PadZone(
    label: String,
    modifier: Modifier,
    longLabel: String? = null,
    onLong: (() -> Unit)? = null,
    onTap: () -> Unit,
) {
    Box(modifier.semantics {
        role = Role.Button
        contentDescription = label
        onClick { onTap(); true }
        if (onLong != null) onLongClick(label = longLabel) { onLong(); true }
    })
}
