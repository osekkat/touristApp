import Foundation

/// Central dependency container for the app.
///
/// Provides lazy-initialized repositories and services that depend on the DatabaseManager.
/// All properties are computed to defer initialization until DatabaseManager is ready.
@MainActor
final class Container: ObservableObject {
    static let shared = Container()

    private init() {}

    // MARK: - Database

    /// The shared database manager. Must be initialized before accessing repositories.
    private var _databaseManager: DatabaseManager?

    var databaseManager: DatabaseManager {
        get async throws {
            if let existing = _databaseManager {
                return existing
            }
            let manager = try await DatabaseManager.shared
            _databaseManager = manager
            return manager
        }
    }

    /// Check if the database manager is already initialized
    var isDatabaseReady: Bool {
        _databaseManager != nil
    }

    // MARK: - Content Repositories

    /// Phrase repository for Darija phrasebook
    var phraseRepository: PhraseRepository {
        get async throws {
            let manager = try await databaseManager
            return PhraseRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Itinerary repository for pre-built day plans
    var itineraryRepository: ItineraryRepository {
        get async throws {
            let manager = try await databaseManager
            return ItineraryRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Tip repository for safety and practical tips
    var tipRepository: TipRepository {
        get async throws {
            let manager = try await databaseManager
            return TipRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Culture repository for etiquette articles
    var cultureRepository: CultureRepository {
        get async throws {
            let manager = try await databaseManager
            return CultureRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Place repository for attractions and points of interest
    var placeRepository: PlaceRepository {
        get async throws {
            let manager = try await databaseManager
            return PlaceRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Price card repository for common prices
    var priceCardRepository: PriceCardRepository {
        get async throws {
            let manager = try await databaseManager
            return PriceCardRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Activity repository for things to do
    var activityRepository: ActivityRepository {
        get async throws {
            let manager = try await databaseManager
            return ActivityRepositoryImpl(contentDb: manager.content)
        }
    }

    /// Event repository for local events
    var eventRepository: EventRepository {
        get async throws {
            let manager = try await databaseManager
            return EventRepositoryImpl(contentDb: manager.content)
        }
    }

    // MARK: - User Repositories

    /// Favorites repository for saved items
    var favoritesRepository: FavoritesRepository {
        get async throws {
            let manager = try await databaseManager
            return FavoritesRepositoryImpl(userDb: manager.user)
        }
    }

    /// User settings repository
    var userSettingsRepository: UserSettingsRepository {
        get async throws {
            let manager = try await databaseManager
            return UserSettingsRepositoryImpl(userDb: manager.user)
        }
    }

    /// Recent views repository
    var recentsRepository: RecentsRepository {
        get async throws {
            let manager = try await databaseManager
            return RecentsRepositoryImpl(userDb: manager.user)
        }
    }
}
