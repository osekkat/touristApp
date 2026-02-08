package com.marrakechguide.core.engine

import kotlin.math.*

/**
 * GeoEngine provides geographic calculation utilities for the Marrakech Guide app.
 *
 * All methods are pure functions with no side effects. Both iOS and Android
 * implementations MUST produce identical outputs for identical inputs.
 *
 * Test vectors: shared/tests/geo-engine-vectors.json
 */
object GeoEngine {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val DEFAULT_WALK_SPEED_MPS = 1.25 // 4.5 km/h
    private const val MEDINA_WALK_MULTIPLIER = 0.7

    /**
     * Data class representing a geographic coordinate.
     */
    data class Coordinate(
        val lat: Double,
        val lng: Double
    )

    /**
     * Calculates the great-circle distance between two coordinates using the Haversine formula.
     *
     * @param from Starting coordinate
     * @param to Ending coordinate
     * @return Distance in meters
     */
    fun haversine(from: Coordinate, to: Coordinate): Double {
        val lat1Rad = Math.toRadians(from.lat)
        val lat2Rad = Math.toRadians(to.lat)
        val deltaLatRad = Math.toRadians(to.lat - from.lat)
        val deltaLngRad = Math.toRadians(to.lng - from.lng)

        val rawA = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
        val a = rawA.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the initial bearing from one coordinate to another.
     *
     * @param from Starting coordinate
     * @param to Ending coordinate
     * @return Bearing in degrees (0 = North, 90 = East, 180 = South, 270 = West)
     */
    fun bearing(from: Coordinate, to: Coordinate): Double {
        val lat1Rad = Math.toRadians(from.lat)
        val lat2Rad = Math.toRadians(to.lat)
        val deltaLngRad = Math.toRadians(to.lng - from.lng)

        val x = sin(deltaLngRad) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLngRad)

        val bearingRad = atan2(x, y)
        val bearingDeg = Math.toDegrees(bearingRad)

        // Normalize to 0-360
        return (bearingDeg + 360) % 360
    }

    /**
     * Calculates the relative angle for compass arrow rotation.
     *
     * Given the bearing to the target and the device's current heading,
     * returns the rotation angle for the arrow to point toward the target.
     *
     * @param targetBearing Bearing to target in degrees (0-360)
     * @param deviceHeading Current device heading in degrees (0-360)
     * @return Relative angle in degrees (0-360) for arrow rotation
     */
    fun relativeAngle(targetBearing: Double, deviceHeading: Double): Double {
        var angle = (targetBearing - deviceHeading) % 360
        if (angle < 0) {
            angle += 360
        }
        return angle
    }

    /**
     * Formats a distance in meters to a human-readable string.
     *
     * Rules:
     * - Under 100m: "X m" (exact)
     * - 100-999m: "X m" (rounded to 10m)
     * - 1km+: "X.X km"
     *
     * @param meters Distance in meters
     * @return Formatted string
     */
    fun formatDistance(meters: Double): String {
        val safeMeters = meters.coerceAtLeast(0.0)

        return when {
            safeMeters < 100 -> "${safeMeters.roundToInt()} m"
            safeMeters < 1000 -> "${(safeMeters / 10).roundToInt() * 10} m"
            else -> {
                val km = safeMeters / 1000
                val formatted = (km * 10).roundToInt() / 10.0
                if (formatted == formatted.toLong().toDouble()) {
                    "${formatted.toLong()}.0 km"
                } else {
                    "$formatted km"
                }
            }
        }
    }

    /**
     * Estimates walking time based on distance and region.
     *
     * Walking speed assumptions:
     * - Default: 4.5 km/h (1.25 m/s)
     * - Medina: 70% of default (denser paths, more navigation needed)
     *
     * @param meters Distance in meters
     * @param region Region name (affects speed multiplier)
     * @return Estimated walking time in minutes
     */
    fun estimateWalkTime(meters: Double, region: String): Int {
        if (meters <= 0) return 0

        val speedMultiplier = when (region.lowercase()) {
            "medina", "medina_core", "kasbah", "souks" -> MEDINA_WALK_MULTIPLIER
            else -> 1.0
        }

        val effectiveSpeed = DEFAULT_WALK_SPEED_MPS * speedMultiplier
        val timeSeconds = meters / effectiveSpeed
        val timeMinutes = timeSeconds / 60.0

        // Add 10% buffer for navigation/stops
        return ceil(timeMinutes * 1.1).toInt()
    }

    /**
     * Checks if a coordinate is within Marrakech metropolitan area.
     *
     * Useful for sanity checking GPS coordinates before displaying.
     *
     * @param coord Coordinate to check
     * @return true if coordinate is within Marrakech bounds
     */
    fun isWithinMarrakech(coord: Coordinate): Boolean {
        // Approximate bounding box for Marrakech metropolitan area
        val minLat = 31.55
        val maxLat = 31.70
        val minLng = -8.10
        val maxLng = -7.90

        return coord.lat in minLat..maxLat && coord.lng in minLng..maxLng
    }

    /**
     * Determines which region a coordinate is in.
     *
     * Regions:
     * - medina: Old walled city
     * - kasbah: Southern historic district
     * - gueliz: Modern city center
     * - hivernage: Resort/hotel district
     * - other: Outside defined regions
     *
     * @param coord Coordinate to check
     * @return Region name
     */
    fun determineRegion(coord: Coordinate): String {
        // Medina approximate bounds (walled old city)
        val medinaMinLat = 31.615
        val medinaMaxLat = 31.640
        val medinaMinLng = -8.00
        val medinaMaxLng = -7.975

        // Gueliz approximate bounds (modern center)
        val guelizMinLat = 31.630
        val guelizMaxLat = 31.650
        val guelizMinLng = -8.020
        val guelizMaxLng = -7.995

        return when {
            coord.lat in medinaMinLat..medinaMaxLat &&
            coord.lng in medinaMinLng..medinaMaxLng -> "medina"

            coord.lat in guelizMinLat..guelizMaxLat &&
            coord.lng in guelizMinLng..guelizMaxLng -> "gueliz"

            coord.lat < 31.620 && coord.lng < -7.980 -> "kasbah"

            else -> "other"
        }
    }
}
