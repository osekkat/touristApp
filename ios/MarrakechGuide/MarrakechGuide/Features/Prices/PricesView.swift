import SwiftUI

struct PricesView: View {
    private let cards = QuoteActionCatalog.cards

    var body: some View {
        NavigationStack {
            List(cards) { card in
                NavigationLink(value: card) {
                    PriceCardRow(card: card)
                        .padding(.vertical, Theme.Spacing.xs)
                }
            }
            .listStyle(.plain)
            .navigationTitle("Prices")
            .navigationDestination(for: PriceCard.self) { card in
                PriceCardQuickDetailView(card: card)
            }
            .overlay(alignment: .bottomTrailing) {
                NavigationLink {
                    QuoteActionView()
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Theme.Adaptive.primary)
                        .padding(Theme.Spacing.md)
                        .accessibilityLabel("Check a quote")
                }
            }
        }
    }
}

private struct PriceCardRow: View {
    let card: PriceCard

    var body: some View {
        HStack(spacing: Theme.Spacing.sm) {
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(card.title)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                Text(card.category?.capitalized ?? "Other")
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            Spacer()
            PriceTag(minMAD: card.expectedCostMinMad, maxMAD: card.expectedCostMaxMad)
        }
    }
}

private struct PriceCardQuickDetailView: View {
    let card: PriceCard

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: card.title) {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        HStack {
                            Chip(text: card.category?.capitalized ?? "Other", style: .outlined)
                            Spacer()
                            PriceTag(minMAD: card.expectedCostMinMad, maxMAD: card.expectedCostMaxMad)
                        }

                        if let notes = card.expectedCostNotes {
                            Text(notes)
                                .font(.themeFootnote)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }

                        if let updatedAt = card.expectedCostUpdatedAt {
                            Text("Last reviewed: \(updatedAt)")
                                .font(.themeFootnote)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                    }
                }

                NavigationLink {
                    QuoteActionView(initialCardId: card.id)
                } label: {
                    Label("Check this Quote", systemImage: "scale.3d")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Price Card")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    PricesView()
}
