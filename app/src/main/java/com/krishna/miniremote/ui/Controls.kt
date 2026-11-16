package com.krishna.miniremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
