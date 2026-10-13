import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import CatalogPage from './index';
import { pageAssets, getDirectoryTree } from '@/services/data-asset/api';

let mockProject = 1;
let mockPermissions = ['data-asset:read', 'data-asset:update'];
let mockEntry = '';
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useSearchParams: () => {
    const React = require('react');
    const [params, setParams] = React.useState(new URLSearchParams(mockEntry));
    return [params, setParams];
  },
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProject } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({
    can: (code: string) => mockPermissions.includes(code),
    permissionCodes: mockPermissions,
  }),
}));
jest.mock('@/services/data-asset/api', () => ({
  pageAssets: jest.fn(),
  getDirectoryTree: jest.fn(),
  listAssetTags: jest.fn().mockResolvedValue([]),
  batchMoveAssetsDirectory: jest.fn(),
  deleteAsset: jest.fn(),
  ignoreAssets: jest.fn(),
}));
jest.mock('@/services/semantic/api', () => ({ listSemanticLayers: jest.fn().mockResolvedValue([]) }));
jest.mock('@/components/metadata/AssetExplorer', () => () => <div>技术实体结果</div>);
jest.mock('../components/ManualRegisterModal', () => () => null);
jest.mock('../components/OfflineModal', () => () => null);
jest.mock('../components/PublishPrecheckModal', () => () => null);
jest.mock('@/components/ui', () => ({
  YakEmpty: ({ title, children }: any) => (
    <div>
      {title}
      {children}
    </div>
  ),
}));
jest.mock('antd', () => ({ ...jest.requireActual('antd'), Grid: { useBreakpoint: () => ({ sm: true, md: true }) } }));
jest.mock('@/components/ReadableTable', () => ({
  __esModule: true,
  default: ({ dataSource, columns, rowSelection }: any) => (
    <div>
      {dataSource.map((item: any) => (
        <div key={item.id}>
          {rowSelection && <button onClick={() => rowSelection.onChange([item.id], [item])}>选择 {item.name}</button>}
          {columns.map((column: any, index: number) => (
            <div key={index}>{column.render?.(item[column.dataIndex], item)}</div>
          ))}
        </div>
      ))}
    </div>
  ),
}));
const page = jest.mocked(pageAssets);
const result = (name: string) => ({
  records: [
    {
      id: 1,
      name,
      assetKey: 'table:1:db.' + name,
      sourceType: 'METADATA' as const,
      status: 'PENDING' as const,
      assetType: 'TABLE' as const,
    },
  ],
  total: 1,
  pageNo: 1,
  pageSize: 20,
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
  mockProject = 1;
  mockEntry = '';
  mockPermissions = ['data-asset:read', 'data-asset:update'];
  page.mockReset().mockResolvedValue(result('订单台账'));
  jest.mocked(getDirectoryTree).mockReset().mockResolvedValue([]);
});

it('opens entity deep links without making hidden ledger and directory requests', async () => {
  mockEntry = 'view=entity';
  render(<CatalogPage />);
  expect(screen.getByText('技术实体结果')).toBeInTheDocument();
  expect(page).not.toHaveBeenCalled();
  expect(getDirectoryTree).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '资产台账' }));
  await screen.findByRole('link', { name: '订单台账' });
  expect(getDirectoryTree).toHaveBeenCalledTimes(1);
});

it('clears the selection and rejects stale reads when a new filter is submitted', async () => {
  render(<CatalogPage />);
  await screen.findByRole('link', { name: '订单台账' });
  fireEvent.click(screen.getByRole('button', { name: '选择 订单台账' }));
  expect(screen.getByText('已选 1 项')).toBeInTheDocument();
  const old = deferred<ReturnType<typeof result>>();
  page.mockReturnValueOnce(old.promise).mockResolvedValueOnce(result('新结果'));
  fireEvent.change(screen.getByRole('textbox', { name: '搜索目录' }), { target: { value: '第一次' } });
  fireEvent.click(screen.getByRole('button', { name: '搜索' }));
  expect(screen.queryByText('已选 1 项')).not.toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox', { name: '搜索目录' }), { target: { value: '第二次' } });
  fireEvent.click(screen.getByRole('button', { name: '搜索' }));
  await screen.findByRole('link', { name: '新结果' });
  await act(async () => old.resolve(result('旧结果')));
  expect(screen.queryByText('旧结果')).not.toBeInTheDocument();
});

it('discards records from an old project or role and hides selection without update permission', async () => {
  const old = deferred<ReturnType<typeof result>>();
  page
    .mockReset()
    .mockReturnValueOnce(old.promise)
    .mockResolvedValueOnce(result('当前项目'))
    .mockResolvedValueOnce(result('当前权限'));
  const view = render(<CatalogPage />);
  mockProject = 2;
  view.rerender(<CatalogPage />);
  await screen.findByRole('link', { name: '当前项目' });
  await act(async () => old.resolve(result('旧项目')));
  expect(screen.queryByText('旧项目')).not.toBeInTheDocument();
  mockPermissions = ['data-asset:read'];
  view.rerender(<CatalogPage />);
  await screen.findByRole('link', { name: '当前权限' });
  expect(screen.queryByRole('button', { name: '选择 当前权限' })).not.toBeInTheDocument();
});

it('preserves entry status filters and removes them with the visible chip', async () => {
  mockEntry = 'statuses=SOURCE_GONE&grades=D';
  render(<CatalogPage />);
  await waitFor(() =>
    expect(page).toHaveBeenCalledWith(expect.objectContaining({ statuses: ['SOURCE_GONE'], grades: ['D'] })),
  );
  fireEvent.click(screen.getByRole('button', { name: '移除筛选：源已消失' }));
  await waitFor(() =>
    expect(page).toHaveBeenLastCalledWith(expect.objectContaining({ statuses: undefined, grades: ['D'] })),
  );
});
