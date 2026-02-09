package com.marrakechguide.feature.routecards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.marrakechguide.core.engine.GeoEngine
import com.marrakechguide.core.engine.RouteEngine
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Route Cards — Step-by-step itinerary execution with compass navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCardsScreen(
    places: List<RouteEngine.RoutePlace>,
    routeId: String,
    routeTitle: String,
    onExit: () -> Unit = {},
    viewModel: RouteCardsViewModel = remember { RouteCardsViewModel(places, routeId, routeTitle) }
) {
    val uiState by viewModel.uiState.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routeTitle) },
                navigationIcon = {
                    TextButton(onClick = { showExitDialog = true }) {
                        Text("Exit")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (uiState.viewMode == ViewMode.OVERVIEW) {
                                Icons.Default.Navigation
                            } else {
                                Icons.AutoMirrored.Filled.List
                            },
                            contentDescription = "Toggle view"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.viewMode) {
                ViewMode.OVERVIEW -> OverviewContent(viewModel = viewModel)
                ViewMode.NAVIGATION -> NavigationContent(viewModel = viewModel)
                ViewMode.MEDINA_MODE -> MedinaModeContent(viewModel = viewModel)
            }
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Route?") },
            text = { Text("Your progress will be saved but you'll need to restart to continue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exitRoute()
                        showExitDialog = false
                        onExit()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue Route")
                }
            }
        )
    }

    // Completion celebration
    if (uiState.showCompletion) {
        CompletionSheet(
            stopsCompleted = uiState.progress?.stepsCompleted?.size ?: 0,
            totalStops = places.size,
            onDismiss = {
                viewModel.dismissCompletion()
                onExit()
            }
        )
    }
}

// MARK: - ViewModel

enum class ViewMode { OVERVIEW, NAVIGATION, MEDINA_MODE }

class RouteCardsViewModel(
    val places: List<RouteEngine.RoutePlace>,
    routeId: String,
    routeTitle: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouteCardsUiState())
    val uiState: StateFlow<RouteCardsUiState> = _uiState.asStateFlow()

    init {
        val progress = RouteEngine.startRoute(routeId, RouteEngine.RouteType.ITINERARY, places)
        if (progress != null) {
            _uiState.value = _uiState.value.copy(progress = progress)
            updateCurrentLeg()
        }
    }

    fun toggleViewMode() {
        val newMode = when (_uiState.value.viewMode) {
            ViewMode.OVERVIEW -> ViewMode.NAVIGATION
            ViewMode.NAVIGATION -> ViewMode.OVERVIEW
            ViewMode.MEDINA_MODE -> ViewMode.NAVIGATION
        }
        _uiState.value = _uiState.value.copy(viewMode = newMode)
    }

    fun switchToNavigation() {
        _uiState.value = _uiState.value.copy(viewMode = ViewMode.NAVIGATION)
    }

    fun switchToMedinaMode() {
        _uiState.value = _uiState.value.copy(viewMode = ViewMode.MEDINA_MODE)
    }

    fun completeCurrentStep() {
        val progress = _uiState.value.progress ?: return
        RouteEngine.completeCurrentStep(progress)
        _uiState.value = _uiState.value.copy(progress = progress)
        updateCurrentLeg()
        checkCompletion()
    }

    fun skipCurrentStep() {
        val progress = _uiState.value.progress ?: return
        RouteEngine.skipCurrentStep(progress)
        _uiState.value = _uiState.value.copy(progress = progress)
        updateCurrentLeg()
        checkCompletion()
    }

    fun exitRoute() {
        val progress = _uiState.value.progress ?: return
        RouteEngine.exitRoute(progress)
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    fun dismissCompletion() {
        _uiState.value = _uiState.value.copy(showCompletion = false)
    }

    fun getStepStatus(index: Int): StepStatus {
        val progress = _uiState.value.progress ?: return StepStatus.UPCOMING
        return when {
            progress.stepsCompleted.contains(index) -> StepStatus.COMPLETED
            progress.stepsSkipped.contains(index) -> StepStatus.SKIPPED
            index == progress.currentStepIndex -> StepStatus.CURRENT
            else -> StepStatus.UPCOMING
        }
    }

    private fun updateCurrentLeg() {
        val progress = _uiState.value.progress ?: return
        val leg = RouteEngine.getCurrentLeg(progress, places)
        _uiState.value = _uiState.value.copy(currentLeg = leg)
    }

    private fun checkCompletion() {
        val progress = _uiState.value.progress ?: return
        if (RouteEngine.isRouteComplete(progress)) {
            _uiState.value = _uiState.value.copy(showCompletion = true)
        }
    }
}

data class RouteCardsUiState(
    val progress: RouteEngine.RouteProgress? = null,
    val currentLeg: RouteEngine.RouteLeg? = null,
    val viewMode: ViewMode = ViewMode.OVERVIEW,
    val deviceHeading: Double = 0.0,
    val showCompletion: Boolean = false
) {
    val completionPercentage: Float
        get() = progress?.let { RouteEngine.getOverallProgress(it).toFloat() } ?: 0f
}

