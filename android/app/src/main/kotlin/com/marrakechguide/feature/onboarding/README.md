# Onboarding Feature

First-run experience to build user trust and prepare for offline use.

## Files

- **OnboardingScreen.kt** - Main container with 6 step views, ViewModel, and data models

## Usage

```kotlin
// Check if onboarding should be shown
if (OnboardingViewModel.shouldShowOnboarding(context)) {
    // Navigate to OnboardingScreen
}

// In navigation graph
composable("onboarding") {
    OnboardingScreen(
        viewModel = hiltViewModel(),
        onComplete = { navController.popBackStack() }
    )
}

// Re-run from Settings
viewModel.resetOnboarding()
```

## Steps

1. **Welcome** - Language and currency selection
2. **Offline Promise** - Explain what works offline
3. **Downloads** - Optional content packs
4. **Readiness Check** - Verify content is loaded
5. **Demo** - Interactive price check example
6. **Privacy** - Summary of privacy practices

## Features

- Progress indicator across all steps
- Back/Next navigation with Skip option
- State persisted in SharedPreferences
- Animated transitions between steps
- Hilt dependency injection
