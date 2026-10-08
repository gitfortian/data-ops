import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

// All eight live Data Source compatibility imports have been migrated.
// These two obsolete entry points must not be reintroduced; callers now use
// @/services/data-source, whose HttpUtils.getData/postData unwrap business responses.
const REMOVED_MODULES = [
  'data-ops-ui/src/services/data-source/legacy',
  'data-ops-ui/src/pages/data-source/service',
];
const REMOVED_PATHS = REMOVED_MODULES.map((module) => module + '.ts');

const IMPORT_LITERAL =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\bjest\.(?:mock|doMock|requireActual)\s*\(\s*)['"`]([^'"`]+)['"`]/g;

function resolvesToRemovedModule(fromFile, specifier) {
  let target;
  if (specifier.startsWith('@/')) {
    target = 'data-ops-ui/src/' + specifier.slice(2);
  } else if (specifier.startsWith('.')) {
    target = path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), specifier));
  } else {
    return false;
  }
  return REMOVED_MODULES.some((module) =>
    target === module || target === module + '.ts');
}

function removedImports(fromFile, source) {
  const matches = [];
  for (const match of source.matchAll(IMPORT_LITERAL)) {
    if (resolvesToRemovedModule(fromFile, match[1])) {
      matches.push(match[1]);
    }
  }
  return matches;
}

test('old Data Source entry points cannot be restored', () => {
  for (const file of REMOVED_PATHS) {
    assert.equal(existsSync(file), false,
      file + ' is removed after the final frontend migration');
  }
});

test('legacy and page-level facade imports are detected in static and dynamic forms', () => {
  const legacy = 'data-ops-ui/src/services/data-source/api.ts';
  const formerFacade = 'data-ops-ui/src/pages/data-source/index.tsx';
  for (const expression of [
    "import { fetchDataSourceAll } from './legacy';",
    "export * from './legacy.ts';",
    "await import('@/services/data-source/legacy');",
    "const old = require('@/services/data-source/legacy.ts');",
    "jest.mock('./legacy');",
  ]) {
    assert.equal(removedImports(legacy, expression).length, 1, expression);
  }
  for (const expression of [
    "import { fetchDataSourcePage } from '@/pages/data-source/service';",
    "export * from './service';",
    "await import('@/pages/data-source/service.ts');",
    "const old = require('./service.ts');",
    "jest.doMock('./service');",
  ]) {
    assert.equal(removedImports(formerFacade, expression).length, 1, expression);
  }
  assert.deepEqual(removedImports(legacy, "import { current } from './index';"), []);
  assert.deepEqual(removedImports(formerFacade,
    "import { current } from '@/services/data-source';"), []);
  assert.deepEqual(removedImports(formerFacade,
    "import { next } from './components/DriverManager/service';"), []);
});

test('removed Data Source entry points have no new frontend consumers', () => {
  const tracked = execFileSync('git', ['ls-files', '-z', 'data-ops-ui'], {
    encoding: 'utf8',
  }).split(String.fromCharCode(0)).filter(Boolean);
  const sourceExtensions = ['.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs'];
  const violations = [];
  for (const file of tracked) {
    if (!sourceExtensions.some(ext => file.endsWith(ext)) || !existsSync(file)) {
      continue;
    }
    for (const specifier of removedImports(file, readFileSync(file, 'utf8'))) {
      violations.push(file + ': ' + specifier);
    }
  }
  assert.deepEqual(violations, [],
    'Data Source legacy and page facade imports must use the modern API:' +
      String.fromCharCode(10) + violations.join(String.fromCharCode(10)));
});
