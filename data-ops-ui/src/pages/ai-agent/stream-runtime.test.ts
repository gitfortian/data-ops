import type { ChatTurnEvent } from '@/services/agent';
import { coalesceEvents, errorKey, isWatchdogDue, nextConnectionState, presentError } from './stream-runtime';

const textDelta = (delta: string, elapsed?: number): ChatTurnEvent => ({
  type: 'TEXT_MESSAGE_CONTENT',
  delta,
  elapsedMs: elapsed,
});
const thinkDelta = (delta: string, thinkingElapsedMs?: number): ChatTurnEvent => ({
  type: 'REASONING_MESSAGE_CONTENT',
  delta,
  thinkingElapsedMs,
});

describe('错误四级分发（码→文案→动作→去重）', () => {
  it('五种分类码各自映射文案与动作', () => {
    expect(presentError('TIMEOUT')).toMatchObject({
      title: '推理超时',
      action: { type: 'retry' },
      severity: 'warning',
    });
    expect(presentError('USER_ERROR')).toMatchObject({
      title: '请求未能继续',
      action: { type: 'retry' },
    });
    expect(presentError('PROVIDER_ERROR')).toMatchObject({
      title: '模型调用未完成',
      action: { type: 'retry' },
    });
    expect(presentError('GUARD_REJECTED')).toMatchObject({
      title: '执行条件校验未通过',
      action: { type: 'retry' },
      severity: 'warning',
    });
    expect(presentError('GENERIC', '磁盘满')).toMatchObject({
      title: '本轮执行失败',
      action: { type: 'retry' },
    });
  });

  it('未知码使用固定说明而不回显异常', () => {
    const p = presentError(null, null);
    expect(p.title).toBe('本轮执行失败');
    expect(JSON.stringify(presentError('private-code', 'secret api-key'))).not.toContain('secret');
    expect(p.action.type).toBe('retry');
  });

  it('同源去重键：同码同文稳定一致，异码隔离', () => {
    expect(errorKey('TIMEOUT', 'x')).toBe(errorKey('TIMEOUT', 'x'));
    expect(errorKey('TIMEOUT', 'x')).not.toBe(errorKey('GENERIC', 'x'));
    expect(errorKey(undefined, null)).toBe(errorKey('GENERIC', ''));
  });
});

describe('连接状态机（五态）', () => {
  const walk = (events: Parameters<typeof nextConnectionState>[1][]) =>
    events.reduce(nextConnectionState, 'idle' as ReturnType<typeof nextConnectionState>);

  it('正常路径：submit→connecting→首帧 streaming→finish idle', () => {
    expect(walk(['submit', 'first-frame', 'frame', 'finish'])).toBe('idle');
  });

  it('断线续播路径：streaming→drop→reconnect→frame 恢复 streaming', () => {
    expect(walk(['submit', 'first-frame', 'drop', 'reconnect-start', 'frame'])).toBe('streaming');
  });

  it('连续失败进 error，error 下可重新 submit', () => {
    expect(walk(['submit', 'fail'])).toBe('error');
    expect(walk(['submit', 'fail', 'submit'])).toBe('connecting');
  });

  it('reconnecting 中失败进 error；非法事件不改变状态', () => {
    expect(nextConnectionState('reconnecting', 'fail')).toBe('error');
    expect(nextConnectionState('idle', 'frame')).toBe('idle');
  });
});

describe('连接看门狗', () => {
  it('超阈值判定到期，未到期与无帧基线不误报', () => {
    expect(isWatchdogDue(1000, 62_000)).toBe(true);
    expect(isWatchdogDue(1000, 61_000 - 1)).toBe(false);
    expect(isWatchdogDue(0, 999_999)).toBe(false);
  });
});

describe('帧合流（渲染管线）', () => {
  it('连续同类增量合并为单帧：delta 拼接、计时取末帧服务端权威值', () => {
    const merged = coalesceEvents([textDelta('你', 1), textDelta('好', 2), textDelta('！')]);
    expect(merged).toHaveLength(1);
    expect(merged[0].delta).toBe('你好！');
    expect(merged[0].elapsedMs).toBe(2);
  });

  it('异类帧打断合并（保序不跨型拼接）', () => {
    const merged = coalesceEvents([textDelta('a'), thinkDelta('b'), textDelta('c')]);
    expect(merged).toHaveLength(3);
    expect(merged.map((e) => e.type)).toEqual([
      'TEXT_MESSAGE_CONTENT',
      'REASONING_MESSAGE_CONTENT',
      'TEXT_MESSAGE_CONTENT',
    ]);
  });

  it('工具/终态帧不合并不丢失；工具帧不与文本合并', () => {
    const tool: ChatTurnEvent = { type: 'TOOL_CALL_START', toolCallId: 'c1', toolName: 't' };
    const tool2: ChatTurnEvent = { type: 'TOOL_CALL_START', toolCallId: 'c2', toolName: 't2' };
    const merged = coalesceEvents([textDelta('a'), tool, tool2]);
    expect(merged).toHaveLength(3);
    expect(merged[1].toolCallId).toBe('c1');
  });
});
