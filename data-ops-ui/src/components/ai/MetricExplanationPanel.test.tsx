import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MetricExplanationPanel from './MetricExplanationPanel';
import { getMetricExplanationContext } from '@/services/metric/explanation';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/services/metric/explanation', () => ({ getMetricExplanationContext: jest.fn() }));
jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn(), validateMetricExplanation: jest.fn() }, agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));
const target = { metricId: 7, version: 3, businessQuestion: '' };
const suggestion = { kind: 'METRIC_EXPLANATION', target, expectedDefinition: 'a'.repeat(64), skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  candidates: [{ businessDescription: '金额合计', statements: [{ text: '按金额求和', evidence: [{ key: 'measureExpr', label: '度量', value: 'SUM(amount)' }] }] }], questions: [] };
const context = { metricId: 7, version: 3, versionId: 19, definition: 'a'.repeat(64), facts: [] };
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  (getMetricExplanationContext as jest.Mock).mockResolvedValue(context);
  (agentChatApi.submit as jest.Mock).mockResolvedValue({ turnId: 't1' });
  (agentChatApi.cancelTurn as jest.Mock).mockResolvedValue(true);
  (streamTurnEvents as jest.Mock).mockResolvedValue(undefined);
  (agentSessionApi.continuation as jest.Mock).mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'COMPLETED', governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: target } }));
  (agentSessionApi.history as jest.Mock).mockResolvedValue([{ role: 'assistant', turnId: 't1', content: `\`\`\`yak-metric-explanation\n${JSON.stringify(suggestion)}\n\`\`\`` }]);
  (agentChatApi.validateMetricExplanation as jest.Mock).mockResolvedValue(suggestion);
});
it('shows persisted explanation with source evidence and revalidates before adopting the description', async () => {
  const apply = jest.fn(); render(<MetricExplanationPanel metricId={7} version={3} disabled={false} onApply={apply} />);
  fireEvent.click(await screen.findByText('解释口径并生成说明'));
  fireEvent.click(await screen.findByText('带入业务说明'));
  await waitFor(() => expect(apply).toHaveBeenCalledWith('金额合计'));
  expect(agentChatApi.submit).toHaveBeenCalledWith(expect.objectContaining({ governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: target } }));
  expect(agentChatApi.validateMetricExplanation).toHaveBeenCalledWith(suggestion);
});
it('discards a source read from a previous project before allowing generation', async () => {
  let resolve: (value: unknown) => void = () => {};
  (getMetricExplanationContext as jest.Mock).mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockRejectedValue(new Error('forbidden'));
  const view = render(<MetricExplanationPanel metricId={7} version={3} disabled={false} />);
  mockProjectId = 2; view.rerender(<MetricExplanationPanel metricId={7} version={3} disabled={false} />);
  await screen.findByText('指标版本上下文暂不可用，请重试读取或返回原页面核对。'); await act(async () => resolve(context));
  expect(screen.queryByText('forbidden')).not.toBeInTheDocument();
  expect(screen.queryByText('解释口径并生成说明')).not.toBeInTheDocument(); expect(agentChatApi.submit).not.toHaveBeenCalled();
});

it('retries a failed context read only on request and hides arbitrary service exceptions', async () => {
  (getMetricExplanationContext as jest.Mock).mockRejectedValueOnce(new Error('private source failure'));
  render(<MetricExplanationPanel metricId={7} version={3} disabled={false} />);
  await screen.findByText('指标版本上下文暂不可用，请重试读取或返回原页面核对。');
  expect(screen.queryByText(/private/)).not.toBeInTheDocument();
  expect(getMetricExplanationContext).toHaveBeenCalledTimes(1); expect(agentChatApi.submit).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('重试读取'));
  await screen.findByText('解释口径并生成说明');
  expect(getMetricExplanationContext).toHaveBeenCalledTimes(2); expect(agentChatApi.submit).not.toHaveBeenCalled();
});
it('revoking permission during adoption prevents applying a late validation', async () => {
  let resolve: (value: unknown) => void = () => {};
  (agentChatApi.validateMetricExplanation as jest.Mock).mockImplementation(() => new Promise(r => { resolve = r; }));
  const apply = jest.fn(); const view = render(<MetricExplanationPanel metricId={7} version={3} disabled={false} onApply={apply} />);
  fireEvent.click(await screen.findByText('解释口径并生成说明')); fireEvent.click(await screen.findByText('带入业务说明'));
  await waitFor(() => expect(agentChatApi.validateMetricExplanation).toHaveBeenCalled());
  view.rerender(<MetricExplanationPanel metricId={7} version={3} disabled onApply={apply} />);
  await act(async () => resolve(suggestion)); expect(apply).not.toHaveBeenCalled();
});

it('binds exact historical snapshots and never offers adoption even with a callback', async () => {
  const apply = jest.fn(); const snapshotTarget = { ...target, view: 'SNAPSHOT' };
  (agentSessionApi.continuation as jest.Mock).mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'COMPLETED', governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: snapshotTarget } }));
  (agentSessionApi.history as jest.Mock).mockResolvedValue([{ role: 'assistant', turnId: 't1', content: '```yak-metric-explanation\n' + JSON.stringify({ ...suggestion, target: snapshotTarget }) + '\n```' }]);
  render(<MetricExplanationPanel metricId={7} version={3} disabled={false} snapshot onApply={apply} />);
  fireEvent.click(await screen.findByText('解释口径并生成说明')); await screen.findByText('AI 解读：按金额求和');
  expect(getMetricExplanationContext).toHaveBeenCalledWith(7, 3, true);
  expect(agentChatApi.submit).toHaveBeenCalledWith(expect.objectContaining({ governanceTarget: { purpose: 'METRIC_EXPLANATION', metricExplanation: snapshotTarget } }));
  expect(screen.queryByText('带入业务说明')).not.toBeInTheDocument(); expect(apply).not.toHaveBeenCalled();
});
