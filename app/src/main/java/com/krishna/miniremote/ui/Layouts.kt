package com.krishna.miniremote.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.RemoteKey
import com.krishna.miniremote.RemoteVM
import kotlin.math.abs
import kotlin.math.atan2

/** The Home layouts you can pick in Settings. */
enum class HomeLayout(val key: String, val label: String, val about: String) {
    STRIP("strip", "Black", "Black screen, volume ring, two rows of keys."),
    CARDS("cards", "Cards", "Swipe the dots for the volume, channel and number cards.");
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: STRIP }
}

/** Width of the volume ring around the dial. */
val RING_BAND = 26.dp
/** Degrees of turn per volume step. */
private const val RING_STEP = 18f

/**
 * The dial with a volume ring around it, like an iPod wheel: turn clockwise for louder.
 * While turning, an accent arc follows the finger. Touches inside the dial go to [content].
 */
@Composable
fun RingDial(dial: Dp, accent: Color, level: Float? = null, value: String? = null, ring: Boolean = true, onStep: (up: Boolean) -> Unit, content: @Composable (turning: Boolean) -> Unit) {
    // The volume ring shows (and turns) only while a TV is connected.
    val ringA by animateFloatAsState(if (ring) 1f else 0f, tween(300), label = "ringA")
    val shown by animateFloatAsState(level ?: 0f, tween(160), label = "lvl")
    val step by rememberUpdatedState(onStep)
    var start by remember { mutableFloatStateOf(0f) }
    var sweep by remember { mutableFloatStateOf(0f) }
    var turning by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (turning) 1f else 0f, tween(if (turning) 90 else 600), label = "ring")
    Box(
        Modifier.size(dial + RING_BAND * 2)
            .semantics {
                contentDescription = "Volume ring"
                customActions = listOf(
                    CustomAccessibilityAction("Volume up") { step(true); true },
                    CustomAccessibilityAction("Volume down") { step(false); true },
                )
            }
            .pointerInput(dial, ring) {
                if (!ring) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    if ((down.position - c).getDistance() < dial.toPx() / 2f) return@awaitEachGesture
                    down.consume()
                    fun ang(p: Offset) = Math.toDegrees(atan2((p - c).y, (p - c).x).toDouble()).toFloat()
                    var last = ang(down.position)
                    var acc = 0f
                    start = last; sweep = 0f; turning = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val a = ang(ch.position)
                        var d = a - last
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        last = a; acc += d; sweep = (sweep + d).coerceIn(-350f, 350f)
                        while (acc >= RING_STEP) { acc -= RING_STEP; step(true) }
                        while (acc <= -RING_STEP) { acc += RING_STEP; step(false) }
                        ch.consume()
                    }
                    turning = false
                }
            }
            .drawBehind {
                if (ringA <= 0f) return@drawBehind
                val r = dial.toPx() / 2f + RING_BAND.toPx() / 2f
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(M.Highest.copy(alpha = 0.55f * ringA), r, style = Stroke(3.dp.toPx()))
                // The TV's real volume, from the bottom, clockwise.
                if (level != null) { if (shown > 0f) drawArc(accent.copy(alpha = ringA), 90f, 360f * shown, false, tl, sz, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)) }
                else if (glow > 0f && sweep != 0f) drawArc(accent.copy(alpha = glow), start, sweep, false, tl, sz,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) {
        content(turning)
        // At rest the number sits at the foot of the ring; while turning it shows in the middle (see content).
        if (value != null && !turning && ring) Box(Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(T.Screen).padding(horizontal = 8.dp)) {
            Text(value, color = T.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A round key that fires on release (so it can sit in a scrolling strip). Icon or a short word, with an optional caption. */
@Composable
fun StripKey(accent: Color, label: String, size: Dp, active: Boolean = false, ico: Ico? = null, word: String? = null, caption: String? = null, enabled: Boolean = true, onDisabled: (() -> Unit)? = null, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, if (pressed) tween(70) else androidx.compose.animation.core.spring(0.45f, 700f), label = "press")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.32f }
            .then(if (Look.depth) Modifier.drawBehind { depthShape(if (active || pressed) M.Container else M.High, this.size.minDimension / 2f, pressed) } else Modifier)
            .clip(CircleShape)
            .then(if (Look.depth) Modifier else Modifier.background(if (active || pressed) M.Container else T.Key))
            .clickable(src, androidx.compose.material3.ripple(color = Color.White), onClickLabel = label, role = Role.Button) { if (enabled) onClick() else onDisabled?.invoke() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val c = T.Ink
            if (ico != null) LineIcon(ico, c, if (caption != null) 18.dp else 23.dp)
            if (word != null) Text(word, color = c, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (caption != null) Text(caption, color = T.Dim, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium,
                lineHeight = 9.sp, modifier = Modifier.offset(y = (-1).dp))
        }
    }
}

/**
 * Two rows of round keys: Back, Home, Play, Search; then, on live TV, channel down / up,
 * numbers and input, and in apps volume down / up, mute and input.
 */
@Composable
fun KeyRows(vm: RemoteVM, accent: Color, numpad: Boolean, onNumpad: () -> Unit, openKeys: () -> Unit, openMore: () -> Unit = {}, openApps: (Boolean) -> Unit = {}, pager0: androidx.compose.foundation.pager.PagerState? = null) {
    val tv = vm.nowOn?.live == true
    val k = 68.dp
    var sleep by remember { mutableStateOf(false) }
    if (sleep) SleepSheet(vm, accent) { sleep = false }
    // Two pages of eight: swipe across the circles for playback, sleep timer and the TV's keys.
    val pages = if (vm.settings.appsIn == "more") 2 else 4
    // The page dots live at the bottom of the screen (Shell), so the pager state comes from there.
    val own = androidx.compose.foundation.pager.rememberPagerState { pages }
    val pager = pager0 ?: own
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.width(k * 4 + 14.dp * 3), beyondViewportPageCount = 1, pageSpacing = 24.dp) { page ->
    // No hard cut at the edges: the page leaving fades and shrinks a little, the one coming in
    // fades up, so they blend as you swipe.
    Box(Modifier.graphicsLayer {
        val off = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
        alpha = (1f - off * 1.4f).coerceIn(0f, 1f)
        val sc = 1f - 0.08f * off
        scaleX = sc; scaleY = sc
    }) {
    if (page >= 2) AppsPage(vm, accent, k, openApps, part = page - 2) else
    if (page == 1) KeyPage2(vm, accent, k) { sleep = true } else
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StripKey(accent, "Back", k, ico = Ico.BACK, enabled = vm.can(RemoteKey.BACK), onDisabled = { vm.explain(RemoteKey.BACK) }) { vm.press(RemoteKey.BACK) }
            StripKey(accent, "Home", k, ico = Ico.HOME, enabled = vm.can(RemoteKey.HOME), onDisabled = { vm.explain(RemoteKey.HOME) }) { vm.press(RemoteKey.HOME) }
            // Live TV (a set-top box: Tata Play, Airtel, Dish TV…): Mute where Play / Pause would be.
            if (tv) StripKey(accent, if (vm.muted) "Unmute" else "Mute", k, ico = if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, enabled = vm.can(RemoteKey.MUTE), onDisabled = { vm.explain(RemoteKey.MUTE) }) { vm.press(RemoteKey.MUTE) }
            else StripKey(accent, if (vm.playing) "Pause" else "Play", k, ico = if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, enabled = vm.can(RemoteKey.PLAY_PAUSE), onDisabled = { vm.explain(RemoteKey.PLAY_PAUSE) }) { vm.press(RemoteKey.PLAY_PAUSE) }
            StripKey(accent, "Search", k, ico = Ico.SEARCH, enabled = vm.can(RemoteKey.HOME), onDisabled = { vm.explain(RemoteKey.HOME) }, onClick = openKeys)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (tv) {
                StripKey(accent, "Channel down", k, ico = Ico.MINUS, caption = "CH", enabled = vm.can(RemoteKey.CH_DOWN), onDisabled = { vm.explain(RemoteKey.CH_DOWN) }) { vm.press(RemoteKey.CH_DOWN) }
                StripKey(accent, "Channel up", k, ico = Ico.PLUS, caption = "CH", enabled = vm.can(RemoteKey.CH_UP), onDisabled = { vm.explain(RemoteKey.CH_UP) }) { vm.press(RemoteKey.CH_UP) }
                StripKey(accent, "Number pad", k, active = numpad, word = "123", enabled = vm.can(RemoteKey.N0), onDisabled = { vm.explain(RemoteKey.N0) }, onClick = onNumpad)
            } else {
                StripKey(accent, "Volume down", k, ico = Ico.VOL_MINUS, enabled = vm.can(RemoteKey.VOL_DOWN), onDisabled = { vm.explain(RemoteKey.VOL_DOWN) }) { vm.press(RemoteKey.VOL_DOWN, quiet = true) }
                StripKey(accent, "Volume up", k, ico = Ico.VOL_PLUS, enabled = vm.can(RemoteKey.VOL_UP), onDisabled = { vm.explain(RemoteKey.VOL_UP) }) { vm.press(RemoteKey.VOL_UP, quiet = true) }
                VoiceKey(vm, k)
            }
            StripKey(accent, "More: keys, inputs and apps", k, ico = Ico.MORE, onClick = openMore)
        }
    }
    }
    }
    }
}

