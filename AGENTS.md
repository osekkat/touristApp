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

## UBS — Ultimate Bug Scanner

**Golden Rule:** `ubs <changed-files>` before every commit. Exit 0 = safe. Exit >0 = fix & re-run.

### Commands

```bash
ubs file.swift file2.kt                # Specific files (< 1s) — USE THIS
ubs $(git diff --name-only --cached)   # Staged files — before commit
ubs --only=swift,kt src/               # Language filter (3-5x faster)
ubs --ci --fail-on-warning .           # CI mode — before PR
ubs --help                             # Full command reference
ubs sessions --entries 1               # Tail the latest session log
ubs .                                  # Whole project (ignores build/, .gradle/, Pods/)
```

### Output Format

```
⚠️  Category (N errors)
    file.swift:42:5 – Issue description
    💡 Suggested fix
Exit code: 1
```

Parse: `file:line:col` → location | 💡 → how to fix | Exit 0/1 → pass/fail

### Fix Workflow

1. Read finding → category + fix suggestion
2. Navigate `file:line:col` → view context
3. Verify real issue (not false positive)
4. Fix root cause (not symptom)
5. Re-run `ubs <file>` → exit 0
6. Commit

### Bug Severity

- **Critical (always fix):** Memory leaks, null safety, async/await issues, data exposure
- **Important (production):** Unhandled errors, force unwraps, type issues, retain cycles
- **Contextual (judgment):** TODO/FIXME, print debugging, unused variables

### Speed Critical

Scope to changed files. `ubs ios/file.swift` (< 1s) vs `ubs .` (30s). **Never full scan for small edits.**

### Anti-Patterns

- ❌ Ignore findings → ✅ Investigate each
- ❌ Full scan per edit → ✅ Scope to file
- ❌ Fix symptom (`if x != nil { x!.y }`) → ✅ Root cause (`x?.y`)

---

## ast-grep vs ripgrep

**Use `ast-grep` when structure matters.** It parses code and matches AST nodes, ignoring comments/strings, and can **safely rewrite** code.

- Refactors/codemods: rename APIs, change import forms
- Policy checks: enforce patterns across a repo
- Editor/automation: LSP mode, `--json` output

**Use `ripgrep` when text is enough.** Fastest way to grep literals/regex.

- Recon: find strings, TODOs, log lines, config values
- Pre-filter: narrow candidate files before ast-grep

### Rule of Thumb

- Need correctness or **applying changes** → `ast-grep`
- Need raw speed or **hunting text** → `rg`
- Often combine: `rg` to shortlist files, then `ast-grep` to match/modify

### Swift/Kotlin Examples

```bash
# Find structured code (ignores comments)
ast-grep run -l Swift -p 'func $NAME($$$ARGS) -> $RET { $$$BODY }'
ast-grep run -l Kotlin -p 'fun $NAME($$$ARGS): $RET { $$$BODY }'

# Find all print/Log calls
ast-grep run -l Swift -p 'print($$$ARGS)'
ast-grep run -l Kotlin -p 'Log.d($$$ARGS)'

# Find force unwraps in Swift
ast-grep run -l Swift -p '$EXPR!'

# Quick textual hunt
rg -n 'TODO' -t swift -t kotlin

# Combine speed + precision
rg -l -t swift 'URLSession' | xargs ast-grep run -l Swift -p 'URLSession.$METHOD' --json
```

---

## Morph Warp Grep — AI-Powered Code Search

**Use `mcp__morph-mcp__warp_grep` for exploratory "how does X work?" questions.** An AI agent expands your query, greps the codebase, reads relevant files, and returns precise line ranges with full context.

**Use `ripgrep` for targeted searches.** When you know exactly what you're looking for.

**Use `ast-grep` for structural patterns.** When you need AST precision for matching/rewriting.

### When to Use What

| Scenario | Tool | Why |
|----------|------|-----|
| "How is the pricing engine implemented?" | `warp_grep` | Exploratory; don't know where to start |
| "Where is the compass heading calculated?" | `warp_grep` | Need to understand architecture |
| "Find all uses of `LocationService`" | `ripgrep` | Targeted literal search |
| "Find files with force unwraps" | `ripgrep` | Simple pattern |
| "Replace all `print()` with `Logger.debug()`" | `ast-grep` | Structural refactor |

### warp_grep Usage

```
mcp__morph-mcp__warp_grep(
  repoPath: "/data/projects/touristApp",
  query: "How does the Quote → Action fairness calculation work?"
)
```

Returns structured results with file paths, line ranges, and extracted code snippets.

### Anti-Patterns

