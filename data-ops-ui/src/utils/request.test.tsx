/**
 * 请求层错误传递契约（主心骨规矩 S11：出错必须让用户看见，而且要说后端说的原话）。
 *
 * 锁死三件事：
 * 1. reject 模式（getData/postData 等）：业务错误必须带着后端原话 + 错误码拒绝，
 *    调用方的 catch 拿到就能展示——禁止把错误改写成笼统文案；
 * 2. resolve 模式（信封兼容）：业务错误必须弹提示，不许静默放行；
 * 3. HTTP/网络异常必须弹提示后继续抛出；用户主动中断不弹提示。
 *
 * 2026-09-21 M-2 事故（创建失败零提示）的根因是调用方 catch 吞错；
 * 本测试管不住调用方，但保证「只要调用方想展示，原话一定拿得到」。
 *
 * 注：jsdom 环境没有 fetch/Response 全局，这里用最小响应对象替身。
 */
import { notifyOnce } from '@/utils/notifyOnce';

import request, { BizError, resetAuthenticationFailure } from './request';

jest.mock('@/utils/notifyOnce', () => ({ notifyOnce: jest.fn() }));
jest.mock('umi', () => ({ history: { replace: jest.fn() } }));

const URL = '/api/v1/metrics';

const fakeResponse = (
  body: unknown,
  { status = 200, contentType = 'application/json' as string } = {}
) => ({
  status,
  ok: status >= 200 && status < 300,
  url: URL,
  headers: {
    get: (key: string) =>
      key.toLowerCase() === 'content-type' ? contentType : null,
  },
  clone: () => fakeResponse(body, { status, contentType }),
  json: async () => body,
  text: async () =>
    typeof body === 'string' ? body : JSON.stringify(body),
});

const mockFetch = (impl: () => Promise<never> | Promise<unknown>) => {
  const spy = jest.spyOn(global, 'fetch').mockImplementation(impl as never);
  (window as unknown as { fetch: unknown }).fetch = spy;
  return spy;
};

const bizEnvelope = (code: number, message: string) => ({ code, message });

