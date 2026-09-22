import type { ScreenDataOverrides } from '@/components/screen-engine';
import type {
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
} from '@/services/dataset';
import {
  createScreenRuntimeAbortError,
  isScreenRuntimeAbortError,
  mapWithConcurrency,
} from './concurrency';
import type {
  ScreenRuntimeCandidate,
  ScreenRuntimeExecutionStats,
} from './model';
import { ScreenRuntimeQueryCache } from './query-cache';
import {
  queryScreenDatasetResult,
  toScreenComponentData,
  type ScreenRuntimeQueryOptions,
} from './query';

interface ScreenRuntimeQueryGroup {
  queryKey: string;
  dataset: PublishedDataset;
  payload: DatasetQueryPayload;
  candidates: ScreenRuntimeCandidate[];
}

interface ScreenRuntimeGroupResult {
  group: ScreenRuntimeQueryGroup;
  result?: DatasetQueryResult;
  error?: unknown;
  source: 'cache' | 'network';
}

export interface ScreenRuntimeExecutionOptions {
  cacheTtlMs: number;
  maxConcurrency: number;
  signal?: AbortSignal;
}

export interface ScreenRuntimeExecutionResult {
  data: ScreenDataOverrides;
  errors: Record<string, string>;
  stats: ScreenRuntimeExecutionStats;
}

export type ScreenRuntimeDatasetQuery = (
  dataset: PublishedDataset,
  payload: DatasetQueryPayload,
  options?: ScreenRuntimeQueryOptions,
) => Promise<DatasetQueryResult>;

const groupCandidates = (candidates: ScreenRuntimeCandidate[]) => {
  const groups = new Map<string, ScreenRuntimeQueryGroup>();
  candidates.forEach((candidate) => {
    const current = groups.get(candidate.queryKey);
    if (current) {
      current.candidates.push(candidate);
      return;
    }
    groups.set(candidate.queryKey, {
      queryKey: candidate.queryKey,
      dataset: candidate.dataset,
      payload: candidate.payload,
      candidates: [candidate],
    });
  });
  return [...groups.values()];
};

const errorMessage = (error: unknown) => (
  error instanceof Error ? error.message : 'Dataset 查询失败'
);

/**
 * Executes one runtime plan with query-level dedupe, bounded concurrency and a short-lived raw cache.
 * The executor is intentionally instantiated per useScreenRuntime hook, so cache lifetime follows the page.
 */
export class ScreenRuntimeExecutor {
  constructor(
    private readonly query: ScreenRuntimeDatasetQuery = queryScreenDatasetResult,
    private readonly cache = new ScreenRuntimeQueryCache(),
  ) {}

  async execute(
    candidates: ScreenRuntimeCandidate[],
    options: ScreenRuntimeExecutionOptions,
  ): Promise<ScreenRuntimeExecutionResult> {
    const groups = groupCandidates(candidates);
    if (!groups.length) {
      return {
        data: {},
        errors: {},
        stats: {
          candidateCount: 0,
          uniqueQueryCount: 0,
          deduplicatedCount: 0,
          networkQueryCount: 0,
          cacheHitQueryCount: 0,
        },
      };
    }

    const groupResults = await mapWithConcurrency(
      groups,
      options.maxConcurrency,
      async (group): Promise<ScreenRuntimeGroupResult> => {
        if (options.signal?.aborted) throw createScreenRuntimeAbortError();
        const cached = this.cache.get(group.queryKey, options.cacheTtlMs);
        if (cached) return { group, result: cached, source: 'cache' };

        try {
          const result = await this.query(
            group.dataset,
            group.payload,
            { signal: options.signal },
          );
          if (options.signal?.aborted) throw createScreenRuntimeAbortError();
          this.cache.set(group.queryKey, result);
          return { group, result, source: 'network' };
        } catch (error) {
          if (options.signal?.aborted || isScreenRuntimeAbortError(error)) {
            throw createScreenRuntimeAbortError();
          }
          return { group, error, source: 'network' };
        }
      },
      options.signal,
    );

    const data: ScreenDataOverrides = {};
    const errors: Record<string, string> = {};

    groupResults.forEach(({ group, result, error }) => {
      if (error || !result) {
        group.candidates.forEach(({ component }) => {
          errors[component.id] = errorMessage(error);
        });
        return;
      }

      group.candidates.forEach(({ component, binding, dataset }) => {
        try {
          const adapted = toScreenComponentData(component, binding, dataset, result);
          if (adapted) data[component.id] = adapted;
        } catch (adapterError) {
          errors[component.id] = errorMessage(adapterError);
        }
      });
    });

    return {
      data,
      errors,
      stats: {
        candidateCount: candidates.length,
        uniqueQueryCount: groups.length,
        deduplicatedCount: Math.max(0, candidates.length - groups.length),
        networkQueryCount: groupResults.filter((item) => item.source === 'network').length,
        cacheHitQueryCount: groupResults.filter((item) => item.source === 'cache').length,
      },
    };
  }

  dispose() {
    this.cache.clear();
  }
}
