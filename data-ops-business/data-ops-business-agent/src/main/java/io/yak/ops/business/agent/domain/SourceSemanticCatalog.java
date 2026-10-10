package io.yak.ops.business.agent.domain;

import java.util.List;

/**
 * Local read-only catalog port. Business Semantic types must remain inside gateway.
 * A truncated scan is an error, not proof that a definition doesn't exist.
 */
public interface SourceSemanticCatalog {
  record Entry(String kind, Long id, int version, String code, String name,
      String status, String role, Long typeId, Long unitId) {}
  record Snapshot(long projectId, List<Entry> entries, boolean complete) {
    public Snapshot { entries = List.copyOf(entries); }
  }
  Snapshot read();
}
