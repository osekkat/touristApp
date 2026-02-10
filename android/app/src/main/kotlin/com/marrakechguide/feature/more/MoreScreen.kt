package com.marrakechguide.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marrakechguide.ui.components.ContentCard
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.theme.Spacing
import java.util.UUID

@Composable
fun MoreScreen(
    onNavigateToPhrasebook: () -> Unit = {},
    onNavigateToItineraries: () -> Unit = {},
    onNavigateToTips: () -> Unit = {},
    onNavigateToCulture: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        item {
            MoreListItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Darija Phrasebook",
                subtitle = "Essential phrases for getting around",
                onClick = onNavigateToPhrasebook,
            )
        }

        item {
            MoreListItem(
                icon = Icons.Filled.Map,
                title = "Itineraries",
                subtitle = "Pre-built day plans and routes",
                onClick = onNavigateToItineraries,
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }

        item {
            MoreListItem(
                icon = Icons.Filled.Lightbulb,
                title = "Tips & Safety",
                subtitle = "Practical advice and scam awareness",
                onClick = onNavigateToTips,
            )
        }

        item {
            MoreListItem(
                icon = Icons.Filled.Book,
                title = "Culture & Etiquette",
                subtitle = "Do's and don'ts for respectful visits",
                onClick = onNavigateToCulture,
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }

        item {
            MoreListItem(
                icon = Icons.Filled.Favorite,
                title = "Saved Items",
                subtitle = "Your bookmarked places and cards",
                onClick = onNavigateToFavorites,
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }

        item {
            MoreListItem(
                icon = Icons.Filled.Settings,
                title = "Settings",
                subtitle = "App preferences and data",
                onClick = onNavigateToSettings,
            )
        }
    }
}

@Composable
private fun MoreListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
    )
}

// MARK: - Itineraries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItinerariesListScreen(
    onNavigateBack: () -> Unit = {},
    onOpenItinerary: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itineraries") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(ItineraryCatalog.samples) { itinerary ->
                ItineraryCard(
                    itinerary = itinerary,
                    onClick = { onOpenItinerary(itinerary.id) },
                )
            }
        }
    }
}

