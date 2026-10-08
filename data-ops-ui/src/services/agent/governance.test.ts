import { governanceEntryPath, governanceQuestions, governanceSourcePath, governanceTaskTitle, parseGovernanceTarget, sameScenarioTarget } from './governance';
import { scenario } from '../../../tests/fixtures/agent-scenarios';

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

it('identifies all four task kinds including new drafts and exact snapshots', () => {
  expect(governanceTaskTitle(scenario().target)).toContain('类型标准匹配');
  expect(governanceTaskTitle(scenario('MODEL_MAPPING').target)).toContain('来源映射建议');
  expect(governanceTaskTitle(scenario('METRIC_EXPLANATION').target)).toBe('指标 #7 v3 口径解释与说明草稿');
  expect(governanceTaskTitle(scenario('METRIC_EXPLANATION', 'ATOMIC', true).target)).toBe('指标 #7 v3 历史快照');
  const f = scenario('METRIC_DRAFT');
  expect(governanceTaskTitle(f.target)).toBe('新建指标 定义草稿');
  if (f.target.purpose !== 'METRIC_DRAFT') throw new Error('fixture');
  expect(governanceTaskTitle({ ...f.target, metricDraft: { ...f.target.metricDraft, metricId: 12, version: 4 } })).toBe('指标 #12 v4 定义草稿');
  expect(governanceTaskTitle({ assetId: 7 })).toBe('资产 #7 治理解读');
  expect(governanceTaskTitle({ qualityExecutionNo: 'Q_1' })).toBe('质量执行 Q_1 解读与排查');
});

it('compares full task values while tolerating projection nulls and reordered keys', () => {
  const target = scenario().target;
  expect(sameScenarioTarget(target, { ...Object.fromEntries(Object.entries(target).reverse()), metricDraft: null })).toBe(true);
  if (target.purpose !== 'STANDARD_MATCH') throw new Error('fixture');
  expect(sameScenarioTarget(target, { ...target, standardMatch: { ...target.standardMatch, keyword: '另一条件' } })).toBe(false);
  expect(sameScenarioTarget({ metricDraft: { upstreamIds: [1, 2] } }, { metricDraft: { upstreamIds: [2, 1] } })).toBe(false);
  expect(sameScenarioTarget({ metricId: 7, version: 3 }, { metricId: 7, version: 4 })).toBe(false);
  expect(sameScenarioTarget(undefined, {})).toBe(false);
});
