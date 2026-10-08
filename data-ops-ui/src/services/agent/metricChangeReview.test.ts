import { scenario } from '../../../tests/fixtures/agent-scenarios';
import { parseMetricChangeReview, readMetricChangeReviewTarget } from './metricChangeReview';
import { readScenarioHistory } from './scenarioHistory';
import { readContinuation } from './continuation';
import { governanceSourcePath, governanceTaskTitle } from './governance';

const original = () => parseMetricChangeReview(scenario('METRIC_CHANGE_REVIEW').text)!;
const receipt = (value: unknown) => '```yak-metric-change-review\n' + JSON.stringify(value) + '\n```';
it('reads the new precise scope and preserves only unique completed history', () => {
  const fixture = scenario('METRIC_CHANGE_REVIEW');
  expect(parseMetricChangeReview(fixture.text)).not.toBeNull();
  expect(readContinuation(fixture.continuation, 's1').governanceTarget).toEqual(fixture.target);
  expect(governanceTaskTitle(fixture.target)).toContain('发布 v2 → 草稿 v3');
  expect(governanceSourcePath(fixture.target)).toBe('/metric/manage/7');
  expect(readScenarioHistory(fixture.text, 's1', 't1', true, fixture.continuation).status).toBe('READY');
  expect(readScenarioHistory(fixture.text, 's1', 't1', false, fixture.continuation).status).toBe('UNAVAILABLE');
  expect(readScenarioHistory(fixture.text, 's1', 'old', true, fixture.continuation).status).toBe('UNAVAILABLE');
  expect(readScenarioHistory(fixture.text, 's1', 't1', true, { ...fixture.continuation, status: 'RUNNING' }).status).toBe('UNAVAILABLE');
});
it('rejects duplicate/cross-type receipts and a different publication event or prepared digest', () => {
  const fixture = scenario('METRIC_CHANGE_REVIEW'); const value = original();
  expect(parseMetricChangeReview(fixture.text + fixture.text)).toBeNull();
  expect(readScenarioHistory(fixture.text + scenario().text, 's1', 't1', true, fixture.continuation).status).toBe('UNAVAILABLE');
  for (const target of [{ ...value.target, publicationEventId: 32 }, { ...value.target, businessQuestion: '新问题' }]) {
    expect(readScenarioHistory(receipt({ ...value, target }), 's1', 't1', true, fixture.continuation).status).toBe('UNAVAILABLE');
  }
  expect(parseMetricChangeReview(receipt({ ...value, target: { ...value.target, definition: 'c'.repeat(64) } }))).toBeNull();
});
it('rejects forged evidence values, unknown/duplicate references, invalid coverage and oversized output', () => {
  const v = original(); const statement = v.candidates[0].statements[0]; const fact = statement.evidence[0];
  for (const evidence of [[{ ...fact, value: 'forged' }], [{ ...fact, key: 'missing' }], [fact, fact], [null]]) {
    expect(parseMetricChangeReview(receipt({ ...v, candidates: [{ statements: [{ ...statement, evidence }], checks: [] }] }))).toBeNull();
  }
  expect(parseMetricChangeReview(receipt({ ...v, questions: ['x'.repeat(513)] }))).toBeNull();
  expect(parseMetricChangeReview(receipt({ ...v, source: { ...v.source, coverage: [] } }))).toBeNull();
  expect(parseMetricChangeReview(receipt({ ...v, source: { ...v.source, facts: [...v.source.facts, v.source.facts[0]] } }))).toBeNull();
  expect(parseMetricChangeReview(receipt({ ...v, source: { ...v.source, differences: [] } }))).toBeNull();
});
it('rejects invalid targets and mixed source scopes while legacy continuations remain valid', () => {
  const v = original();
  expect(() => readMetricChangeReviewTarget({ ...v.target, publicationEventId: 0 })).toThrow();
  expect(() => readMetricChangeReviewTarget({ ...v.target, version: 1.5 })).toThrow();
  expect(() => readContinuation(JSON.parse(JSON.stringify({ sessionId: 's1', governanceTarget: { ...scenario('METRIC_CHANGE_REVIEW').target, assetId: 4 } })), 's1')).toThrow();
  expect(readContinuation(scenario().continuation, 's1').governanceTarget?.purpose).toBe('STANDARD_MATCH');
});
