import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { validateSuite, scenarioSuiteFile, runRealCase, main } from './run-evaluation.mjs';
import { observeDelivery, scenarioTypes, validateScenarioBinding, digest } from './scenario-evidence.mjs';
import { readApiData } from './evidence-http.mjs';
import { summarizeEvaluation } from './summarize-evaluation.mjs';

const suite = validateSuite(JSON.parse(await readFile(scenarioSuiteFile, 'utf8')));
const clone = v => structuredClone(v);
const receipt = target => ({ kind: target.purpose, target: target[scenarioTypes[target.purpose].key],
  expectedDefinition: 'a'.repeat(64), skillHash: 'b'.repeat(64), skillVersion: 1, truncated: false, candidates: [{}], questions: [] });
const message = (target, v = receipt(target)) => `私有业务说明\n\`\`\`${scenarioTypes[target.purpose].marker}\n${JSON.stringify(v)}\n\`\`\``;
const continuation = target => ({ sessionId: 's1', turnId: 't1', status: 'COMPLETED', governanceTarget: target });
const history = target => [{ role: 'assistant', turnId: 't1', content: message(target) }];

test('all four scenes have fixed cases, multi-step cases stay manual, offline is network-free NOT_RUN', async () => {
  assert.equal(suite.cases.length, 20);
  assert.equal(suite.cases.filter(v => v.executionMode === 'manual').length, 5);
  for (const purpose of Object.keys(scenarioTypes)) {
    assert.ok(suite.cases.some(v => v.target.purpose === purpose && !v.allowed));
  }
  assert.throws(() => validateSuite({ ...suite, cases: suite.cases.slice(1) }));
  const invalid = clone(suite); invalid.cases.find(v => v.caseId === 'sm05').executionMode = 'single-turn';
  assert.throws(() => validateSuite(invalid));
  let calls = 0;
  for (const item of suite.cases.filter(v => v.executionMode === 'manual')) {
    assert.equal((await runRealCase(item, null, null, async () => { calls++; })).status, 'MANUAL_REQUIRED');
  }
  assert.equal(calls, 0);
  const temp = await mkdtemp(join(tmpdir(), 'agent-eval-test-'));
  try {
    const report = await main(['--suite=skill-scenarios', `--out=${join(temp, 'scenes.json')}`], {});
    assert.ok(report.results.every(v => v.status === 'NOT_RUN' && v.expertReview === 'PENDING'));
    const summary = summarizeEvaluation(report);
    assert.equal(summary.tokens.missing, 20);
    assert.equal(summary.humanReview.scores.factSupport.missing, 20);
    assert.equal(summary.cost.status, 'UNKNOWN');
  } finally {
    if (dirname(resolve(temp)) === resolve(tmpdir()) && temp.startsWith(join(tmpdir(), 'agent-eval-test-'))) await rm(temp, { recursive: true, force: true });
  }
});

test('bindings reject cross-purpose, extra sources, invalid draft dependencies and changing historical intent', () => {
  const standard = suite.cases[0].target;
  assert.throws(() => validateScenarioBinding(standard, suite.cases.find(v => v.caseId === 'mm01').target));
  assert.throws(() => validateScenarioBinding(standard, { ...standard, assetId: 1 }));
  assert.throws(() => validateScenarioBinding(standard, { ...standard, standardMatch: { ...standard.standardMatch, columnName: undefined } }));
  const snapshot = suite.cases.find(v => v.caseId === 'me02').target;
  assert.throws(() => validateScenarioBinding(snapshot, { ...snapshot, metricExplanation: { ...snapshot.metricExplanation, view: null } }));
  const derived = suite.cases.find(v => v.caseId === 'md02').target;
  assert.throws(() => validateScenarioBinding(derived, { ...derived, metricDraft: { ...derived.metricDraft, upstreamIds: [1, 2] } }));
  assert.throws(() => validateScenarioBinding(derived, { ...derived, metricDraft: { ...derived.metricDraft, metricType: 'COMPOSITE' } }));
});

