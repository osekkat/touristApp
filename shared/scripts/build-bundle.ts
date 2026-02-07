#!/usr/bin/env bun
/**
 * SQLite Bundle Generator for Marrakech Guide
 *
 * Transforms validated JSON content into SQLite content.db with:
 * - All content tables (places, price_cards, phrases, etc.)
 * - Prebuilt FTS5 indexes for fast search
 * - content_links table for cross-references
 *
 * Usage: npm run build-bundle (or: bun run build-bundle.ts)
 * Output: shared/output/content.db
 */

import { Database } from 'bun:sqlite';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CONTENT_DIR = path.join(__dirname, '..', 'content');
const OUTPUT_DIR = path.join(__dirname, '..', 'output');
const OUTPUT_PATH = path.join(OUTPUT_DIR, 'content.db');

interface ContentFile<T> {
  meta: {
    generated_at: string;
    source_document: string;
    notes: string[];
  };
  items: T[];
}

// Ensure output directory exists
function ensureOutputDir(): void {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }
}

// Load JSON content file
function loadContent<T>(filename: string): T[] {
  const filePath = path.join(CONTENT_DIR, filename);
  if (!fs.existsSync(filePath)) {
    console.log(`  ⚠ ${filename} not found, skipping`);
    return [];
  }
  const content = JSON.parse(fs.readFileSync(filePath, 'utf-8')) as ContentFile<T>;
  return content.items;
}

// Convert array to JSON string for storage
function toJson(value: unknown): string | null {
  if (value === undefined || value === null) return null;
  if (Array.isArray(value) && value.length === 0) return '[]';
  return JSON.stringify(value);
}

class BundleBuilder {
  private db: Database;

  constructor() {
    // Ensure output directory exists
    ensureOutputDir();

    // Remove existing database if it exists
    if (fs.existsSync(OUTPUT_PATH)) {
      fs.unlinkSync(OUTPUT_PATH);
    }

    this.db = new Database(OUTPUT_PATH);

    // Disable WAL mode for read-only distribution
    this.db.run('PRAGMA journal_mode = DELETE');
    this.db.run('PRAGMA synchronous = NORMAL');
  }

