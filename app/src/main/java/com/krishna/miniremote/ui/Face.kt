package com.krishna.miniremote.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The design draws the remote on a 920 x 2000 "board" and scales the whole board to the phone.
 * LocalK is that scale in dp per board pixel, so every size below is the design's own number.
 */
val LocalK = compositionLocalOf { 0.413f }

@Composable fun bdp(x: Float): Dp = (x * LocalK.current).dp
@Composable fun bsp(x: Float): TextUnit = with(LocalDensity.current) { (x * LocalK.current).dp.toSp() }

/** One CSS box-shadow. Lengths are board pixels. */
data class Sh(val dy: Float, val blur: Float, val color: Color, val spread: Float = 0f, val inset: Boolean = false)

/** A CSS background gradient plus its box-shadow list (first listed is painted on top, like CSS). */
data class Face(val colors: List<Color>, val shadows: List<Sh>, val angle: Float = 180f, val stops: List<Float>? = null)

private val B = Color.Black
private val W = Color.White

/** .ib2 / .ipill / .devpill / .ivol at rest (v16 "charcoal soft-touch"). */
val KEY_UP = Face(listOf(T.FaceTop, T.FaceBot), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1.5f), Sh(10f, 20f, B.copy(.5f)),
    Sh(1.5f, 0f, W.copy(.13f), inset = true), Sh(-3f, 4f, B.copy(.35f), inset = true)))

/** The same keys while pressed: gradient flips and the shadow sinks inside. */
val KEY_DOWN = Face(listOf(T.FaceBot, T.FaceTop), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1.5f), Sh(2f, 4f, B.copy(.5f)), Sh(3f, 8f, B.copy(.55f), inset = true)))

/** The volume rocker shell: same face, its segments draw their own pressed state. */
val ROCKER = KEY_UP

/** The clickpad: a raised ring. */
val PAD = Face(listOf(Color(0xFF3D3D40), Color(0xFF2A2A2D), Color(0xFF202023)), listOf(
    Sh(0f, 0f, B.copy(.65f), spread = 1.5f), Sh(18f, 36f, B.copy(.55f)),
    Sh(2f, 0f, W.copy(.12f), inset = true), Sh(-5f, 12f, B.copy(.4f), inset = true), Sh(14f, 28f, W.copy(.035f), inset = true)),
    angle = 160f, stops = listOf(0f, .52f, 1f))

/** Its dished centre (OK). */
val WELL = Face(listOf(Color(0xFF121214), Color(0xFF262629)), listOf(
    Sh(0f, 0f, B.copy(.75f), spread = 1.5f), Sh(1.5f, 0f, W.copy(.08f)),
    Sh(9f, 18f, B.copy(.7f), inset = true), Sh(-2f, 5f, W.copy(.05f), inset = true)))

val WELL_DOWN = Face(listOf(Color(0xFF0D0D0F), Color(0xFF202023)), listOf(
    Sh(0f, 0f, B.copy(.8f), spread = 1.5f), Sh(1f, 0f, W.copy(.06f)), Sh(12f, 22f, B.copy(.8f), inset = true)))

fun flat(c: Color, shadows: List<Sh>) = Face(listOf(c, c), shadows)

fun stadiumPath(w: Float, h: Float, cr: Float? = null) = Path().apply {
    addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(cr ?: (min(w, h) / 2f))))
}

/**
 * Material 3 surface colours (dark, neutral). Every [Face] in the app maps to one of these
 * tonal fills: no bevels, no inner shadows. Raised = lighter container, pressed = one step lighter.
 */
object M {
    val Container get() = Look.p.container
    val High get() = Look.p.high
    val Highest get() = Look.p.highest
    val Pressed get() = Look.p.pressed
}

private fun flatColor(f: Face): Color = when (f) {
    KEY_UP -> M.High
    KEY_DOWN -> M.Pressed
    PAD -> M.Container
    WELL -> M.Highest
    WELL_DOWN -> M.Pressed
    CARD -> M.Container
    CARD_DOWN -> M.High
    HOLLOW -> M.High
    LIGHT -> Color(0xFFE3E3E3)
    LIGHT_DOWN -> Color(0xFFC9C9C9)
    DANGER -> Color(0xFF601410)
    DANGER_DOWN -> Color(0xFF7A1B16)
    NAV_FACE -> M.High
    else -> f.colors.first()
}

