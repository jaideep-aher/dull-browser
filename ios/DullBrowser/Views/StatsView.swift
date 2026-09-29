import SwiftUI

struct StatsView: View {
    @ObservedObject private var stats = Stats.shared
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage(Stats.minutesKey, store: BrowserPreferences.defaults) private var minutes = Stats.defaultMinutes
    @State private var card: Image?

    var body: some View {
        let today = stats.day(stats.today) ?? DayStats(day: stats.today)
        let week = stats.week()
        let streak = stats.currentStreak
        Form {
            if Feature.weeklyShareCard.isUnlocked {
                Section {
                    if let card {
                        card.resizable().scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.hairline))
                            .accessibilityLabel(ShareCard.line(week: week, streak: streak))
                            .accessibilityIdentifier("shareCardPreview")
                        ShareLink(item: card, message: Text(ShareCard.line(week: week, streak: streak)),
                                  preview: SharePreview("This week in Dull", image: card)) {
                            Label("Share this week", systemImage: "square.and.arrow.up")
                        }
                        .accessibilityIdentifier("shareWeek")
                    }
                } footer: {
                    Text("The card shows totals only, never which sites you tried.")
                }
            }

            Section("Today") {
                LabeledContent("Blocked attempts", value: today.blockedTotal.formatted())
                    .accessibilityIdentifier("statsTodayBlocked")
                LabeledContent("Paused, then went back", value: today.wentBack.formatted())
                LabeledContent("Paused, then continued", value: today.continued.formatted())
            }

            Section {
                LabeledContent("Blocked attempts", value: week.attempts.formatted())
                    .accessibilityIdentifier("statsWeekBlocked")
                LabeledContent("Went back from a pause", value: week.wentBack.formatted())
                LabeledContent("Time saved", value: Stats.savedLabel(minutes: week.minutesSaved))
                Picker("Minutes per attempt", selection: $minutes) {
                    ForEach(Stats.minuteOptions, id: \.self) { Text("\($0) min").tag($0) }
                }
            } header: {
                Text("Last 7 days")
            } footer: {
                Text("Time saved counts each blocked attempt and each pause you went back from as \(minutes) minutes you did not spend there.")
            }

            Section {
                LabeledContent("Streak", value: streak == 1 ? "1 day" : "\(streak) days")
                    .accessibilityIdentifier("statsStreak")
            } footer: {
                Text("A day counts when you open Dull and no pause is turned off that day.")
            }

            if Feature.milestones.isUnlocked {
                Section("Milestones") {
                    ForEach(Stats.milestoneDays, id: \.self) { days in
                        let reached = stats.data.milestones.first { $0.days == days }
                        HStack {
                            Image(systemName: reached == nil ? "circle" : "leaf.fill")
                                .foregroundStyle(reached == nil ? Theme.hairline : Theme.ink)
                                .accessibilityHidden(true)
                            Text("\(days) days")
                                .foregroundStyle(reached == nil ? Theme.muted : Theme.ink)
                            Spacer()
                            if let reached, let date = DayKey.date(from: reached.reached) {
                                Text(date.formatted(date: .abbreviated, time: .omitted)).foregroundStyle(Theme.muted)
                            }
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("milestone_\(days)")
                    }
                }
            }

            if !week.topSites.isEmpty {
                Section {
                    ForEach(week.topSites, id: \.site) { entry in
                        LabeledContent(SiteName.display(entry.site), value: entry.count.formatted())
                    }
                } header: {
                    Text("Most tried this week")
                } footer: {
                    Text("Everything here stays on this iPhone.")
                }
            }
        }
        .navigationTitle("Stats")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { card = ShareCard.render(week: week, streak: streak, scheme: colorScheme) }
        .onChange(of: colorScheme) { _, scheme in card = ShareCard.render(week: week, streak: streak, scheme: scheme) }
        .onChange(of: minutes) { _, _ in
            card = ShareCard.render(week: stats.week(), streak: streak, scheme: colorScheme)
        }
    }
}

/// A square card for sharing a week. Totals only, no site names.
struct ShareCard: View {
    let week: WeekSummary
    let streak: Int

    static func line(week: WeekSummary, streak: Int) -> String {
        var parts = ["Dull blocked \(week.attempts) \(week.attempts == 1 ? "attempt" : "attempts") this week"]
        if streak > 0 { parts.append("\(streak)-day streak") }
        if week.minutesSaved > 0 { parts.append("\(Stats.savedLabel(minutes: week.minutesSaved)) saved") }
        return parts.joined(separator: " · ")
    }

    @MainActor
    static func render(week: WeekSummary, streak: Int, scheme: ColorScheme) -> Image? {
        let renderer = ImageRenderer(content: ShareCard(week: week, streak: streak).environment(\.colorScheme, scheme))
        renderer.scale = 3
        return renderer.uiImage.map(Image.init(uiImage:))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ClosedMark()
                .stroke(Theme.ink, lineWidth: 2.4)
                .frame(width: 44, height: 44)
            Spacer()
            Text("\(week.attempts)")
                .font(.system(size: 64, weight: .light))
                .monospacedDigit()
            Text(week.attempts == 1 ? "attempt blocked this week" : "attempts blocked this week")
                .font(.system(size: 17))
                .foregroundStyle(Theme.muted)
            HStack(spacing: 24) {
                if streak > 0 { figure("\(streak)", "day streak") }
                if week.minutesSaved > 0 { figure(Stats.savedLabel(minutes: week.minutesSaved), "saved") }
            }
            .padding(.top, 20)
            Spacer()
            Text("Dull Browser")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Theme.muted)
        }
        .foregroundStyle(Theme.ink)
        .padding(28)
        .frame(width: 360, height: 360, alignment: .leading)
        .background(Theme.paper)
    }

    private func figure(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.system(size: 26, weight: .light)).monospacedDigit()
            Text(label).font(.system(size: 13)).foregroundStyle(Theme.muted)
        }
    }
}
