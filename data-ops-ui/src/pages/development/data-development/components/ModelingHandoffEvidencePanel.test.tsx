import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import ModelingHandoffEvidencePanel from './ModelingHandoffEvidencePanel';
import {
  getModelingModel, inspectPhysicalDataDevHandoff, listModelingVersions,
} from '@/services/modeling/api';
import { getSemanticProcess } from '@/services/semantic/api';
import { getPublishedDwdMapping } from '@/services/modeling/dwdMapping';
import { getDevelopmentTaskDraft } from '@/services/data-development';

let mockProjectId = 1;
let mockAllowed = true;
const mockCanAll = () => mockAllowed;
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true,
  default: () => ({ canAll: mockCanAll }),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: jest.fn(),
  inspectPhysicalDataDevHandoff: jest.fn(),
  listModelingVersions: jest.fn(),
}));
jest.mock('@/services/semantic/api', () => ({ getSemanticProcess: jest.fn() }));
jest.mock('@/services/modeling/dwdMapping', () => ({ getPublishedDwdMapping: jest.fn() }));
jest.mock('@/services/data-development', () => ({ getDevelopmentTaskDraft: jest.fn() }));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Table: ({ dataSource }: {
    dataSource: { code: string; status: string }[];
  }) => <div>{dataSource.map(row => <div key={row.code}>
    {row.code} : {row.status}
  </div>)}</div> };
});

const url = '?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=3';
const owner = { id: 50, code: 'dwd_orders', name: 'DWD订单',
  processId: 7, layerCode: 'DWD', status: 'PUBLISHED' };
const version = { id: 99, modelId: 50, versionNo: 3,
  columnCount: 2, checksum: 'x'.repeat(64), publishTime: '2026-10-11' };
const report = {
  modelId: 50, modelCode: 'dwd_orders', layerCode: 'DWD',
  modelStatus: 'PUBLISHED',
  version: { modelId: 50, versionId: 99, versionNo: 3,
    frozenStructureSha256: 'a'.repeat(64) },
  inspectionSha256: 'b'.repeat(64),
  selectedLatestVersion: false, designStatus: 'DESIGN_NEEDS_REVIEW',
  dataDevStatus: 'NOT_AUTHORIZED_FOR_TASK_CREATION',
  blockers: ['未确认目标 Doris 环境'],
  dependencies: [{ code: 'DATADEV_PROVENANCE', status: 'NOT_APPROVED',
    explanation: '尚无持久版本引用合同' }],
  nextActions: [],
};
const boundMapping = {
  modelVersionId: 99, versionNo: 3, state: 'DESIGN_PUBLISHED_NOT_EXECUTED',
  mapping: {
    mappingRevisionId: 701, mappingSetId: 801, revisionNo: 2,
    logicalModelId: 77, logicalVersionNo: 4,
    logicalSnapshotSha256: 'c'.repeat(64),
    physicalStructureSha256: 'd'.repeat(64),
    contentSha256: 'e'.repeat(64),
    rules: [{ targetColumn: 'order_id', logicalAttributeId: 11,
      stdFieldId: 12, kind: 'DIRECT' }],
  },
};

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 1;
  mockAllowed = true;
  (getModelingModel as jest.Mock).mockResolvedValue(owner);
  (getSemanticProcess as jest.Mock).mockResolvedValue({ id: 7, name: '下单' });
  (listModelingVersions as jest.Mock).mockResolvedValue([version]);
  (inspectPhysicalDataDevHandoff as jest.Mock).mockResolvedValue(report);
  (getPublishedDwdMapping as jest.Mock).mockResolvedValue(boundMapping);
  (getDevelopmentTaskDraft as jest.Mock).mockResolvedValue({
    nodeId: 'sql-1', draftRevision: 5,
    definition: { taskType: 'SQL', schemaVersion: 1,
      content: 'select 1', configJson: '{}' },
  });
});

