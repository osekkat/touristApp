# Marrakech Resource Deep Dive for Content DB Population

Generated: 2026-02-07 (CET)
Input source list: `Marrakech_data_ressources.md`

## Goal
Create a high-signal, database-oriented harvest from all resources listed in `Marrakech_data_ressources.md`, including:
- Place and attraction candidates
- Restaurant and food culture candidates
- Activity and pricing signals (for `price_cards` and `Quote -> Action`)
- Events feed candidates
- Practical/safety/confidence content
- Guidebook metadata for editorial planning

## Method used
- Opened every listed URL (all 31 references) and collected access status.
- Deep-dived content pages and subpages with direct crawl where possible.
- Used text-mirror fallback (`r.jina.ai`) for Cloudflare/JS-heavy pages.
- Captured structured fields that map to your schema (or reveal schema gaps).

## Access note
Some commerce/listing sites were partially blocked (Cloudflare/CAPTCHA/503), but fallback extraction still recovered useful structured data for nearly all of them.

---

## 1) Coverage of all 31 resources

| Ref | Source | Direct access | Deep extraction status | Main useful output |
|---|---|---:|---|---|
| 1 | Visit Marrakech | 200 | High | Official destination clusters, road trips, events, offers |
| 2 | Visit Morocco (MNTO, Marrakech page) | 200 | High | Top-5 sights, district framing, essentials, nearby cities/distances |
| 3 | Time Out Marrakech | 200 | High | Ranked attractions and restaurants list with practical summaries |
| 4 | My Guide Marrakech | 403 | High via mirror | Event feed, experiences taxonomy, monthly calendar routes |
| 5 | Lonely Planet Marrakesh destination | 200 | Medium | Top attractions/themes (via page + search snippets) |
| 6 | Routard Marrakech | 200 | High | Incontournables, practicals, community tips, transport advice |
| 7 | Petit Fute Marrakech | 403 | High via mirror | Category volumes, practical pages, nearby destinations |
| 8 | Tripadvisor Marrakech restaurants | 403 | Medium | Top restaurant names/ratings (captured via alternate extraction) |
| 9 | Airbnb Experiences Marrakesh | 200 | High via mirror | Top-rated experiences with review volume |
| 10 | GetYourGuide Marrakesh | 403 | High via mirror | Top activities list with duration/pickup |
| 11 | Viator Marrakech | 403 | High via mirror | Categories, prices, ratings, top attractions/day trips |
| 12 | Klook Marrakech | 403 | High via mirror | Activity cards with USD prices/bookings/review hints |
| 13 | Atlas Obscura Marrakesh | 403 | Medium-High (web extraction) | Unusual/hidden place candidates |
| 14 | Guichet.com | 403 | High via mirror | Event taxonomy, event cards, prices (MAD), venues |
| 15 | events.ma | 200 | High via mirror | Event categories, dated listings, prices, venue/city |
| 16 | Serious Eats Marrakesh food guide | 403 | High via mirror | Food culture narrative + venue/dish leads |
| 17 | LP Pocket Marrakesh product page | 200 | High | Edition, ISBN, pages, itinerary/map format claims |
| 18 | Amazon LP Pocket Marrakesh | 200 | Medium via mirror | Publication date, print length, edition metadata |
| 19 | Pocket Rough Guide Marrakesh | 200 | High | Area coverage, day-trip targets, ISBN/pages |
| 20 | Amazon Pocket Rough Guide Marrakesh | 200 | Low-Med | Timeout in one pass, limited direct metadata |
| 21 | Amazon Mini Rough Guide Marrakesh | 200 | Medium via mirror | Publication date and print length |
| 22 | Fnac Routard Marrakech 2025/26 | 403 | Low | Title-level confirmation only |
| 23 | Amazon FR Petit Fute City Trip | 500 | Medium via mirror | Publication date and print length |
| 24 | Fnac LP FR Marrakech en quelques jours | 403 | Low | Title-level confirmation only |
| 25 | Amazon Rough Guide Morocco 2025 | 200/500 variable | Medium via mirror | Publication date, print length |
| 26 | Walmart Rough Guide Morocco listing | 200 (robot gate) | Low | Listing presence only |
| 27 | LP Morocco product page | 200 | High | Edition, pages, coverage regions, map/itinerary claims |
| 28 | Amazon LP Morocco | 500 variable | Medium via mirror | Publication date and print length (older edition data) |
| 29 | Insight Guides Morocco | 200 | High | Coverage map, culture-first framing, ISBN/pages |
| 30 | Amazon Insight Guides Morocco | 200/500 variable | Medium via mirror | Publication date and print length |
| 31 | DK Morocco | 200 | High | Eyewitness structure, coverage regions, ISBN and format |

