package com.marrakechguide.core.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

/**
 * Unit tests for GeoEngine using shared test vectors.
 *
 * Test vectors are loaded from: shared/tests/geo-engine-vectors.json
 * Both iOS and Android must pass all tests with the same vectors.
 */
class GeoEngineTest {

    private val testVectors: JSONObject by lazy {
        // Load test vectors from shared directory
        // Note: In actual test run, you may need to adjust the path based on test execution context
        val vectorsPath = findTestVectorsFile()
        JSONObject(File(vectorsPath).readText())
    }

    private fun findTestVectorsFile(): String {
        // Try multiple paths to find the test vectors
        val possiblePaths = listOf(
            "../../../shared/tests/geo-engine-vectors.json",
            "../../shared/tests/geo-engine-vectors.json",
            "../shared/tests/geo-engine-vectors.json",
            "shared/tests/geo-engine-vectors.json",
            System.getProperty("user.dir") + "/shared/tests/geo-engine-vectors.json",
            System.getProperty("user.dir") + "/../shared/tests/geo-engine-vectors.json",
            System.getProperty("user.dir") + "/../../shared/tests/geo-engine-vectors.json"
        )

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        // Fallback for CI environments
        val envPath = System.getenv("GEO_ENGINE_VECTORS_PATH")
        if (envPath != null && File(envPath).exists()) {
            return envPath
        }

        throw RuntimeException(
            "Could not find geo-engine-vectors.json. " +
            "Current dir: ${System.getProperty("user.dir")}. " +
            "Set GEO_ENGINE_VECTORS_PATH environment variable or ensure the file exists."
        )
    }

    // ==================== Haversine Tests ====================

    @Test
    fun `haversine - Jemaa el-Fna to Bahia Palace`() {
        val vectors = testVectors.getJSONArray("haversine")
        val test = findVector(vectors, "Jemaa el-Fna to Bahia Palace")

        val input = test.getJSONObject("input")
        val from = input.getJSONObject("from").toCoordinate()
        val to = input.getJSONObject("to").toCoordinate()

        val expected = test.getJSONObject("expected")
        val expectedMeters = expected.getDouble("distance_meters")
        val tolerance = expected.getDouble("tolerance_meters")

        val result = GeoEngine.haversine(from, to)

        assertEquals(
            "Distance from Jemaa el-Fna to Bahia Palace",
            expectedMeters, result, tolerance
        )
    }

    @Test
    fun `haversine - same point returns zero`() {
        val vectors = testVectors.getJSONArray("haversine")
        val test = findVector(vectors, "Same point")

        val input = test.getJSONObject("input")
        val from = input.getJSONObject("from").toCoordinate()
        val to = input.getJSONObject("to").toCoordinate()

        val result = GeoEngine.haversine(from, to)

        assertEquals(0.0, result, 1.0)
    }

