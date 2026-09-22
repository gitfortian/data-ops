package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Owns normalization and identity rules shared by quality table asset commands and reads. */
@Component
@ConditionalOnQualityEnabled
public class QualityTableTargetPolicy {

  public void requireDataSourceId(long dataSourceId) {
    if (dataSourceId <= 0L) {
      throw new IllegalArgumentException("数据源编号无效");
    }
  }

  public String targetKey(String databaseName, String schemaName, String tableName) {
    return String.join(
        "\u0001",
        normalizeKeyPart(databaseName),
        normalizeKeyPart(schemaName),
        normalizeKeyPart(tableName));
  }

  public String firstNonBlank(String first, String second) {
    String normalized = trimToNull(first);
    return normalized == null ? trimToNull(second) : normalized;
  }

  public String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalizeKeyPart(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
  }
}