---

## 2) Place and attraction candidates (seed-ready)

Fields aligned to `places`: `id`, `name`, `category`, `region_id`, `short_description`, `tags`, `why_recommended`, `source_refs`.

| Suggested ID | Name | Category | Region/Area | Why include | Source refs |
|---|---|---|---|---|---|
| place-jemaa-el-fna | Jemaa el-Fna Square | landmark/square | medina_core | Most repeated must-see; day-night transformation; core confidence anchor | 2,3,5,6,11,13,16 |
| place-medina-marrakech | Marrakech Medina | neighborhood | medina_core | Primary orientation entity for first-timers and routing | 2,6,11 |
| place-koutoubia-mosque | Koutoubia Mosque/Minaret | landmark/religious | medina_core | High recognition icon; appears across official/editorial/tour feeds | 2,3,6,11 |
| place-bahia-palace | Bahia Palace | palace | medina_core | Consistent top attraction on official + commercial lists | 2,3,6,11,13 |
| place-el-badi-palace | El Badi Palace | palace/historic | medina_core | Core historical landmark in multiple rankings | 2,3,6,13 |
| place-ben-youssef-medrassa | Ben Youssef Madrasa | museum/religious | medina_core | Frequently cited architectural must-see | 3,5,6,19 |
| place-saadian-tombs | Saadian Tombs | historic site | medina_core | Official essentials + Routard incontournables | 2,3,6 |
| place-jardin-majorelle | Jardin Majorelle | garden | gueliz | Universal high-demand attraction; visual icon | 2,3,6,11,13,31 |
| place-yves-saint-laurent-museum | Musee Yves Saint Laurent | museum | gueliz | Strong cultural pairing with Majorelle | 3,6 |
| place-le-jardin-secret | Le Jardin Secret | garden/heritage | medina_core | Repeated as quality heritage stop | 3,5 |
| place-mouassine-museum | Museum of Mouassine | museum | medina_core | Unusual-cultural candidate (Atlas Obscura + Time Out mention) | 3,13 |
| place-dar-el-bacha | Dar El Bacha / Musee des Confluences | museum/palace | medina_core | Strong for art-history itinerary routes | 3,13 |
| place-maison-photographie | Maison de la Photographie | museum | medina_core | High-value niche museum candidate | 3 |
| place-dar-si-said | Musee Dar Si Said | museum | medina_core | Routard top list, complements craft/history layer | 6 |
| place-souks-medina | Souks (Medina) | market area | medina_core | Key practical behavior/price-negotiation context | 2,3,6,16 |
| place-mellah-market | Mellah Market | market | mellah | Food culture + local produce + practical navigation anchor | 3,16 |
| place-menara-gardens | Menara Gardens/Basin | garden | menara | Official top-5 inclusion and family-relaxation stop | 2,19 |
| place-place-des-ferblantiers | Place des Ferblantiers | square | medina_edge | Useful waypoint and nearby detail-screen node | 3 |
| place-gueliz | Gueliz district | neighborhood | gueliz | Modern district framing for first-time tourists | 2 |
| place-hivernage | Hivernage district | neighborhood | hivernage | Modern hospitality/nightlife orientation | 2 |
| place-agafay-desert | Agafay Desert | nature/day-trip | agafay | Very high marketplace demand (camel/quad/dinner) | 1,4,10,11,12 |
| place-ourika-valley | Ourika Valley | nature/day-trip | atlas_periphery | Very high day-trip volume + food/hike content | 4,10,11,12 |
| place-ouzoud-waterfalls | Ouzoud Waterfalls | nature/day-trip | azilal | Strong event/activity demand signal | 4,10 |
| place-atlas-mountains | Atlas Mountains | nature/day-trip | atlas_periphery | Core day-trip class for route cards/itineraries | 1,10,11,12,19 |
| place-lalla-takerkoust | Lalla Takerkoust | lake/day-trip | atlas_periphery | Official essential; useful family/relaxed day profile | 2 |
| place-essaouira | Essaouira | city/day-trip | atlantic_coast | Persistent day-trip and overnight extension | 1,11,12,19 |
| place-safi | Safi | city/day-trip | atlantic_coast | Official nearby city with travel-time metadata | 1,2 |
| place-oukaimeden | Oukaimeden | mountain/sport | atlas_periphery | Appears in day-trip and sport framing | 7,19 |
| place-ait-ben-haddou | Ait Ben Haddou | heritage/day-trip | south_route | Frequently bundled in multi-day tours | 7,11 |
| place-tin-mal-mosque | Tin Mal Mosque | heritage/religious | atlas_route | Unusual historical day-trip candidate | 13 |
| place-oasiria | Oasiria Water Park | family leisure | marrakech_outer | Family-leisure fallback option | 13 |

