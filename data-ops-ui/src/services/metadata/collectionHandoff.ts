import { pageCollectJobs } from './api';
import type { CollectJobRecord } from './types';

export interface CollectionSourceScope {
  dataSourceId: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
}

export const collectDefinitionPath = (scope: CollectionSourceScope, jobId?: number): string => {
  const params = new URLSearchParams({ dataSourceId: scope.dataSourceId, action: 'define' });
  if (jobId != null) params.set('jobId', String(jobId));
  if (scope.databaseName) params.set('databaseName', scope.databaseName);
  if (scope.schemaName) params.set('schemaName', scope.schemaName);
  if (scope.tableName) params.set('sourceTableName', scope.tableName);
  return `/data-metadata/collect?${params}`;
};

const normalize = (value?: string | null) => (value || '').trim().toLowerCase();

/** Match existing harvest scope semantics; coverage does not imply a successful collection. */
export const collectionJobCovers = (job: CollectJobRecord, scope: CollectionSourceScope): boolean => {
  if (job.providerType !== 'HARVESTED' || String(job.dataSourceId) !== scope.dataSourceId) return false;
  if (job.databaseName && normalize(job.databaseName) !== normalize(scope.databaseName)) return false;
  if (job.schemaName && normalize(job.schemaName) !== normalize(scope.schemaName)) return false;
  const pattern = (job.tablePattern || '').trim();
  if (!pattern || pattern === '%') return true;
  if (!scope.tableName) return false;
  const regex = [...pattern]
    .map((unit) => (unit === '%' ? '.*' : unit === '_' ? '.' : unit.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    .join('');
  return new RegExp(`^${regex}$`, 'i').test(scope.tableName);
};

/** Read every bounded page before concluding that no existing task covers a source scope. */
export const loadSourceCollectionJobs = async (sourceKey: string): Promise<CollectJobRecord[]> => {
  const sourceId = Number(sourceKey);
  if (!Number.isSafeInteger(sourceId) || sourceId <= 0) throw new Error('Invalid source');
  const jobs: CollectJobRecord[] = [];
  let total = 0;
  for (let pageNo = 1; pageNo <= 20; pageNo += 1) {
    const result = await pageCollectJobs({ dataSourceId: sourceId, providerType: 'HARVESTED', pageNo, pageSize: 100 });
    if (result.records.some((job) => job.providerType !== 'HARVESTED' || String(job.dataSourceId) !== sourceKey))
      throw new Error('Source mismatch');
    if (pageNo > 1 && result.total !== total) throw new Error('Task list changed');
    total = result.total;
    if (!Number.isSafeInteger(total) || total < 0 || total > 2000) throw new Error('Incomplete task list');
    jobs.push(...result.records);
    if (jobs.length >= total) {
      if (new Set(jobs.map((job) => job.id)).size !== total) throw new Error('Incomplete task list');
      return jobs;
    }
    if (!result.records.length) throw new Error('Incomplete task list');
  }
  throw new Error('Incomplete task list');
};

export const findCoveringCollectionJobs = async (scope: CollectionSourceScope): Promise<CollectJobRecord[]> =>
  (await loadSourceCollectionJobs(scope.dataSourceId)).filter((job) => collectionJobCovers(job, scope));

export const collectTaskDetailPath = (jobId: number, sourceId?: string): string =>
  `/data-metadata/collect/${jobId}${sourceId ? `?dataSourceId=${encodeURIComponent(sourceId)}` : ''}`;

export const readCollectionDefinitionHint = (params: URLSearchParams) => {
  const parseId = (value: string | null) =>
    value && /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : undefined;
  const sourceId = parseId(params.get('dataSourceId'));
  const jobId = parseId(params.get('jobId'));
  const databaseName = params.get('databaseName') || undefined;
  const schemaName = params.get('schemaName') || undefined;
  const sourceTableName = params.get('sourceTableName') || undefined;
  const valid =
    Boolean(sourceId) &&
    (!params.has('jobId') || Boolean(jobId)) &&
    [databaseName, schemaName, sourceTableName].every(
      (value) => !value || (value.length <= 256 && !/[\u0000-\u001f]/.test(value)),
    );
  return { valid, sourceId, jobId, databaseName, schemaName, sourceTableName };
};
