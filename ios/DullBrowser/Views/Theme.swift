import SwiftUI

enum Theme {
    static let paper = Color(red: 0xF3 / 255, green: 0xEF / 255, blue: 0xE6 / 255)
    static let ink = Color(red: 0x1C / 255, green: 0x19 / 255, blue: 0x17 / 255)
    static let muted = Color(red: 0x6F / 255, green: 0x6A / 255, blue: 0x64 / 255)
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
