import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdsApplicationDesignPanel from './AdsApplicationDesignPanel';
import {
  getAdsApplicationContract, getAdsApplicationContractHistory,
  validateAdsApplicationContract, saveAdsApplicationContract,
} from '@/services/modeling/adsApplication';
import {
  getModelingModel, getModelingVersion, listModelingVersions, pageModelingModels,
} from '@/services/modeling/api';
import type { ModelingStructureRecord } from '@/services/modeling/types';

jest.mock('@/services/modeling/adsApplication', () => ({
  getAdsApplicationContract: jest.fn(),
  getAdsApplicationContractHistory: jest.fn(),
  validateAdsApplicationContract: jest.fn(),
  saveAdsApplicationContract: jest.fn(),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: jest.fn(),
  getModelingVersion: jest.fn(),
  listModelingVersions: jest.fn(),
  pageModelingModels: jest.fn(),
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
  (getAdsApplicationContract as jest.Mock).mockResolvedValue(stored);
  (getAdsApplicationContractHistory as jest.Mock).mockResolvedValue([stored]);
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
    expect.objectContaining({ appCode: 'order_overview', expectedRevision: 1 })));
  await waitFor(() => expect(getAdsApplicationContract).toHaveBeenCalledTimes(3));
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
