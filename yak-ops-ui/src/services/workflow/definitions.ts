import {
  getCachedTaskCatalogAsset,
  getTaskCatalogAsset,
} from '@/services/taskCatalog';
import HttpUtils from '@/utils/HttpUtils';
import type {
  WorkflowFailureStrategy,
  WorkflowNodeFailurePolicy,
  WorkflowTriggerRule,
} from './instances';

/** PUBLISHED 为收敛后发布态（契约 C1）；ONLINE 仅作为历史值兼容比较。 */
export type WorkflowDefinitionStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'ONLINE';

export const isWorkflowPublished = (status?: string) =>
  status === 'PUBLISHED' || status === 'ONLINE';

export interface WorkflowDefinitionNode {
  id: string;
  taskId: string;
  taskAssetId?: string;
  taskRevisionId?: string;
  taskRevisionNo?: number;
  taskAssetName?: string;
  taskType?: string;
  taskAssetStatus?: string;
  latestTaskRevisionId?: string;
  latestTaskRevisionNo?: number;
  taskRevisionUpdateAvailable?: boolean;
  positionX: number;
  positionY: number;
  maxAttempts: number;
  retryDelaySeconds: number;
  dispatchTimeoutSeconds: number;
  executionTimeoutSeconds: number;
  inputMapping: Record<string, string>;
  triggerRule: WorkflowTriggerRule;
  failurePolicy: WorkflowNodeFailurePolicy;
}

export interface WorkflowDefinitionEdge {
  source: string;
  target: string;
}

export interface WorkflowDefinition {
  id: string;
  name: string;
  description?: string;
  status: WorkflowDefinitionStatus;
  nodeCount: number;
  edgeCount: number;
  nodes: WorkflowDefinitionNode[];
  edges: WorkflowDefinitionEdge[];
  input: Record<string, unknown>;
  editorMeta: Record<string, unknown>;
  workflowTimeoutSeconds: number;
  failureStrategy: WorkflowFailureStrategy;
  activeVersionId?: string;
  activeVersionNo?: number;
  latestVersionNo: number;
  draftChanged: boolean;
  latestExecutionId?: string;
  latestExecutionStatus?: string;
  createTime: string;
  updateTime: string;
}

export interface WorkflowTaskVersionBinding {
  nodeId: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
}

export interface WorkflowVersionSummary {
  id: string;
  versionNo: number;
  active: boolean;
  nodeCount: number;
  edgeCount: number;
  taskBindings: WorkflowTaskVersionBinding[];
  publishedAt: string;
}

export interface WorkflowDefinitionCreatePayload {
  name: string;
  description?: string;
}

export interface WorkflowDefinitionUpdatePayload {
  name: string;
  description?: string;
  nodes: WorkflowDefinitionNode[];
  edges: WorkflowDefinitionEdge[];
  input: Record<string, unknown>;
  editorMeta: Record<string, unknown>;
  workflowTimeoutSeconds: number;
  failureStrategy: WorkflowFailureStrategy;
}

interface PinnedBinding {
  taskAssetId: string;
  taskRevisionId: string;
  taskRevisionNo: number;
}

const bindingCache = new Map<string, PinnedBinding>();
const bindingKey = (workflowId: string, nodeId: string) => `${workflowId}::${nodeId}`;
const taskAssetIdFromTaskId = (taskId: string) =>
  taskId.startsWith('task-asset:') ? taskId.slice('task-asset:'.length).trim() : undefined;

const rememberDefinitionBindings = (definition: WorkflowDefinition) => {
  definition.nodes.forEach((node) => {
    if (!node.taskAssetId || !node.taskRevisionId || !node.taskRevisionNo) return;
    bindingCache.set(bindingKey(definition.id, node.id), {
      taskAssetId: node.taskAssetId,
      taskRevisionId: node.taskRevisionId,
      taskRevisionNo: node.taskRevisionNo,
    });
  });
  return definition;
};

const pinCatalogNode = async (
  workflowId: string,
  node: WorkflowDefinitionNode,
): Promise<WorkflowDefinitionNode> => {
  if (node.taskAssetId && node.taskRevisionId && node.taskRevisionNo) return node;
  const assetId = taskAssetIdFromTaskId(node.taskId);
  if (!assetId) return node;

  const pinned = bindingCache.get(bindingKey(workflowId, node.id));
  if (pinned && pinned.taskAssetId === assetId) {
    return { ...node, ...pinned, taskId: `task-asset:${assetId}` };
  }

  const asset = getCachedTaskCatalogAsset(assetId) || await getTaskCatalogAsset(assetId);
  return {
    ...node,
    taskId: `task-asset:${asset.id}`,
    taskAssetId: asset.id,
    taskRevisionId: asset.currentRevision.taskRevisionId,
    taskRevisionNo: asset.currentRevision.revisionNo,
  };
};

