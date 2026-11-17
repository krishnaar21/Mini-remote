package com.krishna.miniremote.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.miniremote.Brand
import com.krishna.miniremote.Catalog
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteVM
import com.krishna.miniremote.Shortcut
import com.krishna.miniremote.ShortcutKind
import com.krishna.miniremote.net.HostValidator
import com.krishna.miniremote.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Tile colours for something with no brand colours of its own (installed apps, added apps). */
private fun tint(name: String): Color {
    val palette = listOf(0xFF2B3A55, 0xFF3B2B55, 0xFF2B4A3F, 0xFF4A3A2B, 0xFF4A2B3A, 0xFF2B4450, 0xFF3A3A3F)
    return Color(palette[(name.lowercase().hashCode() and 0x7FFFFFFF) % palette.size])
}

private val ChannelBg = Color(0xFF263042)

/** Apps, inputs and channels: quick access row on top, then the TV's apps, popular apps and your channels. */
@Composable
fun ChannelsScreen(vm: RemoteVM, accent: Color, openDevices: () -> Unit) {
    var adding by remember { mutableStateOf<ShortcutKind?>(null) }
    val d = vm.active
    val ready = d != null && vm.link == Link.READY
    LaunchedEffect(vm.activeId, vm.link) { if (vm.link == Link.READY) vm.refreshApps() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = NAV_H + 40.dp),
    ) {
        PageTitle("Channels") {
            RoundIcon(Ico.REFRESH, "Reload the TV's apps") { vm.refreshApps(force = true) }
            RoundIcon(Ico.PLUS, "Add a channel or app") { adding = ShortcutKind.CHANNEL }
        }
        Text(
            when {
                d == null -> "Add a TV to open apps and channels"
                ready -> "On ${d.name}"
                else -> "${d.name} · ${linkWord(vm.link).ifEmpty { "Not connected" }}"
            },
            color = if (ready) Color(0xFF4DDC88) else Muted, fontSize = 13.5.sp,
            modifier = Modifier.then(if (!ready) Modifier.clickable(onClickLabel = "choose a TV", onClick = openDevices) else Modifier),
        )

        // Quick access: what you pinned, in your order.
        SectionTitle("Quick access")
        val pinned = vm.shortcuts.filter { it.pinned }
        if (pinned.isEmpty()) {
            Hint("Long-press any app or channel below and choose Add to quick access. It'll sit here, one tap away.", top = 6)
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pinned.forEachIndexed { i, s ->
                    QuickTile(s, accent, onClick = { vm.launch(s) }, menu = buildList {
                        if (i > 0) add("Move left" to { vm.movePinned(s, -1) })
                        if (i < pinned.lastIndex) add("Move right" to { vm.movePinned(s, 1) })
                        add("Remove from quick access" to { vm.setPinned(s, false) })
                        if (s.added) add("Delete" to { vm.removeShortcut(s) })
                    })
                }
            }
        }

        // What the TV itself reports (Roku, Samsung, LG).
        if (vm.tvApps.isNotEmpty() || vm.appsLoading) {
            SectionTitle("On your TV")
            if (vm.tvApps.isEmpty()) Loading(accent, "Loading apps from the TV…")
            TileGrid(vm.tvApps) { app ->
                val pin = vm.pinnedTvApp(app)
                Tile(app.name, tint(app.name), Color.White, icon = app.icon, pinned = pin != null, onClick = { vm.launchApp(app.name, app.id) },
                    menu = listOf(if (pin == null) "Add to quick access" to { vm.pinTvApp(app) } else "Remove from quick access" to { vm.setPinned(pin, false) }))
            }
        }

        // Popular apps this TV has an id for (all of them when no TV is chosen).
        val brand = d?.brand
        val popular = Catalog.apps.filter { brand == null || it.ids.containsKey(brand) }
        SectionTitle(if (vm.tvApps.isEmpty()) "Apps" else "Popular apps")
        TileGrid(popular) { c ->
            val pin = vm.pinnedCatalog(c)
            Tile(c.name, Color(c.bg), Color(c.ink), pinned = pin != null, onClick = { vm.launchCatalog(c) },
                menu = listOf(if (pin == null) "Add to quick access" to { vm.pinCatalog(c) } else "Remove from quick access" to { vm.setPinned(pin, false) }))
        }

        // Apps added by id.
        val added = vm.shortcuts.filter { it.kind == ShortcutKind.APP && it.added }
        if (added.isNotEmpty()) {
            SectionTitle("Added apps")
            TileGrid(added) { s ->
                Tile(s.name, tint(s.name), Color.White, badge = s.brand?.label, pinned = s.pinned, onClick = { vm.launch(s) },
                    menu = listOf(
                        if (s.pinned) "Remove from quick access" to { vm.setPinned(s, false) } else "Add to quick access" to { vm.setPinned(s, true) },
                        "Delete" to { vm.removeShortcut(s) },
                    ))
            }
        }

        SectionTitle("TV channels")
        val channels = vm.shortcuts.filter { it.kind == ShortcutKind.CHANNEL }
        if (channels.isEmpty()) Hint("Add the channels you watch, like 101 · Star Sports. Tapping one tunes the TV to it.", top = 6)
        else TileGrid(channels) { s ->
            Tile(s.name, ChannelBg, Color.White, badge = s.value, pinned = s.pinned, onClick = { vm.launch(s) },
                menu = listOf(
                    if (s.pinned) "Remove from quick access" to { vm.setPinned(s, false) } else "Add to quick access" to { vm.setPinned(s, true) },
                    "Delete" to { vm.removeShortcut(s) },
                ))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Btn("Add a channel", kind = 1) { adding = ShortcutKind.CHANNEL } }
            Box(Modifier.weight(1f)) { Btn("Add an app", kind = 1) { adding = ShortcutKind.APP } }
        }
        if (brand == Brand.ANDROID_TV) Hint("Android / Google TV can't list its apps over Wi-Fi. Popular apps open by package name; add others with Add an app.")
    }

    adding?.let { k -> AddShortcutSheet(vm, accent, k) { adding = null } }
}

