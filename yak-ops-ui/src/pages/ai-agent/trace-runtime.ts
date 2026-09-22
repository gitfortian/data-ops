import type { SpanNode, StepPayload } from '@/services/agent';
import type { TraceStep } from './types';

/**
 * Agent 链路运行时（O2 渲染顺序规范的纯函数层）：
 * 事件状态机归约、权威水合合并、迭代分组与卡片状态映射。
 * 本模块不做 React、不打时钟、不发请求——时钟与副作用收敛在页面组件，
 * 这里的一切都是（输入, 输出）可测的纯函数（对齐 dashboard/interaction-runtime 先例）。
 */

/** THINKING_DELTA：上一元素非思考块时追加新思考卡（迭代序服务端权威）。 */
export const ensureThinkStep = (trace: TraceStep[], iter?: number): TraceStep[] => {
  const last = trace[trace.length - 1];
  if (last && last.kind === 'think') {
    return trace;
  }
  return [...trace, { key: `t-${trace.length}`, kind: 'think', text: '', running: true, iter }];
};

/** 思考卡运行态收口（TEXT_DELTA/TEXT 前置等场景）。 */
export const markThinkStopped = (trace: TraceStep[]): TraceStep[] =>
  trace.map((step) => (step.kind === 'think' ? { ...step, running: false } : step));

/** THINKING_DELTA 计时回写：仅更新末位思考卡（服务端权威值）。 */
export const applyThinkingElapsed = (trace: TraceStep[], elapsedMs: number): TraceStep[] =>
  trace.map((step, idx) =>
    idx === trace.length - 1 && step.kind === 'think' ? { ...step, durationMs: elapsedMs } : step,
  );

export interface ToolCallFrame {
  toolCallId?: string | null;
  toolName?: string | null;
  iter?: number | null;
  /** 已解析的发起时刻（epoch ms；时钟兜底由调用方完成）。 */
  startedAt: number;
}

/** TOOL_CALL：思考卡收口 + 追加运行中工具卡（配对唯一键 toolCallId）。 */
export const applyToolCall = (trace: TraceStep[], frame: ToolCallFrame): TraceStep[] => [
  ...trace.map((step) => (step.kind === 'think' ? { ...step, running: false } : step)),
  {
    key: `c-${frame.toolCallId ?? trace.length}`,
    kind: 'call',
    text: '',
    toolCallId: frame.toolCallId ?? '',
    toolName: frame.toolName ?? 'tool',
    running: true,
    iter: typeof frame.iter === 'number' ? frame.iter : undefined,
    startedAt: frame.startedAt,
  },
];

export interface ToolResultFrame {
  toolCallId?: string | null;
  durationMs?: number | null;
  toolResult?: string | null;
  toolStatus?: string | null;
}

/**
 * TOOL_RESULT：按 toolCallId 原位闭合（并行批完成序乱序合法）；ABORTED 等非 SUCCESS 即失败。
 * 官方化 v2 双帧：TOOL_CALL_END 带 durationMs/toolStatus，TOOL_CALL_RESULT 带内容——任一帧缺字段时
 * 保留既有值（不互相覆盖），保证两帧合流后卡片状态完整。
 */
export const applyToolResult = (trace: TraceStep[], frame: ToolResultFrame): TraceStep[] =>
  trace.map((step) =>
    step.toolCallId && step.toolCallId === frame.toolCallId
      ? {
          ...step,
          running: false,
          waiting: false,
          durationMs: typeof frame.durationMs === 'number' ? frame.durationMs : step.durationMs,
          resultText: frame.toolResult ?? step.resultText,
          failed: frame.toolStatus ? frame.toolStatus !== 'SUCCESS' : step.failed,
        }
      : step,
  );

/** CLARIFY_REQUESTED：HITL 等待输入是显式状态，不与 RUNNING 转圈混淆（§11.5）。 */
export const applyClarifyWaiting = (trace: TraceStep[], toolCallId?: string | null): TraceStep[] =>
  trace.map((step) =>
    step.toolCallId && step.toolCallId === toolCallId ? { ...step, running: false, waiting: true } : step,
  );

/** 终态防御收敛（CANCELLED/ERROR/MAX_ITERS 兜底路径）。 */
export const closeRunningCalls = (trace: TraceStep[]): TraceStep[] =>
  trace.map((step) => (step.kind === 'call' && step.running ? { ...step, running: false, failed: true } : step));

/**
 * 终态对账（§11.4）：返回收敛后 trace 与违例清单（终态残留运行卡的 toolCallId）。
 * 正常路径违例为空——非空即服务端终态收敛律被破坏，调用方负责上报。
 */
