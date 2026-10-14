import { act, configure, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ButtonHTMLAttributes } from 'react';
import { listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import { listDataSourceColumns, searchDataSourceTables } from '@/services/data-source/catalog';
import LiveSchemaExplorer from './LiveSchemaExplorer';

configure({ asyncUtilTimeout: 5000 });

jest.mock('@/components/ui', () => ({
  YakButton: ({
    children,
    type: _type,
    icon: _icon,
    ...props
  }: ButtonHTMLAttributes<HTMLButtonElement> & { icon?: unknown }) => (
    <button type="button" {...props}>
      {children}
    </button>
  ),
}));
jest.mock('@/services/data-source/api', () => ({
  listDataSourceDatabases: jest.fn(),
  listDataSourceSchemas: jest.fn(),
}));
jest.mock('@/services/data-source/catalog', () => ({
  listDataSourceColumns: jest.fn(),
  searchDataSourceTables: jest.fn(),
}));

const mockDatabases = jest.mocked(listDataSourceDatabases);
const mockSchemas = jest.mocked(listDataSourceSchemas);
const mockTables = jest.mocked(searchDataSourceTables);
const mockColumns = jest.mocked(listDataSourceColumns);
const table = { database: 'trade_db', schema: 'public', name: 'trade_order', type: 'TABLE', remarks: '订单表' };

beforeEach(() => {
  jest.clearAllMocks();
  mockDatabases.mockResolvedValue(['trade_db']);
  mockSchemas.mockResolvedValue(['public', 'audit']);
  mockTables.mockResolvedValue([table]);
  mockColumns.mockResolvedValue([{ name: 'order_id', typeName: 'BIGINT', primaryKey: true, nullable: false }]);
});

it('opens the source catalog automatically and reads columns in place with the complete physical path', async () => {
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  const row = await screen.findByRole('button', { name: 'trade_order' });
  expect(mockDatabases).toHaveBeenCalledWith('18');
  expect(mockSchemas).toHaveBeenCalledWith('18', 'trade_db');
  expect(mockTables).toHaveBeenCalledWith('18', undefined, { database: 'trade_db', schema: undefined, limit: 100 });
  fireEvent.click(row);
  await screen.findByText('order_id');
  expect(mockColumns).toHaveBeenCalledWith('18', 'trade_db', 'public', 'trade_order');
  expect(screen.getByLabelText('主键')).toBeInTheDocument();
  expect(screen.getByText('否')).toBeInTheDocument();
});

it('does not turn an unavailable source into an empty catalog and can recover', async () => {
  mockTables.mockRejectedValueOnce(new Error('connection unavailable'));
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  await screen.findByText('源库结构探查失败');
  expect(screen.queryByText('当前范围没有可读取的表或视图')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新探查' }));
  await screen.findByRole('button', { name: 'trade_order' });
  expect(screen.queryByText('源库结构探查失败')).not.toBeInTheDocument();
});

it('keeps same-named tables in different schemas distinct when reading fields', async () => {
  mockTables.mockResolvedValue([table, { ...table, schema: 'audit' }]);
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  await screen.findAllByRole('button', { name: 'trade_order' });
  fireEvent.click(screen.getAllByRole('button', { name: 'trade_order' })[1]);
  await screen.findByText('order_id');
  expect(mockColumns).toHaveBeenCalledWith('18', 'trade_db', 'audit', 'trade_order');
});

it('ignores fields returned late for a previously selected table', async () => {
  let resolveColumns!: (value: Awaited<ReturnType<typeof listDataSourceColumns>>) => void;
  mockColumns.mockReturnValueOnce(
    new Promise((resolve) => {
      resolveColumns = resolve;
    }),
  );
  mockColumns.mockResolvedValueOnce([{ name: 'detail_id', typeName: 'BIGINT' }]);
  mockTables.mockResolvedValue([table, { ...table, name: 'trade_order_detail' }]);
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  fireEvent.click(await screen.findByRole('button', { name: 'trade_order' }));
  await waitFor(() => expect(mockColumns).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: 'trade_order_detail' }));
  await screen.findByText('detail_id');
  await act(async () => resolveColumns([{ name: 'stale_order_id' }]));
  expect(screen.queryByText('stale_order_id')).not.toBeInTheDocument();
  expect(screen.getByText('detail_id')).toBeInTheDocument();
});

