import XCTest
@testable import MarrakechGuide

/// Unit tests for CompassArrowView helper functions.
///
/// Tests cover:
/// - Cardinal direction calculation from rotation degrees
/// - Edge cases at direction boundaries
/// - Negative angle handling
/// - Angles > 360 degrees
final class CompassArrowViewTests: XCTestCase {

    // MARK: - Cardinal Direction Tests

    func testDirectionFromDegrees_returns_north_for_0_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(0.0), "north")
    }

    func testDirectionFromDegrees_returns_north_for_360_degrees() {
        // 360 mod 360 = 0, which should be north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(360.0), "north")
    }

    func testDirectionFromDegrees_returns_north_for_small_positive_angle() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(10.0), "north")
    }

    func testDirectionFromDegrees_returns_north_for_angle_just_below_360() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(350.0), "north")
    }

    func testDirectionFromDegrees_returns_northeast_for_45_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(45.0), "northeast")
    }

    func testDirectionFromDegrees_returns_east_for_90_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(90.0), "east")
    }

    func testDirectionFromDegrees_returns_southeast_for_135_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(135.0), "southeast")
    }

    func testDirectionFromDegrees_returns_south_for_180_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(180.0), "south")
    }

    func testDirectionFromDegrees_returns_southwest_for_225_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(225.0), "southwest")
    }

    func testDirectionFromDegrees_returns_west_for_270_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(270.0), "west")
    }

    func testDirectionFromDegrees_returns_northwest_for_315_degrees() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(315.0), "northwest")
    }

    // MARK: - Boundary Tests

    func testDirectionFromDegrees_boundary_at_22_5_degrees_is_northeast() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(22.5), "northeast")
    }

    func testDirectionFromDegrees_just_below_22_5_is_north() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(22.4), "north")
    }

    func testDirectionFromDegrees_boundary_at_67_5_degrees_is_east() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(67.5), "east")
    }

    func testDirectionFromDegrees_boundary_at_337_5_degrees_is_north() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(337.5), "north")
    }

    func testDirectionFromDegrees_just_below_337_5_is_northwest() {
        XCTAssertEqual(CompassArrowView.directionFromDegrees(337.4), "northwest")
    }

    // MARK: - Negative Angle Tests

    func testDirectionFromDegrees_handles_negative_angle_minus_45_degrees() {
        // -45 normalized = 315, which is northwest
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-45.0), "northwest")
    }

    func testDirectionFromDegrees_handles_negative_angle_minus_90_degrees() {
        // -90 normalized = 270, which is west
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-90.0), "west")
    }

    func testDirectionFromDegrees_handles_negative_angle_minus_180_degrees() {
        // -180 normalized = 180, which is south
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-180.0), "south")
    }

    func testDirectionFromDegrees_handles_negative_angle_minus_270_degrees() {
        // -270 normalized = 90, which is east
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-270.0), "east")
    }

    func testDirectionFromDegrees_handles_negative_angle_minus_360_degrees() {
        // -360 normalized = 0, which is north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-360.0), "north")
    }

    // MARK: - Large Angle Tests (> 360)

    func testDirectionFromDegrees_handles_angle_720_degrees() {
        // 720 mod 360 = 0, which is north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(720.0), "north")
    }

    func testDirectionFromDegrees_handles_angle_450_degrees() {
        // 450 mod 360 = 90, which is east
        XCTAssertEqual(CompassArrowView.directionFromDegrees(450.0), "east")
    }

    func testDirectionFromDegrees_handles_angle_810_degrees() {
        // 810 mod 360 = 90, which is east
        XCTAssertEqual(CompassArrowView.directionFromDegrees(810.0), "east")
    }

    func testDirectionFromDegrees_handles_large_negative_angle_minus_720_degrees() {
        // -720 normalized = 0, which is north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-720.0), "north")
    }

    // MARK: - Additional Edge Case Tests

    func testDirectionFromDegrees_handles_very_small_negative_angle() {
        // -0.1 normalized = 359.9, which is north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(-0.1), "north")
    }

    func testDirectionFromDegrees_handles_angle_just_above_boundary_at_22_5() {
        // 22.6 should be northeast, not north
        XCTAssertEqual(CompassArrowView.directionFromDegrees(22.6), "northeast")
    }

    func testDirectionFromDegrees_handles_angle_just_above_boundary_at_337_5() {
        // 337.6 should be north (since 337.5 is the boundary)
        XCTAssertEqual(CompassArrowView.directionFromDegrees(337.6), "north")
    }
}
