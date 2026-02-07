import SwiftUI

/// Design tokens and theme configuration for the Marrakech Guide app.
///
/// The visual identity is rooted in Marrakech's warm terracotta palette,
/// evoking the clay walls and earthy tones of the ancient medina.
public enum Theme {

    // MARK: - Primary Colors (Terracotta)

    /// Terracotta color palette - the soul of Marrakech
    public enum Terracotta {
        public static let t50 = Color(hex: 0xFDF5F2)
        public static let t100 = Color(hex: 0xFAE6DE)
        public static let t200 = Color(hex: 0xF5C9B8)
        public static let t300 = Color(hex: 0xED9F7F)
        public static let t400 = Color(hex: 0xE57A4F)
        public static let t500 = Color(hex: 0xD4572B)  // Primary
        public static let t600 = Color(hex: 0xB84421)
        public static let t700 = Color(hex: 0x8F341A)
        public static let t800 = Color(hex: 0x6A2714)
        public static let t900 = Color(hex: 0x4A1C0F)
    }

    // MARK: - Neutral Colors

    public enum Neutral {
        public static let n50 = Color(hex: 0xFAFAFA)
        public static let n100 = Color(hex: 0xF5F5F5)
        public static let n200 = Color(hex: 0xE5E5E5)
        public static let n300 = Color(hex: 0xD4D4D4)
        public static let n400 = Color(hex: 0xA3A3A3)
        public static let n500 = Color(hex: 0x737373)
        public static let n600 = Color(hex: 0x525252)
        public static let n700 = Color(hex: 0x404040)
        public static let n800 = Color(hex: 0x262626)
        public static let n900 = Color(hex: 0x171717)
    }

    // MARK: - Semantic Colors

    public enum Semantic {
        public static let success = Color(hex: 0x22C55E)
        public static let warning = Color(hex: 0xEAB308)
        public static let error = Color(hex: 0xEF4444)
        public static let info = Color(hex: 0x3B82F6)
    }

    // MARK: - Fairness Meter Colors

    public enum Fairness {
        /// Suspiciously cheap - buyer beware
        public static let low = Semantic.warning

        /// Within expected range
        public static let fair = Semantic.success

        /// Slightly high but negotiable
        public static let high = Terracotta.t400

        /// Tourist trap pricing
        public static let veryHigh = Semantic.error

        public static func color(for level: FairnessLevel) -> Color {
            switch level {
            case .low: return low
            case .fair: return fair
            case .high: return high
            case .veryHigh: return veryHigh
            }
        }
    }

    // MARK: - Adaptive Colors

    public enum Adaptive {
        /// Primary brand color
        public static var primary: Color { Terracotta.t500 }

        /// Secondary accent
        public static var secondary: Color { Terracotta.t400 }

        /// Background colors
        public static var background: Color {
            Color(light: Neutral.n50, dark: Neutral.n900)
        }

        /// Surface colors (cards, sheets)
        public static var surface: Color {
            Color(light: .white, dark: Neutral.n800)
        }

        /// Elevated surface (modals, popovers)
        public static var surfaceElevated: Color {
            Color(light: .white, dark: Neutral.n700)
        }

        /// Primary text color
        public static var textPrimary: Color {
            Color(light: Neutral.n900, dark: Neutral.n50)
        }

        /// Secondary text color
        public static var textSecondary: Color {
            Color(light: Neutral.n500, dark: Neutral.n400)
        }

        /// Disabled text/elements
        public static var textDisabled: Color {
            Color(light: Neutral.n300, dark: Neutral.n600)
        }

        /// Separator/divider color
        public static var separator: Color {
            Color(light: Neutral.n200, dark: Neutral.n700)
        }
    }

    // MARK: - Spacing

    public enum Spacing {
        public static let xs: CGFloat = 4
        public static let sm: CGFloat = 8
        public static let md: CGFloat = 16
        public static let lg: CGFloat = 24
        public static let xl: CGFloat = 32
        public static let xxl: CGFloat = 48
    }

    // MARK: - Corner Radius

    public enum CornerRadius {
        public static let sm: CGFloat = 4
        public static let md: CGFloat = 8
        public static let lg: CGFloat = 12
        public static let xl: CGFloat = 16
        public static let full: CGFloat = 9999
    }

    // MARK: - Shadows

