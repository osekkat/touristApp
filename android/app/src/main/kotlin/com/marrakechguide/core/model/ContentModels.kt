package com.marrakechguide.core.model

/**
 * Domain model for a Place.
 * This is the clean domain representation used by ViewModels and UI.
 */
data class Place(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val regionId: String? = null,
    val category: String? = null,
    val shortDescription: String? = null,
    val longDescription: String? = null,
    val reviewedAt: String? = null,
    val status: String? = null,
    val confidence: String? = null,
    val touristTrapLevel: String? = null,
    val whyRecommended: List<String> = emptyList(),
    val neighborhood: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val hoursText: String? = null,
    val hoursWeekly: List<String> = emptyList(),
    val hoursVerifiedAt: String? = null,
    val feesMinMad: Int? = null,
    val feesMaxMad: Int? = null,
    val expectedCostMinMad: Int? = null,
    val expectedCostMaxMad: Int? = null,
    val visitMinMinutes: Int? = null,
    val visitMaxMinutes: Int? = null,
    val bestTimeToGo: String? = null,
    val bestTimeWindows: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val localTips: List<String> = emptyList(),
    val scamWarnings: List<String> = emptyList(),
    val doAndDont: List<String> = emptyList(),
    val images: List<String> = emptyList()
) {
    /** Check if place has valid coordinates */
    val hasCoordinates: Boolean
        get() = lat != null && lng != null

    /** Get formatted visit duration range */
    val visitDurationRange: String?
        get() = when {
            visitMinMinutes != null && visitMaxMinutes != null ->
                "$visitMinMinutes-$visitMaxMinutes min"
            visitMinMinutes != null -> "$visitMinMinutes+ min"
            visitMaxMinutes != null -> "up to $visitMaxMinutes min"
            else -> null
        }

    /** Get formatted price range */
    val priceRange: String?
        get() = when {
            expectedCostMinMad != null && expectedCostMaxMad != null ->
                "$expectedCostMinMad-$expectedCostMaxMad MAD"
            expectedCostMinMad != null -> "$expectedCostMinMad+ MAD"
            expectedCostMaxMad != null -> "up to $expectedCostMaxMad MAD"
            feesMinMad != null && feesMaxMad != null ->
                "$feesMinMad-$feesMaxMad MAD (entry)"
            else -> null
        }
}

/**
 * Domain model for a PriceCard.
 */
data class PriceCard(
    val id: String,
    val title: String,
    val category: String? = null,
    val unit: String? = null,
    val volatility: String? = null,
    val confidence: String? = null,
    val expectedCostMinMad: Int,
    val expectedCostMaxMad: Int,
    val expectedCostNotes: String? = null,
    val expectedCostUpdatedAt: String? = null,
    val whatInfluencesPrice: List<String> = emptyList(),
    val redFlags: List<String> = emptyList(),
    val fairnessLowMultiplier: Double? = null,
    val fairnessHighMultiplier: Double? = null
) {
    /** Get formatted price range */
    val priceRange: String
        get() = "$expectedCostMinMad-$expectedCostMaxMad MAD"

    /** Check if price is high volatility */
    val isHighVolatility: Boolean
        get() = volatility == "high"
}

/**
 * Domain model for a Phrase (phrasebook entry).
 */
data class Phrase(
    val id: String,
    val category: String? = null,
    val arabic: String? = null,
    val latin: String,
    val english: String,
    val audio: String? = null
) {
    /** Check if phrase has audio */
    val hasAudio: Boolean
        get() = !audio.isNullOrBlank()
}

/**
 * Domain model for an Itinerary.
 */
data class Itinerary(
    val id: String,
    val title: String,
    val duration: String? = null,
    val style: String? = null,
    val steps: List<ItineraryStep> = emptyList()
)

/**
 * A step in an itinerary.
 */
data class ItineraryStep(
    val type: String,
    val placeId: String? = null,
    val activityId: String? = null,
    val estimatedStopMinutes: Int? = null,
    val routeHint: String? = null
)

/**
 * Domain model for a Tip.
 */
data class Tip(
    val id: String,
    val title: String,
    val category: String? = null,
    val summary: String? = null,
    val actions: List<String> = emptyList(),
    val severity: String? = null,
    val updatedAt: String? = null,
    val relatedPlaceIds: List<String> = emptyList(),
    val relatedPriceCardIds: List<String> = emptyList()
) {
    /** Check if tip is critical */
    val isCritical: Boolean
        get() = severity == "critical"

    /** Check if tip is important */
    val isImportant: Boolean
        get() = severity == "important" || severity == "critical"
}

/**
 * Domain model for a Culture article.
 */
data class CultureArticle(
    val id: String,
    val title: String,
    val summary: String? = null,
    val category: String? = null,
    val doList: List<String> = emptyList(),
    val dontList: List<String> = emptyList(),
    val updatedAt: String? = null
)

/**
 * Domain model for an Activity.
 */
data class Activity(
    val id: String,
    val title: String,
    val category: String? = null,
    val regionId: String? = null,
    val durationMinMinutes: Int? = null,
    val durationMaxMinutes: Int? = null,
    val pickupAvailable: Boolean? = null,
    val typicalPriceMinMad: Int? = null,
    val typicalPriceMaxMad: Int? = null,
    val ratingSignal: String? = null,
    val reviewCountSignal: String? = null,
    val bestTimeWindows: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: String? = null
) {
    /** Get formatted duration range */
    val durationRange: String?
        get() = when {
            durationMinMinutes != null && durationMaxMinutes != null ->
                "$durationMinMinutes-$durationMaxMinutes min"
            durationMinMinutes != null -> "$durationMinMinutes+ min"
            durationMaxMinutes != null -> "up to $durationMaxMinutes min"
            else -> null
        }

    /** Get formatted price range */
    val priceRange: String?
        get() = when {
            typicalPriceMinMad != null && typicalPriceMaxMad != null ->
                "$typicalPriceMinMad-$typicalPriceMaxMad MAD"
            typicalPriceMinMad != null -> "$typicalPriceMinMad+ MAD"
            typicalPriceMaxMad != null -> "up to $typicalPriceMaxMad MAD"
            else -> null
        }
}

/**
 * Domain model for an Event.
 */
data class Event(
    val id: String,
    val title: String,
    val category: String? = null,
    val city: String? = null,
    val venue: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val priceMinMad: Int? = null,
    val priceMaxMad: Int? = null,
    val ticketStatus: String? = null,
    val sourceUrl: String? = null
) {
    /** Check if tickets are available */
    val ticketsAvailable: Boolean
        get() = ticketStatus == "open"

    /** Check if event is sold out */
    val isSoldOut: Boolean
        get() = ticketStatus == "sold_out"
}
