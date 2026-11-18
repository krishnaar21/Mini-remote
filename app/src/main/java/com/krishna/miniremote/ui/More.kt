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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxHeight
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
        Column(Modifier.navigationBarsPadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("More", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val what = when (vm.screen) {
                RemoteVM.Screen.HOME -> "On the home screen: playback and channel keys are off."
                RemoteVM.Screen.APP -> "In an app: channel keys are off."
                RemoteVM.Screen.LIVE -> "On live TV."
                RemoteVM.Screen.UNKNOWN -> null
            }
            what?.let { Text(it, color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }

            // 1 · Controls: every key in one card (5 across), colour keys under them.
            // Playback as one bar (no circles), then the other keys as labelled tiles.
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaBar(vm)
                listOf(
                    listOf(Triple(RemoteKey.SUBTITLES, Ico.CC, "Subtitles"), Triple(RemoteKey.AUDIO, Ico.AUDIO, "Audio"),
                        Triple(RemoteKey.INFO, Ico.INFO, "Info"), Triple(RemoteKey.GUIDE, Ico.GUIDE, "Guide")),
                    listOf(Triple(RemoteKey.CH_LIST, Ico.LIST, "Channels"), Triple(RemoteKey.INPUT, Ico.INPUT, "Input"),
                        Triple(RemoteKey.MENU, Ico.MORE, "Menu"), Triple(RemoteKey.SETTINGS, Ico.GEAR, "Settings")),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (k, ico, label) -> KeyTile(vm, k, ico, label, Modifier.weight(1f)) }
                    }
                }
                val colours = listOf(RemoteKey.RED to Color(0xFFE5484D), RemoteKey.GREEN to Color(0xFF30A46C), RemoteKey.YELLOW to Color(0xFFF5D90A), RemoteKey.BLUE to Color(0xFF3E63DD))
                if (colours.any { vm.adapterHas(it.first) }) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colours.forEach { (k, c) ->
                            val on = vm.can(k)
                            Box(
                                Modifier.weight(1f).height(30.dp).clip(CircleShape).background(if (on) c else c.copy(alpha = 0.25f))
                                    .clickable(role = Role.Button) { if (on) vm.press(k) else vm.explain(k) }.semantics { contentDescription = k.word },
                            )
                        }
                    }
                }
            }

            // 2 · Inputs: compact tiles, two across; the one on screen is ticked.
            if (vm.inputs.isNotEmpty()) {
                SectionTitle("Inputs")
                Group {
                    val now = vm.nowOn?.name.orEmpty()
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.inputs.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { input ->
                                    val n = input.name.lowercase()
                                    val ico = if ("hdmi" in n || "av" in n || "component" in n) Ico.INPUT else Ico.TV
                                    val current = now.isNotEmpty() && (now.equals(input.name, true) || n.startsWith(now.lowercase()))
                                    Row(
                                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                            .background(if (current) Color.White.copy(0.14f) else M.High)
                                            .clickable(role = Role.Button, onClickLabel = "switch to ${input.name}") { vm.switchInput(input) }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        LineIcon(ico, T.Ink, 18.dp)
                                        Text(input.name, color = T.Ink, fontSize = 13.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (current) LineIcon(Ico.CHECK, T.Ink, 15.dp)
                                    }
                                }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 3 · Sleep timer: one row.
            SectionTitle("Sleep timer")
            Group {
                val at = vm.sleepAt
                if (at != null) {
                    // Counting down: how long is left, big; when; and a way to stop it.
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(at) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
                    val left = ((at - now) / 1000).coerceAtLeast(0)
                    val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { LineIcon(Ico.SLEEP, accent, 22.dp) }
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text("%d:%02d".format(left / 60, left % 60), color = T.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
                            Text("TV turns off at $clock", color = Muted, fontSize = 12.5.sp)
                        }
                        Box(Modifier.height(36.dp).clip(CircleShape).background(M.High).clickable(role = Role.Button) { vm.setSleep(null) }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center) { Text("Cancel", color = T.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) }
                    }
                    Text("Keep Mini Remote open (it can be in the background).", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                } else {
                    Text("Turn the TV off after", color = T.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 90).forEach { m ->
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(CircleShape).background(M.High)
                                .clickable(role = Role.Button, enabled = vm.can(RemoteKey.POWER)) { vm.setSleep(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (m < 60) "$m min" else "${m / 60}${if (m % 60 == 30) ".5" else ""} h", color = if (vm.can(RemoteKey.POWER)) T.Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
        // Always at hand, under the scrolling keys: move around and select without going back Home.
        Box(Modifier.fillMaxWidth().background(SheetBg).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) { NavPad(vm) }
        }
    }
}

/**
 * A compact navigation bar: Back · ◀ · ▲ · OK · ▼ · ▶. Used where the dial isn't on screen
 * (More, Search) so you can move around the TV and select without leaving.
 */
@Composable
internal fun NavPad(vm: RemoteVM) {
    val keys = listOf(
        RemoteKey.BACK to Ico.BACK, RemoteKey.LEFT to Ico.LEFT, RemoteKey.UP to Ico.UP,
        RemoteKey.OK to null, RemoteKey.DOWN to Ico.DOWN, RemoteKey.RIGHT to Ico.RIGHT,
    )
    Row(Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(M.High), verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { i, (k, ico) ->
            val on = vm.can(k)
            val c = if (on) T.Ink else T.Ink.copy(alpha = 0.3f)
            if (i == 1) Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.08f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = k.word) { if (on) vm.press(k) else vm.explain(k) }
                    .semantics { contentDescription = k.word },
                contentAlignment = Alignment.Center,
            ) {
                if (ico != null) LineIcon(ico, c, 21.dp)
                else Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = if (on) 0.12f else 0.05f)), contentAlignment = Alignment.Center) {
                    Text("OK", color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Rewind · Play / pause · Forward · Mute in one capsule: playback without a row of circles. */
@Composable
private fun MediaBar(vm: RemoteVM) {
    val keys = listOf(
        Triple(RemoteKey.REWIND, Ico.REWIND, "Rewind"),
        Triple(RemoteKey.PLAY_PAUSE, if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, if (vm.playing) "Pause" else "Play"),
        Triple(RemoteKey.FAST_FORWARD, Ico.FFWD, "Forward"),
        Triple(RemoteKey.MUTE, if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) "Unmute" else "Mute"),
    )
    Row(Modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(M.High), verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { i, (k, ico, label) ->
            val on = vm.can(k)
            if (i > 0) Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.08f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = label) { if (on) vm.press(k) else vm.explain(k) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { LineIcon(ico, if (on) T.Ink else T.Ink.copy(alpha = 0.3f), 22.dp) }
        }
    }
}

/** A labelled key tile (rounded square): icon over a word; dimmed when it doesn't fit. */
@Composable
private fun KeyTile(vm: RemoteVM, k: RemoteKey, ico: Ico, label: String, modifier: Modifier) {
    val on = vm.can(k)
    Box(
        modifier.height(68.dp).clip(RoundedCornerShape(18.dp)).background(M.High)
            .clickable(role = Role.Button, onClickLabel = label) { if (on) vm.press(k) else vm.explain(k) }
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Icon and word as one group, centred in the tile.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LineIcon(ico, if (on) T.Ink else T.Ink.copy(alpha = 0.3f), 20.dp)
            Text(label, color = if (on) Muted else Muted.copy(alpha = 0.4f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}