---

## 3) Restaurant and food-content candidates

### 3.1 Restaurant candidates

| Suggested ID | Name | Type | Area | Why include | Source refs |
|---|---|---|---|---|---|
| eat-la-trattoria | La Trattoria Marrakech | restaurant | gueliz/hivernage area | Time Out ranked; useful non-Moroccan option | 3 |
| eat-sahbi-sahbi | Sahbi Sahbi | restaurant | marrakech | Time Out ranked; modern local cuisine signal | 3 |
| eat-le-jardin | Le Jardin | restaurant | medina | Repeated in Time Out + Serious Eats | 3,16 |
| eat-terrasse-des-epices | Terrasse des Epices | restaurant/rooftop | medina | Strong tourist utility, central location | 3 |
| eat-naranj | Naranj | restaurant | medina | Time Out ranked | 3 |
| eat-nomad | Nomad | restaurant | medina | Highly visible in curated lists | 3 |
| eat-plus61 | Plus61 | restaurant | marrakech | Time Out ranked modern dining | 3 |
| eat-lmida | L'Mida | restaurant | medina | Chef-backed source credibility from Serious Eats | 3,16 |
| eat-zeitoun-kasbah | Zeitoun Cafe Kasbah | cafe/restaurant | kasbah | Time Out ranked practical stop | 3 |
| eat-dardar | DarDar | rooftop/restaurant | medina | Repeated rooftop dining candidate | 3 |
| eat-trou-au-mur | Le Trou au Mur | restaurant | medina | Time Out ranked; useful for curated eat tab | 3 |
| eat-azalai-urban-souk | Azalai Urban Souk | restaurant | marrakech | Time Out ranked | 3 |
| eat-cafe-kif-kif | Cafe Kif Kif | cafe | medina | Casual budget-friendly category coverage | 3 |
| eat-limoni | Limoni Marrakech | restaurant | marrakech | Tripadvisor top-rated list | 8 |
| eat-le-tanjia | Le Tanjia | restaurant | marrakech | Tripadvisor top-rated list; iconic dish naming overlap | 8 |
| eat-safran-koya | Safran by Koya | restaurant | marrakech | Tripadvisor top-rated list | 8 |
| eat-les-negociants | Les Negociants | restaurant | marrakech | Tripadvisor top-rated list | 8 |
| eat-katsura | Katsura | restaurant | marrakech | Tripadvisor top-rated list; cuisine diversity | 8 |
| eat-pasta-cosy | Pasta Cosy | restaurant | marrakech | Tripadvisor top-rated list | 8 |
| eat-sanseveria | Le Sanseveria Riad Laarouss | restaurant | medina | Tripadvisor top-rated list | 8 |
| eat-dar-marjana | Dar Marjana | restaurant | bab_doukala | Serious Eats featured traditional setting | 16 |

### 3.2 Food-item and dish candidates (for tips + phrasebook + price cards)

