package com.marrakechguide.core.repository

import com.marrakechguide.core.database.UserDatabase
import com.marrakechguide.core.database.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// MARK: - Content Type

/**
 * Types of content that can be favorited or viewed.
 */
enum class ContentType(val value: String) {
    PLACE("place"),
    PRICE_CARD("price_card"),
    PHRASE("phrase"),
    ITINERARY("itinerary"),
    TIP("tip"),
    CULTURE("culture"),
    ACTIVITY("activity"),
    EVENT("event");

    companion object {
        fun fromValue(value: String): ContentType? = entries.find { it.value == value }
    }
}

// MARK: - Setting Keys

/**
 * Keys for user settings stored in user.db.
 */
enum class SettingKey(val value: String) {
    HOME_BASE("home_base"),
    EXCHANGE_RATE("exchange_rate"),
    WIFI_ONLY_DOWNLOADS("wifi_only_downloads"),
    LANGUAGE("language"),
    LAST_CONTENT_VERSION("last_content_version"),
    ONBOARDING_COMPLETE("onboarding_complete"),
    ARRIVAL_MODE_ACTIVE("arrival_mode_active"),
    ARRIVAL_MODE_COMPLETED_AT("arrival_mode_completed_at")
}

// MARK: - User Settings Models

/**
 * User's home base location (e.g., their riad/hotel).
 */
@Serializable
data class HomeBase(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null
)

/**
 * Current exchange rate setting.
 */
@Serializable
data class ExchangeRate(
    val currencyCode: String,  // e.g., "USD", "EUR"
    val rateToMAD: Double,     // How many MAD per 1 unit of currency
    val updatedAt: String
)

// MARK: - FavoritesRepository

/**
 * Repository interface for managing user favorites.
 */
interface FavoritesRepository {
    /** Get all favorites as a Flow */
    fun getFavorites(): Flow<List<FavoriteEntity>>

    /** Get all favorites once */
    suspend fun getFavoritesOnce(): List<FavoriteEntity>

    /** Get favorites of a specific type */
    fun getFavoritesByType(type: ContentType): Flow<List<FavoriteEntity>>

    /** Get favorites of a specific type once */
    suspend fun getFavoritesByTypeOnce(type: ContentType): List<FavoriteEntity>

    /** Check if an item is favorited */
    suspend fun isFavorite(contentType: ContentType, contentId: String): Boolean

    /** Add an item to favorites */
    suspend fun addFavorite(contentType: ContentType, contentId: String)

    /** Remove an item from favorites */
    suspend fun removeFavorite(contentType: ContentType, contentId: String)

    /** Toggle favorite status and return new state */
    suspend fun toggleFavorite(contentType: ContentType, contentId: String): Boolean

    /** Clear all favorites */
    suspend fun clearFavorites()
}

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val userDb: UserDatabase
) : FavoritesRepository {

    override fun getFavorites(): Flow<List<FavoriteEntity>> {
        return userDb.favoritesDao().getAll()
    }

    override suspend fun getFavoritesOnce(): List<FavoriteEntity> {
        return userDb.favoritesDao().getAllOnce()
    }

    override fun getFavoritesByType(type: ContentType): Flow<List<FavoriteEntity>> {
        return userDb.favoritesDao().getByType(type.value)
    }

    override suspend fun getFavoritesByTypeOnce(type: ContentType): List<FavoriteEntity> {
        return userDb.favoritesDao().getByTypeOnce(type.value)
    }

    override suspend fun isFavorite(contentType: ContentType, contentId: String): Boolean {
        return userDb.favoritesDao().isFavorite(contentType.value, contentId) > 0
    }

    override suspend fun addFavorite(contentType: ContentType, contentId: String) {
        val entity = FavoriteEntity(
            contentType = contentType.value,
            contentId = contentId,
            createdAt = Instant.now().toString()
        )
        userDb.favoritesDao().insert(entity)
    }

    override suspend fun removeFavorite(contentType: ContentType, contentId: String) {
        userDb.favoritesDao().delete(contentType.value, contentId)
    }

    override suspend fun toggleFavorite(contentType: ContentType, contentId: String): Boolean {
        val isFav = isFavorite(contentType, contentId)
        if (isFav) {
            removeFavorite(contentType, contentId)
            return false
        } else {
            addFavorite(contentType, contentId)
            return true
        }
    }

    override suspend fun clearFavorites() {
        userDb.favoritesDao().deleteAll()
    }
}

// MARK: - RecentsRepository

/**
 * Repository interface for managing recently viewed items.
 */
interface RecentsRepository {
    /** Get recent items as a Flow */
    fun getRecents(limit: Int = 20): Flow<List<RecentEntity>>

    /** Get recent items once */
    suspend fun getRecentsOnce(limit: Int = 20): List<RecentEntity>

    /** Get recent items of a specific type */
    fun getRecentsByType(type: ContentType, limit: Int = 20): Flow<List<RecentEntity>>

