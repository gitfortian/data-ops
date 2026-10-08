import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  NavigationResolution,
  ProductDiscoveryQuery,
  ProductDiscoveryResult,
  ProductLookupResult,
  ConsumerRef,
  ConsumerImpact,
  Subscription,
  ConsumptionMode,
  ProductType,
} from './types';

const CONSUMPTION_API = '/api/v1/consumption';
const PRODUCT_API = `${CONSUMPTION_API}/products`;

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const queryString = (query: object = {}) => {
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

export const getConsumerImpact = async (productKey: string): Promise<ConsumerImpact> => unwrap(
  await HttpUtils.get<ConsumerImpact>(`${CONSUMPTION_API}/impact${queryString({ productKey, usageLimit: 200 })}`),
  '加载消费影响失败',
);

export const listSubscriptions = async (productKey: string): Promise<Subscription[]> => unwrap(
  await HttpUtils.get<Subscription[]>(`${CONSUMPTION_API}/subscriptions${queryString({ productKey })}`),
  '加载消费订阅失败',
);

export const subscribeToProduct = async (
  productKey: string,
  consumerRef: ConsumerRef,
  consumptionMode: ConsumptionMode,
): Promise<Subscription> => unwrap(
  await HttpUtils.post<Subscription>(`${CONSUMPTION_API}/subscriptions`, {
    productKey,
    consumerType: consumerRef.consumerType,
    sourceDomain: consumerRef.sourceDomain,
    sourceIdentity: consumerRef.sourceIdentity,
    displayHint: consumerRef.displayHint,
    consumptionMode,
  }),
  '声明消费依赖失败',
);

export const cancelSubscription = async (subscriptionId: number): Promise<Subscription> => unwrap(
  await HttpUtils.post<Subscription>(`${CONSUMPTION_API}/subscriptions/${subscriptionId}/cancel`),
  '取消消费订阅失败',
);

export const suspendSubscription = async (subscriptionId: number): Promise<Subscription> => unwrap(
  await HttpUtils.post<Subscription>(`${CONSUMPTION_API}/subscriptions/${subscriptionId}/suspend`),
  '暂停消费依赖失败',
);

export const resumeSubscription = async (subscriptionId: number): Promise<Subscription> => unwrap(
  await HttpUtils.post<Subscription>(`${CONSUMPTION_API}/subscriptions/${subscriptionId}/resume`),
  '恢复消费依赖失败',
);

export const resolveProductFromSource = async (
  productType: ProductType,
  sourceIdentity: string,
): Promise<NavigationResolution> => unwrap(
  await HttpUtils.get<NavigationResolution>(
    `${CONSUMPTION_API}/navigation/sources/${productType}/${encodeURIComponent(sourceIdentity)}`,
  ),
  '解析来源数据产品失败',
);

export const resolveProductFromAsset = async (assetId: string | number): Promise<NavigationResolution> => unwrap(
  await HttpUtils.get<NavigationResolution>(
    `${CONSUMPTION_API}/navigation/assets/${encodeURIComponent(String(assetId))}`,
  ),
  '解析资产数据产品失败',
);
