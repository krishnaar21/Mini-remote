import SwiftUI

/* ------------------------------------------------------------ onboarding */

/**
 * First run: four short pages (what the app is, connecting, the remote's gestures, apps and
 * typing), each with a picture. Skip or Get started ends it; the guided tour follows.
 */
struct OnboardingView: View {
    let accent: Color
    let onDone: () -> Void
    @State private var page = 0

    private let slides: [(String, String, String)] = [
        ("Your TV remote, on your iPhone", "Control Google TV, Android TV, Roku, Samsung and LG TVs: no account, no ads.", "dial"),
        ("Same Wi-Fi as your TV", "Turn the TV on and keep your iPhone on the same Wi-Fi. Mini Remote finds it; Google TVs show a code to pair once.", "wifi"),
        ("Swipe, tap, turn", "Swipe or tap the arrows on the dial, tap OK to select and hold it to talk. Turn your finger round the ring for volume.", "gesture"),
        ("Your apps, one tap away", "Swipe across the circles for more keys and your favourite apps. Search types on the TV from your iPhone's keyboard.", "apps"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if page < slides.count - 1 { Button("Skip", action: onDone).font(.system(size: 15, weight: .medium)).foregroundStyle(T.dim).padding(.horizontal, 20) }
            }
            .frame(height: 44)
            TabView(selection: $page) {
                ForEach(0..<slides.count, id: \.self) { i in
                    VStack(spacing: 0) {
                        art(slides[i].2).frame(height: 240)
                        Text(slides[i].0).font(.system(size: 24, weight: .semibold)).foregroundStyle(T.ink).multilineTextAlignment(.center).padding(.top, 28)
                        Text(slides[i].1).font(.system(size: 15)).foregroundStyle(T.dim).multilineTextAlignment(.center).lineSpacing(3)
                            .padding(.top, 12).frame(maxWidth: 360)
                    }
                    .padding(.horizontal, 32)
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            HStack(spacing: 6) {
                ForEach(0..<slides.count, id: \.self) { i in Capsule().fill(i == page ? accent : T.dotOff).frame(width: i == page ? 18 : 6, height: 6) }
            }
            .animation(.easeOut(duration: 0.2), value: page)
            .padding(.vertical, 18)
            Button { if page < slides.count - 1 { withAnimation { page += 1 } } else { onDone() } } label: {
                Text(page < slides.count - 1 ? "Next" : "Get started").font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Look.p.light ? .white : Color(hex: 0x111111))
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Look.p.light ? Color(hex: 0x1C1C1E) : Color(hex: 0xE3E3E3)))
            }
            .padding(.horizontal, 24).padding(.bottom, 20)
        }
        .background(T.screen.ignoresSafeArea())
    }

    @ViewBuilder private func art(_ kind: String) -> some View {
        switch kind {
        case "dial", "gesture":
            ZStack {
                Circle().stroke(T.ink.opacity(0.12), lineWidth: 3).frame(width: 196, height: 196)
                Circle().trim(from: 0, to: 0.6).stroke(accent, style: StrokeStyle(lineWidth: 5, lineCap: .round)).rotationEffect(.degrees(90)).frame(width: 196, height: 196)
                Circle().fill(.clear).frame(width: 160, height: 160).glassEffect(.regular, in: .circle)
                Circle().fill(T.ink.opacity(0.1)).frame(width: 62, height: 62)
                Text("OK").font(.system(size: 15, weight: .semibold)).foregroundStyle(T.dim)
                ForEach(0..<4, id: \.self) { i in
                    Image(systemName: ["chevron.up", "chevron.right", "chevron.down", "chevron.left"][i]).font(.system(size: 15, weight: .semibold)).foregroundStyle(T.ink)
                        .offset(x: [0, 58, 0, -58][i], y: [-58, 0, 58, 0][i])
                }
                if kind == "gesture" {
                    Image(systemName: "hand.point.up.left.fill").font(.system(size: 40)).foregroundStyle(accent).offset(x: 40, y: 54)
                    Image(systemName: "arrow.left.and.right").font(.system(size: 18, weight: .bold)).foregroundStyle(accent).offset(x: 0, y: 36)
                }
            }
        case "wifi":
            ZStack {
                Circle().fill(accent.opacity(0.07)).frame(width: 190, height: 190)
                Circle().fill(accent.opacity(0.12)).frame(width: 140, height: 140)
                VStack(spacing: 4) {
                    Image(systemName: "wifi").font(.system(size: 30, weight: .semibold)).foregroundStyle(accent)
                    Image(systemName: "tv").font(.system(size: 56, weight: .light)).foregroundStyle(T.ink)
                }
            }
        default:
            let colours: [UInt32] = [0xE50914, 0xFF0000, 0x1A98FF, 0x1DB954, 0x6B21A8, 0x0E2A5C, 0xE5A00D]
            VStack(spacing: 12) {
                ForEach(0..<2, id: \.self) { r in
                    HStack(spacing: 12) {
                        ForEach(0..<4, id: \.self) { c in
                            let i = r * 4 + c
                            if i < 7 { Circle().fill(Color(hex: colours[i])).frame(width: 56, height: 56) }
                            else { Image(systemName: "square.grid.2x2").font(.system(size: 20)).foregroundStyle(T.ink).frame(width: 56, height: 56).glassEffect(.regular, in: .circle) }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ guided tour */

/** Where the tour's spots are: each part it points at reports its bounds. */
struct CoachKey: PreferenceKey {
    static var defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) { value.merge(nextValue()) { $1 } }
}

extension View {
    /** Marks a part of the screen the guided tour can point at. */
    func coach(_ id: String) -> some View { anchorPreference(key: CoachKey.self, value: .bounds) { [id: $0] } }
}

/**
 * The guided tour: the screen dims, the part being explained shows through a spotlight with an
 * accent outline, and a card says what it does. Tap Next (or anywhere) to go on; Skip ends it.
 */
struct CoachTour: View {
    let rects: [String: CGRect]
    let accent: Color
    let onDone: () -> Void
    @State private var i = 0

    private static let steps: [(id: String, title: String, text: String, round: Bool)] = [
        ("tv", "Your TV", "This is a demo TV for the tour. After it, tap here to find and pair your own TV; the dot turns green when it's connected.", false),
        ("power", "Power", "Turns the TV off (it asks first). When it's off, this wakes it up again where the TV allows.", true),
        ("dial", "Move and select", "Tap the arrows or swipe across the dial to move; tap OK to select. Double-tap OK to mute; hold it to talk to the TV.", true),
        ("dial", "Volume ring", "The thin ring round the dial is the volume: the coloured arc is how loud the TV is (24 here). Turn your finger round it to change it. It appears once a TV is connected.", true),
        ("keys", "More keys and your apps", "Swipe these circles sideways: more keys (skip, sleep timer, inputs…), then your quick access apps. Drag an app onto another to move it.", false),
        ("pills", "Pages", "Shows which page of circles you're on: tap it for the next one. What's playing shows on this line too.", false),
    ]

    var body: some View {
        let steps = Self.steps.filter { rects[$0.id] != nil }
        GeometryReader { g in
            if steps.isEmpty { Color.clear.onAppear(perform: onDone) } else {
                let s = steps[min(i, steps.count - 1)]
                let r = rects[s.id] ?? .zero
                let hole: CGRect = {
                    if s.round { let rad = max(r.width, r.height) / 2 + 10; return CGRect(x: r.midX - rad, y: r.midY - rad, width: rad * 2, height: rad * 2) }
                    return r.insetBy(dx: -10, dy: -10)
                }()
                let corner = s.round ? hole.width / 2 : 24
                let below = hole.maxY + 220 < g.size.height
                ZStack(alignment: .topLeading) {
                    // The dim, with the spotlight cut out (even-odd fill; reaching past the safe area).
                    Path { p in
                        p.addRect(CGRect(x: -200, y: -300, width: g.size.width + 400, height: g.size.height + 600))
                        p.addRoundedRect(in: hole, cornerSize: CGSize(width: corner, height: corner), style: .continuous)
                    }
                    .fill(Color.black.opacity(0.72), style: FillStyle(eoFill: true))
                    RoundedRectangle(cornerRadius: corner, style: .continuous).stroke(accent, lineWidth: 2)
                        .frame(width: hole.width, height: hole.height).position(x: hole.midX, y: hole.midY)
                    card(s.title, s.text, number: min(i, steps.count - 1) + 1, of: steps.count, last: i >= steps.count - 1)
                        .frame(width: g.size.width - 40)
                        .position(x: g.size.width / 2, y: below ? hole.maxY + 16 + 95 : hole.minY - 16 - 95)
                        .id(i)
                        .transition(.opacity)
                }
                .contentShape(Rectangle())
                .onTapGesture { next(steps.count) }
                .animation(.easeOut(duration: 0.25), value: i)
            }
        }
    }

    private func next(_ n: Int) { if i >= n - 1 { onDone() } else { i += 1 } }

    private func card(_ title: String, _ text: String, number: Int, of n: Int, last: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("\(number) of \(n)").font(.system(size: 12.5, weight: .medium)).foregroundStyle(accent)
                Spacer()
                // The tour runs on a pretend TV: say so.
                Text("Demo TV · nothing is sent").font(.system(size: 11.5)).foregroundStyle(T.sub)
                    .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(T.ink.opacity(0.08)))
            }
            Text(title).font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).padding(.top, 4)
            Text(text).font(.system(size: 14)).foregroundStyle(T.dim).fixedSize(horizontal: false, vertical: true).padding(.top, 6)
            HStack {
                Button("Skip tour", action: onDone).font(.system(size: 14, weight: .medium)).foregroundStyle(T.dim)
                Spacer()
                Button { next(n) } label: {
                    Text(last ? "Done" : "Next").font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 20).frame(height: 40).background(Capsule().fill(accent))
                }
            }
            .padding(.top, 14)
        }
        .padding(20)
        .frame(height: 190, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(T.sheet))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(T.line, lineWidth: 1))
        .accessibilityElement(children: .contain)
    }
}
