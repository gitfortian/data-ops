import {
  DATASET_SOURCE_TYPES,
  isQueryableDatasetSourceType,
  QUERYABLE_DATASET_SOURCE_TYPES,
} from './constants';

describe('Dataset source contract', () => {
  it('keeps persisted source types aligned with the backend contract', () => {
    expect(DATASET_SOURCE_TYPES).toEqual([
      'QUERY_REVISION',
      'SQL_QUERY',
      'TABLE',
      'VIEW',
    ]);
  });

  it('only exposes source types backed by the current Query Runtime', () => {
    expect(QUERYABLE_DATASET_SOURCE_TYPES).toEqual([
      'QUERY_REVISION',
      'SQL_QUERY',
    ]);
    expect(isQueryableDatasetSourceType('QUERY_REVISION')).toBe(true);
    expect(isQueryableDatasetSourceType('SQL_QUERY')).toBe(true);
    expect(isQueryableDatasetSourceType('TABLE')).toBe(false);
    expect(isQueryableDatasetSourceType('VIEW')).toBe(false);
  });
});
