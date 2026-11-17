package com.krishna.miniremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.Feel
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlin.math.abs

/** What fills the bottom of Home. Swipe beside it, or on the switcher, to change. */
enum class Deck(val label: String, val ico: Ico) {
    DIAL("Dial", Ico.DIAL), TRACKPAD("Trackpad", Ico.TRACKPAD), NUMPAD("Number pad", Ico.NUMPAD)
}

private data class Quick(val key: RemoteKey, val ico: Ico, val label: String)

private val QUICK = listOf(
    Quick(RemoteKey.GUIDE, Ico.GUIDE, "TV Guide"),
    Quick(RemoteKey.CH_LIST, Ico.LIST, "CH. List"),
    Quick(RemoteKey.INFO, Ico.INFO, "Info"),
    Quick(RemoteKey.INPUT, Ico.INPUT, "Input"),
    Quick(RemoteKey.MENU, Ico.DISPLAY, "Display"),
    Quick(RemoteKey.AUDIO, Ico.AUDIO, "Audio"),
)

@Composable
fun HomeScreen(
    vm: RemoteVM,
    accent: Color,
    deck: Deck,
    onDeck: (Deck) -> Unit,
    openDevices: () -> Unit,
    openKeys: () -> Unit,
    openSearch: () -> Unit,
    openApps: () -> Unit,
) {
    val current by rememberUpdatedState(deck)
    fun swipe(by: Int) {
        val all = Deck.entries
        onDeck(all[(all.indexOf(current) + by + all.size) % all.size])
        vm.feel.fire(Feel.DETENT)
    }
    // Horizontal swipe anywhere the dial or trackpad doesn't take the finger itself.
    val swipeable = Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 48.dp.toPx()) swipe(if (total < 0) 1 else -1) },
        ) { change, dx -> change.consume(); total += dx }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = NAV_H + 20.dp)) {
        // Hello! + TV, power on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val first = vm.settings.profileName.trim().substringBefore(' ')
                Text(if (first.isEmpty()) "Hello!" else "Hello, $first!", color = T.Ink, fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvLine(vm, openDevices)
            }
            KeyButton(accent, "Power", Modifier.size(54.dp), restColor = accent, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 22.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        SearchBar(accent, openSearch) { vm.press(RemoteKey.VOICE) }
        Spacer(Modifier.height(14.dp))
        QuickControls(vm, accent)
        Spacer(Modifier.height(16.dp))
        Cluster(vm, accent, openApps, Modifier.fillMaxWidth().height(148.dp))

        // The control deck: dial, trackpad or numbers.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(swipeable), contentAlignment = Alignment.Center) {
            val side = 44.dp
            val pad: Dp = minOf(maxWidth - side * 2 - 20.dp, maxHeight - 12.dp).coerceIn(140.dp, 300.dp)
            AnimatedContent(
                deck,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                },
                label = "deck",
            ) { d ->
                when (d) {
                    Deck.NUMPAD -> Numpad(vm, accent, Modifier.width(minOf(maxWidth, 320.dp)).height(pad))
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MediaKey(accent, "Rewind", Ico.REWIND, side) { vm.press(RemoteKey.REWIND) }
                        val offline = vm.active == null || vm.link == Link.ERROR
                        CompositionLocalProvider(LocalK provides pad.value / 780f) {
                            if (d == Deck.DIAL) Clickpad(
                                size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                                toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                                onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                                onTick = { vm.scrubTick(it) },
                                onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                offline = offline,
                                onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                            ) else Trackpad(
                                size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                                onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                                onTick = { vm.scrubTick(it) },
                                onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                            )
                        }
                        MediaKey(accent, "Fast forward", Ico.FFWD, side) { vm.press(RemoteKey.FAST_FORWARD) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DeckSwitcher(deck, accent, Modifier.align(Alignment.CenterHorizontally).then(swipeable), onPick = { onDeck(it); vm.feel.fire(Feel.TICK) }, openKeys = openKeys)
    }
}

/** "📺 Living room TV ▾" in green when connected; opens the TV list. */
@Composable
private fun TvLine(vm: RemoteVM, openDevices: () -> Unit) {
    val d = vm.active
    val on = d != null && vm.link == Link.READY
    val col = when {
        d == null -> T.Dim
        on -> Color(0xFF4DDC88)
        vm.link == Link.CONNECTING -> Color(0xFFFFD60A)
        else -> Color(0xFFFF8A8A)
    }
    Row(
        Modifier.padding(top = 4.dp).clip(CircleShape)
            .clickable(onClickLabel = "choose a TV", role = Role.Button, onClick = openDevices)
            .semantics(mergeDescendants = true) {
                contentDescription = d?.let { "TV: ${it.name}" } ?: "Choose a TV"
                if (d != null) stateDescription = linkWord(vm.link)
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Ico.TV, col, 15.dp)
        Text(d?.name ?: "Choose a TV", color = col, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (d != null && !on) Text("· ${linkWord(vm.link)}", color = T.Dim, fontSize = 12.5.sp, maxLines = 1)
        LineIcon(Ico.DOWN, T.Dim, 14.dp)
    }
}

@Composable
private fun SearchBar(accent: Color, onOpen: () -> Unit, onMic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).pressable(HOLLOW, HOLLOW, null, label = "search", onClick = onOpen)
            .semantics { contentDescription = "Search for movies, shows, and apps" }
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Ico.SEARCH, T.Dim, 19.dp)
        Text("Search for movies, shows, and apps", color = T.Dim, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "voice search", role = Role.Button, onClick = onMic)
                .semantics { contentDescription = "Voice search" },
            contentAlignment = Alignment.Center,
        ) { LineIcon(Ico.MIC, accent, 19.dp) }
    }
}

