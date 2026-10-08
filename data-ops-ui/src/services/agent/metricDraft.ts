import HttpUtils from '@/utils/HttpUtils';
import { readStructuredReceipt } from './structuredReceipt';
import type { MetricDraftTarget } from './governance';

export interface MetricDraftContext {
  definition: string;
  fields: { name: string; type: string; description: string }[];
  upstream: { id: number; version: number; code: string; name: string; type: string; measure: string; filter: string }[];
}
export interface MetricDefinitionDraft {
  name: string; description: string; period: 'DAY' | 'WEEK' | 'MONTH'; aggregation: string | null; field: string | null;
  qualifiers: { field: string; op: string; value: string }[];
  tokens: { operator: string; metricId: number | null }[];
}
export interface MetricDraftSuggestion {
  kind: 'METRIC_DRAFT'; target: MetricDraftTarget; expectedDefinition: string; skillVersion: number; skillHash: string;
  truncated: false; candidates: MetricDefinitionDraft[]; questions: string[]; source: MetricDraftContext;
}
const short = (v: unknown, max = 512): v is string => typeof v === 'string' && !!v.trim() && v.length <= max;
const id = (v: unknown) => Number.isSafeInteger(v) && Number(v) > 0;
export function readMetricDraftTarget(value: unknown): MetricDraftTarget {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('指标草稿目标无效');
  const v = value as MetricDraftTarget;
  if ((v.metricId == null) !== (v.version == null) || (v.metricId != null && (!id(v.metricId) || !id(v.version)))
    || !['ATOMIC', 'DERIVED', 'COMPOSITE'].includes(v.metricType) || !short(v.requirement)
    || !Array.isArray(v.upstreamIds) || v.upstreamIds.length > 5 || v.upstreamIds.some(i => !id(i))
    || new Set(v.upstreamIds).size !== v.upstreamIds.length
    || (v.metricType === 'ATOMIC' && (!id(v.modelId) || !!v.upstreamIds.length))
    || (v.metricType !== 'ATOMIC' && (v.modelId != null || !v.upstreamIds.length))
    || (v.metricType === 'DERIVED' && v.upstreamIds.length !== 1)) throw new Error('指标草稿目标无效');
  return { metricId: v.metricId ?? null, version: v.version ?? null, metricType: v.metricType, modelId: v.modelId ?? null,
    upstreamIds: v.upstreamIds, requirement: v.requirement };
}
export function parseMetricDraft(text: string): MetricDraftSuggestion | null {
  try {
    const v = readStructuredReceipt(text, 'yak-metric-draft') as MetricDraftSuggestion;
    v.target = readMetricDraftTarget(v.target);
    if (v.kind !== 'METRIC_DRAFT' || !/^[a-f0-9]{64}$/.test(v.expectedDefinition) || !/^[a-f0-9]{64}$/.test(v.skillHash)
      || !id(v.skillVersion) || v.truncated !== false || !v.source || v.source.definition !== v.expectedDefinition
      || !Array.isArray(v.source.fields) || v.source.fields.length > 100
      || v.source.fields.some(f => !f || !short(f.name, 128) || !short(f.type, 64) || typeof f.description !== 'string' || f.description.length > 512)
      || !Array.isArray(v.source.upstream) || v.source.upstream.length > 5
      || v.source.upstream.some(u => !u || !id(u.id) || !id(u.version) || !short(u.code, 64) || !short(u.name, 128))
      || !Array.isArray(v.questions) || v.questions.length > 3 || v.questions.some(q => !short(q))
      || !Array.isArray(v.candidates) || v.candidates.length > 1
      || v.candidates.some(c => !c || !short(c.name, 128) || !short(c.description) || !['DAY', 'WEEK', 'MONTH'].includes(c.period)
        || (c.aggregation != null && !['SUM', 'COUNT', 'COUNT_DISTINCT', 'AVG', 'MIN', 'MAX'].includes(c.aggregation))
        || (c.field != null && !v.source.fields.some(f => f.name === c.field))
        || !Array.isArray(c.qualifiers) || c.qualifiers.length > 5 || c.qualifiers.some(q => !q || !short(q.field, 128) || !short(q.op, 16) || !short(q.value, 128))
        || !Array.isArray(c.tokens) || c.tokens.length > 31 || c.tokens.some(t => !t || !['REF', 'ADD', 'SUB', 'MUL', 'DIV', 'LPAREN', 'RPAREN'].includes(t.operator)
          || (t.operator === 'REF' ? !v.source.upstream.some(u => u.id === t.metricId) : t.metricId != null)))) return null;
    return v;
  } catch { return null; }
}
export const getMetricDraftContext = (target: MetricDraftTarget) => HttpUtils.postData<MetricDraftContext>('/api/v1/metrics/draft-context', target);
