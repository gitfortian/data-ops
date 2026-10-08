package io.yak.ops.business.modeling.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Semantic edit baselines, independent of transport labels and PO timestamps. */
final class MappingContextFingerprint {
  private MappingContextFingerprint() {}
  static String target(long modelId, String dialect, ColumnDefinition column, MappingService.MappingView mapping) {
    return digest(List.of(modelId, dialect, column, mapping));
  }
  static String source(List<CatalogColumn> columns) {
    return digest(columns.stream().sorted(Comparator.comparing(CatalogColumn::name)).toList());
  }
  private static String digest(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(new ObjectMapper().writeValueAsBytes(value)));
    } catch (Exception failure) { throw new IllegalStateException("无法核验映射上下文", failure); }
  }
}
