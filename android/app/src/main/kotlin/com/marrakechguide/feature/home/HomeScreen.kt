package com.marrakechguide.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marrakechguide.core.model.Phrase
import com.marrakechguide.core.model.Tip
import com.marrakechguide.ui.components.ListItemSkeleton
import com.marrakechguide.ui.theme.CornerRadius
import com.marrakechguide.ui.theme.SemanticSuccess
import com.marrakechguide.ui.theme.SemanticWarning
import com.marrakechguide.ui.theme.Spacing
import com.marrakechguide.ui.theme.Terracotta500

/**
 * Home Screen - The Command Center
 * Provides quick access to high-value features and surfaces timely information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToQuote: () -> Unit = {},
    onNavigateToMyDay: () -> Unit = {},
    onNavigateToHomeBase: () -> Unit = {},
    onNavigateToTaxi: () -> Unit = {},
    onNavigateToPhrasebook: () -> Unit = {},
    onNavigateToCurrency: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToItem: (SavedItem) -> Unit = {},
    onNavigateToRecentItem: (RecentItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marrakech Guide") }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && !uiState.isRefreshing) {
                ListItemSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Offline Status Chip
                    item {
                        OfflineStatusChip(
                            status = uiState.offlineStatus,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Quick Actions Grid
                    item {
                        QuickActionsGrid(
                            onCheckPrice = onNavigateToQuote,
                            onMyDay = onNavigateToMyDay,
                            onGoHome = onNavigateToHomeBase,
                            onTaxiPrices = onNavigateToTaxi,
                            onPhrasebook = onNavigateToPhrasebook,
                            onCurrency = onNavigateToCurrency
                        )
                    }

                    // Active Route Banner (conditional)
                    uiState.activeRoute?.let { route ->
                        item {
                            ActiveRouteBanner(
                                route = route,
                                onContinue = { onNavigateToRoute(route.id) }
                            )
                        }
                    }

                    // Today's Tip
                    uiState.tipOfTheDay?.let { tip ->
                        item {
                            TipOfTheDayCard(tip = tip)
                        }
                    }

                    // Phrase of the Day
                    uiState.phraseOfTheDay?.let { phrase ->
                        item {
                            PhraseOfTheDayCard(phrase = phrase)
                        }
                    }

                    // Saved Items (if any)
                    if (uiState.savedItems.isNotEmpty()) {
                        item {
                            SavedItemsSection(
                                items = uiState.savedItems,
                                onSeeAll = onNavigateToSavedItems,
                                onItemClick = onNavigateToItem
                            )
                        }
                    }

                    // Recently Viewed (if any)
                    if (uiState.recentItems.isNotEmpty()) {
                        item {
                            RecentItemsSection(
                                items = uiState.recentItems,
                                onItemClick = onNavigateToRecentItem
                            )
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                }
            }
        }
    }
}

// MARK: - Offline Status Chip

@Composable
private fun OfflineStatusChip(
    status: OfflineStatus,
    modifier: Modifier = Modifier
) {
    val (icon, text, color) = when (status) {
        OfflineStatus.READY -> Triple(
            Icons.Default.CheckCircle,
            "Offline Ready",
            SemanticSuccess
        )
        OfflineStatus.DOWNLOAD_RECOMMENDED -> Triple(
            Icons.Default.Download,
            "Download Recommended",
            SemanticWarning
        )
        OfflineStatus.UPDATE_AVAILABLE -> Triple(
            Icons.Default.Info,
            "Update Available",
            MaterialTheme.colorScheme.primary
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .semantics { contentDescription = "Status: $text" },
            shape = RoundedCornerShape(16.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = color
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

// MARK: - Quick Actions Grid

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionsGrid(
    onCheckPrice: () -> Unit,
    onMyDay: () -> Unit,
    onGoHome: () -> Unit,
    onTaxiPrices: () -> Unit,
    onPhrasebook: () -> Unit,
    onCurrency: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        QuickAction("Check a Price", Icons.Default.AttachMoney, Terracotta500, onCheckPrice),
        QuickAction("My Day", Icons.Default.CalendarMonth, Color(0xFF3B82F6), onMyDay),
        QuickAction("Go Home", Icons.Default.Home, SemanticSuccess, onGoHome),
        QuickAction("Taxi Prices", Icons.Default.DirectionsCar, SemanticWarning, onTaxiPrices),
        QuickAction("Phrasebook", Icons.Default.TextFormat, Color(0xFF9333EA), onPhrasebook),
        QuickAction("Currency", Icons.Default.SwapHoriz, Color(0xFF14B8A6), onCurrency)
    )

    Column(modifier = modifier) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = Spacing.sm)
                .semantics { heading() }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.height(((56 + 8) * 3).dp),
            userScrollEnabled = false
        ) {
            items(actions) { action ->
                QuickActionButton(
                    title = action.title,
                    icon = action.icon,
                    color = action.color,
                    onClick = action.onClick
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.lg))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = title
            },
        color = color,
        shape = RoundedCornerShape(CornerRadius.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// MARK: - Active Route Banner

@Composable
private fun ActiveRouteBanner(
    route: ActiveRoute,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TurnRight,
                        contentDescription = null,
                        tint = Terracotta500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Active Route",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${route.currentStep}/${route.totalSteps} stops",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.currentStopName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = Spacing.xs)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = route.nextStopName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta500),
                shape = RoundedCornerShape(CornerRadius.md)
            ) {
                Text("Continue")
            }
        }
    }
}

// MARK: - Tip of the Day Card

@Composable
private fun TipOfTheDayCard(
    tip: Tip,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Today's Tip",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = tip.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            tip.summary?.let { summary ->
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// MARK: - Phrase of the Day Card

@Composable
private fun PhraseOfTheDayCard(
    phrase: Phrase,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TextFormat,
                    contentDescription = null,
                    tint = Color(0xFF9333EA),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Phrase of the Day",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = phrase.latin,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            phrase.arabic?.let { arabic ->
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = arabic,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = phrase.english,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Saved Items Section

@Composable
private fun SavedItemsSection(
    items: List<SavedItem>,
    onSeeAll: () -> Unit,
    onItemClick: (SavedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Items",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onSeeAll) {
                Text("See All")
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(items) { item ->
                SavedItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun SavedItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = item.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// MARK: - Recent Items Section

@Composable
private fun RecentItemsSection(
    items: List<RecentItem>,
    onItemClick: (RecentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Recently Viewed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items.take(5).forEach { item ->
                RecentItemRow(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: RecentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
