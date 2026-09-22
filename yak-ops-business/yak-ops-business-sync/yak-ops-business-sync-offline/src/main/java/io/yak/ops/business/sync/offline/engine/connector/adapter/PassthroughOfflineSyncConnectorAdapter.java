package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildResult;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Compatibility adapter for framework-neutral Connector IDs that Yak Ops does not yet understand.
 *
 * <p>It deliberately does not resolve datasource credentials. Existing inline connector options
 * continue to pass through unchanged until a dedicated adapter is introduced.</p>
 */
final class PassthroughOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  @Override
  public boolean supports(String connectorId, Role role) {
    return false;
  }

  @Override
  public boolean requiresDataSource(String connectorId, Role role) {
    return false;
  }

  @Override
  public BuildResult build(BuildContext context) {
    String tableView = text(context.options().get("table_path"));
    return new BuildResult(context.options(), tableView, List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    // Intentionally empty: unknown connectors retain their inline options only.
  }

  private String text(JsonNode value) {
    if (value == null || value.isNull() || !value.isValueNode()) {
      return null;
    }
    String text = value.asText(null);
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