    public enum Shadow {
        public static let sm = ShadowStyle(
            color: .black.opacity(0.05),
            radius: 2,
            x: 0,
            y: 1
        )

        public static let md = ShadowStyle(
            color: .black.opacity(0.1),
            radius: 4,
            x: 0,
            y: 2
        )

        public static let lg = ShadowStyle(
            color: .black.opacity(0.15),
            radius: 8,
            x: 0,
            y: 4
        )
    }

    // MARK: - Animation

    public enum Animation {
        public static let quick: SwiftUI.Animation = .easeOut(duration: 0.15)
        public static let standard: SwiftUI.Animation = .easeInOut(duration: 0.25)
        public static let slow: SwiftUI.Animation = .easeInOut(duration: 0.4)
        public static let spring: SwiftUI.Animation = .spring(response: 0.3, dampingFraction: 0.7)
    }
}

// MARK: - Supporting Types

public enum FairnessLevel: String, CaseIterable, Codable {
    case low
    case fair
    case high
    case veryHigh
}

public struct ShadowStyle {
    public let color: Color
    public let radius: CGFloat
    public let x: CGFloat
    public let y: CGFloat
}

// MARK: - Color Extensions

extension Color {
    /// Create a color from hex value
    init(hex: UInt, opacity: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// Create an adaptive color for light/dark mode
    init(light: Color, dark: Color) {
        self.init(uiColor: UIColor { traitCollection in
            switch traitCollection.userInterfaceStyle {
            case .dark:
                return UIColor(dark)
            default:
                return UIColor(light)
            }
        })
    }
}

// MARK: - View Modifiers

extension View {
    /// Apply primary background styling
    public func primaryBackground() -> some View {
        self.background(Theme.Adaptive.background)
    }

    /// Apply surface styling (for cards)
    public func surfaceStyle() -> some View {
        self
            .background(Theme.Adaptive.surface)
            .cornerRadius(Theme.CornerRadius.md)
    }

    /// Apply card styling with shadow
    public func cardStyle() -> some View {
        self
            .background(Theme.Adaptive.surface)
            .cornerRadius(Theme.CornerRadius.lg)
            .shadow(
                color: Theme.Shadow.md.color,
                radius: Theme.Shadow.md.radius,
                x: Theme.Shadow.md.x,
                y: Theme.Shadow.md.y
            )
    }

    /// Apply shadow style
    public func shadow(_ style: ShadowStyle) -> some View {
        self.shadow(
            color: style.color,
            radius: style.radius,
            x: style.x,
            y: style.y
        )
    }

    /// Apply section header styling
    public func sectionHeaderStyle() -> some View {
        self
            .font(.headline)
            .foregroundColor(Theme.Adaptive.textPrimary)
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
    }

    /// Apply primary button styling
    public func primaryButtonStyle() -> some View {
        self
            .font(.headline)
            .foregroundColor(.white)
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)
            .background(Theme.Adaptive.primary)
            .cornerRadius(Theme.CornerRadius.lg)
    }

    /// Apply secondary button styling
    public func secondaryButtonStyle() -> some View {
        self
            .font(.headline)
            .foregroundColor(Theme.Adaptive.primary)
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)
            .background(Theme.Adaptive.surface)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.CornerRadius.lg)
                    .stroke(Theme.Adaptive.primary, lineWidth: 1.5)
            )
            .cornerRadius(Theme.CornerRadius.lg)
    }
}

// MARK: - Typography Helpers

extension Font {
    /// Large title (34pt) - Main screen titles
    public static var themelargeTitle: Font { .largeTitle }

    /// Title (28pt) - Section headers
    public static var themeTitle: Font { .title }

    /// Title2 (22pt) - Card titles
    public static var themeTitle2: Font { .title2 }

    /// Title3 (20pt) - Subsection headers
    public static var themeTitle3: Font { .title3 }

    /// Headline (17pt semibold) - Important labels
    public static var themeHeadline: Font { .headline }

    /// Body (17pt) - Primary content
    public static var themeBody: Font { .body }

    /// Callout (16pt) - Secondary content
    public static var themeCallout: Font { .callout }

    /// Subheadline (15pt) - Supporting text
    public static var themeSubheadline: Font { .subheadline }

    /// Footnote (13pt) - Metadata, hints
    public static var themeFootnote: Font { .footnote }

    /// Caption (12pt) - Labels, badges
    public static var themeCaption: Font { .caption }
}
