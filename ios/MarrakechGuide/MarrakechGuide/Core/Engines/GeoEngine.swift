import Foundation
import CoreLocation

/// GeoEngine provides geographic calculation utilities for the Marrakech Guide app.
///
/// All methods are pure functions with no side effects. Both iOS and Android
/// implementations MUST produce identical outputs for identical inputs.
///
/// Test vectors: shared/tests/geo-engine-vectors.json
public enum GeoEngine {

    // MARK: - Constants

    private static let earthRadiusMeters: Double = 6_371_000
    private static let defaultWalkSpeedMPS: Double = 1.25 // 4.5 km/h
    private static let medinaWalkMultiplier: Double = 0.7

    // MARK: - Haversine Distance

    /// Calculates the great-circle distance between two coordinates using the Haversine formula.
    ///
    /// - Parameters:
    ///   - from: Starting coordinate
    ///   - to: Ending coordinate
    /// - Returns: Distance in meters
    public static func haversine(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> Double {
        let lat1Rad = from.latitude.degreesToRadians
        let lat2Rad = to.latitude.degreesToRadians
        let deltaLatRad = (to.latitude - from.latitude).degreesToRadians
        let deltaLngRad = (to.longitude - from.longitude).degreesToRadians

        let rawA = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                   cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2) * sin(deltaLngRad / 2)
        // Clamp to valid domain to avoid NaN from tiny floating-point drift.
        let a = min(1.0, max(0.0, rawA))
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusMeters * c
    }

    // MARK: - Bearing

    /// Calculates the initial bearing from one coordinate to another.
    ///
    /// - Parameters:
    ///   - from: Starting coordinate
    ///   - to: Ending coordinate
    /// - Returns: Bearing in degrees (0 = North, 90 = East, 180 = South, 270 = West)
    public static func bearing(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> Double {
        let lat1Rad = from.latitude.degreesToRadians
        let lat2Rad = to.latitude.degreesToRadians
        let deltaLngRad = (to.longitude - from.longitude).degreesToRadians

        let x = sin(deltaLngRad) * cos(lat2Rad)
        let y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLngRad)

        let bearingRad = atan2(x, y)
        let bearingDeg = bearingRad.radiansToDegrees

        // Normalize to 0-360
        return (bearingDeg + 360).truncatingRemainder(dividingBy: 360)
    }

    // MARK: - Relative Angle

    /// Calculates the relative angle for compass arrow rotation.
    ///
    /// Given the bearing to the target and the device's current heading,
    /// returns the rotation angle for the arrow to point toward the target.
    ///
    /// - Parameters:
    ///   - targetBearing: Bearing to target in degrees (0-360)
    ///   - deviceHeading: Current device heading in degrees (0-360)
    /// - Returns: Relative angle in degrees (0-360) for arrow rotation
    public static func relativeAngle(targetBearing: Double, deviceHeading: Double) -> Double {
        var angle = (targetBearing - deviceHeading).truncatingRemainder(dividingBy: 360)
        if angle < 0 {
            angle += 360
        }
        return angle
    }

    // MARK: - Format Distance

    /// Formats a distance in meters to a human-readable string.
    ///
    /// Rules:
    /// - Under 100m: "X m" (exact)
    /// - 100-999m: "X m" (rounded to 10m)
    /// - 1km+: "X.X km"
    ///
    /// - Parameter meters: Distance in meters
    /// - Returns: Formatted string
    public static func formatDistance(_ meters: Double) -> String {
        let safeMeters = max(0, meters)

        if safeMeters < 100 {
            return "\(Int(safeMeters.rounded())) m"
        } else if safeMeters < 1000 {
            let rounded = (safeMeters / 10).rounded() * 10
            return "\(Int(rounded)) m"
        } else {
            let km = safeMeters / 1000
            let formatted = (km * 10).rounded() / 10
            return String(format: Locale(identifier: "en_US_POSIX"), "%.1f km", formatted)
        }
    }

    // MARK: - Estimate Walk Time

    /// Estimates walking time based on distance and region.
    ///
    /// Walking speed assumptions:
    /// - Default: 4.5 km/h (1.25 m/s)
    /// - Medina: 70% of default (denser paths, more navigation needed)
    ///
    /// - Parameters:
    ///   - meters: Distance in meters
    ///   - region: Region name (affects speed multiplier)
    /// - Returns: Estimated walking time in minutes
    public static func estimateWalkTime(meters: Double, region: String) -> Int {
        guard meters > 0 else { return 0 }

        let lowercaseRegion = region.lowercased()
        let speedMultiplier: Double

        switch lowercaseRegion {
        case "medina", "medina_core", "kasbah", "souks":
            speedMultiplier = medinaWalkMultiplier
        default:
            speedMultiplier = 1.0
        }

        let effectiveSpeed = defaultWalkSpeedMPS * speedMultiplier
        let timeSeconds = meters / effectiveSpeed
        let timeMinutes = timeSeconds / 60.0

        // Add 10% buffer for navigation/stops
        return Int(ceil(timeMinutes * 1.1))
    }

    // MARK: - Region Detection

    /// Checks if a coordinate is within Marrakech metropolitan area.
    ///
    /// Useful for sanity checking GPS coordinates before displaying.
    ///
    /// - Parameter coordinate: Coordinate to check
    /// - Returns: true if coordinate is within Marrakech bounds
    public static func isWithinMarrakech(_ coordinate: CLLocationCoordinate2D) -> Bool {
        // Approximate bounding box for Marrakech metropolitan area
        let minLat = 31.55
        let maxLat = 31.70
        let minLng = -8.10
        let maxLng = -7.90

        return coordinate.latitude >= minLat &&
               coordinate.latitude <= maxLat &&
               coordinate.longitude >= minLng &&
               coordinate.longitude <= maxLng
    }

    /// Determines which region a coordinate is in.
    ///
    /// Regions:
    /// - medina: Old walled city
    /// - kasbah: Southern historic district
    /// - gueliz: Modern city center
    /// - hivernage: Resort/hotel district
    /// - other: Outside defined regions
    ///
    /// - Parameter coordinate: Coordinate to check
    /// - Returns: Region name
    public static func determineRegion(_ coordinate: CLLocationCoordinate2D) -> String {
        // Medina approximate bounds (walled old city)
        let medinaMinLat = 31.615
        let medinaMaxLat = 31.640
        let medinaMinLng = -8.00
        let medinaMaxLng = -7.975

        // Gueliz approximate bounds (modern center)
        let guelizMinLat = 31.630
        let guelizMaxLat = 31.650
        let guelizMinLng = -8.020
        let guelizMaxLng = -7.995

        let lat = coordinate.latitude
        let lng = coordinate.longitude

        if lat >= medinaMinLat && lat <= medinaMaxLat &&
           lng >= medinaMinLng && lng <= medinaMaxLng {
            return "medina"
        }

        if lat >= guelizMinLat && lat <= guelizMaxLat &&
           lng >= guelizMinLng && lng <= guelizMaxLng {
            return "gueliz"
        }

        if lat < 31.620 && lng < -7.980 {
            return "kasbah"
        }

        return "other"
    }
}

// MARK: - Double Extensions

private extension Double {
    var degreesToRadians: Double {
        self * .pi / 180
    }

    var radiansToDegrees: Double {
        self * 180 / .pi
    }
}
