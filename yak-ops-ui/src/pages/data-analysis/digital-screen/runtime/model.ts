import type {
  ScreenComponent,
  ScreenDataOverrides,
} from '@/components/screen-engine';
import type {
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
} from '@/services/dataset';
import type {
  DigitalScreenBindings,
  DigitalScreenComponentBinding,
} from '@/services/digital-screen';

export interface ScreenRuntimeAdapterContext {
  component: ScreenComponent;
  binding: DigitalScreenComponentBinding;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}

export interface ScreenRuntimeCandidate {
  component: ScreenComponent;
  binding: DigitalScreenComponentBinding;
  dataset: PublishedDataset;
  payload: DatasetQueryPayload;
  /** Stable identity used for request grouping and short-lived raw-result caching. */
  queryKey: string;
}

export interface ScreenRuntimeExecutionStats {
  candidateCount: number;
  uniqueQueryCount: number;
  deduplicatedCount: number;
  networkQueryCount: number;
  cacheHitQueryCount: number;
}

export interface ScreenRuntimeDataState {
  data: ScreenDataOverrides;
  loadingIds: string[];
  errors: Record<string, string>;
  lastUpdatedAt?: number;
  stats: ScreenRuntimeExecutionStats;
}

export interface ScreenRuntimeState extends ScreenRuntimeDataState {
  loadingCount: number;
  boundCount: number;
  /** True when old data remains visible while a refresh is running. */
  isRefreshing: boolean;
  refresh: () => void;
}

export interface ScreenRuntimePlanInput {
  template?: { components: ScreenComponent[] };
  bindings: DigitalScreenBindings;
  datasets: PublishedDataset[];
}
