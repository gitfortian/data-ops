import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isProductionSource, parseCommentMarker, scanSource, parseAddedCommentMarkers,
} from './p3-source-comment-debt-audit.mjs';

test('production paths exclude historical and test-only sources', () => {
  assert.equal(isProductionSource('data-ops-ui/src/pages/metric/index.tsx'), true);
  assert.equal(isProductionSource('data-ops-business/data-ops-business-metric/src/main/java/io/yak/Metric.java'), true);
  assert.equal(isProductionSource('data-ops-framework/src/main/kotlin/Test.kt'), true);
  assert.equal(isProductionSource('data-ops-ui/src/pages/metric/index.test.tsx'), false);
  assert.equal(isProductionSource('data-ops-ui/src/__tests__/fixture.ts'), false);
  assert.equal(isProductionSource('data-ops-business/src/test/java/MetricTest.java'), false);
  assert.equal(isProductionSource('docs/20261001/report.md'), false);
  assert.equal(isProductionSource('scripts/product/sample.mjs'), false);
});

test('comment marker must be real and have an explicit issue to count as tracked', () => {
  assert.deepEqual(parseCommentMarker('// TODO(#345): verify on owner'), {
    marker: 'TODO', tracked: true, text: '// TODO(#345): verify on owner',
  });
  assert.equal(parseCommentMarker('  /* FIXME https://github.com/gitfortian/data-ops/issues/345 */').tracked, true);
  assert.equal(parseCommentMarker('  * HACK DATA-42 needs owner review').tracked, true);
  assert.equal(parseCommentMarker('  {/* TODO missing ownership */}').tracked, false);
  assert.equal(parseCommentMarker('const todo = "/approval/todo";'), null);
  assert.equal(parseCommentMarker('const example = "// TODO #345";'), null);
  assert.equal(parseCommentMarker('https://example.com/TODO'), null);
  assert.equal(parseCommentMarker('// unrelated invariant'), null);
});

test('inventory is deterministic and skips strings and non-production files', () => {
  const source = [
    'const label = "TODO";',
    '// TODO #345 keep compatibility',
    '  // FIXME needs maintainer review',
    'const href = "https://example.com/TODO";',
  ].join('\n');
  assert.deepEqual(scanSource('data-ops-ui/src/pages/workflow/index.tsx', source), [
    { path: 'data-ops-ui/src/pages/workflow/index.tsx', line: 2,
      marker: 'TODO', tracked: true, text: '// TODO #345 keep compatibility' },
    { path: 'data-ops-ui/src/pages/workflow/index.tsx', line: 3,
      marker: 'FIXME', tracked: false, text: '// FIXME needs maintainer review' },
  ]);
  assert.deepEqual(scanSource('docs/historical.md', source), []);
});

test('multi-file zero-context diff preserves NEW line numbers', () => {
  const diff = [
    'diff --git a/data-ops-ui/src/a.ts b/data-ops-ui/src/a.ts',
    '--- a/data-ops-ui/src/a.ts', '+++ b/data-ops-ui/src/a.ts',
    '@@ -1,0 +3,2 @@', '+// TODO without issue', '+const code = "TODO";',
    '@@ -8 +10,2 @@', '-// TODO old comment', '+// FIXME #345 tracked', '+const x = 1;',
    'diff --git a/docs/history.md b/docs/history.md',
    '--- a/docs/history.md', '+++ b/docs/history.md',
    '@@ -0,0 +1 @@', '+// TODO ignore historical',
  ].join('\n');
  assert.deepEqual(parseAddedCommentMarkers(diff), [
    { path: 'data-ops-ui/src/a.ts', line: 3,
      marker: 'TODO', tracked: false, text: '// TODO without issue' },
    { path: 'data-ops-ui/src/a.ts', line: 10,
      marker: 'FIXME', tracked: true, text: '// FIXME #345 tracked' },
  ]);
});

test('added-only guard excludes unchanged debt and removed lines', () => {
  const diff = [
    'diff --git a/data-ops-boot/src/main/java/Test.java b/data-ops-boot/src/main/java/Test.java',
    '--- a/data-ops-boot/src/main/java/Test.java',
    '+++ b/data-ops-boot/src/main/java/Test.java',
    '@@ -4,2 +4,2 @@', ' // TODO existing comment', '-// FIXME old debt',
    '+// HACK #345 reviewed',
  ].join('\n');
  assert.deepEqual(parseAddedCommentMarkers(diff), [
    { path: 'data-ops-boot/src/main/java/Test.java', line: 5,
      marker: 'HACK', tracked: true, text: '// HACK #345 reviewed' },
  ]);
});
