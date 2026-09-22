package io.yak.ops.business.agent.conversation.query;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentStepRecord;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.KindAggregate;
import io.yak.ops.business.agent.domain.KindMeta;
import io.yak.ops.business.agent.domain.SessionObservability;
import io.yak.ops.business.agent.domain.SpanNode;
import io.yak.ops.business.agent.domain.StepPayload;
import io.yak.ops.business.agent.domain.TimelineEntry;
import io.yak.ops.business.agent.domain.TraceCompleteness;
import io.yak.ops.business.agent.domain.TurnObservability;
import io.yak.ops.business.agent.domain.TurnTraceView;
import io.yak.ops.business.agent.telemetry.AgentKindRegistry;
import io.yak.ops.business.agent.telemetry.PayloadEnvelope;
import io.yak.ops.business.agent.telemetry.RenderHint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * trace v2 读侧组装器（设计稿 §7.1/§7.2）：parent 链树组装、归一化时间轴、
 * kind 聚合、链路完整性（I9 断链显式化）、失败语义归一（§7.4）、注册表渲染投影。
 * 纯函数式组装，只读不写。
 */
@ConditionalOnAgentEnabled
@Component
public class TraceViewAssembler {

  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private static final int FAILURE_DETAIL_MAX_CHARS = 500;

  /** 轮次 trace v2 组装入口。 */
  public TurnTraceView assembleTurn(AgentTurnRecord turn, List<AgentStepRecord> steps) {
    List<SpanNode> spans = steps.stream().map(s -> toNode(s, null)).toList();
    Map<Long, List<SpanNode>> childrenByParent = groupChildren(spans);
    List<String> reasons = new ArrayList<>();
    if (spans.isEmpty()) {
      reasons.add("empty_steps");
    }
    // 孤儿工具调用：parent 缺失或 parent 不在集内（断链显式化，不静默）
    List<Long> ids = spans.stream().map(SpanNode::id).toList();
    for (SpanNode span : spans) {
      if ("TOOL_CALL".equals(span.kind())
          && (span.parentStepId() == null || !ids.contains(span.parentStepId()))) {
        reasons.add("orphan_tool_call:" + span.toolCallId());
      }
    }
    if (steps.stream().noneMatch(s -> AgentStepRecord.KIND_TURN_SUMMARY.equals(s.kind()))) {
      reasons.add("missing_turn_summary");
    }
    List<SpanNode> roots = spans.stream()
        .filter(s -> s.parentStepId() == null
            || !ids.contains(s.parentStepId()))
        .map(s -> attach(s, childrenByParent))
        .toList();
    return new TurnTraceView(
        turn.turnId(),
        turn.sessionId(),
        turn.status().name(),
        turn.errorCode(),
        turn.errorMessage(),
        totalTokensOf(steps),
        elapsedMillis(turn.startTime(), turn.endTime()),
        turn.createTime(),
        turn.startTime(),
        turn.endTime(),
        roots,
        spans,
        timeline(turn, steps),
        aggregate(spans),
        new TraceCompleteness(reasons.isEmpty(), reasons),
        kindMetas());
  }

  /** 会话级观测矩阵（§7.2）：turns × kinds，纯 JOIN 无新采集。 */
  public SessionObservability assembleSession(
      String sessionId, List<AgentTurnRecord> turns, List<AgentStepRecord> steps) {
    Map<String, List<AgentStepRecord>> byTurn = new LinkedHashMap<>();
    for (AgentStepRecord step : steps) {
      if (step.turnId() != null) {
        byTurn.computeIfAbsent(step.turnId(), k -> new ArrayList<>()).add(step);
      }
    }
    List<TurnObservability> rows = turns.stream()
        .map(turn -> new TurnObservability(
            turn.turnId(),
            turn.status().name(),
            turn.createTime(),
            elapsedMillis(turn.startTime(), turn.endTime()),
            totalTokensOf(byTurn.getOrDefault(turn.turnId(), List.of())),
            aggregate(toNodes(byTurn.getOrDefault(turn.turnId(), List.of())))))
        .toList();
    return new SessionObservability(sessionId, rows);
  }

