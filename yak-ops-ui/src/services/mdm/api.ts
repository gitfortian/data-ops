import HttpUtils from '@/utils/HttpUtils';

import type {
  MdmAttributeRecord,
  MdmAttributeSavePayload,
  MdmAttributeUpdatePayload,
  MdmChangePageParams,
  MdmChangeRecord,
  MdmChangeSubmitPayload,
  MdmCleanRuleRecord,
  MdmCleanRuleType,
  MdmCollectLinkRecord,
  MdmCollectRunReceipt,
  MdmCollectStatus,
  MdmDedupGroup,
  MdmDedupIgnorePayload,
  MdmDedupIgnoreRecord,
  MdmDedupRunPayload,
  MdmDistributionExecuteResult,
  MdmDistributionPublication,
  MdmDistributionRecord,
  MdmDistributionSavePayload,
  MdmDistributionStatus,
  MdmDistributionUpdatePayload,
  MdmEntityPageParams,
  MdmEntityRecord,
  MdmEntitySavePayload,
  MdmEntityStatus,
  MdmEntityUpdatePayload,
  MdmLandingCheckReceipt,
  MdmLandingQualityStatus,
  MdmLineageSyncReceipt,
  MdmMergeExecutePayload,
  MdmMergeLogRecord,
  MdmMergePreview,
  MdmMergePreviewPayload,
  MdmOverviewData,
  MdmPageResult,
  MdmProcessingTaskReceipt,
  MdmRecord,
  MdmRecordPageParams,
  MdmRecordVersionRecord,
  MdmSourceConfirmPayload,
  MdmSourceRecord,
  MdmSubscriptionRecord,
  MdmSubscriptionSavePayload,
  MdmSubscriptionStatus,
  MdmSubscriptionUpdatePayload,
  MdmTableCandidate,
  MdmTransformApplyPayload,
  MdmTransformPreview,
  MdmTypedCleanRuleSavePayload,
  MdmTypedCleanRuleUpdatePayload,
} from './types';

const MDM_API_PREFIX = '/api/v1/mdm';

export const pageMdmEntities = (params: MdmEntityPageParams): Promise<MdmPageResult<MdmEntityRecord>> =>
  HttpUtils.postData<MdmPageResult<MdmEntityRecord>>(`${MDM_API_PREFIX}/entities/page`, params);

export const getMdmEntity = (id: number): Promise<MdmEntityRecord> =>
  HttpUtils.getData<MdmEntityRecord>(`${MDM_API_PREFIX}/entities/${id}`);

export const createMdmEntity = (payload: MdmEntitySavePayload): Promise<MdmEntityRecord> =>
  HttpUtils.postData<MdmEntityRecord>(`${MDM_API_PREFIX}/entities`, payload);

export const updateMdmEntity = (
  id: number,
  payload: MdmEntityUpdatePayload,
): Promise<boolean> => HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/entities/${id}`, payload);

export const changeMdmEntityStatus = (
  id: number,
  status: MdmEntityStatus,
): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${MDM_API_PREFIX}/entities/${id}/status`, { status });

export const deleteMdmEntity = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/entities/${id}`);

export const listMdmAttributes = (entityId: number): Promise<MdmAttributeRecord[]> =>
  HttpUtils.getData<MdmAttributeRecord[]>(
    `${MDM_API_PREFIX}/entities/${entityId}/attributes`,
  );

export const createMdmAttribute = (
  entityId: number,
  payload: MdmAttributeSavePayload,
): Promise<MdmAttributeRecord> =>
  HttpUtils.postData<MdmAttributeRecord>(
    `${MDM_API_PREFIX}/entities/${entityId}/attributes`,
    payload,
  );

export const updateMdmAttribute = (
  entityId: number,
  id: number,
  payload: MdmAttributeUpdatePayload,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(
    `${MDM_API_PREFIX}/entities/${entityId}/attributes/${id}`,
    payload,
  );

export const deleteMdmAttribute = (entityId: number, id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(
    `${MDM_API_PREFIX}/entities/${entityId}/attributes/${id}`,
  );

export const scanMdmTables = (
  datasourceId: number,
  keyword?: string,
  limit?: number,
): Promise<MdmTableCandidate[]> =>
  HttpUtils.getData<MdmTableCandidate[]>(
    `${MDM_API_PREFIX}/identification/tables?datasourceId=${datasourceId}${
      keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''
    }${limit ? `&limit=${limit}` : ''}`,
  );

export const listMdmSources = (entityId?: number): Promise<MdmSourceRecord[]> =>
  HttpUtils.getData<MdmSourceRecord[]>(
    `${MDM_API_PREFIX}/sources${entityId ? `?entityId=${entityId}` : ''}`,
  );

export const confirmMdmSource = (payload: MdmSourceConfirmPayload): Promise<MdmSourceRecord> =>
  HttpUtils.postData<MdmSourceRecord>(`${MDM_API_PREFIX}/sources`, payload);

export const unbindMdmSource = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/sources/${id}`);

