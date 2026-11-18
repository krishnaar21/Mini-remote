import Foundation
import SwiftUI
import UIKit
import AVFoundation

/** The app's state and actions; the iOS twin of the Android RemoteVM. */
@MainActor
final class RemoteModel: ObservableObject {
    private let store = Store()
    let feel = FeelEngine()

    @Published private(set) var settings: Settings
    @Published private(set) var devices: [Device]
    @Published private(set) var activeId: String?
    @Published private(set) var link: Link = .idle
    @Published private(set) var toast: String?
    @Published private(set) var toastKey: RemoteKey?
    @Published private(set) var scanning = false
    @Published private(set) var found: [Device] = []
    @Published private(set) var lastError: String?
    @Published private(set) var txCount = 0 { didSet { lastTxAt = Date() } }
    /** When the app last sent something to the TV (to tell its text boxes from the physical remote's). */
    private var lastTxAt = Date.distantPast
    /** When you last pressed, turned or typed anything here (menus close 10 s after). */
    private var userAt = Date.distantPast
    /** Rewind / forward: Play / Pause within 10 s confirms the seek with OK (Android TV). */
    private var seekAt = Date.distantPast
    /** A TV menu opened from here: when it closes unless used again, and what was on screen behind it. */
    private var menuUntil: Date?
    private var menuBehind: String?
    private var menuTask: Task<Void, Never>?
    @Published private(set) var muted = false
    @Published private(set) var playing = false
    @Published private(set) var pairPrompt: PairPrompt?
    @Published private(set) var shortcuts: [Shortcut]
    @Published private(set) var tvApps: [TvApp] = []
    @Published private(set) var appsLoading = false
    @Published private(set) var nowOn: NowOn?
    /** The TV's own volume, when it tells us (Android TV, LG, most Samsung). */
    @Published private(set) var tvVolume: TvVolume?
    /** Bumps when the TV asks for a keyboard: the Search sheet opens. */
    @Published var keyboardAsk = 0
    /** The TV's inputs, for the More sheet. */
    @Published private(set) var inputs: [TvInput] = []
    /** Sleep timer: when the TV turns off. */
    @Published private(set) var sleepAt: Date?
    var sleepTask: Task<Void, Never>?
    var voiceSession: (engine: AVAudioEngine, id: UInt64, adapter: AndroidTvAdapter, buffer: VoiceBuffer)?
    /** Voice is streaming to the TV. */
    @Published var listening = false
    /** The TV didn't take the phone's voice: Search opens in Voice mode instead. */
    @Published var voiceTyping = 0

    let maxPinned = 15
    var active: Device? { devices.first { $0.id == activeId } }
    var localSeen: Bool { store.localSeen }

    private var adapter: RemoteAdapter?
    private var connectTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var nowTask: Task<Void, Never>?
    private var codeAnswer: CheckedContinuation<String?, Never>?
    private var appsFor: String?
    private var keys: AsyncStream<RemoteKey>.Continuation?
    private var textOps: AsyncStream<String>.Continuation?
    private var volTask: Task<Void, Never>?
    private var redials = 0

    init() {
        settings = store.loadSettings()
        devices = store.loadDevices()
        activeId = store.activeId
        shortcuts = store.loadShortcuts()
        applyFeel()
        let stream = AsyncStream<RemoteKey>(bufferingPolicy: .bufferingNewest(64)) { self.keys = $0 }
        Task { [weak self] in
            for await k in stream {
                guard let self else { return }
                guard self.link == .ready, let a = self.adapter else { continue }
                do { try await a.send(k); self.txCount += 1 } catch {
                    if let u = error as? UserFacing, u.unsupported { self.feel.fire(.error); self.say(u.message); continue }
                    // The socket died under us: reconnect quietly, then send this key again.
                    guard let d = self.active else { continue }
                    self.adapter = nil; self.redials = 0
                    self.connect(d, quiet: true)
                    var waited = 0
                    while self.link != .ready && waited < 120 { try? await Task.sleep(nanoseconds: 100_000_000); waited += 1 }
                    if self.link == .ready, let b = self.adapter, (try? await b.send(k)) != nil { self.txCount += 1 }
                    else { self.feel.fire(.error); self.say("Lost connection") }
                }
            }
        }
        let ops = AsyncStream<String>(bufferingPolicy: .unbounded) { self.textOps = $0 }
        Task { [weak self] in
            var warned = false
            for await op in ops {
                guard let self, self.link == .ready, let a = self.adapter else { continue }
                do {
                    if op.hasPrefix("\u{1}") { try await a.setText(String(op.dropFirst())) }
                    else if op == "\u{8}" { try await a.backspace() }
                    else if op == "\u{2}" { try await a.submit() }
                    else if op.hasPrefix("\u{3}") { try await a.clearBox(Int(op.dropFirst()) ?? 0) }
                    else { try await a.typeText(op) }
                    self.txCount += 1
                } catch {
                    if !warned { warned = true; self.say((error as? UserFacing)?.message ?? "Couldn't type") }
                }
            }
        }
        if let d = active { connect(d) }
        UIApplication.shared.isIdleTimerDisabled = settings.keepAwake
    }

