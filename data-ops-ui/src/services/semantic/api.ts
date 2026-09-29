import HttpUtils from '@/utils/HttpUtils';

import type { ApprovalInstance } from '@/services/approval/types';

import type {
  CodeSetDetailRecord,
  CodeSetRecord,
  CodeValueItem,
  SemanticCodeSetOption,
  SemanticDomainNode,
  SemanticFieldPageParams,
  SemanticFieldRecord,
  SemanticLayerRecord,
  SemanticPageResult,
  SemanticProcessPageParams,
  SemanticProcessRecord,
  SemanticStandardId,
  SemanticStandardKind,
  SemanticStandardOption,
  SemanticStandardPageParams,
  SemanticStandardRecord,
  SemanticStandardStatus,
  SemanticStandardUsageSummary,
  SemanticStandardVersionRecord,
} from './types';

const SEMANTIC_API_PREFIX = '/api/v1/semantic';

/** 32 首期开放完整管理的类别(决策 C):命名+类型。 */
export const MANAGED_STANDARD_KINDS: readonly SemanticStandardRecord['kind'][] = ['NAMING', 'TYPE'];

export const pageSemanticStandards = (params: SemanticStandardPageParams): Promise<SemanticPageResult> =>
  HttpUtils.postData<SemanticPageResult>(`${SEMANTIC_API_PREFIX}/standards/page`, params);

export const getSemanticStandard = (id: SemanticStandardId): Promise<SemanticStandardRecord> =>
  HttpUtils.getData<SemanticStandardRecord>(`${SEMANTIC_API_PREFIX}/standards/${id}`);

export const getSemanticPresetStatus = (): Promise<{ initialized: boolean }> =>
  HttpUtils.getData<{ initialized: boolean }>(`${SEMANTIC_API_PREFIX}/standards/preset/status`);

export const initializeSemanticPresets = (): Promise<number> =>
  HttpUtils.postData<number>(`${SEMANTIC_API_PREFIX}/standards/preset/initialize`, {});

export interface SemanticStandardSavePayload {
  kind: SemanticStandardRecord['kind'];
  code: string;
  name: string;
  description?: string;
  sortOrder?: number;
  scope?: string;
  layer?: string;
  ruleExpr?: string;
  example?: string;
  typeCode?: string;
  stdType?: string;
  sourceMapping?: string;
  codeSetCode?: string;
  codeValue?: string;
  codeLabel?: string;
  unitCode?: string;
  unitType?: string;
  caliberCode?: string;
  calRule?: string;
  businessDesc?: string;
  levelCode?: string;
  maskRule?: string;
}

export const createSemanticStandard = (payload: SemanticStandardSavePayload): Promise<SemanticStandardRecord> =>
  HttpUtils.postData<SemanticStandardRecord>(`${SEMANTIC_API_PREFIX}/standards`, payload);

export const updateSemanticStandard = (
  id: SemanticStandardId,
  payload: Omit<SemanticStandardSavePayload, 'kind' | 'code'>,
): Promise<SemanticStandardRecord> =>
  HttpUtils.putData<SemanticStandardRecord>(`${SEMANTIC_API_PREFIX}/standards/${id}`, payload);

export const changeSemanticStandardStatus = (
  id: SemanticStandardId,
  status: SemanticStandardStatus,
): Promise<SemanticStandardRecord> =>
  HttpUtils.postData<SemanticStandardRecord>(`${SEMANTIC_API_PREFIX}/standards/${id}/status`, { status });

/** 提交标准生效审批(STANDARD_PUBLISH):批准后回调自动启用;流程未配置 49007、在途重复 49003 由后端拒绝。 */
export const submitStandardPublishApproval = (id: SemanticStandardId): Promise<ApprovalInstance> =>
  HttpUtils.postData<ApprovalInstance>(`${SEMANTIC_API_PREFIX}/standards/${id}/publish-approval`, {});

export const deleteSemanticStandard = (id: SemanticStandardId): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/standards/${id}`);

export const listSemanticStandardVersions = (id: SemanticStandardId): Promise<SemanticStandardVersionRecord[]> =>
  HttpUtils.getData<SemanticStandardVersionRecord[]>(`${SEMANTIC_API_PREFIX}/standards/${id}/versions`);

/** 业务域(ticket 33)。 */
export interface SemanticDomainSavePayload {
  code?: string;
  name: string;
  owner?: string;
  description?: string;
  sortOrder?: number;
}

export const getSemanticDomainTree = (): Promise<SemanticDomainNode[]> =>
  HttpUtils.getData<SemanticDomainNode[]>(`${SEMANTIC_API_PREFIX}/domains/tree`);

export const createSemanticDomain = (
  parentId: number | undefined,
  payload: SemanticDomainSavePayload,
): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${SEMANTIC_API_PREFIX}/domains`, {
    parentId: parentId && parentId > 0 ? parentId : undefined,
    ...payload,
  });

