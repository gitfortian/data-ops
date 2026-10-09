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
  'docs/README.md', 'docs/HISTORICAL_EVIDENCE_INDEX.md',
  'docs/20261001/README.md', 'docs/20261003/README.md',
  'docs/20261004/README.md', 'docs/frontend-review/README.md',
  'docs/architecture-review/README.md', 'docs/reviews/README.md',
  'docs/v1/README.md', 'docs/test/README.md',
  'docs/engineering/p3-document-cleanliness-closeout.md',
  'docs/product/LEGACY_DOC_INDEX.md',
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

test('repo, P3 and business entrypoint Markdown/HTML links all resolve on disk', () => {
  const entrypoints = [
    'README.md', 'README_CN.md', 'AGENTS.md',
    'data-ops-framework/README.md', 'data-ops-ui/README.md',
    ...INDEXES,
    ...tracked.filter(isModuleReadme),
  ];
  assert.ok(entrypoints.length >= 35, entrypoints.length);
  const broken = [];
  for (const file of entrypoints) {
    assert.ok(existsSync(file), file);
    for (const target of localLinks(file)) {
      if (!existsSync(target)) broken.push(file + ' -> ' + target);
    }
  }
  assert.deepEqual(broken, []);
});

test('dated report and review indexes include every direct Markdown report, not just a sample', () => {
  for (const root of [
    'docs/20261001/', 'docs/20261003/', 'docs/20261004/', 'docs/reviews/',
  ]) {
    const index = root + 'README.md';
    const markdown = tracked.filter(file =>
      file.startsWith(root) && file.endsWith('.md') &&
      file.slice(root.length).split('/').length === 1 && file !== index);
    const destinations = new Set(localLinks(index));
    assert.ok(markdown.length >= 1, root);
    for (const file of markdown)
      assert.ok(destinations.has(file), index + ' is missing ' + file);
  }
});

test('all historical entry directories stay indexed without moving original evidence', () => {
  const atlas = read('docs/HISTORICAL_EVIDENCE_INDEX.md');
  for (const file of [
    'docs/20261001/README.md', 'docs/20261003/README.md',
    'docs/20261004/README.md', 'docs/reviews/README.md',
    'docs/frontend-review/README.md', 'docs/architecture-review/README.md',
    'docs/v1/README.md', 'docs/test/README.md',
  ]) {
    assert.ok(trackedSet.has(file), file);
    assert.ok(localLinks('docs/HISTORICAL_EVIDENCE_INDEX.md').includes(file),
      'unlisted evidence owner: ' + file);
    assert.match(read(file), /Evidence|Review|Historical/);
  }
  assert.match(atlas, /不是|非/);
});

test('dated evidence, P3 and repository navigators never become new product authority', () => {
  const files = [
    'docs/HISTORICAL_EVIDENCE_INDEX.md', ...INDEXES.filter(f =>
      f.startsWith('docs/20') || f.startsWith('docs/frontend-review/') ||
      f.startsWith('docs/architecture-review/') || f.startsWith('docs/reviews/') ||
      f.startsWith('docs/v1/') || f.startsWith('docs/test/')),
  ];
  for (const file of files) {
    const source = read(file);
    assert.ok(/Evidence|Historical|Review/.test(source), file);
    assert.ok(/不是|非|不能|不等于|不得/.test(source), file);
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

test('bilingual root READMEs navigate to the same governed repository docs', () => {
  for (const file of ['README.md', 'README_CN.md']) {
    const linked = new Set(localLinks(file));
    assert.ok(linked.has('docs/README.md'), file);
    assert.ok(read(file).includes('https://doc.yak-ops.com/'), file);
  }
});

test('P2 audit, Framework legacy contract, migration history and raw evidence remain available', () => {
  for (const file of [
    'docs/engineering/p2-backend-cleanliness-audit.md',
    'scripts/architecture/backend-p2-reachability-audit.mjs',
    'scripts/architecture/framework-legacy.test.mjs',
    'scripts/db/check-migration-history.mjs',
    'data-ops-framework/legacy/data-job/pom.xml',
    'docs/architecture-review/20261003/evidence.json',
    'docs/frontend-review/20261003/typecheck-comparison.json',
  ]) assert.ok(existsSync(file), file);
});
