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

    var body: some View {
        NavigationStack(path: $path) {
            DevicesList(vm: vm, accent: accent, path: $path)
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
                }
        }
        .tint(T.ink)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

private struct Page<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 0) { content() }.padding(.horizontal, 24).padding(.bottom, 32) }
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
                ProgressView().tint(accent).frame(maxWidth: .infinity).padding(.vertical, 30)
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
                .onChange(of: ip) { _ in err = nil }
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
                        .onChange(of: code) { v in code = String(v.uppercased().filter { $0.isHexDigit }.prefix(6)) }
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
                    ProgressView().tint(accent).frame(maxWidth: .infinity).padding(.vertical, 30)
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
        Button(action: action) {
            HStack(spacing: 14) {
                IconTile(symbol: sym)
                Text(title).foregroundStyle(T.ink)
                Spacer()
                if let value { Text(value).foregroundStyle(T.sub).lineLimit(1) }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(T.sub)
            }
        }
    }

    private func toggle(_ sym: String, _ title: String, _ sub: String, _ on: Binding<Bool>) -> some View {
        Toggle(isOn: on) {
            HStack(spacing: 14) {
                IconTile(symbol: sym)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).foregroundStyle(T.ink)
                    Text(sub).font(.system(size: 12.5)).foregroundStyle(T.sub)
                }
            }
        }
        .tint(accent)
    }

    var body: some View {
        let s = vm.settings
        let apps = vm.shortcuts.filter(\.pinned).count
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        List {
            Section {
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
                    .padding(.vertical, 4)
                }
            }
            Section {
                row(s.controlStyle == "trackpad" ? "hand.draw" : "circle.circle", "Control", s.controlStyle == "trackpad" ? "Trackpad" : "Dial") { open = "control" }
                row("square.grid.2x2", "Quick access", s.appsIn == "more" ? "More sheet" : "Home page") { open = "apps" }
                row("hand.tap", "Gestures") { path.append(.gestures) }
            }
            Section {
                row("tv", "TV controls", "\([s.autoKeyboard, s.autoClose].filter { $0 }.count) of 2 on") { open = "tv" }
                row("speaker.wave.2", "Touch & sound", [s.haptics ? "Haptics" : nil, s.sound ? "Sound" : nil].compactMap { $0 }.joined(separator: ", ").isEmpty ? "Off" : [s.haptics ? "Haptics" : nil, s.sound ? "Sound" : nil].compactMap { $0 }.joined(separator: ", ")) { open = "feel" }
            }
            Section {
                row("paintpalette", "Appearance", "\(Palettes.of(s.theme).label) · \(ACCENTS.first { $0.1.caseInsensitiveCompare(s.accentHex) == .orderedSame }?.0 ?? "Custom")") { open = "look" }
                toggle("sun.max", "Keep screen on", "While the remote is open", b({ $0.keepAwake }, { $0.keepAwake = $1 }))
            }
            Section {
                row("externaldrive", "Backup & restore") { open = "backup" }
                row("hand.raised", "Privacy & data") { path.append(.privacy) }
                row("questionmark.bubble", "Help & feedback") { open = "support" }
                row("info.circle", "About", version) { open = "about" }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(T.sheet)
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
        Button(action: pick) {
            HStack(spacing: 14) {
                Image(systemName: sym).font(.system(size: 15, weight: .medium)).foregroundStyle(.white)
                    .frame(width: 30, height: 30).background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(accent))
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).foregroundStyle(T.ink)
                    Text(sub).font(.system(size: 12.5)).foregroundStyle(T.sub)
                }
                Spacer()
                if sel { Image(systemName: "checkmark").font(.system(size: 15, weight: .semibold)).foregroundStyle(accent) }
            }
        }
    }

    private func header(_ title: String, _ sub: String) -> some View {
        SheetTitle(title: title, sub: sub).listRowInsets(EdgeInsets(top: 8, leading: 4, bottom: 8, trailing: 4)).listRowBackground(Color.clear)
    }

    var body: some View {
        let s = vm.settings
        List {
            switch which {
            case "profile":
                Section { header("Profile", "Stays on this iPhone and in backups.") }
                Section {
                    TextField("Your name", text: $name).textContentType(.name).foregroundStyle(T.ink)
                    TextField("Email (optional)", text: $email).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(T.ink)
                }
                Section {
                    Button("Save") {
                        set { $0.profileName = String(name.trimmingCharacters(in: .whitespaces).prefix(30)); $0.profileEmail = email.trimmingCharacters(in: .whitespaces) }
                        vm.say("Profile saved"); dismiss()
                    }
                    .disabled(!email.isEmpty && !(email.contains("@") && email.contains(".")))
                }
            case "control":
                Section { header("Control", "What the middle of the remote is.") }
                Section {
                    choice("circle.circle", "Dial", "Ring with arrows, OK in the middle; turn the ring for volume", s.controlStyle == "dial") { set { $0.controlStyle = "dial" } }
                    choice("hand.draw", "Trackpad", "Swipe to move, tap to select", s.controlStyle == "trackpad") { set { $0.controlStyle = "trackpad" } }
                }
            case "apps":
                Section { header("Quick access", "Where your apps sit.") }
                Section {
                    choice("house", "Home page", "Two pages of circles: swipe across", s.appsIn != "more") { set { $0.appsIn = "home" } }
                    choice("ellipsis", "More sheet", "At the top of More", s.appsIn == "more") { set { $0.appsIn = "more" } }
                }
            case "tv":
                Section { header("TV controls", "How the iPhone works with the TV.") }
                Section {
                    Toggle(isOn: Binding(get: { vm.settings.autoKeyboard }, set: { v in set { $0.autoKeyboard = v } })) {
                        VStack(alignment: .leading, spacing: 0) { Text("Auto keyboard").foregroundStyle(T.ink); Text("When the TV asks for text").font(.system(size: 12.5)).foregroundStyle(T.sub) }
                    }.tint(accent)
                    Toggle(isOn: Binding(get: { vm.settings.autoClose }, set: { v in set { $0.autoClose = v } })) {
                        VStack(alignment: .leading, spacing: 0) { Text("Close idle TV menus").foregroundStyle(T.ink); Text("Back after 10 s without use").font(.system(size: 12.5)).foregroundStyle(T.sub) }
                    }.tint(accent)
                }
            case "feel":
                Section { header("Touch & sound", "How presses feel and sound.") }
                Section {
                    Toggle("Haptics", isOn: Binding(get: { vm.settings.haptics }, set: { v in set { $0.haptics = v } })).tint(accent)
                    if s.haptics {
                        Picker("Strength", selection: Binding(get: { vm.settings.hapticStrength }, set: { v in set { $0.hapticStrength = v } })) {
                            ForEach(0..<HAPTIC_STRENGTHS.count, id: \.self) { i in Text(HAPTIC_STRENGTHS[i].1).tag(HAPTIC_STRENGTHS[i].0) }
                        }.pickerStyle(.segmented)
                    }
                    Toggle("Sound", isOn: Binding(get: { vm.settings.sound }, set: { v in set { $0.sound = v } })).tint(accent)
                    if s.sound {
                        Picker("Level", selection: Binding(get: { vm.settings.soundLevel }, set: { v in set { $0.soundLevel = v }; vm.feel.fire(.press) })) {
                            ForEach(0..<SOUND_LEVELS.count, id: \.self) { i in Text(SOUND_LEVELS[i].1).tag(SOUND_LEVELS[i].0) }
                        }.pickerStyle(.segmented)
                    }
                }
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack { Text("Scrub step").foregroundStyle(T.ink); Spacer(); Text("\(Int(s.scrubStep)) pt").foregroundStyle(accent) }
                        Text("Distance per step when you swipe. Shorter is faster.").font(.system(size: 12.5)).foregroundStyle(T.sub)
                        Slider(value: Binding(get: { vm.settings.scrubStep }, set: { v in set { $0.scrubStep = v.rounded() } }), in: 24...60, step: 2).tint(accent)
                    }
                }
            case "look":
                Section { header("Appearance", "Theme, colour and depth.") }
                Section {
                    Toggle(isOn: Binding(get: { vm.settings.depth }, set: { v in set { $0.depth = v } })) {
                        VStack(alignment: .leading, spacing: 0) { Text("Depth").foregroundStyle(T.ink); Text("Raised keys, dial and trackpad, with light and shadow").font(.system(size: 12.5)).foregroundStyle(T.sub) }
                    }.tint(accent)
                }
                Section("Theme") {
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
                                    .frame(maxWidth: .infinity).frame(height: 80)
                                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(p.key == "auto"
                                        ? AnyShapeStyle(LinearGradient(stops: [.init(color: Color(hex: Palettes.light.screen), location: 0.5), .init(color: Color(hex: Palettes.dark.screen), location: 0.5)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                        : AnyShapeStyle(Color(hex: p.screen))))
                                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(sel ? accent : Color(hex: p.line), lineWidth: sel ? 2 : 1))
                                    Text(p.label).font(.system(size: 12.5)).foregroundStyle(sel ? T.ink : T.sub)
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(p.label) theme").accessibilityAddTraits(sel ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 6)
                }
                Section("Accent colour") {
                    HStack {
                        ForEach(0..<ACCENTS.count, id: \.self) { i in
                            let hex = ACCENTS[i].1
                            let sel = s.accentHex.caseInsensitiveCompare(hex) == .orderedSame
                            Circle().fill(Color(hexString: hex)).frame(width: 32, height: 32)
                                .overlay(Circle().stroke(T.ink, lineWidth: sel ? 2 : 0).padding(-5))
                                .frame(maxWidth: .infinity)
                                .onTapGesture { set { $0.accentHex = hex } }
                                .accessibilityLabel(ACCENTS[i].0).accessibilityAddTraits(sel ? [.isSelected, .isButton] : .isButton)
                        }
                    }
                    .padding(.vertical, 8)
                }
            case "support":
                Section { header("Help & feedback", "Ideas, problems or questions: they go straight to the developer.") }
                Section {
                    Button { contact("Feature request") } label: { Label("Request a feature", systemImage: "lightbulb") }
                    Button { contact("Bug report") } label: { Label("Report a bug", systemImage: "ladybug") }
                    Button { contact("Question") } label: { Label("Ask a question", systemImage: "questionmark.circle") }
                } footer: {
                    Text("Your app version, iPhone and TV type are added so it's easier to help. Nothing else is sent.")
                }
                Section {
                    Button { set { $0.toured = false }; dismiss() } label: { Label("Take the tour", systemImage: "hand.point.up.left") }
                    Button { set { $0.onboarded = false; $0.toured = false }; dismiss() } label: { Label("Show the welcome again", systemImage: "sparkles") }
                }
            case "backup":
                Section { header("Backup & restore", "Settings, quick access and channels in one file. TVs pair again on a new iPhone.") }
                Section {
                    Button { exporting = true } label: { Label("Back up", systemImage: "square.and.arrow.up") }
                    Button { importing = true } label: { Label("Restore", systemImage: "square.and.arrow.down") }
                }
            default:
                Section { header("Mini Remote", "Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev")") }
                Section {
                    Text("No account, no ads, no analytics or tracking.").foregroundStyle(T.ink)
                    Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names and logos shown are trademarks of their owners. Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' remote-control interfaces on your own network.")
                        .font(.system(size: 12.5)).foregroundStyle(T.sub)
                    Button { openLicenses() } label: { Label("Open-source licences", systemImage: "doc.text") }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(T.sheet)
        .tint(T.ink)
        .presentationDetents(which == "look" || which == "feel" ? [.large] : [.medium, .large])
        .presentationDragIndicator(.visible)
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
