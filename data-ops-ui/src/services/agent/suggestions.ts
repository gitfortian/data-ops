import type { SaveRulePayload } from '@/services/data-quality';

export interface GovernanceSuggestion {
  kind: 'QUALITY_RULES' | 'ASSET_DESCRIPTION';
  targetId: number;
  expectedDefinition: string;
  rules: Array<SaveRulePayload & { enabled: false }>;
  description?: string;
  evidenceRefs: string[];
}

/** Only the final, server-generated artifact is accepted; malformed or ambiguous blocks fail closed. */
export function parseSuggestion(text: string): GovernanceSuggestion | null {
  const blocks = [...text.matchAll(/```yak-suggestion\s*\n([\s\S]*?)\n```/g)];
  if (blocks.length !== 1) return null;
  try {
    const value = JSON.parse(blocks[0][1]);
    if (!['QUALITY_RULES', 'ASSET_DESCRIPTION'].includes(value.kind)
      || !Number.isSafeInteger(value.targetId) || value.targetId <= 0
      || !/^[a-f0-9]{64}$/.test(value.expectedDefinition)
      || !Array.isArray(value.evidenceRefs) || !value.evidenceRefs.length
      || value.evidenceRefs.some((id: unknown) => typeof id !== 'string' || !/^E[A-F0-9]{8}$/.test(id))) return null;
    if (value.kind === 'ASSET_DESCRIPTION') {
      if (typeof value.description !== 'string' || !value.description.trim() || value.description.length > 1024) return null;
    } else if (!Array.isArray(value.rules) || !value.rules.length || value.rules.length > 5
      || value.rules.some((rule: GovernanceSuggestion['rules'][number]) =>
        !Number.isSafeInteger(rule.templateId) || rule.templateId <= 0
        || typeof rule.name !== 'string' || !rule.name.trim() || rule.name.length > 100
        || !['GT', 'GTE', 'EQ', 'LTE', 'LT', 'BETWEEN'].includes(rule.operator || '')
        || !Number.isFinite(rule.threshold) || rule.enabled !== false || Boolean(rule.customSql))) return null;
    return value;
  } catch { return null; }
}

export interface GovernanceEvidenceCard {
  id: string; owner: string; reference: string; status: string;
  observedAt: string; sourceUpdatedAt: string; path: string;
}

export function evidenceCards(text: string): GovernanceEvidenceCard[] {
  try {
    const match = text.match(/```yak-evidence\s*\n([\s\S]*?)\n```/);
    const value = match ? JSON.parse(match[1]) : [];
    return Array.isArray(value) ? value.filter((card) =>
      typeof card.id === 'string' && /^E[A-F0-9]{8}$/.test(card.id)
      && typeof card.path === 'string' && /^\/(?!\/)[A-Za-z0-9/_?=&%.-]+$/.test(card.path)
      && ['OK', 'EMPTY', 'NOT_APPLICABLE', 'UNAVAILABLE', 'PERMISSION_DENIED'].includes(card.status)
      && ['owner', 'reference', 'observedAt', 'sourceUpdatedAt'].every((key) => typeof card[key] === 'string')) : [];
  } catch { return []; }
}

export function visibleGovernanceText(text: string): string {
  return text.replace(/```yak-(?:suggestion|evidence|facts)[\s\S]*?```/g, '').trim();
}

export function verifiedFacts(text: string): Array<{ evidenceRef: string; field: string; value: string }> {
  try {
    const blocks = [...text.matchAll(/```yak-facts\s*\n([\s\S]*?)\n```/g)];
    if (blocks.length !== 1) return [];
    const refs = new Set(evidenceCards(text).filter((card) => card.status === 'OK').map((card) => card.id));
    const value = JSON.parse(blocks[0][1]);
    return Array.isArray(value) ? value.filter((fact) => fact && refs.has(fact.evidenceRef)
      && typeof fact.field === 'string' && fact.field.length <= 512
      && typeof fact.value === 'string' && fact.value.length <= 512).slice(0, 20) : [];
  } catch { return []; }
}
