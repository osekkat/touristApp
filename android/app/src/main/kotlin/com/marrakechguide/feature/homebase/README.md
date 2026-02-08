# Home Base Feature

Offline "Get Me Back" navigation using compass direction and distance.

## Files

- **HomeBaseViewModel.kt** - State management for compass feature using StateFlow
- **GoHomeScreen.kt** - Main compass screen with distance/direction (Compose)
- **CompassArrow.kt** - Animated arrow component (Compose)
- **TaxiDriverCardScreen.kt** - Large text card for showing taxi drivers
- **HomeBaseSetupScreen.kt** - Setup flow for saving home location

## Dependencies

- LocationService (Core/Services)
- HeadingService (Core/Services)
- GeoEngine (Core/Engines)
- UserSettingsRepository (Core/Repositories)

## Usage

```kotlin
// Navigation setup
composable("go_home") {
    GoHomeScreen(
        onNavigateToSetup = { navController.navigate("home_base_setup") },
        onNavigateToTaxiCard = { homeBase ->
            navController.navigate("taxi_card/${homeBase.name}")
        },
        onRequestLocationPermission = {
            // Launch permission request
        }
    )
}
```

## Features

- Compass arrow pointing toward home base
- Distance and walk time estimation
- Heading confidence indicator
- Taxi driver card with Arabic text
- Works 100% offline
