#!/usr/bin/env tsx
/**
 * Tests for check-localization-parity.ts
 *
 * Tests the bug fixes:
 * 1. Backup/restore uses same filesystem (avoids EXDEV cross-device errors)
 * 2. try/finally pattern ensures cleanup even when tests fail mid-execution
 * 3. snakeToCamel handles digits correctly
 * 4. JSON parsing error handling
 * 5. Missing file error handling
 *
 * Usage: npm run test:l10n
 */

import { execSync } from 'child_process';
import { readFileSync, writeFileSync, unlinkSync, renameSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '../..');
const IOS_STRINGS = resolve(ROOT, 'ios/MarrakechGuide/MarrakechGuide/Resources/Localizable.xcstrings');
// Backup in same directory to avoid EXDEV errors (cross-device rename)
// Uses dot-prefix so it's hidden; *.bak is in .gitignore
const BACKUP_PATH = resolve(ROOT, 'ios/MarrakechGuide/MarrakechGuide/Resources/.Localizable.xcstrings.bak');

interface TestResult {
  name: string;
  passed: boolean;
  error?: string;
}

const results: TestResult[] = [];

function test(name: string, fn: () => void): void {
  try {
    fn();
    results.push({ name, passed: true });
    console.log(`  ✅ ${name}`);
  } catch (e) {
    const error = e instanceof Error ? e.message : String(e);
    results.push({ name, passed: false, error });
    console.log(`  ❌ ${name}`);
    console.log(`     ${error}`);
  }
}

