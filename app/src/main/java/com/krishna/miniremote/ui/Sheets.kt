package com.krishna.miniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.ACCENTS
import com.krishna.miniremote.Brand
import com.krishna.miniremote.Device
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteVM

private val Sheet = Color(0xFF1C1C1F)

@Composable
private fun SectionTitle(t: String) =
    Text(t.uppercase(), color = T.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = T.Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF3A3A3D), uncheckedThumbColor = Color(0xFF9A9AA0), uncheckedBorderColor = Color.Transparent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Sheet, contentColor = T.Ink) {
        Column(Modifier.navigationBarsPadding()) {
            // sticky header
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TVs & settings", color = T.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done", color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
            HorizontalDivider(color = Color.White.copy(0.08f))
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), modifier = Modifier.weight(1f, fill = false)) {
                item { SectionTitle("Your TVs") }
                if (vm.devices.isEmpty()) item { Text("No TVs yet. Scan your Wi-Fi or add one by IP.", color = T.Dim, fontSize = 14.sp) }
                items(vm.devices, key = { it.id }) { d -> DeviceRow(vm, d, accent) }
                item {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.scan() }, enabled = !vm.scanning, shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E31), contentColor = T.Ink),
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(if (vm.scanning) "Scanning your Wi-Fi..." else "Scan network")
                    }
                    vm.scanNote?.let { Text(it, color = T.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
                item { AddManual(vm, accent) }

                item { SectionTitle("Feel") }
                item { ToggleRow("Haptics", vm.settings.haptics, accent) { vm.update(vm.settings.copy(haptics = it)) } }
                item { ToggleRow("Click sounds", vm.settings.sound, accent) { vm.update(vm.settings.copy(sound = it)) } }
                item {
                    Text("Scrub distance per step", color = T.Ink, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    Fader(vm.settings.scrubStepDp, 24f..64f, accent) { vm.update(vm.settings.copy(scrubStepDp = it)) }
                    Text("Shorter = faster scrolling", color = T.Dim, fontSize = 12.sp)
                }
                item { SectionTitle("Accent") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ACCENTS.forEach { (name, hex) ->
                            val c = accentColor(hex); val sel = vm.settings.accentHex.equals(hex, true)
                            Box(Modifier.size(38.dp).background(c, CircleShape)
                                .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.White.copy(0.2f), CircleShape)
                                .clickable { vm.update(vm.settings.copy(accentHex = hex)) })
                        }
                    }
                }
                item { SectionTitle("Screen") }
                item { ToggleRow("Keep screen on", vm.settings.keepAwake, accent) { vm.update(vm.settings.copy(keepAwake = it)) } }
                item {
                    SectionTitle("About")
                    Text("Mini Remote 0.1. Controls TVs over your local Wi-Fi only. Nothing leaves your network.", color = T.Dim, fontSize = 13.sp)
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(vm: RemoteVM, d: Device, accent: Color) {
    val isActive = vm.activeId == d.id
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(Color(0xFF2A2A2D), RoundedCornerShape(14.dp))
            .clickable { vm.select(d) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(if (isActive) statusColor(vm.link, accent) else Color(0xFF55555B), CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.name, color = T.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${d.brand.label}  ${d.host}" + if (isActive) "  ${linkWord(vm.link)}" else "", color = T.Dim, fontSize = 12.sp)
        }
        TextButton(onClick = { vm.remove(d) }) { Text("Remove", color = T.Danger, fontSize = 13.sp) }
    }
}

fun statusColor(l: Link, accent: Color): Color = when (l) {
    Link.READY -> Color(0xFF30D158)
    Link.CONNECTING -> Color(0xFFFFD60A)
    Link.ERROR -> T.Danger
    Link.IDLE -> Color(0xFF55555B)
}
fun linkWord(l: Link) = when (l) { Link.READY -> "Connected"; Link.CONNECTING -> "Connecting..."; Link.ERROR -> "Can't reach"; Link.IDLE -> "" }

@Composable
private fun AddManual(vm: RemoteVM, accent: Color) {
    var ip by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.ROKU) }
    var err by remember { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T.Ink, unfocusedTextColor = T.Ink,
        focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF3A3A3D), cursorColor = accent,
        focusedLabelColor = accent, unfocusedLabelColor = T.Dim)
    Column(Modifier.padding(top = 18.dp)) {
        Text("Add by IP", color = T.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(ip, { ip = it; err = null }, label = { Text("TV IP address") }, singleLine = true, colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = err != null,
            modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = T.Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Brand.ROKU, Brand.SAMSUNG, Brand.LG).forEach { b ->
                FilterChip(selected = brand == b, onClick = { brand = b }, label = { Text(b.label.substringBefore(' ')) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(0.25f), selectedLabelColor = T.Ink, labelColor = T.Dim))
            }
        }
        Button(onClick = { err = vm.addManual(name, ip, brand) }, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Add and connect", fontWeight = FontWeight.SemiBold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(vm: RemoteVM, accent: Color, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Sheet, contentColor = T.Ink) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Type on TV", color = T.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Open a search box on the TV first, then send.", color = T.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T.Ink, unfocusedTextColor = T.Ink,
                    focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF3A3A3D), cursorColor = accent))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.backspace() }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E31), contentColor = T.Ink),
                    modifier = Modifier.height(48.dp)) { Text("Delete") }
                Button(onClick = { if (text.isNotEmpty()) { vm.typeText(text); text = "" } }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(48.dp)) { Text("Send to TV", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