    /** Get recent items of a specific type once */
    suspend fun getRecentsByTypeOnce(type: ContentType, limit: Int = 20): List<RecentEntity>

    /** Record a view of an item */
    suspend fun recordView(contentType: ContentType, contentId: String)

    /** Clear all recent views */
    suspend fun clearRecents()
}

@Singleton
class RecentsRepositoryImpl @Inject constructor(
    private val userDb: UserDatabase
) : RecentsRepository {

    override fun getRecents(limit: Int): Flow<List<RecentEntity>> {
        return userDb.recentsDao().getRecent(limit)
    }

    override suspend fun getRecentsOnce(limit: Int): List<RecentEntity> {
        return userDb.recentsDao().getRecentOnce(limit)
    }

    override fun getRecentsByType(type: ContentType, limit: Int): Flow<List<RecentEntity>> {
        return userDb.recentsDao().getRecentByType(type.value, limit)
    }

    override suspend fun getRecentsByTypeOnce(type: ContentType, limit: Int): List<RecentEntity> {
        return userDb.recentsDao().getRecentByTypeOnce(type.value, limit)
    }

    override suspend fun recordView(contentType: ContentType, contentId: String) {
        // Remove existing entry for this item
        userDb.recentsDao().deleteExisting(contentType.value, contentId)

        // Add new entry
        val entity = RecentEntity(
            contentType = contentType.value,
            contentId = contentId,
            viewedAt = Instant.now().toString()
        )
        userDb.recentsDao().insert(entity)

        // Trim to limit
        userDb.recentsDao().trimToLimit(100)
    }

    override suspend fun clearRecents() {
        userDb.recentsDao().deleteAll()
    }
}

// MARK: - UserSettingsRepository

/**
 * Repository interface for managing user settings.
 */
interface UserSettingsRepository {
    /** Get a setting value */
    suspend fun <T> getSetting(key: SettingKey, deserialize: (String) -> T?): T?

    /** Set a setting value */
    suspend fun <T> setSetting(key: SettingKey, value: T, serialize: (T) -> String)

    /** Delete a setting */
    suspend fun deleteSetting(key: SettingKey)

    /** Get the user's home base */
    suspend fun getHomeBase(): HomeBase?

    /** Set the user's home base */
    suspend fun setHomeBase(homeBase: HomeBase)

    /** Get the current exchange rate */
    suspend fun getExchangeRate(): ExchangeRate?

    /** Set the current exchange rate */
    suspend fun setExchangeRate(rate: ExchangeRate)

    /** Check if onboarding is complete */
    suspend fun isOnboardingComplete(): Boolean

    /** Mark onboarding as complete */
    suspend fun setOnboardingComplete(complete: Boolean)

    /** Check whether arrival mode should still be shown. Defaults to true. */
    suspend fun isArrivalModeActive(): Boolean

    /** Set arrival mode active state. */
    suspend fun setArrivalModeActive(active: Boolean)

    /** Get timestamp for when arrival mode was completed. */
    suspend fun getArrivalModeCompletedAt(): String?

    /** Mark arrival mode complete and persist completion timestamp. */
    suspend fun markArrivalModeCompleted(iso8601Timestamp: String)

    /** Reset arrival mode so it can be shown again. */
    suspend fun resetArrivalMode()
}