/** Fills the element with [face]'s Material tonal colour, as a pill/circle or with [corner]. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.softFace(face: Face, sheen: Boolean = true, corner: Dp? = null, dpUnits: Boolean = false): Modifier = composed {
    val density = LocalDensity.current.density
    val cr = corner?.let { it.value * density }
    // Read each time (not remembered): the palette can change while the screen is up.
    val fill = flatColor(face)
    drawBehind {
        val r = cr ?: (min(size.width, size.height) / 2f)
        if (Look.depth && sheen) depthShape(fill, r) else drawRoundRect(fill, cornerRadius = CornerRadius(r))
    }
}

/**
 * A raised surface: a soft drop shadow under it, light from above (a gentle top-to-bottom
 * gradient) and a thin highlight on its upper rim. [pressed] sinks it (smaller, closer shadow).
 */
fun DrawScope.depthShape(fill: Color, radius: Float, pressed: Boolean = false) {
    val dark = !Look.p.light
    drawIntoCanvas { c ->
        val p = android.graphics.Paint().apply {
            isAntiAlias = true
            color = fill.toArgb()
            setShadowLayer(if (pressed) 3.dp.toPx() else 12.dp.toPx(), 0f, if (pressed) 1.dp.toPx() else 6.dp.toPx(),
                Color.Black.copy(alpha = if (dark) 0.65f else 0.22f).toArgb())
        }
        c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, p)
    }
    val top = androidx.compose.ui.graphics.lerp(fill, Color.White, if (pressed) 0.02f else if (dark) 0.10f else 0.35f)
    val bottom = androidx.compose.ui.graphics.lerp(fill, Color.Black, if (pressed) 0.02f else if (dark) 0.18f else 0.06f)
    drawRoundRect(Brush.verticalGradient(if (pressed) listOf(bottom, top) else listOf(top, bottom)), cornerRadius = CornerRadius(radius))
    if (!pressed) drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.16f else 0.7f), Color.Transparent), endY = size.height * 0.45f),
        cornerRadius = CornerRadius(radius), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
    )
}

/* ---- Sheet faces: the remote's charcoal soft-touch language at sheet scale (lengths in dp). ---- */

/** Raised card / secondary button / round header button. */
val CARD = Face(listOf(Color(0xFF333336), Color(0xFF242427)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(4f, 10f, B.copy(.45f)),
    Sh(1f, 0f, W.copy(.09f), inset = true), Sh(-2f, 3f, B.copy(.3f), inset = true)))

val CARD_DOWN = Face(listOf(Color(0xFF222225), Color(0xFF303033)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(1f, 2f, B.copy(.45f)), Sh(2f, 6f, B.copy(.5f), inset = true)))

/** Recessed well: text fields, code boxes, switch and segmented tracks. */
val HOLLOW = Face(listOf(Color(0xFF141416), Color(0xFF1F1F22)), listOf(
    Sh(0f, 0f, B.copy(.7f), spread = 1f), Sh(1f, 0f, W.copy(.06f)),
    Sh(3f, 6f, B.copy(.6f), inset = true)))

/** Primary button: a light soft-touch key. */
val LIGHT = Face(listOf(Color(0xFFF7F7F9), Color(0xFFD6D6DA)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(4f, 10f, B.copy(.45f)),
    Sh(1f, 0f, W.copy(.9f), inset = true), Sh(-2f, 3f, B.copy(.18f), inset = true)))

val LIGHT_DOWN = Face(listOf(Color(0xFFD0D0D4), Color(0xFFEDEDF0)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(1f, 2f, B.copy(.45f)), Sh(2f, 6f, B.copy(.3f), inset = true)))

val DANGER = Face(listOf(Color(0xFF4A2626), Color(0xFF341B1B)), CARD.shadows)
val DANGER_DOWN = Face(listOf(Color(0xFF341B1B), Color(0xFF452424)), CARD_DOWN.shadows)

/** Clip helper for ripples and pressed tints so they stay inside the pill. */
fun DrawScope.insideStadium(block: DrawScope.() -> Unit) = clipPath(stadiumPath(size.width, size.height), block = block)
