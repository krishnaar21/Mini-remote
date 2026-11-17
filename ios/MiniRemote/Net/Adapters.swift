import Foundation

protocol RemoteAdapter: AnyObject {
    func connect() async throws
    func send(_ key: RemoteKey) async throws
    func typeText(_ text: String) async throws
    func backspace() async throws
    func listApps() async throws -> [TvApp]
    func launchApp(_ id: String) async throws
    func openStore(_ id: String) async throws
    func tuneChannel(_ number: String) async throws
    func search(_ query: String) async throws
    func nowOn() async throws -> NowOn
    func close()
}

extension RemoteAdapter {
    func typeText(_ text: String) async throws { throw UserFacing("Typing isn't supported on this TV yet", unsupported: true) }
    func backspace() async throws { throw UserFacing("Typing isn't supported on this TV yet", unsupported: true) }
    func listApps() async throws -> [TvApp] { throw UserFacing("This TV can't list its apps", unsupported: true) }
    func launchApp(_ id: String) async throws { throw UserFacing("Opening apps isn't supported on this TV yet", unsupported: true) }
    func openStore(_ id: String) async throws { throw UserFacing("Install and remove apps from the TV's app store", unsupported: true) }
    func nowOn() async throws -> NowOn { throw UserFacing("This TV can't say what's on screen", unsupported: true) }

    /** Types the number's digits; the TV switches after a moment. */
    func tuneChannel(_ number: String) async throws {
        let keys = number.compactMap { RemoteKey.forDigit($0) }
        if keys.isEmpty { throw UserFacing("That isn't a channel number") }
        for k in keys { try await send(k); try await Task.sleep(nanoseconds: 180_000_000) }
    }

    /** Opens the TV's search and types the words into it. */
    func search(_ query: String) async throws {
        try await send(.voice)
        try await Task.sleep(nanoseconds: 1_500_000_000)
        try await typeText(query)
    }
}

func adapterFor(_ d: Device, onToken: @escaping (String) -> Void, onPin: @escaping (String) -> Void,
                askCode: @escaping (String?) async -> String?) throws -> RemoteAdapter {
    guard HostValidator.isPrivateIPv4(d.host) else { throw UserFacing("Only home-network addresses are allowed") }
    switch d.brand {
    case .ROKU: return RokuAdapter(host: d.host)
    case .SAMSUNG: return SamsungAdapter(host: d.host, token: d.token, pin: d.pin, onToken: onToken, onPin: onPin)
    case .LG: return LgAdapter(host: d.host, token: d.token, pin: d.pin, onToken: onToken, onPin: onPin)
    case .ANDROID_TV: return AndroidTvAdapter(host: d.host, pin: d.pin, onPin: onPin, askCode: askCode)
    }
}

/* ---------------------------------------------------------------- Roku (ECP) */

final class RokuAdapter: RemoteAdapter {
    private let host: String
    init(host: String) { self.host = host }

    private var base: String { "http://\(host):8060/" }
    private static let keys: [RemoteKey: String] = {
        var m: [RemoteKey: String] = [
            .up: "Up", .down: "Down", .left: "Left", .right: "Right", .ok: "Select", .back: "Back", .home: "Home",
            .playPause: "Play", .power: "Power", .volUp: "VolumeUp", .volDown: "VolumeDown", .mute: "VolumeMute",
            .voice: "Search", .rewind: "Rev", .fastForward: "Fwd", .chUp: "ChannelUp", .chDown: "ChannelDown", .info: "Info",
        ]
        for k in RemoteKey.allCases { if let d = k.digit { m[k] = "Lit_\(d)" } }
        return m
    }()

    func connect() async throws { _ = try await Http.get(base + "query/device-info") }
    func send(_ key: RemoteKey) async throws {
        guard let k = Self.keys[key] else { throw missing(key, "Roku") }
        try await Http.post(base + "keypress/" + k)
    }
    func typeText(_ text: String) async throws { for ch in text { try await Http.post(base + "keypress/Lit_" + enc(String(ch))) } }
    func backspace() async throws { try await Http.post(base + "keypress/Backspace") }

