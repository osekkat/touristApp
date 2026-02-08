package com.marrakechguide.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// MARK: - Data Models

data class ContentStats(
    val version: String = "-",
    val lastUpdated: String = "-",
    val placesCount: Int = 0,
    val priceCardsCount: Int = 0,
    val phrasesCount: Int = 0,
    val tipsCount: Int = 0
)

data class StorageStats(
    val totalBytes: Long = 0,
    val contentBytes: Long = 0,
    val userBytes: Long = 0,
    val cacheBytes: Long = 0
) {
    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedContent: String get() = formatBytes(contentBytes)
    val formattedUser: String get() = formatBytes(userBytes)
    val formattedCache: String get() = formatBytes(cacheBytes)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

// MARK: - ViewModel

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _contentStats = MutableStateFlow(ContentStats())
    val contentStats: StateFlow<ContentStats> = _contentStats.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500)

            _contentStats.value = ContentStats(
                version = "2026.02.01",
                lastUpdated = "Feb 8, 2026",
                placesCount = 47,
                priceCardsCount = 23,
                phrasesCount = 85,
                tipsCount = 12
            )

            _storageStats.value = StorageStats(
                totalBytes = 7_340_032,
                contentBytes = 5_242_880,
                userBytes = 204_800,
                cacheBytes = 1_892_352
            )

            _isLoading.value = false
        }
    }

    fun clearCache() {
        _storageStats.value = _storageStats.value.copy(cacheBytes = 0)
    }

    fun generateDebugReport(): String {
        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "1.0.0"
        }

        val stats = _contentStats.value
        val storage = _storageStats.value

        return """
            Marrakech Guide Debug Report
            Generated: ${java.time.LocalDateTime.now()}

            === App Information ===
            Version: $appVersion
            Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}

            === Content Status ===
            Content Version: ${stats.version}
            Last Updated: ${stats.lastUpdated}
            Places: ${stats.placesCount}
            Price Cards: ${stats.priceCardsCount}
            Phrases: ${stats.phrasesCount}
            Tips: ${stats.tipsCount}

            === Pack Status ===
            Base Pack: Installed (v2026.02)
            Medina Map: Not Installed
            Audio Pack: Not Installed

            === Storage ===
            Total: ${storage.formattedTotal}
            Content: ${storage.formattedContent}
            User: ${storage.formattedUser}
            Cache: ${storage.formattedCache}

            === Note ===
            No personal data or location information is included in this report.
        """.trimIndent()
    }
}

// MARK: - Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val contentStats by viewModel.contentStats.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // App Info
            item {
                SectionHeader("App Information")
            }

            item { DiagnosticRow("App Version", appVersion) }
            item { DiagnosticRow("Android Version", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})") }
            item { DiagnosticRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}") }

            // Content Status
            item {
                SectionHeader("Content Status")
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item { DiagnosticRow("Content Version", contentStats.version) }
                item { DiagnosticRow("Last Updated", contentStats.lastUpdated) }
                item { DiagnosticRow("Places", "${contentStats.placesCount}") }
                item { DiagnosticRow("Price Cards", "${contentStats.priceCardsCount}") }
                item { DiagnosticRow("Phrases", "${contentStats.phrasesCount}") }
                item { DiagnosticRow("Tips", "${contentStats.tipsCount}") }
            }

            // Pack Status
            item {
                SectionHeader("Pack Status")
            }

            item { PackStatusRow("Base Pack", true, "2026.02") }
            item { PackStatusRow("Medina Map", false, null) }
            item { PackStatusRow("Audio Pack", false, null) }

            // Offline Readiness
            item {
                SectionHeader("Offline Readiness")
            }

            item { ReadinessRow("Core Content", true) }
            item { ReadinessRow("Search Index", true) }
            item { ReadinessRow("Home Base", false) }

            item {
                ListItem(
                    headlineContent = { Text("Test Offline Mode") },
                    leadingContent = {
                        Icon(
                            Icons.Default.AirplanemodeActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            // Storage
            item {
                SectionHeader("Storage")
            }

            item { DiagnosticRow("Total App Storage", storageStats.formattedTotal) }
            item { DiagnosticRow("Content Database", storageStats.formattedContent) }
            item { DiagnosticRow("User Data", storageStats.formattedUser) }
            item { DiagnosticRow("Cache", storageStats.formattedCache) }

            item {
                ListItem(
                    modifier = Modifier.padding(top = Spacing.sm),
                    headlineContent = {
                        TextButton(onClick = { viewModel.clearCache() }) {
                            Text("Clear Cache")
                        }
                    }
                )
            }

            // Export
            item {
                SectionHeader("Debug Report")
            }

            item {
                ListItem(
                    headlineContent = {
                        Button(onClick = {
                            val report = viewModel.generateDebugReport()
                            shareReport(context, report)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Export Debug Report")
                        }
                    }
                )
            }

            item {
                Text(
                    text = "The debug report contains app version, device info, and content status. No personal data or location information is included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }

            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
private fun PackStatusRow(name: String, isInstalled: Boolean, version: String?) {
    ListItem(
        headlineContent = { Text(name) },
        trailingContent = {
            if (isInstalled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    version?.let {
                        Text(
                            "v$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Not Installed",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun ReadinessRow(name: String, isReady: Boolean) {
    ListItem(
        headlineContent = { Text(name) },
        trailingContent = {
            Icon(
                if (isReady) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isReady) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

private fun shareReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Marrakech Guide Debug Report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Share Debug Report"))
}
