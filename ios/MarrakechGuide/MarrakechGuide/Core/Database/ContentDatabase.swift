import Foundation
import GRDB

/// ContentDatabase provides read-only access to the bundled content.db.
///
/// This database contains all curated Marrakech content: places, price cards,
/// phrases, itineraries, tips, culture articles, activities, and events.
/// It's never modified at runtime - updates come via app updates with a new bundle.
final class ContentDatabase: @unchecked Sendable {
    /// The read-only database pool for concurrent access
    let dbPool: DatabasePool

    /// Path to the content database file
    let path: String

    /// Initialize with a database file path
    /// - Parameter path: Path to content.db file
    /// - Throws: Database errors if file cannot be opened
    init(path: String) throws {
        self.path = path

        // Configure for read-only access
        var config = Configuration()
        config.readonly = true
        config.prepareDatabase { db in
            // Enable memory-mapped I/O for better read performance
            try db.execute(sql: "PRAGMA mmap_size = 268435456") // 256MB
        }

        self.dbPool = try DatabasePool(path: path, configuration: config)
    }

    /// Convenience initializer from bundle
    /// - Throws: Error if content.db not found in bundle or Documents
    convenience init() throws {
        let path = try ContentDatabase.resolveContentDatabasePath()
        try self.init(path: path)
    }

    // MARK: - Path Resolution

    private static let seededBundleVersionKey = "content_db_seeded_bundle_version"
    private static let sqliteHeader = Data("SQLite format 3\0".utf8)

