import { act, renderHook } from '@testing-library/react';
import { useLatestDataServiceRead } from './useLatestDataServiceRead';
import { classifyDataServiceReadIssue } from './read-issue';

describe('data service scoped read truth', () => {
  it('lets only the last request write and invalidates requests on unmount', () => {
    const { result, unmount } = renderHook(useLatestDataServiceRead);
    let first!: () => boolean;
    let next!: () => boolean;
    act(() => { first = result.current(); next = result.current(); });
    expect(first()).toBe(false);
    expect(next()).toBe(true);
    unmount();
    expect(next()).toBe(false);
  });
  it('does not relabel unknown/404/500 as a successful empty result or 403', () => {
    expect(classifyDataServiceReadIssue({ response: { status: 403 } })).toBe('FORBIDDEN');
    expect(classifyDataServiceReadIssue({ code: 403 })).toBe('FORBIDDEN');
    expect(classifyDataServiceReadIssue({ response: { status: 404 } })).toBe('UNAVAILABLE');
    expect(classifyDataServiceReadIssue({ response: { status: 500 } })).toBe('UNAVAILABLE');
    expect(classifyDataServiceReadIssue(new Error('timeout'))).toBe('UNAVAILABLE');
  });
});
