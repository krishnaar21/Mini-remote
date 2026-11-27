import UIKit
import AudioToolbox

enum Feel { case press, tick, detent, heavy, error, success }

/** Haptics and the keyboard click sound, scaled by the settings. */
final class FeelEngine {
    var haptics = true
    var sound = true
    var strength: Double = 1
    var level: Double = 1

    func fire(_ f: Feel) {
        if haptics {
            let s = CGFloat(min(1, 0.55 * strength + 0.15))
            switch f {
            case .press: UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: s)
            case .tick, .detent: UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: s)
            case .heavy: UIImpactFeedbackGenerator(style: .heavy).impactOccurred(intensity: min(1, s + 0.2))
            case .error: UINotificationFeedbackGenerator().notificationOccurred(.error)
            case .success: UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
        // iOS has no volume control for system sounds, so "level" picks a softer or firmer click.
        if sound && f != .error {
            AudioServicesPlaySystemSound(level < 0.8 ? 1306 : (level > 1.3 ? 1105 : 1104))
        }
    }
}
