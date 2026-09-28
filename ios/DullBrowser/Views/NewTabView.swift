import SwiftUI

/// A clock and a search field. Nothing else.
struct NewTabView: View {
    var onSubmit: (String) -> Void

    @State private var query = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            TimelineView(.everyMinute) { context in
                Text(context.date, format: .dateTime.hour().minute())
                    .font(.system(size: 64, weight: .light))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .accessibilityIdentifier("clock")
            }
            TextField("Search", text: $query)
                .focused($focused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.webSearch)
                .submitLabel(.search)
                .onSubmit {
                    let text = query
                    query = ""
                    onSubmit(text)
                }
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 16)
                .frame(height: 48)
                .background(RoundedRectangle(cornerRadius: 12).stroke(Theme.ink.opacity(0.35), lineWidth: 1))
                .padding(.horizontal, 32)
                .accessibilityIdentifier("searchField")
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { focused = false }
    }
}