  private createTables(): void {
    console.log('\n📊 Creating database tables...');

    // Places table
    this.db.run(`
      CREATE TABLE places (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        aliases TEXT,
        region_id TEXT,
        category TEXT,
        short_description TEXT,
        long_description TEXT,
        reviewed_at TEXT,
        status TEXT DEFAULT 'open',
        confidence TEXT,
        tourist_trap_level TEXT,
        why_recommended TEXT,
        neighborhood TEXT,
        address TEXT,
        lat REAL,
        lng REAL,
        hours_text TEXT,
        hours_weekly TEXT,
        hours_verified_at TEXT,
        fees_min_mad INTEGER,
        fees_max_mad INTEGER,
        expected_cost_min_mad INTEGER,
        expected_cost_max_mad INTEGER,
        visit_min_minutes INTEGER,
        visit_max_minutes INTEGER,
        best_time_to_go TEXT,
        best_time_windows TEXT,
        tags TEXT,
        local_tips TEXT,
        scam_warnings TEXT,
        do_and_dont TEXT,
        images TEXT,
        source_refs TEXT
      )
    `);

    // Price cards table
    this.db.run(`
      CREATE TABLE price_cards (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        category TEXT,
        unit TEXT,
        volatility TEXT,
        confidence TEXT,
        expected_cost_min_mad INTEGER NOT NULL,
        expected_cost_max_mad INTEGER NOT NULL,
        expected_cost_notes TEXT,
        expected_cost_updated_at TEXT,
        provenance_note TEXT,
        what_influences_price TEXT,
        inclusions_checklist TEXT,
        negotiation_scripts TEXT,
        red_flags TEXT,
        what_to_do_instead TEXT,
        context_modifiers TEXT,
        fairness_low_multiplier REAL DEFAULT 0.75,
        fairness_high_multiplier REAL DEFAULT 1.25,
        source_refs TEXT
      )
    `);

    // Phrases (glossary) table
    this.db.run(`
      CREATE TABLE phrases (
        id TEXT PRIMARY KEY,
        category TEXT,
        arabic TEXT,
        latin TEXT NOT NULL,
        english TEXT NOT NULL,
        audio TEXT,
        verification_status TEXT
      )
    `);

    // Itineraries table
    this.db.run(`
      CREATE TABLE itineraries (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        duration TEXT,
        style TEXT,
        steps TEXT,
        source_refs TEXT
      )
    `);

    // Tips table
    this.db.run(`
      CREATE TABLE tips (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        summary TEXT NOT NULL,
        category TEXT,
        actions TEXT,
        severity TEXT,
        updated_at TEXT,
        related_place_ids TEXT,
        related_price_card_ids TEXT,
        source_refs TEXT
      )
    `);

    // Culture table
    this.db.run(`
      CREATE TABLE culture (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        summary TEXT NOT NULL,
        category TEXT,
        do_list TEXT,
        dont_list TEXT,
        updated_at TEXT,
        source_refs TEXT
      )
    `);

    // Activities table
    this.db.run(`
      CREATE TABLE activities (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        category TEXT,
        region_id TEXT,
        duration_min_minutes INTEGER,
        duration_max_minutes INTEGER,
        pickup_available INTEGER,
        typical_price_min_mad INTEGER,
        typical_price_max_mad INTEGER,
        rating_signal TEXT,
        review_count_signal TEXT,
        best_time_windows TEXT,
        tags TEXT,
        notes TEXT,
        source_refs TEXT
      )
    `);

    // Events table
    this.db.run(`
      CREATE TABLE events (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        category TEXT,
        city TEXT,
        venue TEXT,
        start_at TEXT,
        end_at TEXT,
        price_min_mad INTEGER,
        price_max_mad INTEGER,
        ticket_status TEXT,
        captured_at TEXT,
        source_url TEXT,
        source_refs TEXT
      )
    `);

    // Content links table
    this.db.run(`
      CREATE TABLE content_links (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        from_type TEXT NOT NULL,
        from_id TEXT NOT NULL,
        to_type TEXT NOT NULL,
        to_id TEXT NOT NULL,
        link_kind TEXT
      )
    `);

    // Create indexes
    this.db.run('CREATE INDEX idx_places_category ON places(category)');
    this.db.run('CREATE INDEX idx_places_region ON places(region_id)');
    this.db.run('CREATE INDEX idx_price_cards_category ON price_cards(category)');
    this.db.run('CREATE INDEX idx_phrases_category ON phrases(category)');
    this.db.run('CREATE INDEX idx_tips_category ON tips(category)');
    this.db.run('CREATE INDEX idx_activities_category ON activities(category)');
    this.db.run('CREATE INDEX idx_content_links_from ON content_links(from_type, from_id)');
    this.db.run('CREATE INDEX idx_content_links_to ON content_links(to_type, to_id)');

    console.log('  ✓ Tables created');
  }

  private createFTSTables(): void {
    console.log('\n🔍 Creating FTS5 search indexes...');

    // Places FTS
    this.db.run(`
      CREATE VIRTUAL TABLE places_fts USING fts5(
        id, name, aliases, short_description, tags,
        content='places', content_rowid='rowid'
      )
    `);

    // Price cards FTS
    this.db.run(`
      CREATE VIRTUAL TABLE price_cards_fts USING fts5(
        id, title, category,
        content='price_cards', content_rowid='rowid'
      )
    `);

    // Phrases FTS
    this.db.run(`
      CREATE VIRTUAL TABLE phrases_fts USING fts5(
        id, arabic, latin, english,
        content='phrases', content_rowid='rowid'
      )
    `);

    // Tips FTS
    this.db.run(`
      CREATE VIRTUAL TABLE tips_fts USING fts5(
        id, title, summary,
        content='tips', content_rowid='rowid'
      )
    `);

    console.log('  ✓ FTS5 indexes created');
  }

