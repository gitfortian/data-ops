import type { LineageGraph } from '@/services/data-analysis';
import type { ApprovalInstance } from '@/services/approval/types';
import HttpUtils from '@/utils/HttpUtils';

import type {
  ModelingCreatePayload,
  ModelingDdlRecord,
  ModelingDerivePreview,
  ModelingDirectoryRecord,
  ModelingMetricDraft,
  ModelingModelId,
  ModelingModelRecord,
  ModelingPageParams,
  ModelingPageResult,
  ModelingSaveStructurePayload,
  ModelingStructureRecord,
  ModelingTagRecord,
  ModelingTypeOption,
  ModelingUpdatePayload,
  ModelingValidationIssue,
  ModelingVersionDetail,
  ModelingVersionSummary,
} from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

export const pageModelingModels = (params: ModelingPageParams): Promise<ModelingPageResult> =>
  HttpUtils.postData<ModelingPageResult>(`${MODELING_API_PREFIX}/models/page`, params);

export const getModelingModel = (id: ModelingModelId): Promise<ModelingModelRecord> =>
  HttpUtils.getData<ModelingModelRecord>(`${MODELING_API_PREFIX}/models/${id}`);

export const getModelingStructure = (id: ModelingModelId): Promise<ModelingStructureRecord> =>
  HttpUtils.getData<ModelingStructureRecord>(`${MODELING_API_PREFIX}/models/${id}/structure`);

export const saveModelingStructure = (
  id: ModelingModelId,
  payload: ModelingSaveStructurePayload,
): Promise<ModelingStructureRecord> =>
  HttpUtils.putData<ModelingStructureRecord>(`${MODELING_API_PREFIX}/models/${id}/structure`, payload);

/** 类型目录同源于后端方言目录（ticket 07）。 */
export const getModelingTypeCatalog = (id: ModelingModelId): Promise<ModelingTypeOption[]> =>
  HttpUtils.getData<ModelingTypeOption[]>(`${MODELING_API_PREFIX}/models/${id}/structure/type-catalog`);

export const validateModelingStructure = (
  id: ModelingModelId,
  payload: ModelingSaveStructurePayload,
): Promise<ModelingValidationIssue[]> =>
  HttpUtils.postData<ModelingValidationIssue[]>(`${MODELING_API_PREFIX}/models/${id}/structure/validate`, payload);

/** 仅生成建库脚本，不在平台内执行（决策 D3）。 */
export const generateModelingDdl = (id: ModelingModelId): Promise<ModelingDdlRecord> =>
  HttpUtils.getData<ModelingDdlRecord>(`${MODELING_API_PREFIX}/models/${id}/ddl`);

export const createModelingModel = (payload: ModelingCreatePayload): Promise<ModelingModelRecord> =>
  HttpUtils.postData<ModelingModelRecord>(`${MODELING_API_PREFIX}/models`, payload);

export const updateModelingModel = (
  id: ModelingModelId,
  payload: ModelingUpdatePayload,
): Promise<ModelingModelRecord> =>
  HttpUtils.putData<ModelingModelRecord>(`${MODELING_API_PREFIX}/models/${id}`, payload);

export const deleteModelingModel = async (id: ModelingModelId): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${MODELING_API_PREFIX}/models/${id}`);
};

export const pageDeletedModelingModels = (params: ModelingPageParams): Promise<ModelingPageResult> =>
  HttpUtils.postData<ModelingPageResult>(`${MODELING_API_PREFIX}/models/deleted/page`, params);

export const restoreModelingModel = async (id: ModelingModelId): Promise<void> => {
  await HttpUtils.postData<boolean>(`${MODELING_API_PREFIX}/models/${id}/restore`, {});
};

export const purgeModelingModel = async (id: ModelingModelId): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${MODELING_API_PREFIX}/models/${id}/purge`);
};

