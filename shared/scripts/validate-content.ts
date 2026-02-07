#!/usr/bin/env tsx
/**
 * Content Validation Script for Marrakech Guide
 *
 * Validates all JSON content files against the content schema and performs
 * additional cross-checks for data integrity.
 *
 * Usage: npm run validate
 * Exit: 0 on success, 1+ on validation errors
 */

import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CONTENT_DIR = path.join(__dirname, '..', 'content');
const SCHEMA_PATH = path.join(__dirname, '..', 'schema', 'content-schema.json');

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

interface ContentFile {
  meta: {
    generated_at: string;
    source_document: string;
    notes: string[];
  };
  items: ContentItem[];
}

// Content files to validate
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

class ContentValidator {
  private errors: ValidationError[] = [];
  private ajv: Ajv2020;
  private schema: object | null = null;

  constructor() {
    // Use Ajv2020 for draft-2020-12 schema support
    this.ajv = new Ajv2020({ allErrors: true, strict: false });
    addFormats(this.ajv);
  }

  private addError(error: ValidationError): void {
    this.errors.push(error);
  }

  private loadSchema(): boolean {
    try {
      if (!fs.existsSync(SCHEMA_PATH)) {
        this.addError({
          file: 'content-schema.json',
          message: `Schema file not found: ${SCHEMA_PATH}`,
        });
        return false;
      }
      const schemaContent = fs.readFileSync(SCHEMA_PATH, 'utf-8');
      this.schema = JSON.parse(schemaContent);
      return true;
    } catch (err) {
      this.addError({
        file: 'content-schema.json',
        message: `Failed to load schema: ${err instanceof Error ? err.message : String(err)}`,
      });
      return false;
    }
  }

  private loadContentFile(filename: string): ContentFile | null {
    const filePath = path.join(CONTENT_DIR, filename);
    try {
      if (!fs.existsSync(filePath)) {
        this.addError({
          file: filename,
          message: `Content file not found: ${filePath}`,
        });
        return null;
      }
      const content = fs.readFileSync(filePath, 'utf-8');
      return JSON.parse(content) as ContentFile;
    } catch (err) {
      this.addError({
        file: filename,
        message: `Failed to parse JSON: ${err instanceof Error ? err.message : String(err)}`,
      });
      return null;
    }
  }

  private validateSchema(filename: string, content: ContentFile): boolean {
    if (!this.schema) return false;

    const validate = this.ajv.compile(this.schema);
    const valid = validate(content);

    if (!valid && validate.errors) {
      for (const error of validate.errors) {
        const itemMatch = error.instancePath.match(/\/items\/(\d+)/);
        const itemIndex = itemMatch ? parseInt(itemMatch[1], 10) : undefined;
        const itemId = itemIndex !== undefined && content.items[itemIndex]?.id
          ? content.items[itemIndex].id
          : undefined;

        this.addError({
          file: filename,
          itemId,
          field: error.instancePath || undefined,
          message: `${error.keyword}: ${error.message}${error.params ? ` (${JSON.stringify(error.params)})` : ''}`,
        });
      }
      return false;
    }
    return true;
  }

  private validateUniqueIds(filename: string, content: ContentFile): void {
    const seenIds = new Set<string>();
    for (const item of content.items) {
      if (seenIds.has(item.id)) {
        this.addError({
          file: filename,
          itemId: item.id,
          message: `Duplicate ID found: "${item.id}"`,
        });
      }
      seenIds.add(item.id);
    }
  }

  private validateCoordinates(filename: string, content: ContentFile): void {
    for (const item of content.items) {
      if ('lat' in item && 'lng' in item) {
        const lat = item.lat as number;
        const lng = item.lng as number;

        if (typeof lat === 'number' && (lat < -90 || lat > 90)) {
          this.addError({
            file: filename,
            itemId: item.id,
            field: 'lat',
            message: `Latitude ${lat} is outside valid range [-90, 90]`,
          });
        }

        if (typeof lng === 'number' && (lng < -180 || lng > 180)) {
          this.addError({
            file: filename,
            itemId: item.id,
            field: 'lng',
            message: `Longitude ${lng} is outside valid range [-180, 180]`,
          });
        }
      }
    }
  }

  private validatePriceRanges(filename: string, content: ContentFile): void {
    const priceFields = [
      ['expected_cost_min_mad', 'expected_cost_max_mad'],
      ['fees_min_mad', 'fees_max_mad'],
      ['typical_price_min_mad', 'typical_price_max_mad'],
      ['price_min_mad', 'price_max_mad'],
    ];

    for (const item of content.items) {
      for (const [minField, maxField] of priceFields) {
        if (minField in item && maxField in item) {
          const min = item[minField] as number | undefined;
          const max = item[maxField] as number | undefined;

          if (typeof min === 'number' && typeof max === 'number' && min > max) {
            this.addError({
              file: filename,
              itemId: item.id,
              field: `${minField}/${maxField}`,
              message: `Price minimum (${min}) exceeds maximum (${max})`,
            });
          }
        }
      }
    }
  }

