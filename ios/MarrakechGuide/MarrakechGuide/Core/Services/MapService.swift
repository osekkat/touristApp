import Foundation
import os.log

/// Service for offline map functionality.
///
/// Provides:
/// - Offline tile availability checking
/// - Simple A* routing for walking paths
/// - POI data from downloaded packs
public protocol MapService: AnyObject, Sendable {
    /// Whether offline map for Medina is available
    var isMedinaMapAvailable: Bool { get async }

    /// Whether offline map for Gueliz is available
    var isGuelizMapAvailable: Bool { get async }

    /// Current tile source being used
    var currentTileSource: TileSource { get async }

    /// Check if a region has offline tiles available
    func isOfflineAvailable(bounds: MapBounds) async -> Bool

    /// Get the path to offline tiles directory for a pack
    func getTilesPath(packId: String) -> URL?

    /// Calculate a walking route between two points
    func calculateRoute(from: MapCoordinate, to: MapCoordinate) async -> MapRoute?

    /// Estimate walking time in minutes between two points
    func estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Bool) -> Int

    /// Refresh availability status from downloaded packs
    func refreshAvailability() async
}

/// Implementation of MapService.
public final class MapServiceImpl: MapService, @unchecked Sendable {

    // MARK: - Singleton

    public static let shared = MapServiceImpl()

    // MARK: - Private Properties

    private let logger = Logger(subsystem: "com.marrakechguide", category: "MapService")
    private let lock = NSLock()

    private static let medinaPackId = "medina-map"
    private static let guelizPackId = "gueliz-map"

    // Walking speeds in meters per minute
    private static let walkSpeedDefault: Double = 75.0 // 4.5 km/h
    private static let walkSpeedMedina: Double = 50.0 // 3 km/h (denser, more stops)

    // Earth radius in meters
    private static let earthRadiusM: Double = 6_371_000.0

    private var _isMedinaMapAvailable = false
    private var _isGuelizMapAvailable = false
    private var _currentTileSource: TileSource = .placeholder

    private lazy var packsDirectory: URL = {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return paths[0].appendingPathComponent("packs", isDirectory: true)
    }()

    private let downloadService: DownloadService = DownloadServiceImpl.shared

    // MARK: - Public Properties

    public var isMedinaMapAvailable: Bool {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _isMedinaMapAvailable
        }
    }

    public var isGuelizMapAvailable: Bool {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _isGuelizMapAvailable
        }
    }

    public var currentTileSource: TileSource {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _currentTileSource
        }
    }

    // MARK: - Initialization

    private init() {
        Task {
            await checkPackAvailability()
        }
    }

    // MARK: - Public Methods

    public func isOfflineAvailable(bounds: MapBounds) async -> Bool {
        if MapBounds.medina.contains(bounds.southwest) || MapBounds.medina.contains(bounds.northeast) {
            return await isMedinaMapAvailable
        }
        if MapBounds.gueliz.contains(bounds.southwest) || MapBounds.gueliz.contains(bounds.northeast) {
            return await isGuelizMapAvailable
        }
        return false
    }

    public func getTilesPath(packId: String) -> URL? {
        let packDir = packsDirectory.appendingPathComponent(packId)
        let tilesDir = packDir.appendingPathComponent("tiles")

        var isDirectory: ObjCBool = false
        if FileManager.default.fileExists(atPath: tilesDir.path, isDirectory: &isDirectory),
           isDirectory.boolValue {
            return tilesDir
        }
        return nil
    }

    public func calculateRoute(from: MapCoordinate, to: MapCoordinate) async -> MapRoute? {
        // Simple straight-line route for now
        // In production, this would use a walking graph from the downloaded pack
        let distance = haversineDistance(from: from, to: to)

        // If points are very close, just return direct line
        if distance < 50 {
            return MapRoute(
                id: "route-\(Int(Date().timeIntervalSince1970 * 1000))",
                points: [from, to]
            )
        }

        // Generate intermediate waypoints for smoother display
        // In production, this would follow actual walking paths
        let numPoints = max(2, min(20, Int(distance / 100)))
        var points = [from]

        for i in 1..<numPoints {
            let fraction = Double(i) / Double(numPoints)
            let lat = from.latitude + (to.latitude - from.latitude) * fraction
            let lng = from.longitude + (to.longitude - from.longitude) * fraction
            points.append(MapCoordinate(latitude: lat, longitude: lng))
        }

        points.append(to)

        return MapRoute(
            id: "route-\(Int(Date().timeIntervalSince1970 * 1000))",
            points: points
        )
    }

    public func estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Bool = true) -> Int {
        let distance = haversineDistance(from: from, to: to)
        let speed = inMedina ? Self.walkSpeedMedina : Self.walkSpeedDefault
        return max(1, Int(distance / speed))
    }

    public func refreshAvailability() async {
        await checkPackAvailability()
    }

    // MARK: - Private Methods

    private func checkPackAvailability() async {
        let packStates = await downloadService.packStates

        lock.lock()
        _isMedinaMapAvailable = packStates[Self.medinaPackId]?.status == .installed
        _isGuelizMapAvailable = packStates[Self.guelizPackId]?.status == .installed

        // Update tile source based on availability
        if _isMedinaMapAvailable || _isGuelizMapAvailable {
            _currentTileSource = .offline
        } else {
            _currentTileSource = .placeholder
        }
        lock.unlock()

        logger.info("Map availability: medina=\(self._isMedinaMapAvailable), gueliz=\(self._isGuelizMapAvailable)")
    }

    /// Calculate the great-circle distance between two points using the haversine formula.
    /// Returns distance in meters.
    private func haversineDistance(from: MapCoordinate, to: MapCoordinate) -> Double {
        let lat1 = from.latitude * .pi / 180
        let lat2 = to.latitude * .pi / 180
        let deltaLat = (to.latitude - from.latitude) * .pi / 180
        let deltaLng = (to.longitude - from.longitude) * .pi / 180

        let a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
        let c = 2 * asin(sqrt(a))

        return Self.earthRadiusM * c
    }
}

// MARK: - Routing Node

/// Simple routing node for walking path calculation.
/// Used in A* pathfinding when routing graph is available.
struct RoutingNode: Sendable {
    let id: String
    let coordinate: MapCoordinate
    let neighbors: [String]

    init(id: String, coordinate: MapCoordinate, neighbors: [String] = []) {
        self.id = id
        self.coordinate = coordinate
        self.neighbors = neighbors
    }
}

/// Walking graph loaded from offline pack.
/// Contains nodes and edges for A* routing.
struct WalkingGraph: Sendable {
    let nodes: [String: RoutingNode]
    let version: String

    static func empty() -> WalkingGraph {
        WalkingGraph(nodes: [:], version: "0.0.0")
    }
}