export const createMdmCollectLink = (payload: {
  sourceId: number;
  sinkDatasourceId: number;
}): Promise<MdmCollectLinkRecord> =>
  HttpUtils.postData<MdmCollectLinkRecord>(`${MDM_API_PREFIX}/collect/links`, payload);

export const listMdmCollectStatus = (entityId?: number): Promise<MdmCollectStatus[]> =>
  HttpUtils.getData<MdmCollectStatus[]>(
    `${MDM_API_PREFIX}/collect/links${entityId ? `?entityId=${entityId}` : ''}`,
  );

export const runMdmCollectLink = (sourceId: number): Promise<MdmCollectRunReceipt> =>
  HttpUtils.postData<MdmCollectRunReceipt>(`${MDM_API_PREFIX}/collect/links/${sourceId}/run`, {});

export const pageMdmRecords = (
  entityId: number,
  params: MdmRecordPageParams,
): Promise<MdmPageResult<MdmRecord>> =>
  HttpUtils.postData<MdmPageResult<MdmRecord>>(
    `${MDM_API_PREFIX}/entities/${entityId}/records/page`,
    params,
  );

export const generateMdmMasterSql = (entityId: number): Promise<string> =>
  HttpUtils.postData<string>(`${MDM_API_PREFIX}/entities/${entityId}/records/master-sql`, {});

/** 落地表质量状态：MDM 实时反查数据质量模块，不落库(R3)。 */
export const getMdmQualityStatus = (entityId: number): Promise<MdmLandingQualityStatus[]> =>
  HttpUtils.getData<MdmLandingQualityStatus[]>(
    `${MDM_API_PREFIX}/entities/${entityId}/quality/status`,
  );

/** 一键质量体检：注册资产 + 按模板建监控 + 触发执行，幂等可重复点击(R3)。 */
export const runMdmQualityCheck = (entityId: number): Promise<MdmLandingCheckReceipt[]> =>
  HttpUtils.postData<MdmLandingCheckReceipt[]>(
    `${MDM_API_PREFIX}/entities/${entityId}/quality/check`,
    {},
  );

/** 同步三段血缘：源表→落地表→yak_mdm_record，转调血缘模块(R3)。 */
export const syncMdmLineage = (entityId: number): Promise<MdmLineageSyncReceipt> =>
  HttpUtils.postData<MdmLineageSyncReceipt>(
    `${MDM_API_PREFIX}/entities/${entityId}/lineage/sync`,
    {},
  );

export const registerMdmProcessingTask = (entityId: number): Promise<MdmProcessingTaskReceipt> =>
  HttpUtils.postData<MdmProcessingTaskReceipt>(
    `${MDM_API_PREFIX}/entities/${entityId}/records/processing-task`,
    {},
  );

export const listMdmCleanRules = (
  entityId: number,
  ruleType?: MdmCleanRuleType,
): Promise<MdmCleanRuleRecord[]> =>
  HttpUtils.getData<MdmCleanRuleRecord[]>(
    `${MDM_API_PREFIX}/clean/rules?entityId=${entityId}${ruleType ? `&ruleType=${ruleType}` : ''}`,
  );

