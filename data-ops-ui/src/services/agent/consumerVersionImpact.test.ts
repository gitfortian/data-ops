import { readConsumerVersionImpactTarget } from './consumerVersionImpact';
import { governanceEntryPath, governanceSourcePath, parseGovernanceTarget } from './governance';
import { readContinuation, type SessionContinuation } from './continuation';
import { evidenceCards } from './suggestions';

const target = { purpose: 'CONSUMER_VERSION_IMPACT' as const, consumerVersionImpact: {
  productType: 'DATASET' as const, productIdentity: '9007199254740993', sourceVersionIdentity: '999999999999999999999999999999',
} };
const entry = governanceEntryPath(target).split('?')[1];

it('preserves exact string identities across entry, continuation and source backlink', () => {
  expect(parseGovernanceTarget(entry)).toEqual(target);
  expect(governanceSourcePath(target)).toBe('/data-analysis/consumption/DATASET%3A9007199254740993?reviewVersion=999999999999999999999999999999');
  expect(readContinuation({ sessionId: 's1', turnId: 't1', status: 'COMPLETED', governanceTarget: target }, 's1').governanceTarget).toEqual(target);
});
it.each(['&assetId=7', '&qualityExecutionNo=Q1', '&qualityMonitorId=7', '&qualityBaselineExecutionNo=Q0',
  '&consumerVersionIdentity=2', '&purpose=ASSET_IMPACT'])('rejects mixed or duplicate URL scopes %s', (suffix) => {
  expect(parseGovernanceTarget(entry + suffix)).toBeNull();
});
it('rejects missing purpose and malformed persisted targets', () => {
  expect(parseGovernanceTarget(entry.replace('purpose=CONSUMER_VERSION_IMPACT&', ''))).toBeNull();
  expect(() => readContinuation({ sessionId: 's1', turnId: 't1', status: 'COMPLETED',
    governanceTarget: { ...target, assetId: 7 } } as unknown as SessionContinuation, 's1')).toThrow();
});
it.each([
  { productIdentity: 7 }, { productIdentity: '01' }, { productIdentity: '9223372036854775808' },
  { sourceVersionIdentity: '0' }, { sourceVersionIdentity: '1'.repeat(31) }, { sourceVersionIdentity: '../1' },
  { productType: 'ASSET' }, { rawSql: 'select secret' },
])('rejects unsafe or ambiguous identity projection %j', (overrides) => {
  expect(() => readConsumerVersionImpactTarget({ ...target.consumerVersionImpact, ...overrides })).toThrow();
});
it('accepts only a fixed internal exact-version evidence backlink', () => {
  const card = { id: 'E1234ABCD', owner: 'CONSUMPTION', reference: 'versionUsage', status: 'OK',
    path: governanceSourcePath(target), observedAt: '2026-10-09', sourceUpdatedAt: 'unknown' };
  const evidence = (path: string) => `\`\`\`yak-evidence\n${JSON.stringify([{ ...card, path }])}\n\`\`\``;
  expect(evidenceCards(evidence(card.path))).toEqual([card]);
  for (const path of [card.path + '&redirect=evil', card.path.replace('reviewVersion=', 'reviewVersion=0'),
    card.path.replace('DATASET', 'ASSET'), '//evil.invalid']) expect(evidenceCards(evidence(path))).toEqual([]);
});