| Suggested ID | Item | Type | Suggested app use | Source refs |
|---|---|---|---|---|
| food-tanjia | Tanjia | dish | Eat tab + culture card + price card seed | 2,6 |
| food-harira | Harira | dish/soup | Food glossary + Ramadan etiquette context | 16 |
| food-sfenj | Sfenj | street snack | Street-food quick tips | 16 |
| food-bastila | Bastila | dish | Must-try list with context | 16 |
| food-kefta-skewers | Lamb kefta skewers | street-food | Night-market recommendations | 16 |
| food-harcha | Harcha | bread | Breakfast tips | 16 |
| food-khobz | Khobz | bread | Dining etiquette and meal context | 16 |
| food-mint-tea | Moroccan mint tea | drink | Pricing + etiquette + phrasebook | 3,16 |
| food-prune-tagine | Lamb and prune tagine | dish | Traditional dish spotlight | 16 |
| food-preserved-lemon-salad | Pea/olive/preserved lemon salad | salad | Seasonal food education card | 16 |

---

## 4) Activity and pricing signals (high leverage for `price_cards`)

Observed signals are marketplace prices and should be normalized by season, group size, and inclusion rules.

| Category | Representative activities | Observed price signal | Typical duration | Demand signal | Source refs |
|---|---|---|---|---|---|
| agafay_package | Quad + camel + dinner/show (Agafay) | ~US$29 to US$40+ | ~5 to 7h | High review and booking volume | 9,10,11,12 |
| hot_air_balloon | Balloon + breakfast + transfer | ~US$114 to US$192+ | ~4 to 5h | Very high review counts on multiple platforms | 9,10,11,12 |
| ourika_day_trip | Ourika Valley + hike/lunch variants | ~US$17 to US$40+ | ~7h | Repeated bestseller pattern | 4,10,11,12 |
| atlas_valleys | Atlas/3 Valleys + camel variants | ~US$23 to US$35+ | ~7 to 9h | Extremely high review volumes | 9,11,12 |
| merzouga_3day | Sahara/Merzouga 3-day tours | ~US$120 to US$240+ | 3 days | Top-ranked long-format product class | 4,10,11,12 |
| essaouira_day_trip | Essaouira guided trip | ~US$17+ (entry-level offers) | full day | Common short extension from Marrakech | 10,12 |
| city_half_day | Private/guide city tours (Bahia/Koutoubia/Jemaa) | ~US$25 to US$41+ | ~3 to 4h | Stable demand + clear inclusion rules needed | 11 |
| hammam_experience | Traditional hammam experiences | price varies (platform-specific) | ~2h | Repeated in top activity lists | 10 |
| street_food_tour | Night street-food local-led tours | ~US$35+ observed | evening | Strong review quality signals | 9,12 |
| quad_palmeraie | Quad biking (Palmeraie/desert edge) | generally budget-mid range | ~2 to 5h | Persistent high-demand pattern | 4,9,12 |

### Price-card implications
- Add/refresh cards for: `agafay_package`, `balloon_flight`, `ourika_day_trip`, `atlas_day_trip`, `merzouga_3day`, `city_private_tour`, `hammam_standard`.
- Add context modifiers: `private_group`, `small_group`, `hotel_pickup`, `meal_included`, `sunset_slot`, `high_season`, `ramadan_period`.
- Add explicit inclusion checklist to avoid false "overpriced" classification.

---

## 5) Events and "what's on" data (for optional Phase 2 events feed)

### 5.1 Event taxonomy extracted
- `concerts_festivals`
- `theatre_humour`
- `cirque`
- `sport`
- `famille_loisirs`
- `salon_formation`

### 5.2 Sample event records (date-stamped extraction)

| Event | City | Venue | Date/time | Price signal | Source refs |
|---|---|---|---|---|---|
| Contemporary African Art Fair | Marrakech | La Mamounia | 5-8 Feb 2026 | 150 MAD | 14 |
| Shaabana Marrakchia | Marrakech | Parc Harti | 8 Feb 2026 15:00 | 280 MAD | 14 |
| Amine Radi - La Suivette (listing) | Marrakech | (listed on Guichet slider) | Feb/Mar 2026 listing period | n/a in slider | 14 |
| Nuit des melodies du Hadra | Fes | Pavillon d'or | 7 Feb 2026 20:00 | ticket closed | 15 |
| Nacim Haddad a Meknes | Meknes | Maison de la culture Fquih Mennouni | 7 Feb 2026 20:00 | from 300 MAD | 15 |
| Sabah Zaidani a Rabat | Rabat | Theatre Mohammed V | 7 Feb 2026 20:00 | from 150 MAD | 15 |
| Les Rythmes de Chaabana | Casablanca | DoubleTree by Hilton | 8 Feb 2026 16:00 | from 410 MAD | 15 |
| HAYHA a Rabat | Rabat | Theatre Mohammed V | 8 Feb 2026 20:00 | from 150 MAD | 15 |

