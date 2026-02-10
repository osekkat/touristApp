package com.marrakechguide.feature.quote

import com.marrakechguide.core.engine.PricingEngine
import com.marrakechguide.core.model.PriceCardDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteActionStartWithCardPolicyTest {

    @Test
    fun `starts when initial card exists and no selection and not loading`() {
        val shouldStart = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = "price-taxi-medina-short-ride",
            selectedPriceCardId = null,
            isLoading = false,
            hasHandledInitialCardLoad = false
        )

        assertTrue(shouldStart)
    }

    @Test
    fun `does not start when initial card is missing or blank`() {
        assertFalse(
            QuoteActionStartWithCardPolicy.shouldStart(
                initialPriceCardId = null,
                selectedPriceCardId = null,
                isLoading = false,
                hasHandledInitialCardLoad = false
            )
        )
        assertFalse(
            QuoteActionStartWithCardPolicy.shouldStart(
                initialPriceCardId = "",
                selectedPriceCardId = null,
                isLoading = false,
                hasHandledInitialCardLoad = false
            )
        )
        assertFalse(
            QuoteActionStartWithCardPolicy.shouldStart(
                initialPriceCardId = "   ",
                selectedPriceCardId = null,
                isLoading = false,
                hasHandledInitialCardLoad = false
            )
        )
    }

    @Test
    fun `does not start when requested card is already selected`() {
        val shouldStart = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = "price-taxi-medina-short-ride",
            selectedPriceCardId = "price-taxi-medina-short-ride",
            isLoading = false,
            hasHandledInitialCardLoad = false
        )

        assertFalse(shouldStart)
    }

    @Test
    fun `starts when a different card is selected`() {
        val shouldStart = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = "price-taxi-medina-short-ride",
            selectedPriceCardId = "price-guide-half-day",
            isLoading = false,
            hasHandledInitialCardLoad = false
        )

        assertTrue(shouldStart)
    }

    @Test
    fun `does not start while loading`() {
        val shouldStart = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = "price-taxi-medina-short-ride",
            selectedPriceCardId = null,
            isLoading = true,
            hasHandledInitialCardLoad = false
        )

        assertFalse(shouldStart)
    }

    @Test
    fun `does not start when initial-card load already handled`() {
        val shouldStart = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = "price-taxi-medina-short-ride",
            selectedPriceCardId = null,
            isLoading = false,
            hasHandledInitialCardLoad = true
        )

        assertFalse(shouldStart)
    }

    @Test
    fun `starts only once across state transitions`() {
        val initialCardId = "price-taxi-medina-short-ride"
        var hasHandled = false

        val firstAttempt = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = initialCardId,
            selectedPriceCardId = null,
            isLoading = false,
            hasHandledInitialCardLoad = hasHandled
        )
        assertTrue(firstAttempt)

        hasHandled = true

        val secondAttempt = QuoteActionStartWithCardPolicy.shouldStart(
            initialPriceCardId = initialCardId,
            selectedPriceCardId = null,
            isLoading = false,
            hasHandledInitialCardLoad = hasHandled
        )
        assertFalse(secondAttempt)
    }
}

class QuoteActionStepDataPolicyTest {

    @Test
    fun `input step requires selected card`() {
        assertFalse(QuoteActionStepDataPolicy.hasInputData(selectedPriceCard = null))
        assertTrue(QuoteActionStepDataPolicy.hasInputData(selectedPriceCard = sampleCard()))
    }

    @Test
    fun `result step requires selected card and result`() {
        val card = sampleCard()
        val result = PricingEngine.Output(
            adjustedMin = 20.0,
            adjustedMax = 30.0,
            fairness = PricingEngine.FairnessLevel.FAIR,
            counterMin = 20.0,
            counterMax = 28.5
        )

        assertFalse(QuoteActionStepDataPolicy.hasResultData(selectedPriceCard = null, result = result))
        assertFalse(QuoteActionStepDataPolicy.hasResultData(selectedPriceCard = card, result = null))
        assertTrue(QuoteActionStepDataPolicy.hasResultData(selectedPriceCard = card, result = result))
    }

    private fun sampleCard(): PriceCardDetail {
        return PriceCardDetail(
            id = "price-taxi-medina-short-ride",
            title = "Taxi ride",
            category = "taxi",
            expectedCostMinMad = 20,
            expectedCostMaxMad = 40
        )
    }
}

class QuoteActionQuantityPolicyTest {

    @Test
    fun `shows quantity controls when unit contains per case-insensitively`() {
        assertTrue(QuoteActionQuantityPolicy.shouldShowQuantity("Per person"))
        assertTrue(QuoteActionQuantityPolicy.shouldShowQuantity("per trip"))
        assertTrue(QuoteActionQuantityPolicy.shouldShowQuantity("PRICE PER ITEM"))
        assertTrue(QuoteActionQuantityPolicy.shouldShowQuantity("per-person"))
        assertTrue(QuoteActionQuantityPolicy.shouldShowQuantity("per_person"))
    }

    @Test
    fun `hides quantity controls when unit does not contain per`() {
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("session"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("person"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("supper menu"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("percentage"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("perçu"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity("per2"))
        assertFalse(QuoteActionQuantityPolicy.shouldShowQuantity(null))
    }
}

class QuoteActionAmountFormatterTest {

    @Test
    fun `format keeps whole numbers without decimal suffix`() {
        assertEquals("1200", QuoteActionAmountFormatter.format(1200.0))
    }

    @Test
    fun `format rounds decimal numbers to two places with half-up rounding`() {
        assertEquals("12.35", QuoteActionAmountFormatter.format(12.345))
    }

    @Test
    fun `format trims trailing zeroes after rounding`() {
        assertEquals("12.5", QuoteActionAmountFormatter.format(12.50))
    }

    @Test
    fun `format returns explicit unavailable marker for non-finite values`() {
        assertEquals("N/A", QuoteActionAmountFormatter.format(Double.NaN))
        assertEquals("N/A", QuoteActionAmountFormatter.format(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `shouldShowAdjustedRange hides row when formatted values match expected integers`() {
        assertFalse(
            QuoteActionAmountFormatter.shouldShowAdjustedRange(
                adjustedMin = 20.00000001,
                adjustedMax = 40.00000001,
                expectedMin = 20,
                expectedMax = 40
            )
        )
    }

    @Test
    fun `shouldShowAdjustedRange shows row when adjusted values differ after formatting`() {
        assertTrue(
            QuoteActionAmountFormatter.shouldShowAdjustedRange(
                adjustedMin = 20.01,
                adjustedMax = 40.0,
                expectedMin = 20,
                expectedMax = 40
            )
        )
    }

    @Test
    fun `shouldShowAdjustedRange shows row when adjusted value is non-finite`() {
        assertTrue(
            QuoteActionAmountFormatter.shouldShowAdjustedRange(
                adjustedMin = Double.NaN,
                adjustedMax = 40.0,
                expectedMin = 20,
                expectedMax = 40
            )
        )
    }
}
