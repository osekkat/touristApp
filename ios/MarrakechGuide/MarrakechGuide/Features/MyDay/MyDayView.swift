import SwiftUI

/// My Day Planner — Generate a personalized day plan based on user constraints.
struct MyDayView: View {
    @StateObject private var viewModel = MyDayViewModel()
    @State private var showingResult = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Spacing.lg) {
                    // Header
                    MyDayHeader()

                    // Constraint pickers
                    VStack(spacing: Theme.Spacing.md) {
                        // Time Available
                        TimeSlider(
                            hours: $viewModel.hoursAvailable,
                            onPresetTap: { viewModel.hoursAvailable = $0 }
                        )

                        Divider()

                        // Pace
                        PacePicker(selectedPace: $viewModel.selectedPace)

                        Divider()

                        // Budget
                        BudgetPicker(selectedBudget: $viewModel.selectedBudget)

                        Divider()

                        // Interests
                        InterestsPicker(selectedInterests: $viewModel.selectedInterests)
                    }
                    .padding(Theme.Spacing.md)
                    .background(Theme.Adaptive.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                    // Generate button
                    Button {
                        viewModel.generatePlan()
                        showingResult = true
                    } label: {
                        Label("Build My Day", systemImage: "sparkles")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(viewModel.selectedInterests.isEmpty)

                    if viewModel.selectedInterests.isEmpty {
                        Text("Select at least one interest to continue")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }

                    Spacer(minLength: Theme.Spacing.xl)
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("My Day")
            .sheet(isPresented: $showingResult) {
                if let plan = viewModel.generatedPlan {
                    MyDayResultView(plan: plan, places: viewModel.placesById)
                }
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class MyDayViewModel: ObservableObject {
    @Published var hoursAvailable: Double = 6
    @Published var selectedPace: PlanEngine.Pace = .standard
    @Published var selectedBudget: PlanEngine.BudgetTier = .mid
    @Published var selectedInterests: Set<PlanEngine.Interest> = []
    @Published var generatedPlan: PlanEngine.Output?

    // Mock places for now - would come from PlaceRepository
    private let samplePlaces: [PlanEngine.Place] = MyDayCatalog.samplePlaces
    var placesById: [String: PlanEngine.Place] {
        Dictionary(uniqueKeysWithValues: samplePlaces.map { ($0.id, $0) })
    }

    func generatePlan() {
        let input = PlanEngine.Input(
            availableMinutes: Int(hoursAvailable * 60),
            startPoint: nil, // Would come from Home Base or location
            interests: Array(selectedInterests),
            pace: selectedPace,
            budgetTier: selectedBudget,
            currentTime: Date(),
            places: samplePlaces,
            recentPlaceIds: []
        )
        generatedPlan = PlanEngine.generate(input)
    }
}

// MARK: - Components

private struct MyDayHeader: View {
    var body: some View {
        VStack(spacing: Theme.Spacing.sm) {
            Image(systemName: "sun.max.fill")
                .font(.system(size: 48))
                .foregroundStyle(.yellow)

            Text("What should I do today?")
                .font(.themeTitle2)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("Tell us your preferences and we'll build a personalized plan")
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, Theme.Spacing.md)
    }
}

private struct TimeSlider: View {
    @Binding var hours: Double
    let onPresetTap: (Double) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            HStack {
                Text("Time Available")
                    .font(.themeSubheadline.weight(.semibold))
                Spacer()
                Text("\(Int(hours)) hours")
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.primary)
            }

            Slider(value: $hours, in: 2...10, step: 1)
                .tint(Theme.Adaptive.primary)

            // Presets
            HStack(spacing: Theme.Spacing.sm) {
                PresetButton(label: "Half Day", value: 4, currentValue: hours, onTap: onPresetTap)
                PresetButton(label: "Full Day", value: 8, currentValue: hours, onTap: onPresetTap)
            }
        }
    }
}

private struct PresetButton: View {
    let label: String
    let value: Double
    let currentValue: Double
    let onTap: (Double) -> Void

    var isSelected: Bool { abs(currentValue - value) < 0.5 }

    var body: some View {
        Button {
            onTap(value)
        } label: {
            Text(label)
                .font(.themeCaption)
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.xs)
                .background(isSelected ? Theme.Adaptive.primary : Theme.Adaptive.surface)
                .foregroundStyle(isSelected ? .white : Theme.Adaptive.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.full))
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.CornerRadius.full)
                        .stroke(Theme.Adaptive.primary, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

private struct PacePicker: View {
    @Binding var selectedPace: PlanEngine.Pace

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text("Pace")
                .font(.themeSubheadline.weight(.semibold))

            HStack(spacing: Theme.Spacing.sm) {
                ForEach(PlanEngine.Pace.allCases, id: \.self) { pace in
                    PaceOption(
                        pace: pace,
                        isSelected: selectedPace == pace,
                        onTap: { selectedPace = pace }
                    )
                }
            }
        }
    }
}

