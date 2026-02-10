package com.marrakechguide.feature.quote

import com.marrakechguide.core.model.PriceCardDetail
import com.marrakechguide.core.repository.PriceCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteActionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateQuotedAmount strips invalid characters and keeps a single decimal point`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1a2..3b4")

        assertEquals("12.34", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps first decimal when multiple separators are entered`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("0.1.2.3")

        assertEquals("0.123", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes comma decimal separator`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1,5")

        assertEquals("1.5", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount uses last separator as decimal when both comma and dot are present`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1,2.3")

        assertEquals("12.3", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps grouped thousands and decimal for US style input`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1,200.50")

        assertEquals("1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps grouped thousands and decimal for EU style input`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1.200,50")

        assertEquals("1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount preserves leading minus sign`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("-1,200.50")

        assertEquals("-1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount treats single grouped separator as thousands`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1,200")

        assertEquals("1200", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps leading-zero dot decimal with three fraction digits`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("0.500")

        assertEquals("0.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps leading-zero comma decimal with three fraction digits`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("0,500")

        assertEquals("0.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps comma decimal when integer part has more than three digits`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1234,567")

        assertEquals("1234.567", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps dot decimal when integer part has more than three digits`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("1234.567")

        assertEquals("1234.567", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount treats multi-group comma value with leading-zero first group as thousands`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("000,001,000")

        assertEquals("000001000", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount treats multi-group dot value with leading-zero first group as thousands`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("000.001.000")

        assertEquals("000001000", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps single comma zero-prefixed group as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("000,500")

        assertEquals("000.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps two-digit leading-zero comma input as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("00,500")

        assertEquals("00.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps single comma value with non-zero leading-zero group as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("010,500")

        assertEquals("010.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount treats single comma value with trailing triple zeroes as grouped thousands`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("010,000")

        assertEquals("010000", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps single dot zero-prefixed group as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("000.500")

        assertEquals("000.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps single dot value with non-zero leading-zero group as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("010.500")

        assertEquals("010.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount treats single dot value with trailing triple zeroes as grouped thousands`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("010.000")

        assertEquals("010000", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount keeps two-digit leading-zero dot input as decimal`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("00.500")

        assertEquals("00.500", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes Arabic digits and decimal separator`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("١٢٫٥")

        assertEquals("12.5", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes Arabic thousands and decimal separators`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("١٬٢٠٠٫٥٠")

        assertEquals("1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount supports Unicode minus sign with Arabic numerals`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("−١٢٫٥")

        assertEquals("-12.5", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes full-width digits and decimal separator`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("１２．５")

        assertEquals("12.5", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes full-width grouped separators`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("１，２００．５０")

        assertEquals("1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes ideographic decimal separator`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("１２。５")

        assertEquals("12.5", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `updateQuotedAmount normalizes half-width ideographic comma separator`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.updateQuotedAmount("１､２００。５０")

        assertEquals("1200.50", viewModel.uiState.value.quotedAmount)
    }

    @Test
    fun `canEvaluate accepts normalized Arabic numeral input`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())
        viewModel.selectPriceCard(sampleCard("price-taxi-medina-short-ride", "taxi"))
        viewModel.updateQuotedAmount("١٢٫٥")

        assertTrue(viewModel.canEvaluate())
    }

    @Test
    fun `canEvaluate returns false for non-finite quote`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())
        viewModel.selectPriceCard(sampleCard("price-taxi-medina-short-ride", "taxi"))
        viewModel.updateQuotedAmount("9".repeat(400))

        assertFalse(viewModel.canEvaluate())
    }

    @Test
    fun `canEvaluate returns false for negative quote`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())
        viewModel.selectPriceCard(sampleCard("price-taxi-medina-short-ride", "taxi"))
        viewModel.updateQuotedAmount("-50")

        assertFalse(viewModel.canEvaluate())
    }

    @Test
    fun `evaluate ignores non-finite quote and keeps input step`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())
        viewModel.selectPriceCard(sampleCard("price-taxi-medina-short-ride", "taxi"))
        viewModel.updateQuotedAmount("9".repeat(400))

        viewModel.evaluate()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.INPUT, state.step)
        assertNull(state.result)
    }

    @Test
    fun `evaluate ignores negative quote and keeps input step`() = runTest {
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())
        viewModel.selectPriceCard(sampleCard("price-taxi-medina-short-ride", "taxi"))
        viewModel.updateQuotedAmount("-50")

        viewModel.evaluate()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.INPUT, state.step)
        assertNull(state.result)
    }

    @Test
    fun `selectPriceCard resets previous quote quantity and result`() = runTest {
        val firstCard = sampleCard("price-taxi-medina-short-ride", "taxi")
        val secondCard = sampleCard("price-taxi-airport-transfer", "taxi")
        val viewModel = QuoteActionViewModel(FakePriceCardRepository())

        viewModel.selectPriceCard(firstCard)
        viewModel.updateQuotedAmount("120")
        viewModel.updateQuantity(3)
        viewModel.evaluate()
        assertEquals(QuoteStep.RESULT, viewModel.uiState.value.step)
        assertEquals(3, viewModel.uiState.value.quantity)
        assertEquals("120", viewModel.uiState.value.quotedAmount)
        assertTrue(viewModel.uiState.value.result != null)

        viewModel.selectPriceCard(secondCard)

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(secondCard.id, state.selectedPriceCard?.id)
        assertEquals("", state.quotedAmount)
        assertEquals(1, state.quantity)
        assertNull(state.result)
    }

    @Test
    fun `startWithPriceCard success clears loading and opens input step`() = runTest {
        val card = sampleCard("price-taxi-medina-short-ride", "taxi")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(card.id to card)
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard(card.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(card.id, state.selectedPriceCard?.id)
        assertNull(state.error)
    }

    @Test
    fun `startWithPriceCard trims surrounding whitespace before lookup`() = runTest {
        val card = sampleCard("price-taxi-medina-short-ride", "taxi")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(card.id to card)
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard("  ${card.id}  ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(card.id, state.selectedPriceCard?.id)
        assertNull(state.error)
    }

    @Test
    fun `startWithPriceCard with whitespace-only id stays on category with not found error`() = runTest {
        val card = sampleCard("price-taxi-medina-short-ride", "taxi")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(card.id to card)
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard(" \n\t ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertNull(state.selectedPriceCard)
        assertEquals("Price card not found", state.error)
    }

    @Test
    fun `selectCategory with single card auto-select clears loading`() = runTest {
        val card = sampleCard("price-activity-city-half-day", "guides")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(card.id to card),
            cardsByCategory = mapOf(PriceCategory.GUIDES.dbValue to listOf(card))
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(card.id, state.selectedPriceCard?.id)
        assertTrue(state.modifierSelections.isEmpty())
    }

    @Test
    fun `goBack from auto-selected single-card category returns to category step`() = runTest {
        val taxiCardA = sampleCard("price-taxi-medina-short-ride", "taxi")
        val taxiCardB = sampleCard("price-taxi-airport-transfer", "taxi")
        val guidesCard = sampleCard("price-guide-half-day", "guides")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(
                taxiCardA.id to taxiCardA,
                taxiCardB.id to taxiCardB,
                guidesCard.id to guidesCard
            ),
            cardsByCategory = mapOf(
                PriceCategory.TAXI.dbValue to listOf(taxiCardA, taxiCardB),
                PriceCategory.GUIDES.dbValue to listOf(guidesCard)
            )
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.TAXI)
        advanceUntilIdle()
        assertEquals(QuoteStep.PRICE_CARD, viewModel.uiState.value.step)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()
        assertEquals(QuoteStep.INPUT, viewModel.uiState.value.step)
        assertEquals(guidesCard.id, viewModel.uiState.value.selectedPriceCard?.id)

        viewModel.goBack()
        assertEquals(QuoteStep.CATEGORY, viewModel.uiState.value.step)
    }

    @Test
    fun `selectCategory failure keeps category step and clears loading`() = runTest {
        val card = sampleCard("price-taxi-medina-short-ride", "taxi")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(card.id to card),
            categoryFailure = IllegalStateException("db unavailable")
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard(card.id)
        advanceUntilIdle()
        assertEquals(QuoteStep.INPUT, viewModel.uiState.value.step)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertNull(state.selectedPriceCard)
        assertEquals("db unavailable", state.error)
    }

    @Test
    fun `selectCategory with no cards stays on category and shows message`() = runTest {
        val repository = FakePriceCardRepository(
            cardsByCategory = mapOf(PriceCategory.ACTIVITIES.dbValue to emptyList())
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.ACTIVITIES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertTrue(state.priceCards.isEmpty())
        assertEquals("No price cards available for this category", state.error)
    }

    @Test
    fun `selectCategory failure without message uses fallback`() = runTest {
        val repository = FakePriceCardRepository(
            categoryFailure = IllegalStateException()
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertEquals("Unable to load price cards", state.error)
    }

    @Test
    fun `selectCategory failure with blank message uses fallback`() = runTest {
        val repository = FakePriceCardRepository(
            categoryFailure = IllegalStateException("")
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertEquals("Unable to load price cards", state.error)
    }

    @Test
    fun `selectCategory failure trims message before showing`() = runTest {
        val repository = FakePriceCardRepository(
            categoryFailure = IllegalStateException("  db unavailable  ")
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertEquals("db unavailable", state.error)
    }

    @Test
    fun `latest category selection wins when requests complete out of order`() = runTest {
        val taxiCardA = sampleCard("price-taxi-medina-short-ride", "taxi")
        val taxiCardB = sampleCard("price-taxi-airport-transfer", "taxi")
        val guidesCard = sampleCard("price-guide-half-day", "guides")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(
                taxiCardA.id to taxiCardA,
                taxiCardB.id to taxiCardB,
                guidesCard.id to guidesCard
            ),
            cardsByCategory = mapOf(
                PriceCategory.TAXI.dbValue to listOf(taxiCardA, taxiCardB),
                PriceCategory.GUIDES.dbValue to listOf(guidesCard)
            ),
            categoryDelayMs = mapOf(
                PriceCategory.TAXI.dbValue to 100L,
                PriceCategory.GUIDES.dbValue to 10L
            )
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.TAXI)
        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PriceCategory.GUIDES, state.selectedCategory)
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(guidesCard.id, state.selectedPriceCard?.id)
        assertEquals(listOf(guidesCard.id), state.priceCards.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun `stale canceled category result is ignored when repository work is non-cancellable`() = runTest {
        val taxiCardA = sampleCard("price-taxi-medina-short-ride", "taxi")
        val taxiCardB = sampleCard("price-taxi-airport-transfer", "taxi")
        val guidesCard = sampleCard("price-guide-half-day", "guides")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(
                taxiCardA.id to taxiCardA,
                taxiCardB.id to taxiCardB,
                guidesCard.id to guidesCard
            ),
            cardsByCategory = mapOf(
                PriceCategory.TAXI.dbValue to listOf(taxiCardA, taxiCardB),
                PriceCategory.GUIDES.dbValue to listOf(guidesCard)
            ),
            categoryDelayIgnoringCancellationMs = mapOf(PriceCategory.TAXI.dbValue to 100L),
            categoryDelayMs = mapOf(PriceCategory.GUIDES.dbValue to 10L)
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.TAXI)
        viewModel.selectCategory(PriceCategory.GUIDES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PriceCategory.GUIDES, state.selectedCategory)
        assertEquals(QuoteStep.INPUT, state.step)
        assertEquals(guidesCard.id, state.selectedPriceCard?.id)
        assertEquals(listOf(guidesCard.id), state.priceCards.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun `startWithPriceCard not found resets to category with error`() = runTest {
        val existingCard = sampleCard("price-taxi-medina-short-ride", "taxi")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(existingCard.id to existingCard),
            cardsByCategory = mapOf(PriceCategory.TAXI.dbValue to listOf(existingCard))
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.TAXI)
        advanceUntilIdle()
        assertEquals(QuoteStep.INPUT, viewModel.uiState.value.step)

        viewModel.startWithPriceCard("missing-id")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertNull(state.selectedCategory)
        assertNull(state.selectedPriceCard)
        assertTrue(state.priceCards.isEmpty())
        assertEquals("Price card not found", state.error)
    }

    @Test
    fun `startWithPriceCard failure without message uses fallback`() = runTest {
        val repository = FakePriceCardRepository(
            cardFailure = IllegalStateException()
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard("price-any")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertNull(state.selectedCategory)
        assertNull(state.selectedPriceCard)
        assertEquals("Unable to load price card", state.error)
    }

    @Test
    fun `startWithPriceCard failure with blank message uses fallback`() = runTest {
        val repository = FakePriceCardRepository(
            cardFailure = IllegalStateException("")
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard("price-any")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertNull(state.selectedCategory)
        assertNull(state.selectedPriceCard)
        assertEquals("Unable to load price card", state.error)
    }

    @Test
    fun `startWithPriceCard failure with whitespace message uses fallback`() = runTest {
        val repository = FakePriceCardRepository(
            cardFailure = IllegalStateException("   ")
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.startWithPriceCard("price-any")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuoteStep.CATEGORY, state.step)
        assertFalse(state.isLoading)
        assertNull(state.selectedCategory)
        assertNull(state.selectedPriceCard)
        assertEquals("Unable to load price card", state.error)
    }

    @Test
    fun `startWithPriceCard success clears prior selected category`() = runTest {
        val taxiCard = sampleCard("price-taxi-medina-short-ride", "taxi")
        val guidesCard = sampleCard("price-guide-half-day", "guides")
        val repository = FakePriceCardRepository(
            cardsById = mapOf(
                taxiCard.id to taxiCard,
                guidesCard.id to guidesCard
            ),
            cardsByCategory = mapOf(
                PriceCategory.TAXI.dbValue to listOf(taxiCard)
            )
        )
        val viewModel = QuoteActionViewModel(repository)

        viewModel.selectCategory(PriceCategory.TAXI)
        advanceUntilIdle()
        assertEquals(PriceCategory.TAXI, viewModel.uiState.value.selectedCategory)
        assertEquals(QuoteStep.INPUT, viewModel.uiState.value.step)

        viewModel.startWithPriceCard(guidesCard.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(QuoteStep.INPUT, state.step)
        assertNull(state.selectedCategory)
        assertEquals(guidesCard.id, state.selectedPriceCard?.id)
    }

    private fun sampleCard(id: String, category: String): PriceCardDetail {
        return PriceCardDetail(
            id = id,
            title = "Sample Card",
            category = category,
            expectedCostMinMad = 20,
            expectedCostMaxMad = 50
        )
    }

    private class FakePriceCardRepository(
        private val cardsById: Map<String, PriceCardDetail> = emptyMap(),
        private val cardsByCategory: Map<String, List<PriceCardDetail>> = emptyMap(),
        private val categoryFailure: Throwable? = null,
        private val categoryDelayMs: Map<String, Long> = emptyMap(),
        private val categoryDelayIgnoringCancellationMs: Map<String, Long> = emptyMap(),
        private val cardFailure: Throwable? = null
    ) : PriceCardRepository {
        override fun getAllPriceCards(): Flow<List<PriceCardDetail>> = flowOf(cardsById.values.toList())

        override suspend fun getAllPriceCardsOnce(): List<PriceCardDetail> = cardsById.values.toList()

        override suspend fun getPriceCard(id: String): PriceCardDetail? {
            cardFailure?.let { throw it }
            return cardsById[id]
        }

        override fun getPriceCardsByCategory(category: String): Flow<List<PriceCardDetail>> {
            return flowOf(cardsByCategory[category].orEmpty())
        }

        override suspend fun getPriceCardsByCategoryOnce(category: String): List<PriceCardDetail> {
            categoryFailure?.let { throw it }
            categoryDelayIgnoringCancellationMs[category]?.let { delayMs ->
                if (delayMs > 0) {
                    withContext(NonCancellable) {
                        delay(delayMs)
                    }
                }
            }
            categoryDelayMs[category]?.let { delayMs ->
                if (delayMs > 0) {
                    delay(delayMs)
                }
            }
            return cardsByCategory[category].orEmpty()
        }

        override suspend fun searchPriceCards(query: String, limit: Int): List<PriceCardDetail> = emptyList()

        override suspend fun getModifiers(cardId: String) = emptyList<com.marrakechguide.core.model.ContextModifier>()

        override suspend fun getScripts(cardId: String) = emptyList<com.marrakechguide.core.model.NegotiationScript>()
    }
}
