# Home Base Feature

Offline "Get Me Back" navigation using compass direction and distance.

## Files

- **HomeBaseViewModel.swift** - State management for compass feature
- **GoHomeView.swift** - Main compass screen with distance/direction
- **CompassArrowView.swift** - Animated arrow component
- **TaxiDriverCardView.swift** - Large text card for showing taxi drivers
- **HomeBaseSetupView.swift** - Setup flow for saving home location

## Dependencies

- LocationService (Core/Services)
- HeadingService (Core/Services)
- GeoEngine (Core/Engines)
- UserSettingsRepository (Core/Repositories)

## Usage

```swift
// From Home screen
NavigationLink {
    GoHomeView(settingsRepository: container.userSettingsRepository)
} label: {
    Label("Go Home", systemImage: "house")
}
```

## Features

- Compass arrow pointing toward home base
- Distance and walk time estimation
- Heading confidence indicator
- Taxi driver card with Arabic text
- Works 100% offline
