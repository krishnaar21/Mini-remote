import SwiftUI

/** Round key: lighter while pressed, icon turns the accent colour. */
struct RoundKey: View {
    let symbol: String
    let label: String
    var size: CGFloat = 62
    var iconSize: CGFloat = 21
    var tint: Color = T.ink
    var accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: iconSize, weight: .medium)).frame(width: size, height: size)
        }
        .buttonStyle(KeyStyle(tint: tint, accent: accent))
        .accessibilityLabel(label)
    }
}

struct KeyStyle: ButtonStyle {
    var tint: Color = T.ink
    var accent: Color
    var fill: Color = T.high
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(configuration.isPressed ? accent : tint)
            .background(Circle().fill(configuration.isPressed ? T.pressed : fill))
    }
}

/** Fires on touch down and repeats while held (volume and channel steps). */
struct RepeatKey<Label: View>: View {
    let action: () -> Void
    @ViewBuilder let label: () -> Label
    @State private var down = false
    @State private var timer: Timer?

    var body: some View {
        label()
            .contentShape(Rectangle())
            .background(down ? Color.black.opacity(0.25) : Color.clear)
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !down else { return }
                    down = true
                    action()
                    timer = Timer.scheduledTimer(withTimeInterval: 0.38, repeats: false) { _ in
                        timer = Timer.scheduledTimer(withTimeInterval: 0.12, repeats: true) { _ in action() }
                    }
                }
                .onEnded { _ in down = false; timer?.invalidate(); timer = nil })
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { action() }
    }
}

/** − WORD +: one calm pill (VOL / CH). */
struct StepperPill: View {
    let word: String
    let downLabel: String
    let upLabel: String
    let accent: Color
    let onDown: () -> Void
    let onUp: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            RepeatKey(action: onDown) { Image(systemName: "minus").font(.system(size: 18, weight: .medium)).frame(maxWidth: .infinity, maxHeight: .infinity) }
                .accessibilityLabel(downLabel)
            Text(word).font(.system(size: 12, weight: .medium)).tracking(1).foregroundStyle(T.dim).frame(width: 52).accessibilityHidden(true)
            RepeatKey(action: onUp) { Image(systemName: "plus").font(.system(size: 18, weight: .medium)).frame(maxWidth: .infinity, maxHeight: .infinity) }
                .accessibilityLabel(upLabel)
        }
        .foregroundStyle(T.ink)
        .background(Capsule().fill(T.high))
        .clipShape(Capsule())
    }
}

/** A settings section's rows on one rounded card. */
struct Card<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(.horizontal, 16)
            .background(RoundedRectangle(cornerRadius: 20).fill(T.container))
    }
}

struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased()).font(.system(size: 12, weight: .medium)).tracking(1).foregroundStyle(T.muted)
            .padding(.top, 28).padding(.bottom, 8).accessibilityAddTraits(.isHeader)
    }
}

struct RowDivider: View { var body: some View { Rectangle().fill(T.line).frame(height: 1) } }

struct ToggleRow: View {
    let title: String
    let sub: String?
    @Binding var on: Bool
    let accent: Color
    var body: some View {
        Toggle(isOn: $on) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 15)).foregroundStyle(T.ink)
                if let sub { Text(sub).font(.system(size: 12.5)).foregroundStyle(T.muted) }
            }
        }
        .tint(accent)
        .padding(.vertical, 14)
    }
}

struct InfoBlock: View {
    let title: String
    let body_: String
    init(_ title: String, _ body: String) { self.title = title; self.body_ = body }
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 15)).foregroundStyle(T.ink)
            Text(body_).font(.system(size: 12.5)).foregroundStyle(T.muted).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/** Primary (light), secondary (dark) or danger button, full width. */
struct WideButton: View {
    enum Kind { case primary, secondary, danger }
    let title: String
    var kind: Kind = .primary
    var enabled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.system(size: 15, weight: .semibold)).frame(maxWidth: .infinity).frame(height: 52)
                .foregroundStyle(kind == .primary ? Color(hex: 0x111111) : (kind == .danger ? Color(hex: 0xFF8A8A) : T.ink))
                .background(RoundedRectangle(cornerRadius: 16).fill(kind == .primary ? Color(hex: 0xE3E3E3) : (kind == .danger ? Color(hex: 0x601410) : T.high)))
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .padding(.top, 12)
    }
}

/** "Are you sure?" as a small sheet. */
struct ConfirmSheet: View {
    let title: String
    let text: String
    let action: String
    var danger = true
    let onConfirm: () -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 0) {
            Text(title).font(.system(size: 19, weight: .semibold)).foregroundStyle(T.ink).multilineTextAlignment(.center)
            Text(text).font(.system(size: 14)).foregroundStyle(T.muted).multilineTextAlignment(.center).padding(.top, 8).padding(.bottom, 10)
            WideButton(title: action, kind: danger ? .danger : .primary) { onConfirm(); dismiss() }
            WideButton(title: "Cancel", kind: .secondary) { dismiss() }
        }
        .padding(24).padding(.top, 8)
        .fitSheet()
    }
}

struct ToastChip: View {
    let text: String?
    var body: some View {
        if let text {
            Text(text).font(.system(size: 13.5, weight: .medium)).foregroundStyle(T.ink)
                .padding(.horizontal, 18).padding(.vertical, 11)
                .background(Capsule().fill(T.container))
                .transition(.opacity.combined(with: .move(edge: .bottom)))
                .accessibilityAddTraits(.updatesFrequently)
        }
    }
}

/** Two page dots; tap one, or swipe across them. */
struct PageDots: View {
    @Binding var page: Int
    let count: Int
    let accent: Color
    var body: some View {
        HStack(spacing: 18) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == page ? accent : Color(hex: 0x7C7C82)).frame(width: i == page ? 18 : 6, height: 6)
                    .frame(width: 24, height: 30).contentShape(Rectangle())
                    .onTapGesture { withAnimation { page = i } }
                    .accessibilityLabel(i == 0 ? "Remote" : "Apps and channels")
                    .accessibilityAddTraits(i == page ? [.isSelected, .isButton] : .isButton)
            }
        }
        .gesture(DragGesture(minimumDistance: 12).onEnded { v in
            withAnimation { page = max(0, min(count - 1, page + (v.translation.width < 0 ? 1 : -1))) }
        })
    }
}
