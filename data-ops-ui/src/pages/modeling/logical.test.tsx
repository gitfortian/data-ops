import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal, message } from 'antd';
import type { LogicalDraftDetail } from '@/services/modeling/logical';
import { logicalSample } from '../../../tests/fixtures/modelingLogical';
import LogicalWorkspace from './logical';

let mockProjectId = 1;
const mockGet = jest.fn();
const mockUpdate = jest.fn();
const mockList = jest.fn();
const mockProcesses = jest.fn();
jest.mock('@umijs/max', () => ({ useSearchParams: () => [new URLSearchParams()], history: { push: jest.fn() } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ can: () => true }) }));
jest.mock('@/services/modeling/api', () => ({ pageModelingModels: jest.fn().mockResolvedValue({ bizData: [] }) }));
jest.mock('@/services/semantic/api', () => ({
  pageSemanticProcesses: (...args: unknown[]) => mockProcesses(...args),
  listSemanticProcessFields: jest.fn().mockResolvedValue([]),
}));
jest.mock('@/services/modeling/logical', () => ({
  listLogicalDrafts: (...args: unknown[]) => mockList(...args),
  getLogicalDraft: (...args: unknown[]) => mockGet(...args),
  listLogicalVersions: jest.fn().mockResolvedValue([]),
  updateLogicalEntity: (...args: unknown[]) => mockUpdate(...args),
}));
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  mockList.mockImplementation(() => Promise.resolve([logicalSample(mockProjectId).model]));
  mockProcesses.mockResolvedValue({ bizData: [logicalSample().process] });
  mockGet.mockImplementation(() => Promise.resolve(logicalSample(mockProjectId)));
});
afterEach(() => { Modal.destroyAll(); message.destroy(); });

const openEditedEntity = async () => {
  fireEvent.click((await screen.findAllByRole('button', { name: '编辑实体' }))[0]);
  const dialog = within(await screen.findByRole('dialog', { name: '编辑业务实体' }));
  fireEvent.change(dialog.getByLabelText('业务名称'), { target: { value: '用户正在编辑的名称' } });
  return dialog;
};

it('submits once with the loaded revision and uses the owner receipt as the new design', async () => {
  let resolve!: (value: LogicalDraftDetail) => void;
  mockUpdate.mockReturnValue(new Promise<LogicalDraftDetail>(done => { resolve = done; }));
  render(<LogicalWorkspace />);
  const dialog = await openEditedEntity();
  fireEvent.click(dialog.getByRole('button', { name: '保存实体' }));
  fireEvent.click(dialog.getByRole('button', { name: '保存实体' }));
  await waitFor(() => expect(mockUpdate).toHaveBeenCalledTimes(1));
  expect(mockUpdate).toHaveBeenCalledWith(10, 11, expect.objectContaining({ businessName: '用户正在编辑的名称' }), 3);
  const receipt = logicalSample(); receipt.model.draftRevision = 4;
  receipt.entities[0].entity.businessName = '服务端实际保存的名称';
  await act(async () => resolve(receipt));
  expect(await screen.findByText('服务端实际保存的名称')).toBeInTheDocument();
  expect(screen.queryByRole('dialog', { name: '编辑业务实体' })).not.toBeInTheDocument();
});

it('retains edited input after a revision conflict and protects cancel until explicit discard', async () => {
  mockUpdate.mockRejectedValue(new Error('修订已变化，请重新核对'));
  render(<LogicalWorkspace />);
  const dialog = await openEditedEntity();
  fireEvent.click(dialog.getByRole('button', { name: '保存实体' }));
  await dialog.findByText('修订已变化，请重新核对');
  expect(dialog.getByLabelText('业务名称')).toHaveValue('用户正在编辑的名称');
  fireEvent.click(dialog.getByRole('button', { name: /^(取\s*消|Cancel)$/ }));
  await screen.findByText('离开会放弃当前表单的修改。已保存的草稿和冻结版本不受影响。');
  fireEvent.click(screen.getByRole('button', { name: '继续编辑' }));
  await waitFor(() => expect(screen.queryByText('离开会放弃当前表单的修改。已保存的草稿和冻结版本不受影响。')).not.toBeInTheDocument());
  expect(dialog.getByLabelText('业务名称')).toHaveValue('用户正在编辑的名称');
});

it('never applies a previous project save receipt to the new project workspace', async () => {
  let resolve!: (value: LogicalDraftDetail) => void;
  mockUpdate.mockReturnValue(new Promise<LogicalDraftDetail>(done => { resolve = done; }));
  const view = render(<LogicalWorkspace />);
  const dialog = await openEditedEntity();
  fireEvent.click(dialog.getByRole('button', { name: '保存实体' }));
  await waitFor(() => expect(mockUpdate).toHaveBeenCalledTimes(1));
  mockProjectId = 2;
  view.rerender(<LogicalWorkspace />);
  await screen.findAllByRole('button', { name: '编辑实体' });
  const oldReceipt = logicalSample(); oldReceipt.entities[0].entity.businessName = '旧项目晚响应';
  await act(async () => resolve(oldReceipt));
  expect(screen.getAllByRole('button', { name: '编辑实体' }).length).toBeGreaterThan(0);
  expect(screen.queryByText(/旧项目晚响应/)).not.toBeInTheDocument();
});

it('keeps the authorized catalog when one draft cannot be read and permits a scoped retry', async () => {
  mockGet.mockRejectedValueOnce(new Error('暂时不可读取'));
  render(<LogicalWorkspace />);
  await screen.findByText('草稿 #10 暂时无法读取');
  expect(screen.getByRole('button', { name: /订单逻辑设计/ })).toBeEnabled();
  fireEvent.click(screen.getByText('重新读取草稿'));
  expect((await screen.findAllByRole('button', { name: '编辑实体' })).length).toBeGreaterThan(0);
});
