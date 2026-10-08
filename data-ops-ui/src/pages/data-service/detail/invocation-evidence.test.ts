import { verifiedInvocationFromWindow } from './invocation-evidence';

describe('Data Service execution evidence deep link', () => {
  const calls = [{ id: 51, apiId: 7 }, { id: 52, apiId: 8 }, { id: 53, apiId: 7 }];

  it('selects only the requested invocation inside its owning API', () => {
    expect(verifiedInvocationFromWindow(calls, 7, '53')).toEqual({ id: 53, apiId: 7 });
    expect(verifiedInvocationFromWindow(calls, 7, '52')).toBeUndefined();
    expect(verifiedInvocationFromWindow(calls, 7, '999')).toBeUndefined();
  });

  it('rejects unsafe JSON numeric IDs and cannot attribute rounded BIGINT evidence', () => {
    expect(verifiedInvocationFromWindow([{ id: 9007199254740992, apiId: 7 }],
      7, '9007199254740993')).toBeUndefined();
    expect(verifiedInvocationFromWindow([{ id: 9007199254740992, apiId: 7 }],
      7, '9007199254740992')).toBeUndefined();
  });

  it('rejects invalid identities rather than falling back to unrelated log rows', () => {
    expect(verifiedInvocationFromWindow(calls, 7, '0')).toBeUndefined();
    expect(verifiedInvocationFromWindow(calls, 7, '../51')).toBeUndefined();
    expect(verifiedInvocationFromWindow(calls, NaN, '51')).toBeUndefined();
  });
});
