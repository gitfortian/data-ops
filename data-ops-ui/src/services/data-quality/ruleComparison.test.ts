import { relatedFormRules, sameRuleConditions } from './ruleComparison';
import type { SaveRulePayload } from './types';

const rule: SaveRulePayload = { templateId: 2, name: '状态', columnName: 'status',
  operator: 'EQ', threshold: 0, enumValues: ['A', 'B'], enabled: false };

test('same conditions ignore name, activation and enum order/repetition', () => {
  expect(sameRuleConditions(rule, { ...rule, name: '另一个名字', enabled: true, enumValues: ['B', 'A', 'A'] })).toBe(true);
});

test.each<Partial<SaveRulePayload>>([
  { templateId: 3 }, { columnName: 'STATUS' }, { operator: 'GT' }, { threshold: 1 },
  { thresholdEnd: 100 }, { enumValues: ['a', 'B'] }, { customSql: 'SELECT 1' },
])('does not infer condition equivalence for %p', (patch) => {
  expect(sameRuleConditions(rule, { ...rule, ...patch })).toBe(false);
});

test('related rules use exact template and field, and preserve all conditions', () => {
  const changed = { ...rule, threshold: 10 };
  expect(relatedFormRules(rule, [changed, { ...rule, templateId: 4 }, { ...rule, columnName: 'Status' }])).toEqual([changed]);
});
