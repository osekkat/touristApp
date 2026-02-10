import SwiftUI

/// Global search screen that searches across all content types.
struct SearchView: View {
    @StateObject private var viewModel = SearchViewModel()
    @FocusState private var isSearchFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Search input
                HStack(spacing: Theme.Spacing.sm) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(Theme.Adaptive.textSecondary)

                    TextField("Search places, prices, phrases...", text: $viewModel.searchQuery)
                        .textFieldStyle(.plain)
                        .focused($isSearchFocused)
                        .submitLabel(.search)
                        .onSubmit {
                            viewModel.performSearch()
                        }

                    if !viewModel.searchQuery.isEmpty {
                        Button {
                            viewModel.searchQuery = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                    }
                }
                .padding(Theme.Spacing.sm)
                .background(Theme.Adaptive.backgroundSecondary)
                .cornerRadius(12)
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm)

                // Content
                ScrollView {
                    VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                        if viewModel.searchQuery.isEmpty {
                            // Recent searches
                            if !viewModel.recentSearches.isEmpty {
                                RecentSearchesSection(
                                    searches: viewModel.recentSearches,
                                    onSelect: { query in
                                        viewModel.searchQuery = query
                                        viewModel.performSearch()
                                    },
                                    onClear: {
                                        viewModel.clearRecentSearches()
                                    }
                                )
                            } else {
                                SearchHintsView()
                            }
                        } else if viewModel.isSearching {
                            ProgressView("Searching...")
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.top, Theme.Spacing.xl)
                        } else if viewModel.hasNoResults {
                            NoResultsView(query: viewModel.searchQuery)
                        } else {
                            SearchResultsView(
                                results: viewModel.searchResults,
                                onPlaceSelected: { viewModel.selectedPlace = $0 },
                                onPriceCardSelected: { viewModel.selectedPriceCard = $0 },
                                onPhraseSelected: { viewModel.selectedPhrase = $0 }
                            )
                        }
                    }
                    .padding(.horizontal, Theme.Spacing.md)
                    .padding(.vertical, Theme.Spacing.sm)
                }
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: viewModel.searchQuery) { _, newValue in
                if newValue.count >= 2 {
                    viewModel.performSearch()
                }
            }
            .navigationDestination(item: $viewModel.selectedPlace) { place in
                PlaceDetailView(place: place)
            }
            .sheet(item: $viewModel.selectedPhrase) { phrase in
                PhraseDetailSheet(phrase: phrase)
            }
            .sheet(item: $viewModel.selectedPriceCard) { card in
                PriceCardDetailSheet(card: card)
            }
        }
        .onAppear {
            isSearchFocused = true
        }
    }
}

// MARK: - ViewModel

@MainActor
final class SearchViewModel: ObservableObject {
    @Published var searchQuery = ""
    @Published var isSearching = false
    @Published var searchResults = SearchResults()
    @Published var recentSearches: [String] = []
    @Published var selectedPlace: Place?
    @Published var selectedPriceCard: PriceCard?
    @Published var selectedPhrase: Phrase?

    var hasNoResults: Bool {
        !isSearching && !searchQuery.isEmpty &&
        searchResults.places.isEmpty &&
        searchResults.priceCards.isEmpty &&
        searchResults.phrases.isEmpty
    }

    init() {
        loadRecentSearches()
    }

    func performSearch() {
        guard searchQuery.count >= 2 else { return }

        isSearching = true
        let query = searchQuery.lowercased()

        // Search mock data (in real app, would use FTS through repositories)
        Task {
            // Simulate search delay
            try? await Task.sleep(nanoseconds: 100_000_000)

            var results = SearchResults()

            // Search places
            results.places = MockSearchData.places.filter { place in
                place.name.lowercased().contains(query) ||
                (place.shortDescription?.lowercased().contains(query) ?? false) ||
                (place.neighborhood?.lowercased().contains(query) ?? false) ||
                (place.tags?.contains { $0.lowercased().contains(query) } ?? false)
            }

            // Search price cards
            results.priceCards = QuoteActionCatalog.cards.filter { card in
                card.title.lowercased().contains(query) ||
                (card.category?.lowercased().contains(query) ?? false)
            }

            // Search phrases
            results.phrases = PhrasebookCatalog.phrases.filter { phrase in
                (phrase.latin?.lowercased().contains(query) ?? false) ||
                (phrase.english?.lowercased().contains(query) ?? false) ||
                (phrase.arabic?.contains(query) ?? false)
            }

            searchResults = results
            isSearching = false

            // Save to recent
            saveRecentSearch(query: searchQuery)
        }
    }

