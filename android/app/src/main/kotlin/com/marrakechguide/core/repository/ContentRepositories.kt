package com.marrakechguide.core.repository

import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.database.entity.*
import com.marrakechguide.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

// MARK: - PhraseRepository

/**
 * Repository interface for phrase (phrasebook) data access.
 */
interface PhraseRepository {
    fun getAllPhrases(): Flow<List<Phrase>>
    suspend fun getAllPhrasesOnce(): List<Phrase>
    suspend fun getPhrase(id: String): Phrase?
    fun getPhrasesByCategory(category: String): Flow<List<Phrase>>
    suspend fun getPhrasesByCategoryOnce(category: String): List<Phrase>
    suspend fun searchPhrases(query: String, limit: Int = 20): List<Phrase>
}

@Singleton
class PhraseRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : PhraseRepository {

    override fun getAllPhrases(): Flow<List<Phrase>> {
        return contentDb.phraseDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllPhrasesOnce(): List<Phrase> {
        return contentDb.phraseDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getPhrase(id: String): Phrase? {
        return contentDb.phraseDao().getById(id)?.toDomainModel()
    }

    override fun getPhrasesByCategory(category: String): Flow<List<Phrase>> {
        return contentDb.phraseDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getPhrasesByCategoryOnce(category: String): List<Phrase> {
        return contentDb.phraseDao().getByCategoryOnce(category).map { it.toDomainModel() }
    }

    override suspend fun searchPhrases(query: String, limit: Int): List<Phrase> {
        if (query.isBlank()) return emptyList()
        return contentDb.phraseDao().search(query).take(limit).map { it.toDomainModel() }
    }
}

// MARK: - ItineraryRepository

/**
 * Repository interface for itinerary data access.
 */
interface ItineraryRepository {
    fun getAllItineraries(): Flow<List<Itinerary>>
    suspend fun getAllItinerariesOnce(): List<Itinerary>
    suspend fun getItinerary(id: String): Itinerary?
    fun getItinerariesByDuration(duration: String): Flow<List<Itinerary>>
    fun getItinerariesByStyle(style: String): Flow<List<Itinerary>>
}

@Singleton
class ItineraryRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : ItineraryRepository {

    override fun getAllItineraries(): Flow<List<Itinerary>> {
        return contentDb.itineraryDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllItinerariesOnce(): List<Itinerary> {
        return contentDb.itineraryDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getItinerary(id: String): Itinerary? {
        return contentDb.itineraryDao().getById(id)?.toDomainModel()
    }

    override fun getItinerariesByDuration(duration: String): Flow<List<Itinerary>> {
        return contentDb.itineraryDao().getByDuration(duration).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getItinerariesByStyle(style: String): Flow<List<Itinerary>> {
        return contentDb.itineraryDao().getByStyle(style).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
}

// MARK: - TipRepository

/**
 * Repository interface for travel tips data access.
 */
interface TipRepository {
    fun getAllTips(): Flow<List<Tip>>
    suspend fun getAllTipsOnce(): List<Tip>
    suspend fun getTip(id: String): Tip?
    fun getTipsByCategory(category: String): Flow<List<Tip>>
    suspend fun getTipsByCategoryOnce(category: String): List<Tip>
    fun getTipsBySeverity(severity: String): Flow<List<Tip>>
    suspend fun getTipsForPlace(placeId: String): List<Tip>
    suspend fun getTipsForPriceCard(priceCardId: String): List<Tip>
    suspend fun searchTips(query: String, limit: Int = 20): List<Tip>
}

@Singleton
class TipRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : TipRepository {

    override fun getAllTips(): Flow<List<Tip>> {
        return contentDb.tipDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllTipsOnce(): List<Tip> {
        return contentDb.tipDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getTip(id: String): Tip? {
        return contentDb.tipDao().getById(id)?.toDomainModel()
    }

    override fun getTipsByCategory(category: String): Flow<List<Tip>> {
        return contentDb.tipDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getTipsByCategoryOnce(category: String): List<Tip> {
        return contentDb.tipDao().getByCategoryOnce(category).map { it.toDomainModel() }
    }

    override fun getTipsBySeverity(severity: String): Flow<List<Tip>> {
        return contentDb.tipDao().getBySeverity(severity).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getTipsForPlace(placeId: String): List<Tip> {
        return getAllTipsOnce().filter { tip ->
            tip.relatedPlaceIds.contains(placeId)
        }
    }

    override suspend fun getTipsForPriceCard(priceCardId: String): List<Tip> {
        return getAllTipsOnce().filter { tip ->
            tip.relatedPriceCardIds.contains(priceCardId)
        }
    }

    override suspend fun searchTips(query: String, limit: Int): List<Tip> {
        if (query.isBlank()) return emptyList()
        return contentDb.tipDao().search(query).take(limit).map { it.toDomainModel() }
    }
}

// MARK: - CultureRepository

/**
 * Repository interface for culture articles data access.
 */
interface CultureRepository {
    fun getAllCultureArticles(): Flow<List<CultureArticle>>
    suspend fun getAllCultureArticlesOnce(): List<CultureArticle>
    suspend fun getCultureArticle(id: String): CultureArticle?
    fun getCultureArticlesByCategory(category: String): Flow<List<CultureArticle>>
}

@Singleton
class CultureRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : CultureRepository {

    override fun getAllCultureArticles(): Flow<List<CultureArticle>> {
        return contentDb.cultureDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllCultureArticlesOnce(): List<CultureArticle> {
        return contentDb.cultureDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getCultureArticle(id: String): CultureArticle? {
        return contentDb.cultureDao().getById(id)?.toDomainModel()
    }

    override fun getCultureArticlesByCategory(category: String): Flow<List<CultureArticle>> {
        return contentDb.cultureDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
}

// MARK: - ActivityRepository

/**
 * Repository interface for activity data access.
 */
interface ActivityRepository {
    fun getAllActivities(): Flow<List<Activity>>
    suspend fun getAllActivitiesOnce(): List<Activity>
    suspend fun getActivity(id: String): Activity?
    fun getActivitiesByCategory(category: String): Flow<List<Activity>>
    suspend fun getActivitiesByCategoryOnce(category: String): List<Activity>
}

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : ActivityRepository {

    override fun getAllActivities(): Flow<List<Activity>> {
        return contentDb.activityDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllActivitiesOnce(): List<Activity> {
        return contentDb.activityDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getActivity(id: String): Activity? {
        return contentDb.activityDao().getById(id)?.toDomainModel()
    }

    override fun getActivitiesByCategory(category: String): Flow<List<Activity>> {
        return contentDb.activityDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getActivitiesByCategoryOnce(category: String): List<Activity> {
        return contentDb.activityDao().getByCategoryOnce(category).map { it.toDomainModel() }
    }
}

// MARK: - EventRepository

/**
 * Repository interface for event data access.
 */
interface EventRepository {
    fun getAllEvents(): Flow<List<Event>>
    suspend fun getAllEventsOnce(): List<Event>
    suspend fun getEvent(id: String): Event?
    fun getUpcomingEvents(): Flow<List<Event>>
    suspend fun getUpcomingEventsOnce(): List<Event>
}

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : EventRepository {

    override fun getAllEvents(): Flow<List<Event>> {
        return contentDb.eventDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllEventsOnce(): List<Event> {
        return contentDb.eventDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getEvent(id: String): Event? {
        return contentDb.eventDao().getById(id)?.toDomainModel()
    }

    override fun getUpcomingEvents(): Flow<List<Event>> {
        val now = java.time.Instant.now().toString()
        return contentDb.eventDao().getUpcoming(now).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getUpcomingEventsOnce(): List<Event> {
        val now = java.time.Instant.now().toString()
        return contentDb.eventDao().getUpcomingOnce(now).map { it.toDomainModel() }
    }
}

// MARK: - Entity to Domain Model Mappers

private fun PhraseEntity.toDomainModel(): Phrase {
    return Phrase(
        id = id,
        category = category,
        arabic = arabic,
        latin = latin,
        english = english,
        audio = audio
    )
}

private fun ItineraryEntity.toDomainModel(): Itinerary {
    return Itinerary(
        id = id,
        title = title,
        duration = duration,
        style = style,
        steps = parseItinerarySteps(steps)
    )
}

private fun TipEntity.toDomainModel(): Tip {
    return Tip(
        id = id,
        title = title,
        category = category,
        summary = summary,
        actions = parseJsonStringList(actions),
        severity = severity,
        updatedAt = updatedAt,
        relatedPlaceIds = parseJsonStringList(relatedPlaceIds),
        relatedPriceCardIds = parseJsonStringList(relatedPriceCardIds)
    )
}

private fun CultureEntity.toDomainModel(): CultureArticle {
    return CultureArticle(
        id = id,
        title = title,
        summary = summary,
        category = category,
        doList = parseJsonStringList(doList),
        dontList = parseJsonStringList(dontList),
        updatedAt = updatedAt
    )
}

private fun ActivityEntity.toDomainModel(): Activity {
    return Activity(
        id = id,
        title = title,
        category = category,
        regionId = regionId,
        durationMinMinutes = durationMinMinutes,
        durationMaxMinutes = durationMaxMinutes,
        pickupAvailable = pickupAvailable,
        typicalPriceMinMad = typicalPriceMinMad,
        typicalPriceMaxMad = typicalPriceMaxMad,
        ratingSignal = ratingSignal,
        reviewCountSignal = reviewCountSignal,
        bestTimeWindows = parseJsonStringList(bestTimeWindows),
        tags = parseJsonStringList(tags),
        notes = notes
    )
}

private fun EventEntity.toDomainModel(): Event {
    return Event(
        id = id,
        title = title,
        category = category,
        city = city,
        venue = venue,
        startAt = startAt,
        endAt = endAt,
        priceMinMad = priceMinMad,
        priceMaxMad = priceMaxMad,
        ticketStatus = ticketStatus,
        sourceUrl = sourceUrl
    )
}

private fun parseJsonStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseItinerarySteps(json: String?): List<ItineraryStep> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            ItineraryStep(
                type = obj.getString("type"),
                placeId = obj.optString("place_id").takeIf { it.isNotBlank() },
                activityId = obj.optString("activity_id").takeIf { it.isNotBlank() },
                estimatedStopMinutes = obj.optInt("estimated_stop_minutes").takeIf { it > 0 },
                routeHint = obj.optString("route_hint").takeIf { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
