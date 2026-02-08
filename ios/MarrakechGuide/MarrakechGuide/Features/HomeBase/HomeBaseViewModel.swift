import SwiftUI
import CoreLocation
import Combine

/// ViewModel for the Home Base compass feature.
///
/// Manages state for:
/// - Home base location setup and retrieval
/// - Current user location tracking
/// - Compass heading for direction to home
/// - Distance and bearing calculations
@MainActor
final class HomeBaseViewModel: ObservableObject {

    // MARK: - Published State

    /// Current home base, nil if not set
    @Published private(set) var homeBase: HomeBase?

    /// Current user location
    @Published private(set) var currentLocation: CLLocation?

    /// Current device heading in degrees
    @Published private(set) var currentHeading: Double?

    /// Confidence level of the heading
    @Published private(set) var headingConfidence: HeadingConfidence = .unavailable

    /// Distance to home base in meters
    @Published private(set) var distanceMeters: Double?

    /// Bearing to home base in degrees
    @Published private(set) var bearingDegrees: Double?

    /// Relative angle for compass arrow (bearing - heading)
    @Published private(set) var arrowRotation: Double = 0

    /// Whether location permission is granted
    @Published private(set) var hasLocationPermission = false

    /// Whether location is being actively tracked
    @Published private(set) var isTracking = false

    /// Error message to display
    @Published var errorMessage: String?

    /// Whether we're loading initial data
    @Published private(set) var isLoading = true

    // MARK: - Dependencies

    private let settingsRepository: UserSettingsRepository
    private let locationService: LocationService
    private let headingService: HeadingService

    // MARK: - Timer for updates

    private var updateTimer: Timer?
    private let updateInterval: TimeInterval = 0.1 // 10Hz

    // MARK: - Initialization

    init(
        settingsRepository: UserSettingsRepository,
        locationService: LocationService = LocationServiceImpl.shared,
        headingService: HeadingService = HeadingServiceImpl.shared
    ) {
        self.settingsRepository = settingsRepository
        self.locationService = locationService
        self.headingService = headingService
    }

    // MARK: - Public Methods

    /// Load initial state
    func load() async {
        isLoading = true
        defer { isLoading = false }

        // Load saved home base
        do {
            homeBase = try await settingsRepository.getHomeBase()
        } catch {
            errorMessage = "Failed to load home base: \(error.localizedDescription)"
        }

        // Check permission status
        hasLocationPermission = locationService.isAuthorized
    }

    /// Request location permission
    func requestPermission() async -> Bool {
        let granted = await locationService.requestPermission()
        hasLocationPermission = granted
        return granted
    }

    /// Save a new home base
    func saveHomeBase(name: String, lat: Double, lng: Double, address: String?) async throws {
        let newHomeBase = HomeBase(name: name, lat: lat, lng: lng, address: address)
        try await settingsRepository.setHomeBase(newHomeBase)
        homeBase = newHomeBase
    }

    /// Set home base to current location
    func setHomeBaseToCurrentLocation(name: String, address: String?) async throws {
        guard let location = locationService.currentLocation else {
            throw HomeBaseError.locationUnavailable
        }

        try await saveHomeBase(
            name: name,
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            address: address
        )
    }

    /// Start tracking location and heading
    func startTracking() {
        guard homeBase != nil else { return }
        guard hasLocationPermission else { return }

        isTracking = true
        locationService.startUpdates()
        headingService.startUpdates()

        // Start update timer
        updateTimer = Timer.scheduledTimer(withTimeInterval: updateInterval, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.updateCompassState()
            }
        }
    }

    /// Stop tracking
    func stopTracking() {
        isTracking = false
        locationService.stopUpdates()
        headingService.stopUpdates()
        updateTimer?.invalidate()
        updateTimer = nil
    }

    /// Refresh location with high accuracy
    func refreshLocation() async {
        do {
            let location = try await locationService.refreshLocation()
            currentLocation = location
            updateCompassState()
        } catch {
            errorMessage = "Couldn't refresh location: \(error.localizedDescription)"
        }
    }

    // MARK: - Private Methods

    private func updateCompassState() {
        // Update current location
        currentLocation = locationService.currentLocation

        // Update heading
        if let heading = headingService.trueHeadingDegrees {
            currentHeading = heading
        }
        headingConfidence = headingService.headingConfidence

        // Calculate distance and bearing to home base
        guard let home = homeBase,
              let location = currentLocation else {
            distanceMeters = nil
            bearingDegrees = nil
            return
        }

        let homeCoord = GeoEngine.Coordinate(lat: home.lat, lng: home.lng)
        let currentCoord = GeoEngine.Coordinate(
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude
        )

        // Calculate distance
        distanceMeters = GeoEngine.haversine(from: currentCoord, to: homeCoord)

        // Calculate bearing
        bearingDegrees = GeoEngine.bearing(from: currentCoord, to: homeCoord)

        // Calculate arrow rotation
        if let bearing = bearingDegrees, let heading = currentHeading {
            arrowRotation = GeoEngine.relativeAngle(bearing: bearing, heading: heading)
        }
    }

    // MARK: - Computed Properties

    /// Formatted distance string
    var formattedDistance: String {
        guard let distance = distanceMeters else { return "—" }
        return GeoEngine.formatDistance(meters: distance)
    }

    /// Estimated walk time
    var estimatedWalkTime: String {
        guard let distance = distanceMeters else { return "—" }

        // Determine if in Medina for speed adjustment
        let region: GeoEngine.MarrakechRegion
        if let location = currentLocation {
            let coord = GeoEngine.Coordinate(
                lat: location.coordinate.latitude,
                lng: location.coordinate.longitude
            )
            region = GeoEngine.detectRegion(coordinate: coord)
        } else {
            region = .other
        }

        let minutes = GeoEngine.estimateWalkTime(meters: distance, region: region)

        if minutes < 1 {
            return "< 1 min"
        } else if minutes < 60 {
            return "\(Int(minutes)) min"
        } else {
            let hours = Int(minutes / 60)
            let remainingMins = Int(minutes.truncatingRemainder(dividingBy: 60))
            return "\(hours)h \(remainingMins)m"
        }
    }

    /// Direction description (N, NE, E, etc.)
    var directionDescription: String {
        guard let bearing = bearingDegrees else { return "—" }
        return bearingToCardinal(bearing)
    }

    private func bearingToCardinal(_ bearing: Double) -> String {
        let normalized = bearing.truncatingRemainder(dividingBy: 360)
        let adjusted = normalized < 0 ? normalized + 360 : normalized

        switch adjusted {
        case 337.5..<360, 0..<22.5: return "North"
        case 22.5..<67.5: return "Northeast"
        case 67.5..<112.5: return "East"
        case 112.5..<157.5: return "Southeast"
        case 157.5..<202.5: return "South"
        case 202.5..<247.5: return "Southwest"
        case 247.5..<292.5: return "West"
        case 292.5..<337.5: return "Northwest"
        default: return "—"
        }
    }
}

// MARK: - Errors

enum HomeBaseError: LocalizedError {
    case locationUnavailable
    case permissionDenied

    var errorDescription: String? {
        switch self {
        case .locationUnavailable:
            return "Unable to get current location"
        case .permissionDenied:
            return "Location permission is required"
        }
    }
}
