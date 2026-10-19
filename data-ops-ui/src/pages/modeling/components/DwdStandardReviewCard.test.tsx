import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import { inspectPhysicalStandardCoverage } from '@/services/modeling/api';
import DwdStandardReviewCard from './DwdStandardReviewCard';

let projectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: projectId } }),
}));
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/modeling/api', () => ({
  inspectPhysicalStandardCoverage: jest.fn(),
}));

const sha = 'a'.repeat(64);
const report = {
  modelId: 50, modelCode: 'dwd_order_detail', layerCode: 'DWD',
  modelStatus: 'DRAFT', status: 'NEEDS_REVIEW',
  structureSha256: sha, evidenceSha256: 'b'.repeat(64),
  columnCount: 2, enabledCount: 1, missingCount: 1, invalidCount: 0,
  reviewCount: 0, missingDataStandardCount: 0, invalidDataStandardCount: 0,
  findings: ['process_time 缺少 StandardField'],
  columns: [], limitations: ['当前标准报告并非发布门禁'],
};
const props = {
  physicalModelId: 50, physicalStructureSha256: sha,
  processId: 22, logicalModelId: 77, logicalVersionNo: 2,
};

beforeEach(() => {
  jest.clearAllMocks();
  projectId = 1;
});

it('opens exact physical editor repair location without writing or inventing publication eligibility', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(report);
  render(<DwdStandardReviewCard {...props} />);
  expect(screen.getByText(/这(是|一)帮助人工修复的即时证据/)).toBeInTheDocument();
  expect(inspectPhysicalStandardCoverage).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
  expect(await screen.findByText('process_time 缺少 StandardField')).toBeInTheDocument();
  expect(screen.getByText('缺少字段 1')).toBeInTheDocument();
  expect(screen.getByText(/证据未冻结，发布时仍须重新校验/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '打开物理模型修复缺口' }));
  expect(history.push).toHaveBeenCalledWith(
    '/modeling/models/50?tab=logical&processId=22&logicalId=77'
      + '&logicalVersion=2&review=standard-coverage',
  );
  expect(inspectPhysicalStandardCoverage).toHaveBeenCalledTimes(1);
});

it('never claims a covered current report means it can publish', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock)
    .mockResolvedValue({ ...report, status: 'COVERED_NOW', findings: [],
      enabledCount: 2, missingCount: 0 });
  render(<DwdStandardReviewCard {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
  expect(await screen.findByText(/本次读取未发现覆盖缺口，但不能据此声明已获正式发布授权/))
    .toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '确认发布设计' })).not.toBeInTheDocument();
});

it('rejects wrong model, DWS layer, stale structure or unknown status without showing findings', async () => {
  const bad = [
    { ...report, modelId: 99 },
    { ...report, layerCode: 'DWS' },
    { ...report, structureSha256: 'c'.repeat(64) },
    { ...report, status: 'UNKNOWN' },
    { ...report, evidenceSha256: null },
    { ...report, status: 'COVERED_NOW', findings: ['仍有待审项'] },
    { ...report, status: 'COVERED_NOW', findings: [], missingCount: 1 },
    { ...report, columnCount: 0 },
  ];
  for (const reply of bad) {
    (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValueOnce(reply);
    const view = render(<DwdStandardReviewCard {...props} />);
    fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
    expect(await screen.findByText('标准证据无法核实')).toBeInTheDocument();
    expect(screen.queryByText('process_time 缺少 StandardField')).not.toBeInTheDocument();
    view.unmount();
  }
});

it('keeps provider failure as unavailable rather than default covered', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockRejectedValue(new Error('当前项目权限已撤销'));
  render(<DwdStandardReviewCard {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
  expect(await screen.findByText('当前项目权限已撤销')).toBeInTheDocument();
  expect(screen.queryByText('当前未发现覆盖缺口')).not.toBeInTheDocument();
});

it('clears old current-project evidence and ignores late replies when switching project', async () => {
  let finish: (value: unknown) => void = () => {};
  (inspectPhysicalStandardCoverage as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ ...report, findings: ['新项目的缺口'] });
  const view = render(<DwdStandardReviewCard {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
  projectId = 2;
  view.rerender(<DwdStandardReviewCard {...props} />);
  expect(screen.queryByText('process_time 缺少 StandardField')).not.toBeInTheDocument();
  await act(async () => finish(report));
  expect(screen.queryByText('process_time 缺少 StandardField')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '核验当前物理标准' }));
  await waitFor(() => expect(screen.getByText('新项目的缺口')).toBeInTheDocument());
});

it('does not navigate with invalid business-process coordinates', () => {
  render(<DwdStandardReviewCard {...props} processId={0} />);
  expect(screen.getByRole('button', { name: '打开物理模型修复缺口' })).toBeDisabled();
  expect(history.push).not.toHaveBeenCalled();
});
