# AGENTS.md — Marrakech Tourist App (Native iOS + Android)

> Guidelines for AI coding agents working in this native mobile codebase (Swift/SwiftUI + Kotlin/Compose).

---

## RULE 0 - THE FUNDAMENTAL OVERRIDE PREROGATIVE

If I tell you to do something, even if it goes against what follows below, YOU MUST LISTEN TO ME. I AM IN CHARGE, NOT YOU.

---

## RULE 1 – ABSOLUTE (DO NOT EVER VIOLATE THIS)

You may NOT delete any file or directory unless I explicitly give the exact command **in this session**.

- This includes files you just created (tests, tmp files, scripts, etc.).
- You do not get to decide that something is "safe" to remove.
- If you think something should be removed, stop and ask. You must receive clear written approval **before** any deletion command is even proposed.

Treat "never delete files without permission" as a hard invariant.

---

## Irreversible Git & Filesystem Actions — DO NOT EVER BREAK GLASS

Absolutely forbidden unless I give the **exact command and explicit approval** in the same message:

- `git reset --hard`
- `git clean -fd`
- `rm -rf`
- Any command that can delete or overwrite code/data

Rules:

1. If you are not 100% sure what a command will delete, do not propose or run it. Ask first.
2. Prefer safe tools: `git status`, `git diff`, `git stash`, copying to backups, etc.
3. After approval, restate the command verbatim, list what it will affect, and wait for confirmation.
4. When a destructive command is run, record in your response:
   - The exact user text authorizing it
   - The command run
   - When you ran it

If that audit trail is missing, then you must act as if the operation never happened.

---

## Git Branch: ONLY Use `main`, NEVER `master`

**The default branch is `main`.**

- **All work happens on `main`** — commits, PRs, feature branches all merge to `main`
- **Never reference `master` in code or docs** — if you see `master` anywhere, it's a bug that needs fixing

---

## Product Overview

This is a **paid, offline-first "confidence companion"** for first-time tourists in Marrakech. Built natively for iOS (Swift/SwiftUI) and Android (Kotlin/Compose).

