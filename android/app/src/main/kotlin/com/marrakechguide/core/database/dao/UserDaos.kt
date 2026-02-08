package com.marrakechguide.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.marrakechguide.core.database.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for UserSettings.
 */
@Dao
interface UserSettingsDao {
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM user_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): UserSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: UserSettingEntity)

    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM user_settings")
    fun getAllSettings(): Flow<List<UserSettingEntity>>
}

/**
 * DAO for Favorites.
 */
@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY created_at DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY created_at DESC")
    suspend fun getAllOnce(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE content_type = :contentType ORDER BY created_at DESC")
    fun getByType(contentType: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE content_type = :contentType ORDER BY created_at DESC")
    suspend fun getByTypeOnce(contentType: String): List<FavoriteEntity>

    @Query("""
        SELECT COUNT(*) FROM favorites
        WHERE content_type = :contentType AND content_id = :contentId
    """)
    suspend fun isFavorite(contentType: String, contentId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Query("""
        DELETE FROM favorites
        WHERE content_type = :contentType AND content_id = :contentId
    """)
    suspend fun delete(contentType: String, contentId: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

/**
 * DAO for Recents.
 */
@Dao
interface RecentsDao {
    @Query("SELECT * FROM recents ORDER BY viewed_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recents ORDER BY viewed_at DESC LIMIT :limit")
    suspend fun getRecentOnce(limit: Int = 20): List<RecentEntity>

    @Query("""
        SELECT * FROM recents
        WHERE content_type = :contentType
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    fun getRecentByType(contentType: String, limit: Int = 20): Flow<List<RecentEntity>>

    @Query("""
        SELECT * FROM recents
        WHERE content_type = :contentType
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getRecentByTypeOnce(contentType: String, limit: Int = 20): List<RecentEntity>

    @Query("""
        DELETE FROM recents
        WHERE content_type = :contentType AND content_id = :contentId
    """)
    suspend fun deleteExisting(contentType: String, contentId: String)

    @Insert
    suspend fun insert(recent: RecentEntity): Long

    @Query("""
        DELETE FROM recents WHERE id NOT IN (
            SELECT id FROM recents ORDER BY viewed_at DESC LIMIT :limit
        )
    """)
    suspend fun trimToLimit(limit: Int = 100)

    @Query("DELETE FROM recents")
    suspend fun deleteAll()
}

/**
 * DAO for SavedPlans.
 */
@Dao
interface SavedPlansDao {
    @Query("SELECT * FROM saved_plans ORDER BY updated_at DESC")
    fun getAll(): Flow<List<SavedPlanEntity>>

    @Query("SELECT * FROM saved_plans ORDER BY updated_at DESC")
    suspend fun getAllOnce(): List<SavedPlanEntity>

    @Query("SELECT * FROM saved_plans WHERE id = :id")
    suspend fun getById(id: Long): SavedPlanEntity?

    @Query("SELECT * FROM saved_plans WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<SavedPlanEntity?>

    @Insert
    suspend fun insert(plan: SavedPlanEntity): Long

    @Update
    suspend fun update(plan: SavedPlanEntity)

    @Query("DELETE FROM saved_plans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_plans")
    suspend fun deleteAll()
}

/**
 * DAO for RouteProgress.
 */
@Dao
interface RouteProgressDao {
    @Query("SELECT * FROM route_progress WHERE plan_id = :planId ORDER BY step_index")
    fun getProgressForPlan(planId: Long): Flow<List<RouteProgressEntity>>

    @Query("SELECT * FROM route_progress WHERE plan_id = :planId ORDER BY step_index")
    suspend fun getProgressForPlanOnce(planId: Long): List<RouteProgressEntity>

    @Query("""
        SELECT * FROM route_progress
        WHERE plan_id = :planId AND step_index = :stepIndex
    """)
    suspend fun getProgressForStep(planId: Long, stepIndex: Int): RouteProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: RouteProgressEntity): Long

    @Update
    suspend fun update(progress: RouteProgressEntity)

    @Query("DELETE FROM route_progress WHERE plan_id = :planId")
    suspend fun deleteForPlan(planId: Long)

    @Query("DELETE FROM route_progress WHERE plan_id = :planId AND step_index = :stepIndex")
    suspend fun deleteStep(planId: Long, stepIndex: Int)
}
