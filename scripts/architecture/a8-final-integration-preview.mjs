#!/usr/bin/env node
/**
 * A0–A8 read-only-in-the-remote integration preview.
 *
 * Fetches exact reviewed PR heads and simulates their sequential integration in a
 * detached TEMPORARY local worktree. Never calls GitHub write endpoints or git push.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPOSITORY = 'gitfortian/data-ops';
export const ARCHITECTURE_SERIES = Object.freeze([
  { number: 370, branch: 'architecture/yak-principles-boot-guard', phase: 'A0' },
  { number: 373, branch: 'architecture/yak-a1-module-topology', phase: 'A1' },
  { number: 375, branch: 'architecture/yak-a2-persistence-wiring-split', phase: 'A2' },
  { number: 385, branch: 'architecture/yak-a2-2-persistence-consumer-contract', phase: 'A2.2' },
  { number: 379, branch: 'architecture/yak-a3-project-context-runtime', phase: 'A3' },
  { number: 389, branch: 'architecture/yak-a3-2-project-access-cleanup', phase: 'A3.2' },
  { number: 380, branch: 'architecture/yak-a4-flyway-ownership-guard', phase: 'A4' },
  { number: 387, branch: 'architecture/yak-a4-2-flyway-startup-order-contract', phase: 'A4.2' },
  { number: 377, branch: 'architecture/yak-a5-frontend-http-boundary', phase: 'A5' },
  { number: 391, branch: 'architecture/yak-a5-2-frontend-transport-contract', phase: 'A5.2' },
  { number: 392, branch: 'architecture/yak-a6-1-engine-spi-compatibility', phase: 'A6.1' },
  { number: 395, branch: 'architecture/yak-a8-integration-acceptance-preview', phase: 'A7' },
  { number: 409, branch: 'architecture/yak-a8-1-project-rbac-real-env', phase: 'A7.1' },
  { number: 417, branch: 'architecture/yak-a8-1-file-reactor-retirement', phase: 'A8.1a' },
  { number: 420, branch: 'architecture/yak-a8-1b-bom-parent-decoupling', phase: 'A8.1b' },
  { number: 423, branch: 'architecture/yak-a8-1c-common-security-contract', phase: 'A8.1c' },
  { number: 461, branch: 'refactor/a8-2n-p-security-runtime-batch', phase: 'A8.2' },
]);

const LABELS = ['do-not-merge', 'architecture-refactor'];
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export function validateArchitecturePull(pr, expected) {
  const reasons = [];
  if (pr?.number !== expected.number) reasons.push('PR number changed');
  if (pr?.state !== 'open' || pr?.merged_at) reasons.push('PR must remain open and unmerged');
  if (pr?.draft !== true) reasons.push('PR must remain draft');
  if (pr?.base?.ref !== 'main' || pr?.base?.repo?.full_name !== REPOSITORY) {
    reasons.push('PR must target data-ops/main');
  }
  if (pr?.head?.repo?.full_name !== REPOSITORY || pr?.head?.ref !== expected.branch) {
    reasons.push('PR head repository/branch drifted');
  }
  if (!/^[0-9a-f]{40}$/.test(pr?.head?.sha ?? '')) reasons.push('Missing immutable Git head SHA');
  const labels = new Set((pr?.labels ?? []).map(x => x.name));
  for (const label of LABELS) if (!labels.has(label)) reasons.push('Required guard label missing: ' + label);
  if (!pr?.title?.startsWith('⛔【暂勿合并｜架构重构 ')) {
    reasons.push('Missing protected architecture PR title');
  }
  if (reasons.length) throw new Error('PR #' + expected.number + ': ' + reasons.join('; '));
  return {
    phase: expected.phase, number: pr.number, branch: pr.head.ref,
    sha: pr.head.sha, url: pr.html_url ?? 'https://github.com/' + REPOSITORY + '/pull/' + pr.number,
  };
}

export function validateIntegrationSeries(series = ARCHITECTURE_SERIES) {
  const numbers = new Set(), branches = new Set();
  if (series.length !== 17) throw new Error('A0–A8 architecture series must include exactly 17 reviewed PRs');
  for (const spec of series) {
    if (!Number.isSafeInteger(spec.number) || spec.number < 1 ||
        !/^(?:architecture\/yak-|refactor\/a8-2n-p-)[a-z0-9-]+$/.test(spec.branch)) {
      throw new Error('Invalid architecture PR specification');
    }
    if (numbers.has(spec.number) || branches.has(spec.branch)) {
      throw new Error('Duplicate architecture PR or branch');
    }
    numbers.add(spec.number);
    branches.add(spec.branch);
  }
  return series;
}

export function integrationSummary(report) {
  const lines = [
    '# A0–A8 Integration preview (never merged to main)',
    '',
    '- Base main commit: ' + report.baseSha,
    '- Preview tree commit: ' + (report.integratedSha || 'NOT COMPLETED'),
    '- State: ' + (report.failed ? 'FAILED (do not merge)' : report.integratedSha ? 'MERGE SIMULATION COMPLETE' : 'PENDING'),
    '- Preview only: all local merge commits exist solely in a detached temporary worktree.',
    '',
    '| Phase | PR | Reviewed head SHA | Result |',
    '|---|---|---|---|',
    ...report.prs.map(pr =>
      '| ' + pr.phase + ' | #' + pr.number + ' | \`' + pr.sha + '\` | ' + (pr.result || 'not executed') + ' |'),
  ];
  if (report.failed) lines.push('', '**Failure:** ' + report.failed);
  lines.push('', 'This is a merge simulation, not deployment or production data migration.');
  return lines.join('\n') + '\n';
}

function git(args, cwd = ROOT) {
  return execFileSync('git', args, {
    cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
    maxBuffer: 8 * 1024 * 1024,
  }).trim();
}

function persist(report, reportPath) {
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
  const markdownPath = reportPath.replace(/\.json$/, '.md');
  fs.writeFileSync(markdownPath, integrationSummary(report));
  if (process.env.GITHUB_STEP_SUMMARY &&
      (report.failed || (report.prs.length > 0 && report.prs.every(row => row.result === 'merged in temporary preview')))) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, integrationSummary(report));
  }
}

async function fetchPull(number, token) {
  const response = await fetch('https://api.github.com/repos/' + REPOSITORY + '/pulls/' + number, {
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: 'Bearer ' + token,
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'data-ops-a8-read-only-integration-preview',
    },
  });
  if (!response.ok) throw new Error('Cannot read PR #' + number + ': HTTP ' + response.status);
  return response.json();
}

/** Fetch-only, no remote writes; builds disposable *local* merge commits. */
export async function prepareIntegration({ worktree, reportPath, token }) {
  validateIntegrationSeries();
  if (!token) throw new Error('Read-only GITHUB_TOKEN must be present');
  if (process.env.GITHUB_REPOSITORY !== REPOSITORY) {
    throw new Error('A0–A8 preview must run in ' + REPOSITORY);
  }
  const absolute = path.resolve(worktree);
  const report = { repository: REPOSITORY, baseSha: null, integratedSha: null, prs: [], failed: null };
  try {
    if (fs.existsSync(absolute)) throw new Error('Refusing to overwrite existing preview directory: ' + absolute);
    const pulls = [];
    for (const expected of ARCHITECTURE_SERIES) {
      const pr = await fetchPull(expected.number, token);
      pulls.push(validateArchitecturePull(pr, expected));
    }
    report.prs = pulls.map(pr => ({ ...pr, result: 'pending' }));
    // Snapshots the current main. PR commit identities are fetched and checked exactly.
    git(['fetch', '--no-tags', 'origin', 'main']);
    report.baseSha = git(['rev-parse', 'FETCH_HEAD']);
    if (!/^[0-9a-f]{40}$/.test(report.baseSha)) throw new Error('Invalid main snapshot');
    git(['worktree', 'add', '--detach', absolute, report.baseSha]);
    const safeGit = ['-c', 'user.name=Data-Ops A0–A8 Preview', '-c', 'user.email=a8-preview@localhost'];
    for (let i = 0; i < pulls.length; i++) {
      const pr = pulls[i];
      git(['fetch', '--no-tags', 'origin', 'refs/pull/' + pr.number + '/head']);
      const fetched = git(['rev-parse', 'FETCH_HEAD']);
      if (fetched !== pr.sha) throw new Error('PR #' + pr.number + ' moved during preview; retry with a new snapshot');
      // Local-only merge commit; never attempts git push or alters the checked-out main.
      git([...safeGit, '-C', absolute, 'merge', '--no-edit', '--no-ff', fetched]);
      report.prs[i].result = 'merged in temporary preview';
      report.integratedSha = git(['-C', absolute, 'rev-parse', 'HEAD']);
      persist(report, reportPath);
    }
    return report;
  } catch (error) {
    const detail = (error.stderr ? String(error.stderr).slice(-1800) : error.message) || String(error);
    let conflicted = '';
    if (fs.existsSync(path.join(absolute, '.git'))) {
      try { conflicted = git(['-C', absolute, 'diff', '--name-only', '--diff-filter=U']); } catch {}
    }
    report.failed = detail + (conflicted ? '\nConflicting files: ' + conflicted : '');
    persist(report, reportPath);
    throw new Error(report.failed);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command, worktree, reportPath] = process.argv.slice(2);
  if (command !== 'prepare' || !worktree || !reportPath) {
    console.error('Usage: node scripts/architecture/a8-final-integration-preview.mjs prepare <new-temp-dir> <report.json>');
    process.exitCode = 2;
  } else {
    prepareIntegration({ worktree, reportPath, token: process.env.GITHUB_TOKEN })
      .then(report => console.log('A0–A8 local merge preview ready at ' + worktree
        + ' (head ' + report.integratedSha + ', base ' + report.baseSha + ')'))
      .catch(error => { console.error('::error::' + error.message); process.exitCode = 1; });
  }
}
