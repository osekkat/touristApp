# Store Assets

This directory contains all assets needed for App Store and Play Store listings.

## Contents

| File | Status | Description |
|------|--------|-------------|
| `app-store-metadata.json` | Complete | Metadata for both stores (title, keywords, category) |
| `description-copy.md` | Complete | Full description text in EN and FR |
| `screenshots-spec.md` | Complete | Specification for 6 required screenshots |
| `whats-new-template.md` | Complete | Template for release notes |
| `screenshots/` | Pending | Actual screenshot PNG files |
| `feature-graphic.png` | Pending | Play Store feature graphic (1024x500) |

## Text Assets (Complete)

All copywriting and metadata is ready for submission:

- **Title:** Marrakech Guide - Offline Travel
- **Subtitle:** Prices, Phrases & Navigation
- **Keywords:** marrakech, morocco, travel, offline, prices, negotiation, guide...
- **Full Description:** EN and FR versions ready
- **What's New:** Template with examples

## Visual Assets (Pending)

### Screenshots Needed (6 per platform)
1. Quote Check - "Know Fair Prices"
2. Offline Map - "Navigate Offline"
3. Price Card - "Avoid Overcharges"
4. Home Base Compass - "Always Find Your Way"
5. Phrasebook - "Speak the Language"
6. Curated Places - "Discover Hidden Gems"

### Device Sizes
- iOS: 6.7", 6.5", 5.5" (see screenshots-spec.md)
- Android: Phone (1080x1920), Tablet 7", Tablet 10"

### Feature Graphic (Play Store)
- Size: 1024 x 500 pixels
- Content: App icon + tagline on terracotta background

## Creating Screenshots

### Option 1: Manual Capture
1. Build and run app on simulator/device
2. Navigate to each screen with realistic data
3. Capture with device screenshot
4. Add text overlays in design tool

### Option 2: Fastlane Snapshot (iOS)
```bash
# Setup fastlane (if not done)
cd ios && fastlane init

# Run snapshot
fastlane snapshot
```

### Option 3: Gradle/Maestro (Android)
```bash
# Run screenshot tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ScreenshotTest
```

## Localization

- EN (English): Primary, required
- FR (French): Secondary, recommended

Both description-copy.md and whats-new-template.md include French translations.

## Submission Checklist

- [x] App title and subtitle
- [x] Keywords (App Store)
- [x] Short description (Play Store)
- [x] Full description (EN)
- [x] Full description (FR)
- [x] What's New template
- [x] Screenshot specifications
- [ ] Screenshot images (6 per device size)
- [ ] Feature graphic (Play Store)
- [ ] App preview video (optional)
