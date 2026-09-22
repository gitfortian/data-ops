package io.yak.ops.business.workflow.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Workflow Schedule 配置规范化与校验；不包含任何调度执行行为。 */
@Component
public class WorkflowScheduleValidator {
  private static final Set<String> EXECUTION_STRATEGIES =
      Set.of("PARALLEL", "SERIAL_WAIT", "SERIAL_DISCARD");
  private static final Set<String> MISFIRE_STRATEGIES = Set.of("SKIP", "FIRE_ONCE");

  public Config normalize(
      String name,
      String cronExpression,
      String timezone,
      Instant startTime,
      Instant endTime,
      String executionStrategy,
      String misfireStrategy,
      Map<String, Object> input) {
    String normalizedName = required(name, "调度名称不能为空");
    if (normalizedName.length() > 100) {
      throw new IllegalArgumentException("调度名称不能超过 100 个字符");
    }

    String cron = required(cronExpression, "Cron 表达式不能为空").replaceAll("\\s+", " ");
    int fieldCount = cron.split(" ").length;
    if (fieldCount < 5 || fieldCount > 7 || cron.length() > 160) {
      throw new IllegalArgumentException("Cron 表达式需包含 5 到 7 个字段，且不能超过 160 个字符");
    }

    String zone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
    try {
      ZoneId.of(zone);
    } catch (ZoneRulesException exception) {
      throw new IllegalArgumentException("无效的调度时区：" + zone, exception);
    }
    if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
      throw new IllegalArgumentException("调度结束时间不能早于开始时间");
    }

    return new Config(
        normalizedName,
        cron,
        zone,
        startTime,
        endTime,
        choice(executionStrategy, "SERIAL_WAIT", EXECUTION_STRATEGIES, "执行策略"),
        choice(misfireStrategy, "FIRE_ONCE", MISFIRE_STRATEGIES, "Misfire 策略"),
        input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input)));
  }

  private String choice(String value, String fallback, Set<String> supported, String label) {
    String normalized = value == null || value.isBlank()
        ? fallback
        : value.trim().toUpperCase(Locale.ROOT);
    if (!supported.contains(normalized)) {
      throw new IllegalArgumentException("不支持的" + label + "：" + normalized);
    }
    return normalized;
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  public record Config(
      String name,
      String cronExpression,
      String timezone,
      Instant startTime,
      Instant endTime,
      String executionStrategy,
      String misfireStrategy,
      Map<String, Object> input) {
  }
}
