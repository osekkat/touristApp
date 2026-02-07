import Foundation
import GRDB

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
    // MARK: - Shared Instance

    /// The shared database manager instance
    private static var _shared: DatabaseManager?

    /// Access the shared database manager
    /// - Throws: Error if databases fail to initialize
    static var shared: DatabaseManager {
        get async throws {
            if let existing = _shared {
                return existing
            }
            let manager = try await DatabaseManager()
            _shared = manager
            return manager
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
        // Initialize content database
        content = try ContentDatabase()

        // Initialize user database
        user = try UserDatabase()

        // Log successful initialization
        #if DEBUG
        print("[DatabaseManager] Content database: \(content.path)")
        print("[DatabaseManager] User database: \(user.path)")
        #endif
    }

    /// Initialize with custom paths (for testing)
    init(contentPath: String, userPath: String) async throws {
        content = try ContentDatabase(path: contentPath)
        user = try UserDatabase(path: userPath)
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
        try await user.dbWriter.write { db in
            let result = try String.fetchOne(db, sql: "PRAGMA integrity_check")
            guard result == "ok" else {
                throw DatabaseManagerError.integrityCheckFailed(result ?? "unknown error")
            }
        }

        // Vacuum user database to reclaim space
        try await user.dbWriter.vacuum()
    }

    /// Get database statistics for debugging
    func getStats() async throws -> DatabaseStats {
        let contentStats = try await getFileStats(path: content.path)
        let userStats = try await getFileStats(path: user.path)

        return DatabaseStats(
            contentDatabaseSize: contentStats.size,
            userDatabaseSize: userStats.size,
            contentPath: content.path,
            userPath: user.path
        )
    }

    private func getFileStats(path: String) async throws -> (size: Int64, modified: Date?) {
        let fileManager = FileManager.default
        let attrs = try fileManager.attributesOfItem(atPath: path)
        let size = attrs[.size] as? Int64 ?? 0
        let modified = attrs[.modificationDate] as? Date
        return (size, modified)
    }
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
struct DatabaseStats {
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
