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

/** 按关键字搜索数据源表(源表绑定下拉用)。 */
export const searchDataSourceTables = (
  id: DataSourceId,
  keyword?: string,
): Promise<DataSourceCatalogTable[]> =>
  HttpUtils.getData<DataSourceCatalogTable[]>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/tables/search${queryString({ keyword })}`,
  );