### Events product recommendation
- Keep events explicitly optional/online additive.
- Cache short-lived local copy with `expires_at`.
- City filter default to Marrakech + radius.

---

## 6) Practical confidence data extracted

| Topic | Extracted insight | Product usage |
|---|---|---|
| Taxi negotiation stress | Routard community recommends app-based rides (Roby, InDrive) to avoid meter disputes | `tips` + `arrival mode` + `price card` context |
| Agafay expectations | Community clarifies Agafay is rocky desert, not dune desert | set correct expectation in day-trip cards |
| Post-seismic variability | Community notes some monument access/hours can vary due restorations | `hours_exceptions` + staleness messaging |
| Water scarcity | Community eco-vigilance around water use | `culture/tips` section and responsible travel card |
| Rooftop sunset ritual | Repeated qualitative highlight for medina experience | itinerary enrichment |
| Medina sensory context | Official/editorial consistently emphasize navigation complexity and sensory load | onboarding + route hints + medina mode UX |

---

## 7) Guidebook metadata (editorial planning layer)

| Guide | Positioning | Key metadata captured | Source refs |
|---|---|---|---|
| Lonely Planet Pocket Marrakesh | short-stay city guide | 7th edition, 160 pages, ISBN 9781837584048, "1-7 day" framing, "Three Perfect Days", pull-out map | 17,18 |
| Pocket Rough Guide Marrakesh | compact practical city guide | Publication 2023-11-01, 136 pages, ISBN 9781839059544, includes Atlas/Ourika/Oukaimeden/Essaouira day trips | 19 |
| Mini Rough Guide to Marrakesh | compact newer format | Publication 2025-04-01, 144 pages | 21 |
| Guide du Routard Marrakech 2025/26 | francophone practical guide | Listing-level confirmation, edition cycle visible | 6,22 |
| Petit Fute City Trip Marrakech 2024/2025 | francophone address-heavy | Publication 2023-09-13, 192 pages | 7,23 |
| Rough Guide to Morocco (main) | broad Morocco + day-trip context | Publication 2025-01-07, 560 pages | 25 |
| Lonely Planet Morocco | multi-week country guide | 15th edition, 456 pages, ISBN 9781837584031, includes pull-out map of Marrakesh | 27,28 |
| Insight Guides Morocco | culture-heavy visual guide | Publication 2023-10-01, 328 pages, ISBN 9781839050107 | 29 |
| DK Eyewitness Morocco | visual illustrated country guide | ISBN 9780241360101, Trade Paperback; explicit Marrakech/Essaouira coverage framing | 31 |

---

## 8) Proposed schema adaptations

These are the highest-value changes based on harvested data shape.

### 8.1 New tables (recommended)

1. `activities` (content.db)
- `id` TEXT PK
- `title` TEXT
- `category` TEXT (`day_trip`, `city_tour`, `wellness`, `adventure`, `food_tour`)
- `region_id` TEXT
- `duration_min_minutes` INTEGER
- `duration_max_minutes` INTEGER
- `difficulty` TEXT nullable
- `family_friendly` INTEGER
- `pickup_available` INTEGER
- `best_time_windows` TEXT JSON
- `updated_at` TEXT

2. `activity_market_signals` (content.db)
- `id` INTEGER PK
- `activity_id` TEXT
- `provider` TEXT (`airbnb`, `gyg`, `viator`, `klook`, etc.)
- `listing_title` TEXT
- `price_min` REAL
- `price_max` REAL
- `currency` TEXT
- `rating` REAL nullable
- `review_count` INTEGER nullable
- `duration_text` TEXT nullable
- `captured_at` TEXT
- `source_url` TEXT

3. `events` (content.db or optional online cache db)
- `id` TEXT PK
- `title` TEXT
- `category` TEXT
- `city` TEXT
- `venue` TEXT nullable
- `start_at` TEXT
- `end_at` TEXT nullable
- `price_min` REAL nullable
- `price_max` REAL nullable
- `currency` TEXT
- `ticket_status` TEXT (`available`,`sold_out`,`closed`,`cancelled`)
- `source_url` TEXT
- `captured_at` TEXT
- `expires_at` TEXT

