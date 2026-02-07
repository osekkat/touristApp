import Foundation
import GRDB

// MARK: - PhraseRepository

/// Protocol for accessing phrase (phrasebook) data.
protocol PhraseRepository: Sendable {
    func getAllPhrases() async throws -> [Phrase]
    func getPhrase(id: String) async throws -> Phrase?
    func getPhrases(category: String) async throws -> [Phrase]
    func searchPhrases(query: String, limit: Int) async throws -> [Phrase]
}

/// Default implementation of PhraseRepository using GRDB.
final class PhraseRepositoryImpl: PhraseRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllPhrases() async throws -> [Phrase] {
        try await contentDb.fetchAllPhrases()
    }

    func getPhrase(id: String) async throws -> Phrase? {
        try await contentDb.fetchPhrase(id: id)
    }

    func getPhrases(category: String) async throws -> [Phrase] {
        try await contentDb.fetchPhrases(category: category)
    }

    func searchPhrases(query: String, limit: Int = 20) async throws -> [Phrase] {
        guard !query.isEmpty else { return [] }

        return try await contentDb.dbPool.read { db in
            let pattern = FTS5Pattern(matchingAllPrefixesIn: query)
            let sql = """
                SELECT phrases.* FROM phrases
                JOIN phrases_fts ON phrases.rowid = phrases_fts.rowid
                WHERE phrases_fts MATCH ?
                ORDER BY bm25(phrases_fts)
                LIMIT ?
            """
            return try Phrase.fetchAll(db, sql: sql, arguments: [pattern?.rawPattern ?? query, limit])
        }
    }
}

// MARK: - ItineraryRepository

/// Protocol for accessing itinerary data.
protocol ItineraryRepository: Sendable {
    func getAllItineraries() async throws -> [Itinerary]
    func getItinerary(id: String) async throws -> Itinerary?
    func getItineraries(duration: String) async throws -> [Itinerary]
    func getItineraries(style: String) async throws -> [Itinerary]
}

/// Default implementation of ItineraryRepository using GRDB.
final class ItineraryRepositoryImpl: ItineraryRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllItineraries() async throws -> [Itinerary] {
        try await contentDb.fetchAllItineraries()
    }

    func getItinerary(id: String) async throws -> Itinerary? {
        try await contentDb.fetchItinerary(id: id)
    }

    func getItineraries(duration: String) async throws -> [Itinerary] {
        try await contentDb.dbPool.read { db in
            try Itinerary.filter(Column("duration") == duration).fetchAll(db)
        }
    }

    func getItineraries(style: String) async throws -> [Itinerary] {
        try await contentDb.dbPool.read { db in
            try Itinerary.filter(Column("style") == style).fetchAll(db)
        }
    }
}

// MARK: - TipRepository

/// Protocol for accessing travel tips.
protocol TipRepository: Sendable {
    func getAllTips() async throws -> [Tip]
    func getTip(id: String) async throws -> Tip?
    func getTips(category: String) async throws -> [Tip]
    func getTips(severity: String) async throws -> [Tip]
    func getTipsForPlace(placeId: String) async throws -> [Tip]
    func getTipsForPriceCard(priceCardId: String) async throws -> [Tip]
    func searchTips(query: String, limit: Int) async throws -> [Tip]
}

