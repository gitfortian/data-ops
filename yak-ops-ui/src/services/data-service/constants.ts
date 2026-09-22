export const DATA_SERVICE_NODE_SOURCE =
  'DATA_DEVELOPMENT_DATA_SERVICE' as const;

export const LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE =
  'DATA_DEVELOPMENT_RELEASE' as const;

/** 由业务模块来源托管（provider-managed）发布的 API 在详情页的来源名。 */
export const DATA_SERVICE_PROVIDER_SOURCE_LABELS: Record<string, string> = {
  MDM_DISTRIBUTION: '主数据分发',
};

export const DATA_SERVICE_API_PREFIX = '/api/v1/data-service';
export const DATA_SOURCE_OPTION_API = '/api/v1/data-source/option';
