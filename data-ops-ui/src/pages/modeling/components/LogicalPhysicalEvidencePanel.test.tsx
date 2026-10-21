import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  getLogicalDraft, listLogicalDrafts, listLogicalVersions, previewLogicalPhysical,
} from '@/services/modeling/logical';
import LogicalPhysicalEvidencePanel from './LogicalPhysicalEvidencePanel';

const mockCan = jest.fn((_p: string) => true);
let mockProjectId: number | undefined = 1;
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({ can: mockCan }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: mockProjectId ? { id: mockProjectId } : undefined }),
}));
jest.mock('@/services/modeling/logical', () => ({
  listLogicalDrafts: jest.fn(), getLogicalDraft: jest.fn(),
  listLogicalVersions: jest.fn(), previewLogicalPhysical: jest.fn(),
}));

const physical = { id: 701, layerCode: 'DWD', processId: 29, name: '订单 DWD' };
const model = { id: 71, name: '订单业务', code: 'trade_order', processId: 29, projectId: 1, domainId: 2, draftRevision: 1, status: 'DRAFT' as const };
const versions = [{ id: 99, versionNo: 3, status: 'DRAFT' as const }];
const sha = 'a'.repeat(64);
const hint = '?tab=logical&processId=29&logicalId=71&logicalVersion=3';
const evidence = {
  logicalModelId: 71, logicalVersionNo: 3, physicalModelId: 701,
  physicalLayer: 'DWD', physicalStatus: 'DRAFT', readyForReview: true,
  logicalSnapshotSha256: sha, physicalStructureSha256: 'b'.repeat(64),
  blockers: [], columns: [{ physicalColumnId: 501, physicalColumn: 'order_id',
    logicalEntity: 'Order', logicalAttribute: '订单号', result: 'CANDIDATE', reason: '标准字段一致' }],
};
beforeEach(() => {
  jest.clearAllMocks();
  mockCan.mockReturnValue(true);
  mockProjectId = 1;
  jest.mocked(listLogicalDrafts).mockResolvedValue([model]);
  jest.mocked(getLogicalDraft).mockResolvedValue({
    model, process: { id: 29 } as any, entities: [], relations: [],
  });
  jest.mocked(listLogicalVersions).mockResolvedValue(versions);
  jest.mocked(previewLogicalPhysical).mockResolvedValue(evidence);
});

it('loads an exact same-process frozen logical version and previews physical evidence without writing', async () => {
  const back = jest.fn();
  render(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={back} />);
  await waitFor(() => expect(listLogicalVersions).toHaveBeenCalledWith(71));
  expect(getLogicalDraft).toHaveBeenCalledWith(71);
  const previewButton = await screen.findByText('开始对照');
  await waitFor(() => expect(previewButton.closest('button')).not.toBeDisabled());
  fireEvent.click(previewButton);
  expect(await screen.findByText('V3 对照结果')).toBeInTheDocument();
  expect(await screen.findByText('order_id')).toBeInTheDocument();
  expect(previewLogicalPhysical).toHaveBeenCalledWith(71, 3, 701);
  fireEvent.click(screen.getByText('打开逻辑模型'));
  expect(back).toHaveBeenCalledWith('/modeling/logical?processId=29&draftId=71&review=physical');
});

it('rejects a foreign process hint without silently choosing another draft', async () => {
  render(<LogicalPhysicalEvidencePanel model={physical}
    search="?tab=logical&processId=30&logicalId=71&logicalVersion=3" onNavigateLogical={jest.fn()} />);
  expect(await screen.findByText('指定逻辑模型不可用，请重新选择')).toBeInTheDocument();
  expect(getLogicalDraft).not.toHaveBeenCalled();
  expect(previewLogicalPhysical).not.toHaveBeenCalled();
});

it('rejects a stale frozen version that does not exist on the owner', async () => {
  render(<LogicalPhysicalEvidencePanel model={physical}
    search="?tab=logical&processId=29&logicalId=71&logicalVersion=10" onNavigateLogical={jest.fn()} />);
  expect(await screen.findByText('该设计版本不可用，请重新选择')).toBeInTheDocument();
  expect(previewLogicalPhysical).not.toHaveBeenCalled();
});

it('rejects mismatched evidence instead of labeling a different model as ready', async () => {
  jest.mocked(previewLogicalPhysical).mockResolvedValueOnce({ ...evidence, physicalModelId: 702 });
  render(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={jest.fn()} />);
  await waitFor(() => expect(listLogicalVersions).toHaveBeenCalledWith(71));
  const previewButton = await screen.findByText('开始对照');
  await waitFor(() => expect(previewButton.closest('button')).not.toBeDisabled());
  fireEvent.click(previewButton);
  expect(await screen.findByText(/模型或版本信息已变化/)).toBeInTheDocument();
  expect(screen.queryByText('V3 对照结果')).not.toBeInTheDocument();
});

it('does not read logical evidence without Modeling READ or for an ODS model', () => {
  mockCan.mockReturnValue(false);
  const { unmount } = render(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={jest.fn()} />);
  expect(screen.getByText('暂无当前项目的建模查看权限')).toBeInTheDocument();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
  unmount();
  mockCan.mockReturnValue(true);
  render(<LogicalPhysicalEvidencePanel model={{ ...physical, layerCode: 'ODS' }} search={hint} onNavigateLogical={jest.fn()} />);
  expect(screen.getByText('该分层无需逻辑模型对照')).toBeInTheDocument();
  expect(listLogicalDrafts).not.toHaveBeenCalled();
});

it('provides a next step when the current process has no logical model', async () => {
  jest.mocked(listLogicalDrafts).mockResolvedValueOnce([]);
  const navigate = jest.fn();
  render(<LogicalPhysicalEvidencePanel model={physical} search="" onNavigateLogical={navigate} />);
  fireEvent.click(await screen.findByRole('button', { name: '前往逻辑建模' }));
  expect(navigate).toHaveBeenCalledWith('/modeling/logical?processId=29');
  expect(previewLogicalPhysical).not.toHaveBeenCalled();
});

it('offers recovery when a logical model has no saved design version', async () => {
  jest.mocked(listLogicalVersions).mockResolvedValueOnce([]);
  const navigate = jest.fn();
  render(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={navigate} />);
  expect(await screen.findByText(/该模型还没有设计版本/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '开始对照' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '打开逻辑模型' }));
  expect(navigate).toHaveBeenCalledWith('/modeling/logical?processId=29&draftId=71&review=physical');
});

it('ignores a late comparison response after the project changes', async () => {
  let finish: (value: typeof evidence) => void = () => {};
  jest.mocked(previewLogicalPhysical).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={jest.fn()} />);
  const button = await screen.findByRole('button', { name: '开始对照' });
  await waitFor(() => expect(button).not.toBeDisabled());
  fireEvent.click(button);
  mockProjectId = 2;
  view.rerender(<LogicalPhysicalEvidencePanel model={physical} search={hint} onNavigateLogical={jest.fn()} />);
  await act(async () => finish(evidence));
  expect(screen.queryByText('V3 对照结果')).not.toBeInTheDocument();
  expect(screen.queryByText('order_id')).not.toBeInTheDocument();
});
