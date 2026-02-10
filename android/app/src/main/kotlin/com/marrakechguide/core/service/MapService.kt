package com.marrakechguide.core.service

import android.content.Context
import android.util.Log
import com.marrakechguide.core.model.MapBounds
import com.marrakechguide.core.model.MapCoordinate
import com.marrakechguide.core.model.MapRoute
import com.marrakechguide.core.model.PackStatus
import com.marrakechguide.core.model.TileSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Service for offline map functionality.
 *
 * Provides:
 * - Offline tile availability checking
 * - Simple A* routing for walking paths
 * - POI data from downloaded packs
 */
interface MapService {
    /** Whether offline map for Medina is available */
    val isMedinaMapAvailable: StateFlow<Boolean>

    /** Whether offline map for Gueliz is available */
    val isGuelizMapAvailable: StateFlow<Boolean>

    /** Current tile source being used */
    val currentTileSource: StateFlow<TileSource>

    /** Check if a region has offline tiles available */
    fun isOfflineAvailable(bounds: MapBounds): Boolean

    /** Get the path to offline tiles directory for a pack */
    fun getTilesPath(packId: String): File?

    /** Calculate a walking route between two points */
    suspend fun calculateRoute(from: MapCoordinate, to: MapCoordinate): MapRoute?

    /** Estimate walking time in minutes between two points */
    fun estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Boolean = true): Int

    /** Refresh availability status from downloaded packs */
    suspend fun refreshAvailability()
}

/**
 * Implementation of MapService.
 */
@Singleton
class MapServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadService: DownloadService
) : MapService {

    companion object {
        private const val TAG = "MapService"
        private const val MEDINA_PACK_ID = "medina-map"
        private const val GUELIZ_PACK_ID = "gueliz-map"

        // Walking speeds in meters per minute
        private const val WALK_SPEED_DEFAULT = 75.0 // 4.5 km/h
        private const val WALK_SPEED_MEDINA = 50.0 // 3 km/h (denser, more stops)

        // Earth radius in meters
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    private val packsDir = File(context.filesDir, "packs")

    private val _isMedinaMapAvailable = MutableStateFlow(false)
    override val isMedinaMapAvailable: StateFlow<Boolean> = _isMedinaMapAvailable.asStateFlow()

    private val _isGuelizMapAvailable = MutableStateFlow(false)
    override val isGuelizMapAvailable: StateFlow<Boolean> = _isGuelizMapAvailable.asStateFlow()

    private val _currentTileSource = MutableStateFlow(TileSource.PLACEHOLDER)
    override val currentTileSource: StateFlow<TileSource> = _currentTileSource.asStateFlow()

    init {
        // Check availability on init
        checkPackAvailability()
    }

    override fun isOfflineAvailable(bounds: MapBounds): Boolean {
        return when {
            MapBounds.MEDINA.contains(bounds.southwest) || MapBounds.MEDINA.contains(bounds.northeast) ->
                _isMedinaMapAvailable.value
            MapBounds.GUELIZ.contains(bounds.southwest) || MapBounds.GUELIZ.contains(bounds.northeast) ->
                _isGuelizMapAvailable.value
            else -> false
        }
    }

    override fun getTilesPath(packId: String): File? {
        val packDir = File(packsDir, packId)
        val tilesDir = File(packDir, "tiles")
        return if (tilesDir.exists() && tilesDir.isDirectory) tilesDir else null
    }

    override suspend fun calculateRoute(from: MapCoordinate, to: MapCoordinate): MapRoute? {
        // Simple straight-line route for now
        // In production, this would use a walking graph from the downloaded pack
        val distance = haversineDistance(from, to)

        // If points are very close, just return direct line
        if (distance < 50) {
            return MapRoute(
                id = "route-${System.currentTimeMillis()}",
                points = listOf(from, to)
            )
        }

        // Generate intermediate waypoints for smoother display
        // In production, this would follow actual walking paths
        val numPoints = (distance / 100).toInt().coerceIn(2, 20)
        val points = mutableListOf(from)

        for (i in 1 until numPoints) {
            val fraction = i.toDouble() / numPoints
            val lat = from.latitude + (to.latitude - from.latitude) * fraction
            val lng = from.longitude + (to.longitude - from.longitude) * fraction
            points.add(MapCoordinate(lat, lng))
        }

        points.add(to)

        return MapRoute(
            id = "route-${System.currentTimeMillis()}",
            points = points
        )
    }

    override fun estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Boolean): Int {
        val distance = haversineDistance(from, to)
        val speed = if (inMedina) WALK_SPEED_MEDINA else WALK_SPEED_DEFAULT
        return (distance / speed).toInt().coerceAtLeast(1)
    }

    override suspend fun refreshAvailability() {
        checkPackAvailability()
    }

    private fun checkPackAvailability() {
        val packStates = downloadService.packStates.value

        _isMedinaMapAvailable.value = packStates[MEDINA_PACK_ID]?.status == PackStatus.INSTALLED
        _isGuelizMapAvailable.value = packStates[GUELIZ_PACK_ID]?.status == PackStatus.INSTALLED

        // Update tile source based on availability
        _currentTileSource.value = when {
            _isMedinaMapAvailable.value || _isGuelizMapAvailable.value -> TileSource.OFFLINE
            else -> TileSource.PLACEHOLDER
        }

        Log.i(TAG, "Map availability: medina=${_isMedinaMapAvailable.value}, gueliz=${_isGuelizMapAvailable.value}")
    }

    /**
     * Calculate the great-circle distance between two points using the haversine formula.
     * Returns distance in meters.
     */
    private fun haversineDistance(from: MapCoordinate, to: MapCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_M * c
    }
}

/**
 * Simple routing node for walking path calculation.
 * Used in A* pathfinding when routing graph is available.
 */
internal data class RoutingNode(
    val id: String,
    val coordinate: MapCoordinate,
    val neighbors: List<String> = emptyList()
)

/**
 * Walking graph loaded from offline pack.
 * Contains nodes and edges for A* routing.
 */
internal data class WalkingGraph(
    val nodes: Map<String, RoutingNode>,
    val version: String
) {
    companion object {
        fun empty() = WalkingGraph(emptyMap(), "0.0.0")
    }
}
