import assert from 'node:assert/strict';
import test from 'node:test';
import { parseEvidenceJson } from './lossless-json.mjs';

test('source BIGINT identities preserve exact digits while safe measurements remain numbers', () => {
  assert.deepEqual(parseEvidenceJson('{"id":2106611110803771394,"negative":-2106611110803771394,"rows":3,"ms":0.25}'),
    { id: '2106611110803771394', negative: '-2106611110803771394', rows: 3, ms: 0.25 });
});

test('quoted SQL and escaped JSON strings are not rewritten', () => {
  const sql = 'SELECT 2106611110803771394, "2106611110803771394"';
  assert.equal(parseEvidenceJson(JSON.stringify({ sql })).sql, sql);
});

test('invalid JSON remains invalid and exponent notation retains numeric semantics', () => {
  assert.throws(() => parseEvidenceJson('{"id":02106611110803771394}'), SyntaxError);
  assert.equal(parseEvidenceJson('{"value":1e20}').value, 1e20);
});
