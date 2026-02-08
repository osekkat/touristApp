# Content Validation Report (bd-jex)

## Scope
- Source files audited in `shared/content/`:
  - `places.json`, `price_cards.json`, `glossary.json`, `itineraries.json`
  - `tips.json`, `culture.json`, `activities.json`, `events.json`
- Audit date: 2026-02-07

## Audit Summary
- Parsed all files successfully.
- Checked duplicate IDs per content type.
- Checked coordinate bounds and coarse Marrakech-area sanity.
- Checked numeric range invariants (`min <= max`) for prices/durations.
- Checked date-field presence for key review/update timestamps.
- Checked link integrity:
  - itinerary steps -> `place_id` / `activity_id`
  - tips `related_place_ids` / `related_price_card_ids`

### Item Counts
- places: 49
- price_cards: 13
- glossary: 28
- itineraries: 9
- tips: 32
- culture: 12
- activities: 18
- events: 2

## Validation Results
- Hard validation errors: **0**
- Duplicate IDs: **none found**
- Broken cross-references: **none found**
- Price/duration inversions (`min > max`): **none found**

### Non-blocking Warnings
- 4 place coordinates are outside a strict Marrakech-city bounding box (expected for day trips / out-of-city destinations):
  - `place-ouzoud-waterfalls`
  - `place-essaouira`
  - `place-safi`
  - `place-ait-benhaddou`

## Coverage Check (Critical Seed Requirements)
- Price-card coverage includes required core categories:
  - taxi: yes
  - hammam: yes
  - souk: yes (`souks`)
  - food: yes
- Phrase coverage includes core intent areas:
  - greetings: yes
  - numbers: yes
  - bargaining semantics: present via `shopping` and `polite_refusal`
  - emergencies: present via `emergency` category

## Data Quality Notes
- Glossary Arabic script values are currently null for all 28 phrases.
- All glossary entries are marked `verification_status: needs_native_review`.
- 33/49 places do not yet include images.
- One itinerary step uses `estimated_stop_minutes: 0` (`itinerary-1-day-medina-architecture` optional swap step).

These are editorial completeness items, not structural blockers.

## Fast Test Subset Created
Created `shared/content/test/` for fast local/unit runs with coherent references:
- `places.json`: 5 items
- `price_cards.json`: 3 items
- `glossary.json`: 10 items
- `itineraries.json`: 1 item
- `tips.json`: 5 items
- `culture.json`: 1 item
- `activities.json`: 2 items
- `events.json`: 1 item

Subset cross-reference check result: **0 errors**.

## Recommended Follow-up
1. Add Arabic script forms for glossary phrases and run native-language QA.
2. Normalize phrase-category taxonomy (`emergency` vs `emergencies`, `shopping` vs explicit `bargaining`) before schema lock.
3. Continue enriching place image coverage in priority user flows (Home, Explore, Prices detail links).
