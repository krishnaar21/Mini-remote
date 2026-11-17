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

@Composable
fun HomeScreen(
    vm: RemoteVM,
    accent: Color,
    deck: Deck,
    onDeck: (Deck) -> Unit,
    openDevices: () -> Unit,
    openKeys: () -> Unit,
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

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = NAV_H + 24.dp)) {
        // Greeting and TV on the left; search and power on the right. Nothing else up top.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                val first = vm.settings.profileName.trim().substringBefore(' ')
                Text(if (first.isEmpty()) "Hello!" else "Hello, $first", color = T.Ink, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvLine(vm, openDevices)
            }
            KeyButton(accent, "Search and type", Modifier.size(46.dp), actionLabel = "search or type on TV", onPress = openKeys) { c, _ ->
                LineIcon(Ico.SEARCH, c, 19.dp)
            }
            KeyButton(accent, "Power", Modifier.size(46.dp), restColor = accent, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 19.dp)
            }
        }

        // The control: dial, trackpad or numbers, as big as the space allows. Swipe to change it.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(swipeable), contentAlignment = Alignment.Center) {
            val pad: Dp = minOf(maxWidth - 8.dp, maxHeight - 60.dp).coerceIn(150.dp, 320.dp)
            AnimatedContent(
                deck,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                },
                label = "deck",
            ) { d ->
                val offline = vm.active == null || vm.link == Link.ERROR
                when (d) {
                    Deck.NUMPAD -> Numpad(vm, accent, Modifier.width(minOf(maxWidth, 300.dp)).height(pad))
                    Deck.DIAL -> CompositionLocalProvider(LocalK provides pad.value / 780f) {
                        Clickpad(
                            size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                            toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                            onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                            onTick = { vm.scrubTick(it) },
                            onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                            offline = offline,
                            onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                        )
                    }
                    Deck.TRACKPAD -> CompositionLocalProvider(LocalK provides pad.value / 780f) {
                        Trackpad(
                            size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                            onKey = { vm.press(it, quiet = it == RemoteKey.OK) },
                            onTick = { vm.scrubTick(it) },
                            onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                            onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                        )
                    }
                }
            }
            DeckDots(deck, accent, Modifier.align(Alignment.BottomCenter)) { onDeck(it); vm.feel.fire(Feel.TICK) }
        }

        Cluster(vm, accent, Modifier.fillMaxWidth())
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

/**
 * Two calm rows: Back · Home · Play/Pause, then the volume pill (− mute +) beside the
 * channel pill (− CH +).
 */
@Composable
private fun Cluster(vm: RemoteVM, accent: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            KeyButton(accent, "Back", Modifier.size(54.dp), onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.BACK, c, 21.dp) }
            KeyButton(accent, "Home", Modifier.size(64.dp), restColor = Color.White, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, 24.dp) }
            KeyButton(accent, "Play or pause", Modifier.size(54.dp), onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, 20.dp) }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VolumePill(accent, vm.muted, Modifier.weight(1f).fillMaxHeight(),
                onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) },
                onMute = { vm.press(RemoteKey.MUTE) },
                onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) })
            ChannelPill(accent, Modifier.weight(1f).fillMaxHeight(),
                onDown = { vm.press(RemoteKey.CH_DOWN) }, onUp = { vm.press(RemoteKey.CH_UP) })
        }
    }
}

/** Three quiet dots under the control: which one is showing. Tap one to jump to it. */
@Composable
private fun DeckDots(deck: Deck, accent: Color, modifier: Modifier, onPick: (Deck) -> Unit) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Deck.entries.forEach { d ->
            val sel = d == deck
            Box(
                Modifier.size(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(d) }
                    .semantics { contentDescription = d.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (sel) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (sel) accent else Color(0xFF4A4A50)))
            }
        }
    }
}
