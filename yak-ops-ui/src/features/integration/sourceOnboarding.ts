import type { DataSourceRecord } from '@/services/data-source';

export interface BatchSourceOnboardingContext {
  createRequested: boolean;
  sourceDataSourceId?: string;
}

const stableDataSourceId = (value: unknown): string =>
  value === undefined || value === null ? '' : String(value).trim();

export const buildBatchSourceOnboardingPath = (
  sourceDataSourceId: string | number,
): string => {
  const sourceId = stableDataSourceId(sourceDataSourceId);
  const params = new URLSearchParams({ create: '1' });
  if (sourceId) params.set('sourceDataSourceId', sourceId);
  return `/sync/batch-link-up?${params.toString()}`;
};

export const parseBatchSourceOnboardingContext = (
  search: string,
): BatchSourceOnboardingContext => {
  const params = new URLSearchParams(search);
  const sourceDataSourceId = stableDataSourceId(
    params.get('sourceDataSourceId'),
  );

  return {
    createRequested: params.get('create') === '1',
    ...(sourceDataSourceId ? { sourceDataSourceId } : {}),
  };
};

export const findReadableSourceDataSource = (
  records: readonly DataSourceRecord[],
  sourceDataSourceId?: string,
): DataSourceRecord | undefined => {
  const sourceId = stableDataSourceId(sourceDataSourceId);
  if (!sourceId) return undefined;
  return records.find((record) => stableDataSourceId(record.id) === sourceId);
};

export const bindOfflineDraftSource = <
  T extends { source: Record<string, unknown> },
>(
  payload: T,
  source: Pick<DataSourceRecord, 'id'>,
): T => {
  const sourceId = stableDataSourceId(source.id);
  if (!sourceId) return payload;

  return {
    ...payload,
    source: {
      ...payload.source,
      dataSourceId: sourceId,
    },
  };
};
