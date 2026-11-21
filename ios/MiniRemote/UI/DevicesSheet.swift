import SwiftUI
import UIKit
import UniformTypeIdentifiers

enum Route: Hashable { case device(String), local, scan, manual, pair(String), settings, privacy, licenses, gestures }

/** The TV sheet: your TVs, adding and pairing a TV, and (behind the gear) Settings. */
struct DevicesSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Environment(\.dismiss) private var dismiss
    /** Pages to open straight away (screenshots). */
    var start: [Route] = []
    @State private var path: [Route] = []
    /** How tall each page's content is, so the sheet fits the page showing (and grows with it). */
    @State private var heights: [String: CGFloat] = [:]

    /** The bar with the title and buttons, the grabber above it, and room at the foot. */
    private let chrome: CGFloat = 96

    /** Fits the page showing; Settings (and the pages under it) are long, so they get the full height. */
    private var detent: PresentationDetent {
        if path.contains(.settings) { return .large }
        guard let h = heights[path.last.map { "\($0)" } ?? "root"] else { return .large }
        return .height(max(260, h + chrome))
    }

    var body: some View {
        NavigationStack(path: $path) {
            DevicesList(vm: vm, accent: accent, path: $path)
                .environment(\.pageKey, "root")
                .onAppear { if path.isEmpty && !start.isEmpty { path = start } }
                .navigationTitle("Choose a TV").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) { Button { path.append(.settings) } label: { Image(systemName: "gearshape") }.accessibilityLabel("Settings") }
                    ToolbarItem(placement: .navigationBarTrailing) { Button { dismiss() } label: { Image(systemName: "xmark") }.accessibilityLabel("Close") }
                }
                .navigationDestination(for: Route.self) { r in
                    Group {
                        switch r {
                        case .device(let id): DeviceDetail(vm: vm, accent: accent, id: id, path: $path)
                        case .local: LocalPage(vm: vm, path: $path)
                        case .scan: ScanPage(vm: vm, accent: accent, path: $path)
                        case .manual: ManualPage(vm: vm, path: $path)
                        case .pair(let id): PairPage(vm: vm, accent: accent, id: id)
                        case .settings: SettingsPage(vm: vm, accent: accent, path: $path)
                        case .privacy: PrivacyPage(vm: vm)
                        case .licenses: LicensesPage()
                        case .gestures: GesturesPage(accent: accent)
                        }
                    }
                    .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { dismiss() } label: { Image(systemName: "xmark") }.accessibilityLabel("Close") } }
                    .environment(\.pageKey, "\(r)")
                }
        }
        .environment(\.pageHeight) { key, h in if heights[key] != h { heights[key] = h } }
        .tint(T.ink)
        .background(T.sheet)
        .presentationBackground(T.sheet)
        .presentationDetents([detent])
        .animation(.spring(response: 0.35, dampingFraction: 0.9), value: detent)
        .presentationDragIndicator(.visible)
    }
}

private struct PageKey: EnvironmentKey { static let defaultValue = "" }
private struct PageHeight: EnvironmentKey { static let defaultValue: (String, CGFloat) -> Void = { _, _ in } }
extension EnvironmentValues {
    /** Which page of the TV sheet this is (its route). */
    fileprivate var pageKey: String { get { self[PageKey.self] } set { self[PageKey.self] = newValue } }
    /** Reports a page's content height to the TV sheet. */
    fileprivate var pageHeight: (String, CGFloat) -> Void { get { self[PageHeight.self] } set { self[PageHeight.self] = newValue } }
}

/**
 * A page of the TV sheet: comfortable margins, an opaque background (so the page sliding in
 * covers the one under it), and its height reported so the sheet fits it.
 */
private struct Page<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.pageKey) private var key
    @Environment(\.pageHeight) private var report
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 32)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { report(key, $0) }
        }
        .scrollBounceBehavior(.basedOnSize)
        .focusEffectDisabled()
        .background(T.sheet.ignoresSafeArea())
    }
}

