import Foundation
import SwiftUI
import UIKit

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
    @Published private(set) var txCount = 0
    @Published private(set) var muted = false
    @Published private(set) var playing = false
    @Published private(set) var pairPrompt: PairPrompt?
    @Published private(set) var shortcuts: [Shortcut]
    @Published private(set) var tvApps: [TvApp] = []
    @Published private(set) var appsLoading = false
    @Published private(set) var nowOn: NowOn?

    let maxPinned = 8
    var active: Device? { devices.first { $0.id == activeId } }
    var localSeen: Bool { store.localSeen }

    private var adapter: RemoteAdapter?
    private var connectTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var nowTask: Task<Void, Never>?
    private var codeAnswer: CheckedContinuation<String?, Never>?
    private var appsFor: String?
    private var keys: AsyncStream<RemoteKey>.Continuation?

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
                    self.feel.fire(.error)
                    if let u = error as? UserFacing, u.unsupported { self.say(u.message) }
                    else { self.say("Lost connection"); self.link = .error }
                }
            }
        }
        if let d = active { connect(d) }
        UIApplication.shared.isIdleTimerDisabled = settings.keepAwake
    }

    /* ------------------------------------------------------------ keys */

    func press(_ key: RemoteKey, quiet: Bool = false) {
        guard let d = active else { feel.fire(.error); say("Choose a TV first"); return }
        if link == .error { connect(d) }
        feel.fire(key == .power ? .heavy : .press)
        if key == .mute { muted.toggle() }
        if key == .volUp { muted = false }
        if key == .playPause { playing.toggle() }
        if !quiet { say(key == .playPause ? (playing ? "Play" : "Pause") : key.word, key) }
        keys?.yield(key)
    }

    func scrubTick(_ key: RemoteKey) { feel.fire(.tick); keys?.yield(key) }

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

    func connect(_ d: Device) {
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
                try await a.connect()
                if Task.isCancelled { a.close(); return }
                self.adapter = a
                self.link = .ready
                self.feel.fire(.success)
            } catch {
                if Task.isCancelled { return }
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
        return (nil, d)
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
        Task { [weak self] in
            let list = await Discovery.scan()
            guard let self else { return }
            let hosts = Set(self.devices.map(\.host))
            self.found = list.filter { !hosts.contains($0.host) }
            self.scanning = false
        }
    }

    /* ------------------------------------------------------- run on TV */

    private func act(_ fail: String, _ block: @escaping (RemoteAdapter) async throws -> Void) {
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
        pinned.swapAt(i, i + by)
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
