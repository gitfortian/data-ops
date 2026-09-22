package io.yak.ops.business.dataservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.dataservice.domain.access.AuthMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataServiceDefinitionTest {

  @Test
  void republishReplacesPinnedSourceAndRuntimeButPreservesRuntimeAndAccessPolicy() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    RuntimePolicy policy = new RuntimePolicy(true, 120, 300, true, 7, 45);
    DataServiceDefinition definition = DataServiceDefinition.restore(
        9L,
        new DataServiceSettings("orders", "/orders", 500, 20, true, "old", false),
        new PublishedRuntimeSnapshot(1L, "select 1"),
        new SourceReference("DATA_DEVELOPMENT_DATA_SERVICE", "100", 10L, 1),
        policy,
        AuthMode.API_KEY,
        now,
        now);

    definition.republish(
        new DataServiceSettings("orders-v2", "/orders", 600, 30, true, "new", true),
        new PublishedRuntimeSnapshot(2L, "select 2"),
        new SourceReference("DATA_DEVELOPMENT_DATA_SERVICE", "100", 11L, 2),
        now.plusMinutes(1));

    assertThat(definition.id()).isEqualTo(9L);
    assertThat(definition.sourceReference().sourceRevisionId()).isEqualTo(11L);
    assertThat(definition.runtimeSnapshot().dataSourceId()).isEqualTo(2L);
    assertThat(definition.runtimePolicy()).isEqualTo(policy);
    assertThat(definition.authMode()).isEqualTo(AuthMode.API_KEY);
  }

  @Test
  void changingEnablementDoesNotRewritePublishedRuntimeSnapshot() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    DataServiceDefinition definition = DataServiceDefinition.create(
        new DataServiceSettings("orders", "/orders", 500, 20, true, null, false),
        new PublishedRuntimeSnapshot(42L, "select * from orders"),
        new SourceReference("SOURCE", "100", 10L, 1),
        RuntimePolicy.defaults(true),
        now);

    definition.setEnabled(false, now.plusMinutes(1));

    assertThat(definition.settings().enabled()).isFalse();
    assertThat(definition.runtimeSnapshot().dataSourceId()).isEqualTo(42L);
    assertThat(definition.runtimeSnapshot().sql()).isEqualTo("select * from orders");
  }
}