    @Test
    fun `haversine - all vectors pass`() {
        val vectors = testVectors.getJSONArray("haversine")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")

            val input = test.getJSONObject("input")
            val from = input.getJSONObject("from").toCoordinate()
            val to = input.getJSONObject("to").toCoordinate()

            val expected = test.getJSONObject("expected")
            val expectedMeters = expected.getDouble("distance_meters")
            val tolerance = expected.getDouble("tolerance_meters")

            val result = GeoEngine.haversine(from, to)

            assertEquals("Haversine test: $name", expectedMeters, result, tolerance)
        }
    }

    // ==================== Bearing Tests ====================

    @Test
    fun `bearing - due north`() {
        val vectors = testVectors.getJSONArray("bearing")
        val test = findVector(vectors, "Due North")

        val input = test.getJSONObject("input")
        val from = input.getJSONObject("from").toCoordinate()
        val to = input.getJSONObject("to").toCoordinate()

        val expected = test.getJSONObject("expected")
        val expectedDegrees = expected.getDouble("bearing_degrees")
        val tolerance = expected.getDouble("tolerance_degrees")

        val result = GeoEngine.bearing(from, to)

        assertEquals("Bearing due north", expectedDegrees, result, tolerance)
    }

    @Test
    fun `bearing - all cardinal directions`() {
        val vectors = testVectors.getJSONArray("bearing")
        val cardinalTests = listOf("Due North", "Due East", "Due South", "Due West")

        for (testName in cardinalTests) {
            val test = findVector(vectors, testName)

            val input = test.getJSONObject("input")
            val from = input.getJSONObject("from").toCoordinate()
            val to = input.getJSONObject("to").toCoordinate()

            val expected = test.getJSONObject("expected")
            val expectedDegrees = expected.getDouble("bearing_degrees")
            val tolerance = expected.getDouble("tolerance_degrees")

            val result = GeoEngine.bearing(from, to)

            assertEquals("Bearing test: $testName", expectedDegrees, result, tolerance)
        }
    }

    @Test
    fun `bearing - all vectors pass`() {
        val vectors = testVectors.getJSONArray("bearing")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")

            val input = test.getJSONObject("input")
            val from = input.getJSONObject("from").toCoordinate()
            val to = input.getJSONObject("to").toCoordinate()

            val expected = test.getJSONObject("expected")
            val expectedDegrees = expected.getDouble("bearing_degrees")
            val tolerance = expected.getDouble("tolerance_degrees")

            val result = GeoEngine.bearing(from, to)

            assertEquals("Bearing test: $name", expectedDegrees, result, tolerance)
        }
    }

    // ==================== Relative Angle Tests ====================

    @Test
    fun `relativeAngle - facing north target east`() {
        val vectors = testVectors.getJSONArray("relativeAngle")
        val test = findVector(vectors, "Facing north, target east")

        val input = test.getJSONObject("input")
        val bearing = input.getDouble("bearing")
        val heading = input.getDouble("heading")

        val expected = test.getJSONObject("expected")
        val expectedAngle = expected.getDouble("relative_angle")

        val result = GeoEngine.relativeAngle(bearing, heading)

        assertEquals(expectedAngle, result, 0.001)
    }

    @Test
    fun `relativeAngle - all vectors pass`() {
        val vectors = testVectors.getJSONArray("relativeAngle")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")

            val input = test.getJSONObject("input")
            val bearing = input.getDouble("bearing")
            val heading = input.getDouble("heading")

            val expected = test.getJSONObject("expected")
            val expectedAngle = expected.getDouble("relative_angle")

            val result = GeoEngine.relativeAngle(bearing, heading)

            assertEquals("RelativeAngle test: $name", expectedAngle, result, 0.001)
        }
    }

    // ==================== Format Distance Tests ====================

    @Test
    fun `formatDistance - all vectors pass`() {
        val vectors = testVectors.getJSONArray("formatDistance")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")

            val input = test.getJSONObject("input")
            val meters = input.getDouble("meters")

            val expected = test.getJSONObject("expected")
            val expectedFormatted = expected.getString("formatted")

            val result = GeoEngine.formatDistance(meters)

            assertEquals("FormatDistance test: $name", expectedFormatted, result)
        }
    }

    // ==================== Estimate Walk Time Tests ====================

    @Test
    fun `estimateWalkTime - all vectors pass`() {
        val vectors = testVectors.getJSONArray("estimateWalkTime")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")

            val input = test.getJSONObject("input")
            val meters = input.getDouble("meters")
            val region = input.getString("region")

            val expected = test.getJSONObject("expected")
            val expectedMinutes = expected.getInt("minutes")
            val tolerance = expected.getInt("tolerance_minutes")

            val result = GeoEngine.estimateWalkTime(meters, region)

            assertTrue(
                "EstimateWalkTime test: $name - expected $expectedMinutes (+/- $tolerance), got $result",
                result in (expectedMinutes - tolerance)..(expectedMinutes + tolerance)
            )
        }
    }

    // ==================== Additional Unit Tests ====================

    @Test
    fun `isWithinMarrakech - Jemaa el-Fna is in Marrakech`() {
        val coord = GeoEngine.Coordinate(31.625831, -7.98892)
        assertTrue(GeoEngine.isWithinMarrakech(coord))
    }

    @Test
    fun `isWithinMarrakech - Paris is not in Marrakech`() {
        val coord = GeoEngine.Coordinate(48.8566, 2.3522)
        assertFalse(GeoEngine.isWithinMarrakech(coord))
    }

    @Test
    fun `determineRegion - Jemaa el-Fna is in medina`() {
        val coord = GeoEngine.Coordinate(31.625831, -7.98892)
        assertEquals("medina", GeoEngine.determineRegion(coord))
    }

    @Test
    fun `determineRegion - Jardin Majorelle is in gueliz`() {
        val coord = GeoEngine.Coordinate(31.641475, -8.002908)
        assertEquals("gueliz", GeoEngine.determineRegion(coord))
    }

    // ==================== Helper Functions ====================

    private fun findVector(array: JSONArray, name: String): JSONObject {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("name") == name) {
                return obj
            }
        }
        throw RuntimeException("Test vector not found: $name")
    }

    private fun JSONObject.toCoordinate(): GeoEngine.Coordinate {
        return GeoEngine.Coordinate(
            lat = getDouble("lat"),
            lng = getDouble("lng")
        )
    }
}
