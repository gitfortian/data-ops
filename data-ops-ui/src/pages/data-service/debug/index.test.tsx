import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history, useLocation } from '@umijs/max';
import { message } from 'antd';
import type { ReactNode } from 'react';
import {
  getDataServiceDocumentation,
  listDataServices,
  testDataService,
  type DataServiceApi,
  type DataServiceDocumentation,
  type DataServiceQueryResult,
} from '@/services/data-service';
import DataServiceDebugPage from './index';

let mockProjectId = 42;
let mockPermissionCodes = ['data-service:debug'];
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: mockPermissionCodes }),
}));
jest.mock('@/services/data-service', () => ({
  getDataServiceDocumentation: jest.fn(),
  listDataServices: jest.fn(),
  testDataService: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
  useLocation: jest.fn(),
  history: { replace: jest.fn() },
}));

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  const Empty = Object.assign(
    ({ children, description }: { children?: ReactNode; description?: ReactNode }) =>
      element('div', null, description, children),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    Alert: ({ message, description }: { message: ReactNode; description: ReactNode }) =>
      element('div', { role: 'alert' }, message, description),
    Button: ({ children, disabled, loading, onClick }: {
      children: ReactNode;
      disabled?: boolean;
      loading?: boolean;
      onClick?: () => void;
    }) => element('button', { disabled: disabled || loading, onClick }, children),
    Empty,
    Input: ({ value, placeholder, onChange }: {
      value?: string;
      placeholder?: string;
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
    }) => element('input', { value, placeholder, onChange }),
    Select: ({ options, value, onChange }: {
      options: Array<{ value: number; label: string }>;
      value?: number;
      onChange: (value: number) => void;
    }) => element('select', {
      'data-testid': 'api-select',
      value: value ?? '',
      onChange: (event: React.ChangeEvent<HTMLSelectElement>) =>
        onChange(Number(event.target.value)),
    }, [
      element('option', { value: '', key: 'empty' }, '请选择 API'),
      ...options.map(option => element('option', {
        key: option.value, value: option.value,
      }, option.label)),
    ]),
    Spin: () => element('span', { 'data-testid': 'debug-spinner' }, '加载中'),
    message: { error: jest.fn(), warning: jest.fn() },
  };
});

const service = (id: number, name: string, enabled: boolean): DataServiceApi => ({
  id,
  name,
  path: '/api/' + id,
  runtimePath: '/api/data-service/' + id,
  dataSourceId: 4,
  sql: 'SELECT * FROM orders',
  parameterNames: ['orderId'],
  maxRows: 100,
  timeoutSeconds: 30,
  enabled,
  authMode: 'API_KEY',
});

const docs = (apiId: number, example: string, required = true): DataServiceDocumentation => ({
  apiId,
  name: 'Orders API',
  runtimePath: '/api/data-service/' + apiId,
  authMode: 'API_KEY',
  documented: true,
  schemaStale: false,
  parameters: [{
    name: 'orderId',
    type: 'INTEGER',
    required,
    description: '订单 ID',
    example,
  }],
  responseFields: [],
});

const resultRow: DataServiceQueryResult = {
  columns: ['id'],
  rows: [{ id: 42 }],
  rowCount: 1,
  durationMs: 23,
  truncated: false,
};

