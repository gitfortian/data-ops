import { readContinuation, sessionLocation, type SessionContinuation } from './continuation';

const completed = { sessionId: 's1', turnId: 't1', status: 'COMPLETED' as const };

it.each([
  [{ assetId: 7, qualityMonitorId: null, qualityExecutionNo: null, purpose: null }, { assetId: 7 }],
  [{ assetId: 7, purpose: 'ASSET_DESCRIPTION' }, { assetId: 7, purpose: 'ASSET_DESCRIPTION' }],
  [{ assetId: null, qualityMonitorId: 9, qualityExecutionNo: null, purpose: 'QUALITY_RULES' }, { qualityMonitorId: 9, purpose: 'QUALITY_RULES' }],
  [{ assetId: null, qualityMonitorId: null, qualityExecutionNo: 'Q_20261007-1', purpose: null }, { qualityExecutionNo: 'Q_20261007-1' }],
])('normalizes persisted target %j without nullable union fields', (target, expected) => {
  expect(readContinuation({ ...completed, governanceTarget: target } as SessionContinuation, 's1').governanceTarget).toEqual(expected);
});

it.each([undefined, null])('keeps an ordinary latest turn ordinary: %s', (governanceTarget) => {
  expect(readContinuation({ ...completed, governanceTarget }, 's1').governanceTarget).toBeNull();
});

it.each([
  { ...completed, sessionId: 'other' },
  { ...completed, status: 'NEW_STATUS' },
  { ...completed, turnId: '' },
  { ...completed, governanceTarget: { assetId: 7, qualityMonitorId: 9 } },
  { ...completed, governanceTarget: { assetId: '7' } },
  { ...completed, governanceTarget: { assetId: -1 } },
  { ...completed, governanceTarget: { qualityMonitorId: 9 } },
  { ...completed, governanceTarget: { qualityExecutionNo: '../other' } },
  { ...completed, governanceTarget: { qualityExecutionNo: 'Q1', purpose: 'QUALITY_RULES' } },
  { ...completed, blockingReason: 42 },
  { ...completed, status: 'WAITING_INPUT' },
  { ...completed, status: 'WAITING_INPUT', clarification: { toolCallId: 'c1', toolName: 'sql', question: '?' } },
  { ...completed, status: 'WAITING_INPUT', clarification: { toolCallId: 'c1', toolName: 'request_clarification', question: '{"question":"哪个？","options":[{}]}' } },
  { ...completed, status: 'WAITING_INPUT', clarification: { toolCallId: 'c1', toolName: 'request_clarification', question: '{broken' } },
])('fails closed for an invalid recovery projection: %j', (view) => {
  expect(() => readContinuation(view as SessionContinuation, 's1')).toThrow();
});

it.each(['QUEUED', 'RUNNING'] as const)('blocks %s even without a server reason', (status) => {
  expect(readContinuation({ ...completed, status }, 's1').blockingReason).toBeTruthy();
});

it('restores only the waiting question and preserves a server block', () => {
  const clarification = { toolCallId: 'c2', toolName: 'request_clarification', question: '{"question":"哪个分区？","options":["昨天"]}' };
  expect(readContinuation({ ...completed, status: 'WAITING_INPUT', clarification }, 's1').clarification).toEqual(clarification);
  expect(readContinuation({ ...completed, clarification }, 's1').clarification).toBeNull();
  expect(readContinuation({ ...completed, status: 'WAITING_INPUT', blockingReason: '待答问题无法读取' }, 's1').blockingReason).toBe('待答问题无法读取');
});

it('supports a session with no persisted turns and encodes its URL', () => {
  expect(readContinuation({ sessionId: 's1' }, 's1').governanceTarget).toBeNull();
  expect(sessionLocation('s/1?x=2')).toBe('/ai-agent?sessionId=s%2F1%3Fx%3D2');
  expect(sessionLocation(null)).toBe('/ai-agent');
});
