import SwiftUI

enum Deck { case dial, trackpad, numpad }

/**
 * The Remote page, after the Apple TV Remote: mute · TV · power on top, the dial or trackpad
 * in the middle, and a bottom panel with Back, Home, Play / Pause and Search that grows into
 * a volume / channel sheet. OK: tap = OK, double tap = mute, hold = voice; two fingers slide
 * for volume and channels; a two-finger double tap swaps dial and trackpad.
 */
struct HomeView: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var tray: Bool
    @Binding var numpad: Bool
    let openDevices: () -> Void
    let openKeys: () -> Void

    @State private var pad = PadState()
    @State private var askPower = false
    @State private var pendingOk: Task<Void, Never>?
    @State private var lastTwoTap: TimeInterval = 0

    private var deck: Deck { numpad ? .numpad : (vm.settings.controlStyle == "trackpad" ? .trackpad : .dial) }
    private var offline: Bool { vm.active == nil || vm.link == .error }

    private func okTap() {
        if let p = pendingOk {
            p.cancel(); pendingOk = nil
            vm.press(.mute)
        } else {
            pendingOk = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 260_000_000)
                if Task.isCancelled { return }
                pendingOk = nil
                vm.press(.ok, quiet: true)
            }
        }
    }

    private func key(_ k: RemoteKey) { if k == .ok { okTap() } else { vm.press(k) } }

    private func twoTap() {
        let now = CACurrentMediaTime()
        if now - lastTwoTap < 0.45 {
            lastTwoTap = 0
            var s = vm.settings
            s.controlStyle = s.controlStyle == "trackpad" ? "dial" : "trackpad"
            vm.update(s)
            vm.feel.fire(.detent)
        } else { lastTwoTap = now }
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar.padding(.horizontal, 26).padding(.top, 20)
            GeometryReader { g in
                surface(width: g.size.width - 32, height: g.size.height).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .padding(.vertical, 16)
            BottomPanel(vm: vm, accent: accent, open: $tray, numpad: $numpad, openKeys: openKeys)
        }
        .onAppear { vm.followNowOn(vm.link == .ready) }
        .onChange(of: vm.link) { l in vm.followNowOn(l == .ready) }
        .onDisappear { vm.followNowOn(false) }
        .sheet(isPresented: $askPower) {
            ConfirmSheet(title: "Turn off \(vm.active.map { shortName($0.name, $0.brand.label) } ?? "the TV")?",
                         text: "If the TV is already off, this turns it on.", action: "Turn off") { vm.press(.power) }
        }
    }

    private var topBar: some View {
        HStack {
            RoundKey(symbol: vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill", label: "Mute", size: 52, iconSize: 19,
                     tint: vm.muted ? accent : T.ink, accent: accent) { vm.press(.mute) }
            Spacer(minLength: 12)
            Button(action: openDevices) {
                HStack(spacing: 8) {
                    if let d = vm.active {
                        Circle().fill(vm.link == .ready ? T.ok : (vm.link == .connecting ? T.warn : T.danger)).frame(width: 7, height: 7)
                        Text(shortName(d.name, d.brand.label)).lineLimit(1)
                    } else { Text("Choose a TV") }
                    Image(systemName: "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(T.dim)
                }
                .font(.system(size: 15, weight: .medium)).foregroundStyle(T.ink)
                .padding(.leading, 16).padding(.trailing, 12).frame(height: 44)
                .background(Capsule().fill(T.high))
            }
            .accessibilityLabel(vm.active.map { "TV: \($0.name), \(linkWord(vm.link))" } ?? "Choose a TV")
            Spacer(minLength: 12)
            RoundKey(symbol: "power", label: "Power", size: 52, iconSize: 19, tint: accent, accent: accent) {
                if vm.active == nil { openDevices() } else { askPower = true }
            }
        }
    }

    @ViewBuilder
    private func surface(width: CGFloat, height: CGFloat) -> some View {
        let common = TouchPad(mode: deck == .trackpad ? .trackpad : .dial, stepPx: CGFloat(vm.settings.scrubStep), offline: offline,
                              onKey: key, onTick: { vm.scrubTick($0) },
                              onVoice: { vm.feel.fire(.heavy); vm.press(.voice) },
                              onTwo: { vm.scrubTick($0) }, onTwoTap: twoTap,
                              onOffline: { vm.feel.fire(.press); openDevices() }, state: $pad)
        switch deck {
        case .numpad:
            Numpad(vm: vm, accent: accent).frame(width: min(width, 320), height: min(height, 340))
        case .dial:
            let s = min(width, height, 340)
            ZStack {
                DialFace(size: s, accent: accent, pad: pad, toast: vm.toast,
                         toastSymbol: vm.toastKey.flatMap { keySymbol($0, muted: vm.muted, playing: vm.playing) }, offline: offline)
                common.frame(width: s, height: s).clipShape(Circle())
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Direction pad")
            .accessibilityActions {
                Button("OK") { vm.press(.ok) }; Button("Up") { vm.press(.up) }; Button("Down") { vm.press(.down) }
                Button("Left") { vm.press(.left) }; Button("Right") { vm.press(.right) }; Button("Mute") { vm.press(.mute) }
                Button("Voice search") { vm.press(.voice) }
            }
        case .trackpad:
            let h = min(height, width * 1.25)
            ZStack {
                TrackpadFace(width: width, height: h, accent: accent, pad: pad, offline: offline)
                common.frame(width: width, height: h)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Trackpad")
            .accessibilityActions {
                Button("OK") { vm.press(.ok) }; Button("Up") { vm.press(.up) }; Button("Down") { vm.press(.down) }
                Button("Left") { vm.press(.left) }; Button("Right") { vm.press(.right) }; Button("Mute") { vm.press(.mute) }
            }
        }
    }
}

/** What the two-finger slide is doing, in place of the arrows. */
struct TwoHint: View {
    let key: RemoteKey?
    let accent: Color
    var big: CGFloat = 34
    var body: some View {
        VStack(spacing: 6) {
            if let k = key {
                let (sym, word): (String, String) = {
                    switch k {
                    case .volUp: return ("chevron.up", "Volume +")
                    case .volDown: return ("chevron.down", "Volume −")
                    case .chUp: return ("chevron.right", "Channel +")
                    default: return ("chevron.left", "Channel −")
                    }
                }()
                Image(systemName: sym).font(.system(size: big, weight: .semibold)).foregroundStyle(accent)
                Text(word).font(.system(size: 14, weight: .medium)).foregroundStyle(T.ink)
            } else {
                Text("↕ Volume   ↔ Channel").font(.system(size: 13)).foregroundStyle(T.ink)
            }
        }
    }
}

struct DialFace: View {
    let size: CGFloat
    let accent: Color
    let pad: PadState
    let toast: String?
    let toastSymbol: String?
    let offline: Bool

    var body: some View {
        let well = size * 0.385
        let cs = size * 0.075
        ZStack {
            Circle().fill(T.container)
            if offline {
                VStack(spacing: 8) {
                    Image(systemName: "tv").font(.system(size: size * 0.13)).foregroundStyle(Color(hex: 0xB0B0B5))
                    Text("No TV connected").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color(hex: 0xB0B0B5))
                    Text("Tap to choose a TV").font(.system(size: 13)).foregroundStyle(Color(hex: 0xA1A1A6))
                }
            } else {
                if !pad.two {
                    let arrows: [(RemoteKey, String, CGSize)] = [
                        (.up, "chevron.up", CGSize(width: 0, height: -size * 0.39)),
                        (.down, "chevron.down", CGSize(width: 0, height: size * 0.39)),
                        (.left, "chevron.left", CGSize(width: -size * 0.39, height: 0)),
                        (.right, "chevron.right", CGSize(width: size * 0.39, height: 0))]
                    ForEach(0..<4, id: \.self) { i in
                        Image(systemName: arrows[i].1).font(.system(size: cs, weight: .semibold))
                            .foregroundStyle(pad.lit == arrows[i].0 ? accent : T.dim).offset(arrows[i].2)
                    }
                }
                Circle().fill(pad.wellDown ? T.pressed : T.highest).frame(width: well, height: well)
                    .scaleEffect(pad.wellDown ? 0.97 : 1)
                Group {
                    if pad.two { TwoHint(key: pad.twoKey, accent: accent, big: 26) }
                    else if let toast {
                        VStack(spacing: 4) {
                            if let s = toastSymbol { Image(systemName: s).font(.system(size: 22, weight: .medium)).foregroundStyle(accent) }
                            Text(toast).font(.system(size: 12.5, weight: .medium)).foregroundStyle(Color(hex: 0xD4D4D9))
                                .multilineTextAlignment(.center).padding(.horizontal, 10)
                        }
                        .frame(width: well)
                    } else {
                        Text("OK").font(.system(size: 17, weight: .semibold)).tracking(1).foregroundStyle(pad.wellDown ? accent : Color(hex: 0xA1A1A6))
                    }
                }
            }
        }
        .frame(width: size, height: size)
        .animation(.easeOut(duration: 0.12), value: pad)
    }
}

struct TrackpadFace: View {
    let width: CGFloat
    let height: CGFloat
    let accent: Color
    let pad: PadState
    let offline: Bool

    /** Arrows appear only as the finger nears their edge, and light up when a step goes that way. */
    private func near(_ k: RemoteKey) -> Double {
        if pad.lit == k { return 1 }
        guard let t = pad.touch else { return 0 }
        let d: CGFloat = { switch k { case .up: return t.y; case .down: return height - t.y; case .left: return t.x; default: return width - t.x } }()
        return Double(max(0, min(1, 1 - d / (min(width, height) * 0.5)))) * 0.85
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 28).fill(T.container)
            Canvas { ctx, size in
                let gap = size.width / 11
                var y = gap
                while y < size.height {
                    for i in 1...10 {
                        ctx.fill(Path(ellipseIn: CGRect(x: CGFloat(i) * gap - 1.3, y: y - 1.3, width: 2.6, height: 2.6)), with: .color(.white.opacity(0.16)))
                    }
                    y += gap
                }
            }
            if offline {
                VStack(spacing: 6) {
                    Image(systemName: "tv").font(.system(size: 34)).foregroundStyle(Color(hex: 0xB0B0B5))
                    Text("No TV connected").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color(hex: 0xB0B0B5))
                    Text("Tap to choose a TV").font(.system(size: 12)).foregroundStyle(Color(hex: 0xA1A1A6))
                }
            } else if pad.two {
                TwoHint(key: pad.twoKey, accent: accent)
            } else {
                let arrows: [(RemoteKey, String, Alignment)] = [
                    (.up, "chevron.up", .top), (.down, "chevron.down", .bottom), (.left, "chevron.left", .leading), (.right, "chevron.right", .trailing)]
                ForEach(0..<4, id: \.self) { i in
                    Image(systemName: arrows[i].1).font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(pad.lit == arrows[i].0 ? accent : Color(hex: 0xBDBDC2)).opacity(near(arrows[i].0))
                        .padding(18).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: arrows[i].2)
                }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 28))
    }
}

