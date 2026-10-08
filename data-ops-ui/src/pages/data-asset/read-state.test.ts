import { classifyAssetReadFailure } from './read-state';

describe('Asset detail read failures', () => {
  it('distinguishes an owning-domain missing/project-inaccessible asset from outages', () => {
    expect(classifyAssetReadFailure({ code: 48001 })).toBe('NOT_FOUND');
    expect(classifyAssetReadFailure({ response: { status: 404 } })).toBe('NOT_FOUND');
    expect(classifyAssetReadFailure({ code: 48011 })).toBe('UNAVAILABLE');
    expect(classifyAssetReadFailure({ response: { status: 500 } })).toBe('UNAVAILABLE');
    expect(classifyAssetReadFailure(new Error('network timeout'))).toBe('UNAVAILABLE');
  });

  it('does not present access denial as missing or empty data', () => {
    expect(classifyAssetReadFailure({ response: { status: 403 } })).toBe('FORBIDDEN');
    expect(classifyAssetReadFailure({ code: 403 })).toBe('FORBIDDEN');
    expect(classifyAssetReadFailure({ code: 48001, response: { status: 403 } })).toBe('FORBIDDEN');
  });

  it('treats unknown errors conservatively', () => {
    expect(classifyAssetReadFailure(undefined)).toBe('UNAVAILABLE');
    expect(classifyAssetReadFailure({})).toBe('UNAVAILABLE');
  });
});