private struct Illustration: View {
    let accent: Color
    var body: some View {
        ZStack {
            Circle().fill(accent.opacity(0.07)).frame(width: 150, height: 150)
            Circle().fill(accent.opacity(0.12)).frame(width: 112, height: 112)
            VStack(spacing: 2) {
                Image(systemName: "wifi").font(.system(size: 22, weight: .semibold)).foregroundStyle(T.ink)
                Image(systemName: "tv").font(.system(size: 40)).foregroundStyle(T.ink)
            }
        }
        .frame(maxWidth: .infinity).padding(.vertical, 18)
    }
}

private struct DevicesList: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var path: [Route]
    var body: some View {
        Page {
            if vm.devices.isEmpty {
                Illustration(accent: accent)
                Text("No TVs yet").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).frame(maxWidth: .infinity)
                Text("Add your TV to start. Your phone and TV need to be on the same Wi-Fi.").font(.system(size: 13))
                    .foregroundStyle(T.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 8).padding(.bottom, 10)
            }
            ForEach(vm.devices) { d in
                Button { path.append(.device(d.id)) } label: { DeviceRow(vm: vm, d: d) }.padding(.bottom, 12)
            }
            WideButton(title: "Add a TV") { path.append(vm.localSeen ? .scan : .local) }
        }
    }
}

private struct DeviceRow: View {
    @ObservedObject var vm: RemoteModel
    let d: Device
    var body: some View {
        let active = vm.activeId == d.id
        let status: (String, Color)? = !active ? nil : (vm.link == .ready ? ("Connected", T.ok) : (vm.link == .connecting ? ("Connecting…", T.warn) : ("Not connected", Color(hex: 0x8A8A8E))))
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(d.name).font(.system(size: 16, weight: .medium)).foregroundStyle(T.ink).lineLimit(1)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color(hex: 0xA8A8AD))
            }
            Text("\(d.brand.label) · \(d.host)").font(.system(size: 13)).foregroundStyle(T.muted)
            if let st = status {
                HStack(spacing: 7) { Circle().fill(st.1).frame(width: 8, height: 8); Text(st.0).font(.system(size: 13)).foregroundStyle(st.1 == T.ok ? T.ok : T.muted) }
                    .padding(.top, 2)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 16).fill(T.container))
    }
}

private struct DeviceDetail: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let id: String
    @Binding var path: [Route]
    @State private var name = ""
    @State private var arm = false
    var body: some View {
        if let d = vm.devices.first(where: { $0.id == id }) {
            let on = vm.activeId == d.id && vm.link == .ready
            Page {
                Card {
                    row("Brand", d.brand.label); RowDivider(); row("Address", d.host); RowDivider()
                    row("Status", on ? "Connected" : (vm.activeId == d.id && vm.link == .connecting ? "Connecting…" : "Not connected"))
                }
                .padding(.top, 8)
                Text("Name").font(.system(size: 13)).foregroundStyle(T.muted).padding(.top, 18).padding(.bottom, 8)
                TextField("Name", text: $name).padding(.horizontal, 12).frame(height: 52)
                    .background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
                if on { WideButton(title: "Disconnect") { vm.disconnect() } } else { WideButton(title: "Connect") { vm.select(d); path.append(.pair(d.id)) } }
                WideButton(title: "Save name", kind: .secondary, enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && name != d.name) { vm.rename(d.id, name); vm.say("Saved") }
                WideButton(title: arm ? "Tap again to forget this TV" : "Forget this TV", kind: .danger) {
                    if !arm { arm = true; vm.feel.fire(.heavy) } else { vm.remove(d); path.removeAll() }
                }
            }
            .navigationTitle(d.name).navigationBarTitleDisplayMode(.inline)
            .onAppear { name = d.name }
        }
    }
    private func row(_ k: String, _ v: String) -> some View {
        HStack { Text(k).foregroundStyle(T.muted); Spacer(); Text(v).foregroundStyle(T.ink) }.font(.system(size: 14)).padding(.vertical, 14)
    }
}

private struct LocalPage: View {
    @ObservedObject var vm: RemoteModel
    @Binding var path: [Route]
    var body: some View {
        Page {
            Illustration(accent: Color(hex: 0x6FB1FF))
            Text("Remote will look for TVs on your Wi-Fi. It only talks to devices on your own network, and it needs no account.")
                .font(.system(size: 14.5)).foregroundStyle(Color(hex: 0xE0E0E4)).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            WideButton(title: "Continue") { vm.markLocalSeen(); path.removeLast(); path.append(.scan) }
        }
        .navigationTitle("Local network access").navigationBarTitleDisplayMode(.inline)
    }
}

