import { hasMenuAccess } from './menu';

describe('menu authorization', () => {
  it('enforces the declared stable menu code when the backend contract is present', () => {
    expect(
      hasMenuAccess(['batch-link-up'], 'batch-link-up', []),
    ).toBe(true);
    expect(
      hasMenuAccess(['client'], 'batch-link-up', []),
    ).toBe(false);
    expect(hasMenuAccess([], 'batch-link-up', [])).toBe(false);
  });

  it('fails closed for an unmapped protected resource once menu grants are present', () => {
    expect(hasMenuAccess([], undefined, [])).toBe(false);
    expect(hasMenuAccess(['batch-link-up'], undefined, [])).toBe(false);
  });

  it('keeps public, root, and staggered deployments compatible', () => {
    expect(hasMenuAccess([], undefined, [], true)).toBe(true);
    expect(
      hasMenuAccess([], 'batch-link-up', ['security:root']),
    ).toBe(true);
    expect(
      hasMenuAccess(undefined, 'batch-link-up', []),
    ).toBe(true);
  });
});
