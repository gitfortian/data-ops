import HttpUtils from '@/utils/HttpUtils';

import type {
  ModelingImportColumnView,
  ModelingImportPayload,
  ModelingImportResult,
  ModelingImportTableView,
} from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

/** 逆向导入(ticket 08)。 */
export const listModelingImportTables = (datasourceId: number, keyword?: string): Promise<ModelingImportTableView[]> =>
  HttpUtils.getData<ModelingImportTableView[]>(
    `${MODELING_API_PREFIX}/import/tables?datasourceId=${datasourceId}${
      keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''
    }`,
  );

export const previewModelingImportColumns = (
  datasourceId: number,
  database: string,
  table: string,
): Promise<ModelingImportColumnView[]> =>
  HttpUtils.postData<ModelingImportColumnView[]>(`${MODELING_API_PREFIX}/import/preview`, {
    datasourceId,
    database,
    table,
  });

export const importModelingTables = (payload: ModelingImportPayload): Promise<ModelingImportResult> =>
  HttpUtils.postData<ModelingImportResult>(`${MODELING_API_PREFIX}/import`, payload);
