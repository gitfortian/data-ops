import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

export type DatasetAssetGovernanceState =
  | 'NOT_CREATED'
  | 'FOUND'
  | 'NOT_INDEXED'
  | 'PERMISSION_DENIED'
  | 'UNAVAILABLE';

export interface AssetSourceLookup {
  state: 'FOUND' | 'NOT_INDEXED';
  sourceType: string;
  sourceId: string;
  assetId?: number | null;
  assetKey?: string | null;
  assetType?: string | null;
  assetStatus?: string | null;
}

export interface DatasetDevelopmentSource {
  datasetId: DevelopmentId;
  developmentNodeId?: DevelopmentId | null;
  state: 'FOUND' | 'NOT_APPLICABLE';
}

export interface DatasetAssetGovernanceView {
  state: DatasetAssetGovernanceState;
  datasetId?: DevelopmentId;
  asset?: AssetSourceLookup;
  reason?: string;
}

export const lookupDatasetAsset = (datasetId: DevelopmentId): Promise<AssetSourceLookup> =>
  HttpUtils.getData<AssetSourceLookup>('/api/v1/assets/source-lookup', {
    params: { sourceType: 'DATASET', sourceId: String(datasetId) },
  });

export const resolveDatasetDevelopmentSource = (
  datasetId: DevelopmentId,
): Promise<DatasetDevelopmentSource> =>
  HttpUtils.getData<DatasetDevelopmentSource>(
    `/api/v1/datasets/${encodeURIComponent(datasetId)}/development-source`,
  );

export const datasetIdFromSearch = (search: string) => {
  const value = new URLSearchParams(search).get('datasetId');
  return value?.trim() || undefined;
};

export const datasetAssetDetailUrl = (asset?: AssetSourceLookup) =>
  asset?.state === 'FOUND' && asset.assetId
    ? `/data-asset/detail/${asset.assetId}`
    : undefined;

export const datasetAssetGovernanceLabel = (state: DatasetAssetGovernanceState) => ({
  NOT_CREATED: 'Asset · 等待创建 Dataset',
  FOUND: 'Asset · 已进入 Governance',
  NOT_INDEXED: 'Asset · 等待 Registry 对账',
  PERMISSION_DENIED: 'Asset · 无治理读取权限',
  UNAVAILABLE: 'Asset · Governance 不可用',
}[state]);