- **Don't** use `warp_grep` to find a specific function name → use `ripgrep`
- **Don't** use `ripgrep` to understand "how does X work" → wastes time with manual reads
- **Don't** use `ripgrep` for codemods → risks collateral edits

---

## cass — Cross-Agent Search

`cass` indexes prior agent conversations (Claude Code, Codex, Cursor, Gemini, ChatGPT, etc.) so we can reuse solved problems.

Rules:

- Never run bare `cass` (TUI). Always use `--robot` or `--json`.

Examples:

```bash
cass health
cass search "pricing engine error" --robot --limit 5
cass view /path/to/session.jsonl -n 42 --json
cass expand /path/to/session.jsonl -n 42 -C 3 --json
cass capabilities --json
cass robot-docs guide
```

Tips:

- Use `--fields minimal` for lean output.
- Filter by agent with `--agent`.
- Use `--days N` to limit to recent history.

stdout is data-only, stderr is diagnostics; exit code 0 means success.

Treat cass as a way to avoid re-solving problems other agents already handled.

---

## Memory System: cass-memory (cm)

The Cass Memory System (cm) gives agents effective memory based on searching across previous coding agent sessions and extracting useful lessons.

### Quick Start

```bash
# 1. Check status and see recommendations
cm onboard status

# 2. Get sessions to analyze (filtered by gaps in your playbook)
cm onboard sample --fill-gaps

# 3. Read a session with rich context
cm onboard read /path/to/session.jsonl --template

# 4. Add extracted rules (one at a time or batch)
cm playbook add "Your rule content" --category "debugging"
# Or batch add:
cm playbook add --file rules.json

# 5. Mark session as processed
cm onboard mark-done /path/to/session.jsonl
```

Before starting complex tasks, retrieve relevant context:

```bash
cm context "<task description>" --json
```

This returns:

- **relevantBullets**: Rules that may help with your task
- **antiPatterns**: Pitfalls to avoid
- **historySnippets**: Past sessions that solved similar problems
- **suggestedCassQueries**: Searches for deeper investigation

### Protocol

1. **START**: Run `cm context "<task>" --json` before non-trivial work
2. **WORK**: Reference rule IDs when following them (e.g., "Following b-8f3a2c...")
3. **FEEDBACK**: Leave inline comments when rules help/hurt:
   - `// [cass: helpful b-xyz] - reason`
   - `// [cass: harmful b-xyz] - reason`
4. **END**: Just finish your work. Learning happens automatically.

### Key Flags

| Flag           | Purpose                                      |
| -------------- | -------------------------------------------- |
| `--json`       | Machine-readable JSON output (required!)     |
| `--limit N`    | Cap number of rules returned                 |
| `--no-history` | Skip historical snippets for faster response |

stdout = data only, stderr = diagnostics. Exit 0 = success.

---

## Third-Party Library Usage

If you aren't 100% sure how to use a third-party library, **SEARCH ONLINE** to find the latest documentation and current best practices.

- **iOS**: Check Apple Developer docs, Swift Package Index, GitHub repos
- **Android**: Check Android Developers docs, KotlinLang docs, library GitHub pages
- **Don't guess** at APIs or patterns that may have changed

---

## Console Output

- Prefer **structured, minimal logs** (avoid spammy debug output)
- Treat user-facing UX as UI-first; logs are for operators/debugging
- **iOS**: Use `os_log` or `Logger` for structured logging
- **Android**: Use `Timber` or `Log` with appropriate levels
- Remove `print()` / `println()` / `Log.d()` debugging statements before commit

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

## Backwards Compatibility

We do not care about backwards compatibility—we're in early development with no users. We want to do things the **RIGHT** way with **NO TECH DEBT**.

- Never create "compatibility shims"
- Never create wrapper functions for deprecated APIs
- Never create variations like `MainV2.swift` or `main_improved.kt`
- Just fix the code directly

---

## Compiler & Linter Checks (CRITICAL)

**After any substantive code changes, you MUST verify no errors were introduced:**

### iOS

```bash
# Check for compiler errors and warnings
xcodebuild build -scheme MarrakechGuide -destination 'generic/platform=iOS' 2>&1 | xcpretty

# Run SwiftLint (if configured)
swiftlint lint --strict

# Verify formatting
swiftformat --lint ios/
```

### Android

```bash
# Check for compiler errors
./gradlew compileDebugKotlin

# Run Android Lint
./gradlew lint

# Check Kotlin formatting
./gradlew ktlintCheck

# Run detekt (if configured)
./gradlew detekt
```

### Content

```bash
# Validate content schema
node shared/scripts/validate-content.ts

# Check internal links
node shared/scripts/check-links.ts
```

