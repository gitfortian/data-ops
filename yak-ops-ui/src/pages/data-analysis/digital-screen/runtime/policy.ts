export interface ScreenRuntimePolicyOptions {
  debounceMs?: number;
  cacheTtlMs?: number;
  maxConcurrency?: number;
  refreshIntervalMs?: number;
}

export interface ScreenRuntimePolicy {
  debounceMs: number;
  cacheTtlMs: number;
  maxConcurrency: number;
  refreshIntervalMs: number;
}

export const SCREEN_RUNTIME_DEFAULT_POLICY: ScreenRuntimePolicy = {
  debounceMs: 180,
  cacheTtlMs: 10_000,
  maxConcurrency: 4,
  refreshIntervalMs: 0,
};

/** Production viewers refresh query data periodically; editors stay event-driven by default. */
export const SCREEN_RUNTIME_VIEWER_REFRESH_INTERVAL_MS = 30_000;

const finiteOr = (value: number | undefined, fallback: number) => (
  typeof value === 'number' && Number.isFinite(value) ? value : fallback
);

export const resolveScreenRuntimePolicy = (
  options?: ScreenRuntimePolicyOptions,
): ScreenRuntimePolicy => ({
  debounceMs: Math.max(0, finiteOr(options?.debounceMs, SCREEN_RUNTIME_DEFAULT_POLICY.debounceMs)),
  cacheTtlMs: Math.max(0, finiteOr(options?.cacheTtlMs, SCREEN_RUNTIME_DEFAULT_POLICY.cacheTtlMs)),
  maxConcurrency: Math.min(
    16,
    Math.max(1, Math.floor(finiteOr(options?.maxConcurrency, SCREEN_RUNTIME_DEFAULT_POLICY.maxConcurrency))),
  ),
  refreshIntervalMs: Math.max(
    0,
    finiteOr(options?.refreshIntervalMs, SCREEN_RUNTIME_DEFAULT_POLICY.refreshIntervalMs),
  ),
});
