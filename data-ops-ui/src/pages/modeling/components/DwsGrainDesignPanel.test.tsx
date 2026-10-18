import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import DwsGrainDesignPanel from './DwsGrainDesignPanel';
import {
  getDwsGrain,
  getDwsGrainHistory,
  listDwsGrainCandidates,
  restoreDwsGrainRevision,
  saveDwsGrain,
  validateDwsGrain,
} from '@/services/modeling/dwsGrain';
import { getModelingVersion } from '@/services/modeling/api';
import type { ModelingStructureRecord } from '@/services/modeling/types';

jest.mock('@/services/modeling/dwsGrain', () => ({
  getDwsGrain: jest.fn(),
  getDwsGrainHistory: jest.fn(),
  listDwsGrainCandidates: jest.fn(),
  restoreDwsGrainRevision: jest.fn(),
  saveDwsGrain: jest.fn(),
  validateDwsGrain: jest.fn(),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingVersion: jest.fn(),
}));

const fingerprint = 'a'.repeat(64);
const structure: ModelingStructureRecord = {
  definition: fingerprint,
  columns: [
    { columnName: 'order_day', dataType: 'DATE', stdFieldId: 10, fieldRole: 'DIMENSION' },
    { columnName: 'order_city', dataType: 'VARCHAR', stdFieldId: 11, fieldRole: 'DIMENSION' },
    { columnName: 'order_count', dataType: 'BIGINT', stdFieldId: 12,
      fieldRole: 'MEASURE', aggregateFunc: 'COUNT_DISTINCT' },
  ],
};
const sourceStructure: ModelingStructureRecord = {
  modelId: 20,
  definition: 'b'.repeat(64),
  columns: [
    { columnName: 'event_time', dataType: 'DATETIME' },
    { columnName: 'order_id', dataType: 'VARCHAR' },
    { columnName: 'item_id', dataType: 'VARCHAR' },
  ],
};
const stored = {
  modelId: 10, revision: 4, structureFingerprint: fingerprint,
  designSha256: 'c'.repeat(64), designJson: '{}',
  design: {
    statPeriod: '1d', timeZone: 'Asia/Shanghai', businessTimeColumn: 'event_time',
    periodBucketColumn: 'order_day',
    dimensions: ['order_day', 'order_city'],
    measures: [{ column: 'order_count', function: 'COUNT_DISTINCT' }],
    upstreamVersions: [{ modelId: 20, versionId: 30, versionNo: 2, checksum: 'b'.repeat(64) }],
  },
};
const history = [
  { ...stored, updatedBy: 'alice', updatedAt: '2026-10-18T01:00:00' },
  { ...stored, revision: 3, updatedBy: 'bob', updatedAt: '2026-10-17T11:00:00' },
];

beforeEach(() => {
  jest.clearAllMocks();
  (getDwsGrain as jest.Mock).mockResolvedValue(stored);
  (getDwsGrainHistory as jest.Mock).mockResolvedValue(history);
  (listDwsGrainCandidates as jest.Mock).mockResolvedValue([
    { modelId: 20, name: '订单明细', code: 'dwd_orders',
      publishedVersionId: 30, publishedVersionNo: 2, usable: true },
  ]);
  (getModelingVersion as jest.Mock).mockResolvedValue({
    version: { versionNo: 2 }, structure: sourceStructure,
  });
  (saveDwsGrain as jest.Mock).mockResolvedValue({ ...stored, revision: 5 });
  (restoreDwsGrainRevision as jest.Mock).mockResolvedValue({ ...stored, revision: 5 });
  (validateDwsGrain as jest.Mock).mockResolvedValue({
    valid: true, blockers: [], structureFingerprint: fingerprint,
    design: stored.design,
  });
});

it('loads the exact saved source version and its immutable revision history without calling Runtime', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计草稿 · R4/)).toBeInTheDocument();
  expect(screen.getByText(/订单明细 \(dwd_orders\) · V2/)).toBeInTheDocument();
  expect(getDwsGrain).toHaveBeenCalledWith(10);
  expect(getDwsGrainHistory).toHaveBeenCalledWith(10);
  expect(listDwsGrainCandidates).toHaveBeenCalledWith(10);
  await waitFor(() => expect(getModelingVersion).toHaveBeenCalledWith(20, 2));
  expect(saveDwsGrain).not.toHaveBeenCalled();
  expect(restoreDwsGrainRevision).not.toHaveBeenCalled();
});

it('does not save a grain contract while the physical structure has unsaved changes', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty />);
  expect(await screen.findByText(/设计草稿 · R4/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('统计粒度'));
  expect(screen.getByRole('button', { name: '保存并冻结当前 DWS 粒度草稿' }))
    .toBeDisabled();
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('saves the exact structure fingerprint and selected DWS grain contract to Modeling only', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计草稿 · R4/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('统计粒度'));
  fireEvent.click(screen.getByRole('button', { name: '保存并冻结当前 DWS 粒度草稿' }));
  await waitFor(() => expect(saveDwsGrain).toHaveBeenCalledWith(10,
    expect.objectContaining({
      expectedStructureFingerprint: fingerprint,
      statPeriod: '1d',
      businessTimeColumn: 'event_time',
      periodBucketColumn: 'order_day',
      dimensions: ['order_day', 'order_city'],
      measures: [{ column: 'order_count', function: 'COUNT_DISTINCT' }],
      upstreamModelIds: [20],
    })));
  expect(await screen.findByText(/设计草稿 · R5/)).toBeInTheDocument();
});

it('runs real server-side static validation separately from the synthetic sample demo', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计草稿 · R4/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('度量与验证'));
  fireEvent.click(screen.getByRole('button', { name: '验证设计合同' }));
  await waitFor(() => expect(validateDwsGrain).toHaveBeenCalledWith(10,
    expect.objectContaining({
      expectedStructureFingerprint: fingerprint,
      upstreamModelIds: [20],
      businessTimeColumn: 'event_time',
    })));
  await waitFor(() => expect(screen.getAllByText('STATIC_PASS').length).toBeGreaterThan(0));
  fireEvent.click(screen.getByRole('button', { name: '运行固定样本演示' }));
  expect(screen.getAllByText('SAMPLE_MATCH').length).toBeGreaterThan(0);
  expect(screen.getByText(/不查询真实业务库/)).toBeInTheDocument();
});

it('restores a prior revision as a new forward revision rather than rewinding history', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计草稿 · R4/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('版本恢复'));
  await screen.findByText('恢复为新修订');
  const restoreButtons = screen.getAllByRole('button', { name: '恢复为新修订' });
  fireEvent.click(restoreButtons[1]);
  await waitFor(() => expect(restoreDwsGrainRevision).toHaveBeenCalledWith(10, 3));
  expect(await screen.findByText(/设计草稿 · R5/)).toBeInTheDocument();
});
