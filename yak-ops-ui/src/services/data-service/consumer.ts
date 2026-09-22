import HttpUtils from '@/utils/HttpUtils';

import { DATA_SERVICE_API_PREFIX } from './constants';
import type {
  DataServiceApiKeyInput,
  DataServiceApiKeyUpdate,
  DataServiceIpAccessMode,
  DataServiceIpAccessRuleInput,
  DataServiceIpAccessRuleType,
} from './types';

export type DataServiceConsumerAccessScope = 'ALL' | 'SELECTED';

export interface DataServiceConsumer {
  id: number;
  name: string;
  description?: string | null;
  enabled: boolean;
  accessScope: DataServiceConsumerAccessScope;
  apiIds: number[];
  apiCount: number;
  keyCount: number;
  activeKeyCount: number;
  ipAccessMode: DataServiceIpAccessMode;
  ipRuleCount: number;
  defaultRateLimitPerMinute: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface DataServiceConsumerInput {
  name: string;
  description?: string | null;
  enabled?: boolean;
  defaultRateLimitPerMinute?: number;
}

export interface DataServiceConsumerAccessInput {
  accessScope: DataServiceConsumerAccessScope;
  apiIds: number[];
}

export interface DataServiceConsumerKey {
  id: number;
  apiId?: number | null;
  name: string;
  keyPrefix: string;
  enabled: boolean;
  rateLimitPerMinute: number;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface CreatedDataServiceConsumerKey {
  key: DataServiceConsumerKey;
  secret: string;
}

export interface DataServiceConsumerIpAccessRule {
  id: number;
  consumerId: number;
  ruleType: DataServiceIpAccessRuleType;
  networkCidr: string;
  description?: string | null;
  enabled: boolean;
  expiresAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface DataServiceConsumerIpAccessPolicy {
  mode: DataServiceIpAccessMode;
  rules: DataServiceConsumerIpAccessRule[];
}

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : '';
};

const CONSUMER_PREFIX = `${DATA_SERVICE_API_PREFIX}/consumers`;

export const listDataServiceConsumers = (): Promise<DataServiceConsumer[]> =>
  HttpUtils.getData<DataServiceConsumer[]>(CONSUMER_PREFIX);

export const getDataServiceConsumer = (id: number): Promise<DataServiceConsumer> =>
  HttpUtils.getData<DataServiceConsumer>(`${CONSUMER_PREFIX}/${id}`);

export const createDataServiceConsumer = (
  payload: DataServiceConsumerInput,
): Promise<DataServiceConsumer> =>
  HttpUtils.postData<DataServiceConsumer>(CONSUMER_PREFIX, payload);

export const updateDataServiceConsumer = (
  id: number,
  payload: DataServiceConsumerInput,
): Promise<DataServiceConsumer> =>
  HttpUtils.putData<DataServiceConsumer>(`${CONSUMER_PREFIX}/${id}`, payload);

export const deleteDataServiceConsumer = async (id: number): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${CONSUMER_PREFIX}/${id}`);
};

export const updateDataServiceConsumerAccess = (
  id: number,
  payload: DataServiceConsumerAccessInput,
): Promise<DataServiceConsumer> =>
  HttpUtils.putData<DataServiceConsumer>(`${CONSUMER_PREFIX}/${id}/access`, payload);

export const listDataServiceConsumerKeys = (
  id: number,
): Promise<DataServiceConsumerKey[]> =>
  HttpUtils.getData<DataServiceConsumerKey[]>(`${CONSUMER_PREFIX}/${id}/keys`);

export const createDataServiceConsumerKey = (
  id: number,
  payload: DataServiceApiKeyInput,
): Promise<CreatedDataServiceConsumerKey> =>
  HttpUtils.postData<CreatedDataServiceConsumerKey>(
    `${CONSUMER_PREFIX}/${id}/keys`,
    payload,
  );

export const updateDataServiceConsumerKey = (
  id: number,
  keyId: number,
  payload: DataServiceApiKeyUpdate,
): Promise<DataServiceConsumerKey> =>
  HttpUtils.putData<DataServiceConsumerKey>(
    `${CONSUMER_PREFIX}/${id}/keys/${keyId}`,
    payload,
  );

export const setDataServiceConsumerKeyEnabled = (
  id: number,
  keyId: number,
  enabled: boolean,
): Promise<DataServiceConsumerKey> =>
  HttpUtils.putData<DataServiceConsumerKey>(
    `${CONSUMER_PREFIX}/${id}/keys/${keyId}/enabled${queryString({ enabled })}`,
    {},
  );

export const rotateDataServiceConsumerKey = (
  id: number,
  keyId: number,
): Promise<CreatedDataServiceConsumerKey> =>
  HttpUtils.postData<CreatedDataServiceConsumerKey>(
    `${CONSUMER_PREFIX}/${id}/keys/${keyId}/rotate`,
    {},
  );

export const deleteDataServiceConsumerKey = async (
  id: number,
  keyId: number,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${CONSUMER_PREFIX}/${id}/keys/${keyId}`);
};

export const getDataServiceConsumerIpAccess = (
  id: number,
): Promise<DataServiceConsumerIpAccessPolicy> =>
  HttpUtils.getData<DataServiceConsumerIpAccessPolicy>(
    `${CONSUMER_PREFIX}/${id}/ip-access`,
  );

export const setDataServiceConsumerIpAccessMode = (
  id: number,
  mode: DataServiceIpAccessMode,
): Promise<DataServiceConsumerIpAccessPolicy> =>
  HttpUtils.putData<DataServiceConsumerIpAccessPolicy>(
    `${CONSUMER_PREFIX}/${id}/ip-access/mode${queryString({ mode })}`,
    {},
  );

export const createDataServiceConsumerIpAccessRule = (
  id: number,
  payload: DataServiceIpAccessRuleInput,
): Promise<DataServiceConsumerIpAccessRule> =>
  HttpUtils.postData<DataServiceConsumerIpAccessRule>(
    `${CONSUMER_PREFIX}/${id}/ip-access/rules`,
    payload,
  );

export const updateDataServiceConsumerIpAccessRule = (
  id: number,
  ruleId: number,
  payload: DataServiceIpAccessRuleInput,
): Promise<DataServiceConsumerIpAccessRule> =>
  HttpUtils.putData<DataServiceConsumerIpAccessRule>(
    `${CONSUMER_PREFIX}/${id}/ip-access/rules/${ruleId}`,
    payload,
  );

export const deleteDataServiceConsumerIpAccessRule = async (
  id: number,
  ruleId: number,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${CONSUMER_PREFIX}/${id}/ip-access/rules/${ruleId}`,
  );
};