    /* ------------------------------------------------------------ keys */

    func press(_ key: RemoteKey, quiet: Bool = false) {
        if demo {
            feel.fire(.press)
            if key == .mute { muted.toggle() }; if key == .playPause { playing.toggle() }
            nudgeVolume(key); if !quiet { say(key.word, key) }; return
        }
        guard let d = active else { feel.fire(.error); say("Choose a TV first"); return }
        if link == .error { connect(d) }
        feel.fire(key == .power ? .heavy : .press)
        if key == .mute { muted.toggle() }
        if key == .volUp { muted = false }
        if key == .playPause { playing.toggle() }
        nudgeVolume(key)
        if !quiet { say(key == .playPause ? (playing ? "Play" : "Pause") : key.word, key) }
        userAt = Date()
        watchMenu(key)
        // Android TV players hold a rewind / forward in a seek preview until it's confirmed, and
        // ignore Play / Pause meanwhile: within 10 s of seeking, Play / Pause sends OK instead.
        if key == .rewind || key == .fastForward { seekAt = Date() }
        if key == .playPause, d.brand == .ANDROID_TV, Date().timeIntervalSince(seekAt) < 10 {
            seekAt = .distantPast; playing = true; keys?.yield(.ok); return
        }
        keys?.yield(key)
    }

    func scrubTick(_ key: RemoteKey) { userAt = Date(); menuUsed(); feel.fire(.tick); nudgeVolume(key); if !demo { keys?.yield(key) } }

    /** The guided tour's pretend TV: the remote as it is when connected; nothing is sent or saved. */
    @Published private(set) var demo = false

    func startDemo() {
        guard !demo else { return }
        disconnect()
        demo = true
        devices = [Device(id: "demo", name: "Demo TV", host: "192.168.1.20", brand: .ANDROID_TV)]
        activeId = "demo"
        link = .ready
        tvVolume = TvVolume(level: 24, max: 100, muted: false)
        nowOn = NowOn(name: "Netflix", live: false, positionMs: 1_204_000, durationMs: 2_710_000)
    }

    /** Back to the real app: your saved TVs (none if you have none yet), connecting as usual. */
    func endDemo() {
        guard demo else { return }
        demo = false
        link = .idle; tvVolume = nil; nowOn = nil; muted = false; playing = false
        devices = store.loadDevices(); activeId = store.activeId
        if let d = active { connect(d) }
    }

    /* ------------------------------------------------- idle TV menus */

    private static let menuKeys: Set<RemoteKey> = [.settings, .input, .menu, .info, .guide, .chList, .subtitles, .audio]

