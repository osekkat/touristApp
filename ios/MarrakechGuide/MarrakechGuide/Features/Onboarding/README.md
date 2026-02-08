# Onboarding Feature

First-run experience to build user trust and prepare for offline use.

## Files

- **OnboardingView.swift** - Main container with 6 step views
- **OnboardingViewModel.swift** - State management, persistence, navigation

## Usage

```swift
// Show onboarding on first launch
if OnboardingViewModel.shouldShowOnboarding() {
    OnboardingView()
}

// Or present as full-screen cover
.fullScreenCover(isPresented: $showOnboarding) {
    OnboardingView()
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
- State persisted in UserDefaults
- Accessible with VoiceOver
- Animations between steps
