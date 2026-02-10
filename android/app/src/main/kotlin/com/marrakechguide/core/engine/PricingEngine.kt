package com.marrakechguide.core.engine

/**
 * PricingEngine evaluates quoted prices against expected ranges with context modifiers.
 *
 * This is the core engine powering the Quote → Action feature. It determines
 * whether a quoted price is fair, high, or suspicious, and provides suggested
 * counter-offer ranges.
 *
 * All methods are pure functions with no side effects. Both iOS and Android
 * implementations MUST produce identical outputs for identical inputs.
 *
 * Test vectors: shared/tests/pricing-engine-vectors.json
 */
object PricingEngine {

    /**
     * Context modifier that adjusts price range based on circumstances.
     * Examples: night surcharge, peak season, tourist lane markup, private group.
     *
     * @property factorMin Multiplier for the minimum price (e.g., 1.1 = +10%)
     * @property factorMax Multiplier for the maximum price (e.g., 1.2 = +20%)
     */
    data class ContextModifier(
        val factorMin: Double,
        val factorMax: Double
    )

    /**
     * Input for pricing evaluation.
     *
     * @property expectedCostMinMad Minimum expected cost in MAD
     * @property expectedCostMaxMad Maximum expected cost in MAD
     * @property quotedMad Quoted price in MAD
     * @property modifiers Context modifiers to apply
     * @property quantity Number of items (defaults to 1)
     * @property fairnessLowMultiplier Multiplier for "low" threshold (default 0.75)
     * @property fairnessHighMultiplier Multiplier for "high" threshold (default 1.25)
     */
    data class Input(
        val expectedCostMinMad: Double,
        val expectedCostMaxMad: Double,
        val quotedMad: Double,
        val modifiers: List<ContextModifier> = emptyList(),
        val quantity: Int = 1,
        val fairnessLowMultiplier: Double = 0.75,
        val fairnessHighMultiplier: Double = 1.25
    )

    /**
     * Output from pricing evaluation.
     *
     * @property adjustedMin Adjusted minimum price after modifiers and quantity
     * @property adjustedMax Adjusted maximum price after modifiers and quantity
     * @property fairness Fairness assessment of the quoted price
     * @property counterMin Suggested counter-offer minimum
     * @property counterMax Suggested counter-offer maximum
     */
    data class Output(
        val adjustedMin: Double,
        val adjustedMax: Double,
        val fairness: FairnessLevel,
        val counterMin: Double,
        val counterMax: Double
    )

    /**
     * Fairness level for a quoted price.
     */
    enum class FairnessLevel(val value: String) {
        /** Price is suspiciously low - may indicate a scam or inferior product */
        LOW("low"),
        /** Price is within expected range - good value */
        FAIR("fair"),
        /** Price is above expected but within tolerance - slightly overpriced */
        HIGH("high"),
        /** Price is significantly overpriced - should negotiate or walk away */
        VERY_HIGH("veryHigh");

        companion object {
            fun fromString(value: String): FairnessLevel {
                return entries.find { it.value == value }
                    ?: throw IllegalArgumentException("Unknown fairness level: $value")
            }
        }
    }

    /**
     * Evaluates a quoted price against expected ranges with context modifiers.
     *
     * Algorithm:
     * 1. Start with base expected range (min, max)
     * 2. Apply each modifier by multiplying min *= factorMin, max *= factorMax
     * 3. Apply quantity multiplier
     * 4. Determine fairness level based on thresholds
     * 5. Calculate suggested counter-offer range
     *
     * @param input Pricing evaluation input
     * @return Pricing evaluation output with fairness assessment
     */
    fun evaluate(input: Input): Output {
        // Step 1: Start with base range
        var minMad = minOf(input.expectedCostMinMad, input.expectedCostMaxMad)
        var maxMad = maxOf(input.expectedCostMinMad, input.expectedCostMaxMad)

        // Step 2: Apply modifiers (multiply sequentially)
        for (modifier in input.modifiers) {
            minMad *= modifier.factorMin
            maxMad *= modifier.factorMax
        }

        // Step 3: Apply quantity
        val quantity = input.quantity.coerceAtLeast(1)
        val rawAdjustedMin = minMad * quantity
        val rawAdjustedMax = maxMad * quantity
        val adjustedMin = minOf(rawAdjustedMin, rawAdjustedMax)
        val adjustedMax = maxOf(rawAdjustedMin, rawAdjustedMax)

        // Step 4: Determine fairness
        val lowThreshold = adjustedMin * input.fairnessLowMultiplier
        val highThreshold = adjustedMax * input.fairnessHighMultiplier

        val fairness = when {
            input.quotedMad < lowThreshold -> FairnessLevel.LOW
            input.quotedMad <= adjustedMax -> FairnessLevel.FAIR
            input.quotedMad <= highThreshold -> FairnessLevel.HIGH
            else -> FairnessLevel.VERY_HIGH
        }

        // Step 5: Calculate counter range
        val counterMin = adjustedMin
        val counterMax = maxOf(counterMin, adjustedMax * 0.95)

        return Output(
            adjustedMin = adjustedMin,
            adjustedMax = adjustedMax,
            fairness = fairness,
            counterMin = counterMin,
            counterMax = counterMax
        )
    }

    /**
     * Quick check if a price is acceptable (fair or low).
     *
     * @param quotedMad Quoted price in MAD
     * @param expectedMin Expected minimum price
     * @param expectedMax Expected maximum price
     * @return true if price is fair or low
     */
    fun isAcceptable(
        quotedMad: Double,
        expectedMin: Double,
        expectedMax: Double
    ): Boolean {
        val result = evaluate(
            Input(
                expectedCostMinMad = expectedMin,
                expectedCostMaxMad = expectedMax,
                quotedMad = quotedMad
            )
        )
        return result.fairness == FairnessLevel.FAIR || result.fairness == FairnessLevel.LOW
    }

    /**
     * Gets a human-readable description of the fairness level.
     *
     * @param fairness Fairness level to describe
     * @return Localized description
     */
    fun description(fairness: FairnessLevel): String {
        return when (fairness) {
            FairnessLevel.LOW -> "Suspiciously cheap"
            FairnessLevel.FAIR -> "Fair price"
            FairnessLevel.HIGH -> "Slightly high"
            FairnessLevel.VERY_HIGH -> "Too expensive"
        }
    }

    /**
     * Gets the suggested action for a fairness level.
     *
     * @param fairness Fairness level
     * @return Suggested action string
     */
    fun suggestedAction(fairness: FairnessLevel): String {
        return when (fairness) {
            FairnessLevel.LOW -> "Be cautious - verify quality before agreeing"
            FairnessLevel.FAIR -> "Good price - accept or negotiate slightly"
            FairnessLevel.HIGH -> "Counter-offer with suggested range"
            FairnessLevel.VERY_HIGH -> "Walk away or make a strong counter-offer"
        }
    }
}
