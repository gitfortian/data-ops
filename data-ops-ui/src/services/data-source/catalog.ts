import { queryString } from '@/services/http/query-string';
import HttpUtils from '@/utils/HttpUtils';

import type { DataSourceCatalogTable, DataSourceId } from './types';

const DATA_SOURCE_CATALOG_API_PREFIX = '/api/v1/data-source/catalog';

/** 表元数据统一取 types.ts 定义,避免 barrel 重复导出(TS2308)。 */
export type { DataSourceCatalogTable };

export interface DataSourceCatalogColumn {
  name: string;
  typeName?: string;
  jdbcType?: number;
  size?: number;
  scale?: number;
  nullable?: boolean;
  ordinalPosition?: number;
  primaryKey?: boolean;
  remarks?: string;
}

export const listDataSourceColumns = (
  id: DataSourceId,
  database: string | undefined,
  schema: string | undefined,
  table: string,
): Promise<DataSourceCatalogColumn[]> =>
  HttpUtils.getData<DataSourceCatalogColumn[]>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/columns${queryString({
      database,
      schema,
      table,
    })}`,
  );

/** SQL 模式和表路径共用的字段查询；保留 POST body，与 GET /{id}/columns 语义不同。 */
export interface DataSourceCatalogColumnOptionRow {
  fieldName?: string;
  fieldType?: string;
  fieldComment?: string;
  fieldKey?: string;
  fieldLength?: number;
  fieldIndex?: number;
  fieldIsNull?: string;
}

export const queryDataSourceColumnOptions = (
  id: DataSourceId,
  requestBody: Record<string, unknown>,
): Promise<DataSourceCatalogColumnOptionRow[]> =>
  HttpUtils.postData<DataSourceCatalogColumnOptionRow[]>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/column/${id}`,
    requestBody,
  );


/** 与字段发现接口不同：保留旧版 SQL/表模式 Top20 预览的 POST body。 */
export interface DataSourcePreviewColumn {
  title?: string;
  dataIndex?: string;
  key?: string;
  ellipsis?: boolean;
}

export interface DataSourcePreviewResult {
  columns?: DataSourcePreviewColumn[];
  data?: Array<Record<string, unknown>>;
  total?: number;
}

export const previewDataSourceTop20 = (
  id: DataSourceId,
  requestBody: Record<string, unknown>,
): Promise<DataSourcePreviewResult> =>
  HttpUtils.postData<DataSourcePreviewResult>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/getTop20Data/${id}`,
    requestBody,
  );

/** 按关键字搜索数据源表(源表绑定下拉用)。 */
export const searchDataSourceTables = (
  id: DataSourceId,
  keyword?: string,
  options: { database?: string; schema?: string; limit?: number } = {},
): Promise<DataSourceCatalogTable[]> =>
  HttpUtils.getData<DataSourceCatalogTable[]>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/tables/search${queryString({
      database: options.database,
      schema: options.schema,
      keyword,
      limit: options.limit,
    })}`,
  );
