import { readScenarioHistory } from './scenarioHistory';
import { receiptText, scenario } from '../../../tests/fixtures/agent-scenarios';
import type { SessionContinuation } from './continuation';

it.each(['STANDARD_MATCH', 'MODEL_MAPPING', 'METRIC_EXPLANATION', 'METRIC_DRAFT'] as const)('projects only the completed persisted %s task and preserves surrounding text', kind => {
  const fixture = scenario(kind);
  const original = JSON.stringify(fixture);
  expect(readScenarioHistory(fixture.text, 's1', 't1', true, fixture.continuation)).toEqual({ status: 'READY', value: fixture.value,
    text: '生成结果\n\n请返回原页面核对', sourcePath: kind === 'STANDARD_MATCH' ? '/modeling/models/7' : kind === 'MODEL_MAPPING' ? '/modeling/models/7/mapping' : kind === 'METRIC_DRAFT' ? '/metric/manage' : '/metric/manage/7' });
  expect(JSON.stringify(fixture)).toBe(original);
});

it.each(['QUEUED', 'RUNNING', 'WAITING_INPUT', 'FAILED', 'CANCELLED', 'INTERRUPTED', null] as const)('keeps %s receipts unverified', status => {
  const f = scenario();
  expect(readScenarioHistory(f.text, 's1', 't1', true, { ...f.continuation, status })).toEqual({ status: 'UNAVAILABLE' });
});

it('requires history provenance, exact session/turn, authority and an unblocked latest task', () => {
  const f = scenario();
  const read = (continuation: SessionContinuation | null, session = 's1', turn: string | undefined = 't1', persisted = true) =>
    readScenarioHistory(f.text, session, turn, persisted, continuation);
  for (const result of [read(null), read(f.continuation, 's2'), read(f.continuation, 's1', 'old'),
    read(f.continuation, 's1', '', true), read(f.continuation, 's1', 't1', false),
    read({ ...f.continuation, blockingReason: '不可访问' }), read({ ...f.continuation, governanceTarget: null }),
    read({ ...f.continuation, governanceTarget: { assetId: 7 } })]) expect(result).toEqual({ status: 'UNAVAILABLE' });
});

it.each(['modelId', 'columnName', 'dataType', 'businessDescription', 'keyword'] as const)('compares the complete standard target including %s', key => {
  const f = scenario();
  if (f.target.purpose !== 'STANDARD_MATCH') throw new Error('fixture');
  const standardMatch = { ...f.target.standardMatch, [key]: key === 'modelId' ? 8 : 'changed' };
  expect(readScenarioHistory(f.text, 's1', 't1', true, { ...f.continuation, governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch } })).toEqual({ status: 'UNAVAILABLE' });
});

it('keeps snapshot and current views distinct while normalizing nullable server fields', () => {
  const f = scenario('METRIC_EXPLANATION', 'ATOMIC', true);
  const current = scenario('METRIC_EXPLANATION');
  expect(readScenarioHistory(f.text, 's1', 't1', true, current.continuation).status).toBe('UNAVAILABLE');
  expect(readScenarioHistory(f.text, 's1', 't1', true, f.continuation).status).toBe('READY');
  const raw = JSON.parse(JSON.stringify({ ...current.continuation, governanceTarget: { ...current.target, assetId: null, qualityMonitorId: null, metricDraft: null } }));
  expect(readScenarioHistory(current.text, 's1', 't1', true, raw).status).toBe('READY');
});

it('rejects duplicate, cross-type, malformed, oversized and wrong-kind receipts without guessing links', () => {
  const f = scenario();
  const cases = [f.text + f.text, f.text + scenario('MODEL_MAPPING').text, '```yak-standard-match\n{}\n```',
    f.text.replace('STANDARD_MATCH', 'MODEL_MAPPING'), f.text.replace('"truncated":false', '"truncated":"no"'),
    f.text + 'x'.repeat(256000), f.text.replace(/\n```\n/, '\n')];
  for (const text of cases) expect(readScenarioHistory(text, 's1', 't1', true, f.continuation)).toEqual({ status: 'UNAVAILABLE' });
  expect(readScenarioHistory('普通回答', 's1', 't1', true, f.continuation)).toEqual({ status: 'NONE' });
});

it.each(['ATOMIC', 'DERIVED', 'COMPOSITE'] as const)('requires the complete %s draft scope and returns the original list', type => {
  const f = scenario('METRIC_DRAFT', type);
  expect(readScenarioHistory(f.text, 's1', 't1', true, f.continuation).status).toBe('READY');
  if (f.value.kind !== 'METRIC_DRAFT') throw new Error('fixture');
  expect(readScenarioHistory(receiptText({ ...f.value, target: { ...f.value.target, requirement: '另一需求' } }), 's1', 't1', true, f.continuation).status).toBe('UNAVAILABLE');
});
