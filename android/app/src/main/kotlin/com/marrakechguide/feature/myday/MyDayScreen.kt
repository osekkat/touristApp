package com.marrakechguide.feature.myday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.marrakechguide.core.engine.PlanEngine
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

/**
 * My Day Planner — Generate a personalized day plan based on user constraints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayScreen(
    viewModel: MyDayViewModel = remember { MyDayViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResult by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Day") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Header
            MyDayHeader()

            // Constraint pickers
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Time Available
                    TimeSlider(
                        hours = uiState.hoursAvailable,
                        onHoursChange = { viewModel.setHours(it) }
                    )

                    HorizontalDivider()

                    // Pace
                    PacePicker(
                        selectedPace = uiState.selectedPace,
                        onPaceSelected = { viewModel.setPace(it) }
                    )

                    HorizontalDivider()

                    // Budget
                    BudgetPicker(
                        selectedBudget = uiState.selectedBudget,
                        onBudgetSelected = { viewModel.setBudget(it) }
                    )

                    HorizontalDivider()

                    // Interests
                    InterestsPicker(
                        selectedInterests = uiState.selectedInterests,
                        onInterestToggle = { viewModel.toggleInterest(it) }
                    )
                }
            }

            // Generate button
            Button(
                onClick = {
                    viewModel.generatePlan()
                    showResult = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedInterests.isNotEmpty()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Build My Day")
            }

            if (uiState.selectedInterests.isEmpty()) {
                Text(
                    text = "Select at least one interest to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Result sheet
    if (showResult && uiState.generatedPlan != null) {
        MyDayResultSheet(
            plan = uiState.generatedPlan!!,
            places = viewModel.placesById,
            onDismiss = { showResult = false }
        )
    }
}

// MARK: - ViewModel

class MyDayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MyDayUiState())
    val uiState: StateFlow<MyDayUiState> = _uiState.asStateFlow()

    private val samplePlaces = MyDayCatalog.samplePlaces
    val placesById: Map<String, com.marrakechguide.core.model.Place> = samplePlaces.associateBy { it.id }

    fun setHours(hours: Float) {
        _uiState.value = _uiState.value.copy(hoursAvailable = hours)
    }

    fun setPace(pace: PlanEngine.Pace) {
        _uiState.value = _uiState.value.copy(selectedPace = pace)
    }

    fun setBudget(budget: PlanEngine.BudgetTier) {
        _uiState.value = _uiState.value.copy(selectedBudget = budget)
    }

    fun toggleInterest(interest: PlanEngine.Interest) {
        val current = _uiState.value.selectedInterests
        val updated = if (current.contains(interest)) {
            current - interest
        } else {
            current + interest
        }
        _uiState.value = _uiState.value.copy(selectedInterests = updated)
    }

    fun generatePlan() {
        val state = _uiState.value
        val input = PlanEngine.Input(
            availableMinutes = (state.hoursAvailable * 60).toInt(),
            startPoint = null,
            interests = state.selectedInterests.toList(),
            pace = state.selectedPace,
            budgetTier = state.selectedBudget,
            currentTime = Instant.now(),
            places = samplePlaces,
            recentPlaceIds = emptySet()
        )
        val plan = PlanEngine.generate(input)
        _uiState.value = _uiState.value.copy(generatedPlan = plan)
    }
}

data class MyDayUiState(
    val hoursAvailable: Float = 6f,
    val selectedPace: PlanEngine.Pace = PlanEngine.Pace.STANDARD,
    val selectedBudget: PlanEngine.BudgetTier = PlanEngine.BudgetTier.MID,
    val selectedInterests: Set<PlanEngine.Interest> = emptySet(),
    val generatedPlan: PlanEngine.Output? = null
)

// MARK: - Components

@Composable
private fun MyDayHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = Icons.Default.WbSunny,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFFFFD700)
        )

        Text(
            text = "What should I do today?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Tell us your preferences and we'll build a personalized plan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimeSlider(
    hours: Float,
    onHoursChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Time Available",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${hours.toInt()} hours",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = hours,
            onValueChange = onHoursChange,
            valueRange = 2f..10f,
            steps = 7
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PresetChip(
                label = "Half Day",
                isSelected = hours.toInt() == 4,
                onClick = { onHoursChange(4f) }
            )
            PresetChip(
                label = "Full Day",
                isSelected = hours.toInt() == 8,
                onClick = { onHoursChange(8f) }
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun PacePicker(
    selectedPace: PlanEngine.Pace,
    onPaceSelected: (PlanEngine.Pace) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Pace",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PlanEngine.Pace.entries.forEach { pace ->
                PaceOption(
                    pace = pace,
                    isSelected = selectedPace == pace,
                    onClick = { onPaceSelected(pace) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PaceOption(
    pace: PlanEngine.Pace,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (pace) {
        PlanEngine.Pace.RELAXED -> Icons.Default.Spa
        PlanEngine.Pace.STANDARD -> Icons.AutoMirrored.Filled.DirectionsWalk
        PlanEngine.Pace.ACTIVE -> Icons.AutoMirrored.Filled.DirectionsRun
    }

    val label = when (pace) {
        PlanEngine.Pace.RELAXED -> "Relaxed"
        PlanEngine.Pace.STANDARD -> "Standard"
        PlanEngine.Pace.ACTIVE -> "Active"
    }

    val description = when (pace) {
        PlanEngine.Pace.RELAXED -> "Fewer stops"
        PlanEngine.Pace.STANDARD -> "Balanced"
        PlanEngine.Pace.ACTIVE -> "More stops"
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder()
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetPicker(
    selectedBudget: PlanEngine.BudgetTier,
    onBudgetSelected: (PlanEngine.BudgetTier) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Budget",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PlanEngine.BudgetTier.entries.forEach { tier ->
                BudgetOption(
                    tier = tier,
                    isSelected = selectedBudget == tier,
                    onClick = { onBudgetSelected(tier) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BudgetOption(
    tier: PlanEngine.BudgetTier,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val symbol = when (tier) {
        PlanEngine.BudgetTier.BUDGET -> "$"
        PlanEngine.BudgetTier.MID -> "$$"
        PlanEngine.BudgetTier.SPLURGE -> "$$$"
    }

    val label = when (tier) {
        PlanEngine.BudgetTier.BUDGET -> "Budget"
        PlanEngine.BudgetTier.MID -> "Mid-range"
        PlanEngine.BudgetTier.SPLURGE -> "Splurge"
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(symbol, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InterestsPicker(
    selectedInterests: Set<PlanEngine.Interest>,
    onInterestToggle: (PlanEngine.Interest) -> Unit
) {
    val displayInterests = listOf(
        Triple(PlanEngine.Interest.HISTORY, "History & Culture", Icons.Default.AccountBalance),
        Triple(PlanEngine.Interest.SHOPPING, "Shopping & Souks", Icons.Default.ShoppingBag),
        Triple(PlanEngine.Interest.FOOD, "Food & Cafes", Icons.Default.Restaurant),
        Triple(PlanEngine.Interest.NATURE, "Gardens", Icons.Default.Park),
        Triple(PlanEngine.Interest.ARCHITECTURE, "Photography", Icons.Default.CameraAlt),
        Triple(PlanEngine.Interest.RELAXATION, "Wellness", Icons.Default.Spa)
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Interests",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.height(150.dp)
        ) {
            items(displayInterests) { (interest, label, icon) ->
                InterestChip(
                    label = label,
                    icon = icon,
                    isSelected = selectedInterests.contains(interest),
                    onClick = { onInterestToggle(interest) }
                )
            }
        }
    }
}

@Composable
private fun InterestChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

// MARK: - Result Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyDayResultSheet(
    plan: PlanEngine.Output,
    places: Map<String, com.marrakechguide.core.model.Place>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    text = "Your Day Plan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Summary
            item {
                PlanSummaryCard(plan = plan)
            }

            // Warnings
            if (plan.warnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            plan.warnings.forEach { warning ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        warning,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stops
            if (plan.stops.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        title = "No Stops Found",
                        message = "Try adjusting your constraints or interests."
                    )
                }
            } else {
                itemsIndexed(plan.stops) { index, stop ->
                    PlanStopCard(
                        stop = stop,
                        place = places[stop.placeId],
                        index = index + 1,
                        isLast = index == plan.stops.lastIndex
                    )
                }
            }

            // Start button
            if (plan.stops.isNotEmpty()) {
                item {
                    Button(
                        onClick = { /* Navigate to Route Cards */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Start My Day")
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

