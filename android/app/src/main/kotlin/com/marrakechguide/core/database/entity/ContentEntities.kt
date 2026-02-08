package com.marrakechguide.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Place entity - a curated place in Marrakech.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aliases: String? = null,
    @ColumnInfo(name = "region_id") val regionId: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "short_description") val shortDescription: String? = null,
    @ColumnInfo(name = "long_description") val longDescription: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: String? = null,
    val status: String? = null,
    val confidence: String? = null,
    @ColumnInfo(name = "tourist_trap_level") val touristTrapLevel: String? = null,
    @ColumnInfo(name = "why_recommended") val whyRecommended: String? = null,
    val neighborhood: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @ColumnInfo(name = "hours_text") val hoursText: String? = null,
    @ColumnInfo(name = "hours_weekly") val hoursWeekly: String? = null,
    @ColumnInfo(name = "hours_verified_at") val hoursVerifiedAt: String? = null,
    @ColumnInfo(name = "fees_min_mad") val feesMinMad: Int? = null,
    @ColumnInfo(name = "fees_max_mad") val feesMaxMad: Int? = null,
    @ColumnInfo(name = "expected_cost_min_mad") val expectedCostMinMad: Int? = null,
    @ColumnInfo(name = "expected_cost_max_mad") val expectedCostMaxMad: Int? = null,
    @ColumnInfo(name = "visit_min_minutes") val visitMinMinutes: Int? = null,
    @ColumnInfo(name = "visit_max_minutes") val visitMaxMinutes: Int? = null,
    @ColumnInfo(name = "best_time_to_go") val bestTimeToGo: String? = null,
    @ColumnInfo(name = "best_time_windows") val bestTimeWindows: String? = null,
    val tags: String? = null,
    @ColumnInfo(name = "local_tips") val localTips: String? = null,
    @ColumnInfo(name = "scam_warnings") val scamWarnings: String? = null,
    @ColumnInfo(name = "do_and_dont") val doAndDont: String? = null,
    val images: String? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * PriceCard entity - a price reference card for common goods/services.
 */
@Entity(tableName = "price_cards")
data class PriceCardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String? = null,
    val unit: String? = null,
    val volatility: String? = null,
    val confidence: String? = null,
    @ColumnInfo(name = "expected_cost_min_mad") val expectedCostMinMad: Int,
    @ColumnInfo(name = "expected_cost_max_mad") val expectedCostMaxMad: Int,
    @ColumnInfo(name = "expected_cost_notes") val expectedCostNotes: String? = null,
    @ColumnInfo(name = "expected_cost_updated_at") val expectedCostUpdatedAt: String? = null,
    @ColumnInfo(name = "provenance_note") val provenanceNote: String? = null,
    @ColumnInfo(name = "what_influences_price") val whatInfluencesPrice: String? = null,
    @ColumnInfo(name = "inclusions_checklist") val inclusionsChecklist: String? = null,
    @ColumnInfo(name = "negotiation_scripts") val negotiationScripts: String? = null,
    @ColumnInfo(name = "red_flags") val redFlags: String? = null,
    @ColumnInfo(name = "what_to_do_instead") val whatToDoInstead: String? = null,
    @ColumnInfo(name = "context_modifiers") val contextModifiers: String? = null,
    @ColumnInfo(name = "fairness_low_multiplier") val fairnessLowMultiplier: Double? = null,
    @ColumnInfo(name = "fairness_high_multiplier") val fairnessHighMultiplier: Double? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * Phrase entity - a Darija phrase for the phrasebook.
 */
@Entity(tableName = "phrases")
data class PhraseEntity(
    @PrimaryKey val id: String,
    val category: String? = null,
    val arabic: String? = null,
    val latin: String,
    val english: String,
    val audio: String? = null,
    @ColumnInfo(name = "verification_status") val verificationStatus: String? = null
)

/**
 * Itinerary entity - a curated day itinerary.
 */
@Entity(tableName = "itineraries")
data class ItineraryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: String? = null,
    val style: String? = null,
    val steps: String? = null,  // JSON array
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * Tip entity - a practical travel tip.
 */
@Entity(tableName = "tips")
data class TipEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String? = null,
    val summary: String? = null,
    val actions: String? = null,  // JSON array
    val severity: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    @ColumnInfo(name = "related_place_ids") val relatedPlaceIds: String? = null,
    @ColumnInfo(name = "related_price_card_ids") val relatedPriceCardIds: String? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * Culture entity - a cultural insight article.
 */
@Entity(tableName = "culture")
data class CultureEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "do_list") val doList: String? = null,  // JSON array
    @ColumnInfo(name = "dont_list") val dontList: String? = null,  // JSON array
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * Activity entity - a bookable activity or tour.
 */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String? = null,
    @ColumnInfo(name = "region_id") val regionId: String? = null,
    @ColumnInfo(name = "duration_min_minutes") val durationMinMinutes: Int? = null,
    @ColumnInfo(name = "duration_max_minutes") val durationMaxMinutes: Int? = null,
    @ColumnInfo(name = "pickup_available") val pickupAvailable: Boolean? = null,
    @ColumnInfo(name = "typical_price_min_mad") val typicalPriceMinMad: Int? = null,
    @ColumnInfo(name = "typical_price_max_mad") val typicalPriceMaxMad: Int? = null,
    @ColumnInfo(name = "rating_signal") val ratingSignal: String? = null,
    @ColumnInfo(name = "review_count_signal") val reviewCountSignal: String? = null,
    @ColumnInfo(name = "best_time_windows") val bestTimeWindows: String? = null,
    val tags: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * Event entity - a time-bound event (concert, festival, etc.).
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String? = null,
    val city: String? = null,
    val venue: String? = null,
    @ColumnInfo(name = "start_at") val startAt: String? = null,
    @ColumnInfo(name = "end_at") val endAt: String? = null,
    @ColumnInfo(name = "price_min_mad") val priceMinMad: Int? = null,
    @ColumnInfo(name = "price_max_mad") val priceMaxMad: Int? = null,
    @ColumnInfo(name = "ticket_status") val ticketStatus: String? = null,
    @ColumnInfo(name = "captured_at") val capturedAt: String? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "source_refs") val sourceRefs: String? = null
)

/**
 * ContentLink entity - cross-references between content items.
 */
@Entity(tableName = "content_links")
data class ContentLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_type") val fromType: String,
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_type") val toType: String,
    @ColumnInfo(name = "to_id") val toId: String,
    @ColumnInfo(name = "link_kind") val linkKind: String
)
