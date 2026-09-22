package io.yak.ops.business.dataset.gateway.datasource;

import java.util.List;

/** Dataset-owned boundary for schema discovery and editor preview SQL. */
public interface DatasetSchemaSqlGateway {

  QueryResult execute(String dataSourceId, String sql, int maxRows, int timeoutSeconds);

  record QueryColumn(
      String name,
      String label,
      String typeName,
      int jdbcType,
      boolean nullable) {}

  record QueryResult(
      boolean resultSet,
      List<QueryColumn> columns,
      List<List<Object>> rows,
      boolean truncated) {
    public QueryResult {
      columns = columns == null ? List.of() : List.copyOf(columns);
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }
}
