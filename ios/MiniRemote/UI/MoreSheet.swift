import SwiftUI

/**
 * A sheet that's as tall as what's in it (and scrolls only when that's taller than the screen):
 * the content is measured and the sheet's height follows it.
 */
struct FitSheet: ViewModifier {
    @State private var h: CGFloat = 320
    func body(content: Content) -> some View {
        ScrollView {
            content.onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h = $0 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(T.sheet)
        .presentationDetents([.height(max(180, h + 12))])
        .grabber()
        .presentationBackground(T.sheet)
    }
}

extension View {
    func fitSheet() -> some View { modifier(FitSheet()) }
}

/** A rounded group of rows on a sheet (as on Android). */
struct SCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(spacing: 0) { content() }
            .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(T.container))
    }
}

/** The thin line between rows in an SCard, starting after the icon. */
struct SLine: View { var inset: CGFloat = 64; var body: some View { Rectangle().fill(T.line).frame(height: 1).padding(.leading, inset) } }

/** A row: icon tile, title and a smaller line, what's set, and a chevron or tick. */
struct SRow: View {
    let symbol: String
    let title: String
    var sub: String? = nil
    var value: String? = nil
    var tile: Color? = nil
    var trailing: String? = "chevron.right"
    var trailingTint: Color = T.sub
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol).font(.system(size: 16, weight: .medium)).foregroundStyle(tile == nil ? T.ink : .white)
                    .frame(width: 34, height: 34).background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(tile ?? T.high))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 17)).foregroundStyle(T.ink)
                    if let sub { Text(sub).font(.system(size: 13)).foregroundStyle(T.sub).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 8)
                if let value { Text(value).font(.system(size: 14)).foregroundStyle(T.sub).lineLimit(1) }
                if let trailing { Image(systemName: trailing).font(.system(size: 13, weight: .semibold)).foregroundStyle(trailingTint) }
            }
            .padding(.horizontal, 16).padding(.vertical, 15).frame(minHeight: 72)
            .contentShape(Rectangle())
        }
        .buttonStyle(Pressable())
    }
}

/** An on / off row with an icon tile: the whole row is the switch. */
struct SToggle: View {
    let symbol: String
    let title: String
    var sub: String? = nil
    let accent: Color
    @Binding var on: Bool
    var body: some View {
        Toggle(isOn: $on) {
            HStack(spacing: 14) {
                Image(systemName: symbol).font(.system(size: 16, weight: .medium)).foregroundStyle(T.ink)
                    .frame(width: 34, height: 34).background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(T.high))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 17)).foregroundStyle(T.ink)
                    if let sub { Text(sub).font(.system(size: 13)).foregroundStyle(T.sub).fixedSize(horizontal: false, vertical: true) }
                }
            }
        }
        .tint(accent)
        .padding(.horizontal, 16).padding(.vertical, 15).frame(minHeight: 72)
    }
}

/** A grey heading over a group of cards ("Remote", "Appearance"…). */
struct SHead: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 17, weight: .semibold)).foregroundStyle(T.sub)
            .frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 20).padding(.top, 14).padding(.bottom, -4)
    }
}

