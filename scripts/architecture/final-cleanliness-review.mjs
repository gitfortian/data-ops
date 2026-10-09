#!/usr/bin/env node
/**
 * #345 final, read-only cleanliness inventory.
 * This tool reports facts; it does not infer symbol reachability or delete files.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { isProductionSource, scanSource } from './p3-source-comment-debt-audit.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export const RETIRED_ARTIFACTS = Object.freeze([
  'abc',
  'test_placeholder_should_not_create',
  'pelican-bicycle.html',
  '.zcode/tmp/jest.config.cjs',
  'data-ops-ui/tsc-output.txt',
  'data-ops-ui/src/services/data-development/legacy.ts',
  'data-ops-ui/src/services/data-service/legacy.ts',
  'data-ops-ui/src/services/data-source/legacy.ts',
  'data-ops-ui/src/services/realtime-sync/legacy.ts',
]);

export const PROTECTED_EVIDENCE = Object.freeze([
  'data/resources/projects/1/.asf.yaml',
  '.zcode/plans/plan-sess_36a3068e-4352-4d63-8388-d516f3f18f85.md',
  '.zcode/plans/plan-sess_a18ccd05-9ad6-47e1-9223-c72e865f530c.md',
  '.zcode/plans/plan-sess_b1fff9ab-dc67-4cd5-822f-1e6dca9c139d.md',
  'data-ops-framework/legacy/data-job/pom.xml',
  'data-ops-ui/scripts/type-baseline.json',
  'docs/product/DOCUMENT_GOVERNANCE.md',
  'docs/release/RELEASING.md',
  'scripts/db/check-migration-history.mjs',
]);

/**
 * Only one-off files immediately under data/ or the IDE scratch directory.
 * data/resources/** is a separate tracked evidence family and never a delete target.
 */
export function isOneOffResidue(file) {
  return /^data\/[^/]+\.(?:py|cjs)$/.test(file) ||
    file === 'data/architecture-mysql-test.json' ||
    file.startsWith('.zcode/tmp/');
}

export function inspectPaths(files) {
  const found = new Set(files);
  const reintroduced = RETIRED_ARTIFACTS.filter(file => found.has(file));
  const missingEvidence = PROTECTED_EVIDENCE.filter(file => !found.has(file));
  const oneOffResidues = [...found].filter(isOneOffResidue).sort();
  const dataPaths = [...found].filter(file => file.startsWith('data/')).sort();
  return {
    reintroduced, missingEvidence, oneOffResidues, dataPaths,
    passesGuard: reintroduced.length === 0 &&
      missingEvidence.length === 0 && oneOffResidues.length === 0,
  };
}

/**
 * Only exact, single-line duplicate imports are review candidates.
 * Import declarations spanning multiple lines are deliberately out of scope.
 * Even an exact duplicate is never silently rewritten: side effects and format
 * conventions belong to the frontend owner.
 */
export function findDuplicateImportLines(file, source) {
  if (!file.startsWith('data-ops-ui/src/') ||
      !/\.[cm]?[jt]sx?$/.test(file) || /\.(?:test|spec)\.[jt]sx?$/.test(file)) return [];
  const firstLine = new Map();
  const duplicates = [];
  for (const [index, value] of source.split(/\r?\n/).entries()) {
    const line = value.trim();
    if (!/^import\s/.test(line) ||
        !/^import\s+(?:type\s+)?(?:[^'";]+?\s+from\s+)?['"][^'"]+['"]\s*;?$/.test(line)) continue;
    const key = line.replace(/;$/, '');
    if (firstLine.has(key)) {
      duplicates.push({ path: file, firstLine: firstLine.get(key), line: index + 1, text: line });
    } else {
      firstLine.set(key, index + 1);
    }
  }
  return duplicates;
}

export function inspectSources(files, read, detailLimit = 40) {
  const commentsByOwner = {};
  let totalMarkers = 0;
  let untrackedMarkers = 0;
  let duplicateImports = 0;
  const samples = [];
  for (const file of files) {
    if (!isProductionSource(file)) continue;
    const source = read(file);
    const owner = file.startsWith('data-ops-ui/src/') ?
      'data-ops-ui' : file.split('/src/main/')[0];
    commentsByOwner[owner] ??= { tracked: 0, needsReview: 0 };
    for (const marker of scanSource(file, source)) {
      totalMarkers++;
      commentsByOwner[owner][marker.tracked ? 'tracked' : 'needsReview']++;
      if (!marker.tracked) {
        untrackedMarkers++;
        if (samples.length < detailLimit) samples.push({ kind: 'untracked-comment', ...marker });
      }
    }
    for (const duplicate of findDuplicateImportLines(file, source)) {
      duplicateImports++;
      if (samples.length < detailLimit) samples.push({ kind: 'duplicate-import', ...duplicate });
    }
  }
  return {
    totalMarkers, untrackedMarkers, duplicateImports, samples,
    samplesTruncated: untrackedMarkers + duplicateImports > samples.length,
    commentsByOwner: Object.fromEntries(
      Object.entries(commentsByOwner).sort(([a], [b]) => a.localeCompare(b))),
  };
}

function run() {
  const files = execFileSync('git', ['ls-files', '-z'], {
    cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024,
  }).split('\0').filter(Boolean);
  const paths = inspectPaths(files);
  const sources = inspectSources(files, file =>
    fs.readFileSync(path.join(ROOT, file), 'utf8'));
  const report = {
    kind: 'p3-final-evidence-inventory',
    scannedTrackedFiles: files.length,
    mutation: false,
    ...paths,
    ...sources,
    note: 'Review candidates are not safe-delete instructions or proof of runtime reachability.',
  };
  console.log(JSON.stringify(report, null, 2));
  if (!paths.passesGuard) process.exitCode = 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    run();
  } catch (error) {
    console.error('Final cleanliness inventory failed: ' + error.message);
    process.exitCode = 1;
  }
}
