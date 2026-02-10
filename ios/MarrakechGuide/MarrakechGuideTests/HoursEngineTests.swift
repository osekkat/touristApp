import XCTest
@testable import MarrakechGuide

final class HoursEngineTests: XCTestCase {

    private var vectors: [[String: Any]] = []

    override func setUpWithError() throws {
        try super.setUpWithError()
        vectors = try loadVectors()
    }

    func testOpenStatusMatchesSharedVectors() throws {
        for testCase in vectors {
            let name = testCase["name"] as! String
            let at = try parseInstant(testCase["at"] as! String)
            let weekly = testCase["weekly"] as? [String] ?? []
            let hoursText = testCase["hours_text"] as? String
            let exceptions = parseExceptions(testCase["exceptions"] as? [[String: Any]] ?? [])
            let expected = testCase["expected"] as! [String: Any]

            let status = HoursEngine.isOpen(
                weekly: weekly,
                hoursText: hoursText,
                at: at,
                exceptions: exceptions
            )

            assertStatus(status, expected: expected, name: name)
        }
    }

    func testNextChangeMatchesSharedVectors() throws {
        for testCase in vectors {
            let name = testCase["name"] as! String
            let at = try parseInstant(testCase["at"] as! String)
            let weekly = testCase["weekly"] as? [String] ?? []
            let hoursText = testCase["hours_text"] as? String
            let exceptions = parseExceptions(testCase["exceptions"] as? [[String: Any]] ?? [])
            let expected = testCase["expected"] as! [String: Any]

            let change = HoursEngine.getNextChange(
                weekly: weekly,
                hoursText: hoursText,
                from: at,
                exceptions: exceptions
            )

            let expectedNext = expected["next_change_local"] as? String
            if let expectedNext {
                XCTAssertNotNil(change, "Expected next change for \(name)")
                XCTAssertEqual(formatLocal(change!.time), expectedNext, "Next change mismatch for \(name)")
            } else {
                XCTAssertNil(change, "Expected no next change for \(name)")
            }
        }
    }

    func testDisplayFormattingMatchesSharedVectors() throws {
        for testCase in vectors {
            let name = testCase["name"] as! String
            let at = try parseInstant(testCase["at"] as! String)
            let weekly = testCase["weekly"] as? [String] ?? []
            let hoursText = testCase["hours_text"] as? String
            let hoursVerifiedAt = testCase["hours_verified_at"] as? String
            let exceptions = parseExceptions(testCase["exceptions"] as? [[String: Any]] ?? [])
            let expected = testCase["expected"] as! [String: Any]
            let expectedDisplay = expected["display"] as! String

            let display = HoursEngine.formatHoursForDisplay(
                weekly: weekly,
                hoursText: hoursText,
                hoursVerifiedAt: hoursVerifiedAt,
                at: at,
                exceptions: exceptions
            )

            XCTAssertEqual(display, expectedDisplay, "Display mismatch for \(name)")
        }
    }

    func testHoursStaleness() throws {
        let reference = try parseInstant("2026-01-10T12:00:00+01:00")

        XCTAssertTrue(HoursEngine.isHoursStale("2025-01-01", at: reference))
        XCTAssertFalse(HoursEngine.isHoursStale("2025-10-15", at: reference))
        XCTAssertFalse(HoursEngine.isHoursStale(nil, at: reference))
    }

    // MARK: Helpers

    private func assertStatus(_ status: HoursEngine.OpenStatus, expected: [String: Any], name: String) {
        let expectedState = expected["state"] as! String
        let expectedNext = expected["next_change_local"] as? String

        switch status {
        case .open(let closesAt):
            XCTAssertEqual(expectedState, "open", "State mismatch for \(name)")
            XCTAssertEqual(formatLocal(closesAt), expectedNext, "Close time mismatch for \(name)")

        case .closed(let opensAt):
            XCTAssertEqual(expectedState, "closed", "State mismatch for \(name)")
            if let opensAt {
                XCTAssertEqual(formatLocal(opensAt), expectedNext, "Open time mismatch for \(name)")
            } else {
                XCTAssertNil(expectedNext, "Expected nil next change for \(name)")
            }

        case .unknown:
            XCTAssertEqual(expectedState, "unknown", "State mismatch for \(name)")
            XCTAssertNil(expectedNext, "Unknown state should not have next change for \(name)")
        }
    }

    private func parseExceptions(_ raw: [[String: Any]]) -> [HoursEngine.ExceptionRule] {
        raw.map { item in
            HoursEngine.ExceptionRule(
                date: item["date"] as? String,
                period: item["period"] as? String,
                open: item["open"] as? String,
                close: item["close"] as? String,
                closed: item["closed"] as? Bool ?? false
            )
        }
    }

    private func parseInstant(_ value: String) throws -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        if let date = formatter.date(from: value) {
            return date
        }

        formatter.formatOptions = [.withInternetDateTime]
        if let date = formatter.date(from: value) {
            return date
        }

        throw NSError(
            domain: "HoursEngineTests",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Unable to parse date: \(value)"]
        )
    }

    private func formatLocal(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Africa/Casablanca")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return formatter.string(from: date)
    }

    private func loadVectors() throws -> [[String: Any]] {
        let possiblePaths = [
            "../../../../shared/tests/hours-engine-vectors.json",
            "../../../shared/tests/hours-engine-vectors.json",
            "../../shared/tests/hours-engine-vectors.json",
            "../shared/tests/hours-engine-vectors.json"
        ]

        var data: Data?

        if let bundleURL = Bundle(for: type(of: self)).url(forResource: "hours-engine-vectors", withExtension: "json") {
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

        if data == nil, let envPath = ProcessInfo.processInfo.environment["HOURS_ENGINE_VECTORS_PATH"] {
            data = try? Data(contentsOf: URL(fileURLWithPath: envPath))
        }

        guard let data else {
            throw NSError(
                domain: "HoursEngineTests",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Could not find hours-engine-vectors.json"]
            )
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let cases = json["cases"] as? [[String: Any]]
        else {
            throw NSError(
                domain: "HoursEngineTests",
                code: 3,
                userInfo: [NSLocalizedDescriptionKey: "Invalid vector format"]
            )
        }

        return cases
    }
}
