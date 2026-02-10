import Foundation
import CoreLocation

// MARK: - Map Coordinate

/// Represents a geographic coordinate.
struct MapCoordinate: Codable, Sendable, Hashable {
    let latitude: Double
    let longitude: Double

    /// Convert to CoreLocation coordinate
    var clLocationCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }

    init(_ coordinate: CLLocationCoordinate2D) {
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
    }
}

extension MapCoordinate {
    /// Jemaa el-Fna center - the heart of the Medina
    static let medinaCenter = MapCoordinate(latitude: 31.6259, longitude: -7.9891)

    /// Default zoom level for Medina view
    static let defaultZoom: Double = 15.0

    /// Maximum zoom level for detailed view
    static let maxZoom: Double = 20.0

    /// Minimum zoom level
    static let minZoom: Double = 10.0
}

// MARK: - Map Bounds

/// Represents a bounding box for map regions.
struct MapBounds: Sendable {
    let southwest: MapCoordinate
    let northeast: MapCoordinate

    /// Check if a coordinate is within these bounds
    func contains(_ coordinate: MapCoordinate) -> Bool {
        coordinate.latitude >= southwest.latitude &&
        coordinate.latitude <= northeast.latitude &&
        coordinate.longitude >= southwest.longitude &&
        coordinate.longitude <= northeast.longitude
    }
}

extension MapBounds {
    /// Marrakech Medina bounds - the old walled city
    static let medina = MapBounds(
        southwest: MapCoordinate(latitude: 31.6150, longitude: -8.0050),
        northeast: MapCoordinate(latitude: 31.6400, longitude: -7.9700)
    )

    /// Gueliz/new city bounds
    static let gueliz = MapBounds(
        southwest: MapCoordinate(latitude: 31.6280, longitude: -8.0200),
        northeast: MapCoordinate(latitude: 31.6480, longitude: -7.9900)
    )
}

// MARK: - Map Camera

/// Represents the current camera/view state of the map.
struct MapCamera: Sendable {
    let center: MapCoordinate
    let zoom: Double
    let bearing: Double
    let tilt: Double

    init(
        center: MapCoordinate,
        zoom: Double = MapCoordinate.defaultZoom,
        bearing: Double = 0,
        tilt: Double = 0
    ) {
        self.center = center
        self.zoom = zoom
        self.bearing = bearing
        self.tilt = tilt
    }

    static let `default` = MapCamera(center: .medinaCenter)
}

// MARK: - Map Marker

/// A marker to display on the map.
struct MapMarker: Identifiable, Sendable {
    let id: String
    let coordinate: MapCoordinate
    let title: String
    let subtitle: String?
    let category: MarkerCategory
    let isSelected: Bool

    init(
        id: String,
        coordinate: MapCoordinate,
        title: String,
        subtitle: String? = nil,
        category: MarkerCategory = .general,
        isSelected: Bool = false
    ) {
        self.id = id
        self.coordinate = coordinate
        self.title = title
        self.subtitle = subtitle
        self.category = category
        self.isSelected = isSelected
    }
}

// MARK: - Marker Category

/// Categories for map markers with associated styling.
enum MarkerCategory: String, Sendable {
    case general
    case landmark
    case restaurant
    case cafe
    case shopping
    case museum
    case hotel
    case userLocation
    case homeBase
    case routeStop
}

// MARK: - Map Route

/// A route to display on the map.
struct MapRoute: Identifiable, Sendable {
    let id: String
    let points: [MapCoordinate]
    let color: Int?
    let width: Float

    init(
        id: String,
        points: [MapCoordinate],
        color: Int? = nil,
        width: Float = 4
    ) {
        self.id = id
        self.points = points
        self.color = color
        self.width = width
    }
}

// MARK: - Map View State

/// Current state of the map view.
struct MapViewState: Sendable {
    var camera: MapCamera
    var markers: [MapMarker]
    var routes: [MapRoute]
    var userLocation: MapCoordinate?
    var userHeading: Double?
    var isOfflineMapAvailable: Bool
    var isLoading: Bool
    var error: String?

    init(
        camera: MapCamera = .default,
        markers: [MapMarker] = [],
        routes: [MapRoute] = [],
        userLocation: MapCoordinate? = nil,
        userHeading: Double? = nil,
        isOfflineMapAvailable: Bool = false,
        isLoading: Bool = false,
        error: String? = nil
    ) {
        self.camera = camera
        self.markers = markers
        self.routes = routes
        self.userLocation = userLocation
        self.userHeading = userHeading
        self.isOfflineMapAvailable = isOfflineMapAvailable
        self.isLoading = isLoading
        self.error = error
    }
}

// MARK: - Tile Source

/// Map tile source types.
enum TileSource: Sendable {
    /// Online tiles from map provider
    case online
    /// Offline tiles from downloaded pack
    case offline
    /// Placeholder when no tiles available
    case placeholder
}

// MARK: - Map Config

/// Configuration for the map view.
struct MapConfig: Sendable {
    let showUserLocation: Bool
    let showCompass: Bool
    let showZoomControls: Bool
    let allowRotation: Bool
    let allowTilt: Bool
    let clusterMarkers: Bool
    let clusterRadius: Int

    init(
        showUserLocation: Bool = true,
        showCompass: Bool = true,
        showZoomControls: Bool = false,
        allowRotation: Bool = true,
        allowTilt: Bool = false,
        clusterMarkers: Bool = true,
        clusterRadius: Int = 50
    ) {
        self.showUserLocation = showUserLocation
        self.showCompass = showCompass
        self.showZoomControls = showZoomControls
        self.allowRotation = allowRotation
        self.allowTilt = allowTilt
        self.clusterMarkers = clusterMarkers
        self.clusterRadius = clusterRadius
    }
}
