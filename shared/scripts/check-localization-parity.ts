#!/usr/bin/env tsx
/**
 * Localization Parity Check
 *
 * Validates that iOS and Android localization files have matching string keys.
 * Detects missing translations before they cause runtime issues.
 *
 * Usage: npm run check-l10n
 */

import { readFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '../..');

// File paths
const ANDROID_STRINGS = resolve(ROOT, 'android/app/src/main/res/values/strings.xml');
const ANDROID_STRINGS_FR = resolve(ROOT, 'android/app/src/main/res/values-fr/strings.xml');
const IOS_STRINGS = resolve(ROOT, 'ios/MarrakechGuide/MarrakechGuide/Resources/Localizable.xcstrings');

interface LocalizationReport {
  platform: string;
  keyCount: number;
  keys: Set<string>;
}

interface ParityResult {
  passed: boolean;
  android: LocalizationReport;
  ios: LocalizationReport;
  missingInIos: string[];
  missingInAndroid: string[];
  androidFrMissing: string[];
  iosFrMissing: string[];
}

/**
 * Generate all possible iOS key variants for an Android key.
 * Returns multiple possibilities since naming conventions vary.
 */
function androidKeyToIosVariants(androidKey: string): string[] {
  const variants: string[] = [];
  const parts = androidKey.split('_');

  // Explicit mappings for known keys with unusual patterns
  const explicitMappings: Record<string, string> = {
    'app_name': 'app_name',
    'home_quick_actions': 'home.quickActions',
    'home_offline_ready': 'home.offlineReady',
    'home_offline_not_ready': 'home.offlineNotReady',
    'my_day_pace_relaxed': 'myDay.pace.relaxed',
    'my_day_pace_standard': 'myDay.pace.standard',
    'my_day_pace_active': 'myDay.pace.active',
    'my_day_budget_low': 'myDay.budget.low',
    'my_day_budget_mid': 'myDay.budget.mid',
    'my_day_budget_high': 'myDay.budget.high',
    'onboarding_offline_title': 'onboarding.offline.title',
    'onboarding_offline_subtitle': 'onboarding.offline.subtitle',
    'onboarding_downloads_title': 'onboarding.downloads.title',
    'onboarding_downloads_subtitle': 'onboarding.downloads.subtitle',
    'onboarding_readiness_title': 'onboarding.readiness.title',
    'onboarding_readiness_verifying': 'onboarding.readiness.verifying',
    'onboarding_readiness_complete': 'onboarding.readiness.complete',
    'onboarding_demo_title': 'onboarding.demo.title',
    'onboarding_demo_subtitle': 'onboarding.demo.subtitle',
    'onboarding_privacy_title': 'onboarding.privacy.title',
    'onboarding_privacy_subtitle': 'onboarding.privacy.subtitle',
  };

  if (explicitMappings[androidKey]) {
    return [explicitMappings[androidKey]];
  }

  // Common prefixes that become dot-separated
  const prefixes = [
    'a11y', 'nav', 'home', 'explore', 'place', 'prices', 'quote', 'eat',
    'phrasebook', 'search', 'more', 'settings', 'diagnostics', 'privacy',
    'arrival', 'currency', 'onboarding', 'action', 'state', 'error',
    'dialog', 'time', 'distance', 'route',
  ];

  // Compound prefixes that merge into camelCase
  const compoundPrefixes: Record<string, string> = {
    'home_base': 'homeBase',
    'home_action': 'home.action',
    'home_greeting': 'home.greeting',
    'my_day': 'myDay',
    'quote_result': 'quote.result',
    'eat_cuisine': 'eat.cuisine',
    'eat_price': 'eat.price',
    'dialog_clear_saved': 'dialog.clearSaved',
    'dialog_exit_route': 'dialog.exitRoute',
  };

  // Try compound prefixes first
  for (const [compound, iosPrefix] of Object.entries(compoundPrefixes)) {
    if (androidKey.startsWith(compound + '_')) {
      const rest = androidKey.slice(compound.length + 1);
      // Variant 1: prefix.camelCaseRest
      variants.push(`${iosPrefix}.${snakeToCamel(rest)}`);
      // Variant 2: prefix.dot.separated
      variants.push(`${iosPrefix}.${rest.replace(/_/g, '.')}`);
    }
  }

  // Try single prefix
  const prefix = parts[0];
  if (prefixes.includes(prefix) && parts.length > 1) {
    const rest = parts.slice(1).join('_');
    // Variant 1: prefix.camelCaseRest
    variants.push(`${prefix}.${snakeToCamel(rest)}`);
    // Variant 2: prefix.dot.separated.rest
    variants.push(`${prefix}.${rest.replace(/_/g, '.')}`);
  }

  // Fallback variants
  variants.push(snakeToCamel(androidKey));
  variants.push(androidKey.replace(/_/g, '.'));

  return [...new Set(variants)]; // Remove duplicates
}

/**
 * Convert snake_case to camelCase
 */
function snakeToCamel(str: string): string {
  // Match underscore followed by any alphanumeric character
  return str.replace(/_([a-z0-9])/gi, (_, char) => char.toUpperCase());
}

/**
 * Parse Android strings.xml and extract key names
 */
function parseAndroidStrings(filePath: string): Set<string> {
  if (!existsSync(filePath)) {
    throw new Error(`Android strings file not found: ${filePath}`);
  }

  const content = readFileSync(filePath, 'utf-8');
  const keys = new Set<string>();

  // Match <string name="key_name">...</string>
  const regex = /<string\s+name="([^"]+)">/g;
  let match;

  while ((match = regex.exec(content)) !== null) {
    keys.add(match[1]);
  }

  return keys;
}

