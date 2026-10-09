import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const ROOT = 'data-ops-ui/src';
const RETIRED = [
  'data-ops-ui/src/services/data-development/legacy',
  'data-ops-ui/src/pages/development/data-development/data-service-node-service',
  'data-ops-ui/src/pages/development/data-development/dataset-service',
];
const PAGE_SERVICE = 'data-ops-ui/src/pages/development/data-development/service';
const COORDINATOR_USER =
  'data-ops-ui/src/pages/development/data-development/components/workbench/DevelopmentWorkbench.tsx';
const COMPAT =
  'data-ops-ui/src/services/data-development/workbench-compat.ts';

const importPattern =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\b(?:jest|vi)\.mock\s*\(\s*)['"\x60]([^'"\x60]+)['"\x60]/g;

const canonical = (file, specifier) => {
  let target;
  if (specifier.startsWith('@/')) {
    target = path.posix.join(ROOT, specifier.slice(2));
  } else if (specifier.startsWith('.')) {
    target = path.posix.normalize(path.posix.join(path.posix.dirname(file), specifier));
  } else {
    return null;
  }
  return target.replace(/\.(?:ts|tsx|js|jsx|mts|mjs)$/, '');
};

test('the three historical Data Development compatibility entry files no longer exist', () => {
  for (const target of RETIRED) {
    assert.equal(existsSync(target + '.ts'), false, 'old facade must be deleted: ' + target);
    assert.equal(existsSync(target + '.tsx'), false, 'old facade must be deleted: ' + target);
  }
  assert.ok(existsSync(COMPAT), 'narrow Workbench-only raw adapter is still required');
});

test('no tracked UI consumer can revive retired imports or bypass the special coordinator', () => {
  const files = execFileSync('git', ['ls-files', '-z', '--', ROOT], {
    encoding: 'utf8',
  }).split('\0').filter((file) => /\.[cm]?[jt]sx?$/.test(file) && existsSync(file));
  assert.ok(files.length > 100, 'repository source enumeration unexpectedly empty');

  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(importPattern)) {
      const resolved = canonical(file, match[1]);
      if (!resolved) continue;
      assert.ok(!RETIRED.includes(resolved),
        file + ' still imports retired Data Development facade: ' + match[1]);
      if (resolved === PAGE_SERVICE) {
        assert.equal(file, COORDINATOR_USER,
          file + ' bypasses the Workbench special-command boundary');
      }
      if (resolved === COMPAT.replace(/\.ts$/, '')) {
        assert.equal(file, PAGE_SERVICE + '.ts',
          file + ' imports raw envelope requests instead of modern service');
      }
    }
  }
});

test('Workbench remains the only page coordinator consumer and only special commands are exported', () => {
  const coordinator = readFileSync(PAGE_SERVICE + '.ts', 'utf8');
  const workbench = readFileSync(COORDINATOR_USER, 'utf8');
  const compat = readFileSync(COMPAT, 'utf8');
  const publicFunctions = [...coordinator.matchAll(/^export const (\w+)/gm)]
    .map((match) => match[1]).sort();
  assert.deepEqual(publicFunctions, [
    'saveDevelopmentTaskDraft',
    'runDevelopmentTask',
    'publishDevelopmentTask',
    'previewDevelopmentSqlLineage',
  ].sort());
  for (const contract of [
    'classifyDraftSaveFailure',
    'confirmConflictOverwrite',
    'validateDevelopmentTaskPublish',
    'validateDevelopmentRunDefinition',
    'getSqlMetadataContext',
  ]) {
    assert.ok(coordinator.includes(contract), 'missing protected coordinator logic: ' + contract);
  }
  for (const request of [
    'runDevelopmentTask',
    'publishDevelopmentTask',
    'previewDevelopmentSqlLineageRequest',
  ]) {
    assert.ok(compat.includes('export const ' + request), 'raw request missing: ' + request);
  }
  assert.ok(workbench.includes("from '../../service'"),
    'Workbench no longer uses guarded special commands');
});

test('Data Service and Dataset editors import their canonical data-only service exports', () => {
  for (const target of [
    'data-ops-ui/src/pages/development/data-development/components/data-service/DataServiceNodeEditor.tsx',
    'data-ops-ui/src/pages/development/data-development/components/dataset/DatasetNodeEditor.tsx',
  ]) {
    assert.ok(readFileSync(target, 'utf8').includes("from '@/services/data-development'"),
      'editor imports a page facade rather than a service: ' + target);
  }
});
