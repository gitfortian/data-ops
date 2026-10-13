import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const read = file => readFileSync(file, 'utf8');
const tracked = execFileSync('git', ['ls-files', '-z'], {
  encoding: 'utf8', maxBuffer: 24 * 1024 * 1024,
}).split(String.fromCharCode(0)).filter(Boolean);
const trackedSet = new Set(tracked);

const INDEXES = [
  // Only indexes still present after 482017e document cleanup.
  'docs/README.md', 'docs/product/README.md',
  'docs/product/LEGACY_DOC_INDEX.md', 'docs/reviews/README.md',
];

const isModuleReadme = file =>
  /^data-ops-business\/(?:data-ops-business-[^/]+\/|data-ops-business-sync\/data-ops-business-sync-[^/]+\/)README\.md$/.test(file);

function linksIn(content) {
  return [
    ...[...content.matchAll(/!?\[[^\]\n]*\]\(([^)\n]+)\)/g)]
      .map(match => match[1]),
    ...[...content.matchAll(/\b(?:href|src)=["']([^"']+)["']/g)]
      .map(match => match[1]),
  ];
}

function localTarget(file, input) {
  const raw = input.trim().replace(/^<|>$/g, '').split('#')[0].split('?')[0];
  if (!raw || /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(raw)) return null;
  let uri = raw;
  try { uri = decodeURIComponent(uri); } catch { /* Keep original spelling */ }
  return path.posix.normalize(uri.startsWith('/') ?
    uri.slice(1) : path.posix.join(path.posix.dirname(file), uri));
}

function localLinks(file) {
  return linksIn(read(file)).map(link => localTarget(file, link)).filter(Boolean);
}

test('retained repository documentation indexes have no broken local links', () => {
  assert.equal(INDEXES.length, 4);
  assert.ok(trackedSet.has('docs/README.md'));
  for (const file of INDEXES) {
    assert.ok(trackedSet.has(file), file);
    assert.ok(existsSync(file), file);
    for (const target of localLinks(file)) {
      assert.ok(existsSync(target), file + ' -> ' + target);
    }
  }
  for (const file of ['README.md', 'README_CN.md', 'AGENTS.md']) {
    assert.ok(trackedSet.has(file), file);
  }
});

test('retained review index includes every current direct Markdown report', () => {
  const root = 'docs/reviews/';
  const index = root + 'README.md';
  const markdown = tracked.filter(file =>
    file.startsWith(root) && file.endsWith('.md') &&
    file.slice(root.length).split('/').length === 1 && file !== index);
  const destinations = new Set(localLinks(index));
  assert.ok(markdown.length >= 1, root);
  for (const file of markdown)
    assert.ok(destinations.has(file), index + ' is missing ' + file);
});

test('removed historic docs are traceable without resurrecting stale local paths', () => {
  const docIndex = read('docs/README.md');
  const classification = read('docs/product/LEGACY_DOC_INDEX.md');
  assert.ok(trackedSet.has('docs/reviews/README.md'));
  assert.ok(localLinks('docs/README.md').includes('docs/reviews/README.md'));
  assert.match(read('docs/reviews/README.md'), /Evidence|Review|Historical/);
  assert.match(docIndex, /482017e/);
  assert.match(classification, /140c82c6227ef39339ff19825c41786b4035c6fb/);
  assert.match(docIndex, /不是|非/);
});

test('retained evidence and repository navigators never become new product authority', () => {
  for (const file of ['docs/README.md', 'docs/product/LEGACY_DOC_INDEX.md',
    'docs/reviews/README.md']) {
    const source = read(file);
    assert.match(source, /Evidence|Historical|Review/i, file);
    assert.match(source, /不是|非|不能|不等于|不得|不表示/, file);
  }
  assert.ok(existsSync('docs/product/DOCUMENT_GOVERNANCE.md'));
  assert.ok(existsSync('docs/product/decisions/PD-003-business-semantic-metric-contract.md'));
  assert.ok(existsSync('docs/release/RELEASING.md'));
});

test('Metric readme no longer points at a non-existent semantic/metrics plan', () => {
  const content = read('data-ops-business/data-ops-business-metric/README.md');
  assert.ok(!content.includes('docs/semantic/metrics/'));
  assert.ok(content.includes('docs/metric/issues/gap-backlog-2026-09.md'));
  assert.ok(content.includes('PD-003-business-semantic-metric-contract.md'));
});

test('all three Framework Maven invocations use a portable repo-root wrapper, not a local Windows drive', () => {
  const content = read('data-ops-framework/README.md');
  assert.ok(!/D:[\\/]baize-works/i.test(content));
  const invocations = content.match(/\.\/mvnw -f data-ops-framework\/pom\.xml/g) ?? [];
  assert.ok(invocations.length >= 4, invocations.length);
  assert.ok(content.includes('central-release'));
  assert.ok(content.includes('release-obfuscated'));
});

test('bilingual root READMEs navigate to the governed docs and current repository', () => {
  for (const file of ['README.md', 'README_CN.md']) {
    const linked = new Set(localLinks(file));
    assert.ok(linked.has('docs/README.md'), file);
    assert.ok(linked.has('docs/product/README.md'), file);
    assert.ok(read(file).includes('https://github.com/gitfortian/data-ops/issues'), file);
  }
});

test('preserved release, framework, migration and evidence contracts remain available', () => {
  for (const file of [
    'scripts/architecture/backend-p2-reachability-audit.mjs',
    'scripts/architecture/framework-legacy.test.mjs',
    'scripts/db/check-migration-history.mjs',
    'data-ops-framework/legacy/data-job/pom.xml',
    'docs/product/DOCUMENT_GOVERNANCE.md',
    'docs/release/RELEASING.md',
    'data-ops-ui/scripts/type-baseline.json',
  ]) assert.ok(existsSync(file), file);
});
