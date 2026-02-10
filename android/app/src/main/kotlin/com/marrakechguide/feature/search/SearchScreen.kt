package com.marrakechguide.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.model.Place
import com.marrakechguide.core.model.Phrase
import com.marrakechguide.core.model.PriceCard
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.components.PriceTag
import com.marrakechguide.ui.theme.Spacing
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Global search screen that searches across all content types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = remember { SearchViewModel() },
    onPlaceSelected: (String) -> Unit = {},
    onPriceCardSelected: (String) -> Unit = {},
    onPhraseSelected: (Phrase) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search places, prices, phrases...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Content
            LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                when {
                    uiState.isQueryBlank -> {
                        if (uiState.recentSearches.isNotEmpty()) {
                            item {
                                RecentSearchesSection(
                                    searches = uiState.recentSearches,
                                    onSelect = { viewModel.updateQuery(it) },
                                    onClear = { viewModel.clearRecentSearches() }
                                )
                            }
                        } else {
                            item {
                                SearchHintsSection()
                            }
                        }
                    }
                    uiState.isQueryTooShort -> {
                        item {
                            QueryTooShortContent()
                        }
                    }
                    uiState.isSearching -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    uiState.hasNoResults -> {
                        item {
                            NoResultsContent(query = uiState.normalizedQuery)
                        }
                    }
                    else -> {
                        // Places
                        if (uiState.results.places.isNotEmpty()) {
                            item {
                                SearchResultHeader(
                                    title = "Places",
                                    icon = Icons.Default.LocationOn,
                                    count = uiState.results.places.size
                                )
                            }
                            items(uiState.results.places, key = { it.id }) { place ->
                                PlaceSearchResultCard(
                                    place = place,
                                    onClick = { onPlaceSelected(place.id) }
                                )
                            }
                        }

                        // Price Cards
                        if (uiState.results.priceCards.isNotEmpty()) {
                            item {
                                SearchResultHeader(
                                    title = "Prices",
                                    icon = Icons.Default.CreditCard,
                                    count = uiState.results.priceCards.size
                                )
                            }
                            items(uiState.results.priceCards, key = { it.id }) { card ->
                                PriceCardSearchResultCard(
                                    card = card,
                                    onClick = { onPriceCardSelected(card.id) }
                                )
                            }
                        }

                        // Phrases
                        if (uiState.results.phrases.isNotEmpty()) {
                            item {
                                SearchResultHeader(
                                    title = "Phrases",
                                    icon = Icons.Default.Translate,
                                    count = uiState.results.phrases.size
                                )
                            }
                            items(uiState.results.phrases, key = { it.id }) { phrase ->
                                PhraseSearchResultCard(
                                    phrase = phrase,
                                    onClick = { onPhraseSelected(phrase) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ViewModel for Search screen.
 */
class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var activeSearchJob: Job? = null
    private var activeSearchVersion: Long = 0

    fun updateQuery(query: String) {
        val currentState = _uiState.value
        val normalizedQuery = query.trim()
        if (normalizedQuery == currentState.normalizedQuery) {
            // Avoid duplicate searches and loading flicker when only whitespace changes.
            _uiState.value = currentState.copy(searchQuery = query)
            return
        }

        _uiState.value = currentState.copy(searchQuery = query)
        activeSearchJob?.cancel()

        if (normalizedQuery.length < 2) {
            activeSearchVersion += 1
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                results = SearchResults()
            )
            return
        }

        val searchVersion = beginSearch()
        activeSearchJob = viewModelScope.launch {
            performSearch(normalizedQuery, searchVersion)
        }
    }

    private fun beginSearch(): Long {
        activeSearchVersion += 1
        return activeSearchVersion
    }

    private fun isStaleSearch(version: Long, query: String): Boolean {
        return version != activeSearchVersion || _uiState.value.normalizedQuery != query
    }

    private suspend fun performSearch(query: String, version: Long) {
        _uiState.value = _uiState.value.copy(isSearching = true)

        // Simulate search delay
        delay(100)
        if (isStaleSearch(version, query)) {
            return
        }

        val queryLower = query.lowercase(Locale.ROOT)
        val results = SearchResults(
            places = MockSearchData.places.filter { place ->
                place.name.lowercase(Locale.ROOT).contains(queryLower) ||
                    (place.shortDescription?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (place.neighborhood?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (place.tags?.any { it.lowercase(Locale.ROOT).contains(queryLower) } == true)
            },
            priceCards = MockSearchData.priceCards.filter { card ->
                card.title.lowercase(Locale.ROOT).contains(queryLower) ||
                    (card.category?.lowercase(Locale.ROOT)?.contains(queryLower) == true)
            },
            phrases = MockSearchData.phrases.filter { phrase ->
                (phrase.latin?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (phrase.english?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (phrase.arabic?.contains(queryLower) == true)
            }
        )

        if (isStaleSearch(version, query)) {
            return
        }

        _uiState.value = _uiState.value.copy(
            isSearching = false,
            results = results
        )

        // Save to recent searches
        if (results.places.isNotEmpty() || results.priceCards.isNotEmpty() || results.phrases.isNotEmpty()) {
            val recents = _uiState.value.recentSearches.toMutableList()
            recents.removeAll { existingQuery ->
                existingQuery.equals(query, ignoreCase = true)
            }
            recents.add(0, query)
            if (recents.size > 10) {
                recents.removeLast()
            }
            _uiState.value = _uiState.value.copy(recentSearches = recents)
        }
    }

    fun clearRecentSearches() {
        _uiState.value = _uiState.value.copy(recentSearches = emptyList())
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val results: SearchResults = SearchResults(),
    val recentSearches: List<String> = emptyList()
) {
    val normalizedQuery: String
        get() = searchQuery.trim()

    val isQueryBlank: Boolean
        get() = normalizedQuery.isEmpty()

    val isQueryTooShort: Boolean
        get() = normalizedQuery.isNotEmpty() && normalizedQuery.length < 2

    val hasNoResults: Boolean
        get() = !isSearching && normalizedQuery.length >= 2 &&
            results.places.isEmpty() &&
            results.priceCards.isEmpty() &&
            results.phrases.isEmpty()
}

data class SearchResults(
    val places: List<Place> = emptyList(),
    val priceCards: List<PriceCard> = emptyList(),
    val phrases: List<Phrase> = emptyList()
)

internal fun formatCategoryLabel(category: String): String {
    return category.replaceFirstChar { firstCharacter ->
        if (firstCharacter.isLowerCase()) {
            firstCharacter.titlecase(Locale.ROOT)
        } else {
            firstCharacter.toString()
        }
    }
}

@Composable
private fun RecentSearchesSection(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }

        searches.forEach { search ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(search) }
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = search,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun SearchHintsSection() {
    Column(
        modifier = Modifier.padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "Search for...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        SearchHintRow(
            icon = Icons.Default.LocationOn,
            text = "Places: \"Jemaa\", \"museum\", \"garden\""
        )
        SearchHintRow(
            icon = Icons.Default.CreditCard,
            text = "Prices: \"taxi\", \"hammam\", \"guide\""
        )
        SearchHintRow(
            icon = Icons.Default.Translate,
            text = "Phrases: \"shukran\", \"how much\""
        )
    }
}

@Composable
private fun QueryTooShortContent() {
    EmptyState(
        icon = Icons.Default.Search,
        title = "Keep typing",
        message = "Enter at least 2 characters to search."
    )
}

@Composable
private fun SearchHintRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchResultHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaceSearchResultCard(
    place: Place,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            place.shortDescription?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                place.category?.let { category ->
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                place.neighborhood?.let { neighborhood ->
                    Text(
                        text = neighborhood,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceCardSearchResultCard(
    card: PriceCard,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                card.category?.let { category ->
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(formatCategoryLabel(category), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            PriceTag(
                minMad = card.expectedCostMinMad,
                maxMad = card.expectedCostMaxMad
            )
        }
    }
}

@Composable
private fun PhraseSearchResultCard(
    phrase: Phrase,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            phrase.latin?.let { latin ->
                Text(
                    text = latin,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            phrase.english?.let { english ->
                Text(
                    text = english,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            phrase.category?.let { category ->
                SuggestionChip(
                    onClick = onClick,
                    label = { Text(formatCategoryLabel(category), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun NoResultsContent(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Try checking your spelling or using simpler terms.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Mock data
object MockSearchData {
    val places = listOf(
        Place(
            id = "jemaa-el-fna",
            name = "Jemaa el-Fna",
            category = "Landmarks",
            shortDescription = "The beating heart of Marrakech",
            neighborhood = "Medina",
            tags = listOf("square", "market", "food")
        ),
        Place(
            id = "bahia-palace",
            name = "Bahia Palace",
            category = "Museums",
            shortDescription = "A stunning 19th-century palace",
            neighborhood = "Mellah",
            tags = listOf("palace", "history", "architecture")
        )
    )

    val priceCards = listOf(
        PriceCard(
            id = "taxi-airport",
            title = "Airport Taxi",
            category = "transport",
            unit = "trip",
            expectedCostMinMad = 150,
            expectedCostMaxMad = 200
        ),
        PriceCard(
            id = "taxi-medina",
            title = "Petit Taxi (Medina)",
            category = "transport",
            unit = "trip",
            expectedCostMinMad = 15,
            expectedCostMaxMad = 30
        ),
        PriceCard(
            id = "hammam-local",
            title = "Local Hammam",
            category = "wellness",
            unit = "session",
            expectedCostMinMad = 15,
            expectedCostMaxMad = 25
        ),
        PriceCard(
            id = "hammam-tourist",
            title = "Tourist Hammam",
            category = "wellness",
            unit = "session",
            expectedCostMinMad = 150,
            expectedCostMaxMad = 400
        ),
        PriceCard(
            id = "guide-half-day",
            title = "Guide (Half Day)",
            category = "services",
            unit = "4 hours",
            expectedCostMinMad = 300,
            expectedCostMaxMad = 500
        )
    )

    val phrases = listOf(
        Phrase(
            id = "greet-hello",
            latin = "Salam",
            arabic = "سلام",
            english = "Hello / Peace",
            category = "greetings"
        ),
        Phrase(
            id = "greet-thank-you",
            latin = "Shukran",
            arabic = "شكرا",
            english = "Thank you",
            category = "greetings"
        ),
        Phrase(
            id = "shop-how-much",
            latin = "B'shhal?",
            arabic = "بشحال؟",
            english = "How much?",
            category = "shopping"
        ),
        Phrase(
            id = "shop-expensive",
            latin = "Ghali bezaf",
            arabic = "غالي بزاف",
            english = "Too expensive",
            category = "shopping"
        ),
        Phrase(
            id = "dir-where",
            latin = "Fin kayn...?",
            arabic = "فين كاين...؟",
            english = "Where is...?",
            category = "directions"
        )
    )
}