describe('请求层错误传递契约（S11）', () => {
  beforeEach(() => {
    (notifyOnce as jest.Mock).mockClear();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('reject 模式：业务错误带着后端原话和错误码拒绝', async () => {
    mockFetch(() =>
      Promise.resolve(fakeResponse(bizEnvelope(44002, '指标编码已存在：qa')))
    );

    await expect(
      request(URL, { method: 'POST', data: {}, businessErrorMode: 'reject' })
    ).rejects.toMatchObject({
      name: 'BizError',
      code: 44002,
      message: '指标编码已存在：qa',
    });
  });

  it('reject 模式：未登录错误走认证失效出口并拒绝', async () => {
    mockFetch(() => Promise.resolve(fakeResponse(bizEnvelope(2001, '用户未登录'))));

    await expect(
      request(URL, { method: 'GET', businessErrorMode: 'reject' })
    ).rejects.toBeInstanceOf(BizError);
    expect(notifyOnce).toHaveBeenCalledWith(
      'authentication',
      expect.objectContaining({ type: 'warning' })
    );
  });

  it('resolve 模式：业务错误弹提示且不静默', async () => {
    mockFetch(() =>
      Promise.resolve(fakeResponse(bizEnvelope(44002, '标准编码已存在：x')))
    );

    // resolve 模式（信封兼容）下 umi-request 返回解析后的信封体，
    // 调用方靠 code 自行判断；关键是错误提示必须弹、不允许静默放行。
    const resolved = (await request('/api/v1/semantic/standards', {
      method: 'POST',
      data: {},
      businessErrorMode: 'resolve',
    })) as { code?: number };

    expect(resolved.code).toBe(44002);
    expect(notifyOnce).toHaveBeenCalledWith(
      expect.stringContaining('business:yak-ops:44002'),
      expect.objectContaining({ description: '标准编码已存在：x' })
    );
  });

  it('HTTP 500：弹提示后继续抛出，失败请求不进入成功链路', async () => {
    mockFetch(() =>
      Promise.resolve(
        fakeResponse('<html>gateway error</html>', {
          status: 500,
          contentType: 'text/html',
        })
      )
    );

    await expect(request(URL, { method: 'GET' })).rejects.toBeTruthy();
    expect(notifyOnce).toHaveBeenCalledWith(
      expect.stringContaining('http:500:'),
      expect.objectContaining({ type: 'error' })
    );
  });

  it('网络异常：弹提示后继续抛出', async () => {
    mockFetch(() => Promise.reject(new TypeError('Failed to fetch')));

    await expect(request(URL, { method: 'GET' })).rejects.toBeTruthy();
    expect(notifyOnce).toHaveBeenCalledWith(
      'network',
      expect.objectContaining({ type: 'warning' })
    );
  });

  it('用户主动中断：不弹提示，直接抛出', async () => {
    const abortError = new Error('aborted');
    abortError.name = 'AbortError';
    mockFetch(() => Promise.reject(abortError));

    await expect(request(URL, { method: 'GET' })).rejects.toBeTruthy();
    expect(notifyOnce).not.toHaveBeenCalled();
  });

  it('成功响应：不弹任何错误提示', async () => {
    mockFetch(() =>
      Promise.resolve(fakeResponse({ code: 200, message: '成功', data: { id: 1 } }))
    );

    await request(URL, { method: 'GET' });
    expect(notifyOnce).not.toHaveBeenCalled();
  });
});


describe('A5.2 shared HTTP boundary: 401, 403 and project context', () => {
  beforeEach(() => {
    resetAuthenticationFailure();
    (notifyOnce as jest.Mock).mockClear();
    window.localStorage.setItem('yak-security.current-project-id', '71');
  });

  afterEach(() => {
    window.localStorage.removeItem('yak-security.current-project-id');
    jest.restoreAllMocks();
  });

  it('sends the stored project header with cookie credentials for a scoped management API', async () => {
    const spy = mockFetch(() =>
      Promise.resolve(fakeResponse({ code: 200, data: { id: 1 } }))
    );

    await request(URL, { method: 'GET', businessErrorMode: 'reject' });

    const fetchOptions = spy.mock.calls[0]?.[1] as
      | { credentials?: string; headers?: Record<string, string> }
      | undefined;
    expect(fetchOptions?.credentials).toBe('include');
    expect(fetchOptions?.headers).toEqual(
      expect.objectContaining({ 'X-YAK-SECURITY-PROJECT-ID': '71' })
    );
    expect(notifyOnce).not.toHaveBeenCalled();
  });

  it('never sends the stored project header to explicitly global runtime APIs', async () => {
    const spy = mockFetch(() =>
      Promise.resolve(fakeResponse({ code: 200, data: [] }))
    );

    await request('/api/v1/compute-environments', { method: 'GET' });

    const fetchOptions = spy.mock.calls[0]?.[1] as
      | { headers?: Record<string, string> }
      | undefined;
    expect(fetchOptions?.headers?.['X-YAK-SECURITY-PROJECT-ID']).toBeUndefined();
  });

  it('HTTP 403 is denied, reported as authorization failure, not treated as login expiry', async () => {
    mockFetch(() => Promise.resolve(
      fakeResponse({ code: 403, msg: '无权访问当前项目' }, { status: 403 })
    ));

    await expect(request(URL, { method: 'GET' })).rejects.toBeTruthy();
    // The JSON business envelope is handled before umi-request's HTTP status
    // fallback; 403 still denotes authorization denial, not session expiry.
    expect(notifyOnce).toHaveBeenCalledWith(
      expect.stringContaining('business:yak-ops:403:'),
      expect.objectContaining({ title: '操作失败', description: '无权访问当前项目' })
    );
    expect(notifyOnce).not.toHaveBeenCalledWith(
      'authentication',
      expect.anything()
    );
  });

  it('HTTP 401 invokes the single authentication expiry exit', async () => {
    mockFetch(() => Promise.resolve(
      fakeResponse({ code: 401, msg: '会话已过期' }, { status: 401 })
    ));

    await expect(request(URL, { method: 'GET' })).rejects.toBeTruthy();
    expect(notifyOnce).toHaveBeenCalledWith(
      'authentication',
      expect.objectContaining({ title: '登录状态失效' })
    );
  });

  it('skipErrorHandler suppresses global notifications but still rejects HTTP errors', async () => {
    mockFetch(() => Promise.resolve(
      fakeResponse({ code: 403, msg: '不足权限' }, { status: 403 })
    ));

    await expect(request(URL, { method: 'GET', skipErrorHandler: true }))
      .rejects.toBeTruthy();
    expect(notifyOnce).not.toHaveBeenCalled();
  });
});
