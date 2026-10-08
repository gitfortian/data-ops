import type { ProductSearchState, ProductType } from '@/services/consumption';

export const PRODUCT_TYPE_LABEL: Record<string, string> = { DATASET: '数据集', DATA_SERVICE: 'API 服务' };
export const LIFECYCLE_LABEL: Record<string, string> = {
  NOT_PUBLISHED: '未发布', PUBLISHED: '已发布', DEPRECATED: '已弃用', RETIRED: '已退役',
};
export const AVAILABILITY_LABEL: Record<string, string> = {
  AVAILABLE: '可用', UNAVAILABLE: '不可用', UNKNOWN: '尚未确认可用性',
};
export const EVIDENCE_LABEL: Record<string, string> = {
  READY: '已读取', EMPTY: '暂无证据', FORBIDDEN: '无权查看', UNAVAILABLE: '暂不可用', NOT_APPLICABLE: '不适用',
};

// An empty result is trustworthy only when all selected discovery providers responded READY.
// Missing provider states are unknown, not proof that there are no products.
export const resolveConsumptionDiscoveryView = (
  loading: boolean,
  error: string,
  productCount: number,
  providerStates: Partial<Record<ProductType, ProductSearchState>>,
  selectedType?: ProductType,
): 'LOADING' | 'ERROR' | 'RESULTS' | 'INCOMPLETE' | 'EMPTY' => {
  if (loading) return 'LOADING';
  if (error) return 'ERROR';
  if (productCount > 0) return 'RESULTS';
  const types = selectedType ? [selectedType] : ['DATASET', 'DATA_SERVICE'] as const;
  return types.every((type) => providerStates[type] === 'READY') ? 'EMPTY' : 'INCOMPLETE';
};
