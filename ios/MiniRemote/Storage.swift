import Foundation
import Security

/**
 * Settings and shortcuts live in UserDefaults. Saved TVs (with their pairing tokens and pins)
 * live in the Keychain, readable only on this device after first unlock and never synced,
 * backed up to another device or migrated.
 */
final class Store {
    private let d = UserDefaults.standard
    private let service = "com.krishna.miniremote.devices"

    func loadDevices() -> [Device] {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: "devices", kSecReturnData as String: true]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return [] }
        return (try? JSONDecoder().decode([Device].self, from: data)) ?? []
    }

    func saveDevices(_ l: [Device]) {
        guard let data = try? JSONEncoder().encode(l) else { return }
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                   kSecAttrAccount as String: "devices"]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    var activeId: String? {
        get { d.string(forKey: "active") }
        set { d.set(newValue, forKey: "active") }
    }

    func loadSettings() -> Settings {
        guard let data = d.data(forKey: "settings"), let s = try? JSONDecoder().decode(Settings.self, from: data) else { return Settings() }
        return s
    }
    func saveSettings(_ s: Settings) { d.set(try? JSONEncoder().encode(s), forKey: "settings") }

    func loadShortcuts() -> [Shortcut] {
        guard let data = d.data(forKey: "shortcuts") else { return [] }
        return (try? JSONDecoder().decode([Shortcut].self, from: data)) ?? []
    }
    func saveShortcuts(_ l: [Shortcut]) { d.set(try? JSONEncoder().encode(l), forKey: "shortcuts") }

    var localSeen: Bool {
        get { d.bool(forKey: "localSeen") }
        set { d.set(newValue, forKey: "localSeen") }
    }

    /** Erase everything this app stored (TVs, keys, settings, shortcuts). */
    func clearAll() {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service]
        SecItemDelete(q as CFDictionary)
        if let id = Bundle.main.bundleIdentifier { d.removePersistentDomain(forName: id) }
    }
}
