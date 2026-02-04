# AGENTS.md — Morocco Food Discovery

> Guidelines for AI coding agents working in this Next.js/TypeScript codebase.

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

## Toolchain: Next.js + TypeScript + Convex

This project uses **pnpm** as the package manager (pinned via Corepack).

### Stack Overview

| Technology               | Purpose                                                            |
| ------------------------ | ------------------------------------------------------------------ |
| **Next.js 16.x**         | React framework with App Router, Server Components, Route Handlers |
| **React 19.x**           | UI library                                                         |
| **TypeScript 5.1+**      | Type safety                                                        |
| **Convex**               | Backend database (owned content, menus, reports, caching)          |
| **Google Places API**    | Discovery provider (search, autocomplete, details)                 |
| **Google Maps**          | Map display                                                        |
| **shadcn/ui + Tailwind** | UI components and styling                                          |
| **Zod**                  | Runtime schema validation                                          |

### Runtime Requirements

- **Node.js:** 20.9+ (Next.js 16 requirement), prefer 24.x on Vercel
- **Package Manager:** pnpm (pinned via Corepack)
- **Lockfile:** Single `pnpm-lock.yaml`, deterministic installs

### Key Configuration Files

| File                 | Purpose                                    |
| -------------------- | ------------------------------------------ |
| `package.json`       | Dependencies and scripts                   |
| `next.config.ts`     | Next.js configuration                      |
| `tsconfig.json`      | TypeScript configuration                   |
| `tailwind.config.ts` | Tailwind CSS configuration                 |
| `convex/schema.ts`   | Convex database schema                     |
| `.env.local`         | Local environment variables (never commit) |

---

## Tooling Assumptions (Developer Toolbelt)

This section is a **reference** of tools available in the development environment.

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

If/when we add generated artifacts (e.g., API clients, types from schema):

- **Rule:** Never hand-edit generated outputs.
- **Convention:** Put generated outputs in a clearly labeled directory (e.g., `generated/`) and document the generator command adjacent to it.

---

## Backwards Compatibility

We optimize for a clean architecture now, not backwards compatibility.

- No "compat shims" or "v2" file clones.
- When changing behavior, migrate callers and remove old code.
- New files are only for genuinely new domains that don't fit existing modules.

---

## Console Output

- Prefer **structured, minimal logs** (avoid spammy debug output).
- Treat user-facing UX as UI-first; logs are for operators/debugging.

---

## Project Architecture

