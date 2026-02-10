import SwiftUI

/// First-hours guidance flow for users who just landed in Marrakech.
struct ArrivalModeView: View {
    let onOpenTaxiPriceCard: () -> Void
    let onSetHomeBase: () -> Void
    let onOpenMyDay: () -> Void

    @State private var completedSectionIDs: Set<String> = []
    @State private var arrivalConfirmed = false

    init(
        onOpenTaxiPriceCard: @escaping () -> Void = {},
        onSetHomeBase: @escaping () -> Void = {},
        onOpenMyDay: @escaping () -> Void = {}
    ) {
        self.onOpenTaxiPriceCard = onOpenTaxiPriceCard
        self.onSetHomeBase = onSetHomeBase
        self.onOpenMyDay = onOpenMyDay
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(
                    title: "Welcome to Marrakech",
                    subtitle: "A calm checklist for your first hours after landing."
                ) {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        progressHeader
                        ProgressView(value: progress, total: 1)
                            .tint(Theme.Fairness.fair)
                            .accessibilityLabel("Arrival checklist progress")
                    }
                }

                ForEach(ArrivalSection.defaultSections) { section in
                    sectionCard(section)
                }

                Button {
                    arrivalConfirmed = true
                } label: {
                    Label("I've Arrived at My Riad", systemImage: "checkmark.seal.fill")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                if arrivalConfirmed {
                    completionCard
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Arrival Mode")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var progressHeader: some View {
        HStack {
            Text("Progress")
                .font(.themeSubheadline.weight(.semibold))
                .foregroundStyle(Theme.Adaptive.textPrimary)
            Spacer()
            Text("\(completedSectionIDs.count)/\(ArrivalSection.defaultSections.count) complete")
                .font(.themeFootnote)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
    }

    private var progress: Double {
        guard !ArrivalSection.defaultSections.isEmpty else { return 0 }
        return Double(completedSectionIDs.count) / Double(ArrivalSection.defaultSections.count)
    }

    @ViewBuilder
    private func sectionCard(_ section: ArrivalSection) -> some View {
        let isComplete = completedSectionIDs.contains(section.id)

        ContentCard(title: section.title, subtitle: section.summary) {
            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                ForEach(section.checklist, id: \.self) { item in
                    Label(item, systemImage: isComplete ? "checkmark.circle.fill" : "circle")
                        .font(.themeBody)
                        .foregroundStyle(isComplete ? Theme.Fairness.fair : Theme.Adaptive.textPrimary)
                }

                HStack(spacing: Theme.Spacing.sm) {
                    Button {
                        toggleSection(section.id)
                    } label: {
                        Label(
                            isComplete ? "Completed" : "Mark Section Done",
                            systemImage: isComplete ? "checkmark.circle.fill" : "circle"
                        )
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(isComplete ? Theme.Fairness.fair : Theme.Adaptive.primary)

                    if let action = section.action {
                        Button {
                            handleAction(action)
                        } label: {
                            Text(action.label)
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
        }
    }

    private var completionCard: some View {
        ContentCard(
            title: "You're Set",
            subtitle: "Arrival basics are done. Next, plan your first day with confidence."
        ) {
            Button {
                onOpenMyDay()
            } label: {
                Label("Plan My Day", systemImage: "calendar.badge.plus")
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func toggleSection(_ id: String) {
        if completedSectionIDs.contains(id) {
            completedSectionIDs.remove(id)
        } else {
            completedSectionIDs.insert(id)
        }
    }

    private func handleAction(_ action: ArrivalAction) {
        switch action {
        case .taxiPriceCard:
            onOpenTaxiPriceCard()
        case .setHomeBase:
            onSetHomeBase()
        }
    }
}

private struct ArrivalSection: Identifiable, Sendable {
    let id: String
    let title: String
    let summary: String
    let checklist: [String]
    let action: ArrivalAction?

    static let defaultSections: [ArrivalSection] = [
        ArrivalSection(
            id: "airport_taxi",
            title: "Airport Taxi",
            summary: "Use official pickup flow and lock the total fare before moving.",
            checklist: [
                "Use the official taxi line outside arrivals.",
                "Confirm destination and all-in fare before loading bags.",
                "Decline unclear offers politely and move to the next taxi."
            ],
            action: .taxiPriceCard
        ),
        ArrivalSection(
            id: "sim_data",
            title: "SIM / eSIM",
            summary: "Get reliable data quickly so maps and messaging are stable.",
            checklist: [
                "Prefer pre-installed eSIM before flight when possible.",
                "If buying at airport, compare data amount and validity.",
                "Test mobile data before leaving terminal area."
            ],
            action: nil
        ),
        ArrivalSection(
            id: "cash_atm",
            title: "Cash / ATM",
            summary: "Keep early transport and small purchases friction-free.",
            checklist: [
                "Withdraw a starter MAD buffer at a reliable ATM.",
                "Break large notes early into smaller bills.",
                "Keep emergency transport cash separate."
            ],
            action: nil
        ),
        ArrivalSection(
            id: "hotel_transfer",
            title: "Hotel / Riad Transfer",
            summary: "Confirm your final destination and save it immediately.",
            checklist: [
                "Verify exact riad/hotel pin before departing airport.",
                "Save Home Base before first medina walk.",
                "Keep accommodation contact handy for driver clarification."
            ],
            action: .setHomeBase
        ),
        ArrivalSection(
            id: "medina_orientation",
            title: "Medina Orientation",
            summary: "Use landmark-based navigation for your first walk.",
            checklist: [
                "Anchor on Jemaa El Fna and Koutoubia as recovery points.",
                "Use a calm 'La shukran' for unsolicited guidance.",
                "Pause in open squares if direction feels uncertain."
            ],
            action: nil
        )
    ]
}

private enum ArrivalAction: Sendable {
    case taxiPriceCard
    case setHomeBase

    var label: String {
        switch self {
        case .taxiPriceCard:
            return "Taxi Price Card"
        case .setHomeBase:
            return "Set Home Base"
        }
    }
}

#Preview {
    NavigationStack {
        ArrivalModeView()
    }
}
