import SwiftUI

enum Route: Hashable { case device(String), local, scan, manual, pair(String), settings, privacy, licenses }

/** The TV sheet: your TVs, adding and pairing a TV, and (behind the gear) Settings. */
struct DevicesSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Environment(\.dismiss) private var dismiss
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            DevicesList(vm: vm, accent: accent, path: $path)
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
                        }
                    }
                    .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { dismiss() } label: { Image(systemName: "xmark") }.accessibilityLabel("Close") } }
                }
        }
        .tint(T.ink)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(T.sheet)
    }
}

private struct Page<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 0) { content() }.padding(.horizontal, 24).padding(.bottom, 32) }
            .background(T.sheet)
    }
}

private struct Illustration: View {
    let accent: Color
    var body: some View {
        ZStack {
            Circle().fill(accent.opacity(0.07)).frame(width: 150, height: 150)
            Circle().fill(accent.opacity(0.12)).frame(width: 112, height: 112)
            VStack(spacing: 2) {
                Image(systemName: "wifi").font(.system(size: 22, weight: .semibold)).foregroundStyle(accent)
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

/** Settings, grouped into cards. */
private struct SettingsPage: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var path: [Route]

    private func set(_ change: (inout Settings) -> Void) { var s = vm.settings; change(&s); vm.update(s) }
    private func b(_ get: @escaping (Settings) -> Bool, _ put: @escaping (inout Settings, Bool) -> Void) -> Binding<Bool> {
        Binding(get: { get(vm.settings) }, set: { v in set { put(&$0, v) } })
    }

    var body: some View {
        Page {
            SectionTitle(text: "Remote")
            Card {
                HStack {
                    Text("Control").foregroundStyle(T.ink)
                    Spacer()
                    Picker("Control", selection: Binding(get: { vm.settings.controlStyle }, set: { v in set { $0.controlStyle = v } })) {
                        Text("Dial").tag("dial"); Text("Trackpad").tag("trackpad")
                    }.pickerStyle(.segmented).frame(width: 190)
                }
                .padding(.vertical, 14)
            }
            Text("One finger navigates; two fingers slide for volume (up or down) and channels (sideways). Double tap OK to mute; hold it for voice. Two-finger double tap switches dial and trackpad.")
                .font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.top, 8)

            SectionTitle(text: "Accent colour")
            Card {
                HStack {
                    ForEach(0..<ACCENTS.count, id: \.self) { i in
                        let name = ACCENTS[i].0, hex = ACCENTS[i].1
                        let sel = vm.settings.accentHex.caseInsensitiveCompare(hex) == .orderedSame
                        Circle().fill(Color(hexString: hex)).frame(width: 40, height: 40)
                            .overlay(Circle().stroke(Color.white, lineWidth: sel ? 2 : 0).padding(-5))
                            .frame(maxWidth: .infinity)
                            .onTapGesture { set { $0.accentHex = hex } }
                            .accessibilityLabel(name).accessibilityAddTraits(sel ? [.isSelected, .isButton] : .isButton)
                    }
                }
                .padding(.vertical, 18)
            }

            SectionTitle(text: "Haptics")
            Card {
                ToggleRow(title: "Haptics", sub: "Feedback on every press and scrub", on: b({ $0.haptics }, { $0.haptics = $1 }), accent: accent)
                RowDivider()
                segRow("Strength", HAPTIC_STRENGTHS, vm.settings.hapticStrength) { v in set { $0.hapticStrength = v } }
            }
            SectionTitle(text: "Sound")
            Card {
                ToggleRow(title: "Sound", sub: "Soft click with every press and scrub", on: b({ $0.sound }, { $0.sound = $1 }), accent: accent)
                RowDivider()
                segRow("Sound level", SOUND_LEVELS, vm.settings.soundLevel) { v in set { $0.soundLevel = v }; vm.feel.fire(.press) }
            }
            SectionTitle(text: "Scrolling")
            Card {
                VStack(alignment: .leading, spacing: 6) {
                    HStack { Text("Scrub distance").foregroundStyle(T.ink); Spacer(); Text("\(Int(vm.settings.scrubStep)) pt").foregroundStyle(accent) }
                    Text("Shorter = faster scrolling").font(.system(size: 12.5)).foregroundStyle(T.muted)
                    Slider(value: Binding(get: { vm.settings.scrubStep }, set: { v in set { $0.scrubStep = v.rounded() } }), in: 24...60, step: 2).tint(accent)
                    HStack { Text("Fast"); Spacer(); Text("Slow") }.font(.system(size: 12)).foregroundStyle(T.muted)
                }
                .padding(.vertical, 14)
            }
            SectionTitle(text: "Screen")
            Card { ToggleRow(title: "Keep screen on", sub: "While the remote is open", on: b({ $0.keepAwake }, { $0.keepAwake = $1 }), accent: accent) }

            SectionTitle(text: "Privacy & data")
            Card {
                Button { path.append(.privacy) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Privacy & data").foregroundStyle(T.ink)
                            Text("What's stored, what goes online, erase data").font(.system(size: 12.5)).foregroundStyle(T.muted)
                        }
                        Spacer(); Image(systemName: "chevron.right").foregroundStyle(Color(hex: 0xA8A8AD))
                    }
                    .padding(.vertical, 14)
                }
            }
            SectionTitle(text: "About")
            Card {
                Text("Mini Remote needs no account and has no ads, analytics or tracking.").font(.system(size: 13)).foregroundStyle(T.muted).padding(.top, 16)
                Text("Roku, Samsung, LG, webOS, Android TV, Google TV and the app names shown are trademarks of their owners. Mini Remote is an independent app, not affiliated with or endorsed by them; it uses the TVs' remote-control interfaces on your own network.")
                    .font(.system(size: 12)).foregroundStyle(T.muted).padding(.top, 10)
                Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev")")
                    .font(.system(size: 13)).foregroundStyle(T.ink).padding(.top, 10).padding(.bottom, 6)
                RowDivider()
                Button { path.append(.licenses) } label: {
                    HStack { Text("Open-source licences").foregroundStyle(T.ink); Spacer(); Image(systemName: "chevron.right").foregroundStyle(Color(hex: 0xA8A8AD)) }
                        .padding(.vertical, 14)
                }
            }
        }
        .navigationTitle("Settings").navigationBarTitleDisplayMode(.inline)
    }

    private func segRow(_ title: String, _ opts: [(Double, String)], _ value: Double, _ pick: @escaping (Double) -> Void) -> some View {
        HStack {
            Text(title).foregroundStyle(T.ink)
            Spacer()
            Picker(title, selection: Binding(get: { value }, set: pick)) { ForEach(0..<opts.count, id: \.self) { i in Text(opts[i].1).tag(opts[i].0) } }
                .pickerStyle(.segmented).frame(width: 210)
        }
        .padding(.vertical, 14)
    }
}

private struct PrivacyPage: View {
    @ObservedObject var vm: RemoteModel
    @State private var ask: String?
    var body: some View {
        Page {
            Card {
                InfoBlock("Stored on this phone", "Your TVs' names and addresses and their pairing keys, in the Keychain (this device only, never synced or backed up), plus quick access, channels and settings."); RowDivider()
                InfoBlock("Network", "Controls only TVs on your home Wi-Fi; public addresses are refused. Nothing else goes online."); RowDivider()
                InfoBlock("Voice", "Use the microphone on your keyboard to dictate searches; that's handled by iOS, and the app never records audio itself."); RowDivider()
                InfoBlock("Permissions", "Local network (find and control TVs). No location, contacts, microphone or photos.")
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
