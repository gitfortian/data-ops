package io.yak.ops.business.sync.offline.schedule;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.PROJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OfflineScheduleEngineBridgeTest {

  @Test
  void shouldMapOnlineOfflineJobToYakScheduleDefinition() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    OfflineScheduleEngineBridge bridge = new OfflineScheduleEngineBridge(provider);

    ScheduleDefinition definition = bridge.toDefinition(
        definition("ONLINE"),
        schedule(true));

    assertThat(definition.key().namespace()).isEqualTo("yak-ops-offline-sync");
    assertThat(definition.key().name()).isEqualTo("42");
    assertThat(definition.trigger().expression()).isEqualTo("0 5 9 ? * *");
    assertThat(definition.trigger().zoneId()).isEqualTo(ZoneId.systemDefault());
    assertThat(definition.target().handler()).isEqualTo("offlineSyncScheduleHandler");
    assertThat(definition.target().payload())
        .containsEntry("definitionId", 42L)
        .containsEntry("projectId", PROJECT_ID);
    assertThat(definition.metadata()).containsEntry("projectId", String.valueOf(PROJECT_ID));
    assertThat(definition.policy().concurrencyPolicy()).isEqualTo(ConcurrencyPolicy.FORBID);
    assertThat(definition.policy().misfirePolicy()).isEqualTo(MisfirePolicy.FIRE_ONCE_NOW);
    assertThat(definition.policy().triggerRetries()).isZero();
    assertThat(definition.enabled()).isTrue();
  }

  @Test
  void shouldKeepOfflineDefinitionDisabledInScheduleDefinition() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    OfflineScheduleEngineBridge bridge = new OfflineScheduleEngineBridge(provider);

    ScheduleDefinition definition = bridge.toDefinition(
        definition("OFFLINE"),
        schedule(true));

    assertThat(definition.enabled()).isFalse();
  }

  private OfflineJobDefinition definition(String state) {
    return OfflineJobDefinition.builder()
        .id(42L)
        .projectId(PROJECT_ID)
        .jobName("订单离线同步")
        .mode("FULL")
        .sourceType("MYSQL")
        .sinkType("MYSQL")
        .releaseState(state)
        .build();
  }

  private OfflineSchedule schedule(boolean enabled) {
    return new OfflineSchedule(
        42L,
        "0 5 9 ? * *",
        enabled,
        2,
        60,
        null,
        null,
        "{}");
  }
}
