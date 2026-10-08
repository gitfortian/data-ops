import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { digest } from './scenario-evidence.mjs';

const dimensions = ['candidateValidity', 'factSupport', 'referenceMatch', 'clarification', 'taskCompletion'];
const distribution = values => {
  const sorted = [...values].sort((a, b) => a - b);
  return { samples: sorted.length, min: sorted[0] ?? null, median: sorted.length ? sorted[Math.floor((sorted.length - 1) / 2)] : null,
    p95: sorted.length ? sorted[Math.ceil(sorted.length * .95) - 1] : null, max: sorted.at(-1) ?? null };
};
const measured = v => Number.isFinite(v) && v >= 0;

export function summarizeEvaluation(report, reviews = null) {
  if (!report || !['real', 'offline'].includes(report.mode) || !Array.isArray(report.results) || !report.suiteHash) throw new Error('INVALID_REPORT');
  const reportHash = digest(JSON.stringify(report));
  const attempts = new Map();
  for (const item of report.results) {
    const key = `${item.caseId}:${item.repeat}`;
    if (attempts.has(key)) throw new Error('DUPLICATE_ATTEMPT');
    attempts.set(key, item);
  }
  if (reviews != null && (reviews.version !== 'F-030-review-v1' || reviews.reportHash !== reportHash || !Array.isArray(reviews.items))) throw new Error('REVIEW_REPORT_MISMATCH');
  const ratings = Object.fromEntries(dimensions.map(key => [key, []]));
  const seen = new Set();
  const edits = [], adopted = [], saved = [];
  let evidenceReferenced = 0;
  for (const review of reviews?.items ?? []) {
    const key = `${review.caseId}:${review.repeat}`, item = attempts.get(key);
    if (seen.has(key) || report.mode !== 'real' || !item?.turnId || !item.outputHash || !item.traceHash
        || review.turnId !== item.turnId || review.outputHash !== item.outputHash || review.traceHash !== item.traceHash
        || !['AWAITING_EXPERT_REVIEW', 'RUN_ERROR', 'AWAITING_HITL_REVIEW'].includes(item.status)
        || typeof review.reviewer !== 'string' || !review.reviewer.trim()
        || !Array.isArray(review.sourceAuditRefs) || review.sourceAuditRefs.some(v => typeof v !== 'string' || !v.trim())) throw new Error('INVALID_REVIEW_BINDING');
    seen.add(key);
    if (review.sourceAuditRefs.length) evidenceReferenced++;
    for (const key of dimensions) {
      const score = review.scores?.[key];
      if (score == null) continue;
      if (![0, 1, 2].includes(score)) throw new Error('INVALID_SCORE');
      if (key === 'candidateValidity' && (!review.sourceAuditRefs.length || !(item.delivery?.candidateCount > 0))) throw new Error('MISSING_CANDIDATE_EVIDENCE');
      ratings[key].push(score);
    }
    if (review.manualEdits != null) {
      if (!Number.isSafeInteger(review.manualEdits) || review.manualEdits < 0) throw new Error('INVALID_EDIT_COUNT');
      edits.push(review.manualEdits);
    }
    for (const [key, values] of [['adopted', adopted], ['saved', saved]]) {
      const count = review[key];
      if (count == null) continue;
      if (!Number.isSafeInteger(count) || count < 0 || !review.sourceAuditRefs.length
          || !Number.isSafeInteger(item.delivery?.candidateCount) || count > item.delivery.candidateCount) throw new Error('INVALID_ADOPTION_EVIDENCE');
      values.push(count);
    }
    if (review.saved != null && (review.adopted == null || review.saved > review.adopted)) throw new Error('SAVE_EXCEEDS_ADOPTION');
  }
  const statuses = [...new Set(report.results.map(v => v.status))];
  return { reportHash, suiteHash: report.suiteHash, mode: report.mode, totalCases: report.results.length,
    statusCounts: Object.fromEntries(statuses.map(status => [status, report.results.filter(v => v.status === status).length])),
    elapsedByStatus: Object.fromEntries(statuses.map(status => [status, distribution(report.results.filter(v => v.status === status && measured(v.elapsedMs)).map(v => v.elapsedMs))])),
    tokens: { ...distribution(report.results.filter(v => measured(v.totalTokens)).map(v => v.totalTokens)),
      totalKnown: report.results.filter(v => measured(v.totalTokens)).reduce((n, v) => n + v.totalTokens, 0),
      partialUsageCases: report.results.filter(v => v.usageStatus === 'PARTIAL').length,
      missing: report.results.filter(v => !measured(v.totalTokens)).length },
    generated: { observedCases: report.results.filter(v => Number.isSafeInteger(v.delivery?.candidateCount)).length,
      candidates: report.results.reduce((n, v) => n + (v.delivery?.candidateCount ?? 0), 0) },
    humanReview: { recorded: seen.size, missing: report.results.length - seen.size, evidenceReferenced,
      scores: Object.fromEntries(dimensions.map(key => [key, { rated: ratings[key].length, missing: report.results.length - ratings[key].length,
        zero: ratings[key].filter(v => v === 0).length, partial: ratings[key].filter(v => v === 1).length, sufficient: ratings[key].filter(v => v === 2).length }])),
      manualEdits: distribution(edits), adopted: { reportedCases: adopted.length, candidates: adopted.reduce((a, b) => a + b, 0) },
      saved: { reportedCases: saved.length, candidates: saved.reduce((a, b) => a + b, 0) }, auditVerification: 'PENDING_EXTERNAL_REVIEW' },
    cost: { status: 'UNKNOWN', reason: 'No verified versioned pricing and complete usage bound to this report.' },
    benefit: 'NOT_MEASURED', acceptanceStatus: 'PENDING_EXPERT_AND_SOURCE_REVIEW' };
}

export async function main(args = process.argv.slice(2)) {
  const option = key => args.find(v => v.startsWith(`${key}=`))?.slice(key.length + 1);
  if (!option('--report')) throw new Error('MISSING_REPORT');
  const report = JSON.parse(await readFile(resolve(option('--report')), 'utf8'));
  const reviews = option('--reviews') ? JSON.parse(await readFile(resolve(option('--reviews')), 'utf8')) : null;
  const summary = summarizeEvaluation(report, reviews);
  const output = resolve(option('--out') ?? '.task-ai-evaluation/summary.json');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(summary, null, 2) + '\n');
  console.log(`Summary: ${output}`);
  return summary;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error('Evaluation summary failed; check report/review identity and scores.'); process.exitCode = 1; });
}
