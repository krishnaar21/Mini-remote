import SwiftUI

enum Deck { case dial, trackpad, numpad }

/** Width of the volume ring around the dial. */
let RING_BAND: CGFloat = 26
/** The rows of circles: 4 × 68 + 3 × 14. */
let KEY: CGFloat = 68
let KEYROW_W: CGFloat = 4 * 68 + 3 * 14

/**
 * The remote, in Liquid Glass: the TV (and what's on it) and power on top; the dial with its
 * volume ring, or the trackpad, in the middle; pages of circles below (swipe across: the main
 * keys, more keys, then quick access apps). OK: tap = OK, double tap = mute, hold = voice.
 */
struct HomeView: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var numpad: Bool
    @Binding var keyPage: Int?
    let openDevices: () -> Void
    let openKeys: () -> Void
    var openMore: () -> Void = {}
    var openApps: (Bool) -> Void = { _ in }
    var openSleep: () -> Void = {}

    @State private var pad = PadState()
    @State private var askPower = false
    @State private var pendingOk: Task<Void, Never>?
    @State private var lastTwoTap: TimeInterval = 0

    private var style: Deck { vm.settings.controlStyle == "trackpad" ? .trackpad : .dial }
    private var deck: Deck { numpad ? .numpad : style }
    private var offline: Bool { vm.active == nil || vm.link == .error }
    private var connected: Bool { vm.link == .ready }
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
        GeometryReader { g in
            if g.size.width > g.size.height {
                // Landscape (and iPad): the pad on the left, keys on the right.
                HStack(spacing: 0) {
                    VStack(spacing: 0) {
                        topBar.padding(.horizontal, 20).padding(.top, 12)
                        middle
                    }
                    .frame(maxWidth: .infinity)
                    KeyPages(vm: vm, accent: accent, numpad: $numpad, page: $keyPage, openKeys: openKeys, openMore: openMore, openApps: openApps, openSleep: openSleep)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .padding(.bottom, 44)
            } else {
                VStack(spacing: 0) {
                    topBar.padding(.horizontal, 16).padding(.top, 30)
                    middle.padding(.top, 14)
                    KeyPages(vm: vm, accent: accent, numpad: $numpad, page: $keyPage, openKeys: openKeys, openMore: openMore, openApps: openApps, openSleep: openSleep)
                        .padding(.top, 10).padding(.bottom, 56)
                }
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
            }
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

    private var statusColour: Color? {
        guard vm.active != nil else { return nil }
        switch vm.link { case .ready: return T.ok; case .connecting: return T.warn; default: return T.danger }
    }

    private var topBar: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                // The TV: a TV icon with the connection colour as a badge, and its name. As tall as
                // the power key; as wide as the name needs, within limits.
                Button(action: openDevices) {
                    HStack(spacing: 10) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "tv").font(.system(size: 16, weight: .medium)).foregroundStyle(T.ink)
                                .frame(width: 38, height: 38).background(Circle().fill(T.ink.opacity(0.08)))
                            if let c = statusColour {
                                Circle().fill(c).frame(width: 7, height: 7).padding(1.5).background(Circle().fill(T.high)).offset(x: -1, y: 2)
                            }
                        }
                        Text(vm.active.map { shortName($0.name, $0.brand.label) } ?? "Choose a TV")
                            .font(.system(size: 14, weight: .medium)).foregroundStyle(T.ink).lineLimit(1)
                        Image(systemName: "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(T.dim)
                    }
                    .padding(.leading, 7).padding(.trailing, 16)
                    .frame(minWidth: 150, maxWidth: 236, minHeight: 52, maxHeight: 52, alignment: .leading)
                    .fixedSize(horizontal: true, vertical: false)
                    .glassEffect(.regular.interactive(), in: .capsule)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(vm.active.map { "TV: \($0.name), \(linkWord(vm.link))" } ?? "Choose a TV")
                Spacer(minLength: 0)
                GlassKey(symbol: "power", label: "Power", size: 52, icon: 19) {
                    // Off (not reachable): wake it. On: ask before turning it off.
                    if vm.active == nil { openDevices() } else if vm.link != .ready { vm.wake() } else { askPower = true }
                }
            }
        }
    }

    /* ------------------------------------------------------------ middle */

    private var middle: some View {
        GeometryReader { g in
            let s = min(g.size.width - 40 - RING_BAND * 2, g.size.height - RING_BAND * 2, 300)
            Group {
                switch deck {
                case .numpad: Numpad(vm: vm).frame(width: min(g.size.width - 40, 320), height: min(g.size.height, 340))
                case .trackpad: trackpad(size: min(g.size.width - 8, g.size.height, KEYROW_W))
                case .dial: dial(size: s)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
        }
        .padding(.vertical, 8)
    }

    private func touch(_ mode: TouchPadView.Mode, ring: Bool) -> TouchPad {
        TouchPad(mode: mode, stepPx: CGFloat(vm.settings.scrubStep), offline: offline,
                 onKey: key, onTick: { vm.scrubTick($0) },
                 onVoice: { vm.feel.fire(.heavy); vm.voiceStart() },
                 onTwo: { vm.scrubTick($0) }, onTwoTap: twoTap,
                 onOffline: { vm.feel.fire(.press); openDevices() },
                 ring: ring ? RING_BAND : 0, onRing: { up in vm.scrubTick(up ? .volUp : .volDown) },
                 onVoiceEnd: { vm.voiceStop() }, state: $pad)
    }

    /** The dial inside its volume ring; the ring shows (and turns) only while a TV is connected. */
    private func dial(size s: CGFloat) -> some View {
        let outer = s + RING_BAND * 2
        let center: String? = pad.turning ? (volText ?? "Volume") : nil
        return ZStack {
            VolumeRing(size: outer, accent: accent, level: vm.tvVolume.map { $0.muted ? 0 : Double($0.level) / Double(max(1, $0.max)) },
                       value: volText, pad: pad)
                .opacity(connected ? 1 : 0)
                .animation(.easeOut(duration: 0.3), value: connected)
            DialFace(size: s, pad: pad, toast: center,
                     toastSymbol: center != nil ? (vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill") : nil,
                     offline: offline)
            touch(.dial, ring: connected).frame(width: outer, height: outer)
        }
        .frame(width: outer, height: outer)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Direction pad")
        .accessibilityActions {
            Button("OK") { vm.press(.ok) }; Button("Up") { vm.press(.up) }; Button("Down") { vm.press(.down) }
            Button("Left") { vm.press(.left) }; Button("Right") { vm.press(.right) }; Button("Mute") { vm.press(.mute) }
            Button("Volume up") { vm.press(.volUp) }; Button("Volume down") { vm.press(.volDown) }
        }
    }

    /** The trackpad lines up with the rows of circles under it. */
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

/** A round Liquid Glass key with an SF Symbol (or a short word); dimmed (and explaining why) when it can't be used. */
struct GlassKey: View {
    var symbol: String? = nil
    var word: String? = nil
    var caption: String? = nil
    let label: String
    var size: CGFloat = 62
    var icon: CGFloat = 21
    var active = false
    var enabled = true
    var onDisabled: () -> Void = {}
    let action: () -> Void

    var body: some View {
        Button(action: { if enabled { action() } else { onDisabled() } }) {
            VStack(spacing: 1) {
                if let symbol { Image(systemName: symbol).font(.system(size: icon, weight: .medium)) }
                if let word { Text(word).font(.system(size: 16, weight: .medium)) }
                if let caption { Text(caption).font(.system(size: 9, weight: .semibold)).tracking(0.8).foregroundStyle(T.dim) }
            }
            .foregroundStyle(T.ink)
            .opacity(enabled ? 1 : 0.32)
            .frame(width: size, height: size)
            .background(active ? Circle().fill(T.ink.opacity(0.18)) : nil)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .circle)
        .accessibilityLabel(label)
        .accessibilityAddTraits(active ? .isSelected : [])
        .accessibilityHint(enabled ? "" : "Not available right now")
    }
}

/**
 * Home's pages of circles, swiped across (the page leaving fades and shrinks a little as the
 * next fades in): the main keys; more keys; then quick access apps (eight, then seven more
 * and All apps). With quick access in More, only the first two.
 */
struct KeyPages: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Binding var numpad: Bool
    @Binding var page: Int?
    let openKeys: () -> Void
    let openMore: () -> Void
    let openApps: (Bool) -> Void
    let openSleep: () -> Void

    private var count: Int { vm.settings.appsIn == "more" ? 2 : 4 }

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(0..<count, id: \.self) { i in
                    Group {
                        switch i {
                        case 0: mainKeys
                        case 1: moreKeys
                        default: AppsPage(vm: vm, accent: accent, part: i - 2, openApps: openApps)
                        }
                    }
                    .frame(width: KEYROW_W + 40)
                    .scrollTransition(.interactive, axis: .horizontal) { c, phase in
                        c.opacity(1 - min(1, abs(phase.value) * 1.4)).scaleEffect(1 - 0.08 * abs(phase.value))
                    }
                    .id(i)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $page)
        .frame(width: KEYROW_W + 40, height: KEY * 2 + 14)
    }

    private func k(_ sym: String, _ label: String, _ key: RemoteKey, icon: CGFloat = 21, quiet: Bool = false) -> GlassKey {
        GlassKey(symbol: sym, label: label, size: KEY, icon: icon, enabled: vm.can(key), onDisabled: { vm.explain(key) }) { vm.press(key, quiet: quiet) }
    }

    private var mainKeys: some View {
        GlassEffectContainer(spacing: 14) {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    k("arrow.uturn.backward", "Back", .back, icon: 21)
                    k("homekit", "Home", .home, icon: 22)
                    // Live TV (a set-top box: Tata Play, Airtel, Dish TV…): Mute where Play / Pause would be.
                    if vm.nowOn?.live == true { k(vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill", vm.muted ? "Unmute" : "Mute", .mute, icon: 19) }
                    else { k(vm.playing ? "pause.fill" : "play.fill", vm.playing ? "Pause" : "Play", .playPause) }
                    GlassKey(symbol: "magnifyingglass", label: "Search", size: KEY, icon: 22, enabled: vm.can(.home), onDisabled: { vm.explain(.home) }, action: openKeys)
                }
                HStack(spacing: 14) {
                    // Live TV: channels and numbers. Apps: volume and the voice assistant.
                    if vm.nowOn?.live == true {
                        GlassKey(symbol: "minus", caption: "CH", label: "Channel down", size: KEY, icon: 18, enabled: vm.can(.chDown), onDisabled: { vm.explain(.chDown) }) { vm.press(.chDown) }
                        GlassKey(symbol: "plus", caption: "CH", label: "Channel up", size: KEY, icon: 18, enabled: vm.can(.chUp), onDisabled: { vm.explain(.chUp) }) { vm.press(.chUp) }
                        GlassKey(word: "123", label: "Number pad", size: KEY, active: numpad, enabled: vm.can(.n0), onDisabled: { vm.explain(.n0) }) { numpad.toggle() }
                    } else {
                        k("speaker.wave.1.fill", "Volume down", .volDown, icon: 19, quiet: true)
                        k("speaker.wave.3.fill", "Volume up", .volUp, icon: 19, quiet: true)
                        GlassKey(symbol: "mic.fill", label: "Voice assistant", size: KEY, icon: 21, active: vm.listening, enabled: vm.can(.home), onDisabled: { vm.explain(.home) }) { vm.voiceStart() }
                    }
                    GlassKey(symbol: "ellipsis", label: "More: keys, inputs and apps", size: KEY, icon: 20, action: openMore)
                }
            }
        }
    }

    private var moreKeys: some View {
        GlassEffectContainer(spacing: 14) {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    k("gobackward.10", "Rewind", .rewind, icon: 21)
                    k("goforward.10", "Fast forward", .fastForward, icon: 21)
                    k(vm.muted ? "speaker.slash.fill" : "speaker.wave.2.fill", vm.muted ? "Unmute" : "Mute", .mute, icon: 19)
                    // Sleep: the minutes left while it counts down.
                    TimelineView(.periodic(from: .now, by: 15)) { t in
                        let left = vm.sleepAt.map { max(1, Int(ceil($0.timeIntervalSince(t.date) / 60))) }
                        GlassKey(symbol: left == nil ? "moon.zzz" : nil, word: left.map { "\($0)m" },
                                 label: left.map { "Sleep timer, \($0) minutes left" } ?? "Sleep timer", size: KEY, icon: 21, active: left != nil,
                                 enabled: vm.can(.power), onDisabled: { vm.explain(.power) }, action: openSleep)
                    }
                }
                HStack(spacing: 14) {
                    k("info.circle", "Info", .info)
                    k("list.bullet.rectangle", "Guide", .guide)
                    k("rectangle.portrait.and.arrow.right", "Input", .input, icon: 20)
                    k("slider.horizontal.3", "TV settings", .settings, icon: 20)
                }
            }
        }
    }
}

