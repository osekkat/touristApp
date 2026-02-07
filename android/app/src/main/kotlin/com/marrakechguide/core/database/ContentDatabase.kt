package com.marrakechguide.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marrakechguide.core.database.dao.*
import com.marrakechguide.core.database.entity.*
import java.io.File
import java.io.FileOutputStream

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

        @Volatile
        private var INSTANCE: ContentDatabase? = null

        /**
         * Get the singleton instance of ContentDatabase.
         * Copies from assets on first run if needed.
         */
        fun getInstance(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ContentDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            // Copy from assets on first run
            if (!dbFile.exists()) {
                copyDatabaseFromAssets(context, dbFile)
            }

            return Room.databaseBuilder(
                context.applicationContext,
                ContentDatabase::class.java,
                DATABASE_NAME
            )
                // Read-only, no migrations needed
                .fallbackToDestructiveMigration(false)
                .build()
        }

        private fun copyDatabaseFromAssets(context: Context, destFile: File) {
            // Ensure parent directory exists
            destFile.parentFile?.mkdirs()

            context.assets.open(SEED_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
