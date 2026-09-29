import HttpUtils from '@/utils/HttpUtils';

import type { ModelingImpactItem, ModelingMainlineCoverage } from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

/** 业务过程主线视图(ticket 47)。 */
export const getModelingMainline = (): Promise<ModelingMainlineCoverage[]> =>
  HttpUtils.getData<ModelingMainlineCoverage[]>(`${MODELING_API_PREFIX}/mainline`);

export const getModelingMainlineCoverage = (processId: number): Promise<ModelingMainlineCoverage> =>
  HttpUtils.getData<ModelingMainlineCoverage>(`${MODELING_API_PREFIX}/mainline/${processId}`);

/** 变更影响分析(ticket 46)。 */
export const getModelingImpactByStandardField = (processFieldId: number): Promise<ModelingImpactItem[]> =>
  HttpUtils.getData<ModelingImpactItem[]>(`${MODELING_API_PREFIX}/impact/by-standard-field/${processFieldId}`);

export const getModelingImpactBySource = (
  datasourceId: number,
  database?: string,
  table?: string,
  column?: string,
): Promise<ModelingImpactItem[]> => {
  const query = new URLSearchParams({ datasourceId: String(datasourceId) });
  if (database) query.set('database', database);
  if (table) query.set('table', table);
  if (column) query.set('column', column);
  return HttpUtils.getData<ModelingImpactItem[]>(`${MODELING_API_PREFIX}/impact/by-source?${query.toString()}`);
};