private struct ScanPage: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var path: [Route]
    var body: some View {
        Page {
            if vm.scanning {
                ProgressView().controlSize(.large).tint(accent).frame(maxWidth: .infinity).padding(.vertical, 30)
                Text("Looking for TVs on your Wi-Fi…").foregroundStyle(T.muted).frame(maxWidth: .infinity)
            } else {
                Text(vm.found.isEmpty ? "No new TVs found. Check that your phone and TV are on the same Wi-Fi, or enter the TV's address." : "Found \(vm.found.count) on your network.")
                    .font(.system(size: 15)).foregroundStyle(Color(hex: 0xE0E0E4)).padding(.bottom, 16)
                ForEach(vm.found) { d in
                    Button { vm.addAndSelect(d); path.append(.pair(d.id)) } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(d.name).font(.system(size: 15)).foregroundStyle(T.ink)
                                Text(d.host).font(.system(size: 12.5)).foregroundStyle(T.muted)
                            }
                            Spacer()
                            Text(d.brand.label).font(.system(size: 11.5)).foregroundStyle(Color(hex: 0xBBBBBB))
                                .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(Color(hex: 0x2C2C2E)))
                        }
                        .padding(16).background(RoundedRectangle(cornerRadius: 16).fill(T.container))
                    }
                    .padding(.bottom, 12)
                }
                WideButton(title: "Enter an IP address", kind: .secondary) { path.append(.manual) }
                if vm.found.isEmpty { WideButton(title: "Scan again", kind: .secondary) { vm.scan() } }
            }
        }
        .navigationTitle("Add a TV").navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.scan() }
    }
}

private struct ManualPage: View {
    @ObservedObject var vm: RemoteModel
    @Binding var path: [Route]
    @State private var ip = ""
    @State private var brand: Brand = .ROKU
    @State private var err: String?
    var body: some View {
        Page {
            Text("IP address").font(.system(size: 13)).foregroundStyle(T.muted).padding(.bottom, 8)
            TextField("192.168.1.20", text: $ip).keyboardType(.decimalPad).padding(.horizontal, 12).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
                .onChange(of: ip) { _, _ in err = nil }
            Text("Brand").font(.system(size: 13)).foregroundStyle(T.muted).padding(.top, 18).padding(.bottom, 8)
            Picker("Brand", selection: $brand) { ForEach(Brand.allCases) { Text($0.label).tag($0) } }.pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 6).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 12).fill(T.high))
            Text(err ?? " ").font(.system(size: 13)).foregroundStyle(Color(hex: 0xFF8A8A)).padding(.top, 8)
            WideButton(title: "Continue") {
                let (e, d) = vm.addManual(ip, brand)
                if let e { err = e; vm.feel.fire(.error) } else if let d { path.append(.pair(d.id)) }
            }
            Text("Find it on your TV under Settings › Network. Only home-network addresses (like 192.168…) are accepted.")
                .font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.top, 18)
        }
        .navigationTitle("Enter an address").navigationBarTitleDisplayMode(.inline)
    }
}

