import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  HomeAssetOverview,
  HomeCockpitOverview,
  HomeDataCenterOverview,
  HomeDataCenterPeriod,
  HomeQualityOverview,
  HomeRecentResponse,
  HomeResourceCenterOverview,
  HomeScheduleCalendar,
  HomeScheduleResponse,
} from './types';

const COCKPIT_PREFIX = '/api/v1/home/cockpit';
const DATA_CENTER_PREFIX = '/api/v1/home/data-center';
const ASSET_PREFIX = '/api/v1/home/assets';
const QUALITY_PREFIX = '/api/v1/home/quality';
const RESOURCE_CENTER_PREFIX = '/api/v1/home/resources';
const SCHEDULE_CENTER_PREFIX = '/api/v1/home/schedule-center';

const requireAvailableQualityOverview = (
  response: ApiResponse<HomeQualityOverview>,
): ApiResponse<HomeQualityOverview> => {
  if (response.data?.available === false) {
    throw new Error('Home quality overview unavailable');
  }

  return response;
};

const requireAvailableResourceCenterOverview = (
  response: ApiResponse<HomeResourceCenterOverview>,
): ApiResponse<HomeResourceCenterOverview> => {
  if (response.data?.available === false) {
    throw new Error('Home resource center unavailable');
  }

  return response;
};

export const homeCockpitApi = {
  overview: (): Promise<ApiResponse<HomeCockpitOverview>> =>
    HttpUtils.get<HomeCockpitOverview>(COCKPIT_PREFIX),
};

export const homeDataCenterApi = {
  overview: (
    period: HomeDataCenterPeriod,
  ): Promise<ApiResponse<HomeDataCenterOverview>> =>
    HttpUtils.get<HomeDataCenterOverview>(
      `${DATA_CENTER_PREFIX}/overview?period=${encodeURIComponent(period)}`,
    ),
  recent: (): Promise<ApiResponse<HomeRecentResponse>> =>
    HttpUtils.get<HomeRecentResponse>(`${DATA_CENTER_PREFIX}/recent`),
  schedule: (
    period: HomeDataCenterPeriod,
  ): Promise<ApiResponse<HomeScheduleResponse>> =>
    HttpUtils.get<HomeScheduleResponse>(
      `${DATA_CENTER_PREFIX}/schedule?period=${encodeURIComponent(period)}`,
    ),
};

export const homeAssetOverviewApi = {
  overview: (): Promise<ApiResponse<HomeAssetOverview>> =>
    HttpUtils.get<HomeAssetOverview>(`${ASSET_PREFIX}/overview`),
};

export const homeQualityOverviewApi = {
  overview: (): Promise<ApiResponse<HomeQualityOverview>> =>
    HttpUtils.get<HomeQualityOverview>(`${QUALITY_PREFIX}/overview`).then(
      requireAvailableQualityOverview,
    ),
};

export const homeResourceCenterApi = {
  overview: (): Promise<ApiResponse<HomeResourceCenterOverview>> =>
    HttpUtils.get<HomeResourceCenterOverview>(
      `${RESOURCE_CENTER_PREFIX}/overview`,
    ).then(requireAvailableResourceCenterOverview),
};

export const homeScheduleCenterApi = {
  calendar: (month: string): Promise<ApiResponse<HomeScheduleCalendar>> =>
    HttpUtils.get<HomeScheduleCalendar>(
      `${SCHEDULE_CENTER_PREFIX}/calendar?month=${encodeURIComponent(month)}`,
    ),
};