export const createMdmCleanRule = (
  payload: MdmTypedCleanRuleSavePayload,
): Promise<MdmCleanRuleRecord> =>
  HttpUtils.postData<MdmCleanRuleRecord>(`${MDM_API_PREFIX}/clean/rules/typed`, payload);

export const updateMdmCleanRule = (
  id: number,
  payload: MdmTypedCleanRuleUpdatePayload,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/clean/rules/${id}/typed`, payload);

export const setMdmCleanRuleEnabled = (id: number, enabled: boolean): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${MDM_API_PREFIX}/clean/rules/${id}/enabled`, { enabled });

export const deleteMdmCleanRule = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/clean/rules/${id}`);

/** 标准化/补全影响预览:总数全量、明细有界（后端截断前 MAX_PREVIEW_ITEMS 条）。 */
export const previewMdmTransform = (
  entityId: number,
  ruleId: number,
): Promise<MdmTransformPreview> =>
  HttpUtils.getData<MdmTransformPreview>(
    `${MDM_API_PREFIX}/clean/transform/${ruleId}/preview?entityId=${entityId}`,
  );

/** 执行标准化/补全:返回实际更新的记录数。 */
export const applyMdmTransform = (payload: MdmTransformApplyPayload): Promise<number> =>
  HttpUtils.postData<number>(`${MDM_API_PREFIX}/clean/transform/apply`, payload);

export const discoverMdmDuplicates = (
  payload: MdmDedupRunPayload,
): Promise<MdmPageResult<MdmDedupGroup>> =>
  HttpUtils.postData<MdmPageResult<MdmDedupGroup>>(`${MDM_API_PREFIX}/clean/dedup/discover`, payload);

export const ignoreMdmDedupGroup = (payload: MdmDedupIgnorePayload): Promise<MdmDedupIgnoreRecord> =>
  HttpUtils.postData<MdmDedupIgnoreRecord>(`${MDM_API_PREFIX}/clean/dedup/ignore`, payload);

export const listMdmDedupIgnores = (
  entityId: number,
  ruleId: number,
): Promise<MdmDedupIgnoreRecord[]> =>
  HttpUtils.getData<MdmDedupIgnoreRecord[]>(
    `${MDM_API_PREFIX}/clean/dedup/ignores?entityId=${entityId}&ruleId=${ruleId}`,
  );

export const unignoreMdmDedupGroup = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/clean/dedup/ignore/${id}`);

export const previewMdmMerge = (payload: MdmMergePreviewPayload): Promise<MdmMergePreview> =>
  HttpUtils.postData<MdmMergePreview>(`${MDM_API_PREFIX}/clean/merge/preview`, payload);

