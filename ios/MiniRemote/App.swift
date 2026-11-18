import SwiftUI

@main
struct MiniRemoteApp: App {
    @StateObject private var vm = RemoteModel()
    var body: some Scene {
        WindowGroup {
            // Auto follows the iPhone; the others set light or dark.
            ContentView(vm: vm).preferredColorScheme(["auto", "retro"].contains(vm.settings.theme) ? nil : (Palettes.of(vm.settings.theme).light ? .light : .dark))
        }
    }
}

/**
 * The remote, with the sheets around it: the TV (and Settings), Search, More, the sleep timer
 * and the apps screen. At the foot: what's playing (or the key just pressed) and the page pills.
 */
struct ContentView: View {
    @ObservedObject var vm: RemoteModel
    @Environment(\.colorScheme) private var scheme
    @State private var numpad = false
    @State private var keyPage: Int? = 0
    @State private var showDevices = false
    @State private var showKeys = false
    @State private var keysVoice = false
    @State private var connecting = false
    @State private var startRoute: [Route] = []
    @State private var showMore = false
    @State private var showSleep = false
    @State private var keysFromTv = false
    /** The apps screen; picking = opened from a + place (tapping an app adds it to quick access). */
    @State private var apps: Bool? = nil

    private var accent: Color { Color(hexString: vm.settings.accentHex) }
    private var pages: Int { vm.settings.appsIn == "more" ? 2 : 4 }

