package com.marrakechguide.feature.eat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.model.Place
import com.marrakechguide.core.repository.PlaceRepositoryImpl
import com.marrakechguide.ui.components.ListItemSkeleton
import com.marrakechguide.ui.components.ErrorState
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.components.PriceTag
import com.marrakechguide.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Eat screen showing curated restaurant and cafe recommendations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatScreen(
    onPlaceClick: (String) -> Unit = {},
    viewModel: EatViewModel = remember { EatViewModel() }
) {
    val appContext = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadRestaurants(appContext)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eat") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            viewModel.search(it)
                        },
                        onSearch = { viewModel.search(it) },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search restaurants...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.search("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {}

            // Filter chips
            EatFilterBar(
                filters = viewModel.filters,
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            // Content based on state
            when {
                uiState.isLoading -> {
                    ListItemSkeleton(
                        modifier = Modifier.fillMaxSize(),
                        rows = 6
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.loadRestaurants(appContext) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.places.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Restaurant,
                        title = "No Restaurants Found",
                        message = if (searchQuery.isEmpty()) {
                            "No restaurants available with this filter."
                        } else {
                            "No restaurants matching \"$searchQuery\""
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    RestaurantList(
                        places = uiState.places,
                        onPlaceClick = onPlaceClick
                    )
                }
            }
        }
    }
}

/**
 * ViewModel for Eat screen.
 */
class EatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EatUiState())
    val uiState: StateFlow<EatUiState> = _uiState.asStateFlow()

    val filters = listOf("All", "Budget", "Rooftop", "Family", "Veg-Friendly", "Local")

    private var allPlaces: List<Place> = emptyList()
    private var searchQuery = ""

    fun loadRestaurants(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val db = ContentDatabase.getInstance(context)
                val repository = PlaceRepositoryImpl(db)

                // Get restaurants and cafes
                val restaurants = repository.getPlacesByCategoryOnce("restaurant")
                val cafes = repository.getPlacesByCategoryOnce("cafe")
                allPlaces = restaurants + cafes
                applyFilters()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load restaurants"
                )
            }
        }
    }

    fun selectFilter(filter: String?) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        applyFilters()
    }

    fun search(query: String) {
        searchQuery = query.trim()
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allPlaces

        // Apply filter
        val filter = _uiState.value.selectedFilter
        if (filter != null && filter != "All") {
            val filterLower = filter.lowercase()
            filtered = filtered.filter { place ->
                place.tags?.any { tag -> tag.lowercase().contains(filterLower) } == true
            }
        }

        // Apply search
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { place ->
                place.name.lowercase().contains(query) ||
                    (place.shortDescription?.lowercase()?.contains(query) == true) ||
                    (place.neighborhood?.lowercase()?.contains(query) == true) ||
                    (place.tags?.any { it.lowercase().contains(query) } == true)
            }
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            places = filtered
        )
    }
}

data class EatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val places: List<Place> = emptyList(),
    val selectedFilter: String? = null
)

@Composable
private fun EatFilterBar(
    filters: List<String>,
    selectedFilter: String?,
    onFilterSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        filters.forEach { filter ->
            val isSelected = selectedFilter == filter || (selectedFilter == null && filter == "All")
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(if (filter == "All") null else filter) },
                label = { Text(filter) }
            )
        }
    }
}

@Composable
private fun RestaurantList(
    places: List<Place>,
    onPlaceClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(places, key = { it.id }) { place ->
            RestaurantCard(
                place = place,
                onClick = { onPlaceClick(place.id) }
            )
        }
    }
}

@Composable
private fun RestaurantCard(
    place: Place,
    onClick: () -> Unit
) {
    val priceTier = when {
        (place.expectedCostMinMad ?: 0) < 50 -> "$"
        (place.expectedCostMinMad ?: 0) < 100 -> "$$"
        else -> "$$$"
    }

    val priceTierColor = when {
        (place.expectedCostMinMad ?: 0) < 50 -> Color(0xFF4CAF50) // Green
        (place.expectedCostMinMad ?: 0) < 100 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = priceTier,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = priceTierColor
                )
            }

            place.shortDescription?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                place.neighborhood?.let { neighborhood ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = neighborhood,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val minCost = place.expectedCostMinMad
                val maxCost = place.expectedCostMaxMad
                if (minCost != null && maxCost != null) {
                    PriceTag(minMad = minCost, maxMad = maxCost)
                }
            }

            // Tags
            place.tags?.take(3)?.let { tags ->
                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
