package io.yak.ops.common.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class YakScheduleGatewayTest {

  @Test
  void shouldUnifyPresentScheduleOperationsWithinNamespace() {
    ScheduleManager manager = mock(ScheduleManager.class);
    YakScheduleGateway gateway = new YakScheduleGateway(() -> manager, "yak-ops-test");
    ScheduleKey key = gateway.key("42");
    when(manager.get(key)).thenReturn(Optional.of(mock(ScheduleSnapshot.class)));

    gateway.pauseIfPresent("42");
    gateway.resumeIfPresent("42");
    assertThat(gateway.runNowIfPresent("42")).isTrue();
    gateway.deleteIfPresent("42");

    verify(manager).pause(key);
    verify(manager).resume(key);
    verify(manager).runNow(key);
    verify(manager).delete(key);
  }

  @Test
  void shouldBeSafeWhenScheduleManagerIsUnavailable() {
    YakScheduleGateway gateway = new YakScheduleGateway(() -> null, "yak-ops-test");

    assertThat(gateway.available()).isFalse();
    assertThat(gateway.snapshot("42")).isEmpty();
    assertThat(gateway.list()).isEmpty();
    assertThat(gateway.runNowIfPresent("42")).isFalse();
  }

  @Test
  void shouldRejectDefinitionFromAnotherNamespace() {
    ScheduleDefinition definition = mock(ScheduleDefinition.class);
    when(definition.key()).thenReturn(new ScheduleKey("other", "42"));
    YakScheduleGateway gateway = new YakScheduleGateway(() -> mock(ScheduleManager.class), "yak-ops-test");

    assertThatThrownBy(() -> gateway.save(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("namespace");
  }
}
