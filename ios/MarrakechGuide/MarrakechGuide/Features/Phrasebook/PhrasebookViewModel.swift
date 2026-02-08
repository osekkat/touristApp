import Foundation
import Combine

/// Categories for organizing Darija phrases.
enum PhraseCategory: String, CaseIterable, Identifiable {
    case greetings = "greetings"
    case shopping = "shopping"
    case directions = "directions"
    case food = "food"
    case communication = "communication"
    case emergency = "emergency"
    case politeRefusal = "polite_refusal"
    case numbers = "numbers"
    case courtesy = "courtesy"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .greetings: return "Greetings & Basics"
        case .shopping: return "Bargaining & Shopping"
        case .directions: return "Taxi & Directions"
        case .food: return "Food & Restaurants"
        case .communication: return "Communication"
        case .emergency: return "Emergencies"
        case .politeRefusal: return "Polite Refusals"
        case .numbers: return "Numbers"
        case .courtesy: return "Courtesy"
        }
    }

    var icon: String {
        switch self {
        case .greetings: return "hand.wave"
        case .shopping: return "cart"
        case .directions: return "car"
        case .food: return "fork.knife"
        case .communication: return "bubble.left.and.bubble.right"
        case .emergency: return "exclamationmark.triangle"
        case .politeRefusal: return "hand.raised"
        case .numbers: return "number"
        case .courtesy: return "heart"
        }
    }
}

/// ViewModel for the Phrasebook feature.
@MainActor
final class PhrasebookViewModel: ObservableObject {

    // MARK: - Published State

    @Published var allPhrases: [Phrase] = []
    @Published var phrasesByCategory: [PhraseCategory: [Phrase]] = [:]
    @Published var searchQuery = ""
    @Published var searchResults: [Phrase] = []
    @Published var recentPhrases: [Phrase] = []
    @Published var selectedCategory: PhraseCategory? = nil
    @Published var isLoading = true
    @Published var errorMessage: String?

    // MARK: - Dependencies

    private let phraseRepository: PhraseRepository

    // MARK: - Private State

    private var cancellables = Set<AnyCancellable>()
    private var recentPhraseIds: [String] = [] {
        didSet {
            UserDefaults.standard.set(recentPhraseIds, forKey: "recentPhraseIds")
        }
    }

    // MARK: - Init

    init(phraseRepository: PhraseRepository) {
        self.phraseRepository = phraseRepository

        // Load recent phrase IDs from UserDefaults
        recentPhraseIds = UserDefaults.standard.stringArray(forKey: "recentPhraseIds") ?? []

        // Set up search debouncing
        $searchQuery
            .debounce(for: .milliseconds(300), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] query in
                Task { await self?.performSearch(query: query) }
            }
            .store(in: &cancellables)
    }

    // MARK: - Public Methods

    func load() async {
        isLoading = true
        errorMessage = nil

        do {
            allPhrases = try await phraseRepository.getAllPhrases()

            // Group by category
            var grouped: [PhraseCategory: [Phrase]] = [:]
            for phrase in allPhrases {
                if let categoryStr = phrase.category,
                   let category = PhraseCategory(rawValue: categoryStr) {
                    var phrases = grouped[category] ?? []
                    phrases.append(phrase)
                    grouped[category] = phrases
                }
            }
            phrasesByCategory = grouped

            // Load recent phrases
            updateRecentPhrases()

        } catch {
            errorMessage = "Failed to load phrases: \(error.localizedDescription)"
        }

        isLoading = false
    }

    func recordView(phrase: Phrase) {
        // Remove if already in recents
        recentPhraseIds.removeAll { $0 == phrase.id }

        // Add to front
        recentPhraseIds.insert(phrase.id, at: 0)

        // Keep only last 10
        if recentPhraseIds.count > 10 {
            recentPhraseIds = Array(recentPhraseIds.prefix(10))
        }

        updateRecentPhrases()
    }

    func getCategoryPhraseCount(_ category: PhraseCategory) -> Int {
        phrasesByCategory[category]?.count ?? 0
    }

    func getPhrases(for category: PhraseCategory) -> [Phrase] {
        phrasesByCategory[category] ?? []
    }

    func clearError() {
        errorMessage = nil
    }

    // MARK: - Private Methods

    private func performSearch(query: String) async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmed.isEmpty {
            searchResults = []
            return
        }

        do {
            searchResults = try await phraseRepository.searchPhrases(query: trimmed, limit: 30)
        } catch {
            // Silent fail for search - just show empty results
            searchResults = []
        }
    }

    private func updateRecentPhrases() {
        recentPhrases = recentPhraseIds.compactMap { id in
            allPhrases.first { $0.id == id }
        }
    }
}