/** Connecting, the pairing code, connected or not; closes itself once the TV is ready. */
private struct PairPage: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let id: String
    @State private var code = ""
    @State private var left = 30
    @Environment(\.dismiss) private var dismissPage

    var body: some View {
        if let d = vm.devices.first(where: { $0.id == id }) {
            let active = vm.activeId == d.id
            Page {
                if let p = vm.pairPrompt {
                    Text("\(p.tvName) is showing a 6-character code. Type it here.").foregroundStyle(Color(hex: 0xE0E0E4)).padding(.bottom, 16)
                    TextField("A1B2C3", text: $code).font(.system(size: 26, design: .monospaced)).multilineTextAlignment(.center)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .onChange(of: code) { _, v in code = String(v.uppercased().filter { $0.isHexDigit }.prefix(6)) }
                        .padding(.horizontal, 12).frame(height: 60).background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
                    Text(p.error ?? " ").font(.system(size: 13)).foregroundStyle(Color(hex: 0xFF8A8A)).padding(.top, 10)
                    WideButton(title: "Pair", enabled: code.count == 6) { vm.answerCode(code); code = "" }
                } else if active && vm.link == .ready {
                    Illustration(accent: T.ok)
                    Text("\(d.name) is ready.").foregroundStyle(Color(hex: 0xE0E0E4)).frame(maxWidth: .infinity)
                } else if active && vm.link == .error {
                    Illustration(accent: Color(hex: 0xFF8A8A))
                    Text(vm.lastError ?? "The TV didn't answer.").foregroundStyle(Color(hex: 0xE0E0E4)).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    WideButton(title: "Try again") { vm.connect(d) }
                } else if active && vm.link == .connecting {
                    ProgressView().controlSize(.large).tint(accent).frame(maxWidth: .infinity).padding(.vertical, 30)
                    Text(d.brand == .SAMSUNG || d.brand == .LG ? "Choose Allow on the TV when it asks. Waiting… \(left)s"
                         : (d.brand == .ANDROID_TV ? "Connecting to \(d.name). It will show a 6-character code." : "Connecting to \(d.name)…"))
                        .foregroundStyle(Color(hex: 0xE0E0E4)).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                        .task { while left > 0 { try? await Task.sleep(nanoseconds: 1_000_000_000); left -= 1 } }
                } else {
                    Illustration(accent: Color(hex: 0xB69CFF))
                    WideButton(title: "Connect") { vm.select(d) }
                }
            }
            .navigationTitle(vm.pairPrompt != nil ? "Enter the code" : (active && vm.link == .ready ? "Connected" : "Connect \(shortName(d.name, d.brand.label))"))
            .navigationBarTitleDisplayMode(.inline)
            .onDisappear { if vm.pairPrompt != nil { vm.answerCode(nil) } }
        }
    }
}

/** A settings icon on its own rounded square (one calm colour for all). */
struct IconTile: View {
    let symbol: String
    var body: some View {
        Image(systemName: symbol).font(.system(size: 15, weight: .medium)).foregroundStyle(T.ink)
            .frame(width: 30, height: 30).background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(T.highest))
    }
}

/**
 * Settings in the iPhone's own style: a profile row, then grouped rows with an icon, what's set
 * on the right and a chevron. Rows with more to them open a sheet; switches flip in place.
 */
