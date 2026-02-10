import SwiftUI
import CoreLocation

/// Route Cards — Step-by-step itinerary execution with compass navigation.
struct RouteCardsView: View {
    @StateObject private var viewModel: RouteCardsViewModel
    @Environment(\.dismiss) private var dismiss

    init(places: [RouteEngine.RoutePlace], routeId: String, routeTitle: String) {
        _viewModel = StateObject(wrappedValue: RouteCardsViewModel(
            places: places,
            routeId: routeId,
            routeTitle: routeTitle
        ))
    }

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.viewMode {
                case .overview:
                    RouteOverviewContent(viewModel: viewModel)
                case .navigation:
                    NextStopContent(viewModel: viewModel)
                case .medinaMode:
                    MedinaModeContent(viewModel: viewModel)
                }
            }
            .navigationTitle(viewModel.routeTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Exit") {
                        viewModel.showExitConfirmation = true
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        viewModel.toggleViewMode()
                    } label: {
                        Image(systemName: viewModel.viewMode == .overview ? "location.fill" : "list.bullet")
                    }
                }
            }
            .alert("Exit Route?", isPresented: $viewModel.showExitConfirmation) {
                Button("Continue Route", role: .cancel) {}
                Button("Exit", role: .destructive) {
                    viewModel.exitRoute()
                    dismiss()
                }
            } message: {
                Text("Your progress will be saved but you'll need to restart to continue.")
            }
            .sheet(isPresented: $viewModel.showCompletionCelebration) {
                RouteCompletionView(
                    stopsCompleted: viewModel.progress?.stepsCompleted.count ?? 0,
                    totalStops: viewModel.places.count,
                    onDismiss: { dismiss() }
                )
            }
        }
    }
}

// MARK: - ViewModel

enum RouteViewMode {
    case overview
    case navigation
    case medinaMode
}

@MainActor
final class RouteCardsViewModel: ObservableObject {
    let places: [RouteEngine.RoutePlace]
    let routeId: String
    let routeTitle: String

    @Published var progress: RouteEngine.RouteProgress?
    @Published var currentLeg: RouteEngine.RouteLeg?
    @Published var viewMode: RouteViewMode = .overview
    @Published var showExitConfirmation = false
    @Published var showCompletionCelebration = false
    @Published var deviceHeading: Double = 0
    @Published var currentLocation: CLLocationCoordinate2D?

    var isRouteComplete: Bool {
        guard let progress = progress else { return false }
        return RouteEngine.isRouteComplete(progress)
    }

    var completionPercentage: Double {
        guard let progress = progress else { return 0 }
        return RouteEngine.getOverallProgress(progress)
    }

    init(places: [RouteEngine.RoutePlace], routeId: String, routeTitle: String) {
        self.places = places
        self.routeId = routeId
        self.routeTitle = routeTitle

        if let progress = RouteEngine.startRoute(
            routeId: routeId,
            routeType: .itinerary,
            places: places
        ) {
            self.progress = progress
            updateCurrentLeg()
        }
    }

    func toggleViewMode() {
        switch viewMode {
        case .overview:
            viewMode = .navigation
        case .navigation:
            viewMode = .overview
        case .medinaMode:
            viewMode = .navigation
        }
    }

    func switchToMedinaMode() {
        viewMode = .medinaMode
    }

    func completeCurrentStep() {
        guard var progress = progress else { return }
        RouteEngine.completeCurrentStep(&progress)
        self.progress = progress
        updateCurrentLeg()
        checkCompletion()
    }

    func skipCurrentStep() {
        guard var progress = progress else { return }
        RouteEngine.skipCurrentStep(&progress)
        self.progress = progress
        updateCurrentLeg()
        checkCompletion()
    }

    func exitRoute() {
        guard var progress = progress else { return }
        RouteEngine.exitRoute(&progress)
        self.progress = progress
    }

    private func updateCurrentLeg() {
        guard let progress = progress else { return }
        currentLeg = RouteEngine.getCurrentLeg(
            progress: progress,
            places: places,
            currentLocation: currentLocation
        )
    }

    private func checkCompletion() {
        if isRouteComplete {
            showCompletionCelebration = true
        }
    }

    func getStepStatus(_ index: Int) -> StepStatus {
        guard let progress = progress else { return .upcoming }
        if progress.stepsCompleted.contains(index) { return .completed }
        if progress.stepsSkipped.contains(index) { return .skipped }
        if index == progress.currentStepIndex { return .current }
        return .upcoming
    }
}

enum StepStatus {
    case completed, skipped, current, upcoming
}

// MARK: - Overview Content

