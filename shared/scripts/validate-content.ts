#!/usr/bin/env tsx
/**
 * Content Validation Script
 * Validates all JSON content files against the schema and performs
 * additional cross-record checks.
 */

import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const CONTENT_DIR = join(__dirname, '..', 'content');
const SCHEMA_PATH = join(__dirname, '..', 'schema', 'content-schema.json');

const CONTENT_FILES = [
  'places.json',
  'price_cards.json',
  'glossary.json',
  'itineraries.json',
  'tips.json',
  'culture.json',
  'activities.json',
  'events.json',
];

interface ValidationError {
  file: string;
  itemId?: string;
  field?: string;
  message: string;
}

interface ContentItem {
  id: string;
  [key: string]: unknown;
}

type SchemaValidationError = {
  instancePath?: string;
  keyword: string;
  message?: string;
};

type ValidatorFn = ((data: unknown) => boolean) & {
  errors?: SchemaValidationError[];
};

type AjvLike = {
  compile: (schema: object) => ValidatorFn;
};

class ContentValidator {
  private errors: ValidationError[] = [];
  private ajv: AjvLike;
  private schema: object;
  private validateFn: ValidatorFn;

  constructor() {
    const AjvCtor = Ajv2020 as unknown as new (options: {
      allErrors: boolean;
      strict: boolean;
    }) => AjvLike;
    this.ajv = new AjvCtor({ allErrors: true, strict: false });

    const addFormatsFn = addFormats as unknown as (ajv: AjvLike) => void;
    addFormatsFn(this.ajv);

    if (!existsSync(SCHEMA_PATH)) {
      throw new Error('Schema file not found: ' + SCHEMA_PATH);
    }
    try {
      this.schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf-8'));
    } catch (e) {
      throw new Error('Schema file contains invalid JSON: ' + String(e));
    }
    this.validateFn = this.ajv.compile(this.schema);
  }

  private addError(error: ValidationError): void {
    this.errors.push(error);
  }

