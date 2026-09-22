import type { ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { isScreenRuntimeAbortError } from '../concurrency';
import { ScreenRuntimeExecutor } from '../executor';
import type {
  ScreenRuntimeDataState,
  ScreenRuntimeExecutionStats,
  ScreenRuntimeState,
} from '../model';
import {
  countBoundScreenComponents,
  planScreenRuntimeQueries,
} from '../planner';
import {
  resolveScreenRuntimePolicy,
  type ScreenRuntimePolicyOptions,
} from '../policy';

const EMPTY_STATS: ScreenRuntimeExecutionStats = {
  candidateCount: 0,
  uniqueQueryCount: 0,
  deduplicatedCount: 0,
  networkQueryCount: 0,
  cacheHitQueryCount: 0,
};

const EMPTY_STATE: ScreenRuntimeDataState = {
  data: {},
  loadingIds: [],
  errors: {},
  stats: EMPTY_STATS,
};

const runtimeErrorMessage = (error: unknown) => (
  error instanceof Error ? error.message : 'Dataset 查询失败'
);

/**
 * Coordinates a screen runtime session. Query grouping/cache/concurrency live in the executor;
 * this hook only owns React lifecycle, cancellation, refresh cadence and stale-result protection.
 */
export function useScreenRuntime(
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
  datasets: PublishedDataset[],
  options?: ScreenRuntimePolicyOptions,
): ScreenRuntimeState {
  const [state, setState] = useState<ScreenRuntimeDataState>(EMPTY_STATE);
  const [refreshTick, setRefreshTick] = useState(0);
  const sequence = useRef(0);
  const previousPlanKey = useRef('');
  const executionActive = useRef(false);
  const executor = useMemo(() => new ScreenRuntimeExecutor(), []);
  const policy = resolveScreenRuntimePolicy(options);
  const bindingKey = useMemo(() => JSON.stringify(bindings), [bindings]);
  const datasetKey = useMemo(
    () => datasets
      .map((dataset) => `${dataset.id}:${dataset.currentVersionNo ?? ''}:${dataset.updatedAt ?? ''}`)
      .join('|'),
    [datasets],
  );
  const planKey = `${template?.id ?? ''}:${template?.version ?? ''}|${bindingKey}|${datasetKey}`;

  const refresh = useCallback(() => {
    setRefreshTick((current) => current + 1);
  }, []);

  useEffect(() => () => executor.dispose(), [executor]);

  useEffect(() => {
    if (!template || policy.refreshIntervalMs <= 0 || typeof window === 'undefined') {
      return undefined;
    }

    const refreshWhenVisible = () => {
      if (executionActive.current) return;
      if (typeof document === 'undefined' || document.visibilityState !== 'hidden') refresh();
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') refreshWhenVisible();
    };
    const timer = window.setInterval(refreshWhenVisible, policy.refreshIntervalMs);
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    return () => {
      window.clearInterval(timer);
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }
    };
  }, [policy.refreshIntervalMs, refresh, template]);

  useEffect(() => {
    if (!template) {
      executionActive.current = false;
      previousPlanKey.current = '';
      setState(EMPTY_STATE);
      return undefined;
    }

    const candidates = planScreenRuntimeQueries(template, bindings, datasets);
    if (!candidates.length) {
      executionActive.current = false;
      previousPlanKey.current = planKey;
      setState(EMPTY_STATE);
      return undefined;
    }

    const requestId = ++sequence.current;
    const controller = new AbortController();
    const samePlan = previousPlanKey.current === planKey;
    previousPlanKey.current = planKey;
    executionActive.current = true;
    const loadingIds = candidates.map(({ component }) => component.id);

    setState((current) => ({
      data: samePlan ? current.data : {},
      loadingIds,
      errors: {},
      lastUpdatedAt: samePlan ? current.lastUpdatedAt : undefined,
      stats: samePlan ? current.stats : EMPTY_STATS,
    }));

    const timer = window.setTimeout(() => {
      void executor.execute(candidates, {
        cacheTtlMs: policy.cacheTtlMs,
        maxConcurrency: policy.maxConcurrency,
        signal: controller.signal,
      }).then((result) => {
        if (requestId !== sequence.current || controller.signal.aborted) return;
        executionActive.current = false;
        setState((current) => ({
          // Timed refreshes keep the last good value for a component whose new request failed.
          data: samePlan ? { ...current.data, ...result.data } : result.data,
          loadingIds: [],
          errors: result.errors,
          lastUpdatedAt: Date.now(),
          stats: result.stats,
        }));
      }).catch((error) => {
        if (
          requestId !== sequence.current
          || controller.signal.aborted
          || isScreenRuntimeAbortError(error)
        ) return;
        executionActive.current = false;
        const message = runtimeErrorMessage(error);
        setState((current) => ({
          ...current,
          loadingIds: [],
          errors: Object.fromEntries(loadingIds.map((componentId) => [componentId, message])),
        }));
      });
    }, policy.debounceMs);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
      executionActive.current = false;
      if (sequence.current === requestId) sequence.current += 1;
    };
  }, [
    bindings,
    datasets,
    executor,
    planKey,
    policy.cacheTtlMs,
    policy.debounceMs,
    policy.maxConcurrency,
    refreshTick,
    template,
  ]);

  const loadingCount = state.loadingIds.length;
  return {
    ...state,
    loadingCount,
    boundCount: countBoundScreenComponents(template, bindings),
    isRefreshing: loadingCount > 0 && Object.keys(state.data).length > 0,
    refresh,
  };
}
