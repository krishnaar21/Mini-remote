package com.krishna.miniremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Raised soft-touch button. Pressed = inverted gradient, accent icon and a ripple that
 * starts where the finger landed. Content gets the current icon colour.
 *
 * [label] is what TalkBack reads; [state] is read after it (e.g. "Connected").
 * [actionLabel] replaces TalkBack's "double-tap to activate" hint when the action isn't obvious.
 */
@Composable
fun KeyButton(
    shape: Shape,
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    state: String? = null,
    actionLabel: String? = null,
    repeat: Boolean = false,
    onPress: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val prog = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val top = if (down) T.FaceBot else T.FaceTop
    val bot = if (down) T.FaceTop else T.FaceBot
    val tilt = LocalTilt.current

    Box(
        modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!state.isNullOrEmpty()) stateDescription = state
                // TalkBack double-tap: one press (no repeat), same feedback path as a finger tap.
                onClick(label = actionLabel) { onPress(); true }
            }
            .shadow(if (down) 2.dp else 10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bot)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.Transparent)), shape)
            .drawWithContent {
                if (!down) sheen(tilt.value)
                drawContent()
                val p = prog.value
                if (p < 1f) {
                    val r = size.maxDimension * 0.9f * p
                    drawCircle(accent.copy(alpha = 0.28f * (1f - p)), r, origin)
                }
            }
            .pointerInput(repeat) {
                detectTapGestures(onPress = { pos ->
                    origin = pos; down = true
                    scope.launch { prog.snapTo(0f); prog.animateTo(1f, tween(520)) }
                    onPress()
                    val rep = if (repeat) scope.launch {
                        delay(380)
                        var d = 160L
                        while (true) { onPress(); delay(d); d = maxOf(70L, d - 12) }
                    } else null
                    tryAwaitRelease()
                    rep?.cancel()
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Hide icons/text from TalkBack; the button's own label speaks for them.
        Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { content(if (down) accent else T.Ink) }
    }
}

@Composable
fun RoundKey(size: Dp, accent: Color, label: String, onPress: () -> Unit, content: @Composable (Color) -> Unit) =
    KeyButton(CircleShape, accent, label, Modifier.size(size), onPress = onPress, content = content)

/**
 * Volume rocker: one soft-touch pill split into − | mute | +. Each segment is its own button for
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
    val shape = CircleShape
    val tilt = LocalTilt.current
    Row(
        modifier
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(T.FaceTop, T.FaceBot)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.Transparent)), shape)
            .drawBehind { sheen(tilt.value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillSegment("Volume down", accent, repeat = true, onPress = onDown) { LineIcon(Ico.MINUS, it) }
        Box(Modifier.width(1.5.dp).height(28.dp).background(Color.Black.copy(0.35f)))
        PillSegment("Mute", accent, state = if (muted) "Muted" else null, onPress = onMute) {
            LineIcon(if (muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (muted) accent else it)
        }
        Box(Modifier.width(1.5.dp).height(28.dp).background(Color.Black.copy(0.35f)))
        PillSegment("Volume up", accent, repeat = true, onPress = onUp) { LineIcon(Ico.PLUS, it) }
    }
}

@Composable
private fun RowScope.PillSegment(
    label: String,
    accent: Color,
    repeat: Boolean = false,
    state: String? = null,
    onPress: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
                onClick { onPress(); true }
            }
            .background(if (down) Color.Black.copy(0.28f) else Color.Transparent)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    down = true
                    onPress()
                    val rep = if (repeat) scope.launch {
                        delay(380)
                        var d = 160L
                        while (true) { onPress(); delay(d); d = maxOf(70L, d - 12) }
                    } else null
                    tryAwaitRelease()
                    rep?.cancel()
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { content(if (down) accent else T.Ink) }
    }
}
