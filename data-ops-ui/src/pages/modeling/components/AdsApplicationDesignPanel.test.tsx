import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdsApplicationDesignPanel from './AdsApplicationDesignPanel';
import { listActiveMetricPublicationReferences } from '@/services/metric/api';
import {
  getAdsApplicationContract, getAdsApplicationContractHistory, getAdsApplicationEvidence,
  validateAdsApplicationContract, saveAdsApplicationContract,
} from '@/services/modeling/adsApplication';
import {
  getModelingModel, getModelingVersion, listModelingVersions, pageModelingModels,
} from '@/services/modeling/api';
import type { ModelingStructureRecord } from '@/services/modeling/types';

jest.mock('@/services/modeling/adsApplication', () => ({
  getAdsApplicationContract: jest.fn(),
  getAdsApplicationContractHistory: jest.fn(),
  getAdsApplicationEvidence: jest.fn(),
  validateAdsApplicationContract: jest.fn(),
  saveAdsApplicationContract: jest.fn(),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: jest.fn(),
  getModelingVersion: jest.fn(),
  listModelingVersions: jest.fn(),
  pageModelingModels: jest.fn(),
}));
jest.mock('@/services/metric/api', () => ({
  listActiveMetricPublicationReferences: jest.fn(),
}));

const contract = {
  appCode: 'order_overview',
  appName: '订单经营日报',
  owner: 'alice',
  purpose: '运营看板',
  consumerType: 'REPORT',
  sourceMode: 'DWS',
  upstream: {
    modelId: 20, versionId: 30, versionNo: 2,
    checksum: 'b'.repeat(64), layerCode: 'DWS',
  },
  outputFields: [
    { sourceColumn: 'order_day', outputColumn: 'order_day', standardFieldId: 11, sensitivity: 'LOW' },
  ],
  businessKeys: ['order_day'],
  filterExpression: '',
  sortExpression: 'order_day DESC',
  metricVersionId: null,
  refreshTarget: 'DAILY',
  targetStructureFingerprint: 'physical-v1',
} as const;
const stored = {
  modelId: 10, revision: 1, contractSha256: 'a'.repeat(64), contractJson: '{}',
  contract, updatedBy: 'alice', updatedAt: '2026-10-18T10:00:00',
};
const physical: ModelingStructureRecord = {
  modelId: 10, definition: 'physical-v1',
  columns: [{ columnName: 'order_day', dataType: 'DATE', stdFieldId: 11 }],
};

const props = {
  modelId: 10, modelName: '订单经营日报', modelCode: 'order_overview',
  processId: 50, canEdit: true, structureDirty: false, structure: physical,
};

beforeEach(() => {
  jest.clearAllMocks();
  (listActiveMetricPublicationReferences as jest.Mock).mockResolvedValue([]);
  (getAdsApplicationContract as jest.Mock).mockResolvedValue(stored);
  (getAdsApplicationContractHistory as jest.Mock).mockResolvedValue([stored]);
  (getAdsApplicationEvidence as jest.Mock).mockResolvedValue({
    modelId: 10, contractRevision: 1, contractSha256: 'a'.repeat(64),
    checkedAt: '2026-10-18T10:00:00Z', domains: [],
  });
  (pageModelingModels as jest.Mock).mockResolvedValue({
    bizData: [
      { id: 20, code: 'dws_order', name: '订单汇总', layerCode: 'DWS',
        processId: 50, publishedVersionId: 30 },
    ],
  });
  (getModelingModel as jest.Mock).mockResolvedValue({
    id: 20, layerCode: 'DWS', processId: 50, publishedVersionId: 30,
  });
  (getModelingVersion as jest.Mock).mockResolvedValue({
    version: { id: 30, modelId: 20, versionNo: 2, checksum: 'b'.repeat(64) },
    structure: { columns: [{ columnName: 'order_day', dataType: 'DATE' }] },
  });
  (listModelingVersions as jest.Mock).mockResolvedValue([
    { id: 30, modelId: 20, versionNo: 2, checksum: 'b'.repeat(64) },
  ]);
  (validateAdsApplicationContract as jest.Mock).mockResolvedValue({
    valid: true, blockers: [], warnings: [], contract,
    targetStructureFingerprint: 'physical-v1',
    deliveryStatus: 'DELIVERY_NOT_CONFIGURED',
  });
  (saveAdsApplicationContract as jest.Mock).mockResolvedValue(stored);
});