it('keeps normal DataDev use unchanged without Modeling handoff', () => {
  const view = render(<ModelingHandoffEvidencePanel search="?nodeId=sql-1"
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(view.container).toBeEmptyDOMElement();
  expect(getModelingModel).not.toHaveBeenCalled();
});

it('shows owner-verified frozen version and unresolved original DataDev blockers', async () => {
  const focus = jest.fn();
  render(<ModelingHandoffEvidencePanel search={url} nodes={[]} onFocusNode={focus} />);
  expect(await screen.findByText(/DATADEV_PROVENANCE : NOT_APPROVED/)).toBeInTheDocument();
  expect(screen.getByText('未确认目标 Doris 环境')).toBeInTheDocument();
  expect(screen.getByText(/不是最新发布指针/)).toBeInTheDocument();
  expect(screen.getByText(/不自动创建新任务/)).toBeInTheDocument();
  expect(getModelingModel).toHaveBeenCalledWith(50);
  expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledWith(50, 3);
  expect(getPublishedDwdMapping).toHaveBeenCalledWith(50, 3);
  expect(screen.getByText(/MappingRevision #701/)).toBeInTheDocument();
  expect(getDevelopmentTaskDraft).not.toHaveBeenCalled();
  expect(focus).not.toHaveBeenCalled();
});

it('rejects an old DWD version without immutable mapping evidence', async () => {
  (getPublishedDwdMapping as jest.Mock).mockResolvedValue({
    modelVersionId: 99, versionNo: 3, state: 'LEGACY_UNBOUND', mapping: null,
  });
  render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(await screen.findByText('交接来源无法核实')).toBeInTheDocument();
  expect(screen.getByText(/冻结映射证据无法核验/)).toBeInTheDocument();
  expect(screen.queryByText(/DATADEV_PROVENANCE : NOT_APPROVED/)).not.toBeInTheDocument();
});

it('rejects forged mapping publication version instead of trusting the URL', async () => {
  (getPublishedDwdMapping as jest.Mock).mockResolvedValue({
    ...boundMapping, modelVersionId: 100,
  });
  render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(await screen.findByText('交接来源无法核实')).toBeInTheDocument();
  expect(screen.queryByText(/MappingRevision #701/)).not.toBeInTheDocument();
});

it('rejects changed or foreign ModelVersion identity instead of falling back to latest', async () => {
  (listModelingVersions as jest.Mock).mockResolvedValue([{ ...version, id: 100 }]);
  render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(await screen.findByText('交接来源无法核实')).toBeInTheDocument();
  expect(screen.queryByText('未确认目标 Doris 环境')).not.toBeInTheDocument();
});

it('rejects tampered URL, missing permission, and unknown Semantic process', async () => {
  const invalid = render(<ModelingHandoffEvidencePanel search="?source=modeling-handoff&processId=7&modelId=-1&modelVersionNo=3"
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(screen.getByText('交接来源无法核实')).toBeInTheDocument();
  expect(getModelingModel).not.toHaveBeenCalled();
  invalid.unmount();

  mockAllowed = false;
  const unauthorized = render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(screen.getByText(/读取权限不足/)).toBeInTheDocument();
  expect(getModelingModel).not.toHaveBeenCalled();
  unauthorized.unmount();

  mockAllowed = true;
  (getSemanticProcess as jest.Mock).mockResolvedValue({ id: 8 });
  render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  expect(await screen.findByText('交接来源无法核实')).toBeInTheDocument();
});

it('discards late Modeling owner response when current project changes', async () => {
  let complete: (value: unknown) => void = () => {};
  (inspectPhysicalDataDevHandoff as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { complete = resolve; }))
    .mockRejectedValueOnce(new Error('新项目不可访问旧模型'));
  const view = render(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  await waitFor(() => expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledTimes(1));
  mockProjectId = 2;
  view.rerender(<ModelingHandoffEvidencePanel search={url}
    nodes={[]} onFocusNode={jest.fn()} />);
  await waitFor(() => expect(inspectPhysicalDataDevHandoff).toHaveBeenCalledTimes(2));
  await act(async () => complete(report));
  expect(await screen.findByText('新项目不可访问旧模型')).toBeInTheDocument();
  expect(screen.queryByText(/DATADEV_PROVENANCE : NOT_APPROVED/)).not.toBeInTheDocument();
});
