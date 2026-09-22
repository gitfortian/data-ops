package io.yak.ops.business.agent.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentStepMapper;
import io.yak.ops.business.agent.dao.model.AgentStepPO;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 步骤级执行记录器（生产化方案 L1 地基 / 可观测性体系 O1 的落库端点）。
 *
 * <p>每一次 LLM 调用 / 工具调用都应恰好落一条 {@code yak_agent_step}；
 * 失败也记账；记录失败只告警，不反向影响已发生的执行事实。</p>
 *
 * <p>O1 收口：本类是 <b>唯一落库端点</b>（单写入口不变，采集组装上移至
 * {@link AgentObservationCollector}）；span 计时升列（started_at/ended_at/attempt，
 * V9）；kind 落库前经 {@link AgentKindRegistry} 准入校验，未注册拒绝并告警。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnAgentEnabled
public class AgentStepRecorder {

  /** 步骤类型常量（与 {@link AgentKindRegistry} 保持同源，注册表为单一真相）。 */
  public static final String KIND_LLM_CALL = AgentKindRegistry.KIND_LLM_CALL;
  public static final String KIND_TOOL_CALL = AgentKindRegistry.KIND_TOOL_CALL;
  public static final String KIND_GUARD = AgentKindRegistry.KIND_GUARD;
  public static final String KIND_HITL = AgentKindRegistry.KIND_HITL;
  public static final String KIND_COMPACTION = AgentKindRegistry.KIND_COMPACTION;
  public static final String KIND_MEMORY_FLUSH = AgentKindRegistry.KIND_MEMORY_FLUSH;
  public static final String KIND_MEMORY_RECALL = AgentKindRegistry.KIND_MEMORY_RECALL;
  public static final String KIND_MEMORY_CONSOLIDATE = AgentKindRegistry.KIND_MEMORY_CONSOLIDATE;
  public static final String KIND_TURN_SUMMARY = AgentKindRegistry.KIND_TURN_SUMMARY;

  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_REJECTED = "REJECTED";

  /** 分类错误码（DOMAIN S10 风格）。 */
  public static final String ERROR_TIMEOUT = "TIMEOUT";
  public static final String ERROR_USER = "USER_ERROR";
  public static final String ERROR_PROVIDER = "PROVIDER_ERROR";
  public static final String ERROR_GUARD_REJECTED = "GUARD_REJECTED";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_TEXT_LENGTH = PayloadPolicy.DEFAULT_MAX_INLINE_CHARS;

  private final AgentStepMapper stepMapper;

  /** turn -> 最近一次成功的 LLM_CALL step id（工具调用记于此下的父节点，形成 turn 内 trace 链）。 */
  private final java.util.Map<String, Long> lastLlmStepIdByTurn =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** 轮次终态收敛时清理 turn 级记账状态（由执行器 settle 回调，防长生命周期泄漏）。 */
  public void clearTurn(String turnId) {
    if (turnId != null) {
      lastLlmStepIdByTurn.remove(turnId);
    }
  }

  /** 最近一次 LLM_CALL step id（供上下文父链调试，无则 null）。 */
  Long lastLlmStepIdOf(String turnId) {
    return turnId == null ? null : lastLlmStepIdByTurn.get(turnId);
  }

