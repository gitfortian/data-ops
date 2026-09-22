import {
  getTaskCatalogAsset,
  type TaskCatalogAsset,
} from '@/services/taskCatalog';
import type { WorkflowCanvasTaskOption } from './types';

export const TASK_ASSET_ID_PREFIX = 'task-asset:';

const WORKFLOW_ELIGIBLE_DATA_DEVELOPMENT_TASK_TYPES = new Set([
  'SQL',
  'SHELL',
  'HTTP',
  'PYTHON',
]);

const TASK_ASSET_SOURCE_LABELS: Record<string, string> = {
  DATA_INTEGRATION: '数据同步',
  DATA_DEVELOPMENT: '数据开发',
  DATA_QUALITY: '数据质量',
};

const taskCatalogTypeLabel = (asset: TaskCatalogAsset) => {
  const source = (asset.source || '').trim().toUpperCase();
  return TASK_ASSET_SOURCE_LABELS[source] || asset.source || '任务';
};

export const isWorkflowEligibleTaskCatalogAsset = (asset: TaskCatalogAsset) => {
  const source = (asset.source || '').trim().toUpperCase();
  const taskType = (asset.taskType || '').trim().toUpperCase();
  if (source !== 'DATA_DEVELOPMENT') return true;
  return WORKFLOW_ELIGIBLE_DATA_DEVELOPMENT_TASK_TYPES.has(taskType);
};

export const taskCatalogOption = (asset: TaskCatalogAsset): WorkflowCanvasTaskOption => {
  if (!isWorkflowEligibleTaskCatalogAsset(asset)) {
    throw new Error(`数据开发资产 ${asset.taskType || '-'} 不能进入工作流编排`);
  }
  return {
    id: `${TASK_ASSET_ID_PREFIX}${asset.id}`,
    label: asset.name,
    typeLabel: taskCatalogTypeLabel(asset),
    taskType: asset.taskType,
    taskAssetId: asset.id,
    taskRevisionId: asset.currentRevision.taskRevisionId,
    taskRevisionNo: asset.currentRevision.revisionNo,
    meta: `已发布 v${asset.currentRevision.revisionNo}`,
  };
};

export const taskAssetIdFromTaskId = (taskId: string) =>
  taskId.startsWith(TASK_ASSET_ID_PREFIX)
    ? taskId.slice(TASK_ASSET_ID_PREFIX.length).trim()
    : undefined;

const hasPinnedCatalogBinding = (option: WorkflowCanvasTaskOption) => Boolean(
  option.taskAssetId
  && option.taskRevisionId
  && option.taskRevisionNo,
);

export const resolveWorkflowTaskOption = async (
  taskId: string,
  knownOptions: WorkflowCanvasTaskOption[],
): Promise<WorkflowCanvasTaskOption | undefined> => {
  const known = knownOptions.find((option) => option.id === taskId);
  if (known && hasPinnedCatalogBinding(known)) return known;

  const assetId = taskAssetIdFromTaskId(taskId);
  if (!assetId) return known;

  return taskCatalogOption(await getTaskCatalogAsset(assetId));
};
