import XCTest
@testable import MarrakechGuide

/// Unit tests for PricingEngine using shared test vectors.
///
/// Test vectors are loaded from: shared/tests/pricing-engine-vectors.json
/// Both iOS and Android must pass all tests with the same vectors.
final class PricingEngineTests: XCTestCase {

    private var testVectors: [String: Any]!
    private var tolerance: Double = 0.01

    override func setUpWithError() throws {
        try super.setUpWithError()
        let vectors = try loadTestVectors()
        testVectors = vectors

        // Extract tolerance from meta
        if let toleranceInfo = vectors["tolerance"] as? [String: Any],
           let absTolerance = toleranceInfo["absoluteTolerance"] as? Double {
            tolerance = absTolerance
        }
    }

    // MARK: - Test Vector Loading

    private func loadTestVectors() throws -> [String: Any] {
        let possiblePaths = [
            "../../../../shared/tests/pricing-engine-vectors.json",
            "../../../shared/tests/pricing-engine-vectors.json",
            "../../shared/tests/pricing-engine-vectors.json",
            "../shared/tests/pricing-engine-vectors.json"
        ]

        var jsonData: Data?

        // First try bundle resource
        if let bundleURL = Bundle(for: type(of: self)).url(forResource: "pricing-engine-vectors", withExtension: "json") {
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
        if jsonData == nil, let envPath = ProcessInfo.processInfo.environment["PRICING_ENGINE_VECTORS_PATH"] {
            jsonData = try? Data(contentsOf: URL(fileURLWithPath: envPath))
        }

        guard let data = jsonData else {
            throw NSError(domain: "PricingEngineTests", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Could not find pricing-engine-vectors.json"
            ])
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "PricingEngineTests", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Invalid JSON format"
            ])
        }

        return json
    }

    // MARK: - All Vectors Test

    func testPricingEngine_AllVectors() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]

        for test in vectors {
            let name = test["name"] as! String
            let input = test["input"] as! [String: Any]
            let expected = test["expected"] as! [String: Any]

            // Build modifiers
            var modifiers: [PricingEngine.ContextModifier] = []
            if let modifiersData = input["modifiers"] as? [[String: Any]] {
                for modData in modifiersData {
                    modifiers.append(PricingEngine.ContextModifier(
                        factorMin: modData["factorMin"] as! Double,
                        factorMax: modData["factorMax"] as! Double
                    ))
                }
            }

            let engineInput = PricingEngine.Input(
                expectedCostMinMad: input["expectedCostMinMad"] as! Double,
                expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
                quotedMad: input["quotedMad"] as! Double,
                modifiers: modifiers,
                quantity: input["quantity"] as? Int ?? 1,
                fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
                fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
            )

            let result = PricingEngine.evaluate(engineInput)

            // Check adjusted range
            let expectedAdjustedMin = expected["adjustedMin"] as! Double
            let expectedAdjustedMax = expected["adjustedMax"] as! Double
            XCTAssertEqual(result.adjustedMin, expectedAdjustedMin, accuracy: tolerance,
                           "\(name): adjustedMin mismatch")
            XCTAssertEqual(result.adjustedMax, expectedAdjustedMax, accuracy: tolerance,
                           "\(name): adjustedMax mismatch")

            // Check fairness
            let expectedFairness = expected["fairness"] as! String
            XCTAssertEqual(result.fairness.rawValue, expectedFairness,
                           "\(name): fairness mismatch")

            // Check counter range
            let expectedCounterMin = expected["counterMin"] as! Double
            let expectedCounterMax = expected["counterMax"] as! Double
            XCTAssertEqual(result.counterMin, expectedCounterMin, accuracy: tolerance,
                           "\(name): counterMin mismatch")
            XCTAssertEqual(result.counterMax, expectedCounterMax, accuracy: tolerance,
                           "\(name): counterMax mismatch")
        }
    }

    // MARK: - Individual Tests

    func testBasicTaxi_FairPrice() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Basic taxi - fair price (middle of range)") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]
        let expected = test["expected"] as! [String: Any]

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .fair)
        XCTAssertEqual(result.adjustedMin, expected["adjustedMin"] as! Double, accuracy: tolerance)
        XCTAssertEqual(result.adjustedMax, expected["adjustedMax"] as! Double, accuracy: tolerance)
    }

    func testBasicTaxi_LowPrice() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Basic taxi - low price (suspiciously cheap)") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]
        let expected = test["expected"] as! [String: Any]

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .low)
        XCTAssertEqual(result.adjustedMin, expected["adjustedMin"] as! Double, accuracy: tolerance)
    }

    func testBasicTaxi_HighPrice() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Basic taxi - high price (slightly overpriced)") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .high)
    }

    func testBasicTaxi_VeryHighPrice() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Basic taxi - very high price (significantly overpriced)") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .veryHigh)
    }

    func testWithNightModifier() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Airport taxi - night modifier applied") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]
        let expected = test["expected"] as! [String: Any]

        let modifiersData = input["modifiers"] as! [[String: Any]]
        let modifiers = modifiersData.map {
            PricingEngine.ContextModifier(
                factorMin: $0["factorMin"] as! Double,
                factorMax: $0["factorMax"] as! Double
            )
        }

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            modifiers: modifiers,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .fair)
        XCTAssertEqual(result.adjustedMin, expected["adjustedMin"] as! Double, accuracy: tolerance)
        XCTAssertEqual(result.adjustedMax, expected["adjustedMax"] as! Double, accuracy: tolerance)
    }

    func testMultipleModifiersStack() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Airport taxi - multiple modifiers stack") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]
        let expected = test["expected"] as! [String: Any]

        let modifiersData = input["modifiers"] as! [[String: Any]]
        let modifiers = modifiersData.map {
            PricingEngine.ContextModifier(
                factorMin: $0["factorMin"] as! Double,
                factorMax: $0["factorMax"] as! Double
            )
        }

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            modifiers: modifiers,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .fair)
        XCTAssertEqual(result.adjustedMin, expected["adjustedMin"] as! Double, accuracy: tolerance)
        XCTAssertEqual(result.adjustedMax, expected["adjustedMax"] as! Double, accuracy: tolerance)
    }

    func testQuantityMultiplier() throws {
        let vectors = testVectors["vectors"] as! [[String: Any]]
        guard let test = findVector(in: vectors, named: "Quantity multiplier - 2 mint teas") else {
            XCTFail("Test vector not found")
            return
        }

        let input = test["input"] as! [String: Any]
        let expected = test["expected"] as! [String: Any]

        let engineInput = PricingEngine.Input(
            expectedCostMinMad: input["expectedCostMinMad"] as! Double,
            expectedCostMaxMad: input["expectedCostMaxMad"] as! Double,
            quotedMad: input["quotedMad"] as! Double,
            quantity: input["quantity"] as! Int,
            fairnessLowMultiplier: input["fairnessLowMultiplier"] as! Double,
            fairnessHighMultiplier: input["fairnessHighMultiplier"] as! Double
        )

        let result = PricingEngine.evaluate(engineInput)

        XCTAssertEqual(result.fairness, .fair)
        XCTAssertEqual(result.adjustedMin, expected["adjustedMin"] as! Double, accuracy: tolerance)
        XCTAssertEqual(result.adjustedMax, expected["adjustedMax"] as! Double, accuracy: tolerance)
    }

    // MARK: - Convenience Method Tests

    func testIsAcceptable_Fair() {
        XCTAssertTrue(PricingEngine.isAcceptable(quotedMad: 35, expectedMin: 20, expectedMax: 50))
    }

    func testIsAcceptable_Low() {
        XCTAssertTrue(PricingEngine.isAcceptable(quotedMad: 10, expectedMin: 20, expectedMax: 50))
    }

    func testIsAcceptable_High() {
        XCTAssertFalse(PricingEngine.isAcceptable(quotedMad: 80, expectedMin: 20, expectedMax: 50))
    }

    func testDescription() {
        XCTAssertEqual(PricingEngine.description(for: .low), "Suspiciously cheap")
        XCTAssertEqual(PricingEngine.description(for: .fair), "Fair price")
        XCTAssertEqual(PricingEngine.description(for: .high), "Slightly high")
        XCTAssertEqual(PricingEngine.description(for: .veryHigh), "Too expensive")
    }

    func testSuggestedAction() {
        XCTAssertFalse(PricingEngine.suggestedAction(for: .low).isEmpty)
        XCTAssertFalse(PricingEngine.suggestedAction(for: .fair).isEmpty)
        XCTAssertFalse(PricingEngine.suggestedAction(for: .high).isEmpty)
        XCTAssertFalse(PricingEngine.suggestedAction(for: .veryHigh).isEmpty)
    }

    // MARK: - Helper Functions

    private func findVector(in vectors: [[String: Any]], named name: String) -> [String: Any]? {
        vectors.first { ($0["name"] as? String) == name }
    }
}