@Composable
private fun ItineraryCard(
    itinerary: Itinerary,
    onClick: () -> Unit,
) {
    ContentCard(
        title = itinerary.title,
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = itinerary.durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${itinerary.stops.size} stops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = itinerary.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            if (itinerary.isFamilyFriendly) {
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Family Friendly") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryDetailScreen(
    itineraryId: String?,
    onNavigateBack: () -> Unit = {},
    onStartRoute: () -> Unit = {},
) {
    val itinerary = ItineraryCatalog.samples.firstOrNull { it.id == itineraryId }
    var isSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itinerary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSaved = !isSaved }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save",
                            tint = if (isSaved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (itinerary == null) {
            EmptyState(
                icon = Icons.Filled.Map,
                title = "Itinerary Not Found",
                message = "The requested itinerary could not be found.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ContentCard(title = itinerary.title) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = itinerary.durationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${itinerary.stops.size} stops",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = itinerary.overview,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Stops",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }

            itemsIndexed(itinerary.stops) { index, stop ->
                ItineraryStopCard(stop = stop, index = index + 1)
            }

            item {
                androidx.compose.material3.Button(
                    onClick = onStartRoute,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Start Route")
                }
            }
        }
    }
}

@Composable
private fun ItineraryStopCard(
    stop: ItineraryStop,
    index: Int,
) {
    ContentCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stop.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stop.duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stop.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// MARK: - Tips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenTip: (String) -> Unit = {},
) {
    var selectedCategory by remember { mutableStateOf<TipCategory?>(null) }
    val filteredTips = remember(selectedCategory) {
        if (selectedCategory != null) {
            TipCatalog.samples.filter { it.category == selectedCategory }
        } else {
            TipCatalog.samples
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tips & Safety") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") },
                )
                TipCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.title) },
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(filteredTips) { tip ->
                    TipCard(
                        tip = tip,
                        onClick = { onOpenTip(tip.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TipCard(
    tip: Tip,
    onClick: () -> Unit,
) {
    ContentCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = false,
                onClick = {},
                label = { Text(tip.category.title) },
            )

            Text(
                text = tip.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = tip.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipDetailScreen(
    tipId: String?,
    onNavigateBack: () -> Unit = {},
) {
    val tip = TipCatalog.samples.firstOrNull { it.id == tipId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tip") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (tip == null) {
            EmptyState(
                icon = Icons.Filled.Lightbulb,
                title = "Tip Not Found",
                message = "The requested tip could not be found.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            item {
                ContentCard(title = tip.title) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tip.category.title) },
                        )

                        Text(
                            text = tip.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Culture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultureScreen(
    onNavigateBack: () -> Unit = {},
    onOpenArticle: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Culture & Etiquette") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(CultureCatalog.samples) { article ->
                ContentCard(
                    title = article.title,
                    onClick = { onOpenArticle(article.id) },
                ) {
                    Text(
                        text = article.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultureDetailScreen(
    articleId: String?,
    onNavigateBack: () -> Unit = {},
) {
    val article = CultureCatalog.samples.firstOrNull { it.id == articleId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Culture") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (article == null) {
            EmptyState(
                icon = Icons.Filled.Book,
                title = "Article Not Found",
                message = "The requested article could not be found.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ContentCard(title = article.title) {
                    Text(
                        text = article.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (article.dos.isNotEmpty()) {
                item {
                    Text(
                        text = "Do's",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                items(article.dos) { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (article.donts.isNotEmpty()) {
                item {
                    Text(
                        text = "Don'ts",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                items(article.donts) { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Favorites

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateBack: () -> Unit = {},
    onOpenItem: (String, String) -> Unit = { _, _ -> },
) {
    val favorites = remember { mutableStateOf(FavoriteCatalog.samples.toMutableList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Items") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (favorites.value.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Favorite,
                title = "No Saved Items",
                message = "Tap the heart icon on any place or price card to save it here.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) {
                FavoriteType.entries.forEach { type ->
                    val items = favorites.value.filter { it.type == type }
                    if (items.isNotEmpty()) {
                        item {
                            Text(
                                text = type.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            )
                        }

                        items(items) { item ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onOpenItem(item.type.name, item.referenceId)
                                },
                                headlineContent = { Text(item.title) },
                                leadingContent = {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
) {
    var offlineMode by remember { mutableStateOf(false) }
    var preferredCurrency by remember { mutableStateOf("MAD") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    text = "Display",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Currency") },
                    supportingContent = { Text(preferredCurrency) },
                    modifier = Modifier.clickable { /* Show currency picker */ },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Offline Mode") },
                    trailingContent = {
                        Switch(
                            checked = offlineMode,
                            onCheckedChange = { offlineMode = it },
                        )
                    },
                )
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Clear Recent History") },
                    modifier = Modifier.clickable { /* Clear recents */ },
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Clear All Saved Items",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable { /* Clear favorites */ },
                )
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    trailingContent = {
                        Text(
                            text = "1.0.0",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Licenses") },
                    modifier = Modifier.clickable { /* Show licenses */ },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    modifier = Modifier.clickable { /* Show privacy */ },
                )
            }
        }
    }
}

// MARK: - Sample Data Models

data class Itinerary(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationLabel: String,
    val overview: String,
    val stops: List<ItineraryStop>,
    val isFamilyFriendly: Boolean,
)

data class ItineraryStop(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val duration: String,
    val description: String,
)

object ItineraryCatalog {
    val samples = listOf(
        Itinerary(
            title = "Classic Medina Walk",
            durationLabel = "4-5 hours",
            overview = "Experience the heart of Marrakech through its most iconic landmarks and hidden gems.",
            stops = listOf(
                ItineraryStop(title = "Jemaa el-Fna", duration = "30 min", description = "Start at the famous square, absorbing the morning energy."),
                ItineraryStop(title = "Koutoubia Mosque", duration = "20 min", description = "Admire the iconic minaret from the gardens."),
                ItineraryStop(title = "Ben Youssef Madrasa", duration = "45 min", description = "Explore the stunning Islamic architecture."),
                ItineraryStop(title = "Bahia Palace", duration = "1 hour", description = "Wander through the opulent palace gardens."),
            ),
            isFamilyFriendly = true,
        ),
        Itinerary(
            title = "Foodie's Delight",
            durationLabel = "3-4 hours",
            overview = "A culinary journey through the best street food and local eateries.",
            stops = listOf(
                ItineraryStop(title = "Morning at Café Clock", duration = "45 min", description = "Start with Moroccan breakfast and camel burger."),
                ItineraryStop(title = "Souk Spice Tour", duration = "30 min", description = "Discover the aromatic spice stalls."),
                ItineraryStop(title = "Street Food Row", duration = "1 hour", description = "Sample msemen, harira, and fresh orange juice."),
            ),
            isFamilyFriendly = true,
        ),
    )
}

enum class TipCategory(val title: String) {
    SCAMS("Scams"),
    SAFETY("Safety"),
    PRACTICAL("Practical"),
    ARRIVAL("Arrival"),
}

data class Tip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val preview: String,
    val content: String,
    val category: TipCategory,
    val relatedPlaceIds: List<String> = emptyList(),
)

object TipCatalog {
    val samples = listOf(
        Tip(
            title = "Taxi Price Agreement",
            preview = "Always agree on the price before getting in a taxi.",
            content = "Petit taxis in Marrakech often don't use meters. Before entering, ask \"Bsh-hal?\" (how much?) and agree on a price. If it seems too high, walk away - there are many taxis available.",
            category = TipCategory.PRACTICAL,
        ),
        Tip(
            title = "The Mint Tea Invitation Scam",
            preview = "Be cautious of overly friendly strangers offering tea in the souks.",
            content = "A common scam involves someone befriending you, taking you to their 'family shop' for tea, and then pressuring you to buy overpriced goods. It's okay to politely decline.",
            category = TipCategory.SCAMS,
        ),
        Tip(
            title = "Stay Hydrated",
            preview = "Carry water, especially in summer months.",
            content = "Marrakech can be extremely hot, especially May through September. Always carry bottled water. The tap water is generally safe but bottled is recommended for visitors.",
            category = TipCategory.SAFETY,
        ),
        Tip(
            title = "Airport to Medina",
            preview = "Know your options for getting from the airport.",
            content = "Official airport taxis have fixed prices displayed at the taxi stand (around 120-220 MAD to the Medina). Avoid touts inside the terminal - go directly to the official taxi line outside.",
            category = TipCategory.ARRIVAL,
        ),
    )
}

data class CultureArticle(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val preview: String,
    val content: String,
    val dos: List<String>,
    val donts: List<String>,
)

object CultureCatalog {
    val samples = listOf(
        CultureArticle(
            title = "Greetings and Respect",
            preview = "Understanding Moroccan greeting customs.",
            content = "Moroccans are warm and welcoming. Greetings are important - take time to say hello properly before getting to business.",
            dos = listOf(
                "Say 'Salam' (peace) when entering a shop",
                "Accept mint tea when offered",
                "Remove shoes when entering homes",
            ),
            donts = listOf(
                "Rush through greetings",
                "Point with your left hand",
                "Photograph people without permission",
            ),
        ),
        CultureArticle(
            title = "Dress Code",
            preview = "What to wear in Marrakech.",
            content = "While Marrakech is cosmopolitan, modest dress is appreciated, especially when visiting mosques and traditional neighborhoods.",
            dos = listOf(
                "Cover shoulders and knees in religious areas",
                "Carry a light scarf for covering if needed",
                "Wear comfortable walking shoes for the Medina",
            ),
            donts = listOf(
                "Wear very revealing clothing in the Medina",
                "Enter mosques as a non-Muslim (most are closed to visitors)",
            ),
        ),
    )
}

enum class FavoriteType(val title: String, val icon: ImageVector) {
    PLACE("Places", Icons.Filled.Map),
    PRICE_CARD("Price Cards", Icons.Filled.Lightbulb),
}

data class FavoriteItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: FavoriteType,
    val referenceId: String,
)

object FavoriteCatalog {
    val samples = listOf(
        FavoriteItem(title = "Jemaa el-Fna", type = FavoriteType.PLACE, referenceId = "jemaa-el-fna"),
        FavoriteItem(title = "Bahia Palace", type = FavoriteType.PLACE, referenceId = "bahia-palace"),
        FavoriteItem(title = "Airport Taxi", type = FavoriteType.PRICE_CARD, referenceId = "price-taxi-airport"),
    )
}
