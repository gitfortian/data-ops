import type { MetricChangeReviewTarget } from './governance';
import { readStructuredReceipt } from './structuredReceipt';

export interface ReviewFact { key: string; label: string; value: string }
export interface ReviewDifference { key: string; label: string; before: string | null; after: string | null }
export interface ReviewCoverage { key: string; label: string; status: 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'FORBIDDEN' | 'NOT_APPLICABLE'; description: string }
export interface MetricChangeReviewSource { definition: string; preparedAt: string; differences: ReviewDifference[]; facts: ReviewFact[]; coverage: ReviewCoverage[] }
export interface ReviewStatement { text: string; evidence: ReviewFact[] }
export interface MetricChangeReviewSuggestion {
  kind: 'METRIC_CHANGE_REVIEW'; target: MetricChangeReviewTarget; expectedDefinition: string;
  skillVersion: number; skillHash: string; truncated: false; source: MetricChangeReviewSource;
  candidates: { statements: ReviewStatement[]; checks: ReviewStatement[] }[]; questions: string[];
}
const digest = (v: unknown): v is string => typeof v === 'string' && /^[a-f0-9]{64}$/.test(v);
const text = (v: unknown, max = 512): v is string => typeof v === 'string' && !!v.trim() && v.length <= max;
export function readMetricChangeReviewTarget(value: unknown): MetricChangeReviewTarget {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('版本对目标无效');
  const v = value as MetricChangeReviewTarget;
  if (![v.metricId, v.version, v.publishedVersion, v.publicationEventId].every(n => Number.isSafeInteger(n) && n > 0)
    || !digest(v.definition) || typeof v.businessQuestion !== 'string' || v.businessQuestion.length > 512) throw new Error('版本对目标无效');
  return { metricId: v.metricId, version: v.version, publishedVersion: v.publishedVersion,
    publicationEventId: v.publicationEventId, definition: v.definition, businessQuestion: v.businessQuestion };
}
export function validMetricChangeReviewSource(v: MetricChangeReviewSource): boolean {
  return !!v && digest(v.definition) && text(v.preparedAt, 64)
    && Array.isArray(v.differences) && v.differences.length <= 40 && v.differences.every(d => d && text(d.key) && text(d.label)
      && (d.before === null || typeof d.before === 'string' && d.before.length <= 4096)
      && (d.after === null || typeof d.after === 'string' && d.after.length <= 4096))
    && new Set(v.differences.map(d => d.key)).size === v.differences.length
    && Array.isArray(v.facts) && v.facts.length <= 100 && v.facts.every(f => f && text(f.key) && text(f.label) && text(f.value, 4096))
    && new Set(v.facts.map(f => f.key)).size === v.facts.length
    && Array.isArray(v.coverage) && v.coverage.length === 6 && v.coverage.every(c => c && text(c.label) && text(c.description)
      && ['READY', 'EMPTY', 'UNAVAILABLE', 'FORBIDDEN', 'NOT_APPLICABLE'].includes(c.status))
    && ['validation', 'references', 'readiness', 'dependencies', 'lineage', 'observed'].every(key => v.coverage.filter(c => c.key === key).length === 1);
}
export function parseMetricChangeReview(content: string): MetricChangeReviewSuggestion | null {
  try {
    if (content.length > 64000) return null;
    const v = readStructuredReceipt(content, 'yak-metric-change-review') as MetricChangeReviewSuggestion;
    readMetricChangeReviewTarget(v.target);
    if (v.kind !== 'METRIC_CHANGE_REVIEW' || !digest(v.expectedDefinition) || v.target.definition !== v.expectedDefinition
      || !digest(v.skillHash) || !Number.isSafeInteger(v.skillVersion) || v.skillVersion < 1 || v.truncated !== false
      || !validMetricChangeReviewSource(v.source) || v.source.definition !== v.expectedDefinition || !v.source.differences.length
      || !Array.isArray(v.questions) || v.questions.length > 3 || v.questions.some(q => !text(q))
      || !Array.isArray(v.candidates) || v.candidates.length > 1) return null;
    const validStatements = (statements: ReviewStatement[]) => Array.isArray(statements) && statements.length <= 5 && statements.every(s => s
      && text(s.text) && Array.isArray(s.evidence) && s.evidence.length > 0 && s.evidence.length <= 4
      && new Set(s.evidence.map(f => f?.key)).size === s.evidence.length
      && s.evidence.every(f => f && v.source.facts.some(owned => owned.key === f.key && owned.label === f.label && owned.value === f.value)));
    if (v.candidates.some(c => !c || !validStatements(c.statements) || !c.statements.length || !validStatements(c.checks))) return null;
    return v;
  } catch { return null; }
}