### Two-Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Provider Discovery Layer                  │
│              (Google Places via ProviderGateway)             │
│  • Autocomplete & text search                                │
│  • Nearby/viewport search for map                            │
│  • Place Details (request-time only, never persisted)        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Owned Content Layer                       │
│                        (Convex DB)                           │
│  • Places (placeKey, shortCode, providerPlaceId)             │
│  • Menus (structured + scans)                                │
│  • Guides & Collections                                       │
│  • Reports & Requests                                         │
│  • Policy-safe caches (ID-only)                              │
└─────────────────────────────────────────────────────────────┘
```

### Key Directories

| Directory               | Purpose                                                   |
| ----------------------- | --------------------------------------------------------- |
| `app/`                  | Next.js App Router (pages, layouts, route handlers)       |
| `app/api/provider/`     | BFF routes for provider calls (Google Places)             |
| `components/`           | React components                                          |
| `lib/`                  | Shared utilities and business logic                       |
| `lib/provider-gateway/` | **CRITICAL:** Single enforcement point for provider calls |
| `convex/`               | Convex functions (queries, mutations, actions)            |
| `convex/schema.ts`      | Database schema definition                                |
| `__tests__/`            | Test files                                                |
| `e2e/`                  | Playwright E2E tests                                      |

---

## Provider Policy Compliance (CRITICAL)

**This is the most important section. Provider policy violations can result in API access revocation.**

### Data Classification

**Allowed to persist (policy-safe):**

- `providerPlaceId` (Google `place_id`) — indefinitely
- `placeKey` — our namespaced identity (e.g., `g:<place_id>`)
- `shortCode` — stable short ID for share URLs
- `lat/lng` — up to 30 days with explicit `expiresAt` + purge
- Owned content (menus, uploads, guides, tags, reports)

**NEVER persist (restricted provider content):**

- provider name, address, phone, hours, website
- provider ratings/reviews
- provider photos/bytes and photo metadata

### Storage + Retention Matrix

| Data / Surface                                                           | Convex DB      | CDN/Edge      | Server memory | Browser storage | Logs/Analytics       |
| ------------------------------------------------------------------------ | -------------- | ------------- | ------------- | --------------- | -------------------- |
| providerPlaceId / placeKey / shortCode                                   | ✅             | ⚠️ optional   | ✅            | ✅ (IDs only)   | ✅ (IDs only)        |
| lat/lng (expiring, <=30d)                                                | ✅ (expiresAt) | ⚠️ optional   | ✅            | ✅ (expiring)   | ✅ (aggregated only) |
| Provider place fields (name/address/hours/phone/website/ratings/reviews) | ❌             | ❌            | ✅ ephemeral  | ❌              | ❌                   |
| Provider photos/bytes                                                    | ❌             | ❌ by default | ✅ ephemeral  | ❌              | ❌                   |
| Owned menus/guides/uploads                                               | ✅             | ✅            | ✅            | ✅              | ✅ (PII-minimized)   |

### Hard Rules

- Default to `Cache-Control: no-store` for provider endpoints
- Never log full provider responses, even on errors
- Telemetry must be **identifier-only** for places (placeKey/providerPlaceId/shortCode)
- Service worker: never cache provider routes or provider-backed HTML/JSON containing provider fields

### ProviderGateway (Single Enforcement Point)

All provider calls MUST go through `lib/provider-gateway/`. Never call Google Places API directly from route handlers or components.

`ProviderGateway` responsibilities:

- Enforce **approved FieldMask sets** (no ad-hoc masks in production)
- Map each field set to a **cost class** for monitoring
- Apply localization defaults (`languageCode` from locale, `regionCode=MA`)
- Apply timeouts + retries (jittered) only for safe idempotent endpoints
- Circuit breaker + bulkheads (cap concurrency) + backpressure
- Singleflight request coalescing (dedupe identical in-flight requests)
- Budget guardrails per endpoint class
- Redact provider payloads from logs/errors by default

### Field Set Tiers

Only these field sets are allowed:

- `AutocompleteLite`: ID + structured formatting for suggestions
- `PlaceCard`: Minimal fields for list cards & bottom sheets
- `PlaceDetailsFull`: Only for place page; still minimal

**Rule:** Never fetch `PlaceDetailsFull` for scrolling, browsing, or map panning.

---

## Place Identity Model

Use 3 identifiers intentionally:

| Identifier        | Format                       | Purpose                                          |
| ----------------- | ---------------------------- | ------------------------------------------------ |
| `providerPlaceId` | Raw Google `place_id`        | Provider reference (allowed to persist)          |
| `placeKey`        | `g:<place_id>` or `c:<slug>` | Namespaced cross-provider identity               |
| `shortCode`       | Short alphanumeric           | Share URLs, canonical routing (`/p/<shortCode>`) |

---

## API Route Handler Rules

Provider route handlers (`app/api/provider/*`) MUST:

```typescript
// Required exports
export const runtime = "nodejs"; // Never Edge for provider routes
export const dynamic = "force-dynamic";
export const revalidate = 0;

// Required headers in response
headers.set("Cache-Control", "private, no-store, max-age=0");
```

Provider fetch calls must use:

```typescript
fetch(url, { cache: "no-store" });
```

---

## Convex Schema Rules

The Convex schema (`convex/schema.ts`) must NEVER include forbidden provider fields:

❌ **Forbidden fields:**

- `providerName`, `providerAddress`, `providerPhone`, `providerHours`
- `providerWebsite`, `providerRating`, `providerReviews`
- `providerPhotos` or photo URLs

✅ **Allowed fields:**

- `providerPlaceId`, `placeKey`, `shortCode`
- `lat`, `lng` (with `expiresAt` for cache tables)
- All owned content fields (menus, guides, etc.)

---

## Linting & Formatting

**After any substantive code changes, verify no errors were introduced:**

```bash
# Type checking
pnpm typecheck

# ESLint
pnpm lint

# Formatting
pnpm format:check

# All checks
pnpm check
```

If you see errors, **carefully understand and resolve each issue**.

---

## UBS — Ultimate Bug Scanner

**Golden Rule:** `ubs <changed-files>` before every commit. Exit 0 = safe. Exit >0 = fix & re-run.

### Commands

```bash
ubs file.ts file2.tsx                  # Specific files (< 1s) — USE THIS
ubs $(git diff --name-only --cached)   # Staged files — before commit
ubs --only=ts,tsx,js src/              # Language filter (3-5x faster)
ubs --ci --fail-on-warning .           # CI mode — before PR
ubs --help                             # Full command reference
ubs sessions --entries 1               # Tail the latest session log
ubs .                                  # Whole project (ignores node_modules/, .next/)
```

### Output Format

```
⚠️  Category (N errors)
    file.ts:42:5 – Issue description
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

- **Critical (always fix):** XSS vulnerabilities, SQL injection, data exposure, null safety, async/await issues
- **Important (production):** Unhandled promise rejections, null dereferences, memory leaks, type narrowing
- **Contextual (judgment):** TODO/FIXME, console.log debugging, unused variables

### Speed Critical

Scope to changed files. `ubs src/file.ts` (< 1s) vs `ubs .` (30s). **Never full scan for small edits.**

### Anti-Patterns

- ❌ Ignore findings → ✅ Investigate each
- ❌ Full scan per edit → ✅ Scope to file
- ❌ Fix symptom (`if (x) { x.y }`) → ✅ Root cause (`x?.y`)

---

## Testing

### Test Pyramid

```
         ┌─────────────┐
         │    E2E      │  ← Playwright: critical user flows
         │  (few)      │
         ├─────────────┤
         │ Integration │  ← API routes, Convex queries, caches
         │  (some)     │
         ├─────────────┤
         │    Unit     │  ← ProviderGateway, utils, components
         │  (many)     │
         └─────────────┘
```

### Running Tests

```bash
# Unit & integration tests
pnpm test

# With coverage
pnpm test:coverage

# E2E tests
pnpm test:e2e

# Specific test file
pnpm test lib/provider-gateway/circuit-breaker.test.ts
```

### Critical Test Suites

The `lib/provider-gateway/` test suite is the **most critical** — all provider calls flow through this component.

Required test coverage:

- Circuit breaker states and transitions
- Bulkhead/concurrency limits
- Singleflight request coalescing
- Budget enforcement
- Field mask enforcement
- Timeout/retry behavior
- Payload redaction

### Coverage Thresholds

| Code Area               | Lines | Branches | Functions |
| ----------------------- | ----- | -------- | --------- |
| `lib/provider-gateway/` | 95%   | 90%      | 100%      |
| `app/api/provider/`     | 90%   | 85%      | 100%      |
| `lib/ranking/`          | 90%   | 85%      | 100%      |
| `convex/`               | 85%   | 80%      | 90%       |
| **Global minimum**      | 80%   | 70%      | 85%       |

### Compliance Tests (Must Block Deployment)

These tests are tripwires that must pass:

- **Schema compliance:** Convex schema has no forbidden provider fields
- **Mutation compliance:** Provider fields rejected/stripped from writes
- **Telemetry compliance:** No provider fields in Sentry/analytics payloads
- **Header compliance:** All `/api/provider/*` routes return `Cache-Control: no-store`
- **Service worker compliance:** SW never caches provider routes

---

## Service Modes (Graceful Degradation)

| Mode               | Trigger                         | Provider search | Provider details    | Photos     | Map                 |
| ------------------ | ------------------------------- | --------------- | ------------------- | ---------- | ------------------- |
| 0 Normal           | healthy + budget ok             | enabled         | enabled             | optional   | enabled             |
| 1 Cost-Saver       | cost spike OR latency spike     | tighter limits  | card-only           | disabled   | ID-only cache first |
| 2 Provider-Limited | provider errors OR breaker open | disabled        | explicit opens only | disabled   | ID-only cache only  |
| 3 Owned Only       | offline OR emergency            | disabled        | disabled            | owned only | saved/guides only   |

**UI Rule:** Only show banners when a user attempts a disabled feature (avoid constant nagging).

---

## i18n & RTL

- **Languages:** fr (French, default), ar (Arabic, RTL), en (English)
- **Locale routes:** `/fr`, `/ar`, `/en`
- **Provider localization:** `languageCode` per locale + `regionCode=MA`
- **RTL verified for:** filters, bottom sheets, place page, navigation

---

## Third-Party Library Usage

If you aren't 100% sure how to use a third-party library, **SEARCH ONLINE** to find the latest documentation and current best practices.

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

### TypeScript/React Examples

```bash
# Find structured code (ignores comments)
ast-grep run -l TypeScript -p 'function $NAME($$$ARGS): $RET { $$$BODY }'

# Find all useEffect with empty deps
ast-grep run -l TypeScript -p 'useEffect($CALLBACK, [])'

# Find console.log calls
ast-grep run -l TypeScript -p 'console.log($$$ARGS)'

# Quick textual hunt
rg -n 'TODO' -t ts -t tsx

# Combine speed + precision
rg -l -t ts 'fetch\(' | xargs ast-grep run -l TypeScript -p 'fetch($URL)' --json
```

---

## Morph Warp Grep — AI-Powered Code Search

Use `mcp__morph-mcp__warp_grep` for "how does X work?" discovery across the codebase.

When to use:

- You don't know where something lives.
- You want data flow across multiple files (API → service → schema → types).
- You want all touchpoints of a cross-cutting concern.

Example:

```
mcp__morph-mcp__warp_grep(
  repoPath: "/path/to/project",
  query: "How does the provider policy compliance work?"
)
```

Warp Grep:

- Expands a natural-language query to multiple search patterns.
- Runs targeted greps, reads code, follows imports, then returns concise snippets with line numbers.
- Reduces token usage by returning only relevant slices, not entire files.

When **not** to use Warp Grep:

- You already know the function/identifier name; use `rg`.
- You know the exact file; just open it.
- You only need a yes/no existence check.

| Scenario                             | Tool        |
| ------------------------------------ | ----------- |
| "How does the ProviderGateway work?" | `warp_grep` |
| "Where is `handleSubmit` defined?"   | `rg`        |
| "Replace all `var` with `const`"     | `ast-grep`  |

---

## cass — Cross-Agent Search

`cass` indexes prior agent conversations (Claude Code, Codex, Cursor, Gemini, ChatGPT, etc.) so we can reuse solved problems.

Rules:

- Never run bare `cass` (TUI). Always use `--robot` or `--json`.

Examples:

```bash
cass health
cass search "provider gateway error" --robot --limit 5
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

## Development Commands

```bash
# Start dev server
pnpm dev

# Start Convex dev
pnpm convex dev

# Build for production
pnpm build

# Type check
pnpm typecheck

# Lint
pnpm lint

# Format
pnpm format

# Run all checks
pnpm check

# Run tests
pnpm test

# Run E2E tests
pnpm test:e2e
```

---

## Environment Variables

Required environment variables (see `.env.example`):

```bash
# Convex
CONVEX_DEPLOYMENT=
NEXT_PUBLIC_CONVEX_URL=

# Google Places (server-side only)
GOOGLE_PLACES_API_KEY=

# Google Maps (browser, referrer-restricted)
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=
```

**Security rules:**

- `GOOGLE_PLACES_API_KEY` must NEVER appear in client bundles
- `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` must be referrer-restricted in Google Cloud Console

---

## Observability

### Key Metrics to Track

**Provider:**

- Latency/error rate by endpoint
- Request volume by endpoint class
- Field set mix (% card vs full)
- Circuit breaker state + service mode

**Caches:**

- Hit rate for searchResultCache, mapTileCache, placeGeoCache
- Cache size + expiry churn

**Product:**

- Zero-result searches
- Menu upload/verification funnel
- Web Vitals (LCP, INP, CLS) — identifier-only

### Telemetry Rules

- Never include provider fields in telemetry events
- Only identifiers allowed: `placeKey`, `providerPlaceId`, `shortCode`
- Redact provider fields from Sentry error payloads

---

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed):
   ```bash
   pnpm typecheck
   pnpm lint
   pnpm test
   ```
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   br sync --flush-only    # Export beads to JSONL (no git ops)
   git add .beads/         # Stage beads changes
   git add <other files>   # Stage code changes
   git commit -m "..."     # Commit everything
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**

- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

---

## Issue Tracking with br (beads_rust)

All issue tracking goes through **br**. No other TODO systems.

**Note:** `br` is non-invasive—it never executes git commands directly. You must run git commands manually after `br sync --flush-only`.

Key invariants:

- `.beads/` is authoritative state and **must always be committed** with code changes.
- Do not edit `.beads/*.jsonl` directly; only via `br`.

### Basics

Check ready work:

```bash
br ready --json
```

Create issues:

```bash
br create "Issue title" -t bug|feature|task|chore -p 0-4 --json
br create "Issue title" -p 1 --deps discovered-from:br-123 --json
```

Update:

```bash
br update br-42 --status in_progress --json
br update br-42 --priority 1 --json
```

Complete:

```bash
br close br-42 --reason "Completed" --json
```

Types:

- `bug`, `feature`, `task`, `epic`, `chore`

Priorities:

- `0` critical (security, data loss, broken builds)
- `1` high
- `2` medium (default)
- `3` low
- `4` backlog

Agent workflow:

1. `br ready` to find unblocked work.
2. Claim: `br update <id> --status in_progress`.
3. Implement + test.
4. If you discover new work, create a new bead with `discovered-from:<parent-id>`.
5. Close when done.
6. Run `br sync --flush-only`, then `git add .beads/ && git commit` in the same commit as code changes.

Sync:

- Run `br sync --flush-only` to export to `.beads/issues.jsonl`.
- Then run `git add .beads/ && git commit -m "Update beads"` to commit changes.

Never:

- Use markdown TODO lists.
- Use other trackers.
- Duplicate tracking.

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
     - `file_reservation_paths(project_key, agent_name, ["src/**"], ttl_seconds=3600, exclusive=true)`.
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

You may see changes in the working tree that you did not make. These are changes created by other agents working on the project concurrently. This is normal and happens frequently.

**NEVER** stash, revert, overwrite, or otherwise disturb changes made by other agents. Treat those changes identically to changes you made yourself.

When committing, do not try to surgically separate other agents' changes (or revert them) unless the user explicitly instructs you to; coordinate via file reservations/messages instead.

---

## Note for Codex/GPT-5.2

You constantly bother me and stop working with concerned questions that look similar to this:

```
Unexpected changes (need guidance)

- Working tree still shows edits I did not make in package.json, pnpm-lock.yaml, src/lib/provider-gateway/index.ts, convex/schema.ts. Please advise whether to keep/commit/revert these before any further work. I did not touch them.

Next steps (pick one)

1. Decide how to handle the unrelated modified files above so we can resume cleanly.
2. Triage linting warnings and type errors.
3. If you want a full suite run later, fix blockers and re-run pnpm test.
```

NEVER EVER DO THAT AGAIN. The answer is literally ALWAYS the same: those are changes created by the potentially dozen of other agents working on the project at the same time. This is not only a common occurrence, it happens multiple times PER MINUTE. The way to deal with it is simple: you NEVER, under ANY CIRCUMSTANCE, stash, revert, overwrite, or otherwise disturb in ANY way the work of other agents. Just treat those changes identically to changes that you yourself made. Just fool yourself into thinking YOU made the changes and simply don't recall it for some reason.

---

## Note on Built-in TODO Functionality

If I ask you to explicitly use your built-in TODO functionality, don't complain about this. You can use built-in TODOs if I tell you specifically to do so. Always comply with such orders.

---

<!-- morocco-food-app-machine-readable-v1 -->

## Machine-Readable Reference

### Place Identity Schema

```typescript
interface PlaceIdentity {
  providerPlaceId: string; // Raw Google place_id
  placeKey: string; // "g:<place_id>" or "c:<slug>"
  shortCode: string; // Short alphanumeric for URLs
}
```

### Provider DTO Boundary

```typescript
// ProviderGateway returns this (UI-safe, validated)
interface ProviderPlaceDTO {
  id: string;
  displayName: string;
  formattedAddress: string;
  location: { lat: number; lng: number };
  // ... other allowed fields
  attributions: Attribution[]; // Required for display
}

// Never store ProviderPlaceDTO — only use at request time
```

### API Endpoints

| Endpoint                     | Method | Returns                           | Cache-Control       |
| ---------------------------- | ------ | --------------------------------- | ------------------- |
| `/api/provider/autocomplete` | GET    | Suggestions array                 | `private, no-store` |
| `/api/provider/search`       | GET    | Places array                      | `private, no-store` |
| `/api/provider/viewportPins` | GET    | `{ providerPlaceId, lat, lng }[]` | `private, no-store` |
| `/api/provider/details`      | GET    | Place details (card or full)      | `private, no-store` |

### Convex Tables (Core)

```typescript
// convex/schema.ts
export default defineSchema({
  places: defineTable({
    placeKey: v.string(),
    provider: v.optional(v.literal("google")),
    providerPlaceId: v.optional(v.string()),
    shortCode: v.string(),
    createdAt: v.number(),
    lastSeenAt: v.number(),
  })
    .index("by_placeKey", ["placeKey"])
    .index("by_providerPlaceId", ["providerPlaceId"])
    .index("by_shortCode", ["shortCode"]),

  menus: defineTable({
    placeKey: v.string(),
    sections: v.array(menuSectionSchema),
    currency: v.string(),
    updatedAt: v.number(),
    verifiedAt: v.optional(v.number()),
  }).index("by_placeKey", ["placeKey"]),

  // ... additional tables
});
```

### Cache Tables (Ephemeral, Policy-Safe)

```typescript
placeGeoCache: defineTable({
  providerPlaceId: v.string(),
  lat: v.number(),
  lng: v.number(),
  fetchedAt: v.number(),
  expiresAt: v.number(),  // <= 30 days from fetchedAt
}).index("by_providerPlaceId", ["providerPlaceId"]),

searchResultCache: defineTable({
  cacheKey: v.string(),  // Versioned: "v1:<hash>"
  providerPlaceIds: v.array(v.string()),
  expiresAt: v.number(),
}).index("by_cacheKey", ["cacheKey"]),
```

<!-- end-morocco-food-app-machine-readable -->