    func loadRecentSearches() {
        recentSearches = UserDefaults.standard.stringArray(forKey: "recentSearches") ?? []
    }

    func saveRecentSearch(query: String) {
        var recents = recentSearches
        recents.removeAll { $0.lowercased() == query.lowercased() }
        recents.insert(query, at: 0)
        if recents.count > 10 {
            recents = Array(recents.prefix(10))
        }
        recentSearches = recents
        UserDefaults.standard.set(recents, forKey: "recentSearches")
    }

    func clearRecentSearches() {
        recentSearches = []
        UserDefaults.standard.removeObject(forKey: "recentSearches")
    }
}

struct SearchResults {
    var places: [Place] = []
    var priceCards: [PriceCard] = []
    var phrases: [Phrase] = []
}

// MARK: - Recent Searches

private struct RecentSearchesSection: View {
    let searches: [String]
    let onSelect: (String) -> Void
    let onClear: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            HStack {
                Text("Recent Searches")
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                Spacer()

                Button("Clear") {
                    onClear()
                }
                .font(.themeSubheadline)
            }

            ForEach(searches, id: \.self) { search in
                Button {
                    onSelect(search)
                } label: {
                    HStack {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                        Text(search)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                        Spacer()
                    }
                    .padding(.vertical, Theme.Spacing.xs)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Search Hints

private struct SearchHintsView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.md) {
            Text("Search for...")
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                SearchHintRow(icon: "mappin", text: "Places: \"Jemaa\", \"museum\", \"garden\"")
                SearchHintRow(icon: "creditcard", text: "Prices: \"taxi\", \"hammam\", \"guide\"")
                SearchHintRow(icon: "text.bubble", text: "Phrases: \"shukran\", \"how much\"")
            }
        }
        .padding(.top, Theme.Spacing.md)
    }
}

private struct SearchHintRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: Theme.Spacing.sm) {
            Image(systemName: icon)
                .foregroundStyle(Theme.Adaptive.primary)
                .frame(width: 24)
            Text(text)
                .font(.themeSubheadline)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
    }
}

// MARK: - Search Results

private struct SearchResultsView: View {
    let results: SearchResults
    let onPlaceSelected: (Place) -> Void
    let onPriceCardSelected: (PriceCard) -> Void
    let onPhraseSelected: (Phrase) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
            // Places
            if !results.places.isEmpty {
                SearchResultSection(title: "Places", icon: "mappin.circle.fill", count: results.places.count) {
                    ForEach(results.places) { place in
                        PlaceSearchResultRow(place: place)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onPlaceSelected(place)
                            }
                    }
                }
            }

            // Price Cards
            if !results.priceCards.isEmpty {
                SearchResultSection(title: "Prices", icon: "creditcard.fill", count: results.priceCards.count) {
                    ForEach(results.priceCards) { card in
                        PriceCardSearchResultRow(card: card)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onPriceCardSelected(card)
                            }
                    }
                }
            }

            // Phrases
            if !results.phrases.isEmpty {
                SearchResultSection(title: "Phrases", icon: "text.bubble.fill", count: results.phrases.count) {
                    ForEach(results.phrases) { phrase in
                        PhraseSearchResultRow(phrase: phrase)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onPhraseSelected(phrase)
                            }
                    }
                }
            }
        }
    }
}

private struct SearchResultSection<Content: View>: View {
    let title: String
    let icon: String
    let count: Int
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(Theme.Adaptive.primary)
                Text(title)
                    .font(.themeHeadline)
                Text("(\(count))")
                    .font(.themeSubheadline)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            content()
        }
    }
}

private struct PlaceSearchResultRow: View {
    let place: Place

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            Text(place.name)
                .font(.themeSubheadline.weight(.semibold))
                .foregroundStyle(Theme.Adaptive.textPrimary)