  private validateDurationRanges(filename: string, content: ContentFile): void {
    const durationFields = [
      ['duration_min_minutes', 'duration_max_minutes'],
      ['visit_min_minutes', 'visit_max_minutes'],
    ];

    for (const item of content.items) {
      for (const [minField, maxField] of durationFields) {
        if (minField in item && maxField in item) {
          const min = item[minField] as number | undefined;
          const max = item[maxField] as number | undefined;

          if (typeof min === 'number' && typeof max === 'number' && min > max) {
            this.addError({
              file: filename,
              itemId: item.id,
              field: `${minField}/${maxField}`,
              message: `Duration minimum (${min}) exceeds maximum (${max})`,
            });
          }
        }
      }
    }
  }

  private validateContextModifiers(filename: string, content: ContentFile): void {
    for (const item of content.items) {
      if ('context_modifiers' in item && Array.isArray(item.context_modifiers)) {
        const modifiers = item.context_modifiers as Array<{
          id: string;
          factor_min?: number;
          factor_max?: number;
        }>;

        for (const modifier of modifiers) {
          if (modifier.factor_min !== undefined && modifier.factor_max !== undefined) {
            if (modifier.factor_min > modifier.factor_max) {
              this.addError({
                file: filename,
                itemId: item.id,
                field: `context_modifiers.${modifier.id}`,
                message: `Modifier factor_min (${modifier.factor_min}) exceeds factor_max (${modifier.factor_max})`,
              });
            }
          }
        }
      }
    }
  }

  private validateFairnessMultipliers(filename: string, content: ContentFile): void {
    for (const item of content.items) {
      if ('fairness_low_multiplier' in item) {
        const low = item.fairness_low_multiplier as number;
        if (typeof low === 'number' && (low <= 0 || low >= 1)) {
          this.addError({
            file: filename,
            itemId: item.id,
            field: 'fairness_low_multiplier',
            message: `fairness_low_multiplier (${low}) must be in range (0, 1)`,
          });
        }
      }

      if ('fairness_high_multiplier' in item) {
        const high = item.fairness_high_multiplier as number;
        if (typeof high === 'number' && high < 1) {
          this.addError({
            file: filename,
            itemId: item.id,
            field: 'fairness_high_multiplier',
            message: `fairness_high_multiplier (${high}) must be >= 1`,
          });
        }
      }
    }
  }

  private printResults(): void {
    if (this.errors.length === 0) {
      console.log('\n✅ All content files validated successfully!\n');
      return;
    }

    console.log(`\n❌ Validation failed with ${this.errors.length} error(s):\n`);

    // Group errors by file
    const errorsByFile = new Map<string, ValidationError[]>();
    for (const error of this.errors) {
      const existing = errorsByFile.get(error.file) || [];
      existing.push(error);
      errorsByFile.set(error.file, existing);
    }

    for (const [file, errors] of errorsByFile) {
      console.log(`📄 ${file}:`);
      for (const error of errors) {
        const location = error.itemId
          ? error.field
            ? `  id: "${error.itemId}" → ${error.field}`
            : `  id: "${error.itemId}"`
          : error.field
            ? `  field: ${error.field}`
            : '';

        if (location) {
          console.log(location);
        }
        console.log(`    ⚠️  ${error.message}`);
      }
      console.log('');
    }
  }

  public validate(): number {
    console.log('🔍 Marrakech Guide Content Validator\n');
    console.log(`Content directory: ${CONTENT_DIR}`);
    console.log(`Schema file: ${SCHEMA_PATH}\n`);

    // Load schema
    if (!this.loadSchema()) {
      this.printResults();
      return 1;
    }
    console.log('✓ Schema loaded successfully');

    // Validate each content file
    let filesProcessed = 0;
    for (const filename of CONTENT_FILES) {
      const content = this.loadContentFile(filename);
      if (!content) {
        continue;
      }

      console.log(`\nValidating ${filename}...`);
      filesProcessed++;

      // Schema validation
      const schemaValid = this.validateSchema(filename, content);
      if (schemaValid) {
        console.log(`  ✓ Schema validation passed (${content.items.length} items)`);
      } else {
        console.log(`  ✗ Schema validation failed`);
      }

      // Cross-field validations
      this.validateUniqueIds(filename, content);
      this.validateCoordinates(filename, content);
      this.validatePriceRanges(filename, content);
      this.validateDurationRanges(filename, content);
      this.validateContextModifiers(filename, content);
      this.validateFairnessMultipliers(filename, content);
    }

    if (filesProcessed === 0) {
      this.addError({
        file: 'content/',
        message: 'No content files were found to validate',
      });
    }

    this.printResults();

    return this.errors.length > 0 ? 1 : 0;
  }
}

// Main execution
const validator = new ContentValidator();
const exitCode = validator.validate();
process.exit(exitCode);
