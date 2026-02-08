import Foundation

/// PricingEngine evaluates quoted prices against expected ranges with context modifiers.
///
/// This is the core engine powering the Quote → Action feature. It determines
/// whether a quoted price is fair, high, or suspicious, and provides suggested
/// counter-offer ranges.
///
/// All methods are pure functions with no side effects. Both iOS and Android
/// implementations MUST produce identical outputs for identical inputs.
///
/// Test vectors: shared/tests/pricing-engine-vectors.json
public enum PricingEngine {

    // MARK: - Types

    /// Context modifier that adjusts price range based on circumstances.
    /// Examples: night surcharge, peak season, tourist lane markup, private group.
    public struct ContextModifier {
        /// Multiplier for the minimum price (e.g., 1.1 = +10%)
        public let factorMin: Double
        /// Multiplier for the maximum price (e.g., 1.2 = +20%)
        public let factorMax: Double

        public init(factorMin: Double, factorMax: Double) {
            self.factorMin = factorMin
            self.factorMax = factorMax
        }
    }

    /// Input for pricing evaluation.
    public struct Input {
        /// Minimum expected cost in MAD
        public let expectedCostMinMad: Double
        /// Maximum expected cost in MAD
        public let expectedCostMaxMad: Double
        /// Quoted price in MAD
        public let quotedMad: Double
        /// Context modifiers to apply
        public let modifiers: [ContextModifier]
        /// Number of items (defaults to 1)
        public let quantity: Int
        /// Multiplier for "low" threshold (default 0.75 = 75% of min is suspicious)
        public let fairnessLowMultiplier: Double
        /// Multiplier for "high" threshold (default 1.25 = 125% of max is too high)
        public let fairnessHighMultiplier: Double

        public init(
            expectedCostMinMad: Double,
            expectedCostMaxMad: Double,
            quotedMad: Double,
            modifiers: [ContextModifier] = [],
            quantity: Int = 1,
            fairnessLowMultiplier: Double = 0.75,
            fairnessHighMultiplier: Double = 1.25
        ) {
            self.expectedCostMinMad = expectedCostMinMad
            self.expectedCostMaxMad = expectedCostMaxMad
            self.quotedMad = quotedMad
            self.modifiers = modifiers
            self.quantity = quantity
            self.fairnessLowMultiplier = fairnessLowMultiplier
            self.fairnessHighMultiplier = fairnessHighMultiplier
        }
    }

    /// Output from pricing evaluation.
    public struct Output {
        /// Adjusted minimum price after modifiers and quantity
        public let adjustedMin: Double
        /// Adjusted maximum price after modifiers and quantity
        public let adjustedMax: Double
        /// Fairness assessment of the quoted price
        public let fairness: FairnessLevel
        /// Suggested counter-offer minimum
        public let counterMin: Double
        /// Suggested counter-offer maximum
        public let counterMax: Double

        public init(
            adjustedMin: Double,
            adjustedMax: Double,
            fairness: FairnessLevel,
            counterMin: Double,
            counterMax: Double
        ) {
            self.adjustedMin = adjustedMin
            self.adjustedMax = adjustedMax
            self.fairness = fairness
            self.counterMin = counterMin
            self.counterMax = counterMax
        }
    }

    /// Fairness level for a quoted price.
    public enum FairnessLevel: String, CaseIterable {
        /// Price is suspiciously low - may indicate a scam or inferior product
        case low
        /// Price is within expected range - good value
        case fair
        /// Price is above expected but within tolerance - slightly overpriced
        case high
        /// Price is significantly overpriced - should negotiate or walk away
        case veryHigh
    }

    // MARK: - Evaluation

    /// Evaluates a quoted price against expected ranges with context modifiers.
    ///
    /// Algorithm:
    /// 1. Start with base expected range (min, max)
    /// 2. Apply each modifier by multiplying min *= factorMin, max *= factorMax
    /// 3. Apply quantity multiplier
    /// 4. Determine fairness level based on thresholds
    /// 5. Calculate suggested counter-offer range
    ///
    /// - Parameter input: Pricing evaluation input
    /// - Returns: Pricing evaluation output with fairness assessment
    public static func evaluate(_ input: Input) -> Output {
        // Step 1: Start with base range
        var minMad = input.expectedCostMinMad
        var maxMad = input.expectedCostMaxMad

        // Step 2: Apply modifiers (multiply sequentially)
        for modifier in input.modifiers {
            minMad *= modifier.factorMin
            maxMad *= modifier.factorMax
        }

        // Step 3: Apply quantity
        let adjustedMin = minMad * Double(input.quantity)
        let adjustedMax = maxMad * Double(input.quantity)

        // Step 4: Determine fairness
        let lowThreshold = adjustedMin * input.fairnessLowMultiplier
        let highThreshold = adjustedMax * input.fairnessHighMultiplier

        let fairness: FairnessLevel
        if input.quotedMad < lowThreshold {
            fairness = .low
        } else if input.quotedMad <= adjustedMax {
            fairness = .fair
        } else if input.quotedMad <= highThreshold {
            fairness = .high
        } else {
            fairness = .veryHigh
        }

        // Step 5: Calculate counter range
        let counterMin = adjustedMin
        let counterMax = adjustedMax * 0.95

        return Output(
            adjustedMin: adjustedMin,
            adjustedMax: adjustedMax,
            fairness: fairness,
            counterMin: counterMin,
            counterMax: counterMax
        )
    }

    // MARK: - Convenience Methods

    /// Quick check if a price is acceptable (fair or low).
    ///
    /// - Parameters:
    ///   - quotedMad: Quoted price in MAD
    ///   - expectedMin: Expected minimum price
    ///   - expectedMax: Expected maximum price
    /// - Returns: true if price is fair or low
    public static func isAcceptable(
        quotedMad: Double,
        expectedMin: Double,
        expectedMax: Double
    ) -> Bool {
        let result = evaluate(Input(
            expectedCostMinMad: expectedMin,
            expectedCostMaxMad: expectedMax,
            quotedMad: quotedMad
        ))
        return result.fairness == .fair || result.fairness == .low
    }

    /// Gets a human-readable description of the fairness level.
    ///
    /// - Parameter fairness: Fairness level to describe
    /// - Returns: Localized description
    public static func description(for fairness: FairnessLevel) -> String {
        switch fairness {
        case .low:
            return "Suspiciously cheap"
        case .fair:
            return "Fair price"
        case .high:
            return "Slightly high"
        case .veryHigh:
            return "Too expensive"
        }
    }

    /// Gets the suggested action for a fairness level.
    ///
    /// - Parameter fairness: Fairness level
    /// - Returns: Suggested action string
    public static func suggestedAction(for fairness: FairnessLevel) -> String {
        switch fairness {
        case .low:
            return "Be cautious - verify quality before agreeing"
        case .fair:
            return "Good price - accept or negotiate slightly"
        case .high:
            return "Counter-offer with suggested range"
        case .veryHigh:
            return "Walk away or make a strong counter-offer"
        }
    }
}
