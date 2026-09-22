import HttpUtils from '@/utils/HttpUtils';

import type { DashboardQueryPerformance } from './types';

const PERFORMANCE_API = '/api/v1/datasets/query-performance';

export const getDashboardQueryPerformance = async (
  queryIds: string[],
): Promise<DashboardQueryPerformance[]> => {
  if (!queryIds.length) return [];
  const params = new URLSearchParams();
  queryIds.forEach((queryId) => params.append('queryIds', queryId));
  params.set('limit', String(Math.min(queryIds.length, 200)));
  return HttpUtils.getData<DashboardQueryPerformance[]>(
    `${PERFORMANCE_API}?${params.toString()}`,
  );
};

export const fetchDashboardQueryPerformance = getDashboardQueryPerformance;
