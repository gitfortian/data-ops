export type DevelopmentTaskType =
  | 'SQL'
  | 'SHELL'
  | 'HTTP'
  | 'PYTHON'
  | 'JAVA';
export type DevelopmentOutputNodeType = 'DATASET' | 'DATA_SERVICE';
export type DevelopmentNodeType =
  | DevelopmentTaskType
  | DevelopmentOutputNodeType;
export type DevelopmentId = string;

export interface DevelopmentDirectory {
  id: DevelopmentId;
  parentId?: DevelopmentId | null;
  name: string;
  path: string;
  createTime?: string;
  updateTime?: string;
}

export interface CreateDevelopmentDirectoryPayload {
  parentId?: DevelopmentId;
  name: string;
}

export interface DevelopmentResourceNodeBase {
  id: DevelopmentId;
  name: string;
  projectId?: DevelopmentId | null;
  directoryId?: DevelopmentId | null;
  configured: boolean;
  createTime?: string;
  updateTime?: string;
  updatedBy?: string | null;
  pendingPublish?: boolean;
}

export interface DevelopmentNode extends DevelopmentResourceNodeBase {
  type: DevelopmentTaskType;
}

export interface DevelopmentOutputNode extends DevelopmentResourceNodeBase {
  type: DevelopmentOutputNodeType;
}

export type DevelopmentResourceNode =
  | DevelopmentNode
  | DevelopmentOutputNode;

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentNodeType;
  /** Omit to create the node in the data-development root. */
  directoryId?: DevelopmentId;
}

export interface DevelopmentTaskDefinition {
  taskType: DevelopmentTaskType;
  schemaVersion: number;
  content: string;
  configJson: string;
}

