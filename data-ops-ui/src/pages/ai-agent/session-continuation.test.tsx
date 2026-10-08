import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import AiAgentPage from './index';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { scenario } from '../../../tests/fixtures/agent-scenarios';

let mockCanRun = false;
let mockCanRead = true;
let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({ can: (code: string) => code === 'agent:session:read' ? mockCanRead : mockCanRun && code === 'agent:chat:run' }) }));
jest.mock('@/services/agent', () => ({
  agentSessionApi: { list: jest.fn(), history: jest.fn(), continuation: jest.fn(), trace: jest.fn(), cancel: jest.fn() },
  agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn() }, streamTurnEvents: jest.fn(),
}));
// These adapters exercise page state and payloads; index.test.tsx retains the installed component smoke test.
jest.mock('@ant-design/x/lib/sender', () => ({ __esModule: true, default: (props: any) => (
  <div><textarea aria-label="会话输入" disabled={props.disabled} value={props.value} onChange={(event) => props.onChange(event.target.value)} />
    <button disabled={props.disabled || props.loading} onClick={() => props.onSubmit(props.value)}>发送测试问题</button>
    {props.loading && <button onClick={props.onCancel}>停止实时轮</button>}</div>
) }));
jest.mock('@ant-design/x/lib/conversations', () => ({ __esModule: true, default: (props: any) => (
  <div>{props.items.map((item: any) => <button key={item.key} onClick={() => props.onActiveChange(item.key)}>{item.label}</button>)}</div>
) }));

const api = jest.mocked(agentSessionApi);
const chat = jest.mocked(agentChatApi);
const stream = jest.mocked(streamTurnEvents);
const context = (sessionId = 's1', extra = {}) => ({ sessionId, turnId: 't1', status: 'COMPLETED' as const, ...extra });
const send = () => {
  fireEvent.change(screen.getByLabelText('会话输入'), { target: { value: '继续核对证据' } });
  fireEvent.click(screen.getByText('发送测试问题'));
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(() => {
  jest.resetAllMocks();
  mockCanRun = false;
  mockCanRead = true; mockProjectId = 1;
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  window.IntersectionObserver = jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) as any;
  window.ResizeObserver = jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) as any;
  window.history.replaceState({}, '', '/ai-agent?sessionId=s1&assetId=999');
  api.list.mockResolvedValue([{ sessionId: 's1', title: '会话一' }, { sessionId: 's2', title: '会话二' }] as any);
  api.history.mockResolvedValue([{ role: 'user', content: '历史问题' }, { role: 'assistant', content: '历史回答' }] as any);
  api.continuation.mockResolvedValue(context('s1', { governanceTarget: { qualityExecutionNo: 'Q_1' } }));
  api.trace.mockResolvedValue({ spans: [] } as any);
  chat.submit.mockResolvedValue({ turnId: 't-next' } as any);
  chat.cancelTurn.mockResolvedValue(true);
  stream.mockImplementation(async (_request, handler) => { handler.onComplete(); });
});
afterEach(() => { window.history.replaceState({}, '', '/'); jest.useRealTimers(); delete (document as any).hidden; });

it.each(['STANDARD_MATCH', 'MODEL_MAPPING', 'METRIC_EXPLANATION', 'METRIC_DRAFT'] as const)('reviews a unique persisted %s result without submitting a turn', async kind => {
  const f = scenario(kind);
  api.history.mockResolvedValue([{ role: 'assistant', content: f.text, turnId: 't1' }] as any);
  api.continuation.mockResolvedValue(f.continuation);
  render(<AiAgentPage />);
  expect(await screen.findByText(/历史生成结果 · Skill v1/)).toBeInTheDocument();
  expect(screen.queryByText(/```yak-/)).not.toBeInTheDocument();
  expect(screen.queryByText(/质量执行 undefined/)).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '返回原页面核对' })).toHaveAttribute('href', kind === 'STANDARD_MATCH' ? '/modeling/models/7' : kind === 'MODEL_MAPPING' ? '/modeling/models/7/mapping' : kind === 'METRIC_DRAFT' ? '/metric/manage' : '/metric/manage/7');
  expect(chat.submit).not.toHaveBeenCalled(); expect(stream).not.toHaveBeenCalled();
});

it.each(['empty duplicate', 'duplicate', 'old turn', 'missing turn', 'failed', 'running', 'wrong scope'] as const)('retains raw receipt for %s without upgrading it', async reason => {
  const f = scenario();
  const history = [{ role: 'assistant', content: f.text, turnId: reason === 'missing turn' ? undefined : reason === 'old turn' ? 'old' : 't1' }];
  if (reason.includes('duplicate')) history.push({ role: 'assistant', content: reason === 'empty duplicate' ? '' : '另一回答', turnId: 't1' });
  api.history.mockResolvedValue(history as any);
  api.continuation.mockResolvedValue(reason === 'wrong scope' ? scenario('MODEL_MAPPING').continuation
    : { ...f.continuation, status: reason === 'failed' ? 'FAILED' : reason === 'running' ? 'RUNNING' : 'COMPLETED' });
  render(<AiAgentPage />);
  expect(await screen.findByText('场景结果尚未核对')).toBeInTheDocument();
  expect(screen.getByText(/```yak-standard-match/)).toBeInTheDocument();
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '返回原页面核对' })).not.toBeInTheDocument();
});