export const updateSemanticDomain = (id: number, payload: SemanticDomainSavePayload): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${SEMANTIC_API_PREFIX}/domains/${id}`, payload);

export const moveSemanticDomain = (id: number, parentId: number | undefined, sortOrder?: number): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${SEMANTIC_API_PREFIX}/domains/${id}/move`, {
    parentId: parentId && parentId > 0 ? parentId : undefined,
    sortOrder,
  });

export const deleteSemanticDomain = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/domains/${id}`);

/** 业务过程(ticket 34)。 */
export interface SemanticProcessSavePayload {
  code?: string;
  name: string;
  domainId: number;
  grain?: string;
  bizType: 'FACT' | 'DIMENSION';
  owner?: string;
  description?: string;
  sortOrder?: number;
}

export const pageSemanticProcesses = (
  params: SemanticProcessPageParams,
): Promise<SemanticPageResult<SemanticProcessRecord>> =>
  HttpUtils.postData<SemanticPageResult<SemanticProcessRecord>>(`${SEMANTIC_API_PREFIX}/processes/page`, params);

export const getSemanticProcess = (id: number): Promise<SemanticProcessRecord> =>
  HttpUtils.getData<SemanticProcessRecord>(`${SEMANTIC_API_PREFIX}/processes/${id}`);

export const createSemanticProcess = (payload: SemanticProcessSavePayload): Promise<SemanticProcessRecord> =>
  HttpUtils.postData<SemanticProcessRecord>(`${SEMANTIC_API_PREFIX}/processes`, payload);

export const updateSemanticProcess = (
  id: number,
  payload: Omit<SemanticProcessSavePayload, 'code'>,
): Promise<SemanticProcessRecord> =>
  HttpUtils.putData<SemanticProcessRecord>(`${SEMANTIC_API_PREFIX}/processes/${id}`, payload);

export const deleteSemanticProcess = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/processes/${id}`);

/** 标准字段(ticket 35)。 */
export interface SemanticFieldSavePayload {
  code?: string;
  name: string;
  role: SemanticFieldRecord['role'];
  /** 生效类型快照;引用类型标准时服务端以标准定义覆盖。 */
  dataType?: string;
  stdTypeId?: number;
  stdUnitId?: number;
  stdCaliberId?: number;
  stdCodeSetCode?: string;
  stdSecurityId?: number;
  businessDesc?: string;
}

/** 编辑标准字段:编码不可改;id/version 为乐观锁字段(约束 3)。 */
export type SemanticFieldUpdatePayload = Omit<SemanticFieldSavePayload, 'code'> & {
  id: number;
  version: number;
};

export const pageSemanticFields = (params: SemanticFieldPageParams): Promise<SemanticPageResult<SemanticFieldRecord>> =>
  HttpUtils.postData<SemanticPageResult<SemanticFieldRecord>>(`${SEMANTIC_API_PREFIX}/fields/page`, params);

export const createSemanticField = (payload: SemanticFieldSavePayload): Promise<SemanticFieldRecord> =>
  HttpUtils.postData<SemanticFieldRecord>(`${SEMANTIC_API_PREFIX}/fields`, payload);

export const updateSemanticField = (id: number, payload: SemanticFieldUpdatePayload): Promise<SemanticFieldRecord> =>
  HttpUtils.putData<SemanticFieldRecord>(`${SEMANTIC_API_PREFIX}/fields/${id}`, payload);

export const deleteSemanticField = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/fields/${id}`);

export const listSemanticProcessFields = (processId: number): Promise<SemanticFieldRecord[]> =>
  HttpUtils.getData<SemanticFieldRecord[]>(`${SEMANTIC_API_PREFIX}/processes/${processId}/fields`);

export const bindSemanticProcessField = (processId: number, fieldId: number, isRequired: boolean): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${SEMANTIC_API_PREFIX}/processes/${processId}/fields/${fieldId}`, { isRequired });

export const unbindSemanticProcessField = (processId: number, fieldId: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/processes/${processId}/fields/${fieldId}`);

/** 过程源表关联(ticket 36)。 */
export interface SemanticProcessSourceRecord {
  id: number;
  processId: number;
  datasourceId: number;
  sourceTable: string;
  tableRole: 'MAIN' | 'DETAIL' | 'DIM';
  joinCondition?: string;
}

export const listSemanticProcessSources = (processId: number): Promise<SemanticProcessSourceRecord[]> =>
  HttpUtils.getData<SemanticProcessSourceRecord[]>(`${SEMANTIC_API_PREFIX}/processes/${processId}/sources`);

export const bindSemanticProcessSource = (
  processId: number,
  payload: {
    datasourceId: number;
    sourceTable: string;
    tableRole: 'MAIN' | 'DETAIL' | 'DIM';
    joinCondition?: string;
  },
): Promise<SemanticProcessSourceRecord> =>
  HttpUtils.postData<SemanticProcessSourceRecord>(`${SEMANTIC_API_PREFIX}/processes/${processId}/sources`, payload);

export const unbindSemanticProcessSource = (processId: number, bindingId: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/processes/${processId}/sources/${bindingId}`);

