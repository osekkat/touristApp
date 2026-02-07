import Foundation
import GRDB

/// Protocol defining the interface for price card data access.
protocol PriceCardRepository: Sendable {
    /// Fetch all price cards
    func getAllPriceCards() async throws -> [PriceCard]

    /// Fetch a single price card by ID
    func getPriceCard(id: String) async throws -> PriceCard?

    /// Fetch price cards by category
    func getPriceCards(category: String) async throws -> [PriceCard]

    /// Search price cards
    func searchPriceCards(query: String, limit: Int) async throws -> [PriceCard]

    /// Get context modifiers for a price card
    func getModifiers(for cardId: String) async throws -> [ContextModifier]

    /// Get negotiation scripts for a price card
    func getScripts(for cardId: String) async throws -> [NegotiationScript]
}

/// Default implementation of PriceCardRepository using GRDB.
final class PriceCardRepositoryImpl: PriceCardRepository, @unchecked Sendable {
    private let contentDb: ContentDatabase

    init(contentDb: ContentDatabase) {
        self.contentDb = contentDb
    }

    func getAllPriceCards() async throws -> [PriceCard] {
        try await contentDb.fetchAllPriceCards()
    }

    func getPriceCard(id: String) async throws -> PriceCard? {
        try await contentDb.fetchPriceCard(id: id)
    }

    func getPriceCards(category: String) async throws -> [PriceCard] {
        try await contentDb.fetchPriceCards(category: category)
    }

    func searchPriceCards(query: String, limit: Int = 20) async throws -> [PriceCard] {
        guard !query.isEmpty else { return [] }

        return try await contentDb.dbPool.read { db in
            let pattern = FTS5Pattern(matchingAllPrefixesIn: query)
            let sql = """
                SELECT price_cards.* FROM price_cards
                JOIN price_cards_fts ON price_cards.rowid = price_cards_fts.rowid
                WHERE price_cards_fts MATCH ?
                ORDER BY bm25(price_cards_fts)
                LIMIT ?
            """
            return try PriceCard.fetchAll(db, sql: sql, arguments: [pattern?.rawPattern ?? query, limit])
        }
    }

    func getModifiers(for cardId: String) async throws -> [ContextModifier] {
        guard let card = try await getPriceCard(id: cardId) else {
            return []
        }
        return card.contextModifiers ?? []
    }

    func getScripts(for cardId: String) async throws -> [NegotiationScript] {
        guard let card = try await getPriceCard(id: cardId) else {
            return []
        }
        return card.negotiationScripts ?? []
    }
}
