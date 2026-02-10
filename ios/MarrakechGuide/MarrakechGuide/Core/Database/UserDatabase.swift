import Foundation
import GRDB

/// UserDatabase provides read-write access to user state.
///
/// This database stores user preferences, favorites, recent views, saved plans,
/// and other mutable state. It persists across app restarts.
final class UserDatabase: @unchecked Sendable {
    /// The database queue for serialized access
    let dbWriter: DatabaseWriter

    /// Path to the user database file
    let path: String

    /// Initialize with a database file path
    /// - Parameter path: Path to user.db file
    /// - Throws: Database errors if file cannot be opened or migrations fail
    init(path: String) throws {
        self.path = path

        // Create database with WAL mode for better performance
        var config = Configuration()
        config.prepareDatabase { db in
            try db.execute(sql: "PRAGMA journal_mode = WAL")
            try db.execute(sql: "PRAGMA foreign_keys = ON")
        }

        let dbPool = try DatabasePool(path: path, configuration: config)
        self.dbWriter = dbPool

        // Run migrations
        try migrateDatabaseIfNeeded(dbPool)
    }

    /// Convenience initializer using Documents directory
    convenience init() throws {
        let path = try UserDatabase.resolveUserDatabasePath()
        try self.init(path: path)
    }

    // MARK: - Path Resolution

    private static func resolveUserDatabasePath() throws -> String {
        let fileManager = FileManager.default
        let documentsURL = try fileManager.url(
            for: .documentDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return documentsURL.appendingPathComponent("user.db").path
    }

    // MARK: - Migrations

    private func migrateDatabaseIfNeeded(_ dbPool: DatabasePool) throws {
        var migrator = DatabaseMigrator()

        // Register migrations
        migrator.registerMigration("v1_initial") { db in
            // User settings table (key-value store)
            try db.create(table: "user_settings") { t in
                t.column("key", .text).primaryKey()
                t.column("value", .text) // JSON encoded
                t.column("updated_at", .text).notNull()
            }

            // Favorites table
            try db.create(table: "favorites") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("content_type", .text).notNull()
                t.column("content_id", .text).notNull()
                t.column("created_at", .text).notNull()

                // Unique constraint to prevent duplicates
                t.uniqueKey(["content_type", "content_id"])
            }
            try db.create(index: "idx_favorites_type", on: "favorites", columns: ["content_type"])

            // Recents table (recently viewed items)
            try db.create(table: "recents") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("content_type", .text).notNull()
                t.column("content_id", .text).notNull()
                t.column("viewed_at", .text).notNull()
            }
            try db.create(index: "idx_recents_viewed", on: "recents", columns: ["viewed_at"])

            // Saved plans table (My Day plans)
            try db.create(table: "saved_plans") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("title", .text).notNull()
                t.column("plan_date", .text) // Date the plan is for
                t.column("plan_data", .text).notNull() // JSON encoded plan
                t.column("created_at", .text).notNull()
                t.column("updated_at", .text).notNull()
            }

            // Route progress table (tracking progress on routes)
            try db.create(table: "route_progress") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("plan_id", .integer)
                    .notNull()
                    .references("saved_plans", onDelete: .cascade)
                t.column("step_index", .integer).notNull()
                t.column("completed_at", .text)
                t.column("skipped", .boolean).notNull().defaults(to: false)
            }
        }

        migrator.registerMigration("v2_route_progress_unique_index") { db in
            // Clean legacy malformed rows before enforcing uniqueness.
            try db.execute(sql: """
                DELETE FROM route_progress
                WHERE plan_id IS NULL
                   OR plan_id NOT IN (SELECT id FROM saved_plans)
            """)
            try db.execute(sql: """
                DELETE FROM route_progress
                WHERE rowid NOT IN (
                    SELECT MAX(rowid)
                    FROM route_progress
                    GROUP BY plan_id, step_index
                )
            """)
            try db.execute(sql: """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_route_progress_plan_step
                ON route_progress(plan_id, step_index)
            """)
        }

        // Run pending migrations
        try migrator.migrate(dbPool)
    }
}

// MARK: - User Settings

