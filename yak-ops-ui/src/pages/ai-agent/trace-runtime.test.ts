import type { SpanNode } from '@/services/agent';
import {
  applyClarifyWaiting,
  applyThinkingElapsed,
  applyToolCall,
  applyToolResult,
  closeRunningCalls,
  convergeRunningCalls,
  ensureThinkStep,
  extractCaliber,
  hydrateTraceSteps,
  markThinkStopped,
  prettyPayload,
  toChainEntries,
  toolCardVisual,
} from './trace-runtime';
import type { TraceStep } from './types';

const call = (overrides: Partial<TraceStep> = {}): TraceStep => ({
  key: 'c-1',
  kind: 'call',
  text: '',
  toolCallId: 'call_1',
  toolName: 'run_dataset_query',
  running: false,
  ...overrides,
});

const span = (overrides: Partial<SpanNode> = {}): SpanNode => ({
  id: 1,
  kind: 'TOOL_CALL',
  name: 'run_dataset_query',
  status: 'COMPLETED',
  toolCallId: 'call_1',
  parentStepId: null,
  attempt: null,
  durationMillis: 88,
  promptTokens: null,
  completionTokens: null,
  retryCount: null,
  errorCode: null,
  errorMessage: null,
  failureDetail: null,
  request: null,
  response: null,
  createTime: null,
  children: [],
  ...overrides,
});

describe('trace-runtime 事件状态机', () => {
  it('ensureThinkStep：末位非思考卡时追加新思考卡并携带迭代序', () => {
    const base = [call()];
    const next = ensureThinkStep(base, 2);
    expect(next).toHaveLength(2);
    expect(next[1]).toMatchObject({ kind: 'think', running: true, iter: 2 });
    // 末位已是思考卡：原样返回（同实例），不重复追加
    expect(ensureThinkStep(next, 2)).toBe(next);
  });

  it('applyToolCall：思考卡收口 + 追加运行中卡片，iter 服务端权威', () => {
    const thinking: TraceStep[] = [{ key: 't-0', kind: 'think', text: '想', running: true, iter: 1 }];
    const next = applyToolCall(thinking, {
      toolCallId: 'call_1',
      toolName: 'list_datasets',
      iter: 1,
      startedAt: 1000,
    });
    expect(next[0]).toMatchObject({ kind: 'think', running: false });
    expect(next[1]).toMatchObject({
      kind: 'call',
      toolCallId: 'call_1',
      toolName: 'list_datasets',
      running: true,
      iter: 1,
      startedAt: 1000,
    });
  });

  it('applyToolResult：按 toolCallId 原位闭合，ABORTED 记失败，waiting 清除', () => {
    const base = [call({ running: true, waiting: true })];
    const next = applyToolResult(base, {
      toolCallId: 'call_1',
      durationMs: 120,
      toolStatus: 'ABORTED',
    });
    expect(next[0]).toMatchObject({ running: false, waiting: false, failed: true, durationMs: 120 });
  });

  it('applyToolResult：无匹配 toolCallId 时整组原样返回（不误伤）', () => {
    const base = [call()];
    expect(applyToolResult(base, { toolCallId: 'other', toolStatus: 'ERROR' })).toEqual(base);
  });

  it('applyClarifyWaiting：仅命中澄清卡进入等待输入态', () => {
    const base = [call({ running: true }), call({ key: 'c-2', toolCallId: 'call_2', toolName: 'x' })];
    const next = applyClarifyWaiting(base, 'call_1');
    expect(next[0]).toMatchObject({ running: false, waiting: true });
    expect(next[1].waiting).toBeFalsy();
  });

  it('applyThinkingElapsed：仅回写末位思考卡的耗时', () => {
    const base = [call(), { key: 't-1', kind: 'think', text: '', running: true } as TraceStep];
    const next = applyThinkingElapsed(base, 3200);
    expect(next[0].durationMs).toBeUndefined();
    expect(next[1].durationMs).toBe(3200);
    expect(markThinkStopped(base).every((s) => !s.running)).toBe(true);
  });
});

describe('trace-runtime 终态收敛（渲染顺序规范 §11.4）', () => {
  it('closeRunningCalls：运行卡全部按失败收敛，已完成卡不动', () => {
    const base = [call({ running: true }), call({ key: 'c-2', toolCallId: 'call_2' })];
    const next = closeRunningCalls(base);
    expect(next[0]).toMatchObject({ running: false, failed: true });
    expect(next[1].failed).toBeFalsy();
  });

  it('convergeRunningCalls：无残留为零违例原样返回；有残留报违例并收敛', () => {
    expect(convergeRunningCalls([call()])).toEqual({ trace: [call()], violations: [] });

    const { trace, violations } = convergeRunningCalls([
      call({ running: true }),
      call({ key: 'c-2', toolCallId: 'call_2', running: true }),
    ]);
    expect(violations).toEqual(['call_1', 'call_2']);
    expect(trace.every((s) => !s.running && s.failed)).toBe(true);
  });
});

