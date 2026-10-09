import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

// These pages have completed the envelope -> modern API migration. This guard
// keeps them from accidentally importing the historical page coordinator.
// Other Workbench consumers intentionally retain that coordinator for
// 409 optimistic-conflict recovery, Publish confirmation and Run preflight.
const MIGRATED_PAGES = [
  'data-ops-ui/src/pages/development/data-development/components/workbench/TaskVersionsPanel.tsx',
  'data-ops-ui/src/pages/development/data-development/executions/index.tsx',
  'data-ops-ui/src/pages/development/data-development/executions/ExecutionDetailDrawer.tsx',
  'data-ops-ui/src/pages/development/data-development/releases/index.tsx',
];

const LEGACY_IMPORT =
  /(?:from\s*|import\s*(?:\(\s*)?|require\s*\(\s*)['"`]([^'"`]+)['"`]/g;

function obsoletePaths(source) {
  return [...source.matchAll(LEGACY_IMPORT)]
    .map((match) => match[1])
    .filter((specifier) =>
      specifier === '../service' || specifier === '../../service'
      || specifier === '../service.ts' || specifier === '../../service.ts'
      || specifier === '@/pages/development/data-development/service'
      || specifier === '@/services/data-development/legacy');
}

test('all migrated Data Development consumers exist and import modern services', () => {
  for (const file of MIGRATED_PAGES) {
    assert.ok(existsSync(file), 'missing migrated page: ' + file);
    const source = readFileSync(file, 'utf8');
    assert.ok(source.includes("from '@/services/data-development'"),
      file + ' must import the modern service');
    assert.deepEqual(obsoletePaths(source), [],
      file + ' must not return to the old envelope facade');
  }
});

test('legacy source scanner catches static and dynamic imports and keeps valid paths', () => {
  const invalid = [
    "import { listDevelopmentReleases } from '../service';",
    "export { cancelDevelopmentTaskExecution } from '../../service';",
    "const old = await import('@/services/data-development/legacy');",
    "const legacy = require('@/pages/development/data-development/service');",
  ];
  for (const expression of invalid) {
    assert.equal(obsoletePaths(expression).length, 1, expression);
  }
  assert.deepEqual(obsoletePaths("import { listDevelopmentReleases } from '@/services/data-development';"), []);
  assert.deepEqual(obsoletePaths("import { something } from '../releaseExperience';"), []);
});

test('historical coordinator remains present while Workbench use-cases depend on it', () => {
  const facade = 'data-ops-ui/src/pages/development/data-development/service.ts';
  assert.ok(existsSync(facade), 'Workbench coordinator must not be deleted without a separate migration');
  const source = readFileSync(facade, 'utf8');
  for (const behavior of [
    'classifyDraftSaveFailure',
    'rebaseDraftSavePayload',
    'validateDevelopmentRunDefinition',
    'validateDevelopmentTaskPublish',
    'getSqlMetadataContext',
  ]) {
    assert.ok(source.includes(behavior), 'Workbench business guard missing: ' + behavior);
  }
});

test('Workbench migrated query and execution commands never use the envelope facade', () => {
  const workbench = readFileSync(
    'data-ops-ui/src/pages/development/data-development/components/workbench/DevelopmentWorkbench.tsx',
    'utf8',
  );
  const modern = readFileSync(
    'data-ops-ui/src/pages/development/data-development/components/workbench/workbenchModernApi.ts',
    'utf8',
  );

  const match = workbench.match(/import\s*\{([^}]+)\}\s*from\s*'\.\.\/\.\.\/service'/);
  assert.ok(match, 'Workbench must still use its special page coordinator');
  const exportedNames = match[1].split(',').map((name) => name.trim()).filter(Boolean).sort();
  assert.deepEqual(exportedNames, [
    'previewDevelopmentSqlLineage',
    'publishDevelopmentTask',
    'runDevelopmentTask',
    'saveDevelopmentTaskDraft',
  ].sort(), 'Only guarded save/run/publish/SQL preview may use the page coordinator');

  for (const name of [
    'loadWorkbenchDraft',
    'loadWorkbenchActiveExecution',
    'readWorkbenchExecution',
    'cancelWorkbenchExecution',
    'retryWorkbenchExecution',
  ]) {
    assert.ok(workbench.includes(name), 'Workbench does not call modern ' + name);
    assert.ok(modern.includes('export const ' + name), 'modern boundary missing ' + name);
  }
  for (const name of [
    'getDevelopmentTaskDraft',
    'getActiveDevelopmentTaskExecution',
    'getDevelopmentTaskExecution',
    'cancelDevelopmentTaskExecution',
    'retryDevelopmentTaskExecution',
  ]) {
    assert.ok(modern.includes(name), 'modern HTTP service missing ' + name);
    assert.ok(!exportedNames.includes(name), 'legacy HTTP read/action reintroduced: ' + name);
  }
  assert.ok(modern.includes("from '@/services/data-development'"),
    'Workbench modern boundary must use modern data-only services');
});

test('Workbench keeps conflict-aware Save, preflight Run, confirmed Publish and editor SQL preview', () => {
  const workbench = readFileSync(
    'data-ops-ui/src/pages/development/data-development/components/workbench/DevelopmentWorkbench.tsx',
    'utf8',
  );
  const protectedCommands = [
    'saveDevelopmentTaskDraft(',
    'runDevelopmentTask(',
    'publishDevelopmentTask(',
    'previewDevelopmentSqlLineage(',
  ];
  for (const name of protectedCommands) {
    assert.ok(workbench.includes(name), 'protected page coordinator bypassed: ' + name);
  }
  assert.equal((workbench.match(/\bresponseData\(/g) || []).length, 4,
    'raw envelope unwraps should remain only in four protected coordination corridors');
});
