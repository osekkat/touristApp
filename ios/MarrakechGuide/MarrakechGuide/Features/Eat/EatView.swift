import SwiftUI

/// Eat screen showing curated restaurant and cafe recommendations.
struct EatView: View {
    @StateObject private var viewModel = EatViewModel()
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Filter chips
                EatFilterBar(
                    filters: viewModel.filters,
                    selectedFilter: $viewModel.selectedFilter,
                    onFilterChanged: { viewModel.applyFilters() }
                )

                // Content based on state
                Group {
                    switch viewModel.state {
                    case .loading:
                        RestaurantListSkeleton()
                    case .content(let places):
                        if places.isEmpty {
                            EmptyRestaurantsView(searchText: searchText)
                        } else {
                            RestaurantListView(places: places)
                        }
                    case .error(let message):
                        ErrorState(title: "Unable to load restaurants", message: message) {
                            Task { await viewModel.loadRestaurants() }
                        }
                    }
                }
            }
            .navigationTitle("Eat")
            .searchable(text: $searchText, prompt: "Search restaurants...")
            .onChange(of: searchText) { _, newValue in
                viewModel.search(query: newValue)
            }
            .refreshable {
                await viewModel.loadRestaurants()
            }
            .task {
                await viewModel.loadRestaurants()
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class EatViewModel: ObservableObject {
    enum State {
        case loading
        case content([Place])
        case error(String)
    }

    @Published var state: State = .loading
    @Published var selectedFilter: String? = nil

    let filters = ["All", "Budget", "Rooftop", "Family", "Veg-Friendly", "Local"]

    private var allPlaces: [Place] = []
    private var searchQuery = ""
    private var placeRepository: PlaceRepository?

    func loadRestaurants() async {
        state = .loading

        do {
            let db = try await DatabaseManager.shared
            placeRepository = PlaceRepositoryImpl(contentDb: db.content)

            // Get restaurants and cafes
            let restaurants = try await placeRepository!.getPlaces(category: "restaurant")
            let cafes = try await placeRepository!.getPlaces(category: "cafe")
            allPlaces = restaurants + cafes
            applyFilters()
        } catch {
            state = .error("Failed to load restaurants: \(error.localizedDescription)")
        }
    }

    func search(query: String) {
        searchQuery = query.trimmingCharacters(in: .whitespaces)
        applyFilters()
    }

    func applyFilters() {
        var filtered = allPlaces

        // Apply filter
        if let filter = selectedFilter, filter != "All" {
            let filterLower = filter.lowercased()
            filtered = filtered.filter { place in
                // Check tags for filter match
                if let tags = place.tags {
                    return tags.contains { tag in
                        tag.lowercased().contains(filterLower)
                    }
                }
                return false
            }
        }

        // Apply search filter
        if !searchQuery.isEmpty {
            let query = searchQuery.lowercased()
            filtered = filtered.filter { place in
                place.name.lowercased().contains(query) ||
                (place.shortDescription?.lowercased().contains(query) ?? false) ||
                (place.neighborhood?.lowercased().contains(query) ?? false) ||
                (place.tags?.contains { $0.lowercased().contains(query) } ?? false)
            }
        }

        state = .content(filtered)
    }
}

// MARK: - Filter Bar

private struct EatFilterBar: View {
    let filters: [String]
    @Binding var selectedFilter: String?
    let onFilterChanged: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Theme.Spacing.sm) {
                ForEach(filters, id: \.self) { filter in
                    let isSelected = selectedFilter == filter || (selectedFilter == nil && filter == "All")
                    Chip(
                        text: filter,
                        style: isSelected ? .filled : .outlined,
                        tint: Theme.Adaptive.primary
                    )
                    .onTapGesture {
                        selectedFilter = filter == "All" ? nil : filter
                        onFilterChanged()
                    }
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .background(Theme.Adaptive.backgroundPrimary)
    }
}

// MARK: - Restaurant List

private struct RestaurantListView: View {
    let places: [Place]

    var body: some View {
        List(places) { place in
            NavigationLink(value: place) {
                RestaurantRowView(place: place)
            }
            .listRowInsets(EdgeInsets(top: Theme.Spacing.sm, leading: Theme.Spacing.md, bottom: Theme.Spacing.sm, trailing: Theme.Spacing.md))
        }
        .listStyle(.plain)
        .navigationDestination(for: Place.self) { place in
            PlaceDetailView(place: place)
        }
    }
}

private struct RestaurantRowView: View {
    let place: Place

    private var priceTier: String {
        guard let minCost = place.expectedCostMinMad else { return "" }
        if minCost < 50 {
            return "$"
        } else if minCost < 100 {
            return "$$"
        } else {
            return "$$$"
        }
    }

    private var priceTierColor: Color {
        guard let minCost = place.expectedCostMinMad else { return Theme.Adaptive.textSecondary }
        if minCost < 50 {
            return .green
        } else if minCost < 100 {
            return .orange
        } else {
            return .red
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            HStack {
                Text(place.name)
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.textPrimary)
                    .lineLimit(1)

                Spacer()

                if !priceTier.isEmpty {
                    Text(priceTier)
                        .font(.themeSubheadline.weight(.semibold))
                        .foregroundColor(priceTierColor)
                }
            }

            if let description = place.shortDescription {
                Text(description)
                    .font(.themeSubheadline)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .lineLimit(2)
            }

            HStack(spacing: Theme.Spacing.sm) {
                if let neighborhood = place.neighborhood {
                    Label(neighborhood, systemImage: "mappin")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                if let minCost = place.expectedCostMinMad, let maxCost = place.expectedCostMaxMad {
                    PriceTag(minMAD: minCost, maxMAD: maxCost)
                }
            }

            // Tags
            if let tags = place.tags, !tags.isEmpty {
                HStack(spacing: Theme.Spacing.xs) {
                    ForEach(tags.prefix(3), id: \.self) { tag in
                        Chip(text: tag, style: .outlined, size: .small)
                    }
                }
            }
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

// MARK: - Loading Skeleton

private struct RestaurantListSkeleton: View {
    var body: some View {
        ListItemSkeleton(rows: 6)
    }
}

// MARK: - Empty State

private struct EmptyRestaurantsView: View {
    let searchText: String

    var body: some View {
        EmptyState(
            icon: "fork.knife",
            title: "No Restaurants Found",
            message: searchText.isEmpty
                ? "No restaurants available with this filter."
                : "No restaurants matching \"\(searchText)\""
        )
    }
}

// MARK: - Previews

#Preview {
    EatView()
}
