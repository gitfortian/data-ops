import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import MetricConsumptionHandoff, { metricConsumptionPath } from './MetricConsumptionHandoff';
import { getMetricUsageList } from '@/services/metric/api';
let mockProjectId = 1;
const mockNavigate = jest.fn();
jest.mock('@umijs/max', () => ({ useNavigate: () => mockNavigate }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/services/metric/api', () => ({ getMetricUsageList: jest.fn() }));
const publication = { publicationEventId: 19, metricId: 7, metricVersionId: 11, metricVersion: 3, snapshotDigest: 'a'.repeat(64), publicationEvidence: [] };
const usage = { id: 1, usageType: 'DATASET', usageId: 9, metricVersion: 3, usageName: '销售数据' };
beforeEach(() => { jest.clearAllMocks(); mockProjectId = 1; (getMetricUsageList as jest.Mock).mockResolvedValue([usage]); });
it('maps only an exact published Dataset reference and provides a safe return route', async () => {
  render(<MetricConsumptionHandoff metricId={7} publication={publication} />);
  fireEvent.click(await screen.findByText('核对数据集：销售数据'));
  expect(mockNavigate).toHaveBeenCalledWith('/data-analysis/consumption/DATASET%3A9?returnMetricId=7');
  for (const change of [{ metricVersion: null }, { metricVersion: 2 }, { usageType: 'API' }, { usageId: -1 }]) {
    expect(metricConsumptionPath(7, 3, { ...usage, ...change })).toBeUndefined();
  }
});
it('reports missing mappings without claiming an execution completed', async () => {
  (getMetricUsageList as jest.Mock).mockResolvedValue([{ ...usage, metricVersion: null }]);
  render(<MetricConsumptionHandoff metricId={7} publication={publication} />);
  await screen.findByText('尚无可映射到该发布版本的消费目标。请先在 Dataset 原页面登记引用，再刷新。');
  expect(screen.queryByText('核对数据集：销售数据')).not.toBeInTheDocument();
});
it('drops a late reference response from a previous project', async () => {
  let resolve: (v: unknown) => void = () => {};
  (getMetricUsageList as jest.Mock).mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockRejectedValue(new Error('forbidden'));
  const view = render(<MetricConsumptionHandoff metricId={7} publication={publication} />);
  mockProjectId = 2; view.rerender(<MetricConsumptionHandoff metricId={7} publication={publication} />);
  await screen.findByText('消费引用暂不可读或无权限，请恢复后重试。');
  await act(async () => resolve([usage])); expect(screen.queryByText('核对数据集：销售数据')).not.toBeInTheDocument();
});
