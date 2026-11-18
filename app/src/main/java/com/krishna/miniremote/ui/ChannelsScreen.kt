package com.krishna.miniremote.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
import com.krishna.miniremote.Logos
import androidx.compose.ui.graphics.toArgb
import com.krishna.miniremote.Link
import com.krishna.miniremote.RemoteVM
import com.krishna.miniremote.Shortcut
import com.krishna.miniremote.ShortcutKind
import com.krishna.miniremote.net.HostValidator
import com.krishna.miniremote.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** Tile colours for something with no brand colours of its own (installed apps, added apps). */
private fun tint(name: String): Color {
    val palette = listOf(0xFF2B3A55, 0xFF3B2B55, 0xFF2B4A3F, 0xFF4A3A2B, 0xFF4A2B3A, 0xFF2B4450, 0xFF3A3A3F)
    return Color(palette[(name.lowercase().hashCode() and 0x7FFFFFFF) % palette.size])
}

private val ChannelBg = Color(0xFF263042)

/** Everything a tile and its details sheet need, whatever the app came from. */
private class AppEntry(
    val key: String,
    val name: String,
    val bg: Color,
    val ink: Color,
    val icon: String? = null,
    val badge: String? = null,
    /** Simple Icons slug for its logo, if it may have one. */
    val logo: String? = null,
    /** The app's website, for its icon when Simple Icons has none. */
    val site: String? = null,
    /** Android package name, for the app's real icon from Google Play. */
    val pkg: String? = null,
    val vendor: String? = null,
    val version: String? = null,
    /** true / false when the TV lists its apps; null when it can't tell. */
    val installed: Boolean? = null,
    val pinned: Boolean,
    /** Its quick access entry, when it has one. */
    val pinnedAs: Shortcut? = null,
    /** Id for the TV's store page (Roku, Android TV); null where install / remove isn't possible from here. */
    val storeId: String? = null,
    val open: () -> Unit,
    val pin: () -> Unit,
    val unpin: () -> Unit,
    val delete: (() -> Unit)? = null,
)

/**
 * Apps and channels. Tap an app for its details (open, quick access, install / remove);
 * long-press it (or tap, when opened to add apps) to put it in quick access; again for its options.
 */
