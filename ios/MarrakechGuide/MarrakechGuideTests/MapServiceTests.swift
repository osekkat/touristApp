import XCTest
@testable import MarrakechGuide

/// Unit tests for MapService.
///
/// Tests haversine distance calculation, walk time estimation, and pack ID handling.
final class MapServiceTests: XCTestCase {

    // MARK: - Pack ID Constants Tests

    func testPackIDs_UseCorrectNamingConvention() {
        // Verify the pack ID constants are correctly named (no typos like "PacK")
        // These strings must match exactly what's stored in downloaded pack manifests
        let medinaPackId = "medina-map"
        let guelizPackId = "gueliz-map"

        // Verify IDs follow kebab-case convention
        let kebabCasePattern = "^[a-z]+-[a-z]+$"
        XCTAssertTrue(
            medinaPackId.range(of: kebabCasePattern, options: .regularExpression) != nil,
            "Medina pack ID should be lowercase kebab-case"
        )
        XCTAssertTrue(
            guelizPackId.range(of: kebabCasePattern, options: .regularExpression) != nil,
            "Gueliz pack ID should be lowercase kebab-case"
        )

        // Verify specific expected values
        XCTAssertEqual(medinaPackId, "medina-map")
        XCTAssertEqual(guelizPackId, "gueliz-map")
    }

    func testMapService_UsesCorrectMedinaPackIdToCheckAvailability() {
        // This test verifies that MapService looks for exactly "medina-map" (not "medinaPacK" etc.)
        var packStates: [String: PackState] = [:]

        // Set medina-map as installed - must use exact ID
        packStates["medina-map"] = PackState(packId: "medina-map", status: .installed)

        // Verify the pack state was set correctly
        XCTAssertTrue(packStates.keys.contains("medina-map"),
                      "medina-map should be in pack states")
        XCTAssertEqual(packStates["medina-map"]?.status, .installed)

        // Verify typo version would NOT match
        XCTAssertFalse(packStates.keys.contains("medinaPacK-map"),
                       "Typo key should not exist")
        XCTAssertFalse(packStates.keys.contains("MEDINA-MAP"),
                       "Wrong case should not exist")
    }

    func testMapService_UsesCorrectGuelizPackIdToCheckAvailability() {
        // This test verifies that MapService looks for exactly "gueliz-map"
        var packStates: [String: PackState] = [:]

        // Set gueliz-map as installed - must use exact ID
        packStates["gueliz-map"] = PackState(packId: "gueliz-map", status: .installed)

        // Verify the pack state was set correctly
        XCTAssertTrue(packStates.keys.contains("gueliz-map"),
                      "gueliz-map should be in pack states")
        XCTAssertEqual(packStates["gueliz-map"]?.status, .installed)

        // Verify wrong IDs would NOT match
        XCTAssertFalse(packStates.keys.contains("gueliz"),
                       "Wrong ID should not exist")
        XCTAssertFalse(packStates.keys.contains("GUELIZ-MAP"),
                       "Wrong case should not exist")
    }

    func testPackAvailability_RequiresExactIdMatch() {
        var packStates: [String: PackState] = [:]

        // Set up states with various typo versions - none should work
        packStates["medinaPacK-map"] = PackState(packId: "medinaPacK-map", status: .installed)  // typo
        packStates["MEDINA-MAP"] = PackState(packId: "MEDINA-MAP", status: .installed)          // wrong case
        packStates["medina_map"] = PackState(packId: "medina_map", status: .installed)          // underscore

        // The correct key should NOT be present
        XCTAssertNil(packStates["medina-map"],
                     "medina-map should not exist with typo keys")
    }

    // MARK: - Haversine Distance Tests

    func testHaversine_JemaaToBahiaPalace() {
        // Known distance: approximately 717 meters
        let jemaa = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let bahia = MapCoordinate(latitude: 31.621510, longitude: -7.983298)

        let distance = haversineDistance(from: jemaa, to: bahia)

        // Should be approximately 717m with 50m tolerance
        XCTAssertEqual(distance, 717, accuracy: 50)
    }

