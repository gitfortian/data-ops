import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const ROOT = 'data-ops-ui/src';
const RETIRED = [
  'data-ops-ui/src/services/data-development/legacy',
  'data-ops-ui/src/pages/development/data-development/data-service-node-service',
  'data-ops-ui/src/pages/development/data-development/data-service-runtime-publication',
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

  const violations = [];
  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(importPattern)) {
      const resolved = canonical(file, match[1]);
      if (!resolved) continue;
      if (RETIRED.includes(resolved)) {
        violations.push(file + ' imports retired facade ' + match[1]);
      }
      if (resolved === PAGE_SERVICE && file !== COORDINATOR_USER) {
        violations.push(file + ' bypasses the Workbench page coordinator');
      }
      if (resolved === COMPAT.replace(/\.ts$/, '')
        && file !== PAGE_SERVICE + '.ts'
        && file !== COMPAT.replace(/\.ts$/, '.test.ts')) {
        violations.push(file + ' imports Workbench raw envelope request directly');
      }
    }
  }
  assert.deepEqual(violations, [],
    'Retired import audit found ' + violations.length + ' violations');
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

test('Quick Create outside Data Development also uses the canonical service', () => {
  const source = readFileSync('data-ops-ui/src/pages/create/index.tsx', 'utf8');
  assert.ok(source.includes("from '@/services/data-development'"),
    'Quick Create still depends on the deprecated node envelope');
  assert.ok(source.includes('await createDevelopmentNode('),
    'Quick Create no longer calls the canonical node creation API');
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

test('Editor Settings uses modern GET/PUT while preserving deferred-save and error feedback', () => {
  const source = readFileSync(
    'data-ops-ui/src/pages/settings/components/EditorSettingsPanel.tsx',
    'utf8',
  );
  assert.ok(source.includes("from '@/services/data-development'"),
    'settings panel still reads an obsolete response envelope');
  for (const contract of [
    'getDevelopmentEditorSettings()',
    'await saveDevelopmentEditorSettings(next)',
    'generation !== editGeneration.current',
    '}, 450)',
    "setLoadFailed(true)",
    "message.error('编辑器设置保存失败')",
  ]) {
    assert.ok(source.includes(contract), 'editor settings persistence guard missing: ' + contract);
  }
  assert.ok(!source.includes('response.data'),
    'settings panel must not read legacy envelope.data');
});
