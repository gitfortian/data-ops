package io.yak.ops.business.sync.offline.engine;

import io.yak.ops.business.sync.offline.engine.connector.OfflineSyncConnectorRegistry;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 数据源类型与 Link-Up Connector 标识转换工具。
 *
 * <p>默认产品映射由 {@link OfflineSyncConnectorRegistry} 统一维护。这里仅保留历史输入
 * 优先级与 Connector ID 规范化，避免前后续执行链路继续复制 datasource 类型集合。</p>
 *
 * @author weifuwan
 */
public final class ConnectorIdResolver {

  private ConnectorIdResolver() {
  }

  public static String resolve(String connectorId, String connectorType, String dbType,
      String fallback) {
    if (StringUtils.hasText(connectorId)) {
      String explicit = connectorId.trim();
      // Historical definitions sometimes stored product labels such as "Doris" in connectorId.
      // Preserve those mixed/upper-case aliases, while a canonical lower-case connectorId such as
      // "doris" is treated as an explicit engine identifier and must not be rewritten to JDBC.
      if (hasUpperCase(explicit)) {
        String legacy = OfflineSyncConnectorRegistry.defaultConnectorId(explicit).orElse(null);
        if (StringUtils.hasText(legacy)) {
          return legacy;
        }
      }
      return normalizeConnectorId(explicit);
    }

    String value = firstText(connectorType, dbType, fallback);
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("Connector 类型不能为空");
    }

    String registered = OfflineSyncConnectorRegistry.defaultConnectorId(value).orElse(null);
    return StringUtils.hasText(registered) ? registered : normalizeConnectorId(value);
  }

  public static boolean isJdbc(String connectorId) {
    return "jdbc".equalsIgnoreCase(connectorId);
  }

  private static String normalizeConnectorId(String value) {
    return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static boolean hasUpperCase(String value) {
    return value.chars().anyMatch(Character::isUpperCase);
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }
}
