# Phrasebook Feature

Darija survival glossary for tourist interactions.

## Files

- **PhrasebookView.swift** - Main view with category chips, search, and phrase list
- **PhrasebookViewModel.swift** - State management using PhraseRepository

## Dependencies

- PhraseRepository (Core/Repositories/ContentRepositories.swift)
- Theme (Shared/Theme.swift)
- Chip, ListItemSkeleton, ErrorState, EmptyState (Shared/Components)

## Usage

```swift
// From More tab or navigation
NavigationLink {
    PhrasebookView(phraseRepository: container.phraseRepository)
} label: {
    Label("Darija Phrasebook", systemImage: "text.bubble")
}
```

## Features

- Category-based organization (Greetings, Shopping, Directions, etc.)
- Full-text search across latin and english
- Recent phrases section
- Phrase detail sheet with copy actions
- Large text "Taxi Driver Card" mode
- RTL Arabic text rendering
- Works 100% offline

## Categories

- Greetings & Basics
- Bargaining & Shopping
- Taxi & Directions
- Food & Restaurants
- Communication
- Emergencies
- Polite Refusals
- Numbers
- Courtesy
