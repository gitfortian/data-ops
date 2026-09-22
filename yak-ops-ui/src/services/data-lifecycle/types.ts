/** 数据生命周期(data-lifecycle)后端契约类型,对齐 /api/v1/lifecycle REST API。 */

export type PolicyScope = 'LAYER_DEFAULT' | 'CUSTOM';
export type Granularity = 'DAY' | 'MONTH' | 'YEAR';
export type StorageType = 'DORIS' | 'PAIMON' | 'UNSUPPORTED';
export type DispatchStatus = 'SUCCESS' | 'FAILED' | 'RETRYING' | 'EXHAUSTED';
export type ModelState = 'UNSET' | 'APPLIED' | 'DRIFT' | 'FAILED';
export type BindingSource = 'OVERRIDE' | 'LAYER_DEFAULT' | 'LEGACY_LAYER' | 'NONE';
export type LifecycleStatus = 'ENABLED' | 'DISABLED';
export type PublishStateType = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';

export interface LifecyclePageResult<T> {
  records: T[];
  total: number;
  pages: number;
  pageNo: number;
  pageSize: number;
}

export interface PolicyRecord {
  id: number;
  policyCode: string;
  policyName: string;
  scopeType: PolicyScope;
  layerCode?: string | null;
  partitionGranularity: Granularity;
  hotDays?: number | null;
  coldDays?: number | null;
  destroyDays?: number | null;
  builtin: boolean;
  status: LifecycleStatus;
  publishState?: PublishStateType;
  latestVersionNo?: number;
  draftRevision?: number;
  hasPendingDraft?: boolean;
  remark?: string | null;
  referenceCount: number;
  createTime?: string;
  updateTime?: string;
}

/** 后端 TtlPolicyVersionService.VersionSummary。 */
export interface PolicyVersionSummary {
  id: number;
  versionNo: number;
  checksum?: string | null;
  createdBy?: string | null;
  createTime?: string | null;
  current: boolean;
}

/** 后端 PublishResult：appended=false 表示内容未变、幂等复用最新版本。 */
export interface PolicyPublishResult {
  version: PolicyVersionSummary;
  appended: boolean;
}

/** 后端 VersionDetailView：payload 为该版全量快照(前端 diff 数据源)。 */
export interface PolicyVersionDetail {
  version: PolicyVersionSummary;
  payload: Record<string, unknown>;
}

export interface PolicyQueryParams {
  pageNo: number;
  pageSize: number;
  scopeType?: PolicyScope | '';
  layerCode?: string;
  keyword?: string;
}

export interface PolicyUpsertParams {
  policyCode?: string;
  policyName: string;
  scopeType: PolicyScope;
  layerCode?: string;
  partitionGranularity: Granularity;
  hotDays?: number | null;
  coldDays?: number | null;
  destroyDays?: number | null;
  remark?: string;
}

export interface LayerTemplate {
  layerCode: string;
  layerName: string;
  hotDays?: number | null;
  coldDays?: number | null;
  destroyDays?: number | null;
  partitionGranularity: Granularity;
}

/** 后端 TtlStatement 记录。 */
export interface TtlStatementInfo {
  storageType: StorageType;
  qualifiedTable: string;
  statement: string;
  writable: boolean;
  note?: string | null;
}

/** 后端 ModelTtlResolution 记录(policy 为 LifecyclePolicyPO 原始字段)。 */
export interface ModelTtlResolution {
  model: {
    id: number;
    name: string;
    tableName: string;
    dialect?: string | null;
    layerCode?: string | null;
    partitionType?: string | null;
  };
  layer?: {
    code: string;
    name: string;
    lifecycleDays?: number | null;
    datasourceId?: number | null;
    databaseName?: string | null;
  } | null;
  bindingSource: BindingSource;
  policy?: PolicyRecord | null;
  virtualPolicy: boolean;
  state: ModelState;
  lastDispatch?: DispatchRecord | null;
  statement?: TtlStatementInfo | null;
  hasTimePartition: boolean;
  previewable: boolean;
  notPreviewableReason?: string | null;
}

export interface PartitionSplit {
  total?: number | null;
  hotCount: number;
  coldCount: number;
  deletedCount: number;
  hotRange?: string | null;
  coldRange?: string | null;
  deletedPartitions: string[];
  estimated: boolean;
  estimatedReason?: string | null;
}

export interface ModelPreview {
  modelId: number;
  modelName: string;
  tableName: string;
  policyName?: string | null;
  policySource: string;
  hotDays?: number | null;
  destroyDays?: number | null;
  statement?: string | null;
  dispatchable: boolean;
  notDispatchableReason?: string | null;
  split: PartitionSplit;
  nextCleanup?: string | null;
}

export interface PreviewBatch {
  confirmToken: string;
  tokenExpiresInSeconds: number;
  previews: ModelPreview[];
}

export interface DispatchOutcome {
  modelId: number;
  modelName: string;
  attempted: boolean;
  success: boolean;
  recordId?: number | null;
  message?: string | null;
}

/** 后端 LifecycleDispatchRecordPO 原始行。 */
export interface DispatchRecord {
  id: number;
  modelId: number;
  policyId?: number | null;
  triggerType?: string | null;
  databaseName?: string | null;
  tableName?: string | null;
  storageType?: string | null;
  statement?: string | null;
  status: DispatchStatus;
  attempts?: number | null;
  nextRetryTime?: string | null;
  errorMessage?: string | null;
  partitionHot?: number | null;
  partitionCold?: number | null;
  partitionDeleted?: number | null;
  operator?: string | null;
  createTime?: string | null;
  finishTime?: string | null;
}

export interface DispatchRecordQueryParams {
  pageNo: number;
  pageSize: number;
  status?: DispatchStatus | '';
  modelId?: number;
}

export interface MonitorModelView {
  modelId: number;
  modelCode: string;
  modelName: string;
  layerCode?: string | null;
  state: ModelState;
  policyName?: string | null;
  bindingSource?: BindingSource | null;
  destroyDays?: number | null;
  lastDispatchStatus?: DispatchStatus | null;
  lastDispatchTime?: string | null;
  message?: string | null;
}

export interface MonitorModelQueryParams {
  pageNo: number;
  pageSize: number;
  state?: ModelState | '';
  layerCode?: string;
  keyword?: string;
}

export interface MonitorAlert {
  recordId: number;
  modelId: number;
  status: DispatchStatus;
  attempts?: number | null;
  errorMessage?: string | null;
  time?: string | null;
}

export interface MonitorSummary {
  total: number;
  applied: number;
  drift: number;
  failed: number;
  unset: number;
  alerts: MonitorAlert[];
}

export interface LayerVolume {
  layerCode: string;
  sizeBytes: number;
  sizeGb: number;
}

export interface HotColdSplit {
  hotBytes: number;
  coldBytes: number;
  estimated: boolean;
}

export interface TrendPoint {
  date: string;
  sizeBytes: number;
}

export interface StorageStats {
  snapshotDate?: string | null;
  totalBytes: number;
  byLayer: LayerVolume[];
  hotCold: HotColdSplit;
  monthlyCost?: number | null;
  pricePerGbMonth?: string | null;
}

/** 对齐后端 {@code StorageStatsService.TableStorage}；快照日期要跟量一起显示——它是每日快照，不是实时值。 */
export interface TableStorage {
  snapshotDate?: string | null;
  layerCode?: string | null;
  datasourceId?: number | null;
  databaseName?: string | null;
  tableName?: string | null;
  sizeBytes: number;
  sizeGb: number;
}
