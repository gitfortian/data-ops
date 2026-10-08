import test from 'node:test';
import assert from 'node:assert/strict';
import { validateInputs, isSuccess, isDenied, runAcceptance } from './a7-project-rbac-real-env.mjs';

const env = {
  YAK_OPS_BASE_URL: 'https://acceptance.example.test',
  YAK_OPS_A7_PRIMARY_PROJECT_ID: '11',
  YAK_OPS_A7_SECONDARY_PROJECT_ID: '22',
  YAK_OPS_A7_METRIC_ID: '99',
  YAK_OPS_USERNAME: 'owner',
  YAK_OPS_PASSWORD: 'owner-secret',
  YAK_OPS_DENIED_USERNAME: 'restricted',
  YAK_OPS_DENIED_PASSWORD: 'restricted-secret',
  YAK_OPS_ACCEPTANCE_COMMIT: 'a'.repeat(40),
};
const response = (data, status = 200, cookie) => new Response(JSON.stringify(data), {
  status, headers: cookie ? { 'set-cookie': cookie } : {},
});
function fakeServer({ leak = false, allowForbidden = false, noMembership = false, allowAnonymous = false } = {}) {
  const calls = [];
  const fetcher = async (url, opts) => {
    const route = new URL(url);
    assert.equal(route.origin, 'https://acceptance.example.test');
    calls.push({ path: route.pathname, method: opts.method, project: opts.headers['X-YAK-SECURITY-PROJECT-ID'],
      cookie: opts.headers.Cookie ?? '', redirect: opts.redirect });
    if (route.pathname.endsWith('/account/login')) {
      const body = JSON.parse(opts.body);
      return response({ code: 200 }, 200, 'sid=' + body.userName + '; HttpOnly; Path=/');
    }
    if (route.pathname.endsWith('/account/current')) {
      const denied = opts.headers.Cookie?.includes('sid=restricted');
      return response({ code: 200, data: { projectList:
        denied ? (noMembership ? [] : [{ id: 11 }]) : [{ id: 11 }, { id: 22 }] } });
    }
    if (route.pathname === '/api/v1/metrics/99') {
      const project = opts.headers['X-YAK-SECURITY-PROJECT-ID'];
      const cookie = opts.headers.Cookie ?? '';
      if (!cookie && !allowAnonymous) return response({ code: 401 }, 401);
      if (cookie.includes('sid=restricted') && !allowForbidden) return response({ code: 403 }, 403);
      if (project === '22' && !leak) return response({ code: 404 }, 404);
      return response({ code: 200, data: { id: 99, metricCode: 'GOLDEN' } });
    }
    throw new Error('unexpected request: ' + route.pathname);
  };
  return { fetcher, calls };
}
test('trusted input and separate Projects are mandatory', () => {
  assert.equal(validateInputs(env).primary, '11');
  assert.throws(() => validateInputs({ ...env, YAK_OPS_A7_SECONDARY_PROJECT_ID: '11' }), /must differ/);
  assert.throws(() => validateInputs({ ...env, YAK_OPS_DENIED_PASSWORD: '' }), /required/);
  assert.throws(() => validateInputs({ ...env, YAK_OPS_BASE_URL: 'http://evil.test' }), /HTTPS root/);
  assert.throws(() => validateInputs({ ...env, YAK_OPS_BASE_URL: 'https://u:p@evil.test/' }), /HTTPS root/);
  assert.throws(() => validateInputs({ ...env, YAK_OPS_BASE_URL: 'https://evil.test/a' }), /HTTPS root/);
});
test('business errors in HTTP 200 responses are still failures', () => {
  assert.equal(isSuccess({ httpStatus: 200, payload: { code: 403 } }), false);
  assert.equal(isDenied({ httpStatus: 200, payload: { code: 403 } }), true);
  assert.equal(isDenied({ httpStatus: 302, payload: null }), false);
});
test('same cookie switches A/B/A, missing credentials and role permission are denied', async () => {
  const { fetcher, calls } = fakeServer();
  const result = await runAcceptance(env, fetcher);
  assert.equal(result.assertions.memberWithoutPermissionForbidden, 'PASSED');
  assert.equal(result.browserInteractionValidated, false);
  assert.equal(result.credentialCaptured, false);
  assert.doesNotMatch(JSON.stringify(result), /owner-secret|restricted-secret|sid=/);
  const reads = calls.filter(c => c.path === '/api/v1/metrics/99');
  assert.deepEqual(reads.map(c => c.project), ['11', '22', '11', '11', '11']);
  assert.equal(reads[0].cookie, reads[1].cookie);
  assert.equal(reads[1].cookie, reads[2].cookie);
  assert.equal(reads[3].cookie, '');
  assert.notEqual(reads[4].cookie, reads[0].cookie);
  assert.ok(calls.every(c => c.redirect === 'manual'));
  assert.ok(reads.every(c => c.method === 'GET'));
});
for (const [name, opts, reason] of [
  ['cross Project data leakage', { leak: true }, /exposed primary/],
  ['restricted role allowed', { allowForbidden: true }, /not forbidden/],
  ['restricted principal not a member', { noMembership: true }, /not a primary Project member/],
  ['anonymous reads allowed', { allowAnonymous: true }, /anonymous Metric read not rejected/],
]) {
  test('fail closed: ' + name, async () => {
    await assert.rejects(runAcceptance(env, fakeServer(opts).fetcher), reason);
  });
}
