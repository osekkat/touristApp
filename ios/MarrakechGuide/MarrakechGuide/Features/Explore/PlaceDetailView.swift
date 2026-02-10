import SwiftUI

/// Detail view for a single place showing full information.
struct PlaceDetailView: View {
    let place: Place
    @State private var isFavorite = false
    @State private var relatedPriceCards: [PriceCard] = []
    @State private var isLoading = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Hero section
                PlaceHeroSection(place: place, isFavorite: $isFavorite)

                // Quick facts
                PlaceQuickFacts(place: place)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                // Description
                if let description = place.longDescription ?? place.shortDescription {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("About")
                            .font(.headline)
                        Text(description)
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                }

                // Local tips
                if let tips = place.localTips, !tips.isEmpty {
                    PlaceTipsSection(title: "Local Tips", tips: tips, iconName: "lightbulb.fill", color: .orange)
                        .padding(.top, 20)
                }

                // Scam warnings
                if let warnings = place.scamWarnings, !warnings.isEmpty {
                    PlaceTipsSection(title: "Watch Out For", tips: warnings, iconName: "exclamationmark.triangle.fill", color: .red)
                        .padding(.top, 20)
                }

                // Do's and Don'ts
                if let doAndDont = place.doAndDont, !doAndDont.isEmpty {
                    PlaceTipsSection(title: "Do's & Don'ts", tips: doAndDont, iconName: "hand.raised.fill", color: .blue)
                        .padding(.top, 20)
                }

                // Related price cards
                if !relatedPriceCards.isEmpty {
                    RelatedPriceCardsSection(priceCards: relatedPriceCards)
                        .padding(.top, 20)
                }

                // Why we recommend (if available)
                if let reasons = place.whyRecommended, !reasons.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Why We Recommend")
                            .font(.headline)
                        ForEach(reasons, id: \.self) { reason in
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                    .font(.caption)
                                Text(reason)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                }

                Spacer(minLength: 32)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    isFavorite.toggle()
                    // TODO: Save to favorites repository
                } label: {
                    Image(systemName: isFavorite ? "heart.fill" : "heart")
                        .foregroundColor(isFavorite ? .red : .primary)
                }
            }

            ToolbarItem(placement: .navigationBarTrailing) {
                ShareLink(item: place.name) {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
        .task {
            await loadRelatedContent()
        }
    }

    private func loadRelatedContent() async {
        do {
            let db = try await DatabaseManager.shared
            let repository = PlaceRepositoryImpl(contentDb: db.content)
            relatedPriceCards = try await repository.getRelatedPriceCards(placeId: place.id)
            isLoading = false
        } catch {
            isLoading = false
        }
    }
}

// MARK: - Hero Section

struct PlaceHeroSection: View {
    let place: Place
    @Binding var isFavorite: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Category and tourist trap indicator
            HStack {
                if let category = place.category {
                    Text(category.uppercased())
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.accentColor)
                }

                Spacer()