    /**
     * A TV menu opened from here (settings, quick settings, inputs…) closes itself: 10 s with
     * nothing done in the app and Back is pressed once. Back, Home or Power end the watch, and
     * so does the screen behind changing (an input or app was picked and took the menu with it).
     */
    private func watchMenu(_ key: RemoteKey) {
        if !settings.autoClose || key == .back || key == .home || key == .power { menuUntil = nil; menuTask?.cancel(); menuTask = nil; return }
        if Self.menuKeys.contains(key) {
            menuBehind = nowOn?.name
            menuUntil = Date().addingTimeInterval(10)
            if menuTask == nil {
                menuTask = Task { [weak self] in
                    while let self, let until = self.menuUntil, !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        if let b = self.menuBehind, let n = self.nowOn?.name, n != b { self.menuUntil = nil; break }
                        if Date() >= until, self.menuUntil == until {
                            self.menuUntil = nil
                            if self.link == .ready { self.keys?.yield(.back); self.say("Closed the TV's menu (unused for 10 s)") }
                        }
                    }
                    self?.menuTask = nil
                }
            }
        } else { menuUsed() }
    }

    private func menuUsed() { if menuUntil != nil { menuUntil = Date().addingTimeInterval(10) } }

    /** Moves the shown volume at once, then asks the TV for the real value a moment later. */
    private func nudgeVolume(_ key: RemoteKey) {
        guard key == .volUp || key == .volDown || key == .mute else { return }
        if var v = tvVolume {
            switch key {
            case .volUp: v.level = min(v.max, v.level + 1); v.muted = false
            case .volDown: v.level = max(0, v.level - 1)
            default: v.muted.toggle()
            }
            tvVolume = v
        }
        refreshVolume(after: 0.7)
    }

    private func refreshVolume(after: Double) {
        volTask?.cancel()
        volTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(after * 1e9))
            guard !Task.isCancelled, let self, let a = self.adapter, let v = await a.readVolume() else { return }
            self.tvVolume = v; self.muted = v.muted
        }
    }

    /** Mirrors an edit of the search box on the TV: deletes what changed, types what's new. */
    func liveType(_ old: String, _ new: String) {
        guard active != nil else { say("Choose a TV first"); return }
        userAt = Date(); menuUsed()
        // Android TV takes the whole box at once (any characters).
        if adapter?.replacesText == true { textOps?.yield("\u{1}" + new); return }
        let o = Array(old), n = Array(new)
        var common = 0
        while common < o.count && common < n.count && o[common] == n[common] { common += 1 }
        for _ in 0..<(o.count - common) { textOps?.yield("\u{8}") }
        if n.count > common { textOps?.yield(String(n[common...])) }
    }

    func say(_ t: String, _ key: RemoteKey? = nil) {
        toast = t; toastKey = key
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(1.1e9 + 45e6 * Double(min(t.count, 80))))
            if Task.isCancelled { return }
            self?.toast = nil; self?.toastKey = nil
        }
    }

    /* ------------------------------------------------------- connection */

    /** The link broke by itself: reconnect quietly, a few tries, waiting longer each time. */
    private func dropped(_ d: Device, _ a: RemoteAdapter) {
        guard adapter === a, activeId == d.id else { return }
        adapter = nil
        guard redials < 4 else { link = .error; say("Lost connection to \(d.name)"); return }
        let wait = [0.3, 1.5, 4, 8][redials]
        redials += 1
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1e9))
            guard let self, self.activeId == d.id, self.adapter == nil else { return }
            self.connect(d, quiet: true)
        }
    }

    func connect(_ d: Device) { connect(d, quiet: false) }

    private func connect(_ d0: Device, quiet: Bool) {
        // Always the saved copy: a reconnect must carry the pin and token saved since (or the TV asks to pair again).
        let d = devices.first { $0.id == d0.id } ?? d0
        if d.id != appsFor { tvApps = []; appsFor = nil }
        nowOn = nil
        connectTask?.cancel()
        adapter?.close(); adapter = nil
        link = .connecting
        lastError = nil
        connectTask = Task { [weak self] in
            guard let self else { return }
            do {
                let a = try adapterFor(d,
                    onToken: { tok in Task { @MainActor in self.updateDevice(d.id) { $0.token = tok } } },
                    onPin: { pin in Task { @MainActor in self.updateDevice(d.id) { $0.pin = pin } } },
                    askCode: { err in await self.askCode(d.name, err) })
                self.tvVolume = nil
                a.onVolume = { v in Task { @MainActor in self.tvVolume = v; self.muted = v.muted } }
                a.onDropped = { [weak a] in Task { @MainActor in if let a { self.dropped(d, a) } } }
                // A text box got focus on the TV: the phone keyboard opens if the phone is what's
                // driving it (something sent from here in the last 20 s), not the physical remote.
                (a as? AndroidTvAdapter)?.onKeyboard = { Task { @MainActor in
                    if self.settings.autoKeyboard && Date().timeIntervalSince(self.lastTxAt) < 20 { self.keyboardAsk += 1 }
                } }
                try await a.connect()
                if Task.isCancelled { a.close(); return }
                self.adapter = a
                self.link = .ready
                self.redials = 0
                self.refreshVolume(after: 0)
                // Remember the TV's network address so it can be woken later.
                if d.mac == nil { Task { if let m = await a.macAddress() { self.updateDevice(d.id) { $0.mac = m } } } }
                self.fetchName(d)
                if !quiet { self.feel.fire(.success) }
            } catch {
                if Task.isCancelled { return }
                if quiet && self.redials < 4 && !(error is CertChanged) && !(error is UserFacing) {
                    let wait = [0.3, 1.5, 4, 8][self.redials]
                    self.redials += 1
                    try? await Task.sleep(nanoseconds: UInt64(wait * 1e9))
                    if self.activeId == d.id { self.connect(d, quiet: true) }
                    return
                }
                self.link = .error
                let msg: String
                if let u = error as? UserFacing { msg = u.message }
                else if error is CertChanged { msg = "\(d.name)'s certificate changed. Remove and add it again if you reset it." }
                else { msg = "Couldn't reach \(d.name)" }
                self.lastError = msg
                self.say(msg)
                self.feel.fire(.error)
            }
        }
    }

    func disconnect() {
        connectTask?.cancel()
        adapter?.close(); adapter = nil
        codeAnswer?.resume(returning: nil); codeAnswer = nil
        pairPrompt = nil
        link = .idle
    }

    private func askCode(_ tvName: String, _ error: String?) async -> String? {
        await withCheckedContinuation { (c: CheckedContinuation<String?, Never>) in
            codeAnswer?.resume(returning: nil)
            codeAnswer = c
            pairPrompt = PairPrompt(tvName: tvName, error: error)
        }
    }

    func answerCode(_ code: String?) {
        let c = codeAnswer
        codeAnswer = nil
        pairPrompt = nil
        c?.resume(returning: code)
    }

    func select(_ d: Device) { activeId = d.id; store.activeId = d.id; connect(d) }

    private func updateDevice(_ id: String, _ change: (inout Device) -> Void) {
        devices = devices.map { var d = $0; if d.id == id { change(&d) }; return d }
        store.saveDevices(devices)
    }

    func addAndSelect(_ d: Device) {
        devices = devices.filter { $0.id != d.id } + [d]
        store.saveDevices(devices)
        found = found.filter { $0.host != d.host }
        select(d)
    }

    func addManual(_ host: String, _ brand: Brand) -> (String?, Device?) {
        let h = host.trimmingCharacters(in: .whitespaces)
        guard HostValidator.isPrivateIPv4(h) else { return ("Enter a home-network address like 192.168.1.20.", nil) }
        let d = Device(id: "\(brand.rawValue.lowercased())-\(h)", name: brand.label, host: h, brand: brand)
        addAndSelect(d)
        fetchName(d)
        return (nil, d)
    }

    /** Replaces a placeholder name ("LG TV", "Google TV"…) with the name set on the TV, when it says. */
    func fetchName(_ d: Device) {
        guard Discovery.generic(d.name) else { return }
        Task { [weak self] in
            guard let n = await Discovery.nameOf(d.host, d.brand), let self,
                  let cur = self.devices.first(where: { $0.id == d.id }), Discovery.generic(cur.name) else { return }
            self.updateDevice(d.id) { $0.name = n }
        }
    }

    func rename(_ id: String, _ name: String) {
        let n = String(name.trimmingCharacters(in: .whitespaces).prefix(30))
        if !n.isEmpty { updateDevice(id) { $0.name = n } }
    }

    func remove(_ d: Device) {
        devices = devices.filter { $0.id != d.id }
        store.saveDevices(devices)
        if activeId == d.id { disconnect(); activeId = nil; store.activeId = nil }
    }

    func markLocalSeen() { store.localSeen = true }

    func scan() {
        guard !scanning else { return }
        scanning = true; found = []
        let hosts = Set(devices.map(\.host))
        Task { [weak self] in
            // Each TV shows up as soon as it's found.
            let list = await Discovery.scan { d in
                Task { @MainActor in
                    guard let self, !hosts.contains(d.host), !self.found.contains(where: { $0.host == d.host }) else { return }
                    self.found.append(d)
                }
            }
            guard let self else { return }
            self.found = list.filter { !hosts.contains($0.host) }
            self.scanning = false
        }
    }

    /* ------------------------------------------------------- run on TV */

    private func act(_ fail: String, _ block: @escaping (RemoteAdapter) async throws -> Void) {
        if demo { say("Demo: nothing is sent to a TV"); return }
        guard let d = active else { feel.fire(.error); say("Choose a TV first"); return }
        guard let a = adapter, link == .ready else {
            feel.fire(.error)
            if link == .connecting { say("Still connecting to \(d.name)") } else { say("Reconnecting to \(d.name)"); connect(d) }
            return
        }
        Task { [weak self] in
            do { try await block(a); self?.txCount += 1 } catch {
                self?.feel.fire(.error)
                self?.say((error as? UserFacing)?.message ?? fail)
            }
        }
    }

    /** Opens the TV's own search screen where the TV has a key for it (Roku, Android TV). */
    func openTvSearch() {
        // Google TV's Search key starts the voice assistant, not a text box: Roku only.
        guard let a = adapter, link == .ready, active?.brand == .ROKU else { return }
        Task { try? await a.send(.voice) }
    }

    /** Android TV: search an app straight from the phone (YouTube, Play Store). */
    var searchesApps: Bool { active?.brand == .ANDROID_TV }

    func searchIn(_ whereTo: String, _ q: String) {
        let s = q.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty, let e = s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) else { return }
        let link = whereTo == "youtube" ? "https://www.youtube.com/results?search_query=\(e)" : "market://search?q=\(e)&c=apps"
        say("Searching \(whereTo == "youtube" ? "YouTube" : "Play Store") for \(s)")
        act("Couldn't search") { try await $0.launchApp(link) }
    }

    /** Runs the search: Roku searches for it; other TVs press Enter in their box (after the typing). */
    func submitSearch(_ text: String) {
        guard active != nil else { say("Choose a TV first"); return }
        if active?.brand == .ROKU { search(text); return }
        textOps?.yield("\u{2}")
    }

    /** Clears the TV's box: all of it, not only what was typed from here. */
    func clearTvBox(_ typed: Int) { if active != nil { textOps?.yield("\u{3}\(typed)") } }

    func typeText(_ t: String) { act("Couldn't type") { try await $0.typeText(t) }; feel.fire(.success) }
    func backspace() { act("Couldn't delete") { try await $0.backspace() } }
    func search(_ q: String) {
        let s = q.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return }
        say("Searching for \(s)")
        act("Couldn't search") { try await $0.search(s) }
    }

    func followNowOn(_ on: Bool) {
        nowTask?.cancel(); nowTask = nil
        guard on else { return }
        nowTask = Task { [weak self] in
            while !Task.isCancelled {
                if let self, let a = self.adapter, self.link == .ready {
                    do { self.nowOn = try await a.nowOn() } catch {
                        if let u = error as? UserFacing, u.unsupported { self.nowOn = nil; return }
                    }
                }
                try? await Task.sleep(nanoseconds: 3_500_000_000)
            }
        }
    }

    /* ---------------------------------------------------- apps, channels */

    func refreshApps(force: Bool = false) {
        guard let a = adapter, let id = activeId, link == .ready else { if force { say("Connect a TV first") }; return }
        if appsLoading { if force { say("Loading…") }; return }
        if !force && appsFor == id { return }
        appsLoading = true
        let before = tvApps
        Task { [weak self] in
            guard let self else { return }
            do {
                self.tvApps = try await a.listApps().sorted { $0.name.lowercased() < $1.name.lowercased() }
                self.appsFor = id
                if force { self.say(self.tvApps == before ? "Up to date" : "\(self.tvApps.count) apps") }
            } catch {
                self.appsFor = id
                if force { self.say((error as? UserFacing)?.unsupported == true ? "Up to date" : "Couldn't load apps") }
            }
            self.appsLoading = false
        }
    }

    func launchApp(_ name: String, _ id: String) {
        feel.fire(.press); say(name)
        act("Couldn't open \(name)") { try await $0.launchApp(id) }
    }

    func launchCatalog(_ c: CatalogApp) {
        guard let brand = active?.brand else { feel.fire(.error); say("Choose a TV first"); return }
        let id = (brand == .ANDROID_TV ? c.link : nil) ?? c.ids[brand]
            ?? tvApps.first { $0.name.caseInsensitiveCompare(c.name) == .orderedSame || $0.name.localizedCaseInsensitiveContains(c.name) }?.id
        guard let id else { feel.fire(.error); say("\(c.name) isn't set up for this TV. Add it with its app ID."); return }
        launchApp(c.name, id)
    }

    func openStore(_ name: String, _ id: String) {
        feel.fire(.press); say("Opening \(name) in the TV's store")
        act("Couldn't open the store") { try await $0.openStore(id) }
    }

    func installed(_ c: CatalogApp) -> Bool? {
        guard let brand = active?.brand, !tvApps.isEmpty else { return nil }
        let id = c.ids[brand]
        return tvApps.contains { $0.id == id || $0.name.caseInsensitiveCompare(c.name) == .orderedSame }
    }

    func tune(_ name: String, _ number: String) {
        feel.fire(.press); say("\(number) · \(name)")
        act("Couldn't change the channel") { try await $0.tuneChannel(number) }
    }

    func launch(_ s: Shortcut) {
        if s.kind == .channel { tune(s.name, s.value); return }
        guard let b = s.brand else {
            if let c = Catalog.byKey(s.value) { launchCatalog(c) } else { say("\(s.name) isn't available any more") }
            return
        }
        if active?.brand != b { feel.fire(.error); say("\(s.name) was saved from a \(b.label) TV"); return }
        launchApp(s.name, s.value)
    }

    private func saveShortcuts(_ l: [Shortcut]) { shortcuts = l; store.saveShortcuts(l) }
    func replaceShortcuts(_ l: [Shortcut]) { saveShortcuts(l) }

    private func roomToPin() -> Bool {
        if shortcuts.filter(\.pinned).count < maxPinned { return true }
        feel.fire(.error); say("Quick access is full (\(maxPinned)). Remove one first.")
        return false
    }

    func addShortcut(_ kind: ShortcutKind, _ name: String, _ value: String, pin pin0: Bool) {
        let pin = pin0 && shortcuts.filter(\.pinned).count < maxPinned
        let s = Shortcut(id: "s\(Int(Date().timeIntervalSince1970 * 1000))", kind: kind, name: String(name.trimmingCharacters(in: .whitespaces).prefix(30)),
                         value: value.trimmingCharacters(in: .whitespaces), brand: kind == .app ? active?.brand : nil, pinned: pin, added: true)
        saveShortcuts(shortcuts + [s])
        feel.fire(.success); say(pin0 && !pin ? "Added (quick access is full)" : "Added \(s.name)")
    }

    func pinnedCatalog(_ c: CatalogApp) -> Shortcut? { shortcuts.first { $0.kind == .app && $0.brand == nil && $0.value == c.key } }
    func pinnedTvApp(_ a: TvApp) -> Shortcut? { shortcuts.first { $0.kind == .app && $0.brand == active?.brand && $0.value == a.id } }

    func pinCatalog(_ c: CatalogApp) {
        guard pinnedCatalog(c) == nil else { say("Already in quick access"); return }
        guard roomToPin() else { return }
        saveShortcuts(shortcuts + [Shortcut(id: "c\(Int(Date().timeIntervalSince1970 * 1000))", kind: .app, name: c.name, value: c.key, pinned: true)])
        say("\(c.name) added to quick access")
    }

    func pinTvApp(_ a: TvApp) {
        guard let b = active?.brand else { return }
        guard pinnedTvApp(a) == nil else { say("Already in quick access"); return }
        guard roomToPin() else { return }
        saveShortcuts(shortcuts + [Shortcut(id: "t\(Int(Date().timeIntervalSince1970 * 1000))", kind: .app, name: a.name, value: a.id, brand: b, icon: a.icon, pinned: true)])
        say("\(a.name) added to quick access")
    }

    func setPinned(_ s: Shortcut, _ pinned: Bool) {
        if pinned && !s.pinned && !roomToPin() { return }
        saveShortcuts(!pinned && !s.added ? shortcuts.filter { $0.id != s.id } : shortcuts.map { var x = $0; if x.id == s.id { x.pinned = pinned }; return x })
        say(pinned ? "\(s.name) added to quick access" : "\(s.name) removed from quick access")
    }

    func removeShortcut(_ s: Shortcut) { saveShortcuts(shortcuts.filter { $0.id != s.id }); say("Removed \(s.name)") }

    func movePinned(_ s: Shortcut, _ by: Int) {
        var pinned = shortcuts.filter(\.pinned)
        guard let i = pinned.firstIndex(where: { $0.id == s.id }), pinned.indices.contains(i + by) else { return }
        let item = pinned.remove(at: i); pinned.insert(item, at: i + by)
        saveShortcuts(pinned + shortcuts.filter { !$0.pinned })
    }

    /* ---------------------------------------------------------- settings */

    func update(_ s: Settings) {
        settings = s
        store.saveSettings(s)
        applyFeel()
        UIApplication.shared.isIdleTimerDisabled = s.keepAwake
    }

    private func applyFeel() {
        feel.haptics = settings.haptics; feel.sound = settings.sound
        feel.strength = settings.hapticStrength; feel.level = settings.soundLevel
    }

    func forgetAllTvs() {
        disconnect()
        devices = []; store.saveDevices([])
        activeId = nil; store.activeId = nil
        tvApps = []; appsFor = nil; nowOn = nil
        ClientIdentity.reset()
        say("All TVs forgotten")
    }

    func eraseAll() {
        forgetAllTvs()
        store.clearAll()
        shortcuts = []
        settings = Settings(); applyFeel()
        say("All data erased")
    }
}

