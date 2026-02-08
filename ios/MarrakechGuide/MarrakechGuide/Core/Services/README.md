# Services Layer

Platform service wrappers for system capabilities.

## Implemented

### LocationService
Wrapper around `CLLocationManager` for GPS coordinates.

- Battery-safe patterns (balanced accuracy by default)
- Automatic timeout after 10 minutes
- Permission handling with async/await
- Throttled updates

### HeadingService
Wrapper around `CLLocationManager` heading for compass functionality.

- Heading confidence indicators (good/weak/unavailable)
- UI update throttling (20Hz max)
- Automatic calibration detection

## Usage

```swift
// Location
let locationService = LocationServiceImpl.shared
let granted = await locationService.requestPermission()
locationService.startUpdates()
// Use currentLocation...
locationService.stopUpdates()

// Heading
let headingService = HeadingServiceImpl.shared
headingService.startUpdates()
// Use trueHeadingDegrees and headingConfidence...
headingService.stopUpdates()
```

## Planned

- SearchService (FTS search across content)
- DownloadService (pack management)
- SyncService (content updates via Convex - Phase 2)
