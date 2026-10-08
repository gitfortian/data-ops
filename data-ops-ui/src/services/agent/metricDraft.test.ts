import { parseMetricDraft, readMetricDraftTarget } from './metricDraft';
import { readContinuation } from './continuation';
const target = { metricId: null, version: null, metricType: 'ATOMIC' as const, modelId: 9, upstreamIds: [], requirement: '每日金额' };
const value = { kind: 'METRIC_DRAFT', target, expectedDefinition: 'a'.repeat(64), skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  source: { definition: 'a'.repeat(64), fields: [{ name: 'amount', type: 'DECIMAL', description: '金额' }], upstream: [] },
  candidates: [{ name: '金额', description: '每日金额', period: 'DAY', aggregation: 'SUM', field: 'amount', qualifiers: [], tokens: [] }], questions: [] };
const text = (v: unknown) => '```yak-metric-draft\n' + JSON.stringify(v) + '\n```';
it('decodes only the unique persisted receipt and restores its original target', () => {
  expect(parseMetricDraft(text(value))).toEqual(value); expect(parseMetricDraft(text(value) + text(value))).toBeNull();
  expect(parseMetricDraft(JSON.stringify(value))).toBeNull();
  expect(readContinuation({ sessionId: 's', turnId: 't', status: 'COMPLETED', governanceTarget: { purpose: 'METRIC_DRAFT', metricDraft: target } }, 's').governanceTarget).toEqual({ purpose: 'METRIC_DRAFT', metricDraft: target });
});
it.each([{ ...target, modelId: null }, { ...target, upstreamIds: [1] }, { ...target, metricId: 7 }, { ...target, requirement: '' }, { ...target, metricType: 'COMPOSITE', modelId: null, upstreamIds: [1, 1] }])('rejects ambiguous target %j', v => expect(() => readMetricDraftTarget(v)).toThrow());
it('rejects missing source evidence, invented fields and foreign receipt kinds', () => {
  expect(parseMetricDraft(text({ ...value, source: { ...value.source, definition: 'c'.repeat(64) } }))).toBeNull();
  expect(parseMetricDraft(text({ ...value, candidates: [{ ...value.candidates[0], field: 'invented' }] }))).toBeNull();
  expect(parseMetricDraft(text({ ...value, kind: 'METRIC_EXPLANATION' }))).toBeNull();
});