/* ------------------------------------------------ wake, voice, more */

extension RemoteModel {
    /** Turns the TV on: Roku's PowerOn, or Wake-on-LAN with its saved network address; then connects. */
    func wake() {
        guard let d = active else { say("Choose a TV first"); return }
        feel.fire(.heavy)
        Task {
            if let a = adapter, link == .ready, await a.powerOn() { say("Turning on \(d.name)"); return }
            guard let mac = d.mac else { say("\(d.name) can't be turned on from here yet. Connect once while it's on."); return }
            let ok = await Task.detached { Wol.wake(mac: mac, host: d.host) }.value
            guard ok else { say("Couldn't wake \(d.name) from this iPhone"); return }
            say("Waking \(d.name)…")
            for _ in 0..<6 {
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                if link == .ready { return }
                if link != .connecting { connect(d) }
            }
        }
    }

    /**
     * Voice to the TV's Assistant (Android TV): Search, answer the TV's voice session, then the
     * iPhone's mic as 16-bit mono 8 kHz PCM in 20 KB messages (TVs ignore small ones). It stops
     * when the TV hears the end of speech, when you let go / tap again, after 2.5 s of quiet once
     * you've spoken, or at 12 s. If the TV doesn't open a session, Search opens in Voice mode.
     */
    func voiceStart() {
        guard let d = active, link == .ready else { say("Choose a TV first"); return }
        guard let a = adapter as? AndroidTvAdapter, d.brand == .ANDROID_TV else { press(.voice); return }
        if voiceSession != nil { voiceStop(); return }
        Task {
            let granted = await AVAudioApplication.requestRecordPermission()
            guard granted else { say("Allow the microphone to talk to the TV"); return }
            guard let id = await a.voiceBegin() else { voiceTyping += 1; return }
            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.record, mode: .measurement, options: .duckOthers)
                try session.setActive(true)
                let engine = AVAudioEngine()
                let input = engine.inputNode
                let inFmt = input.outputFormat(forBus: 0)
                guard let outFmt = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 8000, channels: 1, interleaved: true),
                      let conv = AVAudioConverter(from: inFmt, to: outFmt) else { await a.voiceEnd(id); return }
                let buf = VoiceBuffer()
                input.installTap(onBus: 0, bufferSize: 2048, format: inFmt) { pcm, _ in
                    let cap = AVAudioFrameCount(Double(pcm.frameLength) * 8000 / inFmt.sampleRate) + 16
                    guard let out = AVAudioPCMBuffer(pcmFormat: outFmt, frameCapacity: cap) else { return }
                    var fed = false
                    conv.convert(to: out, error: nil) { _, status in
                        if fed { status.pointee = .noDataNow; return nil }
                        fed = true; status.pointee = .haveData; return pcm
                    }
                    guard let ch = out.int16ChannelData, out.frameLength > 0 else { return }
                    let n = Int(out.frameLength)
                    var sum: Double = 0
                    for i in 0..<n { let v = Double(ch[0][i]); sum += v * v }
                    let chunk = buf.add(Data(bytes: ch[0], count: n * 2), rms: (sum / Double(n)).squareRoot())
                    if let chunk { Task { await a.voiceChunk(id, chunk) } }
                }
                try engine.start()
                voiceSession = (engine, id, a, buf)
                listening = true
                a.onVoiceDone = { [weak self] in Task { @MainActor in if self?.voiceSession?.id == id { self?.voiceStop() } } }
                say("Listening…", .voice)
                let start = Date()
                while voiceSession?.id == id {
                    try? await Task.sleep(nanoseconds: 200_000_000)
                    let quiet = buf.heard && Date().timeIntervalSince(buf.lastLoud) > 2.5
                    if quiet || Date().timeIntervalSince(start) > 12 { voiceStop(); break }
                }
            } catch { await a.voiceEnd(id) }
        }
    }

    func voiceStop() {
        guard let v = voiceSession else { return }
        voiceSession = nil
        listening = false
        v.engine.inputNode.removeTap(onBus: 0)
        v.engine.stop()
        v.adapter.onVoiceDone = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let rest = v.buffer.flush()
        Task {
            if let rest { await v.adapter.voiceChunk(v.id, rest) }
            await v.adapter.voiceEnd(v.id)
        }
    }

    func loadInputs() {
        guard let a = adapter, link == .ready else { return }
        Task { inputs = await a.listInputs() }
    }

    func switchInput(_ i: TvInput) {
        guard let a = adapter else { say("Choose a TV first"); return }
        feel.fire(.press); say(i.name)
        Task { do { try await a.switchInput(i.id); txCount += 1 } catch { say((error as? UserFacing)?.message ?? "Couldn't switch input") } }
    }

    /** Turns the TV off after [minutes] while the app is running; nil turns the timer off. */
    func setSleep(_ minutes: Int?) {
        sleepTask?.cancel(); sleepTask = nil
        guard let minutes else { sleepAt = nil; say("Sleep timer off"); return }
        let at = Date().addingTimeInterval(Double(minutes) * 60)
        sleepAt = at
        say("TV turns off in \(minutes) min")
        sleepTask = Task {
            while Date() < at { try? await Task.sleep(nanoseconds: 1_000_000_000); if Task.isCancelled { return } }
            sleepAt = nil
            if let d = active { if link != .ready { connect(d) }; try? await Task.sleep(nanoseconds: 1_500_000_000); press(.power, quiet: true) }
        }
    }

    /** From Siri / Shortcuts: opens a Quick access or catalog app by name once the TV is connected. */
    func openByName(_ name: String) {
        Task {
            if link != .ready, let d = active, link != .connecting { connect(d) }
            for _ in 0..<100 { if link == .ready { break }; try? await Task.sleep(nanoseconds: 100_000_000) }
            guard link == .ready else { say("Couldn't reach the TV"); return }
            let n = name.lowercased()
            if let s = shortcuts.first(where: { $0.kind == .app && $0.name.lowercased() == n }) { launch(s); return }
            if let c = Catalog.apps.first(where: { $0.name.lowercased() == n || $0.key == n }) { launchCatalog(c); return }
            if let t = tvApps.first(where: { $0.name.lowercased().contains(n) }) { launchApp(t.name, t.id); return }
            say("Couldn't find \(name) on the TV")
        }
    }
}

