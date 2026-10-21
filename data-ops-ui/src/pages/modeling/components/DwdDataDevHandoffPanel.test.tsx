import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import { inspectPhysicalDataDevHandoff, listModelingVersions, type PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';
import { getPublishedDwdMapping, type DwdPublishedMapping } from '@/services/modeling/dwdMapping';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import PhysicalDataDevHandoffPanel from './PhysicalDataDevHandoffPanel';

let mockProjectId = 1;
let mockCanRead = true;
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ canAll: () => mockCanRead }) }));
jest.mock('@/services/modeling/api', () => ({ listModelingVersions: jest.fn(), inspectPhysicalDataDevHandoff: jest.fn() }));
jest.mock('@/services/modeling/dwdMapping', () => ({ getPublishedDwdMapping: jest.fn() }));
const versions = [{ id: 90, modelId: 50, versionNo: 2, columnCount: 2 }];
const readiness: PhysicalDataDevHandoffReadiness = {
  modelId: 50, modelCode: 'dwd_orders', layerCode: 'DWD', modelStatus: 'PUBLISHED',
  version: { modelId: 50, versionId: 90, versionNo: 2, frozenStructureSha256: 'b'.repeat(64) },
  currentStructureSha256: 'b'.repeat(64), currentStandardEvidenceSha256: 'c'.repeat(64),
  selectedLatestVersion: true, designStatus: 'DESIGN_REVIEWABLE', dataDevStatus: 'NOT_AUTHORIZED_FOR_TASK_CREATION',
  frozenColumnCount: 2, currentColumnCount: 2, frozenStandardRefCount: 2, frozenUnboundColumnCount: 0,
  dependencies: [{ code: 'EXACT_MODEL_VERSION', status: 'PINNED', explanation: '精确版本记录' },
    { code: 'TARGET_ENVIRONMENT', status: 'NOT_CHECKED', explanation: '由开发工作台核对目标环境' }],
  blockers: [], nextActions: ['核对环境和执行身份'], inspectionSha256: 'd'.repeat(64),
};
const binding: DwdPublishedMapping = {
  modelVersionId: 90, versionNo: 2, state: 'DESIGN_PUBLISHED_NOT_EXECUTED', mapping: {
    mappingRevisionId: 81, mappingSetId: 71, revisionNo: 3, logicalModelId: 10, logicalVersionNo: 1,
    logicalSnapshotSha256: 'a'.repeat(64), physicalStructureSha256: 'b'.repeat(64), contentSha256: 'c'.repeat(64),
    rules: [{ targetColumn: 'order_id', logicalAttributeId: 101, stdFieldId: 201, kind: 'DIRECT' }],
  },
};
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1; mockCanRead = true;
  jest.mocked(listModelingVersions).mockResolvedValue(versions as ModelingVersionSummary[]);
  jest.mocked(inspectPhysicalDataDevHandoff).mockResolvedValue(readiness);
  jest.mocked(getPublishedDwdMapping).mockResolvedValue(binding);
});
const renderDwd = (processId: number | undefined = 7) => render(<PhysicalDataDevHandoffPanel layerCode="DWD" modelId={50} processId={processId} open onClose={jest.fn()} />);

it('summarizes a DWD version and carries only the inspected identity to the development workspace', async () => {
  renderDwd();
  expect(await screen.findByText('映射已核对')).toBeInTheDocument();
  expect(screen.getByText('待开发核对')).toBeInTheDocument();
  expect(screen.queryByText(binding.mapping!.contentSha256)).not.toBeInTheDocument();
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '进入开发工作台' }));
  expect(history.push).toHaveBeenCalledWith('/data-development?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=2');
  expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledWith(50, 2);
  expect(getPublishedDwdMapping).toHaveBeenCalledWith(50, 2);
});

it('keeps a failed mapping read distinct from a legacy version and retries in place', async () => {
  jest.mocked(getPublishedDwdMapping).mockRejectedValueOnce(new Error('映射服务暂不可用'));
  renderDwd();
  expect(await screen.findByText('映射记录读取失败')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '进入开发工作台' })).toBeDisabled();
  expect(screen.queryByText(/未记录逻辑映射/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
  expect(await screen.findByText('映射已核对')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '进入开发工作台' })).toBeEnabled();
});

it('blocks handoff for a mismatched exact version and for a legacy unbound version', async () => {
  jest.mocked(getPublishedDwdMapping).mockResolvedValueOnce({ ...binding, modelVersionId: 999 });
  const view = renderDwd();
  expect(await screen.findByText('设计版本与映射不一致')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '进入开发工作台' })).toBeDisabled();
  view.unmount();
  jest.mocked(getPublishedDwdMapping).mockResolvedValueOnce({ ...binding, state: 'LEGACY_UNBOUND', mapping: null });
  renderDwd();
  expect(await screen.findByText('设计版本 V2 未记录逻辑映射')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '进入开发工作台' })).toBeDisabled();
  expect(history.push).not.toHaveBeenCalled();
});

it('does not reuse old inspection data after switching projects', async () => {
  let finish!: (value: PhysicalDataDevHandoffReadiness) => void;
  jest.mocked(inspectPhysicalDataDevHandoff).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValue({ ...readiness, modelCode: '新项目设计' });
  const view = renderDwd();
  await waitFor(() => expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledTimes(1));
  mockProjectId = 2;
  view.rerender(<PhysicalDataDevHandoffPanel layerCode="DWD" modelId={50} processId={7} open onClose={jest.fn()} />);
  expect(await screen.findByText('新项目设计')).toBeInTheDocument();
  await act(async () => finish({ ...readiness, modelCode: '旧项目设计' }));
  expect(screen.queryByText('旧项目设计')).not.toBeInTheDocument();
  expect(screen.getByText('新项目设计')).toBeInTheDocument();
});

it('keeps catalog failures recoverable and does not read without modeling access', async () => {
  jest.mocked(listModelingVersions).mockRejectedValueOnce(new Error('版本服务暂不可用'));
  const view = renderDwd();
  expect(await screen.findByText('发布版本读取失败')).toBeInTheDocument();
  expect(screen.queryByText('暂无已发布的 DWD 设计版本')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
  expect(await screen.findByText('映射已核对')).toBeInTheDocument();
  view.unmount();
  jest.clearAllMocks(); mockCanRead = false;
  renderDwd();
  expect(screen.getByText('无法读取当前项目的模型版本')).toBeInTheDocument();
  expect(listModelingVersions).not.toHaveBeenCalled();
});

it('requires a resolved business process to continue', async () => {
  render(<PhysicalDataDevHandoffPanel layerCode="DWD" modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('映射已核对')).toBeInTheDocument();
  expect(screen.getByText('请先在模型基本信息中关联业务过程。')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '进入开发工作台' })).toBeDisabled();
});
