import { verifiedPersistedInvocation } from './invocation-evidence';
import type { DataServiceInvocationEvidence } from '@/services/data-service';

const evidence = (id: string, apiId = '7'): DataServiceInvocationEvidence => ({
  state: 'FOUND',
  record: {
    id, apiId, success: true, durationMs: 25, rowCount: 3,
    sourceRevisionId: '9007199254740995', sourceRevisionNo: 12,
  },
});

describe('exact persisted Data Service invocation evidence', () => {
  it('verifies old audit beyond the 200-row window with unrounded BIGINT', () => {
    const row = verifiedPersistedInvocation(evidence('9007199254740993'),
      7, '9007199254740993');
    expect(row?.id).toBe('9007199254740993');
    expect(row?.sourceRevisionId).toBe('9007199254740995');
  });

  it('does not substitute a different invocation or a foreign API', () => {
    expect(verifiedPersistedInvocation(evidence('101'), 7, '102')).toBeUndefined();
    expect(verifiedPersistedInvocation(evidence('101', '8'), 7, '101')).toBeUndefined();
    expect(verifiedPersistedInvocation(evidence('9007199254740992'), 7,
      '9007199254740993')).toBeUndefined();
  });

  it('never treats missing, invalid or inconsistent source states as verified', () => {
    expect(verifiedPersistedInvocation({ state: 'NOT_FOUND', record: null },
      7, '101')).toBeUndefined();
    expect(verifiedPersistedInvocation(null, 7, '101')).toBeUndefined();
    expect(verifiedPersistedInvocation(evidence('101'), 7, '0')).toBeUndefined();
    expect(verifiedPersistedInvocation(evidence('101'), 7, '../101')).toBeUndefined();
    expect(verifiedPersistedInvocation(evidence('101'), NaN, '101')).toBeUndefined();
    expect(verifiedPersistedInvocation({ state: 'FOUND', record: null }, 7, '101'))
      .toBeUndefined();
  });
});