#if DEBUG
// Screenshot mode (Debug builds only): a pretend TV that's connected, a few apps, nothing saved.
extension RemoteModel {
    func screenshotDemo(trackpad: Bool, cards: Bool, live: Bool = false) {
        devices = [
            Device(id: "demo", name: "Living Room TV", host: "192.168.1.20", brand: .ANDROID_TV),
            Device(id: "demo2", name: "Bedroom Roku", host: "192.168.1.31", brand: .ROKU),
        ]
        activeId = "demo"
        link = .ready
        settings.controlStyle = trackpad ? "trackpad" : "dial"
        settings.onboarded = true; settings.toured = true
        settings.layout = cards ? "cards" : "strip"
        tvVolume = TvVolume(level: 24, max: 100, muted: false)
        nowOn = live ? NowOn(name: "Live TV", live: true) : NowOn(name: "Netflix", live: false, positionMs: 1_204_000, durationMs: 2_710_000)
        inputs = [TvInput(id: "h1", name: "HDMI 1 · PlayStation"), TvInput(id: "h2", name: "HDMI 2"), TvInput(id: "tv", name: "Antenna")]
        shortcuts = [("netflix", "Netflix"), ("youtube", "YouTube"), ("prime", "Prime Video"), ("hotstar", "JioHotstar"),
                     ("disney", "Disney+"), ("sonyliv", "SonyLIV"), ("zee5", "ZEE5"), ("spotify", "Spotify"),
                     ("plex", "Plex"), ("hulu", "Hulu"), ("peacock", "Peacock")].map {
            Shortcut(id: "s-\($0.0)", kind: .app, name: $0.1, value: $0.0, pinned: true)
        }
        tvApps = [
            TvApp(id: "com.netflix.ninja", name: "Netflix"), TvApp(id: "com.google.android.youtube.tv", name: "YouTube"),
            TvApp(id: "com.amazon.amazonvideo.livingroom", name: "Prime Video"), TvApp(id: "com.disney.disneyplus", name: "Disney+"),
            TvApp(id: "com.spotify.tv.android", name: "Spotify"), TvApp(id: "com.plexapp.android", name: "Plex"),
        ]
    }
}
#endif