                if let trapLevel = place.touristTrapLevel {
                    TouristTrapBadge(level: trapLevel)
                }
            }

            // Name
            Text(place.name)
                .font(.title)
                .fontWeight(.bold)

            // Neighborhood and address
            if let neighborhood = place.neighborhood {
                HStack(spacing: 4) {
                    Image(systemName: "mappin")
                        .font(.caption)
                    Text(neighborhood)
                        .font(.subheadline)
                    if let address = place.address {
                        Text("• \(address)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                .foregroundColor(.secondary)
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
    }
}

// MARK: - Quick Facts

struct PlaceQuickFacts: View {
    let place: Place

    var body: some View {
        LazyVGrid(columns: [
            GridItem(.flexible()),
            GridItem(.flexible())
        ], spacing: 12) {
            // Hours
            if let hours = place.hoursText {
                QuickFactCard(icon: "clock", title: "Hours", value: hours)
            }

            // Fees
            if let minFee = place.feesMinMad, let maxFee = place.feesMaxMad {
                QuickFactCard(icon: "creditcard", title: "Entry Fee", value: "\(minFee)-\(maxFee) MAD")
            } else if let minCost = place.expectedCostMinMad, let maxCost = place.expectedCostMaxMad {
                QuickFactCard(icon: "banknote", title: "Expected Cost", value: "\(minCost)-\(maxCost) MAD")
            }

            // Visit duration
            if let minTime = place.visitMinMinutes, let maxTime = place.visitMaxMinutes {
                QuickFactCard(icon: "timer", title: "Visit Time", value: "\(minTime)-\(maxTime) min")
            }

            // Best time
            if let bestTime = place.bestTimeToGo {
                QuickFactCard(icon: "sun.max", title: "Best Time", value: bestTime)
            }
        }
    }
}

struct QuickFactCard: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.accentColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(value)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(12)
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

// MARK: - Tourist Trap Badge

struct TouristTrapBadge: View {
    let level: String

    var color: Color {
        switch level.lowercased() {
        case "low": return .green
        case "medium": return .orange
        case "high": return .red
        default: return .gray
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.caption2)
            Text("Tourist trap: \(level)")
                .font(.caption)
                .fontWeight(.medium)
        }
        .foregroundColor(color)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.15))
        .cornerRadius(8)
    }
}

// MARK: - Tips Section

struct PlaceTipsSection: View {
    let title: String
    let tips: [String]
    let iconName: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: iconName)
                    .foregroundColor(color)
                Text(title)
                    .font(.headline)
            }
            .padding(.horizontal, 16)

            VStack(spacing: 8) {
                ForEach(tips, id: \.self) { tip in
                    HStack(alignment: .top, spacing: 12) {
                        Circle()
                            .fill(color.opacity(0.3))
                            .frame(width: 6, height: 6)
                            .padding(.top, 6)
                        Text(tip)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Related Price Cards

struct RelatedPriceCardsSection: View {
    let priceCards: [PriceCard]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Related Prices")
                .font(.headline)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(priceCards) { card in
                        PriceCardMini(priceCard: card)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

struct PriceCardMini: View {
    let priceCard: PriceCard

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(priceCard.title)
                .font(.subheadline)
                .fontWeight(.medium)
                .lineLimit(2)

            Text("\(priceCard.expectedCostMinMad)-\(priceCard.expectedCostMaxMad) MAD")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.accentColor)

            if let unit = priceCard.unit {
                Text("per \(unit)")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(12)
        .frame(width: 140)
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        PlaceDetailView(place: Place(
            id: "jemaa-el-fna",
            name: "Jemaa el-Fna",
            aliases: ["Djemaa el Fna", "The Square"],
            regionId: "medina",
            category: "Landmarks",
            shortDescription: "The beating heart of Marrakech",
            longDescription: "The main square and marketplace in Marrakech's medina quarter. A UNESCO World Heritage site.",
            reviewedAt: nil,
            status: "active",
            confidence: "high",
            touristTrapLevel: "high",
            whyRecommended: ["Iconic experience", "Great for people watching"],
            neighborhood: "Medina",
            address: "Jemaa el-Fna, Marrakech",
            lat: 31.625831,
            lng: -7.98892,
            hoursText: "24/7",
            hoursWeekly: nil,
            hoursVerifiedAt: nil,
            feesMinMad: 0,
            feesMaxMad: 0,
            expectedCostMinMad: 50,
            expectedCostMaxMad: 200,
            visitMinMinutes: 60,
            visitMaxMinutes: 180,
            bestTimeToGo: "Evening",
            bestTimeWindows: nil,
            tags: ["square", "market", "food"],
            localTips: ["Watch out for henna artists", "Negotiate prices firmly"],
            scamWarnings: ["Photographers will demand money", "Snake charmers overcharge"],
            doAndDont: ["Do try the fresh orange juice", "Don't take photos without asking"],
            images: nil,
            sourceRefs: nil
        ))
    }
}
