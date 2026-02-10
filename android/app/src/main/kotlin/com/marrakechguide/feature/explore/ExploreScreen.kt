package com.marrakechguide.feature.explore

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.model.Place
import com.marrakechguide.core.repository.PlaceRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Explore screen showing curated places with filters and search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPlaceClick: (String) -> Unit = {},
    viewModel: ExploreViewModel = viewModel()
) {
    val appContext = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPlaces(appContext)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore") }
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
                        placeholder = { Text("Search places...") },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            // Category filter chips
            CategoryFilterBar(
                categories = viewModel.categories,
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) }
            )

            // Content based on state
            when {
                uiState.isLoading -> {
                    PlaceListSkeleton()
                }
                uiState.error != null -> {
                    ErrorContent(
                        message = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.loadPlaces(appContext) }
                    )
                }
                uiState.places.isEmpty() -> {
                    EmptyContent(searchQuery = searchQuery)
                }
                else -> {
                    PlaceList(
                        places = uiState.places,
                        onPlaceClick = onPlaceClick
                    )
                }
            }
        }
    }
}

/**
 * ViewModel for Explore screen.
 */
class ExploreViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val categories = EXPLORE_CATEGORY_FILTERS

    private var allPlaces: List<Place> = emptyList()
    private var searchQuery = ""
    private var hasLoadedPlaces = false

    fun loadPlaces(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val loadedPlaces = withContext(Dispatchers.IO) {
                    val db = ContentDatabase.getInstance(context.applicationContext)
                    val repository = PlaceRepositoryImpl(db)
                    repository.getAllPlacesOnce()
                }
                allPlaces = loadedPlaces
                hasLoadedPlaces = true
                applyFilters()
            } catch (e: Exception) {
                hasLoadedPlaces = false
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load places"
                )
            }
        }
    }

    fun search(query: String) {
        searchQuery = query.trim()
        if (hasLoadedPlaces) {
            applyFilters()
        }
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        if (hasLoadedPlaces) {
            applyFilters()
        }
    }

    private fun applyFilters() {
        val filtered = filterExplorePlaces(
            places = allPlaces,
            selectedCategory = _uiState.value.selectedCategory,
            rawQuery = searchQuery
        )
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            places = filtered
        )
    }
}

data class ExploreUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val selectedCategory: String? = null,
    val error: String? = null
)

// MARK: - Category Filter Bar

