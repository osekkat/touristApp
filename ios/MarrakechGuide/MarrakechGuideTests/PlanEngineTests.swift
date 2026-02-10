import XCTest
@testable import MarrakechGuide

/// Unit tests for PlanEngine using shared cross-platform vectors.
///
/// Test vectors are loaded from: shared/tests/plan-engine-vectors.json
/// Both iOS and Android must pass the same vector suite.
final class PlanEngineTests: XCTestCase {

    private var vectorFile: VectorFile!
    private let isoFormatter = ISO8601DateFormatter()

    override func setUpWithError() throws {
        try super.setUpWithError()
        vectorFile = try loadVectors()
    }

    func testPlanEngine_AllVectors() throws {
        for vector in vectorFile.vectors {
            try runVector(vector)
        }
    }

    func testPlanEngine_ShortHistoryDay() throws {
        guard let vector = vectorFile.vectors.first(where: { $0.name == "Short history day" }) else {
            XCTFail("Vector not found: Short history day")
            return
        }

        try runVector(vector)
    }

    func testPlanEngine_ImpossibleConstraints() throws {
        guard let vector = vectorFile.vectors.first(where: { $0.name == "Impossible constraints" }) else {
            XCTFail("Vector not found: Impossible constraints")
            return
        }

        try runVector(vector)
    }

    func testPlanEngine_PreservesDinnerStopWhenNearestNeighborOrderWouldDropIt() throws {
        guard let currentTime = isoFormatter.date(from: "2026-02-10T08:00:00Z") else {
            XCTFail("Failed to parse currentTime")
            return
        }

        let output = PlanEngine.generate(
            PlanEngine.Input(
                availableMinutes: 720,
                startPoint: PlanEngine.Coordinate(lat: 31.6238, lng: -7.9859),
                interests: [.food],
                pace: .relaxed,
                budgetTier: .mid,
                currentTime: currentTime,
                places: [
                    makeFoodPlace(
                        id: "breakfast-cafe",
                        name: "Breakfast Cafe",
                        category: "cafe",
                        lat: 31.6249,
                        lng: -7.9877,
                        visitMin: 45,
                        visitMax: 60,
                        windows: ["morning"],
                        tags: ["food", "breakfast", "budget"],
                        hours: ["daily 07:00-22:00"]
                    ),
                    makeFoodPlace(
                        id: "lunch-bistro",
                        name: "Lunch Bistro",
                        category: "restaurant",
                        lat: 31.6269,
                        lng: -7.9925,
                        visitMin: 60,
                        visitMax: 75,
                        windows: ["lunch", "afternoon"],
                        tags: ["food", "lunch"],
                        hours: ["daily 11:00-23:00"]
                    ),
                    makeFoodPlace(
                        id: "dinner-riad",
                        name: "Dinner Riad",
                        category: "restaurant",
                        lat: 31.6238,
                        lng: -7.9859,
                        visitMin: 75,
                        visitMax: 90,
                        windows: ["evening"],
                        tags: ["food", "dinner", "luxury"],
                        hours: ["daily 12:00-23:59"]
                    ),
                    makeFoodPlace(
                        id: "snack-market",
                        name: "Snack Market",
                        category: "market",
                        lat: 31.6277,
                        lng: -7.9903,
                        visitMin: 30,
                        visitMax: 45,
                        windows: ["afternoon", "evening"],
                        tags: ["food", "shopping"],
                        hours: ["daily 09:00-21:00"]
                    )
                ]
            )
        )

        let selectedIds = Set(output.stops.map(\.placeId))
        XCTAssertTrue(selectedIds.contains("breakfast-cafe"), "Expected breakfast stop")
        XCTAssertTrue(selectedIds.contains("lunch-bistro"), "Expected lunch stop")
        XCTAssertTrue(selectedIds.contains("dinner-riad"), "Expected dinner stop")
        XCTAssertFalse(
            output.warnings.contains { $0.localizedCaseInsensitiveContains("Could not schedule meal stop") },
            "Unexpected missing meal warning: \(output.warnings)"
        )
    }

