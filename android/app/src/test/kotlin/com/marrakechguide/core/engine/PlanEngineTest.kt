package com.marrakechguide.core.engine

import com.marrakechguide.core.model.Place
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Unit tests for PlanEngine using shared cross-platform vectors.
 *
 * Test vectors are loaded from: shared/tests/plan-engine-vectors.json
 * Both iOS and Android must pass the same vector suite.
 */
class PlanEngineTest {

    private val testVectors: JSONObject by lazy {
        JSONObject(File(findVectorsFile()).readText())
    }

    @Test
    fun `planEngine - all vectors pass`() {
        val vectors = testVectors.getJSONArray("vectors")
        for (index in 0 until vectors.length()) {
            runVector(vectors.getJSONObject(index))
        }
    }

    @Test
    fun `planEngine - impossible constraints`() {
        val vectors = testVectors.getJSONArray("vectors")
        val vector = findVector(vectors, "Impossible constraints")
        runVector(vector)
    }

    @Test
    fun `planEngine - preserves dinner stop when nearest-neighbor order would drop it`() {
        val output = PlanEngine.generate(
            PlanEngine.Input(
                availableMinutes = 720,
                startPoint = PlanEngine.Coordinate(lat = 31.6238, lng = -7.9859),
                interests = listOf(PlanEngine.Interest.FOOD),
                pace = PlanEngine.Pace.RELAXED,
                budgetTier = PlanEngine.BudgetTier.MID,
                currentTime = Instant.parse("2026-02-10T08:00:00Z"),
                places = listOf(
                    foodPlace(
                        id = "breakfast-cafe",
                        name = "Breakfast Cafe",
                        category = "cafe",
                        lat = 31.6249,
                        lng = -7.9877,
                        visitMin = 45,
                        visitMax = 60,
                        windows = listOf("morning"),
                        tags = listOf("food", "breakfast", "budget"),
                        hours = listOf("daily 07:00-22:00")
                    ),
                    foodPlace(
                        id = "lunch-bistro",
                        name = "Lunch Bistro",
                        category = "restaurant",
                        lat = 31.6269,
                        lng = -7.9925,
                        visitMin = 60,
                        visitMax = 75,
                        windows = listOf("lunch", "afternoon"),
                        tags = listOf("food", "lunch"),
                        hours = listOf("daily 11:00-23:00")
                    ),
                    foodPlace(
                        id = "dinner-riad",
                        name = "Dinner Riad",
                        category = "restaurant",
                        lat = 31.6238,
                        lng = -7.9859,
                        visitMin = 75,
                        visitMax = 90,
                        windows = listOf("evening"),
                        tags = listOf("food", "dinner", "luxury"),
                        hours = listOf("daily 12:00-23:59")
                    ),
                    foodPlace(
                        id = "snack-market",
                        name = "Snack Market",
                        category = "market",
                        lat = 31.6277,
                        lng = -7.9903,
                        visitMin = 30,
                        visitMax = 45,
                        windows = listOf("afternoon", "evening"),
                        tags = listOf("food", "shopping"),
                        hours = listOf("daily 09:00-21:00")
                    )
                )
            )
        )

        val selectedIds = output.stops.map { it.placeId }.toSet()
        assertTrue("Expected breakfast stop", "breakfast-cafe" in selectedIds)
        assertTrue("Expected lunch stop", "lunch-bistro" in selectedIds)
        assertTrue("Expected dinner stop", "dinner-riad" in selectedIds)
        assertFalse(
            "Unexpected missing meal warning: ${output.warnings}",
            output.warnings.any { it.contains("Could not schedule meal stop", ignoreCase = true) }
        )
    }

