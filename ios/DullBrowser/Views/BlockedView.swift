import SwiftUI

/// Shown in place of a listed site. A dead end: nothing to tap, no way through.
struct BlockedView: View {
    let host: String

    var body: some View {
        VStack(spacing: 0) {
            ClosedMark()
                .stroke(Theme.ink, lineWidth: 2.8)
                .frame(width: 72, height: 72)
                .padding(.bottom, 28)
                .accessibilityHidden(true)
            Text("This site stays closed in Dull Browser.")
                .font(.system(size: 17))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.ink)
                .accessibilityIdentifier("blockedMessage")
            Text(host)
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .padding(.top, 12)
                .accessibilityIdentifier("blockedHost")
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper)
    }
}

struct LoadErrorView: View {
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Text("This page did not load.")
                .font(.system(size: 17))
                .foregroundStyle(Theme.ink)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper)
    }
}
