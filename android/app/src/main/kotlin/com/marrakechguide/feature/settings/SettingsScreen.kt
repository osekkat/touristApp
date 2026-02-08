package com.marrakechguide.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// MARK: - ViewModel

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _preferredLanguage = MutableStateFlow(prefs.getString("language", "en") ?: "en")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _preferredCurrency = MutableStateFlow(prefs.getString("currency", "MAD") ?: "MAD")
    val preferredCurrency: StateFlow<String> = _preferredCurrency.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean("wifi_only", true))
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    private val _storageUsed = MutableStateFlow("Calculating...")
    val storageUsed: StateFlow<String> = _storageUsed.asStateFlow()

    init {
        calculateStorage()
    }

    fun setLanguage(language: String) {
        _preferredLanguage.value = language
        prefs.edit().putString("language", language).apply()
    }

    fun setCurrency(currency: String) {
        _preferredCurrency.value = currency
        prefs.edit().putString("currency", currency).apply()
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        _wifiOnlyDownloads.value = enabled
        prefs.edit().putBoolean("wifi_only", enabled).apply()
    }

    private fun calculateStorage() {
        viewModelScope.launch {
            delay(500)
            _storageUsed.value = "6.9 MB"
        }
    }

    fun clearRecentHistory() {
        // TODO: Clear recent history
    }

    fun clearSavedItems() {
        // TODO: Clear saved items
    }
}

// MARK: - Settings Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToHomeBase: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val preferredCurrency by viewModel.preferredCurrency.collectAsState()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // General
            item {
                SettingsSectionHeader("General")
            }

            item {
                SettingsDropdownItem(
                    label = "Language",
                    value = if (preferredLanguage == "en") "English" else "Français",
                    options = listOf("en" to "English", "fr" to "Français"),
                    onValueChange = { viewModel.setLanguage(it) }
                )
            }

            item {
                SettingsDropdownItem(
                    label = "Home Currency",
                    value = preferredCurrency,
                    options = listOf(
                        "MAD" to "MAD - Moroccan Dirham",
                        "USD" to "USD - US Dollar",
                        "EUR" to "EUR - Euro",
                        "GBP" to "GBP - British Pound"
                    ),
                    onValueChange = { viewModel.setCurrency(it) }
                )
            }

            // Offline & Downloads
            item {
                SettingsSectionHeader("Offline & Downloads")
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.CloudDownload,
                    label = "Downloaded Content",
                    onClick = onNavigateToDownloads
                )
            }

            item {
                SettingsSwitchItem(
                    label = "Wi-Fi Only Downloads",
                    checked = wifiOnlyDownloads,
                    onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                )
            }

            item {
                SettingsInfoItem(label = "Storage Used", value = storageUsed)
            }

            // Home Base
            item {
                SettingsSectionHeader("Home Base")
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Home,
                    label = "Your Hotel/Riad",
                    subtitle = "Not set",
                    onClick = onNavigateToHomeBase
                )
            }

            // Privacy
            item {
                SettingsSectionHeader("Privacy")
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Security,
                    label = "Privacy Center",
                    onClick = onNavigateToPrivacy
                )
            }

            // Data
            item {
                SettingsSectionHeader("Data")
            }

            item {
                SettingsButtonItem(
                    label = "Clear Recent History",
                    onClick = { viewModel.clearRecentHistory() }
                )
            }

            item {
                SettingsButtonItem(
                    label = "Clear All Saved Items",
                    onClick = { showClearDialog = true },
                    isDestructive = true
                )
            }

            // About
            item {
                SettingsSectionHeader("About")
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.BugReport,
                    label = "Diagnostics",
                    subtitle = getAppVersion(context),
                    onClick = onNavigateToDiagnostics
                )
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Description,
                    label = "Open Source Licenses",
                    onClick = onNavigateToLicenses
                )
            }

            // Support
            item {
                SettingsSectionHeader("Support")
            }

            item {
                SettingsButtonItem(
                    icon = Icons.Default.Email,
                    label = "Report an Issue",
                    onClick = { openEmail(context) }
                )
            }

            item {
                SettingsButtonItem(
                    icon = Icons.Default.Star,
                    label = "Rate the App",
                    onClick = { openPlayStore(context) }
                )
            }

            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Refresh,
                    label = "Run Setup Again",
                    onClick = onNavigateToOnboarding
                )
            }

            item {
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Saved Items?") },
            text = { Text("This will remove all your saved places and price cards. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedItems()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// MARK: - Helper Composables

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    )
}

@Composable
private fun SettingsNavigationItem(
    icon: ImageVector? = null,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let {
            { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun SettingsInfoItem(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
private fun SettingsSwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        ListItem(
            modifier = Modifier.menuAnchor(),
            headlineContent = { Text(label) },
            trailingContent = {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onValueChange(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsButtonItem(
    icon: ImageVector? = null,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                label,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = icon?.let {
            { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
    )
}

// MARK: - Helpers

private fun getAppVersion(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pInfo.versionName} (${pInfo.longVersionCode})"
    } catch (e: Exception) {
        "1.0.0"
    }
}

private fun openEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:support@marrakechguide.app")
        putExtra(Intent.EXTRA_SUBJECT, "Marrakech Guide Feedback")
    }
    context.startActivity(Intent.createChooser(intent, "Send email"))
}

private fun openPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to browser
        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
        }
        context.startActivity(webIntent)
    }
}