  private static List<SpanNode> toNodes(List<AgentStepRecord> steps) {
    return steps.stream().map(s -> toNode(s, null)).toList();
  }

  private static SpanNode toNode(AgentStepRecord step, List<SpanNode> children) {
    PayloadEnvelope request = PayloadEnvelope.decode(step.requestJson());
    PayloadEnvelope response = PayloadEnvelope.decode(step.responseJson());
    Object retryCount = statsValue(step.statsJson(), "retryCount");
    return new SpanNode(
        step.id(),
        step.kind(),
        step.name(),
        step.status(),
        step.toolCallId(),
        step.parentStepId(),
        // attempt：V9 升列优先；存量行由 stats_json.retryCount 推导（retryCount = attempt - 1）
        step.attempt() != null ? step.attempt()
            : intOf(retryCount) != null ? intOf(retryCount) + 1 : null,
        durationOf(step),
        intOf(statsValue(step.statsJson(), "promptTokens")),
        intOf(statsValue(step.statsJson(), "completionTokens")),
        intOf(retryCount),
        step.errorCode(),
        step.errorMessage(),
        failureDetailOf(step, response),
        toPayload(request),
        toPayload(response),
        step.createTime(),
        children == null ? List.of() : children);
  }

  private static SpanNode attach(SpanNode base, Map<Long, List<SpanNode>> childrenByParent) {
    List<SpanNode> kids = childrenByParent
        .getOrDefault(base.id(), List.of())
        .stream()
        .map(child -> attach(child, childrenByParent))
        .toList();
    return new SpanNode(
        base.id(), base.kind(), base.name(), base.status(), base.toolCallId(), base.parentStepId(),
        base.attempt(), base.durationMillis(), base.promptTokens(), base.completionTokens(),
        base.retryCount(), base.errorCode(), base.errorMessage(), base.failureDetail(),
        base.request(), base.response(), base.createTime(), kids);
  }

  private static Map<Long, List<SpanNode>> groupChildren(List<SpanNode> spans) {
    Map<Long, List<SpanNode>> byParent = new LinkedHashMap<>();
    for (SpanNode span : spans) {
      if (span.parentStepId() != null) {
        byParent.computeIfAbsent(span.parentStepId(), k -> new ArrayList<>()).add(span);
      }
    }
    return byParent;
  }

  /** 失败语义归一（§7.4）：FAILED/REJECTED 且响应为业务错误结构（success:false）时提取真实原因。 */
  static String failureDetailOf(AgentStepRecord step, PayloadEnvelope response) {
    if (!"FAILED".equals(step.status()) && !"REJECTED".equals(step.status())) {
      return null;
    }
    if (response == null || response.content() == null) {
      return null;
    }
    try {
      com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(response.content());
      if (node.isObject()
          && node.has("success")
          && !node.get("success").asBoolean(true)) {
        for (String key : List.of("error", "message", "msg")) {
          if (node.has(key) && node.get(key).isTextual()
              && !node.get(key).asText().isBlank()) {
            return clip(node.get(key).asText());
          }
        }
      }
    } catch (Exception ignored) {
      // 非结构化响应：回退 errorMessage 模板句（调用方已兜底）
    }
    return null;
  }

  private static StepPayload toPayload(PayloadEnvelope envelope) {
    return envelope == null ? null : new StepPayload(
        envelope.mode(), envelope.content(), envelope.truncated(), envelope.size(),
        envelope.sha256(), envelope.messageCount(), envelope.preview());
  }

  /** span 耗时：V9 升列优先（ended-started），存量行回退 stats_json.latencyMs。 */
  private static Long durationOf(AgentStepRecord step) {
    if (step.startedAt() != null && step.endedAt() != null) {
      return ChronoUnit.MILLIS.between(step.startedAt(), step.endedAt());
    }
    Object latency = statsValue(step.statsJson(), "latencyMs");
    return latency instanceof Number n ? n.longValue() : null;
  }

