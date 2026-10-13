import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import AssetExplorer from './AssetExplorer';
import { searchMetadata, listEntityTypes, getEntityDetail } from '@/services/metadata/api';
import { listAllDataSources } from '@/services/data-source/api';

jest.mock('@/services/metadata/api', () => ({
  searchMetadata: jest.fn(),
  listEntityTypes: jest.fn(),
  getEntityDetail: jest.fn(),
}));
jest.mock('@/services/data-source/api', () => ({ listAllDataSources: jest.fn() }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/components/metadata/AssetDetailDrawer', () => () => null);
jest.mock('@/components/ui', () => ({
  YakEmpty: ({ title, description, children }: any) => (
    <div>
      {title}
      {description}
      {children}
    </div>
  ),
}));
jest.mock('@/components/ReadableTable', () => ({
  __esModule: true,
  default: ({ dataSource, columns }: any) => (
    <div>
      {dataSource.map((item: any) => (
        <div key={item.id}>
          {columns.map((column: any, index: number) => (
            <div key={index}>{column.render?.(item[column.dataIndex], item)}</div>
          ))}
        </div>
      ))}
    </div>
  ),
}));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Grid: { useBreakpoint: () => ({ sm: true }) } };
});

const search = jest.mocked(searchMetadata);
const sources = jest.mocked(listAllDataSources);
const result = (name: string, extra = {}) => ({
  items: [{ id: 10, assetKey: 'table:1:orders', typeName: 'table', name, matchedColumnCount: 2 }],
  total: 1,
  typeFacets: [{ typeName: 'table', count: 1 }],
  from: 0,
  size: 20,
  ...extra,
});
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

beforeEach(() => {
  jest.clearAllMocks();
  window.history.replaceState(null, '', '/data-asset/catalog?view=entity');
  jest
    .mocked(listEntityTypes)
    .mockReset()
    .mockResolvedValue([
      { id: 1, typeName: 'table', displayName: '物理表', category: 'PHYSICAL', collectible: true, status: 'ACTIVE' },
      {
        id: 2,
        typeName: 'tableColumn',
        displayName: '物理列',
        category: 'PHYSICAL',
        collectible: true,
        status: 'ACTIVE',
      },
      {
        id: 3,
        typeName: 'customType',
        displayName: '自定义实体',
        category: 'BUSINESS',
        collectible: false,
        status: 'ACTIVE',
      },
    ]);
  sources.mockReset().mockResolvedValue({ bizData: [{ id: 1, name: '订单库' }] } as any);
  search.mockReset().mockResolvedValue(result('订单主表'));
  jest.mocked(getEntityDetail).mockReset();
});

it('submits the search and removes its chip to restore browsing', async () => {
  render(<AssetExplorer searchable />);
  await screen.findByRole('button', { name: '订单主表' });
  fireEvent.change(screen.getByRole('textbox', { name: '搜索目录' }), { target: { value: '订单' } });
  fireEvent.click(screen.getByRole('button', { name: '搜索' }));
  await waitFor(() => expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ q: '订单', from: 0 })));
  fireEvent.click(screen.getByRole('button', { name: '移除筛选：关键词：订单' }));
  await waitFor(() => expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ q: '', from: 0 })));
});

it('clears the parent restriction when switching away from matched columns', async () => {
  render(<AssetExplorer searchable />);
  fireEvent.click(await screen.findByRole('button', { name: '命中 2 列' }));
  await waitFor(() =>
    expect(search).toHaveBeenLastCalledWith(
      expect.objectContaining({ index: ['tableColumn'], queryFilter: { parentAssetId: 10 } }),
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: '自定义实体' }));
  await waitFor(() => {
    const params = search.mock.calls.at(-1)?.[0];
    expect(params?.index).toContain('customType');
    expect(params?.queryFilter).not.toHaveProperty('parentAssetId');
  });
});

it('rejects a late response from the previous search', async () => {
  const old = deferred<ReturnType<typeof result>>();
  search.mockReset().mockReturnValueOnce(old.promise).mockResolvedValueOnce(result('新查询结果'));
  render(<AssetExplorer searchable />);
  fireEvent.change(screen.getByRole('textbox', { name: '搜索目录' }), { target: { value: '新查询' } });
  fireEvent.click(screen.getByRole('button', { name: '搜索' }));
  await screen.findByRole('button', { name: '新查询结果' });
  await act(async () => old.resolve(result('过期结果')));
  expect(screen.queryByText('过期结果')).not.toBeInTheDocument();
});

it('shows the failed read and recovers on retry without reporting an empty catalog', async () => {
  search.mockReset().mockRejectedValueOnce(new Error('服务不可用')).mockResolvedValueOnce(result('恢复结果'));
  render(<AssetExplorer searchable />);
  expect(await screen.findByRole('alert')).toHaveTextContent('服务不可用');
  expect(screen.queryByText('目录里还没有数据')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await screen.findByRole('button', { name: '恢复结果' });
});

it('revalidates a source deep link after its owner listing fails', async () => {
  window.history.replaceState(null, '', '/data-asset/catalog?view=entity&datasourceId=1');
  sources
    .mockReset()
    .mockRejectedValueOnce(new Error('来源读取失败'))
    .mockResolvedValueOnce({ bizData: [{ id: 1, name: '订单库' }] } as any);
  render(<AssetExplorer searchable />);
  await screen.findByRole('alert');
  expect(search).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await waitFor(() =>
    expect(search).toHaveBeenCalledWith(
      expect.objectContaining({ queryFilter: { datasourceId: '1', providerType: 'HARVESTED' } }),
    ),
  );
});

it('keeps deep-link detail failures visible when catalog search succeeds', async () => {
  window.history.replaceState(null, '', '/data-asset/catalog?view=entity&entityId=7');
  jest.mocked(getEntityDetail).mockRejectedValueOnce(new Error('实体读取失败'));
  render(<AssetExplorer searchable />);
  await screen.findByRole('button', { name: '订单主表' });
  expect(await screen.findByRole('alert')).toHaveTextContent('无法加载目标元数据实体');
});

it('uses the next cursor and disables next page at the last result', async () => {
  search
    .mockReset()
    .mockResolvedValueOnce(result('第一页', { total: 21, nextSearchAfter: 'cursor-2' }))
    .mockResolvedValueOnce(result('最后一页', { total: 21, from: 20 }));
  render(<AssetExplorer searchable />);
  await screen.findByRole('button', { name: '第一页' });
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await screen.findByRole('button', { name: '最后一页' });
  expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ searchAfter: 'cursor-2', from: undefined }));
  expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled();
});
