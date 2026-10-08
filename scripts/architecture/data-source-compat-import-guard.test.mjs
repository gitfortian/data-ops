import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

// Freeze the current Data Source legacy importers until each is explicitly migrated.
// The old API returns response envelopes; replacing its imports with the new data-only API is not behavior-preserving.
const LEGACY_MODULE = 'data-ops-ui/src/services/data-source/legacy';
// Only the live consumers proven by the architecture scan are temporarily allowed.
const EXISTING_CONSUMERS = new Set([
  'data-ops-ui/src/pages/data-source/service.ts',
  'data-ops-ui/src/pages/integration/batch-link-up/components/TableColumnsPopover.tsx',
  'data-ops-ui/src/pages/integration/batch-link-up/config/multi/hooks/useMultiWorkflowState.tsx',
  'data-ops-ui/src/pages/integration/batch-link-up/config/multi/index.tsx',
  'data-ops-ui/src/pages/integration/batch-link-up/config/single/index.tsx',
  'data-ops-ui/src/pages/integration/batch-link-up/detail/components/SingleTablePreviewModal.tsx',
  'data-ops-ui/src/pages/integration/batch-link-up/detail/hooks/useDataSourceColumns.ts',
  'data-ops-ui/src/pages/integration/batch-link-up/detail/hooks/useDataSourceTables.ts',
]);

const IMPORT_LITERAL =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\bjest\.(?:mock|doMock|requireActual)\s*\(\s*)['"`]([^'"`]+)['"`]/g;

function resolvesToLegacyModule(fromFile, specifier) {
  let target;
  if (specifier.startsWith('@/')) {
    target = 'data-ops-ui/src/' + specifier.slice(2);
  } else if (specifier.startsWith('.')) {
    target = path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), specifier));
  } else {
    // A bare import would not resolve through the project's @/ alias.
    return false;
  }
  return target === LEGACY_MODULE || target === LEGACY_MODULE + '.ts';
}

function legacyImports(file, source) {
  const matches = [];
  for (const match of source.matchAll(IMPORT_LITERAL)) {
    if (resolvesToLegacyModule(file, match[1])) matches.push(match[1]);
  }
  return matches;
}

test('Data Source legacy imports are recognized in relative and alias forms', () => {
  const file = 'data-ops-ui/src/services/data-source/api.ts';
  for (const expression of [
    "import { old } from './legacy';",
    "export { old } from './legacy.ts';",
    "await import('@/services/data-source/legacy');",
    "const old = require('@/services/data-source/legacy.ts');",
    "jest.mock('./legacy');",
  ]) {
    assert.equal(legacyImports(file, expression).length, 1, expression);
  }
  assert.deepEqual(legacyImports(file, "import { current } from './index';"), []);
  assert.deepEqual(legacyImports(
    'data-ops-ui/src/pages/data-source/view.tsx',
    "import old from '../../services/data-source/legacy';"), ['../../services/data-source/legacy']);
});

function unexpectedImports(sources) {
  const violations = [];
  const allImporters = [];
  for (const [file, contents] of sources) {
    for (const specifier of legacyImports(file, contents)) {
      allImporters.push(file);
      if (!EXISTING_CONSUMERS.has(file)) {
        violations.push(file + ': ' + specifier);
      }
    }
  }
  return { violations, allImporters };
}

test('new Data Source legacy consumers are not allowed', () => {
  const future = 'data-ops-ui/src/pages/new-feature/example.tsx';
  const result = unexpectedImports([
    [future, "import { fetchDataSourceAll } from '@/services/data-source/legacy';"],
  ]);
  assert.deepEqual(result.violations, [
    future + ': @/services/data-source/legacy',
  ]);
});

test('live compatibility adapter cannot be removed or gain new frontend importers', () => {
  const tracked = execFileSync('git', ['ls-files', '-z', 'data-ops-ui'], {
    encoding: 'utf8',
  }).split('\\0'.replace('\\0', '\0')).filter(Boolean);
  const sources = tracked
    .filter(file => /\\.(?:[cm]?js|jsx|ts|tsx)$/.test(file) && existsSync(file))
    .map(file => [file, readFileSync(file, 'utf8')]);
  const { violations, allImporters } = unexpectedImports(sources);
  assert.deepEqual(violations, [],
    'new Data Source legacy imports must be migrated to the current API:\\n' +
      violations.join('\\n'));
  if (allImporters.length > 0) {
    assert.equal(existsSync(LEGACY_MODULE + '.ts'), true,
      'legacy.ts is still required by existing envelope-response consumers');
  }
});