4. `source_mentions` (content.db)
- `id` INTEGER PK
- `entity_type` TEXT (`place`,`price_card`,`phrase`,`tip`,`activity`,`event`,`restaurant`)
- `entity_id` TEXT
- `source_ref` TEXT
- `source_url` TEXT
- `confidence` TEXT
- `captured_at` TEXT

5. `food_items` (content.db)
- `id` TEXT PK
- `name` TEXT
- `category` TEXT (`dish`,`drink`,`bread`,`street_food`)
- `description` TEXT
- `typical_context` TEXT
- `source_refs` TEXT JSON

### 8.2 Extensions to current tables

1. `price_cards`
- add `seasonality_tags` TEXT JSON
- add `inclusions_checklist` TEXT JSON (already conceptually present in plan)
- add `source_currency` TEXT
- add `source_observed_price_samples` TEXT JSON
- add `last_market_scan_at` TEXT

2. `places`
- add `district` TEXT nullable (normalized neighborhood name)
- add `landmark_type` TEXT nullable
- add `nearby_day_trip` INTEGER default 0
- add `source_count` INTEGER nullable (for editorial confidence)

3. `itineraries.steps`
- allow `activity_id` references in addition to `place_id`

### 8.3 Why these changes matter
- Tour/activity data does not fit cleanly inside `places` or `price_cards` alone.
- Events are date-bound and need expiry semantics.
- Provenance is now mission-critical for trust UI and stale-data rules.

---

## 9) Immediate ingestion plan (practical next move)

1. Ingest 20 high-confidence places first (`TOP 5` overlap + repeated cross-source mentions).
2. Ingest 10 activity-backed `price_cards` from section 4 with explicit inclusion rules.
3. Ingest 15 restaurant candidates with confidence tiers (`high`: multi-source, `medium`: single strong source).
4. Add 8-12 food culture cards (harira, sfenj, tanjia, bastila, mint tea, etc.).
5. Keep events in optional feed path (`events` table + short TTL), not core offline promise.

---

## 10) Raw source index (from your file)

- [1] https://visitmarrakech.com/en/
- [2] https://www.visitmorocco.com/en/travel/marrakesh
- [3] https://www.timeout.com/marrakech
- [4] https://www.myguidemarrakech.com/
- [5] https://www.lonelyplanet.com/destinations/morocco/marrakesh
- [6] https://www.routard.com/fr/guide/afrique/maroc/marrakech
- [7] https://www.petitfute.com/v48159-marrakech/
- [8] https://www.tripadvisor.com/Restaurants-g293734-Marrakech_Marrakech_Safi.html
- [9] https://www.airbnb.com/marrakesh-morocco/things-to-do
- [10] https://www.getyourguide.com/marrakesh-l208/
- [11] https://www.viator.com/Marrakech/d5408-ttd
- [12] https://www.klook.com/experiences/list/marrakech-tours-experiences/c289/
- [13] https://www.atlasobscura.com/things-to-do/marrakesh-morocco
- [14] https://guichet.com/ma-en
- [15] https://events.ma/
- [16] https://www.seriouseats.com/travel-guide-marrakesh-morocco-11700964
- [17] https://shop.lonelyplanet.com/products/pocket-marrakesh
- [18] https://www.amazon.com/Lonely-Planet-Pocket-Marrakesh-Travel/dp/178657036X
- [19] https://shop.roughguides.com/book/pocket-rough-guide-marrakesh/9781839059544
- [20] https://www.amazon.com/Pocket-Rough-Guide-Marrakesh-Guides/dp/0241306493
- [21] https://www.amazon.com/Mini-Rough-Guide-Marrakesh-Travel/dp/1835292720
- [22] https://www.fnac.com/a20562185/Collectif-Guide-du-Routard-Marrakech-2025-26
- [23] https://www.amazon.fr/Guide-Marrakech-2024-City-Petit/dp/2305099118
- [24] https://www.fnac.com/a16372388/Lonely-planet-fr-Marrakech-En-quelques-jours-7ed
- [25] https://www.amazon.com/Rough-Guide-Morocco-Travel-Guides/dp/1839059885
- [26] https://www.walmart.com/ip/Rough-Guides-Main-The-Rough-Guide-to-Morocco-Travel-Guide-with-eBook-Paperback/18929804536
- [27] https://shop.lonelyplanet.com/products/morocco
- [28] https://www.amazon.com/Lonely-Planet-Morocco-Travel-Guide/dp/1742204260
- [29] https://shop.insightguides.com/book/insight-guides-morocco/9781839050107
- [30] https://www.amazon.com/Insight-Guides-Morocco/dp/1786716372
- [31] https://dk.com/en-us/products/9780241360101-dk-morocco

