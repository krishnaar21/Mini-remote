package com.krishna.miniremote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.krishna.miniremote.R

/** Colours from the design preview (its final "charcoal soft-touch" layer). */
object T {
    val Bg = Color(0xFF242427)
    val FaceTop = Color(0xFF38383B)
    val FaceBot = Color(0xFF212124)
    val Ink = Color(0xFFECECEF)
    val Dim = Color(0xFF86868C)
    val Well = Color(0xFF121214)
    val Danger = Color(0xFFFF453A)
}

/** Inter stands in for the preview's system font (SF Pro); IBM Plex Mono for its mono labels. Latin subsets. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Mono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
)

fun accentColor(hex: String): Color =
    runCatching { Color(("FF$hex").toLong(16)) }.getOrDefault(Color(0xFFFF453A))

/** CSS color-mix(in srgb, a p%, b). */
fun mix(a: Color, p: Float, b: Color) = Color(
    a.red * p + b.red * (1 - p), a.green * p + b.green * (1 - p), a.blue * p + b.blue * (1 - p), 1f,
)