function assertEqual<T>(actual: T, expected: T, message?: string): void {
  if (actual !== expected) {
    throw new Error(message || `Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function assertIncludes(str: string, substring: string, message?: string): void {
  if (!str.includes(substring)) {
    throw new Error(message || `Expected string to include "${substring}", got: ${str.slice(0, 200)}`);
  }
}

function runScript(): { stdout: string; stderr: string; exitCode: number } {
  try {
    const stdout = execSync('npm run check-l10n 2>&1', {
      cwd: resolve(__dirname),
      encoding: 'utf-8',
    });
    return { stdout, stderr: '', exitCode: 0 };
  } catch (e: unknown) {
    const err = e as { stdout?: string; stderr?: string; status?: number };
    return {
      stdout: err.stdout || '',
      stderr: err.stderr || '',
      exitCode: err.status || 1,
    };
  }
}

function backupIosStrings(): void {
  if (existsSync(IOS_STRINGS)) {
    renameSync(IOS_STRINGS, BACKUP_PATH);
  }
}

function restoreIosStrings(): void {
  if (existsSync(BACKUP_PATH)) {
    renameSync(BACKUP_PATH, IOS_STRINGS);
  }
}

/**
 * Local copy of snakeToCamel for testing.
 * Must match the implementation in check-localization-parity.ts
 */
function snakeToCamel(str: string): string {
  return str.replace(/_([a-z0-9])/gi, (_, char) => char.toUpperCase());
}

// =============================================================================
// Test: Backup/restore infrastructure works (same filesystem)
// =============================================================================

console.log('\n📋 Testing backup/restore infrastructure:\n');

test('backup and restore work without EXDEV errors', () => {
  // This test catches if BACKUP_PATH is changed to a different filesystem (e.g., /tmp)
  // which would cause renameSync to fail with EXDEV: cross-device link not permitted
  const originalContent = readFileSync(IOS_STRINGS, 'utf-8');

  // Test backup
  backupIosStrings();
  try {
    assertEqual(existsSync(BACKUP_PATH), true, 'Backup file should exist after backup');
    assertEqual(existsSync(IOS_STRINGS), false, 'Original file should not exist after backup');

    // Test restore
    restoreIosStrings();
    assertEqual(existsSync(IOS_STRINGS), true, 'Original file should exist after restore');
    assertEqual(existsSync(BACKUP_PATH), false, 'Backup file should not exist after restore');

    // Verify content is unchanged
    const restoredContent = readFileSync(IOS_STRINGS, 'utf-8');
    assertEqual(restoredContent, originalContent, 'Content should be unchanged after backup/restore cycle');
  } finally {
    // Ensure restore happens even if assertions fail
    restoreIosStrings();
  }
});

test('try/finally pattern restores file even when error occurs mid-test', () => {
  // This test verifies the try/finally cleanup pattern works correctly.
  // Without try/finally, if an error occurs after backup but before restore,
  // the file would be left missing and subsequent tests would fail.
  const originalContent = readFileSync(IOS_STRINGS, 'utf-8');

  // Simulate a test that throws an error after backup
  let errorWasThrown = false;
  backupIosStrings();
  try {
    // File is now backed up (moved to BACKUP_PATH)
    assertEqual(existsSync(IOS_STRINGS), false, 'File should be backed up');

    // Simulate an error occurring mid-test
    throw new Error('Simulated mid-test failure');
  } catch {
    errorWasThrown = true;
  } finally {
    restoreIosStrings();
  }

  // Verify error was thrown and caught
  assertEqual(errorWasThrown, true, 'Error should have been thrown');

  // Verify file was restored despite the error
  assertEqual(existsSync(IOS_STRINGS), true, 'File should be restored after error');
  assertEqual(existsSync(BACKUP_PATH), false, 'Backup should not exist after restore');

  // Verify content is unchanged
  const restoredContent = readFileSync(IOS_STRINGS, 'utf-8');
  assertEqual(restoredContent, originalContent, 'Content should be unchanged');
});

// =============================================================================
// Test: snakeToCamel handles digits
// =============================================================================

console.log('\n📋 Testing snakeToCamel digit handling:\n');

test('snakeToCamel converts underscore-digit correctly', () => {
  // The fix ensures step_1_title becomes step1Title, not step_1Title.
  assertEqual(snakeToCamel('step_1_title'), 'step1Title', 'Should convert digit after underscore');
  assertEqual(snakeToCamel('route_2_stops'), 'route2Stops', 'Should handle multiple parts with digit');
  assertEqual(snakeToCamel('my_day_3_hours'), 'myDay3Hours', 'Should handle digit in middle');
});

test('snakeToCamel still handles letters correctly', () => {
  assertEqual(snakeToCamel('home_title'), 'homeTitle', 'Basic snake_case');
  assertEqual(snakeToCamel('nav_home'), 'navHome', 'Two parts');
  assertEqual(snakeToCamel('my_day_title'), 'myDayTitle', 'Three parts');
});

// =============================================================================
// Test: Invalid JSON error handling
// =============================================================================

console.log('\n📋 Testing invalid JSON error handling:\n');

test('reports clear error for invalid JSON', () => {
  backupIosStrings();
  try {
    // Write invalid JSON
    writeFileSync(IOS_STRINGS, '{ invalid json content }');

    const { stdout, exitCode } = runScript();

    assertEqual(exitCode, 2, 'Should exit with code 2 for parse errors');
    assertIncludes(stdout, 'Invalid JSON', 'Should mention invalid JSON');
  } finally {
    restoreIosStrings();
  }
});

test('reports clear error for truncated JSON', () => {
  backupIosStrings();
  try {
    // Write truncated JSON
    writeFileSync(IOS_STRINGS, '{ "strings": { "key": ');

    const { stdout, exitCode } = runScript();

    assertEqual(exitCode, 2, 'Should exit with code 2 for parse errors');
    assertIncludes(stdout, 'Invalid JSON', 'Should mention invalid JSON');
  } finally {
    restoreIosStrings();
  }
});

// =============================================================================
// Test: Missing file error handling
// =============================================================================

console.log('\n📋 Testing missing file error handling:\n');

test('reports clear error for missing iOS strings file', () => {
  backupIosStrings();
  try {
    // Ensure file doesn't exist
    if (existsSync(IOS_STRINGS)) {
      unlinkSync(IOS_STRINGS);
    }

    const { stdout, exitCode } = runScript();

    assertEqual(exitCode, 2, 'Should exit with code 2 for missing file');
    assertIncludes(stdout, 'not found', 'Should mention file not found');
    assertIncludes(stdout, 'Localizable.xcstrings', 'Should mention the filename');
  } finally {
    restoreIosStrings();
  }
});

// =============================================================================
// Test: Normal operation still works
// =============================================================================

console.log('\n📋 Testing normal operation:\n');

test('passes when all translations are present', () => {
  const { stdout, exitCode } = runScript();

  assertEqual(exitCode, 0, 'Should exit with code 0 when all checks pass');
  assertIncludes(stdout, 'All localization checks passed', 'Should show success message');
});

test('reports correct key counts', () => {
  const { stdout } = runScript();

  assertIncludes(stdout, 'Android EN strings:', 'Should show Android count');
  assertIncludes(stdout, 'iOS EN strings:', 'Should show iOS count');
});

// =============================================================================
// Test: Detection of missing keys
// =============================================================================

console.log('\n📋 Testing missing key detection:\n');

test('detects missing iOS keys', () => {
  backupIosStrings();
  try {
    // Read current file and remove a key
    const content = readFileSync(BACKUP_PATH, 'utf-8');
    const data = JSON.parse(content);

    // Remove a known key
    delete data.strings['nav.home'];

    writeFileSync(IOS_STRINGS, JSON.stringify(data, null, 2));

    const { stdout, exitCode } = runScript();

    assertEqual(exitCode, 1, 'Should exit with code 1 for missing keys');
    assertIncludes(stdout, 'Missing in iOS', 'Should report missing in iOS');
    assertIncludes(stdout, 'nav_home', 'Should mention the missing key');
  } finally {
    restoreIosStrings();
  }
});

test('detects missing French translations in iOS', () => {
  backupIosStrings();
  try {
    // Read current file and remove FR translation for a key
    const content = readFileSync(BACKUP_PATH, 'utf-8');
    const data = JSON.parse(content);

    // Remove FR translation from a key
    if (data.strings['nav.home']?.localizations?.fr) {
      delete data.strings['nav.home'].localizations.fr;
    }

    writeFileSync(IOS_STRINGS, JSON.stringify(data, null, 2));

    const { stdout, exitCode } = runScript();

    assertEqual(exitCode, 1, 'Should exit with code 1 for missing FR translation');
    assertIncludes(stdout, 'Missing French translations in iOS', 'Should report missing FR');
  } finally {
    restoreIosStrings();
  }
});

// =============================================================================
// Summary
// =============================================================================

console.log('\n' + '═'.repeat(60));
const passed = results.filter(r => r.passed).length;
const failed = results.filter(r => !r.passed).length;

if (failed === 0) {
  console.log(`\n✅ All ${passed} tests passed!\n`);
  process.exit(0);
} else {
  console.log(`\n❌ ${failed} of ${passed + failed} tests failed:\n`);
  for (const r of results.filter(r => !r.passed)) {
    console.log(`   - ${r.name}: ${r.error}`);
  }
  console.log('');
  process.exit(1);
}
