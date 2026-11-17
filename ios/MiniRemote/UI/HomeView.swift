import SwiftUI

enum Deck { case dial, trackpad, numpad }

/** Width of the volume ring around the dial. */
let RING_BAND: CGFloat = 26

/**
 * The Remote page, after the Apple TV Remote, in Liquid Glass: mute · TV · power on top, the
 * dial (with its volume ring) or trackpad in the middle, keys below. Two layouts, picked in
 * Settings: Black (ring, two rows of keys) and Cards (swipe between the pad, volume / channel
 * and numbers). OK: tap = OK, double tap = mute, hold = voice. Two fingers slide for volume and
 * channels; a two-finger double tap swaps dial and trackpad.
 */
struct HomeView: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var numpad: Bool
    let openDevices: () -> Void
    let openKeys: () -> Void

    @State private var pad = PadState()
    @State private var askPower = false
    @State private var pendingOk: Task<Void, Never>?
    @State private var lastTwoTap: TimeInterval = 0
    @State private var card = 0

    private var cards: Bool { vm.settings.layout == "cards" }
    private var style: Deck { vm.settings.controlStyle == "trackpad" ? .trackpad : .dial }
    private var deck: Deck { numpad ? .numpad : style }
    private var offline: Bool { vm.active == nil || vm.link == .error }
    private var volText: String? { vm.tvVolume.map { $0.muted ? "Muted" : "\($0.level)" } }

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
            if cards { cardsBody } else { blackBody }
            Spacer().frame(height: 62)
        }
        .onAppear { vm.followNowOn(vm.link == .ready) }
        .onChange(of: vm.link) { _, l in vm.followNowOn(l == .ready) }
        .onDisappear { vm.followNowOn(false) }
        .sheet(isPresented: $askPower) {
            ConfirmSheet(title: "Turn off \(vm.active.map { shortName($0.name, $0.brand.label) } ?? "the TV")?",
                         text: "If the TV is already off, this turns it on.", action: "Turn off") { vm.press(.power) }
        }
    }

    /* ---------------------------------------------------------------- top */

    private var topBar: some View {
        GlassEffectContainer(spacing: 12) {
            HStack {
                GlassKey(symbol: vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill", label: "Mute", size: 52, icon: 19) { vm.press(.mute) }
                Spacer(minLength: 12)
                Button(action: openDevices) {
                    HStack(spacing: 8) {
                        if let d = vm.active {
                            Circle().fill(vm.link == .ready ? T.ok : (vm.link == .connecting ? T.warn : T.danger)).frame(width: 7, height: 7)
                            Text(shortName(d.name, d.brand.label)).lineLimit(1)
                        } else { Text("Choose a TV") }
                        Image(systemName: "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(T.dim)
                    }
                    .font(.system(size: 15, weight: .medium)).foregroundStyle(.white)
                    .padding(.leading, 16).padding(.trailing, 12).frame(height: 44)
                    .glassEffect(.regular.interactive(), in: .capsule)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(vm.active.map { "TV: \($0.name), \(linkWord(vm.link))" } ?? "Choose a TV")
                Spacer(minLength: 12)
                GlassKey(symbol: "power", label: "Power", size: 52, icon: 19) {
                    if vm.active == nil { openDevices() } else { askPower = true }
                }
            }
        }
    }

    /* -------------------------------------------------------------- black */

    private var blackBody: some View {
        VStack(spacing: 0) {
            GeometryReader { g in
                let s = min(g.size.width - 40 - RING_BAND * 2, g.size.height - RING_BAND * 2, 300)
                Group {
                    switch deck {
                    case .numpad: Numpad(vm: vm).frame(width: min(g.size.width - 40, 320), height: min(g.size.height, 340))
                    case .trackpad: trackpad(size: s + RING_BAND * 2)
                    case .dial: dial(size: s, ring: true)
                    }
                }
                .frame(width: g.size.width, height: g.size.height)
            }
            .padding(.vertical, 8)
            KeyRows(vm: vm, numpad: $numpad, openKeys: openKeys).padding(.top, 10)
        }
    }

    /* -------------------------------------------------------------- cards */

    private var cardsBody: some View {
        VStack(spacing: 0) {
            TabView(selection: $card) {
                GeometryReader { g in
                    let s = min(CARD_W, g.size.height, 340)
                    Group { if style == .trackpad { trackpad(size: s) } else { dial(size: s, ring: false) } }
                        .frame(width: g.size.width, height: g.size.height)
                }.tag(0)
                GlassCard { VolChCard(vm: vm) }.tag(1)
                GlassCard { Numpad(vm: vm) }.tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .padding(.top, 8)
            CardDots(page: $card, count: 3)
            FourKeys(vm: vm, openKeys: openKeys).padding(.top, 6)
        }
    }

    /* -------------------------------------------------------- dial / pad */

    private func touch(_ mode: TouchPadView.Mode, ring: Bool) -> TouchPad {
        TouchPad(mode: mode, stepPx: CGFloat(vm.settings.scrubStep), offline: offline,
                 onKey: key, onTick: { vm.scrubTick($0) },
                 onVoice: { vm.feel.fire(.heavy); vm.press(.voice) },
                 onTwo: { vm.scrubTick($0) }, onTwoTap: twoTap,
                 onOffline: { vm.feel.fire(.press); openDevices() },
                 ring: ring ? RING_BAND : 0, onRing: { up in vm.scrubTick(up ? .volUp : .volDown) }, state: $pad)
    }

    private func dial(size s: CGFloat, ring: Bool) -> some View {
        let outer = ring ? s + RING_BAND * 2 : s
        let center: String? = pad.turning ? (volText ?? "Volume") : nil
        return ZStack {
            if ring {
                VolumeRing(size: outer, accent: accent, level: vm.tvVolume.map { $0.muted ? 0 : Double($0.level) / Double(max(1, $0.max)) },
                           value: volText, pad: pad)
            }
            DialFace(size: s, pad: pad, toast: center ?? vm.toast,
                     toastSymbol: center != nil ? (vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                        : vm.toastKey.flatMap { keySymbol($0, muted: vm.muted, playing: vm.playing) },
                     offline: offline)
            touch(.dial, ring: ring).frame(width: outer, height: outer)
        }
        .frame(width: outer, height: outer)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Direction pad")
        .accessibilityActions {
            Button("OK") { vm.press(.ok) }; Button("Up") { vm.press(.up) }; Button("Down") { vm.press(.down) }
            Button("Left") { vm.press(.left) }; Button("Right") { vm.press(.right) }; Button("Mute") { vm.press(.mute) }
            Button("Volume up") { vm.press(.volUp) }; Button("Volume down") { vm.press(.volDown) }
            Button("Voice search") { vm.press(.voice) }
        }
    }

    /** The trackpad takes the dial's place and size. */
    private func trackpad(size s: CGFloat) -> some View {
        ZStack {
            TrackpadFace(width: s, height: s, pad: pad, offline: offline)
            touch(.trackpad, ring: false).frame(width: s, height: s)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Trackpad")
        .accessibilityActions {
            Button("OK") { vm.press(.ok) }; Button("Up") { vm.press(.up) }; Button("Down") { vm.press(.down) }
            Button("Left") { vm.press(.left) }; Button("Right") { vm.press(.right) }; Button("Mute") { vm.press(.mute) }
        }
    }
}

/* ------------------------------------------------------------------ pieces */

/** A round Liquid Glass key with an SF Symbol (or a short word) in white, and an optional caption. */
struct GlassKey: View {
    var symbol: String? = nil
    var word: String? = nil
    var caption: String? = nil
    let label: String
    var size: CGFloat = 62
    var icon: CGFloat = 21
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 1) {
                if let symbol { Image(systemName: symbol).font(.system(size: icon, weight: .medium)) }
                if let word { Text(word).font(.system(size: 16, weight: .medium)) }
                if let caption { Text(caption).font(.system(size: 9, weight: .semibold)).tracking(0.8).foregroundStyle(T.dim) }
            }
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(active ? Circle().fill(.white.opacity(0.18)) : nil)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .circle)
        .accessibilityLabel(label)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

/** Back, Home, Play / pause, Search. */
struct FourKeys: View {
    @ObservedObject var vm: RemoteModel
    let openKeys: () -> Void
    var size: CGFloat = 62
    var body: some View {
        GlassEffectContainer(spacing: 18) {
            HStack(spacing: 18) {
                GlassKey(symbol: "chevron.left", label: "Back", size: size, icon: 22) { vm.press(.back) }
                GlassKey(symbol: "house", label: "Home", size: size) { vm.press(.home) }
                GlassKey(symbol: vm.playing ? "pause.fill" : "play.fill", label: vm.playing ? "Pause" : "Play", size: size, icon: 20) { vm.press(.playPause) }
                GlassKey(symbol: "magnifyingglass", label: "Search", size: size, action: openKeys)
            }
        }
    }
}

/** Black layout: two rows. Volume is the ring; mute is at the top. */
struct KeyRows: View {
    @ObservedObject var vm: RemoteModel
    @Binding var numpad: Bool
    let openKeys: () -> Void
    private let k: CGFloat = 68
    var body: some View {
        GlassEffectContainer(spacing: 14) {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    GlassKey(symbol: "chevron.left", label: "Back", size: k, icon: 23) { vm.press(.back) }
                    GlassKey(symbol: "house", label: "Home", size: k, icon: 22) { vm.press(.home) }
                    GlassKey(symbol: vm.playing ? "pause.fill" : "play.fill", label: vm.playing ? "Pause" : "Play", size: k, icon: 21) { vm.press(.playPause) }
                    GlassKey(symbol: "magnifyingglass", label: "Search", size: k, icon: 22, action: openKeys)
                }
                HStack(spacing: 14) {
                    GlassKey(symbol: "minus", caption: "CH", label: "Channel down", size: k, icon: 18) { vm.press(.chDown) }
                    GlassKey(symbol: "plus", caption: "CH", label: "Channel up", size: k, icon: 18) { vm.press(.chUp) }
                    GlassKey(word: "123", label: "Number pad", size: k, active: numpad) { numpad.toggle() }
                    GlassKey(symbol: "rectangle.portrait.and.arrow.right", label: "Input", size: k, icon: 20) { vm.press(.input) }
                }
            }
        }
    }
}

/**
 * The volume ring around the dial. With the TV's volume known, it fills from the bottom,
 * clockwise, with the number at its foot; otherwise an arc follows the finger while turning.
 */
struct VolumeRing: View {
    let size: CGFloat
    let accent: Color
    let level: Double?
    let value: String?
    let pad: PadState

    var body: some View {
        let r = size / 2 - RING_BAND / 2
        ZStack {
            Circle().stroke(.white.opacity(0.14), lineWidth: 3).frame(width: r * 2, height: r * 2)
            if let level {
                Circle().trim(from: 0, to: max(0.001, level))
                    .stroke(accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    .rotationEffect(.degrees(90)) // start at the bottom
                    .frame(width: r * 2, height: r * 2)
                    .animation(.easeOut(duration: 0.16), value: level)
            } else if pad.turning && pad.ringSweep != 0 {
                let from = pad.ringSweep > 0 ? pad.ringStart : pad.ringStart + pad.ringSweep
                Circle().trim(from: 0, to: abs(pad.ringSweep) / 360)
                    .stroke(accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    .rotationEffect(.degrees(from))
                    .frame(width: r * 2, height: r * 2)
            }
            if let value, !pad.turning {
                Text(value).font(.system(size: 12, weight: .medium)).foregroundStyle(T.dim)
                    .padding(.horizontal, 8).background(Capsule().fill(.black))
                    .offset(y: r)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/** What the two-finger slide is doing: louder / quieter speaker, or + / − channel, in white. */
struct TwoHint: View {
    let key: RemoteKey?
    var big: CGFloat = 34
    var body: some View {
        VStack(spacing: 6) {
            if let k = key {
                let (sym, word): (String, String) = {
                    switch k {
                    case .volUp: return ("speaker.wave.3.fill", "Vol")
                    case .volDown: return ("speaker.wave.1.fill", "Vol")
                    case .chUp: return ("plus", "Channel")
                    default: return ("minus", "Channel")
                    }
                }()
                Image(systemName: sym).font(.system(size: big, weight: .medium)).foregroundStyle(.white)
                Text(word).font(.system(size: 14, weight: .medium)).foregroundStyle(.white)
            } else {
                Text("↕ Volume   ↔ Channel").font(.system(size: 13)).foregroundStyle(.white)
            }
        }
    }
}

struct DialFace: View {
    let size: CGFloat
    let pad: PadState
    let toast: String?
    let toastSymbol: String?
    let offline: Bool

    var body: some View {
        let well = size * 0.385
        let cs = size * 0.075
        ZStack {
            Circle().fill(.clear).glassEffect(.regular, in: .circle)
            if offline {
                VStack(spacing: 8) {
                    Image(systemName: "tv").font(.system(size: size * 0.13)).foregroundStyle(T.dim)
                    Text("No TV connected").font(.system(size: 15, weight: .semibold)).foregroundStyle(T.dim)
                    Text("Tap to choose a TV").font(.system(size: 13)).foregroundStyle(T.muted)
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
                            .foregroundStyle(pad.lit == arrows[i].0 ? .white : T.dim).offset(arrows[i].2)
                    }
                }
                Circle().fill(.white.opacity(pad.wellDown ? 0.2 : 0.1)).frame(width: well, height: well)
                    .scaleEffect(pad.wellDown ? 0.97 : 1)
                Group {
                    if pad.two { TwoHint(key: pad.twoKey, big: 26) }
                    else if let toast {
                        VStack(spacing: 4) {
                            if let s = toastSymbol { Image(systemName: s).font(.system(size: 22, weight: .medium)).foregroundStyle(.white) }
                            Text(toast).font(.system(size: pad.turning ? 22 : 12.5, weight: .medium)).foregroundStyle(.white)
                                .multilineTextAlignment(.center).padding(.horizontal, 10)
                        }
                        .frame(width: well)
                    } else {
                        Text("OK").font(.system(size: 17, weight: .semibold)).tracking(1).foregroundStyle(pad.wellDown ? .white : T.dim)
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
            RoundedRectangle(cornerRadius: 32).fill(.clear).glassEffect(.regular, in: .rect(cornerRadius: 32))
            if offline {
                VStack(spacing: 6) {
                    Image(systemName: "tv").font(.system(size: 34)).foregroundStyle(T.dim)
                    Text("No TV connected").font(.system(size: 14, weight: .semibold)).foregroundStyle(T.dim)
                    Text("Tap to choose a TV").font(.system(size: 12)).foregroundStyle(T.muted)
                }
            } else if pad.two {
                TwoHint(key: pad.twoKey)
            } else {
                let arrows: [(RemoteKey, String, Alignment)] = [
                    (.up, "chevron.up", .top), (.down, "chevron.down", .bottom), (.left, "chevron.left", .leading), (.right, "chevron.right", .trailing)]
                ForEach(0..<4, id: \.self) { i in
                    Image(systemName: arrows[i].1).font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white).opacity(near(arrows[i].0))
                        .padding(18).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: arrows[i].2)
                }
            }
        }
        .frame(width: width, height: height)
    }
}

/** Width of the Cards layout's cards: the same as the row of four keys under them. */
let CARD_W: CGFloat = 4 * 62 + 3 * 18

/** A Liquid Glass card for the Cards layout: lined up with the keys, 24pt inside. */
struct GlassCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        GeometryReader { g in
            content()
                .padding(24)
                .frame(width: CARD_W, height: min(g.size.height - 16, 360))
                .glassEffect(.regular, in: .rect(cornerRadius: 28))
                .frame(width: g.size.width, height: g.size.height)
        }
    }
}

/** Two tall rockers (volume, channel), with mute, input and guide under them. */
struct VolChCard: View {
    @ObservedObject var vm: RemoteModel
    var body: some View {
        VStack(spacing: 22) {
            HStack(spacing: 28) { // 80 + 28 = 54 + 54
                Rocker(word: "VOL", up: { vm.press(.volUp, quiet: true) }, down: { vm.press(.volDown, quiet: true) })
                Rocker(word: "CH", up: { vm.press(.chUp) }, down: { vm.press(.chDown) })
            }
            GlassEffectContainer(spacing: 16) {
                HStack(spacing: 54) { // under the rockers' centres
                    GlassKey(symbol: "rectangle.portrait.and.arrow.right", label: "Input", size: 54, icon: 19) { vm.press(.input) }
                    GlassKey(symbol: "list.bullet.rectangle", label: "Guide", size: 54, icon: 19) { vm.press(.guide) }
                }
            }
        }
    }
}

/** + on top, the word in the middle, − below; both ends repeat while held. */
struct Rocker: View {
    let word: String
    let up: () -> Void
    let down: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            RepeatKey(action: up) { Image(systemName: "plus").font(.system(size: 20, weight: .medium)).frame(maxWidth: .infinity, maxHeight: .infinity) }
                .accessibilityLabel(word == "VOL" ? "Volume up" : "Channel up")
            Text(word).font(.system(size: 12, weight: .semibold)).tracking(1).foregroundStyle(T.dim).accessibilityHidden(true)
            RepeatKey(action: down) { Image(systemName: "minus").font(.system(size: 20, weight: .medium)).frame(maxWidth: .infinity, maxHeight: .infinity) }
                .accessibilityLabel(word == "VOL" ? "Volume down" : "Channel down")
        }
        .foregroundStyle(.white)
        .frame(width: 80, height: 210)
        .clipShape(Capsule())
        .glassEffect(.regular.interactive(), in: .capsule)
    }
}

/** Small dots for the cards: tap one, or swipe across them. */
struct CardDots: View {
    @Binding var page: Int
    let count: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == page ? T.dim : Color(hex: 0x5A5A60)).frame(width: i == page ? 14 : 5, height: 5)
                    .frame(width: 20, height: 30).contentShape(Rectangle())
                    .onTapGesture { withAnimation { page = i } }
                    .accessibilityLabel(["Remote", "Volume and channels", "Numbers"][i])
                    .accessibilityAddTraits(i == page ? [.isSelected, .isButton] : .isButton)
            }
        }
        .gesture(DragGesture(minimumDistance: 12).onEnded { v in
            withAnimation { page = max(0, min(count - 1, page + (v.translation.width < 0 ? 1 : -1))) }
        })
    }
}

/** 1–9, then channel list · 0 · OK, as glass capsules. */
struct Numpad: View {
    @ObservedObject var vm: RemoteModel
    var body: some View {
        let rows: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["L", "0", "K"]]
        GlassEffectContainer(spacing: 12) {
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
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .contentShape(Capsule())
                            }
                            .buttonStyle(.plain)
                            .glassEffect(.regular.interactive(), in: .capsule)
                            .accessibilityLabel(c == "L" ? "Channel list" : (c == "K" ? "OK" : c))
                        }
                    }
                }
            }
        }
    }
}
