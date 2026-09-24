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
  taskType?: DevelopmentTaskType;
  sqlDialect?: DevelopmentSqlDialect;
}

export interface DevelopmentOutputNode extends DevelopmentResourceNodeBase {
  type: DevelopmentOutputNodeType;
}

export type DevelopmentResourceNode = DevelopmentNode | DevelopmentOutputNode;

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentNodeType;
  directoryId?: DevelopmentId;
}

export interface MoveDevelopmentResourcePayload {
  directoryId?: DevelopmentId | null;
}

export type DevelopmentSqlDialect =
  | 'GENERIC'
  | 'MYSQL'
  | 'TIDB'
  | 'GOLDENDB'
  | 'GBASE8C'
  | 'GBASE8A'
  | 'GBASE8S'
  | 'HANA'
  | 'ORACLE'
  | 'POSTGRE_SQL'
  | 'DB2'
  | 'OPEN_GAUSS'
  | 'SQL_SERVER'
  | 'OCEANBASE'
  | 'YASHAN_DB'
  | 'HIGHGO'
  | 'IRIS'
  | 'XUGU'
  | 'DUCKDB'
  | 'DORIS'
  | 'STARROCKS'
  | 'CLICKHOUSE'
  | 'KINGBASE'
  | 'DAMENG';

export interface DevelopmentTaskDefinition {
  taskType: DevelopmentTaskType;
  schemaVersion: number;
  content: string;
  configJson: string;
}

export interface DevelopmentTaskDraft extends DevelopmentTaskDefinition {
  nodeId: DevelopmentId;
  draftRevision: number;
  updateTime?: string;
}

export interface DevelopmentTaskRevisionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  taskType: DevelopmentTaskType;
  schemaVersion: number;
  checksum?: string;
  createTime?: string;
}

export interface DevelopmentTaskRevision extends DevelopmentTaskRevisionSummary {
  content: string;
  configJson: string;
}

export interface SaveDevelopmentTaskDraftPayload extends DevelopmentTaskDefinition {
  baseRevision: number;
}

export type DevelopmentTaskExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMEOUT';

export interface DevelopmentTaskExecutionSubmission {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  taskType: DevelopmentTaskType;
  runtimeExecutionId?: string | null;
  status: DevelopmentTaskExecutionStatus;
}

export interface DevelopmentTaskRunResult {
  executionId?: DevelopmentId;
  runtimeExecutionId?: string | null;
  status: DevelopmentTaskExecutionStatus;
  message?: string | null;
  durationMs?: number | null;
  output?: Record<string, unknown>;
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
  failureReason?: string | null;
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

export interface DevelopmentTaskRevisionPage {
  records: DevelopmentTaskRevisionSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DevelopmentTaskPublishValidationIssue {
  code: string;
  field?: string | null;
  message: string;
}

export interface DevelopmentTaskPublishValidation {
  nodeId: DevelopmentId;
  draftRevision: number;
  valid: boolean;
  issues: DevelopmentTaskPublishValidationIssue[];
}

export interface DevelopmentDatasetDefinition {
  nodeId: DevelopmentId;
  sourceNodeId?: DevelopmentId | null;
  sourceRevisionId?: DevelopmentId | null;
  sourceRevisionNo?: number | null;
  name?: string;
  description?: string;
  schemaJson?: string;
  configured?: boolean;
}

export interface DevelopmentDataServiceDefinition {
  nodeId: DevelopmentId;
  sourceNodeId?: DevelopmentId | null;
  sourceRevisionId?: DevelopmentId | null;
  sourceRevisionNo?: number | null;
  name?: string;
  description?: string;
  path?: string;
  method?: string;
  definitionJson?: string;
  configured?: boolean;
}