    /// Resolves the path to content.db, syncing from bundle on first run and app updates.
    private static func resolveContentDatabasePath() throws -> String {
        let fileManager = FileManager.default
        let documentsURL = try fileManager.url(
            for: .documentDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let destURL = documentsURL.appendingPathComponent("content.db")
        let currentBundleVersion = Bundle.main.object(
            forInfoDictionaryKey: "CFBundleVersion"
        ) as? String ?? "unknown"
        let lastSeededBundleVersion = UserDefaults.standard.string(forKey: seededBundleVersionKey)
        let hasExistingDatabase = fileManager.fileExists(atPath: destURL.path)
        let hadUsableDatabase = isUsableSQLiteDatabase(at: destURL, fileManager: fileManager)
        let needsSeedInstall = !hadUsableDatabase
            || lastSeededBundleVersion != currentBundleVersion

        if needsSeedInstall {
            let tempURL = documentsURL.appendingPathComponent("content.db.tmp.\(UUID().uuidString)")
            do {
                let sourceURL = try resolveSeedDatabaseURL()
                if fileManager.fileExists(atPath: tempURL.path) {
                    try fileManager.removeItem(at: tempURL)
                }
                defer {
                    try? fileManager.removeItem(at: tempURL)
                }

                try fileManager.copyItem(at: sourceURL, to: tempURL)

                if hasExistingDatabase {
                    _ = try fileManager.replaceItemAt(destURL, withItemAt: tempURL)
                } else {
                    try fileManager.moveItem(at: tempURL, to: destURL)
                }

                // Best effort: remove stale SQLite sidecars from the previous DB state.
                // Run before usability checks so stale WAL/SHM files do not invalidate a fresh swap.
                try? cleanupJournalFiles(for: destURL, fileManager: fileManager)

                guard isUsableSQLiteDatabase(at: destURL, fileManager: fileManager) else {
                    throw ContentDatabaseError.migrationFailed(
                        "Staged content.db is not a valid SQLite file"
                    )
                }
                UserDefaults.standard.set(currentBundleVersion, forKey: seededBundleVersionKey)
            } catch {
                // Keep app usable offline if refresh fails but a prior DB still exists.
                // If no database file is left after a failed swap, startup must fail.
                let databaseStillUsable = isUsableSQLiteDatabase(at: destURL, fileManager: fileManager)
                if !hasExistingDatabase || !hadUsableDatabase || !databaseStillUsable {
                    throw error
                }
            }
        }

        // Backup exclusion is best-effort and should never block app startup.
        try? excludeFromBackup(url: destURL)
        return destURL.path
    }

    private static func resolveSeedDatabaseURL() throws -> URL {
        if let bundleURL = Bundle.main.url(forResource: "content", withExtension: "db") {
            return bundleURL
        }
        if let seedURL = Bundle.main.url(forResource: "content", withExtension: "db", subdirectory: "SeedData") {
            return seedURL
        }
        throw ContentDatabaseError.seedDatabaseNotFound
    }

    private static func excludeFromBackup(url: URL) throws {
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        var mutableURL = url
        try mutableURL.setResourceValues(resourceValues)
    }

    private static func cleanupJournalFiles(for databaseURL: URL, fileManager: FileManager) throws {
        let sidecarSuffixes = ["-wal", "-shm", "-journal"]
        for suffix in sidecarSuffixes {
            let sidecarURL = URL(fileURLWithPath: databaseURL.path + suffix)
            if fileManager.fileExists(atPath: sidecarURL.path) {
                try fileManager.removeItem(at: sidecarURL)
            }
        }
    }

    private static func isUsableSQLiteDatabase(at url: URL, fileManager: FileManager) -> Bool {
        guard fileManager.fileExists(atPath: url.path) else { return false }
        guard let attributes = try? fileManager.attributesOfItem(atPath: url.path),
              let fileSize = attributes[.size] as? NSNumber,
              fileSize.int64Value >= Int64(sqliteHeader.count) else {
            return false
        }

        guard hasValidSQLiteHeader(at: url) else {
            return false
        }

        return canOpenSQLiteDatabase(at: url)
    }

    private static func hasValidSQLiteHeader(at url: URL) -> Bool {
        do {
            let fileHandle = try FileHandle(forReadingFrom: url)
            defer {
                try? fileHandle.close()
            }
            var headerData = Data()
            while headerData.count < sqliteHeader.count {
                let remaining = sqliteHeader.count - headerData.count
                let chunk = try fileHandle.read(upToCount: remaining) ?? Data()
                if chunk.isEmpty {
                    return false
                }
                headerData.append(chunk)
            }
            return headerData == sqliteHeader
        } catch {
            return false
        }
    }

    private static func canOpenSQLiteDatabase(at url: URL) -> Bool {
        do {
            var config = Configuration()
            config.readonly = true
            let dbQueue = try DatabaseQueue(path: url.path, configuration: config)
            let isExpectedContentDatabase = try dbQueue.read { db in
                let schemaVersion = try Int.fetchOne(db, sql: "PRAGMA schema_version")
                let hasPlacesTable = try Int.fetchOne(
                    db,
                    sql: "SELECT 1 FROM sqlite_master WHERE type='table' AND name='places' LIMIT 1"
                ) != nil
                return schemaVersion != nil && hasPlacesTable
            }
            return isExpectedContentDatabase
        } catch {
            return false
        }
    }

    // MARK: - Version Info

    /// Get database metadata (version info from meta table if exists)
    func getMetadata() async throws -> [String: String] {
        try await dbPool.read { db in
            var meta: [String: String] = [:]

            // Check if we have a metadata table
            let tables = try String.fetchAll(db, sql: """
                SELECT name FROM sqlite_master WHERE type='table' AND name='metadata'
            """)

            if tables.contains("metadata") {
                let rows = try Row.fetchAll(db, sql: "SELECT key, value FROM metadata")
                for row in rows {
                    if let key: String = row["key"], let value: String = row["value"] {
                        meta[key] = value
                    }
                }
            }

            return meta
        }
    }
}

// MARK: - Errors

enum ContentDatabaseError: LocalizedError {
    case seedDatabaseNotFound
    case migrationFailed(String)

    var errorDescription: String? {
        switch self {
        case .seedDatabaseNotFound:
            return "Content database not found in app bundle. The app may be corrupted."
        case .migrationFailed(let reason):
            return "Database migration failed: \(reason)"
        }
    }
}

// MARK: - Place Queries

extension ContentDatabase {
    /// Fetch all places
    func fetchAllPlaces() async throws -> [Place] {
        try await dbPool.read { db in
            try Place.fetchAll(db)
        }
    }