@Composable
fun CategoryFilterBar(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = isExploreCategorySelected(category = category, selectedCategory = selectedCategory),
                onClick = { onCategorySelected(if (category == "All") null else category) },
                label = {
                    Text(displayCategoryLabel(category))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

internal val EXPLORE_CATEGORY_FILTERS = listOf(
    "All",
    "landmark",
    "museum",
    "historic_site",
    "garden",
    "market",
    "neighborhood",
    "cafe",
    "restaurant"
)

internal fun displayCategoryLabel(category: String): String {
    return category
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

internal fun isExploreCategorySelected(category: String, selectedCategory: String?): Boolean {
    val normalizedSelected = selectedCategory?.trim()
    return if (category == "All") {
        normalizedSelected.isNullOrBlank() || normalizedSelected.equals("all", ignoreCase = true)
    } else {
        normalizedSelected.equals(category, ignoreCase = true)
    }
}

internal fun filterExplorePlaces(
    places: List<Place>,
    selectedCategory: String?,
    rawQuery: String
): List<Place> {
    val query = rawQuery.trim().lowercase()
    val normalizedCategory = selectedCategory?.trim()
    return places.filter { place ->
        val categoryMatches = normalizedCategory.isNullOrBlank() ||
            normalizedCategory.equals("all", ignoreCase = true) ||
            place.category?.equals(normalizedCategory, ignoreCase = true) == true

        val searchMatches = query.isEmpty() ||
            place.name.lowercase().contains(query) ||
            place.shortDescription?.lowercase()?.contains(query) == true ||
            place.neighborhood?.lowercase()?.contains(query) == true ||
            place.tags.any { it.lowercase().contains(query) }

        categoryMatches && searchMatches
    }
}

// MARK: - Place List

@Composable
fun PlaceList(
    places: List<Place>,
    onPlaceClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(places, key = { it.id }) { place ->
            PlaceCard(
                place = place,
                onClick = { onPlaceClick(place.id) }
            )
        }
    }
}

@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit
) {
    val accessibilityLabel = buildString {
        append(place.name)
        place.category?.let { append(", $it") }
        place.neighborhood?.let { append(", in $it") }
        place.priceRange?.let { append(", $it") }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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

                place.category?.let { category ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            place.shortDescription?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                place.neighborhood?.let { neighborhood ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = neighborhood,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                place.priceRange?.let { priceRange ->
                    Text(
                        text = priceRange,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                place.visitDurationRange?.let { duration ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Loading Skeleton

@Composable
fun PlaceListSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            PlaceCardSkeleton()
        }
    }
}

@Composable
fun PlaceCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

// MARK: - Empty State

@Composable
fun EmptyContent(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Places Found",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (searchQuery.isEmpty()) {
                "No places available in this category."
            } else {
                "No places matching \"$searchQuery\""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Error State

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something Went Wrong",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// MARK: - Place Detail Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: String?,
    onBackClick: () -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext
    var reloadNonce by remember(placeId) { mutableIntStateOf(0) }
    var place by remember { mutableStateOf<Place?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(placeId, reloadNonce) {
        isLoading = true
        error = null

        if (placeId == null) {
            place = null
            error = "Place not found"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            val loadedPlace = withContext(Dispatchers.IO) {
                val db = ContentDatabase.getInstance(appContext)
                val repository = PlaceRepositoryImpl(db)
                repository.getPlace(placeId)
            }
            place = loadedPlace
            if (loadedPlace == null) {
                error = "Place not found"
            }
        } catch (e: Exception) {
            place = null
            error = e.message ?: "Failed to load place"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "Place Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                ErrorContent(
                    message = error ?: "Failed to load place",
                    onRetry = { reloadNonce += 1 }
                )
            }
            place != null -> {
                PlaceDetailContent(
                    place = place!!,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                ErrorContent(
                    message = "Place not found",
                    onRetry = { reloadNonce += 1 }
                )
            }
        }
    }
}

@Composable
fun PlaceDetailContent(
    place: Place,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero section
        item {
            PlaceHeroSection(place = place)
        }

        // Quick facts
        item {
            PlaceQuickFacts(place = place)
        }

        // Description
        place.longDescription?.let { description ->
            item {
                DetailSection(title = "About") {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } ?: place.shortDescription?.let { description ->
            item {
                DetailSection(title = "About") {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Local tips
        if (place.localTips.isNotEmpty()) {
            item {
                TipsSection(
                    title = "Local Tips",
                    tips = place.localTips,
                    icon = Icons.Default.Lightbulb,
                    iconTint = Color(0xFFFF9800)
                )
            }
        }

        // Scam warnings
        if (place.scamWarnings.isNotEmpty()) {
            item {
                TipsSection(
                    title = "Watch Out For",
                    tips = place.scamWarnings,
                    icon = Icons.Default.Warning,
                    iconTint = MaterialTheme.colorScheme.error
                )
            }
        }

        // Do's and Don'ts
        if (place.doAndDont.isNotEmpty()) {
            item {
                TipsSection(
                    title = "Do's & Don'ts",
                    tips = place.doAndDont,
                    icon = Icons.Default.ThumbsUpDown,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Why we recommend
        if (place.whyRecommended.isNotEmpty()) {
            item {
                DetailSection(title = "Why We Recommend") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        place.whyRecommended.forEach { reason ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceHeroSection(place: Place) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            place.category?.let { category ->
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            place.touristTrapLevel?.let { level ->
                TouristTrapBadge(level = level)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = place.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        place.neighborhood?.let { neighborhood ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = neighborhood,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                place.address?.let { address ->
                    Text(
                        text = " • $address",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceQuickFacts(place: Place) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            place.hoursText?.let { hours ->
                QuickFactCard(
                    icon = Icons.Default.Schedule,
                    title = "Hours",
                    value = hours,
                    modifier = Modifier.weight(1f)
                )
            }

            place.priceRange?.let { priceRange ->
                QuickFactCard(
                    icon = Icons.Default.Payments,
                    title = "Cost",
                    value = priceRange,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            place.visitDurationRange?.let { duration ->
                QuickFactCard(
                    icon = Icons.Default.Timer,
                    title = "Visit Time",
                    value = duration,
                    modifier = Modifier.weight(1f)
                )
            }

            place.bestTimeToGo?.let { bestTime ->
                QuickFactCard(
                    icon = Icons.Default.WbSunny,
                    title = "Best Time",
                    value = bestTime,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickFactCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TouristTrapBadge(level: String) {
    val color = when (level.lowercase()) {
        "low" -> Color(0xFF4CAF50)
        "medium" -> Color(0xFFFF9800)
        "high" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Tourist trap: $level",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun TipsSection(
    title: String,
    tips: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tips.forEach { tip ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
