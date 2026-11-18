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
import androidx.compose.ui.graphics.nativeCanvas
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
 * More: the keys not on Home's pages (subtitles, audio, channel list, menu, the colour keys),
 * the TV's inputs by name, quick access when it's set to live here, and arrows to move around.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(vm: RemoteVM, accent: Color, openApps: (Boolean) -> Unit = {}, onClose: () -> Unit) {
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(vm.activeId, vm.link) { vm.loadInputs() }
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            val what = when (vm.screen) {
                RemoteVM.Screen.HOME -> "On the home screen: playback and channel keys are off."
                RemoteVM.Screen.APP -> "In an app: channel keys are off."
                RemoteVM.Screen.LIVE -> "On live TV."
                RemoteVM.Screen.UNKNOWN -> null
            }
            SheetTitle("More", what, Modifier.padding(top = 4.dp))

            // Quick access, when it lives here (Settings › Remote › Quick access).
            if (vm.settings.appsIn == "more") {
                SectionTitle("Quick access")
                AppSquares(vm) { pick -> onClose(); openApps(pick) }
            }

            // Keys not on Home's pages: subtitles, audio, channel list, menu; colour keys under them.
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(RemoteKey.SUBTITLES, Ico.CC, "Subtitles"), Triple(RemoteKey.AUDIO, Ico.AUDIO, "Audio"),
                        Triple(RemoteKey.CH_LIST, Ico.LIST, "Channels"), Triple(RemoteKey.MENU, Ico.MORE, "Menu"),
                    ).forEach { (k, ico, label) -> KeyTile(vm, k, ico, label, Modifier.weight(1f)) }
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
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        vm.inputs.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { input ->
                                    val n = input.name.lowercase()
                                    val ico = if ("hdmi" in n || "av" in n || "component" in n) Ico.INPUT else Ico.TV
                                    val current = now.isNotEmpty() && (now.equals(input.name, true) || n.startsWith(now.lowercase()))
                                    Row(
                                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                            .background(if (current) T.Ink.copy(0.14f) else M.High)
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
            if (i == 1) Box(Modifier.width(1.dp).height(24.dp).background(T.Ink.copy(alpha = 0.08f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = k.word) { if (on) vm.press(k) else vm.explain(k) }
                    .semantics { contentDescription = k.word },
                contentAlignment = Alignment.Center,
            ) {
                if (ico != null) LineIcon(ico, c, 21.dp)
                else Box(Modifier.size(38.dp).clip(CircleShape).background(T.Ink.copy(alpha = if (on) 0.12f else 0.05f)), contentAlignment = Alignment.Center) {
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
            if (i > 0) Box(Modifier.width(1.dp).height(24.dp).background(T.Ink.copy(alpha = 0.08f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = label) { if (on) vm.press(k) else vm.explain(k) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { LineIcon(ico, if (on) T.Ink else T.Ink.copy(alpha = 0.3f), 22.dp) }
        }
    }
}

/**
 * Quick access in More: rounded squares the size of the key tiles (4 across, 68 dp tall),
 * up to seven apps and All apps.
 */
@Composable
private fun AppSquares(vm: RemoteVM, openApps: (Boolean) -> Unit) {
    val pinned = vm.shortcuts.filter { it.pinned }
    val ready = vm.active != null && vm.link == com.krishna.miniremote.Link.READY
    val shape = RoundedCornerShape(18.dp)
    // Two pages (swipe across; no dots here): seven apps and All apps, then eight more.
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 12.dp), pageSpacing = 16.dp) { part ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            com.krishna.miniremote.ui.appCells(pinned.size, part).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cell ->
                        val m = Modifier.weight(1f).height(68.dp)
                        when {
                            cell == -1 -> Box(m.clip(shape).background(M.High).clickable(role = Role.Button, onClickLabel = "all apps") { openApps(false) }
                                .semantics(mergeDescendants = true) { contentDescription = "All apps" }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LineIcon(Ico.GRID, T.Ink, 20.dp)
                                    Text("All apps", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                            cell != null -> { val s = pinned[cell]; AppCircle(vm, s, 68.dp, shape, m) { if (ready) vm.launch(s) else vm.explain(RemoteKey.HOME) } }
                            else -> Box(m.clip(shape).border(1.dp, T.Ink.copy(alpha = 0.10f), shape).clickable(onClickLabel = "add apps") { openApps(true) },
                                contentAlignment = Alignment.Center) { LineIcon(Ico.PLUS, T.Ink.copy(alpha = 0.3f), 16.dp) }
                        }
                    }
                }
            }
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

/** The sleep timer: a countdown with Cancel when set, and 15 / 30 / 60 / 90 minute choices. */
@Composable
internal fun SleepCard(vm: RemoteVM, accent: Color, card: Boolean = true) {
    val wrap: @Composable (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) -> Unit =
        if (card) { c -> Group(c) } else { c -> Column(Modifier.fillMaxWidth(), content = c) }
    wrap {
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

/** The sleep timer on its own (from the circle on Home's second page). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SleepArt(accent, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp).height(120.dp))
            SheetTitle("Sleep timer", "Pick when the TV turns itself off.", Modifier.padding(bottom = 8.dp), center = true, small = true)
            SleepCard(vm, accent, card = false)
        }
    }
}

/** A TV resting under a crescent moon, with a few stars and a soft glow: the sleep timer's picture. */
@Composable
private fun SleepArt(accent: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = "" }) {
        val cx = size.width / 2f
        val u = size.height / 120f
        // Glow behind the moon.
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(cx + 26 * u, 30 * u), radius = 60 * u),
            radius = 60 * u, center = androidx.compose.ui.geometry.Offset(cx + 26 * u, 30 * u))
        // Crescent moon: a disc with a bite taken out.
        val moon = androidx.compose.ui.geometry.Offset(cx + 26 * u, 30 * u)
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(moon, 20 * u))
            val bite = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(moon.x + 10 * u, moon.y - 8 * u), 18 * u)) }
            op(this, bite, androidx.compose.ui.graphics.PathOperation.Difference)
        }
        drawPath(path, accent)
        // Stars.
        listOf(Triple(-52f, 18f, 2.6f), Triple(-30f, 6f, 1.8f), Triple(58f, 8f, 2f), Triple(70f, 46f, 1.6f), Triple(-64f, 46f, 1.6f)).forEach { (dx, dy, r) ->
            drawCircle(T.Ink.copy(alpha = 0.7f), r * u, androidx.compose.ui.geometry.Offset(cx + dx * u, dy * u))
        }
        // The TV: screen and stand, dark with a thin outline.
        val w = 96 * u; val h = 54 * u; val top = 54 * u
        val tl = androidx.compose.ui.geometry.Offset(cx - w / 2, top)
        drawRoundRect(Color(0xFF1C1C1E), tl, androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(8 * u))
        drawRoundRect(T.Ink.copy(alpha = 0.22f), tl, androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(8 * u),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * u))
        drawLine(T.Ink.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(cx - 18 * u, top + h + 8 * u), androidx.compose.ui.geometry.Offset(cx + 18 * u, top + h + 8 * u), 3 * u,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // "z z" drifting off the screen.
        val z = android.graphics.Paint().apply { color = android.graphics.Color.argb(150, 255, 255, 255); textSize = 13 * u; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        drawContext.canvas.nativeCanvas.drawText("z", cx - 8 * u, top + h / 2 + 5 * u, z)
        z.textSize = 10 * u
        drawContext.canvas.nativeCanvas.drawText("z", cx + 6 * u, top + h / 2 - 4 * u, z)
    }
}
