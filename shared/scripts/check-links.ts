#!/usr/bin/env tsx
/**
 * Internal Link Checker for Marrakech Guide content.
 *
 * Validates cross-file references so runtime UI never points at missing content.
 *
 * Usage: npm run check-links
 * Exit: 0 on success, 1 on link errors or file-load errors.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CONTENT_DIR = path.join(__dirname, '..', 'content');

const FILES = {
  places: 'places.json',
  activities: 'activities.json',
  itineraries: 'itineraries.json',
  tips: 'tips.json',
  priceCards: 'price_cards.json',
} as const;

interface BaseItem {
  id: string;
  [key: string]: unknown;
}

interface ContentFile<T extends BaseItem> {
  meta: {
    generated_at: string;
    source_document: string;
    notes: string[];
  };
  items: T[];
}

interface ItineraryStep {
  type?: string;
  place_id?: string;
  activity_id?: string;
}

interface ItineraryItem extends BaseItem {
  steps?: ItineraryStep[];
}

interface TipItem extends BaseItem {
  related_place_ids?: string[];
  related_price_card_ids?: string[];
}

interface LinkError {
  file: string;
  itemId: string;
  field: string;
  value: string;
  message: string;
}

class LinkChecker {
  private errors: LinkError[] = [];
  private fileErrors: string[] = [];

  private addError(error: LinkError): void {
    this.errors.push(error);
  }

  private addFileError(message: string): void {
    this.fileErrors.push(message);
  }

  private loadFile<T extends BaseItem>(filename: string): ContentFile<T> | null {
    const filePath = path.join(CONTENT_DIR, filename);

    if (!fs.existsSync(filePath)) {
      this.addFileError(`Missing required content file: ${filename}`);
      return null;
    }

    try {
      const raw = fs.readFileSync(filePath, 'utf-8');
      return JSON.parse(raw) as ContentFile<T>;
    } catch (err) {
      this.addFileError(
        `Failed to parse ${filename}: ${err instanceof Error ? err.message : String(err)}`
      );
      return null;
    }
  }

  private collectIds<T extends BaseItem>(items: T[]): Set<string> {
    const ids = new Set<string>();
    for (const item of items) {
      if (typeof item.id === 'string' && item.id.length > 0) {
        ids.add(item.id);
      }
    }
    return ids;
  }

  private checkItineraryLinks(
    itineraries: ItineraryItem[],
    placeIds: Set<string>,
    activityIds: Set<string>
  ): void {
    for (const itinerary of itineraries) {
      const steps = Array.isArray(itinerary.steps) ? itinerary.steps : [];

      for (let index = 0; index < steps.length; index++) {
        const step = steps[index];
        const type = step.type;

        if ((type === 'place' || type === 'meal') && step.place_id) {
          if (!placeIds.has(step.place_id)) {
            this.addError({
              file: FILES.itineraries,
              itemId: itinerary.id,
              field: `steps[${index}].place_id`,
              value: step.place_id,
              message: 'Referenced place does not exist',
            });
          }
        }

        if (type === 'activity' && step.activity_id) {
          if (!activityIds.has(step.activity_id)) {
            this.addError({
              file: FILES.itineraries,
              itemId: itinerary.id,
              field: `steps[${index}].activity_id`,
              value: step.activity_id,
              message: 'Referenced activity does not exist',
            });
          }
        }
      }
    }
  }

  private checkTipLinks(
    tips: TipItem[],
    placeIds: Set<string>,
    priceCardIds: Set<string>
  ): void {
    for (const tip of tips) {
      const relatedPlaces = Array.isArray(tip.related_place_ids)
        ? tip.related_place_ids
        : [];
      const relatedPriceCards = Array.isArray(tip.related_price_card_ids)
        ? tip.related_price_card_ids
        : [];

      for (let index = 0; index < relatedPlaces.length; index++) {
        const placeId = relatedPlaces[index];
        if (!placeIds.has(placeId)) {
          this.addError({
            file: FILES.tips,
            itemId: tip.id,
            field: `related_place_ids[${index}]`,
            value: placeId,
            message: 'Referenced place does not exist',
          });
        }
      }

      for (let index = 0; index < relatedPriceCards.length; index++) {
        const priceCardId = relatedPriceCards[index];
        if (!priceCardIds.has(priceCardId)) {
          this.addError({
            file: FILES.tips,
            itemId: tip.id,
            field: `related_price_card_ids[${index}]`,
            value: priceCardId,
            message: 'Referenced price_card does not exist',
          });
        }
      }
    }
  }

  private printResults(): void {
    if (this.fileErrors.length > 0) {
      console.log('\n❌ File loading/parsing errors:\n');
      for (const err of this.fileErrors) {
        console.log(`- ${err}`);
      }
      console.log('');
    }

    if (this.errors.length === 0 && this.fileErrors.length === 0) {
      console.log('\n✅ All internal links are valid.\n');
      return;
    }

    if (this.errors.length > 0) {
      console.log(`\n❌ Link validation failed with ${this.errors.length} error(s):\n`);
      for (const err of this.errors) {
        console.log(`${err.file}:`);
        console.log(`  id: "${err.itemId}"`);
        console.log(`  ${err.field}: "${err.value}"`);
        console.log(`    error: ${err.message}\n`);
      }
    }
  }

  public run(): number {
    console.log('🔗 Marrakech Guide Internal Link Checker\n');
    console.log(`Content directory: ${CONTENT_DIR}\n`);

    const places = this.loadFile<BaseItem>(FILES.places);
    const activities = this.loadFile<BaseItem>(FILES.activities);
    const itineraries = this.loadFile<ItineraryItem>(FILES.itineraries);
    const tips = this.loadFile<TipItem>(FILES.tips);
    const priceCards = this.loadFile<BaseItem>(FILES.priceCards);

    if (!places || !activities || !itineraries || !tips || !priceCards) {
      this.printResults();
      return 1;
    }

    const placeIds = this.collectIds(places.items);
    const activityIds = this.collectIds(activities.items);
    const priceCardIds = this.collectIds(priceCards.items);

    this.checkItineraryLinks(itineraries.items, placeIds, activityIds);
    this.checkTipLinks(tips.items, placeIds, priceCardIds);

    this.printResults();

    return this.errors.length > 0 || this.fileErrors.length > 0 ? 1 : 0;
  }
}

const checker = new LinkChecker();
process.exit(checker.run());
