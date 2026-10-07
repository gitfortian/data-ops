import { evidenceCards, parseSuggestion, verifiedFacts, visibleGovernanceText } from './suggestions';

const suggestion = { kind: 'QUALITY_RULES', targetId: 7, expectedDefinition: 'a'.repeat(64),
  rules: [{ templateId: 1, name: '非空', columnName: 'id', operator: 'GTE', threshold: 99, enabled: false }],
  evidenceRefs: ['E1234ABCD'] };
const block = (value: unknown) => `\`\`\`yak-suggestion\n${JSON.stringify(value)}\n\`\`\``;

it('accepts only a disabled source-bound artifact and rejects ambiguity', () => {
  expect(parseSuggestion(block(suggestion))).toEqual(suggestion);
  expect(parseSuggestion(`${block(suggestion)}\n${block(suggestion)}`)).toBeNull();
  expect(parseSuggestion(block({ ...suggestion, rules: [{ ...suggestion.rules[0], enabled: true }] }))).toBeNull();
  expect(parseSuggestion(block({ ...suggestion, rules: [{ ...suggestion.rules[0], customSql: 'select 1' }] }))).toBeNull();
  expect(parseSuggestion(block({ ...suggestion, expectedDefinition: 'stale' }))).toBeNull();
});

it('renders only internal source links and facts with readable evidence', () => {
  const card = { id: 'E1234ABCD', owner: 'QUALITY', reference: 'run', status: 'OK',
    path: '/data-quality/execution/run', observedAt: '2026-10-05', sourceUpdatedAt: 'unknown' };
  const text = `结果\n\`\`\`yak-evidence\n${JSON.stringify([card, { ...card, id: 'E00000000', path: '//evil.example' }])}\n\`\`\`
\`\`\`yak-facts\n${JSON.stringify([{ evidenceRef: card.id, field: 'issueCount', value: '3' },
  { evidenceRef: 'E00000000', field: 'issueCount', value: '0' }])}\n\`\`\``;
  expect(evidenceCards(text)).toEqual([card]);
  expect(verifiedFacts(text)).toEqual([{ evidenceRef: card.id, field: 'issueCount', value: '3' }]);
  expect(visibleGovernanceText(text)).toBe('结果');
});

const card = { id: 'E1234ABCD', owner: 'QUALITY', reference: 'run', status: 'OK',
  path: '/data-quality/execution/run', observedAt: '2026-10-07', sourceUpdatedAt: 'unknown' };
const evidence = (cards: unknown) => `\`\`\`yak-evidence\n${JSON.stringify(cards)}\n\`\`\``;
const facts = (values: unknown) => `\`\`\`yak-facts\n${JSON.stringify(values)}\n\`\`\``;

it('rejects ambiguous evidence blocks and duplicate identities including invalid duplicates', () => {
  expect(evidenceCards(`${evidence([card])}\n${evidence([card])}`)).toEqual([]);
  expect(evidenceCards(evidence([card, { ...card, path: '//invalid' }]))).toEqual([]);
  expect(evidenceCards(evidence([card, card]))).toEqual([]);
  expect(evidenceCards(evidence([null, card]))).toEqual([card]);
});

it.each(['/admin/users', '//external.invalid', '/data-asset/detail/0', '/data-asset/detail/9007199254740992',
  '/data-quality/monitor/1?redirect=evil', '/data-quality/execution/../../admin', '/data-quality/execution/%2F%2Fevil'])
('rejects non-source or malformed navigation %s', (path) => {
  expect(evidenceCards(evidence([{ ...card, path }]))).toEqual([]);
});

it.each(['/data-asset/detail/1', '/data-quality/monitor/7', '/data-quality/execution/run_2026-10', '/dataset/7'])
('preserves the published source route %s', (path) => {
  expect(evidenceCards(evidence([{ ...card, path }]))).toEqual([{ ...card, path }]);
});

it('only exposes unique fields linked to a unique readable OK source', () => {
  const fact = { evidenceRef: card.id, field: 'status', value: 'ERROR' };
  const text = evidence([card]) + facts([fact, { ...fact, value: 'PASSED' }, { ...fact, field: 'count', value: '2' }]);
  expect(verifiedFacts(text)).toEqual([{ ...fact, field: 'count', value: '2' }]);
  expect(verifiedFacts(evidence([{ ...card, status: 'UNAVAILABLE' }]) + facts([fact]))).toEqual([]);
  expect(verifiedFacts(evidence([card, card]) + facts([fact]))).toEqual([]);
  expect(verifiedFacts(evidence([card]) + facts([fact]) + facts([fact]))).toEqual([]);
});

it('reads the prior Jackson epoch-seconds representation without rewriting or guessing milliseconds', () => {
  expect(evidenceCards(evidence([{ ...card, observedAt: 1791338400.125 }]))[0].observedAt)
    .toBe(new Date(1791338400.125 * 1000).toISOString());
  for (const observedAt of [null, true, {}, -1, 1791338400000]) {
    expect(evidenceCards(evidence([{ ...card, observedAt }]))).toEqual([]);
  }
  expect(evidenceCards(evidence([card]))[0].observedAt).toBe(card.observedAt);
});
