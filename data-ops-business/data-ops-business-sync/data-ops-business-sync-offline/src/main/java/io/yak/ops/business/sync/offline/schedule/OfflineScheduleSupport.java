package io.yak.ops.business.sync.offline.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 解析和校验任务级调度配置，不承担数据库访问和运行时触发。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleSupport {

  private final ObjectMapper objectMapper;

  public OfflineScheduleSupport(@Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OfflineSchedule prepare(Long definitionId, JsonNode schedule) {
    String cron = firstText(schedule, "cron", "cronExpression");
    boolean enabled = enabled(schedule, cron);
    if (enabled && !StringUtils.hasText(cron)) {
      throw new IllegalArgumentException("启用调度时必须填写 Cron 表达式");
    }
    if (StringUtils.hasText(cron)) {
      cron = normalizeQuartzCron(cron);
    }

    int attempts = Math.max(1, maxAttempts(schedule));
    int backoff = Math.max(1, backoffSeconds(schedule));
    return new OfflineSchedule(
        definitionId,
        cron,
        enabled,
        attempts,
        backoff,
        null,
        null,
        writeNullable(schedule));
  }

  String normalizeQuartzCron(String value) {
    CronExpression.parse(value);
    String[] fields = value.trim().replaceAll("\\s+", " ").split(" ");
    if (fields.length < 6) {
      return String.join(" ", Arrays.asList(fields));
    }

    if ("*".equals(fields[3]) && !"?".equals(fields[5])) {
      fields[3] = "?";
    } else if (!"?".equals(fields[3]) && "*".equals(fields[5])) {
      fields[5] = "?";
    }
    return String.join(" ", Arrays.asList(fields));
  }

  private boolean enabled(JsonNode node, String cron) {
    if (node == null || node.isNull()) {
      return false;
    }
    if (node.hasNonNull("enabled")) {
      return node.path("enabled").asBoolean(false);
    }

    String type = text(node, "scheduleRunType");
    if (StringUtils.hasText(type)) {
      return !"pause".equalsIgnoreCase(type) && !"paused".equalsIgnoreCase(type);
    }
    return StringUtils.hasText(cron);
  }

  private int maxAttempts(JsonNode node) {
    if (node == null || node.isNull()) {
      return 1;
    }
    if (node.hasNonNull("retryOnFailure")) {
      return node.path("retryOnFailure").asBoolean() ? 2 : 1;
    }
    if (!node.path("autoRetry").asBoolean(true)) {
      return 1;
    }

    int configured = node.path("retryPolicy").path("maxAttempts").asInt(0);
    if (configured > 0) {
      return configured;
    }
    return Math.max(1, node.path("retryTimes").asInt(0) + 1);
  }

  private int backoffSeconds(JsonNode node) {
    if (node == null || node.isNull()) {
      return 60;
    }

    int configured = node.path("retryPolicy").path("backoffSeconds").asInt(0);
    if (configured > 0) {
      return configured;
    }

    configured = node.path("retryIntervalSeconds").asInt(0);
    if (configured > 0) {
      return configured;
    }
    return Math.max(1, node.path("retryInterval").asInt(1)) * 60;
  }

  private String firstText(JsonNode node, String first, String second) {
    String value = text(node, first);
    return StringUtils.hasText(value) ? value : text(node, second);
  }

  private String text(JsonNode node, String field) {
    if (node == null || !node.hasNonNull(field)) {
      return null;
    }
    String value = node.path(field).asText(null);
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String writeNullable(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化调度配置失败", exception);
    }
  }
}
