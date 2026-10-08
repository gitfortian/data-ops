import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import StandardMatchPanel from './StandardMatchPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { receiptText, scenario } from '../../../tests/fixtures/agent-scenarios';

jest.mock('@/services/agent', () => ({
  agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn(), validateStandardMatch: jest.fn() },
  agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn(),
}));
const api = jest.mocked(agentSessionApi);
const chat = jest.mocked(agentChatApi);
const stream = jest.mocked(streamTurnEvents);
const fixture = scenario();
if (fixture.value.kind !== 'STANDARD_MATCH') throw new Error('fixture');
const value = fixture.value;
const props = { target: value.target, definition: value.expectedDefinition, disabled: false };
const keyword = () => screen.getByPlaceholderText('字段/标准检索词（可空，读取有界目录）');
const generate = () => fireEvent.click(screen.getByRole('button', { name: /生成类型候选/ }));
const refresh = () => fireEvent.click(screen.getByRole('button', { name: '刷新核对' }));
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
beforeEach(() => {
  jest.resetAllMocks();
  chat.submit.mockResolvedValue({ turnId: 't1' });
  chat.cancelTurn.mockResolvedValue(true);
  stream.mockResolvedValue(undefined);
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId }));
  api.history.mockResolvedValue([{ role: 'assistant', content: fixture.text, turnId: 't1' }]);
  chat.validateStandardMatch.mockResolvedValue(value);
});

it('invalidates old search candidates immediately and never restores them by refreshing their old turn', async () => {
  const apply = jest.fn(); render(<StandardMatchPanel {...props} onApply={apply} />);
  generate(); await screen.findByText('带入类型引用');
  fireEvent.change(keyword(), { target: { value: ' 新条件 ' } });
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
  refresh();
  await waitFor(() => expect(screen.getByText('生成类型候选').closest('button')).not.toBeDisabled());
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
  const next = { ...value, target: { ...value.target, keyword: '新条件' } };
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId,
    governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: next.target } }));
  api.history.mockResolvedValue([{ role: 'assistant', content: receiptText(next), turnId: 't1' }]);
  generate(); await screen.findByText('带入类型引用');
  expect(chat.submit).toHaveBeenCalledTimes(2);
  expect(chat.submit.mock.calls[1][0].governanceTarget).toEqual({ purpose: 'STANDARD_MATCH', standardMatch: next.target });
  expect(chat.validateStandardMatch).not.toHaveBeenCalled(); expect(apply).not.toHaveBeenCalled();
});

it('keeps an active old search blocked until its exact terminal state is read', async () => {
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status: 'RUNNING' }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); await screen.findByText('本轮仍在排队或推理，可停止或刷新核对。');
  fireEvent.change(keyword(), { target: { value: '另一条件' } });
  generate(); expect(chat.submit).toHaveBeenCalledTimes(1);
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId }));
  refresh(); await screen.findByText('检索条件已变化，旧候选已失效，请重新生成。');
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
  expect(api.history).not.toHaveBeenCalled();
  expect(screen.getByText('生成类型候选').closest('button')).not.toBeDisabled();
});

it('serializes duplicate stops with refresh and ignores late stream completion', async () => {
  const live = deferred<void>(); stream.mockReturnValue(live.promise);
  const stopped = deferred<boolean>(); chat.cancelTurn.mockReturnValue(stopped.promise);
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status: 'CANCELLED' }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); const stop = await screen.findByText('停止本轮');
  act(() => { fireEvent.click(stop); fireEvent.click(stop); });
  refresh(); generate();
  expect(chat.cancelTurn).toHaveBeenCalledTimes(1); expect(chat.cancelTurn).toHaveBeenCalledWith('t1');
  expect(api.continuation).not.toHaveBeenCalled(); expect(chat.submit).toHaveBeenCalledTimes(1);
  expect(keyword()).toBeDisabled();
  await act(async () => stopped.resolve(true));
  expect(await screen.findByText('本轮生成已停止，可重新生成。')).toBeInTheDocument();
  await act(async () => live.resolve());
  expect(api.continuation).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});