If you see errors, **carefully understand and resolve each issue**. Read sufficient context to fix them the RIGHT way.

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

**Note:** `br` is non-invasive—it never executes git commands directly. After `br sync --flush-only`, you must manually run `git add .beads/` and `git commit`.

Key invariants:

- `.beads/` is authoritative state and **must always be committed** with code changes.
- Do not edit `.beads/*.jsonl` directly; only via `br`.

### Essential Commands

```bash
# View issues (launches TUI - avoid in automated sessions)
bv

# CLI commands for agents (use these instead)
br ready              # Show issues ready to work (no blockers)
br ready --json       # JSON output for parsing
br list --status=open # All open issues
br show <id>          # Full issue details with dependencies
br create --title="..." --type=task --priority=2
br update <id> --status=in_progress
br close <id> --reason="Completed"
br close <id1> <id2>  # Close multiple issues at once
br sync --flush-only  # Flush changes to .beads/ (does NOT run git)
```

Types: `bug`, `feature`, `task`, `epic`, `chore`

Priorities: `0` critical, `1` high, `2` medium (default), `3` low, `4` backlog

### Workflow Pattern

1. **Start**: Run `br ready` to find actionable work
2. **Claim**: Use `br update <id> --status in_progress`
3. **Work**: Implement the task
4. **Complete**: Use `br close <id> --reason "Completed"`
5. **Sync**: Always run `br sync --flush-only` then `git add .beads/ && git commit` at session end

### Key Concepts

- **Dependencies**: Issues can block other issues. `br ready` shows only unblocked work.
- **Blocking**: `br dep add <issue> <depends-on>` to add dependencies
- **Shared IDs**: Use Beads issue ID (e.g., `br-123`) as Mail `thread_id` and prefix subjects with `[br-123]`
- **Reservations**: When starting a task, call `file_reservation_paths()` with the issue ID in `reason`

### Mapping Cheat Sheet

| Concept | Value |
|---------|-------|
| Mail `thread_id` | `br-###` |
| Mail subject | `[br-###] ...` |
| File reservation `reason` | `br-###` |
| Commit messages | Include `br-###` for traceability |

---

## bv — Graph-Aware Triage Engine

bv is a graph-aware triage engine for Beads projects (`.beads/beads.jsonl`). Instead of parsing JSONL or hallucinating graph traversal, use robot flags for deterministic, dependency-aware outputs with precomputed metrics (PageRank, betweenness, critical path, cycles, HITS, eigenvector, k-core).

**Scope boundary:** bv handles _what to work on_ (triage, priority, planning). For agent-to-agent coordination (messaging, work claiming, file reservations), use MCP Agent Mail, which should be available to you as an MCP server. If it's not, flag to the user—they may need to start Agent Mail using the `am` alias.

**⚠️ CRITICAL: Use ONLY `--robot-*` flags. Bare `bv` launches an interactive TUI that blocks your session.**

### The Workflow: Start With Triage

**`bv --robot-triage` is your single entry point.** It returns everything you need in one call:

- `quick_ref`: at-a-glance counts + top 3 picks
- `recommendations`: ranked actionable items with scores, reasons, unblock info
- `quick_wins`: low-effort high-impact items
- `blockers_to_clear`: items that unblock the most downstream work
- `project_health`: status/type/priority distributions, graph metrics
- `commands`: copy-paste shell commands for next steps

```bash
bv --robot-triage        # THE MEGA-COMMAND: start here
bv --robot-next          # Minimal: just the single top pick + claim command
```

### Command Reference

**Planning:**
| Command | Returns |
|---------|---------|
| `--robot-plan` | Parallel execution tracks with `unblocks` lists |
| `--robot-priority` | Priority misalignment detection with confidence |

**Graph Analysis:**
| Command | Returns |
|---------|---------|
| `--robot-insights` | Full metrics: PageRank, betweenness, HITS (hubs/authorities), eigenvector, critical path, cycles, k-core, articulation points, slack |
| `--robot-label-health` | Per-label health: `health_level` (healthy\|warning\|critical), `velocity_score`, `staleness`, `blocked_count` |
| `--robot-label-flow` | Cross-label dependency: `flow_matrix`, `dependencies`, `bottleneck_labels` |
| `--robot-label-attention [--attention-limit=N]` | Attention-ranked labels by: (pagerank × staleness × block_impact) / velocity |

**History & Change Tracking:**
| Command | Returns |
|---------|---------|
| `--robot-history` | Bead-to-commit correlations: `stats`, `histories` (per-bead events/commits/milestones), `commit_index` |
| `--robot-diff --diff-since <ref>` | Changes since ref: new/closed/modified issues, cycles introduced/resolved |

