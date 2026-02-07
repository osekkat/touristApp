# Marrakech Tourist App

Native iOS + Android app for first-time Marrakech visitors.

This project builds a paid, offline-first travel companion focused on practical confidence: fair prices, scam avoidance, cultural etiquette, phrase support, and reliable offline guidance.

## Project Status

This repository is currently content-first and planning-heavy:
- Product and engineering blueprint: `plan.md`
- Engineering guardrails and team conventions: `AGENTS.md`
- Curated content source files: `shared/content/*.json`
- Extracted guide assets and mappings: `docs/lonely_planet_extracted/`

Planned app code (`ios/`, `android/`) and shared build scripts are defined in the plan but may not yet be fully checked in.

## Product Goals

- Help tourists know what things should cost in MAD.
- Reduce common tourist-trap risk.
- Make etiquette and local norms easy to apply.
- Reduce language friction with Darija phrases.
- Keep core value fully usable offline.

## Core Features (v1)

- `Quote -> Action`: fairness check for quoted prices with negotiation scripts.
- `Home Base` compass: offline direction guidance back to hotel/riad.
- `My Day`: offline itinerary builder based on time, pace, and interests.
- `Route Cards`: next-stop guidance for itineraries and plans.
- `Price Cards`: structured ranges, scripts, red flags, safer alternatives.
- `Phrasebook`: Arabic + Latin transliteration + English (optional audio).
- `Explore` / `Eat`: curated places, practical tips, and trust cues.

## Non-Negotiable Product Rules

- Core flows must work immediately after install, offline.
- No blocking first-launch download wall.
- Optional packs must remain optional.
- Online features are additive, never mandatory.
- Bottom-tab navigation is fixed:
  1. Home
  2. Explore
  3. Eat
  4. Prices
  5. More

## Architecture Overview

### Why native

- iOS: Swift + SwiftUI
- Android: Kotlin + Jetpack Compose
- Rationale: platform feel, sensor reliability, robust store billing paths, and stronger offline/storage control.

### Shared strategy

- Shared content authored in JSON under `shared/content/`.
- Shared schema/build pipeline generates platform `content.db` bundles.
- Business logic parity across platforms via common engine test vectors.

### Two-database model per platform

- `content.db`: read-mostly curated content + prebuilt FTS tables.
- `user.db`: writable user state (favorites, recents, home base, progress, downloads).

Content updates use crash-safe file swap semantics (close connections -> atomic replace -> reopen).

## Repository Layout

Current high-level structure:

```text
.
├── AGENTS.md
├── plan.md
├── docs/
│   └── lonely_planet_extracted/
└── shared/
    └── content/
        ├── activities.json
        ├── culture.json
        ├── events.json
        ├── glossary.json
        ├── itineraries.json
        ├── places.json
        ├── price_cards.json
        └── tips.json
```

Planned monorepo shape (from `plan.md` / `AGENTS.md`):

```text
marrakech-guide/
├── ios/
├── android/
├── shared/
│   ├── content/
│   ├── schema/
│   └── scripts/
├── convex/   # phase 2 optional backend
└── docs/
```

## Shared Content Files

- `shared/content/places.json`: landmarks, neighborhoods, food/shopping places.
- `shared/content/price_cards.json`: price ranges, modifiers, scripts.
- `shared/content/glossary.json`: Darija phrase entries.
- `shared/content/culture.json`: etiquette and social norms.
- `shared/content/tips.json`: practical safety and decision tips.
- `shared/content/itineraries.json`: day plans and steps.
- `shared/content/activities.json`: guided activities/day trips.
- `shared/content/events.json`: optional time-sensitive events.

The content envelope uses `meta` + `items`; `items[].id` is the stable key for joins and user references.

## Development Workflow (Planned Standard)

1. Edit curated data in `shared/content/`.
2. Validate schema and links.
3. Build SQLite `content.db` bundle.
4. Integrate latest bundle into iOS and Android seed assets.
5. Run platform build/lint/test gates.
6. Run offline smoke tests and engine parity checks.

### Content pipeline commands

```bash
node shared/scripts/validate-content.ts
node shared/scripts/check-links.ts
node shared/scripts/build-bundle.ts
```

### iOS checks

```bash
xcodebuild build -scheme MarrakechGuide -destination 'generic/platform=iOS' 2>&1 | xcpretty
swiftlint lint --strict
swiftformat --lint ios/
xcodebuild test -scheme MarrakechGuide -destination 'platform=iOS Simulator,name=iPhone 15'
```

### Android checks

```bash
./gradlew compileDebugKotlin
./gradlew lint
./gradlew ktlintCheck
./gradlew detekt
./gradlew test
```

## Quality Gates

Before release, both platforms must pass:
- Offline smoke: core flows usable in airplane mode.
- Pack integrity: manifest signature/hash verification + safe install/rollback.
- Engine parity: Pricing/Plan/Route/Geo outputs match shared vectors.
- Permission policy: no forbidden contacts/photos permissions.
- Performance budgets:
  - Cold start < 2s (mid-tier)
  - Search p95 < 100ms
  - Map rendering ~60fps (mid-tier)
  - Route compute < 500ms (Medina core)

## Implementation Phases (from plan)

1. Foundation: shared pipeline + app shells.
2. Core product: Explore, Prices, Quote -> Action, Phrasebook, Itineraries.
3. Location intelligence: Home Base, My Day, Route Cards.
4. Polish and store readiness.
5. Optional phase 2: Convex-backed content updates/sync.

## Team Conventions

- Treat offline-first behavior as a hard requirement.
- Keep feature parity between iOS and Android.
- Prefer curation and trust over breadth.
- Use `br` as the issue tracker and keep `.beads/` in sync when enabled.

## Primary References

- Product/engineering blueprint: `plan.md`
- Coding and quality guardrails: `AGENTS.md`