    var body: some View {
        // The theme: every colour reads from it; the whole tree is rebuilt when it changes.
        let _ = { Look.p = Palettes.resolve(vm.settings.theme, dark: scheme == .dark) }()
        ZStack(alignment: .bottom) {
            T.screen.ignoresSafeArea()
            HomeView(vm: vm, accent: accent, numpad: $numpad, keyPage: $keyPage,
                     openDevices: { showDevices = true }, openKeys: { keysVoice = false; showKeys = true },
                     openMore: { showMore = true }, openApps: { pick in apps = pick }, openSleep: { showSleep = true })

            // Messages (not key names: those show at the foot) float above the keys.
            ToastChip(text: vm.toastKey == nil ? vm.toast : nil).padding(.bottom, 300)
                .animation(.easeOut(duration: 0.2), value: vm.toast)

            // The foot: what's playing, scrolling, or the key just pressed; the page pills on the right.
            HStack(spacing: 8) {
                let keyName = vm.toastKey != nil ? vm.toast : nil
                if let line = keyName ?? vm.nowLine {
                    HStack(spacing: 8) {
                        Image(systemName: keyName != nil ? (vm.toastKey.flatMap { keySymbol($0, muted: vm.muted, playing: vm.playing) } ?? "circle.fill") : "play.fill")
                            .font(.system(size: 11, weight: .semibold)).foregroundStyle(keyName != nil ? T.ink : T.dim)
                        Marquee(text: line, color: keyName != nil ? T.ink : T.dim).frame(height: 18)
                    }
                    .transition(.opacity)
                    .id(line)
                } else { Spacer() }
                PagePills(count: pages, page: min(keyPage ?? 0, pages - 1), accent: accent) {
                    vm.feel.fire(.tick)
                    withAnimation(.easeInOut(duration: 0.3)) { keyPage = ((keyPage ?? 0) + 1) % pages }
                }
                .coach("pills")
            }
            .animation(.easeOut(duration: 0.16), value: vm.toast)
            .frame(width: KEYROW_W)
            .padding(.bottom, 4)
        }
        // The guided tour, over the remote (after the welcome pages).
        .overlayPreferenceValue(CoachKey.self) { anchors in
            if vm.settings.onboarded && !vm.settings.toured && !showDevices && !showMore && !showKeys && apps == nil {
                GeometryReader { g in
                    CoachTour(rects: anchors.mapValues { g[$0] }, accent: accent) {
                        var s = vm.settings; s.toured = true; vm.update(s)
                        if vm.active == nil { showDevices = true }
                    }
                }
            }
        }
        .id(vm.settings.theme + (scheme == .dark ? "-d" : "-l"))
        .fullScreenCover(isPresented: Binding(get: { !vm.settings.onboarded }, set: { _ in })) {
            OnboardingView(accent: accent) { var s = vm.settings; s.onboarded = true; vm.update(s) }
        }
        .tint(accent)
        .sheet(isPresented: $showDevices) { DevicesSheet(vm: vm, accent: accent, start: startRoute) }
        .sheet(isPresented: $showKeys, onDismiss: { keysFromTv = false; keysVoice = false }) { KeyboardSheet(vm: vm, accent: accent, openSearch: !keysFromTv, startVoice: keysVoice) }
        .sheet(isPresented: $showMore) { MoreSheet(vm: vm, accent: accent, openApps: { pick in showMore = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { apps = pick } }) }
        .sheet(isPresented: $showSleep) { SleepSheet(vm: vm, accent: accent) }
        .sheet(item: Binding(get: { apps.map { AppsMode(picking: $0) } }, set: { apps = $0?.picking })) { m in
            AppsView(vm: vm, accent: accent, picking: m.picking, openDevices: { apps = nil; showDevices = true }, onFull: { apps = nil; vm.say("Quick access is full") })
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
        // The TV focused a text box (login, search): open the typing sheet, without pressing Search again.
        .onChange(of: vm.keyboardAsk) { _, _ in if !showKeys { keysFromTv = true; showKeys = true } }
        // The TV didn't take the phone's voice: Search in Voice mode.
        .onChange(of: vm.voiceTyping) { _, _ in keysFromTv = true; keysVoice = true; showKeys = true }
        .onChange(of: vm.settings.appsIn) { _, _ in keyPage = 0 }
        .onAppear {
            IntentInbox.shared.model = vm
            if let f = IntentInbox.shared.pending { IntentInbox.shared.pending = nil; f(vm) }
        }
        .sheet(item: Binding(get: { showDevices ? nil : vm.pairPrompt.map { PromptBox(p: $0) } }, set: { if $0 == nil { vm.answerCode(nil) } })) { b in
            CodeSheet(vm: vm, accent: accent, prompt: b.p)
        }
        // Once a TV finishes connecting, the TV sheet has done its job.
        .onChange(of: vm.link) { _, l in
            switch l {
            case .connecting: connecting = true
            case .ready:
                if connecting && showDevices { DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { showDevices = false } }
                connecting = false
            default: connecting = false
            }
        }
        .onChange(of: vm.nowOn?.live) { _, live in if live != true { numpad = false } }
        #if DEBUG
        .onAppear { screenshot() }
        #endif
    }
}

private struct AppsMode: Identifiable { let picking: Bool; var id: Bool { picking } }

#if DEBUG
extension ContentView {
    /** SHOT=<screen> opens one screen with a pretend TV, for the CI screenshot run. */
    func screenshot() {
        guard let s = ProcessInfo.processInfo.environment["SHOT"] else { return }
        vm.demo(trackpad: s == "trackpad", cards: false, live: s == "live")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            switch s {
            case "more": showMore = true
            case "sleep": showSleep = true
            case "welcome": var st = vm.settings; st.onboarded = false; vm.update(st)
            case "tour": var st = vm.settings; st.toured = false; vm.update(st)
            case "page2": keyPage = 1
            case "page3": keyPage = 2
            case "light": var st = vm.settings; st.theme = "light"; vm.update(st)
            case "navy": var st = vm.settings; st.theme = "navy"; vm.update(st)
            case "auto": var st = vm.settings; st.theme = "auto"; vm.update(st)
            case "gestures": startRoute = [.settings, .gestures]; showDevices = true
            case "apps": apps = false
            case "devices": showDevices = true
            case "scan": startRoute = [.scan]; showDevices = true
            case "settings": startRoute = [.settings]; showDevices = true
            case "privacy": startRoute = [.settings, .privacy]; showDevices = true
            case "licenses": startRoute = [.settings, .licenses]; showDevices = true
            case "keys": showKeys = true
            default: break
            }
        }
    }
}
#endif

private struct PromptBox: Identifiable { let p: PairPrompt; var id: String { p.tvName + (p.error ?? "") } }

/** Pairing code entry when it's needed with the TV sheet closed. */
private struct CodeSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let prompt: PairPrompt
    @State private var code = ""
    var body: some View {
        VStack(spacing: 14) {
            Text("Pair with \(prompt.tvName)").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink)
            Text("Type the 6-character code on the TV.").foregroundStyle(T.muted)
            TextField("A1B2C3", text: $code).font(.system(size: 26, design: .monospaced)).multilineTextAlignment(.center)
                .textInputAutocapitalization(.characters).autocorrectionDisabled()
                .onChange(of: code) { v in code = String(v.uppercased().filter { $0.isHexDigit }.prefix(6)) }
                .frame(height: 60).background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
            if let e = prompt.error { Text(e).font(.system(size: 13)).foregroundStyle(Color(hex: 0xFF8A8A)) }
            WideButton(title: "Pair", enabled: code.count == 6) { vm.answerCode(code) }
        }
        .padding(24)
        .presentationDetents([.height(340)])
    }
}
