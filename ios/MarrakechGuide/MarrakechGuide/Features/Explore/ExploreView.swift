import SwiftUI

/// Main Explore screen showing curated places with filters and search.
struct ExploreView: View {
    @StateObject private var viewModel = ExploreViewModel()
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Category filter chips
                CategoryFilterBar(
                    categories: viewModel.categories,
                    selectedCategory: $viewModel.selectedCategory
                )

                // Content based on state
                Group {
                    switch viewModel.state {
                    case .loading:
                        PlaceListSkeleton()
                    case .content(let places):
                        if places.isEmpty {
                            EmptyPlacesView(searchText: searchText)
                        } else {
                            PlaceListView(places: places)
                        }
                    case .error(let message):
                        ErrorStateView(message: message) {
                            Task { await viewModel.loadPlaces() }
                        }
                    }
                }
            }
            .navigationTitle("Explore")
            .searchable(text: $searchText, prompt: "Search places...")
            .onChange(of: searchText) { _, newValue in
                viewModel.search(query: newValue)
            }
            .refreshable {
                await viewModel.loadPlaces()
            }
            .task {
                await viewModel.loadPlaces()
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class ExploreViewModel: ObservableObject {
    enum State {
        case loading
        case content([Place])
        case error(String)
    }

    @Published var state: State = .loading
    @Published var selectedCategory: String? = nil

    let categories = ["All", "Landmarks", "Museums", "Gardens", "Shopping", "Riads"]

    private var allPlaces: [Place] = []
    private var searchQuery = ""
    private var placeRepository: PlaceRepository?

    func loadPlaces() async {
        state = .loading

        do {
            let db = try await DatabaseManager.shared
            placeRepository = PlaceRepositoryImpl(contentDb: db.content)

            let places = try await placeRepository!.getAllPlaces()
            allPlaces = places
            applyFilters()
        } catch {
            state = .error("Failed to load places: \(error.localizedDescription)")
        }
    }

    func search(query: String) {
        searchQuery = query.trimmingCharacters(in: .whitespaces)
        applyFilters()
    }

    private func applyFilters() {
        var filtered = allPlaces

        // Apply category filter
        if let category = selectedCategory, category != "All" {
            filtered = filtered.filter { place in
                place.category?.lowercased() == category.lowercased()
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

// MARK: - Category Filter Bar

struct CategoryFilterBar: View {
    let categories: [String]
    @Binding var selectedCategory: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(categories, id: \.self) { category in
                    CategoryChip(
                        title: category,
                        isSelected: selectedCategory == category || (selectedCategory == nil && category == "All")
                    ) {
                        selectedCategory = category == "All" ? nil : category
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color(.systemBackground))
    }
}

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .semibold : .regular)
                .foregroundColor(isSelected ? .white : .primary)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(isSelected ? Color.accentColor : Color(.systemGray5))
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - Place List

struct PlaceListView: View {
    let places: [Place]

    var body: some View {
        List(places) { place in
            NavigationLink(value: place) {
                PlaceRowView(place: place)
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
        }
        .listStyle(.plain)
        .navigationDestination(for: Place.self) { place in
            PlaceDetailView(place: place)
        }
    }
}

struct PlaceRowView: View {
    let place: Place

    private var accessibilityDescription: String {
        var parts: [String] = [place.name]
        if let category = place.category {
            parts.append(category)
        }
        if let neighborhood = place.neighborhood {
            parts.append("in \(neighborhood)")
        }
        if let minCost = place.feesMinMad, let maxCost = place.feesMaxMad {
            parts.append("\(minCost) to \(maxCost) MAD")
        } else if let minCost = place.expectedCostMinMad, let maxCost = place.expectedCostMaxMad {
            parts.append("\(minCost) to \(maxCost) MAD")
        }
        return parts.joined(separator: ", ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(place.name)
                    .font(.headline)
                    .lineLimit(1)

                Spacer()

                if let category = place.category {
                    Text(category)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray6))
                        .clipShape(Capsule())
                }
            }

            if let description = place.shortDescription {
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }

            HStack(spacing: 12) {
                if let neighborhood = place.neighborhood {
                    Label(neighborhood, systemImage: "mappin")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                if let minCost = place.feesMinMad, let maxCost = place.feesMaxMad {
                    Text("\(minCost)-\(maxCost) MAD")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.accentColor)
                } else if let minCost = place.expectedCostMinMad, let maxCost = place.expectedCostMaxMad {
                    Text("\(minCost)-\(maxCost) MAD")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.accentColor)
                }

                if let visitMin = place.visitMinMinutes, let visitMax = place.visitMaxMinutes {
                    Label("\(visitMin)-\(visitMax) min", systemImage: "clock")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityDescription)
    }
}

// MARK: - Loading Skeleton

struct PlaceListSkeleton: View {
    var body: some View {
        List {
            ForEach(0..<5, id: \.self) { _ in
                PlaceRowSkeleton()
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            }
        }
        .listStyle(.plain)
        .disabled(true)
    }
}

struct PlaceRowSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(.systemGray5))
                    .frame(width: 180, height: 20)
                Spacer()
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(.systemGray6))
                    .frame(width: 60, height: 20)
            }

            RoundedRectangle(cornerRadius: 4)
                .fill(Color(.systemGray5))
                .frame(height: 16)

            RoundedRectangle(cornerRadius: 4)
                .fill(Color(.systemGray5))
                .frame(width: 200, height: 16)

            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(.systemGray6))
                    .frame(width: 80, height: 14)
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(.systemGray6))
                    .frame(width: 60, height: 14)
            }
        }
        .padding(.vertical, 4)
        .redacted(reason: .placeholder)
    }
}

// MARK: - Empty State

struct EmptyPlacesView: View {
    let searchText: String

    var body: some View {
        ContentUnavailableView {
            Label("No Places Found", systemImage: "mappin.slash")
        } description: {
            if searchText.isEmpty {
                Text("No places available in this category.")
            } else {
                Text("No places matching \"\(searchText)\"")
            }
        }
    }
}

// MARK: - Error State

struct ErrorStateView: View {
    let message: String
    let retryAction: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Something Went Wrong", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Retry") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

// MARK: - Previews

#Preview {
    ExploreView()
}
