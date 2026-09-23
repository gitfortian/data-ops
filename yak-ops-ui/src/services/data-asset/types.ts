/** 数据资产(Asset Center)前端类型契约,对齐 yak-ops-business-asset controller/DTO。 */

export type AssetStatus = 'PENDING' | 'PUBLISHED' | 'OFFLINE' | 'IGNORED' | 'SOURCE_GONE';

export type AssetSourceType =
  | 'MODEL' | 'METRIC' | 'METADATA' | 'DATASET' | 'DASHBOARD' | 'CHART' | 'TASK' | 'MANUAL';

/** 展示类型(与 LineageAssetType 命名对齐;DOC 仅 MANUAL 用)。 */
export type AssetType = 'TABLE' | 'METRIC' | 'DATASET' | 'DASHBOARD' | 'CHART' | 'TASK' | 'DOC';

export type HealthGrade = 'A' | 'B' | 'C' | 'D';

export interface AssetRecord {
  id: number;
  assetKey: string;
  sourceType: AssetSourceType;
  sourceId?: string;
  assetType?: AssetType;
  name: string;
  description?: string;
  layerCode?: string;
  domainCode?: string;
  directoryId?: number | null;
  owner?: string;
  status: AssetStatus;
  securityLevelCode?: string;
  healthScore?: number | null;
  healthGrade?: HealthGrade | null;
  viewCount30d?: number;
  accessUri?: string;
  sourceUpdatedAt?: string;
  firstListedAt?: string;
  lastListedAt?: string;
  lastOfflineAt?: string;
  lastOfflineReason?: string;
  reconciledAt?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AssetPageParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: AssetStatus;
  assetType?: string;
  sourceType?: string;
  owner?: string;
  directoryId?: number;
  assetTypes?: string[];
  layerCodes?: string[];
  statuses?: AssetStatus[];
  grades?: HealthGrade[];
  tagIds?: number[];
  /** 空=健康×活跃度加权公式;TIME/VIEWS/NAME。 */
  sortBy?: 'TIME' | 'VIEWS' | 'NAME';
}

export interface AssetPageResult {
  records: AssetRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
}

/** 分区容错:OK|UNAVAILABLE,note 为不可用原因(不伪造空)。 */
export interface AssetSection<T = unknown> {
  status: 'OK' | 'EMPTY' | 'NOT_APPLICABLE' | 'UNAVAILABLE' | 'PERMISSION_DENIED';
  note?: string | null;
  data?: T | null;
}

export interface AssetSourceAttrs {
  name?: string;
  description?: string;
  assetType?: string;
  layerCode?: string;
  domainCode?: string;
  suggestedOwner?: string;
  updatedAt?: string;
  sourceChanged?: boolean;
  extra?: Record<string, unknown> | null;
}

export interface LineageAssetNode {
  id: number;
  assetKey: string;
  assetType?: string;
  name?: string;
  sourceType?: string;
}

export interface LineageGraphData {
  root?: LineageAssetNode;
  direction?: string;
  depth?: number;
  nodes?: LineageAssetNode[];
  relations?: { id?: number; fromAssetId?: number; toAssetId?: number; relationType?: string }[];
}

export interface ClassificationData {
  objectKey: string;
  levelCode?: string;
  levelName?: string;
  levelRank?: number;
  categoryCode?: string;
  categoryName?: string;
}

export interface AssetHealthData {
  score: number;
  grade?: string;
  /** 评分明细 JSON 串(每项得分与缺口)。 */
  detail?: string | null;
}

export interface DailyView {
  date: string;
  count: number;
}

/** 资产状态条一格(docs/PLATFORM_CORE_FLOW.md M2-1 只读口径);PASS|FAIL|NA|UNKNOWN。 */
export interface StatusFlowStep {
  key: string;
  title: string;
  result: 'PASS' | 'FAIL' | 'NA' | 'UNKNOWN';
  note?: string | null;
  facts?: Record<string, unknown>;
}

export interface AssetStatusFlowData {
  steps: StatusFlowStep[];
  currentStageKey: string;
}

export interface AssetDetailView {
  asset: AssetRecord;
  sections: {
    statusFlow?: AssetSection<AssetStatusFlowData>;
    sourceAttrs?: AssetSection<AssetSourceAttrs>;
    technicalMetadata?: AssetSection<{
      summary?: { dataSourceId?: string; databaseName?: string; schemaName?: string;
        tableName?: string; entityStatus?: string; metadataEntityId?: string };
      ownerDomain?: string;
      provenance?: { sourceDomain?: string; sourceId?: string; observedAt?: string };
      actions?: { label: string; target: string; sourceId: string }[];
    }>;
    lineage?: AssetSection<LineageGraphData>;
    security?: AssetSection<ClassificationData>;
    fields?: AssetSection;
    quality?: AssetSection;
    ttl?: AssetSection;
    trend?: AssetSection<DailyView[]>;
    health?: AssetSection<AssetHealthData>;
  };
}

