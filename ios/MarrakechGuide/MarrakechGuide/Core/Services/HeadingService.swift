import Foundation
import CoreLocation
import os.log

/// Confidence level for compass heading readings.
public enum HeadingConfidence: String, CaseIterable, Sendable {
    /// Heading is reliable and accurate
    case good
    /// Heading may be affected by magnetic interference
    case weak
    /// Heading is not available (no sensors or significant interference)
    case unavailable
}

/// Protocol for heading/compass services.
///
/// This service wraps CLLocationManager heading updates with:
/// - Confidence indicators for heading reliability
/// - Battery-safe start/stop patterns
/// - UI update throttling (10-20Hz max)
/// - Automatic calibration detection
///
/// Usage:
/// ```swift
/// let headingService = HeadingServiceImpl.shared
/// headingService.startUpdates()
/// // ... use currentHeading and headingConfidence
/// headingService.stopUpdates()
/// ```
public protocol HeadingService: AnyObject, Sendable {
    /// Current magnetic heading in degrees (0-360, 0 = magnetic north).
    /// Nil if heading is unavailable.
    var currentHeading: CLHeading? { get }

    /// True heading adjusted for magnetic declination (if location available).
    /// Falls back to magnetic heading if true heading unavailable.
    var trueHeadingDegrees: Double? { get }

    /// Confidence level of the current heading reading.
    var headingConfidence: HeadingConfidence { get }

    /// Whether heading services are available on this device.
    var isHeadingAvailable: Bool { get }

    /// Starts receiving heading updates.
    /// Updates are throttled to preserve battery.
    /// Call stopUpdates() when compass screen is no longer visible.
    func startUpdates()

    /// Stops receiving heading updates.
    /// Always call this when the compass screen closes.
    func stopUpdates()

    /// Requests the system to display heading calibration if needed.
    /// Returns true if calibration was shown.
    @MainActor
    func requestCalibration() -> Bool
}

/// Implementation of HeadingService using CLLocationManager.
///
/// Thread-safety: All CLLocationManager operations happen on the main thread.
/// Public interface is Sendable for use from async contexts.
public final class HeadingServiceImpl: NSObject, HeadingService, @unchecked Sendable {

    // MARK: - Singleton

    public static let shared = HeadingServiceImpl()

    // MARK: - Private Properties

    private let locationManager: CLLocationManager
    private let logger = Logger(subsystem: "com.marrakechguide", category: "HeadingService")

    /// Lock for thread-safe access to mutable state
    private let lock = NSLock()

    /// Stored current heading (accessed with lock)
    private var _currentHeading: CLHeading?

    /// Stored confidence (accessed with lock)
    private var _headingConfidence: HeadingConfidence = .unavailable

    /// Whether updates are currently active
    private var isUpdating = false

    /// Timeout timer for automatic stop
    private var timeoutTimer: Timer?

    /// Timeout duration in seconds (10 minutes default)
    private let timeoutDuration: TimeInterval = 600

    /// Last time heading was updated (for throttling)
    private var lastUpdateTime: Date?

    /// Minimum interval between heading updates (50ms = 20Hz)
    private let minUpdateInterval: TimeInterval = 0.05

    // MARK: - Public Properties

    public var currentHeading: CLHeading? {
        lock.lock()
        defer { lock.unlock() }
        return _currentHeading
    }

    public var trueHeadingDegrees: Double? {
        guard let heading = currentHeading else { return nil }

        // Use true heading if available (requires location fix)
        if heading.trueHeading >= 0 {
            return heading.trueHeading
        }

        // Fall back to magnetic heading
        if heading.magneticHeading >= 0 {
            return heading.magneticHeading
        }

        return nil
    }

    public var headingConfidence: HeadingConfidence {
        lock.lock()
        defer { lock.unlock() }
        return _headingConfidence
    }

    public var isHeadingAvailable: Bool {
        CLLocationManager.headingAvailable()
    }

    // MARK: - Initialization

    private override init() {
        locationManager = CLLocationManager()
        super.init()

        locationManager.delegate = self

        // Set heading filter to reduce update frequency (1 degree change minimum)
        locationManager.headingFilter = 1.0
    }

    // MARK: - Public Methods

    public func startUpdates() {
        guard isHeadingAvailable else {
            logger.info("Heading services not available on this device")
            updateConfidence(.unavailable)
            return
        }

        guard !isUpdating else {
            logger.debug("Heading updates already active")
            return
        }

        isUpdating = true
        locationManager.startUpdatingHeading()
        logger.info("Started heading updates")

        // Set timeout to prevent indefinite battery drain
        resetTimeoutTimer()
    }

    public func stopUpdates() {
        guard isUpdating else { return }

        isUpdating = false
        locationManager.stopUpdatingHeading()
        cancelTimeoutTimer()
        logger.info("Stopped heading updates")
    }

    @MainActor
    public func requestCalibration() -> Bool {
        // CLLocationManager will show calibration UI automatically when needed
        // We can't force it, but we can check if calibration is needed
        if let heading = currentHeading, heading.headingAccuracy < 0 {
            logger.info("Heading calibration needed")
            return true
        }
        return false
    }

    // MARK: - Private Methods

    private func resetTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutTimer = Timer.scheduledTimer(withTimeInterval: timeoutDuration, repeats: false) { [weak self] _ in
            self?.logger.warning("Heading updates timed out after \(self?.timeoutDuration ?? 0) seconds")
            self?.stopUpdates()
        }
    }

    private func cancelTimeoutTimer() {
        timeoutTimer?.invalidate()
        timeoutTimer = nil
    }

    private func updateHeading(_ heading: CLHeading) {
        lock.lock()
        _currentHeading = heading
        lock.unlock()
    }

    private func updateConfidence(_ confidence: HeadingConfidence) {
        lock.lock()
        _headingConfidence = confidence
        lock.unlock()
    }

    private func calculateConfidence(from accuracy: CLLocationDirection) -> HeadingConfidence {
        // headingAccuracy is in degrees, negative means invalid
        if accuracy < 0 {
            return .unavailable
        } else if accuracy <= 15 {
            return .good
        } else {
            return .weak
        }
    }

    private func shouldThrottleUpdate() -> Bool {
        guard let lastUpdate = lastUpdateTime else {
            lastUpdateTime = Date()
            return false
        }

        let elapsed = Date().timeIntervalSince(lastUpdate)
        if elapsed >= minUpdateInterval {
            lastUpdateTime = Date()
            return false
        }

        return true
    }
}

// MARK: - CLLocationManagerDelegate

extension HeadingServiceImpl: CLLocationManagerDelegate {

    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        // Throttle updates to 20Hz max
        if shouldThrottleUpdate() {
            return
        }

        updateHeading(newHeading)

        // Calculate confidence based on accuracy
        let confidence = calculateConfidence(from: newHeading.headingAccuracy)
        updateConfidence(confidence)

        logger.debug("Heading updated: \(newHeading.magneticHeading)° (accuracy: \(newHeading.headingAccuracy)°, confidence: \(confidence.rawValue))")

        // Reset timeout since we're getting updates
        if isUpdating {
            resetTimeoutTimer()
        }
    }

    public func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        // Allow system to show calibration UI when needed
        logger.info("Heading calibration requested by system")
        return true
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        logger.error("Heading manager error: \(error.localizedDescription)")
        updateConfidence(.unavailable)
    }
}
