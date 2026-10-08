import test from 'node:test';
import assert from 'node:assert/strict';
import {
  REPOSITORY, ARCHITECTURE_SERIES,
  validateArchitecturePull, validateIntegrationSeries, integrationSummary,
} from './a7-integration-preview.mjs';

function pull(spec = ARCHITECTURE_SERIES[0]) {
  return {
    number: spec.number, title: '⛔【暂勿合并｜架构重构 A0】Protected',
    state: 'open', merged_at: null, draft: true,
    base: { ref: 'main', repo: { full_name: REPOSITORY } },
    head: { ref: spec.branch, repo: { full_name: REPOSITORY }, sha: 'a'.repeat(40) },
    labels: [{ name: 'do-not-merge' }, { name: 'architecture-refactor' }],
    html_url: 'https://github.com/' + REPOSITORY + '/pull/' + spec.number,
  };
}

test('A7 inventory has unique reviewed PR numbers and correct branches', () => {
  assert.equal(validateIntegrationSeries().length, 11);
  assert.deepEqual(ARCHITECTURE_SERIES.map(s => s.phase),
    ['A0', 'A1', 'A2', 'A2.2', 'A3', 'A3.2', 'A4', 'A4.2', 'A5', 'A5.2', 'A6.1']);
  assert.throws(() => validateIntegrationSeries(ARCHITECTURE_SERIES.slice(1)), /exactly 11/);
  assert.throws(() => validateIntegrationSeries([
    ...ARCHITECTURE_SERIES.slice(0, 10), ARCHITECTURE_SERIES[0],
  ]), /Duplicate/);
});

test('live GitHub metadata is pinned to a verified exact head commit', () => {
  const result = validateArchitecturePull(pull(), ARCHITECTURE_SERIES[0]);
  assert.equal(result.number, 370);
  assert.equal(result.sha, 'a'.repeat(40));
  assert.equal(result.phase, 'A0');
  assert.match(result.url, /pull\/370$/);
});

test('fail closed on PR that was merged, published, retargeted, replaced or stripped of protection', () => {
  const spec = ARCHITECTURE_SERIES[0];
  const mutations = [
    { draft: false },
    { state: 'closed' },
    { merged_at: '2026-10-08T10:00:00Z' },
    { base: { ref: 'release', repo: { full_name: REPOSITORY } } },
    { head: { ref: 'main', repo: { full_name: REPOSITORY }, sha: 'a'.repeat(40) } },
    { head: { ref: spec.branch, repo: { full_name: 'another/repo' }, sha: 'a'.repeat(40) } },
    { head: { ref: spec.branch, repo: { full_name: REPOSITORY }, sha: 'moving' } },
    { labels: [{ name: 'architecture-refactor' }] },
    { labels: [{ name: 'do-not-merge' }] },
    { title: 'Merge me now' },
    { number: 999 },
  ];
  for (const mutation of mutations) {
    assert.throws(() => validateArchitecturePull({ ...pull(spec), ...mutation }, spec),
      /PR #370:/, JSON.stringify(mutation));
  }
});

test('detached preview report records real SHA and failure without asserting CI succeeded', () => {
  const first = { ...validateArchitecturePull(pull(), ARCHITECTURE_SERIES[0]), result: 'merged in temporary preview' };
  const content = integrationSummary({
    baseSha: 'b'.repeat(40), integratedSha: null,
    failed: 'local conflict detected', prs: [first],
  });
  assert.match(content, /FAILED \(do not merge\)/);
  assert.match(content, /local conflict detected/);
  assert.match(content, /never merged to main/);
  assert.match(content, /370/);
  assert.doesNotMatch(content, /CI PASSED|safe to merge/);
});