/**
 * Quick access cells, page by page: eight apps on the first apps page, seven more and All apps
 * (always the very last place) on the second: fifteen in all. Empty places are faint rings; the first one shows a +.
 * [part] is the page (0 or 1).
 */
internal fun appCells(count: Int, part: Int): List<Int?> =
    if (part == 0) (0 until 8).map { if (it < count) it else null }
    else (8 until 15).map { if (it < count) it else null } + listOf(-1)

/**
 * A page of quick access circles. Long-press an app and drag it to another place on the page
 * to reorder; tap to open it on the TV.
 */
@Composable
internal fun AppsPage(vm: RemoteVM, accent: Color, k: Dp, openApps: (Boolean) -> Unit, part: Int = 0) {
    val pinned = vm.shortcuts.filter { it.pinned }
    val ready = vm.active != null && vm.link == com.krishna.miniremote.Link.READY
    val cells = appCells(pinned.size, part)
    val gap = 14.dp
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { (k + gap).toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragBy by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // Where the dragged app would land (a place on this page that holds an app).
    val target = dragging?.let { i ->
        val from = cells.indexOf(i)
        val col = (from % 4 + kotlin.math.round(dragBy.x / step).toInt()).coerceIn(0, 3)
        val row = (from / 4 + kotlin.math.round(dragBy.y / step).toInt()).coerceIn(0, 1)
        cells.getOrNull(row * 4 + col)?.takeIf { it >= 0 } ?: i
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell ->
                    when {
                        cell == -1 -> StripKey(accent, "All apps", k, ico = Ico.GRID) { openApps(false) }
                        cell != null -> {
                            val s = pinned[cell]
                            val me = dragging == cell
                            Box(
                                Modifier.size(k)
                                    .zIndex(if (me) 1f else 0f)
                                    .graphicsLayer {
                                        if (me) { translationX = dragBy.x; translationY = dragBy.y; scaleX = 1.12f; scaleY = 1.12f; shadowElevation = 16f }
                                        else if (target == cell && dragging != null) { alpha = 0.45f }
                                    }
                                    .pointerInput(s.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragging = cell; dragBy = androidx.compose.ui.geometry.Offset.Zero; vm.feel.fire(com.krishna.miniremote.Feel.HEAVY) },
                                            onDragEnd = {
                                                val to = target
                                                if (to != null && to != cell) { vm.movePinned(s, to - cell); vm.feel.fire(com.krishna.miniremote.Feel.TICK) }
                                                dragging = null; dragBy = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            onDragCancel = { dragging = null; dragBy = androidx.compose.ui.geometry.Offset.Zero },
                                        ) { c, by -> c.consume(); dragBy += by }
                                    },
                            ) { AppCircle(vm, s, k) { if (ready) vm.launch(s) else vm.explain(RemoteKey.HOME) } }
                        }
                        else -> Box(Modifier.size(k).clip(CircleShape).border(1.dp, T.Ink.copy(alpha = 0.10f), CircleShape)
                            .clickable(onClickLabel = "add apps") { openApps(true) }, contentAlignment = Alignment.Center) {
                            LineIcon(Ico.PLUS, T.Ink.copy(alpha = 0.3f), 16.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Home's second page: Rewind · Forward · Mute · Sleep, then Info · Guide · Input · Settings. */
@Composable
private fun KeyPage2(vm: RemoteVM, accent: Color, k: Dp, openSleep: () -> Unit) {
    @Composable fun key(label: String, ico: Ico, key: RemoteKey) =
        StripKey(accent, label, k, ico = ico, enabled = vm.can(key), onDisabled = { vm.explain(key) }) { vm.press(key) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            key("Rewind", Ico.SEEK_BACK, RemoteKey.REWIND)
            key("Fast forward", Ico.SEEK_FWD, RemoteKey.FAST_FORWARD)
            key(if (vm.muted) "Unmute" else "Mute", if (vm.muted) Ico.SPEAKER_MUTED else Ico.SPEAKER, RemoteKey.MUTE)
            // Sleep: shows the minutes left while it's counting down.
            val at = vm.sleepAt
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(at) { while (at != null) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(15_000) } }
            val left = at?.let { ((it - now) / 60_000 + 1).coerceAtLeast(1) }
            StripKey(accent, if (left != null) "Sleep timer, $left minutes left" else "Sleep timer", k, active = at != null,
                ico = if (left == null) Ico.SLEEP else null, word = left?.let { "${it}m" },
                enabled = vm.can(RemoteKey.POWER), onDisabled = { vm.explain(RemoteKey.POWER) }, onClick = openSleep)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            key("Info", Ico.INFO, RemoteKey.INFO)
            key("Guide", Ico.GUIDE, RemoteKey.GUIDE)
            key("Input", Ico.INPUT, RemoteKey.INPUT)
            key("TV settings", Ico.SLIDERS, RemoteKey.SETTINGS)
        }
    }
}

/**
 * The voice assistant key: hold and speak (on Android / Google TV your voice goes to the TV's
 * Assistant until you let go); on other TVs a tap opens the TV's own voice search.
 */
@Composable
fun VoiceKey(vm: RemoteVM, size: Dp) {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) 0.94f else 1f, tween(90), label = "voice")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (vm.can(RemoteKey.HOME)) 1f else 0.32f }
            .then(if (Look.depth) Modifier.drawBehind { depthShape(if (down || vm.listening) M.Container else M.High, this.size.minDimension / 2f, down) } else Modifier)
            .clip(CircleShape)
            .then(if (Look.depth) Modifier else Modifier.background(if (down || vm.listening) M.High else T.Key))
            .semantics {
                contentDescription = "Voice assistant"
                role = Role.Button
                onClick(label = "talk to the TV") { vm.voiceStart(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    // Tap: listen until you stop talking (or tap again). Hold: listen until you let go.
                    if (!vm.can(RemoteKey.HOME)) { vm.explain(RemoteKey.HOME); return@detectTapGestures }
                    if (vm.listening) { vm.voiceStop(); return@detectTapGestures }
                    down = true
                    vm.feel.fire(com.krishna.miniremote.Feel.HEAVY)
                    val t0 = System.currentTimeMillis()
                    val streaming = vm.voiceStart()
                    tryAwaitRelease()
                    down = false
                    if (streaming && System.currentTimeMillis() - t0 > 450) vm.voiceStop()
                })
            },
        contentAlignment = Alignment.Center,
    ) { LineIcon(Ico.MIC, T.Ink, 23.dp) }
}

