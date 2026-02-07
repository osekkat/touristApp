import XCTest
import CoreLocation
@testable import MarrakechGuide

/// Unit tests for GeoEngine using shared test vectors.
///
/// Test vectors are loaded from: shared/tests/geo-engine-vectors.json
/// Both iOS and Android must pass all tests with the same vectors.
final class GeoEngineTests: XCTestCase {

    private var testVectors: [String: Any]!

    override func setUpWithError() throws {
        try super.setUpWithError()
        testVectors = try loadTestVectors()
    }

    // MARK: - Test Vector Loading

    private func loadTestVectors() throws -> [String: Any] {
        // Try multiple paths to find the test vectors
        let possiblePaths = [
            "../../../../shared/tests/geo-engine-vectors.json",
            "../../../shared/tests/geo-engine-vectors.json",
            "../../shared/tests/geo-engine-vectors.json",
            "../shared/tests/geo-engine-vectors.json"
        ]

        var jsonData: Data?

        // First try bundle resource
        if let bundleURL = Bundle(for: type(of: self)).url(forResource: "geo-engine-vectors", withExtension: "json") {
            jsonData = try? Data(contentsOf: bundleURL)
        }

        // Then try file paths relative to test directory
        if jsonData == nil {
            for path in possiblePaths {
                let url = URL(fileURLWithPath: path)
                if FileManager.default.fileExists(atPath: url.path) {
                    jsonData = try? Data(contentsOf: url)
                    if jsonData != nil { break }
                }
            }
        }

        // Try environment variable path
        if jsonData == nil, let envPath = ProcessInfo.processInfo.environment["GEO_ENGINE_VECTORS_PATH"] {
            jsonData = try? Data(contentsOf: URL(fileURLWithPath: envPath))
        }

        guard let data = jsonData else {
            throw NSError(domain: "GeoEngineTests", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Could not find geo-engine-vectors.json"
            ])
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "GeoEngineTests", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Invalid JSON format"
            ])
        }

        return json
    }

    // MARK: - Haversine Tests

    func testHaversine_JemaaToBAhia() throws {
        let vectors = testVectors["haversine"] as! [[String: Any]]
        let test = findVector(in: vectors, named: "Jemaa el-Fna to Bahia Palace")!

        let input = test["input"] as! [String: Any]
        let from = (input["from"] as! [String: Double]).toCoordinate()
        let to = (input["to"] as! [String: Double]).toCoordinate()

        let expected = test["expected"] as! [String: Double]
        let expectedMeters = expected["distance_meters"]!
        let tolerance = expected["tolerance_meters"]!

        let result = GeoEngine.haversine(from: from, to: to)

        XCTAssertEqual(result, expectedMeters, accuracy: tolerance,
                       "Distance from Jemaa el-Fna to Bahia Palace")
    }

    func testHaversine_SamePoint() throws {
        let vectors = testVectors["haversine"] as! [[String: Any]]
        let test = findVector(in: vectors, named: "Same point")!

        let input = test["input"] as! [String: Any]
        let from = (input["from"] as! [String: Double]).toCoordinate()
        let to = (input["to"] as! [String: Double]).toCoordinate()

        let result = GeoEngine.haversine(from: from, to: to)

        XCTAssertEqual(result, 0, accuracy: 1.0, "Same point should return 0")
    }

    func testHaversine_AllVectors() throws {
        let vectors = testVectors["haversine"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let from = (input["from"] as! [String: Double]).toCoordinate()
            let to = (input["to"] as! [String: Double]).toCoordinate()

            let expected = test["expected"] as! [String: Double]
            let expectedMeters = expected["distance_meters"]!
            let tolerance = expected["tolerance_meters"]!

            let result = GeoEngine.haversine(from: from, to: to)

            XCTAssertEqual(result, expectedMeters, accuracy: tolerance,
                           "Haversine test: \(name)")
        }
    }

    // MARK: - Bearing Tests

    func testBearing_DueNorth() throws {
        let vectors = testVectors["bearing"] as! [[String: Any]]
        let test = findVector(in: vectors, named: "Due North")!

        let input = test["input"] as! [String: Any]
        let from = (input["from"] as! [String: Double]).toCoordinate()
        let to = (input["to"] as! [String: Double]).toCoordinate()

        let expected = test["expected"] as! [String: Double]
        let expectedDegrees = expected["bearing_degrees"]!
        let tolerance = expected["tolerance_degrees"]!

        let result = GeoEngine.bearing(from: from, to: to)

        XCTAssertEqual(result, expectedDegrees, accuracy: tolerance,
                       "Bearing due north")
    }

    func testBearing_AllCardinalDirections() throws {
        let vectors = testVectors["bearing"] as! [[String: Any]]
        let cardinalTests = ["Due North", "Due East", "Due South", "Due West"]

        for testName in cardinalTests {
            let test = findVector(in: vectors, named: testName)!

            let input = test["input"] as! [String: Any]
            let from = (input["from"] as! [String: Double]).toCoordinate()
            let to = (input["to"] as! [String: Double]).toCoordinate()

            let expected = test["expected"] as! [String: Double]
            let expectedDegrees = expected["bearing_degrees"]!
            let tolerance = expected["tolerance_degrees"]!

            let result = GeoEngine.bearing(from: from, to: to)

            XCTAssertEqual(result, expectedDegrees, accuracy: tolerance,
                           "Bearing test: \(testName)")
        }
    }

    func testBearing_AllVectors() throws {
        let vectors = testVectors["bearing"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let from = (input["from"] as! [String: Double]).toCoordinate()
            let to = (input["to"] as! [String: Double]).toCoordinate()

            let expected = test["expected"] as! [String: Double]
            let expectedDegrees = expected["bearing_degrees"]!
            let tolerance = expected["tolerance_degrees"]!

            let result = GeoEngine.bearing(from: from, to: to)

            XCTAssertEqual(result, expectedDegrees, accuracy: tolerance,
                           "Bearing test: \(name)")
        }
    }

    // MARK: - Relative Angle Tests

    func testRelativeAngle_FacingNorthTargetEast() throws {
        let vectors = testVectors["relativeAngle"] as! [[String: Any]]
        let test = findVector(in: vectors, named: "Facing north, target east")!

        let input = test["input"] as! [String: Any]
        let bearing = input["bearing"] as! Double
        let heading = input["heading"] as! Double

        let expected = test["expected"] as! [String: Double]
        let expectedAngle = expected["relative_angle"]!

        let result = GeoEngine.relativeAngle(targetBearing: bearing, deviceHeading: heading)

        XCTAssertEqual(result, expectedAngle, accuracy: 0.001)
    }

    func testRelativeAngle_AllVectors() throws {
        let vectors = testVectors["relativeAngle"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let bearing = input["bearing"] as! Double
            let heading = input["heading"] as! Double

            let expected = test["expected"] as! [String: Double]
            let expectedAngle = expected["relative_angle"]!

            let result = GeoEngine.relativeAngle(targetBearing: bearing, deviceHeading: heading)

            XCTAssertEqual(result, expectedAngle, accuracy: 0.001,
                           "RelativeAngle test: \(name)")
        }
    }

    // MARK: - Format Distance Tests

    func testFormatDistance_AllVectors() throws {
        let vectors = testVectors["formatDistance"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let meters = input["meters"] as! Double

            let expected = test["expected"] as! [String: String]
            let expectedFormatted = expected["formatted"]!

            let result = GeoEngine.formatDistance(meters)

            XCTAssertEqual(result, expectedFormatted,
                           "FormatDistance test: \(name)")
        }
    }

    // MARK: - Estimate Walk Time Tests

    func testEstimateWalkTime_AllVectors() throws {
        let vectors = testVectors["estimateWalkTime"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let meters = input["meters"] as! Double
            let region = input["region"] as! String

            let expected = test["expected"] as! [String: Int]
            let expectedMinutes = expected["minutes"]!
            let tolerance = expected["tolerance_minutes"]!

            let result = GeoEngine.estimateWalkTime(meters: meters, region: region)

            XCTAssertTrue(
                (expectedMinutes - tolerance)...(expectedMinutes + tolerance) ~= result,
                "EstimateWalkTime test: \(name) - expected \(expectedMinutes) (+/- \(tolerance)), got \(result)"
            )
        }
    }

    // MARK: - Additional Unit Tests

    func testIsWithinMarrakech_JemaaElFna() {
        let coord = CLLocationCoordinate2D(latitude: 31.625831, longitude: -7.98892)
        XCTAssertTrue(GeoEngine.isWithinMarrakech(coord))
    }

    func testIsWithinMarrakech_Paris() {
        let coord = CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522)
        XCTAssertFalse(GeoEngine.isWithinMarrakech(coord))
    }

    func testDetermineRegion_JemaaElFna() {
        let coord = CLLocationCoordinate2D(latitude: 31.625831, longitude: -7.98892)
        XCTAssertEqual(GeoEngine.determineRegion(coord), "medina")
    }

    func testDetermineRegion_JardinMajorelle() {
        let coord = CLLocationCoordinate2D(latitude: 31.641475, longitude: -8.002908)
        XCTAssertEqual(GeoEngine.determineRegion(coord), "gueliz")
    }

    // MARK: - Helper Functions

    private func findVector(in vectors: [[String: Any]], named name: String) -> [String: Any]? {
        vectors.first { ($0["name"] as? String) == name }
    }
}

// MARK: - Dictionary Extension

private extension Dictionary where Key == String, Value == Double {
    func toCoordinate() -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(
            latitude: self["lat"]!,
            longitude: self["lng"]!
        )
    }
}
