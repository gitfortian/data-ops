import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyMetricResponse, allowedVerdict } from './p0-project-role-version-acceptance.mjs';

test('read-only own-project and exact version evidence is identity-bound', () => {
  assert.equal(classifyMetricResponse(200, { code: 200, data: { id: 42, version: 8 } }, '42'), 'FOUND');
  assert.equal(classifyMetricResponse(200, { code: 200, data: { id: 99 } }, '42'), 'WRONG_ID');
  assert.equal(classifyMetricResponse(200, { code: 200, data: { id: 42, version: 8 } }, 8, 'version'), 'FOUND');
  assert.equal(classifyMetricResponse(200, { code: 200, data: { id: 42, version: 7 } }, 8, 'version'), 'WRONG_ID');
  assert.equal(allowedVerdict('WRONG_ID', 'FOUND'), false);
});

test('forbidden and missing are not treated as successful reads', () => {
  assert.equal(classifyMetricResponse(403, { code: 403 }, 42), 'DENIED');
  assert.equal(classifyMetricResponse(404, { code: 404 }, 42), 'DENIED');
  assert.equal(classifyMetricResponse(200, { code: 403, data: { id: 42 } }, 42), 'DENIED');
  assert.equal(classifyMetricResponse(200, { code: 200, data: null }, 42), 'ABSENT');
  assert.equal(allowedVerdict('DENIED', 'NOT_VISIBLE'), true);
  assert.equal(allowedVerdict('ABSENT', 'NOT_VISIBLE'), true);
  assert.equal(allowedVerdict('FOUND', 'NOT_VISIBLE'), false);
  assert.equal(allowedVerdict('ABSENT', 'DENIED'), false);
  assert.equal(allowedVerdict('FOUND', 'DENIED'), false);
});

test('redirect, unauthenticated, server outage and malformed response never pass denial checks', () => {
  for (const [status, envelope, expected] of [
    [302, null, 'AUTH_FAILURE'],
    [401, { code: 401 }, 'AUTH_FAILURE'],
    [503, { message: 'service down' }, 'UNAVAILABLE'],
    [200, { code: 999, data: null }, 'UNAVAILABLE'],
    [200, { code: 500, data: null }, 'UNAVAILABLE'],
    [200, { success: false, data: null }, 'INDETERMINATE'],
    [200, null, 'INDETERMINATE'],
    [200, { code: 200, data: {} }, 'INDETERMINATE'],
  ]) {
    const actual = classifyMetricResponse(status, envelope, '42');
    assert.equal(actual, expected);
    assert.equal(allowedVerdict(actual, 'DENIED'), false);
    assert.equal(allowedVerdict(actual, 'NOT_VISIBLE'), false);
  }
});
