package com.marrakechguide.core.database

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DatabaseManager is the central access point for all database operations.
 *
 * It initializes and manages both the content database (read-only curated content)
 * and the user database (read-write user state). Injected via Hilt.
 *
 * ## Usage
 * ```kotlin
 * @Inject lateinit var databaseManager: DatabaseManager
 *
 * // Access content
 * val places = databaseManager.content.placeDao().getAll()
 *
 * // Access user state
 * databaseManager.user.favoritesDao().insert(favorite)
 * ```
 */
@Singleton
class DatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Read-only content database.
     * Contains all curated Marrakech content.
     */
    val content: ContentDatabase by lazy {
        ContentDatabase.getInstance(context).also {
            Log.d(TAG, "Content database initialized")
        }
    }

    /**
     * Read-write user database.
     * Stores user preferences, favorites, recents, and plans.
     */
    val user: UserDatabase by lazy {
        UserDatabase.getInstance(context).also {
            Log.d(TAG, "User database initialized")
        }
    }

    /**
     * Get database statistics for debugging.
     */
    fun getStats(): DatabaseStats {
        val contentPath = context.getDatabasePath("content.db")
        val userPath = context.getDatabasePath("user.db")

        return DatabaseStats(
            contentDatabaseSize = contentPath.length(),
            userDatabaseSize = userPath.length(),
            contentPath = contentPath.absolutePath,
            userPath = userPath.absolutePath
        )
    }

    /**
     * Close all databases.
     * Call this when the app is being destroyed.
     */
    fun closeAll() {
        content.close()
        user.close()
        Log.d(TAG, "All databases closed")
    }

    companion object {
        private const val TAG = "DatabaseManager"
    }
}

/**
 * Database statistics for debugging and monitoring.
 */
data class DatabaseStats(
    val contentDatabaseSize: Long,
    val userDatabaseSize: Long,
    val contentPath: String,
    val userPath: String
) {
    val formattedContentSize: String
        get() = formatBytes(contentDatabaseSize)

    val formattedUserSize: String
        get() = formatBytes(userDatabaseSize)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
