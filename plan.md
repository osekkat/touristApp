# Marrakech Tourist App (iOS + Android) — React Native / Expo Plan (Offline-First + Convex Phase 2)

## 1) Product thesis

This is a **paid "confidence" app** for tourists in Marrakech (Morocco):

- Know **what to do today** (curated, not overwhelming) — plus a 1-tap **My Day plan** that fits your time/budget.
- Know **what things should cost** (MAD ranges + negotiation scripts)
- Avoid **common tourist traps**
- Act with **cultural confidence** (etiquette + do/don't)
- Reduce **language friction** (Darija glossary + optional audio)
- Works **offline** as the default mode

- Instantly sanity-check any quoted price in the moment (**Quote → Action**)
- Set a **Home Base** (riad/hotel) and get an offline compass back to it
- Follow itineraries with **Route Cards** (next stop, distance, time, offline-friendly)

### Target users & moments (personas)

- **First-time visitors (1–4 days)** who want a clear plan without endless research.
- **Families / groups** who need "safe defaults" (hours, fees, etiquette, kid-friendly).
- **Budget travelers** who care most about fair prices + negotiation confidence.
- **Anxiety-prone travelers** who want reassurance, scripts, and "what to do if…" flows.

### Jobs-to-be-done (JTBD)

- "I'm here today—give me a realistic plan for the next 6–10 hours."
- "I'm being quoted a price—help me respond confidently and fairly."
- "I have no internet—help me navigate, communicate, and avoid common traps."

### Product principles (keep decisions consistent)

- **Curation over completeness** (small set, high confidence).
- **Offline-first UX** (no dead-ends offline; online is additive).
- **Short, actionable writing** (checklists, scripts, ranges, red flags).
- **Trust cues everywhere** (review dates + clear "staleness" messaging).

### Success metrics (so you can evaluate v1)

- Activation: % of new users who view ≥1 Price Card + save ≥1 item in first session
- Retention: % returning on day 2 / day 7 (tourists are short-lived—optimize for day 2)
- Helpfulness: "Was this useful?" ≥80% positive on Price Cards

**Not** a reviews app (TripAdvisor/Yelp).
**Not** a generic directory (Google Maps).
It's the "locals' practical guide" you can trust.

---

## 2) Monetization

**Primary (recommended):** free download + one-time IAP unlock (no subscription)
- Reason: better conversion + lets users experience the "confidence" moment before paying.

Two packaging options:

- **Option A: paid install (current plan).**
  - Target: $4.99–$9.99 (consider regional pricing)
- **Option B (default): free download + one-time IAP unlock (often higher conversion).**
  - Free includes a small "starter pack" (e.g., 5 places + 5 price cards + 20 phrases).
  - Starter pack should include at least 1 "high-stress" flow (e.g., Taxi + Quote → Action) so users feel value immediately.
  - Paywall triggers: after viewing X price cards, or after completing 1–2 Quote → Action checks (when confidence is highest).
  - Unlock removes limits and downloads the full offline guide.
  - Pros: easier marketing + lets users "see the value" before paying.
  - Cons: slightly more implementation + store compliance work.

**Optional IAP add-ons (keep simple):**

- Offline **Audio Pack** (Darija phrases + mini audio guides)
- "Fresh Content Pack" (not required, but could fund continuous updates—optional)

No ads. No subscriptions unless you later add heavy ongoing services (not needed).

Store-quality details to plan for:

- "Restore purchases" flow (required on iOS).
- Clear messaging on what's included offline vs downloaded packs (sizes, Wi‑Fi-only toggle).
- A simple **Downloads** screen: pause/resume, progress, error states, and "free space required" preflight.

---

## 3) Core features (what must ship)

### A) Explore places (landmarks, monuments, neighborhoods, shopping)

Curated entities with:

- Short description (skimmable)
- Neighborhood context (Medina, Gueliz, etc.)
- "Best time to go", time needed, entry fees (if applicable)
- Hours + **Open now** indicator (offline, when structured hours are available)
- **Expected cost range in MAD** + "last reviewed" date
- "Local tips" and "watch outs"
- Directions (open in Apple Maps / Google Maps)
- Save / share (including a clean, screenshot-friendly "share card")

### B) Eat (restaurants & cafés + what to order)

Curated list—not giant database.
For each place:

- Why it's worth it
- What to order / what to avoid
- Typical spend range (MAD) + last reviewed
- Hours + **Open now** indicator (offline, when structured hours are available)
- Tags: budget, rooftop, family-friendly, veg-friendly, etc.

### C) Prices & negotiation (the killer differentiator)

This is where the app earns its price.
Structured "Price Cards" for common tourist spend categories:

- Taxi fares (airport, short ride, night)
- Hammam (local vs tourist)
- Souk items (lanterns, spices, rugs, babouches, argan oil, etc.)
- Food & drinks (mint tea, tagine, fresh juice, pastries)
- Guides/tours/day trips
- SIM cards / eSIM guidance (high-level)

Each price card includes:

- **Expected MAD range** (with unit like *per ride / per person / per item*) + last reviewed
- **Provenance note** (how this range was derived) + optional seasonality tags
- **Confidence + volatility** indicator (helps users interpret how strict the range is, and triggers staleness warnings sooner for volatile categories)
- "What influences price"
- Negotiation scripts (simple phrases)
- "Red flags" + "what to do instead"
- Notes for different contexts (night / peak season)

#### Quote → Action (instant quote fairness check + what to say)

A fast tool for the exact high-stress moment tourists face: *"I was quoted X MAD—am I being overcharged, and what should I say?"*

**UX flow (works offline):**

1. User selects a category (Taxi / Hammam / Souk item / Guide / Food & drink / etc.)
2. The app suggests the most relevant **Price Card** (or user picks one)
3. User enters the quoted amount (MAD) (+ quantity if relevant, e.g., per person, per item, per km)
   - The UI always shows the unit to avoid apples-to-oranges comparisons.
4. Optional context toggles adjust the expected range (e.g., night, peak season, group size, distance tier)
5. Results screen shows:
   - **Fairness meter**: Fair / High / Very High (with a confidence note)
   - **Expected range (adjusted)** + last reviewed + provenance
   - **Suggested counter-offer range** (polite + firm)
   - **Best 1–3 scripts** (Darija + English)
   - **If they won't budge**: "what to do instead" alternatives

**Implementation details:**

- All logic is deterministic and on-device; no AI required.
- Add a small `pricingEngine` module:
- Inputs: `priceCard.expectedCostMAD`, `priceCard.unit`, `quoteMAD`, optional `quantity`, selected `contextModifiers`
- Output: `{ adjustedRange, fairnessLevel, suggestedCounterRange, scripts, explanation, confidence }`
- Fairness heuristic (simple + explainable, tunable per card):
  - `Fair` if `quoteMAD <= adjustedMax`
  - `High` if `quoteMAD <= adjustedMax * highMultiplier`
  - `Very High` if `quoteMAD > adjustedMax * highMultiplier`
  - Where `highMultiplier` defaults to `1.25`, but can be overridden per Price Card/category (e.g., souks are higher-variance than posted fees).
- Suggested counter range:
  - `counterMin = adjustedMin` (or slightly above in high-friction categories)
  - `counterMax = adjustedMax` (or `adjustedMax * 0.95`)
- Store the user's last 10 Quote → Action checks locally (recents) so they can quickly re-open.

**Data requirements:**

- Extend `PriceCard` to include optional `contextModifiers` (so you can adjust ranges without changing code), for example:
  - `id`: `"night"`, `"peak_season"`, `"group_3plus"`, `"airport_fixed_fare"`, etc.
  - `label`: shown in the UI
  - `factorMin/factorMax` or `addMin/addMax`: how the expected range changes
  - `notes`: optional explanation shown to the user

Implementation note: keep modifiers *small and explainable*—the goal is confidence, not false precision.

### D) Culture & etiquette (practical, short)

Skimmable, useful:

- Dress code suggestions (contexts)
- Tipping norms
- Photography etiquette
- Ramadan etiquette basics
- Mosques / religious spaces etiquette
- Friday customs
- Respectful behavior in souks

### E) Darija survival glossary (searchable; audio optional)

- Categories: greetings, taxi, directions, bargaining, food, emergencies
- Each phrase:
  - Darija in Arabic
  - Latin transliteration
  - English meaning
  - RTL display support (Arabic shown right-to-left; "show large text" mode for taxi drivers)
  - Optional audio playback
- Include **numbers** and bargaining essentials (very high utility)

### F) Itineraries (opinionated, day-by-day)

Examples:

- 1-day "first timer"
- 3-day "best of"
- Shopping day
- Food day
- Relaxed / family-friendly day

#### My Day (offline daily plan builder)

A lightweight planner that fulfills the core JTBD ("give me a realistic plan for the next 6–10 hours") without needing internet or AI.

**UX flow (offline):**
1. Pick your constraints: time available (2–10h), pace (relaxed/standard), budget (budget/mid/splurge), and interests (food, shopping, museums, gardens, hammam, etc.).
2. Choose a starting point:
   - Current location (optional, if permission granted)
   - Home Base (if set)
   - A neighborhood (manual selection)
3. The app builds a plan with 3–7 stops + suggested meal/drink breaks.
4. Tap "Start" to follow it via **Route Cards**. You can skip a stop and the plan reflows.

**Implementation approach:** deterministic `planEngine` using your curated content.
- Inputs: `availableMinutes`, `startPoint`, `interests[]`, `pace`, `budgetTier`
- Data: per-place `estimatedVisitTime`, `bestTimeToGo`, tags, and optional `crowdLevel`/`kidFriendly` tags
- Output: ordered `Plan` (a list of place ids + time blocks) suitable for Route Cards

This keeps the promise of "what should I do today?" even for users who never open the itineraries list.

Each step links to places already in the app.

#### Route Cards (execute itineraries without getting lost)

For each itinerary (and any generated plan later), provide a **Route Card** view that helps users *do the plan* with minimal friction.

**Route Card UX:**

- "Route overview" screen:
  - list of stops in order
  - estimated total time (sum of stop times + walking estimates)
  - "Start route" button
- "Next stop" screen (the core):
  - big stop name + quick facts
  - **compass arrow + distance** to the next stop (offline)
  - rough "walk time" estimate (offline heuristic)
  - optional "Open in maps" button (online-only)
  - "Mark as done" → advances to next stop
- Optional: "route hints" for tricky legs (short text written by you)

**Implementation details:**

- No full offline navigation required.
- For each leg, compute straight-line distance with Haversine and estimate walk time with tuned speeds:
  - default: ~4.5 km/h (outside medina)
  - medina multiplier: slower (dense paths)
- Use the same heading/bearing helpers as Home Base compass to render the arrow.
- Persist route progress locally so the user can leave the app and come back:
  - `{ routeId, itineraryId, currentStepIndex, startedAt }`

**Data requirements:**

- Add optional fields to `Itinerary.steps`:
  - `estimatedStopMinutes?: number`
  - `routeHint?: string` (shown only in Route mode)

### G) Tips & safety (confidence builder)

- Common scams (henna, faux guides, "helpful" strangers, etc.)
- How to say "no" politely
- Cash/ATM basics
- Emergency numbers and what to do (high-level)
- "What to do if…" quick guides (lost phone, taxi issues, etc.)

### H) Arrival mode + quick tools (high perceived value)

An opinionated "first hours in Marrakech" flow that works fully offline:

- **Airport arrival checklist:** taxi expectations, typical fare ranges, polite refusal scripts
- **SIM / eSIM checklist:** what to ask for, what to avoid (high-level)
- **Cash / ATM checklist:** what to do if an ATM fails, small-bills reminder
- **Medina orientation tips:** "how to handle 'helpful' strangers", staying calm in crowds

Offline utility tools (simple, not gimmicky):

- **MAD ↔ home currency converter** (manual rate + last-updated timestamp)
- **Tipping quick guide** (common situations + ranges)
- **Bargaining calculator** (start price → target range; show "good deal / okay / too high")
- **Home Base compass** (set your riad/hotel; offline arrow + distance + taxi-driver card)

#### Home Base compass (offline "get me back")

Users set a single "Home Base" once (their riad/hotel). Then the app can always show:

- **Compass arrow** pointing to Home Base + distance (meters/km)
- Quick actions:
  - "Copy address / name"
  - "Show taxi driver" (large Arabic/Latin destination + simple phrase)
  - "Open directions" (online-only, optional)

**Implementation details (Expo-friendly):**

- Store `homeBase` locally as `{ name, lat, lng, notes? }`.
- Use `expo-location`:
  - `getCurrentPositionAsync` when the screen opens (fast first fix, shows "last updated" time)
  - Optional `watchPositionAsync` **only while the compass screen is visible**, with conservative settings (`Accuracy.Balanced`, `timeInterval`, `distanceInterval`)
  - `watchHeadingAsync` when available; fallback to "bearing only" if heading is unavailable
  - Provide a manual "Refresh location" button (important on devices with aggressive battery optimizations)
- Compute:
  - `distanceMeters = haversine(current, homeBase)`
  - `bearingDegrees = bearing(current, homeBase)`
  - `relativeAngle = bearingDegrees - headingDegrees` (for rotating the arrow)
- Privacy: no backend, no background tracking; location used only on-device when the compass/route screens are open.

---

## 4) UX & navigation (simple, premium)

### Bottom tabs (recommended)

1. **Home**
2. **Explore**
3. **Eat**
4. **Prices**
5. **More** (Darija, Itineraries, Tips, Culture, Settings)

### Home (paid-app feel)

- "What do you need right now?" quick actions:
  - Taxi prices
  - **My Day** (build a realistic plan for the next 6–10 hours)
  - **Quote → Action** (check a quoted price)
  - Souk bargaining
  - Arrival mode
  - **Go Home** (Home Base compass)
  - Currency converter
  - Must-see landmarks
  - Phrasebook
  - Continue route (shown only if an active Route Card is running)
- **My Day** plan builder (constraints → generated plan → Route Cards)
- **Today's Tip** (rotating)
- **Phrase of the Day**
- Optional: Weather (online-only, cached; hide gracefully offline)
- "Saved" + "Recently viewed"

### Key UX mechanics (small but high impact)

- Map/List toggle in Explore
- Fast global search across places + prices + phrases + tips
- Deep links: shared items open directly to the correct Place/PriceCard screen
- One-tap entrypoints for high-stress moments (Quote → Action, Go Home)
- "Active route" banner when following a Route Card
- Favorites + recents
- "Quick fact chips" on detail screens (hours, fee, time needed, neighborhood)
- "Open now" chips + filter (uses structured hours; falls back gracefully)
- Clear offline state UX ("No internet? Core guide still works.")
- Language support: **localized UI** (EN/FR first; Arabic UI later) + locale-aware dates/numbers/currency formatting
- RTL readiness: ensure Arabic text renders correctly (alignment, numerals, shaping) in phrasebook + taxi-driver cards
- Dark mode
- Accessibility: dynamic type, contrast, touch targets

---

## 5) Content strategy (this is the product)

Your advantage is **trusted curation + pricing confidence**.
You are not competing on "more listings". You are competing on:

- clearer advice
- more practical info
- better structure
- offline usability
- pricing + scripts + "watch outs"

### Content scope for a strong v1

- 20–40 places (high-quality, well-written)
- 15–25 price cards (high-impact categories)
- 50–150 Darija phrases
- 5–10 itineraries
- Culture/tips: concise but complete essentials

### Localization strategy (keep it manageable)

- v1 recommendation: ship **English content** + **localized UI** (EN/FR).
- Add French content as a v1.x content update once the editorial pipeline is stable.
- Always keep a safe fallback: if a translation is missing, show English rather than blanks.

### Content operations (treat content like releases)

Minimum pipeline (even if manual at first):

- Single source of truth (repo or lightweight CMS export)
- Schema validation (TypeScript + runtime validation via Zod/JSON Schema) before shipping bundles
  - enforce invariants: unique ids, valid coordinates, price min<=max, required `updatedAt`, no missing referenced ids
- Reference validation:
  - itinerary steps reference valid place/price ids
  - internal links are not broken
- Editorial checks:
  - every price card has updatedAt + provenance note
  - every place has `reviewedAt` (and `hours.verifiedAt` if hours are present)
  - places with hours include `hours.verifiedAt` (so you can show "hours last checked" and staleness messaging)
  - staleness warnings appear when appropriate
- Generate a small **content changelog** per release (used for "What's new" in-app + store update notes)
- Asset budgeting:
  - image compression + size caps
  - audio pack size displayed before download

### Trust UI for prices + places

- Every price range shows:
  - "Last reviewed: YYYY-MM-DD"
  - Optional warning if old (e.g., > 6 months):
    - "Prices may have changed"

- Every place detail shows:
  - "Last reviewed: YYYY-MM-DD" (hours/fees/tips)
  - Optional warning if old (e.g., > 12 months): "Details may have changed"

Add 2 lightweight credibility boosters:

- **Provenance note** (1 short line): "based on recent quotes in 2026-01" / "posted fare" / "local hammam vs tourist hammam"
- **Report outdated info**: simple button ("Was this accurate?" yes/no) with optional note.
  - v1: store locally + open email composer
  - phase 2: upload to backend when online

---

## 6) Technical stack (mobile)

### React Native / Expo

- **Expo + React Native**
- TypeScript
- Expo Router (file-based navigation)
- expo-localization (detect locale)
- i18n library (e.g., i18next) for UI strings + formatting helpers
- react-native-maps (map view + markers)
- expo-linking (open external directions)
- expo-sharing (share to Messages/WhatsApp/etc.)
- react-native-view-shot (render "share card" images reliably)
- expo-location (optional "near me", privacy-forward)
- Images: expo-image (optional), standard RN Image works too

### State & data

Keep it simple:

- Zustand (lightweight UI state) or React context (MVP)
- Local persistence:
  - **SQLite (expo-sqlite)** with **two DB files**:
    - `content.db` (mostly read-only): places, price cards, phrases, tips/articles, FTS tables
    - `user.db` (write-heavy): favorites, recents, Home Base, active route progress, downloads metadata
  - Why two DBs: content updates become a fast **file swap** (less risk of corrupting user state, and no long import time).
  - AsyncStorage only for tiny preferences:
    - settings (language, exchange rate, Wi‑Fi-only downloads)
- Offline content store:
  - Ship bundled JSON as **seed content**
  - On first run, import seed JSON into `content.db` (and build FTS)
  - Cache updated bundles to FileSystem; verify; then either:
    - (Preferred) replace `content.db` with a prebuilt, verified DB file for that version, or
    - import JSON into a temp DB and swap (fallback for early pipeline)

### Downloads & storage (offline packs)

Treat downloads like a mini product: predictable, resumable, and easy to manage.

- Use `expo-file-system` download resumables for pause/resume + retry
- Preflight: check available disk space before starting a download
- Verify downloads (sha256 from manifest) before importing/using assets
- Cache eviction policy:
  - show total space used by packs
  - allow "delete audio pack" / "delete images pack"
  - keep the last 1–2 content versions for rollback, then auto-clean older ones
- Respect Wi‑Fi-only toggle (and show a clear "cellular download" confirmation)

### Search

- Prefer **SQLite FTS** for fast, consistent on-device search:
  - FTS across places, price cards, phrases, tips/articles
  - Add lightweight ranking boosts (category, exact/prefix match)
  - Avoid rebuilding a heavy JS index at every launch

---

## 7) Architecture (offline-first, no backend required for v1)

### v1 (no backend)

```
React Native App
├─ Bundled seed content (data/*.json + assets)
├─ Local databases (SQLite): `content.db` + `user.db`
├─ Content loader (seed → content.db, DB migrations, verified updates via file swap)
├─ Search (SQLite FTS)
├─ Pricing engine (Quote → Action)
├─ Plan engine (My Day offline builder)
├─ Geo helpers (distance/bearing for compass + routes)
├─ FileSystem cache (downloaded bundles + audio packs)
├─ Maps (map preview + external directions)
└─ Optional online bonuses (weather, near-me sorting)
```

**Rule:** The app remains fully useful without internet.

---

## 8) Phase 2 backend: Convex (for updates + sync, not dependency)

Convex is added to:

- update content without app releases
- optionally sync favorites/bookmarks across devices (if you add auth)
- manage content with a lightweight admin workflow

### What Convex should store

**Content tables (curated):**

- `places`
- `priceCards`
- `glossaryPhrases`
- `cultureArticles`
- `itineraries`
- `tips`

**Versioning:**

- `contentVersions` (latest version + release notes + rollout rules)
  - include: `latest`, `releasedAt`, `releaseNotesByLocale`, `minSupportedAppVersion`, `rolloutPercent`

**Optional user state (if you do auth):**

- `userState` (favorites, bookmarks, downloads metadata)

**Feedback (high leverage for trust):**

- `feedback` (contentId, type, note, createdAt, appVersion, locale; no account required)

### Content sync approach (keep it robust + simple)

Two good approaches:

**Option A — Bundle-based (simplest)**

- Convex stores a `contentBundle` JSON for each version (or latest only)
- App checks `contentVersions.latest`
- If newer, downloads bundle and replaces cached content
- Rebuild local search index

Pros: easiest, least code, safest.
Cons: bigger downloads (still fine if curated).

**Option B — Incremental diffs (more complex)**

- App pulls updated docs since last version
- Writes updates to local cache
- Rebuild index

Pros: smaller updates.
Cons: more edge cases.

**Recommendation:** Start with **bundle-based**.

### Hardening (strongly recommended once updates ship)

- Add a **signed content manifest**:
  - manifest contains: `version`, `locale`, `bundleUrl`/`dbUrl`, `sizeBytes`, `sha256`, `releasedAt`, `minSupportedAppVersion`, `expiresAt`, `signature`
  - signature: Ed25519 (app ships with pinned public key; reject unsigned/expired manifests)
  - replay protection: store the highest accepted `releasedAt` and do not accept older manifests unless explicitly allowed (prevents downgrade attacks)
  - app verifies signature **before** download/import, then verifies sha256 **after** download and **before** activation
- **Atomic import + rollback**:
  - download → verify → **swap in a new content DB file** → keep previous version for rollback (fallback: import+swap tables)
- **Staged rollout**:
  - allow a rollout percentage / "beta channel" to catch bad releases early

### Assets (images/audio)

- v1: ship a small set in-app (best offline experience)
- phase 2:
  - host assets on a CDN/object store (S3/Cloudflare R2/etc.)
  - store URLs in Convex
  - download audio packs on demand and cache locally

Important: ensure asset URLs are versioned and match the content bundle version to avoid "missing audio/image" issues.

---

## 9) Data model (TypeScript-first, consistent content)

### Unify "place-like" entities

Landmarks, restaurants, shops, markets can share a base shape:

**Place**

- `id: string`
- `name: string`
- `category: "landmark" | "museum" | "garden" | "neighborhood" | "restaurant" | "cafe" | "shopping" | ...`
- `shortDescription: string`
- `longDescription?: string`
- `reviewedAt?: string` (YYYY-MM-DD)
- `confidence?: "high" | "medium" | "low"`
- `neighborhood?: string`
- `address?: string`
- `location?: { lat: number; lng: number }`
- `hours?: { text: string; timezone?: string; weekly?: { day: number; open: string; close: string }[]; verifiedAt?: string }`
  - `text` is always shown; `weekly` enables "Open now" offline when present.
- `feesMAD?: { min?: number; max?: number; notes?: string }`
- `expectedCostMAD?: { min: number; max: number; notes?: string; updatedAt: string }`
- `estimatedVisitTime?: string` (e.g., "45–90 min")
- `bestTimeToGo?: string`
- `tags?: string[]`
- `localTips?: string[]`
- `scamWarnings?: string[]`
- `doAndDont?: string[]`
- `images?: string[]` (local asset refs or URLs)

**PriceCard**

- `id: string`
- `title: string` (e.g., "Taxi: Medina short ride")
- `category: "taxi" | "hammam" | "souks" | "food" | "guides" | ...`
- `unit?: string` (e.g., "per ride", "per person", "per item")
- `volatility?: "low" | "medium" | "high"`
- `confidence?: "high" | "medium" | "low"`
- `expectedCostMAD: { min: number; max: number; notes?: string; updatedAt: string }`
- `whatInfluencesPrice?: string[]`
- `negotiationScripts?: { darijaLatin: string; english: string }[]`
- `redFlags?: string[]`
- `whatToDoInstead?: string[]`
- `contextModifiers?: { id: string; label: string; factorMin?: number; factorMax?: number; addMin?: number; addMax?: number; notes?: string }[]`
- `fairness?: { highMultiplier?: number }` (override default fairness threshold for this card)
- `quantity?: { label: string; default?: number; min?: number; step?: number }` (shown in Quote → Action when the unit implies a quantity)
  - Used by **Quote → Action** to adjust expected ranges without changing code.

**GlossaryPhrase**

- `id: string`
- `category: string`
- `arabic: string`
- `latin: string`
- `english: string`
- `audio?: string` (asset ref or URL)

**Itinerary**

- `id: string`
- `title: string`
- `duration: "1 day" | "3 days" | ...`
- `steps: { timeBlock?: string; placeId?: string; note: string; estimatedStopMinutes?: number; routeHint?: string }[]`

**TravelProfile (local, optional)**

- `budgetTier?: "budget" | "mid" | "splurge"`
- `pace?: "relaxed" | "standard"`
- `interests?: string[]`
- `groupType?: "solo" | "couple" | "family" | "friends"`
- `mobilityNotes?: string`

**Plan (generated, local)**

- `id: string`
- `createdAt: string`
- `inputs: { availableMinutes: number; startMode: "gps" | "homeBase" | "neighborhood"; interests?: string[]; budgetTier?: string; pace?: string }`
- `steps: { placeId: string; timeBlock?: string; note?: string }[]`

**UserSettings (local)**

- `homeBase?: { name: string; lat: number; lng: number; notes?: string }`
- `activeRoute?: { routeId: string; itineraryId: string; currentStepIndex: number; startedAt: string }`

---

## 10) Screen list (everything that needs to be built)

### Navigation + shell

- Tab navigator layout
- Global search modal/screen
- Shared "detail" screens (place detail, price card detail)
- Settings screen (offline downloads/**Downloads manager**, exchange rate, **language**, privacy, diagnostics, "What's new")
- Diagnostics screen (content version, last sync/import status, storage usage, export debug report)

### Home

- Quick actions
- **My Day** plan builder (constraints → generated plan → Route Cards)
- Today's tip
- Phrase of the day
- Saved + recents

### Arrival + tools

- Arrival mode (first 2 hours + first day)
- Currency converter (manual rate + timestamp)
- Tipping guide
- Bargaining calculator
- **Home Base setup** (set riad/hotel)
- **Go Home** (compass screen)

### Explore

- List view with filters
- Map view with markers + list preview
- Place detail screen
- Neighborhood section (optional but valuable)

### Eat

- Curated list view + filters
- Place detail (reuse)
- "What to eat" guide page (optional)

### Prices

- Price card categories
- Price card detail pages
- **Quote → Action** tool (modal/screen)
- Negotiation playbook page (optional)

### More

- Culture & etiquette pages
- Darija phrasebook: categories + search + phrase detail
- Itineraries list + itinerary detail
- **Route Card** (route overview + next stop)
- Tips & safety pages

### Cross-cutting features

- Favorites
- Recently viewed
- Share
- Offline content loader + caching
- Search indexing

---

## 11) Project structure (recommended)

```
marrakech-guide/
├── app/
│   ├── (tabs)/
│   │   ├── index.tsx          # Home
│   │   ├── explore.tsx        # Explore
│   │   ├── eat.tsx            # Eat
│   │   ├── prices.tsx         # Prices
│   │   └── more.tsx           # More
│   ├── place/[id].tsx         # Place detail
│   ├── price/[id].tsx         # Price card detail
│   ├── search.tsx             # Global search
│   ├── diagnostics.tsx        # content + storage + logs
│   ├── tools/
│   │   ├── quote-action.tsx    # Quote → Action
│   │   ├── go-home.tsx         # Home Base compass
│   │   ├── my-day.tsx          # My Day plan builder
│   │   └── route.tsx           # Route Card (overview + next stop)
│   └── _layout.tsx
├── components/
│   ├── Card.tsx
│   ├── Chip.tsx
│   ├── PriceTag.tsx
│   ├── FairnessMeter.tsx       # Quote → Action UI
│   ├── CompassArrow.tsx        # Go Home + Route Cards
│   ├── RouteCard.tsx           # stop list + next stop UI
│   ├── MapToggle.tsx
│   ├── MapPreview.tsx
│   ├── SearchBar.tsx
│   ├── SectionHeader.tsx
│   ├── ShareCard.tsx           # renderable image for sharing
│   └── OfflineBanner.tsx
├── data/
│   ├── places.json
│   ├── price_cards.json
│   ├── glossary.json
│   ├── culture.json
│   ├── tips.json
│   └── itineraries.json
├── i18n/
│   ├── en.json
│   └── fr.json
├── hooks/
│   ├── useContentStore.ts     # load + cache content
│   ├── useDownloads.ts        # download manager (packs + retries)
│   ├── useFavorites.ts
│   ├── useRecents.ts
│   ├── useHomeBase.ts         # set/get Home Base
│   ├── useHeading.ts          # heading watcher + fallbacks
│   ├── useActiveRoute.ts      # Route Card progress
│   └── useQuoteAction.ts      # Quote → Action engine wrapper
├── db/
│   ├── schema.ts
│   ├── migrations/
│   └── index.ts               # db init + helpers
├── utils/
│   ├── downloadManager.ts     # resumable downloads + verification
│   ├── diagnostics.ts         # build exportable debug report
│   ├── storage.ts
│   ├── searchIndex.ts
│   ├── geo.ts                 # haversine, bearing helpers
│   ├── pricingEngine.ts       # Quote → Action logic
│   ├── planEngine.ts          # My Day: offline plan builder
│   ├── routeEngine.ts         # leg estimates + progress helpers
│   └── money.ts               # exchange rate helpers
├── assets/
│   ├── images/
│   └── audio/
├── scripts/
│   ├── validate-content.ts
│   ├── build-bundle.ts
│   └── check-links.ts
└── convex/                    # phase 2 only
    ├── schema.ts
    ├── content.ts
    └── versions.ts
```

---

## 12) Non-functional requirements (make it "paid quality")

### Performance

- Fast startup (avoid heavy network calls on launch)
- Lazy-load images
- Build search index once and reuse
- Set performance budgets:
  - cold start target (mid-tier device)
  - search response time target (p95)
  - memory ceiling in Explore + Map views

### Offline behavior

- Entire guide works without internet
- Online features are additive only
- Clear messaging when offline, not scary

### Privacy

- "Near me" is optional
- Location used only on device for sorting
- No selling data, no ad tracking

### QA checklist (minimum)

- Works in airplane mode
- VoiceOver/TalkBack smoke test on core flows (Quote → Action, Go Home, Route Cards, Search)
- Works on low-memory devices
- Map view doesn't crash with many markers (keep curated marker count reasonable)
- Search works fast across content
- All external links open correctly
- Location flows tested:
  - deny permission (app still usable)
  - grant permission (Go Home + Route Cards work)
  - heading unavailable (fallback UI still works)
  - low power mode / Android battery optimizations (manual refresh still works)

Add "paid app" reliability basics:

- Interruption tests:
  - content update download interrupted
  - low storage during download
  - app killed mid-import (must recover safely)
- Observability:
  - crash reporting (privacy-forward; opt-in if preferred)
  - lightweight logging around content import failures
  - in-app Diagnostics screen + "Export debug report" (helps support without needing user screenshots)

---

## 13) Store listing strategy (so paid installs convert)

Your App Store / Play Store copy should sell outcomes:

- "Fair prices in MAD + negotiation scripts"
- "Offline-first: works without internet"
- "Avoid common scams"
- "Darija phrases + etiquette"
- "Curated must-sees + itineraries"

Screenshots should highlight:

- Price card UI
- Itinerary UI
- Phrasebook UI
- Arrival mode + converter (shows "paid utility" immediately)
- Quote → Action + Go Home + Route Cards (shows "confidence in the moment")
- Shareable Price Card "snapshot" (helps marketing + word-of-mouth)
- Offline mode

---

## 14) What to build first (order of implementation, not milestones)

1. App shell + theme + core components
2. Content loading from bundled JSON + favorites/recents
3. Explore list + place detail
4. Prices list + price card detail (this is the money-maker)
5. **Quote → Action** (reuses Price Cards; high value)
6. Darija phrasebook + search
7. Itineraries + tips/culture pages
8. **Home Base compass** (location + heading; big confidence win)
9. **My Day** plan builder (offline daily plan)
10. **Route Cards** (execute itineraries with next-stop guidance)
11. Map toggle + external directions
12. Polish: offline UX, copywriting, store-ready screenshots
13. Phase 2 Convex: content versioning + bundle downloads + optional user sync

---


## 15) Phase 2 Convex: exact "needs to be built"

- Convex schema: content tables + `contentVersions`
- Upload pipeline for curated content (could be manual at first)
- App sync module:
  - read latest version
  - compare with cached version
  - download **manifest + bundle**
  - verify hash/signature
  - import with atomic swap (keep previous version for rollback)
  - update local FTS/search tables
- Optional auth + user state sync (favorites/bookmarks)

---

## 16) Definition of "done"

The app is "ready to sell" when:

- It works great offline
- Pricing + negotiation content feels trustworthy (ranges + reviewed date)
- It's curated enough to feel premium (not empty, not bloated)
- Tourists can plan a day, move around, eat well, negotiate, and avoid traps
- Quote → Action, Go Home, My Day, and Route Cards work reliably offline (core "confidence" moments)
- Store page clearly communicates the value in 5 seconds
- Offline promise is validated via a repeatable test checklist (airplane mode + interrupted update + low storage)
