import { parseModelMapping } from './modelMapping';
import { readContinuation } from './continuation';
const target = { modelId: 7, columnName: 'user_id', datasourceId: 9, database: 'db', table: 'users', businessDescription: '', keyword: '' };
const value = { kind: 'MODEL_MAPPING', target, expectedDefinition: 'a'.repeat(64), sourceDefinition: 'b'.repeat(64), targetType: 'BIGINT', skillHash: 'c'.repeat(64), skillVersion: 1, truncated: false,
  candidates: [{ sourceColumn: 'buyer_id', type: 'BIGINT', nullable: false, reason: '对应买家' }], questions: [] };
const text = (v: unknown) => `\`\`\`yak-model-mapping\n${JSON.stringify(v)}\n\`\`\``;
it('accepts one server delivery and rejects ambiguous, duplicate or malformed fields', () => {
  expect(parseModelMapping(text(value))).toEqual(value);
  expect(parseModelMapping(text(value) + text(value))).toBeNull();
  expect(parseModelMapping(text({ ...value, candidates: [...value.candidates, ...value.candidates] }))).toBeNull();
  expect(parseModelMapping(text({ ...value, sourceDefinition: 'unknown' }))).toBeNull();
  expect(parseModelMapping(text({ ...value, candidates: [{ ...value.candidates[0], nullable: 'yes' }] }))).toBeNull();
});
it('restores the exact bound table and rejects mixed or oversized session targets', () => {
  const view = { sessionId: 's', status: 'COMPLETED' as const, turnId: 't', governanceTarget: { purpose: 'MODEL_MAPPING' as const, modelMapping: target } };
  expect(readContinuation(view, 's').governanceTarget).toEqual(view.governanceTarget);
  expect(() => readContinuation({ ...view, governanceTarget: { ...view.governanceTarget, assetId: 3 } as never }, 's')).toThrow();
  expect(() => readContinuation({ ...view, governanceTarget: { ...view.governanceTarget, modelMapping: { ...target, table: 'x'.repeat(129) } } }, 's')).toThrow();
});
