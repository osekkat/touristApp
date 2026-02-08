import Foundation
import CoreLocation
import os.log

/// Protocol for location services providing GPS coordinates.
///
/// This service wraps CLLocationManager with battery-safe patterns:
/// - Request only "When In Use" location (foreground only)
/// - Start/stop updates based on screen visibility
/// - Automatic timeout to prevent battery drain
/// - Graceful handling of permission denial
///
/// Usage:
/// ```swift
/// let locationService = LocationServiceImpl.shared
/// await locationService.requestPermission()
/// locationService.startUpdates()
/// // ... use currentLocation
/// locationService.stopUpdates()
/// ```
public protocol LocationService: AnyObject, Sendable {
    /// Current user location, nil if unknown or permission denied.
    var currentLocation: CLLocation? { get }

    /// Current authorization status for location services.
    var locationAuthorizationStatus: CLAuthorizationStatus { get }

    /// Whether location services are authorized and available.
    var isAuthorized: Bool { get }

    /// Requests location permission from the user.
    /// - Returns: true if permission was granted, false otherwise.
    @MainActor
    func requestPermission() async -> Bool

    /// Starts receiving location updates.
    /// Updates are throttled to preserve battery.
    /// Call stopUpdates() when compass screen is no longer visible.
    func startUpdates()

    /// Stops receiving location updates.
    /// Always call this when the compass screen closes.
    func stopUpdates()

    /// Requests a single high-accuracy location refresh.
    /// Use sparingly as this is more battery-intensive.
    /// - Returns: The refreshed location.
    /// - Throws: LocationError if location cannot be obtained.
    func refreshLocation() async throws -> CLLocation
}

/// Errors that can occur during location operations.
public enum LocationError: Error, LocalizedError {
    case permissionDenied
    case locationUnavailable
    case timeout
    case servicesDisabled

    public var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Location permission was denied"
        case .locationUnavailable:
            return "Unable to determine your location"
        case .timeout:
            return "Location request timed out"
        case .servicesDisabled:
            return "Location services are disabled"
        }
    }
}

/// Implementation of LocationService using CLLocationManager.
///
/// Thread-safety: All CLLocationManager operations happen on the main thread.
/// Public interface is Sendable for use from async contexts.
public final class LocationServiceImpl: NSObject, LocationService, @unchecked Sendable {

    // MARK: - Singleton

    public static let shared = LocationServiceImpl()

    // MARK: - Private Properties

    private let locationManager: CLLocationManager
    private let logger = Logger(subsystem: "com.marrakechguide", category: "LocationService")

    /// Lock for thread-safe access to mutable state
    private let lock = NSLock()

    /// Stored current location (accessed with lock)
    private var _currentLocation: CLLocation?

    /// Whether updates are currently active
    private var isUpdating = false

    /// Timeout timer for automatic stop
    private var timeoutTimer: Timer?

    /// Timeout duration in seconds (10 minutes default)
    private let timeoutDuration: TimeInterval = 600

    /// Continuation for permission request
    private var permissionContinuation: CheckedContinuation<Bool, Never>?

    /// Continuation for refresh request
    private var refreshContinuation: CheckedContinuation<CLLocation, Error>?

    /// Flag indicating we're waiting for a high-accuracy refresh
    private var isRefreshing = false

    // MARK: - Public Properties

    public var currentLocation: CLLocation? {
        lock.lock()
        defer { lock.unlock() }
        return _currentLocation
    }

    public var locationAuthorizationStatus: CLAuthorizationStatus {
        locationManager.authorizationStatus
    }

    public var isAuthorized: Bool {
        let status = locationAuthorizationStatus
        return status == .authorizedWhenInUse || status == .authorizedAlways
    }

    // MARK: - Initialization

    private override init() {
        locationManager = CLLocationManager()
        super.init()

        locationManager.delegate = self
        // Use balanced accuracy by default (battery-friendly)
        locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        // Distance filter to reduce update frequency
        locationManager.distanceFilter = 5.0
    }

    // MARK: - Public Methods

    @MainActor
    public func requestPermission() async -> Bool {
        let currentStatus = locationAuthorizationStatus

        switch currentStatus {
        case .notDetermined:
            // Request permission and wait for response
            return await withCheckedContinuation { continuation in
                self.permissionContinuation = continuation
                self.locationManager.requestWhenInUseAuthorization()
            }

        case .authorizedWhenInUse, .authorizedAlways:
            return true

        case .denied, .restricted:
            logger.info("Location permission denied or restricted")
            return false

        @unknown default:
            logger.warning("Unknown authorization status: \(String(describing: currentStatus))")
            return false
        }
    }

