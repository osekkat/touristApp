import Foundation
import CoreLocation

/// RouteEngine manages route execution state and calculates leg information for Route Cards.
///
/// Test vectors: shared/tests/route-engine-vectors.json
public enum RouteEngine {

    // MARK: - Types

    public enum RouteType: String, Codable, Sendable {
        case itinerary
        case myDayPlan
    }

    public struct RouteProgress: Codable, Sendable {
        public let routeId: String
        public let routeType: RouteType
        public var currentStepIndex: Int
        public var stepsCompleted: [Int]
        public var stepsSkipped: [Int]
        public let startedAt: Date
        public var pausedAt: Date?
        public let totalSteps: Int

        public init(
            routeId: String,
            routeType: RouteType,
            totalSteps: Int,
            startedAt: Date = Date()
        ) {
            self.routeId = routeId
            self.routeType = routeType
            self.currentStepIndex = 0
            self.stepsCompleted = []
            self.stepsSkipped = []
            self.startedAt = startedAt
            self.pausedAt = nil
            self.totalSteps = totalSteps
        }
    }

    public struct RouteLeg: Sendable {
        public let fromPlace: RoutePlace?
        public let toPlace: RoutePlace
        public let distanceMeters: Double
        public let bearingDegrees: Double
        public let estimatedWalkMinutes: Int
        public let routeHint: String?
        public let isLastStep: Bool

        public init(
            fromPlace: RoutePlace?,
            toPlace: RoutePlace,
            distanceMeters: Double,
            bearingDegrees: Double,
            estimatedWalkMinutes: Int,
            routeHint: String?,
            isLastStep: Bool
        ) {
            self.fromPlace = fromPlace
            self.toPlace = toPlace
            self.distanceMeters = distanceMeters
            self.bearingDegrees = bearingDegrees
            self.estimatedWalkMinutes = estimatedWalkMinutes
            self.routeHint = routeHint
            self.isLastStep = isLastStep
        }
    }

    public struct RoutePlace: Sendable {
        public let id: String
        public let name: String
        public let coordinate: CLLocationCoordinate2D
        public let routeHint: String?

        public init(id: String, name: String, coordinate: CLLocationCoordinate2D, routeHint: String? = nil) {
            self.id = id
            self.name = name
            self.coordinate = coordinate
            self.routeHint = routeHint
        }
    }

    // MARK: - Constants

    private static let defaultWalkSpeedMPM: Double = 75 // meters per minute (4.5 km/h)
    private static let medinaWalkSpeedMPM: Double = 50 // meters per minute (3 km/h, denser)

    // MARK: - Route Start

    /// Start a new route with the given places.
    public static func startRoute(
        routeId: String,
        routeType: RouteType,
        places: [RoutePlace]
    ) -> RouteProgress? {
        guard !places.isEmpty else { return nil }
        return RouteProgress(
            routeId: routeId,
            routeType: routeType,
            totalSteps: places.count
        )
    }

    // MARK: - Current Leg

    /// Get the current leg information for navigation.
    public static func getCurrentLeg(
        progress: RouteProgress,
        places: [RoutePlace],
        currentLocation: CLLocationCoordinate2D?,
        isInMedina: Bool = true
    ) -> RouteLeg? {
        guard progress.currentStepIndex < places.count else { return nil }

        let toPlace = places[progress.currentStepIndex]
        let fromPlace: RoutePlace? = progress.currentStepIndex > 0 ? places[progress.currentStepIndex - 1] : nil

        // Calculate from point: user's current location or previous stop
        let fromCoord: CLLocationCoordinate2D
        if let currentLocation = currentLocation {
            fromCoord = currentLocation
        } else if let fromPlace = fromPlace {
            fromCoord = fromPlace.coordinate
        } else {
            // First step, no previous, no current location - use a default
            fromCoord = toPlace.coordinate
        }

        let distanceMeters = GeoEngine.haversine(from: fromCoord, to: toPlace.coordinate)
        let bearingDegrees = GeoEngine.bearing(from: fromCoord, to: toPlace.coordinate)

        let walkSpeed = isInMedina ? medinaWalkSpeedMPM : defaultWalkSpeedMPM
        let estimatedWalkMinutes = max(1, Int(ceil(distanceMeters / walkSpeed)))

        let isLastStep = progress.currentStepIndex == places.count - 1

        return RouteLeg(
            fromPlace: fromPlace,
            toPlace: toPlace,
            distanceMeters: distanceMeters,
            bearingDegrees: bearingDegrees,
            estimatedWalkMinutes: estimatedWalkMinutes,
            routeHint: toPlace.routeHint,
            isLastStep: isLastStep
        )
    }

