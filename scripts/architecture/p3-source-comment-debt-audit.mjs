#!/usr/bin/env node
/**
 * Inventory TODO-style source comments without inferring dead-code reachability.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const MARKER = /^\s*(?:\/\/|\/\*+|\{\s*\/\*+|\*+|#)\s*(TODO|FIXME|XXX|HACK)\b(.*)$/i;
const ISSUE = /(?:^|[^\w])#\d+\b|https?:\/\/github\.com\/\S+\/issues\/\d+\b|\b[A-Z][A-Z0-9]+-\d+\b/i;

export function isProductionSource(file) {
  const p = file.replaceAll('\\', '/');
  if (/(?:^|\/)(?:__tests__|__mocks__)\//.test(p) ||
      /\.(?:test|spec)\.[jt]sx?$/.test(p)) return false;
  return (p.startsWith('data-ops-ui/src/') && /\.[jt]sx?$/.test(p)) ||
    (p.includes('/src/main/java/') && p.endsWith('.java')) ||
    (p.includes('/src/main/kotlin/') && p.endsWith('.kt'));
}

/**
 * Match only line-leading comment markers. Non-leading block comment prose,
 * inline comments and AST reachability still need an owning module's review.
 */
export function parseCommentMarker(line) {
  const match = line.match(MARKER);
  if (!match) return null;
  return {
    marker: match[1].toUpperCase(),
    tracked: ISSUE.test(match[2]),
    text: line.trim(),
  };
}

export function scanSource(file, source) {
  if (!isProductionSource(file)) return [];
  return source.split(/\r?\n/).flatMap((line, index) => {
    const marker = parseCommentMarker(line);
    return marker ? [{ path: file, line: index + 1, ...marker }] : [];
  });
}

/** Scan added lines only, preserving the NEW line numbers from unified diffs. */
export function parseAddedCommentMarkers(diff) {
  const findings = [];
  let file = '';
  let newLine = 0;
  for (const line of diff.split(/\r?\n/)) {
    if (line.startsWith('+++ b/')) {
      file = line.slice(6);
      continue;
    }
    if (line === '+++ /dev/null') {
      file = '';
      continue;
    }
    if (line.startsWith('@@ ')) {
      const hunk = line.match(/\+(\d+)(?:,\d+)?/);
      if (hunk) newLine = Number(hunk[1]);
      continue;
    }
    if (line.startsWith('+') && !line.startsWith('+++')) {
      if (isProductionSource(file)) {
        const marker = parseCommentMarker(line.slice(1));
        if (marker) findings.push({ path: file, line: newLine, ...marker });
      }
      newLine++;
    } else if (line.startsWith(' ')) {
      newLine++;
    }
  }
  return findings;
}

function git(args) {
  return execFileSync('git', args, {
    cwd: ROOT,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
}

function inventory() {
  const tracked = git(['ls-files', '-z']).split('\0').filter(isProductionSource);
  const findings = [];
  for (const file of tracked) {
    const absolute = path.join(ROOT, file);
    if (!fs.existsSync(absolute)) continue;
    findings.push(...scanSource(file, fs.readFileSync(absolute, 'utf8')));
  }
  return { scannedFiles: tracked.length, findings };
}

function summary(findings) {
  const byOwner = {};
  for (const item of findings) {
    const owner = item.path.startsWith('data-ops-ui/src/') ? 'data-ops-ui' :
      item.path.split('/src/main/')[0];
    byOwner[owner] ??= { tracked: 0, needsReview: 0 };
    byOwner[owner][item.tracked ? 'tracked' : 'needsReview']++;
  }
  return Object.fromEntries(Object.entries(byOwner).sort(([a], [b]) => a.localeCompare(b)));
}

function run() {
  const args = process.argv.slice(2);
  const base = args.find((arg) => arg.startsWith('--base='))?.slice(7);
  const checkAdded = args.includes('--check-added');
  if (checkAdded && !base) {
    throw new Error('--check-added requires --base=<existing git ref>');
  }
  if (checkAdded) {
    const diff = git(['diff', '--no-ext-diff', '--unified=0', '--diff-filter=ACMR',
      base + '...HEAD', '--']);
    const added = parseAddedCommentMarkers(diff);
    const untracked = added.filter((item) => !item.tracked);
    console.log(JSON.stringify({
      mode: 'added-comment-guard', base, newMarkers: added.length,
      untracked, byOwner: summary(added),
    }, null, 2));
    if (untracked.length) process.exitCode = 1;
    return;
  }
  const report = inventory();
  console.log(JSON.stringify({
    mode: 'production-comment-inventory',
    scannedFiles: report.scannedFiles,
    totalMarkers: report.findings.length,
    byOwner: summary(report.findings),
    findings: args.includes('--json') ? report.findings :
      report.findings.filter((item) => !item.tracked).slice(0, 40),
    findingsTruncated: !args.includes('--json'),
  }, null, 2));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    run();
  } catch (error) {
    console.error('P3 comment debt audit failed: ' + error.message);
    process.exitCode = 1;
  }
}