test('scene executor rejects mismatched trace before reading or attributing private history', async () => {
  const item = suite.cases[0], calls = [];
  await assert.rejects(runRealCase(item, { account: item.account, projectId: 1, target: item.target },
    { baseUrl: 'https://fixture.invalid', authHeaders: {}, timeoutMs: 500 }, async url => {
      calls.push(url.pathname);
      if (url.pathname.endsWith('/chat/turns')) return new Response(JSON.stringify({ code: 200, data: { turnId: 't1' } }));
      if (url.pathname.endsWith('/events')) return new Response('data: {"type":"RUN_FINISHED"}\n\n');
      return new Response(JSON.stringify({ code: 200, data: { turnId: 'other', sessionId: 'other', totalTokens: 999 } }));
    }), /TRACE_CONTEXT_MISMATCH/);
  assert.equal(calls.length, 3);
  assert.ok(!calls.some(v => v.endsWith('/history') || v.endsWith('/cancel')));
});

test('delivery requires completed same-turn same-target unique history; all receipts are observations, not validity PASS', () => {
  for (const purpose of Object.keys(scenarioTypes)) {
    const target = suite.cases.find(v => v.target.purpose === purpose).target;
    const result = observeDelivery(target, 's1', 't1', continuation(target), history(target));
    assert.equal(result.status, 'RECEIPT_ENVELOPE_OBSERVED');
    assert.equal(result.candidateValidity, 'PENDING_SOURCE_REVIEW');
    assert.equal(result.factSupport, 'PENDING_EXPERT_REVIEW');
    assert.ok(!JSON.stringify(result).includes('私有'));
    for (const change of [{ status: 'RUNNING' }, { turnId: 'other' }, { sessionId: 'other' }, { blockingReason: 'unavailable' }, { governanceTarget: {} }]) {
      assert.equal(observeDelivery(target, 's1', 't1', { ...continuation(target), ...change }, history(target)).status, 'UNAVAILABLE');
    }
    assert.equal(observeDelivery(target, 's1', 't1', continuation(target), [...history(target), ...history(target)]).status, 'UNAVAILABLE');
    assert.equal(observeDelivery(target, 's1', 't1', continuation(target), [{ ...history(target)[0], turnId: null }]).status, 'UNAVAILABLE');
    assert.equal(observeDelivery(target, 's1', 't1', continuation(target), [{ role: 'assistant', turnId: 't1', content: 'clarification only' }]).status, 'NO_RECEIPT');
    for (const change of [{ target: {} }, { skillHash: '' }, { skillVersion: 0 }, { truncated: true }, { questions: [null] }]) {
      assert.equal(observeDelivery(target, 's1', 't1', continuation(target), [{ role: 'assistant', turnId: 't1', content: message(target, { ...receipt(target), ...change }) }]).status, 'UNAVAILABLE');
    }
    assert.equal(observeDelivery(target, 's1', 't1', continuation(target), [{ ...history(target)[0], content: message(target) + '\n' + message(target) }]).reason, 'RECEIPT_NOT_UNIQUE');
  }
});