    // MARK: - Mutations

    /// Complete the current step and advance to next.
    public static func completeCurrentStep(_ progress: inout RouteProgress) {
        guard progress.currentStepIndex < progress.totalSteps else { return }
        progress.stepsCompleted.append(progress.currentStepIndex)
        progress.currentStepIndex += 1
    }

    /// Skip the current step and advance to next.
    public static func skipCurrentStep(_ progress: inout RouteProgress) {
        guard progress.currentStepIndex < progress.totalSteps else { return }
        progress.stepsSkipped.append(progress.currentStepIndex)
        progress.currentStepIndex += 1
    }

    /// Pause the route.
    public static func pauseRoute(_ progress: inout RouteProgress) {
        progress.pausedAt = Date()
    }

    /// Resume the route.
    public static func resumeRoute(_ progress: inout RouteProgress) {
        progress.pausedAt = nil
    }

    /// Exit/abandon the route.
    public static func exitRoute(_ progress: inout RouteProgress) {
        // Mark remaining steps as skipped
        while progress.currentStepIndex < progress.totalSteps {
            progress.stepsSkipped.append(progress.currentStepIndex)
            progress.currentStepIndex += 1
        }
    }

    // MARK: - Queries

    /// Check if the route is complete (all steps processed).
    public static func isRouteComplete(_ progress: RouteProgress) -> Bool {
        return progress.currentStepIndex >= progress.totalSteps
    }

    /// Get completion percentage (0.0 to 1.0).
    public static func getCompletionPercentage(_ progress: RouteProgress) -> Double {
        guard progress.totalSteps > 0 else { return 0 }
        return Double(progress.stepsCompleted.count) / Double(progress.totalSteps)
    }

    /// Get overall progress percentage including skipped (0.0 to 1.0).
    public static func getOverallProgress(_ progress: RouteProgress) -> Double {
        guard progress.totalSteps > 0 else { return 0 }
        return Double(progress.currentStepIndex) / Double(progress.totalSteps)
    }

    /// Get time elapsed since route started.
    public static func getTimeElapsed(_ progress: RouteProgress) -> TimeInterval {
        return Date().timeIntervalSince(progress.startedAt)
    }

    /// Get count of remaining steps.
    public static func getRemainingSteps(_ progress: RouteProgress) -> Int {
        return max(0, progress.totalSteps - progress.currentStepIndex)
    }

    /// Check if the route is paused.
    public static func isRoutePaused(_ progress: RouteProgress) -> Bool {
        return progress.pausedAt != nil
    }

    // MARK: - Distance Formatting

    /// Format distance for display.
    public static func formatDistance(_ meters: Double) -> String {
        if meters >= 1000 {
            return String(format: "%.1f km", meters / 1000)
        } else {
            return String(format: "%.0f m", meters)
        }
    }

    /// Format walk time for display.
    public static func formatWalkTime(_ minutes: Int) -> String {
        if minutes >= 60 {
            let hours = minutes / 60
            let mins = minutes % 60
            return mins > 0 ? "\(hours)h \(mins)m" : "\(hours)h"
        }
        return "\(minutes) min"
    }
}
