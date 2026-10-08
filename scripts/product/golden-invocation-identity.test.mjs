import assert from 'node:assert/strict';
import { test } from 'node:test';
import { maxRecordId, findNewInvocation } from './golden-invocation-identity.mjs';
import { parseEvidenceJson } from './lossless-json.mjs';

const service = { sourceRevisionId: '9007199254740999', sourceRevisionNo: 4 };
const call = (id, revision = service.sourceRevisionId, apiId = '7', success = true) => ({
  id, sourceRevisionId: revision, sourceRevisionNo: 4, apiId, success,
});

test('BIGINT invocation selection remains exact across the JS safe integer boundary', () => {
  const before = parseEvidenceJson('{"records":[{"id":9007199254740993,"apiId":7}]}').records;
  const after = parseEvidenceJson('{"records":[{"id":9007199254740994,"sourceRevisionId":9007199254740999,"sourceRevisionNo":4,"apiId":7,"success":true},{"id":9007199254740995,"sourceRevisionId":9007199254740999,"sourceRevisionNo":4,"apiId":7,"success":true}]}').records;
  const previous = maxRecordId(before);
  assert.equal(previous, 9007199254740993n);
  assert.equal(findNewInvocation(after, previous, service, '7').id, '9007199254740995');
});

test('old, failed, wrong consumer service or wrong revision evidence cannot be selected', () => {
  const previous = maxRecordId([call('101')]);
  const candidates = [
    call('101'), call('102', service.sourceRevisionId, '9'),
    call('103', service.sourceRevisionId, '7', false),
    call('104', '111'), call('105', '333', '7'),
  ];
  assert.equal(findNewInvocation(candidates, previous, service, '7'), null);
  assert.equal(findNewInvocation([call('106'), ...candidates], previous, service, '7').id, '106');
});

test('unsafe JS Number cannot masquerade as a stable BIGINT identity', () => {
  assert.throws(() => maxRecordId([{ id: 9007199254740993 }]), /unsafe numeric identity/);
  assert.throws(() => findNewInvocation([call('not-a-number')], 0n, service, '7'), /non-decimal/);
  assert.equal(maxRecordId([]), 0n);
  assert.equal(maxRecordId([{ id: 7 }]), 7n);
});
