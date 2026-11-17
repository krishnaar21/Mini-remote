package com.krishna.miniremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteVM
import com.krishna.miniremote.ShortcutKind

/** Settings as a full page (same controls the sheet used to hold). */
@Composable
fun SettingsScreen(vm: RemoteVM, accent: Color, manage: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = NAV_H + 40.dp),
    ) {
        PageTitle("Settings")
        SettingsPage(vm, accent, manage)
    }
}

/** Who's using the remote, their TVs, and the app's version. */
@Composable
fun ProfileScreen(vm: RemoteVM, accent: Color, open: (Page) -> Unit) {
    val s = vm.settings
    var name by remember(s.profileName) { mutableStateOf(s.profileName) }
    fun save() { vm.update(s.copy(profileName = name.trim())); vm.say("Saved") }
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = NAV_H + 40.dp),
    ) {
        PageTitle("Profile")
        Column(
            Modifier.fillMaxWidth().softFace(CARD, corner = 24.dp, dpUnits = true).padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(initials(s.profileName), accent, 84.dp)
            Text(s.profileName.ifBlank { "Add your name" }, color = T.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp))
            val d = vm.active
            Text(
                when {
                    d == null -> "No TV chosen"
                    vm.link == Link.READY -> "Controlling ${d.name}"
                    else -> "${d.name} · ${linkWord(vm.link).ifEmpty { "Not connected" }}"
                },
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("${vm.devices.size}", if (vm.devices.size == 1) "TV" else "TVs")
                Stat("${vm.shortcuts.count { it.pinned }}", "Quick access")
                Stat("${vm.shortcuts.count { it.kind == ShortcutKind.CHANNEL }}", "Channels")
            }
        }

        SectionTitle("Your name")
        Field(
            "Home greets you with it", name, { name = it.take(24) }, placeholder = "Krishna",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Btn("Save name", kind = 1, enabled = name.trim() != s.profileName) { save() }

        SectionTitle("Your TVs")
        if (vm.devices.isEmpty()) Hint("No TVs yet. Add one to start.", top = 6)
        Column(Modifier.padding(top = 10.dp)) {
            vm.devices.forEach { d ->
                val on = vm.activeId == d.id && vm.link == Link.READY
                CardRow(d.name, statusLine(vm, d), dot = if (on) OkGreen else Color(0xFF555555),
                    onClick = { open(Page.DeviceInfo(d.id)) }, clickLabel = "open") { Chevron() }
            }
        }
        Btn("Add a TV") { open(if (vm.localSeen) Page.Scan else Page.Local) }

        SectionTitle("About")
        Hint("Mini Remote talks only to TVs on your home network. No account and no tracking; pairing keys stay on this phone.", top = 4)
        Text("Version $version", color = Muted, fontSize = 12.5.sp, fontFamily = Mono, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(value, color = T.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
