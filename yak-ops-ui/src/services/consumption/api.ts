import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  ProductDiscoveryQuery,
  ProductDiscoveryResult,
  ProductLookupResult,
} from './types';

const PRODUCT_API = '/api/v1/consumption/products';

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const queryString = (query: ProductDiscoveryQuery = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value != null && String(value).trim()) params.set(key, String(value).trim());
  });
  const text = params.toString();
  return text ? `?${text}` : '';
};

export const discoverProducts = async (
  query: ProductDiscoveryQuery = {},
): Promise<ProductDiscoveryResult> => unwrap(
  await HttpUtils.get<ProductDiscoveryResult>(`${PRODUCT_API}${queryString(query)}`),
  '加载数据消费目录失败',
);

export const getProduct = async (productKey: string): Promise<ProductLookupResult> => unwrap(
  await HttpUtils.get<ProductLookupResult>(`${PRODUCT_API}/${encodeURIComponent(productKey)}`),
  '加载数据产品详情失败',
);
