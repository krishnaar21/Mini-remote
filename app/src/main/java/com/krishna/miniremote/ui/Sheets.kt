package com.krishna.miniremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
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
internal val SheetBg = Color(0xFF1C1C1E)
internal val CardBg = Color(0xFF222223)
internal val Line = Color(0xFF2E2E30)
internal val Muted = Color(0xFF8C8C90)
internal val LeadInk = Color(0xFFC3C3C7)
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
        Column(Modifier.navigationBarsPadding()) {
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
                    Header("Allow local network access", onClose, back = if (canBack) ::back else null)
                    Body { LocalPage { vm.markLocalSeen(); replace(Page.Scan) } }
                }
                Page.Scan -> {
                    Header("Add a TV", onClose, back = if (canBack) ::back else null)
                    Body { ScanPage(vm, accent, pick = { d -> vm.addAndSelect(d); go(Page.Pairing(d.id)) }, manual = { go(Page.Manual) }) }
                }
                Page.Manual -> {
                    Header("Enter an address", onClose, back = ::back)
                    Body { ManualPage(vm, accent) { d -> go(Page.Pairing(d.id)) } }
                }
                is Page.Pairing -> {
                    val d = vm.devices.firstOrNull { it.id == page.id }
                    if (d == null) { LaunchedEffect(Unit) { back() }; return@Column }
                    PairPage(vm, d, accent, onClose, back = ::back)
                }
                Page.Settings -> {
                    Header("Settings", onClose, back = if (canBack) ::back else null)
                    Body { SettingsPage(vm, accent, manage = null) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ building blocks */

internal val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** .grab: 38 x 4 handle, 12 above and 4 below. */
@Composable
internal fun Grab() = Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(38.dp, 4.dp).background(Color(0xFF444444), RoundedCornerShape(4.dp)))

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
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 12.dp).padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        back?.let { CircleIcon(Ico.CHEVRON_L, "Back", it) }
        Text(title, color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.18).sp, maxLines = 2,
            modifier = Modifier.weight(1f).semantics { heading() })
        gear?.let { CircleIcon(Ico.GEAR, "Settings", it) }
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

@Composable internal fun Chevron() = LineIcon(Ico.CHEVRON_R, Color(0xFF6C6C70), 18.dp)

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

@Composable
internal fun DevicesPage(vm: RemoteVM, open: (String) -> Unit, add: () -> Unit) {
    if (vm.devices.isEmpty()) Lead("No TVs yet. Add one to start controlling it.")
    vm.devices.forEach { d ->
        val on = vm.activeId == d.id && vm.link == Link.READY
        CardRow(d.name, statusLine(vm, d), dot = if (on) OkGreen else Color(0xFF555555), onClick = { open(d.id) }, clickLabel = "open") { Chevron() }
    }
    Btn("Add a TV", onClick = add)
    Hint("Soundbar volume goes through your TV over HDMI-CEC / ARC, so there's nothing to pair for it.", top = 18)
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
    Btn("Save name", kind = 1, enabled = name.isNotBlank() && name.trim() != d.name) { vm.rename(d.id, name); vm.say("Saved") }
    Btn(if (arm) "Tap again to forget this TV" else "Forget this TV", kind = 2) {
        // Removing it makes this page's TV disappear, and the sheet steps back on its own.
        if (!arm) { arm = true; vm.feel.fire(com.krishna.miniremote.Feel.HEAVY) } else vm.remove(d)
    }
}

@Composable
internal fun LocalPage(onContinue: () -> Unit) {
    Hero(Ico.WIFI, Color(0xFF1D2A3A), Color(0xFF6FB1FF))
    Lead("Remote will look for TVs on your Wi-Fi. It only talks to devices on your own network, and it needs no account.")
    Btn("Continue", onClick = onContinue)
}

