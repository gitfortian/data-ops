import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import AiAgentPage from './index';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';

let mockCanRun = false;
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({ can: (code: string) => mockCanRun && code === 'agent:chat:run' }) }));
jest.mock('@/services/agent', () => ({
  agentSessionApi: { list: jest.fn(), history: jest.fn(), continuation: jest.fn(), trace: jest.fn() },
  agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn() }, streamTurnEvents: jest.fn(),
}));
// These adapters exercise page state and payloads; index.test.tsx retains the installed component smoke test.
jest.mock('@ant-design/x/lib/sender', () => ({ __esModule: true, default: (props: any) => (
  <div><textarea aria-label="会话输入" disabled={props.disabled} value={props.value} onChange={(event) => props.onChange(event.target.value)} />
    <button disabled={props.disabled || props.loading} onClick={() => props.onSubmit(props.value)}>发送测试问题</button></div>
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
  jest.clearAllMocks();
  mockCanRun = false;
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
  await waitFor(() => expect(screen.queryByText('AI 需要补充信息')).not.toBeInTheDocument());
});

it('restores description purpose as a description task and keeps it on follow-up', async () => {
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