/** 1–9, then channel list · 0 · OK. */
struct Numpad: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    var body: some View {
        let rows: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["L", "0", "K"]]
        VStack(spacing: 12) {
            ForEach(rows, id: \.self) { r in
                HStack(spacing: 14) {
                    ForEach(r, id: \.self) { c in
                        Button {
                            switch c {
                            case "L": vm.press(.chList)
                            case "K": vm.press(.ok)
                            default: if let k = RemoteKey.forDigit(Character(c)) { vm.press(k) }
                            }
                        } label: {
                            Group {
                                if c == "L" { Image(systemName: "list.bullet") } else { Text(c == "K" ? "OK" : c) }
                            }
                            .font(.system(size: c == "K" || c == "L" ? 17 : 22, weight: .medium, design: .rounded))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        .buttonStyle(PillKeyStyle(accent: accent))
                        .accessibilityLabel(c == "L" ? "Channel list" : (c == "K" ? "OK" : c))
                    }
                }
            }
        }
    }
}

struct PillKeyStyle: ButtonStyle {
    let accent: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.foregroundStyle(configuration.isPressed ? accent : T.ink)
            .background(Capsule().fill(configuration.isPressed ? T.pressed : T.high))
    }
}

/**
 * Bottom of the Remote page. Collapsed: a chevron over four keys. Swipe up (or tap the
 * chevron) and it becomes a sheet with volume, mute, channels and the number pad key.
 */
