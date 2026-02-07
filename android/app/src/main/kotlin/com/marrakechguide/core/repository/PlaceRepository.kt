package com.marrakechguide.core.repository

import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.database.entity.PlaceEntity
import com.marrakechguide.core.database.entity.PriceCardEntity
import com.marrakechguide.core.database.entity.PhraseEntity
import com.marrakechguide.core.model.Place
import com.marrakechguide.core.model.PriceCard
import com.marrakechguide.core.model.Phrase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for place data access.
 */
interface PlaceRepository {
    /** Get all places as a Flow */
    fun getAllPlaces(): Flow<List<Place>>

    /** Get all places once (suspending) */
    suspend fun getAllPlacesOnce(): List<Place>

    /** Get a single place by ID */
    suspend fun getPlace(id: String): Place?

    /** Get places by category as a Flow */
    fun getPlacesByCategory(category: String): Flow<List<Place>>

    /** Get places by category once (suspending) */
    suspend fun getPlacesByCategoryOnce(category: String): List<Place>

    /** Get places by region as a Flow */
    fun getPlacesByRegion(region: String): Flow<List<Place>>

    /** Search places using text search */
    suspend fun searchPlaces(query: String, limit: Int = 20): List<Place>

    /** Get related price cards for a place */
    suspend fun getRelatedPriceCards(placeId: String): List<PriceCard>

    /** Get related phrases for a place */
    suspend fun getRelatedPhrases(placeId: String): List<Phrase>
}

/**
 * Default implementation of PlaceRepository using Room.
 */
@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : PlaceRepository {

    override fun getAllPlaces(): Flow<List<Place>> {
        return contentDb.placeDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllPlacesOnce(): List<Place> {
        return contentDb.placeDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getPlace(id: String): Place? {
        return contentDb.placeDao().getById(id)?.toDomainModel()
    }

    override fun getPlacesByCategory(category: String): Flow<List<Place>> {
        return contentDb.placeDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getPlacesByCategoryOnce(category: String): List<Place> {
        return contentDb.placeDao().getByCategoryOnce(category).map { it.toDomainModel() }
    }

    override fun getPlacesByRegion(region: String): Flow<List<Place>> {
        return getAllPlaces().map { places ->
            places.filter { it.regionId == region }
        }
    }

    override suspend fun searchPlaces(query: String, limit: Int): List<Place> {
        if (query.isBlank()) return emptyList()
        return contentDb.placeDao().search(query).take(limit).map { it.toDomainModel() }
    }

    override suspend fun getRelatedPriceCards(placeId: String): List<PriceCard> {
        val links = contentDb.contentLinkDao().getLinksFrom("place", placeId)
        val priceCardIds = links.filter { it.toType == "price_card" }.map { it.toId }

        if (priceCardIds.isEmpty()) return emptyList()

        return priceCardIds.mapNotNull { id ->
            contentDb.priceCardDao().getById(id)?.toDomainModel()
        }
    }

    override suspend fun getRelatedPhrases(placeId: String): List<Phrase> {
        val links = contentDb.contentLinkDao().getLinksFrom("place", placeId)
        val phraseIds = links.filter { it.toType == "phrase" }.map { it.toId }

        if (phraseIds.isEmpty()) return emptyList()

        return phraseIds.mapNotNull { id ->
            contentDb.phraseDao().getById(id)?.toDomainModel()
        }
    }
}

// MARK: - Entity to Domain Model Mappers

private fun PlaceEntity.toDomainModel(): Place {
    return Place(
        id = id,
        name = name,
        aliases = parseJsonStringList(aliases),
        regionId = regionId,
        category = category,
        shortDescription = shortDescription,
        longDescription = longDescription,
        reviewedAt = reviewedAt,
        status = status,
        confidence = confidence,
        touristTrapLevel = touristTrapLevel,
        whyRecommended = parseJsonStringList(whyRecommended),
        neighborhood = neighborhood,
        address = address,
        lat = lat,
        lng = lng,
        hoursText = hoursText,
        hoursWeekly = parseJsonStringList(hoursWeekly),
        hoursVerifiedAt = hoursVerifiedAt,
        feesMinMad = feesMinMad,
        feesMaxMad = feesMaxMad,
        expectedCostMinMad = expectedCostMinMad,
        expectedCostMaxMad = expectedCostMaxMad,
        visitMinMinutes = visitMinMinutes,
        visitMaxMinutes = visitMaxMinutes,
        bestTimeToGo = bestTimeToGo,
        bestTimeWindows = parseJsonStringList(bestTimeWindows),
        tags = parseJsonStringList(tags),
        localTips = parseJsonStringList(localTips),
        scamWarnings = parseJsonStringList(scamWarnings),
        doAndDont = parseJsonStringList(doAndDont),
        images = parseJsonStringList(images)
    )
}

private fun PriceCardEntity.toDomainModel(): PriceCard {
    return PriceCard(
        id = id,
        title = title,
        category = category,
        unit = unit,
        volatility = volatility,
        confidence = confidence,
        expectedCostMinMad = expectedCostMinMad,
        expectedCostMaxMad = expectedCostMaxMad,
        expectedCostNotes = expectedCostNotes,
        expectedCostUpdatedAt = expectedCostUpdatedAt,
        whatInfluencesPrice = parseJsonStringList(whatInfluencesPrice),
        redFlags = parseJsonStringList(redFlags),
        fairnessLowMultiplier = fairnessLowMultiplier,
        fairnessHighMultiplier = fairnessHighMultiplier
    )
}

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

private fun parseJsonStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
