import { createHash } from 'node:crypto';

export const scenarioTypes = {
  STANDARD_MATCH: { key: 'standardMatch', marker: 'yak-standard-match', skillId: 'standard-match', tool: 'get_standard_match_context', maxCandidates: 3 },
  MODEL_MAPPING: { key: 'modelMapping', marker: 'yak-model-mapping', skillId: 'model-field-mapping', tool: 'get_model_mapping_context', maxCandidates: 3 },
  METRIC_EXPLANATION: { key: 'metricExplanation', marker: 'yak-metric-explanation', skillId: 'metric-caliber-explanation', tool: 'get_metric_caliber_context', maxCandidates: 1 },
  METRIC_DRAFT: { key: 'metricDraft', marker: 'yak-metric-draft', skillId: 'metric-definition-draft', tool: 'get_metric_draft_context', maxCandidates: 1 },
};
export const digest = value => createHash('sha256').update(value).digest('hex');
const id = value => Number.isSafeInteger(value) && value > 0;
const text = (value, max, empty = false) => typeof value === 'string' && value.length <= max && (empty || !!value.trim());
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const keysWithin = (value, keys) => object(value) && Object.keys(value).every(key => keys.includes(key));
// Server records include nullable union members; omit nulls, sort keys, preserve array order.
export function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (object(value)) return Object.fromEntries(Object.keys(value).sort().filter(key => value[key] != null).map(key => [key, canonical(value[key])]));
  return value;
}
export const same = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));

export function validateScenarioTarget(target) {
  const type = scenarioTypes[target?.purpose];
  if (!type || !keysWithin(canonical(target), ['purpose', type.key])) throw new Error('Invalid scenario target');
  const v = target[type.key];
  let valid = false;
  if (target.purpose === 'STANDARD_MATCH') {
    valid = keysWithin(v, ['modelId', 'columnName', 'dataType', 'businessDescription', 'keyword'])
      && id(v.modelId) && text(v.dataType, 64) && text(v.businessDescription, 512, true) && text(v.keyword, 64, true);
  } else if (target.purpose === 'MODEL_MAPPING') {
    valid = keysWithin(v, ['modelId', 'columnName', 'datasourceId', 'database', 'table', 'businessDescription', 'keyword'])
      && id(v.modelId) && id(v.datasourceId) && text(v.database, 128) && text(v.table, 128)
      && text(v.businessDescription, 512, true) && text(v.keyword, 64, true);
  } else if (target.purpose === 'METRIC_EXPLANATION') {
    valid = keysWithin(v, ['metricId', 'version', 'businessQuestion', 'view']) && id(v.metricId) && id(v.version)
      && text(v.businessQuestion, 512, true) && (v.view == null || v.view === 'SNAPSHOT');
  } else {
    valid = keysWithin(v, ['metricId', 'version', 'metricType', 'modelId', 'upstreamIds', 'requirement'])
      && (v.metricId == null ? v.version == null : id(v.metricId) && id(v.version))
      && text(v.requirement, 512) && Array.isArray(v.upstreamIds) && v.upstreamIds.length <= 5
      && v.upstreamIds.every(id) && new Set(v.upstreamIds).size === v.upstreamIds.length
      && (v.metricType === 'ATOMIC' ? id(v.modelId) && !v.upstreamIds.length
        : ['DERIVED', 'COMPOSITE'].includes(v.metricType) && v.modelId == null && v.upstreamIds.length > 0
          && (v.metricType !== 'DERIVED' || v.upstreamIds.length === 1));
  }
  if (['STANDARD_MATCH', 'MODEL_MAPPING'].includes(target.purpose)) valid &&= typeof v.columnName === 'string' && /^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/.test(v.columnName);
  if (!valid) throw new Error('Invalid scenario target');
  return type;
}

export function validateScenarioBinding(expected, actual) {
  const type = validateScenarioTarget(expected);
  validateScenarioTarget(actual);
  const a = expected[type.key], b = actual[type.key];
  if (actual.purpose !== expected.purpose || (a.view ?? null) !== (b.view ?? null) || a.metricType !== b.metricType
      || (a.metricId == null) !== (b.metricId == null) || a.upstreamIds?.length !== b.upstreamIds?.length) {
    throw new Error('Mapping changes scenario intent');
  }
}

// This observes a persisted receipt envelope, not natural-language or source validity.
export function observeDelivery(target, sessionId, turnId, continuation, history) {
  const type = validateScenarioTarget(target);
  const unavailable = reason => ({ status: 'UNAVAILABLE', reason });
  if (continuation?.sessionId !== sessionId || continuation?.turnId !== turnId || !same(continuation.governanceTarget, target)) return unavailable('CONTEXT_MISMATCH');
  if (continuation.status !== 'COMPLETED' || continuation.blockingReason) return unavailable('TURN_NOT_COMPLETED');
  if (!Array.isArray(history)) return unavailable('HISTORY_UNAVAILABLE');
  const messages = history.filter(item => typeof item?.role === 'string' && item.role.toUpperCase() === 'ASSISTANT' && item.turnId === turnId);
  if (messages.length !== 1 || typeof messages[0].content !== 'string') return unavailable('HISTORY_NOT_UNIQUE');
  const content = messages[0].content;
  const receipts = [...content.matchAll(new RegExp('```' + type.marker + '\\s*\\n([\\s\\S]*?)\\n```', 'g'))];
  if (!receipts.length) return { status: 'NO_RECEIPT', historyHash: digest(content) };
  if (receipts.length !== 1) return unavailable('RECEIPT_NOT_UNIQUE');
  let receipt;
  try { receipt = JSON.parse(receipts[0][1]); } catch { return unavailable('INVALID_RECEIPT'); }
  if (!receipt || receipt.kind !== target.purpose || !same(receipt.target, target[type.key])
      || !/^[a-f0-9]{64}$/.test(receipt.expectedDefinition) || !/^[a-f0-9]{64}$/.test(receipt.skillHash)
      || !id(receipt.skillVersion) || receipt.truncated !== false
      || !Array.isArray(receipt.candidates) || receipt.candidates.length > type.maxCandidates
      || !Array.isArray(receipt.questions) || receipt.questions.length > 3
      || receipt.questions.some(q => !text(q, 512))) return unavailable('INVALID_RECEIPT');
  return { status: 'RECEIPT_ENVELOPE_OBSERVED', skillId: type.skillId, skillVersion: receipt.skillVersion,
    skillHash: receipt.skillHash, definitionHash: receipt.expectedDefinition, historyHash: digest(content),
    candidateCount: receipt.candidates.length, questionCount: receipt.questions.length,
    candidateValidity: 'PENDING_SOURCE_REVIEW', factSupport: 'PENDING_EXPERT_REVIEW' };
}
