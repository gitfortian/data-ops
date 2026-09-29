import HttpUtils from '@/utils/HttpUtils';

import { DATA_SERVICE_API_PREFIX } from './constants';
import type { DataServiceAuthMode, DataServiceIpAccessMode } from './types';

/** Read model used only by the standalone access-control management page. */
export interface DataServiceAccessOverviewItem {
  apiId: number;
  name: string;
  path: string;
  runtimePath: string;
  parameterNames: string[];
  enabled: boolean;
  authMode: DataServiceAuthMode;
  ipAccessMode: DataServiceIpAccessMode;
  apiKeyCount: number;
  activeApiKeyCount: number;
  allowlistRuleCount: number;
  activeAllowlistRuleCount: number;
  denylistRuleCount: number;
  activeDenylistRuleCount: number;
  updateTime?: string | null;
}

export const listDataServiceAccessOverview = (): Promise<DataServiceAccessOverviewItem[]> =>
  HttpUtils.getData<DataServiceAccessOverviewItem[]>(
    `${DATA_SERVICE_API_PREFIX}/access-overview`,
  );
