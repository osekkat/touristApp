package com.marrakechguide.feature.homebase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for CompassArrow helper functions.
 *
 * Tests cover:
 * - Cardinal direction calculation from rotation degrees
 * - Edge cases at direction boundaries
 * - Negative angle handling
 * - Angles > 360 degrees
 */
class CompassArrowTest {

    // MARK: - Cardinal Direction Tests

    @Test
    fun `directionFromRotation returns north for 0 degrees`() {
        assertEquals("north", directionFromRotation(0.0))
    }

    @Test
    fun `directionFromRotation returns north for 360 degrees`() {
        // 360 mod 360 = 0, which should be north
        assertEquals("north", directionFromRotation(360.0))
    }

    @Test
    fun `directionFromRotation returns north for small positive angle`() {
        assertEquals("north", directionFromRotation(10.0))
    }

    @Test
    fun `directionFromRotation returns north for angle just below 360`() {
        assertEquals("north", directionFromRotation(350.0))
    }

    @Test
    fun `directionFromRotation returns northeast for 45 degrees`() {
        assertEquals("northeast", directionFromRotation(45.0))
    }

    @Test
    fun `directionFromRotation returns east for 90 degrees`() {
        assertEquals("east", directionFromRotation(90.0))
    }

    @Test
    fun `directionFromRotation returns southeast for 135 degrees`() {
        assertEquals("southeast", directionFromRotation(135.0))
    }

    @Test
    fun `directionFromRotation returns south for 180 degrees`() {
        assertEquals("south", directionFromRotation(180.0))
    }

    @Test
    fun `directionFromRotation returns southwest for 225 degrees`() {
        assertEquals("southwest", directionFromRotation(225.0))
    }

    @Test
    fun `directionFromRotation returns west for 270 degrees`() {
        assertEquals("west", directionFromRotation(270.0))
    }

    @Test
    fun `directionFromRotation returns northwest for 315 degrees`() {
        assertEquals("northwest", directionFromRotation(315.0))
    }

    // MARK: - Boundary Tests

    @Test
    fun `directionFromRotation boundary at 22_5 degrees is northeast`() {
        assertEquals("northeast", directionFromRotation(22.5))
    }

    @Test
    fun `directionFromRotation just below 22_5 is north`() {
        assertEquals("north", directionFromRotation(22.4))
    }

    @Test
    fun `directionFromRotation boundary at 67_5 degrees is east`() {
        assertEquals("east", directionFromRotation(67.5))
    }

    @Test
    fun `directionFromRotation boundary at 337_5 degrees is north`() {
        assertEquals("north", directionFromRotation(337.5))
    }

    @Test
    fun `directionFromRotation just below 337_5 is northwest`() {
        assertEquals("northwest", directionFromRotation(337.4))
    }

    // MARK: - Negative Angle Tests (verifies mod() handles negatives correctly)

    @Test
    fun `directionFromRotation handles negative angle -45 degrees`() {
        // -45 mod 360 = 315 in Kotlin, which is northwest
        assertEquals("northwest", directionFromRotation(-45.0))
    }

    @Test
    fun `directionFromRotation handles negative angle -90 degrees`() {
        // -90 mod 360 = 270 in Kotlin, which is west
        assertEquals("west", directionFromRotation(-90.0))
    }

    @Test
    fun `directionFromRotation handles negative angle -180 degrees`() {
        // -180 mod 360 = 180 in Kotlin, which is south
        assertEquals("south", directionFromRotation(-180.0))
    }

    @Test
    fun `directionFromRotation handles negative angle -270 degrees`() {
        // -270 mod 360 = 90 in Kotlin, which is east
        assertEquals("east", directionFromRotation(-270.0))
    }

    @Test
    fun `directionFromRotation handles negative angle -360 degrees`() {
        // -360 mod 360 = 0 in Kotlin, which is north
        assertEquals("north", directionFromRotation(-360.0))
    }

    // MARK: - Large Angle Tests (> 360)

    @Test
    fun `directionFromRotation handles angle 720 degrees`() {
        // 720 mod 360 = 0, which is north
        assertEquals("north", directionFromRotation(720.0))
    }

    @Test
    fun `directionFromRotation handles angle 450 degrees`() {
        // 450 mod 360 = 90, which is east
        assertEquals("east", directionFromRotation(450.0))
    }

    @Test
    fun `directionFromRotation handles angle 810 degrees`() {
        // 810 mod 360 = 90, which is east
        assertEquals("east", directionFromRotation(810.0))
    }

    @Test
    fun `directionFromRotation handles large negative angle -720 degrees`() {
        // -720 mod 360 = 0 in Kotlin, which is north
        assertEquals("north", directionFromRotation(-720.0))
    }

    // MARK: - Additional Edge Case Tests

    @Test
    fun `directionFromRotation handles very small negative angle`() {
        // -0.1 mod 360 = 359.9 in Kotlin, which is north
        assertEquals("north", directionFromRotation(-0.1))
    }

    @Test
    fun `directionFromRotation handles angle just above boundary at 22_5`() {
        // 22.6 should be northeast, not north
        assertEquals("northeast", directionFromRotation(22.6))
    }

    @Test
    fun `directionFromRotation handles angle just below boundary at 337_5`() {
        // 337.4 is northwest (already tested), 337.5 is north
        // Test 337.6 to verify it's still north
        assertEquals("north", directionFromRotation(337.6))
    }
}
