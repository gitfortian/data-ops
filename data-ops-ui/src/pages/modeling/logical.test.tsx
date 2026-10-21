import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal, message } from 'antd';
import type { LogicalDraftDetail, LogicalVersionSemanticReview } from '@/services/modeling/logical';
import { logicalSample } from '../../../tests/fixtures/modelingLogical';
import LogicalWorkspace from './logical';

let mockProjectId = 1;
const mockGet = jest.fn();
const mockUpdate = jest.fn();
const mockList = jest.fn();
const mockProcesses = jest.fn();
const mockFields = jest.fn();
const mockVersions = jest.fn();
const mockFreeze = jest.fn();
const mockSnapshot = jest.fn();
const mockReview = jest.fn();
jest.mock('@umijs/max', () => ({ useSearchParams: () => [new URLSearchParams()], history: { push: jest.fn() } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ can: () => true }) }));
jest.mock('@/services/modeling/api', () => ({ pageModelingModels: jest.fn().mockResolvedValue({ bizData: [] }) }));
jest.mock('@/services/semantic/api', () => ({
  pageSemanticProcesses: (...args: unknown[]) => mockProcesses(...args),
  listSemanticProcessFields: (...args: unknown[]) => mockFields(...args),
}));
jest.mock('@/services/modeling/logical', () => ({
  listLogicalDrafts: (...args: unknown[]) => mockList(...args),
  getLogicalDraft: (...args: unknown[]) => mockGet(...args),
  listLogicalVersions: (...args: unknown[]) => mockVersions(...args),
  freezeLogicalDraft: (...args: unknown[]) => mockFreeze(...args),
  getLogicalVersionSnapshot: (...args: unknown[]) => mockSnapshot(...args),
  reviewLogicalVersionSemantics: (...args: unknown[]) => mockReview(...args),
  updateLogicalEntity: (...args: unknown[]) => mockUpdate(...args),
}));
jest.mock('./components/LogicalDesignStudio', () => ({ __esModule: true,
  default: ({ detail, onEditEntity, onEditAttribute }: { detail: LogicalDraftDetail; onEditEntity: (id: number) => void; onEditAttribute: (entityId: number) => void }) => <div>
    <span>工作区项目{detail.model.projectId}:{detail.entities[0].entity.businessName}</span>
    <button type="button" onClick={() => onEditEntity(11)}>编辑测试实体</button>
    <button type="button" onClick={() => onEditAttribute(11)}>添加测试属性</button>
  </div>,
}));

beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  mockList.mockImplementation(() => Promise.resolve([logicalSample(mockProjectId).model]));
  mockProcesses.mockResolvedValue({ bizData: [logicalSample().process] });
  mockFields.mockResolvedValue([]);
  mockVersions.mockResolvedValue([]);
  mockGet.mockImplementation(() => Promise.resolve(logicalSample(mockProjectId)));
});
afterEach(() => { Modal.destroyAll(); message.destroy(); });

const openEditedEntity = async () => {
  fireEvent.click(await screen.findByText('编辑测试实体'));
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
  expect(await screen.findByText('工作区项目1:服务端实际保存的名称')).toBeInTheDocument();
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
  await screen.findByText('工作区项目2:订单');
  const oldReceipt = logicalSample(); oldReceipt.entities[0].entity.businessName = '旧项目晚响应';
  await act(async () => resolve(oldReceipt));
  expect(screen.getByText('工作区项目2:订单')).toBeInTheDocument();
  expect(screen.queryByText(/旧项目晚响应/)).not.toBeInTheDocument();
});

it('keeps the authorized catalog when one draft cannot be read and permits a scoped retry', async () => {
  mockGet.mockRejectedValueOnce(new Error('暂时不可读取'));
  render(<LogicalWorkspace />);
  await screen.findByText('草稿 #10 暂时无法读取');
  expect(screen.getByRole('button', { name: /订单逻辑设计/ })).toBeEnabled();
  fireEvent.click(screen.getByText('重新读取草稿'));
  expect(await screen.findByText('工作区项目1:订单')).toBeInTheDocument();
});

