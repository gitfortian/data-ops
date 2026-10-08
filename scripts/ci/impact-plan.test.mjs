import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyChangedPaths, planForEvent, fullPlan } from './impact-plan.mjs';

const modules = new Set([
  'data-ops-business-semantic',
  'data-ops-business-metric',
  'data-ops-business-modeling',
  'data-ops-business-security',
  'data-ops-business-metadata',
  'data-ops-business-asset',
]);
const reverse = new Map([
  ['data-ops-business-semantic', new Set([
    'data-ops-business-modeling', 'data-ops-business-security',
  ])],
  ['data-ops-business-modeling', new Set(['data-ops-business-metric'])],
  ['data-ops-business-metadata', new Set(['data-ops-business-asset'])],
]);

test('main and manual runs always retain full coverage', () => {
  assert.equal(planForEvent('push', [], modules, reverse).mode, 'full');
  assert.equal(planForEvent('workflow_dispatch', [], modules, reverse).full, true);
  assert.equal(fullPlan('explicit').frontend, true);
});

test('semantic persistence edits test owner plus all transitive business consumers', () => {
  const plan = classifyChangedPaths([
    'data-ops-business/data-ops-business-semantic/src/main/java/io/yak/ops/business/semantic/repository/BusinessDomainRepositoryAdapter.java',
    'data-ops-business/data-ops-business-semantic/REQUIREMENTS.md',
  ], modules, reverse);
  assert.deepEqual([plan.mode, plan.backend, plan.frontend], ['scoped', true, false]);
  assert.equal(plan.modules,
    'data-ops-business/data-ops-business-metric,' +
    'data-ops-business/data-ops-business-modeling,' +
    'data-ops-business/data-ops-business-security,' +
    'data-ops-business/data-ops-business-semantic');
});

test('a test-only backend edit does not pull in downstream modules', () => {
  const plan = classifyChangedPaths([
    'data-ops-business/data-ops-business-semantic/src/test/java/SemanticTest.java',
  ], modules, reverse);
  assert.equal(plan.modules, 'data-ops-business/data-ops-business-semantic');
  assert.equal(plan.frontend, false);
});

test('a semantic UI edit tests Semantic and Metric without rebuilding backend', () => {
  const plan = classifyChangedPaths([
    'data-ops-ui/src/pages/semantic/domains/index.tsx',
    'data-ops-ui/src/services/semantic/api.ts',
  ], modules, reverse);
  assert.deepEqual([plan.mode, plan.backend, plan.frontend], ['scoped', false, true]);
  assert.equal(plan.jestTargets,
    'src/pages/metric,src/pages/semantic,src/services/semantic');
});

test('frontend-only service and locale edits remain scoped', () => {
  const plan = classifyChangedPaths([
    'data-ops-ui/src/services/metadata/api.ts',
    'data-ops-ui/src/locales/zh-CN/data-metadata.ts',
  ], modules, reverse);
  assert.equal(plan.mode, 'scoped');
  assert.equal(plan.backend, false);
  assert.equal(plan.frontend, true);
  assert.equal(plan.jestTargets, 'src/locales,src/pages/data-metadata,src/services/metadata');
});

test('docs-only PR runs static checks but correctly skips Maven/Jest/distribution', () => {
  const plan = classifyChangedPaths(['docs/engineering/ci.md', 'README.md'], modules, reverse);
  assert.deepEqual([plan.mode, plan.backend, plan.frontend, plan.full],
    ['docs-only', false, false, false]);
});

test('foundation, Maven POM, Flyway, contract API, workflow and scripts are full', () => {
  for (const path of [
    'pom.xml',
    '.github/workflows/architecture-checks.yml',
    'scripts/ci/impact-plan.mjs',
    'data-ops-common/src/main/java/Shared.java',
    'data-ops-boot/src/main/java/Bootstrap.java',
    'data-ops-business/pom.xml',
    'data-ops-business/data-ops-business-semantic/pom.xml',
    'data-ops-business/data-ops-business-semantic/src/main/java/io/yak/ops/business/semantic/api/Standard.java',
    'data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/V1.sql',
    'data-ops-ui/package.json',
    'data-ops-ui/src/components/Shared.tsx',
    'data-ops-ui/src/services/http/HttpClient.ts',
  ]) {
    assert.equal(classifyChangedPaths([path], modules, reverse).mode, 'full', path);
  }
});

test('unknown modules, unknown files and empty/unsafe diffs fail closed', () => {
  for (const paths of [
    [],
    ['data-ops-business/data-ops-business-new/src/main/java/A.java'],
    ['unknown/new-file.txt'],
    ['../escape.java'],
    ['docs/valid.md', 'scripts/ci/\nmalicious.mjs'],
  ]) {
    assert.equal(classifyChangedPaths(paths, modules, reverse).full, true,
      JSON.stringify(paths));
  }
});

test('any high-risk file upgrades otherwise scoped edits to full', () => {
  const plan = classifyChangedPaths([
    'data-ops-ui/src/pages/semantic/standards/index.tsx',
    'data-ops-business/data-ops-business-metric/pom.xml',
  ], modules, reverse);
  assert.equal(plan.full, true);
});

test('backend only and frontend only selections keep independent routing', () => {
  const backend = classifyChangedPaths([
    'data-ops-business/data-ops-business-metadata/src/main/java/Impl.java',
  ], modules, reverse);
  assert.equal(backend.modules,
    'data-ops-business/data-ops-business-asset,data-ops-business/data-ops-business-metadata');
  assert.equal(backend.jestTargets, '');
  const frontend = classifyChangedPaths([
    'data-ops-ui/src/pages/data-metadata/collect/index.tsx',
  ], modules, reverse);
  assert.equal(frontend.modules, '');
  assert.equal(frontend.jestTargets, 'src/pages/data-metadata');
});
