package io.yak.ops.business.semantic.api;

import java.util.List;

/**
 * F-039 bounded, permission-checked Semantic catalog projection.
 * No candidate data is ever written to the Semantic domain by this API.
 */
public interface SemanticCandidateCatalogApi {
  record Entry(String kind, Long id, int version, String code, String name,
      String status, String role, Long typeId, Long unitId) {}
  record Snapshot(long projectId, List<Entry> entries, boolean complete) {
    public Snapshot { entries = List.copyOf(entries); }
  }
  /** Refuses an incomplete/oversize scan; never treats truncation as absence. */
  Snapshot read();
}