it('uses actual completion after a lost stop acknowledgement and does not infer cancellation', async () => {
  const live = deferred<void>(); stream.mockReturnValue(live.promise);
  chat.cancelTurn.mockRejectedValue(new Error('private cancellation transport'));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); fireEvent.click(await screen.findByText('停止本轮'));
  expect(await screen.findByText('带入类型引用')).toBeInTheDocument();
  expect(screen.queryByText(/已停止/)).not.toBeInTheDocument();
  expect(screen.queryByText(/private/)).not.toBeInTheDocument();
  await act(async () => live.resolve());
});

it('finishes a pending refresh before allowing an exact stop', async () => {
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status: 'RUNNING' }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); await screen.findByText('本轮仍在排队或推理，可停止或刷新核对。');
  const checked = deferred<Awaited<ReturnType<typeof agentSessionApi.continuation>>>();
  api.continuation.mockReturnValueOnce(checked.promise);
  refresh(); fireEvent.click(screen.getByText('停止本轮')); refresh();
  expect(chat.cancelTurn).not.toHaveBeenCalled(); expect(api.continuation).toHaveBeenCalledTimes(2);
  await act(async () => checked.resolve({ ...fixture.continuation, sessionId: chat.submit.mock.calls[0][0].sessionId, status: 'RUNNING' }));
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status: 'CANCELLED' }));
  fireEvent.click(screen.getByText('停止本轮'));
  await screen.findByText('本轮生成已停止，可重新生成。');
  expect(chat.cancelTurn).toHaveBeenCalledTimes(1);
});

it('keeps an uncertain stop blocked until a later refresh proves cancellation', async () => {
  const live = deferred<void>(); stream.mockReturnValue(live.promise);
  chat.cancelTurn.mockRejectedValue(new Error('private transport'));
  api.continuation.mockRejectedValueOnce(new Error('private state read'));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); fireEvent.click(await screen.findByText('停止本轮'));
  await screen.findByText('停止结果尚未确认，请刷新核对或查看原会话。');
  generate(); expect(chat.submit).toHaveBeenCalledTimes(1);
  expect(screen.queryByText(/private/)).not.toBeInTheDocument();
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status: 'CANCELLED' }));
  refresh(); await screen.findByText('本轮生成已停止，可重新生成。');
  expect(screen.getByText('生成类型候选').closest('button')).not.toBeDisabled();
  await act(async () => live.resolve());
});

it('never refreshes a replacement panel or repeats an in-flight stop on disposal', async () => {
  const live = deferred<void>(); stream.mockReturnValue(live.promise);
  const stopped = deferred<boolean>(); chat.cancelTurn.mockReturnValue(stopped.promise);
  const apply = jest.fn(); const view = render(<StandardMatchPanel {...props} onApply={apply} />);
  generate(); fireEvent.click(await screen.findByText('停止本轮'));
  view.rerender(<StandardMatchPanel {...props} target={{ ...props.target, columnName: 'another' }} onApply={apply} />);
  await act(async () => { stopped.resolve(true); live.resolve(); });
  expect(chat.cancelTurn).toHaveBeenCalledTimes(1);
  expect(api.continuation).not.toHaveBeenCalled(); expect(apply).not.toHaveBeenCalled();
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});

it('does not revive a validation after permission is removed and restored', async () => {
  const checked = deferred<typeof value>(); chat.validateStandardMatch.mockReturnValue(checked.promise);
  const apply = jest.fn(); const view = render(<StandardMatchPanel {...props} onApply={apply} />);
  generate(); fireEvent.click(await screen.findByText('带入类型引用'));
  await waitFor(() => expect(chat.validateStandardMatch).toHaveBeenCalled());
  view.rerender(<StandardMatchPanel {...props} disabled onApply={apply} />);
  view.rerender(<StandardMatchPanel {...props} onApply={apply} />);
  await act(async () => checked.resolve(value));
  expect(apply).not.toHaveBeenCalled(); expect(chat.cancelTurn).not.toHaveBeenCalled();
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});