/// Default implementation of TipRepository using GRDB.
final class TipRepositoryImpl: TipRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllTips() async throws -> [Tip] {
        try await contentDb.fetchAllTips()
    }

    func getTip(id: String) async throws -> Tip? {
        try await contentDb.fetchTip(id: id)
    }

    func getTips(category: String) async throws -> [Tip] {
        try await contentDb.fetchTips(category: category)
    }

    func getTips(severity: String) async throws -> [Tip] {
        try await contentDb.dbPool.read { db in
            try Tip.filter(Column("severity") == severity).fetchAll(db)
        }
    }

    func getTipsForPlace(placeId: String) async throws -> [Tip] {
        // Search tips where related_place_ids contains the placeId
        let allTips = try await getAllTips()
        return allTips.filter { tip in
            tip.relatedPlaceIds?.contains(placeId) ?? false
        }
    }

    func getTipsForPriceCard(priceCardId: String) async throws -> [Tip] {
        // Search tips where related_price_card_ids contains the priceCardId
        let allTips = try await getAllTips()
        return allTips.filter { tip in
            tip.relatedPriceCardIds?.contains(priceCardId) ?? false
        }
    }

    func searchTips(query: String, limit: Int = 20) async throws -> [Tip] {
        guard !query.isEmpty else { return [] }

        return try await contentDb.dbPool.read { db in
            let pattern = FTS5Pattern(matchingAllPrefixesIn: query)
            let sql = """
                SELECT tips.* FROM tips
                JOIN tips_fts ON tips.rowid = tips_fts.rowid
                WHERE tips_fts MATCH ?
                ORDER BY bm25(tips_fts)
                LIMIT ?
            """
            return try Tip.fetchAll(db, sql: sql, arguments: [pattern?.rawPattern ?? query, limit])
        }
    }
}

// MARK: - CultureRepository

/// Protocol for accessing culture articles.
protocol CultureRepository: Sendable {
    func getAllCultureArticles() async throws -> [CultureArticle]
    func getCultureArticle(id: String) async throws -> CultureArticle?
    func getCultureArticles(category: String) async throws -> [CultureArticle]
}

/// Default implementation of CultureRepository using GRDB.
final class CultureRepositoryImpl: CultureRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllCultureArticles() async throws -> [CultureArticle] {
        try await contentDb.fetchAllCultureArticles()
    }

    func getCultureArticle(id: String) async throws -> CultureArticle? {
        try await contentDb.fetchCultureArticle(id: id)
    }

    func getCultureArticles(category: String) async throws -> [CultureArticle] {
        try await contentDb.dbPool.read { db in
            try CultureArticle.filter(Column("category") == category).fetchAll(db)
        }
    }
}

// MARK: - ActivityRepository

/// Protocol for accessing activity data.
protocol ActivityRepository: Sendable {
    func getAllActivities() async throws -> [Activity]
    func getActivity(id: String) async throws -> Activity?
    func getActivities(category: String) async throws -> [Activity]
    func getActivities(region: String) async throws -> [Activity]
}

/// Default implementation of ActivityRepository using GRDB.
final class ActivityRepositoryImpl: ActivityRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllActivities() async throws -> [Activity] {
        try await contentDb.fetchAllActivities()
    }

    func getActivity(id: String) async throws -> Activity? {
        try await contentDb.fetchActivity(id: id)
    }

    func getActivities(category: String) async throws -> [Activity] {
        try await contentDb.fetchActivities(category: category)
    }

    func getActivities(region: String) async throws -> [Activity] {
        try await contentDb.dbPool.read { db in
            try Activity.filter(Column("region_id") == region).fetchAll(db)
        }
    }
}

// MARK: - EventRepository

/// Protocol for accessing event data.
protocol EventRepository: Sendable {
    func getAllEvents() async throws -> [Event]
    func getEvent(id: String) async throws -> Event?
    func getUpcomingEvents() async throws -> [Event]
    func getEvents(category: String) async throws -> [Event]
}

/// Default implementation of EventRepository using GRDB.
final class EventRepositoryImpl: EventRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllEvents() async throws -> [Event] {
        try await contentDb.fetchAllEvents()
    }

    func getEvent(id: String) async throws -> Event? {
        try await contentDb.fetchEvent(id: id)
    }

    func getUpcomingEvents() async throws -> [Event] {
        try await contentDb.fetchUpcomingEvents()
    }

    func getEvents(category: String) async throws -> [Event] {
        try await contentDb.dbPool.read { db in
            try Event.filter(Column("category") == category).fetchAll(db)
        }
    }
}
