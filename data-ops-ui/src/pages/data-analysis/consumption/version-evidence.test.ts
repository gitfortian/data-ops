import { formatObservedVersion } from './version-evidence';

describe('Consumer Impact observed-version presentation', () => {
  it('keeps exact DatasetVersion ID alongside user-friendly version number', () => {
    expect(formatObservedVersion({
      sourceVersion: { identity: '9001', displayVersion: 'v12' },
      successfulUsageCount: 2, lastObservedAt: '2026-10-08T12:00:00',
      providerEvidenceRefs: ['query:a', 'query:c'],
    })).toBe('v12 · ID 9001 · 2 次成功消费');
  });

  it('exposes exact Data Service source revision even when historic display version is missing', () => {
    expect(formatObservedVersion({
      sourceVersion: { identity: '101' }, successfulUsageCount: 1,
      providerEvidenceRefs: ['invocation:501'],
    })).toBe('ID 101 · 1 次成功消费');
  });
});
