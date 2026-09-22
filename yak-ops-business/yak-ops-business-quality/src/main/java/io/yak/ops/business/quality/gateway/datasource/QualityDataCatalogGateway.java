package io.yak.ops.business.quality.gateway.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quality-owned port for the small subset of datasource catalog capabilities it needs. */
public interface QualityDataCatalogGateway {
  List<QualityPhysicalTable> listTables(
      long dataSourceId, String databaseName, String schemaName, String keyword);

  String buildSqlTemplate(long dataSourceId, String tablePath);

  QualityQueryResult preview(long dataSourceId, String sql);

  record QualityPhysicalTable(
      String databaseName,
      String schemaName,
      String tableName,
      String tableType,
      String remarks) {}

  record QualityQueryResult(List<Map<String, Object>> rows) {
    public QualityQueryResult {
      if (rows == null || rows.isEmpty()) {
        rows = List.of();
      } else {
        List<Map<String, Object>> copied = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
          copied.add(row == null
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        rows = Collections.unmodifiableList(copied);
      }
    }
  }
}
