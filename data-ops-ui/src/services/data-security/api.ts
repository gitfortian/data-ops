import HttpUtils from '@/utils/HttpUtils';

import type {
  AccessDecidePayload,
  AccessDecision,
  AccessLog,
  AccessPolicy,
  Classification,
  ClassificationUpsertPayload,
  ComplianceFinding,
  ComplianceRule,
  ComplianceRuleCreatePayload,
  ComplianceRunResult,
  DataCategory,
  DataCategoryCreatePayload,
  DataCategoryUpdatePayload,
  DiscoverableField,
  DiscoveryRule,
  DiscoveryRuleCreatePayload,
  DiscoveryRuleUpdatePayload,
  DsecPageParams,
  DsecPageResult,
  LevelCount,
  MaskingAlgorithm,
  MaskingAlgorithmCreatePayload,
  MaskingDirective,
  MaskingPolicy,
  MaskingPolicyCreatePayload,
  MaskingPreviewPayload,
  SecurityLevel,
  SecurityLevelCreatePayload,
  SecurityLevelUpdatePayload,
  SecurityOverview,
} from './types';

const P = '/api/v1/data-security';

const toPageParams = <T extends DsecPageParams>(params: T) => ({
  ...params,
  keyword: params.keyword || undefined,
});

/* ---------------- 安全等级 ---------------- */
export const pageSecurityLevels = (
  params: DsecPageParams & { status?: string },
): Promise<DsecPageResult<SecurityLevel>> =>
  HttpUtils.postData(`${P}/levels/page`, toPageParams(params));

export const listActiveSecurityLevels = (): Promise<SecurityLevel[]> =>
  HttpUtils.getData(`${P}/levels/active`);

export const getSecurityLevel = (id: number): Promise<SecurityLevel> =>
  HttpUtils.getData(`${P}/levels/${id}`);

export const createSecurityLevel = (payload: SecurityLevelCreatePayload): Promise<SecurityLevel> =>
  HttpUtils.postData(`${P}/levels`, payload);

export const updateSecurityLevel = (
  id: number,
  payload: SecurityLevelUpdatePayload,
): Promise<SecurityLevel> => HttpUtils.putData(`${P}/levels/${id}`, payload);

export const changeSecurityLevelStatus = (id: number, status: string): Promise<boolean> =>
  HttpUtils.postData(`${P}/levels/${id}/status`, { status });

export const deleteSecurityLevel = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/levels/${id}`);

/* ---------------- 数据分类 ---------------- */
export const pageDataCategories = (
  params: DsecPageParams,
): Promise<DsecPageResult<DataCategory>> =>
  HttpUtils.postData(`${P}/categories/page`, toPageParams(params));

export const listAllDataCategories = (): Promise<DataCategory[]> =>
  HttpUtils.getData(`${P}/categories/all`);

export const getDataCategory = (id: number): Promise<DataCategory> =>
  HttpUtils.getData(`${P}/categories/${id}`);

export const createDataCategory = (payload: DataCategoryCreatePayload): Promise<DataCategory> =>
  HttpUtils.postData(`${P}/categories`, payload);

export const updateDataCategory = (
  id: number,
  payload: DataCategoryUpdatePayload,
): Promise<DataCategory> => HttpUtils.putData(`${P}/categories/${id}`, payload);

export const deleteDataCategory = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/categories/${id}`);

/* ---------------- 资产分级标签 ---------------- */
export const pageClassifications = (
  params: DsecPageParams & { levelId?: number; categoryId?: number; status?: string },
): Promise<DsecPageResult<Classification>> =>
  HttpUtils.postData(`${P}/classifications/page`, toPageParams(params));

export const getClassification = (id: number): Promise<Classification> =>
  HttpUtils.getData(`${P}/classifications/${id}`);

export const upsertClassification = (
  payload: ClassificationUpsertPayload,
): Promise<Classification> => HttpUtils.postData(`${P}/classifications`, payload);

export const changeClassificationStatus = (id: number, status: string): Promise<Classification> =>
  HttpUtils.postData(`${P}/classifications/${id}/status`, { status });

export const deleteClassification = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/classifications/${id}`);

/* ---------------- 敏感发现规则 ---------------- */
export const pageDiscoveryRules = (
  params: DsecPageParams,
): Promise<DsecPageResult<DiscoveryRule>> =>
  HttpUtils.postData(`${P}/discovery-rules/page`, toPageParams(params));

export const getDiscoveryRule = (id: number): Promise<DiscoveryRule> =>
  HttpUtils.getData(`${P}/discovery-rules/${id}`);

export const createDiscoveryRule = (
  payload: DiscoveryRuleCreatePayload,
): Promise<DiscoveryRule> => HttpUtils.postData(`${P}/discovery-rules`, payload);

export const updateDiscoveryRule = (
  id: number,
  payload: DiscoveryRuleUpdatePayload,
): Promise<DiscoveryRule> => HttpUtils.putData(`${P}/discovery-rules/${id}`, payload);

export const deleteDiscoveryRule = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/discovery-rules/${id}`);

export const scanDiscoveryFields = (fields: DiscoverableField[]): Promise<number> =>
  HttpUtils.postData(`${P}/discovery-rules/scan`, { fields });

