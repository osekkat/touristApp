package com.marrakechguide.feature.homebase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marrakechguide.core.repository.HomeBase
import com.marrakechguide.core.service.HeadingConfidence
import com.marrakechguide.core.service.PermissionStatus
import com.marrakechguide.ui.theme.MarrakechGuideTheme
import com.marrakechguide.ui.theme.MarrakechOrange

/**
 * Main compass screen for navigating back to home base.
 *
 * Shows:
 * - Compass arrow pointing to home
 * - Distance and estimated walk time
 * - Home base name and location
 * - Heading confidence indicator
 * - Actions: Refresh, Show to Taxi Driver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoHomeScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToTaxiCard: (HomeBase) -> Unit,
    onRequestLocationPermission: () -> Unit,
    viewModel: HomeBaseViewModel = hiltViewModel()
) {
    val homeBase by viewModel.homeBase.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val arrowRotation by viewModel.arrowRotation.collectAsState()
    val formattedDistance by viewModel.formattedDistance.collectAsState()
    val estimatedWalkTime by viewModel.estimatedWalkTime.collectAsState()
    val directionDescription by viewModel.directionDescription.collectAsState()
    val headingConfidence by viewModel.headingConfidence.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    // Start/stop tracking based on lifecycle
    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose {
            viewModel.stopTracking()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Go Home") },
                actions = {
                    if (homeBase != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Change Home Base") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToSetup()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                homeBase == null -> {
                    NoHomeBaseContent(onSetupClick = onNavigateToSetup)
                }
                permissionStatus != PermissionStatus.AUTHORIZED -> {
                    PermissionRequiredContent(
                        homeBase = homeBase,
                        onEnableLocationClick = onRequestLocationPermission,
                        onShowTaxiCardClick = { homeBase?.let(onNavigateToTaxiCard) }
                    )
                }
                else -> {
                    CompassContent(
                        homeBase = homeBase!!,
                        arrowRotation = arrowRotation,
                        formattedDistance = formattedDistance,
                        estimatedWalkTime = estimatedWalkTime,
                        directionDescription = directionDescription,
                        headingConfidence = headingConfidence,
                        onRefreshClick = { viewModel.refreshLocation() },
                        onShowTaxiCardClick = { onNavigateToTaxiCard(homeBase!!) }
                    )
                }
            }
        }

        // Error dialog
        errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun CompassContent(
    homeBase: HomeBase,
    arrowRotation: Double,
    formattedDistance: String,
    estimatedWalkTime: String,
    directionDescription: String,
    headingConfidence: HeadingConfidence,
    onRefreshClick: () -> Unit,
    onShowTaxiCardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Home base name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = homeBase.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            homeBase.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Compass
        CompassArrow(
            rotationDegrees = arrowRotation,
            confidence = headingConfidence,
            size = 220.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Distance and time
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedDistance,
                    fontSize = 32.sp,
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
                    text = estimatedWalkTime,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Walk time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direction
        Text(
            text = directionDescription,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Heading confidence
        HeadingConfidenceIndicator(confidence = headingConfidence)

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRefreshClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Refresh Location")
            }

            Button(
                onClick = onShowTaxiCardClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarrakechOrange
                )
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Show to Taxi Driver")
            }
        }
    }
}

@Composable
private fun HeadingConfidenceIndicator(confidence: HeadingConfidence) {
    val (color, text) = when (confidence) {
        HeadingConfidence.GOOD -> Color(0xFF4CAF50) to "Heading: Good"
        HeadingConfidence.WEAK -> Color(0xFFFF9800) to "Heading: Weak — move phone for better accuracy"
        HeadingConfidence.UNAVAILABLE -> Color(0xFF9E9E9E) to "Heading: Unavailable"
    }

    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoHomeBaseContent(onSetupClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Home,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MarrakechOrange
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Set Your Home Base",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Save where you're staying so you can always find your way back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSetupClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MarrakechOrange
            )
        ) {
            Text("Set Home Base")
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    homeBase: HomeBase?,
    onEnableLocationClick: () -> Unit,
    onShowTaxiCardClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MarrakechOrange
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Location Permission Needed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To show the compass direction to your home base, we need access to your location. Your location is only used on-device and never sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onEnableLocationClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Location")
        }

        if (homeBase != null) {
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You can still use the taxi driver card",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShowTaxiCardClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MarrakechOrange
                )
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Show to Taxi Driver")
            }
        }
    }
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun GoHomeScreenPreview() {
    MarrakechGuideTheme {
        CompassContent(
            homeBase = HomeBase(
                name = "Riad Dar Maya",
                lat = 31.6295,
                lng = -7.9912,
                address = "12 Derb Sidi Bouloukate, Medina"
            ),
            arrowRotation = 45.0,
            formattedDistance = "450 m",
            estimatedWalkTime = "7 min",
            directionDescription = "Northeast",
            headingConfidence = HeadingConfidence.GOOD,
            onRefreshClick = {},
            onShowTaxiCardClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoHomeBasePreview() {
    MarrakechGuideTheme {
        NoHomeBaseContent(onSetupClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRequiredPreview() {
    MarrakechGuideTheme {
        PermissionRequiredContent(
            homeBase = HomeBase(
                name = "Riad Dar Maya",
                lat = 31.6295,
                lng = -7.9912
            ),
            onEnableLocationClick = {},
            onShowTaxiCardClick = {}
        )
    }
}
