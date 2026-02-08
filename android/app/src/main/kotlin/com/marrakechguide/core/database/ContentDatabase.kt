package com.marrakechguide.core.database

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marrakechguide.core.database.dao.*
import com.marrakechguide.core.database.entity.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * ContentDatabase provides read-only access to the bundled content.db.
 *
 * This database contains all curated Marrakech content: places, price cards,
 * phrases, itineraries, tips, culture articles, activities, and events.
 * It's never modified at runtime - updates come via app updates with a new bundle.
 */
@Database(
    entities = [
        PlaceEntity::class,
        PriceCardEntity::class,
        PhraseEntity::class,
        ItineraryEntity::class,
        TipEntity::class,
        CultureEntity::class,
        ActivityEntity::class,
        EventEntity::class,
        ContentLinkEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun priceCardDao(): PriceCardDao
    abstract fun phraseDao(): PhraseDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun tipDao(): TipDao
    abstract fun cultureDao(): CultureDao
    abstract fun activityDao(): ActivityDao
    abstract fun eventDao(): EventDao
    abstract fun contentLinkDao(): ContentLinkDao

    companion object {
        private const val DATABASE_NAME = "content.db"
        private const val SEED_PATH = "seed/content.db"
        private const val SEEDED_VERSION_KEY = "content_db_seeded_version_code"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        @Volatile
        private var INSTANCE: ContentDatabase? = null

        /**
         * Get the singleton instance of ContentDatabase.
         * Copies from assets on first run and on app updates.
         */
        fun getInstance(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ContentDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            // Sync from assets on first run or when app is updated with new content
            syncFromAssetsIfNeeded(context, dbFile)

            return Room.databaseBuilder(
                context.applicationContext,
                ContentDatabase::class.java,
                DATABASE_NAME
            )
                // Content DB is read-only and ships pre-built from assets.
                // Updates come via new app bundles; we re-sync on version change.
                .build()
        }

        /**
         * Sync content.db from assets if this is a first run or app update.
         * Mirrors the iOS behavior of tracking bundle version and re-syncing.
         */
        private fun syncFromAssetsIfNeeded(context: Context, dbFile: File) {
            val prefs = context.getSharedPreferences("content_db_prefs", Context.MODE_PRIVATE)
            val currentVersionCode = try {
                getVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
            } catch (e: PackageManager.NameNotFoundException) {
                -1L
            }
            val seededVersionCode = prefs.getLong(SEEDED_VERSION_KEY, -1L)
            val hasExistingDatabase = dbFile.exists()
            val hadUsableDatabase = isUsableSQLiteDatabase(dbFile)

            val needsSync = !hadUsableDatabase || seededVersionCode != currentVersionCode

            if (needsSync) {
                try {
                    copyDatabaseFromAssets(context, dbFile)
                    prefs.edit().putLong(SEEDED_VERSION_KEY, currentVersionCode).apply()
                } catch (e: Exception) {
                    // Keep app usable offline if refresh fails but a prior DB exists.
                    // Matches iOS fallback behavior.
                    val databaseStillUsable = isUsableSQLiteDatabase(dbFile)
                    if (!hasExistingDatabase || !hadUsableDatabase || !databaseStillUsable) {
                        throw e
                    }
                    // Silently continue with existing database
                }
            }
        }

        /**
         * Get version code compatible with API 26+.
         * Uses androidx compat helper to handle API-level differences.
         */
        private fun getVersionCode(packageInfo: PackageInfo): Long {
            return PackageInfoCompat.getLongVersionCode(packageInfo)
        }

        private fun copyDatabaseFromAssets(context: Context, destFile: File) {
            // Ensure parent directory exists
            destFile.parentFile?.mkdirs()

            // Use temp file for atomic replacement
            val tempFile = File(destFile.parentFile, "content.db.tmp")

            try {
                context.assets.open(SEED_PATH).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Replace existing DB atomically when possible, with a safe fallback.
                try {
                    Files.move(
                        tempFile.toPath(),
                        destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tempFile.toPath(),
                        destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }

                // Best-effort cleanup of stale journal files from the previous DB state.
                // Run this before usability checks to avoid stale sidecars making a fresh DB appear invalid.
                cleanupJournalFiles(destFile)

                if (!isUsableSQLiteDatabase(destFile)) {
                    throw IllegalStateException(
                        "Staged content DB is not a valid SQLite file at ${destFile.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to stage content DB at ${destFile.absolutePath}",
                    e
                )
            } finally {
                // Best-effort cleanup for failed or partial staging paths.
                tempFile.delete()
            }
        }

        private fun cleanupJournalFiles(destFile: File) {
            try {
                Files.deleteIfExists(File(destFile.path + "-wal").toPath())
                Files.deleteIfExists(File(destFile.path + "-shm").toPath())
                Files.deleteIfExists(File(destFile.path + "-journal").toPath())
            } catch (_: Exception) {
                // Ignore cleanup failures; the main DB file was already staged.
            }
        }

        private fun isUsableSQLiteDatabase(file: File): Boolean {
            if (!file.exists() || !file.isFile || file.length() < SQLITE_HEADER.size.toLong()) {
                return false
            }

            return if (!hasValidSQLiteHeader(file)) {
                false
            } else {
                canOpenSQLiteDatabase(file)
            }
        }

        private fun hasValidSQLiteHeader(file: File): Boolean {
            return try {
                val headerBytes = ByteArray(SQLITE_HEADER.size)
                FileInputStream(file).use { input ->
                    var totalRead = 0
                    while (totalRead < headerBytes.size) {
                        val bytesRead = input.read(
                            headerBytes,
                            totalRead,
                            headerBytes.size - totalRead
                        )
                        if (bytesRead <= 0) {
                            return false
                        }
                        totalRead += bytesRead
                    }
                }
                headerBytes.contentEquals(SQLITE_HEADER)
            } catch (_: Exception) {
                false
            }
        }

        private fun canOpenSQLiteDatabase(file: File): Boolean {
            return try {
                val isExpectedContentDatabase = SQLiteDatabase.openDatabase(
                    file.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    val hasSchemaVersion = db.rawQuery("PRAGMA schema_version", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                    val hasPlacesTable = db.rawQuery(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='places' LIMIT 1",
                        null
                    ).use { cursor ->
                        cursor.moveToFirst()
                    }
                    hasSchemaVersion && hasPlacesTable
                }
                isExpectedContentDatabase
            } catch (_: Exception) {
                false
            }
        }
    }
}
