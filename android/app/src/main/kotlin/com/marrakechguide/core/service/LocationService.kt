package com.marrakechguide.core.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Permission status for location services.
 */
enum class PermissionStatus {
    /** Permission not yet requested */
    NOT_DETERMINED,
    /** Permission granted for foreground use */
    AUTHORIZED,
    /** Permission denied by user */
    DENIED,
    /** Permission restricted by system policy */
    RESTRICTED
}

/**
 * Errors that can occur during location operations.
 */
sealed class LocationError : Exception() {
    object PermissionDenied : LocationError() {
        private fun readResolve(): Any = PermissionDenied
        override val message = "Location permission was denied"
    }
    object LocationUnavailable : LocationError() {
        private fun readResolve(): Any = LocationUnavailable
        override val message = "Unable to determine your location"
    }
    object Timeout : LocationError() {
        private fun readResolve(): Any = Timeout
        override val message = "Location request timed out"
    }
    object ServicesDisabled : LocationError() {
        private fun readResolve(): Any = ServicesDisabled
        override val message = "Location services are disabled"
    }
}

/**
 * Interface for location services providing GPS coordinates.
 *
 * This service wraps FusedLocationProviderClient with battery-safe patterns:
 * - Request only foreground location (ACCESS_FINE_LOCATION)
 * - Start/stop updates based on screen visibility
 * - Automatic timeout to prevent battery drain
 * - Graceful handling of permission denial
 */
interface LocationService {
    /** Current user location, null if unknown or permission denied. */
    val currentLocation: StateFlow<Location?>

    /** Current permission status for location services. */
    val permissionStatus: StateFlow<PermissionStatus>

    /** Whether location services are authorized and available. */
    val isAuthorized: Boolean

    /**
     * Checks the current permission status without requesting.
     */
    fun checkPermission(): PermissionStatus

    /**
     * Starts receiving location updates.
     * Updates use balanced accuracy to preserve battery.
     * Call stopUpdates() when compass screen is no longer visible.
     */
    fun startUpdates()

    /**
     * Stops receiving location updates.
     * Always call this when the compass screen closes.
     */
    fun stopUpdates()

    /**
     * Requests a single high-accuracy location refresh.
     * Use sparingly as this is more battery-intensive.
     *
     * @return The refreshed location.
     * @throws LocationError if location cannot be obtained.
     */
    suspend fun refreshLocation(): Location
}

/**
 * Implementation of LocationService using FusedLocationProviderClient.
 *
 * This implementation:
 * - Uses PRIORITY_BALANCED_POWER_ACCURACY by default
 * - Escalates to HIGH_ACCURACY only for explicit refresh
 * - Includes automatic timeout after 10 minutes
 * - Throttles updates to reduce battery usage
 */
@Singleton
class LocationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationService {

    companion object {
        private const val TAG = "LocationService"
        private const val TIMEOUT_DURATION_MS = 600_000L // 10 minutes
        private const val UPDATE_INTERVAL_MS = 5_000L // 5 seconds
        private const val FASTEST_INTERVAL_MS = 2_000L // 2 seconds minimum
        private const val REFRESH_TIMEOUT_MS = 15_000L // 15 seconds for refresh
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatus.NOT_DETERMINED)
    override val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    override val isAuthorized: Boolean
        get() = checkPermission() == PermissionStatus.AUTHORIZED

    private var isUpdating = false
    private var timeoutTimer: Timer? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Filter out stale locations (older than 10 seconds)
                val age = System.currentTimeMillis() - location.time
                if (age > 10_000) {
                    Log.d(TAG, "Ignoring stale location (age: ${age}ms)")
                    return
                }

                // Filter out invalid locations
                if (location.accuracy <= 0) {
                    Log.d(TAG, "Ignoring invalid location (accuracy: ${location.accuracy})")
                    return
                }

                _currentLocation.value = location
                Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")

                // Reset timeout since we're getting updates
                if (isUpdating) {
                    resetTimeoutTimer()
                }
            }
        }
    }

    override fun checkPermission(): PermissionStatus {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val status = when {
            fineLocation == PackageManager.PERMISSION_GRANTED -> PermissionStatus.AUTHORIZED
            coarseLocation == PackageManager.PERMISSION_GRANTED -> PermissionStatus.AUTHORIZED
            else -> PermissionStatus.DENIED
        }

        _permissionStatus.value = status
        return status
    }

    @SuppressLint("MissingPermission")
    override fun startUpdates() {
        if (!isAuthorized) {
            Log.i(TAG, "Cannot start updates: not authorized")
            return
        }

        if (isUpdating) {
            Log.d(TAG, "Updates already active")
            return
        }

        isUpdating = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        Log.i(TAG, "Started location updates")

        // Set timeout to prevent indefinite battery drain
        resetTimeoutTimer()
    }

    override fun stopUpdates() {
        if (!isUpdating) return

        isUpdating = false
        fusedClient.removeLocationUpdates(locationCallback)
        cancelTimeoutTimer()
        Log.i(TAG, "Stopped location updates")
    }

    @SuppressLint("MissingPermission")
    override suspend fun refreshLocation(): Location {
        if (!isAuthorized) {
            throw LocationError.PermissionDenied
        }

        // If we have a recent location, return it
        _currentLocation.value?.let { location ->
            val age = System.currentTimeMillis() - location.time
            if (age < 5_000) {
                return location
            }
        }

        // Request high-accuracy location
        return withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val highAccuracyRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1_000L
                ).apply {
                    setMaxUpdates(1)
                    setWaitForAccurateLocation(true)
                }.build()

                val refreshCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        val location = result.lastLocation
                        if (location != null) {
                            _currentLocation.value = location
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        } else {
                            if (continuation.isActive) {
                                continuation.resumeWithException(LocationError.LocationUnavailable)
                            }
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                fun startRequest() {
                    fusedClient.requestLocationUpdates(
                        highAccuracyRequest,
                        refreshCallback,
                        Looper.getMainLooper()
                    )
                }
                startRequest()

                continuation.invokeOnCancellation {
                    fusedClient.removeLocationUpdates(refreshCallback)
                }
            }
        } ?: throw LocationError.Timeout
    }

    private fun resetTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    Log.w(TAG, "Location updates timed out after ${TIMEOUT_DURATION_MS}ms")
                    stopUpdates()
                }
            }, TIMEOUT_DURATION_MS)
        }
    }

    private fun cancelTimeoutTimer() {
        timeoutTimer?.cancel()
        timeoutTimer = null
    }
}
