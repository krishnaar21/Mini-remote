package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM

/**
 * More: the keys that don't fit on Home (menu, info, guide, subtitles, audio, rewind / fast
 * forward, channel list, the four colour keys), the TV's inputs by name, and a sleep timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(vm.activeId, vm.link) { vm.loadInputs() }
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("More", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            SectionTitle("Keys")
            val keys = listOf(
                Triple(RemoteKey.SETTINGS, Ico.GEAR, "Menu"), Triple(RemoteKey.INFO, Ico.INFO, "Info"),
                Triple(RemoteKey.GUIDE, Ico.GUIDE, "Guide"), Triple(RemoteKey.CH_LIST, Ico.LIST, "Channels"),
                Triple(RemoteKey.SUBTITLES, Ico.CC, "Subtitles"), Triple(RemoteKey.AUDIO, Ico.AUDIO, "Audio"),
                Triple(RemoteKey.REWIND, Ico.REWIND, "Rewind"), Triple(RemoteKey.FAST_FORWARD, Ico.FFWD, "Forward"),
            )
            keys.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (k, ico, label) ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            KeyButton(accent, label, Modifier.size(56.dp), repeat = k == RemoteKey.REWIND || k == RemoteKey.FAST_FORWARD,
                                pressedScale = 0.94f, onPress = { vm.press(k) }) { c, _ -> LineIcon(ico, c, 21.dp) }
                            Text(label, color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            // The four colour keys (teletext, guides, some apps).
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(RemoteKey.RED to Color(0xFFE5484D), RemoteKey.GREEN to Color(0xFF30A46C), RemoteKey.YELLOW to Color(0xFFF5D90A), RemoteKey.BLUE to Color(0xFF3E63DD))
                    .forEach { (k, c) ->
                        Box(
                            Modifier.width(64.dp).height(30.dp).clip(CircleShape).background(c)
                                .clickable(role = Role.Button) { vm.press(k) }.semantics { contentDescription = k.word },
                        )
                    }
            }

            SectionTitle("Inputs")
            if (vm.inputs.isEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    KeyButton(accent, "Input", Modifier.size(56.dp), pressedScale = 0.94f, onPress = { vm.press(RemoteKey.INPUT) }) { c, _ -> LineIcon(Ico.INPUT, c, 21.dp) }
                    Text("This TV doesn't list its inputs. Press Input to cycle through them.", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.inputs.forEach { i ->
                        Box(
                            Modifier.height(40.dp).clip(CircleShape).background(M.High).clickable(role = Role.Button) { vm.switchInput(i) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(i.name, color = T.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }

            SectionTitle("Sleep timer")
            val opts = listOf<Int?>(null, 15, 30, 60, 90)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opts.forEach { m ->
                    val on = if (m == null) vm.sleepAt == null else false
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (on) Color.White.copy(0.16f) else M.High)
                            .then(if (on) Modifier.border(1.dp, Color.White.copy(0.5f), CircleShape) else Modifier)
                            .clickable(role = Role.Button) { vm.setSleep(m) },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (m == null) "Off" else "$m m", color = T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
            vm.sleepAt?.let { at ->
                val t = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(Ico.SLEEP, T.Dim, 16.dp)
                    Text("TV turns off at $t. Keep Mini Remote open.", color = Muted, fontSize = 12.5.sp)
                }
            }
        }
    }
}