private struct RouteOverviewContent: View {
    @ObservedObject var viewModel: RouteCardsViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.lg) {
                // Progress header
                ProgressHeader(
                    completed: viewModel.progress?.stepsCompleted.count ?? 0,
                    total: viewModel.places.count,
                    percentage: viewModel.completionPercentage
                )

                // Steps list
                VStack(spacing: 0) {
                    ForEach(Array(viewModel.places.enumerated()), id: \.element.id) { index, place in
                        StepRow(
                            index: index + 1,
                            place: place,
                            status: viewModel.getStepStatus(index),
                            isLast: index == viewModel.places.count - 1
                        )
                    }
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                // Continue button
                if !viewModel.isRouteComplete {
                    Button {
                        viewModel.viewMode = .navigation
                    } label: {
                        Label("Continue Route", systemImage: "arrow.right.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
            }
            .padding(Theme.Spacing.md)
        }
    }
}

private struct ProgressHeader: View {
    let completed: Int
    let total: Int
    let percentage: Double

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Text("\(completed) of \(total) stops")
                .font(.themeTitle2)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            ProgressView(value: percentage)
                .progressViewStyle(.linear)
                .tint(Theme.Adaptive.primary)

            Text("\(Int(percentage * 100))% complete")
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
        .padding(Theme.Spacing.md)
        .background(Theme.Adaptive.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

private struct StepRow: View {
    let index: Int
    let place: RouteEngine.RoutePlace
    let status: StepStatus
    let isLast: Bool

    var statusColor: Color {
        switch status {
        case .completed: return Theme.Fairness.fair
        case .skipped: return Theme.Adaptive.textSecondary
        case .current: return Theme.Adaptive.primary
        case .upcoming: return Theme.Adaptive.textSecondary.opacity(0.5)
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.md) {
            // Timeline
            VStack(spacing: 0) {
                Circle()
                    .fill(statusColor)
                    .frame(width: 28, height: 28)
                    .overlay(stepIcon)

                if !isLast {
                    Rectangle()
                        .fill(statusColor.opacity(0.3))
                        .frame(width: 2)
                        .frame(height: 40)
                }
            }

            // Content
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(place.name)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(status == .upcoming ? Theme.Adaptive.textSecondary : Theme.Adaptive.textPrimary)

                if let hint = place.routeHint {
                    Text(hint)
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                if status == .current {
                    Text("Current stop")
                        .font(.themeCaption.weight(.semibold))
                        .foregroundStyle(Theme.Adaptive.primary)
                }
            }
            .padding(.bottom, isLast ? 0 : Theme.Spacing.sm)

            Spacer()
        }
    }

    @ViewBuilder
    var stepIcon: some View {
        switch status {
        case .completed:
            Image(systemName: "checkmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
        case .skipped:
            Image(systemName: "arrow.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
        case .current:
            Image(systemName: "location.fill")
                .font(.caption)
                .foregroundStyle(.white)
        case .upcoming:
            Text("\(index)")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
        }
    }
}

// MARK: - Navigation Content

private struct NextStopContent: View {
    @ObservedObject var viewModel: RouteCardsViewModel

    var body: some View {
        VStack(spacing: Theme.Spacing.lg) {
            if let leg = viewModel.currentLeg {
                // Current stop (if not first)
                if let fromPlace = leg.fromPlace {
                    CurrentStopCard(place: fromPlace)
                }

                // Next stop destination
                DestinationCard(leg: leg)

                // Navigation panel
                NavigationPanel(
                    leg: leg,
                    deviceHeading: viewModel.deviceHeading
                )

                Spacer()

                // Action buttons
                ActionButtons(
                    onComplete: { viewModel.completeCurrentStep() },
                    onSkip: { viewModel.skipCurrentStep() },
                    onMedinaMode: { viewModel.switchToMedinaMode() }
                )
            } else {
                EmptyState(
                    icon: "checkmark.circle.fill",
                    title: "Route Complete",
                    message: "You've finished all stops!"
                )
            }
        }
        .padding(Theme.Spacing.md)
    }
}

private struct CurrentStopCard: View {
    let place: RouteEngine.RoutePlace

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text("Current Location")
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                Text(place.name)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)
            }
            Spacer()
            Image(systemName: "checkmark.circle")
                .foregroundStyle(Theme.Fairness.fair)
        }
        .padding(Theme.Spacing.md)
        .background(Theme.Adaptive.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
    }
}

private struct DestinationCard: View {
    let leg: RouteEngine.RouteLeg

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Text("Next Stop")
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            Text(leg.toPlace.name)
                .font(.themeTitle1)
                .foregroundStyle(Theme.Adaptive.textPrimary)
                .multilineTextAlignment(.center)

            if let hint = leg.routeHint {
                Text(hint)
                    .font(.themeBody)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .multilineTextAlignment(.center)
            }

            if leg.isLastStep {
                Text("Final Stop")
                    .font(.themeCaption.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.primary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Theme.Spacing.lg)
        .background(Theme.Adaptive.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

private struct NavigationPanel: View {
    let leg: RouteEngine.RouteLeg
    let deviceHeading: Double

    var relativeAngle: Double {
        GeoEngine.relativeAngle(targetBearing: leg.bearingDegrees, deviceHeading: deviceHeading)
    }

    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            // Compass arrow
            Image(systemName: "arrow.up")
                .font(.system(size: 64, weight: .bold))
                .foregroundStyle(Theme.Adaptive.primary)
                .rotationEffect(.degrees(relativeAngle))
                .animation(.easeInOut(duration: 0.3), value: relativeAngle)

            // Distance and time
            HStack(spacing: Theme.Spacing.xl) {
                VStack {
                    Text(RouteEngine.formatDistance(leg.distanceMeters))
                        .font(.themeTitle2)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                    Text("Distance")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                VStack {
                    Text(RouteEngine.formatWalkTime(leg.estimatedWalkMinutes))
                        .font(.themeTitle2)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                    Text("Walk time")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Theme.Spacing.lg)
        .background(Theme.Adaptive.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

private struct ActionButtons: View {
    let onComplete: () -> Void
    let onSkip: () -> Void
    let onMedinaMode: () -> Void

    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Button {
                onComplete()
            } label: {
                Label("I've Arrived", systemImage: "checkmark.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            HStack(spacing: Theme.Spacing.sm) {
                Button {
                    onSkip()
                } label: {
                    Label("Skip", systemImage: "arrow.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    onMedinaMode()
                } label: {
                    Label("Need Help", systemImage: "questionmark.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
    }
}

// MARK: - Medina Mode Content

private struct MedinaModeContent: View {
    @ObservedObject var viewModel: RouteCardsViewModel

    var body: some View {
        VStack(spacing: Theme.Spacing.lg) {
            if let leg = viewModel.currentLeg {
                Text("Medina Mode")
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)

                Text("Getting to \(leg.toPlace.name)")
                    .font(.themeTitle2)
                    .foregroundStyle(Theme.Adaptive.textPrimary)
                    .multilineTextAlignment(.center)

                // Large text direction phrase
                VStack(spacing: Theme.Spacing.md) {
                    Text("Ask: \"Fin \(leg.toPlace.name)?\"")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("(Where is \(leg.toPlace.name)?)")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
                .padding(Theme.Spacing.lg)
                .frame(maxWidth: .infinity)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                // Distance info
                HStack(spacing: Theme.Spacing.lg) {
                    VStack {
                        Text(RouteEngine.formatDistance(leg.distanceMeters))
                            .font(.themeHeadline)
                        Text("Away")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }

                    VStack {
                        Text(RouteEngine.formatWalkTime(leg.estimatedWalkMinutes))
                            .font(.themeHeadline)
                        Text("Walk")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }

                if let hint = leg.routeHint {
                    Text(hint)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(Theme.Spacing.md)
                        .background(Theme.Adaptive.backgroundSecondary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
                }

                Spacer()

                Button {
                    viewModel.completeCurrentStep()
                } label: {
                    Label("I've Arrived", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                Button {
                    viewModel.viewMode = .navigation
                } label: {
                    Text("Back to Compass")
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(Theme.Spacing.md)
    }
}

// MARK: - Completion View

private struct RouteCompletionView: View {
    let stopsCompleted: Int
    let totalStops: Int
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: Theme.Spacing.xl) {
            Spacer()

            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 80))
                .foregroundStyle(Theme.Fairness.fair)

            Text("Route Complete!")
                .font(.themeTitle1)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("You completed \(stopsCompleted) of \(totalStops) stops")
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            Spacer()

            Button {
                onDismiss()
            } label: {
                Text("Done")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(Theme.Spacing.lg)
    }
}

// MARK: - Preview

#Preview {
    RouteCardsView(
        places: [
            RouteEngine.RoutePlace(
                id: "1",
                name: "Jemaa el-Fna",
                coordinate: CLLocationCoordinate2D(latitude: 31.6259, longitude: -7.9891),
                routeHint: "Start at the main square"
            ),
            RouteEngine.RoutePlace(
                id: "2",
                name: "Bahia Palace",
                coordinate: CLLocationCoordinate2D(latitude: 31.6216, longitude: -7.9831),
                routeHint: "Turn left at the minaret"
            ),
            RouteEngine.RoutePlace(
                id: "3",
                name: "Jardin Majorelle",
                coordinate: CLLocationCoordinate2D(latitude: 31.6417, longitude: -8.0033)
            )
        ],
        routeId: "preview-route",
        routeTitle: "Classic Medina Walk"
    )
}