/**
 * A page of quick access circles: eight apps on the first, seven more and All apps (always
 * the very last place) on the second. Empty places are faint rings with a +. Drag an app onto
 * another to move it there.
 */
struct AppsPage: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    let part: Int
    let openApps: (Bool) -> Void

    /** Index into the pinned list, nil for an empty place, -1 for All apps. */
    static func cells(_ count: Int, _ part: Int) -> [Int?] {
        part == 0 ? (0..<8).map { $0 < count ? $0 : nil } : (8..<15).map { $0 < count ? $0 : nil } + [-1]
    }

    var body: some View {
        let pinned = vm.shortcuts.filter(\.pinned)
        let cells = Self.cells(pinned.count, part)
        let ready = vm.active != nil && vm.link == .ready
        VStack(spacing: 14) {
            ForEach(0..<2, id: \.self) { r in
                HStack(spacing: 14) {
                    ForEach(0..<4, id: \.self) { c in
                        let cell = cells[r * 4 + c]
                        if cell == -1 {
                            GlassKey(symbol: "square.grid.2x2", label: "All apps", size: KEY, icon: 21) { openApps(false) }
                        } else if let i = cell {
                            let s = pinned[i]
                            AppTile(s: s, size: KEY)
                                .onTapGesture { if ready { vm.launch(s) } else { vm.explain(.home) } }
                                .draggable(s.id)
                                .dropDestination(for: String.self) { ids, _ in
                                    guard let id = ids.first, let from = pinned.firstIndex(where: { $0.id == id }), from != i else { return false }
                                    vm.movePinned(pinned[from], i - from); vm.feel.fire(.tick)
                                    return true
                                }
                        } else {
                            Button { openApps(true) } label: {
                                Image(systemName: "plus").font(.system(size: 16, weight: .medium)).foregroundStyle(T.ink.opacity(0.3))
                                    .frame(width: KEY, height: KEY)
                                    .overlay(Circle().strokeBorder(T.ink.opacity(0.1), lineWidth: 1))
                                    .contentShape(Circle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Add apps")
                        }
                    }
                }
            }
        }
    }
}

/** Back · ◀ · ▲ · OK · ▼ · ▶ in one capsule, for sheets that cover the dial (More, Search). */
struct NavPad: View {
    @ObservedObject var vm: RemoteModel
    var body: some View {
        let keys: [(RemoteKey, String?)] = [(.back, "arrow.uturn.backward"), (.left, "chevron.left"), (.up, "chevron.up"), (.ok, nil), (.down, "chevron.down"), (.right, "chevron.right")]
        HStack(spacing: 0) {
            ForEach(0..<keys.count, id: \.self) { i in
                let (k, sym) = keys[i]
                Button { if vm.can(k) { vm.press(k) } else { vm.explain(k) } } label: {
                    Group {
                        if let sym { Image(systemName: sym).font(.system(size: 18, weight: .semibold)) }
                        else { Text("OK").font(.system(size: 13, weight: .semibold)).frame(width: 38, height: 38).background(Circle().fill(T.ink.opacity(0.12))) }
                    }
                    .foregroundStyle(T.ink.opacity(vm.can(k) ? 1 : 0.3))
                    .frame(maxWidth: .infinity, maxHeight: .infinity).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(k.word)
                if i == 0 { Rectangle().fill(T.ink.opacity(0.08)).frame(width: 1, height: 24) }
            }
        }
        .frame(height: 54)
        .glassEffect(.regular, in: .capsule)
    }
}

/** Page dots: a pill in the accent colour for the page showing; tap anywhere on them for the next page (and round again). */
struct PagePills: View {
    let count: Int
    let page: Int
    let accent: Color
    let next: () -> Void
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == page ? accent : T.dotOff).frame(width: i == page ? 18 : 6, height: 6)
            }
        }
        .animation(.easeOut(duration: 0.2), value: page)
        .padding(.horizontal, 10).frame(height: 40).contentShape(Rectangle())
        .onTapGesture(perform: next)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Page \(page + 1) of \(count)")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Shows the next page")
    }
}

