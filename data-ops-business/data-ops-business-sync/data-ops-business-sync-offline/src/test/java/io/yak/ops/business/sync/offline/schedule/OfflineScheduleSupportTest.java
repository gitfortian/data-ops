package io.yak.ops.business.sync.offline.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import org.junit.jupiter.api.Test;

class OfflineScheduleSupportTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OfflineScheduleSupport support = new OfflineScheduleSupport(objectMapper);

  @Test
  void shouldNormalizeCronAndLeaveNextFireTimeToYakSchedule() {
    ObjectNode config = objectMapper.createObjectNode();
    config.put("enabled", true);
    config.put("cron", "0 5 9 * * *");
    config.put("retryTimes", 2);
    config.put("retryIntervalSeconds", 30);

    OfflineSchedule schedule = support.prepare(42L, config);

    assertThat(schedule.cronExpression()).isEqualTo("0 5 9 ? * *");
    assertThat(schedule.enabled()).isTrue();
    assertThat(schedule.retryMaxAttempts()).isEqualTo(3);
    assertThat(schedule.retryBackoffSeconds()).isEqualTo(30);
    assertThat(schedule.nextFireTime()).isNull();
  }

  @Test
  void shouldNormalizeDayOfWeekCronForQuartz() {
    ObjectNode config = objectMapper.createObjectNode();
    config.put("enabled", true);
    config.put("cronExpression", "0 0 12 * * MON");

    assertThat(support.prepare(42L, config).cronExpression())
        .isEqualTo("0 0 12 ? * MON");
  }
}