/** 数仓分层(ticket 37;库名/数据源必填,2026-09-16)。 */
export interface SemanticLayerSavePayload {
  code?: string;
  name: string;
  databaseName: string;
  datasourceId: number;
  stdNamingId?: number;
  defaultPartition?: string;
  storageFormat?: string;
  lifecycleDays?: number;
  description?: string;
  sortOrder?: number;
  /** 定标闸门(M2-5);缺省=强制。 */
  stdMandatory?: boolean;
}

export const listSemanticLayers = (): Promise<SemanticLayerRecord[]> =>
  HttpUtils.getData<SemanticLayerRecord[]>(`${SEMANTIC_API_PREFIX}/layers`);

export const createSemanticLayer = (payload: SemanticLayerSavePayload): Promise<SemanticLayerRecord> =>
  HttpUtils.postData<SemanticLayerRecord>(`${SEMANTIC_API_PREFIX}/layers`, payload);

export const updateSemanticLayer = (
  id: number,
  payload: Omit<SemanticLayerSavePayload, 'code'>,
): Promise<SemanticLayerRecord> =>
  HttpUtils.putData<SemanticLayerRecord>(`${SEMANTIC_API_PREFIX}/layers/${id}`, payload);

export const changeSemanticLayerStatus = (id: number, status: 'ENABLED' | 'DISABLED'): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${SEMANTIC_API_PREFIX}/layers/${id}/status`, {
    status,
  });

export const deleteSemanticLayer = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/layers/${id}`);

export const initializeSemanticLayers = (): Promise<number> =>
  HttpUtils.postData<number>(`${SEMANTIC_API_PREFIX}/layers/initialize`, {});

export const getSemanticStandardUsage = (id: SemanticStandardId): Promise<SemanticStandardUsageSummary> =>
  HttpUtils.getData<SemanticStandardUsageSummary>(`${SEMANTIC_API_PREFIX}/standards/${id}/usage`);

/** 各业务过程引用字段数（ticket 34 列表页）。 */
export const listSemanticProcessFieldCounts = (): Promise<{ processId: number; fieldCount: number }[]> =>
  HttpUtils.getData<{ processId: number; fieldCount: number }[]>(`${SEMANTIC_API_PREFIX}/processes/field-counts`);

/** 码集(ticket 32.1 码值类聚合交互:一次保存"码集+多行码值")。 */

export interface CodeSetSavePayload {
  codeSetCode: string;
  /** 存量空码集行补全编码时传原组键(std_code),其余不传。 */
  originCodeSetCode?: string;
  name: string;
  description?: string;
  values: CodeValueItem[];
}

export const saveCodeSet = (payload: CodeSetSavePayload): Promise<SemanticStandardRecord[]> =>
  HttpUtils.postData<SemanticStandardRecord[]>(`${SEMANTIC_API_PREFIX}/standards/code-set`, payload);

export interface CodeSetPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: SemanticStandardStatus;
}

export const pageCodeSets = (params: CodeSetPageParams): Promise<SemanticPageResult<CodeSetRecord>> =>
  HttpUtils.postData<SemanticPageResult<CodeSetRecord>>(`${SEMANTIC_API_PREFIX}/standards/code-set/page`, params);

export const getCodeSet = (codeSetCode: string): Promise<CodeSetDetailRecord> =>
  HttpUtils.getData<CodeSetDetailRecord>(`${SEMANTIC_API_PREFIX}/standards/code-set/${codeSetCode}`);

export const existsCodeSet = (codeSetCode: string): Promise<boolean> =>
  HttpUtils.getData<boolean>(`${SEMANTIC_API_PREFIX}/standards/code-set/${codeSetCode}/exists`);

/** 启用码集选项(标准字段码值引用下拉,value = code_set_code)。 */
export const getCodeSetOptions = (): Promise<SemanticCodeSetOption[]> =>
  HttpUtils.getData<SemanticCodeSetOption[]>(`${SEMANTIC_API_PREFIX}/standards/code-set/options`);

/** 启用标准选项(类型/单位/口径/安全引用下拉,编辑弹窗打开时按 kinds 按需加载)。 */
export const getStandardOptions = (
  kinds: SemanticStandardKind[],
): Promise<Partial<Record<SemanticStandardKind, SemanticStandardOption[]>>> =>
  HttpUtils.getData<Partial<Record<SemanticStandardKind, SemanticStandardOption[]>>>(
    `${SEMANTIC_API_PREFIX}/standards/options?kinds=${kinds.join(',')}`,
  );

/** 码集级历史版本(合并组内各行修改前快照,按时间倒序)。 */
export const getCodeSetVersions = (codeSetCode: string): Promise<SemanticStandardVersionRecord[]> =>
  HttpUtils.getData<SemanticStandardVersionRecord[]>(
    `${SEMANTIC_API_PREFIX}/standards/code-set/${codeSetCode}/versions`,
  );

export const changeCodeSetStatus = (codeSetCode: string, status: SemanticStandardStatus): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${SEMANTIC_API_PREFIX}/standards/code-set/${codeSetCode}/status`, {
    status,
  });

export const deleteCodeSet = (codeSetCode: string): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${SEMANTIC_API_PREFIX}/standards/code-set/${codeSetCode}`);
