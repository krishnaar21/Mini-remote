package com.krishna.miniremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

private val SheetBg = Color(0xFF1B1B1C)
private val CardBg = Color(0xFF222223)
private val Line = Color(0xFF2E2E30)
private val Muted = Color(0xFF8C8C90)
private val LeadInk = Color(0xFFC3C3C7)
private val Fill2 = Color(0xFF2A2A2B)
private val OkGreen = Color(0xFF4DDC88)
private val SwitchOn = Color(0xFF34C759)
private val DangerBg = Color(0xFF3A1E1E)
private val DangerInk = Color(0xFFFF8A8A)

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

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = SheetBg, contentColor = T.Ink) {
        BackHandler(enabled = stack.size > 1) { back() }
        val canBack = stack.size > 1 || page !is Page.Devices
        Column(Modifier.navigationBarsPadding()) {
            when (page) {
                Page.Devices -> {
                    Header("Devices", onClose, gear = { go(Page.Settings) })
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
                    Body { SettingsPage(vm, accent, manage = { go(Page.Devices) }) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ building blocks */

@Composable
private fun Header(title: String, onClose: () -> Unit, back: (() -> Unit)? = null, gear: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        back?.let { CircleIcon(Ico.CHEVRON_L, "Back", it) }
        Text(title, color = T.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            modifier = Modifier.weight(1f).semantics { heading() })
        gear?.let { CircleIcon(Ico.SLIDERS, "Settings", it) }
        CircleIcon(Ico.CLOSE, "Close", onClose)
    }
}

@Composable
private fun CircleIcon(ico: Ico, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Fill2)
            .clickable(role = Role.Button, onClickLabel = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(ico, T.Ink, 20.dp) }
}

@Composable
private fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), content = content)
}

@Composable
private fun Lead(text: String, center: Boolean = false) =
    Text(text, color = LeadInk, fontSize = 15.sp, lineHeight = 23.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))

@Composable
private fun Hint(text: String, top: Int = 16) =
    Text(text, color = Muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = top.dp))

@Composable
private fun SectionTitle(t: String) =
    Text(t.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() })

@Composable
private fun Btn(text: String, kind: Int = 0, enabled: Boolean = true, onClick: () -> Unit) {
    // 0 = primary (white), 1 = secondary, 2 = danger
    val (bg, fg) = when (kind) { 1 -> Fill2 to T.Ink; 2 -> DangerBg to DangerInk; else -> Color.White to Color(0xFF111111) }
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg.copy(0.4f), disabledContentColor = fg.copy(0.5f)),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 54.dp)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hero(ico: Ico, bg: Color, fg: Color) {
    Box(Modifier.fillMaxWidth().height(96.dp).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) { LineIcon(ico, fg, 30.dp) }
    }
}

@Composable
private fun Spinner(accent: Color) {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent, trackColor = Color(0xFF333333), strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
    }
}

/** Card-style list row: optional status dot, title + subtitle, then a trailing slot. */
@Composable
private fun CardRow(
    title: String,
    sub: String?,
    dot: Color? = null,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 66.dp)
            .clip(shape).background(CardBg).border(1.dp, Line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        dot?.let { Box(Modifier.size(9.dp).background(it, CircleShape)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Ink, fontSize = 15.sp)
            sub?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        trailing()
    }
}

@Composable private fun Chevron() = LineIcon(Ico.CHEVRON_R, Color(0xFF6C6C70), 18.dp)

@Composable
private fun Badge(text: String) =
    Text(text, color = Color(0xFFBBBBBB), fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))

private fun statusLine(vm: RemoteVM, d: Device): String {
    val st = if (vm.activeId != d.id) "" else when (vm.link) {
        Link.READY -> " · Connected"; Link.CONNECTING -> " · Connecting…"; else -> " · Not connected"
    }
    return "${d.brand.label} · ${d.host}$st"
}

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = T.Ink, unfocusedTextColor = T.Ink, focusedBorderColor = Color(0xFF8C8C90), unfocusedBorderColor = Line,
    cursorColor = T.Ink, focusedLabelColor = LeadInk, unfocusedLabelColor = Muted,
    focusedContainerColor = Color(0xFF141415), unfocusedContainerColor = Color(0xFF141415))

/* ---------------------------------------------------------------- devices */

