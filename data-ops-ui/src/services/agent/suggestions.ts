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

function isGovernanceSourcePath(path: string): boolean {
  const numeric = path.match(/^\/(?:data-asset\/detail|data-quality\/monitor|dataset)\/([1-9]\d*)$/);
  return numeric ? Number.isSafeInteger(Number(numeric[1]))
    : /^\/data-quality\/execution\/[A-Za-z0-9_-]{1,128}$/.test(path);
}

function readObservedAt(value: unknown): string | null {
  if (typeof value === 'string') return value;
  // Previous final-message encoders serialized Instant as epoch seconds, not milliseconds.
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 253402300799) {
    return new Date(value * 1000).toISOString();
  }
  return null;
}

export function evidenceCards(text: string): GovernanceEvidenceCard[] {
  try {
    const blocks = [...text.matchAll(/```yak-evidence\s*\n([\s\S]*?)\n```/g)];
    if (blocks.length !== 1) return [];
    const value = JSON.parse(blocks[0][1]);
    if (!Array.isArray(value)) return [];
    const ids = value.filter((card) => card && typeof card.id === 'string').map((card) => card.id);
    // Even an invalid duplicate makes identity ambiguous; filtering it first would choose a winner.
    if (new Set(ids).size !== ids.length) return [];
    return value.filter((card) => card &&
      typeof card.id === 'string' && /^E[A-F0-9]{8}$/.test(card.id)
      && typeof card.path === 'string' && isGovernanceSourcePath(card.path)
      && ['OK', 'EMPTY', 'NOT_APPLICABLE', 'UNAVAILABLE', 'PERMISSION_DENIED'].includes(card.status)
      && readObservedAt(card.observedAt) !== null
      && ['owner', 'reference', 'sourceUpdatedAt'].every((key) => typeof card[key] === 'string'))
      .map((card) => ({ ...card, observedAt: readObservedAt(card.observedAt)! }));
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
    const valid = Array.isArray(value) ? value.filter((fact) => fact && refs.has(fact.evidenceRef)
      && typeof fact.field === 'string' && fact.field.length <= 512
      && typeof fact.value === 'string' && fact.value.length <= 512) : [];
    const key = (fact: { evidenceRef: string; field: string }) => JSON.stringify([fact.evidenceRef, fact.field]);
    const counts = new Map<string, number>();
    valid.forEach((fact) => counts.set(key(fact), (counts.get(key(fact)) ?? 0) + 1));
    return valid.filter((fact) => counts.get(key(fact)) === 1).slice(0, 20);
  } catch { return []; }
}
