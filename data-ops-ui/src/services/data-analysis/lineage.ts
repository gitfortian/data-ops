import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  LineageAsset,
  LineageAssetType,
  LineageDirection,
  LineageGraph,
  LineageRelation,
} from './types';

const LINEAGE_API = '/api/v1/lineage';
type WireId = string | number;

interface LineageAssetWire extends Omit<LineageAsset, 'id' | 'parentAssetId'> {
  id: WireId;
  parentAssetId?: WireId | null;
}

interface LineageRelationWire extends Omit<LineageRelation, 'id' | 'sourceAssetId' | 'targetAssetId'> {
  id: WireId;
  sourceAssetId: WireId;
  targetAssetId: WireId;
}

interface LineageGraphWire {
  root: LineageAssetWire;
  direction: LineageDirection;
  depth: number;
  nodes?: LineageAssetWire[];
  relations?: LineageRelationWire[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const toAsset = (value: LineageAssetWire): LineageAsset => ({
  ...value,
  id: String(value.id),
  parentAssetId: value.parentAssetId == null ? undefined : String(value.parentAssetId),
});

const toRelation = (value: LineageRelationWire): LineageRelation => ({
  ...value,
  id: String(value.id),
  sourceAssetId: String(value.sourceAssetId),
  targetAssetId: String(value.targetAssetId),
});

const toGraph = (value: LineageGraphWire): LineageGraph => ({
  root: toAsset(value.root),
  direction: value.direction,
  depth: value.depth,
  nodes: (value.nodes || []).map(toAsset),
  relations: (value.relations || []).map(toRelation),
});

export interface SearchLineageAssetsParams {
  keyword?: string;
  assetType?: LineageAssetType;
  limit?: number;
}

export const searchLineageAssets = async ({
  keyword,
  assetType,
  limit = 30,
}: SearchLineageAssetsParams): Promise<LineageAsset[]> => {
  const query = new URLSearchParams();
  if (keyword?.trim()) query.set('keyword', keyword.trim());
  if (assetType) query.set('assetType', assetType);
  query.set('limit', String(limit));
  const response = await HttpUtils.get<LineageAssetWire[]>(
    `${LINEAGE_API}/assets?${query.toString()}`,
  );
  return unwrap(response, '搜索血缘资产失败').map(toAsset);
};

export const getLineageAssetByKey = async (assetKey: string): Promise<LineageAsset> => {
  const query = new URLSearchParams({ assetKey });
  const response = await HttpUtils.get<LineageAssetWire>(
    `${LINEAGE_API}/assets/by-key?${query.toString()}`,
  );
  return toAsset(unwrap(response, '查询血缘资产失败'));
};

export const getLineageGraph = async (
  assetId: string,
  depth = 3,
  direction: LineageDirection = 'BOTH',
): Promise<LineageGraph> => {
  const query = new URLSearchParams({ direction, depth: String(depth) });
  const response = await HttpUtils.get<LineageGraphWire>(
    `${LINEAGE_API}/assets/${encodeURIComponent(assetId)}/graph?${query.toString()}`,
  );
  return toGraph(unwrap(response, '查询血缘图失败'));
};