/** A small grey note under a card. */
struct SNote: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 13)).foregroundStyle(T.sub).lineSpacing(2)
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 20).padding(.top, -6)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/** A sheet's title with its line of explanation close under it, on the left (or centred). */
struct SheetTitle: View {
    let title: String
    var sub: String? = nil
    var center = false
    var small = false
    var body: some View {
        VStack(alignment: center ? .center : .leading, spacing: 6) {
            Text(title).font(.system(size: small ? 18 : 22, weight: .semibold)).foregroundStyle(T.ink)
            if let sub { Text(sub).font(.system(size: small ? 13 : 14)).foregroundStyle(T.sub).lineSpacing(2).multilineTextAlignment(center ? .center : .leading).fixedSize(horizontal: false, vertical: true) }
        }
        .frame(maxWidth: .infinity, alignment: center ? .center : .leading)
        .padding(.bottom, 6)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/**
 * More: quick access (when it lives here), the keys not on Home's pages (subtitles, audio,
 * channel list, menu, the colour keys), the TV's inputs by name, and arrows to move around
 * the TV without leaving the sheet.
 */
struct MoreSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    var openApps: (Bool) -> Void = { _ in }

    private var what: String? {
        switch vm.screen {
        case .home: return "On the home screen: playback and channel keys are off."
        case .app: return "In an app: channel keys are off."
        case .live: return "On live TV."
        case .unknown: return nil
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
                SheetTitle(title: "More", sub: what).padding(.top, 28)

                if vm.settings.appsIn == "more" {
                    SectionTitle(text: "Quick access")
                    AppSquares(vm: vm, openApps: openApps)
                }

                // Keys not on Home's pages, then the colour keys.
                HStack(spacing: 10) {
                    tile(.subtitles, "captions.bubble", "Subtitles")
                    tile(.audio, "waveform", "Audio")
                    tile(.chList, "list.bullet", "Channels")
                    tile(.menu, "ellipsis.rectangle", "Menu")
                }
                .padding(.top, 22)
                HStack(spacing: 12) {
                    ForEach([(RemoteKey.red, Color(hex: 0xE5484D)), (.green, Color(hex: 0x30A46C)), (.yellow, Color(hex: 0xF5D90A)), (.blue, Color(hex: 0x3E63DD))], id: \.0) { k, c in
                        let on = vm.can(k)
                        Button { if on { vm.press(k) } else { vm.explain(k) } } label: { Capsule().fill(on ? c : c.opacity(0.25)).frame(height: 30) }
                            .buttonStyle(.plain).accessibilityLabel(k.word)
                    }
                }
                .padding(.top, 22)

                if !vm.inputs.isEmpty {
                    SectionTitle(text: "Inputs")
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 12) {
                        ForEach(vm.inputs) { i in
                            let lower = i.name.lowercased()
                            let now = (vm.nowOn?.name ?? "").lowercased()
                            let current = !now.isEmpty && (lower == now || lower.hasPrefix(now))
                            Button { vm.switchInput(i) } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: lower.contains("hdmi") || lower.contains("av") ? "cable.connector" : "tv").font(.system(size: 15))
                                    Text(i.name).font(.system(size: 14)).lineLimit(1)
                                    Spacer(minLength: 0)
                                    if current { Image(systemName: "checkmark").font(.system(size: 13, weight: .semibold)) }
                                }
                                .foregroundStyle(T.ink).padding(.horizontal, 12).frame(height: 48)
                                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(current ? T.ink.opacity(0.14) : T.high))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                // Move around the TV and select without going back.
                NavPad(vm: vm).padding(.top, 24)
            }
            .padding(.horizontal, 24).padding(.bottom, 20)
        .fitSheet()
        .onAppear { vm.loadInputs() }
    }

