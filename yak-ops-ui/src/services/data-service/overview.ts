import HttpUtils from '@/utils/HttpUtils';

export type DataServiceOverviewRange = '24h' | '7d' | '30d';

export interface DataServiceOverviewTrendPoint {
  time: string;
  calls: number;
  successCalls: number;
  failureCalls: number;
  averageDurationMs: number;
}

export interface DataServiceOverviewHotApi {
  apiId: number;
  name: string;
  path: string;
  calls: number;
  successRate: number;
  averageDurationMs: number;
}

export interface DataServiceOverviewFailure {
  id: number;
  apiId: number;
  serviceName: string;
  servicePath: string;
  durationMs: number;
  errorMessage?: string | null;
  createTime?: string | null;
}

export interface DataServiceOverview {
  range: DataServiceOverviewRange;
  startTime: string;
  endTime: string;
  apiTotal: number;
  runningApis: number;
  stoppedApis: number;
  totalCalls: number;
  successCalls: number;
  failureCalls: number;
  successRate: number;
  averageDurationMs: number;
  totalRows: number;
  trend: DataServiceOverviewTrendPoint[];
  hotApis: DataServiceOverviewHotApi[];
  recentFailures: DataServiceOverviewFailure[];
}

const DATA_SERVICE_OVERVIEW_API = '/api/v1/data-service/overview';

export const getDataServiceOverview = (
  range: DataServiceOverviewRange,
): Promise<DataServiceOverview> =>
  HttpUtils.getData<DataServiceOverview>(
    `${DATA_SERVICE_OVERVIEW_API}?range=${encodeURIComponent(range)}`,
  );