  private static List<TimelineEntry> timeline(AgentTurnRecord turn, List<AgentStepRecord> steps) {
    LocalDateTime start = turn.startTime();
    return steps.stream()
        .map(step -> new TimelineEntry(
            step.id(),
            step.kind(),
            step.name(),
            step.status(),
            offsetMillis(start, step),
            durationOf(step)))
        .toList();
  }

  /** 相对轮次起点的毫秒偏移：V9 行用 started_at 精确值，存量行以 create_time 近似（≈ 终态落库时刻）。 */
  private static Long offsetMillis(LocalDateTime turnStart, AgentStepRecord step) {
    if (turnStart == null) {
      return null;
    }
    LocalDateTime effective = step.startedAt() != null ? step.startedAt() : step.createTime();
    return ChronoUnit.MILLIS.between(turnStart, effective);
  }

  static List<KindAggregate> aggregate(List<SpanNode> spans) {
    Map<String, List<SpanNode>> byKind = new TreeMap<>();
    for (SpanNode span : spans) {
      byKind.computeIfAbsent(span.kind(), k -> new ArrayList<>()).add(span);
    }
    return byKind.entrySet().stream()
        .map(TraceViewAssembler::aggregateOf)
        .toList();
  }

  private static KindAggregate aggregateOf(Map.Entry<String, List<SpanNode>> entry) {
    List<SpanNode> nodes = entry.getValue();
    List<Long> durations = nodes.stream()
        .map(SpanNode::durationMillis)
        .filter(d -> d != null && d >= 0)
        .sorted()
        .toList();
    long total = durations.stream().mapToLong(Long::longValue).sum();
    long max = durations.isEmpty() ? 0L : durations.get(durations.size() - 1);
    long avg = durations.isEmpty() ? 0L : total / durations.size();
    long p95 = durations.isEmpty() ? 0L : durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
    long prompt = nodes.stream().map(SpanNode::promptTokens).filter(t -> t != null).mapToLong(Integer::longValue).sum();
    long completion = nodes.stream().map(SpanNode::completionTokens).filter(t -> t != null).mapToLong(Integer::longValue).sum();
    return new KindAggregate(
        entry.getKey(),
        nodes.size(),
        total,
        max,
        avg,
        p95,
        prompt > 0 ? prompt : null,
        completion > 0 ? completion : null);
  }

  static List<KindMeta> kindMetas() {
    return AgentKindRegistry.all().stream()
        .sorted(Comparator.comparingInt(spec -> spec.renderHint().order()))
        .map(spec -> {
          RenderHint hint = spec.renderHint();
          return new KindMeta(spec.kind(), spec.title(), hint.color(), hint.icon(), hint.order());
        })
        .toList();
  }

  private static Long totalTokensOf(List<AgentStepRecord> steps) {
    for (AgentStepRecord step : steps) {
      if (AgentStepRecord.KIND_TURN_SUMMARY.equals(step.kind())) {
        Object tokens = statsValue(step.statsJson(), "totalTokens");
        if (tokens instanceof Number n) {
          return n.longValue();
        }
      }
    }
    return null;
  }

  private static Long elapsedMillis(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null) {
      return null;
    }
    return ChronoUnit.MILLIS.between(start, end);
  }

  private static Integer intOf(Object value) {
    return value instanceof Number n ? n.intValue() : null;
  }

  private static Object statsValue(String statsJson, String key) {
    if (statsJson == null || statsJson.isBlank()) {
      return null;
    }
    try {
      return JSON.readValue(statsJson, Map.class).get(key);
    } catch (Exception e) {
      return null;
    }
  }

  private static String clip(String text) {
    return text.length() <= FAILURE_DETAIL_MAX_CHARS
        ? text
        : text.substring(0, FAILURE_DETAIL_MAX_CHARS) + "…";
  }
}
