import { parseMetricExplanation } from './metricExplanation';
import { readContinuation } from './continuation';
const target = { metricId: 7, version: 3, businessQuestion: '' };
const suggestion = { kind: 'METRIC_EXPLANATION', target, expectedDefinition: 'a'.repeat(64), skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  candidates: [{ businessDescription: '金额合计', statements: [{ text: '按金额求和', evidence: [{ key: 'measureExpr', label: '度量', value: 'SUM(amount)' }] }] }], questions: [] };
const text = (value: unknown) => `\`\`\`yak-metric-explanation\n${JSON.stringify(value)}\n\`\`\``;
it('accepts one typed receipt and rejects duplicate blocks and unbounded evidence', () => {
  expect(parseMetricExplanation(text(suggestion))).toEqual(suggestion);
  expect(parseMetricExplanation(text(suggestion) + text(suggestion))).toBeNull();
  expect(parseMetricExplanation(text({ ...suggestion, candidates: [suggestion.candidates[0], suggestion.candidates[0]] }))).toBeNull();
  expect(parseMetricExplanation(text({ ...suggestion, target: { ...target, version: 0 } }))).toBeNull();
});
it('normalizes the saved metric scope and rejects mixed targets', () => {
  expect(readContinuation({ sessionId: 's', governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: target } }, 's').governanceTarget).toEqual({ purpose: 'METRIC_EXPLANATION', metricExplanation: target });
  expect(() => readContinuation({ sessionId: 's', governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: target, assetId: 1 } as never }, 's')).toThrow();
});
