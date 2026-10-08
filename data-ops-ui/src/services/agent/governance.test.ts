import { governanceEntryPath, governanceQuestions, governanceSourcePath, parseGovernanceTarget } from './governance';

it('roundtrips asset and execution selections to existing routes', () => {
  const asset = { assetId: 7 };
  expect(parseGovernanceTarget(governanceEntryPath(asset).split('?')[1])).toEqual(asset);
  expect(governanceSourcePath(asset)).toBe('/data-asset/detail/7');
  const quality = { qualityExecutionNo: 'Q_20261005-1' };
  expect(parseGovernanceTarget(governanceEntryPath(quality).split('?')[1])).toEqual(quality);
  expect(governanceQuestions(quality)[0]).toContain('未执行');
});

it.each(['?assetId=-1', '?assetId=0', '?assetId=9007199254740993', '?assetId=7&qualityExecutionNo=q', '?qualityExecutionNo=../../secret', '?qualityExecutionNo=%3Cscript%3E'])('rejects malformed selections %s', (search) => {
  expect(parseGovernanceTarget(search)).toBeNull();
});

it('returns metric tasks to their canonical existing detail route', () => {
  expect(governanceSourcePath({ purpose: 'METRIC_EXPLANATION', metricExplanation: { metricId: 7, version: 3, businessQuestion: '', view: 'SNAPSHOT' } })).toBe('/metric/manage/7');
  const metricDraft = { metricId: 7, version: 3, metricType: 'ATOMIC' as const, modelId: 9, upstreamIds: [], requirement: '每日金额' };
  expect(governanceSourcePath({ purpose: 'METRIC_DRAFT', metricDraft })).toBe('/metric/manage/7');
  expect(governanceSourcePath({ purpose: 'METRIC_DRAFT', metricDraft: { ...metricDraft, metricId: null, version: null } })).toBe('/metric/manage');
});