test('scene transport uses bizData, split UTF8, persisted delivery and trace identity without source writes', async () => {
  const item = suite.cases[0], calls = [];
  let sessionId;
  const json = data => new Response(JSON.stringify({ code: 200, bizData: data }));
  const request = async (url, options) => {
    calls.push({ path: url.pathname, method: options.method ?? 'GET' });
    if (url.pathname.endsWith('/chat/turns')) { sessionId = JSON.parse(options.body).sessionId; return json({ turnId: 't1' }); }
    if (url.pathname.endsWith('/events')) {
      const bytes = new TextEncoder().encode('data: {"type":"TEXT_MESSAGE_CONTENT","delta":"私有流文本"}\n\ndata: {"type":"RUN_FINISHED","outcome":{"type":"success"}}\n\n');
      return new Response(new ReadableStream({ start(c) { for (const byte of bytes) c.enqueue(new Uint8Array([byte])); c.close(); } }));
    }
    if (url.pathname.endsWith('/trace')) return json({ sessionId, turnId: 't1', totalTokens: -1 });
    if (url.pathname.endsWith('/continuation')) return json({ ...continuation(item.target), sessionId });
    return json(history(item.target));
  };
  const report = await runRealCase(item, { account: item.account, projectId: 1, target: item.target },
    { baseUrl: 'https://fixture.invalid', authHeaders: { Cookie: 'private-secret' }, timeoutMs: 5000 }, request);
  assert.equal(report.delivery.status, 'RECEIPT_ENVELOPE_OBSERVED');
  assert.equal(report.outputHash, digest('私有流文本'));
  assert.equal(report.totalTokens, null);
  assert.equal(calls.length, 5);
  assert.equal(calls.filter(v => v.method === 'POST').length, 1);
  assert.ok(!JSON.stringify(report).includes('私有'));
  assert.ok(!JSON.stringify(report).includes('private-secret'));
});

test('bounded API read rejects oversized private response and error envelopes', async () => {
  await assert.rejects(readApiData(new Response(JSON.stringify({ code: 403, message: 'secret' }))), /API_REJECTED/);
  await assert.rejects(readApiData(new Response(' '.repeat(2_000_001))), /RESPONSE_TOO_LARGE/);
});

test('summary keeps failure/zero/missing denominators, binds review to exact report and separates reported adoption/save', () => {
  const result = { caseId: 'sm01', repeat: 1, status: 'AWAITING_EXPERT_REVIEW', turnId: 't1', outputHash: 'out', traceHash: 'trace',
    elapsedMs: 12, totalTokens: 0, delivery: { candidateCount: 1 } };
  const report = { mode: 'real', suiteHash: 'suite', results: [result, { caseId: 'sm02', repeat: 1, status: 'ERROR', elapsedMs: 80 }] };
  const review = { caseId: 'sm01', repeat: 1, turnId: 't1', outputHash: 'out', traceHash: 'trace', reviewer: 'private-name',
    sourceAuditRefs: ['private-audit'], scores: { candidateValidity: 2, factSupport: 0, taskCompletion: null }, manualEdits: 2, adopted: 1, saved: 0 };
  const reviews = { version: 'F-030-review-v1', reportHash: digest(JSON.stringify(report)), items: [review] };
  const summary = summarizeEvaluation(report, reviews);
  assert.equal(summary.totalCases, 2);
  assert.equal(summary.tokens.samples, 1); assert.equal(summary.tokens.missing, 1);
  assert.equal(summary.elapsedByStatus.ERROR.max, 80);
  assert.equal(summary.humanReview.scores.factSupport.zero, 1);
  assert.equal(summary.humanReview.scores.factSupport.missing, 1);
  assert.equal(summary.humanReview.scores.taskCompletion.rated, 0);
  assert.equal(summary.humanReview.adopted.candidates, 1); assert.equal(summary.humanReview.saved.candidates, 0);
  assert.ok(!JSON.stringify(summary).includes('private-'));
  assert.throws(() => summarizeEvaluation(report, { ...reviews, reportHash: 'different' }));
  assert.throws(() => summarizeEvaluation(report, { ...reviews, items: [review, review] }));
  assert.throws(() => summarizeEvaluation(report, { ...reviews, items: [{ ...review, turnId: 'different' }] }));
  assert.throws(() => summarizeEvaluation(report, { ...reviews, items: [{ ...review, sourceAuditRefs: [] }] }));
  assert.throws(() => summarizeEvaluation(report, { ...reviews, items: [{ ...review, adopted: 0, saved: 1 }] }));
  assert.throws(() => summarizeEvaluation({ ...report, mode: 'offline' }, { ...reviews, reportHash: digest(JSON.stringify({ ...report, mode: 'offline' })) }));
});
