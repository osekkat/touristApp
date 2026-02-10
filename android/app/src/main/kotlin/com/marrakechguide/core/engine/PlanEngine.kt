package com.marrakechguide.core.engine

import com.marrakechguide.core.model.Place
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * PlanEngine generates deterministic "My Day" plans from user constraints.
 *
 * Test vectors: shared/tests/plan-engine-vectors.json
 */
object PlanEngine {

    // MARK: Types

    data class Coordinate(
        val lat: Double,
        val lng: Double
    )

    enum class Interest(val value: String) {
        HISTORY("history"),
        FOOD("food"),
        SHOPPING("shopping"),
        NATURE("nature"),
        CULTURE("culture"),
        ARCHITECTURE("architecture"),
        RELAXATION("relaxation"),
        NIGHTLIFE("nightlife"),
        GENERAL("general");

        companion object {
            fun from(rawValue: String): Interest? {
                return entries.find { it.value == rawValue.lowercase() }
            }
        }
    }

    enum class Pace(val value: String) {
        RELAXED("relaxed"),
        STANDARD("standard"),
        ACTIVE("active");

        companion object {
            fun from(rawValue: String): Pace? {
                return entries.find { it.value == rawValue.lowercase() }
            }
        }
    }

    enum class BudgetTier(val value: String) {
        BUDGET("budget"),
        MID("mid"),
        SPLURGE("splurge");

        companion object {
            fun from(rawValue: String): BudgetTier? {
                return entries.find { it.value == rawValue.lowercase() }
            }
        }
    }

    data class Input(
        val availableMinutes: Int,
        val startPoint: Coordinate?,
        val interests: List<Interest>,
        val pace: Pace,
        val budgetTier: BudgetTier,
        val currentTime: Instant,
        val places: List<Place>,
        val recentPlaceIds: Set<String> = emptySet()
    )

    data class PriceRange(
        val minMad: Int,
        val maxMad: Int
    )

    data class PlanStop(
        val placeId: String,
        val arrivalTime: Instant,
        val departureTime: Instant,
        val travelMinutesFromPrevious: Int,
        val visitMinutes: Int
    )

    data class Output(
        val stops: List<PlanStop>,
        val totalMinutes: Int,
        val estimatedCostRange: PriceRange,
        val warnings: List<String>
    )

    // MARK: Public API

