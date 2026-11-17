import SwiftUI

/** Search the TV, or type into a box that's already open on it. Dictate with the keyboard's mic. */
struct KeyboardSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var search = true
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(search ? "Search on TV" : "Type on TV").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).frame(maxWidth: .infinity)
            Picker("Mode", selection: $search) { Text("Search").tag(true); Text("Type").tag(false) }.pickerStyle(.segmented)
            Text(search ? "Find movies, shows and apps. Tap the mic on your keyboard to speak." : "Open a search box on the TV, then type here and send.")
                .font(.system(size: 14)).foregroundStyle(Color(hex: 0xE0E0E4))
            TextField(search ? "Movies, shows, apps" : "Text for the TV", text: $text)
                .focused($focused).submitLabel(search ? .search : .send)
                .onSubmit { go() }
                .padding(.horizontal, 12).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 12).fill(T.high)).foregroundStyle(T.ink)
            if search {
                WideButton(title: "Search", enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty) { go() }
            } else {
                HStack(spacing: 12) {
                    WideButton(title: "Delete", kind: .secondary) { vm.backspace() }
                    WideButton(title: "Send", enabled: !text.isEmpty) { go() }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .presentationDetents([.height(360), .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(T.sheet)
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { focused = true } }
    }

    private func go() {
        guard !text.isEmpty else { return }
        if search { vm.search(text); dismiss() } else { vm.typeText(text); text = "" }
    }
}
