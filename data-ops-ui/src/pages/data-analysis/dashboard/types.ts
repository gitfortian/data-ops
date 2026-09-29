export type DashboardStatusFilter =
  | 'all'
  | 'published'
  | 'draft'
  | 'unpublished';

export type DashboardLifecycleState = Exclude<DashboardStatusFilter, 'all'>;

export type DashboardTimeRange = 'all' | '7d' | '30d';

export interface DashboardLifecycle {
  published: boolean;
  hasDraft: boolean;
  state: DashboardLifecycleState;
}

export interface DashboardLifecycleCounts {
  published: number;
  draft: number;
  unpublished: number;
}

export interface DashboardListFilters {
  keyword: string;
  status: DashboardStatusFilter;
  timeRange: DashboardTimeRange;
}