    public func startUpdates() {
        guard isAuthorized else {
            logger.info("Cannot start updates: not authorized")
            return
        }

        guard CLLocationManager.locationServicesEnabled() else {
            logger.info("Cannot start updates: location services disabled")
            return
        }

        guard !isUpdating else {
            logger.debug("Updates already active")
            return
        }

        isUpdating = true
        locationManager.startUpdatingLocation()
        logger.info("Started location updates")

        // Set timeout to prevent indefinite battery drain
        resetTimeoutTimer()
    }

    public func stopUpdates() {
        guard isUpdating else { return }

        isUpdating = false
        locationManager.stopUpdatingLocation()
        cancelTimeoutTimer()
        logger.info("Stopped location updates")
    }

    public func refreshLocation() async throws -> CLLocation {
        guard isAuthorized else {
            throw LocationError.permissionDenied
        }

        guard CLLocationManager.locationServicesEnabled() else {
            throw LocationError.servicesDisabled
        }

        // If we already have a recent location, return it
        if let location = currentLocation,
           Date().timeIntervalSince(location.timestamp) < 5 {
            return location
        }

        // Request high-accuracy location
        return try await withCheckedThrowingContinuation { continuation in
            self.refreshContinuation = continuation
            self.isRefreshing = true

            // Temporarily increase accuracy
            self.locationManager.desiredAccuracy = kCLLocationAccuracyBest

            if !self.isUpdating {
                self.locationManager.startUpdatingLocation()
            }

            // Timeout after 15 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self] in
                guard let self = self, self.isRefreshing else { return }
                self.isRefreshing = false
                self.refreshContinuation?.resume(throwing: LocationError.timeout)
                self.refreshContinuation = nil

                // Restore balanced accuracy
                self.locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters

                if !self.isUpdating {
                    self.locationManager.stopUpdatingLocation()
                }
            }
        }
    }

    // MARK: - Private Methods

    private func resetTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutTimer = Timer.scheduledTimer(withTimeInterval: timeoutDuration, repeats: false) { [weak self] _ in
            self?.logger.warning("Location updates timed out after \(self?.timeoutDuration ?? 0) seconds")
            self?.stopUpdates()
        }
    }

    private func cancelTimeoutTimer() {
        timeoutTimer?.invalidate()
        timeoutTimer = nil
    }

    private func updateLocation(_ location: CLLocation) {
        lock.lock()
        _currentLocation = location
        lock.unlock()
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationServiceImpl: CLLocationManagerDelegate {

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        logger.info("Authorization status changed: \(String(describing: status))")

        // Resume permission continuation if waiting
        if let continuation = permissionContinuation {
            permissionContinuation = nil
            let granted = status == .authorizedWhenInUse || status == .authorizedAlways
            continuation.resume(returning: granted)
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        // Filter out stale or inaccurate locations
        let age = Date().timeIntervalSince(location.timestamp)
        if age > 10 {
            logger.debug("Ignoring stale location (age: \(age)s)")
            return
        }

        if location.horizontalAccuracy < 0 {
            logger.debug("Ignoring invalid location (negative accuracy)")
            return
        }

        updateLocation(location)
        logger.debug("Location updated: \(location.coordinate.latitude), \(location.coordinate.longitude) (accuracy: \(location.horizontalAccuracy)m)")

        // Handle refresh request
        if isRefreshing {
            // Wait for a reasonably accurate location
            if location.horizontalAccuracy <= 20 {
                isRefreshing = false
                refreshContinuation?.resume(returning: location)
                refreshContinuation = nil

                // Restore balanced accuracy
                locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters

                if !isUpdating {
                    locationManager.stopUpdatingLocation()
                }
            }
        }

        // Reset timeout since we're getting updates
        if isUpdating {
            resetTimeoutTimer()
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        logger.error("Location manager error: \(error.localizedDescription)")

        if isRefreshing {
            isRefreshing = false
            refreshContinuation?.resume(throwing: LocationError.locationUnavailable)
            refreshContinuation = nil

            locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters

            if !isUpdating {
                locationManager.stopUpdatingLocation()
            }
        }
    }
}
