import type {
  DatasetSourceType,
  QueryableDatasetSourceType,
} from './types';

/** Persisted backend sourceType values; TABLE/VIEW remain reserved contract values. */
export const DATASET_SOURCE_TYPES = [
  'QUERY_REVISION',
  'SQL_QUERY',
  'TABLE',
  'VIEW',
] as const satisfies readonly DatasetSourceType[];

/** Source types with an executable DatasetSourceQueryAdapter today. */
export const QUERYABLE_DATASET_SOURCE_TYPES = [
  'QUERY_REVISION',
  'SQL_QUERY',
] as const satisfies readonly QueryableDatasetSourceType[];

export const isQueryableDatasetSourceType = (
  sourceType: DatasetSourceType,
): sourceType is QueryableDatasetSourceType =>
  QUERYABLE_DATASET_SOURCE_TYPES.some((value) => value === sourceType);
