import {
  API_SUCCESS_CODE,
  extractErrorMessage,
  extractUnknownErrorMessage,
  isApiResponse,
  isSuccessfulResponse,
  isUnauthenticatedResponse,
  protocolForUrl,
} from './response';

describe('API response protocols', () => {
  it('uses the framework success code for every API namespace', () => {
    expect(API_SUCCESS_CODE).toBe(200);
    expect(isSuccessfulResponse({ code: 200 }, 'yak-ops')).toBe(true);
    expect(isSuccessfulResponse({ code: 200 }, 'security')).toBe(true);
    expect(isSuccessfulResponse({ code: 0 }, 'yak-ops')).toBe(false);
    expect(isSuccessfulResponse({ code: 0 }, 'security')).toBe(false);
  });

  it('extracts errors from both envelope variants', () => {
    expect(extractErrorMessage({ code: 999, msg: 'ops failed' })).toBe(
      'ops failed',
    );
    expect(extractErrorMessage({ code: 500, message: 'security failed' })).toBe(
      'security failed',
    );
  });

  it('preserves useful backend messages from HTTP error payloads', () => {
    expect(
      extractUnknownErrorMessage({
        code: 500,
        data: null,
        msg: '数据库连接失败',
      }),
    ).toBe('数据库连接失败');
    expect(extractUnknownErrorMessage({ message: '服务不可用' })).toBe(
      '服务不可用',
    );
    expect(extractUnknownErrorMessage({ detail: '参数格式错误' })).toBe(
      '参数格式错误',
    );
    expect(extractUnknownErrorMessage('纯文本错误')).toBe('纯文本错误');
    expect(extractUnknownErrorMessage({}, 'fallback')).toBe('fallback');
  });

  it('does not expose HTML error pages or oversized diagnostics to users', () => {
    const htmlError =
      '<!doctype html><html><head><title>HTTP Status 500 - Internal Server Error</title></head><body>stack trace</body></html>';

    expect(extractUnknownErrorMessage(htmlError, '服务器发生错误')).toBe(
      '服务器发生错误',
    );
    expect(
      extractUnknownErrorMessage({ message: htmlError }, '请求失败'),
    ).toBe('请求失败');
    expect(
      extractErrorMessage(
        { code: 500, msg: htmlError, message: '数据查询失败' },
        '操作失败',
      ),
    ).toBe('数据查询失败');
    expect(extractUnknownErrorMessage('x'.repeat(300))).toBe(
      `${'x'.repeat(239)}…`,
    );
  });

  it('recognizes authentication failures without treating forbidden as anonymous', () => {
    expect(isUnauthenticatedResponse({ code: 401 }, 'yak-ops')).toBe(true);
    expect(isUnauthenticatedResponse({ code: 401 }, 'security')).toBe(true);
    expect(isUnauthenticatedResponse({ code: 2001 }, 'yak-ops')).toBe(true);
    expect(isUnauthenticatedResponse({ code: 2001 }, 'security')).toBe(true);
    expect(isUnauthenticatedResponse({ code: 403 }, 'security')).toBe(false);
    expect(
      isUnauthenticatedResponse(
        { code: 999, message: 'UNAUTHENTICATED' },
        'security',
      ),
    ).toBe(true);
    expect(
      isUnauthenticatedResponse(
        { code: 999, message: 'session-expired' },
        'security',
      ),
    ).toBe(true);
    expect(
      isUnauthenticatedResponse(
        { code: 999, message: 'token expired' },
        'security',
      ),
    ).toBe(true);
    expect(
      isUnauthenticatedResponse(
        { code: 999, message: 'validation failed' },
        'security',
      ),
    ).toBe(false);
  });

  it('routes only security endpoints to the security protocol', () => {
    expect(isApiResponse({ data: { code: 200 } })).toBe(false);
    expect(protocolForUrl('/yak-security/api/v1/account/current')).toBe(
      'security',
    );
    expect(protocolForUrl('/api/v1/workflows')).toBe('yak-ops');
    expect(protocolForUrl('/api/v1/data-source/page')).toBe('yak-ops');
    expect(protocolForUrl('/api/v1/jobs')).toBe('yak-ops');
  });
});
