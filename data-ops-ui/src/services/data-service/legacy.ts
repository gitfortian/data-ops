import HttpUtils from '@/utils/HttpUtils';

import {
  DATA_SERVICE_API_PREFIX,
  DATA_SOURCE_OPTION_API,
} from './constants';
import type {
  CommonApiResponse,
  CreatedDataServiceApiKey,
  DataServiceApi,
  DataServiceApiKey,
  DataServiceApiKeyInput,
  DataServiceApiKeyUpdate,
  DataServiceAuthMode,
  DataServiceCallLog,
  DataServiceDocumentation,
  DataServiceDocumentationInput,
  DataServicePublishPayload,
  DataServiceQueryResult,
  DataServiceRuntimeConfig,
  DataServiceRuntimeStatus,
  DataServiceSourcePage,
  DataServiceSourceQuery,
  DataServiceUpdatePayload,
  DataSourceOption,
} from './types';

const queryString = (params: object) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : '';
};

export const fetchDataServices = (): Promise<
  CommonApiResponse<DataServiceApi[]>
> => HttpUtils.get<DataServiceApi[]>(DATA_SERVICE_API_PREFIX);

export const fetchDataServiceSources = ({
  sourceType,
  pageNo = 1,
  pageSize = 100,
  keyword,
}: DataServiceSourceQuery): Promise<
  CommonApiResponse<DataServiceSourcePage>
> =>
  HttpUtils.get<DataServiceSourcePage>(
    `${DATA_SERVICE_API_PREFIX}/sources${queryString({
      sourceType,
      pageNo,
      pageSize,
      keyword,
    })}`,
  );

export const publishDataService = (
  payload: DataServicePublishPayload,
): Promise<CommonApiResponse<DataServiceApi>> =>
  HttpUtils.post<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/publish`,
    payload,
  );

export const republishDataService = (
  id: number,
): Promise<CommonApiResponse<DataServiceApi>> =>
  HttpUtils.post<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}/republish`,
    {},
  );

export const updateDataService = (
  id: number,
  payload: DataServiceUpdatePayload,
): Promise<CommonApiResponse<DataServiceApi>> =>
  HttpUtils.put<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}`,
    payload,
  );

export const deleteDataService = (
  id: number,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.delete<boolean>(`${DATA_SERVICE_API_PREFIX}/${id}`);

export const setDataServiceEnabled = (
  id: number,
  enabled: boolean,
): Promise<CommonApiResponse<DataServiceApi>> =>
  HttpUtils.put<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}/enabled${queryString({ enabled })}`,
    {},
  );

export const fetchDataServiceDocumentation = (
  id: number,
): Promise<CommonApiResponse<DataServiceDocumentation>> =>
  HttpUtils.get<DataServiceDocumentation>(
    `${DATA_SERVICE_API_PREFIX}/${id}/documentation`,
  );

export const saveDataServiceDocumentation = (
  id: number,
  payload: DataServiceDocumentationInput,
): Promise<CommonApiResponse<DataServiceDocumentation>> =>
  HttpUtils.put<DataServiceDocumentation>(
    `${DATA_SERVICE_API_PREFIX}/${id}/documentation`,
    payload,
  );

export const fetchDataServiceOpenApi = (
  id: number,
): Promise<CommonApiResponse<Record<string, unknown>>> =>
  HttpUtils.get<Record<string, unknown>>(
    `${DATA_SERVICE_API_PREFIX}/${id}/openapi`,
  );

export const fetchDataServiceRuntime = (
  id: number,
): Promise<CommonApiResponse<DataServiceRuntimeStatus>> =>
  HttpUtils.get<DataServiceRuntimeStatus>(
    `${DATA_SERVICE_API_PREFIX}/${id}/runtime`,
  );

export const updateDataServiceRuntime = (
  id: number,
  payload: DataServiceRuntimeConfig,
): Promise<CommonApiResponse<DataServiceRuntimeStatus>> =>
  HttpUtils.put<DataServiceRuntimeStatus>(
    `${DATA_SERVICE_API_PREFIX}/${id}/runtime`,
    payload,
  );

export const setDataServiceAuthMode = (
  id: number,
  mode: DataServiceAuthMode,
): Promise<CommonApiResponse<DataServiceAuthMode>> =>
  HttpUtils.put<DataServiceAuthMode>(
    `${DATA_SERVICE_API_PREFIX}/${id}/auth-mode${queryString({ mode })}`,
    {},
  );

export const fetchDataServiceKeys = (
  id: number,
): Promise<CommonApiResponse<DataServiceApiKey[]>> =>
  HttpUtils.get<DataServiceApiKey[]>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys`,
  );

export const createDataServiceKey = (
  id: number,
  payload: DataServiceApiKeyInput,
): Promise<CommonApiResponse<CreatedDataServiceApiKey>> =>
  HttpUtils.post<CreatedDataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys`,
    payload,
  );

export const updateDataServiceKey = (
  id: number,
  keyId: number,
  payload: DataServiceApiKeyUpdate,
): Promise<CommonApiResponse<DataServiceApiKey>> =>
  HttpUtils.put<DataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}`,
    payload,
  );

export const setDataServiceKeyEnabled = (
  id: number,
  keyId: number,
  enabled: boolean,
): Promise<CommonApiResponse<DataServiceApiKey>> =>
  HttpUtils.put<DataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}/enabled${queryString({
      enabled,
    })}`,
    {},
  );

export const rotateDataServiceKey = (
  id: number,
  keyId: number,
): Promise<CommonApiResponse<CreatedDataServiceApiKey>> =>
  HttpUtils.post<CreatedDataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}/rotate`,
    {},
  );

export const deleteDataServiceKey = (
  id: number,
  keyId: number,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.delete<boolean>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}`,
  );

export const testDataService = (
  id: number,
  parameters: Record<string, string>,
): Promise<CommonApiResponse<DataServiceQueryResult>> =>
  HttpUtils.post<DataServiceQueryResult>(
    `${DATA_SERVICE_API_PREFIX}/${id}/test`,
    parameters,
  );

export const fetchDataServiceLogs = (): Promise<
  CommonApiResponse<DataServiceCallLog[]>
> =>
  HttpUtils.get<DataServiceCallLog[]>(
    `${DATA_SERVICE_API_PREFIX}/logs/recent`,
  );

export const fetchDataSourceOptions = (): Promise<
  CommonApiResponse<DataSourceOption[]>
> => HttpUtils.get<DataSourceOption[]>(DATA_SOURCE_OPTION_API);
