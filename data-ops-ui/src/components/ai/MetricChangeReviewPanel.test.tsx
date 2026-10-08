import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { scenario } from '../../../tests/fixtures/agent-scenarios';
import { parseMetricChangeReview } from '@/services/agent/metricChangeReview';
import { getMetricChangeReviewContext } from '@/services/metric/changeReview';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import MetricChangeReviewPanel from './MetricChangeReviewPanel';
import ScenarioHistoryCard from './ScenarioHistoryCard';
let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/services/metric/changeReview', () => ({ getMetricChangeReviewContext: jest.fn() }));
jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn() }, agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));
const fixture = scenario('METRIC_CHANGE_REVIEW');
const result = parseMetricChangeReview(fixture.text)!;
const context = { ...result.source, ...result.target, status: 'READY', publishedVersionId: 11 };
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  (getMetricChangeReviewContext as jest.Mock).mockResolvedValue(context);
  (agentChatApi.submit as jest.Mock).mockResolvedValue({ turnId: 't1' });
  (agentChatApi.cancelTurn as jest.Mock).mockResolvedValue(true);
  (streamTurnEvents as jest.Mock).mockResolvedValue(undefined);
  (agentSessionApi.continuation as jest.Mock).mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId }));
  (agentSessionApi.history as jest.Mock).mockResolvedValue([{ role: 'assistant', turnId: 't1', content: fixture.text }]);
});
it('generates explicitly, rereads current facts after history, and offers no adoption or source command', async () => {
  render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  const generate = await screen.findByRole('button', { name: '解释版本变更' });
  expect(agentChatApi.submit).not.toHaveBeenCalled(); fireEvent.click(generate);
  await screen.findByText('AI 变更说明：变更为按金额求和');
  expect(getMetricChangeReviewContext).toHaveBeenCalledTimes(2);
  expect(agentChatApi.submit).toHaveBeenCalledWith(expect.objectContaining({ governanceTarget: fixture.target }));
  expect(screen.queryByRole('button', { name: /带入|保存|验证|发布/ })).not.toBeInTheDocument();
});
it.each(['NO_BASELINE', 'UNCHANGED'])('does not create a turn for %s', async status => {
  (getMetricChangeReviewContext as jest.Mock).mockResolvedValue({ ...context, status, differences: [] });
  render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  await screen.findByText(status === 'NO_BASELINE' ? /暂无生效发布版本可比较/ : /白名单定义一致/);
  expect(screen.queryByRole('button', { name: '解释版本变更' })).not.toBeInTheDocument(); expect(agentChatApi.submit).not.toHaveBeenCalled();
});
it('withdraws the completed result when publication or evidence changes before displaying history', async () => {
  (getMetricChangeReviewContext as jest.Mock).mockResolvedValueOnce(context).mockResolvedValue({ ...context, definition: 'c'.repeat(64) });
  render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  fireEvent.click(await screen.findByRole('button', { name: '解释版本变更' }));
  await screen.findByText('来源或发布证据暂无法核对，请重新准备版本与证据。');
  expect(screen.queryByText('AI 变更说明：变更为按金额求和')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '重新准备版本与证据' })).not.toBeDisabled();
});
it('locks question and source replacement while a turn remains unknown or active', async () => {
  let resolve: () => void = () => {};
  (streamTurnEvents as jest.Mock).mockImplementation(() => new Promise<void>(r => { resolve = r; }));
  render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  fireEvent.click(await screen.findByRole('button', { name: '解释版本变更' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '重新准备版本与证据' })).toBeDisabled());
  expect(screen.getByPlaceholderText('发布前希望核对的重点（可空）')).toBeDisabled();
  await act(async () => resolve()); await screen.findByText('AI 变更说明：变更为按金额求和');
  expect(screen.getByRole('button', { name: '重新准备版本与证据' })).not.toBeDisabled();
});
it('discards late source results across project and permission changes and hides service exceptions', async () => {
  let resolve: (v: unknown) => void = () => {};
  (getMetricChangeReviewContext as jest.Mock).mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockRejectedValue(new Error('private-source'));
  const view = render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  mockProjectId = 2; view.rerender(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  await screen.findByText(/版本变更上下文暂不可用/); await act(async () => resolve(context));
  expect(screen.queryByRole('button', { name: '解释版本变更' })).not.toBeInTheDocument(); expect(screen.queryByText('private-source')).not.toBeInTheDocument();
  view.rerender(<MetricChangeReviewPanel metricId={7} version={3} disabled />);
  expect(agentChatApi.submit).not.toHaveBeenCalled();
});
it('retries source preparation explicitly and retains user focus when preparing again', async () => {
  render(<MetricChangeReviewPanel metricId={7} version={3} disabled={false} />);
  const input = await screen.findByPlaceholderText('发布前希望核对的重点（可空）');
  fireEvent.change(input, { target: { value: '核对退款口径' } });
  fireEvent.click(screen.getByRole('button', { name: '重新准备版本与证据' }));
  await waitFor(() => expect(getMetricChangeReviewContext).toHaveBeenCalledTimes(2));
  expect(await screen.findByPlaceholderText('发布前希望核对的重点（可空）')).toHaveValue('核对退款口径'); expect(agentChatApi.submit).not.toHaveBeenCalled();
});
it('history is read-only, displays generating-time scope and coverage, and performs no source request', () => {
  render(<ScenarioHistoryCard review={{ status: 'READY', value: result, sourcePath: '/metric/manage/7', text: '' }} />);
  expect(screen.getByText(/生成时发布 v2/)).toBeInTheDocument(); expect(screen.getByText(/尚未核验当前有效性/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '返回原页面核对' })).toHaveAttribute('href', '/metric/manage/7');
  expect(getMetricChangeReviewContext).not.toHaveBeenCalled(); expect(agentChatApi.submit).not.toHaveBeenCalled();
});
