import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

// Historical page-only proxies. The canonical services remain in services/*.
const ROOT = 'data-ops-ui/src';
const RETIRED = [
  "data-ops-ui/src/pages/data-analysis/data-catalog/service",
  "data-ops-ui/src/pages/data-analysis/lineage/components/service",
  "data-ops-ui/src/pages/data-analysis/lineage/service",
  "data-ops-ui/src/pages/data-source/components/DriverManager/service",
  "data-ops-ui/src/pages/resources/resource-management/service",
  "data-ops-ui/src/pages/data-analysis/dashboard/service",
  "data-ops-ui/src/pages/data-analysis/dashboard/dashboard-service"
];
const IMPORT_PATTERN =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\b(?:jest|vi)\.mock\s*\(\s*)['"\x60]([^'"\x60]+)['"\x60]/g;

const resolveImport = (consumer, specifier) => {
  const target = specifier.startsWith('@/') ?
    path.posix.join(ROOT, specifier.slice(2)) :
    specifier.startsWith('.') ?
      path.posix.normalize(path.posix.join(path.posix.dirname(consumer), specifier)) :
      null;
  return target?.replace(/\.(?:ts|tsx|js|jsx|mjs|mts)$/, '') ?? null;
};

const sourceOf = (relative) => readFileSync(ROOT + '/' + relative, 'utf8');

test('the seven page-only legacy service proxies stay retired', () => {
  for (const target of RETIRED) {
    assert.equal(existsSync(target + '.ts'), false, target + '.ts');
    assert.equal(existsSync(target + '.tsx'), false, target + '.tsx');
  }
});

test('tracked frontend import graph does not reference retired proxy modules', () => {
  const files = execFileSync('git', ['ls-files', '-z', '--', ROOT], {
    encoding: 'utf8',
  }).split('\0').filter(file => /\.[cm]?[jt]sx?$/.test(file) && existsSync(file));
  assert.ok(files.length > 100, 'full frontend import audit unexpectedly empty');
  const violations = [];
  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(IMPORT_PATTERN)) {
      const resolved = resolveImport(file, match[1]);
      if (resolved && RETIRED.includes(resolved)) {
        violations.push(file + ' -> ' + match[1]);
      }
    }
  }
  assert.deepEqual(violations, [], 'old proxy imports must not re-enter the frontend');
});

test('scanner resolves @ alias, relative imports and dynamic imports', () => {
  const consumer = ROOT + '/pages/data-analysis/lineage/LineageWorkspace.tsx';
  assert.equal(resolveImport(consumer, './service'), ROOT + '/pages/data-analysis/lineage/service');
  assert.equal(resolveImport(consumer, '@/services/data-analysis'), ROOT + '/services/data-analysis');
  const dynamic = "await import('@/pages/data-analysis/lineage/service')";
  const matched = [...dynamic.matchAll(IMPORT_PATTERN)];
  assert.equal(matched.length, 1);
  assert.equal(resolveImport(consumer, matched[0][1]), RETIRED[2]);
});

test('lineage workspace consumes canonical lineage service with unchanged call names', () => {
  const source = sourceOf('pages/data-analysis/lineage/LineageWorkspace.tsx');
  assert.ok(source.includes("getLineageAssetByKey as fetchLineageAssetByKey"));
  assert.ok(source.includes("getLineageGraph as fetchLineageGraph"));
  assert.ok(source.includes("from '@/services/data-analysis'"));
  assert.ok(source.includes('searchLineageAssets'));
  const canonical = sourceOf('services/data-analysis/lineage.ts');
  for (const api of ['searchLineageAssets', 'getLineageAssetByKey', 'getLineageGraph']) {
    assert.ok(canonical.includes('export const ' + api), api);
  }
});

test('resource list and content editor share the original canonical service', () => {
  for (const relative of [
    'pages/resources/resource-management/index.tsx',
    'pages/resources/resource-management/components/ResourceDetailDrawer.tsx',
  ]) {
    assert.ok(sourceOf(relative).includes("from '@/services/resource-management'"), relative);
  }
  const barrel = sourceOf('services/resource-management/index.ts');
  assert.ok(barrel.includes("export * from './api'"));
});

test('dashboard document and dataset owners remain distinct after proxy removal', () => {
  const dashboard = sourceOf('pages/data-analysis/dashboard/use-dashboard.ts');
  assert.ok(dashboard.includes("from '@/services/dashboard'"));
  assert.ok(dashboard.includes('fetchAnalysisDatasets as fetchDashboardDatasets'));
  assert.ok(dashboard.includes("from '@/services/dataset'"));
  for (const consumer of [
    'pages/data-analysis/dashboard/version-history-drawer.tsx',
    'pages/data-analysis/dashboard/widget-action-editor.tsx',
  ]) {
    assert.ok(sourceOf(consumer).includes("from '@/services/dashboard'"), consumer);
  }
  assert.ok(sourceOf('services/dashboard/index.ts').includes("export * from './api'"));
  assert.ok(sourceOf('services/dataset/index.ts').includes("export * from './api'"));
});

test('data catalog and datasource driver pages already use canonical service imports', () => {
  assert.ok(sourceOf('pages/data-analysis/data-catalog/hooks/useDataCatalog.ts')
    .includes("from '@/services/data-analysis'"));
  assert.ok(sourceOf('pages/data-source/components/DriverManager/index.tsx')
    .includes("from '@/services/data-source'"));
});