export const convergeRunningCalls = (
  trace: TraceStep[],
): { trace: TraceStep[]; violations: Array<string | undefined> } => {
  const violations = trace.filter((s) => s.kind === 'call' && s.running).map((s) => s.toolCallId);
  if (!violations.length) {
    return { trace, violations: [] };
  }
  return { trace: closeRunningCalls(trace), violations };
};

/** 口径卡提取（PI-103）：从语义查询结果文本提取"本次口径：..."行；多轮取首条。 */
export const extractCaliber = (resultTexts: Array<string | undefined>): string | null => {
  for (const text of resultTexts) {
    if (!text) {
      continue;
    }
    const match = text.match(/本次口径：(.+)/);
    if (match?.[1]) {
      return match[1].trim();
    }
  }
  return null;
};

/** 载荷美化：JSON 尽量 pretty-print，非 JSON 原样返回。 */
export const prettyPayload = (payload?: StepPayload | null): string | undefined => {
  if (!payload?.content) {
    return undefined;
  }
  try {
    return JSON.stringify(JSON.parse(payload.content), null, 2);
  } catch {
    return payload.content;
  }
};

/**
 * 权威水合合并（O2 双通道）：把 trace v2 事实侧的入参/结果/失败归因/耗时合并进帧侧卡片。
 * 语义：按 toolCallId 原位更新（think 文本保留不覆盖）；帧未覆盖的事实卡片按序补挂；
 * 水合不清理 waiting 态（挂起期水合不得误闭澄清卡，恢复流由 TOOL_RESULT 闭合）。
 */
export const hydrateTraceSteps = (existing: TraceStep[], spans: SpanNode[]): TraceStep[] => {
  const byCallId = new Map(existing.filter((s) => s.toolCallId).map((s) => [s.toolCallId as string, s]));
  const merged: TraceStep[] = [...existing];
  for (const span of spans) {
    if (span.kind !== 'TOOL_CALL' || !span.toolCallId) {
      continue;
    }
    const inputText = prettyPayload(span.request);
    const resultText = span.response?.content ?? undefined;
    const failed = span.status !== 'COMPLETED';
    const matched = byCallId.get(span.toolCallId);
    if (matched) {
      const idx = merged.indexOf(matched);
      merged[idx] = {
        ...matched,
        inputText: inputText ?? matched.inputText,
        resultText: resultText ?? matched.resultText,
        failureDetail: span.failureDetail ?? matched.failureDetail,
        durationMs: span.durationMillis ?? matched.durationMs,
        failed,
      };
    } else {
      merged.push({
        key: `c-${span.toolCallId}`,
        kind: 'call',
        text: '',
        toolCallId: span.toolCallId,
        toolName: span.name,
        running: false,
        failed,
        inputText,
        resultText,
        failureDetail: span.failureDetail ?? undefined,
        durationMs: span.durationMillis ?? undefined,
      });
    }
  }
  return merged;
};

/** 链路渲染条目：迭代分隔标记或步骤本体（§11.5 迭代分组的纯分组层）。 */
export type TraceChainEntry = { type: 'divider'; iter: number; key: string } | { type: 'step'; step: TraceStep };

export const toChainEntries = (trace: TraceStep[]): TraceChainEntry[] => {
  const entries: TraceChainEntry[] = [];
  let lastIter: number | undefined;
  trace.forEach((step) => {
    if (typeof step.iter === 'number' && step.iter > 0 && step.iter !== lastIter) {
      lastIter = step.iter;
      entries.push({ type: 'divider', iter: step.iter, key: `iter-${step.iter}-${step.key}` });
    }
    entries.push({ type: 'step', step });
  });
  return entries;
};

/** 工具卡状态映射：等待（金）/失败（红）/运行中/耗时文本——渲染层的纯判定部分。 */
export const toolCardVisual = (
  step: TraceStep,
): { color: string; statusText: string; failed: boolean; waiting: boolean } => {
  const waiting = Boolean(step.waiting);
  const failed = Boolean(step.failed);
  if (step.running) {
    return { color: 'blue', statusText: '执行中...', failed, waiting };
  }
  if (waiting) {
    return { color: 'gold', statusText: '等待输入', failed, waiting };
  }
  if (failed) {
    return {
      color: 'red',
      statusText:
        typeof step.durationMs === 'number' ? `失败（耗时 ${(step.durationMs / 1000).toFixed(1)} 秒）` : '失败',
      failed,
      waiting,
    };
  }
  return {
    color: 'blue',
    statusText: typeof step.durationMs === 'number' ? `耗时 ${(step.durationMs / 1000).toFixed(1)} 秒` : '完成',
    failed,
    waiting,
  };
};