private struct SettingsPage: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var path: [Route]
    @State private var open: String?

    private func set(_ change: (inout Settings) -> Void) { var s = vm.settings; change(&s); vm.update(s) }
    private func b(_ get: @escaping (Settings) -> Bool, _ put: @escaping (inout Settings, Bool) -> Void) -> Binding<Bool> {
        Binding(get: { get(vm.settings) }, set: { v in set { put(&$0, v) } })
    }

    private func row(_ sym: String, _ title: String, _ value: String? = nil, _ action: @escaping () -> Void) -> some View {
        SRow(symbol: sym, title: title, value: value, action: action)
    }

    var body: some View {
        let s = vm.settings
        let apps = vm.shortcuts.filter(\.pinned).count
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        let feel = [s.haptics ? "Haptics" : nil, s.sound ? "Sound" : nil].compactMap { $0 }.joined(separator: ", ")
        ScrollView {
            VStack(spacing: 14) {
                SCard {
                    Button { open = "profile" } label: {
                        HStack(spacing: 14) {
                            Text(initialsOf(s.profileName)).font(.system(size: 19, weight: .semibold)).foregroundStyle(.white)
                                .frame(width: 52, height: 52).background(Circle().fill(accent))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(s.profileName.isEmpty ? "Add your name" : s.profileName).font(.system(size: 17, weight: .medium)).foregroundStyle(s.profileName.isEmpty ? T.sub : T.ink)
                                Text(s.profileEmail.isEmpty ? "\(vm.devices.count) TV\(vm.devices.count == 1 ? "" : "s") · \(apps) app\(apps == 1 ? "" : "s")" : s.profileEmail)
                                    .font(.system(size: 13)).foregroundStyle(T.sub)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(T.sub)
                        }
                        .padding(16).contentShape(Rectangle())
                    }
                    .buttonStyle(Pressable())
                }
                SHead(text: "Remote")
                SCard {
                    row(s.controlStyle == "trackpad" ? "hand.draw" : "circle.circle", "Control", s.controlStyle == "trackpad" ? "Trackpad" : "Dial") { open = "control" }
                    SLine()
                    row("square.grid.2x2", "Quick access", s.appsIn == "more" ? "More sheet" : "Home page") { open = "apps" }
                    SLine()
                    row("hand.tap", "Gestures") { path.append(.gestures) }
                }
                SHead(text: "TV")
                SCard {
                    row("tv", "TV controls", "\([s.autoKeyboard, s.autoClose].filter { $0 }.count) of 2 on") { open = "tv" }
                    SLine()
                    row("speaker.wave.2", "Touch & sound", feel.isEmpty ? "Off" : feel) { open = "feel" }
                }
                SHead(text: "Look")
                SCard {
                    row("paintpalette", "Appearance", "\(Palettes.of(s.theme).label) · \(ACCENTS.first { $0.1.caseInsensitiveCompare(s.accentHex) == .orderedSame }?.0 ?? "Custom")") { open = "look" }
                    SLine()
                    SToggle(symbol: "sun.max", title: "Keep screen on", sub: "While the remote is open", accent: accent, on: b({ $0.keepAwake }, { $0.keepAwake = $1 }))
                }
                SHead(text: "Data & help")
                SCard {
                    row("externaldrive", "Backup & restore") { open = "backup" }
                    SLine()
                    row("hand.raised", "Privacy & data") { path.append(.privacy) }
                    SLine()
                    row("questionmark.bubble", "Help & feedback") { open = "support" }
                    SLine()
                    row("info.circle", "About", version) { open = "about" }
                }
                Label("No account · nothing leaves your Wi-Fi", systemImage: "lock.shield")
                    .font(.system(size: 14, weight: .medium)).foregroundStyle(T.sub).padding(.top, 18)
            }
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 32)
        }
        .background(T.sheet.ignoresSafeArea())
        .focusEffectDisabled()
        .sheetBackdrop(open != nil)
        .navigationTitle("Settings").navigationBarTitleDisplayMode(.inline)
        .sheet(item: Binding(get: { open.map { Opened(id: $0) } }, set: { open = $0?.id })) { o in
            SettingsSheets(vm: vm, accent: accent, which: o.id, openLicenses: { open = nil; path.append(.licenses) })
        }
    }
    private struct Opened: Identifiable { let id: String }
}

func initialsOf(_ name: String) -> String {
    let parts = name.split(separator: " ").prefix(2)
    let s = parts.compactMap { $0.first }.map(String.init).joined().uppercased()
    return s.isEmpty ? "?" : s
}

