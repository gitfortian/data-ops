import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

// Guard the one retired Data Source adapter without banning unrelated legacy compatibility.
const RETIRED_MODULE = 'data-ops-ui/src/services/data-source/legacy';
const IMPORT_LITERAL =
  /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|\bjest\.(?:mock|doMock|requireActual)\s*\(\s*)['"`]([^'"`]+)['"`]/g;

function resolvesToRetiredModule(fromFile, specifier) {
  let target;
  if (specifier.startsWith('@/')) {
    target = 'data-ops-ui/src/' + specifier.slice(2);
  } else if (specifier.startsWith('.')) {
    target = path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), specifier));
  } else {
    // A bare import would not resolve through the project's @/ alias.
    return false;
  }
  return target === RETIRED_MODULE || target === RETIRED_MODULE + '.ts';
}

function legacyImports(file, source) {
  const matches = [];
  for (const match of source.matchAll(IMPORT_LITERAL)) {
    if (resolvesToRetiredModule(file, match[1])) matches.push(match[1]);
  }
  return matches;
}

test('retired Data Source imports are recognized in relative and alias forms', () => {
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

test('retired Data Source compatibility adapter has no tracked frontend importers', () => {
  assert.equal(existsSync(RETIRED_MODULE + '.ts'), false,
    'retired Data Source legacy.ts must not be reintroduced');
  const tracked = execFileSync('git', ['ls-files', '-z', 'data-ops-ui'], {
    encoding: 'utf8',
  }).split('\0').filter(Boolean);
  const violations = [];
  for (const file of tracked) {
    if (!/\.(?:[cm]?js|jsx|ts|tsx)$/.test(file) || !existsSync(file)) continue;
    for (const specifier of legacyImports(file, readFileSync(file, 'utf8'))) {
      violations.push(file + ': ' + specifier);
    }
  }
  assert.deepEqual(violations, [], 'retired Data Source imports remain:\n' + violations.join('\n'));
});
