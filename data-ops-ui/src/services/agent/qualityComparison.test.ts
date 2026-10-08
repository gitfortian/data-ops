import { governanceEntryPath, governanceQuestions, governanceTaskTitle, parseGovernanceTarget, sameScenarioTarget } from './governance';
import { readContinuation, type SessionContinuation } from './continuation';

const target = { qualityExecutionNo: 'after', qualityBaselineExecutionNo: 'before' };
test('explicit pair survives entry, latest turn restoration and scope equality', () => {
  expect(parseGovernanceTarget(governanceEntryPath(target).split('?')[1])).toEqual(target);
  expect(governanceTaskTitle(target)).toContain('before → after');
  expect(governanceQuestions(target)[0]).toContain('稳定规则 ID');
  const view = { sessionId: 's', turnId: 't', status: 'COMPLETED' as const, governanceTarget: target };
  expect(readContinuation(view, 's').governanceTarget).toEqual(target);
  expect(sameScenarioTarget(target, { ...target, qualityBaselineExecutionNo: 'other' })).toBe(false);
  expect(sameScenarioTarget(target, { qualityExecutionNo: 'after' })).toBe(false);
  const clarification = { toolName: 'request_clarification', toolCallId: 'c', question: '{"question":"关注什么？"}' };
  expect(readContinuation({ ...view, status: 'WAITING_INPUT', clarification }, 's').governanceTarget).toEqual(target);
});

test.each([
  '?qualityBaselineExecutionNo=before', '?qualityExecutionNo=after&qualityBaselineExecutionNo=after',
  '?qualityExecutionNo=after&qualityBaselineExecutionNo=../before', '?qualityExecutionNo=after&qualityBaselineExecutionNo=',
  '?assetId=7&qualityBaselineExecutionNo=before', '?qualityMonitorId=7&qualityBaselineExecutionNo=before',
  '?qualityExecutionNo=after&qualityBaselineExecutionNo=before&qualityBaselineExecutionNo=other',
  '?qualityExecutionNo=after&qualityExecutionNo=other&qualityBaselineExecutionNo=before',
])('rejects malformed or ambiguous URL pair %s', (search) => expect(parseGovernanceTarget(search)).toBeNull());

test.each([
  { qualityBaselineExecutionNo: 'before' }, { assetId: 7, qualityBaselineExecutionNo: 'before' },
  { ...target, qualityBaselineExecutionNo: 7 }, { ...target, qualityBaselineExecutionNo: 'after' },
  { ...target, qualityBaselineExecutionNo: '' }, { ...target, qualityBaselineExecutionNo: '../other' },
  { ...target, purpose: 'QUALITY_RULES' }, { ...target, assetId: 7 },
])('rejects malformed restored pairs %j', (governanceTarget) => {
  expect(() => readContinuation({ sessionId: 's', turnId: 't', status: 'COMPLETED', governanceTarget } as SessionContinuation, 's')).toThrow();
});