test('selects and displays a complete immutable Metric publication pin', async () => {
  (listActiveMetricPublicationReferences as jest.Mock).mockResolvedValue([{
    publicationEventId: 72,
    metricId: 70,
    metricVersionId: 71,
    metricVersion: 4,
    snapshotDigest: 'c'.repeat(64),
    metricCode: 'gmv',
    metricName: '成交总额',
    publishedAt: '2026-10-18T10:00:00',
  }]);
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  fireEvent.click(screen.getByText('业务口径'));
  await waitFor(() => {
    expect(listActiveMetricPublicationReferences).toHaveBeenCalled();
    expect(screen.getByRole('combobox')).not.toBeDisabled();
  });
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByText(/gmv · 成交总额 · V4/));

  expect(screen.getByText(/Metric #70 · V4/)).toBeInTheDocument();
  expect(screen.getByText(/发布事件 #72/)).toBeInTheDocument();
  expect(screen.getByText(/摘要 cccccccccccc/)).toBeInTheDocument();
  expect(screen.queryByText(/Metric 已发布版本 ID/)).not.toBeInTheDocument();
});

test('rehydrates persisted version identity and never invents execution or grants', async () => {
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  fireEvent.click(screen.getByText('发布来源'));
  await waitFor(() => expect(getModelingVersion).toHaveBeenCalledWith(20, 2));
  expect(screen.getByText(/精确发布版本/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('验证保存'));
  expect(screen.getAllByText('NOT_CONNECTED')).toHaveLength(2);
  expect(screen.getByText('UNKNOWN · 未接入消费原域')).toBeInTheDocument();
  expect(saveAdsApplicationContract).not.toHaveBeenCalled();
});

test('uses real server static validation before save and verifies readback', async () => {
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  await waitFor(() => expect(getModelingModel).toHaveBeenCalledWith(20));
  fireEvent.click(screen.getByText('验证保存'));
  fireEvent.click(screen.getByRole('button', { name: '服务端静态校验' }));
  await waitFor(() => expect(validateAdsApplicationContract).toHaveBeenCalledWith(10,
    expect.objectContaining({
      sourceMode: 'DWS', upstreamModelId: 20, upstreamVersionId: 30,
      upstreamChecksum: 'b'.repeat(64), businessKeys: ['order_day'],
    })));
  fireEvent.click(screen.getByRole('button', { name: '保存应用设计合同' }));
  await waitFor(() => expect(saveAdsApplicationContract).toHaveBeenCalledWith(10,
    expect.objectContaining({
      appCode: 'order_overview', expectedRevision: 1,
      expectedTargetStructureFingerprint: 'physical-v1',
    })));
  await waitFor(() => expect(getAdsApplicationContract).toHaveBeenCalledTimes(3));
});

test('keeps the confirmed saved revision when history refresh fails', async () => {
  const nextStored = {
    ...stored,
    revision: 2,
    contractSha256: 'c'.repeat(64),
    contract: { ...contract, appName: '订单经营日报 V2' },
  };
  (getAdsApplicationContract as jest.Mock)
    .mockResolvedValueOnce(stored)
    .mockResolvedValueOnce(stored)
    .mockResolvedValueOnce(nextStored);
  (getAdsApplicationContractHistory as jest.Mock)
    .mockResolvedValueOnce([stored])
    .mockRejectedValueOnce(new Error('history temporarily unavailable'));
  (saveAdsApplicationContract as jest.Mock).mockResolvedValue(nextStored);

  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  fireEvent.click(screen.getByText('应用目标'));
  fireEvent.change(screen.getByPlaceholderText('订单经营日报'), {
    target: { value: '订单经营日报 V2' },
  });
  fireEvent.click(screen.getByText('验证保存'));
  fireEvent.click(screen.getByRole('button', { name: '服务端静态校验' }));
  await waitFor(() => expect(validateAdsApplicationContract).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '保存应用设计合同' }));

  expect(await screen.findByText('合同修订 R2')).toBeInTheDocument();
  expect(screen.getByText('R2 · 订单经营日报 V2')).toBeInTheDocument();
  expect(screen.getByText('R1 · 订单经营日报')).toBeInTheDocument();
  expect(saveAdsApplicationContract).toHaveBeenCalledWith(10,
    expect.objectContaining({ appName: '订单经营日报 V2', expectedRevision: 1 }));
  expect(getAdsApplicationContractHistory).toHaveBeenCalledTimes(2);
});

test('requires a fresh static review before saving after target structure drift', async () => {
  (validateAdsApplicationContract as jest.Mock).mockResolvedValue({
    valid: true, blockers: [], warnings: [],
    contract: { ...contract, targetStructureFingerprint: 'physical-v2' },
    targetStructureFingerprint: 'physical-v2',
    deliveryStatus: 'DELIVERY_NOT_CONFIGURED',
  });
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  await waitFor(() => expect(getModelingModel).toHaveBeenCalledWith(20));
  await waitFor(() => expect(screen.getByText('ADS 物理结构指纹已变化')).toBeInTheDocument());

  const saveButton = screen.getByRole('button', { name: '保存应用设计合同' });
  expect(saveButton).toBeDisabled();
  const validationCallsBeforeReview = (validateAdsApplicationContract as jest.Mock).mock.calls.length;
  fireEvent.click(screen.getByText('验证保存'));
  fireEvent.click(screen.getByRole('button', { name: '服务端静态校验' }));
  await waitFor(() => expect(
    (validateAdsApplicationContract as jest.Mock).mock.calls.length,
  ).toBeGreaterThan(validationCallsBeforeReview));
  await waitFor(() => expect(screen.getByText(/静态校验通过，但 ADS 物理结构指纹已变化/)).toBeInTheDocument());
  await waitFor(() => expect(saveButton).toBeEnabled());
  expect(saveAdsApplicationContract).not.toHaveBeenCalled();
});

test('locks editable fields while saving so in-flight edits cannot be overwritten', async () => {
  let finishSave!: (value: typeof stored) => void;
  (saveAdsApplicationContract as jest.Mock).mockImplementation(() =>
    new Promise<typeof stored>((resolve) => { finishSave = resolve; }));
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole('button', {
    name: '保存应用设计合同',
  })).toBeEnabled());

  fireEvent.click(screen.getByRole('button', { name: '保存应用设计合同' }));
  await waitFor(() => expect(saveAdsApplicationContract).toHaveBeenCalled());
  fireEvent.click(screen.getByText('应用目标'));
  expect(screen.getByPlaceholderText('如 order_overview')).toBeDisabled();

  finishSave(stored);
  await waitFor(() => expect(getAdsApplicationContract).toHaveBeenCalledTimes(3));
  fireEvent.click(screen.getByText('应用目标'));
  expect(screen.getByPlaceholderText('如 order_overview')).not.toBeDisabled();
});

test('blocks save if the upstream published pointer drifted', async () => {
  (getModelingModel as jest.Mock).mockResolvedValue({
    id: 20, layerCode: 'DWS', processId: 50, publishedVersionId: 31,
  });
  render(<AdsApplicationDesignPanel {...props} />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('上游发布版本发生漂移')).toBeInTheDocument());
  fireEvent.click(screen.getByText('验证保存'));
  expect(screen.getByRole('button', { name: '保存应用设计合同' })).toBeDisabled();
  expect(saveAdsApplicationContract).not.toHaveBeenCalled();
});

test('does not save while physical structure is dirty or missing field standards', async () => {
  const noStandards = {
    ...physical, columns: [{ columnName: 'order_day', dataType: 'DATE', stdFieldId: null }],
  };
  render(<AdsApplicationDesignPanel {...props} structure={noStandards} structureDirty />);
  expect(await screen.findByText('合同修订 R1')).toBeInTheDocument();
  fireEvent.click(screen.getByText('验证保存'));
  expect(screen.getByRole('button', { name: '保存应用设计合同' })).toBeDisabled();
  expect(saveAdsApplicationContract).not.toHaveBeenCalled();
});