    /** Channels (appl) and, on Roku TVs, inputs (tvin). Icons come from the Roku. */
    func listApps() async throws -> [TvApp] {
        let xml = try await Http.get(base + "query/apps")
        return allMatches(#"<app id="([^"]+)"([^>]*)>([^<]*)</app>"#, in: xml).map { m in
            let version = firstMatch(#"version="([^"]*)""#, in: m[2])?[1]
            let name = xmlText(m[3])
            return TvApp(id: m[1], name: name.isEmpty ? m[1] : name, icon: base + "query/icon/" + enc(m[1]),
                         version: (version?.isEmpty ?? true) ? nil : version)
        }
    }
    func launchApp(_ id: String) async throws { try await Http.post(base + "launch/" + enc(id)) }
    func openStore(_ id: String) async throws { try await Http.post(base + "install/" + enc(id)) }
    func tuneChannel(_ number: String) async throws { try await Http.post(base + "launch/tvinput.dtv?ch=" + enc(number)) }
    func search(_ query: String) async throws { try await Http.post(base + "search/browse?keyword=" + enc(query)) }
    func nowOn() async throws -> NowOn {
        let xml = try await Http.get(base + "query/active-app")
        guard let m = firstMatch(#"<app( id="([^"]*)")?[^>]*>([^<]*)</app>"#, in: xml), !m[2].isEmpty else { return NowOn(name: "Home", live: false) }
        let name = xmlText(m[3])
        return NowOn(name: name.isEmpty ? m[2] : name, live: m[2].hasPrefix("tvinput."))
    }
    func close() {}
}

/* ------------------------------------------------------------ Samsung (Tizen) */

final class SamsungAdapter: RemoteAdapter {
    private let host: String
    private var token: String?
    private let pin: String?
    private let onToken: (String) -> Void
    private let onPin: (String) -> Void
    private var ws: WS?
    private var appsReply: Once<[[String: Any]]>?

    init(host: String, token: String?, pin: String?, onToken: @escaping (String) -> Void, onPin: @escaping (String) -> Void) {
        self.host = host; self.token = token; self.pin = pin; self.onToken = onToken; self.onPin = onPin
    }

    private static let keys: [RemoteKey: String] = {
        var m: [RemoteKey: String] = [
            .up: "KEY_UP", .down: "KEY_DOWN", .left: "KEY_LEFT", .right: "KEY_RIGHT", .ok: "KEY_ENTER", .back: "KEY_RETURN",
            .home: "KEY_HOME", .playPause: "KEY_PLAY_BACK", .power: "KEY_POWER", .volUp: "KEY_VOLUP", .volDown: "KEY_VOLDOWN",
            .mute: "KEY_MUTE", .voice: "KEY_VOICE", .rewind: "KEY_REWIND", .fastForward: "KEY_FF", .chUp: "KEY_CHUP",
            .chDown: "KEY_CHDOWN", .guide: "KEY_GUIDE", .chList: "KEY_CH_LIST", .info: "KEY_INFO", .input: "KEY_SOURCE",
            .menu: "KEY_MENU", .audio: "KEY_MTS",
        ]
        for k in RemoteKey.allCases { if let d = k.digit { m[k] = "KEY_\(d)" } }
        return m
    }()

    func connect() async throws {
        close()
        let name = Data("Mini Remote".utf8).base64EncodedString()
        var url = "wss://\(host):8002/api/v2/channels/samsung.remote.control?name=\(enc(name))"
        if let t = token { url += "&token=" + enc(t) }
        guard let u = URL(string: url) else { throw UserFacing("Bad address") }
        let ready = Once<Void>()
        let s = WS(u, pin: pin)
        s.onText = { [weak self] text in
            guard let self, let j = json(text) else { return }
            switch j["event"] as? String {
            case "ms.channel.connect":
                if let t = (j["data"] as? [String: Any])?["token"] as? String, !t.isEmpty { self.token = t; self.onToken(t) }
                ready.succeed(())
            case "ms.channel.timeOut": ready.fail(UserFacing("The TV's Allow prompt timed out. Connect again and choose Allow."))
            case "ms.channel.unauthorized": ready.fail(UserFacing("The TV denied access. Connect again and choose Allow on the TV."))
            case "ed.installedApp.get":
                let list = ((j["data"] as? [String: Any])?["data"] as? [[String: Any]]) ?? []
                self.appsReply?.succeed(list)
            default: break
            }
        }
        s.onFail = { e in ready.fail(e) }
        ws = s
        s.start()
        do { try await ready.wait(25) } catch { close(); throw error }
        if pin == nil, let seen = s.delegate.seen { onPin(seen) }
    }

    func send(_ key: RemoteKey) async throws {
        guard let s = ws else { throw UserFacing("Not connected") }
        guard let k = Self.keys[key] else { throw missing(key, "Samsung") }
        try await s.send(jsonString(["method": "ms.remote.control",
            "params": ["Cmd": "Click", "DataOfCmd": k, "Option": "false", "TypeOfRemote": "SendRemoteKey"]]))
    }

    private func emit(_ event: String, _ data: [String: Any]? = nil) async throws {
        guard let s = ws else { throw UserFacing("Not connected") }
        var params: [String: Any] = ["event": event, "to": "host"]
        if let data { params["data"] = data }
        try await s.send(jsonString(["method": "ms.channel.emit", "params": params]))
    }

    func listApps() async throws -> [TvApp] {
        let d = Once<[[String: Any]]>()
        appsReply = d
        defer { appsReply = nil }
        try await emit("ed.installedApp.get")
        let list = try await d.wait(6, "The TV didn't send its app list")
        return list.compactMap { o in
            guard let id = o["appId"] as? String, !id.isEmpty else { return nil }
            let n = (o["name"] as? String) ?? id
            return TvApp(id: id, name: n.isEmpty ? id : n)
        }
    }

    func launchApp(_ id: String) async throws {
        if (try? await Http.post("http://\(host):8001/api/v2/applications/" + enc(id))) != nil { return }
        try await emit("ed.apps.launch", ["appId": id, "action_type": "DEEP_LINK"])
    }

    func search(_ query: String) async throws { throw UserFacing("Search isn't supported on Samsung TVs yet", unsupported: true) }

    func close() { ws?.close(); ws = nil }
}

/* --------------------------------------------------------------- LG (webOS) */

final class LgAdapter: RemoteAdapter {
    private let host: String
    private var token: String?
    private let pin: String?
    private let onToken: (String) -> Void
    private let onPin: (String) -> Void
    private var ws: WS?
    private var pointer: WS?
    private var nextId = 1
    private var pending: [String: Once<[String: Any]>] = [:]
    private let lock = NSLock()
    private var muted = false
    private var playing = false

    init(host: String, token: String?, pin: String?, onToken: @escaping (String) -> Void, onPin: @escaping (String) -> Void) {
        self.host = host; self.token = token; self.pin = pin; self.onToken = onToken; self.onPin = onPin
    }

    private let manifest: [String: Any] = ["manifestVersion": 1, "appVersion": "1.1", "permissions": [
        "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "READ_INSTALLED_APPS",
    ]]

    private func tryConnect(_ url: String) async throws -> WS {
        guard let u = URL(string: url) else { throw UserFacing("Bad address") }
        let registered = Once<Void>()
        let s = WS(u, pin: url.hasPrefix("wss") ? pin : nil)
        s.onText = { [weak self] text in
            guard let self, let j = json(text) else { return }
            let type = j["type"] as? String
            let id = j["id"] as? String ?? ""
            if type == "registered" {
                if let k = (j["payload"] as? [String: Any])?["client-key"] as? String, !k.isEmpty { self.token = k; self.onToken(k) }
                registered.succeed(())
            } else if type == "error" && id == "register" {
                registered.fail(UserFacing((j["error"] as? String) ?? "Pairing refused"))
            }
            self.lock.lock(); let p = self.pending.removeValue(forKey: id); self.lock.unlock()
            p?.succeed(j)
        }
        s.onFail = { e in registered.fail(e) }
        s.start()
        var payload: [String: Any] = ["forcePairing": false, "pairingType": "PROMPT", "manifest": manifest]
        if let t = token { payload["client-key"] = t }
        try await s.send(jsonString(["type": "register", "id": "register", "payload": payload]))
        do { try await registered.wait(30) } catch { s.close(); throw error }
        return s
    }

    func connect() async throws {
        close()
        if pin != nil { ws = try await tryConnect("wss://\(host):3001"); return }
        // Encrypted port first; older webOS only has the plain one.
        do {
            let s = try await tryConnect("wss://\(host):3001")
            ws = s
            if let seen = s.delegate.seen { onPin(seen) }
        } catch let e as CertChanged {
            throw e
        } catch {
            ws = try await tryConnect("ws://\(host):3000")
        }
    }

    @discardableResult
    private func request(_ uri: String, _ payload: [String: Any] = [:]) async throws -> [String: Any] {
        guard let s = ws else { throw UserFacing("Not connected") }
        lock.lock(); let id = "r\(nextId)"; nextId += 1; let d = Once<[String: Any]>(); pending[id] = d; lock.unlock()
        defer { lock.lock(); pending[id] = nil; lock.unlock() }
        try await s.send(jsonString(["type": "request", "id": id, "uri": uri, "payload": payload]))
        let j = try await d.wait(6, "The TV didn't answer")
        if j["type"] as? String == "error" { throw UserFacing("The TV refused: \((j["error"] as? String) ?? "unknown error")") }
        return j
    }

    private func ensurePointer() async throws -> WS {
        if let p = pointer { return p }
        let r = try await request("ssap://com.webos.service.networkinput/getPointerInputSocket")
        guard let path = (r["payload"] as? [String: Any])?["socketPath"] as? String, let u = URL(string: path) else {
            throw UserFacing("The TV refused the input channel")
        }
        let up = Once<Void>()
        let s = WS(u, pin: path.hasPrefix("wss") ? pin : nil)
        s.delegate.onOpen = { up.succeed(()) }
        s.onFail = { [weak self] e in up.fail(e); self?.pointer = nil }
        s.start()
        try await up.wait(5)
        pointer = s
        return s
    }

    private func button(_ name: String) async throws { try await ensurePointer().send("type:button\nname:\(name)\n\n") }

    func send(_ key: RemoteKey) async throws {
        if let d = key.digit { try await button(String(d)); return }
        switch key {
        case .up: try await button("UP")
        case .down: try await button("DOWN")
        case .left: try await button("LEFT")
        case .right: try await button("RIGHT")
        case .ok: try await button("ENTER")
        case .back: try await button("BACK")
        case .home: try await button("HOME")
        case .playPause: playing.toggle(); try await request(playing ? "ssap://media.controls/play" : "ssap://media.controls/pause")
        case .power: try await request("ssap://system/turnOff")
        case .volUp: try await request("ssap://audio/volumeUp")
        case .volDown: try await request("ssap://audio/volumeDown")
        case .mute: muted.toggle(); try await request("ssap://audio/setMute", ["mute": muted])
        case .rewind: try await request("ssap://media.controls/rewind")
        case .fastForward: try await request("ssap://media.controls/fastForward")
        case .chUp: try await button("CHANNELUP")
        case .chDown: try await button("CHANNELDOWN")
        case .guide: try await button("GUIDE")
        case .chList: try await button("LIST")
        case .info: try await button("INFO")
        case .input: try await button("INPUT_HUB")
        case .menu: try await button("MENU")
        case .audio: try await button("SAP")
        case .voice: throw UserFacing("Voice isn't available on LG over Wi-Fi", unsupported: true)
        default: throw missing(key, "LG")
        }
    }

    func typeText(_ text: String) async throws { try await request("ssap://com.webos.service.ime/insertText", ["text": text, "replace": 0]) }
    func backspace() async throws { try await request("ssap://com.webos.service.ime/deleteCharacters", ["count": 1]) }

    func listApps() async throws -> [TvApp] {
        let r = try await request("ssap://com.webos.applicationManager/listLaunchPoints")
        let list = ((r["payload"] as? [String: Any])?["launchPoints"] as? [[String: Any]]) ?? []
        return list.compactMap { o in
            guard let id = o["id"] as? String, !id.isEmpty else { return nil }
            let large = o["largeIcon"] as? String ?? ""
            let icon = large.isEmpty ? (o["icon"] as? String ?? "") : large
            let title = o["title"] as? String ?? id
            return TvApp(id: id, name: title.isEmpty ? id : title, icon: icon.hasPrefix("http") ? icon : nil)
        }
    }
    func launchApp(_ id: String) async throws { try await request("ssap://system.launcher/launch", ["id": id]) }
    func search(_ query: String) async throws { throw UserFacing("Open a search box on the LG, then use the keyboard to type", unsupported: true) }

    func nowOn() async throws -> NowOn {
        let r = try await request("ssap://com.webos.applicationManager/getForegroundAppInfo")
        let id = (r["payload"] as? [String: Any])?["appId"] as? String ?? ""
        let l = id.lowercased()
        let name: String
        if l.isEmpty || l.contains("home") || l.contains("launcher") { name = "Home" }
        else if l.contains("livetv") { name = "Live TV" }
        else if l.contains("hdmi") { name = "HDMI " + id.filter(\.isNumber) }
        else { name = (id.split(separator: ".").last.map(String.init) ?? id).capitalized }
        let live = ["livetv", "hdmi", "externalinput", "tvinput"].contains { l.contains($0) }
        return NowOn(name: name, live: live)
    }

    func close() {
        pointer?.close(); pointer = nil
        ws?.close(); ws = nil
        lock.lock(); let p = pending; pending = [:]; lock.unlock()
        p.values.forEach { $0.fail(UserFacing("Disconnected")) }
    }
}
