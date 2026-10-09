import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  REPOSITORY, ARCHITECTURE_SERIES,
  validateArchitecturePull, validateIntegrationSeries, integrationSummary, verifyRollback,
} from './a8-final-integration-preview.mjs';

function pull(spec = ARCHITECTURE_SERIES[0]) {
  return {
    number: spec.number, title: '⛔【暂勿合并｜架构重构 A0】Protected',
    state: 'open', merged_at: null, draft: true,
    base: { ref: 'main', repo: { full_name: REPOSITORY } },
    head: { ref: spec.branch, repo: { full_name: REPOSITORY }, sha: 'a'.repeat(40) },
    labels: [{ name: 'do-not-merge' }, { name: 'architecture-refactor' }],
    html_url: 'https://github.com/' + REPOSITORY + '/pull/' + spec.number,
  };
}

test('A0–A8 inventory has unique reviewed PR numbers and correct branches', () => {
  assert.equal(validateIntegrationSeries().length, 17);
  assert.deepEqual(ARCHITECTURE_SERIES.map(s => s.phase),
    ['A0', 'A1', 'A2', 'A2.2', 'A3', 'A3.2', 'A4', 'A4.2',
     'A5', 'A5.2', 'A6.1', 'A7', 'A7.1', 'A8.1a', 'A8.1b', 'A8.1c', 'A8.2']);
  assert.deepEqual(ARCHITECTURE_SERIES.map(x => [x.number, x.branch]), [
    [370, 'architecture/yak-principles-boot-guard'],
    [373, 'architecture/yak-a1-module-topology'],
    [375, 'architecture/yak-a2-persistence-wiring-split'],
    [385, 'architecture/yak-a2-2-persistence-consumer-contract'],
    [379, 'architecture/yak-a3-project-context-runtime'],
    [389, 'architecture/yak-a3-2-project-access-cleanup'],
    [380, 'architecture/yak-a4-flyway-ownership-guard'],
    [387, 'architecture/yak-a4-2-flyway-startup-order-contract'],
    [377, 'architecture/yak-a5-frontend-http-boundary'],
    [391, 'architecture/yak-a5-2-frontend-transport-contract'],
    [392, 'architecture/yak-a6-1-engine-spi-compatibility'],
    [395, 'architecture/yak-a7-integration-acceptance-preview'],
    [409, 'architecture/yak-a7-1-project-rbac-real-env'],
    [417, 'architecture/yak-a8-1-file-reactor-retirement'],
    [420, 'architecture/yak-a8-1b-bom-parent-decoupling'],
    [423, 'architecture/yak-a8-1c-common-security-contract'],
    [461, 'refactor/a8-2n-p-security-runtime-batch'],
  ]);
  assert.equal(ARCHITECTURE_SERIES.at(-1).number, 461);
  assert.equal(ARCHITECTURE_SERIES.at(-1).branch,
    'refactor/a8-2n-p-security-runtime-batch');
  assert.throws(() => validateIntegrationSeries(ARCHITECTURE_SERIES.slice(1)), /exactly 17/);
  assert.throws(() => validateIntegrationSeries([
    ...ARCHITECTURE_SERIES.slice(0, 16), ARCHITECTURE_SERIES[0],
  ]), /Duplicate/);
});

test('live GitHub metadata is pinned to a verified exact head commit', () => {
  const result = validateArchitecturePull(pull(), ARCHITECTURE_SERIES[0]);
  assert.equal(result.number, 370);
  assert.equal(result.sha, 'a'.repeat(40));
  assert.equal(result.phase, 'A0');
  assert.match(result.url, /pull\/370$/);
});

