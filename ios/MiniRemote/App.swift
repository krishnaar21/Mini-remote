import SwiftUI

@main
struct MiniRemoteApp: App {
    @StateObject private var vm = RemoteModel()
    var body: some Scene {
        WindowGroup {
            ContentView(vm: vm).preferredColorScheme(.dark)
        }
    }
}

/** Two pages (Remote, Apps) with dots at the bottom, plus the TV and search sheets. */
struct ContentView: View {
    @ObservedObject var vm: RemoteModel
    @State private var page = 0
    @State private var tray = false
    @State private var numpad = false
    @State private var showDevices = false
    @State private var showKeys = false
    @State private var connecting = false

    private var accent: Color { Color(hexString: vm.settings.accentHex) }

    var body: some View {
        ZStack(alignment: .bottom) {
            T.bg.ignoresSafeArea()
            TabView(selection: $page) {
                HomeView(vm: vm, accent: accent, tray: $tray, numpad: $numpad,
                         openDevices: { showDevices = true }, openKeys: { showKeys = true }).tag(0)
                AppsView(vm: vm, accent: accent, openDevices: { showDevices = true }).tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea(edges: .bottom)

            // The dial shows messages in its well; everywhere else they float near the bottom.
            let inWell = page == 0 && !numpad && vm.settings.controlStyle == "dial" && !tray
            ToastChip(text: inWell ? nil : vm.toast).padding(.bottom, page == 0 ? (tray ? 300 : 190) : 60)
                .animation(.easeOut(duration: 0.2), value: vm.toast)
            PageDots(page: $page, count: 2, accent: accent).padding(.bottom, 4)
        }
        .tint(accent)
        .sheet(isPresented: $showDevices) { DevicesSheet(vm: vm, accent: accent) }
        .sheet(isPresented: $showKeys) { KeyboardSheet(vm: vm, accent: accent) }
        .sheet(item: Binding(get: { showDevices ? nil : vm.pairPrompt.map { PromptBox(p: $0) } }, set: { if $0 == nil { vm.answerCode(nil) } })) { b in
            CodeSheet(vm: vm, accent: accent, prompt: b.p)
        }
        // Once a TV finishes connecting, the TV sheet has done its job.
        .onChange(of: vm.link) { l in
            switch l {
            case .connecting: connecting = true
            case .ready:
                if connecting && showDevices { DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { showDevices = false } }
                connecting = false
            default: connecting = false
            }
        }
        .onChange(of: page) { p in if p != 0 { tray = false } }
        .onChange(of: tray) { t in
            if t { Task { try? await Task.sleep(nanoseconds: 7_000_000_000); tray = false } }
        }
    }
}

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
        .presentationBackground(T.sheet)
    }
}