    fun generate(input: Input): Output {
        val availableMinutes = input.availableMinutes.coerceAtLeast(0)
        if (availableMinutes <= 0) {
            return Output(
                stops = emptyList(),
                totalMinutes = 0,
                estimatedCostRange = PriceRange(0, 0),
                warnings = listOf("Available time is too short to generate a plan.")
            )
        }

        var requiredMealSlots = mealSlotsOverlapping(
            start = input.currentTime,
            durationMinutes = availableMinutes
        )

        val warnings = mutableListOf<String>()
        var candidates = input.places.filter { !input.recentPlaceIds.contains(it.id) }

        if (input.interests.isNotEmpty()) {
            candidates = candidates.filter { interestMatchCount(it, input.interests) > 0 }
        }

        candidates = candidates.filter { budgetAllows(it, input.budgetTier) }

        // For long food-focused days, require all meal slots represented by feasible food venues.
        // This keeps plans from skipping dinner simply because the time window overlap omitted it.
        if (input.interests.contains(Interest.FOOD) && availableMinutes >= 360) {
            val availableMealSlots = candidates.flatMap { mealSlots(it) }.toSet()
            requiredMealSlots = requiredMealSlots.union(availableMealSlots)
        }

        if (candidates.isEmpty()) {
            warnings += "No places match your constraints right now."
            return Output(
                stops = emptyList(),
                totalMinutes = 0,
                estimatedCostRange = PriceRange(0, 0),
                warnings = warnings
            )
        }

        val selection = greedySelection(
            candidates = candidates,
            input = input,
            availableMinutes = availableMinutes,
            requiredMealSlots = requiredMealSlots
        )

        if (selection.closedExclusionCount > 0) {
            warnings += "Some places were excluded because they are closed at the planned visit time."
        }

        if (selection.places.isEmpty()) {
            warnings += "No plan could fit your time and constraints. Try increasing available time or broadening interests."
            return Output(
                stops = emptyList(),
                totalMinutes = 0,
                estimatedCostRange = PriceRange(0, 0),
                warnings = warnings
            )
        }

        val directSchedule = buildSchedule(
            places = selection.places,
            startTime = input.currentTime,
            startPoint = input.startPoint,
            availableMinutes = availableMinutes,
            pace = input.pace
        )
        val reorderedPlaces = reorderNearestNeighbor(selection.places, input.startPoint)
        val scheduled = if (reorderedPlaces == selection.places) {
            directSchedule
        } else {
            val reorderedSchedule = buildSchedule(
                places = reorderedPlaces,
                startTime = input.currentTime,
                startPoint = input.startPoint,
                availableMinutes = availableMinutes,
                pace = input.pace
            )
            preferredSchedule(
                primary = directSchedule,
                alternative = reorderedSchedule,
                requiredMealSlots = requiredMealSlots
            )
        }

        if (scheduled.droppedCount > 0) {
            warnings += "Some candidate stops were dropped during schedule construction."
        }

        val selectedMealSlots = scheduled.selectedPlaces.flatMap { mealSlots(it) }.toSet()
        val missingMealSlots = requiredMealSlots.minus(selectedMealSlots)
        if (missingMealSlots.isNotEmpty()) {
            val formatted = missingMealSlots.map { it.value }.sorted().joinToString(", ")
            warnings += "Could not schedule meal stop(s): $formatted."
        }

        val costRange = estimatedCostRange(scheduled.selectedPlaces, input.budgetTier)

        return Output(
            stops = scheduled.stops,
            totalMinutes = scheduled.totalMinutes,
            estimatedCostRange = costRange,
            warnings = warnings
        )
    }

    // MARK: Selection

    private data class EvaluatedCandidate(
        val place: Place,
        val travelMinutes: Int,
        val visitMinutes: Int,
        val score: Double
    )

    private data class SelectionResult(
        val places: List<Place>,
        val closedExclusionCount: Int
    )

    private fun greedySelection(
        candidates: List<Place>,
        input: Input,
        availableMinutes: Int,
        requiredMealSlots: Set<MealSlot>
    ): SelectionResult {
        var remainingMinutes = availableMinutes
        var elapsedMinutes = 0
        var currentCoordinate = input.startPoint
        val selected = mutableListOf<Place>()
        val selectedIds = mutableSetOf<String>()
        val selectedMealSlots = mutableSetOf<MealSlot>()
        var closedExclusionCount = 0

        val minimumStopMinutes = minVisitMinutes(input.pace)
        val maximumStops = maxStops(input.pace, availableMinutes)

        while (remainingMinutes >= minimumStopMinutes && selected.size < maximumStops) {
            val pendingMealSlots = requiredMealSlots.minus(selectedMealSlots)

            val evaluations = candidates
                .asSequence()
                .filter { !selectedIds.contains(it.id) }
                .mapNotNull { candidate ->
                    val travelMinutes = travelMinutes(currentCoordinate, candidate)
                    val visitMinutes = recommendedVisitMinutes(candidate, input.pace)
                    val requiredMinutes = travelMinutes + visitMinutes
                    if (requiredMinutes > remainingMinutes) {
                        return@mapNotNull null
                    }

                    val arrivalTime = input.currentTime.plusSeconds(((elapsedMinutes + travelMinutes) * 60).toLong())
                    if (HoursEngine.isOpen(place = candidate, at = arrivalTime) is HoursEngine.OpenStatus.Closed) {
                        closedExclusionCount += 1
                        return@mapNotNull null
                    }

                    var score = baseScore(candidate, input.interests, input.budgetTier, arrivalTime)

                    if (selected.none { it.category == candidate.category }) {
                        score += 3.0
                    }

                    val mealMatches = mealSlots(candidate).intersect(pendingMealSlots).size
                    if (mealMatches > 0) {
                        score += (mealMatches * 12).toDouble()
                    }

                    score -= travelMinutes.toDouble() * 0.35

                    EvaluatedCandidate(
                        place = candidate,
                        travelMinutes = travelMinutes,
                        visitMinutes = visitMinutes,
                        score = score
                    )
                }
                .sortedWith(
                    compareByDescending<EvaluatedCandidate> { it.score }
                        .thenBy { it.travelMinutes + it.visitMinutes }
                        .thenBy { it.place.id }
                )
                .toList()

            val best = evaluations.firstOrNull() ?: break

            selected += best.place
            selectedIds += best.place.id
            selectedMealSlots += mealSlots(best.place)
            remainingMinutes -= (best.travelMinutes + best.visitMinutes)
            elapsedMinutes += (best.travelMinutes + best.visitMinutes)

            coordinate(best.place)?.let { currentCoordinate = it }
        }

        return SelectionResult(
            places = selected,
            closedExclusionCount = closedExclusionCount
        )
    }

