package com.krishna.miniremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The design's ripple: white .16 from the centre, 1.6x the key, .55s, fading as it grows. */
private val RipEase = CubicBezierEasing(.2f, .7f, .3f, 1f)

private fun CoroutineScope.ripple(a: Animatable<Float, *>) = launch { a.snapTo(0f); a.animateTo(1f, tween(550, easing = RipEase)) }

private fun Modifier.rippleLayer(p: () -> Float) = drawWithContent {
    drawContent()
    val v = p()
    if (v < 1f) insideStadium {
        val r = size.maxDimension * 1.6f / 2f * (0.1f + 0.9f * v)
        drawCircle(Color.White.copy(alpha = 0.16f * (1f - v)), r, center)
    }
}

/** Press handling shared by every key: fires on touch-down, optional auto-repeat while held. */
private fun Modifier.pressInput(repeat: Boolean, scope: CoroutineScope, onDown: (Boolean) -> Unit, onPress: () -> Unit) =
    pointerInput(repeat) {
        detectTapGestures(onPress = {
            onDown(true)
            onPress()
            val rep = if (repeat) scope.launch {
                delay(380)
                var d = 160L
                while (true) { onPress(); delay(d); d = maxOf(70L, d - 12) }
            } else null
            tryAwaitRelease()
            rep?.cancel()
            onDown(false)
        })
    }

/** Scale that dips fast on press and springs back with a little overshoot, like the design's bezier(.3,1.7,.5,1). */
@Composable
private fun pressScale(down: Boolean, to: Float) = animateFloatAsState(
    if (down) to else 1f,
    if (down) tween(80) else spring(dampingRatio = 0.42f, stiffness = 700f), label = "press",
)

/**
 * Raised soft-touch key (circle or pill). Pressed = flipped gradient with an inner shadow,
 * scale [pressedScale], accent icon and a centre ripple. Content gets the current icon colour.
 *
 * [label] is what TalkBack reads; [state] is read after it (e.g. "Connected").
 * [actionLabel] replaces TalkBack's "double-tap to activate" hint when the action isn't obvious.
 */
@Composable
fun KeyButton(
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    state: String? = null,
    actionLabel: String? = null,
    repeat: Boolean = false,
    pressedScale: Float = 0.95f,
    downFace: Face = KEY_DOWN,
    restColor: Color = T.Ink,
    /** False: shown dimmed and does nothing (not available for what's on the TV). */
    enabled: Boolean = true,
    onPress: () -> Unit,
    content: @Composable (color: Color, down: Boolean) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val rip = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val scale by pressScale(down, pressedScale)

    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.32f }
            .semantics(mergeDescendants = true) {
                if (!enabled) androidx.compose.ui.semantics.disabled()
                role = Role.Button
                contentDescription = label
                if (!state.isNullOrEmpty()) stateDescription = state
                // TalkBack double-tap: one press (no repeat), same feedback path as a finger tap.
                onClick(label = actionLabel) { onPress(); true }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .softFace(if (down) downFace else KEY_UP, sheen = !down)
            .rippleLayer { rip.value }
            .then(if (enabled) Modifier.pressInput(repeat, scope, { down = it; if (it) scope.ripple(rip) }, onPress) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Hide icons/text from TalkBack; the button's own label speaks for them.
        Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { content(if (down) Color.White else restColor, down) }
    }
}

/**
 * .ivol: one soft-touch pill split into − | mute | +. Each segment is its own button for
 * TalkBack; − and + repeat while held. Mute turns the accent colour while muted.
 */
