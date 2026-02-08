package com.marrakechguide.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marrakechguide.core.database.dao.*
import com.marrakechguide.core.database.entity.*

/**
 * UserDatabase provides read-write access to user state.
 *
 * This database stores user preferences, favorites, recent views, saved plans,
 * and other mutable state. It persists across app restarts.
 */
@Database(
    entities = [
        UserSettingEntity::class,
        FavoriteEntity::class,
        RecentEntity::class,
        SavedPlanEntity::class,
        RouteProgressEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun recentsDao(): RecentsDao
    abstract fun savedPlansDao(): SavedPlansDao
    abstract fun routeProgressDao(): RouteProgressDao

    companion object {
        private const val DATABASE_NAME = "user.db"

        @Volatile
        private var INSTANCE: UserDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove malformed legacy rows that reference a missing plan.
                db.execSQL(
                    """
                    DELETE FROM route_progress
                    WHERE plan_id IS NULL
                       OR plan_id NOT IN (SELECT id FROM saved_plans)
                    """.trimIndent()
                )

                // Deduplicate before adding uniqueness constraint.
                // Keep the most recent entry for each (plan_id, step_index).
                db.execSQL(
                    """
                    DELETE FROM route_progress
                    WHERE rowid NOT IN (
                        SELECT MAX(rowid)
                        FROM route_progress
                        GROUP BY plan_id, step_index
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_route_progress_plan_id_step_index
                    ON route_progress(plan_id, step_index)
                    """.trimIndent()
                )
            }
        }

        /**
         * Get the singleton instance of UserDatabase.
         * Creates the database on first run.
         */
        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): UserDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UserDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                // User DB stores important user state (favorites, plans, progress).
                // We want to fail on version mismatch rather than silently lose data.
                // Add proper migrations when schema changes.
                .build()
        }
    }
}
