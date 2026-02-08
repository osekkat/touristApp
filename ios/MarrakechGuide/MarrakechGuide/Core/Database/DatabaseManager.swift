import Foundation
import GRDB
import os

/// DatabaseManager is the central access point for all database operations.
///
/// It initializes and manages both the content database (read-only curated content)
/// and the user database (read-write user state). Use this as a singleton or inject
/// via dependency injection.
///
/// ## Usage
/// ```swift
/// // Initialize on app launch
/// let manager = try await DatabaseManager.shared
///
/// // Access content
/// let places = try await manager.content.fetchAllPlaces()
///
/// // Access user state
/// try await manager.user.addFavorite(contentType: "place", contentId: "djemaa-el-fna")
/// ```
@MainActor
final class DatabaseManager {
    private static let logger = Logger(subsystem: "com.marrakechguide.app", category: "database")
    // MARK: - Shared Instance

    private struct InFlightInitialization {
        let id: UUID
        let task: Task<DatabaseManager, Error>
    }

    /// The shared database manager instance
    private static var _shared: DatabaseManager?
    private static var _sharedTask: InFlightInitialization?

    /// Access the shared database manager
    /// - Throws: Error if databases fail to initialize
    static var shared: DatabaseManager {
        get async throws {
            if let existing = _shared {
                return existing
            }
            if let inFlight = _sharedTask {
                return try await resolveShared(from: inFlight)
            }

            let task = Task<DatabaseManager, Error> { @MainActor in
                try await DatabaseManager()
            }
            let inFlight = InFlightInitialization(id: UUID(), task: task)
            _sharedTask = inFlight

            return try await resolveShared(from: inFlight)
        }
    }

    private static func resolveShared(from inFlight: InFlightInitialization) async throws -> DatabaseManager {
        do {
            let manager = try await inFlight.task.value
            _shared = manager
            if _sharedTask?.id == inFlight.id {
                _sharedTask = nil
            }
            return manager
        } catch {
            // Do not clear in-flight task when the current caller is cancelled
            // but initialization is still running for other callers.
            if Task.isCancelled, error is CancellationError, !inFlight.task.isCancelled {
                throw error
            }
            if _sharedTask?.id == inFlight.id {
                _sharedTask = nil
            }
            throw error
        }
    }

    // MARK: - Databases

    /// Read-only content database
    let content: ContentDatabase

    /// Read-write user database
    let user: UserDatabase

    // MARK: - Initialization

    /// Initialize the database manager
    /// - Throws: Error if databases cannot be opened or migrated
    init() async throws {
        let databases = try await Task.detached(priority: .userInitiated) { () throws -> (ContentDatabase, UserDatabase) in
            let content = try ContentDatabase()
            let user = try UserDatabase()
            return (content, user)
        }.value
        content = databases.0
        user = databases.1

        // Log successful initialization
        #if DEBUG
        Self.logger.debug("Content database path: \(self.content.path, privacy: .public)")
        Self.logger.debug("User database path: \(self.user.path, privacy: .public)")
        #endif
    }

    /// Initialize with custom paths (for testing)
    init(contentPath: String, userPath: String) async throws {
        let databases = try await Task.detached(priority: .userInitiated) { () throws -> (ContentDatabase, UserDatabase) in
            let content = try ContentDatabase(path: contentPath)
            let user = try UserDatabase(path: userPath)
            return (content, user)
        }.value
        content = databases.0
        user = databases.1
    }

    // MARK: - Content Swap

    /// Swap the content database with a new version
    ///
    /// This is used when the app updates to a new content bundle. The swap is atomic:
    /// 1. Close current content database
    /// 2. Replace file
    /// 3. Reopen database
    ///
    /// - Parameter newContentPath: Path to the new content.db file
    /// - Throws: Error if swap fails
    func swapContentDatabase(from newContentPath: String) async throws {
        // This is a placeholder for Phase 2 content updates
        // For now, content is only updated via app updates
        throw DatabaseManagerError.contentSwapNotSupported
    }

    // MARK: - Maintenance

    /// Perform database maintenance (vacuum, integrity check)
    func performMaintenance() async throws {
        // Check integrity of user database
        try await user.dbWriter.read { db in
            let result = try String.fetchOne(db, sql: "PRAGMA integrity_check")
            guard result == "ok" else {
                throw DatabaseManagerError.integrityCheckFailed(result ?? "unknown error")
            }
        }

        // Vacuum user database to reclaim space
        try await user.dbWriter.writeWithoutTransaction { db in
            try db.execute(sql: "VACUUM")
        }
    }

    /// Get database statistics for debugging
    func getStats() async throws -> DatabaseStats {
        let contentPath = content.path
        let userPath = user.path
        return try await Task.detached(priority: .utility) { () throws -> DatabaseStats in
            let contentStats = try fileStats(path: contentPath)
            let userStats = try fileStats(path: userPath)
            return DatabaseStats(
                contentDatabaseSize: contentStats.size,
                userDatabaseSize: userStats.size,
                contentPath: contentPath,
                userPath: userPath
            )
        }.value
    }
}

private func fileStats(path: String) throws -> (size: Int64, modified: Date?) {
    let fileManager = FileManager.default
    let attrs = try fileManager.attributesOfItem(atPath: path)
    let size = attrs[.size] as? Int64 ?? 0
    let modified = attrs[.modificationDate] as? Date
    return (size, modified)
}

// MARK: - Errors

enum DatabaseManagerError: LocalizedError {
    case contentSwapNotSupported
    case integrityCheckFailed(String)

    var errorDescription: String? {
        switch self {
        case .contentSwapNotSupported:
            return "Content database swap is not yet supported"
        case .integrityCheckFailed(let detail):
            return "Database integrity check failed: \(detail)"
        }
    }
}

// MARK: - Stats

/// Database statistics for debugging and monitoring
struct DatabaseStats: Sendable {
    let contentDatabaseSize: Int64
    let userDatabaseSize: Int64
    let contentPath: String
    let userPath: String

    var formattedContentSize: String {
        ByteCountFormatter.string(fromByteCount: contentDatabaseSize, countStyle: .file)
    }

    var formattedUserSize: String {
        ByteCountFormatter.string(fromByteCount: userDatabaseSize, countStyle: .file)
    }
}