    // MARK: Schedule

    private data class ScheduleResult(
        val stops: List<PlanStop>,
        val selectedPlaces: List<Place>,
        val totalMinutes: Int,
        val droppedCount: Int
    )

    private fun buildSchedule(
        places: List<Place>,
        startTime: Instant,
        startPoint: Coordinate?,
        availableMinutes: Int,
        pace: Pace
    ): ScheduleResult {
        val stops = mutableListOf<PlanStop>()
        val selectedPlaces = mutableListOf<Place>()
        var currentCoordinate = startPoint
        var elapsedMinutes = 0
        var droppedCount = 0

        for (place in places) {
            val travel = travelMinutes(currentCoordinate, place)
            val visit = recommendedVisitMinutes(place, pace)
            val required = travel + visit

            if (elapsedMinutes + required > availableMinutes) {
                droppedCount += 1
                continue
            }

            val arrival = startTime.plusSeconds(((elapsedMinutes + travel) * 60).toLong())
            if (HoursEngine.isOpen(place = place, at = arrival) is HoursEngine.OpenStatus.Closed) {
                droppedCount += 1
                continue
            }

            val departure = arrival.plusSeconds((visit * 60).toLong())

            stops += PlanStop(
                placeId = place.id,
                arrivalTime = arrival,
                departureTime = departure,
                travelMinutesFromPrevious = travel,
                visitMinutes = visit
            )
            selectedPlaces += place

            elapsedMinutes += required
            coordinate(place)?.let { currentCoordinate = it }
        }

        return ScheduleResult(
            stops = stops,
            selectedPlaces = selectedPlaces,
            totalMinutes = elapsedMinutes,
            droppedCount = droppedCount
        )
    }

    private fun reorderNearestNeighbor(places: List<Place>, startPoint: Coordinate?): List<Place> {
        if (startPoint == null || places.size <= 1) {
            return places
        }

        val remaining = places.toMutableList()
        val ordered = mutableListOf<Place>()
        var current: Coordinate = startPoint

        while (remaining.isNotEmpty()) {
            val currentCoord = current
            val nextIndex = remaining.indices.minWithOrNull(
                compareBy<Int> { linearDistance(currentCoord, remaining[it]) }
                    .thenBy { remaining[it].id }
            ) ?: break

            val next = remaining.removeAt(nextIndex)
            ordered += next
            coordinate(next)?.let { current = it }
        }

        return ordered
    }