@Composable
fun VolumePill(
    accent: Color,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onMute: () -> Unit,
    onUp: () -> Unit,
) {
    Row(modifier.softFace(ROCKER).clip(androidx.compose.foundation.shape.CircleShape), verticalAlignment = Alignment.CenterVertically) {
        PillSegment("Volume down", accent, first = true, repeat = true, onPress = onDown) { LineIcon(Ico.MINUS, it, bdp(48f), stroke = 1.9f) }
        PillSegment("Mute", accent, state = if (muted) "Muted" else null, onPress = onMute) {
            LineIcon(if (muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, it, bdp(44f), stroke = 1.9f)
        }
        PillSegment("Volume up", accent, last = true, repeat = true, onPress = onUp) { LineIcon(Ico.PLUS, it, bdp(48f), stroke = 1.9f) }
    }
}

/** .iv: pressed = black .28 tint with an inner top shadow; a 1.5px dark divider on its left. */
@Composable
private fun RowScope.PillSegment(
    label: String,
    accent: Color,
    first: Boolean = false,
    last: Boolean = false,
    repeat: Boolean = false,
    state: String? = null,
    onPress: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val rip = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val k = LocalK.current
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
                onClick { onPress(); true }
            }
            .drawBehind {
                val u = k * density
                if (down) {
                    drawRect(Color.Black.copy(0.28f))
                    // inset 0 3px 8px rgba(0,0,0,.55): a soft dark band along the top edge
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(0.55f), Color.Transparent), 0f, 11f * u))
                }
                if (!first) drawRect(Color.Black.copy(0.35f), size = size.copy(width = 1.5f * u))
            }
            .drawWithContent {
                drawContent()
                val v = rip.value
                if (v < 1f) {
                    val r = size.maxDimension * 0.8f * (0.1f + 0.9f * v)
                    drawCircle(Color.White.copy(alpha = 0.16f * (1f - v)), r, center)
                }
            }
            .pressInput(repeat, scope, { down = it; if (it) scope.ripple(rip) }, onPress),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { content(if (down) Color.White else T.Ink) }
    }
}

/**
 * A tall soft-touch rocker: [upIco] on top, [word] (VOL / CH) in the middle, [downIco] below.
 * Both ends repeat while held and are separate buttons for TalkBack.
 */
@Composable
fun VerticalRocker(
    accent: Color,
    word: String,
    upLabel: String,
    downLabel: String,
    upIco: Ico,
    downIco: Ico,
    modifier: Modifier = Modifier,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(modifier.softFace(ROCKER).clip(androidx.compose.foundation.shape.CircleShape), horizontalAlignment = Alignment.CenterHorizontally) {
        RockerSeg(upLabel, accent, onUp) { LineIcon(upIco, it, 20.dp, stroke = 1.9f) }
        Text(word, color = T.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
            modifier = Modifier.clearAndSetSemantics {})
        RockerSeg(downLabel, accent, onDown) { LineIcon(downIco, it, 20.dp, stroke = 1.9f) }
    }
}

@Composable
private fun ColumnScope.RockerSeg(label: String, accent: Color, onPress: () -> Unit, content: @Composable (Color) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val rip = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.weight(1f).fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { onPress(); true }
            }
            .drawBehind { if (down) drawRect(Color.Black.copy(0.28f)) }
            .drawWithContent {
                drawContent()
                val v = rip.value
                if (v < 1f) drawCircle(Color.White.copy(alpha = 0.16f * (1f - v)), size.maxDimension * 0.8f * (0.1f + 0.9f * v), center)
            }
            .pressInput(true, scope, { down = it; if (it) scope.ripple(rip) }, onPress),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { content(if (down) Color.White else T.Ink) }
    }
}

/** − | CH | +: down and up (both repeat while held), with a plain label between. Also used for VOL. */
@Composable
fun ChannelPill(
    accent: Color,
    modifier: Modifier = Modifier,
    word: String = "CH",
    downLabel: String = "Channel down",
    upLabel: String = "Channel up",
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(modifier.softFace(ROCKER).clip(androidx.compose.foundation.shape.CircleShape), verticalAlignment = Alignment.CenterVertically) {
        // first = true on both ends: no divider lines, one calm pill.
        PillSegment(downLabel, accent, first = true, repeat = true, onPress = onDown) { LineIcon(Ico.MINUS, it, 18.dp, stroke = 1.9f) }
        Box(Modifier.weight(0.8f).fillMaxHeight().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Text(word, color = T.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
        PillSegment(upLabel, accent, first = true, repeat = true, onPress = onUp) { LineIcon(Ico.PLUS, it, 18.dp, stroke = 1.9f) }
    }
}