/* ---------------- 访问策略 ---------------- */
export const pageAccessPolicies = (
  params: DsecPageParams & { status?: string },
): Promise<DsecPageResult<AccessPolicy>> =>
  HttpUtils.postData(`${P}/access-policies/page`, toPageParams(params));

export const getAccessPolicy = (id: number): Promise<AccessPolicy> =>
  HttpUtils.getData(`${P}/access-policies/${id}`);

export const createAccessPolicy = (payload: Partial<AccessPolicy>): Promise<AccessPolicy> =>
  HttpUtils.postData(`${P}/access-policies`, payload);

export const updateAccessPolicy = (
  id: number,
  payload: Partial<AccessPolicy>,
): Promise<AccessPolicy> => HttpUtils.putData(`${P}/access-policies/${id}`, payload);

export const submitAccessPolicyApproval = (id: number): Promise<unknown> =>
  HttpUtils.postData(`${P}/access-policies/${id}/apply-approval`, {});

export const disableAccessPolicy = (id: number): Promise<boolean> =>
  HttpUtils.postData(`${P}/access-policies/${id}/disable`, {});

export const deleteAccessPolicy = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/access-policies/${id}`);

/* ---------------- 访问裁决 ---------------- */
export const decideAccess = (payload: AccessDecidePayload): Promise<AccessDecision> =>
  HttpUtils.postData(`${P}/access/decide`, payload);

/* ---------------- 脱敏 ---------------- */
export const listSupportedAlgorithms = (): Promise<string[]> =>
  HttpUtils.getData(`${P}/masking/algorithms/supported`);

export const listMaskingAlgorithms = (): Promise<MaskingAlgorithm[]> =>
  HttpUtils.getData(`${P}/masking/algorithms`);

export const getMaskingAlgorithm = (id: number): Promise<MaskingAlgorithm> =>
  HttpUtils.getData(`${P}/masking/algorithms/${id}`);

export const createMaskingAlgorithm = (
  payload: MaskingAlgorithmCreatePayload,
): Promise<MaskingAlgorithm> => HttpUtils.postData(`${P}/masking/algorithms`, payload);

export const deleteMaskingAlgorithm = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/masking/algorithms/${id}`);

export const pageMaskingPolicies = (
  params: DsecPageParams,
): Promise<DsecPageResult<MaskingPolicy>> =>
  HttpUtils.postData(`${P}/masking/policies/page`, toPageParams(params));

export const getMaskingPolicy = (id: number): Promise<MaskingPolicy> =>
  HttpUtils.getData(`${P}/masking/policies/${id}`);

export const createMaskingPolicy = (
  payload: MaskingPolicyCreatePayload,
): Promise<MaskingPolicy> => HttpUtils.postData(`${P}/masking/policies`, payload);

export const deleteMaskingPolicy = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/masking/policies/${id}`);

export const resolveMasking = (objectKey: string): Promise<MaskingDirective> =>
  HttpUtils.getData(`${P}/masking/resolve?objectKey=${encodeURIComponent(objectKey)}`);

export const previewMasking = (payload: MaskingPreviewPayload): Promise<string> =>
  HttpUtils.postData(`${P}/masking/preview`, payload);

/* ---------------- 访问审计 ---------------- */
export const pageAccessLogs = (
  params: {
    pageNo: number;
    pageSize: number;
    actor?: string;
    decision?: string;
    resourceKey?: string;
    start?: string;
    end?: string;
  },
): Promise<DsecPageResult<AccessLog>> =>
  HttpUtils.postData(`${P}/access-logs/page`, {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    actor: params.actor || undefined,
    decision: params.decision || undefined,
    resourceKey: params.resourceKey || undefined,
    start: params.start || undefined,
    end: params.end || undefined,
  });

export const topAccessActors = (): Promise<Record<string, unknown>[]> =>
  HttpUtils.postData(`${P}/access-logs/top-actors`, {});

/* ---------------- 合规 ---------------- */
export const pageComplianceRules = (
  params: DsecPageParams,
): Promise<DsecPageResult<ComplianceRule>> =>
  HttpUtils.postData(`${P}/compliance/rules/page`, toPageParams(params));

export const getComplianceRule = (id: number): Promise<ComplianceRule> =>
  HttpUtils.getData(`${P}/compliance/rules/${id}`);

export const createComplianceRule = (
  payload: ComplianceRuleCreatePayload,
): Promise<ComplianceRule> => HttpUtils.postData(`${P}/compliance/rules`, payload);

export const deleteComplianceRule = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/compliance/rules/${id}`);

export const runComplianceRule = (id: number): Promise<ComplianceRunResult> =>
  HttpUtils.postData(`${P}/compliance/rules/${id}/run`, {});

export const pageComplianceFindings = (
  params: { pageNo: number; pageSize: number; batchId?: string; passed?: boolean },
): Promise<DsecPageResult<ComplianceFinding>> =>
  HttpUtils.postData(`${P}/compliance/findings/page`, {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    batchId: params.batchId || undefined,
    passed: params.passed,
  });

export const complianceSummary = (): Promise<Record<string, unknown>> =>
  HttpUtils.getData(`${P}/compliance/summary`);

/* ---------------- 总览 ---------------- */
export const getSecurityOverview = (): Promise<SecurityOverview> =>
  HttpUtils.getData(`${P}/overview`);

export type { LevelCount };
