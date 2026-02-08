import SwiftUI

/// Navigation hub for secondary features: Phrasebook, Itineraries, Tips, Culture, and Settings.
struct MoreView: View {
    @State private var phraseRepository: PhraseRepository?
    @State private var isLoadingRepository = true

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        PhrasebookDestinationView()
                    } label: {
                        MoreListItem(
                            icon: "bubble.left.and.text.bubble.right",
                            title: "Darija Phrasebook",
                            subtitle: "Essential phrases for getting around"
                        )
                    }

                    NavigationLink {
                        ItinerariesListView()
                    } label: {
                        MoreListItem(
                            icon: "map",
                            title: "Itineraries",
                            subtitle: "Pre-built day plans and routes"
                        )
                    }
                }

                Section {
                    NavigationLink {
                        TipsView()
                    } label: {
                        MoreListItem(
                            icon: "lightbulb",
                            title: "Tips & Safety",
                            subtitle: "Practical advice and scam awareness"
                        )
                    }

                    NavigationLink {
                        CultureView()
                    } label: {
                        MoreListItem(
                            icon: "book.closed",
                            title: "Culture & Etiquette",
                            subtitle: "Do's and don'ts for respectful visits"
                        )
                    }
                }

                Section {
                    NavigationLink {
                        FavoritesView()
                    } label: {
                        MoreListItem(
                            icon: "heart",
                            title: "Saved Items",
                            subtitle: "Your bookmarked places and cards"
                        )
                    }
                }

                Section {
                    NavigationLink {
                        SettingsView()
                    } label: {
                        MoreListItem(
                            icon: "gearshape",
                            title: "Settings",
                            subtitle: "App preferences and data"
                        )
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("More")
        }
    }
}

/// Wrapper view that handles async loading of PhraseRepository for PhrasebookView.
private struct PhrasebookDestinationView: View {
    @State private var phraseRepository: PhraseRepository?
    @State private var loadError: Error?

    var body: some View {
        Group {
            if let repository = phraseRepository {
                PhrasebookView(phraseRepository: repository)
            } else if loadError != nil {
                ErrorState(
                    icon: "exclamationmark.triangle",
                    title: "Unable to Load",
                    message: "Could not load the phrasebook. Please try again."
                ) {
                    loadError = nil
                    Task { await loadRepository() }
                }
            } else {
                ProgressView("Loading...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            await loadRepository()
        }
    }

    private func loadRepository() async {
        do {
            phraseRepository = try await Container.shared.phraseRepository
        } catch {
            loadError = error
        }
    }
}

private struct MoreListItem: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: Theme.Spacing.md) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.Adaptive.primary)
                .frame(width: 32, alignment: .center)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(title)
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                Text(subtitle)
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

// MARK: - Itineraries

struct ItinerariesListView: View {
    var body: some View {
        List {
            ForEach(Itinerary.samples) { itinerary in
                NavigationLink {
                    ItineraryDetailView(itinerary: itinerary)
                } label: {
                    ItineraryRow(itinerary: itinerary)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Itineraries")
    }
}

private struct ItineraryRow: View {
    let itinerary: Itinerary

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            HStack {
                Text(itinerary.title)
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.textPrimary)
                Spacer()
                Text(itinerary.durationLabel)
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            Text(itinerary.overview)
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .lineLimit(2)

            HStack(spacing: Theme.Spacing.sm) {
                Chip(text: "\(itinerary.stops.count) stops", style: .outlined, tint: Theme.Adaptive.primary)
                if itinerary.isFamilyFriendly {
                    Chip(text: "Family", style: .outlined, tint: Theme.Adaptive.textSecondary)
                }
            }
        }
        .padding(.vertical, Theme.Spacing.sm)
    }
}

struct ItineraryDetailView: View {
    let itinerary: Itinerary
    @State private var isSaved = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: itinerary.title) {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        HStack {
                            Label(itinerary.durationLabel, systemImage: "clock")
                            Spacer()
                            Label("\(itinerary.stops.count) stops", systemImage: "mappin.and.ellipse")
                        }
                        .font(.themeFootnote)
                        .foregroundStyle(Theme.Adaptive.textSecondary)

                        Text(itinerary.overview)
                            .font(.themeBody)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }
                }

                SectionHeader(title: "Stops")

                ForEach(Array(itinerary.stops.enumerated()), id: \.element.id) { index, stop in
                    ItineraryStopCard(stop: stop, index: index + 1)
                }

                Button {
                    // TODO: Navigate to Route Cards with this itinerary
                } label: {
                    Label("Start Route", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding(.top, Theme.Spacing.sm)
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Itinerary")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isSaved.toggle()
                } label: {
                    Image(systemName: isSaved ? "heart.fill" : "heart")
                        .foregroundStyle(isSaved ? Theme.Semantic.error : Theme.Adaptive.textSecondary)
                }
            }
        }
    }
}

