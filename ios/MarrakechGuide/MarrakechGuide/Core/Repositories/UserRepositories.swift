import Foundation

// MARK: - Content Type

/// Types of content that can be favorited or viewed
enum ContentType: String, Codable, CaseIterable {
    case place
    case priceCard = "price_card"
    case phrase
    case itinerary
    case tip
    case culture
    case activity
    case event
}

// MARK: - Setting Keys

/// Keys for user settings stored in user.db
enum SettingKey: String, CaseIterable {
    case homeBase = "home_base"
    case exchangeRate = "exchange_rate"
    case wifiOnlyDownloads = "wifi_only_downloads"
    case language = "language"
    case lastContentVersion = "last_content_version"
    case onboardingComplete = "onboarding_complete"
    case arrivalModeActive = "arrival_mode_active"
}

// MARK: - User Settings Models

/// User's home base location (e.g., their riad/hotel)
struct HomeBase: Codable, Equatable {
    let name: String
    let lat: Double
    let lng: Double
    let address: String?
}

/// Current exchange rate setting
struct ExchangeRate: Codable, Equatable {
    let currencyCode: String  // e.g., "USD", "EUR"
    let rateToMAD: Double     // How many MAD per 1 unit of currency
    let updatedAt: String
}

// MARK: - FavoritesRepository

/// Protocol for managing user favorites.
protocol FavoritesRepository: Sendable {
    /// Get all favorites
    func getFavorites() async throws -> [Favorite]

    /// Get favorites of a specific type
    func getFavorites(type: ContentType) async throws -> [Favorite]

    /// Check if an item is favorited
    func isFavorite(contentType: ContentType, contentId: String) async throws -> Bool

    /// Add an item to favorites
    func addFavorite(contentType: ContentType, contentId: String) async throws

    /// Remove an item from favorites
    func removeFavorite(contentType: ContentType, contentId: String) async throws

    /// Toggle favorite status and return new state
    func toggleFavorite(contentType: ContentType, contentId: String) async throws -> Bool
}

/// Default implementation of FavoritesRepository.
final class FavoritesRepositoryImpl: FavoritesRepository, @unchecked Sendable {
    private let userDb: UserDatabase

    init(userDb: UserDatabase) {
        self.userDb = userDb
    }

    func getFavorites() async throws -> [Favorite] {
        try await userDb.getFavorites()
    }

    func getFavorites(type: ContentType) async throws -> [Favorite] {
        try await userDb.getFavorites(contentType: type.rawValue)
    }

    func isFavorite(contentType: ContentType, contentId: String) async throws -> Bool {
        try await userDb.isFavorite(contentType: contentType.rawValue, contentId: contentId)
    }

    func addFavorite(contentType: ContentType, contentId: String) async throws {
        try await userDb.addFavorite(contentType: contentType.rawValue, contentId: contentId)
    }

    func removeFavorite(contentType: ContentType, contentId: String) async throws {
        try await userDb.removeFavorite(contentType: contentType.rawValue, contentId: contentId)
    }

    func toggleFavorite(contentType: ContentType, contentId: String) async throws -> Bool {
        let isFav = try await isFavorite(contentType: contentType, contentId: contentId)
        if isFav {
            try await removeFavorite(contentType: contentType, contentId: contentId)
            return false
        } else {
            try await addFavorite(contentType: contentType, contentId: contentId)
            return true
        }
    }
}

// MARK: - RecentsRepository

/// Protocol for managing recently viewed items.
protocol RecentsRepository: Sendable {
    /// Get recent items
    func getRecents(limit: Int) async throws -> [Recent]

    /// Get recent items of a specific type
    func getRecents(type: ContentType, limit: Int) async throws -> [Recent]

    /// Record a view of an item
    func recordView(contentType: ContentType, contentId: String) async throws

    /// Clear all recent views
    func clearRecents() async throws
}

/// Default implementation of RecentsRepository.
final class RecentsRepositoryImpl: RecentsRepository, @unchecked Sendable {
    private let userDb: UserDatabase

    init(userDb: UserDatabase) {
        self.userDb = userDb
    }

    func getRecents(limit: Int = 20) async throws -> [Recent] {
        try await userDb.getRecents(limit: limit)
    }

    func getRecents(type: ContentType, limit: Int = 20) async throws -> [Recent] {
        try await userDb.getRecents(contentType: type.rawValue, limit: limit)
    }

    func recordView(contentType: ContentType, contentId: String) async throws {
        try await userDb.recordView(contentType: contentType.rawValue, contentId: contentId)
    }

    func clearRecents() async throws {
        try await userDb.clearRecents()
    }
}

// MARK: - UserSettingsRepository

/// Protocol for managing user settings.
protocol UserSettingsRepository: Sendable {
    /// Get a setting value
    func getSetting<T: Decodable>(key: SettingKey) async throws -> T?

