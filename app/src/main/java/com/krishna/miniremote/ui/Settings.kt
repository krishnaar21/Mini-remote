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

/*
 * Settings, in the phone's own style: a profile card at the top, then rounded groups of rows,
 * each with a coloured icon, its title, what's set (on the right) and a chevron. Rows with more
 * to them open a sheet; simple on / off rows switch in place.
 */

/** Left-aligned sheet title with its line of explanation close under it. */
@Composable
internal fun SheetTitle(title: String, sub: String? = null, modifier: Modifier = Modifier, center: Boolean = false, small: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(bottom = 6.dp).semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(title, color = T.Ink, fontSize = if (small) 18.sp else 22.sp, fontWeight = FontWeight.SemiBold, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        sub?.let { Text(it, color = SubInk, fontSize = if (small) 13.sp else 14.sp, lineHeight = if (small) 18.sp else 19.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** A sheet opened from a settings row: title on the left, its rows on a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetSheet(title: String, sub: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val st = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            SheetTitle(title, sub, Modifier.padding(top = 12.dp, bottom = 2.dp))
            content()
        }
    }
}

/** Secondary text: quieter than the titles. */
internal val SubInk get() = Look.p.sub

/**
 * A settings icon on its own rounded square. One calm colour for every tile (the row's colour
 * is ignored) so the page stays quiet; [fill] marks a chosen option (accent tile, white icon).
 */
@Composable
internal fun IconTile(ico: Ico, @Suppress("UNUSED_PARAMETER") tint: Color, fill: Color? = null) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(fill ?: M.High), contentAlignment = Alignment.Center) {
        LineIcon(ico, if (fill != null) Color.White else T.Ink, 19.dp)
    }
}

/** A grey heading over a group of cards ("Remote", "Look"…). */
@Composable
internal fun SetHead(text: String, first: Boolean = false) =
    Text(text, color = SubInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = if (first) 8.dp else 32.dp).semantics { heading() })

/** A rounded group of rows. */
@Composable
internal fun SetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(26.dp)).background(M.Container), content = content)
}

/** The thin line between rows, starting after the icon. */
@Composable
internal fun SetLine(inset: androidx.compose.ui.unit.Dp = 64.dp) = Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(T.Ink.copy(alpha = 0.07f)))

/** One row: icon, title (and a smaller line), what's set, and a chevron when it opens something. */
@Composable
internal fun SetRow(ico: Ico, tint: Color, title: String, value: String? = null, sub: String? = null, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 16.dp, end = 14.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(ico, tint)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = T.Ink, fontSize = 17.sp, lineHeight = 21.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        value?.let { Text(it, color = SubInk, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 8.dp)) }
        if (chevron) Chevron()
    }
}

