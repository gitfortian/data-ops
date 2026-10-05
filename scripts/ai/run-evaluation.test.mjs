import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { validateSuite, decodeFrame, runRealCase, suiteFile, main } from './run-evaluation.mjs';

test('default offline execution reports every model case NOT_RUN without network or authentication', async () => {
  const temp = await mkdtemp(join(tmpdir(), 'agent-eval-test-'));
  try {
    const report = await main([`--out=${join(temp, 'report.json')}`], {});
    assert.equal(report.mode, 'offline');
    assert.equal(report.results.length, 12);
    assert.ok(report.results.every(item => item.status === 'NOT_RUN'));
    assert.equal(JSON.parse(await readFile(join(temp, 'report.json'), 'utf8')).model, 'NOT_RUN');
  } finally {
    // Delete only this test's verified temporary directory.
    if (dirname(resolve(temp)) === resolve(tmpdir()) && temp.startsWith(join(tmpdir(), 'agent-eval-test-'))) {
      await rm(temp, { recursive: true, force: true });
    }
  }
});

test('fixed suite has unique complete task contracts and rejects malformed targets', async () => {
  const suite = validateSuite(JSON.parse(await readFile(suiteFile, 'utf8')));
  assert.equal(suite.cases.length, 12);
  assert.throws(() => validateSuite({ ...suite, cases: [suite.cases[0], suite.cases[0]] }));
  assert.throws(() => validateSuite({ ...suite, cases: [{ ...suite.cases[0], target: { assetId: 7, qualityExecutionNo: 'x' } }] }));
});

test('SSE parser accepts data-only and multiline frames without treating heartbeats as results', () => {
  assert.equal(decodeFrame(': heartbeat'), null);
  assert.deepEqual(decodeFrame('id: 2\ndata: {"type":\ndata: "RUN_FINISHED"}'), { type: 'RUN_FINISHED' });
});

const item = { caseId: 'asset', account: 'authorized', target: { assetId: 7 }, question: 'fixture' };
const binding = { account: 'authorized', projectId: 1, target: { assetId: 77 } };
const config = { baseUrl: 'http://fixture.invalid', authHeaders: { Authorization: 'private-test-secret' }, timeoutMs: 500 };
const json = data => new Response(JSON.stringify({ code: 200, data }), { headers: { 'Content-Type': 'application/json' } });

test('real executor uses published routes, hashes private outputs, and leaves semantics pending', async () => {
  const calls = [];
  const request = async (url, options) => {
    calls.push({ path: url.pathname, options });
    if (url.pathname.endsWith('/chat/turns')) return json({ turnId: 't1' });
    if (url.pathname.endsWith('/events')) {
      const bytes = new TextEncoder().encode('data: {"type":"TEXT_MESSAGE_CONTENT","delta":"private answer"}\n\ndata: {"type":"RUN_FINISHED","outcome":{"type":"success"}}\n\n');
      return new Response(new ReadableStream({ start(controller) { controller.enqueue(bytes.slice(0, 45)); controller.enqueue(bytes.slice(45)); controller.close(); } }));
    }
    return json({ totalTokens: null, privateTrace: 'private answer' });
  };
  const report = await runRealCase(item, binding, config, request);
  assert.equal(report.status, 'AWAITING_EXPERT_REVIEW');
  assert.equal(report.usageStatus, 'UNKNOWN');
  assert.equal(report.totalTokens, null);
  assert.equal(calls[0].options.headers['X-YAK-SECURITY-PROJECT-ID'], '1');
  assert.equal(JSON.parse(calls[0].options.body).governanceTarget.assetId, 77);
  assert.equal(calls.length, 3);
  assert.ok(!JSON.stringify(report).includes('private answer'));
  assert.ok(!JSON.stringify(report).includes('private-test-secret'));
});

test('incomplete transport explicitly cancels its own session instead of relying on disconnect', async () => {
  const calls = [];
  await assert.rejects(runRealCase(item, binding, config, async (url) => {
    calls.push(url.pathname);
    if (url.pathname.endsWith('/chat/turns')) return json({ turnId: 't2' });
    if (url.pathname.endsWith('/events')) return new Response('data: {"type":"RUN_STARTED"}\n\n');
    return json(true);
  }), /INCOMPLETE_STREAM/);
  assert.equal(calls.length, 3);
  assert.match(calls[2], /\/sessions\/ai-eval-.*\/cancel$/);
});

test('missing/mismatched bindings make zero network calls', async () => {
  let calls = 0;
  const request = async () => { calls++; };
  await assert.rejects(runRealCase(item, { ...binding, account: 'other' }, config, request));
  await assert.rejects(runRealCase(item, { ...binding, target: { qualityMonitorId: 2 } }, config, request));
  assert.equal(calls, 0);
});
