# Settings Feature

User preferences, diagnostics, and privacy information.

## Files

- **SettingsView.swift** - Main settings screen with preferences
- **DiagnosticsView.swift** - App status and troubleshooting info
- **PrivacyCenterView.swift** - Privacy practices explanation

## Usage

```swift
// From More tab
NavigationLink {
    SettingsView()
} label: {
    Label("Settings", systemImage: "gearshape")
}
```

## Sections

### SettingsView
- **General**: Language (EN/FR), Currency selection
- **Offline & Downloads**: Content packs, Wi-Fi only toggle
- **Home Base**: Hotel/riad location for compass
- **Privacy**: Link to Privacy Center
- **Data**: Clear history, clear saved items
- **About**: Version info, diagnostics, licenses
- **Support**: Report issue, rate app, rerun setup

### DiagnosticsView
- App version, iOS version, device info
- Content status (version, counts)
- Pack status (installed/not installed)
- Offline readiness check
- Storage breakdown
- Export debug report

### PrivacyCenterView
- What's stored on device
- What leaves device (nothing)
- Location data handling
- No accounts/tracking explanation
