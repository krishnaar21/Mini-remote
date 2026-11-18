package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
            val what = when (vm.screen) {
                RemoteVM.Screen.HOME -> "On the home screen: playback and channel keys are off."
                RemoteVM.Screen.APP -> "In an app: channel keys are off."
                RemoteVM.Screen.LIVE -> "On live TV."
                RemoteVM.Screen.UNKNOWN -> null
            }
            what?.let { Text(it, color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }

            SectionTitle("Playback")
            Group {
                KeyRow(vm, accent, listOf(
                    Triple(RemoteKey.REWIND, Ico.REWIND, "Rewind"),
                    Triple(RemoteKey.PLAY_PAUSE, if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, if (vm.playing) "Pause" else "Play"),
                    Triple(RemoteKey.FAST_FORWARD, Ico.FFWD, "Forward"),
                ))
            }

            SectionTitle("TV")
            Group {
                KeyRow(vm, accent, listOf(
                    Triple(RemoteKey.SETTINGS, Ico.GEAR, "Menu"), Triple(RemoteKey.INFO, Ico.INFO, "Info"),
                    Triple(RemoteKey.GUIDE, Ico.GUIDE, "Guide"), Triple(RemoteKey.CH_LIST, Ico.LIST, "Channels"),
                ))
            }

            SectionTitle("Captions and audio")
            Group {
                KeyRow(vm, accent, listOf(Triple(RemoteKey.SUBTITLES, Ico.CC, "Subtitles"), Triple(RemoteKey.AUDIO, Ico.AUDIO, "Audio")))
            }

            val colours = listOf(RemoteKey.RED to Color(0xFFE5484D), RemoteKey.GREEN to Color(0xFF30A46C), RemoteKey.YELLOW to Color(0xFFF5D90A), RemoteKey.BLUE to Color(0xFF3E63DD))
            if (colours.any { vm.can(it.first) }) {
                SectionTitle("Colour keys")
                Group {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colours.forEach { (k, c) ->
                            val on = vm.can(k)
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(CircleShape).background(if (on) c else c.copy(alpha = 0.25f))
                                    .clickable(enabled = on, role = Role.Button) { vm.press(k) }.semantics { contentDescription = k.word },
                            )
                        }
                    }
                }
            }

            SectionTitle("Inputs")
            Group {
                if (vm.inputs.isEmpty()) {
                    InputRow(Ico.INPUT, "Next input", "This TV doesn't list its inputs: each tap moves to the next.", current = false, last = true) { vm.press(RemoteKey.INPUT) }
                } else {
                    val now = vm.nowOn?.name.orEmpty()
                    vm.inputs.forEachIndexed { i, input ->
                        val n = input.name.lowercase()
                        val ico = if ("hdmi" in n || "av" in n || "component" in n) Ico.INPUT else Ico.TV
                        val current = now.isNotEmpty() && (now.equals(input.name, true) || n.startsWith(now.lowercase()))
                        InputRow(ico, input.name, if (current) "On screen now" else null, current, last = i == vm.inputs.lastIndex) { vm.switchInput(input) }
                    }
                }
            }

            SectionTitle("Sleep timer")
            Group {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = if (vm.sleepAt == null) 16.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 15, 30, 60, 90).forEach { m ->
                        val on = m == null && vm.sleepAt == null
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(CircleShape).background(if (on) Color.White.copy(0.16f) else M.High)
                                .then(if (on) Modifier.border(1.dp, Color.White.copy(0.5f), CircleShape) else Modifier)
                                .clickable(role = Role.Button) { vm.setSleep(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (m == null) "Off" else "$m m", color = T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                vm.sleepAt?.let { at ->
                    val t = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
                    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineIcon(Ico.SLEEP, T.Dim, 16.dp)
                        Text("TV turns off at $t. Keep Mini Remote open.", color = Muted, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

/** A row of round keys with labels, inside a card; keys that don't fit what's on the TV are dimmed. */
@Composable
private fun KeyRow(vm: RemoteVM, accent: Color, keys: List<Triple<RemoteKey, Ico, String>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        keys.forEach { (k, ico, label) ->
            val on = vm.can(k)
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                KeyButton(accent, label, Modifier.size(54.dp), repeat = k == RemoteKey.REWIND || k == RemoteKey.FAST_FORWARD,
                    pressedScale = 0.94f, enabled = on, onPress = { vm.press(k) }) { c, _ -> LineIcon(ico, c, 21.dp) }
                Text(label, color = if (on) Muted else Muted.copy(alpha = 0.4f), fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** One input: its icon, name, a note, and a check on the one on screen. */
@Composable
private fun InputRow(ico: Ico, name: String, note: String?, current: Boolean, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = "switch to $name", onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(M.High), contentAlignment = Alignment.Center) { LineIcon(ico, T.Ink, 19.dp) }
        Column(Modifier.weight(1f)) {
            Text(name, color = T.Ink, fontSize = 15.sp)
            note?.let { Text(it, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (current) LineIcon(Ico.CHECK, T.Ink, 18.dp)
    }
    if (!last) Divider()
}