test('fail closed on PR that was merged, published, retargeted, replaced or stripped of protection', () => {
  const spec = ARCHITECTURE_SERIES[0];
  const mutations = [
    { draft: false },
    { state: 'closed' },
    { merged_at: '2026-10-08T10:00:00Z' },
    { base: { ref: 'release', repo: { full_name: REPOSITORY } } },
    { head: { ref: 'main', repo: { full_name: REPOSITORY }, sha: 'a'.repeat(40) } },
    { head: { ref: spec.branch, repo: { full_name: 'another/repo' }, sha: 'a'.repeat(40) } },
    { head: { ref: spec.branch, repo: { full_name: REPOSITORY }, sha: 'moving' } },
    { labels: [{ name: 'architecture-refactor' }] },
    { labels: [{ name: 'do-not-merge' }] },
    { title: 'Merge me now' },
    { number: 999 },
  ];
  for (const mutation of mutations) {
    assert.throws(() => validateArchitecturePull({ ...pull(spec), ...mutation }, spec),
      /PR #370:/, JSON.stringify(mutation));
  }
});

test('detached preview report records real SHA and failure without asserting CI succeeded', () => {
  const first = { ...validateArchitecturePull(pull(), ARCHITECTURE_SERIES[0]), result: 'merged in temporary preview' };
  const content = integrationSummary({
    baseSha: 'b'.repeat(40), integratedSha: null,
    failed: 'local conflict detected', prs: [first],
  });
  assert.match(content, /FAILED \(do not merge\)/);
  assert.match(content, /local conflict detected/);
  assert.match(content, /never merged to main/);
  assert.match(content, /370/);
  assert.doesNotMatch(content, /CI PASSED|safe to merge/);
});

test('A0–A8 requires mutually isolated MySQL and PostgreSQL integration profiles', () => {
  const workflow = readFileSync('.github/workflows/architecture-checks.yml', 'utf8');
  assert.match(workflow,
    /env -u ARCHITECTURE_POSTGRESQL_URL SPRING_PROFILES_ACTIVE=mysql\s*\\\s*bash \.\/mvnw -B -ntp verify/);
  assert.match(workflow,
    /env -u ARCHITECTURE_MYSQL_URL SPRING_PROFILES_ACTIVE=postgresql\s*\\\s*bash \.\/mvnw -B -ntp -pl data-ops-boot -am/);
  assert.match(workflow, /-Dtest=PostgresqlStorageSmokeTest/);
  assert.match(workflow, /-Dsurefire\.failIfNoSpecifiedTests=false test/);
});

test('A8.2 integrated PR must retain exact guarded head branch and be last', () => {
  const final = ARCHITECTURE_SERIES.at(-1);
  const valid = pull(final);
  assert.equal(validateArchitecturePull(valid, final).number, 461);
  assert.throws(() => validateArchitecturePull({
    ...valid, head: {...valid.head, ref: 'main'}}, final), /branch drifted/);
  assert.throws(() => validateIntegrationSeries(
    [...ARCHITECTURE_SERIES.slice(0, -1),
      {...final, branch: 'untrusted/random'}]), /Invalid architecture PR/);
});

test('A0–A8 rollback fails closed without all seventeen verified merge records', () => {
  const dir = mkdtempSync(join(tmpdir(), 'a8-rollback-contract-'));
  const reportPath = join(dir, 'report.json');
  try {
    writeFileSync(reportPath, JSON.stringify({
      baseSha: 'a'.repeat(40), integratedSha: 'b'.repeat(40), prs: [],
    }));
    assert.throws(() => verifyRollback({worktree: dir, reportPath}),
      /complete exact-SHA A0–A8 integration record/);
  } finally {
    rmSync(dir, {recursive: true, force: true});
  }
});

test('A0–A8 release preview must verify rollback and never push architecture branches', () => {
  const workflow = readFileSync('.github/workflows/architecture-checks.yml', 'utf8');
  assert.match(workflow, /a8-final-integration-preview\.mjs rollback/);
  assert.match(workflow, /persist-credentials: false/);
  assert.match(workflow, /permissions:\s+contents: read\s+pull-requests: read/);
  assert.doesNotMatch(workflow, /git push|gh pr merge|git reset --hard origin\/main/);
});
