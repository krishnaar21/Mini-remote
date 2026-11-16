package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlin.random.Random

private fun grain(): ImageBitmap {
    val n = 96
    val px = IntArray(n * n)
    val r = Random(7)
    for (i in px.indices) {
        val a = r.nextInt(0, 22)
        px[i] = (a shl 24) or (0xFFFFFF)
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

/** The icon the well shows above a key's name, as in the design's toast. */
private fun keyIcon(k: RemoteKey, muted: Boolean): Ico? = when (k) {
    RemoteKey.UP -> Ico.UP; RemoteKey.DOWN -> Ico.DOWN; RemoteKey.LEFT -> Ico.LEFT; RemoteKey.RIGHT -> Ico.RIGHT
    RemoteKey.BACK -> Ico.BACK; RemoteKey.HOME -> Ico.HOME; RemoteKey.PLAY_PAUSE -> Ico.PLAY; RemoteKey.POWER -> Ico.POWER
    RemoteKey.VOL_UP -> Ico.PLUS; RemoteKey.VOL_DOWN -> Ico.MINUS; RemoteKey.VOICE -> Ico.MIC
    RemoteKey.MUTE -> if (muted) Ico.SPEAKER_MUTED else Ico.SPEAKER
    RemoteKey.OK -> null
}

@Composable
fun RemoteScreen(vm: RemoteVM) {
    val accent = accentColor(vm.settings.accentHex)
    val grainBrush = remember { ShaderBrush(ImageShader(grain(), TileMode.Repeated, TileMode.Repeated)) }
    // Which sheet page is open (null = closed). The pill opens Devices; the offline pad does too.
    var sheet by remember { mutableStateOf<Page?>(null) }
    var showKeys by remember { mutableStateOf(false) }
    val tilt = rememberTilt(vm.settings.gyro)
    val offline = vm.active == null || vm.link == Link.ERROR

    // Transmit light: the dot in the TV pill flashes the accent colour each time a command goes out.
    val lit = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(vm.txCount) {
        if (vm.txCount > 0) { lit.snapTo(1f); lit.animateTo(0f, androidx.compose.animation.core.tween(380)) }
    }
    val blink = rememberInfiniteTransition(label = "blink").animateFloat(
        0.2f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(500), RepeatMode.Reverse), label = "blink")

    CompositionLocalProvider(LocalTilt provides tilt) {
    BoxWithConstraints(Modifier.fillMaxSize().background(T.Bg).drawBehind { drawRect(grainBrush, alpha = 0.30f) }.systemBarsPadding()) {
        // The design's board: 920 x 2000, as wide as the phone minus a 16dp gutter each side
        // (at most 420dp), shrunk further if the screen is short, centred and nudged down 70.
        val bw = (maxWidth.value - 32f).coerceAtMost(420f)
        val k = minOf(bw / 920f, maxHeight.value / 2000f)
        val ox = (maxWidth.value - 920f * k) / 2f
        val oy = ((maxHeight.value - 2000f * k) / 2f + 70f * k).coerceAtLeast(0f)
        /** Places a w x h element centred on board point (cx, cy). */
        fun at(cx: Float, cy: Float, w: Float, h: Float) =
            Modifier.offset((ox + (cx - w / 2f) * k).dp, (oy + (cy - h / 2f) * k).dp).size((w * k).dp, (h * k).dp)

        CompositionLocalProvider(LocalK provides k) {
            // Voice (.ib2.mic): pressed it glows with the accent and a pulsing inner ring.
            val micDown = flat(mix(accent, 0.26f, Color(0xFF2A2A2D)), KEY_DOWN.shadows)
            KeyButton(accent, "Voice search", at(102f, 190f, 124f, 124f), downFace = micDown, onPress = { vm.press(RemoteKey.VOICE) }) { c, down ->
                if (down) Box(Modifier.size((124f * k).dp).drawBehind {
                    drawCircle(accent.copy(0.7f * blink.value), size.minDimension / 2f - 1.5f * k * density, style = androidx.compose.ui.graphics.drawscope.Stroke(3f * k * density))
                })
                LineIcon(Ico.MIC, c, bdp(50f))
            }

            // TV pill (.devpill): status light, name, chevron. Opens TVs & settings.
            KeyButton(
                accent, label = vm.active?.let { "TV: ${it.name}" } ?: "Choose a TV",
                modifier = at(460f, 190f, 508f, 124f),
                state = if (vm.active != null) linkWord(vm.link) else null,
                actionLabel = "open TVs and settings", pressedScale = 0.98f,
                onPress = { sheet = Page.Devices },
            ) { c, down ->
                Row(Modifier.padding(horizontal = bdp(32f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(bdp(22f))) {
                    val off = vm.active == null || vm.link == Link.ERROR
                    val dotAlpha = when {
                        off -> 0.4f
                        vm.link == Link.CONNECTING -> blink.value
                        else -> 0.16f + 0.84f * lit.value
                    }
                    Box(Modifier.size(bdp(22f))
                        .drawBehind {
                            if (!off && lit.value > 0f) drawCircle(Brush.radialGradient(
                                listOf(accent.copy(0.6f * lit.value), Color.Transparent), center, size.minDimension / 2f + 29f * k * density),
                                size.minDimension / 2f + 29f * k * density)
                            if (off) drawCircle(Color(0xFF666666).copy(alpha = dotAlpha))
                            else drawCircle(Brush.radialGradient(
                                0f to mix(accent, 0.55f, Color.White), 0.5f to accent, 1f to mix(accent, 0.45f, Color.Black),
                                center = Offset(size.width * 0.4f, size.height * 0.35f), radius = size.minDimension * 0.75f), alpha = dotAlpha)
                        })
                    Text(vm.active?.name ?: "Choose a TV", Modifier.widthIn(max = bdp(300f)), color = T.Ink, fontSize = bsp(40f),
                        fontWeight = FontWeight.SemiBold, letterSpacing = bsp(-0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LineIcon(Ico.CHEVRON_R, if (down) accent else Color(0xFF9A9AA0), bdp(40f), stroke = 2.2f)
                }
            }

            // Power: its icon is always in the accent colour.
            KeyButton(accent, "Power", at(818f, 190f, 124f, 124f), restColor = accent, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, bdp(50f))
            }

            Box(at(460f, 770f, 780f, 780f), contentAlignment = Alignment.Center) {
                Clickpad(
                    size = (780f * k).dp, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                    toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                    onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                    onTick = { vm.scrubTick(it) },
                    onVoice = { vm.feel.fire(com.krishna.miniremote.Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                    offline = offline,
                    onOffline = { vm.feel.fire(com.krishna.miniremote.Feel.PRESS); sheet = Page.Devices },
                )
            }

            // Big keys (.ib2.lg): 196 across, 70px icons at stroke 2.
            KeyButton(accent, "Back", at(190f, 1370f, 196f, 196f), onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.BACK, c, bdp(70f), stroke = 2f) }
            KeyButton(accent, "Play or pause", at(460f, 1370f, 196f, 196f), onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, bdp(70f), stroke = 2f) }
            KeyButton(accent, "Home", at(730f, 1370f, 196f, 196f), onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, bdp(70f), stroke = 2f) }

            // Type (.ipill) and the volume rocker (.ivol).
            KeyButton(accent, "Type on TV", at(250f, 1700f, 360f, 130f), actionLabel = "open keyboard", pressedScale = 0.97f, onPress = { showKeys = true }) { c, _ ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(bdp(20f))) {
                    LineIcon(Ico.KEYBOARD, c, bdp(48f))
                    Text("Type", color = T.Ink, fontSize = bsp(38f), fontWeight = FontWeight.SemiBold)
                }
            }
            VolumePill(
                accent, vm.muted, at(655f, 1700f, 390f, 130f),
                onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) },
                onMute = { vm.press(RemoteKey.MUTE) },
                onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) },
            )
        }
    }

    sheet?.let { start -> RemoteSheet(vm, accent, start) { sheet = null } }
    if (showKeys) KeyboardSheet(vm, accent) { showKeys = false }
    // Pairing normally happens inside the sheet; this covers a pairing that starts with it closed.
    if (sheet == null) vm.pairPrompt?.let { PairCodeDialog(vm, it, accent) }
    }
}