    private fun preferredSchedule(
        primary: ScheduleResult,
        alternative: ScheduleResult,
        requiredMealSlots: Set<MealSlot>
    ): ScheduleResult {
        val primaryMealCoverage = coveredRequiredMealSlots(primary, requiredMealSlots)
        val alternativeMealCoverage = coveredRequiredMealSlots(alternative, requiredMealSlots)
        if (alternativeMealCoverage != primaryMealCoverage) {
            return if (alternativeMealCoverage > primaryMealCoverage) alternative else primary
        }

        if (alternative.stops.size != primary.stops.size) {
            return if (alternative.stops.size > primary.stops.size) alternative else primary
        }

        if (alternative.totalMinutes != primary.totalMinutes) {
            return if (alternative.totalMinutes < primary.totalMinutes) alternative else primary
        }

        return primary
    }

    private fun coveredRequiredMealSlots(
        schedule: ScheduleResult,
        requiredMealSlots: Set<MealSlot>
    ): Int {
        if (requiredMealSlots.isEmpty()) {
            return 0
        }

        val selectedMealSlots = schedule.selectedPlaces.flatMap { mealSlots(it) }.toSet()
        return selectedMealSlots.intersect(requiredMealSlots).size
    }

    // MARK: Scoring Helpers

    private enum class MealSlot(val value: String) {
        BREAKFAST("breakfast"),
        LUNCH("lunch"),
        DINNER("dinner")
    }

    private fun baseScore(
        place: Place,
        interests: List<Interest>,
        budgetTier: BudgetTier,
        arrivalTime: Instant
    ): Double {
        val interestScore = interestMatchCount(place, interests) * 10.0
        val trapPenalty = touristTrapPenalty(place)
        val timeBonus = bestTimeBonus(place, arrivalTime)
        val budgetBonus = budgetFitBonus(place, budgetTier)
        return interestScore + trapPenalty + timeBonus + budgetBonus
    }

    private fun interestMatchCount(place: Place, interests: List<Interest>): Int {
        if (interests.isEmpty()) return 1

        val category = place.category?.lowercase().orEmpty()
        val tags = place.tags.map { it.lowercase() }.toSet()
        val name = place.name.lowercase()

        return interests.fold(0) { acc, interest ->
            if (interest == Interest.GENERAL) {
                return@fold acc + 1
            }

            val categories = interestCategories[interest].orEmpty()
            val tokens = interestTokens[interest].orEmpty()

            val categoryMatch = categories.contains(category)
            val tagMatch = tags.any { tag -> tokens.any { token -> tag.contains(token) } }
            val nameMatch = tokens.any { token -> name.contains(token) }

            acc + if (categoryMatch || tagMatch || nameMatch) 1 else 0
        }
    }

    private val interestCategories: Map<Interest, Set<String>> = mapOf(
        Interest.HISTORY to setOf("museum", "historic_site", "landmark", "neighborhood"),
        Interest.FOOD to setOf("restaurant", "cafe", "market"),
        Interest.SHOPPING to setOf("market", "neighborhood"),
        Interest.NATURE to setOf("garden", "nature"),
        Interest.CULTURE to setOf("museum", "historic_site", "landmark", "neighborhood"),
        Interest.ARCHITECTURE to setOf("museum", "historic_site", "landmark"),
        Interest.RELAXATION to setOf("garden", "nature", "cafe"),
        Interest.NIGHTLIFE to setOf("restaurant", "cafe", "market", "landmark")
    )

    private val interestTokens: Map<Interest, List<String>> = mapOf(
        Interest.HISTORY to listOf("history", "heritage", "palace", "madrasa", "museum", "historic"),
        Interest.FOOD to listOf("food", "eat", "restaurant", "cafe", "snack"),
        Interest.SHOPPING to listOf("shop", "shopping", "souk", "market", "artisan"),
        Interest.NATURE to listOf("garden", "park", "nature"),
        Interest.CULTURE to listOf("culture", "heritage", "tradition", "architecture"),
        Interest.ARCHITECTURE to listOf("architecture", "design", "mosaic", "riads"),
        Interest.RELAXATION to listOf("relax", "calm", "garden", "spa"),
        Interest.NIGHTLIFE to listOf("night", "evening", "music", "rooftop")
    )