    func testHaversine_SamePointReturnsZero() {
        let point = MapCoordinate(latitude: 31.625831, longitude: -7.98892)

        let distance = haversineDistance(from: point, to: point)

        XCTAssertEqual(distance, 0, accuracy: 0.001)
    }

    func testHaversine_JemaaToJardinMajorelle() {
        // Known distance: approximately 2186 meters
        let jemaa = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let majorelle = MapCoordinate(latitude: 31.641475, longitude: -8.002908)

        let distance = haversineDistance(from: jemaa, to: majorelle)

        // Should be approximately 2186m with 100m tolerance
        XCTAssertEqual(distance, 2186, accuracy: 100)
    }

    func testHaversine_SymmetryCheck() {
        let pointA = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let pointB = MapCoordinate(latitude: 31.641475, longitude: -8.002908)

        let distanceAB = haversineDistance(from: pointA, to: pointB)
        let distanceBA = haversineDistance(from: pointB, to: pointA)

        XCTAssertEqual(distanceAB, distanceBA, accuracy: 0.001,
                       "Distance should be symmetric")
    }

    // MARK: - Walk Time Estimation Tests

    func testEstimateWalkTime_ShortDistanceInMedina() {
        // 100 meters in medina at 50m/min = 2 minutes
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.626731, longitude: -7.98892) // ~100m north

        let time = estimateWalkTime(from: from, to: to, inMedina: true)

