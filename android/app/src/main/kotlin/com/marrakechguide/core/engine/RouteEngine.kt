package com.marrakechguide.core.engine

import java.util.Date
import kotlin.math.ceil
import kotlin.math.max

/**
 * RouteEngine manages route execution state and calculates leg information for Route Cards.
 *
 * Test vectors: shared/tests/route-engine-vectors.json
 */
object RouteEngine {

    // MARK: - Types

    enum class RouteType {
        ITINERARY,
        MY_DAY_PLAN
    }

    data class RouteProgress(
        val routeId: String,
        val routeType: RouteType,
        var currentStepIndex: Int = 0,
        val stepsCompleted: MutableList<Int> = mutableListOf(),
        val stepsSkipped: MutableList<Int> = mutableListOf(),
        val startedAt: Date = Date(),
        var pausedAt: Date? = null,
        val totalSteps: Int
    )

    data class RouteLeg(
        val fromPlace: RoutePlace?,
        val toPlace: RoutePlace,
        val distanceMeters: Double,
        val bearingDegrees: Double,
        val estimatedWalkMinutes: Int,
        val routeHint: String?,
        val isLastStep: Boolean
    )

    data class RoutePlace(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val routeHint: String? = null
    )

    data class Coordinate(
        val lat: Double,
        val lng: Double
    )

    // MARK: - Constants

    private const val DEFAULT_WALK_SPEED_MPM = 75.0 // meters per minute (4.5 km/h)
    private const val MEDINA_WALK_SPEED_MPM = 50.0 // meters per minute (3 km/h, denser)

    // MARK: - Route Start

    /**
     * Start a new route with the given places.
     */
    fun startRoute(
        routeId: String,
        routeType: RouteType,
        places: List<RoutePlace>
    ): RouteProgress? {
        if (places.isEmpty()) return null
        return RouteProgress(
            routeId = routeId,
            routeType = routeType,
            totalSteps = places.size
        )
    }

    // MARK: - Current Leg

    /**
     * Get the current leg information for navigation.
     */
    fun getCurrentLeg(
        progress: RouteProgress,
        places: List<RoutePlace>,
        currentLocation: Coordinate? = null,
        isInMedina: Boolean = true
    ): RouteLeg? {
        if (progress.currentStepIndex >= places.size) return null

        val toPlace = places[progress.currentStepIndex]
        val fromPlace: RoutePlace? = if (progress.currentStepIndex > 0) {
            places[progress.currentStepIndex - 1]
        } else null

        // Calculate from point: user's current location or previous stop
        val fromCoord: Coordinate = when {
            currentLocation != null -> currentLocation
            fromPlace != null -> Coordinate(fromPlace.lat, fromPlace.lng)
            else -> Coordinate(toPlace.lat, toPlace.lng) // First step fallback
        }

        val toCoord = Coordinate(toPlace.lat, toPlace.lng)
        val distanceMeters = GeoEngine.haversine(
            from = GeoEngine.Coordinate(fromCoord.lat, fromCoord.lng),
            to = GeoEngine.Coordinate(toCoord.lat, toCoord.lng)
        )
        val bearingDegrees = GeoEngine.bearing(
            from = GeoEngine.Coordinate(fromCoord.lat, fromCoord.lng),
            to = GeoEngine.Coordinate(toCoord.lat, toCoord.lng)
        )

        val walkSpeed = if (isInMedina) MEDINA_WALK_SPEED_MPM else DEFAULT_WALK_SPEED_MPM
        val estimatedWalkMinutes = max(1, ceil(distanceMeters / walkSpeed).toInt())

        val isLastStep = progress.currentStepIndex == places.size - 1

        return RouteLeg(
            fromPlace = fromPlace,
            toPlace = toPlace,
            distanceMeters = distanceMeters,
            bearingDegrees = bearingDegrees,
            estimatedWalkMinutes = estimatedWalkMinutes,
            routeHint = toPlace.routeHint,
            isLastStep = isLastStep
        )
    }

    // MARK: - Mutations

    /**
     * Complete the current step and advance to next.
     */
    fun completeCurrentStep(progress: RouteProgress) {
        if (progress.currentStepIndex >= progress.totalSteps) return
        progress.stepsCompleted.add(progress.currentStepIndex)
        progress.currentStepIndex++
    }

    /**
     * Skip the current step and advance to next.
     */
    fun skipCurrentStep(progress: RouteProgress) {
        if (progress.currentStepIndex >= progress.totalSteps) return
        progress.stepsSkipped.add(progress.currentStepIndex)
        progress.currentStepIndex++
    }

    /**
     * Pause the route.
     */
    fun pauseRoute(progress: RouteProgress) {
        progress.pausedAt = Date()
    }

    /**
     * Resume the route.
     */
    fun resumeRoute(progress: RouteProgress) {
        progress.pausedAt = null
    }

    /**
     * Exit/abandon the route.
     */
    fun exitRoute(progress: RouteProgress) {
        while (progress.currentStepIndex < progress.totalSteps) {
            progress.stepsSkipped.add(progress.currentStepIndex)
            progress.currentStepIndex++
        }
    }

    // MARK: - Queries

    /**
     * Check if the route is complete (all steps processed).
     */
    fun isRouteComplete(progress: RouteProgress): Boolean {
        return progress.currentStepIndex >= progress.totalSteps
    }

    /**
     * Get completion percentage (0.0 to 1.0).
     */
    fun getCompletionPercentage(progress: RouteProgress): Double {
        if (progress.totalSteps == 0) return 0.0
        return progress.stepsCompleted.size.toDouble() / progress.totalSteps.toDouble()
    }

    /**
     * Get overall progress percentage including skipped (0.0 to 1.0).
     */
    fun getOverallProgress(progress: RouteProgress): Double {
        if (progress.totalSteps == 0) return 0.0
        return progress.currentStepIndex.toDouble() / progress.totalSteps.toDouble()
    }

    /**
     * Get time elapsed since route started.
     */
    fun getTimeElapsed(progress: RouteProgress): Long {
        return Date().time - progress.startedAt.time
    }

    /**
     * Get count of remaining steps.
     */
    fun getRemainingSteps(progress: RouteProgress): Int {
        return max(0, progress.totalSteps - progress.currentStepIndex)
    }

    /**
     * Check if the route is paused.
     */
    fun isRoutePaused(progress: RouteProgress): Boolean {
        return progress.pausedAt != null
    }

    // MARK: - Distance Formatting

    /**
     * Format distance for display.
     */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.1f km", meters / 1000)
        } else {
            String.format("%.0f m", meters)
        }
    }

    /**
     * Format walk time for display.
     */
    fun formatWalkTime(minutes: Int): String {
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        } else {
            "$minutes min"
        }
    }
}
