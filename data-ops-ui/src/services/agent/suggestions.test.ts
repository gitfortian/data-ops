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
  const text = `结果\n\`\`\`yak-evidence\n${JSON.stringify([card, { ...card, path: '//evil.example' }])}\n\`\`\`
\`\`\`yak-facts\n${JSON.stringify([{ evidenceRef: card.id, field: 'issueCount', value: '3' },
  { evidenceRef: 'E00000000', field: 'issueCount', value: '0' }])}\n\`\`\``;
  expect(evidenceCards(text)).toEqual([card]);
  expect(verifiedFacts(text)).toEqual([{ evidenceRef: card.id, field: 'issueCount', value: '3' }]);
  expect(visibleGovernanceText(text)).toBe('结果');
});