@Composable
private fun Loading(accent: Color, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = accent, trackColor = Color(0xFF333333), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

/** Two tiles per row. */
@Composable
private fun <T> TileGrid(items: List<T>, cols: Int = 2, tile: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { tile(it) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Soft-touch tile in the app's colour, with a name (or the TV's icon). Long-press for options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    title: String,
    bg: Color,
    ink: Color,
    icon: String? = null,
    badge: String? = null,
    pinned: Boolean = false,
    height: Dp = 92.dp,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    var open by remember { mutableStateOf(false) }
    val face = Face(listOf(mix(bg, 0.9f, Color.White), mix(bg, 0.85f, Color.Black)), CARD.shadows)
    Box(
        Modifier.fillMaxWidth().height(height).softFace(face, corner = 18.dp, dpUnits = true).clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClickLabel = "open", onLongClickLabel = "options", role = Role.Button, onClick = onClick, onLongClick = { open = true })
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        val name: @Composable () -> Unit = {
            Text(title, color = ink, fontSize = if (title.length > 14) 15.sp else 19.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (icon != null) TvIcon(icon, Modifier.fillMaxSize().padding(10.dp), name) else name()
        badge?.let {
            Text(it, color = Color.White, fontSize = 11.sp, fontFamily = Mono, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (pinned) LineIcon(Ico.STAR, ink.copy(0.85f), 14.dp, Modifier.align(Alignment.TopEnd).padding(9.dp))
        OptionsMenu(open, { open = false }, menu)
    }
}

/** Quick access item: a rounded square with the app's colour or the channel number, name below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(s: Shortcut, accent: Color, onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    val cat = if (s.kind == ShortcutKind.APP && s.brand == null) Catalog.byKey(s.value) else null
    val bg = when {
        s.kind == ShortcutKind.CHANNEL -> ChannelBg
        cat != null -> Color(cat.bg)
        else -> tint(s.name)
    }
    val ink = cat?.let { Color(it.ink) } ?: Color.White
    Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(70.dp).softFace(Face(listOf(mix(bg, 0.9f, Color.White), mix(bg, 0.85f, Color.Black)), CARD.shadows), corner = 20.dp, dpUnits = true)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClickLabel = "open ${s.name}", onLongClickLabel = "options", role = Role.Button, onClick = onClick, onLongClick = { open = true }),
            contentAlignment = Alignment.Center,
        ) {
            val short: @Composable () -> Unit = {
                Text(
                    if (s.kind == ShortcutKind.CHANNEL) s.value else s.name.take(2).uppercase(),
                    color = ink, fontSize = if (s.kind == ShortcutKind.CHANNEL && s.value.length > 3) 15.sp else 20.sp,
                    fontWeight = FontWeight.Bold, fontFamily = if (s.kind == ShortcutKind.CHANNEL) Mono else Inter, maxLines = 1,
                )
            }
            if (s.icon != null) TvIcon(s.icon, Modifier.fillMaxSize().padding(8.dp), short) else short()
            OptionsMenu(open, { open = false }, menu)
        }
        Text(s.name, color = T.Ink, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun OptionsMenu(open: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    DropdownMenu(open, onDismiss, Modifier.background(Color(0xFF26262A))) {
        items.forEach { (label, act) ->
            DropdownMenuItem(text = { Text(label, color = if (label == "Delete") Color(0xFFFF8A8A) else T.Ink) }, onClick = { onDismiss(); act() })
        }
    }
}

/* ------------------------------------------------------------- TV icons */

private val icons = LruCache<String, ImageBitmap>(80)

/** An icon served by the TV itself (Roku, LG). Only loads from home-network addresses. */
@Composable
private fun TvIcon(url: String, modifier: Modifier, fallback: @Composable () -> Unit) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(icons.get(url)) }
    LaunchedEffect(url) {
        if (img != null) return@LaunchedEffect
        img = withContext(Dispatchers.IO) {
            runCatching {
                val host = android.net.Uri.parse(url).host.orEmpty()
                if (!HostValidator.isPrivateIPv4(host)) return@runCatching null
                Net.plain.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = if (r.isSuccessful) r.body?.bytes() else null
                    if (bytes == null || bytes.size > 2_000_000) null
                    else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { icons.put(url, it) }
    }
    val i = img
    if (i != null) Image(i, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
    else fallback()
}

/* ------------------------------------------------------------- add sheet */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShortcutSheet(vm: RemoteVM, accent: Color, start: ShortcutKind, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(start) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf(true) }
    val brand = vm.active?.brand
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, containerColor = T.Bg, contentColor = T.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), scrimColor = Color.Black.copy(0.55f),
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add to Channels", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            SegRow("Type", listOf(ShortcutKind.CHANNEL to "TV channel", ShortcutKind.APP to "App"), kind) { kind = it; value = "" }
            Spacer(Modifier.height(16.dp))
            if (kind == ShortcutKind.CHANNEL) {
                Field("Name", name, { name = it.take(30) }, placeholder = "Star Sports")
                Field("Channel number", value, { v -> value = v.filter { it.isDigit() || it == '.' || it == '-' }.take(8) },
                    placeholder = "101", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            } else {
                Field("Name", name, { name = it.take(30) }, placeholder = "JioHotstar")
                Field("App ID on ${brand?.label ?: "your TV"}", value, { value = it.trim().take(200) }, placeholder = when (brand) {
                    Brand.ROKU -> "12"; Brand.SAMSUNG -> "3201907018807"; Brand.LG -> "netflix"; Brand.ANDROID_TV -> "com.netflix.ninja"; null -> ""
                })
                Hint(when (brand) {
                    Brand.ANDROID_TV -> "Use the app's package name. You'll find it in the app's Play Store web address after id=."
                    Brand.ROKU -> "Roku channel ids are numbers. Installed channels already show under On your TV."
                    Brand.SAMSUNG, Brand.LG -> "Installed apps already show under On your TV, so you only need this for ones the TV doesn't list."
                    null -> "Choose a TV first: app ids are different on every kind of TV."
                }, top = 0)
            }
            ToggleRow("Add to quick access", "Show it in the row at the top", pin) { pin = it }
            Btn("Save", enabled = name.isNotBlank() && value.isNotBlank() && (kind == ShortcutKind.CHANNEL || brand != null)) {
                vm.addShortcut(kind, name, value, pin); onClose()
            }
        }
    }
}