describe('trace-runtime 权威水合（O2 双通道）', () => {
  it('按 toolCallId 原位合并入参/结果/失败归因，think 文本保留', () => {
    const existing: TraceStep[] = [
      { key: 't-0', kind: 'think', text: '我的推理过程', running: false, iter: 1 },
      call({ running: true, resultText: '帧侧结果' }),
    ];
    const merged = hydrateTraceSteps(existing, [
      span({
        request: { mode: 'INLINE', content: '{"datasetId":7}' },
        response: { mode: 'INLINE', content: '{"rows":[]}' },
        durationMillis: 200,
      }),
    ]);
    expect(merged[0].text).toBe('我的推理过程');
    // 运行态归帧侧与终态对账所有——水合只补事实载荷，不越权改 running
    expect(merged[1]).toMatchObject({
      inputText: '{\n  "datasetId": 7\n}',
      resultText: '{"rows":[]}',
      durationMs: 200,
      failed: false,
      running: true,
    });
  });

  it('FAILED span 映射失败并把 failureDetail 提上屏', () => {
    const merged = hydrateTraceSteps(
      [call()],
      [
        span({
          status: 'FAILED',
          failureDetail: '字段不在白名单',
          response: { mode: 'INLINE', content: 'raw' },
        }),
      ],
    );
    expect(merged[0]).toMatchObject({ failed: true, failureDetail: '字段不在白名单' });
  });

  it('帧未覆盖的事实卡片按序补挂（守卫/投递缺口）', () => {
    const merged = hydrateTraceSteps(
      [],
      [
        span({ toolCallId: 'call_9', name: 'list_datasets' }),
        span({ id: 2, toolCallId: 'call_10', name: 'get_object_schema', status: 'FAILED' }),
      ],
    );
    expect(merged).toHaveLength(2);
    expect(merged[0]).toMatchObject({ toolCallId: 'call_9', toolName: 'list_datasets', failed: false });
    expect(merged[1]).toMatchObject({ toolCallId: 'call_10', failed: true });
  });

  it('非 TOOL_CALL span 忽略；帧侧已有结果且事实侧缺失时不回退清空', () => {
    const existing = [call({ resultText: '帧侧已有输出' })];
    const merged = hydrateTraceSteps(existing, [
      span({ kind: 'LLM_CALL', name: 'spike', toolCallId: null }),
      span({ response: null }),
    ]);
    expect(merged[0].resultText).toBe('帧侧已有输出');
  });
});

describe('trace-runtime 迭代分组与卡片状态（§11.5）', () => {
  it('toChainEntries：迭代变更处插入分隔标记；并行批同组不重复', () => {
    const trace: TraceStep[] = [
      { key: 't-0', kind: 'think', text: '', running: false, iter: 1 },
      call({ key: 'c-1', iter: 1 }),
      call({ key: 'c-2', toolCallId: 'call_2', iter: 1 }),
      { key: 't-2', kind: 'think', text: '', running: false, iter: 2 },
      call({ key: 'c-3', toolCallId: 'call_3', iter: 2 }),
    ];
    const entries = toChainEntries(trace);
    const dividers = entries.filter((e) => e.type === 'divider');
    expect(dividers.map((d) => (d as { iter: number }).iter)).toEqual([1, 2]);
    expect(entries.filter((e) => e.type === 'step')).toHaveLength(5);
  });

  it('toChainEntries：无 iter 的旧帧不产生分隔（降级兼容）', () => {
    expect(toChainEntries([call()]).every((e) => e.type === 'step')).toBe(true);
  });

  it('toolCardVisual：等待金/失败红/运行中/耗时文本四态', () => {
    expect(toolCardVisual(call({ running: true }))).toMatchObject({
      color: 'blue',
      statusText: '执行中...',
    });
    expect(toolCardVisual(call({ waiting: true }))).toMatchObject({
      color: 'gold',
      statusText: '等待输入',
    });
    expect(toolCardVisual(call({ failed: true, durationMs: 1500 }))).toMatchObject({
      color: 'red',
      statusText: '失败（耗时 1.5 秒）',
    });
    expect(toolCardVisual(call({ durationMs: 900 }))).toMatchObject({
      color: 'blue',
      statusText: '耗时 0.9 秒',
    });
  });
});

describe('trace-runtime 载荷美化', () => {
  it('JSON pretty-print；非 JSON 原样；空值 undefined', () => {
    expect(prettyPayload({ mode: 'INLINE', content: '{"a":1}' })).toBe('{\n  "a": 1\n}');
    expect(prettyPayload({ mode: 'INLINE', content: '普通文本' })).toBe('普通文本');
    expect(prettyPayload(undefined)).toBeUndefined();
    expect(prettyPayload({ mode: 'INLINE', content: null })).toBeUndefined();
  });
});

describe('trace-runtime 口径卡提取（PI-103）', () => {
  it('从语义查询结果提取本次口径行；多轮取首条；无命中返回 null', () => {
    expect(extractCaliber(['查询成功，返回 5 行\n本次口径：营收=含税GMV，不含退款'])).toBe('营收=含税GMV，不含退款');
    expect(extractCaliber(['无口径行的结果', '查询成功\n本次口径：按订单创建时间统计'])).toBe('按订单创建时间统计');
    expect(extractCaliber(['普通文本'])).toBeNull();
    expect(extractCaliber([undefined])).toBeNull();
    expect(extractCaliber([])).toBeNull();
  });
});
