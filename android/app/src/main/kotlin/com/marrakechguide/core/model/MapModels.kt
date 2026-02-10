package com.marrakechguide.core.model

/**
 * Represents a geographic coordinate.
 */
data class MapCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        /** Jemaa el-Fna center - the heart of the Medina */
        val MEDINA_CENTER = MapCoordinate(31.6259, -7.9891)

        /** Default zoom level for Medina view */
        const val DEFAULT_ZOOM = 15.0

        /** Maximum zoom level for detailed view */
        const val MAX_ZOOM = 20.0

        /** Minimum zoom level */
        const val MIN_ZOOM = 10.0
    }
}

/**
 * Represents a bounding box for map regions.
 */
data class MapBounds(
    val southwest: MapCoordinate,
    val northeast: MapCoordinate
) {
    /** Check if a coordinate is within these bounds */
    fun contains(coordinate: MapCoordinate): Boolean {
        return coordinate.latitude in southwest.latitude..northeast.latitude &&
                coordinate.longitude in southwest.longitude..northeast.longitude
    }

    companion object {
        /** Marrakech Medina bounds - the old walled city */
        val MEDINA = MapBounds(
            southwest = MapCoordinate(31.6150, -8.0050),
            northeast = MapCoordinate(31.6400, -7.9700)
        )

        /** Gueliz/new city bounds */
        val GUELIZ = MapBounds(
            southwest = MapCoordinate(31.6280, -8.0200),
            northeast = MapCoordinate(31.6480, -7.9900)
        )
    }
}

/**
 * Represents the current camera/view state of the map.
 */
data class MapCamera(
    val center: MapCoordinate,
    val zoom: Double = MapCoordinate.DEFAULT_ZOOM,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0
) {
    companion object {
        val DEFAULT = MapCamera(center = MapCoordinate.MEDINA_CENTER)
    }
}

/**
 * A marker to display on the map.
 */
data class MapMarker(
    val id: String,
    val coordinate: MapCoordinate,
    val title: String,
    val subtitle: String? = null,
    val category: MarkerCategory = MarkerCategory.GENERAL,
    val isSelected: Boolean = false
)

/**
 * Categories for map markers with associated styling.
 */
enum class MarkerCategory {
    GENERAL,
    LANDMARK,
    RESTAURANT,
    CAFE,
    SHOPPING,
    MUSEUM,
    HOTEL,
    USER_LOCATION,
    HOME_BASE,
    ROUTE_STOP
}

/**
 * A route to display on the map.
 */
data class MapRoute(
    val id: String,
    val points: List<MapCoordinate>,
    val color: Int? = null,
    val width: Float = 4f
)

/**
 * Current state of the map view.
 */
data class MapViewState(
    val camera: MapCamera = MapCamera.DEFAULT,
    val markers: List<MapMarker> = emptyList(),
    val routes: List<MapRoute> = emptyList(),
    val userLocation: MapCoordinate? = null,
    val userHeading: Double? = null,
    val isOfflineMapAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Map tile source types.
 */
enum class TileSource {
    /** Online tiles from map provider */
    ONLINE,
    /** Offline tiles from downloaded pack */
    OFFLINE,
    /** Placeholder when no tiles available */
    PLACEHOLDER
}

/**
 * Configuration for the map view.
 */
data class MapConfig(
    val showUserLocation: Boolean = true,
    val showCompass: Boolean = true,
    val showZoomControls: Boolean = false,
    val allowRotation: Boolean = true,
    val allowTilt: Boolean = false,
    val clusterMarkers: Boolean = true,
    val clusterRadius: Int = 50
)
