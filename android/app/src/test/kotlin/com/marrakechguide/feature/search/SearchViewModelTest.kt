package com.marrakechguide.feature.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun `clearing query during pending search clears spinner and results`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("ta")
        runCurrent()
        assertTrue(viewModel.uiState.value.isSearching)

        viewModel.updateQuery("")
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertFalse(state.isSearching)
        assertTrue(state.isQueryBlank)
        assertFalse(state.isQueryTooShort)
        assertTrue(state.results.places.isEmpty())
        assertTrue(state.results.priceCards.isEmpty())
        assertTrue(state.results.phrases.isEmpty())
    }

    @Test
    fun `latest query wins when query changes quickly`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("ta")
        runCurrent()

        viewModel.updateQuery("ham")
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ham", state.searchQuery)
        assertFalse(state.isSearching)
        assertTrue(state.results.priceCards.isNotEmpty())
        assertTrue(state.results.priceCards.all { card ->
            card.title.contains("hammam", ignoreCase = true)
        })
        assertTrue(state.results.priceCards.none { card ->
            card.title.contains("taxi", ignoreCase = true)
        })
    }

    @Test
    fun `query shorter than two clears previous results`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("taxi")
        runCurrent()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.results.priceCards.isNotEmpty())

        viewModel.updateQuery("t")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("t", state.searchQuery)
        assertFalse(state.isSearching)
        assertFalse(state.isQueryBlank)
        assertTrue(state.isQueryTooShort)
        assertTrue(state.results.places.isEmpty())
        assertTrue(state.results.priceCards.isEmpty())
        assertTrue(state.results.phrases.isEmpty())
        assertFalse(state.hasNoResults)
    }

    @Test
    fun `whitespace around query is ignored for matching`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery(" taxi ")
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(" taxi ", state.searchQuery)
        assertEquals("taxi", state.normalizedQuery)
        assertFalse(state.isSearching)
        assertFalse(state.isQueryBlank)
        assertFalse(state.isQueryTooShort)
        assertTrue(state.results.priceCards.isNotEmpty())
        assertTrue(state.results.priceCards.any { card ->
            card.title.contains("taxi", ignoreCase = true)
        })
    }

    @Test
    fun `whitespace-only query formatting changes do not rerun search`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("taxi")
        runCurrent()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.results.priceCards.isNotEmpty())

        viewModel.updateQuery(" taxi ")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(" taxi ", state.searchQuery)
        assertEquals("taxi", state.normalizedQuery)
        assertFalse(state.isSearching)
        assertTrue(state.results.priceCards.isNotEmpty())
    }

    @Test
    fun `whitespace changes while searching keep active request and complete`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("ta")
        runCurrent()
        assertTrue(viewModel.uiState.value.isSearching)

        viewModel.updateQuery(" ta ")
        runCurrent()
        assertTrue(viewModel.uiState.value.isSearching)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(" ta ", state.searchQuery)
        assertEquals("ta", state.normalizedQuery)
        assertFalse(state.isSearching)
        assertTrue(state.results.places.isNotEmpty() || state.results.priceCards.isNotEmpty() || state.results.phrases.isNotEmpty())
    }

    @Test
    fun `whitespace-only query is treated as blank state`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("   ")
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("   ", state.searchQuery)
        assertEquals("", state.normalizedQuery)
        assertFalse(state.isSearching)
        assertTrue(state.isQueryBlank)
        assertFalse(state.isQueryTooShort)
        assertFalse(state.hasNoResults)
        assertTrue(state.results.places.isEmpty())
        assertTrue(state.results.priceCards.isEmpty())
        assertTrue(state.results.phrases.isEmpty())
    }

    @Test
    fun `recent searches dedupe case-insensitively`() = runTest {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("taxi")
        runCurrent()
        advanceUntilIdle()
        assertEquals(listOf("taxi"), viewModel.uiState.value.recentSearches)

        viewModel.updateQuery("Taxi")
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Taxi"), state.recentSearches)
    }

    @Test
    fun `uppercase query matches taxi results under Turkish locale`() = runTest {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val viewModel = SearchViewModel()

            viewModel.updateQuery("TAXI")
            runCurrent()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSearching)
            assertFalse(state.hasNoResults)
            assertTrue(state.results.priceCards.isNotEmpty())
            assertTrue(state.results.priceCards.any { card ->
                card.title.contains("taxi", ignoreCase = true)
            })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `formatCategoryLabel uses locale root under Turkish locale`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("Istanbul", formatCategoryLabel("istanbul"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `formatCategoryLabel preserves already capitalized text`() {
        assertEquals("Taxi", formatCategoryLabel("Taxi"))
    }
}
