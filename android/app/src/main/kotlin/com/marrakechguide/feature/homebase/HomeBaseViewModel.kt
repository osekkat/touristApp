package com.marrakechguide.feature.homebase

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.engine.GeoEngine
import com.marrakechguide.core.repository.HomeBase
import com.marrakechguide.core.repository.UserSettingsRepository
import com.marrakechguide.core.service.HeadingConfidence
import com.marrakechguide.core.service.HeadingService
import com.marrakechguide.core.service.LocationError
import com.marrakechguide.core.service.LocationService
import com.marrakechguide.core.service.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

/**
 * ViewModel for the Home Base compass feature.
 *
 * Manages:
 * - Loading/saving home base from settings
 * - Combining location and heading data
 * - Computing compass arrow rotation
 * - Formatting distance and time estimates
 */
@HiltViewModel
class HomeBaseViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val locationService: LocationService,
    private val headingService: HeadingService
) : ViewModel() {

    // MARK: - State

    private val _homeBase = MutableStateFlow<HomeBase?>(null)
    val homeBase: StateFlow<HomeBase?> = _homeBase.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val permissionStatus: StateFlow<PermissionStatus> = locationService.permissionStatus

    val hasLocationPermission: Boolean
        get() = locationService.isAuthorized

    val headingConfidence: StateFlow<HeadingConfidence> = headingService.headingConfidence

    // MARK: - Computed State

    /**
     * Arrow rotation angle for the compass.
     * Combines device heading with bearing to home base.
     */
    val arrowRotation: StateFlow<Double> = combine(
        locationService.currentLocation,
        headingService.currentHeading,
        _homeBase
    ) { location, heading, homeBase ->
        computeArrowRotation(location, heading, homeBase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /**
     * Current distance to home base in meters.
     */
    val distanceMeters: StateFlow<Double?> = combine(
        locationService.currentLocation,
        _homeBase
    ) { location, homeBase ->
        computeDistance(location, homeBase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Formatted distance string (e.g., "450 m" or "1.2 km").
     */
    val formattedDistance: StateFlow<String> = combine(
        distanceMeters
    ) { (distance) ->
        distance?.let { GeoEngine.formatDistance(it) } ?: "--"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--")

    /**
     * Estimated walk time string (e.g., "10 min").
     */
    val estimatedWalkTime: StateFlow<String> = combine(
        distanceMeters,
        locationService.currentLocation
    ) { distance, location ->
        computeWalkTimeString(distance, location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--")

    /**
     * Cardinal direction description (e.g., "Northeast").
     */
    val directionDescription: StateFlow<String> = combine(
        arrowRotation
    ) { (rotation) ->
        cardinalDirection(rotation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // MARK: - Lifecycle

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                _homeBase.value = settingsRepository.getHomeBase()
                locationService.checkPermission()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load home base: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startTracking() {
        if (!locationService.isAuthorized) return

        locationService.startUpdates()
        headingService.startUpdates()
    }

    fun stopTracking() {
        locationService.stopUpdates()
        headingService.stopUpdates()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            try {
                locationService.refreshLocation()
            } catch (e: LocationError) {
                _errorMessage.value = e.message
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh location: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    // MARK: - Private Methods

    private fun computeArrowRotation(
        location: Location?,
        heading: Float?,
        homeBase: HomeBase?
    ): Double {
        if (location == null || homeBase == null) return 0.0
        if (heading == null) return 0.0

        val currentCoord = GeoEngine.Coordinate(location.latitude, location.longitude)
        val homeCoord = GeoEngine.Coordinate(homeBase.lat, homeBase.lng)

        val bearingToHome = GeoEngine.bearing(currentCoord, homeCoord)
        return GeoEngine.relativeAngle(bearingToHome, heading.toDouble())
    }

    private fun computeDistance(location: Location?, homeBase: HomeBase?): Double? {
        if (location == null || homeBase == null) return null

        val currentCoord = GeoEngine.Coordinate(location.latitude, location.longitude)
        val homeCoord = GeoEngine.Coordinate(homeBase.lat, homeBase.lng)

        return GeoEngine.haversine(currentCoord, homeCoord)
    }

    private fun computeWalkTimeString(distance: Double?, location: Location?): String {
        if (distance == null || location == null) return "--"

        val currentCoord = GeoEngine.Coordinate(location.latitude, location.longitude)
        val region = GeoEngine.determineRegion(currentCoord)
        val minutes = GeoEngine.estimateWalkTime(distance, region)

        return when {
            minutes < 1 -> "<1 min"
            minutes == 1 -> "1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) {
                    "$hours hr"
                } else {
                    "$hours hr $remainingMinutes min"
                }
            }
        }
    }

    private fun cardinalDirection(rotation: Double): String {
        val normalized = rotation.mod(360.0)

        return when {
            normalized >= 337.5 || normalized < 22.5 -> "North"
            normalized >= 22.5 && normalized < 67.5 -> "Northeast"
            normalized >= 67.5 && normalized < 112.5 -> "East"
            normalized >= 112.5 && normalized < 157.5 -> "Southeast"
            normalized >= 157.5 && normalized < 202.5 -> "South"
            normalized >= 202.5 && normalized < 247.5 -> "Southwest"
            normalized >= 247.5 && normalized < 292.5 -> "West"
            normalized >= 292.5 && normalized < 337.5 -> "Northwest"
            else -> ""
        }
    }
}