    private fun touristTrapPenalty(place: Place): Double {
        return when (place.touristTrapLevel?.lowercase()) {
            "high" -> -5.0
            "mixed" -> -2.0
            else -> 0.0
        }
    }

    private fun bestTimeBonus(place: Place, at: Instant): Double {
        val currentWindow = timeWindow(at)
        val windows = place.bestTimeWindows.map { it.lowercase() }.toSet()
        return if (windows.contains(currentWindow)) 5.0 else 0.0
    }

    private fun budgetAllows(place: Place, tier: BudgetTier): Boolean {
        val tags = place.tags.map { it.lowercase() }.toSet()

        return when (tier) {
            BudgetTier.BUDGET -> tags.none {
                it.contains("luxury") || it.contains("fine-dining") || it.contains("upscale")
            }
            BudgetTier.MID -> tags.none { it.contains("ultra-luxury") }
            BudgetTier.SPLURGE -> true
        }
    }

    private fun budgetFitBonus(place: Place, tier: BudgetTier): Double {
        val tags = place.tags.map { it.lowercase() }.toSet()

        return when (tier) {
            BudgetTier.BUDGET -> when {
                tags.any { it.contains("budget") || it.contains("local") } -> 2.0
                tags.any { it.contains("luxury") || it.contains("fine-dining") } -> -4.0
                else -> 0.0
            }
            BudgetTier.MID -> 0.0
            BudgetTier.SPLURGE -> if (tags.any { it.contains("luxury") || it.contains("fine-dining") }) 3.0 else 0.0
        }
    }

    // MARK: Meal Helpers

    private fun mealSlotsOverlapping(start: Instant, durationMinutes: Int): Set<MealSlot> {
        val startLocal = start.atZone(PLANNING_ZONE)
        val endLocal = startLocal.plusMinutes(durationMinutes.toLong())

        fun overlaps(hourStart: Int, hourEnd: Int): Boolean {
            val rangeStart = startLocal.withHour(hourStart).withMinute(0).withSecond(0).withNano(0)
            val rangeEnd = startLocal.withHour(hourEnd).withMinute(0).withSecond(0).withNano(0)
            val latestStart = if (startLocal.isAfter(rangeStart)) startLocal else rangeStart
            val earliestEnd = if (endLocal.isBefore(rangeEnd)) endLocal else rangeEnd
            return latestStart.isBefore(earliestEnd)
        }

        val slots = mutableSetOf<MealSlot>()
        if (overlaps(7, 11)) slots += MealSlot.BREAKFAST
        if (overlaps(12, 15)) slots += MealSlot.LUNCH
        if (overlaps(19, 22)) slots += MealSlot.DINNER
        return slots
    }

    private fun mealSlots(place: Place): Set<MealSlot> {
        val category = place.category?.lowercase().orEmpty()
        if (category != "restaurant" && category != "cafe") {
            return emptySet()
        }

        val tags = place.tags.map { it.lowercase() }.toSet()
        val windows = place.bestTimeWindows.map { it.lowercase() }.toSet()

        val slots = mutableSetOf<MealSlot>()

        if (
            tags.any { it.contains("breakfast") || it.contains("brunch") } ||
            windows.contains("morning")
        ) {
            slots += MealSlot.BREAKFAST
        }

        if (
            tags.any { it.contains("lunch") } ||
            windows.contains("lunch") ||
            windows.contains("afternoon")
        ) {
            slots += MealSlot.LUNCH
        }

        if (
            tags.any { it.contains("dinner") || it.contains("evening") } ||
            windows.contains("evening")
        ) {
            slots += MealSlot.DINNER
        }

        return slots
    }

    // MARK: Time/Travel Helpers