  private addScopedError(
    file: string,
    itemId: string | undefined,
    field: string,
    message: string
  ): void {
    const error: ValidationError = { file, field, message };
    if (itemId !== undefined) {
      error.itemId = itemId;
    }
    this.addError(error);
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  private isNonEmptyTrimmedString(value: unknown): value is string {
    return typeof value === 'string' && value.length > 0 && value === value.trim();
  }

  private getValidItemId(item: ContentItem): string | undefined {
    return this.isNonEmptyTrimmedString(item.id) ? item.id : undefined;
  }

  private normalizeItems(file: string, items: unknown[]): ContentItem[] {
    const normalized: ContentItem[] = [];

    for (let index = 0; index < items.length; index++) {
      const item = items[index];
      if (!this.isRecord(item)) {
        this.addError({
          file,
          field: `/items/${index}`,
          message: 'Item must be an object',
        });
        continue;
      }
      normalized.push(item as ContentItem);
    }

    return normalized;
  }

  private validateSchema(file: string, data: unknown): boolean {
    const valid = this.validateFn(data);

    if (!valid && this.validateFn.errors) {
      for (const e of this.validateFn.errors) {
        const path = e.instancePath || '/';
        const itemMatch = path.match(/\/items\/(\d+)/);
        let itemId: string | undefined;

        if (itemMatch && data && typeof data === 'object' && 'items' in data) {
          const idxRaw = itemMatch[1];
          if (idxRaw !== undefined) {
            const idx = Number.parseInt(idxRaw, 10);
            if (!Number.isNaN(idx)) {
              const items = (data as { items?: Array<{ id?: unknown }> }).items;
              const candidateId = items?.[idx]?.id;
              if (this.isNonEmptyTrimmedString(candidateId)) {
                itemId = candidateId;
              }
            }
          }
        }

        const validationError: ValidationError = {
          file,
          field: path,
          message: e.keyword + ': ' + (e.message ?? 'validation failed'),
        };
        if (itemId !== undefined) {
          validationError.itemId = itemId;
        }
        this.addError(validationError);
      }
      return false;
    }
    return true;
  }

  private validateUniqueIds(file: string, items: ContentItem[]): void {
    const seenIds = new Set<string>();
    for (const item of items) {
      if (!this.isNonEmptyTrimmedString(item.id)) {
        this.addError({ file, message: 'Item missing required trimmed string "id" field' });
        continue;
      }
      if (seenIds.has(item.id)) {
        this.addError({ file, itemId: item.id, message: 'Duplicate ID: "' + item.id + '"' });
      }
      seenIds.add(item.id);
    }
  }

  private validateCoordinates(file: string, items: ContentItem[]): void {
    for (const item of items) {
      const itemId = this.getValidItemId(item);
      const lat = item.lat as number | undefined;
      const lng = item.lng as number | undefined;

      if (lat !== undefined && (typeof lat !== 'number' || lat < -90 || lat > 90)) {
        this.addScopedError(file, itemId, 'lat', 'Invalid latitude: ' + lat);
      }
      if (lng !== undefined && (typeof lng !== 'number' || lng < -180 || lng > 180)) {
        this.addScopedError(file, itemId, 'lng', 'Invalid longitude: ' + lng);
      }
    }
  }

  private validatePriceRanges(file: string, items: ContentItem[]): void {
    const fields: Array<[string, string]> = [
      ['fees_min_mad', 'fees_max_mad'],
      ['expected_cost_min_mad', 'expected_cost_max_mad'],
      ['typical_price_min_mad', 'typical_price_max_mad'],
      ['price_min_mad', 'price_max_mad'],
    ];

    for (const item of items) {
      const itemId = this.getValidItemId(item);
      for (const [minF, maxF] of fields) {
        const min = item[minF] as number | undefined;
        const max = item[maxF] as number | undefined;
        if (typeof min === 'number' && typeof max === 'number' && min > max) {
          this.addScopedError(
            file,
            itemId,
            minF + '/' + maxF,
            'Price range invalid: min (' + min + ') > max (' + max + ')'
          );
        }
      }
    }
  }

  private validateDurationRanges(file: string, items: ContentItem[]): void {
    const fields: Array<[string, string]> = [
      ['visit_min_minutes', 'visit_max_minutes'],
      ['duration_min_minutes', 'duration_max_minutes'],
    ];

    for (const item of items) {
      const itemId = this.getValidItemId(item);
      for (const [minF, maxF] of fields) {
        const min = item[minF] as number | undefined;
        const max = item[maxF] as number | undefined;
        if (typeof min === 'number' && typeof max === 'number' && min > max) {
          this.addScopedError(
            file,
            itemId,
            minF + '/' + maxF,
            'Duration invalid: min (' + min + ') > max (' + max + ')'
          );
        }
      }
    }
  }

  private validateContextModifiers(file: string, items: ContentItem[]): void {
    for (const item of items) {
      const itemId = this.getValidItemId(item);
      if (!Array.isArray(item.context_modifiers)) {
        continue;
      }

      const modifiers = item.context_modifiers as unknown[];
      const seenModifierIds = new Set<string>();
      for (let index = 0; index < modifiers.length; index++) {
        const modifier = modifiers[index];
        if (!modifier || typeof modifier !== 'object') {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'Context modifier must be an object'
          );
          continue;
        }

        const candidate = modifier as {
          id?: unknown;
          label?: unknown;
          factor_min?: unknown;
          factor_max?: unknown;
          add_min?: unknown;
          add_max?: unknown;
        };
        const factorMin = candidate.factor_min;
        const factorMax = candidate.factor_max;
        const addMin = candidate.add_min;
        const addMax = candidate.add_max;
        const modifierId = candidate.id;
        const modifierLabel = candidate.label;

        if (!this.isNonEmptyTrimmedString(modifierId)) {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].id`,
            'Context modifier id must be a non-empty trimmed string'
          );
        }
        if (!this.isNonEmptyTrimmedString(modifierLabel)) {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].label`,
            'Context modifier label must be a non-empty trimmed string'
          );
        }

        if (this.isNonEmptyTrimmedString(modifierId)) {
          if (seenModifierIds.has(modifierId)) {
            this.addScopedError(
              file,
              itemId,
              `context_modifiers[${index}].id`,
              'Duplicate context modifier id: ' + modifierId
            );
          } else {
            seenModifierIds.add(modifierId);
          }
        }

        if (factorMin !== undefined && typeof factorMin !== 'number') {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].factor_min`,
            'factor_min must be a number'
          );
        }
        if (factorMax !== undefined && typeof factorMax !== 'number') {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].factor_max`,
            'factor_max must be a number'
          );
        }
        if (addMin !== undefined && typeof addMin !== 'number') {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].add_min`,
            'add_min must be a number'
          );
        }
        if (addMax !== undefined && typeof addMax !== 'number') {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}].add_max`,
            'add_max must be a number'
          );
        }

        const factorMinProvided = factorMin !== undefined;
        const factorMaxProvided = factorMax !== undefined;
        const addMinProvided = addMin !== undefined;
        const addMaxProvided = addMax !== undefined;

        if (factorMinProvided !== factorMaxProvided) {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'factor_min and factor_max must be provided together'
          );
        }
        if (addMinProvided !== addMaxProvided) {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'add_min and add_max must be provided together'
          );
        }

        const hasFactorPair = typeof factorMin === 'number' && typeof factorMax === 'number';
        const hasAddPair = typeof addMin === 'number' && typeof addMax === 'number';
        if (!hasFactorPair && !hasAddPair) {
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'Context modifier must define either factor_min/factor_max or add_min/add_max'
          );
        }

        if (typeof factorMin === 'number' && typeof factorMax === 'number' && factorMin > factorMax) {
          const suffix = typeof candidate.id === 'string' ? ` (${candidate.id})` : '';
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'Modifier range invalid: factor_min (' + factorMin + ') > factor_max (' + factorMax + ')' + suffix
          );
        }
        if (typeof addMin === 'number' && typeof addMax === 'number' && addMin > addMax) {
          const suffix = typeof candidate.id === 'string' ? ` (${candidate.id})` : '';
          this.addScopedError(
            file,
            itemId,
            `context_modifiers[${index}]`,
            'Modifier range invalid: add_min (' + addMin + ') > add_max (' + addMax + ')' + suffix
          );
        }
      }
    }
  }

  private validateFairnessMultipliers(file: string, items: ContentItem[]): void {
    for (const item of items) {
      const itemId = this.getValidItemId(item);
      const low = item.fairness_low_multiplier;
      const high = item.fairness_high_multiplier;

      if (low !== undefined && (typeof low !== 'number' || low <= 0 || low >= 1)) {
        this.addScopedError(
          file,
          itemId,
          'fairness_low_multiplier',
          'fairness_low_multiplier must be in range (0, 1)'
        );
      }
      if (high !== undefined && (typeof high !== 'number' || high < 1)) {
        this.addScopedError(
          file,
          itemId,
          'fairness_high_multiplier',
          'fairness_high_multiplier must be >= 1'
        );
      }
      if (typeof low === 'number' && typeof high === 'number' && low >= high) {
        this.addScopedError(
          file,
          itemId,
          'fairness_low_multiplier/fairness_high_multiplier',
          'fairness_low_multiplier must be lower than fairness_high_multiplier'
        );
      }
    }
  }

  private validateMeta(file: string, data: Record<string, unknown>): void {
    const meta = data.meta;
    if (!this.isRecord(meta)) {
      this.addError({ file, field: 'meta', message: 'Missing or invalid "meta" object' });
      return;
    }

    if (!this.isNonEmptyTrimmedString(meta.generated_at)) {
      this.addError({
        file,
        field: 'meta.generated_at',
        message: 'meta.generated_at must be a non-empty trimmed string',
      });
    }

    if (!this.isNonEmptyTrimmedString(meta.source_document)) {
      this.addError({
        file,
        field: 'meta.source_document',
        message: 'meta.source_document must be a non-empty trimmed string',
      });
    }

    const notes = meta.notes;
    if (!Array.isArray(notes)) {
      this.addError({
        file,
        field: 'meta.notes',
        message: 'meta.notes must be an array of strings',
      });
      return;
    }

    for (let index = 0; index < notes.length; index++) {
      if (!this.isNonEmptyTrimmedString(notes[index])) {
        this.addError({
          file,
          field: `meta.notes[${index}]`,
          message: 'meta.notes values must be non-empty trimmed strings',
        });
      }
    }
  }

  private validateContentFile(fileName: string): void {
    const filePath = join(CONTENT_DIR, fileName);

    if (!existsSync(filePath)) {
      this.addError({ file: fileName, message: 'Content file not found' });
      return;
    }

    let data: unknown;
    try {
      data = JSON.parse(readFileSync(filePath, 'utf-8'));
    } catch (e) {
      this.addError({ file: fileName, message: 'Invalid JSON: ' + String(e) });
      return;
    }

    this.validateSchema(fileName, data);

    if (!this.isRecord(data)) {
      this.addError({ file: fileName, message: 'Root JSON value must be an object' });
      return;
    }

    this.validateMeta(fileName, data);

    const itemsRaw = (data as { items?: unknown }).items;
    if (!Array.isArray(itemsRaw)) {
      this.addError({ file: fileName, message: 'Missing or invalid "items" array' });
      return;
    }

    const items = this.normalizeItems(fileName, itemsRaw);
    this.validateUniqueIds(fileName, items);
    this.validateCoordinates(fileName, items);
    this.validatePriceRanges(fileName, items);
    this.validateDurationRanges(fileName, items);
    this.validateContextModifiers(fileName, items);
    this.validateFairnessMultipliers(fileName, items);
  }

  public validate(): boolean {
    console.log('Validating content files...\n');

    if (!existsSync(CONTENT_DIR)) {
      console.error('Content directory not found: ' + CONTENT_DIR);
      return false;
    }

    for (const file of CONTENT_FILES) {
      console.log('  Checking ' + file + '...');
      this.validateContentFile(file);
    }

    console.log('');

    if (this.errors.length === 0) {
      console.log('All validations passed.');
      return true;
    }

    console.error('Found ' + this.errors.length + ' validation error(s):\n');

    const byFile = new Map<string, ValidationError[]>();
    for (const err of this.errors) {
      const list = byFile.get(err.file) || [];
      list.push(err);
      byFile.set(err.file, list);
    }

    for (const [file, errs] of byFile) {
      console.error('file: ' + file);
      for (const err of errs) {
        if (err.itemId) console.error('  id: "' + err.itemId + '"');
        if (err.field) console.error('    field: ' + err.field);
        console.error('    error: ' + err.message + '\n');
      }
    }

    return false;
  }
}

try {
  const validator = new ContentValidator();
  const success = validator.validate();
  process.exit(success ? 0 : 1);
} catch (err) {
  const message = err instanceof Error ? err.message : String(err);
  console.error('Validation startup failed: ' + message);
  process.exit(1);
}
