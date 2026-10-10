package io.yak.ops.business.metadata.api;

import java.util.List;

/**
 * Bounded, read-only catalog evidence for an explicitly chosen physical source selection.
 * Project identity comes exclusively from trusted CurrentProject in the Metadata implementation.
 * This is NOT a datasource ACL grant, SQL access, or an assertion that a whole database was captured.
 */
public interface PhysicalScopeEvidenceQueryApi {

  Evidence readSelectedTables(List<String> assetKeys);

  record Column(String name, String contentHash, String dataType, boolean primaryKey,
      String comment) {}

  record Table(String assetKey, String name, String contentHash, int declaredColumnCount,
      List<Column> columns) {
    public Table {
      columns = List.copyOf(columns);
    }
  }

  record Evidence(long projectId, String dataSourceId, String database, String schema,
      String collectJobId, String lastCollectAt, String fingerprint,
      List<Table> tables) {
    public Evidence {
      tables = List.copyOf(tables);
    }
  }
}