private struct PaceOption: View {
    let pace: PlanEngine.Pace
    let isSelected: Bool
    let onTap: () -> Void

    var icon: String {
        switch pace {
        case .relaxed: return "tortoise.fill"
        case .standard: return "figure.walk"
        case .active: return "figure.run"
        }
    }

    var label: String {
        switch pace {
        case .relaxed: return "Relaxed"
        case .standard: return "Standard"
        case .active: return "Active"
        }
    }

    var description: String {
        switch pace {
        case .relaxed: return "Fewer stops, more time"
        case .standard: return "Balanced"
        case .active: return "More stops"
        }
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Theme.Spacing.xs) {
                Image(systemName: icon)
                    .font(.title2)
                Text(label)
                    .font(.themeCaption.weight(.semibold))
                Text(description)
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.sm)
            .background(isSelected ? Theme.Adaptive.primary.opacity(0.1) : Theme.Adaptive.backgroundSecondary)
            .foregroundStyle(isSelected ? Theme.Adaptive.primary : Theme.Adaptive.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.CornerRadius.md)
                    .stroke(isSelected ? Theme.Adaptive.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct BudgetPicker: View {
    @Binding var selectedBudget: PlanEngine.BudgetTier

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text("Budget")
                .font(.themeSubheadline.weight(.semibold))

            HStack(spacing: Theme.Spacing.sm) {
                ForEach(PlanEngine.BudgetTier.allCases, id: \.self) { tier in
                    BudgetOption(
                        tier: tier,
                        isSelected: selectedBudget == tier,
                        onTap: { selectedBudget = tier }
                    )
                }
            }
        }
    }
}

private struct BudgetOption: View {
    let tier: PlanEngine.BudgetTier
    let isSelected: Bool
    let onTap: () -> Void

    var label: String {
        switch tier {
        case .budget: return "$"
        case .mid: return "$$"
        case .splurge: return "$$$"
        }
    }

    var description: String {
        switch tier {
        case .budget: return "Budget"
        case .mid: return "Mid-range"
        case .splurge: return "Splurge"
        }
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Theme.Spacing.xs) {
                Text(label)
                    .font(.themeTitle3)
                Text(description)
                    .font(.themeCaption)
            }
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.sm)
            .background(isSelected ? Theme.Adaptive.primary.opacity(0.1) : Theme.Adaptive.backgroundSecondary)
            .foregroundStyle(isSelected ? Theme.Adaptive.primary : Theme.Adaptive.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.CornerRadius.md)
                    .stroke(isSelected ? Theme.Adaptive.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct InterestsPicker: View {
    @Binding var selectedInterests: Set<PlanEngine.Interest>

    let displayInterests: [(PlanEngine.Interest, String, String)] = [
        (.history, "History & Culture", "building.columns"),
        (.shopping, "Shopping & Souks", "bag"),
        (.food, "Food & Cafes", "fork.knife"),
        (.nature, "Gardens & Outdoors", "leaf"),
        (.architecture, "Photography", "camera"),
        (.relaxation, "Hammam & Wellness", "sparkles")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text("Interests")
                .font(.themeSubheadline.weight(.semibold))

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: Theme.Spacing.sm) {
                ForEach(displayInterests, id: \.0) { interest, label, icon in
                    InterestChip(
                        label: label,
                        icon: icon,
                        isSelected: selectedInterests.contains(interest),
                        onTap: {
                            if selectedInterests.contains(interest) {
                                selectedInterests.remove(interest)
                            } else {
                                selectedInterests.insert(interest)
                            }
                        }
                    )
                }
            }
        }
    }
}