@Composable
private fun DevicesPage(vm: RemoteVM, open: (String) -> Unit, add: () -> Unit) {
    if (vm.devices.isEmpty()) Lead("No TVs yet. Add one to start controlling it.")
    vm.devices.forEach { d ->
        val on = vm.activeId == d.id && vm.link == Link.READY
        CardRow(d.name, statusLine(vm, d), dot = if (on) OkGreen else Color(0xFF555555), onClick = { open(d.id) }, clickLabel = "open") { Chevron() }
    }
    Btn("Add a TV", onClick = add)
    Hint("Soundbar volume goes through your TV over HDMI-CEC / ARC, so there's nothing to pair for it.", top = 18)
}

@Composable
private fun DevicePage(vm: RemoteVM, d: Device, accent: Color) {
    val on = vm.activeId == d.id && vm.link == Link.READY
    var name by remember(d.id) { mutableStateOf(d.name) }
    var arm by remember(d.id) { mutableStateOf(false) }
    CardRow("Brand", d.brand.label)
    CardRow("Address", d.host)
    CardRow("Status", if (on) "Connected" else if (vm.activeId == d.id && vm.link == Link.CONNECTING) "Connecting…" else "Not connected")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, colors = fieldColors,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    if (on) Btn("Disconnect") { vm.disconnect() } else Btn("Connect") { vm.select(d) }
    Btn("Save name", kind = 1, enabled = name.isNotBlank() && name.trim() != d.name) { vm.rename(d.id, name); vm.say("Saved") }
    Btn(if (arm) "Tap again to forget this TV" else "Forget this TV", kind = 2) {
        // Removing it makes this page's TV disappear, and the sheet steps back on its own.
        if (!arm) { arm = true; vm.feel.fire(com.krishna.miniremote.Feel.HEAVY) } else vm.remove(d)
    }
}

@Composable
private fun LocalPage(onContinue: () -> Unit) {
    Hero(Ico.WIFI, Color(0xFF1D2A3A), Color(0xFF6FB1FF))
    Lead("Remote will look for TVs on your Wi-Fi. It only talks to devices on your own network, and it needs no account.")
    Btn("Continue", onClick = onContinue)
}

@Composable
private fun ScanPage(vm: RemoteVM, accent: Color, pick: (Device) -> Unit, manual: () -> Unit) {
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
private fun ManualPage(vm: RemoteVM, accent: Color, onAdded: (Device) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.ROKU) }
    var open by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(ip, { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15); err = null }, label = { Text("IP address") },
        placeholder = { Text("192.168.1.20", color = Color(0xFF5D5D62)) }, singleLine = true, isError = err != null, colors = fieldColors,
        shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(brand.label, {}, readOnly = true, label = { Text("Brand") }, colors = fieldColors, shape = RoundedCornerShape(12.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }, containerColor = Color(0xFF26262A)) {
            Brand.entries.forEach { b -> DropdownMenuItem(text = { Text(b.label, color = T.Ink) }, onClick = { brand = b; open = false }) }
        }
    }
    Text(err ?: "", color = DangerInk, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).heightIn(min = 18.dp)
        .semantics { liveRegion = LiveRegionMode.Assertive })
    Btn("Continue") {
        val (e, d) = vm.addManual(ip, brand)
        if (e != null) { err = e; vm.feel.fire(com.krishna.miniremote.Feel.ERROR) } else if (d != null) onAdded(d)
    }
    Hint("Find it on your TV under Settings › Network. Only home-network addresses (like 192.168…) are accepted.")
}

/* ----------------------------------------------------------------- pairing */

@Composable
private fun PairPage(vm: RemoteVM, d: Device, accent: Color, onClose: () -> Unit, back: () -> Unit) {
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
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append("${left}s") }
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

private fun brandHelp(b: Brand): String? = when (b) {
    Brand.ROKU -> "Roku doesn't need a pairing code. If it can't connect, open Settings › System › Advanced system settings › Control by mobile apps on the TV and choose Default."
    Brand.SAMSUNG -> "On Samsung, check Settings › General › External Device Manager › Device Connect Manager. Menu names vary by model."
    Brand.LG -> "On LG, make sure LG Connect Apps / Mobile TV On is turned on. Menu names vary by model."
    Brand.ANDROID_TV -> "Make sure the TV is on and on the same Wi-Fi as your phone."
}

