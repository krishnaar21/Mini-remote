package com.krishna.miniremote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Colours from the design preview (its final "charcoal soft-touch" layer). */
object T {
    val Bg = Color(0xFF1C1C1E)
    val FaceTop = Color(0xFF3A3A3D)
    val FaceBot = Color(0xFF3A3A3D)
    val Ink = Color(0xFFF7F7F8)
    val Dim = Color(0xFFBDC1C6)
    val Well = Color(0xFF2C2C2E)
    val Danger = Color(0xFFF2B8B5)
}

/** Material uses the system font (Roboto on most phones). Kept under this name so every screen follows. */
val Inter: FontFamily = FontFamily.Default
/** Numbers and codes: the system monospace font (no bundled font files). */
val Mono: FontFamily = FontFamily.Monospace

fun accentColor(hex: String): Color =
    runCatching { Color(("FF$hex").toLong(16)) }.getOrDefault(Color(0xFFFF453A))

/** CSS color-mix(in srgb, a p%, b). */
fun mix(a: Color, p: Float, b: Color) = Color(
    a.red * p + b.red * (1 - p), a.green * p + b.green * (1 - p), a.blue * p + b.blue * (1 - p), 1f,
)
