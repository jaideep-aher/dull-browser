import AuthenticationServices
import SwiftUI
import UIKit

/// Categories can be turned on at once. Turning anything off is scheduled for 24 hours later.
struct PauseSettingsView: View {
    @ObservedObject private var pauses = PauseList.shared
    @State private var newSite = ""
    @State private var message: String?
    @State private var confirming: PendingChoice?

    private struct PendingChoice: Identifiable {
        let kind: ScheduledRemoval.Kind
        let value: String
        let name: String
        var id: String { "\(kind.rawValue):\(value)" }
    }

    var body: some View {
        Form {
            Section {
                ForEach(PauseCategory.allCases) { category in
                    categoryRow(category)
                }
            } header: {
                Text("Categories")
            } footer: {
                Text("Major news sites are already blocked and stay blocked. Pauses cover sites that are not.")
            }

            Section {
                HStack {
                    TextField("example.com", text: $newSite)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .submitLabel(.done)
                        .onSubmit(addSite)
                        .accessibilityIdentifier("pauseSiteField")
                    Button("Add", action: addSite)
                        .disabled(newSite.trimmingCharacters(in: .whitespaces).isEmpty)
                        .accessibilityIdentifier("pauseSiteAdd")
                }
                if let message {
                    Text(message).foregroundStyle(Theme.muted).accessibilityIdentifier("pauseSiteMessage")
                }
                ForEach(pauses.settings.sites, id: \.self) { site in
                    row(title: site, detail: nil, kind: .site, value: site, isOn: true)
                }
            } header: {
                Text("Your sites")
            }

            Section {
                EmptyView()
            } footer: {
                Text("Turning a pause on happens now. Turning one off takes 24 hours, and you can change your mind before then. Blocked sites are never paused; they stay closed.")
            }
        }
        .navigationTitle("Pause before sites")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { pauses.applyDueRemovals() }
        .confirmationDialog("Turn this pause off?", isPresented: Binding(get: { confirming != nil },
                                                                         set: { if !$0 { confirming = nil } }),
                            titleVisibility: .visible, presenting: confirming) { choice in
            Button("Turn off in 24 hours", role: .destructive) {
                switch choice.kind {
                case .category: PauseCategory(rawValue: choice.value).map(pauses.scheduleRemoval(of:))
                case .site: pauses.scheduleRemoval(ofSite: choice.value)
                }
            }
            .accessibilityIdentifier("confirmPauseRemoval")
        } message: { choice in
            Text("\(choice.name) keeps its pause for 24 more hours. You can cancel before then.")
        }
    }

    private func categoryRow(_ category: PauseCategory) -> some View {
        row(title: category.title,
            detail: category.domains.prefix(4).joined(separator: ", ") + "…",
            kind: .category, value: category.rawValue, isOn: pauses.isOn(category)) {
            pauses.turnOn(category)
        }
    }

    private func row(title: String, detail: String?, kind: ScheduledRemoval.Kind, value: String, isOn: Bool,
                     turnOn: (() -> Void)? = nil) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                if let detail { Text(detail).font(.caption).foregroundStyle(Theme.muted).lineLimit(1) }
                if let pending = pauses.pendingRemoval(kind, value) {
                    Text("Turns off \(pending.effectiveAt.formatted(.relative(presentation: .named)))")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                        .accessibilityIdentifier("pauseRemovalPending_\(value)")
                }
            }
            Spacer()
            if !isOn, let turnOn {
                Button("Turn on", action: turnOn)
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("pauseOn_\(value)")
            } else if let pending = pauses.pendingRemoval(kind, value) {
                Button("Keep") { pauses.cancelRemoval(pending) }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("pauseKeep_\(value)")
            } else {
                Button("Turn off…") { confirming = PendingChoice(kind: kind, value: value, name: title) }
                    .foregroundStyle(Theme.muted)
                    .accessibilityIdentifier("pauseOff_\(value)")
            }
        }
    }

    private func addSite() {
        switch pauses.addSite(newSite) {
        case .added(let site): message = "\(site) now pauses first."; newSite = ""
        case .alreadyPaused(let site): message = "\(site) already pauses first."; newSite = ""
        case .blocked(let site): message = "\(site) is blocked, which is stronger than a pause."; newSite = ""
        case .invalid: message = "Enter a site name like example.com."
        }
    }
}

