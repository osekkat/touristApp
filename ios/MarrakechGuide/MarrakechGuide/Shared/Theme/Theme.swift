import SwiftUI

// Compatibility namespace that mirrors the canonical `Theme` tokens.
// New code should prefer `Theme` directly to avoid parallel token drift.
enum MarrakechTheme {
    enum Colors {
        static let terracotta50 = Theme.Terracotta.t50
        static let terracotta100 = Theme.Terracotta.t100
        static let terracotta200 = Theme.Terracotta.t200
        static let terracotta300 = Theme.Terracotta.t300
        static let terracotta400 = Theme.Terracotta.t400
        static let terracotta500 = Theme.Terracotta.t500
        static let terracotta600 = Theme.Terracotta.t600
        static let terracotta700 = Theme.Terracotta.t700
        static let terracotta800 = Theme.Terracotta.t800
        static let terracotta900 = Theme.Terracotta.t900

        static let neutral50 = Theme.Neutral.n50
        static let neutral100 = Theme.Neutral.n100
        static let neutral200 = Theme.Neutral.n200
        static let neutral500 = Theme.Neutral.n500
        static let neutral700 = Theme.Neutral.n700
        static let neutral900 = Theme.Neutral.n900

        static let success = Theme.Semantic.success
        static let warning = Theme.Semantic.warning
        static let error = Theme.Semantic.error
        static let info = Theme.Semantic.info

        static let background = Theme.Adaptive.background
        static let surface = Theme.Adaptive.surface
        static let textPrimary = Theme.Adaptive.textPrimary
        static let textSecondary = Theme.Adaptive.textSecondary
    }

    enum FairnessColors {
        static let low = Colors.warning
        static let fair = Colors.success
        static let high = Colors.terracotta400
        static let veryHigh = Colors.error
    }

    enum Spacing {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
        static let xxl: CGFloat = 48
    }

    enum CornerRadius {
        static let sm: CGFloat = 4
        static let md: CGFloat = 8
        static let lg: CGFloat = 12
        static let xl: CGFloat = 16
    }

    enum Typography {
        static let largeTitle = Font.system(.largeTitle, design: .default)
        static let title = Font.system(size: 28, weight: .semibold, design: .default)
        static let title2 = Font.system(size: 22, weight: .semibold, design: .default)
        static let title3 = Font.system(size: 20, weight: .semibold, design: .default)
        static let headline = Font.system(.headline, design: .default)
        static let body = Font.system(.body, design: .default)
        static let callout = Font.system(.callout, design: .default)
        static let subheadline = Font.system(.subheadline, design: .default)
        static let footnote = Font.system(.footnote, design: .default)
        static let caption = Font.system(.caption, design: .default)
    }
}

extension View {
    func marrakechPrimaryBackground() -> some View {
        primaryBackground()
    }

    func marrakechCardStyle() -> some View {
        cardStyle()
    }

    func marrakechSectionHeaderStyle() -> some View {
        sectionHeaderStyle()
    }
}
