import type { SessionContinuation } from './continuation';
import { readContinuation } from './continuation';
import { governanceSourcePath, sameScenarioTarget } from './governance';
import { parseStandardMatch, type StandardMatchSuggestion } from './standardMatch';
import { parseModelMapping, type ModelMappingSuggestion } from './modelMapping';
import { parseMetricExplanation, type MetricExplanationSuggestion } from './metricExplanation';
import { parseMetricDraft, type MetricDraftSuggestion } from './metricDraft';

export type ScenarioSuggestion = StandardMatchSuggestion | ModelMappingSuggestion | MetricExplanationSuggestion | MetricDraftSuggestion;
export type ScenarioHistory = { status: 'NONE' | 'UNAVAILABLE' }
  | { status: 'READY'; value: ScenarioSuggestion; text: string; sourcePath: string };
const markers = /```yak-(?:standard-match|model-mapping|metric-explanation|metric-draft)\s*\n/g;

/** Read-only projection of the latest completed task. Never infer its scope from model text. */
export function readScenarioHistory(text: string, sessionId: string | null, turnId: string | undefined,
  persistedHistory: boolean, continuation: SessionContinuation | null): ScenarioHistory {
  const blocks = [...text.matchAll(markers)];
  if (!blocks.length) return { status: 'NONE' };
  const unavailable: ScenarioHistory = { status: 'UNAVAILABLE' };
  if (blocks.length !== 1 || text.length > 256000 || !sessionId || !turnId || !persistedHistory || !continuation) return unavailable;
  try {
    const view = readContinuation(continuation, sessionId);
    if (view.turnId !== turnId || view.status !== 'COMPLETED' || view.blockingReason) return unavailable;
    const target = view.governanceTarget;
    let value: ScenarioSuggestion | null = null;
    let expected: unknown;
    switch (target?.purpose) {
      case 'STANDARD_MATCH': value = parseStandardMatch(text); expected = target.standardMatch; break;
      case 'MODEL_MAPPING': value = parseModelMapping(text); expected = target.modelMapping; break;
      case 'METRIC_EXPLANATION': value = parseMetricExplanation(text); expected = target.metricExplanation; break;
      case 'METRIC_DRAFT': value = parseMetricDraft(text); expected = target.metricDraft; break;
      default: return unavailable;
    }
    if (!value || typeof value.truncated !== 'boolean' || value.kind !== target.purpose || !sameScenarioTarget(value.target, expected)) return unavailable;
    return { status: 'READY', value, sourcePath: governanceSourcePath(target),
      text: text.replace(/```yak-(?:standard-match|model-mapping|metric-explanation|metric-draft)\s*\n[\s\S]*?\n```/, '').trim() };
  } catch { return unavailable; }
}
