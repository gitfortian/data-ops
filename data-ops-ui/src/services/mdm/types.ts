/** 主数据管理类型(ticket 50/51)。 */

export type MdmEntityStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED';

export type MdmEntityId = number;

export interface MdmEntityRecord {
  id: MdmEntityId;
  code: string;
  name: string;
  status: MdmEntityStatus;
  owner?: string;
  description?: string;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MdmPageInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface MdmPageResult<T = MdmEntityRecord> {
  bizData: T[];
  pagination: MdmPageInfo;
}

export interface MdmEntityPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: MdmEntityStatus | '';
}

export interface MdmEntitySavePayload {
  code: string;
  name: string;
  owner?: string;
  description?: string;
}

export interface MdmEntityUpdatePayload {
  name: string;
  owner?: string;
  description?: string;
}

export type MdmAttributeType = 'PK' | 'ATTR' | 'RELATION';

export interface MdmAttributeRecord {
  id: number;
  entityId: number;
  code: string;
  name: string;
  type: MdmAttributeType;
  dataType?: string;
  stdTypeId?: number;
  stdTypeName?: string;
  stdUnitId?: number;
  stdUnitName?: string;
  stdCodeSetCode?: string;
  stdSecurityId?: number;
  stdSecurityName?: string;
  required: boolean;
  businessDesc?: string;
  sortOrder: number;
  status: string;
  createTime?: string;
  updateTime?: string;
}

export interface MdmAttributeSavePayload {
  code: string;
  name: string;
  attrType: MdmAttributeType;
  dataType?: string;
  stdTypeId?: number;
  stdUnitId?: number;
  stdCodeSetCode?: string;
  stdSecurityId?: number;
  required?: boolean;
  businessDesc?: string;
  sortOrder?: number;
}

export type MdmAttributeUpdatePayload = Omit<MdmAttributeSavePayload, 'code'>;

export type MdmSourceRole = 'MAIN' | 'AUXILIARY';

export interface MdmSourceRecord {
  id: number;
  entityId: number;
  entityCode: string;
  entityName: string;
  datasourceId: number;
  datasourceName: string;
  database?: string;
  schema?: string;
  table: string;
  /** 属性编码→源列名;null/缺省=同名回退。 */
  fieldMapping?: Record<string, string> | null;
  role: MdmSourceRole;
  status: string;
  createTime?: string;
}

export interface MdmTableCandidate {
  database?: string;
  schema?: string;
  name: string;
  type?: string;
  remarks?: string;
  candidates: MdmCandidateHint[];
  confirmed: boolean;
}

export interface MdmCandidateHint {
  entityId: number;
  entityCode: string;
  entityName: string;
}

export interface MdmSourceConfirmPayload {
  entityId: number;
  datasourceId: number;
  database?: string;
  schema?: string;
  table: string;
  role?: MdmSourceRole;
  /** 仅提交与属性编码不同名的映射;全同名时省略(后端同名回退)。 */
  fieldMapping?: Record<string, string>;
}

// ===== R1 采集落地(复用数据集成,sync 零改动) =====

export interface MdmCollectLinkRecord {
  id: number;
  entityId: number;
  sourceId: number;
  datasourceId: number;
  landingTable: string;
  jobDefinitionId: number;
}

export interface MdmCollectStatus {
  linkId: number;
  entityId: number;
  sourceId: number;
  sourceTable: string;
  datasourceId: number;
  landingTable: string;
  jobDefinitionId: number;
  jobName?: string | null;
  releaseState?: string | null;
  lastJobStatus?: string | null;
  lastSuccessTime?: string | null;
  lastSuccessRows?: number | null;
  linkCreateTime?: string | null;
}

export interface MdmCollectRunReceipt {
  id?: number;
  status?: string;
  errorMessage?: string | null;
}

export type MdmRecordStatus = 'ACTIVE' | 'MERGED' | 'DELETED';

export interface MdmRecord {
  id: number;
  entityId: number;
  masterId: string;
  attributes: string;
  sourceIds: string;
  /** JSON map of values maintained by MDM approvals/cleansing and preserved on source refresh. */
  attributeOverrides?: string;
  status: MdmRecordStatus;
  version: number;
  createTime?: string;
  updateTime?: string;
}

export interface MdmRecordPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: MdmRecordStatus | '';
}

/** 加工任务注册回执(R2,review P2-6):数据开发 SQL 节点 + 草稿。 */
export interface MdmProcessingTaskReceipt {
  nodeId: number;
  taskName: string;
  nodeCreated: boolean;
}

/** 落地表质量状态(R3,实时反查 quality 模块,不落库)。 */
export interface MdmLandingQualityStatus {
  landingTable: string;
  datasourceId: number;
  datasourceName: string;
  database: string;
  assetRegistered: boolean;
  monitorId?: number | null;
  monitorName?: string | null;
  monitorEnabled?: boolean;
  ruleCount: number;
  lastResult?: string | null;
  lastExecutionNo?: string | null;
  lastRunTime?: string | null;
}

