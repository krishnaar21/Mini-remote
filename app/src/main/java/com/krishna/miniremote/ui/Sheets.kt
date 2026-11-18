package com.krishna.miniremote.ui

import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.ACCENTS
import com.krishna.miniremote.Brand
import com.krishna.miniremote.Device
import com.krishna.miniremote.HAPTIC_STRENGTHS
import com.krishna.miniremote.Link
import com.krishna.miniremote.PairPrompt
import com.krishna.miniremote.RemoteVM
import com.krishna.miniremote.SOUND_LEVELS
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/* ---------------------------------------------------------------- palette */

/** Sheets use the remote's own charcoal so they read as part of the same object. */
internal val SheetBg = Color(0xFF262629)
internal val CardBg = Color(0xFF222223)
internal val Line = Color(0xFF2E2E30)
internal val Muted = Color(0xFFB4B4B9)
internal val LeadInk = Color(0xFFE0E0E4)
internal val Fill2 = Color(0xFF2A2A2B)
internal val OkGreen = Color(0xFF4DDC88)
internal val SwitchOn = Color(0xFF34C759)
internal val DangerBg = Color(0xFF3A1E1E)
internal val DangerInk = Color(0xFFFF8A8A)

/* ------------------------------------------------------------------ pages */

/** Screens inside the devices/settings sheet. Back walks the stack; close dismisses the sheet. */
sealed interface Page {
    data object Devices : Page
    data class DeviceInfo(val id: String) : Page
    data object Local : Page
    data object Scan : Page
    data object Manual : Page
    data class Pairing(val id: String) : Page
    data object Settings : Page
    data object Licenses : Page
    data object Privacy : Page
    data object Gestures : Page
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSheet(vm: RemoteVM, accent: Color, start: Page, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stack = remember { mutableStateListOf(start) }
    val page = stack.last()
    fun go(p: Page) { stack.add(p) }
    fun replace(p: Page) { stack[stack.lastIndex] = p }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }

    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        BackHandler(enabled = stack.size > 1) { back() }
        val canBack = stack.size > 1 || page !is Page.Devices
        val maxH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp -
            androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() - 28.dp
        Column(Modifier.heightIn(max = maxH).navigationBarsPadding()) {
            when (page) {
                Page.Devices -> {
                    Header("Choose a TV", onClose, gear = { go(Page.Settings) })
                    Body { DevicesPage(vm, open = { go(Page.DeviceInfo(it)) }, add = { go(if (vm.localSeen) Page.Scan else Page.Local) }) }
                }
                is Page.DeviceInfo -> {
                    val d = vm.devices.firstOrNull { it.id == page.id }
                    if (d == null) { LaunchedEffect(Unit) { back() }; return@Column }
                    Header(d.name, onClose, back = ::back)
                    Body { DevicePage(vm, d, accent) }
                }
                Page.Local -> {
                    Header("Local network", onClose, back = if (canBack) ::back else null)
                    Body { LocalPage { vm.markLocalSeen(); replace(Page.Scan) } }
                }
                Page.Scan -> {
                    Header("Add a TV", onClose, back = if (canBack) ::back else null)
                    Body { ScanPage(vm, accent, pick = { d -> vm.addAndSelect(d); go(Page.Pairing(d.id)) }, manual = { go(Page.Manual) }) }
                }
                Page.Manual -> {
                    Header("Enter IP", onClose, back = ::back)
                    Body { ManualPage(vm, accent) { d -> go(Page.Pairing(d.id)) } }
                }
                is Page.Pairing -> {
                    val d = vm.devices.firstOrNull { it.id == page.id }
                    if (d == null) { LaunchedEffect(Unit) { back() }; return@Column }
                    PairPage(vm, d, accent, onClose, back = ::back)
                }
                Page.Privacy -> {
                    Header("Privacy & data", onClose, back = ::back)
                    Body { PrivacyPage(vm) }
                }
                Page.Gestures -> {
                    Header("Gestures", onClose, back = ::back)
                    Body { GesturesPage(accent) }
                }
                Page.Licenses -> {
                    Header("Licences", onClose, back = ::back)
                    Body { LicensesPage() }
                }
                Page.Settings -> {
                    Header("Settings", onClose, back = if (canBack) ::back else null)
                    Body { SettingsPage(vm, accent, manage = null, openLicenses = { go(Page.Licenses) }, openPrivacy = { go(Page.Privacy) }, openGestures = { go(Page.Gestures) }) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ building blocks */

internal val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** .grab: 38 x 4 handle, 12 above and 4 below. */
@Composable
internal fun Grab() = Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(28.dp, 3.dp).background(Color(0xFF8A8A8E), RoundedCornerShape(2.dp)))

/** .field: label above (13, muted), 52 tall, radius 12, #141415, 1px line, 16 text. */
@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 13.sp)
        BasicTextField(
            value, onChange, singleLine = true, readOnly = readOnly,
            textStyle = TextStyle(color = T.Ink, fontSize = 16.sp, fontFamily = Inter),
            cursorBrush = SolidColor(T.Ink),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().height(52.dp).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxSize().softFace(HOLLOW, sheen = false, corner = 12.dp, dpUnits = true)
                        .then(when { isError -> Modifier.border(1.dp, DangerInk, shape); focused -> Modifier.border(1.dp, Muted, shape); else -> Modifier })
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, color = Color(0xFF5D5D62), fontSize = 16.sp)
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

/** .sw: 50 x 30 track (#34c759 on, #3a3a3c off), 24px white knob that slides 20. */
@Composable
internal fun Toggle(on: Boolean) {
    // The Material 3 switch; the whole row handles the tap, so this one only shows the state.
    androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
}

@Composable
internal fun Header(title: String, onClose: () -> Unit, back: (() -> Unit)? = null, gear: (() -> Unit)? = null) {
    // Title centred between one round button on each side: back (or settings) and close.
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            back != null -> CircleIcon(Ico.CHEVRON_L, "Back", back)
            gear != null -> CircleIcon(Ico.GEAR, "Settings", gear)
            else -> Spacer(Modifier.size(44.dp))
        }
        Text(title, color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.18).sp, maxLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
        CircleIcon(Ico.CLOSE, "Close", onClose)
    }
}

@Composable
internal fun CircleIcon(ico: Ico, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(CARD, CARD_DOWN, null, label = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(ico, T.Ink, 20.dp) }
}

/**
 * Soft-touch press behaviour for sheet controls: [up] at rest, [down] (no sheen) while the
 * finger is on it. [corner] null = pill/circle. Shadows are in dp.
 */
@Composable
internal fun Modifier.pressable(
    up: Face, down: Face, corner: androidx.compose.ui.unit.Dp?, enabled: Boolean = true,
    role: Role = Role.Button, label: String? = null, onClick: () -> Unit,
): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = corner?.let { RoundedCornerShape(it) } ?: CircleShape
    return this.softFace(if (pressed) down else up, sheen = !pressed, corner = corner, dpUnits = true)
        .clip(shape)
        .clickable(src, indication = null, enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
}

@Composable
internal fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), content = content)
}

