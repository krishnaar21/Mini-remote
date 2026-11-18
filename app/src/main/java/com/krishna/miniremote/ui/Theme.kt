package com.krishna.miniremote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** One look for the whole app: every surface and text colour comes from here. */
data class Palette(
    val key: String, val label: String, val light: Boolean,
    val screen: Color, val bg: Color, val container: Color, val high: Color, val highest: Color, val pressed: Color,
    val sheet: Color, val keyFill: Color, val ink: Color, val dim: Color, val muted: Color, val lead: Color, val sub: Color,
    val line: Color, val well: Color, val dotOff: Color,
)

object Palettes {
    val DARK = Palette("dark", "Dark", false,
        screen = Color(0xFF000000), bg = Color(0xFF1C1C1E), container = Color(0xFF2E2E31), high = Color(0xFF3D3D41), highest = Color(0xFF4C4C51), pressed = Color(0xFF5A5A60),
        sheet = Color(0xFF262629), keyFill = Color(0xFF1C1C1E), ink = Color(0xFFF7F7F8), dim = Color(0xFFBDC1C6), muted = Color(0xFFB4B4B9), lead = Color(0xFFE0E0E4), sub = Color(0xFF8E8E93),
        line = Color(0xFF3A3A3E), well = Color(0xFF2C2C2E), dotOff = Color(0xFF7C7C82))
    val LIGHT = Palette("light", "Light", true,
        screen = Color(0xFFF2F2F7), bg = Color(0xFFFFFFFF), container = Color(0xFFFFFFFF), high = Color(0xFFE6E6EB), highest = Color(0xFFD5D5DB), pressed = Color(0xFFC7C7CD),
        sheet = Color(0xFFF2F2F7), keyFill = Color(0xFFFFFFFF), ink = Color(0xFF1C1C1E), dim = Color(0xFF48484D), muted = Color(0xFF5E5E63), lead = Color(0xFF2C2C2E), sub = Color(0xFF8A8A8F),
        line = Color(0xFFD9D9DE), well = Color(0xFFE9E9EE), dotOff = Color(0xFFB8B8BE))
    val NAVY = Palette("navy", "Navy", false,
        screen = Color(0xFF070D1E), bg = Color(0xFF111A33), container = Color(0xFF16213F), high = Color(0xFF203058), highest = Color(0xFF2B3D6B), pressed = Color(0xFF364A7D),
        sheet = Color(0xFF121C37), keyFill = Color(0xFF131D39), ink = Color(0xFFEEF2FF), dim = Color(0xFFBAC4E2), muted = Color(0xFFA6B1D3), lead = Color(0xFFDCE3F7), sub = Color(0xFF7F8BB2),
        line = Color(0xFF26355F), well = Color(0xFF1A2547), dotOff = Color(0xFF55618A))
    /** Follows the phone: Light by day settings, Dark when the phone is dark (see [resolve]). */
    val AUTO = DARK.copy(key = "auto", label = "Auto")
    val all = listOf(AUTO, DARK, LIGHT, NAVY)
    fun of(k: String) = all.firstOrNull { it.key == k } ?: DARK
    /** The palette to draw with: Auto becomes Light or Dark as the phone is. */
    fun resolve(k: String, systemDark: Boolean): Palette = if (k == "auto" || k == "retro") (if (systemDark) DARK else LIGHT) else of(k)
}

/** The palette in use (Settings › Appearance › Theme). Reading it recomposes on change. */
object Look { var p by mutableStateOf(Palettes.DARK) }

/** Text and surface colours, from the current palette. */
object T {
    val Screen get() = Look.p.screen
    val Bg get() = Look.p.bg
    val FaceTop get() = Look.p.high
    val FaceBot get() = Look.p.high
    val Ink get() = Look.p.ink
    val Dim get() = Look.p.dim
    val Sub get() = Look.p.sub
    val Well get() = Look.p.well
    val Key get() = Look.p.keyFill
    val DotOff get() = Look.p.dotOff
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
