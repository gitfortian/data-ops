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
  schema?: string,
): Promise<ModelingImportColumnView[]> =>
  HttpUtils.postData<ModelingImportColumnView[]>(`${MODELING_API_PREFIX}/import/preview`, {
    datasourceId,
    database,
    table,
    schema,
  });

export const importModelingTables = (payload: ModelingImportPayload): Promise<ModelingImportResult> =>
  HttpUtils.postData<ModelingImportResult>(`${MODELING_API_PREFIX}/import`, payload);

/** Modeling Owner verifies Project + datasource + database + schema + table before returning 0/1/N. */
export interface OdsSourceCandidate {
  modelId: number;
  code: string;
  name: string;
  status: string;
  sourceDatabase: string;
  sourceSchema?: string | null;
  sourceTable: string;
}
export interface OdsSourceLookup {
  state: 'READY' | 'UNVERIFIED';
  reason: string;
  datasourceId: number;
  database: string;
  schema?: string | null;
  table: string;
  candidates: OdsSourceCandidate[];
}

export const getOdsSourceCandidates = (key: {
  datasourceId: number; database: string; schema?: string; table: string;
}): Promise<OdsSourceLookup> => {
  const q = new URLSearchParams({
    datasourceId: String(key.datasourceId), database: key.database, table: key.table,
  });
  if (key.schema) q.set('schema', key.schema);
  return HttpUtils.getData<OdsSourceLookup>(`${MODELING_API_PREFIX}/source-journey/ods-candidates?${q}`);
};