/** Mic audio collected into the 20 KB pieces the TV wants (thread-safe: filled from the audio thread). */
final class VoiceBuffer: @unchecked Sendable {
    private let lock = NSLock()
    private var data = Data()
    private(set) var heard = false
    private(set) var lastLoud = Date()

    /** Adds audio; returns a full 20 KB piece when one is ready. */
    func add(_ d: Data, rms: Double) -> Data? {
        lock.lock(); defer { lock.unlock() }
        if rms > 700 { heard = true; lastLoud = Date() }
        data.append(d)
        guard data.count >= AndroidTvAdapter.voiceChunkSize else { return nil }
        let piece = data.prefix(AndroidTvAdapter.voiceChunkSize)
        data.removeFirst(AndroidTvAdapter.voiceChunkSize)
        return Data(piece)
    }

    /** What's left (padded when sent). */
    func flush() -> Data? {
        lock.lock(); defer { lock.unlock() }
        defer { data = Data() }
        return data.isEmpty ? nil : data
    }
}

/* ------------------------------------------- what works right now */

extension RemoteModel {
    enum Screen { case home, app, live, unknown }

    /** What the TV shows: its home screen, an app, or live TV (from what it reports). */
    var screen: Screen {
        guard link == .ready, let n = nowOn else { return .unknown }
        if n.live { return .live }
        if n.name.isEmpty { return .unknown }
        let p = n.name.lowercased()
        if n.name == "Home" || p.contains("launcher") { return .home }
        return .app
    }

