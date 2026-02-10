import SwiftUI

/// Arrival Mode — Opinionated guidance for the first hours in Marrakech.
struct ArrivalModeView: View {
    @StateObject private var viewModel = ArrivalModeViewModel()
    @State private var expandedSection: ArrivalSection?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Spacing.lg) {
                    // Welcome header
                    WelcomeHeader(progress: viewModel.completionProgress)

                    // Checklist sections
                    ForEach(ArrivalSection.allCases) { section in
                        ArrivalSectionCard(
                            section: section,
                            isComplete: viewModel.isComplete(section),
                            isExpanded: expandedSection == section,
                            onToggle: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    expandedSection = expandedSection == section ? nil : section
                                }
                            },
                            onMarkComplete: {
                                viewModel.toggleComplete(section)
                            }
                        )
                    }

                    // Completion button
                    if viewModel.allSectionsComplete {
                        CompletionButton(onComplete: {
                            viewModel.markArrivalComplete()
                            dismiss()
                        })
                    }

                    Spacer(minLength: Theme.Spacing.xl)
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Arrival Mode")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                if viewModel.isArrivalComplete {
                    dismiss()
                }
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class ArrivalModeViewModel: ObservableObject {
    @Published var completedSections: Set<ArrivalSection> = []
    @Published var isArrivalComplete = false

    var completionProgress: Double {
        Double(completedSections.count) / Double(ArrivalSection.allCases.count)
    }

    var allSectionsComplete: Bool {
        completedSections.count == ArrivalSection.allCases.count
    }

    init() {
        loadState()
    }

    func isComplete(_ section: ArrivalSection) -> Bool {
        completedSections.contains(section)
    }

    func toggleComplete(_ section: ArrivalSection) {
        if completedSections.contains(section) {
            completedSections.remove(section)
        } else {
            completedSections.insert(section)
        }
        saveState()
    }

    func markArrivalComplete() {
        isArrivalComplete = true
        UserDefaults.standard.set(true, forKey: "arrivalComplete")
        UserDefaults.standard.set(Date().ISO8601Format(), forKey: "arrivalCompletedAt")
    }

    private func loadState() {
        isArrivalComplete = UserDefaults.standard.bool(forKey: "arrivalComplete")
        if let savedSections = UserDefaults.standard.array(forKey: "arrivalSections") as? [String] {
            completedSections = Set(savedSections.compactMap { ArrivalSection(rawValue: $0) })
        }
    }

    private func saveState() {
        UserDefaults.standard.set(completedSections.map { $0.rawValue }, forKey: "arrivalSections")
    }
}

// MARK: - Section Model

enum ArrivalSection: String, CaseIterable, Identifiable {
    case airportTaxi
    case simEsim
    case cashAtm
    case hotelTransfer
    case medinaOrientation

    var id: String { rawValue }

    var title: String {
        switch self {
        case .airportTaxi: return "Airport Taxi"
        case .simEsim: return "SIM / eSIM"
        case .cashAtm: return "Cash / ATM"
        case .hotelTransfer: return "Hotel Transfer"
        case .medinaOrientation: return "Medina Orientation"
        }
    }

    var icon: String {
        switch self {
        case .airportTaxi: return "car.fill"
        case .simEsim: return "simcard.fill"
        case .cashAtm: return "banknote.fill"
        case .hotelTransfer: return "house.fill"
        case .medinaOrientation: return "map.fill"
        }
    }