it('withdraws a verified card immediately on refresh and keeps raw history after read failure', async () => {
  const f = scenario();
  api.history.mockResolvedValue([{ role: 'assistant', content: f.text, turnId: 't1' }] as any);
  api.continuation.mockResolvedValue(f.continuation);
  render(<AiAgentPage />);
  await screen.findByText(/历史生成结果/);
  const pending = deferred<any>(); api.continuation.mockReturnValueOnce(pending.promise);
  api.history.mockRejectedValueOnce(new Error('HTTP 403'));
  fireEvent.click(screen.getByText('刷新会话'));
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  await act(async () => pending.resolve(f.continuation));
  expect(screen.getByText(/```yak-standard-match/)).toBeInTheDocument();
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
});

it.each(['project', 'permission'] as const)('clears cards and ignores stale history reads after %s changes', async scope => {
  const f = scenario();
  api.history.mockResolvedValue([{ role: 'assistant', content: f.text, turnId: 't1' }] as any);
  api.continuation.mockResolvedValue(f.continuation);
  const { rerender } = render(<AiAgentPage />);
  await screen.findByText(/历史生成结果/);
  const late = deferred<any>(); api.history.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByText('刷新会话'));
  api.history.mockRejectedValue(new Error('HTTP 403')); api.continuation.mockRejectedValue(new Error('HTTP 403'));
  if (scope === 'project') mockProjectId = 2; else mockCanRead = false;
  rerender(<AiAgentPage />);
  await act(async () => late.resolve([{ role: 'assistant', content: f.text, turnId: 't1' }]));
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  expect(screen.queryByText(/```yak-standard-match/)).not.toBeInTheDocument();
});

it('does not upgrade a completed live receipt until authorized history is reloaded', async () => {
  mockCanRun = true;
  const f = scenario();
  api.continuation.mockResolvedValue({ ...f.continuation, turnId: 't-next' });
  stream.mockImplementation(async (_request, handler) => {
    handler.onEvent({ type: 'TEXT_MESSAGE_CONTENT', delta: f.text }); handler.onComplete();
  });
  render(<AiAgentPage />);
  await screen.findByText('历史回答'); send();
  await screen.findByText('场景结果尚未核对');
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  api.history.mockResolvedValue([{ role: 'assistant', content: f.text, turnId: 't-next' }] as any);
  fireEvent.click(screen.getByText('刷新会话'));
  expect(await screen.findByText(/历史生成结果/)).toBeInTheDocument();
  expect(chat.submit).toHaveBeenCalledTimes(1);
});