    /** A labelled key tile: icon over a word, centred; dimmed (and explaining why) when it doesn't fit. */
    private func tile(_ k: RemoteKey, _ sym: String, _ label: String) -> some View {
        let on = vm.can(k)
        return Button { if on { vm.press(k) } else { vm.explain(k) } } label: {
            VStack(spacing: 3) {
                Image(systemName: sym).font(.system(size: 19)).foregroundStyle(T.ink.opacity(on ? 1 : 0.3))
                Text(label).font(.system(size: 12)).foregroundStyle(T.muted.opacity(on ? 1 : 0.4)).lineLimit(1)
            }
            .frame(maxWidth: .infinity).frame(height: 68)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(T.high))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/** Quick access in More: rounded squares the size of the key tiles; two pages to swipe (no dots). */
struct AppSquares: View {
    @ObservedObject var vm: RemoteModel
    let openApps: (Bool) -> Void
    var body: some View {
        let pinned = vm.shortcuts.filter(\.pinned)
        let ready = vm.active != nil && vm.link == .ready
        TabView {
            ForEach(0..<2, id: \.self) { part in
                let cells = AppsPage.cells(pinned.count, part)
                VStack(spacing: 14) {
                    ForEach(0..<2, id: \.self) { r in
                        HStack(spacing: 10) {
                            ForEach(0..<4, id: \.self) { c in
                                let cell = cells[r * 4 + c]
                                if cell == -1 {
                                    Button { openApps(false) } label: {
                                        VStack(spacing: 3) {
                                            Image(systemName: "square.grid.2x2").font(.system(size: 19))
                                            Text("All apps").font(.system(size: 12)).foregroundStyle(T.muted)
                                        }
                                        .foregroundStyle(T.ink).frame(maxWidth: .infinity).frame(height: 68)
                                        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(T.high))
                                    }
                                    .buttonStyle(.plain)
                                } else if let i = cell {
                                    let s = pinned[i]
                                    AppTile(s: s, size: 68, square: true)
                                        .onTapGesture { if ready { vm.launch(s) } else { vm.explain(.home) } }
                                } else {
                                    Button { openApps(true) } label: {
                                        Image(systemName: "plus").font(.system(size: 15)).foregroundStyle(T.ink.opacity(0.3))
                                            .frame(maxWidth: .infinity).frame(height: 68)
                                            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(T.ink.opacity(0.1), lineWidth: 1))
                                    }
                                    .buttonStyle(.plain).accessibilityLabel("Add apps")
                                }
                            }
                        }
                    }
                }
                .tag(part)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: 68 * 2 + 14)
        .padding(.top, 4)
    }
}

/** The sleep timer on its own: a picture, a short line, and the choices (or the countdown). */
struct SleepSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle().fill(RadialGradient(colors: [accent.opacity(0.28), .clear], center: .center, startRadius: 0, endRadius: 70))
                    .frame(width: 140, height: 140).offset(x: 26, y: -18)
                Image(systemName: "moon.stars.fill").font(.system(size: 38)).foregroundStyle(accent).offset(x: 30, y: -26)
                Image(systemName: "tv").font(.system(size: 64, weight: .light)).foregroundStyle(T.ink.opacity(0.75)).offset(y: 18)
                Text("z z").font(.system(size: 13, weight: .bold)).foregroundStyle(T.ink.opacity(0.6)).offset(x: -4, y: 12)
            }
            .frame(height: 120).padding(.top, 28).padding(.bottom, 18)
            .accessibilityHidden(true)
            SheetTitle(title: "Sleep timer", sub: "When the TV turns off.", center: true, small: true).padding(.bottom, 8)
            if let at = vm.sleepAt {
                TimelineView(.periodic(from: .now, by: 1)) { t in
                    let left = max(0, Int(at.timeIntervalSince(t.date)))
                    HStack(spacing: 14) {
                        Image(systemName: "moon.zzz.fill").foregroundStyle(accent).frame(width: 44, height: 44).background(Circle().fill(accent.opacity(0.2)))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(String(format: "%d:%02d", left / 60, left % 60)).font(.system(size: 26, weight: .semibold, design: .monospaced)).foregroundStyle(T.ink)
                            Text("TV turns off at \(at.formatted(date: .omitted, time: .shortened))").font(.system(size: 13)).foregroundStyle(T.muted)
                        }
                        Spacer()
                        Button("Cancel") { vm.setSleep(nil) }.buttonStyle(.bordered).tint(T.ink)
                    }
                    .padding(.top, 12)
                }
                Text("Keep Mini Remote open (it can be in the background).").font(.system(size: 12)).foregroundStyle(T.muted).padding(.top, 10)
            }
            HStack(spacing: 8) {
                ForEach([15, 30, 60, 90], id: \.self) { m in
                    Button { vm.setSleep(m) } label: {
                        Text(m < 60 ? "\(m) min" : (m % 60 == 0 ? "\(m / 60) h" : "1.5 h")).font(.system(size: 13, weight: .medium)).foregroundStyle(T.ink)
                            .frame(maxWidth: .infinity).frame(height: 40)
                    }
                    .buttonStyle(Pressable()).background(Capsule().fill(T.high))
                    .disabled(!vm.can(.power))
                }
            }
            .padding(.top, 16)
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .fitSheet()
    }
}
