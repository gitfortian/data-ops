import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

export interface SqlDataSourceOption {
  label: string;
  value: string;
  dbType?: string;
}

export interface SqlDataSourceBinding {
  database?: string;
  schema?: string;
}

export interface SqlCatalogTable {
  database?: string | null;
  schema?: string | null;
  name: string;
  type?: string | null;
  remarks?: string | null;
}

export interface SqlCatalogColumn {
  name: string;
  typeName?: string | null;
  jdbcType?: number | null;
  size?: number | null;
  scale?: number | null;
  nullable?: boolean | null;
  ordinalPosition?: number | null;
  primaryKey?: boolean | null;
  remarks?: string | null;
}

interface ApiResponse<T> {
  code?: number;
  data?: T;
  msg?: string;
  message?: string;
}

interface SqlDataSourceDetail {
  originalJson?: string | null;
}

const DATA_SOURCE_API = '/api/v1/data-source';
const CATALOG_API = `${DATA_SOURCE_API}/catalog`;

const responseData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const queryString = (params: Record<string, string | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value?.trim()) search.set(key, value.trim());
  });
  const query = search.toString();
  return query ? `?${query}` : '';
};

const configString = (value: unknown) => {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim();
  return normalized || undefined;
};

const parseConnectionConfig = (originalJson?: string | null) => {
  if (!originalJson) return {} as Record<string, unknown>;
  try {
    const parsed = JSON.parse(originalJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {} as Record<string, unknown>;
  }
};

export const listSqlDataSources = async (): Promise<SqlDataSourceOption[]> =>
  responseData(
    await HttpUtils.get<SqlDataSourceOption[]>(`${DATA_SOURCE_API}/option`),
    '查询数据源失败',
  );

export const getSqlDataSourceBinding = async (
  dataSourceId: string,
): Promise<SqlDataSourceBinding> => {
  const detail = responseData(
    await HttpUtils.get<SqlDataSourceDetail>(`${DATA_SOURCE_API}/${dataSourceId}`),
    '查询数据源配置失败',
  );
  const config = parseConnectionConfig(detail.originalJson);

  return {
    database: configString(
      config.database ?? config.databaseName ?? config.dbName ?? config.catalog,
    ),
    schema: configString(config.schema ?? config.schemaName),
  };
};

export const listSqlDatabases = async (
  dataSourceId: string,
): Promise<string[]> =>
  responseData(
    await HttpUtils.get<string[]>(`${CATALOG_API}/${dataSourceId}/databases`),
    '查询数据库失败',
  );

export const listSqlSchemas = async (
  dataSourceId: string,
  database?: string,
): Promise<string[]> =>
  responseData(
    await HttpUtils.get<string[]>(
      `${CATALOG_API}/${dataSourceId}/schemas${queryString({ database })}`,
    ),
    '查询 Schema 失败',
  );

export const listSqlTables = async (
  dataSourceId: string,
  options: { database?: string; schema?: string; keyword?: string } = {},
): Promise<SqlCatalogTable[]> =>
  responseData(
    await HttpUtils.get<SqlCatalogTable[]>(
      `${CATALOG_API}/${dataSourceId}/tables${queryString(options)}`,
    ),
    '查询表元数据失败',
  );

export const listSqlColumns = async (
  dataSourceId: string,
  options: { database?: string; schema?: string; table: string },
): Promise<SqlCatalogColumn[]> =>
  responseData(
    await HttpUtils.get<SqlCatalogColumn[]>(
      `${CATALOG_API}/${dataSourceId}/columns${queryString(options)}`,
    ),
    '查询字段元数据失败',
  );
