import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import PhysicalDataDevHandoffPanel from './PhysicalDataDevHandoffPanel';
import { inspectPhysicalDataDevHandoff, listModelingVersions } from '@/services/modeling/api';

let mockProjectId = 1;
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({ canAll: () => true }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/services/modeling/api', () => ({
  listModelingVersions: jest.fn(),
  inspectPhysicalDataDevHandoff: jest.fn(),
}));
jest.mock('antd', () => {
  const real = jest.requireActual('antd');
  return { ...real, Table: ({ dataSource }: {
    dataSource: { code: string; status: string }[];
  }) => <div>{dataSource.map(row =>
    <div key={row.code}>{row.code} : {row.status}</div>)}</div> };
});

const report = {
  modelId: 50, modelCode: 'dwd_orders', layerCode: 'DWD', modelStatus: 'PUBLISHED',
  version: { modelId: 50, versionId: 90, versionNo: 2,
    frozenStructureSha256: 'a'.repeat(64) },
  currentStructureSha256: 'b'.repeat(64),
  currentStandardEvidenceSha256: 'c'.repeat(64),
  selectedLatestVersion: true, designStatus: 'DESIGN_REVIEWABLE',
  dataDevStatus: 'NOT_AUTHORIZED_FOR_TASK_CREATION',
  frozenColumnCount: 2, currentColumnCount: 2,
  frozenStandardRefCount: 2, frozenUnboundColumnCount: 0,
  dependencies: [
    { code: 'EXACT_MODEL_VERSION', status: 'PINNED', explanation: '精确版本' },
    { code: 'DATADEV_PROVENANCE', status: 'NOT_APPROVED', explanation: '尚未批准' },
    { code: 'TARGET_ENVIRONMENT', status: 'NOT_CHECKED', explanation: 'DataDev 核验' },
  ],
  blockers: [], nextActions: ['由 DataDev 核验环境与授权'],
  inspectionSha256: 'd'.repeat(64),
};

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 1;
  (listModelingVersions as jest.Mock).mockResolvedValue([{ versionNo: 2, columnCount: 2 }]);
  (inspectPhysicalDataDevHandoff as jest.Mock).mockResolvedValue(report);
});

it('shows frozen version and non-authorized DataDev facts without issuing task creation', async () => {
  render(<PhysicalDataDevHandoffPanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText(/DATADEV_PROVENANCE : NOT_APPROVED/)).toBeInTheDocument();
  expect(screen.getByText(/TARGET_ENVIRONMENT : NOT_CHECKED/)).toBeInTheDocument();
  expect(screen.getByText(/不是 DataDev 建任务许可/)).toBeInTheDocument();
  expect(screen.getByText(/由 DataDev 核验环境与授权/)).toBeInTheDocument();
  expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledWith(50, 2);
});

it('discards stale cross-project request results', async () => {
  let finish: (value: unknown) => void = () => {};
  (inspectPhysicalDataDevHandoff as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    // Project changes re-fetch version list before repeating inspection. Both
    // the immediate and the version-list-driven checks must return NEW scope.
    .mockResolvedValue({ ...report, dependencies: [
      { code: 'PROJECT_SCOPE', status: 'NOT_CHECKED', explanation: '新项目审阅' },
    ] });
  const view = render(<PhysicalDataDevHandoffPanel modelId={50} open onClose={jest.fn()} />);
  await waitFor(() => expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledTimes(1));
  mockProjectId = 2;
  view.rerender(<PhysicalDataDevHandoffPanel modelId={50} open onClose={jest.fn()} />);
  await waitFor(() => expect((inspectPhysicalDataDevHandoff as jest.Mock).mock.calls.length).toBeGreaterThanOrEqual(2));
  await act(async () => finish(report));
  expect(await screen.findByText('PROJECT_SCOPE : NOT_CHECKED')).toBeInTheDocument();
  expect(screen.queryByText('DATADEV_PROVENANCE : NOT_APPROVED')).not.toBeInTheDocument();
});

it('only forwards a real inspected version and process to DataDev as a read-only hint', async () => {
  render(<PhysicalDataDevHandoffPanel modelId={50} processId={7} open onClose={jest.fn()} />);
  expect(await screen.findByText(/DATADEV_PROVENANCE : NOT_APPROVED/)).toBeInTheDocument();
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '前往 DataDev 核对该冻结版本（只读）' }));
  expect(history.push).toHaveBeenCalledWith(
    '/data-development?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=2',
  );
});

it('never offers a DataDev handoff button without a resolved owning business process', async () => {
  render(<PhysicalDataDevHandoffPanel modelId={50} open onClose={jest.fn()} />);
  await screen.findByText(/DATADEV_PROVENANCE : NOT_APPROVED/);
  expect(screen.getByRole('button', { name: '前往 DataDev 核对该冻结版本（只读）' }))
    .toBeDisabled();
  expect(history.push).not.toHaveBeenCalled();
});
