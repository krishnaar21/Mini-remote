import SwiftUI
import UniformTypeIdentifiers

private func tileColor(_ name: String) -> Color {
    let palette: [UInt32] = [0x2B3A55, 0x3B2B55, 0x2B4A3F, 0x4A3A2B, 0x4A2B3A, 0x2B4450, 0x3A3A3F]
    var h: UInt64 = 5381
    for u in name.lowercased().unicodeScalars { h = (h &* 33) &+ UInt64(u.value) }
    return Color(hex: palette[Int(h % UInt64(palette.count))])
}

private let channelBg = Color(hex: 0x263042)

/** Everything a tile and its details sheet need, whatever the app came from. */
final class AppEntry: Identifiable {
    let id: String
    let name: String
    let bg: Color
    let ink: Color
    var icon: String? = nil
    var badge: String? = nil
    var vendor: String? = nil
    var version: String? = nil
    var installed: Bool? = nil
    var pinned = false
    /** Its quick access entry, when it has one. */
    var pinnedAs: Shortcut? = nil
    var storeId: String? = nil
    var open: () -> Void = {}
    var pin: () -> Void = {}
    var unpin: () -> Void = {}
    var delete: (() -> Void)? = nil
    init(id: String, name: String, bg: Color, ink: Color) { self.id = id; self.name = name; self.bg = bg; self.ink = ink }
}

/**
 * Apps and channels: what's on the TV, then the catalogue's other apps as "Not on your TV",
 * added apps and channels. Opened from a + place (picking), tapping an app adds it to quick
 * access and tapping it again shows its quick access options; it closes once quick access is full.
 */