it('searches directly within the chosen source range with a bounded result size', async () => {
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  await screen.findByRole('button', { name: 'trade_order' });
  fireEvent.change(screen.getByRole('textbox', { name: '搜索源库表' }), { target: { value: 'order' } });
  await waitFor(() =>
    expect(mockTables).toHaveBeenLastCalledWith('18', 'order', { database: 'trade_db', schema: undefined, limit: 100 }),
  );
});

it('does not make source reads without permission', () => {
  render(<LiveSchemaExplorer sourceId="18" canRead={false} />);
  expect(screen.getByText('当前没有数据源结构读取权限')).toBeInTheDocument();
  expect(mockDatabases).not.toHaveBeenCalled();
  expect(mockTables).not.toHaveBeenCalled();
  expect(mockColumns).not.toHaveBeenCalled();
});

it('reports field-read failure without substituting collected metadata', async () => {
  mockColumns.mockRejectedValueOnce(new Error('denied'));
  render(<LiveSchemaExplorer sourceId="18" canRead />);
  fireEvent.click(await screen.findByRole('button', { name: 'trade_order' }));
  await screen.findByText('未能读取字段结构');
  expect(screen.queryByText('没有返回字段结构')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取所选表字段' }));
  await screen.findByText('order_id');
});

it('opens ODS only from a confirmed physical table with exact source coordinates', async () => {
  const onOpenOds = jest.fn();
  render(<LiveSchemaExplorer sourceId="18" canRead onOpenOds={onOpenOds} />);
  fireEvent.click(await screen.findByRole('button', { name: 'trade_order' }));
  await screen.findByText('order_id');
  fireEvent.click(screen.getByRole('button', { name: '打开或创建 ODS' }));
  expect(onOpenOds).toHaveBeenCalledWith({
    dataSourceId: 18, database: 'trade_db', schema: 'public', table: 'trade_order',
  });
});

it('restores exactly the returned schema/table rather than a different same-name source', async () => {
  mockTables.mockResolvedValue([table, { ...table, schema: 'audit' }]);
  render(<LiveSchemaExplorer sourceId="18" canRead initialTable={{
    database: 'trade_db', schema: 'public', table: 'trade_order',
  }} />);
  await screen.findByText('order_id');
  expect(mockColumns).toHaveBeenCalledWith('18', 'trade_db', 'public', 'trade_order');
  expect(mockColumns).not.toHaveBeenCalledWith('18', 'trade_db', 'audit', 'trade_order');
});

it('does not offer ODS creation for a view or when current columns could not be verified', async () => {
  const onOpenOds = jest.fn();
  mockTables.mockResolvedValue([{ ...table, type: 'VIEW' }]);
  const { unmount } = render(<LiveSchemaExplorer sourceId="18" canRead onOpenOds={onOpenOds} />);
  fireEvent.click(await screen.findByRole('button', { name: 'trade_order' }));
  await screen.findByText('order_id');
  expect(screen.queryByRole('button', { name: '打开或创建 ODS' })).not.toBeInTheDocument();
  unmount();
  mockTables.mockResolvedValue([table]);
  mockColumns.mockRejectedValue(new Error('denied'));
  render(<LiveSchemaExplorer sourceId="18" canRead onOpenOds={onOpenOds} />);
  fireEvent.click(await screen.findByRole('button', { name: 'trade_order' }));
  await screen.findByText('未能读取字段结构');
  expect(screen.getByRole('button', { name: '打开或创建 ODS' })).toBeDisabled();
  expect(onOpenOds).not.toHaveBeenCalled();
});