@Composable
internal fun Lead(text: String, center: Boolean = false) =
    Text(text, color = LeadInk, fontSize = 14.5.sp, lineHeight = 23.2.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))

@Composable
internal fun Hint(text: String, top: Int = 18) =
    Text(text, color = Muted, fontSize = 12.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = top.dp))

@Composable
internal fun SectionTitle(t: String) =
    Text(t.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 0.96.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 32.dp, bottom = 4.dp).semantics { heading() })

@Composable
internal fun Btn(text: String, kind: Int = 0, enabled: Boolean = true, onClick: () -> Unit) {
    // 0 = primary (white), 1 = secondary, 2 = danger
    val (faces, fg) = when (kind) { 1 -> (CARD to CARD_DOWN) to T.Ink; 2 -> (DANGER to DANGER_DOWN) to DangerInk; else -> (LIGHT to LIGHT_DOWN) to Color(0xFF111111) }
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 54.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(faces.first, faces.second, 16.dp, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Hero(ico: Ico, bg: Color, fg: Color) {
    Box(Modifier.fillMaxWidth().padding(bottom = 18.dp).height(96.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp).softFace(Face(listOf(mix(bg, 0.8f, Color.White), bg), CARD.shadows), dpUnits = true), contentAlignment = Alignment.Center) { LineIcon(ico, fg, 30.dp) }
    }
}

/** A TV with Wi-Fi waves over it, in soft rings: the picture for empty and first-run screens. */
@Composable
internal fun TvIllustration(accent: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(accent.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LineIcon(Ico.WIFI, T.Ink, 26.dp, stroke = 2f)
                    LineIcon(Ico.TV, T.Ink, 46.dp, stroke = 1.6f)
                }
            }
        }
    }
}

@Composable
internal fun Spinner(accent: Color) {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent, trackColor = Color(0xFF333333), strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
    }
}

/** Card-style list row: optional status dot, title + subtitle, then a trailing slot. */
@Composable
internal fun CardRow(
    title: String,
    sub: String?,
    dot: Color? = null,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 66.dp)
            .then(if (onClick != null) Modifier.pressable(CARD, CARD_DOWN, 16.dp, label = clickLabel, onClick = onClick)
                  else Modifier.softFace(CARD, corner = 16.dp, dpUnits = true))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        dot?.let { c ->
            // .dot.ok glows: 0 0 8px #4ddc8888
            Box(Modifier.size(9.dp).drawBehind {
                if (c == OkGreen) drawCircle(Brush.radialGradient(listOf(c.copy(0.53f), Color.Transparent)), size.minDimension * 1.4f)
                drawCircle(c)
            })
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Ink, fontSize = 15.sp)
            sub?.let { Text(it, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        trailing()
    }
}

@Composable internal fun Chevron() = LineIcon(Ico.CHEVRON_R, Color(0xFFA8A8AD), 18.dp)

@Composable
internal fun Badge(text: String) =
    Text(text, color = Color(0xFFBBBBBB), fontSize = 11.5.sp, maxLines = 1,
        modifier = Modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))

internal fun statusLine(vm: RemoteVM, d: Device): String {
    val st = if (vm.activeId != d.id) "" else when (vm.link) {
        Link.READY -> " · Connected"; Link.CONNECTING -> " · Connecting…"; else -> " · Not connected"
    }
    return "${d.brand.label} · ${d.host}$st"
}

/* ---------------------------------------------------------------- devices */

/**
 * One saved TV: name and chevron on the first line, brand · address under it, and for the
 * active TV a status line with its coloured dot ("● Connected"). All left-aligned.
 */