/** directoryId 传 0 表示移出目录（未分类）。 */
export const assignModelingModelDirectory = async (id: ModelingModelId, directoryId: number): Promise<void> => {
  await HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/models/${id}/directory`, {
    directoryId,
  });
};

export const assignModelingModelTags = async (id: ModelingModelId, tagIds: number[]): Promise<void> => {
  await HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/models/${id}/tags`, {
    tagIds,
  });
};

export const listModelingDirectories = (): Promise<ModelingDirectoryRecord[]> =>
  HttpUtils.getData<ModelingDirectoryRecord[]>(`${MODELING_API_PREFIX}/directories`);

export const createModelingDirectory = (payload: {
  parentId?: number;
  name: string;
}): Promise<ModelingDirectoryRecord> =>
  HttpUtils.postData<ModelingDirectoryRecord>(`${MODELING_API_PREFIX}/directories`, payload);

export const renameModelingDirectory = async (id: number, name: string): Promise<void> => {
  await HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/directories/${id}/name`, {
    name,
  });
};

/** parentId 传 0 表示移动到根。 */
export const moveModelingDirectory = async (id: number, parentId: number): Promise<void> => {
  await HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/directories/${id}/parent`, {
    parentId,
  });
};

export const deleteModelingDirectory = async (id: number): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${MODELING_API_PREFIX}/directories/${id}`);
};

export const listModelingTags = (): Promise<ModelingTagRecord[]> =>
  HttpUtils.getData<ModelingTagRecord[]>(`${MODELING_API_PREFIX}/tags`);

export const createModelingTag = (name: string): Promise<ModelingTagRecord> =>
  HttpUtils.postData<ModelingTagRecord>(`${MODELING_API_PREFIX}/tags`, { name });

export const deleteModelingTag = async (id: number): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${MODELING_API_PREFIX}/tags/${id}`);
};

/** 血缘引导式登记(ticket 23,幂等)。 */
export const registerModelingLineage = (id: ModelingModelId): Promise<{ tableAssetId: number; columnCount: number }> =>
  HttpUtils.postData<{ tableAssetId: number; columnCount: number }>(
    `${MODELING_API_PREFIX}/models/${id}/lineage/register`,
    {},
  );