@Composable
private fun PlanSummaryCard(plan: PlanEngine.Output) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = Icons.Default.LocationOn,
                value = "${plan.stops.size}",
                label = "Stops"
            )
            SummaryItem(
                icon = Icons.Default.AccessTime,
                value = formatDuration(plan.totalMinutes),
                label = "Duration"
            )
            SummaryItem(
                icon = Icons.Default.Payments,
                value = "${plan.estimatedCostRange.minMad}-${plan.estimatedCostRange.maxMad}",
                label = "MAD"
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanStopCard(
    stop: PlanEngine.PlanStop,
    place: com.marrakechguide.core.model.Place?,
    index: Int,
    isLast: Boolean
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Timeline
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = place?.name ?: stop.placeId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        timeFormat.format(stop.arrivalTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${stop.visitMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (stop.travelMinutesFromPrevious > 0) {
                Text(
                    "${stop.travelMinutesFromPrevious} min walk from previous",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

// MARK: - Sample Data

object MyDayCatalog {
    val samplePlaces = listOf(
        com.marrakechguide.core.model.Place(
            id = "jemaa-el-fna",
            name = "Jemaa el-Fna",
            category = "landmark",
            lat = 31.6259,
            lng = -7.9891,
            visitMinMinutes = 60,
            visitMaxMinutes = 90,
            expectedCostMinMad = 0,
            expectedCostMaxMad = 0,
            tags = listOf("history", "culture", "food")
        ),
        com.marrakechguide.core.model.Place(
            id = "bahia-palace",
            name = "Bahia Palace",
            category = "landmark",
            lat = 31.6216,
            lng = -7.9831,
            visitMinMinutes = 60,
            visitMaxMinutes = 90,
            expectedCostMinMad = 70,
            expectedCostMaxMad = 70,
            tags = listOf("history", "architecture")
        ),
        com.marrakechguide.core.model.Place(
            id = "jardin-majorelle",
            name = "Jardin Majorelle",
            category = "garden",
            lat = 31.6417,
            lng = -8.0033,
            visitMinMinutes = 60,
            visitMaxMinutes = 90,
            expectedCostMinMad = 150,
            expectedCostMaxMad = 150,
            tags = listOf("nature", "architecture")
        ),
        com.marrakechguide.core.model.Place(
            id = "souk-spices",
            name = "Spice Souk",
            category = "shopping",
            lat = 31.6285,
            lng = -7.9858,
            visitMinMinutes = 30,
            visitMaxMinutes = 60,
            expectedCostMinMad = 0,
            expectedCostMaxMad = 200,
            tags = listOf("shopping", "culture")
        ),
        com.marrakechguide.core.model.Place(
            id = "cafe-clock",
            name = "Café Clock",
            category = "restaurant",
            lat = 31.6294,
            lng = -7.9873,
            visitMinMinutes = 45,
            visitMaxMinutes = 75,
            expectedCostMinMad = 80,
            expectedCostMaxMad = 150,
            tags = listOf("food")
        )
    )
}