    private fun runVector(vector: JSONObject) {
        val name = vector.getString("name")
        val input = vector.getJSONObject("input")
        val expected = vector.getJSONObject("expected")

        val startPoint = input.optJSONObject("startPoint")?.let {
            PlanEngine.Coordinate(
                lat = it.getDouble("lat"),
                lng = it.getDouble("lng")
            )
        }

        val interests = input.getJSONArray("interests")
            .toStringList()
            .mapNotNull { PlanEngine.Interest.from(it) }

        val pace = PlanEngine.Pace.from(input.getString("pace"))
            ?: throw IllegalArgumentException("$name: invalid pace")

        val budgetTier = PlanEngine.BudgetTier.from(input.getString("budgetTier"))
            ?: throw IllegalArgumentException("$name: invalid budget tier")

        val currentTime = Instant.parse(input.getString("currentTime"))

        val recentPlaceIds = if (input.has("recentPlaceIds")) {
            input.getJSONArray("recentPlaceIds").toStringList().toSet()
        } else {
            emptySet()
        }

        val places = input.getJSONArray("places").toPlaceList()

        val output = PlanEngine.generate(
            PlanEngine.Input(
                availableMinutes = input.getInt("availableMinutes"),
                startPoint = startPoint,
                interests = interests,
                pace = pace,
                budgetTier = budgetTier,
                currentTime = currentTime,
                places = places,
                recentPlaceIds = recentPlaceIds
            )
        )

        val minStops = expected.getInt("minStops")
        val maxStops = expected.getInt("maxStops")
        val maxTotalMinutes = expected.getInt("maxTotalMinutes")

        assertTrue("$name: expected >= $minStops stops, got ${output.stops.size}", output.stops.size >= minStops)
        assertTrue("$name: expected <= $maxStops stops, got ${output.stops.size}", output.stops.size <= maxStops)
        assertTrue("$name: expected totalMinutes <= $maxTotalMinutes, got ${output.totalMinutes}", output.totalMinutes <= maxTotalMinutes)

        val selectedIds = output.stops.map { it.placeId }.toSet()
        val allowedCategories = expected.getJSONArray("allowedCategories").toStringList().map { it.lowercase() }.toSet()
        val requiredPlaceIds = expected.getJSONArray("requiredPlaceIds").toStringList()
        val excludedPlaceIds = expected.getJSONArray("excludedPlaceIds").toStringList()
        val requiredWarnings = expected.getJSONArray("requiredWarningSubstrings").toStringList()

        val categoryById = places
            .mapNotNull { place -> place.category?.lowercase()?.let { category -> place.id to category } }
            .toMap()

        requiredPlaceIds.forEach { requiredId ->
            assertTrue("$name: missing required stop $requiredId", selectedIds.contains(requiredId))
        }

        excludedPlaceIds.forEach { excludedId ->
            assertFalse("$name: excluded stop present $excludedId", selectedIds.contains(excludedId))
        }

        if (allowedCategories.isNotEmpty()) {
            selectedIds.forEach { stopId ->
                val category = categoryById[stopId]
                if (category != null) {
                    assertTrue(
                        "$name: stop $stopId has unexpected category $category",
                        allowedCategories.contains(category)
                    )
                }
            }
        }

        val warningBlob = output.warnings.joinToString("\n").lowercase()
        requiredWarnings.forEach { requiredWarning ->
            assertTrue(
                "$name: missing warning substring '$requiredWarning'",
                warningBlob.contains(requiredWarning.lowercase())
            )
        }
    }

    private fun findVector(vectors: JSONArray, name: String): JSONObject {
        for (index in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(index)
            if (vector.getString("name") == name) {
                return vector
            }
        }
        throw IllegalArgumentException("Vector not found: $name")
    }

    private fun findVectorsFile(): String {
        val userDir = System.getProperty("user.dir").orEmpty()

        val possiblePaths = mutableListOf(
            "../../../shared/tests/plan-engine-vectors.json",
            "../../shared/tests/plan-engine-vectors.json",
            "../shared/tests/plan-engine-vectors.json",
            "shared/tests/plan-engine-vectors.json"
        )

        if (userDir.isNotEmpty()) {
            possiblePaths.addAll(
                listOf(
                    "$userDir/shared/tests/plan-engine-vectors.json",
                    "$userDir/../shared/tests/plan-engine-vectors.json",
                    "$userDir/../../shared/tests/plan-engine-vectors.json",
                    "$userDir/../../../shared/tests/plan-engine-vectors.json"
                )
            )
        }

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        val envPath = System.getenv("PLAN_ENGINE_VECTORS_PATH")
        if (envPath != null && File(envPath).exists()) {
            return envPath
        }

        throw RuntimeException(
            "Could not find plan-engine-vectors.json. " +
                "Current dir: ${System.getProperty("user.dir")}. " +
                "Set PLAN_ENGINE_VECTORS_PATH environment variable or ensure the file exists."
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).map { getString(it) }
    }

    private fun JSONArray.toPlaceList(): List<Place> {
        return (0 until length()).map { index ->
            val obj = getJSONObject(index)
            Place(
                id = obj.getString("id"),
                name = obj.getString("name"),
                regionId = obj.optStringOrNull("regionId"),
                category = obj.optStringOrNull("category"),
                lat = obj.optDoubleOrNull("lat"),
                lng = obj.optDoubleOrNull("lng"),
                hoursText = obj.optStringOrNull("hoursText"),
                hoursWeekly = if (obj.has("hoursWeekly")) obj.getJSONArray("hoursWeekly").toStringList() else emptyList(),
                touristTrapLevel = obj.optStringOrNull("touristTrapLevel"),
                visitMinMinutes = obj.optIntOrNull("visitMinMinutes"),
                visitMaxMinutes = obj.optIntOrNull("visitMaxMinutes"),
                bestTimeWindows = if (obj.has("bestTimeWindows")) obj.getJSONArray("bestTimeWindows").toStringList() else emptyList(),
                tags = if (obj.has("tags")) obj.getJSONArray("tags").toStringList() else emptyList()
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key)) getDouble(key) else null
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (has(key) && !isNull(key)) getInt(key) else null
    }

    private fun foodPlace(
        id: String,
        name: String,
        category: String,
        lat: Double,
        lng: Double,
        visitMin: Int,
        visitMax: Int,
        windows: List<String>,
        tags: List<String>,
        hours: List<String>
    ): Place {
        return Place(
            id = id,
            name = name,
            category = category,
            lat = lat,
            lng = lng,
            visitMinMinutes = visitMin,
            visitMaxMinutes = visitMax,
            bestTimeWindows = windows,
            tags = tags,
            hoursWeekly = hours,
            touristTrapLevel = "low"
        )
    }
}
