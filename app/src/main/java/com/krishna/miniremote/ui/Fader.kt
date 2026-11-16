package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Retro hardware fader: wide, low thumb with ridges and an accent mark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fader(value: Float, range: ClosedFloatingPointRange<Float>, accent: Color, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        thumb = {
            val shape = RoundedCornerShape(7.dp)
            Box(
                Modifier.size(58.dp, 28.dp)
                    .shadow(6.dp, shape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF48484C), Color(0xFF2A2A2D))), shape)
                    .border(1.dp, Color.White.copy(0.14f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { Box(Modifier.width(2.dp).height(14.dp).background(Color.Black.copy(0.45f))) }
                    Box(Modifier.width(3.dp).height(16.dp).background(accent, RoundedCornerShape(2.dp)))
                    repeat(3) { Box(Modifier.width(2.dp).height(14.dp).background(Color.Black.copy(0.45f))) }
                }
            }
        },
        track = {
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .background(Color(0xFF121214), RoundedCornerShape(3.dp))
                    .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(3.dp))
            )
        },
    )
}
