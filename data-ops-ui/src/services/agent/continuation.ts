import type { GovernanceTarget } from './governance';

export interface SessionContinuation {
  sessionId: string;
  turnId?: string | null;
  status?: 'QUEUED' | 'RUNNING' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED' | null;
  governanceTarget?: GovernanceTarget | null;
  clarification?: { toolCallId: string; toolName: string; question: string } | null;
  blockingReason?: string | null;
}

/** Normalize nullable server fields before exposing the discriminated UI target. */
function readTarget(value: unknown): GovernanceTarget | null {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) throw new Error('任务上下文无效');
  const target = value as Record<string, unknown>;
  const { assetId, qualityMonitorId, qualityExecutionNo, purpose } = target;
  if ([assetId, qualityMonitorId, qualityExecutionNo].filter((item) => item != null).length !== 1) {
    throw new Error('任务上下文不唯一');
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
    if (/^[{[]/.test(pending.question.trim())) {
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
  return { ...view, governanceTarget: target, clarification, blockingReason: reason };
}

export function sessionLocation(sessionId: string | null): string {
  return sessionId ? `/ai-agent?sessionId=${encodeURIComponent(sessionId)}` : '/ai-agent';
}
