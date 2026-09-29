import type { DatasetQueryResult } from '@/services/dataset';

interface CacheEntry {
  result: DatasetQueryResult;
  storedAt: number;
}

/** Small per-runtime LRU cache of raw Dataset query results. */
export class ScreenRuntimeQueryCache {
  private readonly entries = new Map<string, CacheEntry>();

  constructor(private readonly maxEntries = 100) {}

  get(key: string, maxAgeMs: number, now = Date.now()) {
    if (maxAgeMs <= 0) return undefined;
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (now - entry.storedAt > maxAgeMs) {
      this.entries.delete(key);
      return undefined;
    }
    // Touch the entry so Map insertion order acts as a compact LRU queue.
    this.entries.delete(key);
    this.entries.set(key, entry);
    return entry.result;
  }

  set(key: string, result: DatasetQueryResult, now = Date.now()) {
    this.entries.delete(key);
    this.entries.set(key, { result, storedAt: now });
    while (this.entries.size > this.maxEntries) {
      const oldestKey = this.entries.keys().next().value as string | undefined;
      if (!oldestKey) break;
      this.entries.delete(oldestKey);
    }
  }

  clear() {
    this.entries.clear();
  }

  get size() {
    return this.entries.size;
  }
}