**Core value propositions:**
- Know **what things should cost** (MAD ranges + negotiation scripts)
- Avoid **common tourist traps**
- Act with **cultural confidence** (etiquette + do/don't)
- Reduce **language friction** (Darija glossary + optional audio)
- Works **offline** as the default mode

**Key features:**
- **Quote → Action**: Instant quote fairness check + scripts
- **Home Base compass**: Offline navigation back to hotel/riad
- **My Day**: Offline daily plan builder
- **Route Cards**: Guided itinerary execution
- **Price Cards**: Structured pricing info with negotiation scripts
- **Darija phrasebook**: Arabic/Latin/English with audio

---

## Toolchain: Native iOS + Android

This is a **monorepo** with native apps and shared content.

### Stack Overview

| Platform | Technology | Purpose |
|----------|------------|---------|
| **iOS** | Swift 5.9+ / SwiftUI | Native iOS app |
| **iOS** | iOS 16.0+ | Minimum deployment target |
| **iOS** | GRDB.swift | SQLite database (FTS5 support) |
| **iOS** | CoreLocation | GPS + heading for compass |
| **Android** | Kotlin 1.9+ / Jetpack Compose | Native Android app |
| **Android** | API 26+ (Android 8.0) | Minimum SDK |
| **Android** | Room | SQLite database (FTS4/FTS5) |
| **Android** | FusedLocationProviderClient | GPS + sensors for compass |
| **Shared** | TypeScript/Node | Content validation + build scripts |
| **Shared** | SQLite | Content database format |
| **Phase 2** | Convex | Backend for content updates (optional) |

### Key Configuration Files

| File | Purpose |
|------|---------|
| `ios/MarrakechGuide.xcodeproj` | iOS Xcode project |
| `android/build.gradle.kts` | Android Gradle config |
| `shared/schema/content-schema.json` | JSON Schema for content validation |
| `shared/scripts/build-bundle.ts` | JSON → SQLite content.db builder |
| `shared/scripts/validate-content.ts` | Content schema validation |

---

## Project Structure (Monorepo)

```
marrakech-guide/
├── ios/                        # iOS app (Xcode project)
│   └── MarrakechGuide/
│       ├── App/                # Entry point, lifecycle
│       ├── Core/               # Database, repositories, services, engines
│       ├── Features/           # Feature modules (Home, Explore, Prices, etc.)
│       ├── Shared/             # Components, models, extensions, utilities
│       └── Resources/          # Assets, localization, seed data
├── android/                    # Android app (Gradle project)
│   └── app/src/main/
│       ├── kotlin/.../         # Same structure as iOS
│       ├── res/                # Resources, strings, themes
│       └── assets/seed/        # Bundled content.db
├── shared/                     # Shared content + scripts
│   ├── content/                # JSON content files
│   ├── scripts/                # Validation, build, check scripts
│   └── schema/                 # JSON Schema definitions
├── convex/                     # Phase 2 backend (optional)
└── docs/
```

### iOS Architecture

```
MarrakechGuide/
├── Core/
│   ├── Database/           # ContentDatabase.swift, UserDatabase.swift
│   ├── Repositories/       # PlaceRepository, PriceCardRepository, etc.
│   ├── Services/           # LocationService, DownloadService, SearchService
│   └── Engines/            # PricingEngine, PlanEngine, RouteEngine, GeoEngine
├── Features/
│   ├── Home/               # HomeView, HomeViewModel
│   ├── Explore/            # ExploreView, PlaceDetailView
│   ├── Prices/             # PricesView, PriceCardDetailView
│   ├── QuoteAction/        # QuoteActionView, FairnessMeterView
│   ├── HomeBase/           # GoHomeView, CompassArrowView
│   ├── MyDay/              # MyDayView, ConstraintPickerView
│   ├── RouteCards/         # RouteOverviewView, NextStopView
│   └── Phrasebook/         # PhrasebookView, PhraseDetailView
└── Shared/
    ├── Components/         # Reusable SwiftUI views
    └── Models/             # Place, PriceCard, GlossaryPhrase, etc.
```

### Android Architecture

```
app/src/main/kotlin/com/marrakechguide/
├── core/
│   ├── database/           # ContentDatabase, UserDatabase, DAOs
│   ├── repository/         # PlaceRepository, PriceCardRepository, etc.
│   ├── service/            # LocationService, DownloadService, SearchService
│   └── engine/             # PricingEngine, PlanEngine, RouteEngine, GeoEngine
├── feature/
│   ├── home/               # HomeScreen, HomeViewModel
│   ├── explore/            # ExploreScreen, PlaceDetailScreen
│   ├── prices/             # PricesScreen, PriceCardDetailScreen
│   ├── quoteaction/        # QuoteActionScreen, FairnessMeter
│   ├── homebase/           # GoHomeScreen, CompassArrow
│   ├── myday/              # MyDayScreen, ConstraintPicker
│   ├── routecards/         # RouteOverviewScreen, NextStopScreen
│   └── phrasebook/         # PhrasebookScreen, PhraseDetailScreen
├── ui/
│   ├── components/         # Reusable Compose components
│   ├── theme/              # Theme, Color, Type
│   └── navigation/         # NavGraph, BottomNavBar
└── di/                     # Hilt modules
```

---

## Database Architecture (Critical)

**Two SQLite database files per platform:**

| Database | Purpose | Write Pattern |
|----------|---------|---------------|
| `content.db` | Places, price cards, phrases, tips, FTS tables | Read-only (swap on update) |
| `user.db` | Favorites, recents, Home Base, route progress, downloads | Read-write |

**Why two DBs:** Content updates become a fast **file swap** (no migration risk, no user data corruption).

### Content DB Activation Protocol

Activation must be exclusive and crash-safe:

1. Pause reads, close DB connections
2. Swap the file using platform-safe API
3. Reopen the DB pool

**iOS:** Use `FileManager.replaceItem(...)` with optional backup name
**Android:** Close and recreate Room instance; atomic move/rename on same filesystem

### FTS Tables

**FTS tables must ship prebuilt** in `content.db`. Never build FTS at first run (causes slow startup on low-end devices).

If rebuild is ever required, do it in background and keep core search usable.

---

## Offline-First Rules (Non-Negotiable)

This is a **paid offline-first app**. These rules are absolute:

1. **Core value works immediately after install, offline** — no blocking downloads
2. **No blocking "downloading resources…" screen on first launch**
3. **If optional packs are not downloaded, the app still remains fully usable**
4. **Online features are additive only** — never required for core functionality
5. **Clear messaging when offline** — calm, not scary

### Backup Policy (Critical for Large Offline Packs)

- **Exclude** re-downloadable packs/tiles/audio/images and cached `content.db` copies from backups (iCloud / Auto Backup)
- **Backup only** user intent/state (`user.db` + small settings)

This keeps backups small and restores fast.

---

## Platform-Native Navigation Rules (Non-Negotiable)

These rules prevent "it feels off" moments that undermine trust in a paid utility app.

**iOS**
- Keep the 5 bottom tabs as the *only* top-level navigation
- Use standard push navigation for drill-down flows (NavigationStack)
- Preserve interactive swipe-back
- Use sheets/modals only for focused, temporary tasks (filters, pickers)

**Android**
- Use Material navigation patterns: Navigation Bar for tabs + Jetpack Navigation Compose
- Follow Back/Up principles: Back pops history; Up never exits the app
- Ensure gesture navigation compatibility
- Implement predictive back support for custom transitions

### Bottom Tabs

1. **Home**
2. **Explore**
3. **Eat**
4. **Prices**
5. **More** (Darija, Itineraries, Tips, Culture, Settings)

---

## UI/UX Standards

### Loading, Progress, and Error UX (Standardized)

Every screen must follow the same state model:

- `loading` → show skeleton/placeholder content (no blank screens)
- `content` → normal state
- `refreshing` → keep content visible; show subtle progress
- `offline` → show cached content + clear "what still works" message
- `error` → explain what failed + provide next action (Retry / Downloads / Work offline)

**Progress indicator rules:**
- Prefer **determinate** progress for downloads/imports (bytes + % + pause/resume/cancel)
- Use **indeterminate** spinners only for short unknown-duration work
- If >10s, show recovery actions

### Touch Targets

- **iOS:** Hit targets ≥ 44×44 pt
- **Android:** Hit targets ≥ 48×48 dp

### Accessibility

- Dynamic type / font scaling
- Correct contrast ratios
- Correct focus order for VoiceOver/TalkBack
- RTL readiness for Arabic text

---

## Location & Sensor Guardrails (Battery-Safe)

### iOS (CoreLocation)

- Use `CLLocationManager` with appropriate accuracy
- `startUpdatingLocation()` **only while compass screen is visible**
- Choose least-accurate setting that meets UX needs (start at `kCLLocationAccuracyNearestTenMeters`)
- Throttle UI redraw (arrow rotation) to 10–20 Hz regardless of sensor frequency
- Add safety timeout (stop updates after X minutes if screen left in weird lifecycle state)
- Expose "Heading confidence" state (good / weak / unavailable)
- Provide manual "Refresh location" button

### Android (FusedLocation + Sensors)

- Use `FusedLocationProviderClient` with `PRIORITY_BALANCED_POWER_ACCURACY` by default
- Escalate to high accuracy only when actively navigating or on explicit refresh
- `requestLocationUpdates()` **only while compass screen is visible** (lifecycle-aware)
- Set stop-updates timeout
- Use `SensorManager` with `TYPE_ROTATION_VECTOR` for compass heading
- Register sensor listeners only when screen is active; unregister in `onPause()`
- Throttle UI redraw to 10–20 Hz
- Track and surface sensor accuracy

### Privacy

- Request **foreground/"When in Use"** location only (never background in v1)
- Show pre-permission explanation before system prompt
- Denial path must keep app fully usable
- No backend, no background tracking
- Location used only on-device when compass/route screens are open

---

## Performance Requirements

### Responsiveness (Non-Negotiable)

**Never do on main/UI thread:**
- Disk IO
- Network calls
- JSON decoding
- SQLite queries
- Image decoding

Use platform tooling to catch regressions:
- **iOS:** Hang analysis, Instruments
- **Android:** StrictMode, jank tooling

### Startup Performance

- **iOS:** Profile launch time, eliminate UI hangs on core flows
- **Android:** Track TTID/TTFD, generate **Baseline Profiles**, run **Macrobenchmark** in CI

### Performance Budgets

| Metric | Target |
|--------|--------|
| Cold start (mid-tier device) | < 2s to interactive |
| Search response (p95) | < 100ms |
| Map render (fps) | 60fps on mid-tier |
| Route computation (Medina core) | < 500ms |

### Map Markers

Keep curated marker count reasonable. Enforce marker budget via **clustering/progressive disclosure**.

---

## Content Pipeline

### Shared Content (in `shared/content/`)

```
places.json         # Curated places
price_cards.json    # Price ranges + scripts
glossary.json       # Darija phrases
culture.json        # Culture & etiquette
tips.json           # Safety tips
itineraries.json    # Day-by-day plans
```

### Build Pipeline

```bash
# Validate content schema
node shared/scripts/validate-content.ts

# Build SQLite content.db
node shared/scripts/build-bundle.ts

# Check internal links
node shared/scripts/check-links.ts
```

### Content DB Includes

- All content tables
- FTS5 tables (prebuilt)
- `content_links` table (cross-references)

### Schema Validation Rules

- Unique IDs
- Valid coordinates
- Price min ≤ max
- Required `updatedAt` / `reviewedAt` dates
- No missing referenced IDs
- Itinerary steps reference valid place/price IDs

---

## Downloads & Packs

### Pack Types

| Pack | Contents | Ships With App |
|------|----------|----------------|
| Base Pack | Core content.db | Yes |
| Medina Pack | Offline map + routing graph + POIs | Optional |
| Gueliz Pack | Offline POIs + map | Optional |
| Day Trips Pack | Guides + optional map | Optional |
| Audio Pack | Spoken phrases + mini guides | Optional |
| Images Pack | Hi-res images | Optional |

### Downloads Screen (Product Surface)

- Determinate progress (bytes + %)
- Per-pack state: queued → downloading → verifying → installing → ready (or failed)
- Pause/resume/cancel with clear error reasons
- "Free space required" preflight
- Wi‑Fi-only toggle + cellular confirmation for large packs
- State persists across app restarts/process death

### Pack Integrity

- Verify sha256 from manifest before importing
- Signed manifest (Ed25519) with pinned public key
- Safe install: download → verify → unpack to temp → validate → atomic move → register
- Rollback: keep last-known-good version

---

## Engines (Business Logic)

Four key engines implement core offline logic. **Both platforms must produce identical outputs for identical inputs.**

### PricingEngine (Quote → Action)

```
Input: priceCard, quotedMAD, contextModifiers, quantity
Output: { adjustedRange, fairnessLevel, suggestedCounterRange, scripts, explanation, confidence }
```

Fairness levels: Low / Fair / High / Very High

### PlanEngine (My Day)

```
Input: availableMinutes, startPoint, interests[], pace, budgetTier
Output: ordered Plan (list of place IDs + time blocks)
```

### RouteEngine (Route Cards)

```
Input: itinerary/plan, currentStepIndex
Output: { nextStop, distance, bearing, estimatedWalkTime, routeHint }
```

### GeoEngine (Utilities)

- `haversine(point1, point2)` → distance in meters
- `bearing(from, to)` → degrees
- `relativeAngle(bearing, heading)` → arrow rotation

### Shared Engine Test Vectors

Write platform-agnostic test cases in JSON:

```json
{
  "pricingEngine": [
    {
      "input": { "cardId": "taxi-short", "quotedMAD": 50, "modifiers": ["night"] },
      "expected": { "fairness": "fair", "adjustedMax": 60 }
    }
  ]
}
```

Both iOS and Android test suites read these and verify engine implementations match.

---

## Tooling Assumptions (Developer Toolbelt)

### Shell & Terminal UX

- **zsh** + **oh-my-zsh** + **powerlevel10k**
- **lsd** (or eza fallback) — Modern ls
- **atuin** — Shell history with Ctrl-R
- **fzf** — Fuzzy finder
- **zoxide** — Better cd
- **direnv** — Directory-specific env vars

### Dev Tools

- **tmux** — Terminal multiplexer
- **ripgrep** (`rg`) — Fast search
- **ast-grep** (`sg`) — Structural search/replace
- **lazygit** — Git TUI
- **bat** — Better cat

### Coding Agents

- **Claude Code** — Anthropic's coding agent
- **Codex CLI** — OpenAI's coding agent
- **Gemini CLI** — Google's coding agent

### Dicklesworthstone Stack

1. **ntm** — Named Tmux Manager (agent cockpit)
2. **mcp_agent_mail** — Agent coordination via mail-like messaging
3. **ultimate_bug_scanner** (`ubs`) — Bug scanning with guardrails
4. **beads_viewer** (`bv`) — Task management TUI
5. **coding_agent_session_search** (`cass`) — Unified agent history search
6. **cass_memory_system** (`cm`) — Procedural memory for agents
7. **coding_agent_account_manager** (`caam`) — Agent auth switching
8. **simultaneous_launch_button** (`slb`) — Two-person rule for dangerous commands

---

## Code Editing Discipline

- Do **not** run scripts that bulk-modify code (codemods, invented one-off scripts, giant `sed`/regex refactors).
- Large mechanical changes: break into smaller, explicit edits and review diffs.
- Subtle/complex changes: edit by hand, file-by-file, with careful reasoning.

### No File Proliferation

If you want to change something or add a feature, **revise existing code files in place**.

New files are only for genuinely new domains that don't fit existing modules. The bar for adding files is very high.

---

## Generated Files — NEVER Edit Manually

- **Rule:** Never hand-edit generated outputs.
- Generated from shared schema:
  - Swift models from JSON Schema
  - Kotlin models from JSON Schema
  - SQLite content.db from JSON content
- **Convention:** Document generator commands; regenerate rather than hand-edit.

---

## Testing

### Test Pyramid

```
         ┌─────────────┐
         │    E2E      │  ← UI tests: critical user flows
         │  (few)      │
         ├─────────────┤
         │ Integration │  ← DB queries, services, sync
         │  (some)     │
         ├─────────────┤
         │    Unit     │  ← Engines, utilities, components
         │  (many)     │
         └─────────────┘
```

### iOS Testing

```bash
# Run unit tests
xcodebuild test -scheme MarrakechGuide -destination 'platform=iOS Simulator,name=iPhone 15'

# Run specific test
xcodebuild test -scheme MarrakechGuide -only-testing:MarrakechGuideTests/PricingEngineTests
```

### Android Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run specific test
./gradlew test --tests "com.marrakechguide.core.engine.PricingEngineTest"
```

### Critical Test Suites

- **Engines:** PricingEngine, PlanEngine, RouteEngine, GeoEngine
- **Database:** Content loading, FTS search, migrations
- **Offline:** Airplane mode flows, pack installation, content swap

### Compliance Tests (Must Block Deployment)

- **Offline smoke test:** Core flows work in airplane mode
- **Pack integrity:** Manifest verification, sha256, install/uninstall
- **Forbidden permissions:** No contacts/photos in manifest
- **Store policy:** iOS SDK / Android target SDK meet requirements

---

## QA Checklist

### Both Platforms

- Works in airplane mode (immediately after fresh install)
- Works on low-memory devices
- Map view smooth with clustering/progressive disclosure
- Search fast across all content
- Dark mode correct
- RTL text (Arabic) renders correctly

### iOS-Specific

- VoiceOver smoke test on core flows
- Dynamic Type sizes work
- Works on iOS 16.0
- Background download resume after app termination

### Android-Specific

- TalkBack smoke test on core flows
- Font scaling works
- Works on API 26
- WorkManager download resume after process death
- Battery optimization handling (Doze mode)

### Location Flows (Both)

- Deny permission → app still usable
- Grant permission → Go Home + Route Cards work
- Heading unavailable → fallback UI works
- Low power mode → manual refresh works
- Offline navigation 10 minutes → stable, reasonable battery

---

## Observability

### Quality Gates (Release Blockers)

- **Android vitals:** Crash rate, ANR rate
- **iOS metrics:** Crash/hang metrics (Xcode Organizer/MetricKit)
- These are **release blockers**, not "nice to haves"

### Crash Reporting

- **Opt-in preferred**
- Privacy-forward messaging in Privacy Center
- On-device ring-buffer logs (redacted; no precise location; no user-entered notes)
- Export debug report: pack/version state + recent redacted events

---

## CI Gates

### Content Pipeline

```bash
# Must pass before merge
node shared/scripts/validate-content.ts
node shared/scripts/check-links.ts
node shared/scripts/build-bundle.ts
```

### iOS

```bash
xcodebuild build -scheme MarrakechGuide
xcodebuild test -scheme MarrakechGuide
```

### Android

```bash
./gradlew build
./gradlew test
./gradlew lint
```

### Shared Engine Tests

Both platforms run test vectors from `shared/tests/engine-vectors.json`.

### Trust & Reliability Gates

- Pack integrity test (manifests + sha256)
- Offline smoke test (core flows in airplane mode)
- Forbidden-permissions check (fail if contacts/photos in manifest)
- Store SDK/target requirements check

---

## Issue Tracking with br (beads_rust)

All issue tracking goes through **br**. No other TODO systems.

**Note:** `br` is non-invasive—it never executes git commands directly.

Key invariants:

- `.beads/` is authoritative state and **must always be committed** with code changes.
- Do not edit `.beads/*.jsonl` directly; only via `br`.

### Basics

```bash
br ready --json                           # Check ready work
br create "Issue title" -t bug -p 1 --json  # Create issue
br update br-42 --status in_progress --json # Update
br close br-42 --reason "Completed" --json  # Complete
```

Types: `bug`, `feature`, `task`, `epic`, `chore`

Priorities: `0` critical, `1` high, `2` medium (default), `3` low, `4` backlog

### Agent Workflow

1. `br ready` to find unblocked work
2. Claim: `br update <id> --status in_progress`
3. Implement + test
4. If you discover new work, create new bead with `discovered-from:<parent-id>`
5. Close when done
6. `br sync --flush-only`, then `git add .beads/ && git commit`

---

## bv — Graph-Aware Triage Engine

**⚠️ CRITICAL: Use ONLY `--robot-*` flags. Bare `bv` launches an interactive TUI that blocks your session.**

```bash
bv --robot-triage        # THE MEGA-COMMAND: start here
bv --robot-next          # Minimal: just the single top pick
bv --robot-plan          # Parallel execution tracks
bv --robot-insights      # Full metrics: PageRank, cycles, etc.
```

Use bv instead of parsing beads.jsonl—it computes PageRank, critical paths, cycles, and parallel tracks deterministically.

---

## MCP Agent Mail — Multi-Agent Coordination

Agent Mail is available as an MCP server for coordinating work across agents.

### CRITICAL: How Agents Access Agent Mail

**Coding agents access Agent Mail NATIVELY via MCP tools.**

- You do NOT need HTTP wrappers or client classes
- MCP tools are available directly (e.g., `macro_start_session`, `send_message`, `fetch_inbox`)
- If MCP tools aren't available, flag it to the user

### Core Patterns

1. **Register identity:**
   - `ensure_project` then `register_agent` with repo's absolute path as `project_key`

2. **Reserve files before editing:**
   - `file_reservation_paths(project_key, agent_name, ["ios/**"], ttl_seconds=3600, exclusive=true)`

3. **Communicate:**
   - `send_message(..., thread_id="FEAT-123")`
   - `fetch_inbox`, then `acknowledge_message`

4. **Macros:**
   - `macro_start_session`, `macro_prepare_thread`, `macro_file_reservation_cycle`

---

## Note for AI Agents on Multi-Agent Work

You may see changes in the working tree that you did not make. These are changes created by other agents working on the project concurrently. This is normal.

**NEVER** stash, revert, overwrite, or otherwise disturb changes made by other agents. Treat those changes identically to changes you made yourself.

---

## Note for Codex/GPT-5.2

You constantly bother me with questions about unexpected changes. NEVER DO THAT. Those are changes from other agents working concurrently. Just treat them as your own changes. NEVER stash, revert, or disturb other agents' work.

---

## Landing the Plane (Session Completion)

**When ending a work session**, complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create beads for follow-up
2. **Run quality gates** (if code changed):
   ```bash
   # iOS
   xcodebuild build -scheme MarrakechGuide

   # Android
   ./gradlew build test

   # Content
   node shared/scripts/validate-content.ts
   ```
3. **Update issue status** - Close finished work
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   br sync --flush-only
   git add .beads/
   git add <other files>
   git commit -m "..."
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Verify** - All changes committed AND pushed

**CRITICAL RULES:**

- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

---

## Machine-Readable Reference

### Place Schema (SQLite)

```sql
CREATE TABLE places (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    aliases TEXT,  -- JSON array
    region_id TEXT,
    category TEXT,
    short_description TEXT,
    long_description TEXT,
    reviewed_at TEXT,
    status TEXT DEFAULT 'open',
    confidence TEXT,
    tourist_trap_level TEXT,
    neighborhood TEXT,
    address TEXT,
    lat REAL,
    lng REAL,
    hours_text TEXT,
    hours_weekly TEXT,  -- JSON array
    hours_verified_at TEXT,
    fees_min_mad INTEGER,
    fees_max_mad INTEGER,
    expected_cost_min_mad INTEGER,
    expected_cost_max_mad INTEGER,
    visit_min_minutes INTEGER,
    visit_max_minutes INTEGER,
    best_time_to_go TEXT,
    tags TEXT,  -- JSON array
    local_tips TEXT,  -- JSON array
    scam_warnings TEXT,  -- JSON array
    images TEXT  -- JSON array
);
```

### PriceCard Schema (SQLite)

```sql
CREATE TABLE price_cards (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT,
    unit TEXT,
    volatility TEXT,
    confidence TEXT,
    expected_cost_min_mad INTEGER NOT NULL,
    expected_cost_max_mad INTEGER NOT NULL,
    expected_cost_updated_at TEXT,
    what_influences_price TEXT,  -- JSON array
    negotiation_scripts TEXT,  -- JSON array
    red_flags TEXT,  -- JSON array
    context_modifiers TEXT,  -- JSON array
    fairness_high_multiplier REAL DEFAULT 1.25
);
```

### User Database Tables

```sql
-- user.db
CREATE TABLE user_settings (
    key TEXT PRIMARY KEY,
    value TEXT  -- JSON-encoded
);
-- Keys: homeBase, activeRoute, travelProfile, exchangeRate

CREATE TABLE favorites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_type TEXT,
    content_id TEXT,
    created_at TEXT
);

CREATE TABLE recents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_type TEXT,
    content_id TEXT,
    viewed_at TEXT
);
```

### Content Link Graph

```sql
CREATE TABLE content_links (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_type TEXT,
    from_id TEXT,
    to_type TEXT,
    to_id TEXT,
    link_kind TEXT  -- related_price, useful_phrase, avoid_scam, safe_alternative
);
```

<!-- marrakech-tourist-app-machine-readable-v1 -->
