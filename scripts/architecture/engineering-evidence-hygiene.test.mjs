import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const read = (name) => readFileSync(name, 'utf8');
const PROPOSALS = [
  '.zcode/plans/plan-sess_36a3068e-4352-4d63-8388-d516f3f18f85.md',
  '.zcode/plans/plan-sess_a18ccd05-9ad6-47e1-9223-c72e865f530c.md',
  '.zcode/plans/plan-sess_b1fff9ab-dc67-4cd5-822f-1e6dca9c139d.md',
];

test('captured, stale TypeScript stdout does not return to the checked-in frontend', () => {
  assert.equal(existsSync('data-ops-ui/tsc-output.txt'), false);
  const tracked = execFileSync('git', ['ls-files', '--', 'data-ops-ui/tsc-output.txt'],
    { encoding: 'utf8' });
  assert.equal(tracked.trim(), '');
  assert.ok(read('data-ops-ui/.gitignore').includes('/tsc-output.txt'));
});

test('canonical TypeScript debt gate continues running fresh tsc against JSON debt baseline', () => {
  assert.ok(existsSync('data-ops-ui/scripts/type-baseline.json'));
  const checker = read('data-ops-ui/scripts/check-type-baseline.mjs');
  assert.ok(checker.includes('node_modules/typescript/bin/tsc'));
  assert.ok(checker.includes('type-baseline.json'));
  assert.ok(!checker.includes('tsc-output.txt'));
  const packageFile = JSON.parse(read('data-ops-ui/package.json'));
  assert.equal(packageFile.scripts['check:types'], 'node scripts/check-type-baseline.mjs');
  const workflow = read('.github/workflows/architecture-checks.yml');
  assert.ok(workflow.includes('node scripts/check-type-baseline.mjs'));
});

test('historical design and Flyway-rewrite proposals remain evidence, not execution input', () => {
  const guide = read('docs/README.md');
  const classification = read('docs/product/LEGACY_DOC_INDEX.md');
  for (const proposal of PROPOSALS) {
    assert.equal(existsSync(proposal), true, proposal);
    const basename = proposal.split('/').at(-1);
    assert.ok(guide.includes(basename), basename);
    assert.ok(classification.includes(basename), basename);
  }
  assert.ok(guide.includes('严禁') || guide.includes('不得'));
  assert.ok(classification.includes('Flyway SQL'));
});

test('the existing migration and independent legacy deployment guards are retained', () => {
  assert.ok(existsSync('scripts/db/check-migration-history.mjs'));
  assert.ok(existsSync('scripts/architecture/framework-legacy.test.mjs'));
  const workflow = read('.github/workflows/architecture-checks.yml');
  assert.ok(workflow.includes('scripts/db/check-migration-history.mjs'));
});

test('documentation navigation links current owners without rewriting Product Truth', () => {
  const doc = read('docs/README.md');
  for (const target of [
    'docs/product/README.md',
    'docs/product/DOCUMENT_GOVERNANCE.md',
    'docs/product/LEGACY_DOC_INDEX.md',
    'docs/release/RELEASING.md',
    'docs/engineering/ci-impact-validation.md',
    'docs/engineering/code-cleanliness-p1-closeout.md',
  ]) {
    assert.equal(existsSync(target), true, target);
  }
  assert.ok(doc.includes('ACCEPTED'));
  assert.ok(doc.includes('APPROVED'));
  assert.ok(doc.includes('Historical') || doc.includes('历史'));
});

test('engineering README matches shipped frontend build, type and dev script entrypoints', () => {
  const packageFile = JSON.parse(read('data-ops-ui/package.json'));
  const readme = read('data-ops-ui/README.md');
  assert.ok(packageFile.scripts['start:dev']);
  assert.ok(packageFile.scripts['check:types']);
  assert.ok(readme.includes('npm run start:dev'));
  assert.ok(readme.includes('npm run check:types'));
  assert.ok(readme.includes('npm run build'));
});
