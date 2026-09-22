import type { ChatTurnEvent } from '@/services/agent';

/**
 * 流式会话运行时（Phase 2 前端三件套的纯函数层）：
 * 错误码四级分发（码→文案→动作→去重）、连接状态机（五态+看门狗判定）、帧合流。
 * 纯函数、无 React、无时钟副作用（时钟由调用方注入），对齐 trace-runtime 先例。
 */

// ---- 一级·错误码 → 二级·文案 / 三级·动作 ----

export type ErrorActionType = 'retry' | 'narrow' | 'check-config' | 'none';

export interface ErrorAction {
  type: ErrorActionType;
  label: string;
}

export interface ErrorPresentation {
  title: string;
  hint: string;
  action: ErrorAction;
  severity: 'error' | 'warning';
}

const ACTIONS: Record<ErrorActionType, ErrorAction> = {
  retry: { type: 'retry', label: '重试' },
  narrow: { type: 'narrow', label: '调整条件后重试' },
  'check-config': { type: 'check-config', label: '查看模型配置' },
  none: { type: 'none', label: '' },
};

/** 分类错误码 → 用户可操作的文案与动作（服务端权威码，禁止裸 e.message 上屏）。 */
export const presentError = (errorCode?: string | null, fallbackMessage?: string | null): ErrorPresentation => {
  switch (errorCode) {
    case 'TIMEOUT':
      return {
        title: '推理超时',
        hint: '本轮推理超过时限未完成，建议缩小问题范围后重试。',
        action: ACTIONS.retry,
        severity: 'warning',
      };
    case 'USER_ERROR':
      return {
        title: '模型配置异常',
        hint: '请检查模型服务配置（密钥 / 额度 / 限流）后重试。',
        action: ACTIONS['check-config'],
        severity: 'error',
      };
    case 'PROVIDER_ERROR':
      return {
        title: '模型服务暂不可用',
        hint: '服务端已自动重试仍失败，请稍后重试。',
        action: ACTIONS.retry,
        severity: 'error',
      };
    case 'GUARD_REJECTED':
      return {
        title: '口径校验未通过',
        hint: '本次取数的字段或指标不符合治理口径，请调整查询条件。',
        action: ACTIONS.narrow,
        severity: 'warning',
      };
    default: {
      const detail = fallbackMessage?.trim() ? fallbackMessage : '执行失败';
      return { title: detail, hint: '请重试；若持续失败请联系管理员。', action: ACTIONS.retry, severity: 'error' };
    }
  }
};

/** 四级·同源去重键：同一轮内相同 (码+正文) 只呈现一次。 */
export const errorKey = (errorCode?: string | null, message?: string | null): string =>
  `${errorCode ?? 'GENERIC'}|${message ?? ''}`;

// ---- 连接状态机（五态） ----

export type ConnectionState = 'idle' | 'connecting' | 'streaming' | 'reconnecting' | 'error';

export type ConnectionEvent =
  | 'submit'
  | 'first-frame'
  | 'frame'
  | 'drop'
  | 'reconnect-start'
  | 'reconnect-ok'
  | 'fail'
  | 'finish'
  | 'cancel';

const TRANSITIONS: Record<ConnectionState, Partial<Record<ConnectionEvent, ConnectionState>>> = {
  idle: { submit: 'connecting', finish: 'idle', cancel: 'idle' },
  connecting: { 'first-frame': 'streaming', frame: 'streaming', fail: 'error', finish: 'idle', cancel: 'idle' },
  streaming: {
    frame: 'streaming',
    drop: 'reconnecting',
    'reconnect-start': 'reconnecting',
    fail: 'error',
    finish: 'idle',
    cancel: 'idle',
  },
  reconnecting: {
    'reconnect-start': 'reconnecting',
    'reconnect-ok': 'streaming',
    frame: 'streaming',
    fail: 'error',
    finish: 'idle',
    cancel: 'idle',
  },
  error: { submit: 'connecting', cancel: 'idle' },
};

/** 状态机步进：未登记的转换保持原状态（非法事件不炸 UI）。 */
export const nextConnectionState = (state: ConnectionState, event: ConnectionEvent): ConnectionState =>
  TRANSITIONS[state]?.[event] ?? state;

/** 看门狗判定：流式期间超过阈值无任何帧 → 判定连接可能中断。 */
export const isWatchdogDue = (lastFrameAt: number, now: number, timeoutMs = 60_000): boolean =>
  lastFrameAt > 0 && now - lastFrameAt > timeoutMs;

// ---- 帧合流（渲染管线纪律：禁止逐帧直驱 setState） ----

const isDeltaType = (type: ChatTurnEvent['type']) =>
  type === 'TEXT_MESSAGE_CONTENT' || type === 'REASONING_MESSAGE_CONTENT';

/**
 * 把一串事件合流为可批量应用的帧序列：
 * 连续同类增量帧合并为单帧（delta 拼接，计时字段取服务端最末权威值），
 * 非增量帧（工具/终态/反问）保持独立不合并——保序、不丢帧。
 */
export const coalesceEvents = (events: ChatTurnEvent[]): ChatTurnEvent[] => {
  const out: ChatTurnEvent[] = [];
  for (const event of events) {
    const prev = out[out.length - 1];
    if (prev && isDeltaType(prev.type) && prev.type === event.type && !event.toolCallId) {
      out[out.length - 1] = {
        ...prev,
        delta: (prev.delta ?? '') + (event.delta ?? ''),
        // 服务端权威计时：同型增量流的末帧值最新
        thinkingElapsedMs: event.thinkingElapsedMs ?? prev.thinkingElapsedMs,
        elapsedMs: event.elapsedMs ?? prev.elapsedMs,
      };
      continue;
    }
    out.push(event);
  }
  return out;
};
