export type HomeDataCenterPeriod = 'yesterday' | '7d' | '30d';
export type HomeTaskType = 'OFFLINE_SYNC' | 'WORKFLOW' | 'DATA_QUALITY';

export interface HomeDataCenterPeriodView {
  start: string;
  end: string;
}

export interface HomeLatestTask {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  durationMs: number;
  runCount: number;
  exceptionCount: number;
  status: string;
  detailPath: string;
}

export interface HomeDataCenterMetrics {
  successCount: number;
  runningCount: number;
  failedCount: number;
  scheduleCount: number;
  processedRecords: number;
  avgDurationMs: number;
}

export interface HomeDataCenterMetricCompare {
  successCount: number;
  runningCount: number;
  failedCount: number;
  scheduleCount: number;
  processedRecordsRate: number;
  avgDurationMs: number;
}

export interface HomeDataCenterOverview {
  period: HomeDataCenterPeriodView;
  latestTask?: HomeLatestTask;
  trend: { labels: string[]; values: number[] };
  metrics: HomeDataCenterMetrics;
  compare: HomeDataCenterMetricCompare;
}

export interface HomeRecentTask {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  lastRunTime: string;
  runCount: number;
  successCount: number;
  failedCount: number;
  lastDurationMs: number;
  lastStatus: string;
  detailPath: string;
}

export interface HomeRecentResponse {
  items: HomeRecentTask[];
}

export interface HomeScheduleItem {
  taskId: string;
  taskType: HomeTaskType | string;
  taskName: string;
  cronExpression?: string;
  status: string;
  lastScheduleTime?: string;
  nextScheduleTime?: string;
  detailPath: string;
}

export interface HomeScheduleResponse {
  period: HomeDataCenterPeriodView;
  total: number;
  items: HomeScheduleItem[];
}

export interface HomeAssetDatasetItem {
  id: string;
  name: string;
  description?: string | null;
  status: string;
  updatedAt?: string | null;
}

export interface HomeAssetDatasetOverview {
  datasetCount: number | null;
  tableAssetCount: number | null;
  columnAssetCount: number | null;
  todayCreatedCount: number | null;
  recentDatasets: HomeAssetDatasetItem[];
  onlineDatasets: HomeAssetDatasetItem[];
}

export interface HomeLineageNode {
  id: string;
  name: string;
  assetType: string;
  sourceType?: string | null;
}

export interface HomeLineageEdge {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  relationType: string;
}

export interface HomeLineageActivity {
  id: string;
  sourceName: string;
  targetName: string;
  relationType: string;
  occurredAt?: string | null;
}

export interface HomeAssetLineageOverview {
  assetCount: number | null;
  relationCount: number | null;
  todayUpdatedCount: number | null;
  datasetAssetCount: number | null;
  nodes: HomeLineageNode[];
  edges: HomeLineageEdge[];
  recentActivities: HomeLineageActivity[];
}

export interface HomeAssetOverview {
  dataset: HomeAssetDatasetOverview;
  lineage: HomeAssetLineageOverview;
}

export interface HomeQualityDimension {
  dimension: string;
  total: number;
  issues: number;
  passRate: number | null;
}

export interface HomeQualityIssue {
  id: string;
  executionNo: string;
  monitorId: string;
  monitorName: string;
  objectName?: string | null;
  tableName?: string | null;
  ruleName: string;
  dimension: string;
  columnName?: string | null;
  checkResult: string;
  queuedAt?: string | null;
}

export interface HomeQualityOverview {
  /** False means the quality read model is temporarily unavailable, not that metrics are zero. */
  available?: boolean;
  rangeStart?: string | null;
  rangeEnd?: string | null;
  passRate: number | null;
  monitoredTableCount: number | null;
  enabledRuleCount: number | null;
  todayExecutionCount: number | null;
  todayIssueTableCount: number | null;
  recentIssueCount: number | null;
  dimensions: HomeQualityDimension[];
  recentIssues: HomeQualityIssue[];
}

export interface HomeScheduleOccurrence {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  time: string;
  scheduleText: string;
  detailPath: string;
}

export interface HomeScheduleDay {
  date: string;
  count: number;
  items: HomeScheduleOccurrence[];
}

export interface HomeScheduleSummary {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  scheduleText: string;
  nextRunDate: string;
  nextRunTime: string;
  detailPath: string;
}

export interface HomeScheduleCalendar {
  month: string;
  totalSchedules: number;
  days: HomeScheduleDay[];
  overview: HomeScheduleSummary[];
}

export interface HomeResourceCenterRecentFile {
  id: string;
  name: string;
  fullPath?: string | null;
  suffix?: string | null;
  fileSize: number;
  updatedAt?: string | null;
}

export interface HomeResourceCenterOverview {
  /** False means the resource read model is temporarily unavailable, not that metrics are zero. */
  available?: boolean;
  fileCount: number | null;
  folderCount: number | null;
  totalBytes: number | null;
  recentFiles: HomeResourceCenterRecentFile[];
}

export interface HomeCockpitHeaderStats {
  dataSourceCount: number;
  runningCount: number;
}

export interface HomeCockpitOverview {
  header: HomeCockpitHeaderStats;
}