  private populatePlaces(): number {
    interface Place {
      id: string;
      name: string;
      aliases?: string[];
      region_id?: string;
      category?: string;
      short_description?: string;
      long_description?: string;
      reviewed_at?: string;
      status?: string;
      confidence?: string;
      tourist_trap_level?: string;
      why_recommended?: string[];
      neighborhood?: string;
      address?: string;
      lat?: number;
      lng?: number;
      hours_text?: string;
      hours_weekly?: string[];
      hours_verified_at?: string;
      fees_min_mad?: number;
      fees_max_mad?: number;
      expected_cost_min_mad?: number;
      expected_cost_max_mad?: number;
      visit_min_minutes?: number;
      visit_max_minutes?: number;
      best_time_to_go?: string;
      best_time_windows?: string[];
      tags?: string[];
      local_tips?: string[];
      scam_warnings?: string[];
      do_and_dont?: string[];
      images?: string[];
      source_refs?: number[];
    }

    const places = loadContent<Place>('places.json');
    if (places.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO places (
        id, name, aliases, region_id, category, short_description, long_description,
        reviewed_at, status, confidence, tourist_trap_level, why_recommended,
        neighborhood, address, lat, lng, hours_text, hours_weekly, hours_verified_at,
        fees_min_mad, fees_max_mad, expected_cost_min_mad, expected_cost_max_mad,
        visit_min_minutes, visit_max_minutes, best_time_to_go, best_time_windows,
        tags, local_tips, scam_warnings, do_and_dont, images, source_refs
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const p of places) {
      stmt.run(
        p.id, p.name, toJson(p.aliases), p.region_id, p.category,
        p.short_description, p.long_description, p.reviewed_at, p.status,
        p.confidence, p.tourist_trap_level, toJson(p.why_recommended),
        p.neighborhood, p.address, p.lat, p.lng, p.hours_text,
        toJson(p.hours_weekly), p.hours_verified_at, p.fees_min_mad, p.fees_max_mad,
        p.expected_cost_min_mad, p.expected_cost_max_mad, p.visit_min_minutes,
        p.visit_max_minutes, p.best_time_to_go, toJson(p.best_time_windows),
        toJson(p.tags), toJson(p.local_tips), toJson(p.scam_warnings),
        toJson(p.do_and_dont), toJson(p.images), toJson(p.source_refs)
      );
    }

    return places.length;
  }

  private populatePriceCards(): number {
    interface PriceCard {
      id: string;
      title: string;
      category?: string;
      unit?: string;
      volatility?: string;
      confidence?: string;
      expected_cost_min_mad: number;
      expected_cost_max_mad: number;
      expected_cost_notes?: string;
      expected_cost_updated_at?: string;
      provenance_note?: string;
      what_influences_price?: string[];
      inclusions_checklist?: string[];
      negotiation_scripts?: object[];
      red_flags?: string[];
      what_to_do_instead?: string[];
      context_modifiers?: object[];
      fairness_low_multiplier?: number;
      fairness_high_multiplier?: number;
      source_refs?: number[];
    }

    const priceCards = loadContent<PriceCard>('price_cards.json');
    if (priceCards.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO price_cards (
        id, title, category, unit, volatility, confidence,
        expected_cost_min_mad, expected_cost_max_mad, expected_cost_notes,
        expected_cost_updated_at, provenance_note, what_influences_price,
        inclusions_checklist, negotiation_scripts, red_flags, what_to_do_instead,
        context_modifiers, fairness_low_multiplier, fairness_high_multiplier, source_refs
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const pc of priceCards) {
      stmt.run(
        pc.id, pc.title, pc.category, pc.unit, pc.volatility, pc.confidence,
        pc.expected_cost_min_mad, pc.expected_cost_max_mad, pc.expected_cost_notes,
        pc.expected_cost_updated_at, pc.provenance_note, toJson(pc.what_influences_price),
        toJson(pc.inclusions_checklist), toJson(pc.negotiation_scripts), toJson(pc.red_flags),
        toJson(pc.what_to_do_instead), toJson(pc.context_modifiers),
        pc.fairness_low_multiplier ?? 0.75, pc.fairness_high_multiplier ?? 1.25,
        toJson(pc.source_refs)
      );
    }

    return priceCards.length;
  }

  private populatePhrases(): number {
    interface Phrase {
      id: string;
      category?: string;
      arabic?: string;
      latin: string;
      english: string;
      audio?: string;
      verification_status?: string;
    }

    const phrases = loadContent<Phrase>('glossary.json');
    if (phrases.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO phrases (id, category, arabic, latin, english, audio, verification_status)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `);

    for (const p of phrases) {
      stmt.run(p.id, p.category, p.arabic, p.latin, p.english, p.audio, p.verification_status);
    }

    return phrases.length;
  }

  private populateItineraries(): number {
    interface Itinerary {
      id: string;
      title: string;
      duration?: string;
      style?: string;
      steps?: object[];
      source_refs?: number[];
    }

    const itineraries = loadContent<Itinerary>('itineraries.json');
    if (itineraries.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO itineraries (id, title, duration, style, steps, source_refs)
      VALUES (?, ?, ?, ?, ?, ?)
    `);

    for (const i of itineraries) {
      stmt.run(i.id, i.title, i.duration, i.style, toJson(i.steps), toJson(i.source_refs));
    }

    return itineraries.length;
  }

  private populateTips(): number {
    interface Tip {
      id: string;
      title: string;
      summary: string;
      category?: string;
      actions?: string[];
      severity?: string;
      updated_at?: string;
      related_place_ids?: string[];
      related_price_card_ids?: string[];
      source_refs?: number[];
    }

    const tips = loadContent<Tip>('tips.json');
    if (tips.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO tips (
        id, title, summary, category, actions, severity, updated_at,
        related_place_ids, related_price_card_ids, source_refs
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const t of tips) {
      stmt.run(
        t.id, t.title, t.summary, t.category, toJson(t.actions), t.severity,
        t.updated_at, toJson(t.related_place_ids), toJson(t.related_price_card_ids),
        toJson(t.source_refs)
      );
    }

    return tips.length;
  }

  private populateCulture(): number {
    interface Culture {
      id: string;
      title: string;
      summary: string;
      category?: string;
      do?: string[];
      dont?: string[];
      updated_at?: string;
      source_refs?: number[];
    }

    const culture = loadContent<Culture>('culture.json');
    if (culture.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO culture (id, title, summary, category, do_list, dont_list, updated_at, source_refs)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const c of culture) {
      stmt.run(
        c.id, c.title, c.summary, c.category, toJson(c.do), toJson(c.dont),
        c.updated_at, toJson(c.source_refs)
      );
    }

    return culture.length;
  }

  private populateActivities(): number {
    interface Activity {
      id: string;
      title: string;
      category?: string;
      region_id?: string;
      duration_min_minutes?: number;
      duration_max_minutes?: number;
      pickup_available?: boolean;
      typical_price_min_mad?: number;
      typical_price_max_mad?: number;
      rating_signal?: string;
      review_count_signal?: string;
      best_time_windows?: string[];
      tags?: string[];
      notes?: string;
      source_refs?: number[];
    }

    const activities = loadContent<Activity>('activities.json');
    if (activities.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO activities (
        id, title, category, region_id, duration_min_minutes, duration_max_minutes,
        pickup_available, typical_price_min_mad, typical_price_max_mad,
        rating_signal, review_count_signal, best_time_windows, tags, notes, source_refs
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const a of activities) {
      stmt.run(
        a.id, a.title, a.category, a.region_id, a.duration_min_minutes, a.duration_max_minutes,
        a.pickup_available ? 1 : 0, a.typical_price_min_mad, a.typical_price_max_mad,
        a.rating_signal, a.review_count_signal, toJson(a.best_time_windows),
        toJson(a.tags), a.notes, toJson(a.source_refs)
      );
    }

    return activities.length;
  }

  private populateEvents(): number {
    interface Event {
      id: string;
      title: string;
      category?: string;
      city?: string;
      venue?: string;
      start_at?: string;
      end_at?: string;
      price_min_mad?: number;
      price_max_mad?: number;
      ticket_status?: string;
      captured_at?: string;
      source_url?: string;
      source_refs?: number[];
    }

    const events = loadContent<Event>('events.json');
    if (events.length === 0) return 0;

    const stmt = this.db.prepare(`
      INSERT INTO events (
        id, title, category, city, venue, start_at, end_at,
        price_min_mad, price_max_mad, ticket_status, captured_at, source_url, source_refs
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    for (const e of events) {
      stmt.run(
        e.id, e.title, e.category, e.city, e.venue, e.start_at, e.end_at,
        e.price_min_mad, e.price_max_mad, e.ticket_status, e.captured_at,
        e.source_url, toJson(e.source_refs)
      );
    }

    return events.length;
  }

  private populateContentLinks(): number {
    interface Tip {
      id: string;
      related_place_ids?: string[];
      related_price_card_ids?: string[];
    }

    const tips = loadContent<Tip>('tips.json');
    let linkCount = 0;

    const stmt = this.db.prepare(`
      INSERT INTO content_links (from_type, from_id, to_type, to_id, link_kind)
      VALUES (?, ?, ?, ?, ?)
    `);

    for (const tip of tips) {
      if (tip.related_place_ids) {
        for (const placeId of tip.related_place_ids) {
          stmt.run('tip', tip.id, 'place', placeId, 'related_place');
          linkCount++;
        }
      }
      if (tip.related_price_card_ids) {
        for (const priceCardId of tip.related_price_card_ids) {
          stmt.run('tip', tip.id, 'price_card', priceCardId, 'related_price');
          linkCount++;
        }
      }
    }

    return linkCount;
  }

  private rebuildFTSIndexes(): void {
    console.log('\n📝 Populating FTS indexes...');

    // Rebuild FTS indexes from content tables
    this.db.run("INSERT INTO places_fts(places_fts) VALUES('rebuild')");
    this.db.run("INSERT INTO price_cards_fts(price_cards_fts) VALUES('rebuild')");
    this.db.run("INSERT INTO phrases_fts(phrases_fts) VALUES('rebuild')");
    this.db.run("INSERT INTO tips_fts(tips_fts) VALUES('rebuild')");

    console.log('  ✓ FTS indexes populated');
  }

  private verifyDatabase(): void {
    console.log('\n🔬 Verifying database...');

    // Test a sample FTS query
    const placesResult = this.db.query("SELECT COUNT(*) as count FROM places").get() as { count: number };
    const priceCardsResult = this.db.query("SELECT COUNT(*) as count FROM price_cards").get() as { count: number };
    const phrasesResult = this.db.query("SELECT COUNT(*) as count FROM phrases").get() as { count: number };

    console.log(`  ✓ places: ${placesResult.count} rows`);
    console.log(`  ✓ price_cards: ${priceCardsResult.count} rows`);
    console.log(`  ✓ phrases: ${phrasesResult.count} rows`);

    // Test FTS search
    const searchResult = this.db.query("SELECT id FROM places_fts WHERE places_fts MATCH 'palace' LIMIT 3").all();
    console.log(`  ✓ FTS search test: ${searchResult.length} results for 'palace'`);
  }

  private optimizeDatabase(): void {
    console.log('\n⚡ Optimizing database...');

    this.db.run('VACUUM');
    this.db.run('ANALYZE');

    console.log('  ✓ Database optimized');
  }

  public build(): number {
    console.log('🏗️  Marrakech Guide Bundle Builder\n');
    console.log(`Content directory: ${CONTENT_DIR}`);
    console.log(`Output: ${OUTPUT_PATH}`);

    ensureOutputDir();
    this.createTables();
    this.createFTSTables();

    console.log('\n📦 Populating content tables...');

    const placesCount = this.populatePlaces();
    console.log(`  ✓ places: ${placesCount} items`);

    const priceCardsCount = this.populatePriceCards();
    console.log(`  ✓ price_cards: ${priceCardsCount} items`);

    const phrasesCount = this.populatePhrases();
    console.log(`  ✓ phrases: ${phrasesCount} items`);

    const itinerariesCount = this.populateItineraries();
    console.log(`  ✓ itineraries: ${itinerariesCount} items`);

    const tipsCount = this.populateTips();
    console.log(`  ✓ tips: ${tipsCount} items`);

    const cultureCount = this.populateCulture();
    console.log(`  ✓ culture: ${cultureCount} items`);

    const activitiesCount = this.populateActivities();
    console.log(`  ✓ activities: ${activitiesCount} items`);

    const eventsCount = this.populateEvents();
    console.log(`  ✓ events: ${eventsCount} items`);

    const linksCount = this.populateContentLinks();
    console.log(`  ✓ content_links: ${linksCount} links`);

    this.rebuildFTSIndexes();
    this.verifyDatabase();
    this.optimizeDatabase();

    const totalItems = placesCount + priceCardsCount + phrasesCount + itinerariesCount +
                       tipsCount + cultureCount + activitiesCount + eventsCount;

    // Get file size
    const stats = fs.statSync(OUTPUT_PATH);
    const sizeKb = Math.round(stats.size / 1024);

    console.log(`\n✅ Bundle created successfully!`);
    console.log(`   Total items: ${totalItems}`);
    console.log(`   Database size: ${sizeKb} KB`);
    console.log(`   Output: ${OUTPUT_PATH}\n`);

    this.db.close();
    return 0;
  }
}

// Main execution
const builder = new BundleBuilder();
const exitCode = builder.build();
process.exit(exitCode);
