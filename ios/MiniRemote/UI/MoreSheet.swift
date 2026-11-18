import SwiftUI

/** More: keys that don't fit on Home, the TV's inputs by name, and a sleep timer. */
struct MoreSheet: View {
    @ObservedObject var vm: RemoteModel

    private let keys: [(RemoteKey, String, String)] = [
        (.settings, "gearshape", "Menu"), (.info, "info.circle", "Info"), (.guide, "list.bullet.rectangle", "Guide"),
        (.chList, "list.bullet", "Channels"), (.subtitles, "captions.bubble", "Subtitles"), (.audio, "waveform", "Audio"),
        (.rewind, "backward.fill", "Rewind"), (.fastForward, "forward.fill", "Forward"),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("More").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).frame(maxWidth: .infinity).padding(.top, 20)
                SectionTitle(text: "Keys")
                GlassEffectContainer(spacing: 12) {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 14) {
                        ForEach(keys, id: \.0) { k, sym, label in
                            VStack(spacing: 6) {
                                GlassKey(symbol: sym, label: label, size: 56, icon: 19) { vm.press(k) }
                                Text(label).font(.system(size: 11.5)).foregroundStyle(T.muted)
                            }
                        }
                    }
                }
                HStack {
                    ForEach([(RemoteKey.red, Color(hex: 0xE5484D)), (.green, Color(hex: 0x30A46C)), (.yellow, Color(hex: 0xF5D90A)), (.blue, Color(hex: 0x3E63DD))], id: \.0) { k, c in
                        Button { vm.press(k) } label: { Capsule().fill(c).frame(height: 30) }
                            .accessibilityLabel(k.word)
                    }
                }
                .padding(.top, 14)

                SectionTitle(text: "Inputs")
                if vm.inputs.isEmpty {
                    HStack(spacing: 14) {
                        GlassKey(symbol: "rectangle.portrait.and.arrow.right", label: "Input", size: 56, icon: 19) { vm.press(.input) }
                        Text("This TV doesn't list its inputs. Press Input to cycle through them.").font(.system(size: 13)).foregroundStyle(T.muted)
                    }
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(vm.inputs) { i in
                                Button { vm.switchInput(i) } label: {
                                    Text(i.name).font(.system(size: 14, weight: .medium)).foregroundStyle(.white).padding(.horizontal, 16).frame(height: 40)
                                }
                                .buttonStyle(.plain).glassEffect(.regular.interactive(), in: .capsule)
                            }
                        }
                    }
                }

                SectionTitle(text: "Sleep timer")
                HStack(spacing: 8) {
                    ForEach([nil, 15, 30, 60, 90] as [Int?], id: \.self) { m in
                        let on = m == nil && vm.sleepAt == nil
                        Button { vm.setSleep(m) } label: {
                            Text(m.map { "\($0) m" } ?? "Off").font(.system(size: 13, weight: .medium)).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).frame(height: 40)
                                .background(on ? Capsule().fill(.white.opacity(0.18)) : nil)
                        }
                        .buttonStyle(.plain).glassEffect(.regular.interactive(), in: .capsule)
                    }
                }
                if let at = vm.sleepAt {
                    Label("TV turns off at \(at.formatted(date: .omitted, time: .shortened)). Keep Mini Remote open.", systemImage: "moon")
                        .font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.top, 10)
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 24)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .onAppear { vm.loadInputs() }
    }
}
