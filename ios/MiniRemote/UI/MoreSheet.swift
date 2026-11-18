import SwiftUI

/** A sheet's title with its line of explanation close under it, on the left (or centred). */
struct SheetTitle: View {
    let title: String
    var sub: String? = nil
    var center = false
    var small = false
    var body: some View {
        VStack(alignment: center ? .center : .leading, spacing: 2) {
            Text(title).font(.system(size: small ? 17 : 20, weight: .semibold)).foregroundStyle(T.ink)
            if let sub { Text(sub).font(.system(size: small ? 12.5 : 13)).foregroundStyle(T.sub).multilineTextAlignment(center ? .center : .leading) }
        }
        .frame(maxWidth: .infinity, alignment: center ? .center : .leading)
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
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                SheetTitle(title: "More", sub: what).padding(.top, 22)

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
            }
            .padding(.horizontal, 24).padding(.bottom, 16)
        }
        // Always at hand, under the keys: move around the TV and select without going back.
        .safeAreaInset(edge: .bottom) { NavPad(vm: vm).padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 8).background(T.sheet) }
        .background(T.sheet)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .onAppear { vm.loadInputs() }
    }

    /** A labelled key tile: icon over a word, centred; dimmed (and explaining why) when it doesn't fit. */
    private func tile(_ k: RemoteKey, _ sym: String, _ label: String) -> some View {
        let on = vm.can(k)
        return Button { if on { vm.press(k) } else { vm.explain(k) } } label: {
            VStack(spacing: 3) {
                Image(systemName: sym).font(.system(size: 19)).foregroundStyle(T.ink.opacity(on ? 1 : 0.3))
                Text(label).font(.system(size: 11)).foregroundStyle(T.muted.opacity(on ? 1 : 0.4)).lineLimit(1)
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
                                            Text("All apps").font(.system(size: 11)).foregroundStyle(T.muted)
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
            SheetTitle(title: "Sleep timer", sub: "Pick when the TV turns itself off.", center: true, small: true).padding(.bottom, 8)
            if let at = vm.sleepAt {
                TimelineView(.periodic(from: .now, by: 1)) { t in
                    let left = max(0, Int(at.timeIntervalSince(t.date)))
                    HStack(spacing: 14) {
                        Image(systemName: "moon.zzz.fill").foregroundStyle(accent).frame(width: 44, height: 44).background(Circle().fill(accent.opacity(0.2)))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(String(format: "%d:%02d", left / 60, left % 60)).font(.system(size: 26, weight: .semibold, design: .monospaced)).foregroundStyle(T.ink)
                            Text("TV turns off at \(at.formatted(date: .omitted, time: .shortened))").font(.system(size: 12.5)).foregroundStyle(T.muted)
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
                    .buttonStyle(.plain).glassEffect(.regular.interactive(), in: .capsule)
                    .disabled(!vm.can(.power))
                }
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24).padding(.bottom, 20)
        .background(T.sheet)
        .presentationDetents([.height(vm.sleepAt == nil ? 360 : 460)])
        .presentationDragIndicator(.visible)
    }
}