private struct ItineraryStopCard: View {
    let stop: ItineraryStop
    let index: Int

    var body: some View {
        ContentCard {
            HStack(alignment: .top, spacing: Theme.Spacing.md) {
                Text("\(index)")
                    .font(.themeHeadline)
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(Theme.Adaptive.primary)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                    Text(stop.title)
                        .font(.themeSubheadline.weight(.semibold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)

                    Text(stop.duration)
                        .font(.themeFootnote)
                        .foregroundStyle(Theme.Adaptive.textSecondary)

                    Text(stop.description)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                }
            }
        }
    }
}

// MARK: - Tips

struct TipsView: View {
    @State private var selectedCategory: TipCategory?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Theme.Spacing.sm) {
                    TipCategoryChip(
                        title: "All",
                        isSelected: selectedCategory == nil,
                        onTap: { selectedCategory = nil }
                    )
                    ForEach(TipCategory.allCases) { category in
                        TipCategoryChip(
                            title: category.title,
                            isSelected: selectedCategory == category,
                            onTap: { selectedCategory = category }
                        )
                    }
                }
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm)
            }

            List {
                ForEach(filteredTips) { tip in
                    NavigationLink {
                        TipDetailView(tip: tip)
                    } label: {
                        TipRow(tip: tip)
                    }
                }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Tips & Safety")
    }

    private var filteredTips: [Tip] {
        if let category = selectedCategory {
            return Tip.samples.filter { $0.category == category }
        }
        return Tip.samples
    }
}