/** An on / off row with an icon: the whole row is the switch. */
@Composable
internal fun SetToggle(ico: Ico, tint: Color, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(start = 16.dp, end = 14.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(ico, tint)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
            Text(title, color = T.Ink, fontSize = 17.sp, lineHeight = 21.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        Toggle(checked)
    }
}

/** A choice in a sheet: the option, and a tick on the one that's set. */
@Composable
private fun Choice(label: String, sub: String?, sel: Boolean, accent: Color, ico: Ico? = null, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).selectable(sel, role = Role.RadioButton, onClick = onPick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ico?.let { IconTile(it, accent, fill = if (sel) accent else null); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, color = T.Ink, fontSize = 17.sp, lineHeight = 21.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (sel) LineIcon(Ico.CHECK, accent, 20.dp)
    }
}

private object SetTint {
    val blue = Color(0xFF4C8DFF); val green = Color(0xFF34C759); val orange = Color(0xFFFF9F0A); val purple = Color(0xFFAF7BFF)
    val pink = Color(0xFFFF5C8A); val teal = Color(0xFF30C8D6); val grey = T.Sub; val red = Color(0xFFFF6B5E); val yellow = Color(0xFFFFD60A)
}

@Composable
internal fun SettingsPage(vm: RemoteVM, accent: Color, manage: (() -> Unit)?, openLicenses: (() -> Unit)? = null, openPrivacy: (() -> Unit)? = null, openGestures: (() -> Unit)? = null) {
    val s = vm.settings
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Shot.openSetting?.let { Shot.openSetting = null; delay(700); open = it } }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev" }
    val iconName = com.krishna.miniremote.AppIcon.all.firstOrNull { it.first == com.krishna.miniremote.AppIcon.current(ctx) }?.second ?: "Pebble"
    val accentName = ACCENTS.firstOrNull { it.second.equals(s.accentHex, true) }?.first ?: "Custom"

    // In groups under grey headings, as on iOS. (The profile lives on in backups, not here.)
    SetHead("Remote", first = true)
    SetCard {
        SetRow(if (s.controlStyle == "dial") Ico.DIAL else Ico.TRACKPAD, SetTint.blue, "Control", when (s.controlStyle) { "trackpad" -> "Trackpad"; "touch" -> "Touch"; else -> "Dial" }) { open = "control" }
        SetLine()
        SetRow(Ico.GRID, SetTint.purple, "Quick access", if (s.appsIn == "more") "More sheet" else "Home page") { open = "apps" }
        if (openGestures != null) { SetLine(); SetRow(Ico.TRACKPAD, SetTint.teal, "Gestures", onClick = openGestures) }
    }
    SetHead("TV")
    SetCard {
        val on = listOf(s.volumeKeys, s.autoKeyboard, s.notifRemote, s.autoClose).count { it }
        SetRow(Ico.TV, SetTint.blue, "TV controls", "$on of 4 on") { open = "tv" }
        SetLine()
        SetRow(Ico.SPEAKER, SetTint.red, "Touch & sound", listOf(if (s.haptics) "Haptics" else null, if (s.sound) "Sound" else null).filterNotNull().joinToString(", ").ifEmpty { "Off" }) { open = "feel" }
    }
    SetHead("Look")
    SetCard {
        SetRow(Ico.PALETTE, SetTint.pink, "Appearance", "${Palettes.of(s.theme).label} · $accentName") { open = "look" }
        SetLine()
        SetToggle(Ico.STAR, SetTint.yellow, "App logos", "Downloaded for your apps", s.logos) { vm.update(s.copy(logos = it)) }
        SetLine()
        SetToggle(Ico.SUN, SetTint.purple, "Keep screen on", "While the remote is open", s.keepAwake) { vm.update(s.copy(keepAwake = it)) }
    }
    SetHead("Data & help")
    SetCard {
        if (manage != null) { SetRow(Ico.TV, SetTint.blue, "TVs", "${vm.devices.size}", onClick = manage); SetLine() }
        SetRow(Ico.BACKUP, SetTint.green, "Backup & restore") { open = "backup" }
        SetLine()
        SetRow(Ico.REFRESH, SetTint.orange, "Updates", when (vm.update) { is RemoteVM.Update.Ready, is RemoteVM.Update.Manual -> "Ready"; else -> if (s.autoUpdate) "Auto" else "Off" }) { open = "updates" }
        if (openPrivacy != null) { SetLine(); SetRow(Ico.SHIELD, SetTint.grey, "Privacy & data", onClick = openPrivacy) }
        SetLine()
        SetRow(Ico.ALERT, SetTint.grey, "Help & feedback") { open = "support" }
        SetLine()
        SetRow(Ico.INFO, SetTint.grey, "About", version) { open = "about" }
    }
    Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        LineIcon(Ico.SHIELD, SubInk, 17.dp)
        Text("No account · no ads or tracking", color = SubInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
    Spacer(Modifier.height(8.dp))

    when (open) {
        "profile" -> ProfileEditSheet(vm, accent) { open = null }
        "tv" -> SetSheet("TV controls", "How the phone works with the TV.", { open = null }) {
            SetCard {
                SetToggle(Ico.VOL_PLUS, SetTint.orange, "Volume keys", "Phone keys change TV volume", s.volumeKeys) { vm.update(s.copy(volumeKeys = it)) }
                SetLine()
                SetToggle(Ico.KEYBOARD, SetTint.blue, "Auto keyboard", "When the TV asks for text", s.autoKeyboard) { vm.update(s.copy(autoKeyboard = it)) }
                SetLine()
                SetToggle(Ico.ALERT, SetTint.green, "Notification remote", "Keys in the notification", s.notifRemote) { vm.update(s.copy(notifRemote = it)) }
                SetLine()
                SetToggle(Ico.BACK, SetTint.teal, "Close idle TV menus", "After 10 s idle", s.autoClose) { vm.update(s.copy(autoClose = it)) }
            }
        }
        "control" -> SetSheet("Control", "The middle of the remote.", { open = null }) {
            SetCard(Modifier.padding(top = 0.dp)) {
                Choice("Dial", "Arrows and OK; turn for volume", s.controlStyle == "dial", accent, Ico.DIAL) { vm.update(s.copy(controlStyle = "dial")) }
                SetLine()
                Choice("Trackpad", "Swipe to move, tap to select", s.controlStyle == "trackpad", accent, Ico.TRACKPAD) { vm.update(s.copy(controlStyle = "trackpad")) }
                SetLine()
                Choice("Touch", "Big swipe area, four keys", s.controlStyle == "touch", accent, Ico.TRACKPAD) { vm.update(s.copy(controlStyle = "touch")) }
            }
        }
        "apps" -> SetSheet("Quick access", "Where your apps sit.", { open = null }) {
            SetCard {
                Choice("Home page", "Swipe across the circles", s.appsIn != "more", accent, Ico.HOME) { vm.update(s.copy(appsIn = "home")) }
                SetLine()
                Choice("More sheet", "At the top of More", s.appsIn == "more", accent, Ico.MORE) { vm.update(s.copy(appsIn = "more")) }
            }
        }
        "feel" -> SetSheet("Touch & sound", "Feel and sound of presses.", { open = null }) {
            SetCard {
                SetToggle(Ico.TRACKPAD, SetTint.blue, "Haptics", null, s.haptics) { vm.update(s.copy(haptics = it)) }
                if (s.haptics) Box(Modifier.padding(horizontal = 16.dp)) { SegRow("Strength", HAPTIC_STRENGTHS, s.hapticStrength, last = true) { vm.update(s.copy(hapticStrength = it)) } }
                SetLine()
                SetToggle(Ico.SPEAKER, SetTint.red, "Sound", null, s.sound) { vm.update(s.copy(sound = it)) }
                if (s.sound) Box(Modifier.padding(horizontal = 16.dp)) { SegRow("Level", SOUND_LEVELS, s.soundLevel, last = true) { vm.update(s.copy(soundLevel = it)); vm.feel.fire(com.krishna.miniremote.Feel.PRESS) } }
            }
            SetCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Scrub step", color = T.Ink, fontSize = 16.sp)
                            Text("Distance per step when you swipe. Shorter is faster.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text("${s.scrubStepDp.roundToInt()} dp", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Fader(s.scrubStepDp, 24f..60f, accent, label = "Scrub distance", valueText = "${s.scrubStepDp.roundToInt()} dp", steps = 17) { vm.update(s.copy(scrubStepDp = it)) }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Fast", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Slow", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        "look" -> SetSheet("Appearance", "Theme, colour and depth.", { open = null }) {
            SetCard {
                SetToggle(Ico.LAYERS, SetTint.grey, "Depth", "Raised keys with soft shadows", s.depth) { vm.update(s.copy(depth = it)) }
            }
            SetHead("Theme")
            run {
                Row(Modifier.fillMaxWidth().selectableGroup().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palettes.all.forEach { p ->
                        val sel = s.theme == p.key
                        // A little remote in the theme's colours: screen, a key row and a dial.
                        Column(Modifier.weight(1f).selectable(sel, role = Role.RadioButton) { vm.update(s.copy(theme = p.key)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp))
                                    .then(if (p.key == "auto") Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                                        0f to Palettes.LIGHT.screen, 0.5f to Palettes.LIGHT.screen, 0.5f to Palettes.DARK.screen, 1f to Palettes.DARK.screen)) else Modifier.background(p.screen))
                                    .border(if (sel) 2.dp else 1.dp, if (sel) accent else p.line, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(p.high), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(p.highest))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.size(9.dp).clip(CircleShape).background(p.keyFill.takeIf { p.light }?.let { p.high } ?: p.high)) } }
                                    Box(Modifier.size(22.dp, 3.dp).clip(CircleShape).background(accent))
                                }
                            }
                            Text(p.label, color = if (sel) T.Ink else SubInk, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            SetHead("App icon")
            SetCard {
                var icon by remember { mutableStateOf(com.krishna.miniremote.AppIcon.current(ctx)) }
                Row(Modifier.fillMaxWidth().selectableGroup().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    com.krishna.miniremote.AppIcon.all.forEach { (key, label) ->
                        val sel = icon == key
                        Column(Modifier.selectable(sel, role = Role.RadioButton) {
                            if (!sel) { com.krishna.miniremote.AppIcon.set(ctx, key); icon = key; vm.say("App icon: $label") }
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(52.dp).then(if (sel) Modifier.border(2.dp, Color.White, CircleShape).padding(4.dp) else Modifier.padding(4.dp)).clip(CircleShape)) {
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.krishna.miniremote.AppIcon.bg(key)), null,
                                    Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.krishna.miniremote.AppIcon.fg(key)), null,
                                    Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            }
                            Text(label, color = if (sel) T.Ink else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            SetHead("Accent colour")
            SetCard {
                Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ACCENTS.forEach { (name, hex) ->
                        val sel = s.accentHex.equals(hex, true)
                        Box(Modifier.size(42.dp).selectable(sel, role = Role.RadioButton) { vm.update(s.copy(accentHex = hex)) }.semantics { contentDescription = name },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(30.dp).background(accentColor(hex), CircleShape))
                            if (sel) Box(Modifier.requiredSize(40.dp).border(2.dp, T.Ink, CircleShape))
                        }
                    }
                }
            }
        }
        "backup" -> BackupSheet(vm) { open = null }
        "updates" -> SetSheet("Updates", "New builds from GitHub.", { open = null }) {
            SetCard {
                SetToggle(Ico.REFRESH, SetTint.orange, "Auto updates", "Tell me when one is ready", s.autoUpdate) { vm.update(s.copy(autoUpdate = it)); if (it) vm.checkForUpdate() }
                SetLine()
                val u = vm.update
                SetRow(Ico.BACKUP, SetTint.blue,
                    when (u) { is RemoteVM.Update.Ready -> "Install update"; is RemoteVM.Update.Manual -> "Get update"; else -> "Check now" },
                    sub = when (u) {
                        RemoteVM.Update.Idle -> "Latest build on GitHub"
                        RemoteVM.Update.Checking -> "Checking…"
                        RemoteVM.Update.UpToDate -> "You're on the latest build"
                        is RemoteVM.Update.Downloading -> "Downloading ${u.info.name}… ${(u.progress * 100).toInt()}%"
                        is RemoteVM.Update.Ready -> "Version ${u.info.name} is downloaded"
                        is RemoteVM.Update.Failed -> u.message
                        is RemoteVM.Update.Manual -> "Version ${u.info.name} needs a reinstall"
                    },
                ) {
                    when (u) {
                        is RemoteVM.Update.Ready -> vm.installUpdate()
                        is RemoteVM.Update.Manual -> vm.openReleasePage()
                        is RemoteVM.Update.Checking, is RemoteVM.Update.Downloading -> {}
                        else -> vm.checkForUpdate(manual = true)
                    }
                }
            }
        }
        "support" -> SetSheet("Help & feedback", "Straight to the developer.", { open = null }) {
            SetCard {
                SetRow(Ico.STAR, SetTint.grey, "Request a feature") { vm.contact("Feature request") }
                SetLine()
                SetRow(Ico.ALERT, SetTint.grey, "Report a bug") { vm.contact("Bug report") }
                SetLine()
                SetRow(Ico.USER, SetTint.grey, "Ask a question") { vm.contact("Question") }
            }
            SetCard {
                SetRow(Ico.TRACKPAD, SetTint.grey, "Take the tour") {
                    open = null; vm.update(s.copy(toured = false))
                }
                SetLine()
                SetRow(Ico.INFO, SetTint.grey, "Show the welcome again") {
                    open = null; vm.update(s.copy(onboarded = false, toured = false))
                }
            }
            Text("Your app version, phone and TV type are added so it's easier to help. Nothing else is sent.",
                color = SubInk, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        }
        "about" -> SetSheet("Mini Remote", "Version $version", { open = null }) {
            Text("No account, no ads, no analytics or tracking.", color = T.Ink, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 2.dp))
            Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names and logos shown are trademarks of their owners. " +
                "Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' published or documented remote-control interfaces on your own network.",
                color = Muted, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp))
            if (openLicenses != null) SetCard { SetRow(Ico.LIST, SetTint.grey, "Licences") { open = null; openLicenses() } }
        }
    }
}

/** Back up the whole setup to a file, or load one. */
@Composable
private fun BackupSheet(vm: RemoteVM, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s = vm.settings
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportProfile(com.krishna.miniremote.AppIcon.current(ctx)).toByteArray()) }
            vm.say("Backup saved")
        }.onFailure { vm.say("Couldn't save the backup") }
    }
    val load = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().take(400_000).toByteArray().decodeToString() } }.getOrNull()
            if (text == null) vm.say("Couldn't read that file")
            else vm.importProfile(text)?.let { icon -> if (icon != com.krishna.miniremote.AppIcon.current(ctx)) com.krishna.miniremote.AppIcon.set(ctx, icon) }
        }
    }
    SetSheet("Backup & restore", "Everything in one file.", onClose) {
        SetCard {
            SetRow(Ico.BACKUP, SetTint.green, "Back up", sub = "Save or share a file") {
                save.launch("Mini Remote${if (s.profileName.isNotBlank()) " - " + s.profileName else ""}.json")
            }
            SetLine()
            SetRow(Ico.RESTORE, SetTint.blue, "Restore", sub = "From a backup file") { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
    }
}