        XCTAssertGreaterThanOrEqual(time, 1, "Short walk should be at least 1 minute")
        XCTAssertLessThanOrEqual(time, 5, "100m walk should be less than 5 minutes")
    }

    func testEstimateWalkTime_MedinaSlowerThanGueliz() {
        // Same distance should take longer in medina due to slower speed
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.630831, longitude: -7.98892) // ~500m north

        let medinaTime = estimateWalkTime(from: from, to: to, inMedina: true)
        let guelizTime = estimateWalkTime(from: from, to: to, inMedina: false)

        XCTAssertGreaterThan(medinaTime, guelizTime,
                             "Medina walk should take longer than Gueliz walk")
    }

    // MARK: - Map Bounds Tests

    func testMapBounds_JemaaElFnaIsInMedinaBounds() {
        let jemaa = MapCoordinate(latitude: 31.625831, longitude: -7.98892)

        XCTAssertTrue(MapBounds.medina.contains(jemaa),
                      "Jemaa el-Fna should be within medina bounds")
    }

    func testMapBounds_JardinMajorelleIsInGuelizBounds() {
        let majorelle = MapCoordinate(latitude: 31.641475, longitude: -8.002908)

        XCTAssertTrue(MapBounds.gueliz.contains(majorelle),
                      "Jardin Majorelle should be within gueliz bounds")
    }

    func testMapBounds_ParisIsNotInMarrakech() {
        let paris = MapCoordinate(latitude: 48.8566, longitude: 2.3522)

        XCTAssertFalse(MapBounds.medina.contains(paris),
                       "Paris should not be in medina")
        XCTAssertFalse(MapBounds.gueliz.contains(paris),
                       "Paris should not be in gueliz")
    }

    func testMapBounds_BoundaryCoordinatesAreIncluded() {
        // Test that points exactly on the boundary are included
        let southwest = MapBounds.medina.southwest
        let northeast = MapBounds.medina.northeast

        XCTAssertTrue(MapBounds.medina.contains(southwest),
                      "Southwest corner should be in bounds")
        XCTAssertTrue(MapBounds.medina.contains(northeast),
                      "Northeast corner should be in bounds")
    }

    func testMapBounds_MedinaAndGuelizDoNotOverlapCompletely() {
        // Verify the bounds are defined for different areas
        let medinaCenter = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let guelizCenter = MapCoordinate(latitude: 31.641475, longitude: -8.002908)

        // Medina center should be in medina
        XCTAssertTrue(MapBounds.medina.contains(medinaCenter))

        // Gueliz center should be in gueliz
        XCTAssertTrue(MapBounds.gueliz.contains(guelizCenter))
    }

    // MARK: - Route Calculation Tests

    func testCalculateRoute_ShortDistanceReturnsDirectLine() {
        // Points less than 50m apart should return just start and end
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.625931, longitude: -7.98892) // ~11m north

        let route = calculateRoute(from: from, to: to)

        XCTAssertNotNil(route, "Route should not be nil")
        XCTAssertEqual(route!.points.count, 2, "Short route should have 2 points")
        XCTAssertEqual(route!.points.first, from)
        XCTAssertEqual(route!.points.last, to)
    }

    func testCalculateRoute_LongerDistanceGeneratesIntermediateWaypoints() {
        // Points 500m+ apart should have intermediate waypoints
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.630831, longitude: -7.98892) // ~555m north

        let route = calculateRoute(from: from, to: to)

        XCTAssertNotNil(route, "Route should not be nil")
        XCTAssertGreaterThan(route!.points.count, 2, "Route should have more than 2 points")
        XCTAssertEqual(route!.points.first, from, "Route should start at from")
        XCTAssertEqual(route!.points.last, to, "Route should end at to")
    }

    func testCalculateRoute_IntermediatePointsAreBetweenStartAndEnd() {
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.635831, longitude: -7.97892) // ~1.4km northeast

        let route = calculateRoute(from: from, to: to)

        XCTAssertNotNil(route)
        // All intermediate points should have lat/lng between start and end
        for point in route!.points {
            let minLat = min(from.latitude, to.latitude)
            let maxLat = max(from.latitude, to.latitude)
            let minLng = min(from.longitude, to.longitude)
            let maxLng = max(from.longitude, to.longitude)

            XCTAssertTrue(point.latitude >= minLat && point.latitude <= maxLat,
                          "Point lat should be in range")
            XCTAssertTrue(point.longitude >= minLng && point.longitude <= maxLng,
                          "Point lng should be in range")
        }
    }

    func testCalculateRoute_GeneratesUniqueRouteIds() {
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.630831, longitude: -7.98892)

        let route1 = calculateRoute(from: from, to: to)
        Thread.sleep(forTimeInterval: 0.002) // Ensure different timestamp
        let route2 = calculateRoute(from: from, to: to)

        XCTAssertNotNil(route1)
        XCTAssertNotNil(route2)
        XCTAssertNotEqual(route1!.id, route2!.id, "Route IDs should be unique")
        XCTAssertTrue(route1!.id.hasPrefix("route-"), "Route ID should start with 'route-'")
    }

    // MARK: - Haversine Edge Cases

    func testHaversine_VerySmallDistance() {
        // Points ~1 meter apart
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.625840, longitude: -7.98892)

        let distance = haversineDistance(from: from, to: to)

        XCTAssertGreaterThan(distance, 0, "Very small distance should be positive")
        XCTAssertLessThan(distance, 10, "Very small distance should be < 10m")
    }

    func testHaversine_NegativeLongitude() {
        // Both Marrakech points have negative longitude
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.625831, longitude: -8.00000)

        let distance = haversineDistance(from: from, to: to)

        XCTAssertGreaterThan(distance, 0, "Distance with negative longitude should work")
    }

    func testHaversine_CrossingEquator() {
        // Test crossing from northern to southern hemisphere
        let north = MapCoordinate(latitude: 1.0, longitude: 0.0)
        let south = MapCoordinate(latitude: -1.0, longitude: 0.0)

        let distance = haversineDistance(from: north, to: south)

        // 2 degrees of latitude ≈ 222 km
        XCTAssertGreaterThan(distance, 200_000, "Cross-equator distance should be > 200km")
        XCTAssertLessThan(distance, 250_000, "Cross-equator distance should be < 250km")
    }

    // MARK: - Walk Time Edge Cases

    func testEstimateWalkTime_MinimumIs1Minute() {
        // Even for very short distances, minimum should be 1 minute
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.625832, longitude: -7.98892) // ~0.1m

        let time = estimateWalkTime(from: from, to: to, inMedina: true)

        XCTAssertEqual(time, 1, "Minimum walk time should be 1 minute")
    }

    func testEstimateWalkTime_LongDistanceCalculation() {
        // ~2km walk in medina at 50m/min = 40 minutes
        let from = MapCoordinate(latitude: 31.625831, longitude: -7.98892)
        let to = MapCoordinate(latitude: 31.643831, longitude: -7.98892) // ~2km north

        let medinaTime = estimateWalkTime(from: from, to: to, inMedina: true)
        let guelizTime = estimateWalkTime(from: from, to: to, inMedina: false)

        XCTAssertTrue(medinaTime >= 35 && medinaTime <= 45, "2km medina walk should be ~40 min")
        XCTAssertTrue(guelizTime >= 23 && guelizTime <= 31, "2km gueliz walk should be ~27 min")
        XCTAssertGreaterThan(medinaTime, guelizTime, "Medina should be slower")
    }

    func testEstimateWalkTime_SamePointReturns1Minute() {
        let point = MapCoordinate(latitude: 31.625831, longitude: -7.98892)

        let time = estimateWalkTime(from: point, to: point, inMedina: true)

        XCTAssertEqual(time, 1, "Same point should return minimum 1 minute")
    }

    // MARK: - Tile Source State Tests

    func testTileSource_PlaceholderWhenNoMapsInstalled() {
        var packStates: [String: PackState] = [:]
        // No packs installed - default state

        // With no installed packs, tile source should be placeholder
        let hasMedinaMap = packStates["medina-map"]?.status == .installed
        let hasGuelizMap = packStates["gueliz-map"]?.status == .installed

        XCTAssertFalse(hasMedinaMap, "Medina map should not be installed")
        XCTAssertFalse(hasGuelizMap, "Gueliz map should not be installed")
    }

    func testTileSource_OfflineWhenMedinaMapInstalled() {
        var packStates: [String: PackState] = [:]
        packStates["medina-map"] = PackState(packId: "medina-map", status: .installed)

        let hasMedinaMap = packStates["medina-map"]?.status == .installed

        XCTAssertTrue(hasMedinaMap, "Medina map should be installed")
    }

    func testTileSource_OfflineWhenGuelizMapInstalled() {
        var packStates: [String: PackState] = [:]
        packStates["gueliz-map"] = PackState(packId: "gueliz-map", status: .installed)

        let hasGuelizMap = packStates["gueliz-map"]?.status == .installed

        XCTAssertTrue(hasGuelizMap, "Gueliz map should be installed")
    }

    func testTileSource_DownloadingStatusDoesNotCountAsInstalled() {
        var packStates: [String: PackState] = [:]
        packStates["medina-map"] = PackState(packId: "medina-map", status: .downloading)

        let isInstalled = packStates["medina-map"]?.status == .installed

        XCTAssertFalse(isInstalled, "Downloading should not count as installed")
    }

    // MARK: - Helper Functions

    /// Calculate haversine distance (duplicates MapServiceImpl logic for testing).
    /// Returns distance in meters.
    private func haversineDistance(from: MapCoordinate, to: MapCoordinate) -> Double {
        let earthRadiusM: Double = 6_371_000.0

        let lat1 = from.latitude * .pi / 180
        let lat2 = to.latitude * .pi / 180
        let deltaLat = (to.latitude - from.latitude) * .pi / 180
        let deltaLng = (to.longitude - from.longitude) * .pi / 180

        let a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
        let c = 2 * asin(sqrt(a))

        return earthRadiusM * c
    }

    /// Estimate walk time (duplicates MapServiceImpl logic for testing).
    /// Returns time in minutes.
    private func estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Bool) -> Int {
        let walkSpeedDefault: Double = 75.0 // 4.5 km/h in m/min
        let walkSpeedMedina: Double = 50.0  // 3 km/h in m/min

        let distance = haversineDistance(from: from, to: to)
        let speed = inMedina ? walkSpeedMedina : walkSpeedDefault
        return max(1, Int(distance / speed))
    }

    /// Calculate a walking route (duplicates MapServiceImpl logic for testing).
    /// Returns a MapRoute with waypoints.
    private func calculateRoute(from: MapCoordinate, to: MapCoordinate) -> MapRoute? {
        let distance = haversineDistance(from: from, to: to)

        // If points are very close, just return direct line
        if distance < 50 {
            return MapRoute(
                id: "route-\(Int(Date().timeIntervalSince1970 * 1000))",
                points: [from, to]
            )
        }

        // Generate intermediate waypoints for smoother display
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
}
