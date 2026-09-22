package io.yak.ops.business.quality.workspace;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.LogLevel;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Projects persisted execution evidence into deterministic structured log lines. */
@Component
public class QualityExecutionLogProjector {

  public StructuredLog project(Execution execution) {
    List<LogLine> lines = new ArrayList<>();
    lines.add(
        new LogLine(
            execution.queuedAt(),
            LogLevel.INFO,
            "DISPATCH",
            "执行任务已创建，触发方式："
                + (execution.triggerType() == TriggerType.SCHEDULE ? "调度触发" : "手动触发")
                + "，操作人："
                + safe(execution.operator())));

    if (execution.startedAt() != null) {
      lines.add(
          new LogLine(
              execution.startedAt(),
              LogLevel.INFO,
              "EXECUTION",
              "开始执行质量检查，共 " + execution.totalRules() + " 条规则"));
    }

    for (RuleExecution rule : execution.rules()) {
      lines.add(
          new LogLine(
              fallback(rule.createdAt(), execution.startedAt(), execution.queuedAt()),
              logLevel(rule.checkResult()),
              "RULE",
              ruleMessage(rule)));
    }

    if (hasText(execution.errorMessage())) {
      lines.add(
          new LogLine(
              fallback(execution.finishedAt(), execution.startedAt(), execution.queuedAt()),
              LogLevel.ERROR,
              "EXECUTION",
              execution.errorMessage()));
    }

    if (execution.finishedAt() != null) {
      lines.add(
          new LogLine(
              execution.finishedAt(),
              execution.checkResult() == CheckResult.PASSED ? LogLevel.INFO : LogLevel.WARN,
              "FINISH",
              "执行结束：通过 "
                  + execution.passedRules()
                  + "，未通过 "
                  + execution.failedRules()
                  + "，异常 "
                  + execution.errorRules()
                  + "，耗时 "
                  + (execution.durationMs() == null ? 0 : execution.durationMs())
                  + " ms"));
    }
    return new StructuredLog(execution.executionNo(), lines);
  }

  private static String ruleMessage(RuleExecution rule) {
    StringBuilder message =
        new StringBuilder()
            .append("规则「")
            .append(rule.ruleName())
            .append("」")
            .append(resultLabel(rule.checkResult()));
    if (hasText(rule.metricValue())) {
      message.append("，实际值：").append(rule.metricValue());
    }
    if (hasText(rule.expectedValue())) {
      message.append("，期望值：").append(rule.expectedValue());
    }
    if (rule.durationMs() != null) {
      message.append("，耗时：").append(rule.durationMs()).append(" ms");
    }
    if (hasText(rule.errorMessage())) {
      message.append("，错误：").append(rule.errorMessage());
    }
    return message.toString();
  }

  private static String resultLabel(CheckResult result) {
    return switch (result) {
      case PASSED -> "通过";
      case NOT_PASSED -> "未通过";
      case ERROR -> "执行异常";
      case RUNNING -> "运行中";
      case NOT_RUN -> "未运行";
    };
  }

  private static LogLevel logLevel(CheckResult result) {
    return switch (result) {
      case PASSED, RUNNING, NOT_RUN -> LogLevel.INFO;
      case NOT_PASSED -> LogLevel.WARN;
      case ERROR -> LogLevel.ERROR;
    };
  }

  private static LocalDateTime fallback(LocalDateTime... values) {
    for (LocalDateTime value : values) {
      if (value != null) {
        return value;
      }
    }
    return LocalDateTime.now();
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "system" : value;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record LogLine(LocalDateTime time, LogLevel level, String stage, String message) {}

  public record StructuredLog(String executionNo, List<LogLine> lines) {
    public StructuredLog {
      lines = lines == null ? List.of() : List.copyOf(lines);
    }
  }
}
