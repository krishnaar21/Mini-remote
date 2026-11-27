import Foundation

/** The TV's own volume: level of max, and whether it's muted. */
struct TvVolume: Equatable { var level: Int; var max: Int; var muted: Bool }

protocol RemoteAdapter: AnyObject {
    /** The TV reported its volume by itself (Android TV, LG). */
    var onVolume: ((TvVolume) -> Void)? { get set }
    /** An open connection broke by itself (not after close()). */
    var onDropped: (() -> Void)? { get set }
    /** Asks the TV for its volume; nil when it can't tell. */
    func readVolume() async -> TvVolume?
    /** The TV's network address, inputs, waking from standby, and the whole-text-box path. */
    func macAddress() async -> String?
    func listInputs() async -> [TvInput]
    func switchInput(_ id: String) async throws
    func powerOn() async -> Bool
    var replacesText: Bool { get }
    func setText(_ text: String) async throws
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
    /** Runs the search typed in the TV's box (Enter); by default the OK key. */
    func submit() async throws
    /** Empties the TV's text box, including what was there before we typed. */
    func clearBox(_ known: Int) async throws
    func close()
}

extension RemoteAdapter {
    func readVolume() async -> TvVolume? { nil }
    func macAddress() async -> String? { nil }
    func listInputs() async -> [TvInput] { [] }
    func switchInput(_ id: String) async throws { throw UserFacing("Pick the input on the TV", unsupported: true) }
    func powerOn() async -> Bool { false }
    var replacesText: Bool { false }
    func setText(_ text: String) async throws { throw UserFacing("Typing isn't supported on this TV yet", unsupported: true) }
    func typeText(_ text: String) async throws { throw UserFacing("Typing isn't supported on this TV yet", unsupported: true) }
    func backspace() async throws { throw UserFacing("Typing isn't supported on this TV yet", unsupported: true) }
    func listApps() async throws -> [TvApp] { throw UserFacing("This TV can't list its apps", unsupported: true) }
    func launchApp(_ id: String) async throws { throw UserFacing("Opening apps isn't supported on this TV yet", unsupported: true) }
    func openStore(_ id: String) async throws { throw UserFacing("Install and remove apps from the TV's app store", unsupported: true) }
    func nowOn() async throws -> NowOn { throw UserFacing("This TV can't say what's on screen", unsupported: true) }
    func submit() async throws { try await send(.ok) }
    func clearBox(_ known: Int) async throws { for _ in 0..<known { try await backspace() } }

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
    var onVolume: ((TvVolume) -> Void)?
    var onDropped: (() -> Void)?
    private let host: String
    init(host: String) { self.host = host }

