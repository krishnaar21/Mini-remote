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
    // Material 3 slider in the accent colour: filled track, round thumb, no tick marks.
    Slider(
        value = value, onValueChange = onChange, valueRange = range, steps = steps,
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .semantics { contentDescription = label; stateDescription = valueText },
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = M.High,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        ),
        // A small round knob instead of the tall default handle.
        thumb = { Box(Modifier.size(20.dp).background(accent, androidx.compose.foundation.shape.CircleShape)) },
    )
}
