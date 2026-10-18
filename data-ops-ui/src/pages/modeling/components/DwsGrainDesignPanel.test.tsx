import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import DwsGrainDesignPanel from './DwsGrainDesignPanel';
import {
  getDwsGrain, listDwsGrainCandidates, saveDwsGrain,
} from '@/services/modeling/dwsGrain';
import type { DwsGrainSaved } from '@/services/modeling/dwsGrain';
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

const makeStored = (versionNo = 2, versionId = 30, revision = 4): DwsGrainSaved => ({
  modelId: 10, revision, structureFingerprint: fingerprint,
  designSha256: 'c'.repeat(64), designJson: '{}',
  design: {
    statPeriod: '1d', timeZone: 'Asia/Shanghai', businessTimeColumn: 'event_time',
    periodBucketColumn: 'order_day',
    dimensions: ['order_day', 'order_city'],
    measures: [{ column: 'order_count', function: 'COUNT_DISTINCT' }],
    upstreamVersions: [{
      modelId: 20, versionId, versionNo, checksum: (versionNo === 2 ? 'b' : 'd').repeat(64),
    }],
  },
});
const stored = makeStored();

const makeCandidate = (versionNo = 2, versionId = 30) => ({
  modelId: 20, name: '订单明细', code: 'dwd_orders',
  publishedVersionId: versionId, publishedVersionNo: versionNo, usable: true,
});

beforeEach(() => {
  jest.clearAllMocks();
  (getDwsGrain as jest.Mock).mockResolvedValue(stored);
  (listDwsGrainCandidates as jest.Mock).mockResolvedValue([makeCandidate()]);
  (saveDwsGrain as jest.Mock).mockResolvedValue(makeStored(2, 30, 5));
});

it('reopens the persisted design and pinned upstream version without querying Runtime', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  expect(await screen.findByText(/设计修订 R4/)).toBeInTheDocument();
  expect(screen.getByText(/模型 #20 V2/)).toBeInTheDocument();
  expect(screen.getByText('V1 真实只读样本预演')).toBeInTheDocument();
  expect(screen.getByText('NOT_CONNECTED')).toBeInTheDocument();
  expect(screen.getByText('NOT_EXECUTED')).toBeInTheDocument();
  expect(getDwsGrain).toHaveBeenCalledWith(10);
  expect(listDwsGrainCandidates).toHaveBeenCalledWith(10);
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('does not enable saving while the physical structure has unsaved edits', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty />);
  expect(await screen.findByText(/设计修订 R4/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存 DWS 设计合同' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/物理表结构尚未保存/)).toBeInTheDocument();
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('requires a current static validation before saving the frozen DWS grain', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  await screen.findByText(/设计修订 R4/);
  expect(screen.getByRole('button', { name: '保存 DWS 设计合同' })).toBeDisabled();

  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/当前设计的静态约束通过/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '保存 DWS 设计合同' }));

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
  expect(await screen.findByText(/设计修订 R5/)).toBeInTheDocument();
});

it('marks static validation stale after an input changes and blocks an invalid timezone', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  await screen.findByText(/设计修订 R4/);
  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/当前设计的静态约束通过/)).toBeInTheDocument();

  fireEvent.change(screen.getByLabelText('统计时区（IANA）'), {
    target: { value: 'Invalid/Zone' },
  });
  expect(await screen.findByText(/结果已过期/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存 DWS 设计合同' })).toBeDisabled();

  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/统计时区不是有效的 IANA 时区/)).toBeInTheDocument();
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('shows the count-vs-distinct example but never changes the real sample-query status', async () => {
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  await screen.findByText(/设计修订 R4/);
  fireEvent.click(screen.getByRole('button', { name: '查看口径示例（合成数据）' }));

  expect(await screen.findByText('订单去重示例 · 非真实查询')).toBeInTheDocument();
  expect(screen.getByText(/不连接任何数据源/)).toBeInTheDocument();
  expect(screen.getByText('NOT_CONNECTED')).toBeInTheDocument();
  expect(screen.getByText('NOT_EXECUTED')).toBeInTheDocument();
  expect(screen.getByText(/明细行数 COUNT\(\*\)/)).toBeInTheDocument();
  expect(screen.getByText('订单数 DISTINCT')).toBeInTheDocument();
  expect(saveDwsGrain).not.toHaveBeenCalled();
});

it('requires explicit confirmation before migrating a pinned V2 contract to current V3', async () => {
  (listDwsGrainCandidates as jest.Mock).mockResolvedValue([makeCandidate(3, 31)]);
  (saveDwsGrain as jest.Mock).mockResolvedValue(makeStored(3, 31, 5));
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);

  expect(await screen.findByText(/冻结 V2/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存 DWS 设计合同' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '确认迁移到当前发布版本' }));
  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/当前设计的静态约束通过/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '保存 DWS 设计合同' }));

  await waitFor(() => expect(saveDwsGrain).toHaveBeenCalledWith(10,
    expect.objectContaining({ upstreamModelIds: [20] })));
  expect(await screen.findByText(/设计修订 R5/)).toBeInTheDocument();
});


it('allows re-confirming the grain contract after a saved physical structure fingerprint changes', async () => {
  const nextFingerprint = 'f'.repeat(64);
  (saveDwsGrain as jest.Mock).mockResolvedValue({
    ...makeStored(2, 30, 5), structureFingerprint: nextFingerprint,
  });
  render(
    <DwsGrainDesignPanel
      modelId={10}
      structure={{ ...structure, definition: nextFingerprint }}
      dirty={false}
    />,
  );
  await screen.findByText(/设计修订 R4/);
  expect(screen.getByText(/物理结构已变化，现有粒度合同尚未重新确认/)).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/当前设计的静态约束通过/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '保存 DWS 设计合同' }));

  await waitFor(() => expect(saveDwsGrain).toHaveBeenCalledWith(10,
    expect.objectContaining({ expectedStructureFingerprint: nextFingerprint })));
  expect(await screen.findByText(/设计修订 R5/)).toBeInTheDocument();
});

it('does not treat an unknown save response as success when read-back is still the old design', async () => {
  (saveDwsGrain as jest.Mock).mockRejectedValue(new Error('request timeout'));
  (getDwsGrain as jest.Mock).mockResolvedValue(stored);
  render(<DwsGrainDesignPanel modelId={10} structure={structure} dirty={false} />);
  await screen.findByText(/设计修订 R4/);

  fireEvent.change(screen.getByLabelText('统计时区（IANA）'), {
    target: { value: 'Asia/Tokyo' },
  });
  fireEvent.click(screen.getByRole('button', { name: '运行设计校验' }));
  expect(await screen.findByText(/当前设计的静态约束通过/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '保存 DWS 设计合同' }));

  await waitFor(() => expect(getDwsGrain).toHaveBeenCalledTimes(2));
  expect(await screen.findByText(/保存结果未知/)).toBeInTheDocument();
  expect(screen.getByText(/设计修订 R4/)).toBeInTheDocument();
});