/** The sheets behind the settings rows: each a short list with its title on the left. */
private struct SettingsSheets: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let which: String
    let openLicenses: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var exporting = false
    @State private var importing = false

    private func set(_ change: (inout Settings) -> Void) { var s = vm.settings; change(&s); vm.update(s) }
    @Environment(\.openURL) private var openURL

    /** An email to SUPPORT_EMAIL when set, else a new GitHub issue, with a few details filled in. */
    private func contact(_ kind: String) {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        let details = "\n\n---\nMini Remote \(v) · iOS \(UIDevice.current.systemVersion) · \(UIDevice.current.model)" + (vm.active.map { " · TV: \($0.brand.label)" } ?? "")
        let body = (kind == "Bug report" ? "What happened:\n\nWhat you expected:\n\nSteps to see it again:\n" : kind == "Feature request" ? "What you'd like:\n\nWhy it would help:\n" : "") + details
        func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "" }
        let link = SUPPORT_EMAIL.isEmpty ? "\(SUPPORT_ISSUES)?title=\(enc(kind + ": "))&body=\(enc(body))"
            : "mailto:\(SUPPORT_EMAIL)?subject=\(enc("Mini Remote: " + kind))&body=\(enc(body))"
        if let u = URL(string: link) { openURL(u) }
    }

    private func choice(_ sym: String, _ title: String, _ sub: String, _ sel: Bool, _ pick: @escaping () -> Void) -> some View {
        SRow(symbol: sym, title: title, sub: sub, tile: sel ? accent : nil, trailing: sel ? "checkmark" : nil, trailingTint: accent, action: pick)
    }

    private func label(_ t: String) -> some View {
        Text(t.uppercased()).font(.system(size: 12, weight: .semibold)).tracking(0.6).foregroundStyle(T.sub)
            .padding(.leading, 6).padding(.top, 8)
    }
    private func note(_ t: String) -> some View {
        Text(t).font(.system(size: 12.5)).foregroundStyle(T.sub).padding(.horizontal, 6).fixedSize(horizontal: false, vertical: true)
    }
    private func field(_ placeholder: String, _ text: Binding<String>) -> some View {
        TextField(placeholder, text: text).font(.system(size: 16)).foregroundStyle(T.ink)
            .padding(.horizontal, 16).frame(height: 54)
    }
    private func bind(_ get: @escaping (Settings) -> Bool, _ put: @escaping (inout Settings, Bool) -> Void) -> Binding<Bool> {
        Binding(get: { get(vm.settings) }, set: { v in set { put(&$0, v) } })
    }

    var body: some View {
        let s = vm.settings
        VStack(alignment: .leading, spacing: 12) {
            switch which {
            case "profile":
                SheetTitle(title: "Profile", sub: "Stays on this iPhone and in backups.").padding(.top, 28)
                SCard {
                    field("Your name", $name).textContentType(.name)
                    SLine(inset: 16)
                    field("Email (optional)", $email).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                WideButton(title: "Save", enabled: email.isEmpty || (email.contains("@") && email.contains("."))) {
                    set { $0.profileName = String(name.trimmingCharacters(in: .whitespaces).prefix(30)); $0.profileEmail = email.trimmingCharacters(in: .whitespaces) }
                    vm.say("Profile saved"); dismiss()
                }
            case "control":
                SheetTitle(title: "Control", sub: "What the middle of the remote is.").padding(.top, 28)
                SCard {
                    choice("circle.circle", "Dial", "Ring with arrows, OK in the middle; turn the ring for volume", s.controlStyle == "dial") { set { $0.controlStyle = "dial" } }
                    SLine()
                    choice("hand.draw", "Trackpad", "Swipe to move, tap to select", s.controlStyle == "trackpad") { set { $0.controlStyle = "trackpad" } }
                }
            case "apps":
                SheetTitle(title: "Quick access", sub: "Where your apps sit.").padding(.top, 28)
                SCard {
                    choice("house", "Home page", "Two pages of circles: swipe across", s.appsIn != "more") { set { $0.appsIn = "home" } }
                    SLine()
                    choice("ellipsis", "More sheet", "At the top of More", s.appsIn == "more") { set { $0.appsIn = "more" } }
                }
            case "tv":
                SheetTitle(title: "TV controls", sub: "How the iPhone works with the TV.").padding(.top, 28)
                SCard {
                    SToggle(symbol: "keyboard", title: "Auto keyboard", sub: "When the TV asks for text", accent: accent, on: bind({ $0.autoKeyboard }, { $0.autoKeyboard = $1 }))
                    SLine()
                    SToggle(symbol: "timer", title: "Close idle TV menus", sub: "Back after 10 s without use", accent: accent, on: bind({ $0.autoClose }, { $0.autoClose = $1 }))
                }
            case "feel":
                SheetTitle(title: "Touch & sound", sub: "How presses feel and sound.").padding(.top, 28)
                SCard {
                    SToggle(symbol: "iphone.radiowaves.left.and.right", title: "Haptics", accent: accent, on: bind({ $0.haptics }, { $0.haptics = $1 }))
                    if s.haptics {
                        Picker("Strength", selection: Binding(get: { vm.settings.hapticStrength }, set: { v in set { $0.hapticStrength = v } })) {
                            ForEach(0..<HAPTIC_STRENGTHS.count, id: \.self) { i in Text(HAPTIC_STRENGTHS[i].1).tag(HAPTIC_STRENGTHS[i].0) }
                        }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.bottom, 14)
                    }
                    SLine()
                    SToggle(symbol: "speaker.wave.2", title: "Sound", accent: accent, on: bind({ $0.sound }, { $0.sound = $1 }))
                    if s.sound {
                        Picker("Level", selection: Binding(get: { vm.settings.soundLevel }, set: { v in set { $0.soundLevel = v }; vm.feel.fire(.press) })) {
                            ForEach(0..<SOUND_LEVELS.count, id: \.self) { i in Text(SOUND_LEVELS[i].1).tag(SOUND_LEVELS[i].0) }
                        }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.bottom, 14)
                    }
                }
                SCard {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack { Text("Scrub step").font(.system(size: 16)).foregroundStyle(T.ink); Spacer(); Text("\(Int(s.scrubStep)) pt").foregroundStyle(accent) }
                        Text("Distance per step when you swipe. Shorter is faster.").font(.system(size: 12.5)).foregroundStyle(T.sub)
                        Slider(value: Binding(get: { vm.settings.scrubStep }, set: { v in set { $0.scrubStep = v.rounded() } }), in: 24...60, step: 2).tint(accent)
                    }
                    .padding(16)
                }
            case "look":
                SheetTitle(title: "Appearance", sub: "Theme, colour and depth.").padding(.top, 28)
                SCard {
                    SToggle(symbol: "square.3.layers.3d", title: "Depth", sub: "Raised keys, dial and trackpad, with light and shadow", accent: accent, on: bind({ $0.depth }, { $0.depth = $1 }))
                }
                SHead(text: "Theme")
                HStack(spacing: 10) {
                    ForEach(Palettes.all, id: \.key) { p in
                        let sel = s.theme == p.key
                        Button { set { $0.theme = p.key } } label: {
                            VStack(spacing: 8) {
                                VStack(spacing: 6) {
                                    Circle().fill(Color(hex: p.high)).frame(width: 30, height: 30).overlay(Circle().fill(Color(hex: p.highest)).frame(width: 12, height: 12))
                                    HStack(spacing: 4) { ForEach(0..<3, id: \.self) { _ in Circle().fill(Color(hex: p.high)).frame(width: 8, height: 8) } }
                                    Capsule().fill(accent).frame(width: 20, height: 3)
                                }
                                .frame(maxWidth: .infinity).frame(height: 84)
                                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(p.key == "auto"
                                    ? AnyShapeStyle(LinearGradient(stops: [.init(color: Color(hex: Palettes.light.screen), location: 0.5), .init(color: Color(hex: Palettes.dark.screen), location: 0.5)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    : AnyShapeStyle(Color(hex: p.screen))))
                                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(sel ? accent : Color(hex: p.line), lineWidth: sel ? 2 : 1))
                                Text(p.label).font(.system(size: 12.5, weight: sel ? .semibold : .regular)).foregroundStyle(sel ? T.ink : T.sub)
                            }
                        }
                        .buttonStyle(Pressable())
                        .accessibilityLabel("\(p.label) theme").accessibilityAddTraits(sel ? .isSelected : [])
                    }
                }
                SHead(text: "Accent colour")
                SCard {
                    HStack {
                        ForEach(0..<ACCENTS.count, id: \.self) { i in
                            let hex = ACCENTS[i].1
                            let sel = s.accentHex.caseInsensitiveCompare(hex) == .orderedSame
                            Circle().fill(Color(hexString: hex)).frame(width: 30, height: 30)
                                .overlay(Circle().stroke(T.ink, lineWidth: sel ? 2 : 0).padding(-5))
                                .frame(maxWidth: .infinity)
                                .onTapGesture { set { $0.accentHex = hex } }
                                .accessibilityLabel(ACCENTS[i].0).accessibilityAddTraits(sel ? [.isSelected, .isButton] : .isButton)
                        }
                    }
                    .padding(.vertical, 18).padding(.horizontal, 8)
                }
            case "support":
                SheetTitle(title: "Help & feedback", sub: "Ideas, problems or questions: they go straight to the developer.").padding(.top, 28)
                SCard {
                    SRow(symbol: "lightbulb", title: "Request a feature") { contact("Feature request") }
                    SLine()
                    SRow(symbol: "ladybug", title: "Report a bug") { contact("Bug report") }
                    SLine()
                    SRow(symbol: "questionmark.circle", title: "Ask a question") { contact("Question") }
                }
                SNote(text: "Your app version, iPhone and TV type are added so it's easier to help. Nothing else is sent.")
                SCard {
                    SRow(symbol: "hand.point.up.left", title: "Take the tour") { set { $0.toured = false }; dismiss() }
                    SLine()
                    SRow(symbol: "sparkles", title: "Show the welcome again") { set { $0.onboarded = false; $0.toured = false }; dismiss() }
                }
            case "backup":
                SheetTitle(title: "Backup & restore", sub: "Settings, quick access and channels in one file. TVs pair again on a new iPhone.").padding(.top, 28)
                SCard {
                    SRow(symbol: "square.and.arrow.up", title: "Back up", sub: "Save a file to Files or share it") { exporting = true }
                    SLine()
                    SRow(symbol: "square.and.arrow.down", title: "Restore", sub: "From a backup file") { importing = true }
                }
            default:
                SheetTitle(title: "Mini Remote", sub: "Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev")").padding(.top, 28)
                SCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("No account, no ads, no analytics or tracking.").font(.system(size: 16)).foregroundStyle(T.ink)
                        Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names and logos shown are trademarks of their owners. Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' remote-control interfaces on your own network.")
                            .font(.system(size: 12.5)).foregroundStyle(T.sub).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    SLine(inset: 16)
                    SRow(symbol: "doc.text", title: "Open-source licences") { openLicenses() }
                }
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 32)
        .tint(T.ink)
        .fitSheet()
        .onAppear { name = vm.settings.profileName; email = vm.settings.profileEmail }
        .fileExporter(isPresented: $exporting, document: ProfileFile(data: vm.exportProfile()), contentType: .json,
                      defaultFilename: "Mini Remote\(vm.settings.profileName.isEmpty ? "" : " - " + vm.settings.profileName)") { r in
            if case .success = r { vm.say("Backup saved") }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url), d.count < 400_000 { vm.importProfile(d) } else { vm.say("Couldn't read that file") }
        }
    }
}

