import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { listModelingVersions } from '@/services/modeling/api';
import { getPublishedDwdMapping, type DwdPublishedMapping } from '@/services/modeling/dwdMapping';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import DwdPublishedMappingHistoryPanel from './DwdPublishedMappingHistoryPanel';

let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/services/modeling/api', () => ({ listModelingVersions: jest.fn() }));
jest.mock('@/services/modeling/dwdMapping', () => ({ getPublishedDwdMapping: jest.fn() }));
const versions = [{ id: 90, modelId: 50, versionNo: 2, columnCount: 2 }, { id: 89, modelId: 50, versionNo: 1, columnCount: 1 }];
const binding: DwdPublishedMapping = {
  modelVersionId: 90, versionNo: 2, state: 'DESIGN_PUBLISHED_NOT_EXECUTED', mapping: {
    mappingRevisionId: 81, mappingSetId: 71, revisionNo: 3, logicalModelId: 10, logicalVersionNo: 1,
    logicalSnapshotSha256: 'a'.repeat(64), physicalStructureSha256: 'b'.repeat(64), contentSha256: 'c'.repeat(64),
    rules: [{ targetColumn: 'order_id', logicalAttributeId: 101, stdFieldId: 201, kind: 'DIRECT' },
      { targetColumn: 'paid_amount', logicalAttributeId: 102, stdFieldId: 202, kind: 'RENAME' }],
  },
};
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  jest.mocked(listModelingVersions).mockResolvedValue(versions as ModelingVersionSummary[]);
  jest.mocked(getPublishedDwdMapping).mockResolvedValue(binding);
});

it('shows the exact historical mapping with search and optional verification details', async () => {
  render(<DwdPublishedMappingHistoryPanel open modelId={50} onClose={jest.fn()} />);
  expect(await screen.findByText('order_id')).toBeInTheDocument();
  expect(screen.getByText('原样映射')).toBeInTheDocument();
  expect(screen.getByText('重命名')).toBeInTheDocument();
  expect(getPublishedDwdMapping).toHaveBeenCalledWith(50, 2);
  expect(screen.queryByText(binding.mapping!.contentSha256)).not.toBeInTheDocument();
  fireEvent.change(screen.getByRole('searchbox', { name: '搜索历史映射字段' }), { target: { value: 'paid' } });
  expect(screen.queryByText('order_id')).not.toBeInTheDocument();
  expect(screen.getByText('paid_amount')).toBeInTheDocument();
  fireEvent.click(screen.getByText('核验详情'));
  expect(await screen.findByText(binding.mapping!.contentSha256)).toBeInTheDocument();
});

it('retries a failed catalog read without presenting it as an empty model', async () => {
  jest.mocked(listModelingVersions).mockRejectedValueOnce(new Error('版本服务暂不可用'));
  render(<DwdPublishedMappingHistoryPanel open modelId={50} onClose={jest.fn()} />);
  expect(await screen.findByText('历史版本读取失败')).toBeInTheDocument();
  expect(screen.queryByText('暂无已发布的 DWD 设计版本')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
  expect(await screen.findByText('order_id')).toBeInTheDocument();
  expect(listModelingVersions).toHaveBeenCalledTimes(2);
});

it('keeps identity mismatches as errors and allows re-reading the selected version', async () => {
  jest.mocked(getPublishedDwdMapping).mockResolvedValueOnce({ ...binding, modelVersionId: 999 });
  render(<DwdPublishedMappingHistoryPanel open modelId={50} onClose={jest.fn()} />);
  expect(await screen.findByText('映射记录读取失败')).toBeInTheDocument();
  expect(screen.queryByText('order_id')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
  expect(await screen.findByText('order_id')).toBeInTheDocument();
  expect(getPublishedDwdMapping).toHaveBeenCalledTimes(2);
});

it('discards a previous version response after selecting another version', async () => {
  let finish!: (value: DwdPublishedMapping) => void;
  jest.mocked(getPublishedDwdMapping).mockImplementation((_modelId, versionNo) => versionNo === 2
    ? new Promise(resolve => { finish = resolve; })
    : Promise.resolve({ ...binding, modelVersionId: 89, versionNo: 1 }));
  render(<DwdPublishedMappingHistoryPanel open modelId={50} onClose={jest.fn()} />);
  await waitFor(() => expect(getPublishedDwdMapping).toHaveBeenCalledWith(50, 2));
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'DWD历史设计版本' }));
  fireEvent.click(await screen.findByText('V1 · 1 个字段'));
  expect(await screen.findByText('设计版本 V1')).toBeInTheDocument();
  await act(async () => finish({ ...binding, mapping: { ...binding.mapping!, rules: [
    { targetColumn: 'old_response_field', logicalAttributeId: 101, stdFieldId: 201, kind: 'DIRECT' },
  ] } }));
  expect(screen.getByText('设计版本 V1')).toBeInTheDocument();
  expect(screen.queryByText('old_response_field')).not.toBeInTheDocument();
});

it('distinguishes a legacy version without a mapping from a read failure', async () => {
  const close = jest.fn();
  jest.mocked(getPublishedDwdMapping).mockResolvedValueOnce({ ...binding, state: 'LEGACY_UNBOUND', mapping: null });
  render(<DwdPublishedMappingHistoryPanel open modelId={50} onClose={close} />);
  expect(await screen.findByText('设计版本 V2 未记录逻辑映射')).toBeInTheDocument();
  expect(screen.queryByText('映射记录读取失败')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '返回模型设计' }));
  expect(close).toHaveBeenCalled();
});