struct AppsView: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    var picking = false
    let openDevices: () -> Void
    var onFull: () -> Void = {}

    @State private var details: AppEntry?
    @State private var adding: ShortcutKind?
    @State private var options: Shortcut?

    private var ready: Bool { vm.active != nil && vm.link == .ready }
    private func needTv() -> Bool { if !ready { openDevices(); return true }; return false }

    private var entries: [AppEntry] {
        let brand = vm.active?.brand
        return vm.tvApps.map { a in
            let pin = vm.pinnedTvApp(a)
            let e = AppEntry(id: "tv:\(a.id)", name: a.name, bg: tileColor(a.name), ink: .white)
            e.icon = a.icon; e.version = a.version; e.installed = true; e.pinned = pin != nil; e.pinnedAs = pin
            e.storeId = brand == .ROKU ? a.id : nil
            e.open = { vm.launchApp(a.name, a.id) }; e.pin = { vm.pinTvApp(a) }; e.unpin = { if let p = pin { vm.setPinned(p, false) } }
            return e
        }
    }

    private var popular: [AppEntry] {
        let brand = vm.active?.brand
        return Catalog.apps.filter { brand == nil || $0.ids[brand!] != nil }.map { c in
            let pin = vm.pinnedCatalog(c)
            let e = AppEntry(id: "cat:\(c.key)", name: c.name, bg: Color(hex: c.bg), ink: Color(hex: c.ink))
            e.vendor = c.vendor.isEmpty ? nil : c.vendor; e.installed = vm.installed(c); e.pinned = pin != nil; e.pinnedAs = pin
            if let b = brand, b == .ROKU || b == .ANDROID_TV { e.storeId = c.ids[b] }
            e.open = { vm.launchCatalog(c) }; e.pin = { vm.pinCatalog(c) }; e.unpin = { if let p = pin { vm.setPinned(p, false) } }
            return e
        }
    }

    private func shortcutEntries(_ kind: ShortcutKind) -> [AppEntry] {
        vm.shortcuts.filter { $0.kind == kind && (kind == .channel || $0.added) }.map { s in
            let e = AppEntry(id: "s:\(s.id)", name: s.name, bg: kind == .channel ? channelBg : tileColor(s.name), ink: .white)
            e.badge = kind == .channel ? s.value : s.brand?.label; e.pinned = s.pinned; e.pinnedAs = s.pinned ? s : nil
            if kind == .app, let b = s.brand, (b == .ROKU || b == .ANDROID_TV), !s.value.contains("://") { e.storeId = s.value }
            e.open = { vm.launch(s) }; e.pin = { vm.setPinned(s, true) }; e.unpin = { vm.setPinned(s, false) }
            e.delete = { vm.removeShortcut(s) }
            return e
        }
    }

    /** Adding mode: the first tap adds the app, a second shows its quick access options. */
    private func pick(_ e: AppEntry) {
        if let s = e.pinnedAs { options = s; return }
        e.pin(); vm.feel.fire(.success)
        if vm.quickFull { DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { onFull() } }
    }
    private func tap(_ e: AppEntry) { if picking { pick(e) } else { details = e } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(picking ? "Add to quick access" : "Apps").font(.system(size: picking ? 24 : 30, weight: .semibold)).foregroundStyle(T.ink).accessibilityAddTraits(.isHeader)
                    Spacer()
                    RoundKey(symbol: "arrow.clockwise", label: "Reload the TV's apps", size: 44, iconSize: 17, accent: accent) { vm.refreshApps(force: true) }
                    RoundKey(symbol: "plus", label: "Add a channel or app", size: 44, iconSize: 17, accent: accent) { adding = .channel }
                }
                .padding(.top, 18)
                Group {
                    if picking {
                        Text("Tap apps to add them. Tap one again for its options.").foregroundStyle(T.muted)
                    } else if let d = vm.active {
                        Text(ready ? "On \(shortName(d.name, d.brand.label))" : "\(shortName(d.name, d.brand.label)) · \(linkWord(vm.link).isEmpty ? "Not connected" : linkWord(vm.link))")
                            .foregroundStyle(ready ? T.ok : T.muted)
                    } else { Text("Add a TV to open apps and channels").foregroundStyle(T.muted) }
                }
                .font(.system(size: 13.5)).padding(.top, 4)
                .onTapGesture { if !ready && !picking { openDevices() } }

                if !vm.tvApps.isEmpty || vm.appsLoading {
                    SectionTitle(text: "On your TV")
                    if vm.tvApps.isEmpty { ProgressView().tint(accent).padding(.vertical, 8) }
                    grid(entries)
                    // The catalogue's other apps: suggestions, to get from the TV's store.
                    let missing = popular.filter { $0.installed == false }
                    if !missing.isEmpty {
                        SectionTitle(text: "Not on your TV")
                        Text("Tap one to get it from the TV's store.").font(.system(size: 13)).foregroundStyle(T.muted).padding(.bottom, 6)
                        grid(missing)
                    }
                } else {
                    SectionTitle(text: "Apps")
                    grid(popular)
                }
                let added = shortcutEntries(.app)
                if !added.isEmpty { SectionTitle(text: "Added apps"); grid(added) }
                SectionTitle(text: "TV channels")
                let channels = shortcutEntries(.channel)
                if channels.isEmpty {
                    Text("Add the channels you watch, like 101 · Star Sports. Tapping one tunes the TV to it.")
                        .font(.system(size: 13)).foregroundStyle(T.muted)
                } else { grid(channels) { e in if picking { pick(e) } else if !needTv() { e.open() } } }
                HStack(spacing: 12) {
                    WideButton(title: "Add a channel", kind: .secondary) { adding = .channel }
                    WideButton(title: "Add an app", kind: .secondary) { adding = .app }
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 40)
        }
        .background(T.sheet)
        .sheetBackdrop(details != nil || adding != nil || options != nil)
        .sheet(item: $details) { e in
            AppSheet(entry: e, accent: accent, onOpen: { if !needTv() { e.open() } },
                     onStore: { if !needTv(), let id = e.storeId { vm.openStore(e.name, id) } })
        }
        .sheet(item: $adding) { k in AddSheet(vm: vm, accent: accent, start: k) }
        .sheet(item: $options) { s in QuickOptions(vm: vm, s: s) }
    }

    private func grid(_ items: [AppEntry], tap custom: ((AppEntry) -> Void)? = nil) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            ForEach(items) { e in
                Tile(e: e)
                    .onTapGesture { if let custom { custom(e) } else { tap(e) } }
                    // Long-press: add to quick access (or its options), from anywhere.
                    .onLongPressGesture { vm.feel.fire(.heavy); pick(e) }
            }
        }
        .padding(.top, 4)
    }
}