@Composable
internal fun ScanPage(vm: RemoteVM, accent: Color, pick: (Device) -> Unit, manual: () -> Unit) {
    LaunchedEffect(Unit) { vm.scan() }
    if (vm.scanning) {
        Spinner(accent)
        Lead("Looking for TVs on your Wi-Fi…", center = true)
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
    Btn("Enter an IP address", kind = 1, onClick = manual)
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
    Hint("Find it on your TV under Settings › Network. Only home-network addresses (like 192.168…) are accepted.")
}

/* ----------------------------------------------------------------- pairing */

@Composable
internal fun PairPage(vm: RemoteVM, d: Device, accent: Color, onClose: () -> Unit, back: () -> Unit) {
    val active = vm.activeId == d.id
    val prompt = vm.pairPrompt
    when {
        prompt != null -> {
            Header("Enter the code", onClose, back = { vm.answerCode(null); back() })
            Body { CodeEntry(vm, prompt) }
        }
        active && vm.link == Link.READY -> {
            Header("Connected", onClose)
            Body {
                Hero(Ico.CHECK, Color(0xFF1D3A29), OkGreen)
                Lead("${d.name} is ready. Try the clickpad.", center = true)
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
            Header("Connect ${d.name}", onClose, back = back)
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
    Lead("${prompt.tvName} is showing a 6-character code. Type it here.")
    BasicTextField(
        code,
        // Codes are hex: keep 0-9 and A-F only, uppercase, max 6.
        { v -> code = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6) },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (ok) vm.answerCode(code) }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pairing code, 6 characters" },
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
internal fun SettingsPage(vm: RemoteVM, accent: Color, manage: (() -> Unit)?) {
    val s = vm.settings
    SectionTitle("Remote")
    SegRow("Control", listOf("dial" to "Dial", "trackpad" to "Trackpad"), s.controlStyle) { vm.update(s.copy(controlStyle = it)) }
    ToggleRow("Phone volume buttons", "Change the TV's volume while the app is open", s.volumeKeys) { vm.update(s.copy(volumeKeys = it)) }
    Hint("On the remote, one finger navigates and two fingers slide for volume (up or down) and channels (sideways). Tap Vol · Ch (or swipe up) for the volume and channel buttons. Double tap OK to mute; hold it for voice.", top = 6)

    SectionTitle("Accent colour")
    Row(Modifier.selectableGroup().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENTS.forEach { (name, hex) ->
            val sel = s.accentHex.equals(hex, true)
            Box(
                Modifier.size(44.dp)
                    .selectable(sel, role = Role.RadioButton) { vm.update(s.copy(accentHex = hex)) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(44.dp).background(accentColor(hex), CircleShape).border(2.dp, Color.Black.copy(0.25f), CircleShape))
                // ::after ring: 2px white, 5px outside the swatch
                if (sel) Box(Modifier.requiredSize(54.dp).border(2.dp, Color.White, CircleShape))
            }
        }
    }
    Hint("Pressed icons, the active arrow and the transmit light use this colour.", top = 6)

    SectionTitle("Feel")
    ToggleRow("Haptics", "Physical feedback on every press and scrub", s.haptics) { vm.update(s.copy(haptics = it)) }
    SegRow("Strength", HAPTIC_STRENGTHS, s.hapticStrength) { vm.update(s.copy(hapticStrength = it)) }
    ToggleRow("Sound", "Soft click with every press and scrub", s.sound) { vm.update(s.copy(sound = it)) }
    SegRow("Sound level", SOUND_LEVELS, s.soundLevel) { vm.update(s.copy(soundLevel = it)); vm.feel.fire(com.krishna.miniremote.Feel.PRESS) }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Scrub distance", color = T.Ink, fontSize = 15.sp)
                Text("Shorter = faster scrolling", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text("${s.scrubStepDp.roundToInt()} dp", color = Muted, fontSize = 13.sp, fontFamily = Mono)
        }
        Fader(s.scrubStepDp, 24f..60f, accent, label = "Scrub distance", valueText = "${s.scrubStepDp.roundToInt()} dp", steps = 17) {
            vm.update(s.copy(scrubStepDp = it))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("FAST", color = Color(0xFF6C6C70), fontSize = 11.sp, letterSpacing = 0.88.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
            Text("SLOW", color = Color(0xFF6C6C70), fontSize = 11.sp, letterSpacing = 0.88.sp, fontFamily = Mono)
        }
    }
    Divider()
    ToggleRow("Keep screen on", "While the remote is open", s.keepAwake) { vm.update(s.copy(keepAwake = it)) }

    if (manage != null) {
    SectionTitle("Devices")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = manage).padding(vertical = 16.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manage TVs", color = T.Ink, fontSize = 15.sp)
            Text("${vm.devices.size} saved", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Chevron()
    }
    Divider()
    }
    SectionTitle("Updates")
    ToggleRow("Automatic updates", "Download new builds in the background, then ask to install", s.autoUpdate) {
        vm.update(s.copy(autoUpdate = it)); if (it) vm.checkForUpdate()
    }
    val u = vm.update
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clickable(role = Role.Button, enabled = u !is RemoteVM.Update.Checking && u !is RemoteVM.Update.Downloading) {
                if (u is RemoteVM.Update.Ready) vm.installUpdate() else vm.checkForUpdate(manual = true)
            }
            .padding(vertical = 16.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(if (u is RemoteVM.Update.Ready) "Install update" else "Check for updates", color = T.Ink, fontSize = 15.sp)
            Text(
                when (u) {
                    RemoteVM.Update.Idle -> "Looks at the latest build on GitHub"
                    RemoteVM.Update.Checking -> "Checking…"
                    RemoteVM.Update.UpToDate -> "You're on the latest build"
                    is RemoteVM.Update.Downloading -> "Downloading ${u.info.name}… ${(u.progress * 100).toInt()}%"
                    is RemoteVM.Update.Ready -> "Version ${u.info.name} is downloaded"
                    is RemoteVM.Update.Failed -> u.message
                },
                color = if (u is RemoteVM.Update.Failed) DangerInk else Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chevron()
    }
    Divider()
    SectionTitle("About")
    Hint("Remote talks only to devices on your local network. No account, no tracking. Pairing keys stay on this phone.", top = 4)
    // Lets you tell builds apart: CI numbers every build (0.2.<run>).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev"
    }
    Text("Version $version", color = Muted, fontSize = 12.5.sp, fontFamily = Mono, modifier = Modifier.padding(top = 10.dp))
}

@Composable
internal fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF262627)))

@Composable
internal fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
    Divider()
}