enum class StepStatus { COMPLETED, SKIPPED, CURRENT, UPCOMING }

// MARK: - Overview Content

@Composable
private fun OverviewContent(viewModel: RouteCardsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Progress header
        item {
            ProgressHeader(
                completed = uiState.progress?.stepsCompleted?.size ?: 0,
                total = viewModel.places.size,
                percentage = uiState.completionPercentage
            )
        }

        // Steps list
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    viewModel.places.forEachIndexed { index, place ->
                        StepRow(
                            index = index + 1,
                            place = place,
                            status = viewModel.getStepStatus(index),
                            isLast = index == viewModel.places.lastIndex
                        )
                    }
                }
            }
        }

        // Continue button
        if (uiState.progress?.let { !RouteEngine.isRouteComplete(it) } == true) {
            item {
                Button(
                    onClick = { viewModel.switchToNavigation() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Continue Route")
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    completed: Int,
    total: Int,
    percentage: Float
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "$completed of $total stops",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(percentage * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    place: RouteEngine.RoutePlace,
    status: StepStatus,
    isLast: Boolean
) {
    val successColor = Color(0xFF4CAF50)
    val statusColor = when (status) {
        StepStatus.COMPLETED -> successColor
        StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        StepStatus.CURRENT -> MaterialTheme.colorScheme.primary
        StepStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Timeline
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = statusColor,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (status) {
                        StepStatus.COMPLETED -> Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        StepStatus.SKIPPED -> Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        StepStatus.CURRENT -> Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        StepStatus.UPCOMING -> Text(
                            "$index",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(statusColor.copy(alpha = 0.3f))
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else Spacing.sm)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (status == StepStatus.UPCOMING) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            place.routeHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (status == StepStatus.CURRENT) {
                Text(
                    text = "Current stop",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// MARK: - Navigation Content

@Composable
private fun NavigationContent(viewModel: RouteCardsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        val leg = uiState.currentLeg

        if (leg != null) {
            // Current stop
            leg.fromPlace?.let { fromPlace ->
                CurrentStopCard(place = fromPlace)
            }

            // Destination
            DestinationCard(leg = leg)

            // Navigation panel
            NavigationPanel(
                leg = leg,
                deviceHeading = uiState.deviceHeading
            )

            Spacer(Modifier.weight(1f))

            // Actions
            ActionButtons(
                onComplete = { viewModel.completeCurrentStep() },
                onSkip = { viewModel.skipCurrentStep() },
                onMedinaMode = { viewModel.switchToMedinaMode() }
            )
        } else {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "Route Complete",
                message = "You've finished all stops!",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CurrentStopCard(place: RouteEngine.RoutePlace) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Current Location",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun DestinationCard(leg: RouteEngine.RouteLeg) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Next Stop",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = leg.toPlace.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            leg.routeHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (leg.isLastStep) {
                Text(
                    text = "Final Stop",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NavigationPanel(
    leg: RouteEngine.RouteLeg,
    deviceHeading: Double
) {
    val relativeAngle = GeoEngine.relativeAngle(leg.bearingDegrees, deviceHeading)
    val animatedAngle by animateFloatAsState(relativeAngle.toFloat(), label = "compass")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Compass arrow
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "Direction",
                modifier = Modifier
                    .size(80.dp)
                    .rotate(animatedAngle),
                tint = MaterialTheme.colorScheme.primary
            )

            // Distance and time
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RouteEngine.formatDistance(leg.distanceMeters),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Distance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RouteEngine.formatWalkTime(leg.estimatedWalkMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Walk time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onMedinaMode: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("I've Arrived")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Skip")
            }
            OutlinedButton(
                onClick = onMedinaMode,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Need Help")
            }
        }
    }
}

// MARK: - Medina Mode Content

@Composable
private fun MedinaModeContent(viewModel: RouteCardsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val leg = uiState.currentLeg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        if (leg != null) {
            Text(
                text = "Medina Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Getting to ${leg.toPlace.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Large text direction phrase
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Ask: \"Fin ${leg.toPlace.name}?\"",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "(Where is ${leg.toPlace.name}?)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Distance info
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RouteEngine.formatDistance(leg.distanceMeters),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Away",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RouteEngine.formatWalkTime(leg.estimatedWalkMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Walk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            leg.routeHint?.let { hint ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.completeCurrentStep() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("I've Arrived")
            }

            OutlinedButton(
                onClick = { viewModel.switchToNavigation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Compass")
            }
        }
    }
}

// MARK: - Completion Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletionSheet(
    stopsCompleted: Int,
    totalStops: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val successColor = Color(0xFF4CAF50)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = successColor
            )

            Text(
                text = "Route Complete!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "You completed $stopsCompleted of $totalStops stops",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.lg))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