/** A quick access app's options: open it, move it, take it out (or delete an added one). */
private struct QuickOptions: View {
    @ObservedObject var vm: RemoteModel
    let s: Shortcut
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let pinned = vm.shortcuts.filter(\.pinned)
        let i = pinned.firstIndex { $0.id == s.id } ?? 0
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                AppTile(s: s, size: 52, square: true).frame(width: 52)
                VStack(alignment: .leading, spacing: 1) {
                    Text(s.name).font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink)
                    Text("Quick access · place \(i + 1) of \(pinned.count)").font(.system(size: 13)).foregroundStyle(T.sub)
                }
            }
            .padding(.top, 22)
            SCard {
                SRow(symbol: "play.rectangle", title: "Open on TV", trailing: nil) { vm.launch(s); dismiss() }
                if i > 0 { SLine(); SRow(symbol: "arrow.left", title: "Move earlier", trailing: nil) { vm.movePinned(s, -1) } }
                if i < pinned.count - 1 { SLine(); SRow(symbol: "arrow.right", title: "Move later", trailing: nil) { vm.movePinned(s, 1) } }
                SLine()
                SRow(symbol: "minus.circle", title: "Remove from quick access", trailing: nil) { vm.setPinned(s, false); dismiss() }
                if s.added { SLine(); SRow(symbol: "trash", title: "Delete", tile: Color(hex: 0x8E2B2B), trailing: nil) { vm.removeShortcut(s); dismiss() } }
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 28)
        .fitSheet()
    }
}

extension ShortcutKind: Identifiable { var id: String { rawValue } }

private struct Tile: View {
    let e: AppEntry
    var body: some View {
        ZStack(alignment: .topTrailing) {
            ZStack {
                RoundedRectangle(cornerRadius: 18).fill(e.bg)
                if let icon = e.icon { TvIcon(url: icon) { name }.padding(10) } else { name }
            }
            if e.pinned { Image(systemName: "star.fill").font(.system(size: 12)).foregroundStyle(e.ink.opacity(0.85)).padding(9) }
            if let b = e.badge {
                Text(b).font(.system(size: 12, design: .monospaced)).foregroundStyle(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2).background(RoundedRectangle(cornerRadius: 6).fill(.black.opacity(0.35)))
                    .padding(8).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .frame(height: 92)
        .contentShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(e.name)
        .accessibilityAddTraits(.isButton)
    }
    private var name: some View {
        Text(e.name).font(.system(size: e.name.count > 14 ? 15 : 19, weight: .bold)).foregroundStyle(e.ink)
            .multilineTextAlignment(.center).lineLimit(2).padding(.horizontal, 12)
    }
}

private struct QuickTile: View {
    let s: Shortcut
    let icon: String?
    var body: some View {
        let cat = s.kind == .app && s.brand == nil ? Catalog.byKey(s.value) : nil
        let bg = s.kind == .channel ? channelBg : (cat.map { Color(hex: $0.bg) } ?? tileColor(s.name))
        let ink = cat.map { Color(hex: $0.ink) } ?? .white
        VStack(spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 20).fill(bg)
                let short = Text(s.kind == .channel ? s.value : String(s.name.prefix(2)).uppercased())
                    .font(.system(size: 20, weight: .bold, design: s.kind == .channel ? .monospaced : .default)).foregroundStyle(ink)
                if let icon { TvIcon(url: icon) { short }.padding(8) }
                else { SiteLogo(site: cat.flatMap { Logos.sites[$0.key] }) { short }.padding(cat == nil ? 0 : 10) }
            }
            .frame(width: 70, height: 70)
            Text(s.name).font(.system(size: 12)).foregroundStyle(T.ink).lineLimit(1).frame(width: 74)
        }
        .accessibilityElement(children: .ignore).accessibilityLabel(s.name).accessibilityAddTraits(.isButton)
    }
}

/** An icon the TV serves itself (Roku, LG). Only loads from home-network addresses. */
struct TvIcon<Fallback: View>: View {
    let url: String
    @ViewBuilder let fallback: () -> Fallback
    @State private var image: UIImage?
    private static var cache: NSCache<NSString, UIImage> { IconCache.shared }

    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12)) } else { fallback() }
        }
        .task(id: url) {
            if let c = Self.cache.object(forKey: url as NSString) { image = c; return }
            guard let u = URL(string: url), let host = u.host, HostValidator.isPrivateIPv4(host),
                  let res = try? await Http.plain.data(from: u), res.0.count < 2_000_000, let img = UIImage(data: res.0) else { return }
            Self.cache.setObject(img, forKey: url as NSString)
            image = img
        }
    }
}

enum IconCache { static let shared = NSCache<NSString, UIImage>() }

/** An app's details: maker, version, on the TV or not; Open, quick access, Install / Uninstall (asks again first). */
private struct AppSheet: View {
    let entry: AppEntry
    let accent: Color
    let onOpen: () -> Void
    let onStore: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirm = false