  /** 执行任意动作并自动落一条步骤记录：成功/失败/耗时全部捕获，结果原样返回。 */
  public <T> T record(
      String sessionId, String kind, String name, Supplier<String> requestSupplier,
      Callable<T> action) {
    long start = System.currentTimeMillis();
    String request = truncate(safe(requestSupplier));
    try {
      T result = action.call();
      insert(sessionId, kind, name, STATUS_COMPLETED, request,
          result == null ? null : truncate(String.valueOf(result)), null, null, start);
      return result;
    } catch (java.util.concurrent.TimeoutException e) {
      insert(sessionId, kind, name, STATUS_FAILED, request, null,
          ERROR_TIMEOUT, "执行超时", start);
      throw new IllegalStateException("[TURN_TIMEOUT] 步骤执行超时：" + name
          + "；solution=稍后重试或缩小查询范围", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      insert(sessionId, kind, name, STATUS_FAILED, request, null,
          "INTERRUPTED", "执行被中断", start);
      throw new IllegalStateException("[INTERRUPTED] 步骤被中断：" + name, e);
    } catch (Exception e) {
      insert(sessionId, kind, name, STATUS_FAILED, request, null,
          classify(e), safeMessage(e), start);
      sneaky(e);
      return null; // unreachable
    }
  }

  /** 直接落一条已完成步骤（无包裹动作的场景，如 TURN_SUMMARY 汇总）。 */
  public void recordCompleted(
      String sessionId, String kind, String name, String requestJson, String statsJson) {
    insert(sessionId, null, kind, name, null, STATUS_COMPLETED, requestJson, null, null, null,
        System.currentTimeMillis(), statsJson, null, null, null);
  }

  /** 直接落一条已完成步骤并绑定轮次归属（TURN_SUMMARY 等 executor 场景）。 */
  public void recordCompleted(
      String sessionId, String turnId, String kind, String name, String requestJson,
      String statsJson) {
    insert(sessionId, turnId, kind, name, null, STATUS_COMPLETED, requestJson, null, null, null,
        System.currentTimeMillis(), statsJson, null, null, null);
  }

  /**
   * 模型调用尝试记账：每次尝试恰好一条，失败也记账。
   * retryCount = attempt - 1（首次为 0）。
   *
   * @return 插入成功的 step id（失败为 null）；成功时记录为 turn 的工具父节点。
   */
  public Long recordLlmCall(
      String sessionId, String turnId, String modelName, boolean success, long latencyMillis,
      int promptTokens, int completionTokens, int attempt, String errorCode,
      String errorPreview) {
    return recordLlmCallAttempt(sessionId, turnId, modelName, success, latencyMillis,
        System.currentTimeMillis() - Math.max(0, latencyMillis),
        promptTokens, completionTokens, attempt, errorCode, errorPreview, null);
  }

  /**
   * 模型调用尝试记账（O1 完整形态）：span 起止升列 + 请求内容观测（SUMMARY_HASH 信封，
   * 由 Collector 经策略引擎产出）。
   */
  public Long recordLlmCallAttempt(
      String sessionId, String turnId, String modelName, boolean success, long latencyMillis,
      long startedAtEpochMillis, int promptTokens, int completionTokens, int attempt,
      String errorCode, String errorPreview, String requestJson) {
    String stats;
    try {
      stats =
          JSON.writeValueAsString(
              java.util.Map.of(
                  "promptTokens", promptTokens,
                  "completionTokens", completionTokens,
                  "latencyMs", latencyMillis,
                  "retryCount", Math.max(0, attempt - 1)));
    } catch (Exception e) {
      stats = "{\"latencyMs\":-1}";
    }
    Long stepId =
        insertSpan(sessionId, turnId, KIND_LLM_CALL,
            modelName == null ? "llm" : modelName,
            null,
            success ? STATUS_COMPLETED : STATUS_FAILED,
            requestJson,
            null,
            success ? null : errorCode,
            success ? null : truncate(errorPreview),
            startedAtEpochMillis,
            latencyMillis,
            stats,
            attempt);
    if (stepId != null && turnId != null && success) {
      // 下一次（或并行批）工具调用以此为父：一次 LLM 调用 -> 其触发的工具链
      lastLlmStepIdByTurn.put(turnId, stepId);
    }
    return stepId;
  }

  /**
   * 工具执行审计（onActing 中间件回写，O1 完整形态）：按单个工具调用记账，成功与失败都记账。
   * toolCallId 与事件帧 TOOL_CALL/TOOL_RESULT 的 toolCallId 一对一关联（因果 join 键）；
   * 入参落 requestJson（O1 修复：此前 Track 捕获后丢失）、输出摘要入 responseJson、
   * 失败原因入 errorCode/errorPreview，span 起止升列。
   */
  public void recordToolCallSpan(
      String sessionId, String turnId, String toolCallId, String toolName, boolean success,
      long latencyMillis, long startedAtEpochMillis, String requestJson, String resultJson,
      String errorCode, String errorPreview) {
    insertSpan(
        sessionId,
        turnId,
        KIND_TOOL_CALL,
        toolName == null || toolName.isBlank() ? "tool" : toolName,
        toolCallId,
        success ? STATUS_COMPLETED : STATUS_FAILED,
        requestJson,
        success ? resultJson : null,
        success ? null : errorCode,
        success ? null : truncate(errorPreview),
        startedAtEpochMillis,
        latencyMillis,
        statsJsonOf(latencyMillis),
        null);
  }

  /** 兼容旧签名（无入参/无 span 起止）：历史调用点过渡。 */
  public void recordToolCall(
      String sessionId, String turnId, String toolCallId, String toolName, boolean success,
      long latencyMillis, String resultJson, String errorCode, String errorPreview) {
    recordToolCallSpan(sessionId, turnId, toolCallId, toolName, success, latencyMillis,
        System.currentTimeMillis() - Math.max(0, latencyMillis), null, resultJson,
        errorCode, errorPreview);
  }

  /**
   * 事件型 span 通用写入（O3 观测点：GUARD/HITL 等）：kind 经注册表准入校验，
   * 可携带 toolCallId 与事件帧 join；即时事件耗时记 0。未注册 kind 拒绝（告警）。
   */
  public void recordEventSpan(
      String sessionId, String turnId, String kind, String name, String toolCallId,
      String status, String requestJson, String responseJson, String errorCode,
      String errorPreview) {
    long now = System.currentTimeMillis();
    insertSpan(sessionId, turnId, kind, name, toolCallId, status, requestJson, responseJson,
        errorCode, errorPreview, now, 0L, statsJsonOf(0L), null);
  }

  private static String statsJsonOf(long latencyMillis) {
    try {
      return JSON.writeValueAsString(java.util.Map.of("latencyMs", latencyMillis));
    } catch (Exception e) {
      return "{\"latencyMs\":-1}";
    }
  }

  private void insert(
      String sessionId, String kind, String name, String status, String request,
      String response, String errorCode, String errorMessage, long startMillis) {
    insert(sessionId, null, kind, name, null, status, request, response, errorCode,
        errorMessage, startMillis, null, null, null, null);
  }

  private void insert(
      String sessionId, String turnId, String kind, String name, String toolCallId,
      String status, String request, String response, String errorCode, String errorMessage,
      Long startMillis, String statsJson, Long endedAtEpochMillis, Long startedAtEpochMillis,
      Integer attempt) {
    long latency = endedAtEpochMillis != null && startedAtEpochMillis != null
        ? Math.max(0, endedAtEpochMillis - startedAtEpochMillis)
        : startMillis != 0L ? System.currentTimeMillis() - startMillis : -1L;
    insertSpan(sessionId, turnId, kind, name, toolCallId, status, request, response,
        errorCode, errorMessage,
        startedAtEpochMillis != null ? startedAtEpochMillis
            : startMillis != 0L ? System.currentTimeMillis() - Math.max(0, latency) : null,
        latency, statsJson, attempt);
  }

  /**
   * 统一落库：TOOL 类挂最近一次 LLM_CALL（turn 内 trace 父链），kind 经注册表准入校验；
   * 返回插入 id（失败返回 null，不影响执行事实）。
   */
  private Long insertSpan(
      String sessionId, String turnId, String kind, String name, String toolCallId,
      String status, String request, String response, String errorCode, String errorMessage,
      Long startedAtEpochMillis, long latencyMillis, String statsJson, Integer attempt) {
    try {
      KindSpec spec = AgentKindRegistry.require(kind);
      AgentStepPO po = new AgentStepPO();
      po.setSessionId(sessionId);
      po.setTurnId(turnId);
      // 父链：LAST_LLM 规则类（TOOL_CALL 等）指向触发它的 LLM_CALL；其余为根（null）
      po.setParentStepId(spec.parentRule() == KindSpec.ParentRule.LAST_LLM
          ? lastLlmStepIdByTurn.get(turnId)
          : null);
      po.setToolCallId(toolCallId);
      po.setKind(kind);
      po.setName(name);
      po.setStatus(status);
      po.setRequestJson(request);
      po.setResponseJson(response);
      po.setErrorCode(errorCode);
      po.setErrorMessage(truncate(errorMessage));
      po.setStatsJson(statsJson != null ? statsJson : statsJsonOf(latencyMillis));
      if (startedAtEpochMillis != null) {
        po.setStartedAt(toLocalDateTime(startedAtEpochMillis));
        po.setEndedAt(toLocalDateTime(startedAtEpochMillis + Math.max(0, latencyMillis)));
      }
      po.setAttempt(attempt);
      stepMapper.insert(po);
      return po.getId();
    } catch (IllegalArgumentException unregistered) {
      log.warn("rejected unregistered agent step kind: kind={}, name={}", kind, name);
      return null;
    } catch (Exception e) {
      log.warn("failed to record agent step: sessionId={}, kind={}, name={}",
          sessionId, kind, name, e);
      return null;
    }
  }

  private static LocalDateTime toLocalDateTime(long epochMillis) {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault());
  }

