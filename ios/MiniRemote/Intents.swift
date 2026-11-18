import AppIntents
import Foundation

/** Siri and Shortcuts: "Open Netflix on the TV", "Turn on the TV". They open Mini Remote and act there. */
@MainActor
final class IntentInbox {
    static let shared = IntentInbox()
    var model: RemoteModel?
    var pending: ((RemoteModel) -> Void)?
    func run(_ f: @escaping (RemoteModel) -> Void) { if let m = model { f(m) } else { pending = f } }
}

struct OpenOnTVIntent: AppIntent {
    static var title: LocalizedStringResource = "Open an app on the TV"
    static var description = IntentDescription("Opens an app (like Netflix) on your TV with Mini Remote.")
    static var openAppWhenRun = true
    @Parameter(title: "App") var app: String
    @MainActor func perform() async throws -> some IntentResult {
        let name = app
        IntentInbox.shared.run { $0.openByName(name) }
        return .result()
    }
}

struct TurnOnTVIntent: AppIntent {
    static var title: LocalizedStringResource = "Turn on the TV"
    static var openAppWhenRun = true
    @MainActor func perform() async throws -> some IntentResult {
        IntentInbox.shared.run { $0.wake() }
        return .result()
    }
}

struct MiniRemoteShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: TurnOnTVIntent(), phrases: ["Turn on the TV with \(.applicationName)"], shortTitle: "Turn on TV", systemImageName: "power")
        AppShortcut(intent: OpenOnTVIntent(), phrases: ["Open an app on the TV with \(.applicationName)"], shortTitle: "Open on TV", systemImageName: "tv")
    }
}
