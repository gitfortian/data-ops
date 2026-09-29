import type {
  ReleaseState,
  RealtimeStateGroup,
} from '@/services/realtime-sync';

export type * from '@/services/realtime-sync';
export type { ApiResponse } from '@/services/http/response';

export type RealtimePageStateGroup = 'ALL' | RealtimeStateGroup;

export interface RealtimeFilterState {
  keyword?: string;
  id?: string;
  releaseState?: ReleaseState;
  stateGroup: RealtimePageStateGroup;
}

export interface RealtimePaginationState {
  current: number;
  pageSize: number;
  total: number;
}

export type RealtimeFilterField = keyof RealtimeFilterState;