extension UserDatabase {
    /// Get a user setting value
    func getSetting(key: String) async throws -> String? {
        try await dbWriter.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT value FROM user_settings WHERE key = ?
            """, arguments: [key])
            return row?["value"]
        }
    }

    /// Get a typed user setting
    func getSetting<T: Decodable>(key: String, as type: T.Type) async throws -> T? {
        guard let jsonString = try await getSetting(key: key) else {
            return nil
        }
        guard let data = jsonString.data(using: .utf8) else {
            return nil
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Set a user setting value
    func setSetting(key: String, value: String) async throws {
        let now = ISO8601DateFormatter().string(from: Date())
        try await dbWriter.write { db in
            try db.execute(sql: """
                INSERT INTO user_settings (key, value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
            """, arguments: [key, value, now])
        }
    }

    /// Set a typed user setting
    func setSetting<T: Encodable>(key: String, value: T) async throws {
        let data = try JSONEncoder().encode(value)
        guard let jsonString = String(data: data, encoding: .utf8) else {
            throw UserDatabaseError.encodingFailed
        }
        try await setSetting(key: key, value: jsonString)
    }

    /// Delete a user setting
    func deleteSetting(key: String) async throws {
        try await dbWriter.write { db in
            try db.execute(sql: "DELETE FROM user_settings WHERE key = ?", arguments: [key])
        }
    }
}

// MARK: - Favorites

extension UserDatabase {
    /// Add an item to favorites
    func addFavorite(contentType: String, contentId: String) async throws {
        let now = ISO8601DateFormatter().string(from: Date())
        try await dbWriter.write { db in
            try db.execute(sql: """
                INSERT OR IGNORE INTO favorites (content_type, content_id, created_at)
                VALUES (?, ?, ?)
            """, arguments: [contentType, contentId, now])
        }
    }

    /// Remove an item from favorites
    func removeFavorite(contentType: String, contentId: String) async throws {
        try await dbWriter.write { db in
            try db.execute(sql: """
                DELETE FROM favorites WHERE content_type = ? AND content_id = ?
            """, arguments: [contentType, contentId])
        }
    }

    /// Check if an item is favorited
    func isFavorite(contentType: String, contentId: String) async throws -> Bool {
        try await dbWriter.read { db in
            let count = try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM favorites WHERE content_type = ? AND content_id = ?
            """, arguments: [contentType, contentId]) ?? 0
            return count > 0
        }
    }

    /// Get all favorites, optionally filtered by type
    func getFavorites(contentType: String? = nil) async throws -> [Favorite] {
        try await dbWriter.read { db in
            if let contentType {
                return try Favorite
                    .filter(Column("content_type") == contentType)
                    .order(Column("created_at").desc)
                    .fetchAll(db)
            } else {
                return try Favorite.order(Column("created_at").desc).fetchAll(db)
            }
        }
    }

    /// Clear all favorites
    func clearFavorites() async throws {
        try await dbWriter.write { db in
            try db.execute(sql: "DELETE FROM favorites")
        }
    }
}

// MARK: - Recents

extension UserDatabase {
    /// Record a view of an item
    func recordView(contentType: String, contentId: String) async throws {
        let now = ISO8601DateFormatter().string(from: Date())
        try await dbWriter.write { db in
            // Remove existing entry for this item (if any)
            try db.execute(sql: """
                DELETE FROM recents WHERE content_type = ? AND content_id = ?
            """, arguments: [contentType, contentId])

            // Add new entry at the top
            try db.execute(sql: """
                INSERT INTO recents (content_type, content_id, viewed_at)
                VALUES (?, ?, ?)
            """, arguments: [contentType, contentId, now])

            // Trim to keep only the 100 most recent
            try db.execute(sql: """
                DELETE FROM recents WHERE id NOT IN (
                    SELECT id FROM recents ORDER BY viewed_at DESC LIMIT 100
                )
            """)
        }
    }

    /// Get recent views, optionally filtered by type
    func getRecents(contentType: String? = nil, limit: Int = 20) async throws -> [Recent] {
        guard limit > 0 else { return [] }
        let safeLimit = min(limit, 100)

        try await dbWriter.read { db in
            var request = Recent.order(Column("viewed_at").desc).limit(safeLimit)
            if let contentType {
                request = request.filter(Column("content_type") == contentType)
            }
            return try request.fetchAll(db)
        }
    }

    /// Clear all recent views
    func clearRecents() async throws {
        try await dbWriter.write { db in
            try db.execute(sql: "DELETE FROM recents")
        }
    }
}

// MARK: - Saved Plans

extension UserDatabase {
    /// Save a new plan
    func savePlan(title: String, planDate: String?, planData: String) async throws -> Int64 {
        let now = ISO8601DateFormatter().string(from: Date())
        return try await dbWriter.write { db in
            try db.execute(sql: """
                INSERT INTO saved_plans (title, plan_date, plan_data, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
            """, arguments: [title, planDate, planData, now, now])
            return db.lastInsertedRowID
        }
    }

    /// Update an existing plan
    func updatePlan(id: Int64, title: String? = nil, planData: String? = nil) async throws {
        let now = ISO8601DateFormatter().string(from: Date())
        try await dbWriter.write { db in
            var updates: [String] = ["updated_at = ?"]
            var args: [DatabaseValue] = [now.databaseValue]

            if let title {
                updates.append("title = ?")
                args.append(title.databaseValue)
            }
            if let planData {
                updates.append("plan_data = ?")
                args.append(planData.databaseValue)
            }
            args.append(id.databaseValue)

            let sql = "UPDATE saved_plans SET \(updates.joined(separator: ", ")) WHERE id = ?"
            try db.execute(sql: sql, arguments: StatementArguments(args))
        }
    }

