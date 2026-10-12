import { render, screen, waitFor } from '@testing-library/react';
import { createCollectJob, updateCollectJob } from '@/services/metadata/api';
import { listDataSourceSchemas } from '@/services/data-source/api';
import CollectJobDrawer from './CollectJobDrawer';

jest.mock('@/components/CronSchedulerEditor/CronSchedulerInput', () => ({ value }: { value?: string }) =>
  <input aria-label="调度表达式" value={value || ''} readOnly />);
jest.mock('@/services/metadata/api', () => ({
  createCollectJob: jest.fn(), updateCollectJob: jest.fn(),
  getEffectivePresencePolicy: jest.fn(async () => ({ collapseThresholdPct: 30, missingRounds: 2 })),
  listEntityTypes: jest.fn(async () => []),
}));
jest.mock('@/services/data-source/api', () => ({
  listAllDataSources: jest.fn(async () => ({ bizData: [{ id: 18, name: '交易库', dbType: 'MYSQL' }], pagination: { total: 1, pageNo: 1, pageSize: 10 } })),
  listDataSourceDatabases: jest.fn(async () => ['trade_db', 'saved_db']),
  listDataSourceSchemas: jest.fn(async () => ['public', 'saved_schema']),
}));
beforeEach(() => jest.clearAllMocks());

it('prefills the actual new-task form with source scope while leaving table matching unrestricted', async () => {
  render(<CollectJobDrawer open editing={null} initialDataSourceId={18}
    initialScope={{ databaseName: 'trade_db', schemaName: 'public', jobName: '交易库结构采集', sourceTableName: 'trade_order' }}
    onClose={jest.fn()} onSaved={jest.fn()} />);
  await waitFor(() => expect(screen.getByRole('textbox', { name: '任务名称' })).toHaveValue('交易库结构采集'));
  expect(screen.getByText('trade_db', { selector: '.ant-select-selection-item' })).toBeInTheDocument();
  expect(screen.getByText('public', { selector: '.ant-select-selection-item' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: /表名匹配式/ })).toHaveValue('');
  expect(screen.getByText('从表「trade_order」进入任务定义')).toBeInTheDocument();
  await waitFor(() => expect(listDataSourceSchemas).toHaveBeenCalledWith(18, 'trade_db'));
  expect(createCollectJob).not.toHaveBeenCalled(); expect(updateCollectJob).not.toHaveBeenCalled();
});

it('keeps an existing task range and schedule when unrelated navigation hints are present', async () => {
  const editing = { id: 71, jobCode: 'saved', jobName: '原有任务', providerType: 'HARVESTED' as const, dataSourceId: 18,
    databaseName: 'saved_db', schemaName: 'saved_schema', tablePattern: 'saved_%', cronExpression: '0 0 2 * * ?', enabled: true, dryRunPassed: true };
  render(<CollectJobDrawer open editing={editing} initialDataSourceId={18} initialScope={{ databaseName: 'trade_db', schemaName: 'public', jobName: '导航名称' }}
    onClose={jest.fn()} onSaved={jest.fn()} />);
  await waitFor(() => expect(screen.getByRole('textbox', { name: '任务名称' })).toHaveValue('原有任务'));
  expect(screen.getByRole('textbox', { name: /表名匹配式/ })).toHaveValue('saved_%');
  expect(screen.getByRole('textbox', { name: '调度表达式' })).toHaveValue('0 0 2 * * ?');
  expect(screen.getByText('saved_db', { selector: '.ant-select-selection-item' })).toBeInTheDocument();
  expect(createCollectJob).not.toHaveBeenCalled(); expect(updateCollectJob).not.toHaveBeenCalled();
});
