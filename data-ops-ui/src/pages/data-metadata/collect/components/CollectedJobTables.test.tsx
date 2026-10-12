import { fireEvent, render, screen } from '@testing-library/react';
import { searchMetadata } from '@/services/metadata/api';
import type { CollectJobRecord } from '@/services/metadata/types';
import CollectedJobTables from './CollectedJobTables';
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/metadata/api', () => ({ searchMetadata: jest.fn() }));
const job: CollectJobRecord = {
  id: 71,
  jobCode: 'orders',
  jobName: '订单采集',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  enabled: true,
  dryRunPassed: true,
};
const table = {
  id: 101,
  assetKey: 'table.orders',
  typeName: 'table',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  collectJobId: 71,
  tableName: 'orders',
  databaseName: 'trade_db',
  schemaName: 'public',
};
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(searchMetadata).mockResolvedValue({ items: [table], total: 1, typeFacets: [], from: 0, size: 8 });
});
it('queries tables by task and source identity', async () => {
  render(<CollectedJobTables job={job} sourceId="18" />);
  await screen.findByText('orders');
  expect(searchMetadata).toHaveBeenCalledWith(
    expect.objectContaining({
      queryFilter: { datasourceId: 18, providerType: 'HARVESTED', collectJobId: 71 },
      from: 0,
      size: 8,
    }),
  );
});
it('refuses a table associated with another task', async () => {
  jest
    .mocked(searchMetadata)
    .mockResolvedValue({ items: [{ ...table, collectJobId: 99 }], total: 1, typeFacets: [], from: 0, size: 8 });
  render(<CollectedJobTables job={job} sourceId="18" />);
  await screen.findByText('未能读取该任务的关联表');
  expect(screen.queryByText('orders')).not.toBeInTheDocument();
});
it('paginates on the server and distinguishes failure from empty', async () => {
  jest
    .mocked(searchMetadata)
    .mockResolvedValueOnce({ items: [table], total: 17, typeFacets: [], from: 0, size: 8 })
    .mockRejectedValueOnce(new Error('unavailable'));
  render(<CollectedJobTables job={job} sourceId="18" />);
  await screen.findByText('orders');
  fireEvent.click(screen.getByTitle('2'));
  await screen.findByText('未能读取该任务的关联表');
  expect(searchMetadata).toHaveBeenLastCalledWith(expect.objectContaining({ from: 8 }));
});
