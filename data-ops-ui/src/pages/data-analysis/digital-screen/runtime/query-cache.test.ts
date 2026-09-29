import type { DatasetQueryResult } from '@/services/dataset';
import { ScreenRuntimeQueryCache } from './query-cache';

const result = { datasetId: '1' } as DatasetQueryResult;

describe('ScreenRuntimeQueryCache', () => {
  it('expires entries by the caller supplied TTL', () => {
    const cache = new ScreenRuntimeQueryCache();
    cache.set('query', result, 1_000);

    expect(cache.get('query', 100, 1_050)).toBe(result);
    expect(cache.get('query', 100, 1_101)).toBeUndefined();
  });

  it('bounds cache size with LRU eviction', () => {
    const cache = new ScreenRuntimeQueryCache(2);
    cache.set('a', result, 1);
    cache.set('b', result, 2);
    expect(cache.get('a', 100, 3)).toBe(result);
    cache.set('c', result, 4);

    expect(cache.get('b', 100, 5)).toBeUndefined();
    expect(cache.get('a', 100, 5)).toBe(result);
    expect(cache.get('c', 100, 5)).toBe(result);
  });
});