struct BottomPanel: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var open: Bool
    @Binding var numpad: Bool
    let openKeys: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation(.spring(response: 0.3)) { open.toggle() } } label: {
                Image(systemName: open ? "chevron.down" : "chevron.up").font(.system(size: 20, weight: .semibold)).foregroundStyle(T.dim)
                    .frame(width: 72, height: 30)
            }
            .accessibilityLabel(open ? "Hide volume and channels" : "Show volume and channels")
            .padding(.bottom, 12)
            HStack(spacing: 20) {
                RoundKey(symbol: "chevron.left", label: "Back", iconSize: 22, accent: accent) { vm.press(.back) }
                RoundKey(symbol: "tv", label: "Home", iconSize: 21, accent: accent) { vm.press(.home) }
                RoundKey(symbol: vm.playing ? "pause.fill" : "play.fill", label: vm.playing ? "Pause" : "Play", iconSize: 20, accent: accent) { vm.press(.playPause) }
                RoundKey(symbol: "magnifyingglass", label: "Search", iconSize: 21, accent: accent, action: openKeys)
            }
            if open {
                VStack(spacing: 16) {
                    HStack(spacing: 16) {
                        StepperPill(word: "VOL", downLabel: "Volume down", upLabel: "Volume up", accent: accent,
                                    onDown: { vm.press(.volDown, quiet: true) }, onUp: { vm.press(.volUp, quiet: true) })
                        RoundKey(symbol: vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill", label: "Mute", size: 56, iconSize: 19,
                                 tint: vm.muted ? accent : T.ink, accent: accent) { vm.press(.mute) }
                    }
                    .frame(height: 56)
                    HStack(spacing: 16) {
                        StepperPill(word: "CH", downLabel: "Channel down", upLabel: "Channel up", accent: accent,
                                    onDown: { vm.press(.chDown) }, onUp: { vm.press(.chUp) })
                        Button { numpad.toggle() } label: {
                            Text("123").font(.system(size: 14, weight: .medium)).frame(width: 56, height: 56)
                        }
                        .buttonStyle(KeyStyle(tint: numpad ? accent : T.ink, accent: accent))
                        .accessibilityLabel("Number pad")
                    }
                    .frame(height: 56)
                }
                .frame(width: 308)
                .padding(.top, 26).padding(.bottom, 6)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .padding(.top, open ? 6 : 0)
        .padding(.bottom, open ? 50 : 62)
        .frame(maxWidth: .infinity)
        .background(open ? TopRounded(radius: 28).fill(T.container).ignoresSafeArea() : nil)
        .gesture(DragGesture(minimumDistance: 16).onEnded { v in
            withAnimation(.spring(response: 0.3)) {
                if v.translation.height < -24 { open = true } else if v.translation.height > 24 { open = false }
            }
        })
        .onChange(of: open) { o in if !o { numpad = false } }
    }
}

/** A shape with rounded top corners; its bottom runs past the frame (off-screen). */
struct TopRounded: Shape {
    var radius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height + radius * 2), cornerRadius: radius)
    }
}
