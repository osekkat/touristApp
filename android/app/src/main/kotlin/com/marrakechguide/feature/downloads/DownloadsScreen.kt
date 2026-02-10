package com.marrakechguide.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.model.ContentPack
import com.marrakechguide.core.model.DownloadPreferences
import com.marrakechguide.core.model.PackState
import com.marrakechguide.core.model.PackStatus
import com.marrakechguide.core.model.PackType
import com.marrakechguide.core.service.DownloadService
import com.marrakechguide.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

// MARK: - ViewModel

data class PackDisplayItem(
    val pack: ContentPack,
    val state: PackState
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadService: DownloadService
) : ViewModel() {

    val packItems: StateFlow<List<PackDisplayItem>> = MutableStateFlow<List<PackDisplayItem>>(emptyList()).also { flow ->
        viewModelScope.launch {
            combine(
                downloadService.availablePacks,
                downloadService.packStates
            ) { packs, states ->
                packs.map { pack ->
                    PackDisplayItem(
                        pack = pack,
                        state = states[pack.id] ?: PackState(
                            packId = pack.id,
                            status = PackStatus.NOT_DOWNLOADED
                        )
                    )
                }
            }.collect { flow.value = it }
        }
    }

    val preferences: StateFlow<DownloadPreferences> = downloadService.preferences

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val availableSpace: Long
        get() = downloadService.getAvailableSpace()

    val formattedAvailableSpace: String
        get() {
            val bytes = availableSpace
            return when {
                bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
                else -> String.format("%.0f KB", bytes / 1024.0)
            }
        }

    fun startDownload(packId: String) {
        viewModelScope.launch {
            downloadService.startDownload(packId)
        }
    }

    fun pauseDownload(packId: String) {
        viewModelScope.launch {
            downloadService.pauseDownload(packId)
        }
    }

    fun resumeDownload(packId: String) {
        viewModelScope.launch {
            downloadService.resumeDownload(packId)
        }
    }

    fun cancelDownload(packId: String) {
        viewModelScope.launch {
            downloadService.cancelDownload(packId)
        }
    }

    fun removePack(packId: String) {
        viewModelScope.launch {
            downloadService.removePack(packId)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isRefreshing.value = true
            downloadService.checkForUpdates()
            _isRefreshing.value = false
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        downloadService.updatePreferences(preferences.value.copy(wifiOnly = enabled))
    }

    fun isNetworkAvailable(): Boolean = downloadService.isNetworkAvailable()
}

// MARK: - Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit
) {
    val packItems by viewModel.packItems.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var packToRemove by remember { mutableStateOf<ContentPack?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdates() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                        }
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
            // Storage info
            item {
                StorageInfoCard(
                    availableSpace = viewModel.formattedAvailableSpace,
                    wifiOnly = preferences.wifiOnly,
                    onWifiOnlyChange = { viewModel.updateWifiOnly(it) }
                )
            }

            // Pack list
            items(packItems, key = { it.pack.id }) { item ->
                PackCard(
                    pack = item.pack,
                    state = item.state,
                    onDownload = { viewModel.startDownload(item.pack.id) },
                    onPause = { viewModel.pauseDownload(item.pack.id) },
                    onResume = { viewModel.resumeDownload(item.pack.id) },
                    onCancel = { viewModel.cancelDownload(item.pack.id) },
                    onRemove = { packToRemove = item.pack }
                )
            }

            item {
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    // Remove confirmation dialog
    packToRemove?.let { pack ->
        AlertDialog(
            onDismissRequest = { packToRemove = null },
            title = { Text("Remove ${pack.displayName}?") },
            text = {
                Text("This will free up ${pack.formattedSize} of storage. You can download it again later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removePack(pack.id)
                        packToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { packToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// MARK: - Components

@Composable
private fun StorageInfoCard(
    availableSpace: String,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Storage",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = availableSpace,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download on Wi-Fi only",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange
                )
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: ContentPack,
    state: PackState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = pack.type.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pack.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(state.status)
            }

            // Progress bar (if downloading)
            AnimatedVisibility(visible = state.status == PackStatus.DOWNLOADING) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        label = "progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatBytes(state.downloadedBytes)} / ${pack.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error message
            AnimatedVisibility(visible = state.status == PackStatus.FAILED && state.errorMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.errorMessage ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Size and version info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pack.formattedSize,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.installedVersion != null) {
                    Text(
                        text = "v${state.installedVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons
            PackActionButtons(
                state = state,
                onDownload = onDownload,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRemove = onRemove
            )
        }
    }
}

@Composable
private fun PackActionButtons(
    state: PackState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        when (state.status) {
            PackStatus.NOT_DOWNLOADED, PackStatus.FAILED -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Download")
                }
            }

            PackStatus.QUEUED, PackStatus.DOWNLOADING -> {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Pause")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            PackStatus.PAUSED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Resume")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            PackStatus.VERIFYING, PackStatus.INSTALLING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = if (state.status == PackStatus.VERIFYING) "Verifying..." else "Installing...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            PackStatus.INSTALLED -> {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Remove")
                }
            }

            PackStatus.UPDATE_AVAILABLE -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Update")
                }
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: PackStatus) {
    val (text, color) = when (status) {
        PackStatus.NOT_DOWNLOADED -> null to null
        PackStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.tertiary
        PackStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
        PackStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.secondary
        PackStatus.VERIFYING -> "Verifying" to MaterialTheme.colorScheme.tertiary
        PackStatus.INSTALLING -> "Installing" to MaterialTheme.colorScheme.tertiary
        PackStatus.INSTALLED -> "Installed" to MaterialTheme.colorScheme.primary
        PackStatus.UPDATE_AVAILABLE -> "Update" to MaterialTheme.colorScheme.tertiary
        PackStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }

    if (text != null && color != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (status == PackStatus.INSTALLED) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

// MARK: - Extensions

private val PackType.icon: ImageVector
    get() = when (this) {
        PackType.MEDINA_MAP, PackType.GUELIZ_MAP -> Icons.Default.Map
        PackType.AUDIO_PHRASES -> Icons.Default.AudioFile
        PackType.HIGH_RES_IMAGES -> Icons.Default.Image
    }

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
