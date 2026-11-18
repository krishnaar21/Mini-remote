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
internal fun SheetTitle(title: String, sub: String? = null, modifier: Modifier = Modifier, center: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp).semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(title, color = T.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        sub?.let { Text(it, color = SubInk, fontSize = 13.sp, lineHeight = 16.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.padding(top = 1.dp)) }
    }
}

/** A sheet opened from a settings row: title on the left, its rows on a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetSheet(title: String, sub: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val st = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose, sheetState = st, containerColor = SheetBg, contentColor = T.Ink,
        shape = SheetShape, scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle(title, sub, Modifier.padding(top = 4.dp, bottom = 14.dp))
            content()
        }
    }
}

/** Secondary text: quieter than the titles. */
internal val SubInk = Color(0xFF8E8E93)

/** A settings icon on its own rounded square, in the row's colour. */
@Composable
internal fun IconTile(ico: Ico, tint: Color) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint), contentAlignment = Alignment.Center) { LineIcon(ico, Color.White, 18.dp) }
}

/** A rounded group of rows. */
@Composable
internal fun SetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(M.Container), content = content)
}

/** The thin line between rows, starting after the icon. */
@Composable
internal fun SetLine() = Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(Color.White.copy(alpha = 0.07f)))

/** One row: icon, title (and a smaller line), what's set, and a chevron when it opens something. */
@Composable
internal fun SetRow(ico: Ico, tint: Color, title: String, value: String? = null, sub: String? = null, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(ico, tint)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = T.Ink, fontSize = 16.sp, lineHeight = 19.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 12.5.sp, lineHeight = 15.sp) }
        }
        value?.let { Text(it, color = Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 6.dp)) }
        if (chevron) Chevron()
    }
}

/** An on / off row with an icon: the whole row is the switch. */
@Composable
internal fun SetToggle(ico: Ico, tint: Color, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(ico, tint)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
            Text(title, color = T.Ink, fontSize = 16.sp, lineHeight = 19.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 12.5.sp, lineHeight = 15.sp) }
        }
        Toggle(checked)
    }
}

/** A choice in a sheet: the option, and a tick on the one that's set. */
@Composable
private fun Choice(label: String, sub: String?, sel: Boolean, accent: Color, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).selectable(sel, role = Role.RadioButton, onClick = onPick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = T.Ink, fontSize = 16.sp, lineHeight = 19.sp)
            sub?.let { Text(it, color = SubInk, fontSize = 12.5.sp, lineHeight = 15.sp) }
        }
        if (sel) LineIcon(Ico.CHECK, accent, 20.dp)
    }
}

private object SetTint {
    val blue = Color(0xFF4C8DFF); val green = Color(0xFF34C759); val orange = Color(0xFFFF9F0A); val purple = Color(0xFFAF7BFF)
    val pink = Color(0xFFFF5C8A); val teal = Color(0xFF30C8D6); val grey = Color(0xFFA1A1A6); val red = Color(0xFFFF6B5E); val yellow = Color(0xFFFFD60A)
}

