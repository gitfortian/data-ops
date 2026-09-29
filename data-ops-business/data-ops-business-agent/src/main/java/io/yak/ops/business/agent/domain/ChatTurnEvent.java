package io.yak.ops.business.agent.domain;

/**
 * 一轮推理的领域事件。由 runtime 从 AgentScope 事件流映射而来，是 SSE 的唯一事实来源。
 *
 * <p>计数字段全部为<b>服务端权威值</b>（不允许前端本地掐表替代）：</p>
 * <ul>
 *   <li>{@code thinkingElapsedMs} — THINKING_DELTA：当前思考块的已耗时（块起点=上一非思考帧后第一个增量）；</li>
 *   <li>{@code startedAt} — TOOL_CALL：工具调用发起时刻（epoch 毫秒）；</li>
 *   <li>{@code durationMs}/{@code toolStatus} — TOOL_RESULT：工具执行耗时与终态（SUCCESS/ERROR/INTERRUPTED/DENIED，取自框架 ToolResultEndEvent.state）；</li>
 *   <li>{@code elapsedMs} — TURN_FINISHED：轮次总耗时（执行器从 claim 后起算）；</li>
 *   <li>{@code errorCode} — ERROR/终态失败帧：分类错误码（TIMEOUT/USER_ERROR/PROVIDER_ERROR/GUARD_REJECTED/GENERIC）；</li>
 *   <li>{@code iter} — 渲染顺序规范（O1）：迭代序（1 起，成功模型调用边界递增、重试不递增），
 *       前端按迭代分组渲染的基本依据；TOOL_CALL/TOOL_RESULT/THINKING_DELTA/TEXT_DELTA 帧携带。</li>
 *   <li>{@code phase} — TEXT_DELTA 文本分层：FINAL=结果事件派生的完整正文；null=流式增量
 *       （最终性由前端迭代模型判定：同迭代内后随工具调用的文本降级为中间叙述）。</li>
 * </ul>
 */
public record ChatTurnEvent(
    TurnEventType type,
    String delta,
    String toolCallId,
    String toolName,
    String toolResult,
    String errorMessage,
    Long totalTokens,
    Long elapsedMs,
    Long thinkingElapsedMs,
    Long startedAt,
    Long durationMs,
    String toolStatus,
    String errorCode,
    Integer iter,
    String phase) {

  public enum TurnEventType {
    TURN_STARTED,
    TEXT_DELTA,
    THINKING_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    /** HITL：外部工具（反问）挂起，等待用户应答后由 resume 续跑。 */
    CLARIFY_REQUESTED,
    TURN_FINISHED,
    /** 停止生成：显式取消产生的终态帧。 */
    TURN_CANCELLED,
    EXCEEDED_MAX_ITERS,
    ERROR
  }

  /** 兼容旧 7 字段构造（历史/编解码既有调用点不爆破；新字段全部为 null）。 */
  public ChatTurnEvent(
      TurnEventType type,
      String delta,
      String toolCallId,
      String toolName,
      String toolResult,
      String errorMessage,
      Long totalTokens) {
    this(type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        null, null, null, null, null, null, null, null);
  }

  public static ChatTurnEvent of(TurnEventType type) {
    return new ChatTurnEvent(type, null, null, null, null, null, null);
  }

  public static ChatTurnEvent delta(TurnEventType type, String delta) {
    return new ChatTurnEvent(type, delta, null, null, null, null, null);
  }

  public static ChatTurnEvent thinkingDelta(String delta, long thinkingElapsedMs) {
    return new ChatTurnEvent(
        TurnEventType.THINKING_DELTA, delta, null, null, null, null, null,
        null, thinkingElapsedMs, null, null, null, null, null, null);
  }

  public static ChatTurnEvent tool(
      TurnEventType type, String toolCallId, String toolName, String toolResult) {
    return new ChatTurnEvent(type, null, toolCallId, toolName, toolResult, null, null);
  }

  /** 流结束：携带本轮累计 Token 用量（可为 null）。 */
  public static ChatTurnEvent finished(Long totalTokens) {
    return new ChatTurnEvent(
        TurnEventType.TURN_FINISHED, null, null, null, null, null, totalTokens);
  }

  /** 流结束 + 服务端权威轮次耗时。 */
  public static ChatTurnEvent finished(Long totalTokens, long elapsedMs) {
    return new ChatTurnEvent(
        TurnEventType.TURN_FINISHED, null, null, null, null, null, totalTokens,
        elapsedMs, null, null, null, null, null, null, null);
  }

  public static ChatTurnEvent error(String message) {
    return new ChatTurnEvent(TurnEventType.ERROR, null, null, null, null, message, null);
  }

  /** 错误帧 + 分类错误码（I6：替代纯文本 errorMessage 的唯一分类载体）。 */
  public static ChatTurnEvent error(String message, String errorCode) {
    return new ChatTurnEvent(TurnEventType.ERROR, null, null, null, null, message, null,
        null, null, null, null, null, errorCode, null, null);
  }

  /** 附带工具输出文本（执行 SQL / 返回内容），供前端链路步骤展示。 */
  public ChatTurnEvent withToolResult(String toolResult) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 工具结果终态一次性补全：耗时 + 状态（来自框架 ToolResultEndEvent.state）。 */
  public ChatTurnEvent withToolOutcome(String toolResult, long durationMs, String toolStatus) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 工具发起时刻（epoch 毫秒）。 */
  public ChatTurnEvent withStartedAt(long startedAt) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 思考增量服务端耗时。 */
  public ChatTurnEvent withThinkingElapsedMs(long thinkingElapsedMs) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 轮次总耗时（服务端权威）。 */
  public ChatTurnEvent withElapsedMs(long elapsedMs) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 迭代序（渲染顺序规范：1 起，成功模型调用边界递增、重试不递增）。 */
  public ChatTurnEvent withIter(int iter) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }

  /** 文本分层标记（TEXT_DELTA：FINAL=结果事件派生全文；null=流式增量）。 */
  public ChatTurnEvent withPhase(String phase) {
    return new ChatTurnEvent(
        type, delta, toolCallId, toolName, toolResult, errorMessage, totalTokens,
        elapsedMs, thinkingElapsedMs, startedAt, durationMs, toolStatus, errorCode, iter, phase);
  }
}