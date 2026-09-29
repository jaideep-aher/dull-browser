import SwiftUI

/// Shown in place of a listed site. Nothing here leads to the site: the actions only go
/// somewhere else.
struct BlockedView: View {
    let host: String
    var site: String? = nil
    var goBack: (() -> Void)? = nil
    var openReadLater: (() -> Void)? = nil
    var startPage: (() -> Void)? = nil

    @ObservedObject private var stats = Stats.shared
    @AppStorage(BrowserPreferences.blockedNoteKey, store: BrowserPreferences.defaults) private var note = ""

    private var attemptsLine: String? {
        guard let site else { return nil }
        let count = stats.blockedToday(site: site)
        guard count > 0 else { return nil }
        let name = SiteName.display(site)
        return count == 1 ? "You tried \(name) once today." : "You tried \(name) \(count) times today."
    }

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
            if let attemptsLine {
                Text(attemptsLine)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 20)
                    .accessibilityIdentifier("blockedAttempts")
            }
            let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
            if Feature.blockedPageNote.isUnlocked, !trimmed.isEmpty {
                Text(trimmed)
                    .font(.system(size: 16, design: .serif))
                    .italic()
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.center)
                    .padding(.top, 24)
                    .accessibilityIdentifier("blockedNote")
            }
            actions.padding(.top, 36)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper)
    }

    @ViewBuilder private var actions: some View {
        HStack(spacing: 10) {
            if let goBack { action("Go back", id: "blockedGoBack", goBack) }
            if let openReadLater, Feature.readLater.isUnlocked { action("Read later", id: "blockedReadLater", openReadLater) }
            if let startPage { action("Start page", id: "blockedStartPage", startPage) }
        }
    }

    private func action(_ title: String, id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(title)
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 14)
                .frame(height: 38)
                .background(Capsule().stroke(Theme.ink.opacity(0.3), lineWidth: 1))
        }
        .accessibilityIdentifier(id)
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