/** 血缘追溯:写入字段导入方式和来源模型。 */
export const assignImportLineage = async (
  id: ModelingModelId,
  importMode: string,
  sourceModelId?: number | null,
): Promise<void> => {
  await HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/models/${id}/import-lineage`, {
    importMode,
    sourceModelId: sourceModelId ?? null,
  });
};

/** 查询模型在血缘图谱中的资产ID。 */
export const getModelingLineageAssetId = (id: ModelingModelId): Promise<number | null> =>
  HttpUtils.getData<number | null>(`${MODELING_API_PREFIX}/models/${id}/lineage/asset-id`);

/** 查询模型血缘图(跨模块多跳遍历;未登记时后端自动登记)。root 为空表示暂无可展示资产。
 *  后端返回的 id 可能为数字，需转为字符串以兼容 LineageGraph 类型。 */
export const getModelingLineageGraph = async (
  id: ModelingModelId,
  direction: string = 'BOTH',
  depth: number = 3,
): Promise<LineageGraph | null> => {
  const wire = await HttpUtils.getData<Record<string, unknown>>(
    `${MODELING_API_PREFIX}/models/${id}/lineage/graph?direction=${direction}&depth=${depth}`,
  );
  if (!wire || wire.root == null) return null;
  const toStr = (v: unknown) => (v == null ? undefined : String(v));
  const convertAsset = (a: Record<string, unknown>) => ({
    ...a,
    id: toStr(a.id)!,
    parentAssetId: toStr(a.parentAssetId),
  });
  const convertRelation = (r: Record<string, unknown>) => ({
    ...r,
    id: toStr(r.id)!,
    sourceAssetId: toStr(r.sourceAssetId)!,
    targetAssetId: toStr(r.targetAssetId)!,
  });
  return {
    root: convertAsset(wire.root as Record<string, unknown>) as LineageGraph['root'],
    direction: wire.direction as LineageGraph['direction'],
    depth: wire.depth as number,
    nodes: ((wire.nodes as Record<string, unknown>[]) || []).map(convertAsset) as LineageGraph['nodes'],
    relations: ((wire.relations as Record<string, unknown>[]) || []).map(convertRelation) as LineageGraph['relations'],
  };
};

/** 派生预览(2026-09-17):源表就绪情况 + 字段继承清单 + 治理率;只读。 */
export const previewModelingDerive = (
  processId: number,
  layerCode: string,
  dialect?: string,
  scdType?: string,
  upstreamModelIds?: number[],
  statPeriod?: string,
  appCode?: string,
  appName?: string,
  upstreamLayer?: string,
): Promise<ModelingDerivePreview> => {
  const upstream = (upstreamModelIds ?? [])
    .map((id) => `&upstreamModelIds=${id}`)
    .join('');
  return HttpUtils.getData<ModelingDerivePreview>(
    `${MODELING_API_PREFIX}/derive/preview?processId=${processId}&layerCode=${encodeURIComponent(layerCode)}` +
      `${dialect ? `&dialect=${encodeURIComponent(dialect)}` : ''}` +
      `${scdType ? `&scdType=${encodeURIComponent(scdType)}` : ''}` +
      upstream +
      `${statPeriod ? `&statPeriod=${encodeURIComponent(statPeriod)}` : ''}` +
      `${appCode ? `&appCode=${encodeURIComponent(appCode)}` : ''}` +
      `${appName ? `&appName=${encodeURIComponent(appName)}` : ''}` +
      `${upstreamLayer ? `&upstreamLayer=${encodeURIComponent(upstreamLayer)}` : ''}`,
  );
};

/** 指标反推草稿(60/61):选指标 → 业务过程 + 上游模型 + 统计周期 + 度量/维度建议。 */
export const getModelingMetricDraft = (
  metricIds: number[],
  dialect?: string,
  sourceLayer?: string,
): Promise<ModelingMetricDraft> =>
  HttpUtils.getData<ModelingMetricDraft>(
    `${MODELING_API_PREFIX}/derive/metric-draft?metricIds=${metricIds.join(',')}` +
      `${dialect ? `&dialect=${encodeURIComponent(dialect)}` : ''}` +
      `${sourceLayer ? `&sourceLayer=${encodeURIComponent(sourceLayer)}` : ''}`,
  );

// ---- 版本管理 ----

/** 发布当前结构为新版本。 */
export const publishModelingModel = (id: ModelingModelId): Promise<ModelingVersionSummary> =>
  HttpUtils.postData<ModelingVersionSummary>(`${MODELING_API_PREFIX}/models/${id}/publish`, {});

/** 列出版本历史。 */
export const listModelingVersions = (id: ModelingModelId): Promise<ModelingVersionSummary[]> =>
  HttpUtils.getData<ModelingVersionSummary[]>(`${MODELING_API_PREFIX}/models/${id}/versions`);

/** 查看某版本结构详情。 */
export const getModelingVersion = (id: ModelingModelId, versionNo: number): Promise<ModelingVersionDetail> =>
  HttpUtils.getData<ModelingVersionDetail>(`${MODELING_API_PREFIX}/models/${id}/versions/${versionNo}`);

/** 回滚第一步：用指定版本覆盖当前草稿（置 DRAFT，不发布）。 */
export const rollbackModelingVersion = (id: ModelingModelId, versionNo: number): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${MODELING_API_PREFIX}/models/${id}/versions/${versionNo}/rollback`, {});

// ---- 发布审批(01) ----

/** 提交发布审批(MODEL_PUBLISH):批准后回调自动发布;流程未配置 49007、在途重复 49003 由后端拒绝。 */
export const submitModelingPublishApproval = (id: ModelingModelId): Promise<ApprovalInstance> =>
  HttpUtils.postData<ApprovalInstance>(`${MODELING_API_PREFIX}/models/${id}/publish-approval`, {});
