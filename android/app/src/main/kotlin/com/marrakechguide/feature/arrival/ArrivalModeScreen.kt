package com.marrakechguide.feature.arrival

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import com.marrakechguide.ui.theme.Spacing

/**
 * First-hours guidance screen for users who just arrived in Marrakech.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalModeScreen(
    onNavigateBack: (() -> Unit)? = null,
    onOpenTaxiPriceCard: () -> Unit = {},
    onSetHomeBase: () -> Unit = {},
    onOpenMyDay: () -> Unit = {},
) {
    var completedSectionIds by remember { mutableStateOf(setOf<String>()) }
    var arrivalConfirmed by remember { mutableStateOf(false) }

    val sections = remember { ArrivalSection.defaultSections }
    val progress = if (sections.isEmpty()) 0f else completedSectionIds.size.toFloat() / sections.size.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arrival Mode") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = "Welcome to Marrakech",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "A calm checklist for your first hours after landing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Progress",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = "${completedSectionIds.size}/${sections.size} complete",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            items(sections, key = { it.id }) { section ->
                val isComplete = completedSectionIds.contains(section.id)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = section.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        section.checklist.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = if (isComplete) Icons.Filled.CheckCircle else section.icon,
                                    contentDescription = null,
                                    tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Button(
                                onClick = {
                                    completedSectionIds = if (isComplete) {
                                        completedSectionIds - section.id
                                    } else {
                                        completedSectionIds + section.id
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (isComplete) "Completed" else "Mark Section Done")
                            }

                            section.action?.let { action ->
                                OutlinedButton(
                                    onClick = {
                                        when (action) {
                                            ArrivalAction.OPEN_TAXI_CARD -> onOpenTaxiPriceCard()
                                            ArrivalAction.SET_HOME_BASE -> onSetHomeBase()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { arrivalConfirmed = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("I've Arrived at My Riad")
                }
            }

            if (arrivalConfirmed) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = "You're Set",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Arrival basics are done. Build a calm first day plan next.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onOpenMyDay,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Plan My Day")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class ArrivalAction(val label: String) {
    OPEN_TAXI_CARD("Taxi Price Card"),
    SET_HOME_BASE("Set Home Base"),
}

private data class ArrivalSection(
    val id: String,
    val title: String,
    val summary: String,
    val checklist: List<String>,
    val icon: ImageVector,
    val action: ArrivalAction? = null,
) {
    companion object {
        val defaultSections = listOf(
            ArrivalSection(
                id = "airport_taxi",
                title = "Airport Taxi",
                summary = "Use the official queue and confirm total fare before departure.",
                checklist = listOf(
                    "Go to the official taxi stand outside arrivals.",
                    "Confirm destination and all-in fare before loading bags.",
                    "Decline unclear offers and move to the next licensed taxi.",
                ),
                icon = Icons.Filled.DirectionsCar,
                action = ArrivalAction.OPEN_TAXI_CARD,
            ),
            ArrivalSection(
                id = "sim_data",
                title = "SIM / eSIM",
                summary = "Stabilize maps and messaging by setting up data early.",
                checklist = listOf(
                    "Prefer eSIM setup before departure when possible.",
                    "If buying on arrival, compare data amount and validity.",
                    "Test data before leaving the airport area.",
                ),
                icon = Icons.Filled.SimCard,
            ),
            ArrivalSection(
                id = "cash_atm",
                title = "Cash / ATM",
                summary = "Keep transport and first purchases friction-free.",
                checklist = listOf(
                    "Withdraw a starter MAD cash buffer.",
                    "Break large bills early into smaller denominations.",
                    "Keep emergency transport cash separate from daily spending.",
                ),
                icon = Icons.Filled.AccountBalance,
            ),
            ArrivalSection(
                id = "hotel_transfer",
                title = "Hotel / Riad Transfer",
                summary = "Verify the final pin and save Home Base immediately.",
                checklist = listOf(
                    "Confirm exact accommodation pin before leaving airport.",
                    "Save Home Base before first medina exploration.",
                    "Keep accommodation contact ready for driver clarification.",
                ),
                icon = Icons.Filled.Home,
                action = ArrivalAction.SET_HOME_BASE,
            ),
            ArrivalSection(
                id = "medina_orientation",
                title = "Medina Orientation",
                summary = "Use landmark navigation for lower-stress first walks.",
                checklist = listOf(
                    "Anchor on Jemaa El Fna and Koutoubia as recovery points.",
                    "Use a polite \"La shukran\" for unsolicited guidance.",
                    "Pause in open squares if direction feels uncertain.",
                ),
                icon = Icons.Filled.Explore,
            ),
        )
    }
}