    // MARK: - Vector Runner

    private func runVector(_ vector: VectorCase) throws {
        guard let currentTime = isoFormatter.date(from: vector.input.currentTime),
              let pace = PlanEngine.Pace.from(vector.input.pace),
              let budgetTier = PlanEngine.BudgetTier.from(vector.input.budgetTier)
        else {
            XCTFail("Invalid input setup for vector: \(vector.name)")
            return
        }

        let startPoint = vector.input.startPoint.map {
            PlanEngine.Coordinate(lat: $0.lat, lng: $0.lng)
        }

        let interests = vector.input.interests.compactMap(PlanEngine.Interest.from)
        let places = vector.input.places.map { $0.toPlace() }
        let recents = Set(vector.input.recentPlaceIds ?? [])

        let output = PlanEngine.generate(
            PlanEngine.Input(
                availableMinutes: vector.input.availableMinutes,
                startPoint: startPoint,
                interests: interests,
                pace: pace,
                budgetTier: budgetTier,
                currentTime: currentTime,
                places: places,
                recentPlaceIds: recents
            )
        )

        let expected = vector.expected
        XCTAssertTrue(
            output.stops.count >= expected.minStops,
            "\(vector.name): expected at least \(expected.minStops) stops, got \(output.stops.count)"
        )
        XCTAssertTrue(
            output.stops.count <= expected.maxStops,
            "\(vector.name): expected at most \(expected.maxStops) stops, got \(output.stops.count)"
        )
        XCTAssertLessThanOrEqual(
            output.totalMinutes,
            expected.maxTotalMinutes,
            "\(vector.name): total minutes exceeded max"
        )

        let selectedIds = Set(output.stops.map(\.placeId))
        let categoryById = Dictionary(uniqueKeysWithValues: places.compactMap { place in
            place.category.map { (place.id, $0.lowercased()) }
        })
        let allowedCategories = Set(expected.allowedCategories.map { $0.lowercased() })

        for requiredId in expected.requiredPlaceIds {
            XCTAssertTrue(selectedIds.contains(requiredId), "\(vector.name): missing required stop \(requiredId)")
        }

        for excludedId in expected.excludedPlaceIds {
            XCTAssertFalse(selectedIds.contains(excludedId), "\(vector.name): excluded stop present \(excludedId)")
        }

        if !allowedCategories.isEmpty {
            for stopId in selectedIds {
                if let category = categoryById[stopId] {
                    XCTAssertTrue(
                        allowedCategories.contains(category),
                        "\(vector.name): stop \(stopId) has unexpected category \(category)"
                    )
                }
            }
        }

        let warningBlob = output.warnings.joined(separator: "\n").lowercased()
        for requiredWarning in expected.requiredWarningSubstrings {
            XCTAssertTrue(
                warningBlob.contains(requiredWarning.lowercased()),
                "\(vector.name): missing warning substring '\(requiredWarning)'"
            )
        }
    }

    // MARK: - Vector Loading

    private func loadVectors() throws -> VectorFile {
        let possiblePaths = [
            "../../../../shared/tests/plan-engine-vectors.json",
            "../../../shared/tests/plan-engine-vectors.json",
            "../../shared/tests/plan-engine-vectors.json",
            "../shared/tests/plan-engine-vectors.json"
        ]

        var data: Data?

        if let bundleURL = Bundle(for: type(of: self)).url(forResource: "plan-engine-vectors", withExtension: "json") {
            data = try? Data(contentsOf: bundleURL)
        }

        if data == nil {
            for path in possiblePaths {
                let url = URL(fileURLWithPath: path)
                if FileManager.default.fileExists(atPath: url.path) {
                    data = try? Data(contentsOf: url)
                    if data != nil { break }
                }
            }
        }

        if data == nil,
           let envPath = ProcessInfo.processInfo.environment["PLAN_ENGINE_VECTORS_PATH"]
        {
            data = try? Data(contentsOf: URL(fileURLWithPath: envPath))
        }

        guard let vectorData = data else {
            throw NSError(domain: "PlanEngineTests", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Could not find plan-engine-vectors.json"
            ])
        }

