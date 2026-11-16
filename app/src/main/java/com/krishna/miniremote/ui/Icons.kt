package com.krishna.miniremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ico { UP, DOWN, LEFT, RIGHT, MIC, POWER, BACK, PLAY, HOME, KEYBOARD, MINUS, PLUS, SPEAKER, CHEVRON_R, GEAR, CLOSE }

/** Light line icons drawn on a 24x24 grid so they match the soft-touch look without extra libraries. */
@Composable
fun LineIcon(ico: Ico, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val st = Stroke(width = 2.1f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, o(x1, y1), o(x2, y2), st.width, StrokeCap.Round)
        fun DrawScope.poly(close: Boolean, vararg p: Pair<Float, Float>) {
            val path = Path()
            p.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * k, y * k) else path.lineTo(x * k, y * k) }
            if (close) path.close()
            drawPath(path, color, style = st)
        }
        when (ico) {
            Ico.UP -> poly(false, 6f to 15f, 12f to 9f, 18f to 15f)
            Ico.DOWN -> poly(false, 6f to 9f, 12f to 15f, 18f to 9f)
            Ico.LEFT -> poly(false, 15f to 6f, 9f to 12f, 15f to 18f)
            Ico.RIGHT, Ico.CHEVRON_R -> poly(false, 9f to 6f, 15f to 12f, 9f to 18f)
            Ico.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            Ico.MINUS -> line(5f, 12f, 19f, 12f)
            Ico.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ico.POWER -> {
                drawArc(color, -50f, 280f, false, o(5f, 6f), Size(14f * k, 14f * k), style = st)
                line(12f, 3.5f, 12f, 12f)
            }
            Ico.MIC -> {
                drawRoundRect(color, o(9f, 3f), Size(6f * k, 11f * k), CornerRadius(3f * k), style = st)
                drawArc(color, 0f, 180f, false, o(5.5f, 7f), Size(13f * k, 11f * k), style = st)
                line(12f, 18f, 12f, 21f)
            }
            Ico.BACK -> {
                poly(false, 9f to 5f, 4f to 10f, 9f to 15f)
                val p = Path().apply {
                    moveTo(4f * k, 10f * k); lineTo(14f * k, 10f * k)
                    cubicTo(21f * k, 10f * k, 21f * k, 19f * k, 14f * k, 19f * k)
                    lineTo(9f * k, 19f * k)
                }
                drawPath(p, color, style = st)
            }
            Ico.PLAY -> {
                poly(true, 4f to 5f, 4f to 19f, 13f to 12f)
                line(17f, 5f, 17f, 19f); line(20.5f, 5f, 20.5f, 19f)
            }
            Ico.HOME -> {
                poly(false, 3.5f to 11f, 12f to 3.5f, 20.5f to 11f)
                poly(false, 6f to 9.5f, 6f to 20f, 18f to 20f, 18f to 9.5f)
                poly(false, 10f to 20f, 10f to 14f, 14f to 14f, 14f to 20f)
            }
            Ico.KEYBOARD -> {
                drawRoundRect(color, o(2.5f, 6f), Size(19f * k, 12f * k), CornerRadius(2.5f * k), style = st)
                listOf(6.5f, 10f, 13.5f, 17f).forEach { line(it, 10f, it + 0.01f, 10f) }
                line(8f, 14.5f, 16f, 14.5f)
            }
            Ico.SPEAKER -> {
                poly(true, 4f to 10f, 8f to 10f, 12.5f to 6f, 12.5f to 18f, 8f to 14f, 4f to 14f)
                drawArc(color, -50f, 100f, false, o(11f, 8f), Size(10f * k, 8f * k), style = st)
            }
            Ico.GEAR -> {
                drawCircle(color, 3f * k, o(12f, 12f), style = st)
                drawCircle(color, 7.5f * k, o(12f, 12f), style = Stroke(2.1f * k, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.2f * k, 2.2f * k))))
            }
        }
    }
}
