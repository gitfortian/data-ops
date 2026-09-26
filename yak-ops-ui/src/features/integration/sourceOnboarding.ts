import type { DataSourceRecord } from '@/services/data-source';
import {
  buildIntegrationCreatePath,
  parseIntegrationSourceHandoff,
} from '@/pages/integration/sourceHandoff';

export interface BatchSourceOnboardingContext {
  createRequested: boolean;
  sourceDataSourceId?: string;
}

const stableDataSourceId = (value: unknown): string =>
  value === undefined || value === null ? '' : String(value).trim();

export const buildBatchSourceOnboardingPath = (
  sourceDataSourceId: string | number,
): string =>
  buildIntegrationCreatePath('batch', {
    dataSourceId: stableDataSourceId(sourceDataSourceId),
  });

export const parseBatchSourceOnboardingContext = (
  search: string,
): BatchSourceOnboardingContext => {
  const params = new URLSearchParams(search);
  const source = parseIntegrationSourceHandoff(search);

  return {
    createRequested: params.get('create') === '1',
    ...(source?.dataSourceId
      ? { sourceDataSourceId: source.dataSourceId }
      : {}),
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