**Other:**
| Command | Returns |
|---------|---------|
| `--robot-burndown <sprint>` | Sprint burndown, scope changes, at-risk items |
| `--robot-forecast <id\|all>` | ETA predictions with dependency-aware scheduling |
| `--robot-alerts` | Stale issues, blocking cascades, priority mismatches |
| `--robot-suggest` | Hygiene: duplicates, missing deps, label suggestions, cycle breaks |
| `--robot-graph [--graph-format=json\|dot\|mermaid]` | Dependency graph export |
| `--export-graph <file.html>` | Self-contained interactive HTML visualization |

### Scoping & Filtering

```bash
bv --robot-plan --label backend              # Scope to label's subgraph
bv --robot-insights --as-of HEAD~30          # Historical point-in-time
bv --recipe actionable --robot-plan          # Pre-filter: ready to work (no blockers)
bv --recipe high-impact --robot-triage       # Pre-filter: top PageRank scores
bv --robot-triage --robot-triage-by-track    # Group by parallel work streams
bv --robot-triage --robot-triage-by-label    # Group by domain
```

### Understanding Robot Output

**All robot JSON includes:**

- `data_hash` — Fingerprint of source beads.jsonl (verify consistency across calls)
- `status` — Per-metric state: `computed|approx|timeout|skipped` + elapsed ms
- `as_of` / `as_of_commit` — Present when using `--as-of`; contains ref and resolved SHA

**Two-phase analysis:**

- **Phase 1 (instant):** degree, topo sort, density — always available immediately
- **Phase 2 (async, 500ms timeout):** PageRank, betweenness, HITS, eigenvector, cycles — check `status` flags

**For large graphs (>500 nodes):** Some metrics may be approximated or skipped. Always check `status`.

### jq Quick Reference

```bash
bv --robot-triage | jq '.quick_ref'                        # At-a-glance summary
bv --robot-triage | jq '.recommendations[0]'               # Top recommendation
bv --robot-plan | jq '.plan.summary.highest_impact'        # Best unblock target
bv --robot-insights | jq '.status'                         # Check metric readiness
bv --robot-insights | jq '.Cycles'                         # Circular deps (must fix!)
bv --robot-label-health | jq '.results.labels[] | select(.health_level == "critical")'
```

**Performance:** Phase 1 instant, Phase 2 async (500ms timeout). Prefer `--robot-plan` over `--robot-insights` when speed matters. Results cached by data hash.

Use bv instead of parsing beads.jsonl—it computes PageRank, critical paths, cycles, and parallel tracks deterministically.

---

## MCP Agent Mail — Multi-Agent Coordination

Agent Mail is available as an MCP server for coordinating work across agents.

### CRITICAL: How Agents Access Agent Mail

**Coding agents (Claude Code, Codex, Gemini CLI) access Agent Mail NATIVELY via MCP tools.**

- You do NOT need to implement HTTP wrappers, client classes, or JSON-RPC handling
- MCP tools are available directly in your environment (e.g., `macro_start_session`, `send_message`, `fetch_inbox`)
- If MCP tools aren't available, flag it to the user — they may need to start the Agent Mail server

**DO NOT** create HTTP wrappers or unify "client code" for agent-to-Agent-Mail communication — this is already handled by your MCP runtime.

What Agent Mail gives:

- Identities, inbox/outbox, searchable threads.
- Advisory file reservations (leases) to avoid agents clobbering each other.
- Persistent artifacts in git (human-auditable).

Core patterns:

1. **Same repo**
   - Register identity:
     - `ensure_project` then `register_agent` with the repo's absolute path as `project_key`.
   - Reserve files before editing:
     - `file_reservation_paths(project_key, agent_name, ["ios/**", "android/**"], ttl_seconds=3600, exclusive=true)`.
   - Communicate:
     - `send_message(..., thread_id="FEAT-123")`.
     - `fetch_inbox`, then `acknowledge_message`.
   - Fast reads:
     - `resource://inbox/{Agent}?project=<abs-path>&limit=20`.
     - `resource://thread/{id}?project=<abs-path>&include_bodies=true`.

2. **Macros vs granular:**
   - Prefer macros when speed is more important than fine-grained control:
     - `macro_start_session`, `macro_prepare_thread`, `macro_file_reservation_cycle`, `macro_contact_handshake`.
   - Use granular tools when you need explicit behavior.

Common pitfalls:

- "from_agent not registered" → call `register_agent` with correct `project_key`.
- `FILE_RESERVATION_CONFLICT` → adjust patterns, wait for expiry, or use non-exclusive reservation.

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