/** Text that scrolls round when it's wider than its space. */
struct Marquee: View {
    let text: String
    var font: Font = .system(size: 13)
    var color: Color = T.dim
    @State private var w: CGFloat = 0
    @State private var box: CGFloat = 0
    @State private var go = false
    var body: some View {
        GeometryReader { g in
            let over = w > g.size.width
            HStack(spacing: 40) {
                Text(text).font(font).foregroundStyle(color).fixedSize()
                    .background(GeometryReader { t in Color.clear.onAppear { w = t.size.width } .onChange(of: text) { _, _ in w = t.size.width } })
                if over { Text(text).font(font).foregroundStyle(color).fixedSize() }
            }
            .offset(x: over && go ? -(w + 40) : 0)
            .animation(over && go ? .linear(duration: Double(w + 40) / 30).delay(1.2).repeatForever(autoreverses: false) : .default, value: go)
            .onAppear { box = g.size.width; go = true }
            .onChange(of: text) { _, _ in go = false; DispatchQueue.main.async { go = true } }
        }
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
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
            Circle().stroke(T.ink.opacity(0.14), lineWidth: 3).frame(width: r * 2, height: r * 2)
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
                    .padding(.horizontal, 8).background(Capsule().fill(T.screen))
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
                    case .chUp: return ("chevron.forward.2", "Channel")
                    default: return ("chevron.backward.2", "Channel")
                    }
                }()
                Image(systemName: sym).font(.system(size: big, weight: .medium)).foregroundStyle(T.ink)
                Text(word).font(.system(size: 14, weight: .medium)).foregroundStyle(T.ink)
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
                            .foregroundStyle(pad.lit == arrows[i].0 ? T.ink : T.dim).offset(arrows[i].2)
                    }
                }
                Circle().fill(T.ink.opacity(pad.wellDown ? 0.2 : 0.1)).frame(width: well, height: well)
                    .scaleEffect(pad.wellDown ? 0.97 : 1)
                Group {
                    if pad.two { TwoHint(key: pad.twoKey, big: 26) }
                    else if let toast {
                        VStack(spacing: 4) {
                            if let s = toastSymbol { Image(systemName: s).font(.system(size: 22, weight: .medium)).foregroundStyle(T.ink) }
                            Text(toast).font(.system(size: pad.turning ? 22 : 12.5, weight: .medium)).foregroundStyle(T.ink)
                                .multilineTextAlignment(.center).padding(.horizontal, 10)
                        }
                        .frame(width: well)
                    } else {
                        Text("OK").font(.system(size: 17, weight: .semibold)).tracking(1).foregroundStyle(pad.wellDown ? T.ink : T.dim)
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
                        .foregroundStyle(T.ink).opacity(near(arrows[i].0))
                        .padding(18).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: arrows[i].2)
                }
            }
        }
        .frame(width: width, height: height)
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
                                .foregroundStyle(T.ink)
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

