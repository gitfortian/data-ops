package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.telemetry.AgentKindRegistry;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.business.agent.telemetry.KindSpec;
import io.yak.ops.business.agent.telemetry.PayloadEnvelope;
import io.yak.ops.business.agent.telemetry.PayloadPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 观测采集统一入口（可观测性体系 O1 协议层，O4 接入运行时治理）：KindSpec 准入校验
 * + 载荷策略引擎 + span 组装，落库收敛到 {@link AgentStepRecorder} 单写端点。
 *
 * <p>本类与中间件同住 runtime（依赖 config 读取治理配置，telemetry 只留 dao 依赖的
 * 写端点与策略工具）。采集面边界（架构守护锁定）：仅 runtime 中间件与 conversation
 * 执行器可触达本类；业务工具与非 runtime 层禁止直调。</p>
 *
 * <p>治理取值全部<b>调用时现读</b>（AgentProperties.Observability：总开关 / per-kind
 * 开关 / INLINE 上限 / LLM 请求升档），严禁静态缓存；接入 Phase 2 配置热更后即达
 * 改值无重启生效。开关关闭时零写入，trace 读路径不受影响。</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
public class AgentObservationCollector {

  /** LLM 请求摘要档的末条用户消息预览长度（排障最小可见性）。 */
  static final int LLM_PREVIEW_CHARS = 200;

  private final AgentStepRecorder recorder;
  private final AgentProperties properties;
  private final AgentDynamicConfigService dynamicConfig;

  public AgentObservationCollector(
      AgentStepRecorder recorder, AgentProperties properties, AgentDynamicConfigService dynamicConfig) {
    this.recorder = recorder;
    this.properties = properties;
    this.dynamicConfig = dynamicConfig;
  }

  /**
   * 模型调用尝试记账：请求内容经 SUMMARY_HASH 档观测（G5——消息条数/末条用户消息预览/
   * 总字符/sha256，原文不落库；debug 升档走 INLINE），span 起止升列（V9）。
   * 框架类型依赖（Msg 提取）收敛在 runtime 中间件侧，本类只接 neutral 字符串。
   */
  public Long llmCallAttempt(
      String sessionId, String turnId, String modelName, boolean success, long latencyMillis,
      long startedAtEpochMillis, int promptTokens, int completionTokens, int attempt,
      String errorCode, String errorPreview,
      Integer requestMessageCount, String requestText, String requestLastUserPreview) {
    if (!recordingAllowed(AgentKindRegistry.KIND_LLM_CALL)) {
      return null;
    }
    String request = requestText == null ? null
        : llmRequestEnvelope(requestText, requestMessageCount, requestLastUserPreview).encode();
    return recorder.recordLlmCallAttempt(
        sessionId, turnId, modelName, success, latencyMillis, startedAtEpochMillis,
        promptTokens, completionTokens, attempt, errorCode, errorPreview, request);
  }

  /**
   * 工具调用记账：入参/输出经 INLINE 档（JSON 感知截断）落库。
   * O1 修复：此前 ToolAuditMiddleware 捕获的入参在 Recorder 边界被丢弃（死字段）。
   */
  public void toolCall(
      String sessionId, String turnId, String toolCallId, String toolName, boolean success,
      long latencyMillis, long startedAtEpochMillis, String requestJson, String resultJson,
      String errorCode, String errorPreview) {
    if (!recordingAllowed(AgentKindRegistry.KIND_TOOL_CALL)) {
      return;
    }
    KindSpec spec = AgentKindRegistry.require(AgentKindRegistry.KIND_TOOL_CALL);
    recorder.recordToolCallSpan(
        sessionId, turnId, toolCallId, toolName, success, latencyMillis, startedAtEpochMillis,
        envelopeOrRaw(requestJson, spec.requestMode()),
        envelopeOrRaw(resultJson, spec.responseMode()),
        errorCode, errorPreview);
  }