    /// Fetch a single place by ID
    func fetchPlace(id: String) async throws -> Place? {
        try await dbPool.read { db in
            try Place.fetchOne(db, key: id)
        }
    }

    /// Fetch places by category
    func fetchPlaces(category: String) async throws -> [Place] {
        try await dbPool.read { db in
            try Place.filter(Column("category") == category).fetchAll(db)
        }
    }

    /// Search places using FTS5
    func searchPlaces(query: String) async throws -> [Place] {
        guard let pattern = FTS5Pattern(matchingAllPrefixesIn: query) else { return [] }

        return try await dbPool.read { db in
            let sql = """
                SELECT places.* FROM places
                JOIN places_fts ON places.rowid = places_fts.rowid
                WHERE places_fts MATCH ?
                ORDER BY bm25(places_fts)
                LIMIT 50
            """
            return try Place.fetchAll(db, sql: sql, arguments: [pattern.rawPattern])
        }
    }
}

// MARK: - PriceCard Queries

extension ContentDatabase {
    /// Fetch all price cards
    func fetchAllPriceCards() async throws -> [PriceCard] {
        try await dbPool.read { db in
            try PriceCard.fetchAll(db)
        }
    }

    /// Fetch a single price card by ID
    func fetchPriceCard(id: String) async throws -> PriceCard? {
        try await dbPool.read { db in
            try PriceCard.fetchOne(db, key: id)
        }
    }

    /// Fetch price cards by category
    func fetchPriceCards(category: String) async throws -> [PriceCard] {
        try await dbPool.read { db in
            try PriceCard.filter(Column("category") == category).fetchAll(db)
        }
    }

    /// Search price cards using FTS5
    func searchPriceCards(query: String) async throws -> [PriceCard] {
        guard let pattern = FTS5Pattern(matchingAllPrefixesIn: query) else { return [] }

        return try await dbPool.read { db in
            let sql = """
                SELECT price_cards.* FROM price_cards
                JOIN price_cards_fts ON price_cards.rowid = price_cards_fts.rowid
                WHERE price_cards_fts MATCH ?
                ORDER BY bm25(price_cards_fts)
                LIMIT 50
            """
            return try PriceCard.fetchAll(db, sql: sql, arguments: [pattern.rawPattern])
        }
    }
}

// MARK: - Phrase Queries

extension ContentDatabase {
    /// Fetch all phrases
    func fetchAllPhrases() async throws -> [Phrase] {
        try await dbPool.read { db in
            try Phrase.fetchAll(db)
        }
    }

    /// Fetch a single phrase by ID
    func fetchPhrase(id: String) async throws -> Phrase? {
        try await dbPool.read { db in
            try Phrase.fetchOne(db, key: id)
        }
    }

    /// Fetch phrases by category
    func fetchPhrases(category: String) async throws -> [Phrase] {
        try await dbPool.read { db in
            try Phrase.filter(Column("category") == category).fetchAll(db)
        }
    }

    /// Search phrases using FTS5
    func searchPhrases(query: String) async throws -> [Phrase] {
        guard let pattern = FTS5Pattern(matchingAllPrefixesIn: query) else { return [] }

        return try await dbPool.read { db in
            let sql = """
                SELECT phrases.* FROM phrases
                JOIN phrases_fts ON phrases.rowid = phrases_fts.rowid
                WHERE phrases_fts MATCH ?
                ORDER BY bm25(phrases_fts)
                LIMIT 50
            """
            return try Phrase.fetchAll(db, sql: sql, arguments: [pattern.rawPattern])
        }
    }
}

// MARK: - Itinerary Queries

extension ContentDatabase {
    /// Fetch all itineraries
    func fetchAllItineraries() async throws -> [Itinerary] {
        try await dbPool.read { db in
            try Itinerary.fetchAll(db)
        }
    }

