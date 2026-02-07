package com.marrakechguide.core.repository

import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.database.entity.PriceCardEntity
import com.marrakechguide.core.model.ContextModifier
import com.marrakechguide.core.model.NegotiationScript
import com.marrakechguide.core.model.PriceCardDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for price card data access.
 */
interface PriceCardRepository {
    /** Get all price cards as a Flow */
    fun getAllPriceCards(): Flow<List<PriceCardDetail>>

    /** Get all price cards once (suspending) */
    suspend fun getAllPriceCardsOnce(): List<PriceCardDetail>

    /** Get a single price card by ID */
    suspend fun getPriceCard(id: String): PriceCardDetail?

    /** Get price cards by category as a Flow */
    fun getPriceCardsByCategory(category: String): Flow<List<PriceCardDetail>>

    /** Get price cards by category once (suspending) */
    suspend fun getPriceCardsByCategoryOnce(category: String): List<PriceCardDetail>

    /** Search price cards */
    suspend fun searchPriceCards(query: String, limit: Int = 20): List<PriceCardDetail>

    /** Get context modifiers for a price card */
    suspend fun getModifiers(cardId: String): List<ContextModifier>

    /** Get negotiation scripts for a price card */
    suspend fun getScripts(cardId: String): List<NegotiationScript>
}

/**
 * Default implementation of PriceCardRepository using Room.
 */
@Singleton
class PriceCardRepositoryImpl @Inject constructor(
    private val contentDb: ContentDatabase
) : PriceCardRepository {

    override fun getAllPriceCards(): Flow<List<PriceCardDetail>> {
        return contentDb.priceCardDao().getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllPriceCardsOnce(): List<PriceCardDetail> {
        return contentDb.priceCardDao().getAllOnce().map { it.toDomainModel() }
    }

    override suspend fun getPriceCard(id: String): PriceCardDetail? {
        return contentDb.priceCardDao().getById(id)?.toDomainModel()
    }

    override fun getPriceCardsByCategory(category: String): Flow<List<PriceCardDetail>> {
        return contentDb.priceCardDao().getByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getPriceCardsByCategoryOnce(category: String): List<PriceCardDetail> {
        return contentDb.priceCardDao().getByCategoryOnce(category).map { it.toDomainModel() }
    }

    override suspend fun searchPriceCards(query: String, limit: Int): List<PriceCardDetail> {
        if (query.isBlank()) return emptyList()
        return contentDb.priceCardDao().search(query).take(limit).map { it.toDomainModel() }
    }

    override suspend fun getModifiers(cardId: String): List<ContextModifier> {
        return getPriceCard(cardId)?.contextModifiers ?: emptyList()
    }

    override suspend fun getScripts(cardId: String): List<NegotiationScript> {
        return getPriceCard(cardId)?.negotiationScripts ?: emptyList()
    }
}

// MARK: - Entity to Domain Model Mapper

private fun PriceCardEntity.toDomainModel(): PriceCardDetail {
    return PriceCardDetail(
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
        provenanceNote = provenanceNote,
        whatInfluencesPrice = parseJsonStringList(whatInfluencesPrice),
        inclusionsChecklist = parseJsonStringList(inclusionsChecklist),
        negotiationScripts = parseNegotiationScripts(negotiationScripts),
        redFlags = parseJsonStringList(redFlags),
        whatToDoInstead = parseJsonStringList(whatToDoInstead),
        contextModifiers = parseContextModifiers(contextModifiers),
        fairnessLowMultiplier = fairnessLowMultiplier,
        fairnessHighMultiplier = fairnessHighMultiplier
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

private fun parseContextModifiers(json: String?): List<ContextModifier> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            ContextModifier(
                id = obj.getString("id"),
                label = obj.getString("label"),
                factorMin = obj.optDouble("factor_min").takeIf { !it.isNaN() },
                factorMax = obj.optDouble("factor_max").takeIf { !it.isNaN() },
                addMin = obj.optDouble("add_min").takeIf { !it.isNaN() },
                addMax = obj.optDouble("add_max").takeIf { !it.isNaN() },
                notes = obj.optString("notes").takeIf { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseNegotiationScripts(json: String?): List<NegotiationScript> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            NegotiationScript(
                darijaLatin = obj.getString("darija_latin"),
                english = obj.getString("english"),
                darijaArabic = obj.optString("darija_arabic").takeIf { it.isNotBlank() },
                french = obj.optString("french").takeIf { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