it('invalidates an unacknowledged submission on target change without depending on a parent key', async () => {
  const submitted = deferred<{ turnId: string }>(); chat.submit.mockReturnValue(submitted.promise);
  const view = render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate();
  view.rerender(<StandardMatchPanel {...props} target={{ ...props.target, businessDescription: '新含义' }} onApply={jest.fn()} />);
  await act(async () => submitted.resolve({ turnId: 'old-confirmed' }));
  expect(chat.cancelTurn).toHaveBeenCalledWith('old-confirmed');
  expect(stream).not.toHaveBeenCalled(); expect(api.continuation).not.toHaveBeenCalled();
});

it('invalidates a late history read after the definition changes', async () => {
  const history = deferred<Awaited<ReturnType<typeof agentSessionApi.history>>>(); api.history.mockReturnValue(history.promise);
  const view = render(<StandardMatchPanel {...props} onApply={jest.fn()} />);
  generate(); await waitFor(() => expect(api.history).toHaveBeenCalled());
  view.rerender(<StandardMatchPanel {...props} definition={'c'.repeat(64)} onApply={jest.fn()} />);
  await act(async () => history.resolve([{ role: 'assistant', content: fixture.text, turnId: 't1' }]));
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument(); expect(chat.cancelTurn).not.toHaveBeenCalled();
});

it.each(['submit', 'continuation', 'history', 'validation', 'blocking reason'] as const)('does not expose arbitrary service text from %s failures', async phase => {
  const secret = 'private-server-exception-token';
  if (phase === 'submit') chat.submit.mockRejectedValue(new Error(secret));
  if (phase === 'continuation') api.continuation.mockRejectedValue(new Error(secret));
  if (phase === 'history') api.history.mockRejectedValue(new Error(secret));
  if (phase === 'validation') chat.validateStandardMatch.mockRejectedValue(new Error(secret));
  if (phase === 'blocking reason') api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, blockingReason: secret }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); generate();
  if (phase === 'validation') fireEvent.click(await screen.findByText('带入类型引用'));
  await screen.findByText(phase === 'validation' ? '候选或来源暂无法核对，请重新加载原编辑器后生成。'
    : '提交或结果尚未确认，请刷新核对或查看原会话，避免重复生成。');
  expect(screen.queryByText(new RegExp(secret))).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '查看原会话' })).toHaveAttribute('href', expect.stringContaining('/ai-agent?sessionId='));
});

it.each(['WAITING_INPUT', null] as const)('keeps %s blocked without inventing a new turn', async status => {
  api.continuation.mockImplementation(async sessionId => ({ ...fixture.continuation, sessionId, status,
    clarification: { toolCallId: 'q1', toolName: 'request_clarification', question: '确认范围' } }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); generate();
  await screen.findByText(status ? '本轮有待答问题，请在原会话完成应答后刷新核对。' : '本轮状态尚未确认，请刷新核对或查看原会话。');
  generate(); expect(chat.submit).toHaveBeenCalledTimes(1); expect(api.history).not.toHaveBeenCalled();
});

it('requires a usable submit acknowledgement before subscribing or cancelling', async () => {
  chat.submit.mockResolvedValue({ turnId: '' });
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); generate();
  await screen.findByText('提交或结果尚未确认，请刷新核对或查看原会话，避免重复生成。');
  expect(stream).not.toHaveBeenCalled(); expect(chat.cancelTurn).not.toHaveBeenCalled();
  generate(); expect(chat.submit).toHaveBeenCalledTimes(1);
});

it('accepts reordered receipt target keys without losing full scope checks', async () => {
  const target = Object.fromEntries(Object.entries(value.target).reverse());
  api.history.mockResolvedValue([{ role: 'assistant', turnId: 't1', content: fixture.text.replace(JSON.stringify(value.target), JSON.stringify(target)) }]);
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); generate();
  expect(await screen.findByText('带入类型引用')).toBeInTheDocument();
});