@Composable
internal fun DeviceRow(vm: RemoteVM, d: Device, onClick: () -> Unit) {
    val active = vm.activeId == d.id
    val (word, dot) = when {
        !active -> null to null
        vm.link == Link.READY -> "Connected" to OkGreen
        vm.link == Link.CONNECTING -> "Connecting…" to Color(0xFFFDD663)
        else -> "Not connected" to Color(0xFF8A8A8E)
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .pressable(CARD, CARD_DOWN, 16.dp, label = "open", onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, color = T.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Chevron()
        }
        Text("${d.brand.label} · ${d.host}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (word != null && dot != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(word, color = if (dot == OkGreen) OkGreen else Muted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun DevicesPage(vm: RemoteVM, open: (String) -> Unit, add: () -> Unit) {
    if (vm.devices.isEmpty()) {
        TvIllustration(com.krishna.miniremote.ui.accentColor(vm.settings.accentHex))
        Text("No TVs yet", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Spacer(Modifier.height(14.dp))
    }
    vm.devices.forEach { d ->
        val on = vm.activeId == d.id && vm.link == Link.READY
        DeviceRow(vm, d) { open(d.id) }
    }
    Btn("Add a TV", onClick = add)
}

@Composable
internal fun DevicePage(vm: RemoteVM, d: Device, accent: Color) {
    val on = vm.activeId == d.id && vm.link == Link.READY
    var name by remember(d.id) { mutableStateOf(d.name) }
    var arm by remember(d.id) { mutableStateOf(false) }
    CardRow("Brand", d.brand.label)
    CardRow("Address", d.host)
    CardRow("Status", if (on) "Connected" else if (vm.activeId == d.id && vm.link == Link.CONNECTING) "Connecting…" else "Not connected")
    Spacer(Modifier.height(8.dp))
    Field("Name", name, { name = it.take(30) })
    if (on) Btn("Disconnect") { vm.disconnect() } else Btn("Connect") { vm.select(d) }
    Btn("Save", kind = 1, enabled = name.isNotBlank() && name.trim() != d.name) { vm.rename(d.id, name); vm.say("Saved") }
    // Bluetooth: the TV pairs with the phone as a remote, so keys work without Wi-Fi.
    if (vm.btAvailable) {
        vm.btTick
        SectionTitle("Bluetooth")
        Group {
            val pairing = vm.btPairing == d.id
            Column(Modifier.padding(vertical = 14.dp)) {
                Text(when {
                    pairing -> "Waiting for the TV…"
                    d.bt != null && vm.btReady && vm.activeId == d.id -> "Paired · connected"
                    d.bt != null -> "Paired · used when Wi-Fi can't reach the TV"
                    else -> "Not paired"
                }, color = T.Ink, fontSize = 15.sp)
                Text(
                    if (pairing) "On the TV, open Settings › Remotes & Accessories (or Bluetooth) › Pair accessory, and choose Mini Remote. Keep this screen open."
                    else if (d.bt != null) "Arrows, OK, Back, Home, volume, play and search work over Bluetooth. Apps, inputs and typing need Wi-Fi."
                    else "Pair once and the phone works as a Bluetooth remote too: no Wi-Fi needed for the basic keys.",
                    color = Muted, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        when {
            vm.btPairing == d.id -> Btn("Cancel pairing", kind = 1) { vm.btCancelPairing() }
            d.bt != null -> Btn("Forget Bluetooth", kind = 1) { vm.btForget(d) }
            else -> Btn("Pair over Bluetooth", kind = 1) { vm.btPair(d) }
        }
    }
    Btn(if (arm) "Tap to confirm" else "Forget TV", kind = 2) {
        // Removing it makes this page's TV disappear, and the sheet steps back on its own.
        if (!arm) { arm = true; vm.feel.fire(com.krishna.miniremote.Feel.HEAVY) } else vm.remove(d)
    }
}

@Composable
internal fun LocalPage(onContinue: () -> Unit) {
    TvIllustration(Color(0xFF6FB1FF))
    Lead("Finds TVs on your Wi-Fi. No account needed.", center = true)
    Btn("Continue", onClick = onContinue)
}

@Composable
internal fun ScanPage(vm: RemoteVM, accent: Color, pick: (Device) -> Unit, manual: () -> Unit) {
    LaunchedEffect(Unit) { vm.scan() }
    if (vm.scanning) {
        Spinner(accent)
        Lead("Looking for TVs…", center = true)
        return
    }
    val n = vm.found.size
    Text(
        if (n == 0) "No new TVs found. Check that your phone and TV are on the same Wi-Fi, or enter the TV's address."
        else "Found $n on your network.",
        color = LeadInk, fontSize = 15.sp, lineHeight = 23.sp,
        modifier = Modifier.padding(bottom = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
    vm.found.forEach { d -> CardRow(d.name, d.host, onClick = { pick(d) }, clickLabel = "add and connect") { Badge(d.brand.label) } }
    Btn("Enter IP", kind = 1, onClick = manual)
    if (n == 0) Btn("Scan again", kind = 1) { vm.scan() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualPage(vm: RemoteVM, accent: Color, onAdded: (Device) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.ROKU) }
    var open by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Field("IP address", ip, { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15); err = null }, placeholder = "192.168.1.20",
        isError = err != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        Field("Brand", brand.label, {}, readOnly = true, modifier = Modifier.menuAnchor(),
            trailing = { LineIcon(if (open) Ico.UP else Ico.DOWN, Muted, 18.dp) })
        ExposedDropdownMenu(open, { open = false }, containerColor = Color(0xFF26262A)) {
            Brand.entries.forEach { b -> DropdownMenuItem(text = { Text(b.label, color = T.Ink) }, onClick = { brand = b; open = false }) }
        }
    }
    Text(err ?: "", color = DangerInk, fontSize = 13.sp, modifier = Modifier.padding(top = 0.dp, bottom = 10.dp).heightIn(min = 18.dp)
        .semantics { liveRegion = LiveRegionMode.Assertive })
    Btn("Continue") {
        val (e, d) = vm.addManual(ip, brand)
        if (e != null) { err = e; vm.feel.fire(com.krishna.miniremote.Feel.ERROR) } else if (d != null) onAdded(d)
    }
    Hint("On the TV: Settings › Network.")
}

/* ----------------------------------------------------------------- pairing */

@Composable
internal fun PairPage(vm: RemoteVM, d: Device, accent: Color, onClose: () -> Unit, back: () -> Unit) {
    val active = vm.activeId == d.id
    val prompt = vm.pairPrompt
    when {
        prompt != null -> {
            Header("Enter code", onClose, back = { vm.answerCode(null); back() })
            Body { CodeEntry(vm, prompt) }
        }
        active && vm.link == Link.READY -> {
            Header("Connected", onClose)
            Body {
                Hero(Ico.CHECK, Color(0xFF1D3A29), OkGreen)
                Lead("${d.name} is ready.", center = true)
                Btn("Done", onClick = onClose)
            }
        }
        active && vm.link == Link.ERROR -> {
            Header("Couldn't connect", onClose, back = back)
            Body {
                Hero(Ico.ALERT, DangerBg, DangerInk)
                Lead(vm.lastError ?: "The TV didn't answer.", center = true)
                brandHelp(d.brand)?.let { Hint(it, top = 0) }
                Btn("Try again") { vm.connect(d) }
                Btn("Close", kind = 1, onClick = onClose)
            }
        }
        active && vm.link == Link.CONNECTING && (d.brand == Brand.SAMSUNG || d.brand == Brand.LG) -> {
            Header("Look at your TV", onClose, back = back)
            Body {
                var left by remember { mutableIntStateOf(30) }
                LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
                Spinner(accent)
                Text(buildAnnotatedString {
                    append("1.  Turn the TV on and stay on its home screen.\n2.  Choose ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = T.Ink)) { append(if (d.brand == Brand.SAMSUNG) "Allow" else "Yes") }
                    append(" when the TV asks to let Remote connect.")
                }, color = LeadInk, fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text(buildAnnotatedString {
                    append("Waiting… ")
                    withStyle(SpanStyle(fontFamily = Mono)) { append("${left}s") }
                }, color = LeadInk, fontSize = 15.sp)
                brandHelp(d.brand)?.let { Hint("No prompt? $it") }
            }
        }
        active && vm.link == Link.CONNECTING -> {
            Header("Connecting", onClose, back = back)
            Body {
                Spinner(accent)
                Lead(if (d.brand == Brand.ANDROID_TV) "Connecting to ${d.name}. It will show a 6-character code." else "Connecting to ${d.name}…", center = true)
            }
        }
        else -> {
            Header("Connect", onClose, back = back)
            Body {
                Hero(Ico.TV, Color(0xFF2A2140), Color(0xFFB69CFF))
                brandHelp(d.brand)?.let { Lead(it) }
                Btn("Connect") { vm.select(d) }
            }
        }
    }
}

internal fun brandHelp(b: Brand): String? = when (b) {
    Brand.ROKU -> "Roku doesn't need a pairing code. If it can't connect, open Settings › System › Advanced system settings › Control by mobile apps on the TV and choose Default."
    Brand.SAMSUNG -> "On Samsung, check Settings › General › External Device Manager › Device Connect Manager. Menu names vary by model."
    Brand.LG -> "On LG, make sure LG Connect Apps / Mobile TV On is turned on. Menu names vary by model."
    Brand.ANDROID_TV -> "Make sure the TV is on and on the same Wi-Fi as your phone."
}

/** Six boxes, one per character, backed by a single text field so paste and TalkBack work normally. */
@Composable
internal fun CodeEntry(vm: RemoteVM, prompt: PairPrompt) {
    var code by remember(prompt) { mutableStateOf("") }
    val ok = code.length == 6
    // The keyboard comes up as soon as the code entry shows.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(prompt) { kotlinx.coroutines.delay(250); runCatching { focus.requestFocus() }; kb?.show() }
    Lead("Type the code on the TV.")
    BasicTextField(
        code,
        // Codes are hex: keep 0-9 and A-F only, uppercase, max 6.
        { v -> code = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6) },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (ok) vm.answerCode(code) }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Pairing code, 6 characters" },
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        val focus = i == code.length
                        Box(
                            Modifier.width(44.dp).height(56.dp).softFace(HOLLOW, sheen = false, corner = 12.dp, dpUnits = true)
                                .then(if (focus) Modifier.border(1.dp, Muted, RoundedCornerShape(12.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text(ch, color = T.Ink, fontSize = 22.sp, fontFamily = Mono) }
                    }
                }
                Box(Modifier.matchParentSizeCompat()) { inner() }
            }
        },
    )
    Text(prompt.error ?: "", color = DangerInk, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp).heightIn(min = 18.dp)
        .semantics { liveRegion = LiveRegionMode.Assertive })
    Btn("Pair", enabled = ok) { vm.answerCode(code) }
}

/** The inner text field fills the boxes' area so taps anywhere focus it. */
internal fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(56.dp)

/* ---------------------------------------------------------------- settings */

@Composable
internal fun SettingsPage(vm: RemoteVM, accent: Color, manage: (() -> Unit)?, openLicenses: (() -> Unit)? = null, openPrivacy: (() -> Unit)? = null, openGestures: (() -> Unit)? = null) {
    val s = vm.settings
    val ctx = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.CompositionLocalProvider(LocalAccent provides accent) {
    // Profile: your name, and everything you've set up, saved to a file (and loaded back).
    ProfileCard(vm, accent)

    androidx.compose.runtime.CompositionLocalProvider(LocalRowLines provides false) {
    Fold("Remote", "", collapsible = false) {
        SegRow("Quick access", listOf("home" to "Home page", "more" to "More sheet"), s.appsIn) { vm.update(s.copy(appsIn = it)) }
        SegRow("Control", listOf("dial" to "Dial", "trackpad" to "Trackpad"), s.controlStyle) { vm.update(s.copy(controlStyle = it)) }
        ToggleRow("Volume keys", "Phone keys change TV volume", s.volumeKeys) { vm.update(s.copy(volumeKeys = it)) }
        ToggleRow("Auto keyboard", "Opens when the TV asks for text", s.autoKeyboard) { vm.update(s.copy(autoKeyboard = it)) }
        ToggleRow("Notification remote", "Volume and play in the background", s.notifRemote, last = openGestures == null) { vm.update(s.copy(notifRemote = it)) }
        if (openGestures != null) LinkRow("Gestures", "Swipes and taps, with demos", last = true, onClick = openGestures)
    }

    Fold("Appearance", "${com.krishna.miniremote.AppIcon.all.firstOrNull { it.first == com.krishna.miniremote.AppIcon.current(ctx) }?.second ?: "Dial"} icon · ${ACCENTS.firstOrNull { it.second.equals(s.accentHex, true) }?.first ?: "Accent"}") {
        Text("App icon", color = T.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var icon by remember { mutableStateOf(com.krishna.miniremote.AppIcon.current(ctx)) }
        Row(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            com.krishna.miniremote.AppIcon.all.forEach { (key, label) ->
                val sel = icon == key
                Column(
                    Modifier.selectable(sel, role = Role.RadioButton) {
                        if (!sel) { com.krishna.miniremote.AppIcon.set(ctx, key); icon = key; vm.say("App icon: $label") }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Preview as the launcher masks it (a circle here); the art keeps to the safe zone.
                    Box(
                        Modifier.size(54.dp).then(if (sel) Modifier.border(2.dp, Color.White, CircleShape).padding(4.dp) else Modifier.padding(4.dp))
                            .clip(CircleShape),
                    ) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.krishna.miniremote.AppIcon.bg(key)), null,
                            Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.krishna.miniremote.AppIcon.fg(key)), null,
                            Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    Text(label, color = if (sel) T.Ink else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
            Divider()
        Text("Accent colour", color = T.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ACCENTS.forEach { (name, hex) ->
            val sel = s.accentHex.equals(hex, true)
            Box(
                Modifier.size(42.dp)
                    .selectable(sel, role = Role.RadioButton) { vm.update(s.copy(accentHex = hex)) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).background(accentColor(hex), CircleShape).border(2.dp, Color.Black.copy(0.25f), CircleShape))
                // Selected: a white ring 4dp outside the swatch.
                if (sel) Box(Modifier.requiredSize(42.dp).border(2.dp, Color.White, CircleShape))
            }
        }
    }
        }

    Fold("Touch & sound", "", collapsible = false) {
        ToggleRow("Haptics", "On every press", s.haptics) { vm.update(s.copy(haptics = it)) }
        if (s.haptics) SegRow("Strength", HAPTIC_STRENGTHS, s.hapticStrength) { vm.update(s.copy(hapticStrength = it)) }
        ToggleRow("Sound", "Click on press", s.sound) { vm.update(s.copy(sound = it)) }
        if (s.sound) SegRow("Level", SOUND_LEVELS, s.soundLevel) { vm.update(s.copy(soundLevel = it)); vm.feel.fire(com.krishna.miniremote.Feel.PRESS) }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Scrub step", color = T.Ink, fontSize = 15.sp)
                Text("Shorter is faster", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text("${s.scrubStepDp.roundToInt()} dp", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Fader(s.scrubStepDp, 24f..60f, accent, label = "Scrub distance", valueText = "${s.scrubStepDp.roundToInt()} dp", steps = 17) {
            vm.update(s.copy(scrubStepDp = it))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Fast", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Slow", color = Muted, fontSize = 12.sp)
        }
    }
        }

    Fold("General", "", collapsible = false) {
        ToggleRow("App logos", "Download logos", s.logos) { vm.update(s.copy(logos = it)) }
        ToggleRow("Stay awake", "While open", s.keepAwake) { vm.update(s.copy(keepAwake = it)) }
        if (manage != null) { LinkRow("TVs", "${vm.devices.size} saved", onClick = manage) }
    ToggleRow("Auto updates", "Notify when ready", s.autoUpdate) {
        vm.update(s.copy(autoUpdate = it)); if (it) vm.checkForUpdate()
    }
    val u = vm.update
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clickable(role = Role.Button, enabled = u !is RemoteVM.Update.Checking && u !is RemoteVM.Update.Downloading) {
                when (u) {
                    is RemoteVM.Update.Ready -> vm.installUpdate()
                    is RemoteVM.Update.Manual -> vm.openReleasePage()
                    else -> vm.checkForUpdate(manual = true)
                }
            }
            .padding(vertical = 16.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(when (u) { is RemoteVM.Update.Ready -> "Install update"; is RemoteVM.Update.Manual -> "Get update"; else -> "Check now" }, color = T.Ink, fontSize = 15.sp)
            Text(
                when (u) {
                    RemoteVM.Update.Idle -> "Latest build on GitHub"
                    RemoteVM.Update.Checking -> "Checking…"
                    RemoteVM.Update.UpToDate -> "You're on the latest build"
                    is RemoteVM.Update.Downloading -> "Downloading ${u.info.name}… ${(u.progress * 100).toInt()}%"
                    is RemoteVM.Update.Ready -> "Version ${u.info.name} is downloaded"
                    is RemoteVM.Update.Failed -> u.message
                    is RemoteVM.Update.Manual -> "Version ${u.info.name} needs a reinstall: tap to download it"
                },
                color = if (u is RemoteVM.Update.Failed) DangerInk else Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chevron()
    }
            if (openPrivacy != null) { Divider(); LinkRow("Privacy & data", "Storage, network, erase", last = true, onClick = openPrivacy) }
    }

    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev" }
    Fold("About", "Version $version") {
    Text("Mini Remote needs no account and has no ads, analytics or tracking.",
        color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 16.dp))
    Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names and logos shown are trademarks of their owners. " +
        "Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' published or documented remote-control interfaces on your own network.",
        color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
    Text("Version $version", color = T.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    if (openLicenses != null) {
        Divider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = openLicenses).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Licences", color = T.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chevron()
        }
    } else Spacer(Modifier.height(10.dp))
        }
    }
    }
}

/**
 * A settings section as one card that folds: the title and a one-line summary of what's set,
 * and the rows when open. Remembered while Settings stays open.
 */
@Composable
internal fun Fold(title: String, summary: String, startOpen: Boolean = false, collapsible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    // Native settings style: no cards. A small accent heading, then the rows; only the big
    // sections (Appearance, About) fold away, with a one-line summary while folded.
    val accent = LocalAccent.current
    if (!collapsible) {
        Text(title, color = accent, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 28.dp, bottom = 2.dp, start = 2.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth(), content = content)
        return
    }
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(startOpen) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "fold")
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clickable(onClickLabel = if (open) "collapse $title" else "expand $title", role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed"; heading() }
                .padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = accent, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                if (!open) Text(summary, color = Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.graphicsLayer { rotationZ = turn }) { LineIcon(Ico.DOWN, T.Dim, 18.dp, stroke = 2.2f) }
        }
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Settings rows draw no lines between them (native style); other pages keep theirs. */
internal val LocalRowLines = androidx.compose.runtime.compositionLocalOf { true }
/** The accent colour, for section headings. */
internal val LocalAccent = androidx.compose.runtime.compositionLocalOf { Color(0xFF8AB4F8) }

/** A tonal button with an icon, for the profile card. */
@Composable
private fun ProfileAction(ico: Ico, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        LineIcon(ico, T.Ink, 18.dp)
        Text(label, color = T.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A row that opens another page. */
@Composable
internal fun LinkRow(title: String, sub: String, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Ink, fontSize = 15.sp)
            Text(sub, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Chevron()
    }
    if (!last) Divider()
}

/** Profile: name and initials, and the whole setup saved to (or loaded from) a file. */
@Composable
internal fun ProfileCard(vm: RemoteVM, accent: Color) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s = vm.settings
    var name by remember(s.profileName) { mutableStateOf(s.profileName) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportProfile(com.krishna.miniremote.AppIcon.current(ctx)).toByteArray()) }
            vm.say("Profile saved")
        }.onFailure { vm.say("Couldn't save the profile") }
    }
    val load = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().take(400_000).toByteArray().decodeToString() } }.getOrNull()
            if (text == null) vm.say("Couldn't read that file")
            else vm.importProfile(text)?.let { icon -> if (icon != com.krishna.miniremote.AppIcon.current(ctx)) com.krishna.miniremote.AppIcon.set(ctx, icon) }
        }
    }
    val apps = vm.shortcuts.count { it.pinned }
    val stats = listOf(
        "${vm.devices.size} TV${if (vm.devices.size == 1) "" else "s"}",
        "$apps app${if (apps == 1) "" else "s"}",
    ).joinToString(" · ")
    var editing by remember { mutableStateOf(false) }
    if (editing) ProfileEditSheet(vm, accent) { editing = false }
    // A plain card: you (tap Edit for name and email), then backup and restore.
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(24.dp)).background(M.Container).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(initials(s.profileName), accent, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(s.profileName.ifBlank { "Add your name" }, color = if (s.profileName.isBlank()) Muted else T.Ink, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(s.profileEmail.ifBlank { stats }, color = Muted, fontSize = 12.5.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.height(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(role = Role.Button, onClickLabel = "edit profile") { editing = true }
                .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LineIcon(Ico.EDIT, T.Ink, 15.dp)
                    Text("Edit", color = T.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileAction(Ico.BACKUP, "Back up", Modifier.weight(1f)) { save.launch("Mini Remote profile${if (s.profileName.isNotBlank()) " - " + s.profileName else ""}.json") }
            ProfileAction(Ico.RESTORE, "Restore", Modifier.weight(1f)) { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
        Text("Backs up settings and apps. TVs pair again on a new phone.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
    }
}

/** Edit profile: name and email (both optional, kept on this phone and in backups). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val s = vm.settings
    var name by remember { mutableStateOf(s.profileName) }
    var email by remember { mutableStateOf(s.profileEmail) }
    val st = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(initials(name), accent, 72.dp) }
            Text("Profile", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text("Shown here and on Home. Stays on this phone.", color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp))
            @Composable fun field(label: String, value: String, hint: String, type: KeyboardType, cap: KeyboardCapitalization, set: (String) -> Unit) {
                Text(label, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 2.dp))
                BasicTextField(
                    value, set, singleLine = true,
                    textStyle = TextStyle(color = T.Ink, fontSize = 16.sp, fontFamily = Inter), cursorBrush = SolidColor(T.Ink),
                    keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = type, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = label },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().softFace(HOLLOW, sheen = false, corner = 14.dp, dpUnits = true).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(hint, color = Color(0xFF8A8A8E), fontSize = 16.sp); inner()
                        }
                    },
                )
            }
            field("Name", name, "Your name", KeyboardType.Text, KeyboardCapitalization.Words) { name = it.take(30) }
            field("Email", email, "Optional", KeyboardType.Email, KeyboardCapitalization.None) { email = it.take(80).trim() }
            val bad = email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            if (bad) Text("That doesn't look like an email address", color = DangerInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Btn("Cancel", kind = 1) { onClose() } }
                Box(Modifier.weight(1.4f)) { Btn("Save", enabled = !bad) {
                    vm.update(s.copy(profileName = name.trim(), profileEmail = email.trim())); vm.say("Profile saved"); onClose()
                } }
            }
        }
    }
}

/** A settings section's rows on one rounded card, with thin lines between rows. */
@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(M.Container).padding(horizontal = 16.dp),
        content = content,
    )
}


@Composable
internal fun Divider() { if (LocalRowLines.current) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3A3E))) }

