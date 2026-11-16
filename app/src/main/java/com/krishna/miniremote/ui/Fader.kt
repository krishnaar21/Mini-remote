package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Retro hardware fader: recessed groove lit in the accent colour up to the thumb, a ridged
 * metal thumb with an accent mark, and a tick scale underneath (long ticks at ends and middle).
 * Built on Material Slider, so TalkBack adjust works; [label] and [valueText] name it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fader(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    label: String,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            // 48dp is the minimum touch target; the track and thumb still draw at their own size.
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .semantics { contentDescription = label; stateDescription = valueText },
            thumb = {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.size(58.dp, 28.dp)
                        .shadow(6.dp, shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF4A4A4E), Color(0xFF29292B))), shape)
                        .border(1.dp, Color.Black, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { Box(Modifier.width(2.dp).height(14.dp).background(Color.White.copy(0.09f))) }
                        Box(Modifier.width(2.dp).height(16.dp).background(accent, RoundedCornerShape(1.dp)))
                        repeat(3) { Box(Modifier.width(2.dp).height(14.dp).background(Color.White.copy(0.09f))) }
                    }
                }
            },
            track = {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.fillMaxWidth().height(12.dp)
                        .background(Color(0xFF141415), shape)
                        .border(1.dp, Color.Black, shape)
                        .drawBehind {
                            drawRoundRect(accent, size = size.copy(width = size.width * frac),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                        }
                )
            },
        )
        // Tick scale: 11 marks, the ends and middle longer, aligned under the thumb's travel.
        Box(
            Modifier.fillMaxWidth().height(9.dp).padding(horizontal = 29.dp)
                .drawBehind {
                    for (i in 0..10) {
                        val x = size.width * i / 10f
                        val long = i == 0 || i == 5 || i == 10
                        val h = if (long) size.height else size.height * 0.66f
                        drawLine(if (long) Color(0xFF8A8A90) else Color(0xFF5D5D62), Offset(x, size.height - h), Offset(x, size.height), 1.5.dp.toPx())
                    }
                }
        )
    }
}