@Composable
internal fun <V> SegRow(title: String, opts: List<Pair<V, String>>, value: V, onPick: (V) -> Unit) {
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
    Divider()
}

/* ------------------------------------------------------------- keyboard etc. */

fun statusColor(l: Link, accent: Color): Color = when (l) {
    Link.READY -> Color(0xFF30D158)
    Link.CONNECTING -> Color(0xFFFFD60A)
    Link.ERROR -> T.Danger
    Link.IDLE -> Color(0xFF55555B)
}
fun linkWord(l: Link) = when (l) { Link.READY -> "Connected"; Link.CONNECTING -> "Connecting..."; Link.ERROR -> "Can't reach"; Link.IDLE -> "" }

/**
 * Type on TV. Like the design, this sheet drops from the top (radius on its lower corners) so
 * the phone keyboard never covers it. The text box takes focus as soon as it opens.
 */
@Composable
fun KeyboardSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf(true) }
    // Voice search: the phone's own speech recogniser fills in the words, then we search the TV.
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        val said = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) { text = said; vm.search(said); onClose() }
    }
    fun listen() {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search on TV")
        // No speech service on the phone: fall back to the TV's own voice search.
        try { speech.launch(i) } catch (e: android.content.ActivityNotFoundException) { vm.press(com.krishna.miniremote.RemoteKey.VOICE); onClose() }
    }
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    BackHandler { onClose() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); runCatching { focus.requestFocus() } }
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
                Header(if (search) "Search on TV" else "Type on TV", onClose)
                Body {
                    // One sheet for both: search the TV, or type into a box that's already open on it.
                    SegRow("Mode", listOf(true to "Search", false to "Type"), search) { search = it }
                    Spacer(Modifier.height(14.dp))
                    if (search) {
                        Lead("Find movies, shows and apps. The TV opens its own search with your words.")
                        Field("Search for", text, { text = it }, modifier = Modifier.focusRequester(focus), placeholder = "Movies, shows, apps",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) { vm.search(text); onClose() } }),
                            trailing = {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f))
                                        .clickable(onClickLabel = "search by voice", role = Role.Button) { listen() }
                                        .semantics { contentDescription = "Voice search" },
                                    contentAlignment = Alignment.Center,
                                ) { LineIcon(Ico.MIC, accent, 20.dp) }
                            })
                        Btn("Search", enabled = text.isNotBlank()) { vm.search(text); onClose() }
                        return@Body
                    }
                    Lead("Open a search box on the TV, then type here and send.")
                    Field("Text for the TV", text, { text = it }, modifier = Modifier.focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (text.isNotEmpty()) { vm.typeText(text); text = "" } }))
                    // .two: 1fr 1.4fr, gap 12
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { Btn("Delete", kind = 1) { vm.backspace() } }
                        Box(Modifier.weight(1.4f)) { Btn("Send", enabled = text.isNotEmpty()) { vm.typeText(text); text = "" } }
                    }
                }
            }
        }
    }
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