/** Small dots for the cards: tap one, or swipe across them. */
@Composable
fun CardDots(n: Int, page: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.height(36.dp).pointerInput(page) {
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                if (total < -16.dp.toPx()) onPick((page + 1).coerceAtMost(n - 1)) else if (total > 16.dp.toPx()) onPick((page - 1).coerceAtLeast(0))
            }) { c, dx -> c.consume(); total += dx }
        }.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val names = listOf("Remote", "Volume and channels", "Numbers")
        repeat(n) { i ->
            val sel = i == page
            Box(
                Modifier.height(24.dp).clip(CircleShape).selectable(sel, role = Role.Tab) { onPick(i) }
                    .semantics { contentDescription = names.getOrElse(i) { "Card ${i + 1}" } }.padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(if (sel) 14.dp else 5.dp, 5.dp).clip(CircleShape).background(if (sel) T.Dim else Color(0xFF5A5A60))) }
        }
    }
}

/** Width of the Black layout's two key rows (4 × 68 + 3 × 14): the trackpad matches it. */
val KEYROW_W = 314.dp

/** Width of the Cards layout's cards: the same as the row of four keys under them (4 × 62 + 3 × 20). */
val CARD_W = 308.dp

/** The volume / channel card: two tall rockers, then input and guide. */
@Composable
fun VolChCard(vm: RemoteVM, accent: Color, openMore: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // Rockers exactly as wide as the round keys under them, centred over them.
        Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) {
            VerticalRocker(accent, "VOL", "Volume up", "Volume down", Ico.PLUS, Ico.MINUS, Modifier.width(64.dp).height(200.dp),
                onUp = { vm.press(RemoteKey.VOL_UP, quiet = true) }, onDown = { vm.press(RemoteKey.VOL_DOWN, quiet = true) })
            VerticalRocker(accent, "CH", "Channel up", "Channel down", Ico.PLUS, Ico.MINUS, Modifier.width(64.dp).height(200.dp),
                onUp = { vm.press(RemoteKey.CH_UP) }, onDown = { vm.press(RemoteKey.CH_DOWN) })
        }
        // Under the rockers' centres.
        Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) {
            KeyButton(accent, "Input", Modifier.size(64.dp), pressedScale = 1f, onPress = { vm.press(RemoteKey.INPUT) }) { c, _ -> LineIcon(Ico.INPUT, c, 20.dp) }
            KeyButton(accent, "More", Modifier.size(64.dp), pressedScale = 0.94f, onPress = openMore) { c, _ -> LineIcon(Ico.MORE, c, 22.dp) }
        }
    }
}

