import Foundation

/// Everything that could one day sit behind a paid plan. All of it is unlocked today.
///
/// A lock may only hide a way to add strictness or comfort. Rules the person already chose,
/// such as sites they added to the block list or a pause they turned on, keep applying.
enum Feature: String, CaseIterable {
    case mindfulPause
    case customBlocklist
    case countdowns
    case stats
    case weeklyShareCard
    case milestones
    case blockedPageNote
    case readLater
    case readingWindow
    case bookmarks
    case tabThumbnails

    var isUnlocked: Bool { Entitlements.current.unlocks(self) }
}

struct Entitlements {
    static var current = Entitlements()

    var locked: Set<Feature> = []

    func unlocks(_ feature: Feature) -> Bool { !locked.contains(feature) }
}