it('withdraws scene cards during a session switch and ignores its response after a new conversation', async () => {
  const f = scenario();
  api.history.mockResolvedValue([{ role: 'assistant', content: f.text, turnId: 't1' }] as any);
  api.continuation.mockResolvedValue(f.continuation);
  render(<AiAgentPage />);
  await screen.findByText(/历史生成结果/);
  const late = deferred<any>(); api.continuation.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByText('会话二'));
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('新建会话'));
  await act(async () => late.resolve({ ...f.continuation, sessionId: 's2' }));
  expect(screen.queryByText(/历史生成结果/)).not.toBeInTheDocument();
  expect(screen.queryByText(/```yak-standard-match/)).not.toBeInTheDocument();
  expect(window.location.search).toBe('');
});

it('stops only the acknowledged live turn, ignores duplicate stops and late callbacks, and reads the actual completion', async () => {
  mockCanRun = true;
  let handler!: Parameters<typeof streamTurnEvents>[1];
  const live = deferred<void>();
  stream.mockImplementation(async (_request, h) => { handler = h; await live.promise; });
  await act(async () => { render(<AiAgentPage />); });
  send();
  await waitFor(() => expect(stream).toHaveBeenCalled());
  const stopped = deferred<boolean>(); chat.cancelTurn.mockReturnValueOnce(stopped.promise);
  api.continuation.mockResolvedValueOnce(context('s1', { turnId: 't-next', status: 'COMPLETED' }));
  const stop = screen.getByText('停止实时轮');
  act(() => { fireEvent.click(stop); fireEvent.click(stop); });
  act(() => { handler.onEvent({ type: 'TEXT_MESSAGE_CONTENT', delta: '迟到的错误会话内容' }); handler.onComplete(); });
  expect(chat.cancelTurn).toHaveBeenCalledTimes(1);
  expect(chat.cancelTurn).toHaveBeenCalledWith('t-next');
  expect(api.cancel).not.toHaveBeenCalled();
  expect(screen.queryByText(/最近轮次状态：已停止/)).not.toBeInTheDocument();
  await act(async () => stopped.resolve(true));
  expect(await screen.findByText(/最近轮次状态：已完成/)).toBeInTheDocument();
  api.continuation.mockResolvedValueOnce(context('s2', { governanceTarget: { assetId: 2 } }));
  fireEvent.click(screen.getByText('会话二'));
  await screen.findByText('资产 #2 治理解读');
  const reads = api.continuation.mock.calls.length;
  await act(async () => { handler.onError('secret'); handler.onReconnect?.(1); handler.onComplete(); live.resolve(); });
  expect(api.continuation).toHaveBeenCalledTimes(reads);
  expect(screen.queryByText('迟到的错误会话内容')).not.toBeInTheDocument();
  expect(window.location.search).toBe('?sessionId=s2');
});

it('does not cancel an unknown turn while submit is pending, then uses the confirmed receipt', async () => {
  mockCanRun = true;
  const receipt = deferred<any>(); chat.submit.mockReturnValueOnce(receipt.promise);
  const live = deferred<void>(); stream.mockImplementation(async () => live.promise);
  await act(async () => { render(<AiAgentPage />); });
  send(); fireEvent.click(screen.getByText('停止实时轮'));
  expect(await screen.findByText('提交尚未确认，暂不能停止；请等待回执后核对。')).toBeInTheDocument();
  expect(chat.cancelTurn).not.toHaveBeenCalled(); expect(api.cancel).not.toHaveBeenCalled();
  await act(async () => receipt.resolve({ turnId: 'confirmed' }));
  fireEvent.click(screen.getByText('停止实时轮'));
  await waitFor(() => expect(chat.cancelTurn).toHaveBeenCalledWith('confirmed'));
  await act(async () => live.resolve());
});

it('blocks after a lost live stop acknowledgement and only displays cancelled after refresh proves it', async () => {
  mockCanRun = true;
  const live = deferred<void>(); stream.mockImplementation(async () => live.promise);
  await act(async () => { render(<AiAgentPage />); });
  send(); await waitFor(() => expect(stream).toHaveBeenCalled());
  chat.cancelTurn.mockRejectedValueOnce(new Error('secret timeout'));
  fireEvent.click(screen.getByText('停止实时轮'));
  expect(await screen.findByText('停止请求未确认，请刷新会话核对状态。')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(screen.queryByText(/最近轮次状态：已停止/)).not.toBeInTheDocument();
  api.continuation.mockResolvedValueOnce(context('s1', { turnId: 't-next', status: 'CANCELLED' }));
  fireEvent.click(screen.getByText('刷新会话'));
  expect(await screen.findByText(/最近轮次状态：已停止/)).toBeInTheDocument();
  await act(async () => live.resolve());
});

it('preserves received text on transport and refresh failure without fabricating a failed turn', async () => {
  let handler!: Parameters<typeof streamTurnEvents>[1];
  const live = deferred<void>(); stream.mockImplementation(async (_request, h) => { handler = h; await live.promise; });
  await act(async () => { render(<AiAgentPage />); });
  send(); await waitFor(() => expect(stream).toHaveBeenCalled());
  api.continuation.mockRejectedValueOnce(new Error('read failed'));
  act(() => { handler.onEvent({ type: 'TEXT_MESSAGE_CONTENT', delta: '已收到的部分回答' }); handler.onError('secret connection'); });
  expect(await screen.findByText('最新状态未确认，请刷新会话核对后继续。')).toBeInTheDocument();
  expect(screen.getByText('已收到的部分回答')).toBeInTheDocument();
  expect(screen.queryByText('本轮执行失败')).not.toBeInTheDocument();
  api.history.mockRejectedValueOnce(new Error('history unavailable'));
  fireEvent.click(screen.getByText('刷新会话'));
  expect(await screen.findByText(/会话恢复失败/)).toBeInTheDocument();
  expect(screen.getByText('已收到的部分回答')).toBeInTheDocument();
  await act(async () => live.resolve());
});

it('does not turn a completed transport into a completed backend turn', async () => {
  let handler!: Parameters<typeof streamTurnEvents>[1];
  const live = deferred<void>(); stream.mockImplementation(async (_request, h) => { handler = h; await live.promise; });
  await act(async () => { render(<AiAgentPage />); });
  send(); await waitFor(() => expect(stream).toHaveBeenCalled());
  api.continuation.mockResolvedValueOnce(context('s1', { turnId: 't-next', status: 'RUNNING' }));
  act(() => handler.onComplete());
  expect(await screen.findByText(/最近轮次状态：推理中/)).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(api.cancel).not.toHaveBeenCalled();
  await act(async () => live.resolve());
});

it('restores the original pending question when a stop races with clarification', async () => {
  mockCanRun = true;
  const live = deferred<void>(); stream.mockImplementation(async () => live.promise);
  await act(async () => { render(<AiAgentPage />); });
  send(); await waitFor(() => expect(stream).toHaveBeenCalled());
  api.continuation.mockResolvedValueOnce(context('s1', { turnId: 't-next', status: 'WAITING_INPUT',
    clarification: { toolCallId: 'original-call', toolName: 'request_clarification', question: '{"question":"请确认背景","options":["按原背景"]}' } }));
  fireEvent.click(screen.getByText('停止实时轮'));
  expect(await screen.findByText('请确认背景')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(screen.queryByText(/最近轮次状态：已停止/)).not.toBeInTheDocument();
  stream.mockImplementationOnce(async (_request, h) => h.onComplete());
  fireEvent.click(screen.getByText('按原背景'));
  await waitFor(() => expect(chat.submit).toHaveBeenLastCalledWith({ sessionId: 's1', toolResults: [
    { toolCallId: 'original-call', toolName: 'request_clarification', output: '按原背景' },
  ] }));
  await act(async () => live.resolve());
});

it('does not issue a live stop without CHAT_RUN', async () => {
  const live = deferred<void>(); stream.mockImplementation(async () => live.promise);
  await act(async () => { render(<AiAgentPage />); });
  send(); await waitFor(() => expect(stream).toHaveBeenCalled());
  fireEvent.click(screen.getByText('停止实时轮'));
  expect(chat.cancelTurn).not.toHaveBeenCalled(); expect(api.cancel).not.toHaveBeenCalled();
  await act(async () => live.resolve());
});

it('ignores a submit receipt after unmount instead of opening an abandoned subscription', async () => {
  const receipt = deferred<any>(); chat.submit.mockReturnValueOnce(receipt.promise);
  let view!: ReturnType<typeof render>;
  await act(async () => { view = render(<AiAgentPage />); });
  send(); view.unmount();
  await act(async () => receipt.resolve({ turnId: 'abandoned' }));
  expect(stream).not.toHaveBeenCalled(); expect(chat.cancelTurn).not.toHaveBeenCalled();
});

it('fills an exact original only on request, preserves edits and submits a new turn with its source', async () => {
  mockCanRun = true;
  api.continuation.mockResolvedValue(context('s1', { status: 'CANCELLED', questionDraft: '原问题', governanceTarget: { assetId: 7 } }));
  await act(async () => { render(<AiAgentPage />); });
  const fill = await screen.findByRole('button', { name: '填入本轮问题' });
  expect(screen.getByLabelText('会话输入')).toHaveValue('');
  fireEvent.click(fill);
  await waitFor(() => expect(screen.getByLabelText('会话输入')).toHaveValue('原问题'));
  expect(chat.submit).not.toHaveBeenCalled(); expect(stream).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('会话输入'), { target: { value: '核对后的新问题' } });
  expect(fill).toBeDisabled();
  fireEvent.click(screen.getByText('发送测试问题'));
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', message: '核对后的新问题', governanceTarget: { assetId: 7 }, expectedLatestTurnId: 't1' }));
  await waitFor(() => expect(screen.getByLabelText('会话输入')).not.toBeDisabled());
  send();
  await waitFor(() => expect(chat.submit).toHaveBeenLastCalledWith({ sessionId: 's1', message: '继续核对证据', governanceTarget: { assetId: 7 } }));
});

it('does not fill when another tab has advanced the latest turn', async () => {
  mockCanRun = true;
  api.continuation.mockResolvedValueOnce(context('s1', { questionDraft: '原问题' }))
    .mockResolvedValueOnce(context('s1', { turnId: 't2', questionDraft: '别的问题' }));
  render(<AiAgentPage />);
  fireEvent.click(await screen.findByRole('button', { name: '填入本轮问题' }));
  expect(await screen.findByText('任务已变化，请刷新会话后核对。')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toHaveValue('');
  expect(chat.submit).not.toHaveBeenCalled();
});

it('prepares a selected governance question as unconfirmed user background, then explicitly sends it', async () => {
  mockCanRun = true;
  await act(async () => { render(<AiAgentPage />); });
  fireEvent.click(screen.getByText('准备治理问题'));
  fireEvent.change(screen.getByLabelText(/已知背景/), { target: { value: '<img src=x onerror=alert(1)>昨天切换了数据源' } });
  fireEvent.change(screen.getByLabelText(/希望核对/), { target: { value: '请确认哪些状态来自历史执行' } });
  expect(document.querySelector('img[src="x"]')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '填入准备的问题' }));
  const question = (screen.getByLabelText('会话输入') as HTMLTextAreaElement).value;
  expect(question).toContain('用户提供，尚待源证据核对');
  expect(question).toContain('希望核对：请确认哪些状态来自历史执行');
  expect(chat.submit).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '填入准备的问题' })).toBeDisabled();
  expect(screen.getByText('补充排查信息').closest('button')).toBeDisabled();
  fireEvent.click(screen.getByText('发送测试问题'));
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', message: question, governanceTarget: { qualityExecutionNo: 'Q_1' } }));
});

it('clears prepared background when switching scope', async () => {
  mockCanRun = true;
  await act(async () => { render(<AiAgentPage />); });
  fireEvent.click(screen.getByText('准备治理问题'));
  fireEvent.change(screen.getByLabelText(/已知背景/), { target: { value: '旧对象背景' } });
  api.continuation.mockResolvedValueOnce(context('s2', { governanceTarget: { assetId: 2 } }));
  fireEvent.click(screen.getByText('会话二'));
  await screen.findByText('资产 #2 治理解读');
  fireEvent.click(screen.getByText('准备治理问题'));
  expect(screen.getByLabelText(/已知背景/)).toHaveValue('');
  expect(chat.submit).not.toHaveBeenCalled();
});

it.each(['COMPLETED', 'WAITING_INPUT', 'RUNNING'] as const)('blocks preparation without permission or while %s requires waiting', async (status) => {
  mockCanRun = status !== 'COMPLETED';
  api.continuation.mockResolvedValue(context('s1', { status, governanceTarget: { assetId: 7 },
    ...(status === 'WAITING_INPUT' ? { clarification: { toolCallId: 'c', toolName: 'request_clarification', question: '原澄清' } } : {}) }));
  await act(async () => { render(<AiAgentPage />); });
  fireEvent.click(screen.getByText('准备治理问题'));
  expect(screen.getByRole('button', { name: '填入准备的问题' })).toBeDisabled();
  expect(screen.getByLabelText(/已知背景/)).toBeDisabled();
  expect(chat.submit).not.toHaveBeenCalled();
});

it('ignores a draft response after switching session and never overwrites typing while loading', async () => {
  mockCanRun = true;
  api.continuation.mockResolvedValueOnce(context('s1', { questionDraft: '旧问题' }));
  render(<AiAgentPage />);
  const fill = await screen.findByRole('button', { name: '填入本轮问题' });
  const pending = deferred<any>(); api.continuation.mockReturnValueOnce(pending.promise);
  fireEvent.click(fill);
  fireEvent.change(screen.getByLabelText('会话输入'), { target: { value: '正在编辑' } });
  await act(async () => pending.resolve(context('s1', { questionDraft: '旧问题' })));
  expect(screen.getByLabelText('会话输入')).toHaveValue('正在编辑');
  fireEvent.change(screen.getByLabelText('会话输入'), { target: { value: '' } });
  const late = deferred<any>(); api.continuation.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByRole('button', { name: '填入本轮问题' }));
  api.continuation.mockResolvedValueOnce(context('s2'));
  fireEvent.click(screen.getByText('会话二'));
  await waitFor(() => expect(window.location.search).toBe('?sessionId=s2'));
  await act(async () => late.resolve(context('s1', { questionDraft: '迟到问题' })));
  expect(screen.getByLabelText('会话输入')).toHaveValue('');
});

it('locks double submission and preserves an unacknowledged question through refresh', async () => {
  render(<AiAgentPage />);
  await screen.findByText('历史回答');
  const submitted = deferred<any>(); chat.submit.mockReturnValueOnce(submitted.promise);
  fireEvent.change(screen.getByLabelText('会话输入'), { target: { value: '我的问题' } });
  act(() => { fireEvent.click(screen.getByText('发送测试问题')); fireEvent.click(screen.getByText('发送测试问题')); });
  expect(chat.submit).toHaveBeenCalledTimes(1);
  await act(async () => submitted.resolve(Promise.reject(new Error('secret connection'))));
  expect(await screen.findByText('提交未确认，请刷新会话核对状态后继续。')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toHaveValue('我的问题');
  fireEvent.click(screen.getByText('刷新会话'));
  await waitFor(() => expect(screen.getByLabelText('会话输入')).not.toBeDisabled());
  expect(screen.getByLabelText('会话输入')).toHaveValue('我的问题');
  expect(chat.submit).toHaveBeenCalledTimes(1);
});

it('reloads history and persisted scope, ignores extra URL targets and sends a fresh scoped question', async () => {
  render(<AiAgentPage />);
  expect(await screen.findByText('质量执行 Q_1 解读与排查')).toBeInTheDocument();
  expect(screen.getByText('历史回答')).toBeInTheDocument();
  expect(screen.queryByText('资产 #999 治理解读')).not.toBeInTheDocument();
  expect(chat.submit).not.toHaveBeenCalled(); expect(stream).not.toHaveBeenCalled();
  send();
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', message: '继续核对证据', governanceTarget: { qualityExecutionNo: 'Q_1' } }));
  expect(window.location.search).toBe('?sessionId=s1');
});

it('keeps legacy text with an explicit missing association notice and does not hydrate another turn', async () => {
  api.history.mockResolvedValue([{ role: 'assistant', content: '旧正文',
    trace: [{ kind: 'think', text: '不得展示的无关联步骤' }] }] as any);
  render(<AiAgentPage />);
  expect(await screen.findByText('旧正文')).toBeInTheDocument();
  expect(screen.getByText('未能确定这段历史回答所属的执行轮次，暂不展示关联执行证据。')).toBeInTheDocument();
  expect(screen.queryByText('不得展示的无关联步骤')).not.toBeInTheDocument();
  expect(api.trace).not.toHaveBeenCalled();
  expect(chat.submit).not.toHaveBeenCalled();
});

it('hydrates only the confirmed historical answer rather than the latest continuation turn', async () => {
  api.history.mockResolvedValue([{ role: 'assistant', content: '已关联回答', turnId: 't-confirmed' }] as any);
  api.continuation.mockResolvedValue(context('s1', { turnId: 't-latest' }));
  render(<AiAgentPage />);
  expect(await screen.findByText('已关联回答')).toBeInTheDocument();
  await waitFor(() => expect(api.trace).toHaveBeenCalledWith('t-confirmed'));
  expect(api.trace).toHaveBeenCalledTimes(1);
  expect(screen.queryByText(/未能确定这段历史回答/)).not.toBeInTheDocument();
});

it('marks appended failures as independent records without borrowing their identity for older text', async () => {
  api.history.mockResolvedValue([{ role: 'assistant', content: '原正文' },
    { role: 'error', content: '失败事实', turnId: 'failed-turn' }] as any);
  render(<AiAgentPage />);
  expect(await screen.findByText('失败事实')).toBeInTheDocument();
  expect(screen.getByText('独立失败记录；此处展示位置不代表它与前面回答的执行顺序。')).toBeInTheDocument();
  expect(screen.getByText('原正文')).toBeInTheDocument();
  expect(api.trace).not.toHaveBeenCalled();
});

it('restores an exact pending call and answers through the original resume payload', async () => {
  api.continuation.mockResolvedValue(context('s1', {
    status: 'WAITING_INPUT', governanceTarget: { assetId: 7 },
    clarification: { toolCallId: 'clarify-2', toolName: 'request_clarification', question: '{"question":"哪个分区？","options":["昨天"]}' },
  }));
  render(<AiAgentPage />);
  expect(await screen.findByText('哪个分区？')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: /昨\s*天/ }));
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', toolResults: [{ toolCallId: 'clarify-2', toolName: 'request_clarification', output: '昨天' }] }));
  // A completed transport cannot prove pending was consumed; the latest server projection still wins.
  await waitFor(() => expect(screen.getByText('AI 需要补充信息')).toBeInTheDocument());
});

it('restores description purpose as a description task and keeps it on follow-up', async () => {
  mockCanRun = true;
  api.continuation.mockResolvedValue(context('s1', { governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' } }));
  render(<AiAgentPage />);
  expect(await screen.findByText('资产 #7 描述候选')).toBeInTheDocument();
  fireEvent.click(screen.getByText('生成描述候选'));
  expect(screen.getByLabelText('会话输入')).toHaveValue('根据当前资产与字段证据给出资产描述候选；缺业务背景请先确认。');
  expect(chat.submit).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('发送测试问题'));
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1',
    message: '根据当前资产与字段证据给出资产描述候选；缺业务背景请先确认。', governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' } }));
});

it.each(['QUEUED', 'RUNNING'] as const)('blocks %s without replay or submission, then refreshes a completed result', async (status) => {
  api.continuation.mockResolvedValueOnce(context('s1', { status }));
  render(<AiAgentPage />);
  const refresh = await screen.findByText('刷新会话');
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(chat.submit).not.toHaveBeenCalled(); expect(stream).not.toHaveBeenCalled();
  fireEvent.click(refresh);
  await screen.findByText('质量执行 Q_1 解读与排查');
  expect(screen.getByLabelText('会话输入')).not.toBeDisabled();
  expect(api.history).toHaveBeenCalledTimes(2);
});

it('retains readable history but blocks continuation if context lookup fails', async () => {
  api.continuation.mockRejectedValueOnce(new Error('context unavailable'));
  render(<AiAgentPage />);
  expect(await screen.findByText(/会话恢复失败/)).toBeInTheDocument();
  expect(screen.getByText('历史回答')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(chat.submit).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('刷新会话'));
  await screen.findByText('质量执行 Q_1 解读与排查');
  expect(screen.getByLabelText('会话输入')).not.toBeDisabled();
});

it('keeps an ordinary latest turn ordinary and clears URL and scope for a new conversation', async () => {
  api.continuation.mockResolvedValue(context());
  render(<AiAgentPage />);
  await screen.findByText('历史回答');
  expect(screen.queryByText('资产 #999 治理解读')).not.toBeInTheDocument();
  send();
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', message: '继续核对证据', governanceTarget: undefined }));
  await waitFor(() => expect(screen.getByText('发送测试问题')).not.toBeDisabled());
  fireEvent.click(screen.getByText('新建会话'));
  expect(window.location.search).toBe('');
  expect(screen.queryByText('历史回答')).not.toBeInTheDocument();
});

it('ignores late recovery from an older selection and from a cleared conversation', async () => {
  const old = deferred<Awaited<ReturnType<typeof agentSessionApi.continuation>>>();
  api.continuation.mockImplementation((id) => id === 's1' ? old.promise : Promise.resolve(context('s2', { governanceTarget: { assetId: 2 } })));
  render(<AiAgentPage />);
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  fireEvent.click(await screen.findByText('会话二'));
  await screen.findByText('资产 #2 治理解读');
  await act(async () => old.resolve(context('s1', { governanceTarget: { assetId: 1 } })));
  expect(screen.queryByText('资产 #1 治理解读')).not.toBeInTheDocument();
  expect(window.location.search).toBe('?sessionId=s2');
  const late = deferred<Awaited<ReturnType<typeof agentSessionApi.continuation>>>();
  api.continuation.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByText('会话一'));
  fireEvent.click(screen.getByText('新建会话'));
  await act(async () => late.resolve(context('s1', { governanceTarget: { assetId: 1 } })));
  expect(window.location.search).toBe('');
  expect(screen.queryByText('资产 #1 治理解读')).not.toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).not.toBeDisabled();
});

it('blocks after an uncertain submission and refreshes persisted state before another attempt', async () => {
  chat.submit.mockRejectedValueOnce(new Error('network timeout'));
  render(<AiAgentPage />);
  await screen.findByText('质量执行 Q_1 解读与排查');
  send();
  expect(await screen.findByText(/提交未确认/)).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(stream).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('刷新会话'));
  await waitFor(() => expect(screen.getByLabelText('会话输入')).not.toBeDisabled());
  expect(chat.submit).toHaveBeenCalledTimes(1);
});

it('automatically reloads completed history without submitting or replaying the stream', async () => {
  jest.useFakeTimers();
  api.continuation.mockResolvedValueOnce(context('s1', { status: 'RUNNING' }));
  api.history.mockResolvedValueOnce([{ role: 'assistant', content: '历史回答' }] as any)
    .mockResolvedValue([{ role: 'assistant', content: '新的最终结果' }] as any);
  render(<AiAgentPage />);
  await screen.findByText('刷新会话');
  await act(async () => { jest.advanceTimersByTime(3000); });
  expect(await screen.findByText('新的最终结果')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).not.toBeDisabled();
  expect(api.history).toHaveBeenCalledTimes(2);
  expect(chat.submit).not.toHaveBeenCalled(); expect(stream).not.toHaveBeenCalled();
});

it('automatically restores a newly waiting question and preserves its original resume call', async () => {
  jest.useFakeTimers();
  api.continuation.mockResolvedValue(context('s1', { status: 'WAITING_INPUT',
    clarification: { toolCallId: 'c-auto', toolName: 'request_clarification', question: '{"question":"选择范围","options":["昨天"]}' } }))
    .mockResolvedValueOnce(context('s1', { status: 'RUNNING' }));
  render(<AiAgentPage />);
  await screen.findByText('刷新会话');
  await act(async () => { jest.advanceTimersByTime(3000); });
  expect(await screen.findByText('选择范围')).toBeInTheDocument();
  expect(chat.submit).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /昨\s*天/ }));
  await waitFor(() => expect(chat.submit).toHaveBeenCalledWith({ sessionId: 's1', toolResults: [
    { toolCallId: 'c-auto', toolName: 'request_clarification', output: '昨天' }] }));
});

it('stops exactly the restored turn and reads its outcome before unlocking the composer', async () => {
  mockCanRun = true;
  const stopped = deferred<boolean>(); chat.cancelTurn.mockReturnValueOnce(stopped.promise);
  api.continuation.mockResolvedValue(context('s1', { status: 'CANCELLED' }))
    .mockResolvedValueOnce(context('s1', { turnId: 't-old', status: 'RUNNING' }));
  render(<AiAgentPage />);
  fireEvent.click(await screen.findByText('停止本轮'));
  expect(chat.cancelTurn).toHaveBeenCalledWith('t-old');
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(screen.getByText('停止本轮').closest('button')).toBeDisabled();
  await act(async () => stopped.resolve(true));
  await waitFor(() => expect(screen.getByLabelText('会话输入')).not.toBeDisabled());
  expect(api.history).toHaveBeenCalledTimes(2);
  expect(screen.getByText(/最近轮次状态：已停止/)).toBeInTheDocument();
  expect(chat.submit).not.toHaveBeenCalled();
});

it('disables restored stop when CHAT_RUN is missing', async () => {
  api.continuation.mockResolvedValueOnce(context('s1', { status: 'RUNNING' }));
  render(<AiAgentPage />);
  expect((await screen.findByText('停止本轮')).closest('button')).toBeDisabled();
  expect(chat.cancelTurn).not.toHaveBeenCalled();
});

it('does not declare stop success when its acknowledgement is lost', async () => {
  mockCanRun = true; chat.cancelTurn.mockRejectedValueOnce(new Error('timeout'));
  api.continuation.mockResolvedValueOnce(context('s1', { status: 'RUNNING' }));
  render(<AiAgentPage />);
  fireEvent.click(await screen.findByText('停止本轮'));
  expect(await screen.findByText(/停止请求未确认/)).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(screen.queryByText(/最近轮次状态：已停止/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('刷新会话'));
  await waitFor(() => expect(screen.getByLabelText('会话输入')).not.toBeDisabled());
});

it('ignores a late stop acknowledgement after selecting another session', async () => {
  mockCanRun = true;
  const stopped = deferred<boolean>(); chat.cancelTurn.mockReturnValueOnce(stopped.promise);
  api.continuation.mockImplementation(async (id) => context(id, id === 's1'
    ? { status: 'RUNNING' } : { governanceTarget: { assetId: 2 } }));
  render(<AiAgentPage />);
  fireEvent.click(await screen.findByText('停止本轮'));
  fireEvent.click(await screen.findByText('会话二'));
  await screen.findByText('资产 #2 治理解读');
  await act(async () => stopped.resolve(true));
  expect(window.location.search).toBe('?sessionId=s2');
  expect(api.history).toHaveBeenLastCalledWith('s2');
  expect(screen.getByLabelText('会话输入')).not.toBeDisabled();
});

it('keeps history and blocks sending after an automatic state check fails', async () => {
  jest.useFakeTimers();
  api.continuation.mockRejectedValue(new Error('HTTP 403'))
    .mockResolvedValueOnce(context('s1', { status: 'RUNNING' }));
  render(<AiAgentPage />);
  await screen.findByText('刷新会话');
  await act(async () => { jest.advanceTimersByTime(3000); });
  expect(await screen.findByText(/状态检查失败/)).toBeInTheDocument();
  expect(screen.getByText('历史回答')).toBeInTheDocument();
  expect(screen.getByLabelText('会话输入')).toBeDisabled();
  expect(chat.submit).not.toHaveBeenCalled();
});
