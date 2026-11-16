package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
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
    BoxWithConstraints(Modifier.fillMaxSize().background(T.Bg).drawBehind { drawRect(grainBrush) }.systemBarsPadding()) {
        val padSize = (maxWidth - 64.dp).coerceIn(220.dp, 340.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Top row: mic | TV pill | power
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundKey(56.dp, accent, "Voice search", onPress = { vm.press(RemoteKey.VOICE) }) { LineIcon(Ico.MIC, it) }
                KeyButton(
                    RoundedCornerShape(50), accent,
                    label = vm.active?.let { "TV: ${it.name}" } ?: "Choose a TV",
                    modifier = Modifier.weight(1f).height(56.dp),
                    state = if (vm.active != null) linkWord(vm.link) else null,
                    actionLabel = "open TVs and settings",
                    onPress = { sheet = Page.Devices },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val dotAlpha = when {
                            vm.active == null || vm.link == Link.ERROR -> 0.4f
                            vm.link == Link.CONNECTING -> blink.value
                            else -> 0.16f + 0.84f * lit.value
                        }
                        val dotColor = if (vm.active == null || vm.link == Link.ERROR) Color(0xFF666666) else accent
                        Box(Modifier.size(10.dp)
                            .drawBehind { if (lit.value > 0f) drawCircle(accent.copy(0.6f * lit.value), size.minDimension * 1.6f) }
                            .background(dotColor.copy(alpha = dotAlpha), CircleShape))
                        Text(vm.active?.name ?: "Choose a TV", color = T.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        LineIcon(Ico.CHEVRON_R, T.Dim, 16.dp)
                    }
                }
                RoundKey(56.dp, accent, "Power", onPress = { vm.press(RemoteKey.POWER) }) { LineIcon(Ico.POWER, if (it == accent) accent else T.Danger) }
            }

            Clickpad(
                size = padSize, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                onTick = { vm.scrubTick(it) },
                onVoice = { vm.feel.fire(com.krishna.miniremote.Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                offline = offline,
                onOffline = { vm.feel.fire(com.krishna.miniremote.Feel.PRESS); sheet = Page.Devices },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundKey(76.dp, accent, "Back", onPress = { vm.press(RemoteKey.BACK) }) { LineIcon(Ico.BACK, it, 28.dp) }
                RoundKey(76.dp, accent, "Play or pause", onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { LineIcon(Ico.PLAY, it, 28.dp) }
                RoundKey(76.dp, accent, "Home", onPress = { vm.press(RemoteKey.HOME) }) { LineIcon(Ico.HOME, it, 28.dp) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyButton(RoundedCornerShape(50), accent, "Type on TV", Modifier.weight(1f).height(56.dp), actionLabel = "open keyboard", onPress = { showKeys = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LineIcon(Ico.KEYBOARD, it); Text("Type", color = it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                VolumePill(
                    accent, vm.muted, Modifier.weight(1.08f).height(56.dp),
                    onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) },
                    onMute = { vm.press(RemoteKey.MUTE) },
                    onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) },
                )
            }
        }
    }

    sheet?.let { start -> RemoteSheet(vm, accent, start) { sheet = null } }
    if (showKeys) KeyboardSheet(vm, accent) { showKeys = false }
    // Pairing normally happens inside the sheet; this covers a pairing that starts with it closed.
    if (sheet == null) vm.pairPrompt?.let { PairCodeDialog(vm, it, accent) }
    }
}