export const executeMdmMerge = (payload: MdmMergeExecutePayload): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${MDM_API_PREFIX}/clean/merge`, payload);

export const listMdmMergeLogs = (entityId: number): Promise<MdmMergeLogRecord[]> =>
  HttpUtils.getData<MdmMergeLogRecord[]>(`${MDM_API_PREFIX}/clean/merge-logs?entityId=${entityId}`);

// ==== 审批(R4,接审批中心:提单/撤回/查询在 MDM,通过与拒绝在中心待办) ====

export const submitMdmChange = (
  payload: MdmChangeSubmitPayload,
): Promise<MdmChangeRecord> =>
  HttpUtils.postData<MdmChangeRecord>(`${MDM_API_PREFIX}/approval/submit`, payload);

export const pageMdmChanges = (
  params: MdmChangePageParams,
): Promise<MdmPageResult<MdmChangeRecord>> => {
  const query = new URLSearchParams({
    pageNo: String(params.pageNo),
    pageSize: String(params.pageSize),
  });
  if (params.entityId != null) query.set('entityId', String(params.entityId));
  if (params.applicant) query.set('applicant', params.applicant);
  if (params.status) query.set('status', params.status);
  return HttpUtils.getData<MdmPageResult<MdmChangeRecord>>(
    `${MDM_API_PREFIX}/approval?${query.toString()}`,
  );
};

/** 撤回:转审批中心撤销(服务端取当前用户,仅发起人可撤)。 */
export const withdrawMdmChange = (id: number): Promise<unknown> =>
  HttpUtils.putData<unknown>(`${MDM_API_PREFIX}/approval/${id}/withdraw`, {});

/** 某记录的变更申请历史(含在途/终态)。 */
export const listMdmChangeHistory = (
  entityId: number,
  masterId: string,
): Promise<MdmChangeRecord[]> =>
  HttpUtils.getData<MdmChangeRecord[]>(
    `${MDM_API_PREFIX}/approval/entity/${entityId}/changes/${encodeURIComponent(masterId)}`,
  );

/** 某记录的版本快照序列(v(n-1)/v(n) diff 数据源)。 */
export const listMdmRecordVersions = (
  entityId: number,
  masterId: string,
): Promise<MdmRecordVersionRecord[]> =>
  HttpUtils.getData<MdmRecordVersionRecord[]>(
    `${MDM_API_PREFIX}/approval/entity/${entityId}/version/${encodeURIComponent(masterId)}`,
  );

/** 实体下的分发配置列表(R5)。 */
export const listMdmDistributions = (entityId: number): Promise<MdmDistributionRecord[]> =>
  HttpUtils.getData<MdmDistributionRecord[]>(`${MDM_API_PREFIX}/distribution/entity/${entityId}`);

export const createMdmDistribution = (
  payload: MdmDistributionSavePayload,
): Promise<MdmDistributionRecord> =>
  HttpUtils.postData<MdmDistributionRecord>(`${MDM_API_PREFIX}/distribution`, payload);

export const updateMdmDistribution = (
  id: number,
  payload: MdmDistributionUpdatePayload,
): Promise<boolean> => HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/distribution/${id}`, payload);

export const changeMdmDistributionStatus = (
  id: number,
  status: MdmDistributionStatus,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/distribution/${id}/status`, { status });

export const deleteMdmDistribution = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/distribution/${id}`);

/** 执行分发:API 模式=幂等推送至数据服务发布态并回写可供数条数;未接入通道明确失败。 */
export const executeMdmDistribution = (id: number): Promise<MdmDistributionExecuteResult> =>
  HttpUtils.postData<MdmDistributionExecuteResult>(
    `${MDM_API_PREFIX}/distribution/${id}/execute`,
    {},
  );

/** 分发 API 发布态:实时反查数据服务,决定「查看 API / 配 Key」入口可用性。 */
export const getMdmDistributionPublication = (
  id: number,
): Promise<MdmDistributionPublication> =>
  HttpUtils.getData<MdmDistributionPublication>(
    `${MDM_API_PREFIX}/distribution/${id}/publication`,
  );

/** 实体下的订阅列表(R6):含订阅方编码能否解析为平台用户的可达标记。 */
export const listMdmSubscriptions = (entityId: number): Promise<MdmSubscriptionRecord[]> =>
  HttpUtils.getData<MdmSubscriptionRecord[]>(`${MDM_API_PREFIX}/service/subscription/entity/${entityId}`);

export const createMdmSubscription = (
  payload: MdmSubscriptionSavePayload,
): Promise<MdmSubscriptionRecord> =>
  HttpUtils.postData<MdmSubscriptionRecord>(`${MDM_API_PREFIX}/service/subscription`, payload);

export const updateMdmSubscription = (
  id: number,
  payload: MdmSubscriptionUpdatePayload,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/service/subscription/${id}`, payload);

export const changeMdmSubscriptionStatus = (
  id: number,
  status: MdmSubscriptionStatus,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${MDM_API_PREFIX}/service/subscription/${id}/status`, { status });

export const deleteMdmSubscription = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MDM_API_PREFIX}/service/subscription/${id}`);

/**
 * 主数据总览(R7)：六卡 + 管线节点 + 最近实体卡片，一次请求拿全，服务端有界聚合。
 * limit 只约束实体卡片条数（后端上限 50）。
 */
export const getMdmOverview = (limit?: number): Promise<MdmOverviewData> =>
  HttpUtils.getData<MdmOverviewData>(
    `${MDM_API_PREFIX}/overview${limit ? `?limit=${limit}` : ''}`,
  );