            if let description = place.shortDescription {
                Text(description)
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .lineLimit(1)
            }

            HStack(spacing: Theme.Spacing.sm) {
                if let category = place.category {
                    Chip(text: category, style: .outlined, size: .small)
                }
                if let neighborhood = place.neighborhood {
                    Text(neighborhood)
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

private struct PriceCardSearchResultRow: View {
    let card: PriceCard

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(card.title)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                if let category = card.category {
                    Chip(text: category.capitalized, style: .outlined, size: .small)
                }
            }

            Spacer()

            PriceTag(minMAD: card.expectedCostMinMad, maxMAD: card.expectedCostMaxMad)
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

private struct PhraseSearchResultRow: View {
    let phrase: Phrase

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            if let latin = phrase.latin {
                Text(latin)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)
            }

            if let english = phrase.english {
                Text(english)
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            if let category = phrase.category {
                Chip(text: category.capitalized, style: .outlined, size: .small)
            }
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

// MARK: - No Results

private struct NoResultsView: View {
    let query: String

    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 48))
                .foregroundStyle(Theme.Adaptive.textSecondary)

            Text("No results for \"\(query)\"")
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("Try checking your spelling or using simpler terms.")
                .font(.themeSubheadline)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Theme.Spacing.xl)
    }
}

// MARK: - Detail Sheets

private struct PhraseDetailSheet: View {
    let phrase: Phrase
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                    if let latin = phrase.latin {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            Text("Darija (Latin)")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                            Text(latin)
                                .font(.system(size: 28, weight: .semibold))
                        }
                    }

                    if let arabic = phrase.arabic {
                        VStack(alignment: .trailing, spacing: Theme.Spacing.xs) {
                            Text("Arabic")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .trailing)
                            Text(arabic)
                                .font(.system(size: 32))
                                .frame(maxWidth: .infinity, alignment: .trailing)
                        }
                    }

                    if let english = phrase.english {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            Text("English")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                            Text(english)
                                .font(.themeTitle2)
                        }
                    }
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Phrase")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

private struct PriceCardDetailSheet: View {
    let card: PriceCard
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    ContentCard(title: card.title) {
                        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                            HStack {
                                if let category = card.category {
                                    Chip(text: category.capitalized, style: .outlined)
                                }
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
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Price Card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Mock Data

enum MockSearchData {
    static let places: [Place] = [
        Place(
            id: "jemaa-el-fna",
            name: "Jemaa el-Fna",
            aliases: ["Djemaa el Fna", "The Square"],
            regionId: "medina",
            category: "Landmarks",
            shortDescription: "The beating heart of Marrakech",
            longDescription: nil,
            reviewedAt: nil,
            status: "active",
            confidence: "high",
            touristTrapLevel: "high",
            whyRecommended: nil,
            neighborhood: "Medina",
            address: nil,
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
            localTips: nil,
            scamWarnings: nil,
            doAndDont: nil,
            images: nil,
            sourceRefs: nil
        ),
        Place(
            id: "bahia-palace",
            name: "Bahia Palace",
            aliases: nil,
            regionId: "medina",
            category: "Museums",
            shortDescription: "A stunning 19th-century palace",
            longDescription: nil,
            reviewedAt: nil,
            status: "active",
            confidence: "high",
            touristTrapLevel: nil,
            whyRecommended: nil,
            neighborhood: "Mellah",
            address: nil,
            lat: 31.6216,
            lng: -7.9825,
            hoursText: "9am-5pm",
            hoursWeekly: nil,
            hoursVerifiedAt: nil,
            feesMinMad: 70,
            feesMaxMad: 70,
            expectedCostMinMad: nil,
            expectedCostMaxMad: nil,
            visitMinMinutes: 60,
            visitMaxMinutes: 90,
            bestTimeToGo: "Morning",
            bestTimeWindows: nil,
            tags: ["palace", "history", "architecture"],
            localTips: nil,
            scamWarnings: nil,
            doAndDont: nil,
            images: nil,
            sourceRefs: nil
        )
    ]
}

// MARK: - Preview

#Preview {
    SearchView()
}
