import SwiftUI
import UIKit

/// Colors come from the asset catalog, each with a light and a dark variant.
/// Light is plain white paper with near-black ink.
enum Theme {
    static let paperColor = asset("Paper")
    static let inkColor = asset("AccentColor")
    static let mutedColor = asset("Muted")

    static let paper = Color(uiColor: paperColor)
    static let ink = Color(uiColor: inkColor)
    static let muted = Color(uiColor: mutedColor)
    static let hairline = muted.opacity(0.3)

    private static func asset(_ name: String) -> UIColor {
        guard let color = UIColor(named: name, in: .main, compatibleWith: nil) else {
            fatalError("Missing color \(name) in Assets.xcassets")
        }
        return color
    }
}

enum Appearance: String, CaseIterable, Identifiable {
    case light, dark, system

    static let preferenceKey = "appearance"
    var id: String { rawValue }

    static func resolve(_ stored: String?) -> Appearance {
        stored.flatMap(Appearance.init(rawValue:)) ?? .light
    }

    var name: String {
        switch self {
        case .light: "Light"
        case .dark: "Dark"
        case .system: "System"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }

    var interfaceStyle: UIUserInterfaceStyle {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: .unspecified
        }
    }

    /// Also set on the window: `preferredColorScheme(nil)` does not undo an earlier explicit
    /// choice, and web content reads the window's style for `prefers-color-scheme`.
    @MainActor
    func apply() {
        for scene in UIApplication.shared.connectedScenes {
            (scene as? UIWindowScene)?.windows.forEach { $0.overrideUserInterfaceStyle = interfaceStyle }
        }
    }
}

/// Circle with one horizontal line, the same mark as the app icon. A pause mark would be two bars.
struct ClosedMark: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = min(rect.width, rect.height) / 64
        let center = CGPoint(x: rect.midX, y: rect.midY)
        var path = Path()
        path.addEllipse(in: CGRect(x: center.x - 22 * unit, y: center.y - 22 * unit, width: 44 * unit, height: 44 * unit))
        path.move(to: CGPoint(x: center.x - 16 * unit, y: center.y))
        path.addLine(to: CGPoint(x: center.x + 16 * unit, y: center.y))
        return path
    }
}
