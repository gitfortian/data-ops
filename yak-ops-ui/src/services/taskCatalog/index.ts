import type { ApiResponse } from '@/services/http/response';
import { request } from '@umijs/max';

export interface TaskCatalogRevisionRef {
  taskAssetId: string;
  taskRevisionId: string;
  revisionNo: number;
}

export interface TaskCatalogAsset {
  id: string;
  source: string;
  sourceRef: string;
  projectId?: string;
  name: string;
  taskType: string;
  status: string;
  currentRevision: TaskCatalogRevisionRef;
}

const assetCache = new Map<string, TaskCatalogAsset>();
const remember = (asset: TaskCatalogAsset) => {
  assetCache.set(String(asset.id), asset);
  return asset;
};

export const getCachedTaskCatalogAsset = (assetId: string) => assetCache.get(String(assetId));

export const listTaskCatalogAssets = async (params?: {
  source?: string;
  status?: string;
  keyword?: string;
}) => {
  const response = await request<ApiResponse<TaskCatalogAsset[]>>('/api/v1/task-catalog/assets', {
    params,
  });
  return (response.data || []).map(remember);
};

export const getTaskCatalogAsset = async (assetId: string) => {
  const response = await request<ApiResponse<TaskCatalogAsset>>(
    `/api/v1/task-catalog/assets/${encodeURIComponent(assetId)}`,
  );
  return remember(response.data);
};
