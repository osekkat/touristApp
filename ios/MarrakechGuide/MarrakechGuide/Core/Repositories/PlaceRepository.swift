import Foundation
import GRDB

/// Protocol defining the interface for place data access.
/// Implemented by PlaceRepositoryImpl which uses GRDB under the hood.
protocol PlaceRepository: Sendable {
    /// Fetch all places
    func getAllPlaces() async throws -> [Place]

    /// Fetch a single place by ID
    func getPlace(id: String) async throws -> Place?

    /// Fetch places by category
    func getPlaces(category: String) async throws -> [Place]

    /// Fetch places by region
    func getPlaces(region: String) async throws -> [Place]

    /// Search places using full-text search
    /// - Parameters:
    ///   - query: The search query
    ///   - limit: Maximum results to return (default 20)
    func searchPlaces(query: String, limit: Int) async throws -> [Place]

    /// Get related price cards for a place
    func getRelatedPriceCards(placeId: String) async throws -> [PriceCard]

    /// Get related phrases for a place
    func getRelatedPhrases(placeId: String) async throws -> [Phrase]
}

/// Default implementation of PlaceRepository using GRDB.
final class PlaceRepositoryImpl: PlaceRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllPlaces() async throws -> [Place] {
        try await contentDb.fetchAllPlaces()
    }

    func getPlace(id: String) async throws -> Place? {
        try await contentDb.fetchPlace(id: id)
    }

    func getPlaces(category: String) async throws -> [Place] {
        try await contentDb.fetchPlaces(category: category)
    }

    func getPlaces(region: String) async throws -> [Place] {
        try await contentDb.dbPool.read { db in
            try Place.filter(Column("region_id") == region).fetchAll(db)
        }
    }

    func searchPlaces(query: String, limit: Int = 20) async throws -> [Place] {
        guard let pattern = FTS5Pattern(matchingAllPrefixesIn: query) else { return [] }
        guard limit > 0 else { return [] }
        let safeLimit = min(limit, 100)

        return try await contentDb.dbPool.read { db in
            let sql = """
                SELECT places.* FROM places
                JOIN places_fts ON places.rowid = places_fts.rowid
                WHERE places_fts MATCH ?
                ORDER BY bm25(places_fts)
                LIMIT ?
            """
            return try Place.fetchAll(db, sql: sql, arguments: [pattern.rawPattern, safeLimit])
        }
    }

    func getRelatedPriceCards(placeId: String) async throws -> [PriceCard] {
        let links = try await contentDb.fetchRelatedContent(fromType: "place", fromId: placeId)
        let priceCardIds = links.filter { $0.toType == "price_card" }.map { $0.toId }

        guard !priceCardIds.isEmpty else { return [] }

        return try await contentDb.dbPool.read { db in
            try PriceCard.filter(priceCardIds.contains(Column("id"))).fetchAll(db)
        }
    }

    func getRelatedPhrases(placeId: String) async throws -> [Phrase] {
        let links = try await contentDb.fetchRelatedContent(fromType: "place", fromId: placeId)
        let phraseIds = links.filter { $0.toType == "phrase" }.map { $0.toId }

        guard !phraseIds.isEmpty else { return [] }

        return try await contentDb.dbPool.read { db in
            try Phrase.filter(phraseIds.contains(Column("id"))).fetchAll(db)
        }
    }
}
