package io.yak.ops.business.quality.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate read model for template catalog navigation and summaries. */
public interface QualityTemplateCatalogRepository {
  CatalogSummary global();

  ScopeSummary customScope(Long folderId, boolean folderFilter);

  record CatalogSummary(
      long systemTotal,
      long customTotal,
      Map<String, Long> systemDimensions,
      Map<String, Long> customDimensions) {
    public CatalogSummary {
      systemDimensions = immutable(systemDimensions);
      customDimensions = immutable(customDimensions);
    }
  }

  record ScopeSummary(long total, Map<String, Long> dimensions) {
    public ScopeSummary {
      dimensions = immutable(dimensions);
    }
  }

  private static Map<String, Long> immutable(Map<String, Long> values) {
    return values == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