private struct TipCategoryChip: View {
    let title: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(title)
                .font(.themeFootnote.weight(.semibold))
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm)
                .foregroundStyle(isSelected ? .white : Theme.Adaptive.textPrimary)
                .background(isSelected ? Theme.Adaptive.primary : Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.full, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct TipRow: View {
    let tip: Tip

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            HStack {
                Chip(text: tip.category.title, style: .outlined, tint: tip.category.color)
                Spacer()
            }

            Text(tip.title)
                .font(.themeSubheadline.weight(.semibold))
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text(tip.preview)
                .font(.themeFootnote)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .lineLimit(2)
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

struct TipDetailView: View {
    let tip: Tip

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: tip.title) {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        Chip(text: tip.category.title, style: .outlined, tint: tip.category.color)

                        Text(tip.content)
                            .font(.themeBody)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }
                }

                if !tip.relatedPlaceIds.isEmpty {
                    SectionHeader(title: "Related Places")
                    Text("View related places on the Explore tab.")
                        .font(.themeFootnote)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .padding(.horizontal, Theme.Spacing.md)
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Tip")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Culture

struct CultureView: View {
    var body: some View {
        List {
            ForEach(CultureArticle.samples) { article in
                NavigationLink {
                    CultureDetailView(article: article)
                } label: {
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text(article.title)
                            .font(.themeSubheadline.weight(.semibold))
                            .foregroundStyle(Theme.Adaptive.textPrimary)

                        Text(article.preview)
                            .font(.themeFootnote)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                            .lineLimit(2)
                    }
                    .padding(.vertical, Theme.Spacing.xs)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Culture & Etiquette")
    }
}

struct CultureDetailView: View {
    let article: CultureArticle

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: article.title) {
                    Text(article.content)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                }

                if !article.dos.isEmpty {
                    SectionHeader(title: "Do's")
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        ForEach(article.dos, id: \.self) { item in
                            Label(item, systemImage: "checkmark.circle.fill")
                                .font(.themeBody)
                                .foregroundStyle(Theme.Fairness.fair)
                        }
                    }
                    .padding(.horizontal, Theme.Spacing.md)
                }

                if !article.donts.isEmpty {
                    SectionHeader(title: "Don'ts")
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        ForEach(article.donts, id: \.self) { item in
                            Label(item, systemImage: "xmark.circle.fill")
                                .font(.themeBody)
                                .foregroundStyle(Theme.Fairness.veryHigh)
                        }
                    }
                    .padding(.horizontal, Theme.Spacing.md)
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Culture")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Favorites

struct FavoritesView: View {
    @State private var favorites: [FavoriteItem] = FavoriteItem.samples

    var body: some View {
        Group {
            if favorites.isEmpty {
                EmptyState(
                    icon: "heart",
                    title: "No Saved Items",
                    message: "Tap the heart icon on any place or price card to save it here."
                )
            } else {
                List {
                    ForEach(FavoriteType.allCases) { type in
                        let items = favorites.filter { $0.type == type }
                        if !items.isEmpty {
                            Section(type.title) {
                                ForEach(items) { item in
                                    NavigationLink {
                                        Text("Navigate to \(item.title)")
                                    } label: {
                                        HStack {
                                            Image(systemName: type.icon)
                                                .foregroundStyle(Theme.Adaptive.primary)
                                            Text(item.title)
                                                .font(.themeBody)
                                        }
                                    }
                                }
                                .onDelete { indexSet in
                                    let typeItems = favorites.filter { $0.type == type }
                                    for index in indexSet {
                                        if let globalIndex = favorites.firstIndex(where: { $0.id == typeItems[index].id }) {
                                            favorites.remove(at: globalIndex)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("Saved Items")
    }
}

// MARK: - Settings

struct SettingsView: View {
    @AppStorage("preferredCurrency") private var preferredCurrency = "MAD"
    @AppStorage("offlineMode") private var offlineMode = false

    var body: some View {
        List {
            Section("Display") {
                Picker("Currency", selection: $preferredCurrency) {
                    Text("MAD (Moroccan Dirham)").tag("MAD")
                    Text("USD (US Dollar)").tag("USD")
                    Text("EUR (Euro)").tag("EUR")
                    Text("GBP (British Pound)").tag("GBP")
                }

                Toggle("Offline Mode", isOn: $offlineMode)
            }

            Section("Data") {
                Button("Clear Recent History") {
                    // TODO: Clear recents
                }
                .foregroundStyle(Theme.Adaptive.textPrimary)

                Button("Clear All Saved Items") {
                    // TODO: Clear favorites
                }
                .foregroundStyle(Theme.Semantic.error)
            }

            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text("1.0.0")
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                NavigationLink("Licenses") {
                    LicensesView()
                }

                NavigationLink("Privacy Policy") {
                    PrivacyPolicyView()
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Settings")
    }
}

struct LicensesView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: "Open Source Licenses") {
                    Text("This app uses GRDB.swift for SQLite database access, licensed under the MIT License.")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Licenses")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ContentCard(title: "Privacy Policy") {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        Text("Your privacy is important to us.")
                            .font(.themeHeadline)

                        Text("This app stores data locally on your device. No personal information is transmitted to external servers. Location data is used only when you explicitly enable location features and is never stored or shared.")
                            .font(.themeBody)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Sample Data Models

struct Itinerary: Identifiable {
    let id = UUID()
    let title: String
    let durationLabel: String
    let overview: String
    let stops: [ItineraryStop]
    let isFamilyFriendly: Bool

    static let samples: [Itinerary] = [
        Itinerary(
            title: "Classic Medina Walk",
            durationLabel: "4-5 hours",
            overview: "Experience the heart of Marrakech through its most iconic landmarks and hidden gems.",
            stops: [
                ItineraryStop(title: "Jemaa el-Fna", duration: "30 min", description: "Start at the famous square, absorbing the morning energy."),
                ItineraryStop(title: "Koutoubia Mosque", duration: "20 min", description: "Admire the iconic minaret from the gardens."),
                ItineraryStop(title: "Ben Youssef Madrasa", duration: "45 min", description: "Explore the stunning Islamic architecture."),
                ItineraryStop(title: "Bahia Palace", duration: "1 hour", description: "Wander through the opulent palace gardens.")
            ],
            isFamilyFriendly: true
        ),
        Itinerary(
            title: "Foodie's Delight",
            durationLabel: "3-4 hours",
            overview: "A culinary journey through the best street food and local eateries.",
            stops: [
                ItineraryStop(title: "Morning at Café Clock", duration: "45 min", description: "Start with Moroccan breakfast and camel burger."),
                ItineraryStop(title: "Souk Spice Tour", duration: "30 min", description: "Discover the aromatic spice stalls."),
                ItineraryStop(title: "Street Food Row", duration: "1 hour", description: "Sample msemen, harira, and fresh orange juice.")
            ],
            isFamilyFriendly: true
        )
    ]
}

struct ItineraryStop: Identifiable {
    let id = UUID()
    let title: String
    let duration: String
    let description: String
}

enum TipCategory: String, CaseIterable, Identifiable {
    case scams
    case safety
    case practical
    case arrival

    var id: String { rawValue }

    var title: String {
        switch self {
        case .scams: return "Scams"
        case .safety: return "Safety"
        case .practical: return "Practical"
        case .arrival: return "Arrival"
        }
    }

    var color: Color {
        switch self {
        case .scams: return Theme.Fairness.veryHigh
        case .safety: return Theme.Fairness.high
        case .practical: return Theme.Adaptive.primary
        case .arrival: return Theme.Fairness.fair
        }
    }
}

struct Tip: Identifiable {
    let id = UUID()
    let title: String
    let preview: String
    let content: String
    let category: TipCategory
    let relatedPlaceIds: [String]

    static let samples: [Tip] = [
        Tip(
            title: "Taxi Price Agreement",
            preview: "Always agree on the price before getting in a taxi.",
            content: "Petit taxis in Marrakech often don't use meters. Before entering, ask \"Bsh-hal?\" (how much?) and agree on a price. If it seems too high, walk away - there are many taxis available.",
            category: .practical,
            relatedPlaceIds: []
        ),
        Tip(
            title: "The Mint Tea Invitation Scam",
            preview: "Be cautious of overly friendly strangers offering tea in the souks.",
            content: "A common scam involves someone befriending you, taking you to their 'family shop' for tea, and then pressuring you to buy overpriced goods. It's okay to politely decline.",
            category: .scams,
            relatedPlaceIds: []
        ),
        Tip(
            title: "Stay Hydrated",
            preview: "Carry water, especially in summer months.",
            content: "Marrakech can be extremely hot, especially May through September. Always carry bottled water. The tap water is generally safe but bottled is recommended for visitors.",
            category: .safety,
            relatedPlaceIds: []
        ),
        Tip(
            title: "Airport to Medina",
            preview: "Know your options for getting from the airport.",
            content: "Official airport taxis have fixed prices displayed at the taxi stand (around 120-220 MAD to the Medina). Avoid touts inside the terminal - go directly to the official taxi line outside.",
            category: .arrival,
            relatedPlaceIds: []
        )
    ]
}

struct CultureArticle: Identifiable {
    let id = UUID()
    let title: String
    let preview: String
    let content: String
    let dos: [String]
    let donts: [String]

    static let samples: [CultureArticle] = [
        CultureArticle(
            title: "Greetings and Respect",
            preview: "Understanding Moroccan greeting customs.",
            content: "Moroccans are warm and welcoming. Greetings are important - take time to say hello properly before getting to business.",
            dos: [
                "Say 'Salam' (peace) when entering a shop",
                "Accept mint tea when offered",
                "Remove shoes when entering homes"
            ],
            donts: [
                "Rush through greetings",
                "Point with your left hand",
                "Photograph people without permission"
            ]
        ),
        CultureArticle(
            title: "Dress Code",
            preview: "What to wear in Marrakech.",
            content: "While Marrakech is cosmopolitan, modest dress is appreciated, especially when visiting mosques and traditional neighborhoods.",
            dos: [
                "Cover shoulders and knees in religious areas",
                "Carry a light scarf for covering if needed",
                "Wear comfortable walking shoes for the Medina"
            ],
            donts: [
                "Wear very revealing clothing in the Medina",
                "Enter mosques as a non-Muslim (most are closed to visitors)"
            ]
        )
    ]
}

enum FavoriteType: String, CaseIterable, Identifiable {
    case place
    case priceCard

    var id: String { rawValue }

    var title: String {
        switch self {
        case .place: return "Places"
        case .priceCard: return "Price Cards"
        }
    }

    var icon: String {
        switch self {
        case .place: return "mappin"
        case .priceCard: return "tag"
        }
    }
}

struct FavoriteItem: Identifiable {
    let id = UUID()
    let title: String
    let type: FavoriteType
    let referenceId: String

    static let samples: [FavoriteItem] = [
        FavoriteItem(title: "Jemaa el-Fna", type: .place, referenceId: "jemaa-el-fna"),
        FavoriteItem(title: "Bahia Palace", type: .place, referenceId: "bahia-palace"),
        FavoriteItem(title: "Airport Taxi", type: .priceCard, referenceId: "price-taxi-airport")
    ]
}

struct ListItemSkeleton: View {
    var rows: Int = 5

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.md) {
            ForEach(0..<rows, id: \.self) { _ in
                VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                    SkeletonView(width: 120, height: 14)
                    SkeletonView(height: 20)
                    SkeletonView(width: 200, height: 14)
                }
                .padding(.horizontal, Theme.Spacing.md)
            }
        }
        .padding(.vertical, Theme.Spacing.md)
    }
}

#Preview {
    MoreView()
}