@Composable
internal fun ToggleRow(title: String, sub: String?, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    // Whole row is one switch for TalkBack and one big touch target for fingers.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 16.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Ink, fontSize = 15.sp)
            sub?.let { Text(it, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        Toggle(checked)
    }
    if (!last) Divider()
}

@Composable
internal fun <V> SegRow(title: String, opts: List<Pair<V, String>>, value: V, last: Boolean = false, onPick: (V) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 16.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = T.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(Modifier.softFace(HOLLOW, sheen = false, corner = 10.dp, dpUnits = true).padding(3.dp).selectableGroup()) {
            opts.forEach { (v, l) ->
                val sel = v == value
                Box(
                    Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(8.dp))
                        .then(if (sel) Modifier.softFace(CARD, corner = 8.dp, dpUnits = true) else Modifier)
                        .selectable(sel, role = Role.RadioButton) { onPick(v) }
                        .semantics { stateDescription = if (sel) "$title: $l, selected" else "$title: $l" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(l, color = if (sel) Color.White else Muted, fontSize = 13.sp) }
            }
        }
    }
    if (!last) Divider()
}

/* ------------------------------------------------------------- keyboard etc. */

fun statusColor(l: Link, accent: Color): Color = when (l) {
    Link.READY -> Color(0xFF30D158)
    Link.CONNECTING -> Color(0xFFFFD60A)
    Link.ERROR -> T.Danger
    Link.IDLE -> Color(0xFFA1A1A6)
}
fun linkWord(l: Link) = when (l) { Link.READY -> "Connected"; Link.CONNECTING -> "Connecting..."; Link.ERROR -> "Can't reach"; Link.IDLE -> "" }

