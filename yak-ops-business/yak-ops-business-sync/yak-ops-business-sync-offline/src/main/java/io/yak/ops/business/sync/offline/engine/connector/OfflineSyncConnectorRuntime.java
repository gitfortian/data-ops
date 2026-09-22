package io.yak.ops.business.sync.offline.engine.connector;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRoleRuntimeVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeProfileVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeSnapshotVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Merges Yak Ops Connector Profiles with schemas discovered from the configured Link-Up Worker.
 *
 * <p>The Worker is still a fixed endpoint in the current offline runtime. This component therefore
 * keeps a short read-through cache rather than introducing a second worker scheduler. If refresh
 * fails after a successful discovery, schema metadata may remain visible as stale evidence, but
 * {@code available} is always false until a live discovery succeeds again.</p>
 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineSyncConnectorRuntime {

  static final long DEFAULT_CACHE_TTL_MILLIS = 30_000L;

  private final LinkUpConnectorDiscoveryClient discoveryClient;
  private final long cacheTtlMillis;
  private final Object refreshLock = new Object();
  private volatile Snapshot cached = Snapshot.empty();

  @Autowired
  public OfflineSyncConnectorRuntime(LinkUpConnectorDiscoveryClient discoveryClient) {
    this(discoveryClient, DEFAULT_CACHE_TTL_MILLIS);
  }

  OfflineSyncConnectorRuntime(
      LinkUpConnectorDiscoveryClient discoveryClient,
      long cacheTtlMillis) {
    this.discoveryClient = discoveryClient;
    this.cacheTtlMillis = Math.max(0L, cacheTtlMillis);
  }

  public OfflineConnectorRuntimeSnapshotVO snapshot() {
    return toView(current());
  }

  /** Fetches a full schema from the Worker directly; the summary cache is intentionally bypassed. */
  public JsonNode schema(String connectorId, String role) {
    return discoveryClient.schema(
        normalizeConnectorId(connectorId),
        normalizeRole(role));
  }

  private Snapshot current() {
    long now = System.currentTimeMillis();
    Snapshot current = cached;
    if (fresh(current, now)) {
      return current;
    }

    synchronized (refreshLock) {
      current = cached;
      now = System.currentTimeMillis();
      if (fresh(current, now)) {
        return current;
      }

      try {
        Map<String, JsonNode> schemas = index(discoveryClient.schemas());
        cached = Snapshot.success(schemas, now);
      } catch (RuntimeException exception) {
        cached = current.failure(now, safeMessage(exception));
      }
      return cached;
    }
  }

  private boolean fresh(Snapshot value, long now) {
    return cacheTtlMillis > 0L
        && value.checkedAtMillis() > 0L
        && now - value.checkedAtMillis() < cacheTtlMillis;
  }

  private Map<String, JsonNode> index(List<JsonNode> discovered) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (JsonNode schema : discovered == null ? List.<JsonNode>of() : discovered) {
      String connectorId = text(schema, "connectorId");
      String role = text(schema, "role");
      if (!StringUtils.hasText(connectorId) || !StringUtils.hasText(role)) {
        continue;
      }
      String normalizedRole = normalizeRole(role);
      String normalizedId = normalizeConnectorId(connectorId);
      String key = key(normalizedId, normalizedRole);
      if (result.put(key, schema.deepCopy()) != null) {
        throw new IllegalStateException(
            "Link-Up 返回重复 Connector Schema：" + normalizedRole + "/" + normalizedId);
      }
    }
    return Map.copyOf(result);
  }

  private OfflineConnectorRuntimeSnapshotVO toView(Snapshot snapshot) {
    List<OfflineConnectorRoleRuntimeVO> connectors = new ArrayList<>();
    snapshot.schemas().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> connectors.add(roleView(entry.getValue(), snapshot.reachable())));

    List<OfflineConnectorRuntimeProfileVO> profiles =
        OfflineSyncConnectorRegistry.profiles().stream()
            .map(profile -> profileView(profile, snapshot))
            .toList();

    return OfflineConnectorRuntimeSnapshotVO.builder()
        .reachable(snapshot.reachable())
        .stale(snapshot.stale())
        .syncedAtMillis(snapshot.syncedAtMillis() > 0L ? snapshot.syncedAtMillis() : null)
        .checkedAtMillis(snapshot.checkedAtMillis() > 0L ? snapshot.checkedAtMillis() : null)
        .errorMessage(snapshot.errorMessage())
        .connectors(List.copyOf(connectors))
        .profiles(profiles)
        .build();
  }

  private OfflineConnectorRuntimeProfileVO profileView(
      OfflineSyncConnectorProfile profile,
      Snapshot snapshot) {
    JsonNode sourceSchema =
        snapshot.schemas().get(key(profile.sourceConnectorId(), "SOURCE"));
    JsonNode sinkSchema =
        snapshot.schemas().get(key(profile.sinkConnectorId(), "SINK"));

    return OfflineConnectorRuntimeProfileVO.builder()
        .profileId(profile.profileId())
        .dbType(profile.dbType().name())
        .displayName(profile.dbType().getDisplayName())
        .pluginName(profile.pluginName())
        .defaultProfile(profile.defaultProfile())
        .source(roleView(profile.sourceConnectorId(), "SOURCE", sourceSchema, snapshot.reachable()))
        .sink(roleView(profile.sinkConnectorId(), "SINK", sinkSchema, snapshot.reachable()))
        .build();
  }

  private OfflineConnectorRoleRuntimeVO roleView(JsonNode schema, boolean live) {
    return roleView(
        normalizeConnectorId(text(schema, "connectorId")),
        normalizeRole(text(schema, "role")),
        schema,
        live);
  }

  private OfflineConnectorRoleRuntimeVO roleView(
      String connectorId,
      String role,
      JsonNode schema,
      boolean live) {
    return OfflineConnectorRoleRuntimeVO.builder()
        .connectorId(connectorId)
        .role(role)
        .available(live && schema != null)
        .schemaVersion(text(schema, "schemaVersion"))
        .schemaFingerprint(text(schema, "schemaFingerprint"))
        .implementationVersion(text(schema, "implementationVersion"))
        .capabilities(capabilities(schema))
        .build();
  }

  private List<String> capabilities(JsonNode schema) {
    if (schema == null || !schema.path("capabilities").isArray()) {
      return List.of();
    }
    Set<String> values = new LinkedHashSet<>();
    schema.path("capabilities").forEach(
        item -> {
          if (item.isValueNode() && StringUtils.hasText(item.asText())) {
            values.add(item.asText().trim().toUpperCase(Locale.ROOT));
          }
        });
    return values.stream().sorted(Comparator.naturalOrder()).toList();
  }

  private String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return null;
    }
    String text = value.asText(null);
    return StringUtils.hasText(text) ? text.trim() : null;
  }

  private String normalizeConnectorId(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("connectorId 不能为空");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeRole(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("role 不能为空");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!"SOURCE".equals(normalized) && !"SINK".equals(normalized)) {
      throw new IllegalArgumentException("role 必须是 SOURCE 或 SINK");
    }
    return normalized;
  }

  private String key(String connectorId, String role) {
    return normalizeConnectorId(connectorId) + "|" + normalizeRole(role);
  }

  private String safeMessage(RuntimeException exception) {
    String value = exception.getMessage();
    if (!StringUtils.hasText(value)) {
      value = exception.getClass().getSimpleName();
    }
    value = value.replaceAll(
        "(?i)(password|passwd|pwd|token|secret)\\s*[=:]\\s*[^,;\\s]+",
        "$1=***");
    return value.length() <= 500 ? value : value.substring(0, 500);
  }

  private record Snapshot(
      Map<String, JsonNode> schemas,
      long syncedAtMillis,
      long checkedAtMillis,
      boolean reachable,
      boolean stale,
      String errorMessage) {

    static Snapshot empty() {
      return new Snapshot(Map.of(), 0L, 0L, false, false, null);
    }

    static Snapshot success(Map<String, JsonNode> schemas, long now) {
      return new Snapshot(schemas, now, now, true, false, null);
    }

    Snapshot failure(long now, String message) {
      boolean hasStaleSchema = syncedAtMillis > 0L || !schemas.isEmpty();
      return new Snapshot(
          schemas,
          syncedAtMillis,
          now,
          false,
          hasStaleSchema,
          message);
    }
  }
}
