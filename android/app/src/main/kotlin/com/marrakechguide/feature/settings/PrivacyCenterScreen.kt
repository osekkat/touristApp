package com.marrakechguide.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marrakechguide.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Center") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
        ) {
            // Hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = "Your Privacy Matters",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Here's exactly what happens with your data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // What's Stored on Device
            PrivacySection(
                icon = Icons.Default.PhoneAndroid,
                title = "What's Stored on Your Device",
                items = listOf(
                    "Your saved places and recent views",
                    "Your Home Base location (if you set one)",
                    "App settings and preferences",
                    "Downloaded content packs"
                )
            )

            Spacer(Modifier.height(Spacing.md))

            // What Leaves Device
            PrivacySection(
                icon = Icons.Default.CloudUpload,
                title = "What Leaves Your Device",
                items = listOf(
                    "Nothing in version 1.0",
                    "Future versions may offer opt-in crash reports"
                ),
                highlight = "Nothing is shared without your explicit consent."
            )

            Spacer(Modifier.height(Spacing.md))

            // Location Data
            PrivacySection(
                icon = Icons.Default.LocationOn,
                title = "Location Data",
                items = listOf(
                    "Used only when compass/navigation screens are open",
                    "Never sent to any server",
                    "Never stored beyond your current session",
                    "You can use the app without location access"
                )
            )

            Spacer(Modifier.height(Spacing.md))

            // No Accounts
            PrivacySection(
                icon = Icons.Default.PersonOff,
                title = "No Accounts Required",
                items = listOf(
                    "No sign-up or login needed",
                    "No email collection",
                    "No data synced to any cloud",
                    "Your data stays on your device forever"
                )
            )

            Spacer(Modifier.height(Spacing.md))

            // No Tracking
            PrivacySection(
                icon = Icons.Default.VisibilityOff,
                title = "No Tracking",
                items = listOf(
                    "No analytics SDKs",
                    "No advertising identifiers",
                    "No third-party trackers",
                    "No behavioral profiling"
                )
            )

            Spacer(Modifier.height(Spacing.md))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50)
                    )

                    Column {
                        Text(
                            text = "In Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )

                        Text(
                            text = "This app works entirely offline and keeps all your data on your device. We don't collect, store, or share any personal information. Period.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // Contact
            Text(
                text = "Questions?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "If you have questions about our privacy practices, contact us at privacy@marrakechguide.app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun PrivacySection(
    icon: ImageVector,
    title: String,
    items: List<String>,
    highlight: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            highlight?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Spacing.sm))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                        .padding(Spacing.sm)
                )
            }
        }
    }
}
