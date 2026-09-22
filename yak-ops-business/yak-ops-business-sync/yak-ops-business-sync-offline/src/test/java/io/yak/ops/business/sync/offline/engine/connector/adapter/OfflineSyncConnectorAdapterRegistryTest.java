package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildResult;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineSyncConnectorAdapterRegistryTest {

  @Test
  void standardRegistrySelectsJdbcAndFallsBackForUnknownConnector() {
    OfflineSyncConnectorAdapterRegistry registry =
        OfflineSyncConnectorAdapterRegistry.standard(new ObjectMapper());

    assertThat(registry.resolve("jdbc", Role.SOURCE))
        .isInstanceOf(JdbcOfflineSyncConnectorAdapter.class);
    assertThat(registry.resolve("jdbc", Role.SINK))
        .isInstanceOf(JdbcOfflineSyncConnectorAdapter.class);
    assertThat(registry.resolve("future-native", Role.SOURCE).getClass().getSimpleName())
        .isEqualTo("PassthroughOfflineSyncConnectorAdapter");
  }

  @Test
  void duplicateAdapterMatchesFailFast() {
    OfflineSyncConnectorAdapter first = adapter("demo");
    OfflineSyncConnectorAdapter second = adapter("demo");
    OfflineSyncConnectorAdapterRegistry registry =
        new OfflineSyncConnectorAdapterRegistry(List.of(first, second));

    assertThatThrownBy(() -> registry.resolve("demo", Role.SOURCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("多个离线同步 Connector Adapter");
  }

  @Test
  void datasourceIsAvailableOnlyAtExecutionBoundary() {
    assertThat(
            Arrays.stream(BuildContext.class.getRecordComponents())
                .map(component -> component.getName()))
        .doesNotContain("dataSource");
    assertThat(
            Arrays.stream(ExecutionContext.class.getRecordComponents())
                .map(component -> component.getName()))
        .contains("dataSource");
  }

  private OfflineSyncConnectorAdapter adapter(String id) {
    return new OfflineSyncConnectorAdapter() {
      @Override
      public boolean supports(String connectorId, Role role) {
        return id.equals(connectorId);
      }

      @Override
      public boolean requiresDataSource(String connectorId, Role role) {
        return false;
      }

      @Override
      public BuildResult build(BuildContext context) {
        return new BuildResult(context.options(), null, List.of());
      }

      @Override
      public void resolveForExecution(ExecutionContext context) {
      }
    };
  }
}