export interface DevelopmentTaskDraft {
  nodeId: DevelopmentId;
  definition: DevelopmentTaskDefinition;
  draftRevision: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface SaveDevelopmentTaskDraftPayload
  extends DevelopmentTaskDefinition {
  baseRevision: number;
}

export interface DevelopmentTaskRevisionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentTaskRevision
  extends DevelopmentTaskRevisionSummary {
  definition: DevelopmentTaskDefinition;
}

export type DevelopmentTaskExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMEOUT';

/** Immediate acknowledgement after the shared Task Runtime accepts an editor run. */
export interface DevelopmentTaskExecutionSubmission {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  taskType: DevelopmentTaskType;
  runtimeExecutionId?: string | null;
  status: DevelopmentTaskExecutionStatus;
}

/** Workbench projection of one durable execution. */
export interface DevelopmentTaskRunResult {
  executionId?: DevelopmentId;
  runtimeExecutionId?: string | null;
  status: DevelopmentTaskExecutionStatus;
  message: string;
  durationMs: number;
  output: Record<string, unknown>;
}

export interface DevelopmentSqlResultColumn {
  name: string;
  label: string;
  typeName?: string;
  jdbcType?: number;
  nullable?: boolean;
}

export interface DevelopmentSqlRunOutput {
  kind?: 'RESULT_SET' | 'UPDATE_COUNT';
  columns?: DevelopmentSqlResultColumn[];
  rows?: unknown[][];
  returnedRows?: number;
  affectedRows?: number;
  truncated?: boolean;
  dataSourceId?: string;
}

export interface DevelopmentSqlLineagePreviewRequest
  extends DevelopmentTaskDefinition {
  databaseName?: string;
  schemaName?: string;
}

export type DevelopmentSqlLineagePreviewStatus =
  | 'SUCCESS'
  | 'PARTIAL'
  | 'UNRESOLVED'
  | 'FAILED';

export interface DevelopmentSqlLineagePreviewAsset {
  id: string;
  assetKey: string;
  assetType: 'TABLE' | 'SQL_TASK';
  name: string;
  sourceType?: string;
  sourceId?: string;
  parentAssetId?: string;
  dataSourceId?: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  columnName?: string;
  properties?: Record<string, unknown>;
}

export interface DevelopmentSqlLineagePreviewRelation {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  relationType: 'READS_FROM' | 'WRITES_TO';
  sourceType?: string;
  sourceId?: string;
  expression?: string;
  properties?: Record<string, unknown>;
}

export interface DevelopmentSqlLineagePreviewGraph {
  root: DevelopmentSqlLineagePreviewAsset;
  direction: 'BOTH';
  depth: number;
  nodes: DevelopmentSqlLineagePreviewAsset[];
  relations: DevelopmentSqlLineagePreviewRelation[];
}

export interface DevelopmentSqlLineageColumnMapping {
  sourceTable: string;
  sourceColumn: string;
  sourceDataType?: string | null;
  targetTable?: string | null;
  targetColumn: string;
  targetDataType?: string | null;
  mappingKind: 'IDENTITY' | 'TRANSFORMATION' | 'AGGREGATION';
  expression?: string | null;
  outputOrdinal: number;
  sourceOrdinal: number;
}

export interface DevelopmentSqlLineagePreview {
  status: DevelopmentSqlLineagePreviewStatus;
  dataSourceId: string;
  statementCount: number;
  inputTableCount: number;
  outputTableCount: number;
  columnMappingCount: number;
  candidateOutputColumnCount: number;
  unresolvedColumnReferenceCount: number;
  parseError?: string | null;
  columnParseError?: string | null;
  graph: DevelopmentSqlLineagePreviewGraph;
  columnMappings: DevelopmentSqlLineageColumnMapping[];
}

/** One editor output field checked against the semantic NAMING standards (hint, never a gate). */
export interface DevelopmentStandardFieldCheck {
  field: string;
  evaluated: boolean;
  matched: boolean;
  standardId?: number | null;
  standardCode?: string | null;
  standardName?: string | null;
  ruleExpr?: string | null;
}

export interface DevelopmentStandardCheck {
  items: DevelopmentStandardFieldCheck[];
  fieldCount: number;
  truncated: boolean;
  parseError?: string | null;
}

export interface DevelopmentTaskExecutionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  taskName: string;
  taskType: DevelopmentTaskType;
  schemaVersion: number;
  triggerType: string;
  runtimeExecutionId?: string | null;
  retryOfExecutionId?: DevelopmentId | null;
  status: DevelopmentTaskExecutionStatus;
  operatorName?: string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface DevelopmentTaskExecutionDetail
  extends DevelopmentTaskExecutionSummary {
  content: string;
  configJson: string;
  output: Record<string, unknown>;
}

export interface DevelopmentTaskExecutionPage {
  records: DevelopmentTaskExecutionSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DevelopmentTaskExecutionQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: DevelopmentTaskExecutionStatus;
  taskType?: DevelopmentTaskType;
  triggerType?: string;
  startTime?: string;
  endTime?: string;
}

export type DevelopmentReleaseStatus = 'ONLINE' | 'OFFLINE' | 'DISABLED';

export interface DevelopmentReleaseSummary {
  assetId: DevelopmentId;
  nodeId: DevelopmentId;
  taskName: string;
  taskType: DevelopmentTaskType;
  status: DevelopmentReleaseStatus;
  currentRevisionId: DevelopmentId;
  currentRevisionNo: number;
  latestRevisionNo: number;
  hasNewerRevision: boolean;
  checksum: string;
  revisionCreateTime?: string | null;
  updateTime?: string | null;
}

export interface DevelopmentReleasePage {
  records: DevelopmentReleaseSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
  onlineCount: number;
  offlineCount: number;
  disabledCount: number;
}

export interface DevelopmentReleaseDetail {
  release: DevelopmentReleaseSummary;
  currentRevision: DevelopmentTaskRevision;
  revisions: DevelopmentTaskRevisionSummary[];
}

export interface DevelopmentReleaseQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: DevelopmentReleaseStatus | 'ALL';
  taskType?: DevelopmentTaskType;
}

export type YakEditorLineHighlight = 'line' | 'none' | 'gutter' | 'all';
export type YakSqlCompletionFqn = 'none' | 'table' | 'all';
export type YakRenderWhitespace =
  | 'none'
  | 'boundary'
  | 'selection'
  | 'trailing'
  | 'all';

export interface YakEditorSettings {
  theme: string;
  fontSize: number;
  fontFamily: string;
  customFontFamily: string;
  lineHeight: number;
  showLineNumber: boolean;
  showMinimap: boolean;
  wordWrap: boolean;
  folding: boolean;
  renderLineHighlight: YakEditorLineHighlight;
  keywordCase: 'lower' | 'upper';
  sqlCompletionFQN: YakSqlCompletionFqn;
  renderWhitespace: YakRenderWhitespace;
}