@Composable
fun ChannelsScreen(vm: RemoteVM, accent: Color, openDevices: () -> Unit, picking: Boolean = false, onFull: () -> Unit = {}) {
    var adding by remember { mutableStateOf<ShortcutKind?>(null) }
    var details by remember { mutableStateOf<AppEntry?>(null) }
    /** Index of the quick access item whose actions sheet is open. */
    var quickMenu by remember { mutableStateOf<Int?>(null) }
    val d = vm.active
    val ready = d != null && vm.link == Link.READY
    LaunchedEffect(vm.activeId, vm.link) { if (vm.link == Link.READY) vm.refreshApps() }
    /** No TV ready: show the TV sheet (connect / pair) instead. */
    fun needTv(): Boolean = if (!ready) { openDevices(); true } else false

    // Drag to quick access: positions are in root coordinates.
    var drag by remember { mutableStateOf<AppEntry?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var zone by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val over = drag != null && zone.contains(dragPos)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    /** Quick access options for an app already in it (open, move, remove). */
    fun options(e: AppEntry) { e.pinnedAs?.let { s -> quickMenu = vm.shortcuts.filter { it.pinned }.indexOfFirst { it.id == s.id }.takeIf { it >= 0 } } }
    /** Adding to quick access: the first tap adds the app, a second tap opens its options. */
    fun pick(e: AppEntry) {
        if (e.pinned) { options(e); return }
        e.pin(); vm.feel.fire(com.krishna.miniremote.Feel.SUCCESS)
        // Adding mode: once every place is filled, go back to the remote.
        if (picking && vm.shortcuts.count { it.pinned } >= vm.maxPinned) scope.launch { kotlinx.coroutines.delay(450); onFull() }
    }
    /** Tap: in adding mode, add; otherwise the app's details. */
    fun tap(e: AppEntry) { if (picking) pick(e) else details = e }
    // Long-press does the same as a tap in adding mode, from anywhere.
    val dragger = Dragger(start = { e, _ -> vm.feel.fire(com.krishna.miniremote.Feel.HEAVY); pick(e) }, move = {}, end = {})

    val brand = d?.brand
    val entries = remember(vm.tvApps, vm.shortcuts, brand) {
        vm.tvApps.map { app ->
            val pin = vm.pinnedTvApp(app)
            AppEntry(
                key = "tv:${app.id}", name = app.name, bg = tint(app.name), ink = Color.White, icon = app.icon,
                logo = Catalog.apps.firstOrNull { it.name.equals(app.name, true) }?.logo ?: Logos.slugFor(app.name),
                pkg = Catalog.apps.firstOrNull { it.name.equals(app.name, true) }?.let { Catalog.iconPkg(it) } ?: if (brand == Brand.ANDROID_TV) app.id else null,
                version = app.version, installed = true, pinned = pin != null, pinnedAs = pin,
                storeId = if (brand == Brand.ROKU) app.id else null,
                open = { vm.launchApp(app.name, app.id) }, pin = { vm.pinTvApp(app) }, unpin = { pin?.let { vm.setPinned(it, false) } },
            )
        }
    }
    val popular = Catalog.apps.filter { brand == null || it.ids.containsKey(brand) }.map { c ->
        val pin = vm.pinnedCatalog(c)
        AppEntry(
            key = "cat:${c.key}", name = c.name, bg = Color(c.bg), ink = Color(c.ink), vendor = c.vendor.ifEmpty { null }, logo = c.logo, site = c.site.ifEmpty { null }, pkg = Catalog.iconPkg(c),
            installed = vm.installed(c), pinned = pin != null, pinnedAs = pin,
            storeId = brand?.let { b -> if (b == Brand.ROKU || b == Brand.ANDROID_TV) c.ids[b] else null },
            open = { vm.launchCatalog(c) }, pin = { vm.pinCatalog(c) }, unpin = { pin?.let { vm.setPinned(it, false) } },
        )
    }
    val added = vm.shortcuts.filter { it.kind == ShortcutKind.APP && it.added }.map { s ->
        AppEntry(
            key = "add:${s.id}", name = s.name, bg = tint(s.name), ink = Color.White, badge = s.brand?.label, pinned = s.pinned, pinnedAs = s.takeIf { it.pinned },
            logo = Logos.slugFor(s.name),
            pkg = Catalog.apps.firstOrNull { it.name.equals(s.name, true) }?.let { Catalog.iconPkg(it) } ?: if (s.brand == Brand.ANDROID_TV) s.value.takeIf { "://" !in it } else null,
            storeId = if (s.brand == Brand.ROKU || s.brand == Brand.ANDROID_TV) s.value.takeIf { "://" !in it } else null,
            open = { vm.launch(s) }, pin = { vm.setPinned(s, true) }, unpin = { vm.setPinned(s, false) }, delete = { vm.removeShortcut(s) },
        )
    }
    val channels = vm.shortcuts.filter { it.kind == ShortcutKind.CHANNEL }.map { s ->
        AppEntry(
            key = "ch:${s.id}", name = s.name, bg = ChannelBg, ink = Color.White, badge = s.value, pinned = s.pinned, pinnedAs = s.takeIf { it.pinned },
            open = { vm.launch(s) }, pin = { vm.setPinned(s, true) }, unpin = { vm.setPinned(s, false) }, delete = { vm.removeShortcut(s) },
        )
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll, enabled = drag == null)
                .padding(horizontal = 20.dp).padding(bottom = NAV_H + 40.dp),
        ) {
            PageTitle(if (picking) "Add to quick access" else "Apps") {
                RoundIcon(Ico.REFRESH, "Reload the TV's apps") { vm.refreshApps(force = true) }
                RoundIcon(Ico.PLUS, "Add a channel or app") { adding = ShortcutKind.CHANNEL }
            }
            Text(
                when {
                    d == null -> "Add a TV to open apps and channels"
                    ready -> "On ${shortName(d.name, d.brand.label)}"
                    else -> "${shortName(d.name, d.brand.label)} · ${linkWord(vm.link).ifEmpty { "Not connected" }}"
                },
                color = if (ready) Color(0xFF6DD58C) else Muted, fontSize = 13.5.sp,
                modifier = Modifier.then(if (!ready) Modifier.clickable(onClickLabel = "choose a TV", onClick = openDevices) else Modifier),
            )

            if (picking) Hint("Tap apps to add them to quick access. Tap one again for its options.", top = 10)

            if (entries.isNotEmpty() || vm.appsLoading) {
                SectionTitle("On your TV")
                if (entries.isEmpty()) Loading(accent, "Loading apps…")
                TileGrid(entries) { e -> Tile(e, dragger) { tap(e) } }
            }
            // The TV lists its apps: those are above; the catalogue's other apps are suggestions.
            // It doesn't: the catalogue it is (ticked when we can tell they're installed).
            if (entries.isNotEmpty()) {
                val missing = popular.filter { it.installed == false }
                if (missing.isNotEmpty()) {
                    SectionTitle("Not on your TV")
                    Hint("Tap one to get it from the TV's store.", top = 4)
                    TileGrid(missing) { e -> Tile(e, dragger) { tap(e) } }
                }
            } else {
                SectionTitle("Apps")
                TileGrid(popular) { e -> Tile(e, dragger) { tap(e) } }
            }
            if (added.isNotEmpty()) {
                SectionTitle("Added apps")
                TileGrid(added) { e -> Tile(e, dragger) { tap(e) } }
            }

            SectionTitle("TV channels")
            if (channels.isEmpty()) Hint("Add channels by number, like 101.", top = 6)
            // Channels: tap tunes straight away; long-press drags like apps.
            else TileGrid(channels) { e -> Tile(e, dragger) { if (picking) pick(e) else if (!needTv()) e.open() } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Btn("Add channel", kind = 1) { adding = ShortcutKind.CHANNEL } }
                Box(Modifier.weight(1f)) { Btn("Add app", kind = 1) { adding = ShortcutKind.APP } }
            }
        }

        // While dragging: the tile follows the finger.
        drag?.let { e ->
            val density = LocalDensity.current
            Box(
                Modifier.offset {
                    IntOffset(
                        (dragPos.x - origin.x - with(density) { 60.dp.toPx() }).roundToInt(),
                        (dragPos.y - origin.y - with(density) { 40.dp.toPx() }).roundToInt(),
                    )
                }.size(120.dp, 80.dp).shadow(12.dp, RoundedCornerShape(18.dp)).background(e.bg, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.name, color = e.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }

    details?.let { e ->
        AppSheet(e, accent, vm.settings.logos, onOpen = { if (!needTv()) e.open() }, onStore = { if (!needTv()) e.storeId?.let { id -> vm.openStore(e.name, id) } }) { details = null }
    }
    adding?.let { k -> AddShortcutSheet(vm, accent, k) { adding = null } }
    quickMenu?.let { i ->
        val pinned = vm.shortcuts.filter { it.pinned }
        val s = pinned.getOrNull(i)
        if (s == null) quickMenu = null
        else QuickActionsSheet(s, i, pinned.size, vm.settings.logos,
            icon = s.icon ?: vm.tvApps.firstOrNull { it.name.equals(s.name, true) }?.icon,
            onOpen = { if (!needTv()) vm.launch(s) },
            onMove = { by -> vm.movePinned(s, by); quickMenu = i + by },
            onRemove = { vm.setPinned(s, false) },
            onDelete = if (s.added) ({ vm.removeShortcut(s) }) else null,
        ) { quickMenu = null }
    }
}

/** Where a dragged app lands: a grey dashed box in the Quick access row. Lights up when the tile is over it. */
@Composable
private fun DropSlot(over: Boolean, accent: Color, modifier: Modifier) {
    val line = if (over) accent else T.Sub
    Box(
        modifier.fillMaxWidth().height(96.dp)
            .background(if (over) accent.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(20.dp))
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    line, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (over) "Release" else "Drop app here", color = if (over) accent else T.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Long-press drag callbacks shared by every tile. */
private class Dragger(val start: (AppEntry, Offset) -> Unit, val move: (Offset) -> Unit, val end: () -> Unit)

@Composable
private fun Loading(accent: Color, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = accent, trackColor = M.High, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
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

/** A tile in the app's colour, with its name or the TV's icon. Tap = [onTap]; long-press = drag. */
@Composable
private fun Tile(e: AppEntry, dragger: Dragger, onTap: () -> Unit) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    val entry by rememberUpdatedState(e)
    val face = Face(listOf(e.bg, e.bg), CARD.shadows)
    Box(
        Modifier.fillMaxWidth().height(92.dp)
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .softFace(face, corner = 18.dp, dpUnits = true).clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> dragger.start(entry, rect.topLeft + at) },
                    onDrag = { change, by -> change.consume(); dragger.move(by) },
                    onDragEnd = { dragger.end() },
                    onDragCancel = { dragger.end() },
                )
            }
            .clickable(onClickLabel = "details", role = Role.Button, onClick = onTap)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        val name: @Composable () -> Unit = {
            Text(e.name, color = e.ink, fontSize = if (e.name.length > 14) 15.sp else 19.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (e.icon != null) TvIcon(e.icon, Modifier.fillMaxSize().padding(10.dp), name) else name()
        e.badge?.let {
            Text(it, color = Color.White, fontSize = 11.sp, fontFamily = Mono, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (e.pinned) LineIcon(Ico.STAR, e.ink.copy(0.85f), 14.dp, Modifier.align(Alignment.TopEnd).padding(9.dp))
    }
}

/**
 * An app's details: name and maker, version, whether it's on the TV; Open, quick access,
 * and Install / Uninstall (each asks again in a second sheet first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(e: AppEntry, accent: Color, logosOn: Boolean, onOpen: () -> Unit, onStore: () -> Unit, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirm by remember { mutableStateOf(false) }
    val installed = e.installed
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, containerColor = SheetBg, contentColor = T.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 6.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(e.bg), contentAlignment = Alignment.Center) {
                    val short: @Composable () -> Unit = { Text(e.name.take(2).uppercase(), color = e.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    val logo: @Composable () -> Unit = { AppLogo(e.logo, e.ink, 34.dp, logosOn, e.site, e.pkg, fallback = short) }
                    if (e.icon != null) TvIcon(e.icon, Modifier.fillMaxSize().padding(6.dp), logo) else logo()
                }
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = T.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(e.vendor ?: e.badge ?: "App", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Group {
                InfoRow("Provided by", e.vendor ?: "Not shared by the TV")
                Divider()
                InfoRow("Version", e.version ?: "Not shared by the TV")
                Divider()
                InfoRow("On this TV", when (installed) { true -> "Installed"; false -> "Not installed"; null -> "Can't tell" })
                // The TV's own id for the app (what Add asks for).
                if (e.key.startsWith("tv:")) { Divider(); InfoRow("App ID", e.key.removePrefix("tv:")) }
            }
            Spacer(Modifier.height(8.dp))
            if (installed != false) Btn("Open") { onOpen(); onClose() }
            Btn(if (e.pinned) "Unpin" else "Pin", kind = 1) { if (e.pinned) e.unpin() else e.pin(); onClose() }
            if (e.storeId != null) {
                if (installed == false) Btn("Install") { confirm = true }
                else Btn("Uninstall", kind = 2) { confirm = true }
            } else if (e.delete == null) {
                Hint("Install or remove it from the TV's store.", top = 12)
            }
            e.delete?.let { del -> Btn("Delete", kind = 2) { del(); onClose() } }
        }
    }
    if (confirm) {
        val install = installed == false
        ConfirmSheet(
            title = if (install) "Install ${e.name}?" else "Uninstall ${e.name}?",
            text = if (install) "Its page opens in the TV's app store. Choose Install there with the TV's remote."
            else "Its page opens in the TV's app store. Choose Uninstall (or Remove) there to confirm.",
            action = if (install) "Open store" else "Open store",
            danger = !install,
            onConfirm = { onStore(); onClose() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = T.Ink, fontSize = 14.sp, textAlign = TextAlign.End)
    }
}

/** Quick access item: a rounded square with the app's colour or the channel number, name below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(s: Shortcut, accent: Color, icon: String?, logoOn: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val cat = if (s.kind == ShortcutKind.APP && s.brand == null) Catalog.byKey(s.value) else null
    val bg = when {
        s.kind == ShortcutKind.CHANNEL -> ChannelBg
        cat != null -> Color(cat.bg)
        else -> tint(s.name)
    }
    val ink = cat?.let { Color(it.ink) } ?: Color.White
    Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                .combinedClickable(onClickLabel = "open ${s.name}", onLongClickLabel = "options", role = Role.Button, onClick = onClick, onLongClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            val short: @Composable () -> Unit = {
                Text(
                    if (s.kind == ShortcutKind.CHANNEL) s.value else s.name.take(2).uppercase(),
                    color = ink, fontSize = if (s.kind == ShortcutKind.CHANNEL && s.value.length > 3) 15.sp else 20.sp,
                    fontWeight = FontWeight.Bold, fontFamily = if (s.kind == ShortcutKind.CHANNEL) Mono else Inter, maxLines = 1,
                )
            }
            // The TV's own icon for the app when it has one (Roku, LG); otherwise its initials.
            // Logo, in order: the TV's own icon, then Simple Icons, then the initials.
            val slug = if (s.kind == ShortcutKind.CHANNEL) null else cat?.logo ?: Logos.slugFor(s.name)
            val pkg = if (s.kind == ShortcutKind.CHANNEL) null else ((cat ?: Catalog.apps.firstOrNull { it.name.equals(s.name, true) })?.let { Catalog.iconPkg(it) }
                ?: if (s.brand == Brand.ANDROID_TV) s.value.takeIf { "://" !in it } else null)
            val logo: @Composable () -> Unit = { AppLogo(slug, ink, 34.dp, logoOn, cat?.site, pkg, fallback = short) }
            if (icon != null) TvIcon(icon, Modifier.fillMaxSize().padding(8.dp), logo) else logo()
        }
        Text(s.name, color = T.Ink, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** A quick access app as one of Home's circles: its logo on its colour; tap to open it on the TV. */
@Composable
internal fun AppCircle(
    vm: RemoteVM, s: Shortcut, size: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape, modifier: Modifier? = null, onClick: () -> Unit,
) {
    val cat = if (s.kind == ShortcutKind.APP && s.brand == null) Catalog.byKey(s.value) else null
    val bg0 = when {
        s.kind == ShortcutKind.CHANNEL -> ChannelBg
        cat != null -> Color(cat.bg)
        else -> tint(s.name)
    }
    val ink = cat?.let { Color(it.ink) } ?: Color.White
    val icon = s.icon ?: vm.tvApps.firstOrNull { it.name.equals(s.name, true) }?.icon
    // Near-black tiles (Netflix…) would vanish on the black remote: lift them to a dark grey.
    val bg = liftDark(bg0)
    Box(
        (modifier ?: Modifier.size(size)).clip(shape).background(bg)
            .clickable(onClickLabel = "open ${s.name}", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = s.name },
        contentAlignment = Alignment.Center,
    ) {
        val short: @Composable () -> Unit = {
            Text(if (s.kind == ShortcutKind.CHANNEL) s.value else s.name.take(2).uppercase(), color = ink,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = if (s.kind == ShortcutKind.CHANNEL) Mono else Inter, maxLines = 1)
        }
        val slug = if (s.kind == ShortcutKind.CHANNEL) null else cat?.logo ?: Logos.slugFor(s.name)
        val pkg = if (s.kind == ShortcutKind.CHANNEL) null else ((cat ?: Catalog.apps.firstOrNull { it.name.equals(s.name, true) })?.let { Catalog.iconPkg(it) }
            ?: if (s.brand == Brand.ANDROID_TV) s.value.takeIf { "://" !in it } else null)
        val logo: @Composable () -> Unit = { AppLogo(slug, ink, size * 0.46f, vm.settings.logos, cat?.site, pkg, bleed = true, fallback = short) }
        if (icon != null) TvIcon(icon, Modifier.fillMaxSize().padding(10.dp), logo) else logo()
    }
}

/** Long-press on a quick access item: its tile and name, then Open, move, remove (and delete for added ones). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    s: Shortcut, index: Int, count: Int, logoOn: Boolean, icon: String?,
    onOpen: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cat = if (s.kind == ShortcutKind.APP && s.brand == null) Catalog.byKey(s.value) else null
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, containerColor = SheetBg, contentColor = T.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 6.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            QuickTile(s, T.Ink, icon, logoOn, onClick = { onOpen(); onClose() }, onLongPress = {})
            Text(when (s.kind) { ShortcutKind.CHANNEL -> "Channel ${s.value}"; else -> cat?.vendor?.ifEmpty { null } ?: s.brand?.label ?: "App" },
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            Btn(if (s.kind == ShortcutKind.CHANNEL) "Tune" else "Open") { onOpen(); onClose() }
            if (count > 1) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Btn("Move left", kind = 1, enabled = index > 0) { onMove(-1) } }
                Box(Modifier.weight(1f)) { Btn("Move right", kind = 1, enabled = index < count - 1) { onMove(1) } }
            }
            Btn("Unpin", kind = 2) { onRemove(); onClose() }
            onDelete?.let { del -> Btn("Delete", kind = 2) { del(); onClose() } }
        }
    }
}

@Composable
private fun OptionsMenu(open: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    DropdownMenu(open, onDismiss, Modifier.background(Color(0xFF3A3A3D))) {
        items.forEach { (label, act) ->
            DropdownMenuItem(text = { Text(label, color = if (label == "Delete") Color(0xFFFF8A8A) else T.Ink) }, onClick = { onDismiss(); act() })
        }
    }
}

/* ------------------------------------------------------------- TV icons */

/**
 * An app's logo: Simple Icons (drawn in [ink]) first, then the app's own website icon for apps
 * Simple Icons doesn't carry (JioHotstar, Prime Video…). [fallback] while loading, when off, or if neither exists.
 */
/** Tiles this dark disappear on the black remote: they get a dark grey instead. */
internal val LiftGrey get() = Look.p.high
internal fun liftDark(c: Color): Color = if (!Look.p.light && c.luminance() < 0.02f) LiftGrey else c

/** The average colour just inside an icon's corners (null when they're see-through). */
private fun edgeColour(b: ImageBitmap): Color? = runCatching {
    val bm = b.asAndroidBitmap()
    val w = bm.width; val h = bm.height; val i = (minOf(w, h) * 0.04f).toInt().coerceAtLeast(1)
    val px = listOf(bm.getPixel(i, i), bm.getPixel(w - 1 - i, i), bm.getPixel(i, h - 1 - i), bm.getPixel(w - 1 - i, h - 1 - i))
    if (px.any { android.graphics.Color.alpha(it) < 200 }) return@runCatching null
    Color(px.sumOf { android.graphics.Color.red(it) } / 4, px.sumOf { android.graphics.Color.green(it) } / 4, px.sumOf { android.graphics.Color.blue(it) } / 4)
}.getOrNull()

@Composable
private fun AppLogo(slug: String?, ink: Color, size: androidx.compose.ui.unit.Dp, enabled: Boolean, site: String? = null, pkg: String? = null, bleed: Boolean = false, fallback: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val hex = String.format("%06X", ink.toArgb() and 0xFFFFFF)
    var bmp by remember(slug, hex, site, pkg) { mutableStateOf<ImageBitmap?>(null) }
    var full by remember(slug, hex, site, pkg) { mutableStateOf(false) }
    var real by remember(slug, hex, site, pkg) { mutableStateOf(false) }
    LaunchedEffect(slug, hex, enabled, site, pkg) {
        if (!enabled) { bmp = null; return@LaunchedEffect }
        // The real app icon first (Google Play), then the Simple Icons mark, then the website icon.
        val play = pkg?.let { Logos.playIcon(ctx, it) }
        if (play != null) { bmp = play.asImageBitmap(); full = true; real = true; return@LaunchedEffect }
        val mono = slug?.let { Logos.get(ctx, it, hex, px) }
        if (mono != null) { bmp = mono.asImageBitmap(); full = false; return@LaunchedEffect }
        val icon = site?.let { Logos.siteIcon(ctx, it, px * 2) }
        bmp = icon?.asImageBitmap(); full = icon != null
    }
    val b = bmp
    if (b == null) fallback()
    // On a quick access circle or square the real icon is the whole tile, edge to edge (a touch
    // larger, so the icon's own corners fall outside the shape): no tile colour around it.
    else if (real && bleed) {
        // Balanced with the other logos: the icon at about three quarters of the tile, on its own
        // edge colour, so the two blend instead of the app's catalogue colour showing round it.
        // The icon stays its size (about three quarters of the tile); round it, its own edge
        // pixels are stretched outward to the tile's edge, so its background simply carries on
        // (gradients too) instead of a band of another colour.
        val opaque = remember(b) { edgeColour(b) != null }
        if (opaque) {
            val src = remember(b) {
                val ab = b.asAndroidBitmap()
                val i = (minOf(ab.width, ab.height) * 0.035f).toInt() // trim soft, anti-aliased edges
                runCatching { android.graphics.Bitmap.createBitmap(ab, i, i, ab.width - 2 * i, ab.height - 2 * i) }.getOrDefault(ab)
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val target = size.minDimension * 0.74f
                val k = target / maxOf(src.width, src.height)
                val shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                shader.setLocalMatrix(android.graphics.Matrix().apply {
                    setScale(k, k); postTranslate(center.x - src.width * k / 2f, center.y - src.height * k / 2f)
                })
                drawRect(androidx.compose.ui.graphics.ShaderBrush(shader))
            }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // See-through corners (an icon on its own plate): the plate as it is, on the tile's colour.
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(0.74f))
        }
    }
    // The real app icon fills its tile (as square as the tile allows), with app-icon corners.
    else if (real) Image(b, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).padding(6.dp).clip(RoundedCornerShape(16.dp)))
    // Full-colour site icons get a small rounded plate so they sit well on any tile colour.
    else if (full) Image(b, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)))
    else Image(b, contentDescription = null, modifier = Modifier.size(size))
}

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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), scrimColor = Color.Black.copy(0.55f), dragHandle = { Grab() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add", color = T.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
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
                IdHint(brand, accent)
            }
            ToggleRow("Pin it", null, pin) { pin = it }
            Btn("Save", enabled = name.isNotBlank() && value.isNotBlank() && (kind == ShortcutKind.CHANNEL || brand != null)) {
                vm.addShortcut(kind, name, value, pin); onClose()
            }
        }
    }
}