/**
 * Type on TV. Like the design, this sheet drops from the top (radius on its lower corners) so
 * the phone keyboard never covers it. The text box takes focus as soon as it opens.
 */
@Composable
fun KeyboardSheet(vm: RemoteVM, accent: Color, openSearch: Boolean = true, startVoice: Boolean = false, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val voice = remember { VoiceInput(ctx) }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }
    /** Every change goes to the TV as you type: new letters typed, removed ones deleted. */
    fun change(new: String) { vm.liveType(text, new); text = new }
    // Spoken words are added to what's there (with a space) and typed on the TV.
    /** Runs the search on the TV (Enter in its box; Roku searches) and closes. */
    fun submit() { if (text.isNotBlank()) { vm.submitSearch(text); onClose() } }
    /** Clears the whole box, on the phone and on the TV (not just what was typed from here). */
    fun clearAll() { if (voice.on) voice.cancel(); val n = text.length; text = ""; vm.clearTvBox(n) }
    // Spoken words go into the box, are typed on the TV, and searched: no extra tap.
    voice.onFinal = { said -> change(said.trim()); voice.cancel(); submit() }
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok -> if (ok) voice.start() else vm.say("Microphone permission is off") }
    fun mic() {
        when {
            voice.on -> voice.cancel()
            !voice.available -> vm.say("No speech service on this phone")
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> voice.start()
            else -> askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    BackHandler { onClose() }
    // Open the TV's search screen right away, then focus the box.
    LaunchedEffect(Unit) { if (openSearch) vm.openTvSearch(); kotlinx.coroutines.delay(250); if (startVoice) mic() else runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(shown, enter = androidx.compose.animation.fadeIn()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).pointerInput(Unit) { detectTapGestures { onClose() } })
        }
        androidx.compose.animation.AnimatedVisibility(
            shown,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, .8f, .2f, 1f))) { -it },
        ) {
            Column(
                Modifier.fillMaxWidth().shadow(24.dp, shape).background(SheetBg, shape).border(1.dp, Line, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .statusBarsPadding().padding(top = 8.dp),
            ) {
                Header("Search", onClose)
                Body {
                    // 1 · The box and the mic.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(54.dp)) {
                            if (voice.on) {
                                // Voice mode: the box makes way for one calm line (and what you've said so far).
                                Row(
                                    Modifier.fillMaxSize().softFace(HOLLOW, sheen = false, corner = 14.dp, dpUnits = true).padding(horizontal = 16.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(Modifier.size(8.dp).background(if (voice.listening) accent else Muted, CircleShape))
                                    Text(voice.partial.ifEmpty { "Voice mode on…" }, color = T.Ink, fontSize = 16.sp, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            } else {
                                BasicTextField(
                                    text, { change(it) }, singleLine = true,
                                    textStyle = TextStyle(color = T.Ink, fontSize = 16.sp, fontFamily = Inter),
                                    cursorBrush = SolidColor(T.Ink),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                                    modifier = Modifier.fillMaxSize().focusRequester(focus).semantics { contentDescription = "Search on TV" },
                                    decorationBox = { inner ->
                                        Row(
                                            Modifier.fillMaxSize().softFace(HOLLOW, sheen = false, corner = 14.dp, dpUnits = true).padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            LineIcon(Ico.SEARCH, Muted, 18.dp)
                                            Box(Modifier.weight(1f)) {
                                                if (text.isEmpty()) Text("Search or type", color = Color(0xFF8A8A8E), fontSize = 16.sp)
                                                inner()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (voice.on) accent.copy(alpha = 0.25f) else M.High)
                                .clickable(onClickLabel = if (voice.on) "stop voice" else "voice mode", role = Role.Button) { mic() }
                                .semantics { contentDescription = "Voice" },
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(if (voice.on) Ico.CLOSE else Ico.MIC, if (voice.on) accent else T.Ink, 22.dp) }
                    }
                    // 2 · One line on what happens.
                    Text(
                        when {
                            voice.on -> "Speak: it's searched when you stop."
                            voice.error != null -> voice.error!!
                            vm.searchesApps -> "Typed into the box selected on the TV."
                            else -> "Typed on the TV as you go."
                        },
                        color = Muted, fontSize = 12.5.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    // 3 · Clear (all of the TV's box) and Search.
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { Btn("Clear", kind = 1) { clearAll() } }
                        Box(Modifier.weight(1.4f)) { Btn("Search", enabled = text.isNotBlank() || voice.on) { if (voice.on) voice.cancel(); submit() } }
                    }
                    // 4 · Search an app straight away, and the arrows to pick a box on the TV.
                    if (vm.searchesApps) {
                        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Search in", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(end = 4.dp))
                            listOf("youtube" to "YouTube", "play" to "Play Store").forEach { (w, label) ->
                                val ok = text.isNotBlank()
                                Box(
                                    Modifier.weight(1f).height(38.dp).clip(CircleShape).background(M.High)
                                        .clickable(role = Role.Button, enabled = ok) { vm.searchIn(w, text); onClose() }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, color = if (ok) T.Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Box(Modifier.padding(top = 18.dp)) { NavPad(vm) }
                    }
                }
            }
        }
    }
}

/**
 * Speech to text inside the app (no system popup): Android's SpeechRecognizer, preferring
 * on-device recognition. [level] drives the wave, [partial] shows words as they're heard,
 * [onFinal] gets the result. Listens only while the user asks.
 */
internal class VoiceInput(private val ctx: android.content.Context) {
    /** Voice mode: keeps listening, phrase after phrase, until [cancel]. */
    var on by mutableStateOf(false); private set
    var listening by mutableStateOf(false); private set
    var level by mutableStateOf(0f); private set
    var partial by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    var onFinal: (String) -> Unit = {}
    /** Speech works on the phone: its own on-device recogniser (Android 13+), or a speech service. */
    val available: Boolean get() = onDevice() || android.speech.SpeechRecognizer.isRecognitionAvailable(ctx)
    private var rec: android.speech.SpeechRecognizer? = null

    /** The on-device recogniser said it can't do this language: use the speech service instead. */
    private var useService = false

    private fun onDevice(): Boolean = !useService && android.os.Build.VERSION.SDK_INT >= 33 &&
        runCatching { android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)

    /** On-device first (private, works offline: Android 13+), else the phone's speech service (Android 8+). */
    private fun create(): android.speech.SpeechRecognizer =
        if (onDevice() && android.os.Build.VERSION.SDK_INT >= 33) android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        else android.speech.SpeechRecognizer.createSpeechRecognizer(ctx)

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun again() { if (on) main.postDelayed({ if (on) listen() }, 250) }

    fun start() { on = true; listen() }

    private fun listen() {
        error = null; partial = ""; level = 0f
        val r = rec ?: runCatching { create() }.getOrNull()?.also { rec = it }
        if (r == null) { on = false; error = "No speech service on this phone"; return }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onError(code: Int) {
                listening = false; level = 0f
                // Silence or nothing understood: keep listening in voice mode.
                if (on && (code == android.speech.SpeechRecognizer.ERROR_NO_MATCH || code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) { again(); return }
                // On-device can't do this language (12 / 13 on Android 12+): switch to the speech service.
                if (on && (code == 12 || code == 13) && !useService) { useService = true; runCatching { rec?.destroy() }; rec = null; again(); return }
                // A stuck recogniser (busy / client error): start a fresh one and carry on.
                if (on && (code == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == android.speech.SpeechRecognizer.ERROR_CLIENT)) {
                    runCatching { rec?.destroy() }; rec = null; again(); return
                }
                on = false
                error = when (code) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
                    android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs a connection on this phone."
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
                    else -> "Couldn't listen. Try again."
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false; level = 0f
                val said = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                partial = ""
                if (said.isNotBlank()) onFinal(said)
                again()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                partial = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        listening = true
        runCatching { r.startListening(i) }.onFailure { listening = false; on = false; error = "Couldn't listen. Try again." }
    }

    /** Leaves voice mode; whatever was half heard is dropped. */
    fun cancel() { on = false; main.removeCallbacksAndMessages(null); runCatching { rec?.cancel() }; listening = false; level = 0f; partial = "" }
    fun destroy() { cancel(); runCatching { rec?.destroy() }; rec = null }
}

/** Fallback when a pairing code is needed while the sheet is closed. */
@Composable
fun PairCodeDialog(vm: RemoteVM, prompt: PairPrompt, accent: Color) {
    AlertDialog(
        onDismissRequest = { vm.answerCode(null) },
        containerColor = SheetBg,
        title = { Text("Pair with ${prompt.tvName}", color = T.Ink, modifier = Modifier.semantics { heading() }) },
        text = { Column { CodeEntry(vm, prompt) } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.answerCode(null) }) { Text("Cancel", color = Muted) } },
    )
}

/** "Are you sure?" as a small sheet: title, one line, the action, and Cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(title: String, text: String, action: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp)) {
            Text(title, color = T.Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() })
            Text(text, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp))
            Btn(action, kind = if (danger) 2 else 0) { onConfirm(); onDismiss() }
            Btn("Cancel", kind = 1, onClick = onDismiss)
        }
    }
}

@Composable
internal fun InfoBlock(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, color = T.Ink, fontSize = 15.sp)
        Text(body, color = Muted, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Third-party software in the app, its licence, and the full licence texts (shipped in res/raw). */
private val THIRD_PARTY = listOf(
    Triple("AndroidX, Jetpack Compose, Material 3", "Google LLC · The Android Open Source Project", "Apache License 2.0"),
    Triple("Kotlin standard library, kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache License 2.0"),
    Triple("OkHttp, Okio", "Square, Inc.", "Apache License 2.0"),
    Triple("AndroidSVG", "Paul LeBeau, Cave Rock Software Ltd.", "Apache License 2.0"),
    Triple("Hugeicons (free Stroke Rounded icons)", "Hugeicons", "MIT License"),
    Triple("Simple Icons (app logos, downloaded when enabled)", "Simple Icons contributors", "CC0 1.0 Universal; logos remain trademarks of their owners"),
)

@Composable
internal fun LicensesPage() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var shown by remember { mutableStateOf<Int?>(null) }
    Group {
        THIRD_PARTY.forEachIndexed { i, (name, who, lic) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(name, color = T.Ink, fontSize = 15.sp)
                Text(who, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                Text(lic, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (i < THIRD_PARTY.lastIndex) Divider()
        }
    }
    Hint("Tap to read in full.", top = 10)
    listOf("Apache License 2.0" to com.krishna.miniremote.R.raw.apache_2_0, "CC0 1.0 Universal" to com.krishna.miniremote.R.raw.cc0_1_0, "MIT License (Hugeicons)" to com.krishna.miniremote.R.raw.mit_hugeicons)
        .forEach { (title, res) ->
            Btn(if (shown == res) "Hide $title" else title, kind = 1) { shown = if (shown == res) null else res }
            if (shown == res) {
                val text = remember(res) { runCatching { ctx.resources.openRawResource(res).bufferedReader().use { it.readText() } }.getOrDefault("") }
                Text(text, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = Mono, modifier = Modifier.padding(top = 12.dp))
            }
        }
}

/** Privacy & data: what's kept, what goes online, permissions, and the erase buttons. */
@Composable
internal fun PrivacyPage(vm: RemoteVM) {
    Group {
        InfoBlock("Storage", "TVs, pairing keys (encrypted), quick access and settings. On this phone only; never backed up.")
        Divider()
        InfoBlock("Network", "Only TVs on your Wi-Fi. Updates from GitHub; logos from Google Play, Simple Icons, the apps' own websites or Google's favicon service. Both optional.")
        Divider()
        InfoBlock("Voice", "The mic listens only while you tap it in Search. Speech is turned into text on the phone where possible (otherwise by the phone's speech service); only the text goes to your TV. Nothing is saved.")
        Divider()
        InfoBlock("Permissions", "Network, Bluetooth (only if you pair a TV over Bluetooth), microphone (voice search and talking to the TV, only while listening), vibration, notifications (updates, the remote), installing updates. No location, contacts or files.")
    }
    var ask by remember { mutableStateOf<String?>(null) }
    Btn("Forget all TVs", kind = 1) { ask = "tvs" }
    Btn("Erase data", kind = 2) { ask = "all" }
    when (ask) {
        "tvs" -> ConfirmSheet("Forget all TVs?", "Removes every TV and its pairing keys. You'll pair each TV again to use it.",
            "Forget all", danger = true, onConfirm = { vm.forgetAllTvs() }, onDismiss = { ask = null })
        "all" -> ConfirmSheet("Erase all data?", "Removes TVs, pairing keys, quick access, channels and settings. The app starts fresh.",
            "Erase", danger = true, onConfirm = { vm.eraseAll() }, onDismiss = { ask = null })
    }

}
