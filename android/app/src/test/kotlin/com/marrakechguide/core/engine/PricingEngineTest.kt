package com.marrakechguide.core.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

/**
 * Unit tests for PricingEngine using shared test vectors.
 *
 * Test vectors are loaded from: shared/tests/pricing-engine-vectors.json
 * Both iOS and Android must pass all tests with the same vectors.
 */
class PricingEngineTest {

    private lateinit var testVectors: JSONObject
    private var tolerance: Double = 0.01

    @Before
    fun setup() {
        val vectorsPath = findTestVectorsFile()
        testVectors = JSONObject(File(vectorsPath).readText())

        // Extract tolerance from meta
        if (testVectors.has("tolerance")) {
            val toleranceInfo = testVectors.getJSONObject("tolerance")
            if (toleranceInfo.has("absoluteTolerance")) {
                tolerance = toleranceInfo.getDouble("absoluteTolerance")
            }
        }
    }

    private fun findTestVectorsFile(): String {
        val userDir = System.getProperty("user.dir").orEmpty()

        // Try multiple paths to find the test vectors
        val possiblePaths = mutableListOf(
            "../../../shared/tests/pricing-engine-vectors.json",
            "../../shared/tests/pricing-engine-vectors.json",
            "../shared/tests/pricing-engine-vectors.json",
            "shared/tests/pricing-engine-vectors.json"
        )
        if (userDir.isNotEmpty()) {
            possiblePaths.addAll(
                listOf(
                    "$userDir/shared/tests/pricing-engine-vectors.json",
                    "$userDir/../shared/tests/pricing-engine-vectors.json",
                    "$userDir/../../shared/tests/pricing-engine-vectors.json",
                    "$userDir/../../../shared/tests/pricing-engine-vectors.json"
                )
            )
        }

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        // Fallback for CI environments
        val envPath = System.getenv("PRICING_ENGINE_VECTORS_PATH")
        if (envPath != null && File(envPath).exists()) {
            return envPath
        }

        throw RuntimeException(
            "Could not find pricing-engine-vectors.json. " +
            "Current dir: ${System.getProperty("user.dir")}. " +
            "Set PRICING_ENGINE_VECTORS_PATH environment variable or ensure the file exists."
        )
    }

    // ==================== All Vectors Test ====================

    @Test
    fun `pricingEngine - all vectors pass`() {
        val vectors = testVectors.getJSONArray("vectors")

        for (i in 0 until vectors.length()) {
            val test = vectors.getJSONObject(i)
            val name = test.getString("name")
            val input = test.getJSONObject("input")
            val expected = test.getJSONObject("expected")

            // Build modifiers
            val modifiers = mutableListOf<PricingEngine.ContextModifier>()
            if (input.has("modifiers")) {
                val modifiersArray = input.getJSONArray("modifiers")
                for (j in 0 until modifiersArray.length()) {
                    val mod = modifiersArray.getJSONObject(j)
                    modifiers.add(
                        PricingEngine.ContextModifier(
                            factorMin = mod.getDouble("factorMin"),
                            factorMax = mod.getDouble("factorMax")
                        )
                    )
                }
            }

            val engineInput = PricingEngine.Input(
                expectedCostMinMad = input.getDouble("expectedCostMinMad"),
                expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
                quotedMad = input.getDouble("quotedMad"),
                modifiers = modifiers,
                quantity = if (input.has("quantity")) input.getInt("quantity") else 1,
                fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
                fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
            )

            val result = PricingEngine.evaluate(engineInput)

            // Check adjusted range
            val expectedAdjustedMin = expected.getDouble("adjustedMin")
            val expectedAdjustedMax = expected.getDouble("adjustedMax")
            assertEquals(
                "$name: adjustedMin mismatch",
                expectedAdjustedMin, result.adjustedMin, tolerance
            )
            assertEquals(
                "$name: adjustedMax mismatch",
                expectedAdjustedMax, result.adjustedMax, tolerance
            )

            // Check fairness
            val expectedFairness = expected.getString("fairness")
            assertEquals(
                "$name: fairness mismatch",
                expectedFairness, result.fairness.value
            )

            // Check counter range
            val expectedCounterMin = expected.getDouble("counterMin")
            val expectedCounterMax = expected.getDouble("counterMax")
            assertEquals(
                "$name: counterMin mismatch",
                expectedCounterMin, result.counterMin, tolerance
            )
            assertEquals(
                "$name: counterMax mismatch",
                expectedCounterMax, result.counterMax, tolerance
            )
        }
    }

    // ==================== Individual Tests ====================

    @Test
    fun `basic taxi - fair price`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Basic taxi - fair price (middle of range)")