    var body: some View {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 16) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16).fill(entry.bg)
                        if let icon = entry.icon { TvIcon(url: icon) { initials }.padding(6) } else { initials }
                    }
                    .frame(width: 64, height: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.name).font(.system(size: 20, weight: .semibold)).foregroundStyle(T.ink)
                        Text(entry.vendor ?? entry.badge ?? "App").font(.system(size: 13)).foregroundStyle(T.muted)
                    }
                }
                Card {
                    info("Provided by", entry.vendor ?? "Not shared by the TV"); RowDivider()
                    info("Version", entry.version ?? "Not shared by the TV"); RowDivider()
                    info("On this TV", entry.installed == true ? "Installed" : (entry.installed == false ? "Not installed" : "Can't tell"))
                }
                .padding(.top, 18)
                if entry.installed != false { WideButton(title: "Open on TV") { onOpen(); dismiss() } }
                WideButton(title: entry.pinned ? "Remove from Quick access" : "Add to Quick access", kind: .secondary) {
                    if entry.pinned { entry.unpin() } else { entry.pin() }; dismiss()
                }
                if entry.storeId != nil {
                    WideButton(title: entry.installed == false ? "Install…" : "Uninstall…", kind: entry.installed == false ? .primary : .danger) { confirm = true }
                } else if entry.delete == nil {
                    Text("To install or remove it, use the app store on the TV.").font(.system(size: 13)).foregroundStyle(T.muted).padding(.top, 12)
                }
                if let del = entry.delete { WideButton(title: "Delete from this list", kind: .danger) { del(); dismiss() } }
            }
            .padding(24).padding(.top, 8)
            .sheetBackdrop(confirm)
        .fitSheet()
        .sheet(isPresented: $confirm) {
            let install = entry.installed == false
            ConfirmSheet(title: install ? "Install \(entry.name)?" : "Uninstall \(entry.name)?",
                         text: install ? "Its page opens in the TV's app store. Choose Install there with the TV's remote."
                                       : "Its page opens in the TV's app store. Choose Uninstall (or Remove) there to confirm.",
                         action: install ? "Open store page" : "Open store page to uninstall", danger: !install) { onStore(); dismiss() }
        }
    }

    private var initials: some View { Text(String(entry.name.prefix(2)).uppercased()).font(.system(size: 20, weight: .bold)).foregroundStyle(entry.ink) }

    private func info(_ k: String, _ v: String) -> some View {
        HStack { Text(k).foregroundStyle(T.muted); Spacer(); Text(v).foregroundStyle(T.ink).multilineTextAlignment(.trailing) }
            .font(.system(size: 14)).padding(.vertical, 14)
    }
}

/** Add a TV channel (name + number) or an app (name + this TV's id for it). */
private struct AddSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let start: ShortcutKind
    @Environment(\.dismiss) private var dismiss
    @State private var kind: ShortcutKind = .channel
    @State private var name = ""
    @State private var value = ""
    @State private var pin = true

    var body: some View {
        let brand = vm.active?.brand
            VStack(alignment: .leading, spacing: 14) {
                Text("Add to Apps").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).frame(maxWidth: .infinity)
                Picker("Type", selection: $kind) { Text("TV channel").tag(ShortcutKind.channel); Text("App").tag(ShortcutKind.app) }
                    .pickerStyle(.segmented)
                field("Name", $name, kind == .channel ? "Star Sports" : "JioHotstar")
                if kind == .channel {
                    field("Channel number", $value, "101").keyboardType(.decimalPad)
                } else {
                    field("App ID on \(brand?.label ?? "your TV")", $value, {
                        switch brand { case .ROKU: return "12"; case .SAMSUNG: return "3201907018807"; case .LG: return "netflix"; case .ANDROID_TV: return "com.netflix.ninja"; case nil: return "" }
                    }())
                    Text(brand == nil ? "Choose a TV first: app ids are different on every kind of TV." : "Installed apps already show under On your TV (Roku, Samsung, LG).")
                        .font(.system(size: 13)).foregroundStyle(T.muted)
                }
                Toggle("Add to quick access", isOn: $pin).tint(accent).foregroundStyle(T.ink)
                WideButton(title: "Save", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !value.isEmpty && (kind == .channel || brand != nil)) {
                    vm.addShortcut(kind, name, value, pin: pin); dismiss()
                }
            }
            .padding(24).padding(.top, 8)
        .onAppear { kind = start }
        .fitSheet()
    }

    private func field(_ label: String, _ text: Binding<String>, _ placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.system(size: 13)).foregroundStyle(T.muted)
            TextField(placeholder, text: text).textInputAutocapitalization(.never).autocorrectionDisabled()
                .padding(.horizontal, 12).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
        }
    }
}
