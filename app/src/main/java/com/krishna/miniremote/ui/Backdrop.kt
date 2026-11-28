package com.krishna.miniremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * What's behind an open sheet: softly blurred and dimmed, at every level (the remote, the apps
 * screen, Settings under one of its own sheets). It stays a soft, recognisable version of the page
 * you came from, like the design's scrim: a heavy blur plus a dark scrim flattened these dark
 * surfaces into one colour, so Settings under a sheet looked like a blank page. The blur is a GPU
 * effect on the layer (Android 12 and up), not a redraw. It fades in as a sheet opens and is removed at once the moment the sheet starts to
 * close, since any fade-out there reads as lag.
 */
object Backdrop {
    /** The sheets showing, bottom to top. */
    internal val open = mutableStateListOf<Any>()

    /** Blur works on Android 12+; below that the scrim is a little darker instead. */
    val blurs get() = android.os.Build.VERSION.SDK_INT >= 31

    /** The dark layer over what's behind a sheet. */
    fun scrim(): Color = Color.Black.copy(alpha = if (blurs) (if (Look.p.light) 0.14f else 0.30f) else (if (Look.p.light) 0.32f else 0.55f))
}

/** Enough to soften what's behind; text and cards still read as shapes. */
private val BLUR = 6.dp

/** Blurs this when a sheet is open above it: [token] null is the app itself (under every sheet). */
fun Modifier.underSheets(token: Any? = null): Modifier = composed {
    val list = Backdrop.open
    val behind = if (token == null) list.isNotEmpty() else list.indexOf(token).let { it >= 0 && it < list.lastIndex }
    val t by animateFloatAsState(if (behind) 1f else 0f, if (behind) tween(160) else snap(), label = "backdrop")
    if (!Backdrop.blurs || t == 0f) this
    else graphicsLayer { renderEffect = BlurEffect(BLUR.toPx() * t, BLUR.toPx() * t, TileMode.Clamp) }
}

/**
 * Every sheet in the app: the sheet colour, a short grabber, the backdrop blur and scrim.
 * Same shape as Material's ModalBottomSheet so call sites read the same; [scrimColor] and
 * [dragHandle] are fixed here so every sheet matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color = SheetBg,
    contentColor: Color = T.Ink,
    shape: Shape = SheetShape,
    @Suppress("UNUSED_PARAMETER") scrimColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val token = remember { Any() }
    // Counted as open from the moment it starts to show until it starts to hide.
    val showing = sheetState.targetValue != SheetValue.Hidden
    DisposableEffect(showing) {
        if (showing) Backdrop.open.add(token)
        onDispose { Backdrop.open.remove(token) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = containerColor, contentColor = contentColor,
        shape = shape, scrimColor = Backdrop.scrim(), dragHandle = { Grab() },
    ) {
        Column(Modifier.underSheets(token), content = content)
    }
}