    /** Why [k] isn't available right now, in a few words for a toast; nil when it is. */
    func whyNot(_ k: RemoteKey) -> String? {
        guard let d = active else { return "Choose a TV first" }
        if link == .connecting { return "Connecting to \(d.name)…" }
        if link != .ready { return "\(d.name) isn't connected. Tap the TV at the top to connect." }
        let playback: Set<RemoteKey> = [.playPause, .rewind, .fastForward]
        let live: Set<RemoteKey> = [.chUp, .chDown, .guide, .chList]
        let colours: Set<RemoteKey> = [.red, .green, .yellow, .blue]
        switch screen {
        case .home:
            if playback.contains(k) { return "Playback keys work while something's playing" }
            if k == .subtitles || k == .audio { return "\(k.word) works while something's playing" }
            if k == .info { return "Info shows programme details on live TV" }
            if live.contains(k) { return "Channel keys work on live TV" }
            if colours.contains(k) { return "Colour keys work on live TV" }
        case .app:
            if live.contains(k) { return "Channel keys work on live TV" }
            if colours.contains(k) { return "Colour keys work on live TV" }
            if k == .info && d.brand == .ANDROID_TV { return "Info shows programme details on live TV" }
        default: break
        }
        return nil
    }

    func can(_ k: RemoteKey) -> Bool { whyNot(k) == nil }