---

## 11) Content Validation Snapshot (`bd-jex`, 2026-02-07)

This section records the validation audit run over `shared/content/*.json` and the immediate prep outputs for development.

### 11.1 Audit scope and checks

Validated across all current content domains:
- `activities` (18 items)
- `culture` (12 items)
- `events` (2 items)
- `glossary` (28 items)
- `itineraries` (9 items)
- `places` (49 items)
- `price_cards` (13 items)
- `tips` (32 items)

Checks performed:
- Duplicate IDs per file
- Coordinate validity and plausibility bands
- Price range consistency (`min <= max`)
- Duration consistency (`visit_min_minutes <= visit_max_minutes`)
- Required date-field presence
- Cross-reference integrity:
  - `itineraries.steps[].place_id` -> `places.id`
  - `itineraries.steps[].activity_id` -> `activities.id`
  - `tips.related_place_ids[]` -> `places.id`
  - `tips.related_price_card_ids[]` -> `price_cards.id`

### 11.2 What passed

- No duplicate IDs detected in any content file.
- No invalid latitude/longitude values (all within global valid ranges).
- No invalid price ranges in `places` or `price_cards`.
- No invalid visit-duration ranges in `places`.
- No broken itinerary or tip references.
- Required date fields present in:
  - `places.reviewed_at`
  - `places.hours_verified_at`
  - `price_cards.expected_cost_updated_at`
  - `tips.updated_at`
  - `culture.updated_at`
  - `events.captured_at`
- Category coverage present for critical v1 pricing buckets:
  - `taxi`, `hammam`, `souks`, `food`

### 11.3 Gaps identified

1. Glossary Arabic script missing:
   - `28/28` glossary items have `arabic: null`
   - Impact: phrasebook cannot fully satisfy Arabic/Latin/English display promise.
   - Action: editorial/native-language pass required.

2. Place image coverage is partial:
   - `33/49` places have no image references.
   - Current image references that do exist resolve to files on disk.
   - Action: continue image mapping/import for high-priority places.

3. Some price cards have no `context_modifiers`:
   - `3/13`: `price-hammam-local-basic`, `price-hammam-tourist-spa`, `price-food-street-harira`
   - Action: add simple, explainable modifiers where variability is meaningful.

4. Three place coordinates are outside the strict Marrakech-city bounding box but expected for day trips:
   - `place-ouzoud-waterfalls`
   - `place-essaouira`
   - `place-safi`
   - Action: no correction needed; treat as intentional out-of-city destinations.

### 11.4 Development test subset created

Created `shared/content/test/` for fast test cycles:
- `shared/content/test/places.json` (5 records from places dataset)
- `shared/content/test/price_cards.json` (3 price cards)
- `shared/content/test/glossary.json` (10 phrases)

Subset IDs:
- Places:
  - `place-jemaa-el-fna`
  - `place-koutoubia-mosque`
  - `place-bahia-palace`
  - `place-jardin-majorelle`
  - `eat-zeitoun-cafe-kasbah`
- Price cards:
  - `price-taxi-airport-marrakech-center`
  - `price-hammam-local-basic`
  - `price-souk-argan-oil`
- Glossary:
  - `phrase-salam-alaikum`
  - `phrase-la-shukran`
  - `phrase-bsh-hal`
  - `phrase-nqass-shwiya`
  - `phrase-fin`
  - `phrase-ma-fhemtch`
  - `phrase-atay-bla-skar`
  - `phrase-atqnee`
  - `phrase-ayyet-ala-lbulees`
  - `phrase-numbers-1-10`
