import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import DwsGrainDesignPanel from './DwsGrainDesignPanel';
import {
  getDwsGrain, listDwsGrainCandidates, saveDwsGrain,
} from '@/services/modeling/dwsGrain';
import type { ModelingStructureRecord } from '@/services/modeling/types';

jest.mock('@/services/modeling/dwsGrain', () => ({
  getDwsGrain: jest.fn(),
  listDwsGrainCandidates: jest.fn(),
  saveDwsGrain: jest.fn(),
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
beforeEach(() => {
  jest.clearAllMocks();
  (getDwsGrain as jest.Mock).mockResolvedValue(stored);
  (listDwsGrainCandidates as jest.Mock).mockResolvedValue([
    { modelId: 20, name: '订单明细', code: 'dwd_orders',
      publishedVersionId: 30, publishedVersionNo: 2, usable: true },
  ]);
  (saveDwsGrain as jest.Mock).mockResolvedValue({ ...stored, revision: 5 });
});

it('reopens the persisted DWS grain and pinned upstream version without calling Runtime', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计修订 4/)).toBeInTheDocument();
  expect(screen.getByText(/模型 #20 V2/)).toBeInTheDocument();
  expect(getDwsGrain).toHaveBeenCalledWith(10);
  expect(listDwsGrainCandidates).toHaveBeenCalledWith(10);
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('does not save stale local structural edits as if they were frozen', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty />);
  expect(await screen.findByText(/设计修订 4/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存并冻结当前 DWS 粒度草稿' }))
    .toBeDisabled();
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('sends the exact structure fingerprint and selected DWS grain to Modeling only', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  await screen.findByText(/设计修订 4/);
  fireEvent.click(screen.getByRole('button', { name: '保存并冻结当前 DWS 粒度草稿' }));
  await waitFor(() => expect(saveDwsGrain).toHaveBeenCalledWith(10,
    expect.objectContaining({
      expectedStructureFingerprint: fingerprint, statPeriod: '1d',
      businessTimeColumn: 'event_time', periodBucketColumn: 'order_day',
      dimensions: ['order_day', 'order_city'],
      measures: [{ column: 'order_count', function: 'COUNT_DISTINCT' }],
      upstreamModelIds: [20],
    })));
  expect(await screen.findByText(/设计修订 5/)).toBeInTheDocument();
});
