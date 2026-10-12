import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { message } from 'antd';
import { discoverProducts, getProduct, subscribeToProduct } from '@/services/consumption';
import { loadConsumptionRelationships } from './relationship-load';
import Discovery from './index';
import Detail from './detail';

let mockProjectId: number | undefined = 1;
let mockPermissions = new Set(['data-asset:read', 'data-asset:update']);
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: mockProjectId ? { id: mockProjectId } : undefined }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({
    can: (code: string) => mockPermissions.has(code), permissionCodes: [...mockPermissions],
  }),
}));
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useParams: () => ({ productKey: 'DATASET:1' }),
  useSearchParams: () => [new URLSearchParams()],
  useModel: () => ({ initialState: { currentUser: { userName: 'tester' } } }),
}));
jest.mock('@/services/consumption', () => ({
  discoverProducts: jest.fn(), getProduct: jest.fn(),
  subscribeToProduct: jest.fn(), cancelSubscription: jest.fn(),
  suspendSubscription: jest.fn(), resumeSubscription: jest.fn(),
  productKeyValue: (key: { productType: string; sourceIdentity: string }) =>
    `${key.productType}:${key.sourceIdentity}`,
}));
jest.mock('./relationship-load', () => ({ loadConsumptionRelationships: jest.fn() }));
jest.mock('@/services/data-service/consumer', () => ({ listDataServiceConsumers: jest.fn() }));
jest.mock('./VersionChangeImpactReview', () => () => null);
jest.mock('@/components/YakOpsEmpty', () => ({ children, title }: any) => <div>{title}{children}</div>);
jest.mock('antd', () => {
  const Box = ({ children }: any) => <div>{children}</div>;
  const Descriptions = Object.assign(Box, { Item: Box });
  return {
    Alert: ({ message, description, action }: any) => <div><div>{message}</div><div>{description}</div>{action}</div>,
    Button: ({ children, onClick, disabled }: any) => <button disabled={disabled} onClick={onClick}>{children}</button>,
    Card: ({ title, children, onClick }: any) => <div onClick={onClick}>{title}{children}</div>,
    Descriptions, Empty: Box, Popconfirm: Box, Popover: Box, Space: Box, Spin: Box,
    Tag: Box, Tooltip: Box, Table: () => null,
    Typography: { Title: Box, Paragraph: Box, Text: Box },
    Result: ({ title, subTitle }: any) => <div><div>{title}</div><div>{subTitle}</div></div>,
    Input: { Search: ({ value, onChange, onSearch }: any) =>
      <input aria-label="搜索" value={value} onChange={onChange}
        onKeyDown={event => { if (event.key === 'Enter') onSearch(event.currentTarget.value); }} /> },
    Select: ({ value, onChange, options }: any) =>
      <select aria-label="产品类型" value={value || ''} onChange={event => onChange(event.target.value || undefined)}>
        <option value="">全部</option>
        {options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>,
    message: { error: jest.fn(), success: jest.fn() },
  };
});

const product = (name: string, projectId = mockProjectId) => ({
  productKey: { productType: 'DATASET', sourceIdentity: '1' },
  name, description: '', projectId, lifecycle: 'PUBLISHED', availability: 'AVAILABLE',
  access: { providerState: 'READY', decision: 'ALLOWED' },
  activeVersion: { identity: '1', displayVersion: 'V1' },
  contractPayload: { columns: [] }, endpoints: [],
});
const discoveryResult = (name: string) => ({
  products: [product(name)],
  providerStates: { DATASET: 'READY', DATA_SERVICE: 'READY' },
});
const lookup = (name: string) => ({ state: 'FOUND', product: product(name) });
const deferred = () => {
  let resolve!: (value: unknown) => void;
  const promise = new Promise(resolveValue => { resolve = resolveValue; });
  return { promise, resolve };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 1;
  mockPermissions = new Set(['data-asset:read', 'data-asset:update']);
  jest.mocked(discoverProducts).mockResolvedValue(discoveryResult('Current product') as never);
  jest.mocked(getProduct).mockResolvedValue(lookup('Current product') as never);
  jest.mocked(loadConsumptionRelationships).mockResolvedValue({
    impact: null, impactIssue: '', subscriptions: [], subscriptionIssue: '',
  });
});

test('clearing the type filter actually queries all product types', async () => {
  render(<Discovery />);
  await screen.findByText('Current product');
  fireEvent.change(screen.getByLabelText('产品类型'), { target: { value: 'DATASET' } });
  await waitFor(() => expect(discoverProducts).toHaveBeenLastCalledWith(expect.objectContaining({ productType: 'DATASET' })));
  fireEvent.change(screen.getByLabelText('产品类型'), { target: { value: '' } });
  await waitFor(() => expect(discoverProducts).toHaveBeenLastCalledWith(expect.objectContaining({ productType: undefined })));
});

