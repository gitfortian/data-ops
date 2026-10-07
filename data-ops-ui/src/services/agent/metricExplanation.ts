import { readStructuredReceipt } from './structuredReceipt';
import type { MetricExplanationTarget } from './governance';
export interface MetricFact { key: string; label: string; value: string }
export interface MetricExplanationSuggestion {
  kind: 'METRIC_EXPLANATION'; target: MetricExplanationTarget; expectedDefinition: string;
  skillVersion: number; skillHash: string; truncated: false;
  candidates: { businessDescription: string; statements: { text: string; evidence: MetricFact[] }[] }[];
  questions: string[];
}
const shortText = (v: unknown): v is string => typeof v === 'string' && !!v.trim() && v.length <= 512;
export function parseMetricExplanation(text: string): MetricExplanationSuggestion | null {
  try {
    const v = readStructuredReceipt(text, 'yak-metric-explanation') as MetricExplanationSuggestion;
    if (v.kind !== 'METRIC_EXPLANATION' || !v.target || !Number.isSafeInteger(v.target.metricId) || v.target.metricId <= 0
      || !Number.isSafeInteger(v.target.version) || v.target.version <= 0 || typeof v.target.businessQuestion !== 'string' || v.target.businessQuestion.length > 512
      || !/^[a-f0-9]{64}$/.test(v.expectedDefinition) || !/^[a-f0-9]{64}$/.test(v.skillHash)
      || !Number.isSafeInteger(v.skillVersion) || v.skillVersion < 1 || v.truncated !== false
      || !Array.isArray(v.questions) || v.questions.length > 3 || v.questions.some(q => !shortText(q))
      || !Array.isArray(v.candidates) || v.candidates.length > 1
      || v.candidates.some(c => !c || !shortText(c.businessDescription) || !Array.isArray(c.statements)
        || !c.statements.length || c.statements.length > 5 || c.statements.some(s => !s || !shortText(s.text)
          || !Array.isArray(s.evidence) || !s.evidence.length || s.evidence.length > 4
          || s.evidence.some(f => !f || !shortText(f.key) || !shortText(f.label) || typeof f.value !== 'string' || f.value.length > 4096)
          || new Set(s.evidence.map(f => f.key)).size !== s.evidence.length))) return null;
    return v;
  } catch { return null; }
}
