import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

import {
  RETIRED_ARTIFACTS, PROTECTED_EVIDENCE, isOneOffResidue,
  inspectPaths, findDuplicateImportLines, inspectSources,
} from './final-cleanliness-review.mjs';

test('historical one-off script classification never sweeps tracked data resources', () => {
  assert.equal(isOneOffResidue('data/architecture-metric-edits.py'), true);
  assert.equal(isOneOffResidue('data/architecture-mysql-test.json'), true);
  assert.equal(isOneOffResidue('data/local-jest.cjs'), true);
  assert.equal(isOneOffResidue('.zcode/tmp/jest.config.cjs'), true);
  assert.equal(isOneOffResidue('data/resources/projects/1/.asf.yaml'), false);
  assert.equal(isOneOffResidue('data/resources/projects/1/backup.py'), false);
  assert.equal(isOneOffResidue('.zcode/plans/historical.md'), false);
  assert.equal(isOneOffResidue('scripts/architecture/backend-p2-reachability-audit.mjs'), false);
});

test('inventory identifies both reintroduced residue and lost protected evidence', () => {
  const files = [...PROTECTED_EVIDENCE, ...RETIRED_ARTIFACTS.slice(0, 2),
    'data/architecture-modeling-edits.py'];
  const report = inspectPaths(files);
  assert.equal(report.passesGuard, false);
  assert.deepEqual(report.reintroduced, RETIRED_ARTIFACTS.slice(0, 2));
  assert.deepEqual(report.missingEvidence, []);
  assert.deepEqual(report.oneOffResidues, ['data/architecture-modeling-edits.py']);
  const removedEvidence = inspectPaths(PROTECTED_EVIDENCE.slice(1));
  assert.equal(removedEvidence.passesGuard, false);
  assert.deepEqual(removedEvidence.missingEvidence, [PROTECTED_EVIDENCE[0]]);
});

test('clean tracked paths retain the independent release and evidence sources', () => {
  const report = inspectPaths([
    ...PROTECTED_EVIDENCE, 'data-ops-ui/src/services/data-source/api.ts',
    'data-ops-ui/src/services/data-source/index.ts',
  ]);
  assert.equal(report.passesGuard, true);
  assert.deepEqual(report.reintroduced, []);
  assert.deepEqual(report.oneOffResidues, []);
  assert.deepEqual(report.dataPaths, ['data/resources/projects/1/.asf.yaml']);
});

test('duplicate import scanner only reports identical single-line declarations', () => {
  const source = [
    "import { getA } from '@/services/a';",
    'const label = "import { getA } from \'@/services/a\';";',
    "import { getB } from '@/services/a';",
    "import { getA } from '@/services/a'",
    "import type { T } from '@/services/types';",
    "import type { T } from '@/services/types';",
    "import {",
    "  getA",
    "} from '@/services/a';",
  ].join('\n');
  assert.deepEqual(findDuplicateImportLines('data-ops-ui/src/pages/metric/index.tsx', source), [
    { path: 'data-ops-ui/src/pages/metric/index.tsx',
      firstLine: 1, line: 4, text: "import { getA } from '@/services/a'" },
    { path: 'data-ops-ui/src/pages/metric/index.tsx',
      firstLine: 5, line: 6, text: "import type { T } from '@/services/types';" },
  ]);
});

test('duplicate import review excludes historical documents and test files', () => {
  const source = "import 'a';\nimport 'a';";
  assert.deepEqual(findDuplicateImportLines('docs/test/example.md', source), []);
  assert.deepEqual(findDuplicateImportLines('data-ops-ui/src/example.test.ts', source), []);
  assert.deepEqual(findDuplicateImportLines('data-ops-ui/src/example.ts', source),
    [{ path: 'data-ops-ui/src/example.ts', firstLine: 1, line: 2, text: "import 'a';" }]);
});

test('production comment review separates traced and untraced markers without editing', () => {
  const files = ['data-ops-ui/src/pages/a.ts', 'data-ops-boot/src/main/java/Service.java',
    'docs/historical.md', 'data-ops-ui/src/pages/a.test.ts'];
  const textByFile = {
    [files[0]]: ["import 'a';", "import 'a';", '// TODO #345 retain old contract',
      '// FIXME no issue', 'const label = "/approval/todo";'].join('\n'),
    [files[1]]: '// HACK needs owner review',
  };
  const report = inspectSources(files, file => {
    if (!(file in textByFile)) throw Error('unexpected non-production read: ' + file);
    return textByFile[file];
  });
  assert.equal(report.totalMarkers, 3);
  assert.equal(report.untrackedMarkers, 2);
  assert.equal(report.duplicateImports, 1);
  assert.equal(report.samplesTruncated, false);
  assert.deepEqual(report.commentsByOwner['data-ops-ui'], { tracked: 1, needsReview: 1 });
  assert.deepEqual(report.commentsByOwner['data-ops-boot'], { tracked: 0, needsReview: 1 });
  assert.equal(report.samples.length, 3);
});

test('detail truncation is counted accurately, not assumed on small scans', () => {
  const file = 'data-ops-ui/src/pages/a.ts';
  const source = '// TODO first\n// FIXME second\n';
  const brief = inspectSources([file], () => source, 1);
  assert.equal(brief.samples.length, 1);
  assert.equal(brief.samplesTruncated, true);
  const complete = inspectSources([file], () => source, 3);
  assert.equal(complete.samplesTruncated, false);
});

test('the latest main still excludes proven placeholders and retains historical evidence', () => {
  const tracked = execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8' })
    .split('\0').filter(Boolean);
  const report = inspectPaths(tracked);
  assert.deepEqual(report.reintroduced, []);
  assert.deepEqual(report.oneOffResidues, []);
  assert.deepEqual(report.missingEvidence, []);
  assert.ok(report.passesGuard);
  for (const file of PROTECTED_EVIDENCE) assert.ok(existsSync(file), file);
  const ignore = readFileSync('.gitignore', 'utf8');
  assert.ok(ignore.includes('/.zcode/tmp/'));
  assert.ok(ignore.includes('/data/*.py'));
  assert.ok(ignore.includes('/data/*.cjs'));
});

test('existing coverage for retired frontend imports, migrations and source debt is preserved', () => {
  for (const file of [
    'scripts/architecture/backend-p2-reachability-audit.test.mjs',
    'scripts/architecture/engineering-evidence-hygiene.test.mjs',
    'scripts/architecture/p3-source-comment-debt-audit.test.mjs',
    'scripts/architecture/p3-document-entry-integrity.test.mjs',
    'scripts/architecture/realtime-sync-compat-retirement-guard.test.mjs',
    'scripts/architecture/page-service-shim-retirement-guard.test.mjs',
  ]) assert.equal(existsSync(file), true, file);
  const docs = readFileSync('docs/README.md', 'utf8');
  assert.ok(docs.includes('engineering/final-cleanliness-closeout.md'));
  assert.ok(readFileSync('docs/engineering/final-cleanliness-closeout.md', 'utf8')
    .includes('Evidence / Review'));
});
