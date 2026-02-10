import SwiftUI
import UIKit

/// Reusable card container for list and detail surfaces.
struct ContentCard<Content: View>: View {
    var title: String?
    var subtitle: String?
    var onTap: (() -> Void)?
    @ViewBuilder var content: () -> Content

    @ViewBuilder
    var body: some View {
        if let onTap {
            Button(action: onTap) {
                cardBody
            }
            .buttonStyle(.plain)
            .contentShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg, style: .continuous))
        } else {
            cardBody
        }
    }

    private var cardBody: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            if let title, !title.isEmpty {
                Text(title)
                    .font(.themeTitle3)
                    .foregroundStyle(Theme.Adaptive.textPrimary)
                    .multilineTextAlignment(.leading)
                    .accessibilityAddTraits(.isHeader)
            }

            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.themeSubheadline)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .multilineTextAlignment(.leading)
            }

            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.Spacing.md)
        .cardStyle()
    }
}

enum ChipStyle {
    case filled
    case outlined
}

/// Compact label for tags, status, and filters.
struct Chip: View {
    let text: String
    var style: ChipStyle = .filled
    var tint: Color = Theme.Adaptive.primary

    var body: some View {
        Text(text)
            .font(.themeFootnote.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
            .frame(minHeight: 44)
            .foregroundStyle(foregroundColor)
            .background(backgroundColor)
            .overlay {
                if style == .outlined {
                    RoundedRectangle(cornerRadius: Theme.CornerRadius.full, style: .continuous)
                        .stroke(tint, lineWidth: 1.25)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.full, style: .continuous))
            .accessibilityLabel(text)
    }

    private var foregroundColor: Color {
        switch style {
        case .filled:
            return .white
        case .outlined:
            return tint
        }
    }

    private var backgroundColor: Color {
        switch style {
        case .filled:
            return tint
        case .outlined:
            return Theme.Adaptive.surface
        }
    }
}

/// Standardized MAD range rendering.
struct PriceTag: View {
    let minMAD: Int?
    let maxMAD: Int?

    var body: some View {
        Text(formattedValue)
            .font(.themeFootnote.weight(.semibold))
            .foregroundStyle(Theme.Adaptive.primary)
            .padding(.horizontal, Theme.Spacing.sm)
            .padding(.vertical, Theme.Spacing.xs)
            .background(Theme.Terracotta.t50)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.sm, style: .continuous))
            .accessibilityLabel("Price \(formattedValue)")
    }

    private var formattedValue: String {
        switch (minMAD, maxMAD) {
        case let (.some(min), .some(max)) where min == max:
            return "~\(min) MAD"
        case let (.some(min), .some(max)):
            return "\(min)-\(max) MAD"
        case let (.some(min), .none):
            return "\(min)+ MAD"
        case let (.none, .some(max)):
            return "Up to \(max) MAD"
        case (.none, .none):
            return "Price unavailable"
        }
    }
}

/// Consistent section heading with optional action.
struct SectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .center, spacing: Theme.Spacing.sm) {
            Text(title)
                .font(.themeTitle3)
                .foregroundStyle(Theme.Adaptive.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)

            if let actionTitle, !actionTitle.isEmpty {
                Button(actionTitle) {
                    action?()
                }
                .font(.themeSubheadline.weight(.semibold))
                .disabled(action == nil)
                .frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Theme.Spacing.md)
        .padding(.vertical, Theme.Spacing.sm)
    }
}

/// Search input with clear affordance.
struct SearchBar: View {
    @Binding var text: String
    var placeholder: String = "Search"
    var onSubmit: (() -> Void)?

    var body: some View {
        HStack(spacing: Theme.Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .accessibilityHidden(true)

            TextField(placeholder, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit {
                    onSubmit?()
                }

            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
                .frame(width: 44, height: 44)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, Theme.Spacing.md)
        .frame(minHeight: 44)
        .background(Theme.Adaptive.surface)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.CornerRadius.lg, style: .continuous)
                .stroke(Theme.Adaptive.separator, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg, style: .continuous))
    }
}

/// Generic shimmer block to avoid blank loading states.
struct SkeletonView: View {
    var width: CGFloat?
    var height: CGFloat = 16

    @State private var phase: CGFloat = -1.0

    var body: some View {
        Group {
            if let width {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Theme.Adaptive.separator.opacity(0.35))
                    .frame(width: width, height: height)
            } else {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Theme.Adaptive.separator.opacity(0.35))
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
            }
        }
        .overlay {
            GeometryReader { proxy in
                LinearGradient(
                    colors: [.clear, .white.opacity(0.35), .clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .rotationEffect(.degrees(22))
                .offset(x: proxy.size.width * phase)
            }
            .clipped()
        }
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                phase = 2.0
            }
        }
    }
}

/// Empty-state surface used for no data / no results scenarios.
struct EmptyState: View {
    var icon: String = "tray"
    var title: String
    var message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .accessibilityHidden(true)

            Text(title)
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)
                .multilineTextAlignment(.center)

            Text(message)
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)

            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .padding(.top, Theme.Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding(.horizontal, Theme.Spacing.lg)
    }
}

/// Error surface with optional retry action.
struct ErrorState: View {
    var title: String = "Something went wrong"
    var message: String
    var retryTitle: String = "Retry"
    var retryAction: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title3)
                .foregroundStyle(Theme.Semantic.error)
                .accessibilityHidden(true)

            Text(title)
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)
                .multilineTextAlignment(.center)

            Text(message)
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)

            if let retryAction {
                Button(retryTitle, action: retryAction)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .padding(.top, Theme.Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding(.horizontal, Theme.Spacing.lg)
        .accessibilityLabel("Error state")
    }
}

/// Utility for generating shareable card images from SwiftUI views.
enum ShareCardRenderer {
    @MainActor
    static func render<V: View>(
        scale: CGFloat = UIScreen.main.scale,
        @ViewBuilder content: () -> V
    ) -> UIImage? {
        let renderer = ImageRenderer(content: content())
        renderer.scale = scale
        return renderer.uiImage
    }
}

#Preview("Reusable Card") {
    ContentCard(
        title: "Jemaa el-Fna",
        subtitle: "Landmark · Medina"
    ) {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            PriceTag(minMAD: 20, maxMAD: 60)
            Chip(text: "Open now", style: .outlined)
        }
    }
    .padding()
}

#Preview("Search + Empty") {
    VStack(spacing: Theme.Spacing.md) {
        SearchBar(text: .constant(""))
        EmptyState(
            icon: "magnifyingglass",
            title: "No Results",
            message: "Try a different search term."
        )
    }
    .padding()
}
