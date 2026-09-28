import SwiftUI
import UIKit

struct BrowserBar: View {
    @ObservedObject var model: BrowserModel
    let tabCount: Int
    let showTabs: () -> Void
    let showSettings: () -> Void

    @State private var text = ""
    @FocusState private var editing: Bool

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                Rectangle()
                    .fill(Theme.ink)
                    .frame(width: proxy.size.width * model.progress, height: 2)
                    .opacity(model.isLoading ? 1 : 0)
            }
            .frame(height: 2)

            HStack(spacing: 2) {
                barButton("chevron.left", label: "Back", enabled: model.canStepBack) { model.goBack() }
                barButton("chevron.right", label: "Forward", enabled: model.canGoForward) { model.goForward() }

                if !model.showingNewTab {
                    TextField("Search or address", text: $text)
                        .focused($editing)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.webSearch)
                        .submitLabel(.go)
                        .multilineTextAlignment(editing ? .leading : .center)
                        .onSubmit {
                            model.open(text)
                            editing = false
                        }
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.ink)
                        .padding(.horizontal, 12)
                        .frame(height: 38)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(Theme.ink.opacity(0.25), lineWidth: 1))
                        .accessibilityIdentifier("addressField")

                    barButton(model.isLoading ? "xmark" : "arrow.clockwise",
                              label: model.isLoading ? "Stop" : "Reload",
                              enabled: model.blockedHost == nil) { model.reloadOrStop() }
                } else {
                    Spacer()
                }
                Button(action: showTabs) {
                    Text("\(tabCount)")
                        .font(.system(size: 13, weight: .semibold))
                        .frame(minWidth: 23, minHeight: 25)
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(lineWidth: 1.5))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Tabs")
                .accessibilityValue("\(tabCount)")
                .accessibilityIdentifier("tabsButton")
                barButton("gearshape", label: "Settings", enabled: true, action: showSettings)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
        }
        .background(Theme.paper)
        .onAppear { text = model.addressForDisplay }
        .onChange(of: model.addressForDisplay) { _, value in
            if !editing { text = value }
        }
        .onChange(of: editing) { _, isEditing in
            text = isEditing ? model.addressForEditing : model.addressForDisplay
            if isEditing {
                DispatchQueue.main.async {
                    UIApplication.shared.sendAction(#selector(UIResponder.selectAll(_:)), to: nil, from: nil, for: nil)
                }
            }
        }
    }

    private func barButton(_ symbol: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .regular))
                .frame(width: 44, height: 44)
        }
        .foregroundStyle(Theme.ink)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
        .accessibilityLabel(label)
    }
}
