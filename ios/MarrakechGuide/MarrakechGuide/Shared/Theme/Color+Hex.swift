import SwiftUI

extension Color {
    // Compatibility helper for code that wants an explicit Marrakech-specific label.
    // Keep this distinct from `init(hex:opacity:)` in `Shared/Theme.swift`.
    init(marrakechHex: UInt, alpha: Double = 1.0) {
        let red = Double((marrakechHex >> 16) & 0xFF) / 255.0
        let green = Double((marrakechHex >> 8) & 0xFF) / 255.0
        let blue = Double(marrakechHex & 0xFF) / 255.0
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}
