import SwiftUI

/// A clock, unless turned off, a search field, and whatever the person chose to keep here:
/// the next countdown and a few bookmarks.
struct NewTabView: View {
    var showClock = true
    var countdown: String? = nil
    var quickLinks: [Bookmark] = []
    var milestone: Int? = nil
    var dismissMilestone: () -> Void = {}
    var openLink: (URL) -> Void = { _ in }
    var onSubmit: (String) -> Void

    @State private var query = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            VStack(spacing: 10) {
                if showClock {
                    TimelineView(.everyMinute) { context in
                        Text(context.date, format: .dateTime.hour().minute())
                            .font(.system(size: 64, weight: .light))
                            .monospacedDigit()
                            .foregroundStyle(Theme.ink)
                            .accessibilityIdentifier("clock")
                    }
                }
                if let countdown {
                    Text(countdown)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.muted)
                        .accessibilityIdentifier("countdownLabel")
                }
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
            if !quickLinks.isEmpty {
                QuickLinks(links: quickLinks, open: openLink)
                    .padding(.horizontal, 32)
            }
            Spacer()
            Spacer()
            if let milestone {
                MilestoneNotice(days: milestone, dismiss: dismissMilestone)
                    .padding(.horizontal, 32)
                    .padding(.bottom, 12)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper)
        .accessibilityElement(children: .contain)
        .contentShape(Rectangle())
        .onTapGesture { focused = false }
    }
}

private struct QuickLinks: View {
    let links: [Bookmark]
    let open: (URL) -> Void

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 4), spacing: 16) {
            ForEach(Array(links.enumerated()), id: \.element.id) { index, link in
                Button { open(link.url) } label: {
                    VStack(spacing: 6) {
                        Text(String((link.url.host ?? link.title).replacingOccurrences(of: "www.", with: "").prefix(1)).uppercased())
                            .font(.system(size: 17, weight: .medium))
                            .foregroundStyle(Theme.ink)
                            .frame(width: 44, height: 44)
                            .background(Circle().stroke(Theme.ink.opacity(0.25), lineWidth: 1))
                        Text(link.title)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.muted)
                            .lineLimit(1)
                    }
                }
                .accessibilityLabel(link.title)
                .accessibilityIdentifier("quickLink_\(index)")
            }
        }
    }
}

/// A single quiet line when a streak milestone is reached. Shown once.
private struct MilestoneNotice: View {
    let days: Int
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "leaf")
                .foregroundStyle(Theme.muted)
                .accessibilityHidden(true)
            Text("\(days) calm days in a row.")
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
            Spacer()
            Button(action: dismiss) {
                Image(systemName: "xmark").font(.system(size: 13)).frame(width: 32, height: 32)
            }
            .foregroundStyle(Theme.muted)
            .accessibilityLabel("Dismiss")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 12).stroke(Theme.hairline, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("milestoneNotice")
    }
}
