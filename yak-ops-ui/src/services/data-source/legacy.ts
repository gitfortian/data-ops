import HttpUtils from '@/utils/HttpUtils';

import type {
  CommonApiResponse,
  DataSourceCatalogRow,
  DataSourceConnectTestPayload,
  DataSourceId,
  DataSourcePageParams,
  DataSourcePageResult,
  DataSourceRecord,
  DataSourceSavePayload,
  DataSourceSummary,
  DynamicFormSchemaResponse,
} from './types';

const DATA_SOURCE_API_PREFIX = '/api/v1/data-source';
const DATA_SOURCE_CATALOG_API_PREFIX = `${DATA_SOURCE_API_PREFIX}/catalog`;

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
};

export const fetchDataSourcePage = (
  params: DataSourcePageParams,
): Promise<CommonApiResponse<DataSourcePageResult>> =>
  HttpUtils.post<DataSourcePageResult>(`${DATA_SOURCE_API_PREFIX}/page`, params);

export const fetchDataSourceSummary = (): Promise<
  CommonApiResponse<DataSourceSummary>
> => HttpUtils.get<DataSourceSummary>(`${DATA_SOURCE_API_PREFIX}/summary`);

export const fetchDataSourceDetail = (
  id: DataSourceId,
): Promise<CommonApiResponse<DataSourceRecord>> =>
  HttpUtils.get<DataSourceRecord>(`${DATA_SOURCE_API_PREFIX}/${id}`);

export const fetchDataSourceAll = (): Promise<
  CommonApiResponse<DataSourcePageResult>
> => HttpUtils.get<DataSourcePageResult>(`${DATA_SOURCE_API_PREFIX}/all`);

export const createDataSource = (
  payload: DataSourceSavePayload,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.post<boolean>(DATA_SOURCE_API_PREFIX, payload);

export const updateDataSource = (
  id: DataSourceId,
  payload: DataSourceSavePayload,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.put<boolean>(`${DATA_SOURCE_API_PREFIX}/${id}`, payload);

export const deleteDataSource = (
  id: DataSourceId,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.delete<boolean>(`${DATA_SOURCE_API_PREFIX}/${id}`);

export const testDataSourceConnection = (
  id: DataSourceId,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.post<boolean>(`${DATA_SOURCE_API_PREFIX}/${id}/connect-test`, {});

export const testDataSourceConnectionWithParams = (
  payload: DataSourceConnectTestPayload,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.post<boolean>(
    `${DATA_SOURCE_API_PREFIX}/connect-test-with-param`,
    payload,
  );

export const fetchDataSourceOptions = (
  dbType?: string,
): Promise<CommonApiResponse<unknown[]>> =>
  HttpUtils.get<unknown[]>(
    `${DATA_SOURCE_API_PREFIX}/option${queryString({ dbType })}`,
  );

export const fetchDataSourcePluginConfig = (
  pluginType: string,
): Promise<CommonApiResponse<DynamicFormSchemaResponse>> =>
  HttpUtils.get<DynamicFormSchemaResponse>(
    `${DATA_SOURCE_API_PREFIX}/plugin/config${queryString({ pluginType })}`,
  );

export const installDataSourcePlugin = (
  pluginType: string,
): Promise<CommonApiResponse<boolean>> =>
  HttpUtils.post<boolean>(
    `${DATA_SOURCE_API_PREFIX}/plugin/config/install${queryString({ pluginType })}`,
    {},
  );

export const dataSourceCatalogApi = {
  listDatabases: (id: DataSourceId): Promise<CommonApiResponse<string[]>> =>
    HttpUtils.get<string[]>(`${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/databases`),

  listSchemas: (
    id: DataSourceId,
    database?: string,
  ): Promise<CommonApiResponse<string[]>> =>
    HttpUtils.get<string[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/schemas${queryString({ database })}`,
    ),

  listTables: (
    id: DataSourceId,
    database?: string,
    schema?: string,
    keyword?: string,
  ): Promise<CommonApiResponse<DataSourceCatalogRow[]>> =>
    HttpUtils.get<DataSourceCatalogRow[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/tables${queryString({
        database,
        schema,
        keyword,
      })}`,
    ),

  searchTables: (
    id: DataSourceId,
    keyword?: string,
    limit?: number,
    database?: string,
    schema?: string,
  ): Promise<CommonApiResponse<DataSourceCatalogRow[]>> =>
    HttpUtils.get<DataSourceCatalogRow[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/tables/search${queryString({
        database,
        schema,
        keyword,
        limit,
      })}`,
    ),

  listColumns: (
    id: DataSourceId,
    database: string | undefined,
    schema: string | undefined,
    table: string,
  ): Promise<CommonApiResponse<DataSourceCatalogRow[]>> =>
    HttpUtils.get<DataSourceCatalogRow[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/columns${queryString({
        database,
        schema,
        table,
      })}`,
    ),

  listTable: (id: DataSourceId): Promise<CommonApiResponse<unknown[]>> =>
    HttpUtils.get<unknown[]>(`${DATA_SOURCE_CATALOG_API_PREFIX}/list/${id}`),

  listTableReference: (
    id: DataSourceId,
    matchMode?: string | number,
    keyword?: string,
  ): Promise<CommonApiResponse<unknown[]>> =>
    HttpUtils.get<unknown[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/listByMatchMode/${id}${queryString({
        matchMode,
        keyword,
      })}`,
    ),

  count: (
    dataSourceId: DataSourceId,
    requestBody: Record<string, unknown>,
  ): Promise<CommonApiResponse<number>> =>
    HttpUtils.post<number>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/count/${dataSourceId}`,
      requestBody,
    ),

  listColumn: (
    id: DataSourceId,
    requestBody: Record<string, unknown>,
  ): Promise<CommonApiResponse<unknown[]>> =>
    HttpUtils.post<unknown[]>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/column/${id}`,
      requestBody,
    ),

  getTop20Data: (
    dataSourceId: DataSourceId,
    requestBody: Record<string, unknown>,
  ): Promise<CommonApiResponse<unknown>> =>
    HttpUtils.post<unknown>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/getTop20Data/${dataSourceId}`,
      requestBody,
    ),

  buildSqlTemplate: (
    dataSourceId: DataSourceId,
    requestBody: Record<string, unknown>,
  ): Promise<CommonApiResponse<string>> =>
    HttpUtils.post<string>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/sql-template/${dataSourceId}`,
      requestBody,
    ),

  resolveSql: (
    dataSourceId: DataSourceId,
    requestBody: Record<string, unknown>,
  ): Promise<CommonApiResponse<string>> =>
    HttpUtils.post<string>(
      `${DATA_SOURCE_CATALOG_API_PREFIX}/resolve-sql/${dataSourceId}`,
      requestBody,
    ),
};
