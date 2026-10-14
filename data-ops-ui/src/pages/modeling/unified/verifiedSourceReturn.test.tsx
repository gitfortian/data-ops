import React from 'react';
// JSDOM's nwsapi cannot parse some Tailwind-generated CSS selectors while
// Testing Library computes accessible roles. Label/text queries still
// exercise the real buttons, onClick handlers and the draft-confirm contract.
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import UnifiedModelView from './index';

const mockPush = jest.fn();
const mockReplace = jest.fn();
const mockGetModel = jest.fn();
let mockSearch = '?origin=live-source&datasourceId=18&database=trade_db&schema=public&table=trade_order';
let mockEditorValue = '';

jest.mock('@umijs/max', () => ({
  history: { push: (...args: unknown[]) => mockPush(...args),
    replace: (...args: unknown[]) => mockReplace(...args) },
  useParams: () => ({ id: '701' }),
  useLocation: () => ({ pathname: '/modeling/models/701', search: mockSearch }),
}));

jest.mock('@/components/ui', () => ({
  YakButton: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
    <button type="button" {...props}>{children}</button>,
  YakEmpty: () => <div>Empty</div>,
}));
jest.mock('@/pages/modeling/detail', () => {
  const ReactLib = require('react');
  const TestStructureEditor = ({ onEditorStateChange }: {
    onEditorStateChange?: (state: { dirty: boolean; saving: boolean; loading: boolean }) => void;
  }) => {
    const [value, setValue] = ReactLib.useState('server');
    const dirty = value !== 'server';
    ReactLib.useEffect(() => {
      onEditorStateChange?.({ dirty, saving: false, loading: false });
    }, [dirty, onEditorStateChange]);
    return <div>
      <input aria-label="结构草稿" value={value}
        onChange={(e) => { mockEditorValue = e.target.value; setValue(e.target.value); }} />
      <button type="button" onClick={() => setValue('server')}>模拟保存成功</button>
    </div>;
  };
  return { __esModule: true, default: TestStructureEditor };
});
jest.mock('@/pages/modeling/components/ModelLineagePanel', () => () => null);
jest.mock('@/pages/modeling/components/ModelVersionPanel', () => () => null);
jest.mock('@/pages/data-lifecycle/components/ModelLifecycleTab', () => () => null);
jest.mock('@/pages/modeling/layer-mapping', () => () => null);
jest.mock('@/pages/modeling/mapping', () => () => null);
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: (...args: unknown[]) => mockGetModel(...args),
}));
jest.mock('@/services/data-source/api', () => ({
  listDataSources: jest.fn().mockResolvedValue({ bizData: [] }),
}));
jest.mock('@/services/semantic/api', () => ({
  listSemanticLayers: jest.fn().mockResolvedValue([]),
}));

const model = {
  id: 701, layerCode: 'ODS', name: 'Order ODS',
  code: 'ods_trade_order', status: 'DRAFT',
  sourceDatasourceId: 18, sourceDatabase: 'trade_db',
  sourceSchema: 'public', sourceTable: 'trade_order',
};
let pendingConfirm: Parameters<typeof Modal.confirm>[0] | undefined;

beforeEach(() => {
  jest.clearAllMocks();
  mockSearch = '?origin=live-source&datasourceId=18&database=trade_db&schema=public&table=trade_order';
  mockEditorValue = '';
  pendingConfirm = undefined;
  mockGetModel.mockResolvedValue(model);
});

afterEach(() => { jest.restoreAllMocks(); });

it('keeps every dirty structure draft edit when source return is cancelled, and leaves only after explicit discard', async () => {
  jest.spyOn(Modal, 'confirm').mockImplementation((options) => {
    pendingConfirm = options;
    return { destroy: jest.fn(), update: jest.fn() };
  });
  render(<UnifiedModelView />);
  fireEvent.change(await screen.findByLabelText('结构草稿'), {
    target: { value: 'unsaved-field-primary-key-index' },
  });
  expect((await screen.findByText('返回来源表')).closest('button')).not.toBeNull();
  fireEvent.click(screen.getByText('返回来源表'));
  expect(pendingConfirm?.cancelText).toBe('继续编辑');
  expect(pendingConfirm?.okText).toMatch(/放弃未保存/);
  expect(mockPush).not.toHaveBeenCalled();

  // Simulate choosing "continue editing" rather than calling the destructive onOk.
  await act(async () => { pendingConfirm?.onCancel?.(); });
  // Cancelling is the default: no navigation or edits are discarded.
  expect(screen.getByLabelText('结构草稿')).toHaveValue('unsaved-field-primary-key-index');
  expect(mockEditorValue).toBe('unsaved-field-primary-key-index');
  expect(mockPush).not.toHaveBeenCalled();

  await act(async () => { await pendingConfirm?.onOk?.(jest.fn()); });
  expect(mockPush).toHaveBeenCalledTimes(1);
  const returned = mockPush.mock.calls[0][0] as string;
  expect(returned.startsWith('/data-source?')).toBe(true);
  expect(new URLSearchParams(returned.split('?')[1]).get('table')).toBe('trade_order');
});

it('returns directly after successful structure save clears the dirty state', async () => {
  const confirm = jest.spyOn(Modal, 'confirm');
  render(<UnifiedModelView />);
  fireEvent.change(await screen.findByLabelText('结构草稿'), {
    target: { value: 'unsaved-column' },
  });
  fireEvent.click(screen.getByText('模拟保存成功'));
  await waitFor(() => expect(screen.getByLabelText('结构草稿')).toHaveValue('server'));
  fireEvent.click(screen.getByText('返回来源表'));
  expect(confirm).not.toHaveBeenCalled();
  expect(mockPush).toHaveBeenCalledTimes(1);
});

it('does not offer a return action when persisted ODS source differs from URL', async () => {
  mockGetModel.mockResolvedValue({ ...model, sourceSchema: 'audit' });
  render(<UnifiedModelView />);
  await screen.findByText('Order ODS');
  expect(screen.queryByText('返回来源表')).not.toBeInTheDocument();
});

it('does not silently discard the structure editor when switching to logical evidence', async () => {
  jest.spyOn(Modal, 'confirm').mockImplementation((options) => {
    pendingConfirm = options;
    return { destroy: jest.fn(), update: jest.fn() };
  });
  render(<UnifiedModelView />);
  fireEvent.change(await screen.findByLabelText('结构草稿'), {
    target: { value: 'unsaved-business-key' },
  });
  fireEvent.click(screen.getByText('业务逻辑依据'));
  expect(pendingConfirm?.okText).toBe('放弃修改并继续');
  expect(pendingConfirm?.cancelText).toBe('继续编辑');
  expect(mockReplace).not.toHaveBeenCalled();
  expect(screen.getByLabelText('结构草稿')).toHaveValue('unsaved-business-key');
  await act(async () => { pendingConfirm?.onCancel?.(); });
  expect(mockReplace).not.toHaveBeenCalled();
  await act(async () => { await pendingConfirm?.onOk?.(jest.fn()); });
  expect(mockReplace).toHaveBeenCalledTimes(1);
  const destination = mockReplace.mock.calls[0][0] as { search: string };
  expect(new URLSearchParams(destination.search).get('tab')).toBe('logical');
});