        val input = test.getJSONObject("input")
        val expected = test.getJSONObject("expected")

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.FAIR, result.fairness)
        assertEquals(expected.getDouble("adjustedMin"), result.adjustedMin, tolerance)
        assertEquals(expected.getDouble("adjustedMax"), result.adjustedMax, tolerance)
    }

    @Test
    fun `basic taxi - low price`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Basic taxi - low price (suspiciously cheap)")

        val input = test.getJSONObject("input")

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.LOW, result.fairness)
    }

    @Test
    fun `basic taxi - high price`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Basic taxi - high price (slightly overpriced)")

        val input = test.getJSONObject("input")

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.HIGH, result.fairness)
    }

    @Test
    fun `basic taxi - very high price`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Basic taxi - very high price (significantly overpriced)")

        val input = test.getJSONObject("input")

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.VERY_HIGH, result.fairness)
    }

    @Test
    fun `airport taxi - night modifier applied`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Airport taxi - night modifier applied")

        val input = test.getJSONObject("input")
        val expected = test.getJSONObject("expected")

        val modifiersArray = input.getJSONArray("modifiers")
        val modifiers = (0 until modifiersArray.length()).map { j ->
            val mod = modifiersArray.getJSONObject(j)
            PricingEngine.ContextModifier(
                factorMin = mod.getDouble("factorMin"),
                factorMax = mod.getDouble("factorMax")
            )
        }

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            modifiers = modifiers,
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.FAIR, result.fairness)
        assertEquals(expected.getDouble("adjustedMin"), result.adjustedMin, tolerance)
        assertEquals(expected.getDouble("adjustedMax"), result.adjustedMax, tolerance)
    }

    @Test
    fun `airport taxi - multiple modifiers stack`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Airport taxi - multiple modifiers stack")

        val input = test.getJSONObject("input")
        val expected = test.getJSONObject("expected")

        val modifiersArray = input.getJSONArray("modifiers")
        val modifiers = (0 until modifiersArray.length()).map { j ->
            val mod = modifiersArray.getJSONObject(j)
            PricingEngine.ContextModifier(
                factorMin = mod.getDouble("factorMin"),
                factorMax = mod.getDouble("factorMax")
            )
        }

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            modifiers = modifiers,
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.FAIR, result.fairness)
        assertEquals(expected.getDouble("adjustedMin"), result.adjustedMin, tolerance)
        assertEquals(expected.getDouble("adjustedMax"), result.adjustedMax, tolerance)
    }

    @Test
    fun `quantity multiplier - 2 mint teas`() {
        val vectors = testVectors.getJSONArray("vectors")
        val test = findVector(vectors, "Quantity multiplier - 2 mint teas")

        val input = test.getJSONObject("input")
        val expected = test.getJSONObject("expected")

        val engineInput = PricingEngine.Input(
            expectedCostMinMad = input.getDouble("expectedCostMinMad"),
            expectedCostMaxMad = input.getDouble("expectedCostMaxMad"),
            quotedMad = input.getDouble("quotedMad"),
            quantity = input.getInt("quantity"),
            fairnessLowMultiplier = input.getDouble("fairnessLowMultiplier"),
            fairnessHighMultiplier = input.getDouble("fairnessHighMultiplier")
        )

        val result = PricingEngine.evaluate(engineInput)

        assertEquals(PricingEngine.FairnessLevel.FAIR, result.fairness)
        assertEquals(expected.getDouble("adjustedMin"), result.adjustedMin, tolerance)
        assertEquals(expected.getDouble("adjustedMax"), result.adjustedMax, tolerance)
    }

    // ==================== Convenience Method Tests ====================

    @Test
    fun `isAcceptable - fair price`() {
        assertTrue(PricingEngine.isAcceptable(35.0, 20.0, 50.0))
    }

    @Test
    fun `isAcceptable - low price`() {
        assertTrue(PricingEngine.isAcceptable(10.0, 20.0, 50.0))
    }

    @Test
    fun `isAcceptable - high price`() {
        assertFalse(PricingEngine.isAcceptable(80.0, 20.0, 50.0))
    }

    @Test
    fun `description - all levels have text`() {
        assertEquals("Suspiciously cheap", PricingEngine.description(PricingEngine.FairnessLevel.LOW))
        assertEquals("Fair price", PricingEngine.description(PricingEngine.FairnessLevel.FAIR))
        assertEquals("Slightly high", PricingEngine.description(PricingEngine.FairnessLevel.HIGH))
        assertEquals("Too expensive", PricingEngine.description(PricingEngine.FairnessLevel.VERY_HIGH))
    }

    @Test
    fun `suggestedAction - all levels have text`() {
        assertTrue(PricingEngine.suggestedAction(PricingEngine.FairnessLevel.LOW).isNotEmpty())
        assertTrue(PricingEngine.suggestedAction(PricingEngine.FairnessLevel.FAIR).isNotEmpty())
        assertTrue(PricingEngine.suggestedAction(PricingEngine.FairnessLevel.HIGH).isNotEmpty())
        assertTrue(PricingEngine.suggestedAction(PricingEngine.FairnessLevel.VERY_HIGH).isNotEmpty())
    }

    @Test
    fun `fairnessLevel fromString`() {
        assertEquals(PricingEngine.FairnessLevel.LOW, PricingEngine.FairnessLevel.fromString("low"))
        assertEquals(PricingEngine.FairnessLevel.FAIR, PricingEngine.FairnessLevel.fromString("fair"))
        assertEquals(PricingEngine.FairnessLevel.HIGH, PricingEngine.FairnessLevel.fromString("high"))
        assertEquals(PricingEngine.FairnessLevel.VERY_HIGH, PricingEngine.FairnessLevel.fromString("veryHigh"))
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
}
