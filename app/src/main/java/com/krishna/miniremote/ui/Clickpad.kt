package com.krishna.miniremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.padding
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
    /** Icon shown above [toast] in the well (the key that was pressed), if any. */
    toastIcon: Ico? = null,
    onKey: (RemoteKey) -> Unit,
    onTick: (RemoteKey) -> Unit,
    onVoice: () -> Unit,
    /** No TV connected: the pad dims, shows a hint, and any tap calls [onOffline]. */
    offline: Boolean = false,
    onOffline: () -> Unit = {},
) {
    val isOffline = androidx.compose.runtime.rememberUpdatedState(offline)
    val density = LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val stepPx = with(density) { stepDp.dp.toPx() }
    val deadPx = with(density) { 8.dp.toPx() }
    var litDir by remember { mutableStateOf<RemoteKey?>(null) }
    var wellDown by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    // Where the finger is (for the soft glow) and the ripple, both in pad pixels.
    var touch by remember { mutableStateOf<Offset?>(null) }
    var ripAt by remember { mutableStateOf(Offset.Zero) }
    var ripD by remember { mutableStateOf(0f) }
    val rip = remember { androidx.compose.animation.core.Animatable(1f) }
    val wellScale by androidx.compose.animation.core.animateFloatAsState(
        if (wellDown) 0.97f else 1f,
        if (wellDown) androidx.compose.animation.core.tween(80) else androidx.compose.animation.core.spring(0.42f, 700f), label = "well")

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
                    val wellR = this.size.width * WELL_FRAC / 2f
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
                    touch = down.position
                    // Ripple: on the well for OK, on the arrow for an edge, like the design.
                    val w = this.size.width.toFloat()
                    val inset = w * (52f + 38f) / 780f
                    ripAt = if (inWell) c else when (zoneKey) {
                        RemoteKey.UP -> Offset(c.x, inset); RemoteKey.DOWN -> Offset(c.x, w - inset)
                        RemoteKey.LEFT -> Offset(inset, c.y); else -> Offset(w - inset, c.y)
                    }
                    ripD = w * (if (inWell) 0.5f else 0.42f)
                    scope.launch { rip.snapTo(0f); rip.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, .7f, .3f, 1f))) }

                    val hold: Job = scope.launch {
                        delay(350)
                        if (moved) return@launch
                        holdFired = true
                        if (inWell) { listening = true; onVoice() } else {
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
                        touch = ch.position
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
                    litDir = null; wellDown = false; listening = false; touch = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Raised ring (its face and shadows), the finger glow and the ripple.
        Box(Modifier.size(size).softFace(PAD).drawWithContent {
            drawContent()
            if (offline) return@drawWithContent
            insideStadium {
                touch?.let { t ->
                    if (!wellDown) drawCircle(Brush.radialGradient(
                        0f to Color.White.copy(0.10f), 0.7f to Color.Transparent, center = t, radius = size.width * 260f / 780f),
                        size.width * 260f / 780f, t)
                }
                val v = rip.value
                if (v < 1f) {
                    val r = ripD / 2f * (0.1f + 0.9f * v)
                    drawCircle(Brush.radialGradient(
                        0f to Color.White.copy(0.20f * (1f - v)), 0.55f to Color.White.copy(0.07f * (1f - v)), 0.7f to Color.Transparent,
                        center = ripAt, radius = r), r, ripAt)
                }
            }
        })
        if (!offline) Canvas(Modifier.size(size)) {
            // .pchev: 76px chevrons, 52px in from the edge, stroke 2.3, lit in the accent colour.
            val u = this.size.width / 780f
            val cs = 76f * u
            val e = 52f * u
            fun chev(dir: RemoteKey, ico: Ico, x: Float, y: Float) {
                val col = if (litDir == dir) accent else T.Dim
                translate(x, y) {
                    scale(cs / 24f, cs / 24f, pivot = Offset.Zero) {
                        drawPath(ico.path, col, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            val mid = (this.size.width - cs) / 2f
            chev(RemoteKey.UP, Ico.UP, mid, e)
            chev(RemoteKey.DOWN, Ico.DOWN, mid, this.size.height - e - cs)
            chev(RemoteKey.LEFT, Ico.LEFT, e, mid)
            chev(RemoteKey.RIGHT, Ico.RIGHT, this.size.width - e - cs, mid)
        }
        if (offline) {
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "No TV connected"
                    onClick(label = "choose a TV") { onOffline(); true }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(bdp(22f)),
            ) {
                LineIcon(Ico.TV, Color(0xFF6F6F75), bdp(110f), stroke = 1.5f)
                Text("No TV connected", color = Color(0xFF6F6F75), fontSize = bsp(36f), fontWeight = FontWeight.SemiBold)
                Text("Tap to choose a TV", color = Color(0xFF55555B), fontSize = bsp(30f))
            }
            return@Box
        }
        // .well: dished centre, 300 of the pad's 780. Sinks (scale .97) and darkens while pressed;
        // tinted with the accent while holding for voice.
        val wellFace = when {
            listening -> flat(mix(accent, 0.22f, Color(0xFF1A1A1C)), WELL.shadows)
            wellDown -> WELL_DOWN
            else -> WELL
        }
        Box(
            Modifier.size(size * WELL_FRAC).graphicsLayer { scaleX = wellScale; scaleY = wellScale }.softFace(wellFace, sheen = false),
            contentAlignment = Alignment.Center,
        ) {
            if (toast == null) {
                Text("OK", Modifier.clearAndSetSemantics {}, color = if (wellDown) accent else Color(0xFF55555B),
                    fontSize = bsp(40f), fontWeight = FontWeight.SemiBold, letterSpacing = bsp(40f * 0.06f))
            } else {
                // .toast: accent icon (84px) over the key name, 500 28px #d4d4d9; longer messages wrap.
                Column(
                    Modifier.padding(horizontal = bdp(26f)).clearAndSetSemantics {
                        contentDescription = toast; liveRegion = LiveRegionMode.Polite
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(bdp(8f)),
                ) {
                    if (toastIcon != null) LineIcon(toastIcon, accent, bdp(84f), stroke = 1.8f)
                    Text(toast, color = Color(0xFFD4D4D9), fontSize = bsp(28f), fontWeight = FontWeight.Medium,
                        lineHeight = bsp(34f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        val zone = size * 0.28f
        PadZone("Up", Modifier.align(Alignment.TopCenter).size(zone)) { onKey(RemoteKey.UP) }
        PadZone("Left", Modifier.align(Alignment.CenterStart).size(zone)) { onKey(RemoteKey.LEFT) }
        PadZone("OK", Modifier.align(Alignment.Center).size(size * WELL_FRAC), longLabel = "voice search", onLong = onVoice) {
            onKey(RemoteKey.OK)
        }
        PadZone("Right", Modifier.align(Alignment.CenterEnd).size(zone)) { onKey(RemoteKey.RIGHT) }
        PadZone("Down", Modifier.align(Alignment.BottomCenter).size(zone)) { onKey(RemoteKey.DOWN) }
    }
}

/** Well diameter as a share of the pad (design: 300 of 780). */
private const val WELL_FRAC = 300f / 780f

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
