import { prepareGovernanceQuestion } from './question-preparation';
import { governanceQuestions, type GovernanceTarget } from '@/services/agent/governance';

const blank = { templateIndex: 0, background: '', focus: '', outcome: '' };
it.each<GovernanceTarget>([{ assetId: 7 }, { assetId: 7, purpose: 'ASSET_DESCRIPTION' },
  { qualityExecutionNo: 'Q1' }, { qualityMonitorId: 9, purpose: 'QUALITY_RULES' }])('uses only the selected task template %j', (target) => {
  expect(prepareGovernanceQuestion(target, blank)).toBe(governanceQuestions(target)[0]);
});
it('labels user background as unconfirmed and omits blank fields without inventing thresholds', () => {
  const text = prepareGovernanceQuestion({ qualityExecutionNo: 'Q1' }, { ...blank, background: '  昨天换过数据源  ', focus: '  ', outcome: '人工核对步骤' });
  expect(text).toContain('用户提供，尚待源证据核对');
  expect(text).toContain('已知背景：昨天换过数据源');
  expect(text).toContain('期望结果：人工核对步骤');
  expect(text).not.toContain('希望核对：');
});
it('rejects out of range templates and overlong values instead of truncating them', () => {
  for (const input of [{ ...blank, templateIndex: 99 }, { ...blank, templateIndex: 0.5 },
    { ...blank, background: 'x'.repeat(2001) }, { ...blank, focus: 'x'.repeat(1001) }, { ...blank, outcome: 'x'.repeat(1001) }]) {
    expect(() => prepareGovernanceQuestion({ assetId: 7 }, input)).toThrow();
  }
  expect(prepareGovernanceQuestion({ assetId: 7 }, { ...blank, background: 'x'.repeat(2000) }).length).toBeLessThan(8000);
});
