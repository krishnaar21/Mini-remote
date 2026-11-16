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
    var showDevices by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }

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
                    onPress = { showDevices = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(9.dp).background(statusColor(vm.link, accent).copy(alpha = if (vm.active == null) 0.35f else 1f), CircleShape))
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
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton(CircleShape, accent, "Volume down", Modifier.size(56.dp), repeat = true, onPress = { vm.press(RemoteKey.VOL_DOWN, quiet = true) }) { LineIcon(Ico.MINUS, it) }
                    KeyButton(CircleShape, accent, "Mute", Modifier.size(56.dp), onPress = { vm.press(RemoteKey.MUTE) }) { LineIcon(Ico.SPEAKER, it) }
                    KeyButton(CircleShape, accent, "Volume up", Modifier.size(56.dp), repeat = true, onPress = { vm.press(RemoteKey.VOL_UP, quiet = true) }) { LineIcon(Ico.PLUS, it) }
                }
            }
        }
    }

    if (showDevices) DevicesSheet(vm, accent) { showDevices = false }
    if (showKeys) KeyboardSheet(vm, accent) { showKeys = false }
}