it('keeps a failed standards read visible in the attribute editor', async () => {
  mockFields.mockRejectedValue(new Error('标准服务暂不可用'));
  render(<LogicalWorkspace />);
  fireEvent.click(await screen.findByRole('button', { name: '添加测试属性' }));
  const dialog = within(await screen.findByRole('dialog', { name: '新增业务属性' }));
  expect(await dialog.findByText('标准字段读取失败')).toBeInTheDocument();
  expect(dialog.getByText('标准服务暂不可用')).toBeInTheDocument();
  expect(dialog.getByRole('button', { name: '前往业务过程' })).toBeEnabled();
});

it('saves a design version once and ignores its receipt after changing projects', async () => {
  let resolve!: (value: unknown) => void;
  mockFreeze.mockReturnValue(new Promise(done => { resolve = done; }));
  const view = render(<LogicalWorkspace />);
  await screen.findByText('工作区项目1:订单');
  const saveVersion = screen.getByRole('button', { name: '保存设计版本' });
  fireEvent.click(saveVersion);
  fireEvent.click(saveVersion);
  await waitFor(() => expect(mockFreeze).toHaveBeenCalledTimes(1));
  expect(mockFreeze).toHaveBeenCalledWith(10, 3);
  mockProjectId = 2;
  view.rerender(<LogicalWorkspace />);
  await screen.findByText('工作区项目2:订单');
  await act(async () => resolve({ id: 301, versionNo: 8, status: 'DRAFT' }));
  expect(mockVersions).toHaveBeenCalledTimes(2);
  expect(screen.queryByText('V8')).not.toBeInTheDocument();
});

it('does not open an old snapshot after switching projects', async () => {
  mockVersions.mockResolvedValue([{ id: 301, versionNo: 1, status: 'DRAFT' }]);
  let resolve!: (value: string) => void;
  mockSnapshot.mockReturnValue(new Promise<string>(done => { resolve = done; }));
  const view = render(<LogicalWorkspace />);
  fireEvent.click(await screen.findByRole('button', { name: '设计内容' }));
  await waitFor(() => expect(mockSnapshot).toHaveBeenCalledWith(10, 1));
  mockProjectId = 2;
  view.rerender(<LogicalWorkspace />);
  await screen.findByText('工作区项目2:订单');
  await act(async () => resolve('旧项目的设计快照'));
  expect(screen.queryByRole('dialog', { name: '设计版本内容' })).not.toBeInTheDocument();
});

it('does not reopen an editor when the user closes the report during a repair read', async () => {
  mockVersions.mockResolvedValue([{ id: 301, versionNo: 1, status: 'DRAFT' }]);
  const report: LogicalVersionSemanticReview = {
    logicalModelId: 10, versionNo: 1, frozenStatus: 'DRAFT', draftStatus: 'DRAFT',
    frozenSnapshotSha256: 'a'.repeat(64), frozenDesignSha256: 'b'.repeat(64), currentDesignSha256: 'b'.repeat(64),
    entityCount: 3, attributeCount: 2, relationCount: 2, designComparison: 'MATCHES_CURRENT',
    reviewStatus: 'NEEDS_REVIEW', changes: [], relations: [], blockers: [], limitations: [],
    attributes: [{ entityId: 11, entityName: '订单', attributeId: 102, attributeName: '实付金额',
      standardStatus: 'PENDING_STANDARD', explanation: '请关联标准字段' }],
  };
  mockReview.mockResolvedValue(report);
  render(<LogicalWorkspace />);
  fireEvent.click(await screen.findByRole('button', { name: '检查标准与差异' }));
  const reportDialog = within(await screen.findByRole('dialog', { name: '设计版本核对' }));
  let resolve!: (value: LogicalDraftDetail) => void;
  mockGet.mockReturnValueOnce(new Promise<LogicalDraftDetail>(done => { resolve = done; }));
  fireEvent.click(reportDialog.getByRole('button', { name: '定位当前属性' }));
  await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(2));
  fireEvent.click(reportDialog.getByRole('button', { name: /^关\s*闭$/ }));
  await act(async () => resolve(logicalSample()));
  expect(screen.queryByRole('dialog', { name: '编辑业务属性' })).not.toBeInTheDocument();
  expect(screen.queryByRole('dialog', { name: '设计版本核对' })).not.toBeInTheDocument();
});