        return try JSONDecoder().decode(VectorFile.self, from: vectorData)
    }

    private func makeFoodPlace(
        id: String,
        name: String,
        category: String,
        lat: Double,
        lng: Double,
        visitMin: Int,
        visitMax: Int,
        windows: [String],
        tags: [String],
        hours: [String]
    ) -> Place {
        Place(
            id: id,
            name: name,
            aliases: nil,
            regionId: "medina",
            category: category,
            shortDescription: nil,
            longDescription: nil,
            reviewedAt: nil,
            status: nil,
            confidence: nil,
            touristTrapLevel: "low",
            whyRecommended: nil,
            neighborhood: nil,
            address: nil,
            lat: lat,
            lng: lng,
            hoursText: nil,
            hoursWeekly: hours,
            hoursVerifiedAt: nil,
            feesMinMad: nil,
            feesMaxMad: nil,
            expectedCostMinMad: nil,
            expectedCostMaxMad: nil,
            visitMinMinutes: visitMin,
            visitMaxMinutes: visitMax,
            bestTimeToGo: nil,
            bestTimeWindows: windows,
            tags: tags,
            localTips: nil,
            scamWarnings: nil,
            doAndDont: nil,
            images: nil,
            sourceRefs: nil
        )
    }
}

// MARK: - Vector Models

private struct VectorFile: Decodable {
    let vectors: [VectorCase]
}

private struct VectorCase: Decodable {
    let name: String
    let input: VectorInput
    let expected: VectorExpected
}

private struct VectorInput: Decodable {
    let availableMinutes: Int
    let startPoint: VectorCoordinate?
    let interests: [String]
    let pace: String
    let budgetTier: String
    let currentTime: String
    let recentPlaceIds: [String]?
    let places: [VectorPlace]
}

private struct VectorCoordinate: Decodable {
    let lat: Double
    let lng: Double
}

private struct VectorPlace: Decodable {
    let id: String
    let name: String
    let category: String?
    let regionId: String?
    let lat: Double?
    let lng: Double?
    let visitMinMinutes: Int?
    let visitMaxMinutes: Int?
    let bestTimeWindows: [String]?
    let tags: [String]?
    let touristTrapLevel: String?
    let hoursWeekly: [String]?
    let hoursText: String?

    func toPlace() -> Place {
        Place(
            id: id,
            name: name,
            aliases: nil,
            regionId: regionId,
            category: category,
            shortDescription: nil,
            longDescription: nil,
            reviewedAt: nil,
            status: nil,
            confidence: nil,
            touristTrapLevel: touristTrapLevel,
            whyRecommended: nil,
            neighborhood: nil,
            address: nil,
            lat: lat,
            lng: lng,
            hoursText: hoursText,
            hoursWeekly: hoursWeekly,
            hoursVerifiedAt: nil,
            feesMinMad: nil,
            feesMaxMad: nil,
            expectedCostMinMad: nil,
            expectedCostMaxMad: nil,
            visitMinMinutes: visitMinMinutes,
            visitMaxMinutes: visitMaxMinutes,
            bestTimeToGo: nil,
            bestTimeWindows: bestTimeWindows,
            tags: tags,
            localTips: nil,
            scamWarnings: nil,
            doAndDont: nil,
            images: nil,
            sourceRefs: nil
        )
    }
}

private struct VectorExpected: Decodable {
    let minStops: Int
    let maxStops: Int
    let maxTotalMinutes: Int
    let allowedCategories: [String]
    let requiredPlaceIds: [String]
    let excludedPlaceIds: [String]
    let requiredWarningSubstrings: [String]
}
