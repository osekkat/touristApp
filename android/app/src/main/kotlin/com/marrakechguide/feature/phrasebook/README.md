# Phrasebook Feature

Darija survival glossary for tourist interactions.

## Files

- **PhrasebookScreen.kt** - Main Compose screen with category chips, search, phrase list, and detail sheet

## Dependencies

- PhraseRepository (Core/Repository/ContentRepositories.kt)
- ListItemSkeleton, ErrorState, EmptyState (UI/Components)
- Hilt for dependency injection

## Usage

```kotlin
// In navigation graph
composable("phrasebook") {
    PhrasebookScreen()
}
```

## Features

- Category-based organization (Greetings, Shopping, Directions, etc.)
- Full-text search across latin and english
- Phrase detail bottom sheet with copy actions
- Large text "Taxi Driver Card" mode
- RTL Arabic text rendering
- Works 100% offline

## Categories

- All
- Greetings
- Shopping
- Directions
- Food
- Courtesy
- Numbers
- Emergency
- Communication