private struct InterestChip: View {
    let label: String
    let icon: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Theme.Spacing.xs) {
                Image(systemName: icon)
                Text(label)
                    .font(.themeCaption)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.sm)
            .background(isSelected ? Theme.Adaptive.primary : Theme.Adaptive.backgroundSecondary)
            .foregroundStyle(isSelected ? .white : Theme.Adaptive.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Result View

struct MyDayResultView: View {
    let plan: PlanEngine.Output
    let places: [String: PlanEngine.Place]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Spacing.lg) {
                    // Summary
                    PlanSummaryCard(plan: plan)

                    // Warnings
                    if !plan.warnings.isEmpty {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            ForEach(plan.warnings, id: \.self) { warning in
                                HStack {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(.orange)
                                    Text(warning)
                                        .font(.themeCaption)
                                        .foregroundStyle(Theme.Adaptive.textSecondary)
                                }
                            }
                        }
                        .padding(Theme.Spacing.md)
                        .background(Color.orange.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
                    }

                    // Timeline
                    if plan.stops.isEmpty {
                        EmptyState(
                            icon: "calendar.badge.exclamationmark",
                            title: "No Stops Found",
                            message: "Try adjusting your constraints or interests."
                        )
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(plan.stops.enumerated()), id: \.element.placeId) { index, stop in
                                PlanStopCard(
                                    stop: stop,
                                    place: places[stop.placeId],
                                    index: index + 1,
                                    isLast: index == plan.stops.count - 1
                                )
                            }
                        }
                    }

                    // Start button
                    if !plan.stops.isEmpty {
                        Button {
                            // Would navigate to Route Cards
                        } label: {
                            Label("Start My Day", systemImage: "play.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                    }
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Your Day Plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct PlanSummaryCard: View {
    let plan: PlanEngine.Output

    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            HStack(spacing: Theme.Spacing.lg) {
                SummaryItem(
                    icon: "mappin.circle.fill",
                    value: "\(plan.stops.count)",
                    label: "Stops"
                )
                SummaryItem(
                    icon: "clock.fill",
                    value: formatDuration(plan.totalMinutes),
                    label: "Duration"
                )
                SummaryItem(
                    icon: "banknote.fill",
                    value: "\(plan.estimatedCostRange.minMad)-\(plan.estimatedCostRange.maxMad)",
                    label: "MAD"
                )
            }
        }
        .padding(Theme.Spacing.md)
        .frame(maxWidth: .infinity)
        .background(Theme.Adaptive.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }

    func formatDuration(_ minutes: Int) -> String {
        let hours = minutes / 60
        let mins = minutes % 60
        if hours > 0 && mins > 0 {
            return "\(hours)h \(mins)m"
        } else if hours > 0 {
            return "\(hours)h"
        } else {
            return "\(mins)m"
        }
    }
}

private struct SummaryItem: View {
    let icon: String
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: Theme.Spacing.xs) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.Adaptive.primary)
            Text(value)
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)
            Text(label)
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct PlanStopCard: View {
    let stop: PlanEngine.PlanStop
    let place: PlanEngine.Place?
    let index: Int
    let isLast: Bool

    private let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "h:mm a"
        return f
    }()

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.md) {
            // Timeline
            VStack(spacing: 0) {
                Circle()
                    .fill(Theme.Adaptive.primary)
                    .frame(width: 32, height: 32)
                    .overlay(
                        Text("\(index)")
                            .font(.themeCaption.weight(.bold))
                            .foregroundStyle(.white)
                    )

                if !isLast {
                    Rectangle()
                        .fill(Theme.Adaptive.primary.opacity(0.3))
                        .frame(width: 2)
                        .frame(maxHeight: .infinity)
                }
            }

            // Content
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(place?.name ?? stop.placeId)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                HStack(spacing: Theme.Spacing.md) {
                    Label(timeFormatter.string(from: stop.arrivalTime), systemImage: "clock")
                    Label("\(stop.visitMinutes) min", systemImage: "timer")
                }
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)

                if stop.travelMinutesFromPrevious > 0 {
                    Text("\(stop.travelMinutesFromPrevious) min walk from previous")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }
            .padding(.bottom, isLast ? 0 : Theme.Spacing.lg)

            Spacer()
        }
    }
}

// MARK: - Sample Data

enum MyDayCatalog {
    static let samplePlaces: [PlanEngine.Place] = [
        PlanEngine.Place(
            id: "jemaa-el-fna",
            name: "Jemaa el-Fna",
            category: "landmark",
            interests: ["history", "culture", "food"],
            coordinates: PlanEngine.Coordinate(lat: 31.6259, lng: -7.9891),
            typicalVisitMinutes: 60,
            expectedCostMin: 0,
            expectedCostMax: 0,
            openingHours: nil
        ),
        PlanEngine.Place(
            id: "bahia-palace",
            name: "Bahia Palace",
            category: "landmark",
            interests: ["history", "architecture"],
            coordinates: PlanEngine.Coordinate(lat: 31.6216, lng: -7.9831),
            typicalVisitMinutes: 90,
            expectedCostMin: 70,
            expectedCostMax: 70,
            openingHours: nil
        ),
        PlanEngine.Place(
            id: "jardin-majorelle",
            name: "Jardin Majorelle",
            category: "garden",
            interests: ["nature", "architecture"],
            coordinates: PlanEngine.Coordinate(lat: 31.6417, lng: -8.0033),
            typicalVisitMinutes: 90,
            expectedCostMin: 150,
            expectedCostMax: 150,
            openingHours: nil
        ),
        PlanEngine.Place(
            id: "souk-spices",
            name: "Spice Souk",
            category: "shopping",
            interests: ["shopping", "culture"],
            coordinates: PlanEngine.Coordinate(lat: 31.6285, lng: -7.9858),
            typicalVisitMinutes: 45,
            expectedCostMin: 0,
            expectedCostMax: 200,
            openingHours: nil
        ),
        PlanEngine.Place(
            id: "cafe-clock",
            name: "Café Clock",
            category: "restaurant",
            interests: ["food"],
            coordinates: PlanEngine.Coordinate(lat: 31.6294, lng: -7.9873),
            typicalVisitMinutes: 60,
            expectedCostMin: 80,
            expectedCostMax: 150,
            openingHours: nil
        )
    ]
}

// MARK: - Preview

#Preview {
    MyDayView()
}
