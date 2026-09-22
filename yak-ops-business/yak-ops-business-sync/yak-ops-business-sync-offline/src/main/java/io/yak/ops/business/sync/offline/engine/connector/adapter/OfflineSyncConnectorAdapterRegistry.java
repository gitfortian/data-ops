package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Selects exactly one connector adapter, falling back to transparent option passthrough. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineSyncConnectorAdapterRegistry {

  private final List<OfflineSyncConnectorAdapter> adapters;
  private final OfflineSyncConnectorAdapter fallback;

  @Autowired
  public OfflineSyncConnectorAdapterRegistry(List<OfflineSyncConnectorAdapter> adapters) {
    this(adapters, new PassthroughOfflineSyncConnectorAdapter());
  }

  OfflineSyncConnectorAdapterRegistry(
      List<OfflineSyncConnectorAdapter> adapters,
      OfflineSyncConnectorAdapter fallback) {
    this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    this.fallback = Objects.requireNonNull(fallback, "fallback");
  }

  /** Convenience registry used by focused unit tests and legacy direct constructors. */
  public static OfflineSyncConnectorAdapterRegistry standard(ObjectMapper objectMapper) {
    return new OfflineSyncConnectorAdapterRegistry(
        List.of(new JdbcOfflineSyncConnectorAdapter(objectMapper)));
  }

  public OfflineSyncConnectorAdapter resolve(String connectorId, Role role) {
    if (!StringUtils.hasText(connectorId)) {
      throw new IllegalArgumentException("connectorId 不能为空");
    }
    Objects.requireNonNull(role, "role");

    List<OfflineSyncConnectorAdapter> matches = new ArrayList<>();
    for (OfflineSyncConnectorAdapter adapter : adapters) {
      if (adapter.supports(connectorId, role)) {
        matches.add(adapter);
      }
    }

    if (matches.size() > 1) {
      throw new IllegalStateException(
          "存在多个离线同步 Connector Adapter："
              + role
              + "/"
              + connectorId
              + " -> "
              + matches.stream().map(value -> value.getClass().getName()).toList());
    }
    return matches.isEmpty() ? fallback : matches.get(0);
  }
}
