/** 数据安全模块类型：镜像后端 PO 与 API record。 */

export type DsecStatus = 'ACTIVE' | 'DISABLED' | 'DRAFT' | string;

export interface DsecPageInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface DsecPageResult<T> {
  bizData: T[];
  pagination: DsecPageInfo;
}

/** 安全等级字典。 */
export interface SecurityLevel {
  id: number;
  projectId?: number;
  levelCode: string;
  levelName: string;
  rankNo?: number;
  stdSecurityId?: number;
  description?: string;
  status?: DsecStatus;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SecurityLevelCreatePayload {
  code: string;
  name: string;
  rankNo?: number;
  stdSecurityId?: number;
  description?: string;
}

export interface SecurityLevelUpdatePayload {
  name: string;
  rankNo?: number;
  stdSecurityId?: number;
  description?: string;
}

/** 数据分类（树）。 */
export interface DataCategory {
  id: number;
  projectId?: number;
  categoryCode: string;
  categoryName: string;
  parentCode?: string;
  sortOrder?: number;
  description?: string;
  status?: DsecStatus;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataCategoryCreatePayload {
  code: string;
  name: string;
  parentCode?: string;
  sortOrder?: number;
  description?: string;
}

export interface DataCategoryUpdatePayload {
  name: string;
  sortOrder?: number;
  description?: string;
}

/** 资产分级标签。 */
export interface Classification {
  id: number;
  projectId?: number;
  objectType: string;
  objectKey?: string;
  datasourceId?: number;
  dbName?: string;
  tableName?: string;
  columnName?: string;
  objectName?: string;
  levelId?: number;
  categoryId?: number;
  source?: string;
  confidence?: number;
  discoveryRuleId?: number;
  status?: DsecStatus;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ClassificationUpsertPayload {
  objectType: string;
  datasourceId?: number;
  dbName?: string;
  tableName?: string;
  columnName?: string;
  levelId?: number;
  categoryId?: number;
  source?: string;
  confidence?: number;
  discoveryRuleId?: number;
  status?: string;
  objectName?: string;
}

/** 敏感发现规则。 */
export interface DiscoveryRule {
  id: number;
  projectId?: number;
  ruleCode: string;
  ruleName: string;
  matchType: string;
  pattern?: string;
  levelId?: number;
  categoryId?: number;
  enabled?: number;
  description?: string;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DiscoveryRuleCreatePayload {
  code: string;
  name: string;
  matchType: string;
  pattern?: string;
  levelId?: number;
  categoryId?: number;
  enabled?: boolean;
  description?: string;
}

export interface DiscoveryRuleUpdatePayload {
  name: string;
  matchType: string;
  pattern?: string;
  levelId?: number;
  categoryId?: number;
  enabled?: boolean;
  description?: string;
}

export interface DiscoverableField {
  datasourceId?: number;
  dbName?: string;
  tableName?: string;
  columnName?: string;
  comment?: string;
}

/** 访问策略。 */
export interface AccessPolicy {
  id: number;
  projectId?: number;
  policyName: string;
  subjectType?: string;
  subjectKey?: string;
  scopeType?: string;
  datasourceId?: number;
  dbName?: string;
  tableName?: string;
  columnName?: string;
  levelId?: number;
  accessType?: string;
  effect?: string;
  priority?: number;
  validFrom?: string;
  validTo?: string;
  status?: DsecStatus;
  applicant?: string;
  approver?: string;
  reason?: string;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

/** 访问裁决结果。decision ∈ ALLOW / DENY / NEED_APPROVAL。 */
export interface AccessDecision {
  allowed: boolean;
  decision: 'ALLOW' | 'DENY' | 'NEED_APPROVAL' | string;
  matchedPolicyId?: number;
  masked: boolean;
  algoCode?: string;
}

export interface AccessDecidePayload {
  actor: string;
  roles?: string[];
  objectKey: string;
  action: string;
}

/** 脱敏算法字典。 */
export interface MaskingAlgorithm {
  id: number;
  projectId?: number;
  algoCode: string;
  algoName: string;
  params?: string;
  builtin?: number;
  description?: string;
  status?: DsecStatus;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MaskingAlgorithmCreatePayload {
  code: string;
  name: string;
  params?: string;
  description?: string;
}

/** 脱敏策略。 */
export interface MaskingPolicy {
  id: number;
  projectId?: number;
  policyName: string;
  levelId?: number;
  categoryId?: number;
  columnPattern?: string;
  algoId?: number;
  priority?: number;
  enabled?: number;
  description?: string;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MaskingPolicyCreatePayload {
  name: string;
  levelId?: number;
  categoryId?: number;
  columnPattern?: string;
  algoId?: number;
  priority?: number;
  enabled?: boolean;
  description?: string;
}

export interface MaskingDirective {
  mask: boolean;
  algoCode?: string;
  algoParams?: string;
}

export interface MaskingPreviewPayload {
  value: string;
  algoCode: string;
  algoParams?: string;
}

/** 访问审计日志。 */
export interface AccessLog {
  id: number;
  projectId?: number;
  accessTime?: string;
  actor: string;
  resourceType?: string;
  resourceKey?: string;
  resourceName?: string;
  action?: string;
  levelCode?: string;
  decision?: string;
  masked?: number;
  algoCode?: string;
  source?: string;
  createTime?: string;
}

/** 合规规则。 */
export interface ComplianceRule {
  id: number;
  projectId?: number;
  ruleCode: string;
  ruleName: string;
  ruleType: string;
  params?: string;
  severity?: string;
  enabled?: number;
  description?: string;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ComplianceRuleCreatePayload {
  code: string;
  name: string;
  ruleType: string;
  params?: string;
  severity?: string;
  enabled?: boolean;
  description?: string;
}

/** 合规发现明细。 */
export interface ComplianceFinding {
  id: number;
  projectId?: number;
  batchId: string;
  ruleId?: number;
  ruleType?: string;
  targetKey?: string;
  targetName?: string;
  passed?: number;
  finding?: string;
  severity?: string;
  checkedTime?: string;
  createTime?: string;
}

export interface ComplianceRunResult {
  batchId: string;
  checked: number;
  passed: number;
  failed: number;
}

/** 总览。 */
export interface LevelCount {
  levelCode: string;
  levelName: string;
  rank?: number;
  count: number;
}

export interface SecurityOverview {
  levelCount: number;
  categoryCount: number;
  classifiedTotal: number;
  activeClassification: number;
  candidateClassification: number;
  levelDistribution: LevelCount[];
  enabledPolicyCount: number;
  maskingPolicyCount: number;
  accessDenyRecent: number;
  accessMaskedRecent: number;
  topActors: Record<string, unknown>[];
  complianceSummary: Record<string, unknown>;
}

/** 通用分页参数。 */
export interface DsecPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
}