/** 跨域引用摘要(GET /assets/lineage-summary,M2-3)。available=false 时计数不可信,原因见 reason。 */
export interface AssetLineageSummary {
  available: boolean;
  reason?: string | null;
  /** -1 = 事实源不可用;否则为去重后的上游 TABLE 数(不含根). */
  upstreamTables: number;
  /** -1 = 质量事实源不可用;否则为其中未稽核张数. */
  unauditedTables: number;
  depth: number;
}

export interface ManualRegisterParams {
  name: string;
  assetCode?: string;
  assetType?: AssetType;
  description?: string;
  owner?: string;
  accessUri?: string;
  layerCode?: string;
  domainCode?: string;
  directoryId?: number;
}

export interface AssetSnapshotEditParams {
  name?: string;
  description?: string;
  accessUri?: string;
}

export interface AssetTagRecord {
  id: number;
  tagCode: string;
  tagName: string;
  color?: string;
  description?: string;
  usageCount: number;
  createTime?: string;
}

export interface DirNode {
  id: number;
  dirCode: string;
  dirName: string;
  parentId: number;
  path: string;
  sortOrder: number;
  iconKey?: string;
  description?: string;
  builtin: boolean;
  children: DirNode[];
}

/** 预检缺口编码(与 AssetLifecycleService 对齐)。 */
export type AssetGapCode =
  | 'OWNER_MISSING'
  | 'DESCRIPTION_MISSING'
  | 'DIRECTORY_MISSING'
  | 'SECURITY_LEVEL_SUGGESTED';

export interface AssetGapItem {
  assetId: number;
  assetKey: string;
  name: string;
  status: AssetStatus;
  gaps: AssetGapCode[];
  advisories: AssetGapCode[];
  defaults: Record<string, string>;
}

export interface PrecheckResult {
  token: string;
  expiresIn: number;
  allClear: boolean;
  items: AssetGapItem[];
}

// ---------- ticket 100:概览驾驶舱 / 盘点 / 编目 ----------

export interface OverviewKpis {
  total: number;
  published: number;
  pending: number;
  added30d: number;
  /** 0..1 比率,分母 0 记 0(不伪造)。 */
  ownerCoverage: number;
  classifiedRate: number;
  gradeACount: number;
  gradeDCount: number;
}

export interface OverviewTodos {
  pendingPublish: number;
  openChanges: number;
  noOwner: number;
  gradeD: number;
  sourceGone: number;
}

export interface DistributionRow {
  k: string;
  c: number;
}

export interface RecentAssetRow {
  assetId: number;
  name: string;
  assetType?: string;
  owner?: string;
  at?: string;
}

export interface AssetOverviewData {
  kpis: OverviewKpis;
  todos: OverviewTodos;
  distributions: {
    status: DistributionRow[];
    grade: DistributionRow[];
    type: DistributionRow[];
    layer: DistributionRow[];
  };
  recentListed: RecentAssetRow[];
  recentOffline: RecentAssetRow[];
  generatedAt?: string;
}

export interface ReconcileLastRun {
  sourceType?: string;
  scanned?: number;
  created?: number;
  changed?: number;
  restored?: number;
  at?: string;
  error?: string | null;
}

export interface ReconcileStatusRow {
  sourceType: AssetSourceType;
  registered: boolean;
  lastRun?: ReconcileLastRun | null;
}

export type ChangeType = 'NEW' | 'META_CHANGED' | 'SOURCE_GONE' | 'REAPPEARED';

export type ChangeHandleStatus = 'OPEN' | 'CONFIRMED' | 'IGNORED';

export interface ChangeRecord {
  id: number;
  assetId?: number | null;
  assetName?: string;
  changeType: ChangeType;
  diff?: Record<string, unknown> | null;
  handleStatus: ChangeHandleStatus;
  createdBy?: string;
  createTime?: string;
}

export interface RecordPage<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DirectoryUpsertParams {
  parentId?: number;
  dirCode?: string;
  dirName: string;
  iconKey?: string;
  description?: string;
  sortOrder?: number;
}

export interface TagUpsertParams {
  tagCode?: string;
  tagName: string;
  color?: string;
  description?: string;
}

export interface RuleConditions {
  assetTypes?: string[] | null;
  layerCodes?: string[] | null;
  domainCodes?: string[] | null;
  sourceTypes?: string[] | null;
  nameRegex?: string | null;
  keyword?: string | null;
}

export type RuleType = 'DIRECTORY' | 'TAG';

export interface RuleRecord {
  id: number;
  ruleName: string;
  ruleType: RuleType;
  conditions: RuleConditions;
  targetDirectoryId?: number | null;
  targetTagId?: number | null;
  priority?: number;
  enabled: boolean;
  lastApplyHit?: number | null;
  createdBy?: string;
  updateTime?: string;
}

export interface RuleUpsertParams {
  ruleName: string;
  ruleType: RuleType;
  conditions?: RuleConditions;
  targetDirectoryId?: number | null;
  targetTagId?: number | null;
  priority?: number;
}

export interface RuleDryRunResult {
  hitCount: number;
  samples: { assetId: number; name: string; targetId?: number | null }[];
}