    var tips: [ArrivalTip] {
        switch self {
        case .airportTaxi:
            return [
                ArrivalTip(text: "Expected fare to Medina: 150-200 MAD (fixed, don't negotiate)", isHighlight: true),
                ArrivalTip(text: "Find the official taxi stand outside arrivals"),
                ArrivalTip(text: "Avoid 'special price' offers and guides at arrival"),
                ArrivalTip(text: "Polite refusal: \"La, shukran\" (No, thank you)")
            ]
        case .simEsim:
            return [
                ArrivalTip(text: "Best: Get eSIM before arrival (Airalo, Holafly)", isHighlight: true),
                ArrivalTip(text: "At airport: Maroc Telecom, Orange, Inwi booths"),
                ArrivalTip(text: "Ask for: 20-50GB data, 7-14 days"),
                ArrivalTip(text: "Typical cost: 100-200 MAD"),
                ArrivalTip(text: "Phrase: \"Bghit internet, bla appels\" (I want internet, no calls)")
            ]
        case .cashAtm:
            return [
                ArrivalTip(text: "Withdraw 1000-2000 MAD to start", isHighlight: true),
                ArrivalTip(text: "ATMs at airport work but have limits"),
                ArrivalTip(text: "If ATM fails: try CIH or Attijariwafa banks"),
                ArrivalTip(text: "Keep small bills (20, 50 MAD) for tips"),
                ArrivalTip(text: "Rough rate: 1 USD ≈ 10 MAD")
            ]
        case .hotelTransfer:
            return [
                ArrivalTip(text: "Confirm pickup if your riad arranged it", isHighlight: true),
                ArrivalTip(text: "If taxi: show driver the address in Arabic"),
                ArrivalTip(text: "Tip for bag help: 20-50 MAD"),
                ArrivalTip(text: "Ask riad to meet you at a landmark if inside Medina")
            ]
        case .medinaOrientation:
            return [
                ArrivalTip(text: "\"Helpful\" strangers usually want money — polite \"La shukran\"", isHighlight: true),
                ArrivalTip(text: "You WILL get lost. It's OK! Ask shopkeepers for landmarks."),
                ArrivalTip(text: "Jemaa el-Fna is the center — ask \"Fin Jemaa el-Fna?\""),
                ArrivalTip(text: "Keep your riad's card with Arabic address")
            ]
        }
    }
}

struct ArrivalTip: Identifiable {
    let id = UUID()
    let text: String
    var isHighlight: Bool = false
}

// MARK: - Components

private struct WelcomeHeader: View {
    let progress: Double

    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "airplane.arrival")
                .font(.system(size: 48))
                .foregroundStyle(Theme.Adaptive.primary)

            Text("Welcome to Marrakech!")
                .font(.themeTitle1)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("Complete these steps to start your trip with confidence")
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)

            // Progress bar
            VStack(spacing: Theme.Spacing.xs) {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(Theme.Adaptive.primary)

                Text("\(Int(progress * 100))% complete")
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
        .padding(.vertical, Theme.Spacing.lg)
    }
}

private struct ArrivalSectionCard: View {
    let section: ArrivalSection
    let isComplete: Bool
    let isExpanded: Bool
    let onToggle: () -> Void
    let onMarkComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Header
            Button(action: onToggle) {
                HStack(spacing: Theme.Spacing.md) {
                    Image(systemName: section.icon)
                        .font(.title2)
                        .foregroundStyle(isComplete ? Theme.Fairness.fair : Theme.Adaptive.primary)
                        .frame(width: 32)

                    Text(section.title)
                        .font(.themeHeadline)
                        .foregroundStyle(Theme.Adaptive.textPrimary)

                    Spacer()

                    if isComplete {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Theme.Fairness.fair)
                    }

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
                .padding(Theme.Spacing.md)
            }
            .buttonStyle(.plain)

            // Expanded content
            if isExpanded {
                VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                    ForEach(section.tips) { tip in
                        HStack(alignment: .top, spacing: Theme.Spacing.sm) {
                            Image(systemName: tip.isHighlight ? "star.fill" : "circle.fill")
                                .font(.system(size: tip.isHighlight ? 12 : 6))
                                .foregroundStyle(tip.isHighlight ? Theme.Adaptive.primary : Theme.Adaptive.textSecondary)
                                .frame(width: 16, alignment: .center)
                                .padding(.top, tip.isHighlight ? 2 : 6)

                            Text(tip.text)
                                .font(tip.isHighlight ? .themeSubheadline.weight(.semibold) : .themeBody)
                                .foregroundStyle(Theme.Adaptive.textPrimary)
                        }
                    }

                    // Mark complete button
                    Button {
                        onMarkComplete()
                    } label: {
                        HStack {
                            Image(systemName: isComplete ? "checkmark.circle.fill" : "circle")
                            Text(isComplete ? "Completed" : "Mark as Done")
                        }
                        .font(.themeSubheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Theme.Spacing.sm)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(isComplete ? Theme.Fairness.fair : Theme.Adaptive.primary)
                    .padding(.top, Theme.Spacing.sm)
                }
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.bottom, Theme.Spacing.md)
            }
        }
        .background(Theme.Adaptive.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

private struct CompletionButton: View {
    let onComplete: () -> Void

    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 48))
                .foregroundStyle(Theme.Fairness.fair)

            Text("You're Ready!")
                .font(.themeTitle2)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("All arrival steps complete. Enjoy Marrakech!")
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)

            Button {
                onComplete()
            } label: {
                Label("I've Arrived at My Riad", systemImage: "house.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(Theme.Spacing.lg)
        .background(Theme.Fairness.fair.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

// MARK: - Preview

#Preview {
    ArrivalModeView()
}
