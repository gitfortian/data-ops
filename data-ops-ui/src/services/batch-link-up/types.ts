export type BatchLinkUpId = string | number;

export interface Pagination {
  total: number;
  pages: number;
  pageNo: number;
  pageSize: number;
}

export interface PagingData<T> {
  bizData: T[];
  pagination: Pagination;
}

export interface LinkupJobDefinition {
  id?: BatchLinkUpId;
  jobName?: string;
  jobDesc?: string;
  jobDefinitionInfo?: unknown;
  jobVersion?: number;
  createTime?: string;
  updateTime?: string;
}

export interface OfflineJobDefinitionVO extends LinkupJobDefinition {
  jobType?: 'BATCH';
  mode?: string;
  releaseState?: string;
  sourceType?: string;
  sinkType?: string;
  sourceDatasourceId?: BatchLinkUpId;
  sinkDatasourceId?: BatchLinkUpId;
  sourceDatasourceName?: string;
  sinkDatasourceName?: string;
  sourceTable?: string;
  sinkTable?: string;
  lastJobStatus?: string;
  lastErrorMessage?: string;
  instanceId?: BatchLinkUpId;
  engineJobId?: string;
  runMode?: string;
  duration?: number;
  readRowCount?: number;
  qps?: number;
  syncSize?: string;
  cronExpression?: string;
  scheduleStatus?: string;
  lastScheduleTime?: string;
  nextScheduleTime?: string;
  latestVersionNo?: number;
  hasPendingDraft?: boolean;
}

export interface OfflineVersionSummary {
  id: BatchLinkUpId;
  versionNo: number;
  checksum?: string;
  createdBy?: string;
  createTime?: string;
  current: boolean;
}

export interface OfflinePublishResult {
  version: OfflineVersionSummary;
  appended: boolean;
}

export interface OfflineVersionDetail {
  version: OfflineVersionSummary;
  payload: {
    definition?: unknown;
    jobSpec?: unknown;
    configDigest?: string;
  };
}

export interface OfflineSyncTaskPageQuery {
  current: number;
  pageSize: number;
  jobName?: string;
  id?: string;
  status?: string;
  sourceType?: string;
  sinkType?: string;
  sourceTable?: string;
  sinkTable?: string;
  createTimeStart?: string;
  createTimeEnd?: string;
}

export interface OfflineJobExecutionVO {
  id: BatchLinkUpId;
  jobDefinitionId: BatchLinkUpId;
  definitionVersion?: number;
  engineBaseUrl?: string;
  engineJobId?: string;
  externalExecutionId?: string;
  workerInstanceId?: string;
  status?: string;
  stateVersion?: number;
  attemptNo?: number;
  triggerType?: string;
  retryFromExecutionId?: BatchLinkUpId;
  cancellationRequested?: boolean;
  errorMessage?: string;
  sourceRecordCount: number;
  sinkAttemptedRecordCount?: number;
  sinkSuccessRecordCount: number;
  sinkCommittedRecordCount?: number;
  sourceReadBytes: number;
  sinkWrittenBytes: number;
  sourceAverageQps?: number;
  sinkAverageQps?: number;
  failedRecordCount?: number;
  skippedRecordCount?: number;
  databaseCommitMillis?: number;
  sqlExecutionMillis?: number;
  qps: number;
  durationMillis: number;
  createTime?: string;
  startTime?: string;
  endTime?: string;
  nextRetryTime?: string;
  lastSyncTime?: string;
  updateTime?: string;
}

export interface OfflineJobExecutionDetailVO extends OfflineJobExecutionVO {
  execution: OfflineJobExecutionVO;
  job?: Record<string, unknown>;
  pipelines?: Array<Record<string, unknown>>;
  tasks?: Array<Record<string, unknown>>;
  metrics?: Record<string, unknown>;
}

export interface OfflineTableDdl {
  dialect?: string;
  sourceTable?: string;
  targetTable?: string;
  createTableSql?: string;
  executed: boolean;
  status?: 'SUCCEEDED' | 'SKIPPED' | string;
  reason?: string;
  durationMillis: number;
  errorCode?: string;
  errorMessage?: string;
}

export interface OfflineTableMetric {
  id: string;
  pipelineId?: string;
  dataSetId?: string;
  status: string;
  sourceTable: string;
  sinkTable: string;
  sourceConnector?: string;
  sinkConnector?: string;
  sourceTaskCount: number;
  sinkTaskCount: number;
  readRowCount: number;
  writeRowCount: number;
  sinkAttemptedRecordCount: number;
  sinkCommittedRecordCount: number;
  failedRecordCount: number;
  unknownStateRecordCount: number;
  readQps: number;
  writeQps: number;
  sourceReadBytes: number;
  sinkWrittenBytes: number;
  tableDdl?: OfflineTableDdl;
  ddlDialect?: string;
  createTableSql?: string;
  ddlExecuted: boolean;
  ddlStatus?: string;
  ddlReason?: string;
  ddlDurationMillis: number;
  ddlErrorCode?: string;
  ddlErrorMessage?: string;
}

export interface OfflineExecutionLogEntry {
  sequence: number;
  timestampMillis?: number;
  timestamp?: string;
  source: 'YAK_OPS' | 'LINK_UP' | string;
  level: string;
  stage?: string;
  externalExecutionId?: string;
  engineJobId?: string;
  runId?: string;
  thread?: string;
  logger?: string;
  message?: string;
}

export interface OfflineExecutionLogPage {
  items: OfflineExecutionLogEntry[];
  nextCursor: string;
  completed: boolean;
  linkUpAvailable: boolean;
  warning?: string;
}

export interface OfflineBatchOperationError {
  jobDefinitionId?: BatchLinkUpId;
  message?: string;
}

export interface OfflineBatchOperationResult {
  successCount: number;
  failedCount: number;
  errors: OfflineBatchOperationError[];
}

/** Compatibility contracts retained for the historical task-definition screens. */
export interface DataSource {
  id?: string;
  name?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  director?: string;
  remark?: string;
  leaf?: boolean;
  submit?: boolean;
  parentId?: string;
  reviewer?: string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
  endTime?: string;
  currentVersion?: number;
}

export interface HistoryItem {
  id: string;
  jobName: string;
  jobStatus: any;
  time: string;
  endTime?: string;
  startTime: string;
}

export interface TableInfo {
  sourceDatabase: string;
  sourceTable: string;
  targetTable: string;
  method: string;
  ddl: string;
}
