import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const ROOT = 'data-ops-ui/src';
const RETIRED = [
  ROOT + '/pages/integration/realtime-sync/api',
  ROOT + '/services/realtime-sync/legacy',
];
const CONSUMERS = [
  ROOT + '/pages/integration/realtime-sync/detail.tsx',
  ROOT + '/pages/integration/realtime-sync/WizardJobEditor.tsx',
  ROOT + '/pages/integration/realtime-sync/YamlJobEditor.tsx',
  ROOT + '/pages/integration/realtime-sync/RealtimeExecutionPanel.tsx',
  ROOT + '/pages/integration/realtime-sync/RealtimeRuntimeDetail.tsx',
];

const IMPORT_PATTERN =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\b(?:jest|vi)\.mock\s*\(\s*)['"\x60]([^'"\x60]+)['"\x60]/g;

const resolveImport = (file, specifier) => {
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

test('Realtime Sync envelope compatibility implementation and page facade are retired', () => {
  for (const file of RETIRED) {
    assert.equal(existsSync(file + '.ts'), false, 'retired service still exists: ' + file);
    assert.equal(existsSync(file + '.tsx'), false, 'retired page still exists: ' + file);
  }
  const barrel = readFileSync(ROOT + '/services/realtime-sync/index.ts', 'utf8');
  assert.ok(barrel.includes("export * from './api'"));
  assert.ok(!barrel.includes('legacy'));
});

test('the five Realtime Sync editors/runtime consumers call the canonical data-only service', () => {
  for (const file of CONSUMERS) {
    const source = readFileSync(file, 'utf8');
    assert.ok(source.includes("from '@/services/realtime-sync'"), file);
    assert.ok(!/\brealtimeApi\b/.test(source), 'obsolete envelope caller: ' + file);
    assert.ok(!/\bresponse\.data\b|\bresult\.data\b|\brefreshed\.data\b/.test(source),
      'UI still unwraps legacy transport envelope: ' + file);
  }
});

test('the entire tracked UI cannot import either retired Realtime Sync entry point', () => {
  const files = execFileSync('git', ['ls-files', '-z', '--', ROOT], {
    encoding: 'utf8',
  }).split('\0').filter(file => /\.[cm]?[jt]sx?$/.test(file) && existsSync(file));
  assert.ok(files.length > 100, 'unexpectedly empty UI import audit');

  const violations = [];
  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(IMPORT_PATTERN)) {
      const resolved = resolveImport(file, match[1]);
      if (resolved && RETIRED.includes(resolved)) {
        violations.push(file + ' imports ' + match[1]);
      }
    }
  }
  assert.deepEqual(violations, [], 'retired Realtime Sync imports were reintroduced');
});

test('the import scanner handles aliases, relative paths and dynamic imports', () => {
  const consumer = ROOT + '/pages/integration/realtime-sync/detail.tsx';
  assert.equal(resolveImport(consumer, './api'), RETIRED[0]);
  assert.equal(resolveImport(consumer, '@/services/realtime-sync/legacy'), RETIRED[1]);
  assert.equal(resolveImport(consumer, '@/services/realtime-sync'), ROOT + '/services/realtime-sync');
  const dynamic = "await import('@/services/realtime-sync/legacy')";
  const imported = [...dynamic.matchAll(IMPORT_PATTERN)];
  assert.equal(imported.length, 1);
  assert.equal(resolveImport(consumer, imported[0][1]), RETIRED[1]);
});

test('runtime action identity, YAML, catalog and logs stay in modern service ownership', () => {
  const service = readFileSync(ROOT + '/services/realtime-sync/api.ts', 'utf8');
  for (const exportName of [
    'getRealtimeSyncTask',
    'listRealtimeDataSources',
    'listRealtimeCatalogTables',
    'listRealtimeCatalogColumns',
    'parseRealtimeSyncYaml',
    'renderRealtimeSyncYaml',
    'validateRealtimeSyncDefinition',
    'updateRealtimeSyncTask',
    'performRealtimeSyncAction',
    'getRealtimeRuntimeCapabilities',
    'getRealtimeSyncObservability',
    'getRealtimeSyncSubmissionLog',
    'getRealtimeSyncRuntimeLog',
  ]) {
    assert.ok(service.includes('export const ' + exportName), exportName);
  }
  assert.ok(service.includes("'Idempotency-Key': createIdempotencyKey()"),
    'runtime launch commands must preserve per-request idempotency');
  assert.ok(service.includes('HttpUtils.postData'), 'modern API must keep data-only POST');
  assert.ok(service.includes('HttpUtils.getData'), 'modern API must keep data-only GET');
});