/** A backup as a document for the Files app. */
struct ProfileFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

private struct PrivacyPage: View {
    @ObservedObject var vm: RemoteModel
    @State private var ask: String?
    var body: some View {
        Page {
            Card {
                InfoBlock("Stored on this phone", "Your TVs' names and addresses and their pairing keys, in the Keychain (this device only, never synced or backed up), plus quick access, channels and settings."); RowDivider()
                InfoBlock("Network", "Controls only TVs on your home Wi-Fi; public addresses are refused. Nothing else goes online."); RowDivider()
                InfoBlock("Voice", "The mic listens only while Voice mode is on in Search. Speech becomes text on this iPhone where possible (otherwise through Apple's speech service); only the text goes to your TV. No audio is saved."); RowDivider()
                InfoBlock("Permissions", "Local network (find and control TVs), microphone and speech recognition (Voice mode, while listening). No location, contacts or photos.")
            }
            .padding(.top, 8)
            WideButton(title: "Forget all TVs", kind: .secondary) { ask = "tvs" }
            WideButton(title: "Erase all data", kind: .danger) { ask = "all" }
        }
        .sheetBackdrop(ask != nil)
        .navigationTitle("Privacy & data").navigationBarTitleDisplayMode(.inline)
        .sheet(item: Binding(get: { ask.map { Ask(id: $0) } }, set: { ask = $0?.id })) { a in
            if a.id == "tvs" {
                ConfirmSheet(title: "Forget all TVs?", text: "Removes every TV and its pairing keys. You'll pair each TV again to use it.", action: "Forget all TVs") { vm.forgetAllTvs() }
            } else {
                ConfirmSheet(title: "Erase all data?", text: "Removes TVs, pairing keys, quick access, channels and settings.", action: "Erase everything") { vm.eraseAll() }
            }
        }
    }
    private struct Ask: Identifiable { let id: String }
}

private struct LicensesPage: View {
    var body: some View {
        Page {
            Card {
                InfoBlock("Apple frameworks", "SwiftUI, UIKit, Network, Security, CryptoKit: part of iOS.")
            }
            .padding(.top, 8)
            Text("The iOS app uses no third-party code, fonts or images. App logos are not bundled.").font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.top, 10)
        }
        .navigationTitle("Open-source licences").navigationBarTitleDisplayMode(.inline)
    }
}

/** Where Help & feedback goes: an email address (when set), else new issues on GitHub. */
let SUPPORT_EMAIL = "iamyourdev123@gmail.com"
let SUPPORT_ISSUES = "https://github.com/krishnaar21/mini-remote/issues/new"
