import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { getLogicalDraft, listLogicalDrafts, listLogicalVersions } from '@/services/modeling/logical';
import ModelCreationLogicalBasis from './ModelCreationLogicalBasis';

let mockProject = 1;
const mockCan = jest.fn((_permission: string) => true);
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: mockProject ? { id: mockProject } : undefined }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({ can: mockCan }),
}));
jest.mock('@/services/modeling/logical', () => ({
  listLogicalDrafts: jest.fn(), listLogicalVersions: jest.fn(), getLogicalDraft: jest.fn(),
}));
const draft = { id: 71, projectId: 1, processId: 15, domainId: 1,
  name: '订单逻辑设计', code: 'trade_order', status: 'DRAFT' as const, draftRevision: 2 };

beforeEach(() => {
  jest.clearAllMocks();
  mockProject = 1;
  mockCan.mockReturnValue(true);
  jest.mocked(listLogicalDrafts).mockResolvedValue([draft]);
  jest.mocked(listLogicalVersions).mockResolvedValue([{ id: 50, versionNo: 3, status: 'DRAFT' }]);
  jest.mocked(getLogicalDraft).mockResolvedValue({ model: draft, process: { id: 15 } as any, entities: [], relations: [] });
});

it('stays out of the form before the target warehouse layer is selected', () => {
  const { container } = render(<ModelCreationLogicalBasis processId={15} onOpenLogical={jest.fn()} />);
  expect(container).toBeEmptyDOMElement();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
});

it('never queries a logical draft for source-to-ODS creation', () => {
  const { container } = render(<ModelCreationLogicalBasis layer="ODS" processId={15} onOpenLogical={jest.fn()} />);
  expect(container).toBeEmptyDOMElement();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
});

it('read-only roles do not request or mislabel an inaccessible logical catalog as empty', () => {
  mockCan.mockReturnValue(false);
  render(<ModelCreationLogicalBasis layer="DWD" processId={15} onOpenLogical={jest.fn()} />);
  expect(screen.getByText('无法读取当前项目逻辑设计依据')).toBeInTheDocument();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
});

it('shows the missing business process explicitly without manufacturing an approved logical version', () => {
  render(<ModelCreationLogicalBasis layer="DIM" onOpenLogical={jest.fn()} />);
  expect(screen.getByText('关联业务过程后，可查看对应的逻辑设计。')).toBeInTheDocument();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
});

it('only reads drafts for the current process and offers the original logical workspace', async () => {
  const open = jest.fn();
  render(<ModelCreationLogicalBasis layer="DWD" processId={15} onOpenLogical={open} />);
  await waitFor(() => expect(listLogicalDrafts).toHaveBeenCalledTimes(1));
  expect(await screen.findByText('逻辑设计参考')).toBeInTheDocument();
  fireEvent.click(screen.getByText('查看逻辑模型'));
  expect(open).toHaveBeenCalledWith(15);
  expect(listLogicalVersions).not.toHaveBeenCalled(); // no implicit frozen version selection
});

it('separates catalog read failure from the legitimate no-draft state', async () => {
  jest.mocked(listLogicalDrafts).mockRejectedValueOnce(new Error('revoked'));
  render(<ModelCreationLogicalBasis layer="DWD" processId={15} onOpenLogical={jest.fn()} />);
  expect(await screen.findByText('逻辑草稿目录暂时无法读取')).toBeInTheDocument();
  expect(screen.queryByText('该业务过程暂无逻辑模型')).not.toBeInTheDocument();
});

it('offers a direct next step from an empty process', async () => {
  jest.mocked(listLogicalDrafts).mockResolvedValueOnce([]);
  const open = jest.fn();
  render(<ModelCreationLogicalBasis layer="DWD" processId={15} onOpenLogical={open} />);
  expect(await screen.findByText('该业务过程暂无逻辑模型')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '创建逻辑模型' }));
  expect(open).toHaveBeenCalledWith(15);
});

it('can retry a failed version read without reselecting the logical model', async () => {
  jest.mocked(listLogicalVersions).mockRejectedValueOnce(new Error('temporary read failure'));
  render(<ModelCreationLogicalBasis layer="DWD" processId={15} onOpenLogical={jest.fn()} />);
  const chooser = screen.getByRole('combobox', { name: '逻辑模型参考' });
  await waitFor(() => expect(chooser).toBeEnabled());
  fireEvent.mouseDown(chooser);
  fireEvent.click(await screen.findByText('订单逻辑设计 · trade_order'));
  expect(await screen.findByText('版本读取失败')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取版本' }));
  expect(await screen.findByText('设计版本 V3')).toBeInTheDocument();
  expect(listLogicalVersions).toHaveBeenCalledTimes(2);
  expect(listLogicalDrafts).toHaveBeenCalledTimes(1);
});
