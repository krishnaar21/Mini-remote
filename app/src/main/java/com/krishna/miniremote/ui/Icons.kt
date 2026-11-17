package com.krishna.miniremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line icons, using the exact 24x24 SVG paths from the design preview (Lucide style).
 * Numbers are written with explicit separators so the parser never has to guess.
 */
enum class Ico(val d: String) {
    UP("M6 15L12 9L18 15"),
    DOWN("M6 9L12 15L18 9"),
    LEFT("M15 6L9 12L15 18"),
    RIGHT("M9 6L15 12L9 18"),
    CHEVRON_R("M9 6L15 12L9 18"),
    CHEVRON_L("M15 18L9 12L15 6"),
    MIC("M12 2a3 3 0 0 0 -3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0 -3 -3Z M19 10v2a7 7 0 0 1 -14 0v-2 M12 19v3"),
    POWER("M12 2v10 M18.4 6.6a9 9 0 1 1 -12.77 0.04"),
    BACK("M9 14L4 9L9 4 M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1 -5.5 5.5H11"),
    PLAY("M4 5L13 12L4 19Z M17 5v14 M21 5v14"),
    HOME("M15 21v-8a1 1 0 0 0 -1 -1h-4a1 1 0 0 0 -1 1v8 M3 10a2 2 0 0 1 0.709 -1.528l7 -5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2Z"),
    KEYBOARD("M10 8h0.01 M12 12h0.01 M14 8h0.01 M16 12h0.01 M18 8h0.01 M6 8h0.01 M8 12h0.01 M7 16h10 M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2Z"),
    MINUS("M5 12h14"),
    PLUS("M5 12h14 M12 5v14"),
    SPEAKER("M3 9.5h3.5L12 5v14l-5.5 -4.5H3Z M16 9a5 5 0 0 1 0 6"),
    /** Louder: two sound waves. */
    SPEAKER_UP("M3 9.5h3.5L12 5v14l-5.5 -4.5H3Z M15.5 9.5a3.5 3.5 0 0 1 0 5 M18.5 6.5a7.5 7.5 0 0 1 0 11"),
    /** Quieter: one small wave. */
    SPEAKER_DOWN("M3 9.5h3.5L12 5v14l-5.5 -4.5H3Z M15.5 10a2.8 2.8 0 0 1 0 4"),
    SPEAKER_MUTED("M3 9.5h3.5L12 5v14l-5.5 -4.5H3Z M16 9a5 5 0 0 1 0 6 M4 4l16 16"),
    GEAR("M12.22 2h-0.44a2 2 0 0 0 -2 2v0.18a2 2 0 0 1 -1 1.73l-0.43 0.25a2 2 0 0 1 -2 0l-0.15 -0.08a2 2 0 0 0 -2.73 0.73l-0.22 0.38a2 2 0 0 0 0.73 2.73l0.15 0.1a2 2 0 0 1 1 1.72v0.51a2 2 0 0 1 -1 1.74l-0.15 0.09a2 2 0 0 0 -0.73 2.73l0.22 0.38a2 2 0 0 0 2.73 0.73l0.15 -0.08a2 2 0 0 1 2 0l0.43 0.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h0.44a2 2 0 0 0 2 -2v-0.18a2 2 0 0 1 1 -1.73l0.43 -0.25a2 2 0 0 1 2 0l0.15 0.08a2 2 0 0 0 2.73 -0.73l0.22 -0.39a2 2 0 0 0 -0.73 -2.73l-0.15 -0.08a2 2 0 0 1 -1 -1.74v-0.5a2 2 0 0 1 1 -1.74l0.15 -0.09a2 2 0 0 0 0.73 -2.73l-0.22 -0.38a2 2 0 0 0 -2.73 -0.73l-0.15 0.08a2 2 0 0 1 -2 0l-0.43 -0.25a2 2 0 0 1 -1 -1.73V4a2 2 0 0 0 -2 -2Z M15 12a3 3 0 1 1 -6 0a3 3 0 0 1 6 0Z"),
    CLOSE("M18 6L6 18 M6 6l12 12"),
    SLIDERS("M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4"),
    WIFI("M12 20h0.01 M2 8.82a15 15 0 0 1 20 0 M5 12.86a10 10 0 0 1 14 0 M8.5 16.43a5 5 0 0 1 7 0"),
    CHECK("M20 6L9 17l-5 -5"),
    ALERT("M12 7v6 M12 17h0.01"),
    TV("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2Z M8 21h8 M12 17v4"),
    SEARCH("M11 4a7 7 0 1 1 0 14a7 7 0 0 1 0 -14Z M20 20l-4.2 -4.2"),
    GRID("M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1Z M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1Z M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z"),
    USER("M12 12a4 4 0 1 0 0 -8a4 4 0 0 0 0 8Z M4 21a8 8 0 0 1 16 0"),
    GUIDE("M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2Z M6 8h12 M6 12h8 M9 21h6"),
    LIST("M8 6h13 M8 12h13 M8 18h13 M3.5 6h0.01 M3.5 12h0.01 M3.5 18h0.01"),
    INFO("M12 3a9 9 0 1 1 0 18a9 9 0 0 1 0 -18Z M12 11v6 M12 7.5h0.01"),
    INPUT("M10 17l5 -5l-5 -5 M15 12H3 M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-4"),
    DISPLAY("M12 8a4 4 0 1 1 0 8a4 4 0 0 1 0 -8Z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4 -1.4 M17.7 6.3l1.4 -1.4"),
    AUDIO("M3 14v-2a9 9 0 0 1 18 0v2 M3 14h3a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H5a2 2 0 0 1 -2 -2Z M21 14h-3a1 1 0 0 0 -1 1v4a1 1 0 0 0 1 1h1a2 2 0 0 0 2 -2Z"),
    REWIND("M11 18L3 12L11 6Z M21 18L13 12L21 6Z"),
    FFWD("M13 18L21 12L13 6Z M3 18L11 12L3 6Z"),
    DIAL("M12 3a9 9 0 1 1 0 18a9 9 0 0 1 0 -18Z M12 9a3 3 0 1 1 0 6a3 3 0 0 1 0 -6Z"),
    TRACKPAD("M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2Z M12 10a2 2 0 1 1 0 4a2 2 0 0 1 0 -4Z"),
    NUMPAD("M6 5h0.01 M12 5h0.01 M18 5h0.01 M6 10h0.01 M12 10h0.01 M18 10h0.01 M6 15h0.01 M12 15h0.01 M18 15h0.01 M12 20h0.01"),
    REFRESH("M20 12a8 8 0 0 1 -14.3 4.9 M4 12a8 8 0 0 1 14.3 -4.9 M18.5 3v4h-4 M5.5 21v-4h4"),
    STAR("M12 3l2.6 5.6l6 0.7l-4.5 4.1l1.2 6l-5.3 -3l-5.3 3l1.2 -6l-4.5 -4.1l6 -0.7Z"),
    PLAY_ONLY("M7 5L19 12L7 19Z"),
    PAUSE("M8 5v14 M16 5v14");

    internal val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

/**
 * Draws [ico] at [size]. [stroke] is in the icon's own 24-unit grid, like SVG stroke-width
 * (the design uses 1.8 on round keys, 1.9 on volume, 2.0 on the big keys, 2.3 on pad arrows).
 */
@Composable
fun LineIcon(ico: Ico, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier, stroke: Float = 1.8f) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(ico.path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
