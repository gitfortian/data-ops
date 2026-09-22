package io.yak.ops.business.agent.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 对话 HTTP 出参视图集合。 */
public final class AgentViews {

  /** 会话列表项。 */
  public record SessionVO(String sessionId, String title, LocalDateTime updateTime) {}

  /** 轮次提交回执：订阅 {@code GET /chat/turns/{turnId}/events} 获取事件流。 */
  public record TurnSubmittedVO(String turnId) {}

  /** 报告列表项（不含正文）。 */
  public record ReportVO(
      Long id, String sessionId, String title, LocalDateTime createTime, LocalDateTime updateTime) {}

  /** 报告详情（含正文）。 */
  public record ReportDetailVO(
      Long id,
      String sessionId,
      String title,
      String content,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  /** 会话历史轮次（O2：turnId 供前端懒加载 trace v2 权威视图，可空）。 */
  public record HistoryVO(String role, String content, String turnId, List<TraceStepVO> trace) {}

  /** 历史 trace 步骤（思考/工具调用）。 */
  public record TraceStepVO(String kind, String text, String toolCallId, String toolName, String resultText) {}

  /** 轮次 trace 详情 v2（O2）：turn 元事实 + 树/平铺/时间轴/聚合/完整性/渲染投影六视图。 */
  public record TurnTraceVO(
      String turnId,
      String sessionId,
      String status,
      String errorCode,
      String errorMessage,
      Long totalTokens,
      Long elapsedMillis,
      LocalDateTime createTime,
      LocalDateTime startTime,
      LocalDateTime endTime,
      List<SpanNodeVO> tree,
      List<SpanNodeVO> spans,
      List<TimelineEntryVO> timeline,
      List<KindAggregateVO> aggregates,
      CompletenessVO completeness,
      List<KindMetaVO> kinds) {}

  /** 解码后步骤载荷（信封四档：INLINE/SUMMARY_HASH/HASH_ONLY/OMITTED）。 */
  public record StepPayloadVO(
      String mode,
      String content,
      Boolean truncated,
      Long size,
      String sha256,
      Integer messageCount,
      String preview) {}

  /** span 节点（树与平铺共用；children 空即叶子）。 */
  public record SpanNodeVO(
      Long id,
      String kind,
      String name,
      String status,
      String toolCallId,
      Long parentStepId,
      Integer attempt,
      Long durationMillis,
      Integer promptTokens,
      Integer completionTokens,
      Integer retryCount,
      String errorCode,
      String errorMessage,
      String failureDetail,
      StepPayloadVO request,
      StepPayloadVO response,
      LocalDateTime createTime,
      List<SpanNodeVO> children) {}

  /** 归一化时间轴条目：offsetMillis 相对轮次起点（存量行可能为 null，前端顺序兜底）。 */
  public record TimelineEntryVO(
      Long stepId, String kind, String name, String status, Long offsetMillis, Long durationMillis) {}

  /** kind 维度聚合。 */
  public record KindAggregateVO(
      String kind,
      long count,
      long totalMillis,
      long maxMillis,
      long avgMillis,
      long p95Millis,
      Long promptTokens,
      Long completionTokens) {}

  /** 链路完整性（I9：未观测到 ≠ 成功，断链显式化）。 */
  public record CompletenessVO(boolean complete, List<String> reasons) {}

  /** 观测类型渲染投影（注册表驱动，前端零改动渲染新 kind）。 */
  public record KindMetaVO(String kind, String title, String color, String icon, int order) {}

  /** 会话级观测矩阵（O2 §7.2）。 */
  public record SessionObservabilityVO(String sessionId, List<TurnObservabilityVO> turns) {}

  /** 观测矩阵行：一个轮次 × 各 kind 聚合。 */
  public record TurnObservabilityVO(
      String turnId,
      String status,
      LocalDateTime createTime,
      Long elapsedMillis,
      Long totalTokens,
      List<KindAggregateVO> byKind) {}

  /** 运行时动态配置条目（治理界面）。 */
  public record ConfigItemVO(
      String key, String kind, String description, String dbValue) {}

  /** 查询审计条目。 */
  public record QueryAuditVO(
      Long id,
      String sessionId,
      Long datasetId,
      String queryId,
      String status,
      String errorMessage,
      Integer returnedRows,
      Boolean truncated,
      Long elapsedMillis,
      LocalDateTime createTime) {}

  /** 技能条目（skills 在线管理）。 */
  public record SkillVO(
      String skillId,
      String name,
      String description,
      java.util.Map<String, Object> metadata,
      String content,
      Boolean enabled,
      Integer version,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  private AgentViews() {}
}
