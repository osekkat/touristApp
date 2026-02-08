package com.marrakechguide.feature.homebase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marrakechguide.core.repository.HomeBase
import com.marrakechguide.core.repository.UserSettingsRepository
import com.marrakechguide.core.service.LocationError
import com.marrakechguide.core.service.LocationService
import com.marrakechguide.ui.theme.MarrakechGuideTheme
import com.marrakechguide.ui.theme.MarrakechOrange
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * View for setting up or changing the user's home base location.
 *
 * Options:
 * - Use current location
 * - Enter manually
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBaseSetupScreen(
    settingsRepository: UserSettingsRepository,
    locationService: LocationService,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    var isUsingCurrentLocation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSave = name.trim().isNotEmpty() &&
            latitude.isNotEmpty() &&
            longitude.isNotEmpty() &&
            latitude.toDoubleOrNull() != null &&
            longitude.toDoubleOrNull() != null

    fun useCurrentLocation() {
        scope.launch {
            isUsingCurrentLocation = true

            try {
                if (!locationService.isAuthorized) {
                    errorMessage = "Location permission is required to use current location."
                    return@launch
                }

                val location = locationService.refreshLocation()
                latitude = "%.6f".format(location.latitude)
                longitude = "%.6f".format(location.longitude)
            } catch (e: LocationError) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = "Couldn't get your location: ${e.message}"
            } finally {
                isUsingCurrentLocation = false
            }
        }
    }

    fun saveHomeBase() {
        val lat = latitude.toDoubleOrNull()
        val lng = longitude.toDoubleOrNull()

        if (lat == null || lng == null) {
            errorMessage = "Invalid coordinates"
            return
        }

        // Validate coordinates are in reasonable range
        if (lat < -90 || lat > 90) {
            errorMessage = "Latitude must be between -90 and 90"
            return
        }

        if (lng < -180 || lng > 180) {
            errorMessage = "Longitude must be between -180 and 180"
            return
        }

        // Warn if coordinates are far from Marrakech
        val marrakechLat = 31.6295
        val marrakechLng = -7.9891
        val distanceFromMarrakech = sqrt(
            (lat - marrakechLat).pow(2) + (lng - marrakechLng).pow(2)
        )

        // More than ~100km from Marrakech center - could show warning
        // For now, we'll allow it

        isSaving = true

        scope.launch {
            try {
                val homeBase = HomeBase(
                    name = name.trim(),
                    lat = lat,
                    lng = lng,
                    address = address.trim().ifEmpty { null }
                )

                settingsRepository.setHomeBase(homeBase)
                onComplete()
            } catch (e: Exception) {
                errorMessage = "Failed to save: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Home Base") },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { saveHomeBase() },
                        enabled = canSave && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Quick setup section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quick Setup",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { useCurrentLocation() },
                        enabled = !isUsingCurrentLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUsingCurrentLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Current Location")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Stand at your riad or hotel and tap to save this location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Details section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (e.g., Riad Dar Maya)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Coordinates section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Coordinates",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You can find coordinates from Google Maps or your booking confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tips section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TipRow(
                        icon = Icons.Default.Apartment,
                        text = "Enter your riad or hotel name"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TipRow(
                        icon = Icons.Default.LocationOn,
                        text = "Coordinates help the compass point accurately"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TipRow(
                        icon = Icons.Default.Lock,
                        text = "Location is stored only on your device"
                    )
                }
            }
        }

        // Error dialog
        errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun TipRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MarrakechOrange,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun HomeBaseSetupScreenPreview() {
    MarrakechGuideTheme {
        // Preview would need mock implementations
        // For now, show a placeholder
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("HomeBaseSetupScreen Preview")
            Text("(Requires mock repositories)")
        }
    }
}