    private fun recommendedVisitMinutes(place: Place, pace: Pace): Int {
        val defaultVisit = when (pace) {
            Pace.RELAXED -> 90
            Pace.STANDARD -> 60
            Pace.ACTIVE -> 45
        }

        val minVisit = maxOf(20, place.visitMinMinutes ?: defaultVisit)
        val maxVisit = maxOf(minVisit, place.visitMaxMinutes ?: minVisit)

        return when (pace) {
            Pace.RELAXED -> maxVisit
            Pace.STANDARD -> ((minVisit + maxVisit) / 2.0).roundToInt()
            Pace.ACTIVE -> minVisit
        }
    }

    private fun minVisitMinutes(pace: Pace): Int {
        return when (pace) {
            Pace.RELAXED -> 40
            Pace.STANDARD -> 30
            Pace.ACTIVE -> 20
        }
    }

    private fun maxStops(pace: Pace, availableMinutes: Int): Int {
        val paceCap = when (pace) {
            Pace.RELAXED -> 6
            Pace.STANDARD -> 7
            Pace.ACTIVE -> 8
        }

        val timeCap = maxOf(1, availableMinutes / 40)
        return minOf(paceCap, timeCap)
    }

    private fun timeWindow(at: Instant): String {
        val hour = at.atZone(PLANNING_ZONE).hour
        return when (hour) {
            in 6 until 11 -> "morning"
            in 11 until 15 -> "lunch"
            in 15 until 18 -> "afternoon"
            in 18 until 23 -> "evening"
            else -> "night"
        }
    }

    private val PLANNING_ZONE: ZoneId = ZoneId.of("Africa/Casablanca")

    private fun travelMinutes(from: Coordinate?, to: Place): Int {
        if (from == null) return 0
        val destination = coordinate(to) ?: return 10

        val fromCoord = GeoEngine.Coordinate(from.lat, from.lng)
        val toCoord = GeoEngine.Coordinate(destination.lat, destination.lng)
        val distanceMeters = GeoEngine.haversine(fromCoord, toCoord)
        if (distanceMeters <= 20.0) {
            return 0
        }

        val region = to.regionId ?: GeoEngine.determineRegion(toCoord)
        val estimate = GeoEngine.estimateWalkTime(distanceMeters, region)
        return estimate.coerceIn(1, 60)
    }

    private fun linearDistance(from: Coordinate, to: Place): Double {
        val destination = coordinate(to) ?: return 1_000_000.0
        return GeoEngine.haversine(
            GeoEngine.Coordinate(from.lat, from.lng),
            GeoEngine.Coordinate(destination.lat, destination.lng)
        )
    }

    private fun coordinate(place: Place): Coordinate? {
        val lat = place.lat ?: return null
        val lng = place.lng ?: return null
        return Coordinate(lat = lat, lng = lng)
    }

    // MARK: Cost

    private fun estimatedCostRange(places: List<Place>, tier: BudgetTier): PriceRange {
        val multiplier = when (tier) {
            BudgetTier.BUDGET -> 0.80
            BudgetTier.MID -> 1.00
            BudgetTier.SPLURGE -> 1.25
        }

        val totals = places.fold(0 to 0) { acc, place ->
            val base = baseCostRange(place.category)
            (acc.first + base.first) to (acc.second + base.second)
        }

        val adjustedMin = (totals.first * multiplier).roundToInt()
        val adjustedMax = (totals.second * multiplier).roundToInt()

        return PriceRange(
            minMad = adjustedMin.coerceAtLeast(0),
            maxMad = adjustedMax.coerceAtLeast(0)
        )
    }

    private fun baseCostRange(category: String?): Pair<Int, Int> {
        return when (category?.lowercase()) {
            "restaurant" -> 80 to 180
            "cafe" -> 20 to 60
            "museum" -> 70 to 120
            "historic_site" -> 40 to 90
            "garden" -> 40 to 100
            "market" -> 0 to 40
            "landmark", "neighborhood", "nature" -> 0 to 30
            else -> 20 to 80
        }
    }
}
