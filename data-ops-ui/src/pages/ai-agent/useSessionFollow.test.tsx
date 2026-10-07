import { act, renderHook } from '@testing-library/react';
import { agentSessionApi } from '@/services/agent';
import { SESSION_FOLLOW_INTERVAL, SESSION_FOLLOW_LIMIT, useSessionFollow } from './useSessionFollow';

jest.mock('@/services/agent', () => ({ agentSessionApi: { continuation: jest.fn() } }));
const read = jest.mocked(agentSessionApi.continuation);
let hidden = false;
const view = (extra = {}) => ({ sessionId: 's1', turnId: 't1', status: 'RUNNING' as const, ...extra });
const options = () => ({ sessionId: 's1', turnId: 't1', active: true, generation: 1,
  onActive: jest.fn(), onSettled: jest.fn(), onPause: jest.fn() });
const tick = async (ms = SESSION_FOLLOW_INTERVAL) => { await act(async () => { jest.advanceTimersByTime(ms); }); };

beforeEach(() => {
  jest.useFakeTimers(); jest.clearAllMocks(); hidden = false;
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  read.mockResolvedValue(view());
});
afterEach(() => { jest.useRealTimers(); delete (document as any).hidden; });

it('reads only on schedule and stops at a fixed budget even when the active state changes', async () => {
  const handlers = options();
  const { rerender } = renderHook((props) => useSessionFollow(props), { initialProps: handlers });
  expect(read).not.toHaveBeenCalled();
  read.mockResolvedValueOnce(view({ status: 'QUEUED' }));
  await tick(); rerender({ ...handlers, onActive: jest.fn() });
  for (let index = 1; index < SESSION_FOLLOW_LIMIT; index++) await tick();
  expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT);
  expect(handlers.onPause).toHaveBeenCalledWith(expect.stringContaining('自动检查已暂停'));
  await tick(60000); expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT);
});

it('never overlaps a slow request and checks completion before scheduling another', async () => {
  let resolve!: (result: ReturnType<typeof view>) => void;
  read.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
  const handlers = options();
  renderHook(() => useSessionFollow(handlers));
  await tick(); await tick(30000);
  expect(read).toHaveBeenCalledTimes(1);
  await act(async () => resolve(view()));
  await tick(); expect(read).toHaveBeenCalledTimes(2);
});

it.each(['COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED', 'WAITING_INPUT', 'NEW_TURN'])('reloads once for %s without another read', async (status) => {
  const handlers = options();
  const extra = status === 'NEW_TURN' ? { turnId: 't2' } : status === 'WAITING_INPUT'
    ? { status, clarification: { toolCallId: 'c1', toolName: 'request_clarification', question: '哪个分区？' } } : { status };
  read.mockResolvedValue(view(extra) as any);
  renderHook(() => useSessionFollow(handlers));
  await tick(); await tick(60000);
  expect(handlers.onSettled).toHaveBeenCalledTimes(1);
  expect(handlers.onActive).not.toHaveBeenCalled(); expect(read).toHaveBeenCalledTimes(1);
});

it('pauses while hidden and resumes without restoring spent attempts', async () => {
  const handlers = options();
  renderHook(() => useSessionFollow(handlers));
  for (let index = 0; index < SESSION_FOLLOW_LIMIT - 1; index++) await tick();
  hidden = true;
  act(() => document.dispatchEvent(new Event('visibilitychange')));
  await tick(60000); expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT - 1);
  hidden = false;
  await act(async () => document.dispatchEvent(new Event('visibilitychange')));
  expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT);
  expect(handlers.onPause).toHaveBeenCalledTimes(1);
  await tick(60000); expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT);
});

it.each(['network', 'identity', 'malformed'])('stops and preserves uncertainty on %s failure', async (failure) => {
  const handlers = options();
  if (failure === 'network') read.mockRejectedValue(new Error('HTTP 403'));
  else read.mockResolvedValue(view(failure === 'identity' ? { sessionId: 'other' } : { status: 'unknown' }) as any);
  renderHook(() => useSessionFollow(handlers));
  await tick(); await tick(60000);
  expect(handlers.onPause).toHaveBeenCalledWith(expect.stringContaining('状态检查失败'));
  expect(handlers.onActive).not.toHaveBeenCalled(); expect(handlers.onSettled).not.toHaveBeenCalled();
  expect(read).toHaveBeenCalledTimes(1);
});

it.each(['switch', 'disable', 'unmount', 'refresh'])('ignores in-flight results after %s', async (action) => {
  const handlers = options();
  let resolve!: (result: ReturnType<typeof view>) => void;
  read.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
  const { rerender, unmount } = renderHook((props) => useSessionFollow(props), { initialProps: handlers });
  await tick();
  if (action === 'unmount') unmount();
  else rerender({ ...handlers, ...(action === 'switch' ? { sessionId: 's2' }
    : action === 'disable' ? { active: false } : { generation: 2 }) });
  await act(async () => resolve(view({ status: 'COMPLETED' })));
  expect(handlers.onSettled).not.toHaveBeenCalled(); expect(handlers.onActive).not.toHaveBeenCalled();
});

it('a manual refresh can start a new bounded schedule', async () => {
  const handlers = options();
  const { rerender } = renderHook((props) => useSessionFollow(props), { initialProps: handlers });
  for (let index = 0; index < SESSION_FOLLOW_LIMIT; index++) await tick();
  rerender({ ...handlers, generation: 2 });
  await tick(); expect(read).toHaveBeenCalledTimes(SESSION_FOLLOW_LIMIT + 1);
});