/** Back, Home, Play / pause and Search. */
@Composable
fun FourKeys(vm: RemoteVM, accent: Color, openKeys: () -> Unit, size: Dp = 62.dp, gap: Dp = 20.dp) {
    val k = Modifier.size(size)
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        KeyButton(accent, "Back", k, pressedScale = 0.94f, enabled = vm.can(RemoteKey.BACK), onDisabled = { vm.explain(RemoteKey.BACK) }, onPress = { vm.press(RemoteKey.BACK) }) { c, _ -> LineIcon(Ico.CHEVRON_L, c, 24.dp, stroke = 2.2f) }
        KeyButton(accent, "Home", k, pressedScale = 0.94f, enabled = vm.can(RemoteKey.HOME), onDisabled = { vm.explain(RemoteKey.HOME) }, onPress = { vm.press(RemoteKey.HOME) }) { c, _ -> LineIcon(Ico.HOME, c, 22.dp) }
        KeyButton(accent, if (vm.playing) "Pause" else "Play", k, pressedScale = 0.94f, enabled = vm.can(RemoteKey.PLAY_PAUSE), onDisabled = { vm.explain(RemoteKey.PLAY_PAUSE) }, onPress = { vm.press(RemoteKey.PLAY_PAUSE) }) { c, _ ->
            LineIcon(if (vm.playing) Ico.PAUSE else Ico.PLAY_ONLY, c, if (vm.playing) 22.dp else 20.dp, stroke = if (vm.playing) 2.4f else 1.8f)
        }
        KeyButton(accent, "Search", k, actionLabel = "search or type on TV", pressedScale = 0.94f, enabled = vm.can(RemoteKey.HOME), onDisabled = { vm.explain(RemoteKey.HOME) }, onPress = openKeys) { c, _ -> LineIcon(Ico.SEARCH, c, 21.dp) }
    }
}

/** Settings: which Home layout to use. */
@Composable
internal fun LayoutPicker(value: String, accent: Color, onPick: (String) -> Unit) {
    val cur = HomeLayout.of(value)
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 2.dp)) {
        Text("Layout", color = T.Ink, fontSize = 15.sp)
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeLayout.entries.forEach { l ->
                val sel = l == cur
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(if (sel) accent else M.Highest)
                        .selectable(sel, role = Role.RadioButton) { onPick(l.key) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(l.label, color = if (sel) Color.White else T.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Text(cur.about, color = T.Dim, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp))
    }
    Divider()
}

