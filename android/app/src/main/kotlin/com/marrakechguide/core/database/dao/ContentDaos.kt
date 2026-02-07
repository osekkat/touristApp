package com.marrakechguide.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.marrakechguide.core.database.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Place entity.
 */
@Dao
interface PlaceDao {
    @Query("SELECT * FROM places")
    fun getAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places")
    suspend fun getAllOnce(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): PlaceEntity?

    @Query("SELECT * FROM places WHERE category = :category")
    fun getByCategory(category: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<PlaceEntity>

    @Query("""
        SELECT * FROM places
        WHERE name LIKE '%' || :query || '%'
        OR short_description LIKE '%' || :query || '%'
        LIMIT 50
    """)
    suspend fun search(query: String): List<PlaceEntity>
}

/**
 * DAO for PriceCard entity.
 */
@Dao
interface PriceCardDao {
    @Query("SELECT * FROM price_cards")
    fun getAll(): Flow<List<PriceCardEntity>>

    @Query("SELECT * FROM price_cards")
    suspend fun getAllOnce(): List<PriceCardEntity>

    @Query("SELECT * FROM price_cards WHERE id = :id")
    suspend fun getById(id: String): PriceCardEntity?

    @Query("SELECT * FROM price_cards WHERE category = :category")
    fun getByCategory(category: String): Flow<List<PriceCardEntity>>

    @Query("SELECT * FROM price_cards WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<PriceCardEntity>

    @Query("""
        SELECT * FROM price_cards
        WHERE title LIKE '%' || :query || '%'
        OR category LIKE '%' || :query || '%'
        LIMIT 50
    """)
    suspend fun search(query: String): List<PriceCardEntity>
}

/**
 * DAO for Phrase entity.
 */
@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases")
    fun getAll(): Flow<List<PhraseEntity>>

    @Query("SELECT * FROM phrases")
    suspend fun getAllOnce(): List<PhraseEntity>

    @Query("SELECT * FROM phrases WHERE id = :id")
    suspend fun getById(id: String): PhraseEntity?

    @Query("SELECT * FROM phrases WHERE category = :category")
    fun getByCategory(category: String): Flow<List<PhraseEntity>>

    @Query("SELECT * FROM phrases WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<PhraseEntity>

    @Query("""
        SELECT * FROM phrases
        WHERE english LIKE '%' || :query || '%'
        OR latin LIKE '%' || :query || '%'
        OR arabic LIKE '%' || :query || '%'
        LIMIT 50
    """)
    suspend fun search(query: String): List<PhraseEntity>
}

/**
 * DAO for Itinerary entity.
 */
@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itineraries")
    fun getAll(): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itineraries")
    suspend fun getAllOnce(): List<ItineraryEntity>

    @Query("SELECT * FROM itineraries WHERE id = :id")
    suspend fun getById(id: String): ItineraryEntity?

    @Query("SELECT * FROM itineraries WHERE duration = :duration")
    fun getByDuration(duration: String): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itineraries WHERE style = :style")
    fun getByStyle(style: String): Flow<List<ItineraryEntity>>
}

/**
 * DAO for Tip entity.
 */
@Dao
interface TipDao {
    @Query("SELECT * FROM tips")
    fun getAll(): Flow<List<TipEntity>>

    @Query("SELECT * FROM tips")
    suspend fun getAllOnce(): List<TipEntity>

    @Query("SELECT * FROM tips WHERE id = :id")
    suspend fun getById(id: String): TipEntity?

    @Query("SELECT * FROM tips WHERE category = :category")
    fun getByCategory(category: String): Flow<List<TipEntity>>

    @Query("SELECT * FROM tips WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<TipEntity>

    @Query("SELECT * FROM tips WHERE severity = :severity")
    fun getBySeverity(severity: String): Flow<List<TipEntity>>

    @Query("""
        SELECT * FROM tips
        WHERE title LIKE '%' || :query || '%'
        OR summary LIKE '%' || :query || '%'
        LIMIT 50
    """)
    suspend fun search(query: String): List<TipEntity>
}

/**
 * DAO for Culture entity.
 */
@Dao
interface CultureDao {
    @Query("SELECT * FROM culture")
    fun getAll(): Flow<List<CultureEntity>>

    @Query("SELECT * FROM culture")
    suspend fun getAllOnce(): List<CultureEntity>

    @Query("SELECT * FROM culture WHERE id = :id")
    suspend fun getById(id: String): CultureEntity?

    @Query("SELECT * FROM culture WHERE category = :category")
    fun getByCategory(category: String): Flow<List<CultureEntity>>
}

/**
 * DAO for Activity entity.
 */
@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities")
    fun getAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities")
    suspend fun getAllOnce(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE category = :category")
    fun getByCategory(category: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<ActivityEntity>
}

/**
 * DAO for Event entity.
 */
@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun getAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAllOnce(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE start_at >= :now ORDER BY start_at")
    fun getUpcoming(now: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE start_at >= :now ORDER BY start_at")
    suspend fun getUpcomingOnce(now: String): List<EventEntity>
}

/**
 * DAO for ContentLink entity.
 */
@Dao
interface ContentLinkDao {
    @Query("""
        SELECT * FROM content_links
        WHERE from_type = :fromType AND from_id = :fromId
    """)
    suspend fun getLinksFrom(fromType: String, fromId: String): List<ContentLinkEntity>

    @Query("""
        SELECT * FROM content_links
        WHERE to_type = :toType AND to_id = :toId
    """)
    suspend fun getLinksTo(toType: String, toId: String): List<ContentLinkEntity>
}