/**
 * Where to find an app's ID, for the Add sheet: a line for the TV in use and a small picture of
 * where the ID shows, with the ID picked out.
 */
@Composable
private fun IdHint(brand: Brand?, accent: Color) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LineIcon(Ico.INFO, T.Dim, 15.dp)
            Text("Where do I find the App ID?", color = T.Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            LineIcon(if (open) Ico.UP else Ico.DOWN, T.Dim, 13.dp, stroke = 2.2f)
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(M.Container).padding(14.dp)) {
                val (text, before, id, after) = when (brand) {
                    Brand.ANDROID_TV -> listOf("Open the app's Google Play page in a browser. The ID is the part after id= in the address.",
                        "play.google.com/store/apps/details?id=", "com.netflix.ninja", "")
                    Brand.ROKU -> listOf("Installed channels are already under On your TV (tap one to see its ID). For others, it's the number in the channel's address on channelstore.roku.com.",
                        "channelstore.roku.com/details/", "12", "/netflix")
                    Brand.SAMSUNG -> listOf("Apps on the TV are already under On your TV: tap one and its App ID is in the details.",
                        "App ID   ", "3201907018807", "")
                    Brand.LG -> listOf("Apps on the TV are already under On your TV: tap one and its App ID is in the details.",
                        "App ID   ", "netflix", "")
                    null -> listOf("Choose a TV first: app IDs are different on every kind of TV.", "", "", "")
                }
                Text(text, color = Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                if (id.isNotEmpty()) {
                    // The picture: a little screen (browser address bar, or the app's details), with the ID highlighted.
                    Box(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111113))
                            .border(1.dp, T.Ink.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(12.dp),
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach { Box(Modifier.size(7.dp).clip(CircleShape).background(it.copy(alpha = 0.7f))) }
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262A)).padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(before, color = Muted, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Text(id, color = Color.White, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.35f)).border(1.dp, accent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                                if (after.isNotEmpty()) Text(after, color = Muted, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1)
                            }
                            Text("↑ this is the App ID", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp).align(Alignment.End))
                        }
                    }
                }
            }
        }
    }
}