/**
 * Parse iOS Localizable.xcstrings and extract key names
 */
function parseIosStrings(filePath: string): { keys: Set<string>; frKeys: Set<string> } {
  if (!existsSync(filePath)) {
    throw new Error(`iOS strings file not found: ${filePath}`);
  }

  const content = readFileSync(filePath, 'utf-8');

  let data: { strings?: Record<string, { localizations?: { fr?: unknown } }> };
  try {
    data = JSON.parse(content);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    throw new Error(`Invalid JSON in iOS strings file: ${message}`);
  }

  const keys = new Set<string>();
  const frKeys = new Set<string>();

  for (const key of Object.keys(data.strings || {})) {
    keys.add(key);

    const localizations = data.strings?.[key]?.localizations;
    if (localizations?.fr) {
      frKeys.add(key);
    }
  }

  return { keys, frKeys };
}

/**
 * Check if Android FR strings match EN strings
 */
function checkAndroidFrParity(enKeys: Set<string>): string[] {
  if (!existsSync(ANDROID_STRINGS_FR)) {
    return Array.from(enKeys); // All missing if FR file doesn't exist
  }

  const frKeys = parseAndroidStrings(ANDROID_STRINGS_FR);
  const missing: string[] = [];

  for (const key of enKeys) {
    if (!frKeys.has(key)) {
      missing.push(key);
    }
  }

  return missing;
}

/**
 * Main parity check
 */
function checkParity(): ParityResult {
  // Parse Android strings
  const androidKeys = parseAndroidStrings(ANDROID_STRINGS);

  // Parse iOS strings
  const { keys: iosKeys, frKeys: iosFrKeys } = parseIosStrings(IOS_STRINGS);

  // Track which iOS keys are matched
  const matchedIosKeys = new Set<string>();

  // Check for missing keys in iOS using variant matching
  const missingInIos: string[] = [];
  for (const androidKey of androidKeys) {
    const variants = androidKeyToIosVariants(androidKey);
    const found = variants.find(v => iosKeys.has(v));
    if (found) {
      matchedIosKeys.add(found);
    } else {
      missingInIos.push(`${androidKey} -> tried: ${variants.slice(0, 2).join(', ')}`);
    }
  }

  // Check for keys in iOS that don't match any Android key
  const missingInAndroid: string[] = [];
  for (const iosKey of iosKeys) {
    if (!matchedIosKeys.has(iosKey)) {
      missingInAndroid.push(iosKey);
    }
  }

  // Check FR translations
  const androidFrMissing = checkAndroidFrParity(androidKeys);

  const iosFrMissing: string[] = [];
  for (const key of iosKeys) {
    if (!iosFrKeys.has(key)) {
      iosFrMissing.push(key);
    }
  }

  const passed = missingInIos.length === 0 &&
                 androidFrMissing.length === 0 &&
                 iosFrMissing.length === 0;

  return {
    passed,
    android: {
      platform: 'Android',
      keyCount: androidKeys.size,
      keys: androidKeys,
    },
    ios: {
      platform: 'iOS',
      keyCount: iosKeys.size,
      keys: iosKeys,
    },
    missingInIos,
    missingInAndroid,
    androidFrMissing,
    iosFrMissing,
  };
}

/**
 * Print report
 */
function printReport(result: ParityResult): void {
  console.log('\n📱 Localization Parity Report\n');
  console.log('═'.repeat(60));

  console.log(`\n📊 Summary:`);
  console.log(`   Android EN strings: ${result.android.keyCount}`);
  console.log(`   iOS EN strings:     ${result.ios.keyCount}`);

  if (result.missingInIos.length > 0) {
    console.log(`\n❌ Missing in iOS (${result.missingInIos.length} keys):`);
    for (const key of result.missingInIos.slice(0, 20)) {
      console.log(`   - ${key}`);
    }
    if (result.missingInIos.length > 20) {
      console.log(`   ... and ${result.missingInIos.length - 20} more`);
    }
  }

  if (result.missingInAndroid.length > 0) {
    console.log(`\n⚠️  Extra in iOS (not in Android, ${result.missingInAndroid.length} keys):`);
    for (const key of result.missingInAndroid.slice(0, 10)) {
      console.log(`   - ${key}`);
    }
    if (result.missingInAndroid.length > 10) {
      console.log(`   ... and ${result.missingInAndroid.length - 10} more`);
    }
  }

  if (result.androidFrMissing.length > 0) {
    console.log(`\n❌ Missing French translations in Android (${result.androidFrMissing.length} keys):`);
    for (const key of result.androidFrMissing.slice(0, 10)) {
      console.log(`   - ${key}`);
    }
    if (result.androidFrMissing.length > 10) {
      console.log(`   ... and ${result.androidFrMissing.length - 10} more`);
    }
  }

  if (result.iosFrMissing.length > 0) {
    console.log(`\n❌ Missing French translations in iOS (${result.iosFrMissing.length} keys):`);
    for (const key of result.iosFrMissing.slice(0, 10)) {
      console.log(`   - ${key}`);
    }
    if (result.iosFrMissing.length > 10) {
      console.log(`   ... and ${result.iosFrMissing.length - 10} more`);
    }
  }

  console.log('\n' + '═'.repeat(60));

  if (result.passed) {
    console.log('✅ All localization checks passed!\n');
  } else {
    console.log('❌ Localization parity check failed!\n');
    console.log('Fix the missing translations before shipping.\n');
  }
}

// Main
try {
  const result = checkParity();
  printReport(result);

  // Exit with error code if check failed
  if (!result.passed) {
    process.exit(1);
  }
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`\n❌ Error: ${message}\n`);
  process.exit(2);
}
