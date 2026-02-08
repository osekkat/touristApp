# Settings Feature

User preferences, diagnostics, and privacy information.

## Files

- **SettingsScreen.kt** - Main settings with ViewModel
- **DiagnosticsScreen.kt** - App status and troubleshooting
- **PrivacyCenterScreen.kt** - Privacy practices explanation

## Usage

```kotlin
// In navigation graph
composable("settings") {
    SettingsScreen(
        viewModel = hiltViewModel(),
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDiagnostics = { navController.navigate("diagnostics") },
        onNavigateToPrivacy = { navController.navigate("privacy") }
    )
}
```

## Features

- Language selection (EN/FR)
- Currency preference
- Wi-Fi only downloads toggle
- Home Base configuration
- Privacy Center
- Clear data options
- Diagnostics with debug report export
- Licenses and support links