@Composable
internal fun SettingsPage(vm: RemoteVM, accent: Color, manage: (() -> Unit)?, openLicenses: (() -> Unit)? = null, openPrivacy: (() -> Unit)? = null, openGestures: (() -> Unit)? = null) {
    val s = vm.settings
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev" }
    val iconName = com.krishna.miniremote.AppIcon.all.firstOrNull { it.first == com.krishna.miniremote.AppIcon.current(ctx) }?.second ?: "Dial"
    val accentName = ACCENTS.firstOrNull { it.second.equals(s.accentHex, true) }?.first ?: "Custom"

    // Profile.
    val apps = vm.shortcuts.count { it.pinned }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(M.Container)
            .clickable(role = Role.Button, onClickLabel = "edit profile") { open = "profile" }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(initials(s.profileName), accent, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(s.profileName.ifBlank { "Add your name" }, color = if (s.profileName.isBlank()) Muted else T.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(s.profileEmail.ifBlank { "${vm.devices.size} TV${if (vm.devices.size == 1) "" else "s"} · $apps app${if (apps == 1) "" else "s"}" },
                color = Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Chevron()
    }

    // The remote.
    SetCard {
        SetRow(if (s.controlStyle == "trackpad") Ico.TRACKPAD else Ico.DIAL, SetTint.blue, "Control", if (s.controlStyle == "trackpad") "Trackpad" else "Dial") { open = "control" }
        SetLine()
        SetRow(Ico.GRID, SetTint.purple, "Quick access", if (s.appsIn == "more") "More sheet" else "Home page") { open = "apps" }
        if (openGestures != null) { SetLine(); SetRow(Ico.TRACKPAD, SetTint.teal, "Gestures", onClick = openGestures) }
    }
    SetCard {
        SetRow(Ico.SPEAKER, SetTint.red, "Touch & sound", listOf(if (s.haptics) "Haptics" else null, if (s.sound) "Sound" else null).filterNotNull().joinToString(", ").ifEmpty { "Off" }) { open = "feel" }
        SetLine()
        SetToggle(Ico.VOL_PLUS, SetTint.orange, "Volume keys", "Phone keys change TV volume", s.volumeKeys) { vm.update(s.copy(volumeKeys = it)) }
        SetLine()
        SetToggle(Ico.KEYBOARD, SetTint.blue, "Auto keyboard", "When the TV asks for text", s.autoKeyboard) { vm.update(s.copy(autoKeyboard = it)) }
        SetLine()
        SetToggle(Ico.ALERT, SetTint.green, "Notification remote", "Volume and play in the background", s.notifRemote) { vm.update(s.copy(notifRemote = it)) }
        SetLine()
        SetToggle(Ico.BACK, SetTint.teal, "Close idle TV menus", "Back after 30 s without use", s.autoClose) { vm.update(s.copy(autoClose = it)) }
    }
    SetCard {
        SetRow(Ico.DISPLAY, SetTint.pink, "Appearance", "$iconName · $accentName") { open = "look" }
        SetLine()
        SetToggle(Ico.STAR, SetTint.yellow, "App logos", "Download logos", s.logos) { vm.update(s.copy(logos = it)) }
        SetLine()
        SetToggle(Ico.SLEEP, SetTint.purple, "Stay awake", "Screen stays on while open", s.keepAwake) { vm.update(s.copy(keepAwake = it)) }
    }
    SetCard {
        if (manage != null) { SetRow(Ico.TV, SetTint.blue, "TVs", "${vm.devices.size}", onClick = manage); SetLine() }
        SetRow(Ico.BACKUP, SetTint.green, "Backup & restore") { open = "backup" }
        SetLine()
        SetRow(Ico.REFRESH, SetTint.orange, "Updates", when (vm.update) { is RemoteVM.Update.Ready, is RemoteVM.Update.Manual -> "Ready"; else -> if (s.autoUpdate) "Auto" else "Off" }) { open = "updates" }
        if (openPrivacy != null) { SetLine(); SetRow(Ico.USER, SetTint.grey, "Privacy & data", onClick = openPrivacy) }
        SetLine()
        SetRow(Ico.INFO, SetTint.grey, "About", version) { open = "about" }
    }
    Spacer(Modifier.height(8.dp))

    when (open) {
        "profile" -> ProfileEditSheet(vm, accent) { open = null }
        "control" -> SetSheet("Control", "What the middle of the remote is.", { open = null }) {
            SetCard(Modifier.padding(top = 0.dp)) {
                Choice("Dial", "Ring with arrows, OK in the middle; turn the ring for volume", s.controlStyle == "dial", accent) { vm.update(s.copy(controlStyle = "dial")) }
                SetLine()
                Choice("Trackpad", "Swipe to move, tap to select", s.controlStyle == "trackpad", accent) { vm.update(s.copy(controlStyle = "trackpad")) }
            }
        }
        "apps" -> SetSheet("Quick access", "Where your apps sit.", { open = null }) {
            SetCard {
                Choice("Home page", "A third page of circles: swipe across", s.appsIn != "more", accent) { vm.update(s.copy(appsIn = "home")) }
                SetLine()
                Choice("More sheet", "At the top of More", s.appsIn == "more", accent) { vm.update(s.copy(appsIn = "more")) }
            }
        }
        "feel" -> SetSheet("Touch & sound", "How presses feel and sound.", { open = null }) {
            SetCard {
                SetToggle(Ico.TRACKPAD, SetTint.blue, "Haptics", "On every press", s.haptics) { vm.update(s.copy(haptics = it)) }
                if (s.haptics) Box(Modifier.padding(horizontal = 16.dp)) { SegRow("Strength", HAPTIC_STRENGTHS, s.hapticStrength, last = true) { vm.update(s.copy(hapticStrength = it)) } }
                SetLine()
                SetToggle(Ico.SPEAKER, SetTint.red, "Sound", "Click on press", s.sound) { vm.update(s.copy(sound = it)) }
                if (s.sound) Box(Modifier.padding(horizontal = 16.dp)) { SegRow("Level", SOUND_LEVELS, s.soundLevel, last = true) { vm.update(s.copy(soundLevel = it)); vm.feel.fire(com.krishna.miniremote.Feel.PRESS) } }
            }
            SetCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Scrub step", color = T.Ink, fontSize = 16.sp)
                            Text("Distance per step when you swipe. Shorter is faster.", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
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
        "look" -> SetSheet("Appearance", "The app's icon and colour.", { open = null }) {
            SetCard {
                Text("App icon", color = T.Ink, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
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
            SetCard {
                Text("Accent colour", color = T.Ink, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
                Row(Modifier.fillMaxWidth().selectableGroup().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ACCENTS.forEach { (name, hex) ->
                        val sel = s.accentHex.equals(hex, true)
                        Box(Modifier.size(42.dp).selectable(sel, role = Role.RadioButton) { vm.update(s.copy(accentHex = hex)) }.semantics { contentDescription = name },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(32.dp).background(accentColor(hex), CircleShape).border(2.dp, Color.Black.copy(0.25f), CircleShape))
                            if (sel) Box(Modifier.requiredSize(42.dp).border(2.dp, Color.White, CircleShape))
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
        "about" -> SetSheet("Mini Remote", "Version $version", { open = null }) {
            Text("No account, no ads, no analytics or tracking.", color = T.Ink, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 2.dp))
            Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names and logos shown are trademarks of their owners. " +
                "Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' published or documented remote-control interfaces on your own network.",
                color = Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp))
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
    SetSheet("Backup & restore", "Settings, quick access and app icon, in one file. TVs pair again on a new phone.", onClose) {
        SetCard {
            SetRow(Ico.BACKUP, SetTint.green, "Back up", sub = "Save a file to your phone or Drive") {
                save.launch("Mini Remote${if (s.profileName.isNotBlank()) " - " + s.profileName else ""}.json")
            }
            SetLine()
            SetRow(Ico.RESTORE, SetTint.blue, "Restore", sub = "Load a backup file") { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
    }
}