    /// Delete a plan
    func deletePlan(id: Int64) async throws {
        try await dbWriter.write { db in
            try db.execute(sql: "DELETE FROM saved_plans WHERE id = ?", arguments: [id])
        }
    }

    /// Get all saved plans
    func getSavedPlans() async throws -> [SavedPlan] {
        try await dbWriter.read { db in
            try SavedPlan.order(Column("updated_at").desc).fetchAll(db)
        }
    }

    /// Get a specific plan
    func getPlan(id: Int64) async throws -> SavedPlan? {
        try await dbWriter.read { db in
            try SavedPlan.fetchOne(db, key: id)
        }
    }
}

// MARK: - Route Progress

extension UserDatabase {
    /// Mark a step as completed
    func completeStep(planId: Int64, stepIndex: Int) async throws {
        guard planId > 0 else {
            throw UserDatabaseError.invalidInput("planId must be greater than zero")
        }
        guard stepIndex >= 0 else {
            throw UserDatabaseError.invalidInput("stepIndex must be non-negative")
        }

        let now = ISO8601DateFormatter().string(from: Date())
        try await dbWriter.write { db in
            try db.execute(sql: """
                INSERT INTO route_progress (plan_id, step_index, completed_at, skipped)
                VALUES (?, ?, ?, 0)
                ON CONFLICT(plan_id, step_index) DO UPDATE
                SET completed_at = excluded.completed_at,
                    skipped = excluded.skipped
            """, arguments: [planId, stepIndex, now])
        }
    }

    /// Mark a step as skipped
    func skipStep(planId: Int64, stepIndex: Int) async throws {
        guard planId > 0 else {
            throw UserDatabaseError.invalidInput("planId must be greater than zero")
        }
        guard stepIndex >= 0 else {
            throw UserDatabaseError.invalidInput("stepIndex must be non-negative")
        }

        try await dbWriter.write { db in
            try db.execute(sql: """
                INSERT INTO route_progress (plan_id, step_index, completed_at, skipped)
                VALUES (?, ?, NULL, 1)
                ON CONFLICT(plan_id, step_index) DO UPDATE
                SET completed_at = excluded.completed_at,
                    skipped = excluded.skipped
            """, arguments: [planId, stepIndex])
        }
    }

    /// Get progress for a plan
    func getRouteProgress(planId: Int64) async throws -> [RouteProgress] {
        guard planId > 0 else { return [] }

        try await dbWriter.read { db in
            try RouteProgress
                .filter(Column("plan_id") == planId)
                .order(Column("step_index"))
                .fetchAll(db)
        }
    }

    /// Reset progress for a plan
    func resetRouteProgress(planId: Int64) async throws {
        guard planId > 0 else { return }

        try await dbWriter.write { db in
            try db.execute(sql: "DELETE FROM route_progress WHERE plan_id = ?", arguments: [planId])
        }
    }
}

// MARK: - Errors

enum UserDatabaseError: LocalizedError {
    case encodingFailed
    case decodingFailed
    case planNotFound
    case invalidInput(String)

    var errorDescription: String? {
        switch self {
        case .encodingFailed:
            return "Failed to encode value for storage"
        case .decodingFailed:
            return "Failed to decode stored value"
        case .planNotFound:
            return "Saved plan not found"
        case .invalidInput(let message):
            return "Invalid user database input: \(message)"
        }
    }
}

// MARK: - Supporting Records

/// Favorite item record
struct Favorite: Codable, FetchableRecord, PersistableRecord, Sendable {
    var id: Int64?
    var contentType: String
    var contentId: String
    var createdAt: String

    static let databaseTableName = "favorites"

    enum CodingKeys: String, CodingKey {
        case id
        case contentType = "content_type"
        case contentId = "content_id"
        case createdAt = "created_at"
    }
}

/// Recent view record
struct Recent: Codable, FetchableRecord, PersistableRecord, Sendable {
    var id: Int64?
    var contentType: String
    var contentId: String
    var viewedAt: String

    static let databaseTableName = "recents"

    enum CodingKeys: String, CodingKey {
        case id
        case contentType = "content_type"
        case contentId = "content_id"
        case viewedAt = "viewed_at"
    }
}

/// Saved plan record
struct SavedPlan: Codable, FetchableRecord, PersistableRecord, Sendable {
    var id: Int64?
    var title: String
    var planDate: String?
    var planData: String
    var createdAt: String
    var updatedAt: String

    static let databaseTableName = "saved_plans"

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case planDate = "plan_date"
        case planData = "plan_data"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

/// Route progress record
struct RouteProgress: Codable, FetchableRecord, PersistableRecord, Sendable {
    var id: Int64?
    var planId: Int64
    var stepIndex: Int
    var completedAt: String?
    var skipped: Bool

    static let databaseTableName = "route_progress"

    enum CodingKeys: String, CodingKey {
        case id
        case planId = "plan_id"
        case stepIndex = "step_index"
        case completedAt = "completed_at"
        case skipped
    }
}