@Composable
private fun QuickControls(vm: RemoteVM, accent: Color) {
    Column(Modifier.fillMaxWidth().softFace(CARD, corner = 22.dp, dpUnits = true).padding(vertical = 12.dp)) {
        Text("Quick Controls", color = T.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2E2E31)))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QUICK.forEach { q ->
                KeyButton(accent, q.label, Modifier.height(42.dp), pressedScale = 0.97f, onPress = { vm.press(q.key) }) { c, _ ->
                    Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineIcon(q.ico, c, 17.dp)
                        Text(q.label, color = T.Ink, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** VOL rocker | back, mute, HOME, play, apps | CH rocker. */
@Composable
private fun Cluster(vm: RemoteVM, accent: Color, openApps: () -> Unit, modifier: Modifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VerticalRocker(accent, "VOL", "Volume up", "Volume down", Ico.PLUS, Ico.MINUS, Modifier.width(60.dp).fillMaxHeight(),
            onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) }, onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) })
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)) {
            val small = Modifier.size(50.dp)
            KeyButton(accent, "Back", small.align(Alignment.TopStart), onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.BACK, c, 20.dp) }
            KeyButton(accent, "Mute", small.align(Alignment.BottomStart), state = if (vm.muted) "Muted" else null,
                onPress = { vm.press(RemoteKey.MUTE) }) { c, _ ->
                LineIcon(if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, if (vm.muted) accent else c, 20.dp)
            }
            KeyButton(accent, "Home", Modifier.size(78.dp).align(Alignment.Center), restColor = Color.White,
                onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, 28.dp) }
            KeyButton(accent, "Play or pause", small.align(Alignment.TopEnd), onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, 19.dp) }
            KeyButton(accent, "Apps and channels", small.align(Alignment.BottomEnd), actionLabel = "open channels", onPress = openApps) { c, _ ->
                LineIcon(Ico.GRID, c, 19.dp)
            }
        }
        VerticalRocker(accent, "CH", "Channel up", "Channel down", Ico.PLUS, Ico.MINUS, Modifier.width(60.dp).fillMaxHeight(),
            onUp = { vm.press(RemoteKey.CH_UP) }, onDown = { vm.press(RemoteKey.CH_DOWN) })
    }
}

@Composable
private fun MediaKey(accent: Color, label: String, ico: Ico, size: Dp, onPress: () -> Unit) {
    KeyButton(accent, label, Modifier.size(size), onPress = onPress) { c, _ -> LineIcon(ico, c, 17.dp, stroke = 1.7f) }
}

/** Small pill under the deck: dial, trackpad, numbers, and the keyboard. */
@Composable
private fun DeckSwitcher(deck: Deck, accent: Color, modifier: Modifier, onPick: (Deck) -> Unit, openKeys: () -> Unit) {
    Row(
        modifier.height(48.dp).softFace(NAV_FACE, dpUnits = true).padding(5.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Deck.entries.forEach { d ->
            val sel = d == deck
            Box(
                Modifier.width(54.dp).fillMaxHeight()
                    .then(if (sel) Modifier.softFace(HOLLOW, sheen = false, dpUnits = true) else Modifier)
                    .clip(CircleShape)
                    .selectable(sel, role = Role.Tab) { onPick(d) }
                    .semantics { contentDescription = d.label },
                contentAlignment = Alignment.Center,
            ) { LineIcon(d.ico, if (sel) accent else T.Dim, 20.dp, stroke = if (d == Deck.NUMPAD) 3.2f else 1.8f) }
        }
        Box(
            Modifier.width(54.dp).fillMaxHeight().clip(CircleShape)
                .clickable(onClickLabel = "type on TV", role = Role.Button, onClick = openKeys)
                .semantics { contentDescription = "Keyboard" },
            contentAlignment = Alignment.Center,
        ) { LineIcon(Ico.KEYBOARD, T.Dim, 20.dp) }
    }
}
