package com.marrakechguide.feature.arrivalmode

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.marrakechguide.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Arrival Mode — Opinionated guidance for the first hours in Marrakech.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalModeScreen(
    onDismiss: () -> Unit = {},
    viewModel: ArrivalModeViewModel = remember { ArrivalModeViewModel() }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var expandedSection by remember { mutableStateOf<ArrivalSection?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadState(context)
        if (uiState.isArrivalComplete) {
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arrival Mode") },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Skip")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Welcome header
            item {
                WelcomeHeader(progress = uiState.completionProgress)
            }

            // Sections
            items(ArrivalSection.entries) { section ->
                ArrivalSectionCard(
                    section = section,
                    isComplete = uiState.completedSections.contains(section),
                    isExpanded = expandedSection == section,
                    onToggle = {
                        expandedSection = if (expandedSection == section) null else section
                    },
                    onMarkComplete = {
                        viewModel.toggleComplete(section, context)
                    }
                )
            }

            // Completion button
            if (uiState.allSectionsComplete) {
                item {
                    CompletionCard(
                        onComplete = {
                            viewModel.markArrivalComplete(context)
                            onDismiss()
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

// MARK: - ViewModel

class ArrivalModeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ArrivalModeUiState())
    val uiState: StateFlow<ArrivalModeUiState> = _uiState.asStateFlow()

    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences("arrival_mode", Context.MODE_PRIVATE)
        val isComplete = prefs.getBoolean("arrivalComplete", false)
        val savedSections = prefs.getStringSet("completedSections", emptySet()) ?: emptySet()
        val completed = savedSections.mapNotNull { name ->
            ArrivalSection.entries.find { it.name == name }
        }.toSet()

        _uiState.value = _uiState.value.copy(
            completedSections = completed,
            isArrivalComplete = isComplete
        )
    }

    fun toggleComplete(section: ArrivalSection, context: Context) {
        val current = _uiState.value.completedSections
        val updated = if (current.contains(section)) {
            current - section
        } else {
            current + section
        }
        _uiState.value = _uiState.value.copy(completedSections = updated)
        saveState(context)
    }

    fun markArrivalComplete(context: Context) {
        _uiState.value = _uiState.value.copy(isArrivalComplete = true)
        val prefs = context.getSharedPreferences("arrival_mode", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("arrivalComplete", true)
            .putLong("arrivalCompletedAt", System.currentTimeMillis())
            .apply()
    }

    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("arrival_mode", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("completedSections", _uiState.value.completedSections.map { it.name }.toSet())
            .apply()
    }
}

data class ArrivalModeUiState(
    val completedSections: Set<ArrivalSection> = emptySet(),
    val isArrivalComplete: Boolean = false
) {
    val completionProgress: Float
        get() = completedSections.size.toFloat() / ArrivalSection.entries.size

    val allSectionsComplete: Boolean
        get() = completedSections.size == ArrivalSection.entries.size
}

// MARK: - Section Model

enum class ArrivalSection(
    val title: String,
    val icon: ImageVector,
    val tips: List<ArrivalTip>
) {
    AIRPORT_TAXI(
        title = "Airport Taxi",
        icon = Icons.Default.LocalTaxi,
        tips = listOf(
            ArrivalTip("Expected fare to Medina: 150-200 MAD (fixed, don't negotiate)", isHighlight = true),
            ArrivalTip("Find the official taxi stand outside arrivals"),
            ArrivalTip("Avoid 'special price' offers and guides at arrival"),
            ArrivalTip("Polite refusal: \"La, shukran\" (No, thank you)")
        )
    ),
    SIM_ESIM(
        title = "SIM / eSIM",
        icon = Icons.Default.SimCard,
        tips = listOf(
            ArrivalTip("Best: Get eSIM before arrival (Airalo, Holafly)", isHighlight = true),
            ArrivalTip("At airport: Maroc Telecom, Orange, Inwi booths"),
            ArrivalTip("Ask for: 20-50GB data, 7-14 days"),
            ArrivalTip("Typical cost: 100-200 MAD"),
            ArrivalTip("Phrase: \"Bghit internet, bla appels\" (I want internet, no calls)")
        )
    ),
    CASH_ATM(
        title = "Cash / ATM",
        icon = Icons.Default.AttachMoney,
        tips = listOf(
            ArrivalTip("Withdraw 1000-2000 MAD to start", isHighlight = true),
            ArrivalTip("ATMs at airport work but have limits"),
            ArrivalTip("If ATM fails: try CIH or Attijariwafa banks"),
            ArrivalTip("Keep small bills (20, 50 MAD) for tips"),
            ArrivalTip("Rough rate: 1 USD ≈ 10 MAD")
        )
    ),
    HOTEL_TRANSFER(
        title = "Hotel Transfer",
        icon = Icons.Default.Home,
        tips = listOf(
            ArrivalTip("Confirm pickup if your riad arranged it", isHighlight = true),
            ArrivalTip("If taxi: show driver the address in Arabic"),
            ArrivalTip("Tip for bag help: 20-50 MAD"),
            ArrivalTip("Ask riad to meet you at a landmark if inside Medina")
        )
    ),
    MEDINA_ORIENTATION(
        title = "Medina Orientation",
        icon = Icons.Default.Map,
        tips = listOf(
            ArrivalTip("\"Helpful\" strangers usually want money — polite \"La shukran\"", isHighlight = true),
            ArrivalTip("You WILL get lost. It's OK! Ask shopkeepers for landmarks."),
            ArrivalTip("Jemaa el-Fna is the center — ask \"Fin Jemaa el-Fna?\""),
            ArrivalTip("Keep your riad's card with Arabic address")
        )
    )
}

data class ArrivalTip(
    val text: String,
    val isHighlight: Boolean = false
)

// MARK: - Components

@Composable
private fun WelcomeHeader(progress: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Default.FlightLand,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Welcome to Marrakech!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Complete these steps to start your trip with confidence",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArrivalSectionCard(
    section: ArrivalSection,
    isComplete: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onMarkComplete: () -> Unit
) {
    val successColor = Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = if (isComplete) successColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )

                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = successColor
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    section.tips.forEach { tip ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (tip.isHighlight) Icons.Default.Star else Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(if (tip.isHighlight) 16.dp else 8.dp),
                                tint = if (tip.isHighlight) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = tip.text,
                                style = if (tip.isHighlight) {
                                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                        }
                    }

                    // Mark complete button
                    Button(
                        onClick = onMarkComplete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isComplete) successColor else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(if (isComplete) "Completed" else "Mark as Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(onComplete: () -> Unit) {
    val successColor = Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = successColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = successColor
            )

            Text(
                text = "You're Ready!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "All arrival steps complete. Enjoy Marrakech!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = successColor)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("I've Arrived at My Riad")
            }
        }
    }
}