test('discovery retry uses the submitted filter rather than unsent edits', async () => {
  render(<Discovery />);
  await screen.findByText('Current product');
  jest.mocked(discoverProducts).mockRejectedValueOnce(new Error('provider unavailable'));
  fireEvent.change(screen.getByLabelText('搜索'), { target: { value: 'orders' } });
  fireEvent.keyDown(screen.getByLabelText('搜索'), { key: 'Enter' });
  await screen.findByText('provider unavailable');
  fireEvent.change(screen.getByLabelText('搜索'), { target: { value: 'unsent edit' } });
  fireEvent.click(screen.getByText('按当前筛选重试'));
  await waitFor(() => expect(discoverProducts).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'orders' })));
});

test('old discovery responses cannot populate the new project', async () => {
  const old = deferred();
  jest.mocked(discoverProducts).mockImplementationOnce(() => old.promise as never);
  const { rerender } = render(<Discovery />);
  mockProjectId = 2;
  jest.mocked(discoverProducts).mockResolvedValue(discoveryResult('Project B') as never);
  rerender(<Discovery />);
  await screen.findByText('Project B');
  await act(async () => { old.resolve(discoveryResult('Project A')); });
  expect(screen.queryByText('Project A')).not.toBeInTheDocument();
  expect(screen.getByText('Project B')).toBeInTheDocument();
});

test('revoking discovery permission removes visible data and does not fetch again', async () => {
  const { rerender } = render(<Discovery />);
  await screen.findByText('Current product');
  mockPermissions.delete('data-asset:read');
  rerender(<Discovery />);
  expect(screen.queryByText('Current product')).not.toBeInTheDocument();
  expect(screen.getByText('暂无数据产品发现权限')).toBeInTheDocument();
  expect(discoverProducts).toHaveBeenCalledTimes(1);
});

test('a failed detail can recover on the same product without navigating away', async () => {
  jest.mocked(getProduct).mockRejectedValueOnce(new Error('source unavailable'));
  render(<Detail />);
  await screen.findByText('source unavailable');
  fireEvent.click(screen.getByText('重新读取数据产品'));
  await screen.findByText('Current product');
  expect(getProduct).toHaveBeenCalledTimes(2);
  expect(getProduct).toHaveBeenLastCalledWith('DATASET:1');
});

test('product refresh re-reads contract and relationships', async () => {
  render(<Detail />);
  await screen.findByText('Current product');
  await waitFor(() => expect(loadConsumptionRelationships).toHaveBeenCalledTimes(1));
  jest.mocked(getProduct).mockResolvedValueOnce(lookup('Updated contract') as never);
  fireEvent.click(screen.getByText('刷新契约与访问状态'));
  await screen.findByText('Updated contract');
  expect(screen.queryByText('Current product')).not.toBeInTheDocument();
  await waitFor(() => expect(loadConsumptionRelationships).toHaveBeenCalledTimes(2));
});

test('old detail responses cannot start relationship reads in a new project', async () => {
  const old = deferred();
  jest.mocked(getProduct).mockImplementationOnce(() => old.promise as never);
  const { rerender } = render(<Detail />);
  mockProjectId = 2;
  jest.mocked(getProduct).mockResolvedValue(lookup('Project B') as never);
  rerender(<Detail />);
  await screen.findByText('Project B');
  await waitFor(() => expect(loadConsumptionRelationships).toHaveBeenCalledTimes(1));
  await act(async () => { old.resolve(lookup('Project A')); });
  expect(screen.queryByText('Project A')).not.toBeInTheDocument();
  expect(loadConsumptionRelationships).toHaveBeenCalledTimes(1);
});

test('subscription double clicks submit once; a late receipt cannot refresh a new project', async () => {
  const pending = deferred();
  jest.mocked(subscribeToProduct).mockImplementationOnce(() => pending.promise as never);
  const { rerender } = render(<Detail />);
  await screen.findByText('Current product');
  const button = screen.getByText('声明QUERY依赖');
  fireEvent.click(button);
  fireEvent.click(button);
  expect(subscribeToProduct).toHaveBeenCalledTimes(1);
  mockProjectId = 2;
  jest.mocked(getProduct).mockResolvedValue(lookup('Project B') as never);
  rerender(<Detail />);
  await screen.findByText('Project B');
  await waitFor(() => expect(loadConsumptionRelationships).toHaveBeenCalledTimes(2));
  await act(async () => { pending.resolve({ id: 'subscription', status: 'ACTIVE' }); });
  expect(loadConsumptionRelationships).toHaveBeenCalledTimes(2);
  expect(message.success).not.toHaveBeenCalled();
});

test('without a project neither discovery nor detail reads a default scope', () => {
  mockProjectId = undefined;
  const { unmount } = render(<Discovery />);
  expect(discoverProducts).not.toHaveBeenCalled();
  unmount();
  render(<Detail />);
  expect(getProduct).not.toHaveBeenCalled();
});
