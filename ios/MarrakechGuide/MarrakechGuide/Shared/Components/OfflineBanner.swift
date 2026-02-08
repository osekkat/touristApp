import SwiftUI

/// Shared state model used by feature roots.
enum ScreenState<Value> {
    case loading
    case content(Value)
    case refreshing(Value)
    case offline(Value?)
    case error(ScreenError)
}

struct ScreenError: Sendable {
    let message: String

    init(message: String) {
        self.message = message
    }

    init(error: Error) {
        message = error.localizedDescription
    }
}

struct ErrorAction: Identifiable {
    let id = UUID()
    let label: String
    let action: () -> Void
}

struct LoadingContent<Skeleton: View>: View {
    @ViewBuilder let skeleton: () -> Skeleton

    var body: some View {
        skeleton()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityLabel("Loading")
    }
}

struct OfflineBanner: View {
    var onDismiss: (() -> Void)?
    var message: String = "Offline • Core guide still works"

    var body: some View {
        HStack(alignment: .center, spacing: Theme.Spacing.sm) {
            Image(systemName: "wifi.slash")
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .accessibilityHidden(true)

            Text(message)
                .font(.themeFootnote)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.semibold))
                        .frame(width: 20, height: 20)
                        .padding(10)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("Dismiss offline message")
            }
        }
        .padding(.horizontal, Theme.Spacing.md)
        .padding(.vertical, Theme.Spacing.sm)
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .background(Theme.Adaptive.surface)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.CornerRadius.md, style: .continuous)
                .stroke(Theme.Adaptive.separator, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md, style: .continuous))
    }
}

struct ErrorContent: View {
    var message: String
    var onRetry: (() -> Void)?
    var alternativeActions: [ErrorAction] = []

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Theme.Semantic.error)
                .font(.title3)

            Text("Something went wrong")
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text(message)
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)

            VStack(spacing: Theme.Spacing.sm) {
                if let onRetry {
                    Button("Retry", action: onRetry)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
                ForEach(alternativeActions) { action in
                    Button(action.label, action: action.action)
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                }
            }
            .padding(.top, Theme.Spacing.sm)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding(.horizontal, Theme.Spacing.lg)
        .accessibilityLabel("Error state")
    }
}

struct ScreenStateContent<Value, Content: View>: View {
    let state: ScreenState<Value>
    var onRefresh: (() async -> Void)?
    var onRetry: (() -> Void)?
    var alternativeErrorActions: [ErrorAction]

    private let loadingBuilder: () -> AnyView
    private let contentBuilder: (Value) -> Content

    init<Loading: View>(
        state: ScreenState<Value>,
        onRefresh: (() async -> Void)? = nil,
        onRetry: (() -> Void)? = nil,
        alternativeErrorActions: [ErrorAction] = [],
        @ViewBuilder loading: @escaping () -> Loading = { ListItemSkeleton() },
        @ViewBuilder content: @escaping (Value) -> Content
    ) {
        self.state = state
        self.onRefresh = onRefresh
        self.onRetry = onRetry
        self.alternativeErrorActions = alternativeErrorActions
        self.loadingBuilder = { AnyView(loading()) }
        self.contentBuilder = content
    }

    var body: some View {
        switch state {
        case .loading:
            LoadingContent {
                loadingBuilder()
            }

        case .error(let screenError):
            ErrorContent(
                message: screenError.message,
                onRetry: onRetry,
                alternativeActions: alternativeErrorActions
            )

        case .content(let value):
            RefreshableContent(
                isRefreshing: false,
                onRefresh: onRefresh
            ) {
                contentBuilder(value)
            }

        case .refreshing(let value):
            RefreshableContent(
                isRefreshing: true,
                onRefresh: onRefresh
            ) {
                contentBuilder(value)
            }

        case .offline(let cachedValue):
            VStack(spacing: Theme.Spacing.sm) {
                OfflineBanner()

                if let cachedValue {
                    RefreshableContent(
                        isRefreshing: false,
                        onRefresh: onRefresh
                    ) {
                        contentBuilder(cachedValue)
                    }
                } else {
                    ErrorContent(
                        message: "Offline and no cached data is available yet.",
                        onRetry: onRetry
                    )
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.top, Theme.Spacing.sm)
        }
    }
}

private struct RefreshableContent<Content: View>: View {
    var isRefreshing: Bool
    var onRefresh: (() async -> Void)?
    @ViewBuilder let content: () -> Content

    @ViewBuilder
    var body: some View {
        if let onRefresh {
            scrollContainer
                .refreshable {
                    await onRefresh()
                }
        } else {
            scrollContainer
        }
    }

    private var scrollContainer: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.sm) {
                if isRefreshing {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }

                content()
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)
        }
    }
}

struct ListItemSkeleton: View {
    var rows: Int = 5

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            ForEach(0..<rows, id: \.self) { _ in
                CardSkeleton()
            }
        }
        .padding(.horizontal, Theme.Spacing.lg)
        .padding(.vertical, Theme.Spacing.md)
    }
}

struct CardSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            SkeletonBlock(width: 160, height: 20)
            SkeletonBlock(height: 14)
            SkeletonBlock(width: 240, height: 14)
        }
        .padding(Theme.Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Adaptive.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg, style: .continuous))
    }
}

struct DetailSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.md) {
            SkeletonBlock(height: 200)
            SkeletonBlock(width: 220, height: 28)
            SkeletonBlock(height: 16)
            SkeletonBlock(width: 280, height: 16)
            SkeletonBlock(width: 250, height: 16)
        }
        .padding(.horizontal, Theme.Spacing.lg)
        .padding(.vertical, Theme.Spacing.md)
    }
}

struct PlaceholderStateScreen: View {
    var title: String
    var subtitle: String?

    @State private var state: ScreenState<Void> = .loading

    private var isRefreshing: Bool {
        if case .refreshing = state { return true }
        return false
    }

    var body: some View {
        ScreenStateContent(
            state: state,
            onRefresh: {
                await MainActor.run {
                    switch state {
                    case .content:
                        state = .refreshing(())
                    case .offline(.some):
                        state = .refreshing(())
                    default:
                        break
                    }
                }
            },
            onRetry: {
                state = .content(())
            },
            loading: {
                ListItemSkeleton()
            }
        ) { _ in
            VStack(spacing: Theme.Spacing.sm) {
                Text(title)
                    .font(.themeTitle2)
                    .foregroundStyle(Theme.Adaptive.textPrimary)
                    .multilineTextAlignment(.center)

                if let subtitle {
                    Text(subtitle)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 260, alignment: .center)
        }
        .task {
            guard case .loading = state else { return }
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if case .loading = state {
                    state = .content(())
                }
            }
        }
        .task(id: isRefreshing) {
            guard isRefreshing else { return }
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if case .refreshing = state {
                    state = .content(())
                }
            }
        }
    }
}

private struct SkeletonBlock: View {
    var width: CGFloat?
    var height: CGFloat

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
        .modifier(ShimmerModifier())
    }
}

private struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = -1.0

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    LinearGradient(
                        colors: [
                            .clear,
                            Color.white.opacity(0.35),
                            .clear
                        ],
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

#Preview("Offline Banner") {
    OfflineBanner()
        .padding()
}

#Preview("Error State") {
    ErrorContent(
        message: "Could not refresh places.",
        onRetry: {}
    )
}

#Preview("Placeholder Screen") {
    NavigationStack {
        PlaceholderStateScreen(title: "Explore")
            .navigationTitle("Explore")
    }
}
