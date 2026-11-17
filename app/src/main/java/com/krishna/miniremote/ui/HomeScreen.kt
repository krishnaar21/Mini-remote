package com.krishna.miniremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.Feel
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What fills the middle of Home. Dial or trackpad comes from Settings; numbers from the tray. */
enum class Deck(val label: String, val ico: Ico) {
    DIAL("Dial", Ico.DIAL), TRACKPAD("Trackpad", Ico.TRACKPAD), NUMPAD("Number pad", Ico.NUMPAD)
}

/** How long OK waits for a second tap (double tap = mute). */
private const val DOUBLE_TAP_MS = 260L

/**
 * Minimal Home: greeting, search and power on top; the dial (or trackpad) in the middle;
 * Back · Home · Play below. Volume and channels stay out of sight: swipe up for them.
 * OK: tap = OK, double tap = mute, hold = voice search.
 */
@Composable
fun HomeScreen(
    vm: RemoteVM,
    accent: Color,
    deck: Deck,
    openDevices: () -> Unit,
    openKeys: () -> Unit,
    onTray: (open: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pendingOk = remember { arrayOfNulls<Job>(1) }
    /** A second OK within [DOUBLE_TAP_MS] mutes instead; otherwise OK goes out after that wait. */
    fun okTap() {
        val waiting = pendingOk[0]
        if (waiting?.isActive == true) {
            waiting.cancel(); pendingOk[0] = null
            vm.press(RemoteKey.MUTE)
        } else {
            pendingOk[0] = scope.launch { delay(DOUBLE_TAP_MS); pendingOk[0] = null; vm.press(RemoteKey.OK, quiet = true) }
        }
    }
    val onKey: (RemoteKey) -> Unit = { if (it == RemoteKey.OK) okTap() else vm.press(it) }
    val tray by rememberUpdatedState(onTray)
    // Swipe up anywhere the dial doesn't take the finger: volume and channels. Swipe down hides them.
    val swipe = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 40.dp.toPx()) tray(total < 0) },
        ) { change, dy -> change.consume(); total += dy }
    }
    val offline = vm.active == null || vm.link == Link.ERROR

    Column(Modifier.fillMaxSize().then(swipe).padding(horizontal = 12.dp).padding(top = 12.dp, bottom = NAV_H + 34.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                val first = vm.settings.profileName.trim().substringBefore(' ')
                Text(if (first.isEmpty()) "Hello" else "Hello, $first", color = T.Ink, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvLine(vm, openDevices)
            }
            KeyButton(accent, "Search and type", Modifier.size(48.dp), actionLabel = "search or type on TV", pressedScale = 1f, onPress = openKeys) { c, _ ->
                LineIcon(Ico.SEARCH, c, 20.dp)
            }
            KeyButton(accent, "Power", Modifier.size(48.dp), restColor = accent, pressedScale = 1f, onPress = { vm.press(RemoteKey.POWER) }) { c, _ ->
                LineIcon(Ico.POWER, c, 20.dp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val pad: Dp = minOf(maxWidth - 40.dp, maxHeight - 110.dp).coerceIn(180.dp, 330.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(pad).semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Mute") { vm.press(RemoteKey.MUTE); true },
                            CustomAccessibilityAction("Volume and channels") { tray(true); true },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(deck, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "deck") { d ->
                        CompositionLocalProvider(LocalK provides pad.value / 780f) {
                            when (d) {
                                Deck.NUMPAD -> Numpad(vm, accent, Modifier.size(pad))
                                Deck.DIAL -> Clickpad(
                                    size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, toast = vm.toast,
                                    toastIcon = vm.toastKey?.let { keyIcon(it, vm.muted) },
                                    onKey = onKey, onTick = { vm.scrubTick(it) },
                                    onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                    offline = offline, onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                )
                                Deck.TRACKPAD -> Trackpad(
                                    size = pad, accent = accent, stepDp = vm.settings.scrubStepDp, offline = offline,
                                    onKey = onKey, onTick = { vm.scrubTick(it) },
                                    onVoice = { vm.feel.fire(Feel.HEAVY); vm.press(RemoteKey.VOICE) },
                                    onOffline = { vm.feel.fire(Feel.PRESS); openDevices() },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    KeyButton(accent, "Back", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.BACK, c, 22.dp) }
                    KeyButton(accent, "Home", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, 22.dp) }
                    KeyButton(accent, "Play or pause", Modifier.size(56.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ -> LineIcon(Ico.PLAY, c, 21.dp) }
                }
            }
        }
    }
}

/**
 * Volume and channels, tucked into the tab bar: it grows out of the pill when you swipe up
 * on Home or on the pill, or tap Home again. Swipe down, or wait, and it tucks away.
 */
@Composable
fun ControlTray(vm: RemoteVM, accent: Color, numpad: Boolean, onNumpad: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.width(320.dp).shadow(6.dp, RoundedCornerShape(28.dp)).background(M.Container, RoundedCornerShape(28.dp))
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(22.dp).clickable(onClickLabel = "hide volume and channels", role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(Color(0xFF4A4A50))) }
        VolumePill(
            accent, vm.muted, Modifier.fillMaxWidth().height(54.dp),
            onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) },
            onMute = { vm.press(RemoteKey.MUTE) },
            onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) },
        )
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelPill(accent, Modifier.weight(1f).fillMaxHeight(),
                onDown = { vm.press(RemoteKey.CH_DOWN) }, onUp = { vm.press(RemoteKey.CH_UP) })
            KeyButton(accent, "Number pad", Modifier.size(54.dp), state = if (numpad) "Showing" else null, pressedScale = 1f,
                restColor = if (numpad) accent else T.Ink, onPress = onNumpad) { c, _ ->
                Text("123", color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** "📺 Living room TV ▾" in green when connected; opens the TV list. */
@Composable
private fun TvLine(vm: RemoteVM, openDevices: () -> Unit) {
    val d = vm.active
    val on = d != null && vm.link == Link.READY
    val col = when {
        d == null -> T.Dim
        on -> Color(0xFF6DD58C)
        vm.link == Link.CONNECTING -> Color(0xFFFDD663)
        else -> Color(0xFFF2B8B5)
    }
    Row(
        Modifier.padding(top = 2.dp).clip(CircleShape)
            .clickable(onClickLabel = "choose a TV", role = Role.Button, onClick = openDevices)
            .semantics(mergeDescendants = true) {
                contentDescription = d?.let { "TV: ${it.name}" } ?: "Choose a TV"
                if (d != null) stateDescription = linkWord(vm.link)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Ico.TV, col, 15.dp)
        Text(d?.name ?: "Choose a TV", color = col, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (d != null && !on) Text("· ${linkWord(vm.link)}", color = T.Dim, fontSize = 12.5.sp, maxLines = 1)
        LineIcon(Ico.DOWN, T.Dim, 14.dp)
    }
}