/// Add-only. The list has no delete button, swipe action or edit mode.
struct AddedSitesView: View {
    @State private var entry = ""
    @State private var pending: String?
    @State private var message: String?
    @State private var sites = CustomBlocklist.load(from: BrowserPreferences.defaults)

    var body: some View {
        Form {
            Section {
                HStack {
                    TextField("example.com", text: $entry)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .submitLabel(.done)
                        .onSubmit(review)
                        .accessibilityIdentifier("addedSiteField")
                    Button("Block", action: review)
                        .disabled(entry.trimmingCharacters(in: .whitespaces).isEmpty)
                        .accessibilityIdentifier("addedSiteBlock")
                }
                if let message {
                    Text(message).foregroundStyle(Theme.muted).accessibilityIdentifier("addedSiteMessage")
                }
            } footer: {
                Text("A site you add is blocked like the sites on the built-in list, including its subdomains. It cannot be removed later.")
            }

            if !sites.isEmpty {
                Section("Blocked by you") {
                    ForEach(sites.reversed(), id: \.self) { site in
                        Label(site, systemImage: "lock.fill")
                            .accessibilityIdentifier("addedSite_\(site)")
                    }
                }
            }
        }
        .navigationTitle("Sites you added")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Block \(pending ?? "") for good?", isPresented: Binding(get: { pending != nil },
                                                                                    set: { if !$0 { pending = nil } }),
                            titleVisibility: .visible) {
            Button("Block for good", role: .destructive) {
                guard let pending else { return }
                if case .added(let site) = CustomBlocklist.add(pending) {
                    message = "\(site) is now blocked."
                    entry = ""
                }
                sites = CustomBlocklist.load(from: BrowserPreferences.defaults)
                self.pending = nil
            }
            .accessibilityIdentifier("confirmAddedSite")
        } message: {
            Text("There is no way to unblock a site you add.")
        }
    }

    private func review() {
        guard let site = SiteAddress.normalize(entry) else {
            message = "Enter a site name like example.com."
            return
        }
        if SiteBlocker.shared.isListed(host: site) {
            message = "\(site) is already blocked."
            entry = ""
            return
        }
        message = nil
        pending = site
    }
}

struct CountdownsView: View {
    @ObservedObject private var countdowns = Countdowns.shared
    @State private var name = ""
    @State private var date = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()

    var body: some View {
        Form {
            Section {
                TextField("Name, like Finals", text: $name)
                    .accessibilityIdentifier("countdownName")
                DatePicker("Date", selection: $date, in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date)
                    .accessibilityIdentifier("countdownDate")
                Button("Add countdown") {
                    countdowns.add(name: name, on: date)
                    name = ""
                }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                .accessibilityIdentifier("countdownAdd")
            } footer: {
                Text("The start page shows the nearest date next to the clock. Dates that have passed are hidden.")
            }

            if !countdowns.upcoming.isEmpty {
                Section("Coming up") {
                    ForEach(countdowns.upcoming, id: \.countdown.id) { entry in
                        LabeledContent(entry.countdown.name, value: Countdowns.when(days: entry.days))
                    }
                    .onDelete { offsets in
                        let upcoming = countdowns.upcoming
                        offsets.map { upcoming[$0].countdown.id }.forEach(countdowns.remove)
                    }
                }
            }
        }
        .navigationTitle("Countdowns")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Opens the system AutoFill settings where iOS Passwords or another password manager is chosen.
enum PasswordSettings {
    @MainActor
    static func open() {
        ASSettingsHelper.openCredentialProviderAppSettings { error in
            guard error != nil else { return }
            DispatchQueue.main.async {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
        }
    }
}