    /// Set a setting value
    func setSetting<T: Encodable>(key: SettingKey, value: T) async throws

    /// Delete a setting
    func deleteSetting(key: SettingKey) async throws

    /// Get the user's home base
    func getHomeBase() async throws -> HomeBase?

    /// Set the user's home base
    func setHomeBase(_ homeBase: HomeBase) async throws

    /// Get the current exchange rate
    func getExchangeRate() async throws -> ExchangeRate?

    /// Set the current exchange rate
    func setExchangeRate(_ rate: ExchangeRate) async throws

    /// Check if onboarding is complete
    func isOnboardingComplete() async throws -> Bool

    /// Mark onboarding as complete
    func setOnboardingComplete(_ complete: Bool) async throws
}

/// Default implementation of UserSettingsRepository.
final class UserSettingsRepositoryImpl: UserSettingsRepository, @unchecked Sendable {
    private let userDb: UserDatabase

    init(userDb: UserDatabase) {
        self.userDb = userDb
    }

    func getSetting<T: Decodable>(key: SettingKey) async throws -> T? {
        try await userDb.getSetting(key: key.rawValue, as: T.self)
    }

    func setSetting<T: Encodable>(key: SettingKey, value: T) async throws {
        try await userDb.setSetting(key: key.rawValue, value: value)
    }

    func deleteSetting(key: SettingKey) async throws {
        try await userDb.deleteSetting(key: key.rawValue)
    }

    func getHomeBase() async throws -> HomeBase? {
        try await getSetting(key: .homeBase)
    }

    func setHomeBase(_ homeBase: HomeBase) async throws {
        try await setSetting(key: .homeBase, value: homeBase)
    }

    func getExchangeRate() async throws -> ExchangeRate? {
        try await getSetting(key: .exchangeRate)
    }

    func setExchangeRate(_ rate: ExchangeRate) async throws {
        try await setSetting(key: .exchangeRate, value: rate)
    }

    func isOnboardingComplete() async throws -> Bool {
        let value: Bool? = try await getSetting(key: .onboardingComplete)
        return value ?? false
    }

    func setOnboardingComplete(_ complete: Bool) async throws {
        try await setSetting(key: .onboardingComplete, value: complete)
    }
}

// MARK: - SavedPlansRepository

/// Protocol for managing saved My Day plans.
protocol SavedPlansRepository: Sendable {
    /// Get all saved plans
    func getSavedPlans() async throws -> [SavedPlan]

    /// Get a specific plan by ID
    func getPlan(id: Int64) async throws -> SavedPlan?

    /// Save a new plan
    func savePlan(title: String, planDate: String?, planData: String) async throws -> Int64

    /// Update an existing plan
    func updatePlan(id: Int64, title: String?, planData: String?) async throws

    /// Delete a plan
    func deletePlan(id: Int64) async throws

    /// Get progress for a plan
    func getProgress(planId: Int64) async throws -> [RouteProgress]

    /// Mark a step as completed
    func completeStep(planId: Int64, stepIndex: Int) async throws

    /// Skip a step
    func skipStep(planId: Int64, stepIndex: Int) async throws

    /// Reset progress for a plan
    func resetProgress(planId: Int64) async throws
}

/// Default implementation of SavedPlansRepository.
final class SavedPlansRepositoryImpl: SavedPlansRepository, @unchecked Sendable {
    private let userDb: UserDatabase

    init(userDb: UserDatabase) {
        self.userDb = userDb
    }

    func getSavedPlans() async throws -> [SavedPlan] {
        try await userDb.getSavedPlans()
    }

    func getPlan(id: Int64) async throws -> SavedPlan? {
        try await userDb.getPlan(id: id)
    }

    func savePlan(title: String, planDate: String?, planData: String) async throws -> Int64 {
        try await userDb.savePlan(title: title, planDate: planDate, planData: planData)
    }

    func updatePlan(id: Int64, title: String?, planData: String?) async throws {
        try await userDb.updatePlan(id: id, title: title, planData: planData)
    }

    func deletePlan(id: Int64) async throws {
        try await userDb.deletePlan(id: id)
    }

    func getProgress(planId: Int64) async throws -> [RouteProgress] {
        try await userDb.getRouteProgress(planId: planId)
    }

    func completeStep(planId: Int64, stepIndex: Int) async throws {
        try await userDb.completeStep(planId: planId, stepIndex: stepIndex)
    }

    func skipStep(planId: Int64, stepIndex: Int) async throws {
        try await userDb.skipStep(planId: planId, stepIndex: stepIndex)
    }

    func resetProgress(planId: Int64) async throws {
        try await userDb.resetRouteProgress(planId: planId)
    }
}
