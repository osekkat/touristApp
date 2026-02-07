package com.marrakechguide.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
                .fallbackToDestructiveMigration(false)
                .build()
        }
    }
}