/** Six boxes, one per character, backed by a single text field so paste and TalkBack work normally. */
@Composable
private fun CodeEntry(vm: RemoteVM, prompt: PairPrompt) {
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
                            Modifier.width(44.dp).height(56.dp).background(Color(0xFF141415), RoundedCornerShape(12.dp))
                                .border(1.dp, if (focus) Muted else Line, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(ch, color = T.Ink, fontSize = 22.sp, fontFamily = FontFamily.Monospace) }
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
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(56.dp)

/* ---------------------------------------------------------------- settings */

@Composable
private fun SettingsPage(vm: RemoteVM, accent: Color, manage: () -> Unit) {
    val s = vm.settings
    SectionTitle("Accent colour")
    Row(Modifier.selectableGroup().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ACCENTS.forEach { (name, hex) ->
            val sel = s.accentHex.equals(hex, true)
            Box(
                Modifier.size(52.dp)
                    .selectable(sel, role = Role.RadioButton) { vm.update(s.copy(accentHex = hex)) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (sel) Box(Modifier.size(52.dp).border(2.dp, Color.White, CircleShape))
                Box(Modifier.size(42.dp).background(accentColor(hex), CircleShape).border(2.dp, Color.Black.copy(0.25f), CircleShape))
            }
        }
    }
    Hint("Pressed icons, the active arrow and the transmit light use this colour.", top = 6)

    SectionTitle("Feel")
    ToggleRow("Haptics", "Physical feedback on every press and scrub", s.haptics) { vm.update(s.copy(haptics = it)) }
    SegRow("Strength", HAPTIC_STRENGTHS, s.hapticStrength) { vm.update(s.copy(hapticStrength = it)) }
    ToggleRow("Sound", "Soft click with every press and scrub", s.sound) { vm.update(s.copy(sound = it)) }
    SegRow("Sound level", SOUND_LEVELS, s.soundLevel) { vm.update(s.copy(soundLevel = it)); vm.feel.fire(com.krishna.miniremote.Feel.PRESS) }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Scrub distance", color = T.Ink, fontSize = 15.sp)
                Text("Shorter = faster scrolling", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text("${s.scrubStepDp.roundToInt()} dp", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Fader(s.scrubStepDp, 24f..60f, accent, label = "Scrub distance", valueText = "${s.scrubStepDp.roundToInt()} dp", steps = 17) {
            vm.update(s.copy(scrubStepDp = it))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("FAST", color = Color(0xFF6C6C70), fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("SLOW", color = Color(0xFF6C6C70), fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        }
    }
    Divider()
    ToggleRow("Gyroscope lighting", "Highlights shift as you tilt the phone", s.gyro) { vm.update(s.copy(gyro = it)) }
    ToggleRow("Keep screen on", "While the remote is open", s.keepAwake) { vm.update(s.copy(keepAwake = it)) }

    SectionTitle("Devices")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = manage).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manage TVs", color = T.Ink, fontSize = 15.sp)
            Text("${vm.devices.size} saved", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Chevron()
    }
    SectionTitle("About")
    Hint("Remote talks only to devices on your local network. No account, no tracking. Pairing keys stay on this phone.", top = 4)
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF262627)))

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // Whole row is one switch for TalkBack and one big touch target for fingers.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Ink, fontSize = 15.sp)
            sub?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        Switch(checked, null, colors = SwitchDefaults.colors(checkedTrackColor = SwitchOn, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Color(0xFF3A3A3C), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))
    }
    Divider()
}

@Composable
private fun SegRow(title: String, opts: List<Pair<Float, String>>, value: Float, onPick: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = T.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(Modifier.background(Fill2, RoundedCornerShape(10.dp)).padding(2.dp).selectableGroup()) {
            opts.forEach { (v, l) ->
                val sel = v == value
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Color(0xFF4A4A4D) else Color.Transparent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = SheetBg, contentColor = T.Ink) {
        Column(Modifier.navigationBarsPadding()) {
            Header("Type on TV", onClose)
            Body {
                Lead("Open a search box on the TV, then type here and send.")
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text for the TV") }, colors = fieldColors, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotEmpty()) { vm.typeText(text); text = "" } }))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { Btn("Delete", kind = 1) { vm.backspace() } }
                    Box(Modifier.weight(1.4f)) { Btn("Send", enabled = text.isNotEmpty()) { vm.typeText(text); text = "" } }
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
