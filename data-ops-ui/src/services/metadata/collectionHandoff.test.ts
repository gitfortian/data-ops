import { pageCollectJobs } from './api';
import {
  collectDefinitionPath,
  readCollectTaskOrigin,
  verifiedCollectTaskReturnPath,
  collectTaskDetailWithOriginPath,
  collectionJobCovers,
  findCoveringCollectionJobs,
  readCollectionDefinitionHint,
} from './collectionHandoff';
import type { CollectJobRecord } from './types';
jest.mock('./api', () => ({ pageCollectJobs: jest.fn() }));
const job: CollectJobRecord = {
  id: 71,
  jobCode: 'trade',
  jobName: '交易库采集',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  databaseName: 'trade_db',
  enabled: false,
  dryRunPassed: false,
};
const scope = { dataSourceId: '18', databaseName: 'trade_db', schemaName: 'public', tableName: 'trade_order' };
beforeEach(() => jest.clearAllMocks());

it('reuses a whole-database task for a newly discovered table regardless of schedule state', () => {
  expect(collectionJobCovers(job, scope)).toBe(true);
  expect(collectionJobCovers({ ...job, databaseName: undefined }, { ...scope, databaseName: 'new_db' })).toBe(true);
  expect(collectionJobCovers(job, { ...scope, databaseName: 'new_db' })).toBe(false);
  expect(collectionJobCovers({ ...job, schemaName: 'audit' }, scope)).toBe(false);
  expect(collectionJobCovers({ ...job, dataSourceId: 99 }, scope)).toBe(false);
});

it('handles wildcard scope but never treats a partial-table task as a whole-database task', () => {
  expect(collectionJobCovers({ ...job, tablePattern: 'trade_%' }, scope)).toBe(true);
  expect(collectionJobCovers({ ...job, tablePattern: 'trade_%' }, { ...scope, tableName: undefined })).toBe(false);
  expect(collectionJobCovers({ ...job, tablePattern: 'orders.v1' }, { ...scope, tableName: 'ordersXv1' })).toBe(false);
  expect(collectionJobCovers({ ...job, tablePattern: 'orders.v1' }, { ...scope, tableName: 'orders.v1' })).toBe(true);
});

it('checks later pages before concluding that a task does not exist', async () => {
  const first = Array.from({ length: 100 }, (_, index) => ({ ...job, id: index + 100, databaseName: 'other_db' }));
  jest
    .mocked(pageCollectJobs)
    .mockResolvedValueOnce({ records: first, total: 101, pages: 2, pageNo: 1, pageSize: 100 })
    .mockResolvedValueOnce({ records: [job], total: 101, pages: 2, pageNo: 2, pageSize: 100 });
  await expect(findCoveringCollectionJobs(scope)).resolves.toEqual([job]);
  expect(pageCollectJobs).toHaveBeenLastCalledWith({
    dataSourceId: 18,
    providerType: 'HARVESTED',
    pageNo: 2,
    pageSize: 100,
  });
});

it('does not classify failed or incomplete task queries as no matching task', async () => {
  jest.mocked(pageCollectJobs).mockRejectedValueOnce(new Error('failed'));
  await expect(findCoveringCollectionJobs(scope)).rejects.toThrow();
  jest.mocked(pageCollectJobs).mockResolvedValueOnce({ records: [], total: 10, pages: 1, pageNo: 1, pageSize: 100 });
  await expect(findCoveringCollectionJobs(scope)).rejects.toThrow('Incomplete task list');
});

it('passes bounded navigation context without a prefilled run, schedule or narrowed table pattern', () => {
  const path = collectDefinitionPath(scope, 71);
  const params = new URL(path, 'http://localhost').searchParams;
  expect(readCollectionDefinitionHint(params)).toEqual({
    valid: true,
    sourceId: 18,
    jobId: 71,
    databaseName: 'trade_db',
    schemaName: 'public',
    sourceTableName: 'trade_order',
  });
  expect(params.has('tablePattern')).toBe(false);
  expect(params.has('enabled')).toBe(false);
  expect(readCollectionDefinitionHint(new URLSearchParams('dataSourceId=18&jobId=bad')).valid).toBe(false);
  expect(
    readCollectionDefinitionHint(new URLSearchParams(`dataSourceId=18&databaseName=${'x'.repeat(257)}`)).valid,
  ).toBe(false);
});

it('carries only a complete verified origin from the saved task into detail and back to source', () => {
  const saved = { ...job, schemaName: 'public', tablePattern: 'trade_%' };
  const detailPath = collectTaskDetailWithOriginPath(saved, scope);
  expect(detailPath.startsWith('/data-metadata/collect/71?')).toBe(true);
  const hint = readCollectTaskOrigin(new URL(detailPath, 'http://localhost').searchParams);
  expect(hint).toEqual(scope);
  const back = verifiedCollectTaskReturnPath(saved, hint);
  expect(back?.startsWith('/data-source?')).toBe(true);
  const q = new URL(back!, 'http://localhost').searchParams;
  expect(q.get('sourceId')).toBe('18');
  expect(q.get('database')).toBe('trade_db');
  expect(q.get('schema')).toBe('public');
  expect(q.get('table')).toBe('trade_order');
});

it('refuses fake source origins, out-of-scope jobs, and partial table hints', () => {
  const saved = { ...job, schemaName: 'public', tablePattern: 'trade_%' };
  expect(verifiedCollectTaskReturnPath(saved, { ...scope, schemaName: 'audit' })).toBeNull();
  expect(verifiedCollectTaskReturnPath({ ...saved, dataSourceId: 99 }, scope)).toBeNull();
  expect(verifiedCollectTaskReturnPath({ ...saved, providerType: 'REGISTERED' }, scope)).toBeNull();
  expect(verifiedCollectTaskReturnPath({ ...saved, tablePattern: 'unrelated' }, scope)).toBeNull();
  expect(readCollectTaskOrigin(new URLSearchParams('dataSourceId=18&database=trade_db'))).toBeNull();
  expect(readCollectTaskOrigin(new URLSearchParams('dataSourceId=0&database=trade_db&table=trade_order'))).toBeNull();
  const path = collectTaskDetailWithOriginPath({ ...saved, schemaName: 'audit' }, scope);
  expect(new URL(path, 'http://localhost').searchParams.has('table')).toBe(false);
});

it('does not fabricate exact table identity from a broad task-only link', () => {
  const origin = readCollectTaskOrigin(new URLSearchParams('dataSourceId=18'));
  expect(origin).toBeNull();
  expect(verifiedCollectTaskReturnPath(job, origin)).toBeNull();
});
