import type { SaveRulePayload } from './types';

/** Conservative form comparison; display name and activation are not check conditions. */
export function sameRuleConditions(left: SaveRulePayload, right: SaveRulePayload): boolean {
  const conditions = (rule: SaveRulePayload) => JSON.stringify([
    rule.templateId, rule.columnName || '', rule.operator || 'EQ',
    rule.threshold ?? null, rule.thresholdEnd ?? null,
    [...new Set(rule.enumValues || [])].sort(), rule.customSql || '',
  ]);
  return conditions(left) === conditions(right);
}

export function relatedFormRules(candidate: SaveRulePayload, rules: SaveRulePayload[]): SaveRulePayload[] {
  return rules.filter((rule) => rule.templateId === candidate.templateId
    && (rule.columnName || '') === (candidate.columnName || ''));
}