  private static String classify(Throwable error) {
    if (error instanceof java.util.concurrent.TimeoutException) {
      return ERROR_TIMEOUT;
    }
    String message = error.getMessage() == null ? "" : error.getMessage();
    if (message.contains("[GUARD_REJECTED]")) {
      return ERROR_GUARD_REJECTED;
    }
    if (message.contains("401") || message.contains("403") || message.contains("429")) {
      return ERROR_USER;
    }
    return ERROR_PROVIDER;
  }

  private static String safe(Supplier<String> supplier) {
    try {
      return truncate(supplier.get());
    } catch (Exception e) {
      return null;
    }
  }

  private static String truncate(String text) {
    if (text == null) {
      return null;
    }
    return text.length() <= MAX_TEXT_LENGTH
        ? text
        : text.substring(0, MAX_TEXT_LENGTH / 2) + "\n...[truncated]...\n"
            + text.substring(text.length() - MAX_TEXT_LENGTH / 2);
  }

  private static String safeMessage(Throwable error) {
    String message = error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName() : error.getMessage();
    return message;
  }

  @FunctionalInterface
  private interface SneakyThrow {
    void throwIt(Throwable t);
  }

  private static void sneaky(Throwable t) {
    SneakyThrow sneaky = AgentStepRecorder::sneakyInternal;
    sneaky.throwIt(t);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyInternal(Throwable t) throws E {
    throw (E) t;
  }
}