  /**
   * 事件型记账（executor 生命周期场景：TURN_SUMMARY 等）：kind 准入校验后透传。
   * 未注册 kind 只告警不外抛（best-effort：观测失败绝不反向影响执行事实）。
   */
  public void completedEvent(
      String sessionId, String turnId, String kind, String name, String requestJson,
      String statsJson) {
    if (!recordingAllowed(kind)) {
      return;
    }
    try {
      KindSpec spec = AgentKindRegistry.require(kind);
      recorder.recordCompleted(sessionId, turnId, kind, name,
          envelopeOrRaw(requestJson, spec.requestMode()), statsJson);
    } catch (IllegalArgumentException unregistered) {
      log.warn("rejected unregistered agent step kind: kind={}, name={}", kind, name);
    }
  }

  /**
   * 事件型 span 通用写入（O3 观测点统一入口）：可携带 toolCallId 与事件帧 join、
   * 自定义终态；载荷按 KindSpec 声明档位加工。未注册 kind 只告警不外抛。
   */
  public void event(
      String sessionId, String turnId, String kind, String name, String toolCallId,
      String status, String requestJson, String responseJson, String errorCode,
      String errorPreview) {
    if (!recordingAllowed(kind)) {
      return;
    }
    try {
      KindSpec spec = AgentKindRegistry.require(kind);
      recorder.recordEventSpan(sessionId, turnId, kind, name, toolCallId, status,
          envelopeOrRaw(requestJson, spec.requestMode()),
          envelopeOrRaw(responseJson, spec.responseMode()),
          errorCode, errorPreview);
    } catch (IllegalArgumentException unregistered) {
      log.warn("rejected unregistered agent step kind: kind={}, name={}", kind, name);
    }
  }

  /**
   * Guard 拒绝观测（O3）：语义/取数守卫拒绝时落 GUARD span（父=触发它的 LLM_CALL）。
   * 检测点在 ToolAuditMiddleware（该处已持有工具入参与输出全文），本方法只负责落账。
   */
  public void guardRejected(
      String sessionId, String turnId, String toolCallId, String toolName, String requestJson,
      String detail) {
    event(sessionId, turnId, AgentKindRegistry.KIND_GUARD,
        toolName == null || toolName.isBlank() ? "guard" : toolName,
        toolCallId, "REJECTED", requestJson, null,
        AgentStepRecorder.ERROR_GUARD_REJECTED, detail);
  }

  public void clearTurn(String turnId) {
    recorder.clearTurn(turnId);
  }

  /** 总开关（DB 动态键覆盖种子值）+ per-kind 开关：调用时现读（O4 治理 + 热更新）。 */
  private boolean recordingAllowed(String kind) {
    if (!dynamicConfig.enabled(
        AgentDynamicConfigService.KEY_OBSERVABILITY_ENABLED,
        properties.getObservability().isEnabled())) {
      return false;
    }
    Boolean override = properties.getObservability().getKindEnabled().get(kind);
    return override == null || override;
  }

  private PayloadEnvelope llmRequestEnvelope(
      String requestText, Integer messageCount, String lastUserPreview) {
    if (properties.getObservability().isLlmRequestInlineDebug()) {
      // 排障升档：临时落原文（仍受 INLINE 上限截断），用完即关
      return PayloadPolicy.inline(requestText, maxInlineChars());
    }
    return PayloadPolicy.summaryHash(
        requestText,
        messageCount == null ? 0 : messageCount,
        lastUserPreview,
        LLM_PREVIEW_CHARS);
  }

  /** 按 KindSpec 声明的档位加工载荷；NONE 档不落、OMITTED 显式省略。 */
  private String envelopeOrRaw(String text, KindSpec.PayloadMode mode) {
    if (text == null) {
      return null;
    }
    return switch (mode) {
      case INLINE -> PayloadPolicy.inline(text, maxInlineChars()).encode();
      case SUMMARY_HASH -> PayloadPolicy.summaryHash(text, 0, null, 0).encode();
      case HASH_ONLY -> PayloadPolicy.hashOnly(text).encode();
      case OMITTED -> new PayloadEnvelope(
          PayloadEnvelope.MODE_OMITTED, null, null, (long) text.length(), null, null, null).encode();
      case NONE -> null;
    };
  }

  private int maxInlineChars() {
    int configured = properties.getObservability().getMaxInlineChars();
    return configured > 0 ? configured : PayloadPolicy.DEFAULT_MAX_INLINE_CHARS;
  }
}
