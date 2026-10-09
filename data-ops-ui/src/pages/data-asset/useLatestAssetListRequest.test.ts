import { act, renderHook } from '@testing-library/react';
import { useLatestAssetListRequest } from './useLatestAssetListRequest';

describe('Project-scoped async asset list request epochs', () => {
  it('only allows the newest filter/retry request to commit', () => {
    const { result } = renderHook(() => useLatestAssetListRequest());
    let first: () => boolean = () => false;
    let second: () => boolean = () => false;
    act(() => {
      first = result.current();
      expect(first()).toBe(true);
      second = result.current();
    });
    expect(first()).toBe(false);
    expect(second()).toBe(true);
  });

  it('rejects late HTTP results after a Project-scoped page unmounts', () => {
    const { result, unmount } = renderHook(() => useLatestAssetListRequest());
    let old: () => boolean = () => false;
    act(() => { old = result.current(); });
    unmount();
    expect(old()).toBe(false);
  });
});
