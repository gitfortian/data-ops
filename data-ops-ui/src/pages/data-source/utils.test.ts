import {
  buildSubmitPayload,
  normalizeConnectionFormValues,
  parseOriginalJson,
  serializeKeyValueRows,
} from './utils';

describe('datasource utils', () => {
  it('builds the backend connection parameter contract', () => {
    const payload = buildSubmitPayload(
      'MYSQL',
      {
        name: '业务库',
        environment: 'PROD',
        remark: '核心业务',
      },
      {
        host: '127.0.0.1',
        port: 3306,
        password: '******',
      },
    );

    expect(payload).toEqual({
      name: '业务库',
      environment: 'PROD',
      remark: '核心业务',
      dbType: 'MYSQL',
      connectionParams: JSON.stringify({
        host: '127.0.0.1',
        port: 3306,
        password: '******',
        dbType: 'MYSQL',
      }),
    });
  });

  it('serializes advanced property rows back into the plugin object contract', () => {
    expect(
      serializeKeyValueRows([
        { key: ' useSSL ', value: 'false' },
        { key: 'serverTimezone', value: 'UTC' },
        { key: '', value: 'ignored' },
      ]),
    ).toEqual({
      useSSL: 'false',
      serverTimezone: 'UTC',
    });

    expect(
      normalizeConnectionFormValues({
        host: '127.0.0.1',
        properties: [{ key: 'useSSL', value: 'false' }],
      }),
    ).toEqual({
      host: '127.0.0.1',
      properties: { useSSL: 'false' },
    });
  });

  it('keeps advanced properties as a JSON object in submit payload', () => {
    const payload = buildSubmitPayload(
      'MYSQL',
      { name: 'mysql', environment: 'DEVELOP' },
      {
        host: '127.0.0.1',
        properties: [
          { key: 'useSSL', value: 'false' },
          { key: 'serverTimezone', value: 'UTC' },
        ],
      },
    );

    expect(JSON.parse(payload.connectionParams)).toMatchObject({
      dbType: 'MYSQL',
      properties: {
        useSSL: 'false',
        serverTimezone: 'UTC',
      },
    });
  });

  it('returns a stable parsed config for the same detail response', () => {
    const originalJson = '{"host":"db","password":"******"}';

    const first = parseOriginalJson(originalJson);
    const second = parseOriginalJson(originalJson);

    expect(second).toBe(first);
    expect(second).toEqual({ host: 'db', password: '******' });
  });

  it('returns an empty object for malformed detail JSON', () => {
    expect(parseOriginalJson('{broken')).toEqual({});
  });
});
