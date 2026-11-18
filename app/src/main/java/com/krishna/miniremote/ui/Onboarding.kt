package com.krishna.miniremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ------------------------------------------------------------ onboarding */

private class Slide(val title: String, val text: String, val art: @Composable (Color) -> Unit)

/**
 * First run: four short pages (what the app is, connecting, the remote's gestures, apps and
 * typing), each with a picture. Skip or Get started ends it; the guided tour follows.
 */
@Composable
fun Onboarding(accent: Color, onDone: () -> Unit) {
    val slides = remember {
        listOf(
            Slide("Your TV remote, on your phone", "Control Google TV, Android TV, Roku, Samsung and LG TVs: no account, no ads.") { a -> DialArt(a) },
            Slide("Same Wi-Fi as your TV", "Turn the TV on and keep your phone on the same Wi-Fi. Mini Remote finds it; Google TVs show a code to pair once.") { a -> TvIllustration(a) },
            Slide("Swipe, tap, turn", "Swipe or tap the arrows on the dial, tap OK to select and hold it to talk. Turn your finger round the ring for volume.") { a -> GestureArt(a) },
            Slide("Your apps, one tap away", "Swipe across the circles for more keys and your favourite apps. Search types on the TV from your phone's keyboard.") { a -> AppsArt(a) },
        )
    }
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Box(Modifier.fillMaxSize().background(T.Screen).pointerInput(Unit) { detectTapGestures { } }.systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!last) Text("Skip", color = T.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDone).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val s = slides[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { s.art(accent) }
                    Text(s.title, color = T.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 30.sp,
                        modifier = Modifier.padding(top = 28.dp))
                    Text(s.text, color = T.Dim, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 12.dp).widthIn(max = 360.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size) { i ->
                    val sel = i == pager.currentPage
                    Box(Modifier.padding(horizontal = 3.dp).size(if (sel) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (sel) accent else T.DotOff))
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
                Btn(if (last) "Get started" else "Next") { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
            }
        }
    }
}

