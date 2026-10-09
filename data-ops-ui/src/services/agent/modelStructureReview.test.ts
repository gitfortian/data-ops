import { readModelStructureReviewTarget } from './modelStructureReview';
import { governanceEntryPath, governanceSourcePath, parseGovernanceTarget } from './governance';
import { readContinuation, type SessionContinuation } from './continuation';
import { evidenceCards } from './suggestions';

const target = { purpose: 'MODEL_STRUCTURE_REVIEW' as const, modelStructureReview: {
  modelId: '9223372036854775807', baselineVersionNo: 3, definition: 'a'.repeat(64),
} };
const entry = governanceEntryPath(target).split('?')[1];

it('preserves large model identity, precise version and definition across entry and continuation', () => {
  expect(parseGovernanceTarget(entry)).toEqual(target);
  expect(governanceSourcePath(target)).toBe('/modeling/models/9223372036854775807?tab=version&reviewVersion=3');
  expect(readContinuation({ sessionId: 's', turnId: 't', status: 'COMPLETED', governanceTarget: target }, 's').governanceTarget).toEqual(target);
});
it.each(['&assetId=7', '&qualityExecutionNo=Q1', '&qualityBaselineExecutionNo=Q0', '&qualityMonitorId=7',
  '&consumerProductIdentity=7', '&reviewModelId=8', '&reviewBaselineVersionNo=4', '&reviewDefinition=' + 'b'.repeat(64),
  '&purpose=CONSUMER_VERSION_IMPACT'])('rejects mixed and repeated parameters %s', (suffix) => {
  expect(parseGovernanceTarget(entry + suffix)).toBeNull();
});
it('does not fall back to ordinary conversation for missing or partial context', () => {
  expect(parseGovernanceTarget(entry.replace('purpose=MODEL_STRUCTURE_REVIEW&', ''))).toBeNull();
  expect(parseGovernanceTarget('?assetId=7&reviewModelId=7')).toBeNull();
  expect(parseGovernanceTarget(entry.replace('reviewBaselineVersionNo=3', 'reviewBaselineVersionNo=03'))).toBeNull();
  expect(() => readContinuation({ sessionId: 's', turnId: 't', status: 'COMPLETED',
    governanceTarget: { ...target, assetId: 7 } } as unknown as SessionContinuation, 's')).toThrow();
});
it.each([{ modelId: 7 }, { modelId: '01' }, { modelId: '9223372036854775808' }, { baselineVersionNo: 0 },
  { baselineVersionNo: 2147483648 }, { baselineVersionNo: 1.5 }, { definition: 'unprepared' }, { rawSql: 'select secret' }])('rejects malformed target %j', (overrides) => {
  expect(() => readModelStructureReviewTarget({ ...target.modelStructureReview, ...overrides })).toThrow();
});
it('only accepts the fixed internal model version evidence backlink', () => {
  const card = { id: 'E1234ABCD', owner: 'MODELING', reference: 'structureReview', status: 'OK',
    path: governanceSourcePath(target), observedAt: '2026-10-09', sourceUpdatedAt: 'unknown' };
  const evidence = (path: string) => `\`\`\`yak-evidence\n${JSON.stringify([{ ...card, path }])}\n\`\`\``;
  expect(evidenceCards(evidence(card.path))).toEqual([card]);
  for (const path of [card.path + '&redirect=evil', card.path.replace('reviewVersion=3', 'reviewVersion=03'),
    card.path.replace('tab=version', 'tab=mapping'), card.path.replace('reviewVersion=3', 'reviewVersion=2147483648'), '//evil.invalid']) expect(evidenceCards(evidence(path))).toEqual([]);
});