    /** A dimmed key was tapped: say why. */
    func explain(_ k: RemoteKey) { if let w = whyNot(k) { feel.fire(.error); say(w) } }

    /** What's on the TV, for the line at the foot of the remote: "YouTube · 1:23 / 4:56". */
    var nowLine: String? {
        guard link == .ready, let n = nowOn, !n.name.isEmpty, n.name != "Home" else { return nil }
        var name = n.name
        if name.contains("."), !name.contains(" ") {
            guard let known = Catalog.apps.first(where: { $0.ids.values.contains(name) })?.name ?? tvApps.first(where: { $0.id == name })?.name else {
                return name.lowercased().contains("launcher") ? nil : name
            }
            name = known
        }
        func t(_ ms: Int) -> String { let s = ms / 1000; return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60) }
        if let p = n.positionMs, let d = n.durationMs, d > 0 { return "\(name) · \(t(p)) / \(t(d))" }
        return name
    }

    /* ------------------------------------------------ backup, restore */

    /** Settings, quick access and channels as JSON (no TVs: they pair again on a new phone). */
    func exportProfile() -> Data {
        struct Profile: Encodable { let version = 1; let settings: Settings; let shortcuts: [Shortcut] }
        let enc = JSONEncoder(); enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? enc.encode(Profile(settings: settings, shortcuts: shortcuts))) ?? Data()
    }

    func importProfile(_ data: Data) {
        struct Profile: Decodable { let settings: Settings?; let shortcuts: [Shortcut]? }
        guard let p = try? JSONDecoder().decode(Profile.self, from: data), p.settings != nil || p.shortcuts != nil else { say("That isn't a Mini Remote backup"); return }
        if let s = p.settings { update(s) }
        if let l = p.shortcuts { replaceShortcuts(l) }
        feel.fire(.success); say("Backup restored")
    }

    /** Opens the apps screen in adding mode: tapping an app adds it; is quick access full? */
    var quickFull: Bool { shortcuts.filter(\.pinned).count >= maxPinned }
}