@Singleton
class UserSettingsRepositoryImpl @Inject constructor(
    private val userDb: UserDatabase
) : UserSettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun <T> getSetting(key: SettingKey, deserialize: (String) -> T?): T? {
        val value = userDb.userSettingsDao().getValue(key.value) ?: return null
        return deserialize(value)
    }

    override suspend fun <T> setSetting(key: SettingKey, value: T, serialize: (T) -> String) {
        val entity = UserSettingEntity(
            key = key.value,
            value = serialize(value),
            updatedAt = Instant.now().toString()
        )
        userDb.userSettingsDao().upsert(entity)
    }

    override suspend fun deleteSetting(key: SettingKey) {
        userDb.userSettingsDao().delete(key.value)
    }

    override suspend fun getHomeBase(): HomeBase? {
        return getSetting(SettingKey.HOME_BASE) { jsonString ->
            try {
                json.decodeFromString<HomeBase>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setHomeBase(homeBase: HomeBase) {
        setSetting(SettingKey.HOME_BASE, homeBase) { json.encodeToString(it) }
    }

    override suspend fun getExchangeRate(): ExchangeRate? {
        return getSetting(SettingKey.EXCHANGE_RATE) { jsonString ->
            try {
                json.decodeFromString<ExchangeRate>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setExchangeRate(rate: ExchangeRate) {
        setSetting(SettingKey.EXCHANGE_RATE, rate) { json.encodeToString(it) }
    }

    override suspend fun isOnboardingComplete(): Boolean {
        return getSetting(SettingKey.ONBOARDING_COMPLETE) { it.toBooleanStrictOrNull() } ?: false
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        setSetting(SettingKey.ONBOARDING_COMPLETE, complete) { it.toString() }
    }

    override suspend fun isArrivalModeActive(): Boolean {
        return getSetting(SettingKey.ARRIVAL_MODE_ACTIVE) { it.toBooleanStrictOrNull() } ?: true
    }

    override suspend fun setArrivalModeActive(active: Boolean) {
        setSetting(SettingKey.ARRIVAL_MODE_ACTIVE, active) { it.toString() }
    }

    override suspend fun getArrivalModeCompletedAt(): String? {
        return getSetting(SettingKey.ARRIVAL_MODE_COMPLETED_AT) { it }
    }

    override suspend fun markArrivalModeCompleted(iso8601Timestamp: String) {
        setArrivalModeActive(false)
        setSetting(SettingKey.ARRIVAL_MODE_COMPLETED_AT, iso8601Timestamp) { it }
    }

    override suspend fun resetArrivalMode() {
        setArrivalModeActive(true)
        deleteSetting(SettingKey.ARRIVAL_MODE_COMPLETED_AT)
    }
}

// MARK: - SavedPlansRepository

/**
 * Repository interface for managing saved My Day plans.
 */
interface SavedPlansRepository {
    /** Get all saved plans as a Flow */
    fun getSavedPlans(): Flow<List<SavedPlanEntity>>

    /** Get all saved plans once */
    suspend fun getSavedPlansOnce(): List<SavedPlanEntity>

    /** Get a specific plan by ID */
    suspend fun getPlan(id: Long): SavedPlanEntity?

    /** Get a plan as a Flow */
    fun getPlanFlow(id: Long): Flow<SavedPlanEntity?>

    /** Save a new plan */
    suspend fun savePlan(title: String, planDate: String?, planData: String): Long

    /** Update an existing plan */
    suspend fun updatePlan(plan: SavedPlanEntity)

    /** Delete a plan */
    suspend fun deletePlan(id: Long)

    /** Get progress for a plan */
    fun getProgress(planId: Long): Flow<List<RouteProgressEntity>>

    /** Get progress for a plan once */
    suspend fun getProgressOnce(planId: Long): List<RouteProgressEntity>

    /** Mark a step as completed */
    suspend fun completeStep(planId: Long, stepIndex: Int)

    /** Skip a step */
    suspend fun skipStep(planId: Long, stepIndex: Int)

    /** Reset progress for a plan */
    suspend fun resetProgress(planId: Long)
}

@Singleton
class SavedPlansRepositoryImpl @Inject constructor(
    private val userDb: UserDatabase
) : SavedPlansRepository {

    override fun getSavedPlans(): Flow<List<SavedPlanEntity>> {
        return userDb.savedPlansDao().getAll()
    }

    override suspend fun getSavedPlansOnce(): List<SavedPlanEntity> {
        return userDb.savedPlansDao().getAllOnce()
    }

    override suspend fun getPlan(id: Long): SavedPlanEntity? {
        return userDb.savedPlansDao().getById(id)
    }

    override fun getPlanFlow(id: Long): Flow<SavedPlanEntity?> {
        return userDb.savedPlansDao().getByIdFlow(id)
    }

    override suspend fun savePlan(title: String, planDate: String?, planData: String): Long {
        val now = Instant.now().toString()
        val entity = SavedPlanEntity(
            title = title,
            planDate = planDate,
            planData = planData,
            createdAt = now,
            updatedAt = now
        )
        return userDb.savedPlansDao().insert(entity)
    }

    override suspend fun updatePlan(plan: SavedPlanEntity) {
        val updated = plan.copy(updatedAt = Instant.now().toString())
        userDb.savedPlansDao().update(updated)
    }

    override suspend fun deletePlan(id: Long) {
        userDb.savedPlansDao().delete(id)
    }

    override fun getProgress(planId: Long): Flow<List<RouteProgressEntity>> {
        if (planId <= 0) return flowOf(emptyList())
        return userDb.routeProgressDao().getProgressForPlan(planId)
    }

    override suspend fun getProgressOnce(planId: Long): List<RouteProgressEntity> {
        if (planId <= 0) return emptyList()
        return userDb.routeProgressDao().getProgressForPlanOnce(planId)
    }

    override suspend fun completeStep(planId: Long, stepIndex: Int) {
        if (planId <= 0 || stepIndex < 0) return

        val entity = RouteProgressEntity(
            planId = planId,
            stepIndex = stepIndex,
            completedAt = Instant.now().toString(),
            skipped = false
        )
        userDb.routeProgressDao().insert(entity)
    }

    override suspend fun skipStep(planId: Long, stepIndex: Int) {
        if (planId <= 0 || stepIndex < 0) return

        val entity = RouteProgressEntity(
            planId = planId,
            stepIndex = stepIndex,
            completedAt = null,
            skipped = true
        )
        userDb.routeProgressDao().insert(entity)
    }

    override suspend fun resetProgress(planId: Long) {
        if (planId <= 0) return
        userDb.routeProgressDao().deleteForPlan(planId)
    }
}
