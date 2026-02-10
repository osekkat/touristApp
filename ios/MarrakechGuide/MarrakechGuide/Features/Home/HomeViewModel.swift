import Foundation

/// ViewModel for Home Screen.
/// Loads and provides data for quick access features and daily rotating content.
@MainActor
final class HomeViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var offlineStatus: OfflineStatus = .ready
    @Published var activeRoute: ActiveRoute? = nil
    @Published var tipOfTheDay: Tip? = nil
    @Published var phraseOfTheDay: Phrase? = nil
    @Published var savedItems: [SavedItem] = []
    @Published var recentItems: [RecentItem] = []
    @Published var isLoading: Bool = false
    @Published var error: Error? = nil

    // MARK: - Private Properties

    private let tipRepository: TipRepository
    private let phraseRepository: PhraseRepository
    private let favoritesRepository: FavoritesRepository
    private let recentsRepository: RecentsRepository

    // MARK: - Initialization

    init(
        tipRepository: TipRepository = TipRepositoryImpl.shared,
        phraseRepository: PhraseRepository = PhraseRepositoryImpl.shared,
        favoritesRepository: FavoritesRepository = FavoritesRepositoryImpl.shared,
        recentsRepository: RecentsRepository = RecentsRepositoryImpl.shared
    ) {
        self.tipRepository = tipRepository
        self.phraseRepository = phraseRepository
        self.favoritesRepository = favoritesRepository
        self.recentsRepository = recentsRepository
    }

    // MARK: - Public Methods

    /// Loads all home screen data.
    func loadData() async {
        isLoading = true
        error = nil

        do {
            async let tipTask = loadTipOfTheDay()
            async let phraseTask = loadPhraseOfTheDay()
            async let favoritesTask = loadSavedItems()
            async let recentsTask = loadRecentItems()

            let (tip, phrase, favorites, recents) = await (
                try tipTask,
                try phraseTask,
                try favoritesTask,
                try recentsTask
            )

            tipOfTheDay = tip
            phraseOfTheDay = phrase
            savedItems = favorites
            recentItems = recents
            offlineStatus = checkOfflineStatus()

        } catch {
            self.error = error
        }

        isLoading = false
    }

    /// Refreshes home screen data (pull-to-refresh).
    func refresh() async {
        await loadData()
    }

    // MARK: - Private Methods

    /// Selects a tip for today based on date hash.
    private func loadTipOfTheDay() async throws -> Tip? {
        let tips = try await tipRepository.getAllTips()
        guard !tips.isEmpty else { return nil }

        // Use date to deterministically select a tip
        let dayOfYear = Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 1
        let index = dayOfYear % tips.count
        return tips[index]
    }

    /// Selects a phrase for today based on date hash.
    private func loadPhraseOfTheDay() async throws -> Phrase? {
        let phrases = try await phraseRepository.getAllPhrases()
        guard !phrases.isEmpty else { return nil }

        // Use date to deterministically select a phrase (offset by 7 to differ from tip)
        let dayOfYear = (Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 1) + 7
        let index = dayOfYear % phrases.count
        return phrases[index]
    }

    /// Loads saved/favorited items.
    private func loadSavedItems() async throws -> [SavedItem] {
        let favorites = try await favoritesRepository.getAllFavorites()
        return favorites.prefix(10).map { favorite in
            SavedItem(
                id: "\(favorite.contentType)-\(favorite.contentId)",
                title: favorite.contentId, // Would need to resolve actual title from content
                type: favorite.contentType.capitalized
            )
        }
    }

    /// Loads recently viewed items.
    private func loadRecentItems() async throws -> [RecentItem] {
        let recents = try await recentsRepository.getRecentItems(limit: 5)
        return recents.map { recent in
            RecentItem(
                id: "\(recent.contentType)-\(recent.contentId)",
                title: recent.contentId, // Would need to resolve actual title from content
                type: recent.contentType.capitalized,
                viewedAt: recent.viewedAt
            )
        }
    }

    /// Checks the offline status of the app.
    private func checkOfflineStatus() -> OfflineStatus {
        // TODO: Check actual content.db integrity and pack status
        // For now, assume offline ready if we have content
        if tipOfTheDay != nil && phraseOfTheDay != nil {
            return .ready
        }
        return .downloadRecommended
    }
}