    private var base: String { "http://\(host):8060/" }
    private static let keys: [RemoteKey: String] = {
        var m: [RemoteKey: String] = [
            .up: "Up", .down: "Down", .left: "Left", .right: "Right", .ok: "Select", .back: "Back", .home: "Home",
            .playPause: "Play", .power: "Power", .volUp: "VolumeUp", .volDown: "VolumeDown", .mute: "VolumeMute",
            .voice: "Search", .rewind: "Rev", .fastForward: "Fwd", .chUp: "ChannelUp", .chDown: "ChannelDown", .info: "Info",
            .input: "InputTuner",
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
        let mp = (try? await Http.get(base + "query/media-player")) ?? ""
        func ms(_ t: String) -> Int? { firstMatch("<\(t)>(\\d+) ms</\(t)>", in: mp).flatMap { Int($0[1]) } }
        return NowOn(name: name.isEmpty ? m[2] : name, live: m[2].hasPrefix("tvinput."), positionMs: ms("position"), durationMs: ms("duration"))
    }
    func macAddress() async -> String? {
        guard let x = try? await Http.get(base + "query/device-info") else { return nil }
        func tag(_ t: String) -> String? { firstMatch("<\(t)>([^<]*)</\(t)>", in: x).map { $0[1].trimmingCharacters(in: .whitespaces) }.flatMap { $0.isEmpty ? nil : $0 } }
        return tag("network-type") == "ethernet" ? (tag("ethernet-mac") ?? tag("wifi-mac")) : (tag("wifi-mac") ?? tag("ethernet-mac"))
    }
    func listInputs() async -> [TvInput] {
        guard let xml = try? await Http.get(base + "query/apps") else { return [] }
        return allMatches(#"<app id="([^"]+)" type="tvin"[^>]*>([^<]*)</app>"#, in: xml).map { TvInput(id: $0[1], name: xmlText($0[2]).isEmpty ? $0[1] : xmlText($0[2])) }
    }
    func switchInput(_ id: String) async throws { try await Http.post(base + "launch/" + enc(id)) }
    func powerOn() async -> Bool { (try? await Http.post(base + "keypress/PowerOn")) != nil }
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
    var onVolume: ((TvVolume) -> Void)?
    var onDropped: (() -> Void)?
    private var alive = false

    init(host: String, token: String?, pin: String?, onToken: @escaping (String) -> Void, onPin: @escaping (String) -> Void) {
        self.host = host; self.token = token; self.pin = pin; self.onToken = onToken; self.onPin = onPin
    }

    private static let keys: [RemoteKey: String] = {
        var m: [RemoteKey: String] = [
            .up: "KEY_UP", .down: "KEY_DOWN", .left: "KEY_LEFT", .right: "KEY_RIGHT", .ok: "KEY_ENTER", .back: "KEY_RETURN",
            .home: "KEY_HOME", .playPause: "KEY_PLAY_BACK", .power: "KEY_POWER", .volUp: "KEY_VOLUP", .volDown: "KEY_VOLDOWN",
            .mute: "KEY_MUTE", .voice: "KEY_VOICE", .rewind: "KEY_REWIND", .fastForward: "KEY_FF", .chUp: "KEY_CHUP",
            .chDown: "KEY_CHDOWN", .guide: "KEY_GUIDE", .chList: "KEY_CH_LIST", .info: "KEY_INFO", .input: "KEY_SOURCE",
            .menu: "KEY_MENU", .audio: "KEY_MTS", .subtitles: "KEY_CAPTION", .settings: "KEY_MENU",
            .red: "KEY_RED", .green: "KEY_GREEN", .yellow: "KEY_YELLOW", .blue: "KEY_CYAN",
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
        s.onFail = { [weak self, weak s] e in
            ready.fail(e)
            guard let self, let s, self.ws === s, self.alive else { return }
            self.alive = false; self.ws = nil; self.onDropped?()
        }
        ws = s
        s.start()
        do { try await ready.wait(25) } catch { close(); throw error }
        alive = true
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

    func close() { alive = false; ws?.close(); ws = nil }

    func macAddress() async -> String? {
        guard let s = try? await Http.get("http://\(host):8001/api/v2/"), let j = json(s), let d = j["device"] as? [String: Any],
              let m = d["wifiMac"] as? String, !m.isEmpty else { return nil }
        return m
    }
    /** No input list over this channel: the HDMI keys. */
    func listInputs() async -> [TvInput] { (1...4).map { TvInput(id: "KEY_HDMI\($0)", name: "HDMI \($0)") } + [TvInput(id: "KEY_TV", name: "TV")] }
    func switchInput(_ id: String) async throws {
        guard let s = ws else { throw UserFacing("Not connected") }
        try await s.send(jsonString(["method": "ms.remote.control", "params": ["Cmd": "Click", "DataOfCmd": id, "Option": "false", "TypeOfRemote": "SendRemoteKey"]]))
    }

    /** Volume through UPnP RenderingControl (port 9197), which most Samsung TVs answer. */
    func readVolume() async -> TvVolume? {
        func ask(_ action: String, _ tag: String) async -> String? {
            guard let u = URL(string: "http://\(host):9197/upnp/control/RenderingControl1") else { return nil }
            var r = URLRequest(url: u)
            r.httpMethod = "POST"
            r.setValue("text/xml; charset=\"utf-8\"", forHTTPHeaderField: "Content-Type")
            r.setValue("\"urn:schemas-upnp-org:service:RenderingControl:1#\(action)\"", forHTTPHeaderField: "SOAPACTION")
            r.httpBody = Data("<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:\(action) xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel></u:\(action)></s:Body></s:Envelope>".utf8)
            guard let (data, resp) = try? await Http.plain.data(for: r), (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return firstMatch("<\(tag)>([^<]*)</\(tag)>", in: String(decoding: data, as: UTF8.self))?.first
        }
        guard let v = await ask("GetVolume", "CurrentVolume").flatMap({ Int($0.trimmingCharacters(in: .whitespaces)) }) else { return nil }
        let m = await ask("GetMute", "CurrentMute")
        return TvVolume(level: v, max: 100, muted: m == "1" || m?.lowercased() == "true")
    }
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
    var onVolume: ((TvVolume) -> Void)?
    var onDropped: (() -> Void)?
    private var alive = false

    init(host: String, token: String?, pin: String?, onToken: @escaping (String) -> Void, onPin: @escaping (String) -> Void) {
        self.host = host; self.token = token; self.pin = pin; self.onToken = onToken; self.onPin = onPin
    }

    private func parseVolume(_ p: [String: Any]?) -> TvVolume? {
        guard let p else { return nil }
        if let st = p["volumeStatus"] as? [String: Any], let v = st["volume"] as? Int {
            return TvVolume(level: v, max: (st["maxVolume"] as? Int).flatMap { $0 > 0 ? $0 : nil } ?? 100, muted: st["muteStatus"] as? Bool ?? false)
        }
        guard let v = p["volume"] as? Int else { return nil }
        return TvVolume(level: v, max: 100, muted: (p["muted"] as? Bool) ?? (p["mute"] as? Bool) ?? false)
    }

    func macAddress() async -> String? {
        guard let p = (try? await request("ssap://com.webos.service.connectionmanager/getinfo"))?["payload"] as? [String: Any] else { return nil }
        for k in ["wiredInfo", "wifiInfo"] { if let m = (p[k] as? [String: Any])?["macAddress"] as? String, !m.isEmpty { return m } }
        return nil
    }
    func listInputs() async -> [TvInput] {
        guard let p = (try? await request("ssap://tv/getExternalInputList"))?["payload"] as? [String: Any], let a = p["devices"] as? [[String: Any]] else { return [] }
        return a.compactMap { o in (o["id"] as? String).map { TvInput(id: $0, name: (o["label"] as? String) ?? $0) } }
    }
    func switchInput(_ id: String) async throws { try await request("ssap://tv/switchInput", ["inputId": id]) }

    func readVolume() async -> TvVolume? { parseVolume((try? await request("ssap://audio/getVolume"))?["payload"] as? [String: Any]) }

    private let manifest: [String: Any] = ["manifestVersion": 1, "appVersion": "1.1", "permissions": [
        "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "READ_INSTALLED_APPS", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "CONTROL_INPUT_TV",
    ]]

    private func tryConnect(_ url: String) async throws -> WS {
        guard let u = URL(string: url) else { throw UserFacing("Bad address") }
        let registered = Once<Void>()
        let s = WS(u, pin: url.hasPrefix("wss") ? pin : nil)
        s.onText = { [weak self] text in
            guard let self, let j = json(text) else { return }
            let type = j["type"] as? String
            let id = j["id"] as? String ?? ""
            if id == "vol" { if let v = self.parseVolume(j["payload"] as? [String: Any]) { self.onVolume?(v) }; return }
            if type == "registered" {
                if let k = (j["payload"] as? [String: Any])?["client-key"] as? String, !k.isEmpty { self.token = k; self.onToken(k) }
                registered.succeed(())
            } else if type == "error" && id == "register" {
                registered.fail(UserFacing((j["error"] as? String) ?? "Pairing refused"))
            }
            self.lock.lock(); let p = self.pending.removeValue(forKey: id); self.lock.unlock()
            p?.succeed(j)
        }
        s.onFail = { [weak self, weak s] e in
            registered.fail(e)
            guard let self, let s, self.ws === s, self.alive else { return }
            self.alive = false; self.ws = nil; self.pointer = nil; self.onDropped?()
        }
        s.start()
        var payload: [String: Any] = ["forcePairing": false, "pairingType": "PROMPT", "manifest": manifest]
        if let t = token { payload["client-key"] = t }
        try await s.send(jsonString(["type": "register", "id": "register", "payload": payload]))
        do { try await registered.wait(30) } catch { s.close(); throw error }
        return s
    }

    func connect() async throws {
        close()
        // Encrypted only (port 3001): Mini Remote supports TVs from 2017 on, which have it.
        let s = try await tryConnect("wss://\(host):3001")
        ws = s
        alive = true
        if pin == nil, let seen = s.delegate.seen { onPin(seen) }
        // Keep the volume in step: the TV sends it again whenever it changes.
        try? await s.send(jsonString(["type": "subscribe", "id": "vol", "uri": "ssap://audio/getVolume"]))
    }

    @discardableResult
    private func request(_ uri: String, _ payload: [String: Any] = [:]) async throws -> [String: Any] {
        guard let s = ws else { throw UserFacing("Not connected") }
        let d = Once<[String: Any]>()
        let id: String = lock.withLock { let id = "r\(nextId)"; nextId += 1; pending[id] = d; return id }
        defer { lock.withLock { pending[id] = nil } }
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
        case .subtitles: try await button("CC")
        case .settings: try await button("MENU")
        case .red: try await button("RED"); case .green: try await button("GREEN")
        case .yellow: try await button("YELLOW"); case .blue: try await button("BLUE")
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
        alive = false
        pointer?.close(); pointer = nil
        ws?.close(); ws = nil
        lock.lock(); let p = pending; pending = [:]; lock.unlock()
        p.values.forEach { $0.fail(UserFacing("Disconnected")) }
    }
}
