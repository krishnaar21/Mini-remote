package com.krishna.miniremote.ui

import androidx.compose.ui.graphics.Color

object T {
    val Bg = Color(0xFF242427)
    val FaceTop = Color(0xFF38383B)
    val FaceBot = Color(0xFF212124)
    val Ink = Color(0xFFECECEF)
    val Dim = Color(0xFF86868C)
    val Well = Color(0xFF121214)
    val Danger = Color(0xFFFF453A)
}

fun accentColor(hex: String): Color =
    runCatching { Color(("FF$hex").toLong(16)) }.getOrDefault(Color(0xFF0A84FF))
