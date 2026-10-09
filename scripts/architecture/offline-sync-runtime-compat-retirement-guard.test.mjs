import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const ROOT = 'data-ops-ui/src';
const RETIRED = [
  ROOT + '/services/batch-link-up/task-legacy',
  ROOT + '/pages/integration/batch-link-up/type',
];
const CONSUMERS = [
  'pages/integration/batch-link-up/TaskDetailPanel.tsx',
  'pages/integration/batch-link-up/components/TaskHistoryPanel/hooks/useTaskHistory.ts',
  'pages/integration/batch-link-up/detail/redesigned.tsx',
  'pages/integration/batch-link-up/tabs/MetricsTab.tsx',
  'pages/integration/batch-link-up/components/SyncTaskList/components/RunLogDrawer.tsx',
  'pages/integration/batch-link-up/components/SyncTaskList/components/ScheduleInfo.tsx',
  'pages/integration/batch-link-up/components/SyncTaskList/index.tsx',
];
const IMPORT_PATTERN =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\b(?:jest|vi)\.(?:mock|doMock|requireActual)\s*\(\s*)['"\x60]([^'"\x60]+)['"\x60]/g;

const resolveImport = (file, specifier) => {
  const target = specifier.startsWith('@/') ?
    path.posix.join(ROOT, specifier.slice(2)) :
    specifier.startsWith('.') ?
      path.posix.normalize(path.posix.join(path.posix.dirname(file), specifier)) :
      null;
  return target?.replace(/\.(?:ts|tsx|js|jsx|mjs|mts)$/, '') ?? null;
};

test('old offline task adapter and page type facade stay retired', () => {
  for (const file of RETIRED) assert.equal(existsSync(file + '.ts'), false, file);
  const barrel = readFileSync(ROOT + '/services/batch-link-up/index.ts', 'utf8');
  assert.ok(barrel.includes("export * from './api'"));
  assert.ok(!barrel.includes('legacy'));
});

test('full tracked UI import graph cannot refer to retired task adapters', () => {
  const files = execFileSync('git', ['ls-files', '-z', '--', ROOT], {
    encoding: 'utf8',
  }).split(String.fromCharCode(0))
    .filter(file => /\.[cm]?[jt]sx?$/.test(file) && existsSync(file));
  assert.ok(files.length > 100, 'unexpectedly empty audit');
  const violations = [];
  for (const file of files) {
    for (const match of readFileSync(file, 'utf8').matchAll(IMPORT_PATTERN)) {
      if (RETIRED.includes(resolveImport(file, match[1])))
        violations.push(file + ' imports ' + match[1]);
    }
  }
  assert.deepEqual(violations, []);
});

test('import scanner resolves aliases, relative paths and dynamic imports', () => {
  const caller = ROOT + '/pages/integration/batch-link-up/TaskDetailPanel.tsx';
  assert.equal(resolveImport(caller, './type'), RETIRED[1]);
  assert.equal(resolveImport(caller, '@/services/batch-link-up/task-legacy'), RETIRED[0]);
  assert.equal(resolveImport(caller, '@/services/batch-link-up'), ROOT + '/services/batch-link-up');
  const dynamic = "await import('@/pages/integration/batch-link-up/type')";
  assert.equal(resolveImport(caller, [...dynamic.matchAll(IMPORT_PATTERN)][0][1]), RETIRED[1]);
});

test('all seven runtime/list consumers use the canonical data-only service', () => {
  for (const relative of CONSUMERS) {
    const source = readFileSync(ROOT + '/' + relative, 'utf8');
    assert.ok(source.includes("from '@/services/batch-link-up'") ||
      source.includes('from "@/services/batch-link-up"'), relative);
    for (const name of [
      'linkupJobInstanceApi', 'batchJobInstanceApi', 'batchJobExecutorApi',
      'linkupClientApi', 'linkupJobScheduleApi',
    ]) assert.ok(!source.includes(name), relative + ' still uses ' + name);
  }
});

test('complex offline editor still owns special stateful legacy response handling', () => {
  const legacy = readFileSync(ROOT + '/services/batch-link-up/definition-legacy.ts', 'utf8');
  const page = readFileSync(ROOT + '/pages/integration/batch-link-up/api.ts', 'utf8');
  const editor = readFileSync(
    ROOT + '/pages/integration/batch-link-up/config/multi/hooks/useMultiWorkflowState.tsx', 'utf8');
  assert.ok(legacy.includes("import { normalizeOfflineInstancePageRequest } from './api'"));
  assert.ok(legacy.includes('export const linkupJobDefinitionApi'));
  assert.ok(legacy.includes('saveOrUpdateGuideMulti'));
  assert.ok(page.includes('definition-legacy'));
  assert.ok(editor.includes('getSaveResponseData'));
});

test('the modern service owns all read, schedule and batch operations', () => {
  const api = readFileSync(ROOT + '/services/batch-link-up/api.ts', 'utf8');
  for (const name of [
    'listOfflineSyncInstances', 'getOfflineSyncInstanceDetail', 'getOfflineSyncInstanceLog',
    'listOfflineSyncTableMetrics', 'getOfflineSyncScheduleTimes', 'getOfflineSyncClientLogs',
    'batchStartOfflineSyncTasks', 'batchStopOfflineSyncTasks',
  ]) assert.ok(api.includes('export const ' + name), name);
  assert.ok(api.includes('normalizeOfflineInstancePageRequest(query)'));
  assert.ok(api.includes('HttpUtils.getData'));
  assert.ok(api.includes('HttpUtils.postData'));
});