describe('Data Service debug page modern data-only API migration', () => {
  const list = jest.mocked(listDataServices);
  const getDoc = jest.mocked(getDataServiceDocumentation);
  const run = jest.mocked(testDataService);
  const route = jest.mocked(useLocation);
  const replace = jest.mocked(history.replace);
  const error = jest.mocked(message.error);
  const warning = jest.mocked(message.warning);

  beforeEach(() => {
    mockProjectId = 42;
    mockPermissionCodes = ['data-service:debug'];
    list.mockReset();
    getDoc.mockReset();
    run.mockReset();
    replace.mockReset();
    error.mockReset();
    warning.mockReset();
    route.mockReturnValue({ search: '' } as never);
    list.mockResolvedValue([service(11, 'Orders API', true), service(22, 'Revenue API', false)]);
    getDoc.mockImplementation(async id => docs(id, id === 11 ? '42' : '88'));
    run.mockResolvedValue(resultRow);
  });

  it('selects enabled API and initializes documentation parameter examples from data-only responses', async () => {
    render(<DataServiceDebugPage />);

    await waitFor(() => expect(screen.getByDisplayValue('42')).toBeInTheDocument());
    expect(list).toHaveBeenCalledTimes(1);
    expect(getDoc).toHaveBeenCalledWith(11);
    expect(screen.getByTestId('api-select')).toHaveValue('11');
    expect(screen.getByText('运行中')).toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith('/data-service/debug?apiId=11');
    expect(error).not.toHaveBeenCalled();
  });

  it('honors an explicit API ID and supports changing API while updating its parameters', async () => {
    route.mockReturnValue({ search: '?apiId=22' } as never);
    render(<DataServiceDebugPage />);

    await waitFor(() => expect(screen.getByDisplayValue('88')).toBeInTheDocument());
    expect(screen.getByTestId('api-select')).toHaveValue('22');
    expect(replace).not.toHaveBeenCalled();

    fireEvent.change(screen.getByTestId('api-select'), { target: { value: '11' } });
    await waitFor(() => expect(screen.getByDisplayValue('42')).toBeInTheDocument());
    expect(getDoc).toHaveBeenCalledWith(11);
    expect(replace).toHaveBeenCalledWith('/data-service/debug?apiId=11');
  });

  it('prevents missing required parameters from executing a debug query', async () => {
    getDoc.mockResolvedValue(docs(11, ''));
    render(<DataServiceDebugPage />);
    await waitFor(() => expect(screen.getByPlaceholderText('请输入 orderId')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    expect(warning).toHaveBeenCalledWith('请输入参数 orderId');
    expect(run).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText('请输入 orderId'), {
      target: { value: '75' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    await waitFor(() => expect(run).toHaveBeenCalledWith(11, { orderId: '75' }));
  });

  it('posts debug parameters and renders unwrapped result columns, rows and summary', async () => {
    render(<DataServiceDebugPage />);
    await waitFor(() => expect(screen.getByDisplayValue('42')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));

    await waitFor(() => expect(screen.getByText('200 OK')).toBeInTheDocument());
    expect(run).toHaveBeenCalledWith(11, { orderId: '42' });
    expect(screen.getByText('23 ms')).toBeInTheDocument();
    expect(screen.getByText('1 行')).toBeInTheDocument();
    expect(screen.getByText(/"truncated": false/)).toBeInTheDocument();
    expect(error).not.toHaveBeenCalled();
  });

  it('does not treat a null or missing test result as successful', async () => {
    run.mockResolvedValue(null as never);
    render(<DataServiceDebugPage />);
    await waitFor(() => expect(screen.getByDisplayValue('42')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));

    await waitFor(() => expect(error).toHaveBeenCalledWith('调试失败'));
    expect(screen.queryByText('200 OK')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始测试' })).not.toBeDisabled();
  });

  it('preserves server error messages for failed debug requests', async () => {
    run.mockRejectedValue(new Error('执行被拒绝: 无权限'));
    render(<DataServiceDebugPage />);
    await waitFor(() => expect(screen.getByDisplayValue('42')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));

    await waitFor(() => expect(error).toHaveBeenCalledWith('执行被拒绝: 无权限'));
    expect(screen.queryByText('200 OK')).not.toBeInTheDocument();
  });

  it('keeps empty successful API lists without fetching documentation', async () => {
    list.mockResolvedValue([]);
    render(<DataServiceDebugPage />);

    await waitFor(() => expect(screen.getByText('API 测试')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '开始测试' })).toBeDisabled();
    expect(getDoc).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  it('preserves the API-list loading failure feedback', async () => {
    list.mockRejectedValue(new Error('API 列表读取失败'));
    render(<DataServiceDebugPage />);

    await waitFor(() => expect(error).toHaveBeenCalledWith('API 列表读取失败'));
    await waitFor(() => expect(screen.getByText('API 测试')).toBeInTheDocument());
    expect(getDoc).not.toHaveBeenCalled();
  });

  it('resets documentation and parameters after its request fails', async () => {
    getDoc.mockRejectedValue(new Error('文档读取失败'));
    render(<DataServiceDebugPage />);

    await waitFor(() => expect(error).toHaveBeenCalledWith('文档读取失败'));
    expect(screen.getByRole('alert')).toHaveTextContent('API 参数文档读取失败');
    expect(screen.queryByText('当前 API 无请求参数')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('42')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始测试' })).toBeDisabled();
  });

  it('does not show a previous API invocation as a successful result after switching API', async () => {
    let finish!: (value: DataServiceQueryResult) => void;
    const old = new Promise<DataServiceQueryResult>(resolve => { finish = resolve; });
    run.mockReturnValueOnce(old).mockResolvedValueOnce({ ...resultRow, rowCount: 8 });
    render(<DataServiceDebugPage />);
    await screen.findByDisplayValue('42');
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    await waitFor(() => expect(run).toHaveBeenCalledWith(11, { orderId: '42' }));
    fireEvent.change(screen.getByTestId('api-select'), { target: { value: '22' } });
    await screen.findByDisplayValue('88');
    await act(async () => { finish(resultRow); await old; });
    expect(screen.queryByText('200 OK')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    await waitFor(() => expect(screen.getByText('8 行')).toBeInTheDocument());
    expect(run).toHaveBeenLastCalledWith(22, { orderId: '88' });
  });

  it('ignores stale debug results after parameter edits within one API', async () => {
    let finish!: (value: DataServiceQueryResult) => void;
    const old = new Promise<DataServiceQueryResult>(resolve => { finish = resolve; });
    run.mockReturnValueOnce(old);
    render(<DataServiceDebugPage />);
    await screen.findByDisplayValue('42');
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    fireEvent.change(screen.getByDisplayValue('42'), { target: { value: '77' } });
    await act(async () => { finish(resultRow); await old; });
    expect(screen.queryByText('200 OK')).not.toBeInTheDocument();
    expect(screen.getByDisplayValue('77')).toBeInTheDocument();
  });

  it('remounts API selection and drops an older Project invocation response', async () => {
    let finish!: (value: DataServiceQueryResult) => void;
    const old = new Promise<DataServiceQueryResult>(resolve => { finish = resolve; });
    run.mockReturnValueOnce(old);
    const view = render(<DataServiceDebugPage />);
    await screen.findByDisplayValue('42');
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));
    mockProjectId = 43;
    view.rerender(<DataServiceDebugPage />);
    await screen.findByDisplayValue('42');
    await act(async () => { finish(resultRow); await old; });
    expect(screen.queryByText('200 OK')).not.toBeInTheDocument();
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('ignores an outdated documentation response after the selected API changes', async () => {
    let finish!: (value: DataServiceDocumentation) => void;
    const pending = new Promise<DataServiceDocumentation>(resolve => { finish = resolve; });
    getDoc.mockImplementation(id => id === 11
      ? pending : Promise.resolve(docs(22, '88')));
    render(<DataServiceDebugPage />);
    await waitFor(() => expect(getDoc).toHaveBeenCalledWith(11));
    fireEvent.change(screen.getByTestId('api-select'), { target: { value: '22' } });
    await waitFor(() => expect(screen.getByDisplayValue('88')).toBeInTheDocument());

    await act(async () => {
      finish(docs(11, 'old'));
      await pending;
    });
    expect(screen.getByDisplayValue('88')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('old')).not.toBeInTheDocument();
  });
});