/** A dial in its ring, the volume arc in the accent colour. */
@Composable
private fun DialArt(accent: Color) {
    Canvas(Modifier.size(200.dp)) {
        val c = center; val r = size.minDimension / 2f
        drawCircle(T.Ink.copy(alpha = 0.12f), r - 6.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        drawArc(accent, 90f, 220f, false, Offset(c.x - r + 6.dp.toPx(), c.y - r + 6.dp.toPx()), Size((r - 6.dp.toPx()) * 2, (r - 6.dp.toPx()) * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(M.High, r - 22.dp.toPx(), c)
        drawCircle(M.Highest, r * 0.34f, c)
        val a = r * 0.62f; val t = 9.dp.toPx(); val w = 2.4.dp.toPx()
        listOf(Offset(0f, -a), Offset(0f, a), Offset(-a, 0f), Offset(a, 0f)).forEachIndexed { i, o ->
            val p = c + o
            val (d1, d2) = when (i) { 0 -> Offset(-t, t) to Offset(t, t); 1 -> Offset(-t, -t) to Offset(t, -t); 2 -> Offset(t, -t) to Offset(t, t); else -> Offset(-t, -t) to Offset(-t, t) }
            drawLine(T.Ink, p + d1 * 0.6f, p, w, StrokeCap.Round); drawLine(T.Ink, p + d2 * 0.6f, p, w, StrokeCap.Round)
        }
    }
}

/** The dial with a finger's path: a swipe and a turn round the ring. */
@Composable
private fun GestureArt(accent: Color) {
    Box(contentAlignment = Alignment.Center) {
        DialArt(accent)
        Canvas(Modifier.size(200.dp)) {
            val c = center
            drawLine(accent.copy(alpha = 0.9f), c + Offset(-40.dp.toPx(), 30.dp.toPx()), c + Offset(40.dp.toPx(), 30.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 9.dp.toPx(), c + Offset(40.dp.toPx(), 30.dp.toPx()))
            drawCircle(Color.White, 4.dp.toPx(), c + Offset(40.dp.toPx(), 30.dp.toPx()))
        }
    }
}

/** Two rows of app circles in soft colours. */
@Composable
private fun AppsArt(accent: Color) {
    val colours = listOf(Color(0xFFE50914), Color(0xFFFF0000), Color(0xFF1A98FF), Color(0xFF1DB954), Color(0xFF6B21A8), Color(0xFF0E2A5C), Color(0xFFE5A00D), accent)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { c ->
                    val i = r * 4 + c
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (i == 7) M.High else colours[i]),
                        contentAlignment = Alignment.Center) { if (i == 7) LineIcon(Ico.GRID, T.Ink, 22.dp) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ guided tour */

/** Where the tour's spots are: each part it points at reports its bounds here. */
object Coach {
    val rects = mutableStateMapOf<String, Rect>()
}

/** Marks a part of the screen the guided tour can point at. */
fun Modifier.coach(id: String): Modifier = onGloballyPositioned { Coach.rects[id] = it.boundsInRoot() }

private class Step(val id: String, val title: String, val text: String, val round: Boolean = false)

private val STEPS = listOf(
    Step("tv", "Your TV", "Tap here to choose a TV, add another or see how it's connected. The dot shows green when it's ready."),
    Step("power", "Power", "Turns the TV off (it asks first). When it's off, this wakes it up again where the TV allows."),
    Step("dial", "Move and select", "Tap the arrows or swipe across the dial to move; tap OK to select. Double-tap OK to mute; hold it to talk to the TV.", round = true),
    Step("dial", "Volume ring", "Turn your finger round the outer ring to change the volume. The number at its foot is the TV's own volume.", round = true),
    Step("keys", "More keys and your apps", "Swipe these circles sideways: more keys (rewind, sleep timer, inputs…), then your quick access apps."),
    Step("pills", "Pages", "Shows which page of circles you're on. Tap it to go to the next one. What's playing shows on this line too."),
)

/**
 * The guided tour: the screen dims, the part being explained shows through a soft spotlight,
 * and a card says what it does. Tap Next (or anywhere) to go on; Skip ends it.
 */
@Composable
fun CoachTour(accent: Color, onDone: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val steps = STEPS.filter { Coach.rects[it.id] != null }
    if (steps.isEmpty()) { onDone(); return }
    val step = steps[i.coerceAtMost(steps.lastIndex)]
    val r = Coach.rects[step.id]?.translate(-origin) ?: Rect.Zero
    val density = LocalDensity.current
    val pad = with(density) { 10.dp.toPx() }
    val hole = if (step.round) {
        val rad = maxOf(r.width, r.height) / 2f + pad
        Rect(r.center.x - rad, r.center.y - rad, r.center.x + rad, r.center.y + rad)
    } else r.inflate(pad)
    val shown by animateFloatAsState(1f, tween(250), label = "coach")
    fun next() { if (i >= steps.lastIndex) onDone() else i++ }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(i) { detectTapGestures { next() } }) {
        // The dim, with the spotlight cut out of it.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = shown }) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            val cr = if (step.round) hole.width / 2f else 24.dp.toPx()
            drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(cr), blendMode = BlendMode.Clear)
            drawRoundRect(accent, hole.topLeft, hole.size, CornerRadius(cr), style = Stroke(2.dp.toPx()))
        }
        // The card: under the spotlight when there's room, else above it.
        val h = with(density) { maxHeight.toPx() }
        val gap = with(density) { 16.dp.toPx() }
        var cardH by remember { mutableIntStateOf(0) }
        val below = hole.bottom + gap + (if (cardH > 0) cardH.toFloat() else with(density) { 200.dp.toPx() }) < h
        val top = if (below) hole.bottom + gap else hole.top - gap - cardH
        AnimatedContent(i, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "coachCard") { _ ->
            Column(
                Modifier.offset { IntOffset(0, top.roundToInt().coerceAtLeast(0)) }
                    .fillMaxWidth().padding(horizontal = 20.dp)
                    .onSizeChanged { cardH = it.height }
                    .clip(RoundedCornerShape(22.dp)).background(SheetBg).border(1.dp, Line, RoundedCornerShape(22.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
                    .padding(20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text("${steps.indexOf(step) + 1} of ${steps.size}", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(step.title, color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text(step.text, color = T.Dim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Skip tour", color = T.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDone).padding(horizontal = 10.dp, vertical = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).background(accent).clickable(role = Role.Button) { next() }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(if (i >= steps.lastIndex) "Done" else "Next", color = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