const definitionAction = async (id: string, action: string) => {
  const definition = await HttpUtils.postData<WorkflowDefinition>(
    `/api/v1/workflows/definitions/${encodeURIComponent(id)}/${action}`,
  );
  return rememberDefinitionBindings(definition);
};

export const listWorkflowDefinitions = async (params?: {
  keyword?: string;
  status?: WorkflowDefinitionStatus;
}) => {
  const query = new URLSearchParams();
  if (params?.keyword) query.set('keyword', params.keyword);
  if (params?.status) query.set('status', params.status);
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return (
    (await HttpUtils.getData<WorkflowDefinition[]>(
      `/api/v1/workflows/definitions${suffix}`,
    )) || []
  );
};

export const createWorkflowDefinition = async (payload: WorkflowDefinitionCreatePayload) =>
  rememberDefinitionBindings(
    await HttpUtils.postData<WorkflowDefinition>('/api/v1/workflows/definitions', payload),
  );

export const getWorkflowDefinition = async (id: string) =>
  rememberDefinitionBindings(
    await HttpUtils.getData<WorkflowDefinition>(
      `/api/v1/workflows/definitions/${encodeURIComponent(id)}`,
    ),
  );

export const updateWorkflowDefinition = async (
  id: string,
  payload: WorkflowDefinitionUpdatePayload,
) => {
  const nodes = await Promise.all(payload.nodes.map((node) => pinCatalogNode(id, node)));
  return rememberDefinitionBindings(
    await HttpUtils.putData<WorkflowDefinition>(
      `/api/v1/workflows/definitions/${encodeURIComponent(id)}`,
      { ...payload, nodes },
    ),
  );
};

export const upgradeWorkflowNodeTaskRevision = async (id: string, nodeId: string) =>
  rememberDefinitionBindings(
    await HttpUtils.postData<WorkflowDefinition>(
      `/api/v1/workflows/definitions/${encodeURIComponent(id)}/nodes/${encodeURIComponent(nodeId)}/upgrade-task-revision`,
    ),
  );

export const deleteWorkflowDefinition = async (id: string) => {
  await HttpUtils.deleteData<boolean>(
    `/api/v1/workflows/definitions/${encodeURIComponent(id)}`,
  );
  for (const key of Array.from(bindingCache.keys())) {
    if (key.startsWith(`${id}::`)) bindingCache.delete(key);
  }
};

export const onlineWorkflowDefinition = (id: string) => definitionAction(id, 'online');
export const offlineWorkflowDefinition = (id: string) => definitionAction(id, 'offline');
export const runWorkflowDefinition = (id: string) => definitionAction(id, 'run');
export const testRunWorkflowDefinition = (id: string) => definitionAction(id, 'test-run');
export const pauseWorkflowDefinition = (id: string) => definitionAction(id, 'pause');
export const resumeWorkflowDefinition = (id: string) => definitionAction(id, 'resume');

export const listWorkflowVersions = async (id: string) =>
  (await HttpUtils.getData<WorkflowVersionSummary[]>(
    `/api/v1/workflows/definitions/${encodeURIComponent(id)}/versions`,
  )) || [];

/** 单版本详情（diff 数据源）：摘要 + 运行规格 + 画布元数据。 */
export interface WorkflowVersionDetail {
  summary: WorkflowVersionSummary;
  runSpec?: Record<string, unknown>;
  editorMeta?: Record<string, unknown>;
}

export const getWorkflowVersion = (id: string, versionNo: number) =>
  HttpUtils.getData<WorkflowVersionDetail>(
    `/api/v1/workflows/definitions/${encodeURIComponent(id)}/versions/${versionNo}`,
  );

/** 回滚=追加式：以 v{no} 内容生成新版本并置为已发布启用。 */
export const rollbackWorkflowVersion = async (id: string, versionNo: number) =>
  rememberDefinitionBindings(
    await HttpUtils.postData<WorkflowDefinition>(
      `/api/v1/workflows/definitions/${encodeURIComponent(id)}/versions/${versionNo}/rollback`,
    ),
  );
