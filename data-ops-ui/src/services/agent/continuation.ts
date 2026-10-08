import { readMetricDraftTarget } from './metricDraft';
import type { GovernanceTarget } from './governance';

export interface SessionContinuation {
  sessionId: string;
  turnId?: string | null;
  status?: 'QUEUED' | 'RUNNING' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED' | null;
  governanceTarget?: GovernanceTarget | null;
  clarification?: { toolCallId: string; toolName: string; question: string } | null;
  blockingReason?: string | null;
  errorCode?: string | null;
  questionDraft?: string | null;
  draftUnavailableReason?: string | null;
}

/** Normalize nullable server fields before exposing the discriminated UI target. */
function readTarget(value: unknown): GovernanceTarget | null {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) throw new Error('任务上下文无效');
  const target = value as Record<string, unknown>;
  const { assetId, qualityMonitorId, qualityExecutionNo, purpose, standardMatch, modelMapping, metricExplanation, metricDraft } = target;
  if ([assetId, qualityMonitorId, qualityExecutionNo, standardMatch, modelMapping, metricExplanation, metricDraft].filter((item) => item != null).length !== 1) {
    throw new Error('任务上下文不唯一');
  }
  if (purpose === 'METRIC_DRAFT' && metricDraft != null) return { purpose, metricDraft: readMetricDraftTarget(metricDraft) };
  if (purpose === 'METRIC_EXPLANATION' && metricExplanation && typeof metricExplanation === 'object' && !Array.isArray(metricExplanation)) {
    const metric = metricExplanation as Record<string, unknown>;
    if (Number.isSafeInteger(metric.metricId) && Number(metric.metricId) > 0
      && Number.isSafeInteger(metric.version) && Number(metric.version) > 0
      && typeof metric.businessQuestion === 'string' && metric.businessQuestion.length <= 512
      && (metric.view == null || metric.view === 'SNAPSHOT')) {
      return { purpose, metricExplanation: { metricId: Number(metric.metricId), version: Number(metric.version), businessQuestion: metric.businessQuestion,
        ...(metric.view === 'SNAPSHOT' ? { view: 'SNAPSHOT' as const } : {}) } };
    }
    throw new Error('指标版本上下文无效');
  }
  if (purpose === 'MODEL_MAPPING' && modelMapping && typeof modelMapping === 'object' && !Array.isArray(modelMapping)) {
    const field = modelMapping as Record<string, unknown>;
    if (Number.isSafeInteger(field.modelId) && Number(field.modelId) > 0
      && Number.isSafeInteger(field.datasourceId) && Number(field.datasourceId) > 0
      && typeof field.columnName === 'string' && /^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/.test(field.columnName)
      && typeof field.database === 'string' && field.database.trim() && field.database.length <= 128
      && typeof field.table === 'string' && field.table.trim() && field.table.length <= 128
      && typeof field.businessDescription === 'string' && field.businessDescription.length <= 512
      && typeof field.keyword === 'string' && field.keyword.length <= 64) {
      return { purpose, modelMapping: { modelId: Number(field.modelId), columnName: field.columnName,
        datasourceId: Number(field.datasourceId), database: field.database, table: field.table,
        businessDescription: field.businessDescription, keyword: field.keyword } };
    }
    throw new Error('模型映射上下文无效');
  }
  if (purpose === 'STANDARD_MATCH'  && standardMatch && typeof standardMatch === 'object') {
    const field = standardMatch as Record<string, unknown>;
    if (Number.isSafeInteger(field.modelId) && Number(field.modelId) > 0
      && typeof field.columnName === 'string' && /^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/.test(field.columnName)
      && typeof field.dataType === 'string' && field.dataType.trim() && field.dataType.length <= 64
      && typeof field.businessDescription === 'string' && field.businessDescription.length <= 512
      && typeof field.keyword === 'string' && field.keyword.length <= 64) {
      return { purpose, standardMatch: { modelId: Number(field.modelId), columnName: field.columnName,
        dataType: field.dataType, businessDescription: field.businessDescription, keyword: field.keyword } };
    }
    throw new Error('字段草稿上下文无效');
  }
  if (assetId != null && Number.isSafeInteger(assetId) && Number(assetId) > 0
    && (purpose == null || purpose === 'ASSET_DESCRIPTION')) {
    return purpose === 'ASSET_DESCRIPTION' ? { assetId: Number(assetId), purpose } : { assetId: Number(assetId) };
  }
  if (qualityMonitorId != null && Number.isSafeInteger(qualityMonitorId) && Number(qualityMonitorId) > 0 && purpose === 'QUALITY_RULES') {
    return { qualityMonitorId: Number(qualityMonitorId), purpose };
  }
  if (typeof qualityExecutionNo === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(qualityExecutionNo) && purpose == null) {
    return { qualityExecutionNo };
  }
  throw new Error('任务上下文无效');
}

export function readContinuation(view: SessionContinuation, sessionId: string): SessionContinuation {
  if (!view || view.sessionId !== sessionId) throw new Error('会话上下文不匹配');
  const statuses = ['QUEUED', 'RUNNING', 'WAITING_INPUT', 'COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED'];
  if (view.status != null && !statuses.includes(view.status)) throw new Error('会话状态无法识别');
  if (view.status != null && (typeof view.turnId !== 'string' || !view.turnId.trim())) throw new Error('轮次上下文缺失');
  const target = readTarget(view.governanceTarget);
  if (view.blockingReason != null && typeof view.blockingReason !== 'string') throw new Error('会话阻止原因无效');
  let reason = view.blockingReason || null;
  let clarification: SessionContinuation['clarification'] = null;
  if (view.status === 'WAITING_INPUT' && !reason) {
    const pending = view.clarification;
    if (!pending || pending.toolName !== 'request_clarification'
      || typeof pending.toolCallId !== 'string' || !pending.toolCallId.trim()
      || typeof pending.question !== 'string' || !pending.question.trim()) throw new Error('待答问题无法读取');
    if (pending.question.trim().startsWith('{') || pending.question.trim().startsWith('[')) {
      const args: unknown = JSON.parse(pending.question);
      if (!args || typeof args !== 'object' || Array.isArray(args)
        || typeof (args as { question?: unknown }).question !== 'string'
        || !(args as { question: string }).question.trim()) throw new Error('待答问题无效');
      const options = (args as { options?: unknown }).options;
      if (options != null && (!Array.isArray(options) || options.some((item) => typeof item !== 'string'))) {
        throw new Error('待答选项无效');
      }
    }
    clarification = pending;
  }
  if (view.status === 'QUEUED' || view.status === 'RUNNING') reason ||= '该会话仍在排队或推理中，请刷新后继续。';
  const terminal = isTerminal(view);
  const questionDraft = terminal && !reason && typeof view.questionDraft === 'string'
    && view.questionDraft.trim() && view.questionDraft.length <= 8000 ? view.questionDraft : null;
  const errorCode = ['TIMEOUT', 'USER_ERROR', 'PROVIDER_ERROR', 'GUARD_REJECTED'].includes(view.errorCode ?? '') ? view.errorCode : 'GENERIC';
  return { ...view, governanceTarget: target, clarification, blockingReason: reason, errorCode,
    questionDraft, draftUnavailableReason: terminal && !questionDraft ? '原问题暂不可用，请手工整理或刷新核对。' : null };
}

export function isTerminal(view: SessionContinuation | null): boolean {
  return !!view?.turnId && ['COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(view.status ?? '');
}

export function sessionLocation(sessionId: string | null): string {
  return sessionId ? `/ai-agent?sessionId=${encodeURIComponent(sessionId)}` : '/ai-agent';
}