/** 一键体检回执(R3):监控执行受理。 */
export interface MdmLandingCheckReceipt {
  landingTable: string;
  monitorId: number;
  executionNo: string;
}

/** 血缘三段同步回执(R3)。 */
export interface MdmLineageSyncReceipt {
  assetCount: number;
  relationCount: number;
}

/** 主数据清洗(ticket 56)。 */

export type MdmMatchType = 'EXACT' | 'FUZZY';

export interface MdmMatchField {
  attrCode: string;
  matchType: MdmMatchType;
}

export interface MdmCleanRuleExpr {
  fields: MdmMatchField[];
  condition: 'AND' | 'OR';
}

/** 清洗规则类型：DEDUP 按字段匹配找重复；STANDARDIZE 按码值映射改值；COMPLETE 给空值填默认。 */
export type MdmCleanRuleType = 'DEDUP' | 'STANDARDIZE' | 'COMPLETE';

export interface MdmCleanRuleRecord {
  id: number;
  entityId: number;
  ruleType: MdmCleanRuleType;
  ruleName: string;
  /** 仅 DEDUP 规则有结构化匹配字段；STANDARDIZE/COMPLETE 后端返回 null。 */
  fields: MdmMatchField[] | null;
  /** 仅 DEDUP 规则有值。 */
  condition: 'AND' | 'OR' | null;
  /** 原始 rule_expr JSON，所有类型均有值。 */
  rawExpr?: string;
  enabled: boolean;
  sortOrder: number;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 通用清洗规则保存：三种类型都走 `/clean/rules/typed`，`ruleExpr` 是各类型自己的 JSON 串
 * （DEDUP=`{fields,condition}`、STANDARDIZE=`{fields:{属性:{原值:目标值}}}`、COMPLETE=`{defaults:{属性:默认值}}`）。
 * 类型在创建后不可改（后端按存量 ruleType 校验表达式），所以更新负载里没有 ruleType。
 */
export interface MdmTypedCleanRuleSavePayload {
  entityId: number;
  ruleType: MdmCleanRuleType;
  ruleName: string;
  ruleExpr: string;
  sortOrder?: number;
}

export interface MdmTypedCleanRuleUpdatePayload {
  ruleName: string;
  ruleExpr: string;
  sortOrder?: number;
}

/** 标准化/补全的单条变更明细（before/after 为整条属性快照）。 */
export interface MdmTransformChange {
  recordId: number;
  masterId: string;
  before: Record<string, unknown>;
  after: Record<string, unknown>;
}

export interface MdmTransformPreview {
  /** 受影响记录总数，始终按全量统计。 */
  affectedCount: number;
  /** true 表示 changes 只是截断样本（后端有上限），执行仍按全量影响。 */
  truncated: boolean;
  changes: MdmTransformChange[];
}

export interface MdmTransformApplyPayload {
  entityId: number;
  ruleId: number;
}

export interface MdmDedupGroup {
  records: MdmRecord[];
  matchBasis: string;
  confidence: number;
  total: number;
  /** SQL 聚合出的组键原文;忽略时必须原样回传,任何改写都会让忽略失效。 */
  matchKey: string;
}

/** 已忽略的重复组(R7):归属到具体去重规则的组键。 */
export interface MdmDedupIgnoreRecord {
  id: number;
  entityId: number;
  ruleId: number;
  matchKey: string;
  matchBasis?: string;
  reason?: string;
  createdBy?: string;
  createTime?: string;
}

export interface MdmDedupIgnorePayload {
  entityId: number;
  ruleId: number;
  matchKey: string;
  matchBasis?: string;
  reason?: string;
}

export interface MdmDedupRunPayload {
  entityId: number;
  ruleId: number;
  pageNo?: number;
  pageSize?: number;
}

export interface MdmMergeRecordView {
  masterId: string;
  status: string;
  version: number;
  attributes: Record<string, unknown>;
  sourceIds: Record<string, unknown>;
}

export interface MdmMergePreview {
  records: MdmMergeRecordView[];
  mergedAttributes: Record<string, unknown>;
  mergedSourceIds: Record<string, unknown>;
}

export interface MdmMergePreviewPayload {
  entityId: number;
  masterRecordId: number;
  mergedRecordIds: number[];
}

export interface MdmMergeExecutePayload extends MdmMergePreviewPayload {
  ruleId?: number;
}

export interface MdmMergeLogRecord {
  id: number;
  entityId: number;
  ruleId?: number;
  masterRecordId: number;
  mergedRecordIds: string;
  result: string;
  createdBy?: string;
  createTime?: string;
}

/** 变更申请单(R4,审批真相在 yak_mdm_change,推进由审批中心实例负责)。 */
export type MdmChangeType = 'CREATE' | 'UPDATE' | 'DELETE' | 'MERGE';

export type MdmApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface MdmChangeRecord {
  id: number;
  entityId: number;
  masterId: string;
  changeType: MdmChangeType;
  changeContent: string;
  approvalLevel: number;
  approvalStatus: MdmApprovalStatus;
  applicant?: string;
  approver?: string;
  approvalComment?: string;
  approvalTime?: string;
  /** 审批中心实例 ID;跳 /approval/instance/{id}。 */
  instanceId?: number;
  createTime?: string;
}

export interface MdmChangeSubmitPayload {
  entityId: number;
  masterId: string;
  /** 提单仅支持 UPDATE/DELETE(CREATE/MERGE 走清洗合并专用链路)。 */
  changeType: Extract<MdmChangeType, 'UPDATE' | 'DELETE'>;
  changeContent: string;
  approvalLevel?: number;
}

export interface MdmChangePageParams {
  pageNo: number;
  pageSize: number;
  entityId?: number;
  applicant?: string;
  status?: MdmApprovalStatus | '';
}

/** 记录版本快照(R4,全量 attributes,供 v(n-1)/v(n) diff)。 */
export interface MdmRecordVersionRecord {
  id: number;
  entityId: number;
  masterId: string;
  version: number;
  attributes: string;
  status: MdmRecordStatus;
  changeId?: number;
  operator?: string;
  createTime?: string;
}

/** 分发方式:API 复用数据服务对外供数;MESSAGE/FILE 通道尚未接入(R5)。 */
export type MdmDistributionMode = 'API' | 'MESSAGE' | 'FILE';

/** 分发频率:MANUAL 手动,DAILY/HOURLY 由 Yak Schedule 定时刷新发布态。 */
export type MdmDistributionFreq = 'MANUAL' | 'DAILY' | 'HOURLY';

export type MdmDistributionStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED';

export interface MdmDistributionRecord {
  id: number;
  entityId: number;
  targetSystem: string;
  targetName?: string;
  distributeMode: MdmDistributionMode;
  distributeFreq: MdmDistributionFreq;
  distributeScope?: string;
  status: MdmDistributionStatus;
  lastDistributeTime?: string | null;
  lastDistributeCount?: number;
  lastDistributeFail?: number;
  createTime?: string;
}

export interface MdmDistributionSavePayload {
  entityId: number;
  targetSystem: string;
  targetName?: string;
  distributeMode: MdmDistributionMode;
  distributeFreq?: MdmDistributionFreq;
  distributeScope?: string;
}

/** 更新不支持改目标系统编码(与后端 DistributionUpdateRequest 一致)。 */
export interface MdmDistributionUpdatePayload {
  targetName?: string;
  distributeMode: MdmDistributionMode;
  distributeFreq?: MdmDistributionFreq;
  distributeScope?: string;
}

/** 执行分发回执:API 模式=发布态已刷新,count 为当前可供数的 ACTIVE 记录数。 */
export interface MdmDistributionExecuteResult {
  id: number;
  mode: MdmDistributionMode;
  count: number;
  failCount: number;
  executeTime?: string;
  apiId?: number | null;
  apiPath?: string | null;
}

/** 分发 API 发布态(实时反查数据服务,MDM 不落库)。 */
export interface MdmDistributionPublication {
  available: boolean;
  published: boolean;
  apiId?: number | null;
  name?: string | null;
  path?: string | null;
  enabled?: boolean | null;
  authMode?: string | null;
}

/** 订阅记录(R6):notifyMode 一期只有 EVENT=站内信;reachable=false 表示订阅方编码解析不到平台用户。 */
export interface MdmSubscriptionRecord {
  id: number;
  entityId: number;
  subscriberCode: string;
  subscriberName?: string;
  notifyMode: string;
  status: MdmSubscriptionStatus;
  createdBy?: string | null;
  createTime?: string;
  reachable: boolean;
}

export type MdmSubscriptionStatus = 'ACTIVE' | 'DISABLED';

export interface MdmSubscriptionSavePayload {
  entityId: number;
  subscriberCode: string;
  subscriberName?: string;
  notifyMode?: string;
}

export interface MdmSubscriptionUpdatePayload {
  subscriberName?: string;
  notifyMode?: string;
}

/**
 * 总览六卡计数(R7)。
 *
 * 计数字段可能为 -1：后端把「查不到」与「真的是 0」区分开，-1 必须展示为「-」，
 * 否则会伪装成空数据（口径见 docs/home-overview-contract.md）。
 */
export interface MdmOverviewTotals {
  entities: number;
  activeRecords: number;
  pendingChanges: number;
  cleanRules: number;
  distributionTargets: number;
  subscribers: number;
}

export type MdmPipelineNodeKey = 'COLLECT' | 'PROCESSING' | 'CLEANSE' | 'APPROVE' | 'DISTRIBUTE';

/** 管线节点：count=事实数，attention=需要人工处理的红点数。 */
export interface MdmOverviewPipelineNode {
  key: MdmPipelineNodeKey;
  label: string;
  count: number;
  attention: number;
}

/** 实体卡片：点击进实体详情，也是「实体动线」的串联入口。 */
export interface MdmOverviewEntityCard {
  entityId: number;
  entityCode: string;
  entityName: string;
  activeRecords: number;
  distributionTargets: number;
  subscribers: number;
  pendingChanges: number;
}

export interface MdmOverviewData {
  totals: MdmOverviewTotals;
  pipeline: MdmOverviewPipelineNode[];
  entities: MdmOverviewEntityCard[];
}
