import { clearStoredProjectId, storeProjectId, PROJECT_ID_HEADER } from '@/utils/security/projectContext';
import { streamTurnEvents } from './api';

type FetchOptions = RequestInit & { headers?: Record<string, string> };

const createHandler = () => ({
  onEvent: jest.fn(),
  onComplete: jest.fn(),
  onError: jest.fn(),
  onReconnect: jest.fn(),
});

describe('A5.2 Agent native fetch / SSE transport compatibility', () => {
  beforeEach(() => {
    storeProjectId('71');
    Object.defineProperty(globalThis, 'TextDecoder', {
      configurable: true,
      value: class {
        decode(value?: Uint8Array) {
          return value ? Buffer.from(value).toString('utf8') : '';
        }
      },
    });
  });

  afterEach(() => {
    clearStoredProjectId();
    jest.restoreAllMocks();
  });

  it('sends cookie and trusted project header while resuming from the acknowledged event cursor', async () => {
    const frame = Buffer.from('id: 9\n' +
      'data: {"type":"turn_started","turnId":"turn-1"}\n\n');
    const read = jest.fn()
      .mockResolvedValueOnce({ done: false, value: frame })
      .mockResolvedValueOnce({ done: true });
    const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      body: { getReader: () => ({ read }) },
    } as unknown as Response);
    const handler = createHandler();

    await streamTurnEvents(
      { turnId: 'turn-1', cursor: 8, signal: new AbortController().signal },
      handler,
    );

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, options] = fetchSpy.mock.calls[0] as [string, FetchOptions];
    expect(url).toContain('/api/v1/agent/chat/turns/turn-1/events?cursor=8');
    expect(options.credentials).toBe('include');
    expect(options.headers).toEqual(expect.objectContaining({
      [PROJECT_ID_HEADER]: '71',
      Accept: 'text/event-stream',
    }));
    expect(handler.onEvent).toHaveBeenCalledWith(expect.objectContaining({
      turnId: 'turn-1',
      type: 'turn_started',
    }));
    expect(handler.onComplete).toHaveBeenCalledTimes(1);
    expect(handler.onError).not.toHaveBeenCalled();
    expect(handler.onReconnect).not.toHaveBeenCalled();
  });

  it('SSE authorization failure is reported to the stream consumer without retrying or claiming completion', async () => {
    const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 403,
      body: null,
      json: async () => ({ message: '该项目无流式读取权限' }),
    } as unknown as Response);
    const handler = createHandler();

    await streamTurnEvents(
      { turnId: 'turn-1', signal: new AbortController().signal },
      handler,
    );

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(handler.onError).toHaveBeenCalledWith('该项目无流式读取权限');
    expect(handler.onComplete).not.toHaveBeenCalled();
    expect(handler.onReconnect).not.toHaveBeenCalled();
  });

  it('user-initiated SSE cancellation is not mistaken for a network failure or a reconnect', async () => {
    const aborted = new Error('aborted');
    aborted.name = 'AbortError';
    const fetchSpy = jest.spyOn(global, 'fetch').mockRejectedValue(aborted);
    const handler = createHandler();

    await streamTurnEvents(
      { turnId: 'turn-1', signal: new AbortController().signal },
      handler,
    );

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(handler.onComplete).toHaveBeenCalledTimes(1);
    expect(handler.onError).not.toHaveBeenCalled();
    expect(handler.onReconnect).not.toHaveBeenCalled();
  });
});