    /// Fetch a single itinerary by ID
    func fetchItinerary(id: String) async throws -> Itinerary? {
        try await dbPool.read { db in
            try Itinerary.fetchOne(db, key: id)
        }
    }
}

// MARK: - Tip Queries

extension ContentDatabase {
    /// Fetch all tips
    func fetchAllTips() async throws -> [Tip] {
        try await dbPool.read { db in
            try Tip.fetchAll(db)
        }
    }

    /// Fetch a single tip by ID
    func fetchTip(id: String) async throws -> Tip? {
        try await dbPool.read { db in
            try Tip.fetchOne(db, key: id)
        }
    }

    /// Fetch tips by category
    func fetchTips(category: String) async throws -> [Tip] {
        try await dbPool.read { db in
            try Tip.filter(Column("category") == category).fetchAll(db)
        }
    }

    /// Search tips using FTS5
    func searchTips(query: String) async throws -> [Tip] {
        guard let pattern = FTS5Pattern(matchingAllPrefixesIn: query) else { return [] }

        return try await dbPool.read { db in
            let sql = """
                SELECT tips.* FROM tips
                JOIN tips_fts ON tips.rowid = tips_fts.rowid
                WHERE tips_fts MATCH ?
                ORDER BY bm25(tips_fts)
                LIMIT 50
            """
            return try Tip.fetchAll(db, sql: sql, arguments: [pattern.rawPattern])
        }
    }
}

// MARK: - Culture Queries

extension ContentDatabase {
    /// Fetch all culture articles
    func fetchAllCultureArticles() async throws -> [CultureArticle] {
        try await dbPool.read { db in
            try CultureArticle.fetchAll(db)
        }
    }

    /// Fetch a single culture article by ID
    func fetchCultureArticle(id: String) async throws -> CultureArticle? {
        try await dbPool.read { db in
            try CultureArticle.fetchOne(db, key: id)
        }
    }
}

// MARK: - Activity Queries

extension ContentDatabase {
    /// Fetch all activities
    func fetchAllActivities() async throws -> [Activity] {
        try await dbPool.read { db in
            try Activity.fetchAll(db)
        }
    }

    /// Fetch a single activity by ID
    func fetchActivity(id: String) async throws -> Activity? {
        try await dbPool.read { db in
            try Activity.fetchOne(db, key: id)
        }
    }

    /// Fetch activities by category
    func fetchActivities(category: String) async throws -> [Activity] {
        try await dbPool.read { db in
            try Activity.filter(Column("category") == category).fetchAll(db)
        }
    }
}

// MARK: - Event Queries

extension ContentDatabase {
    /// Fetch all events
    func fetchAllEvents() async throws -> [Event] {
        try await dbPool.read { db in
            try Event.fetchAll(db)
        }
    }

    /// Fetch a single event by ID
    func fetchEvent(id: String) async throws -> Event? {
        try await dbPool.read { db in
            try Event.fetchOne(db, key: id)
        }
    }

    /// Fetch upcoming events
    func fetchUpcomingEvents() async throws -> [Event] {
        try await dbPool.read { db in
            let now = ISO8601DateFormatter().string(from: Date())
            return try Event
                .filter(Column("start_at") >= now)
                .order(Column("start_at"))
                .fetchAll(db)
        }
    }
}

// MARK: - Content Links

extension ContentDatabase {
    /// Fetch related content IDs for an item
    func fetchRelatedContent(fromType: String, fromId: String) async throws -> [(toType: String, toId: String, linkKind: String)] {
        try await dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT to_type, to_id, link_kind FROM content_links
                WHERE from_type = ? AND from_id = ?
            """, arguments: [fromType, fromId])

            return rows.compactMap { row in
                guard let toType: String = row["to_type"],
                      let toId: String = row["to_id"],
                      let linkKind: String = row["link_kind"] else {
                    return nil
                }
                return (toType: toType, toId: toId, linkKind: linkKind)
            }
        }
    }
}
