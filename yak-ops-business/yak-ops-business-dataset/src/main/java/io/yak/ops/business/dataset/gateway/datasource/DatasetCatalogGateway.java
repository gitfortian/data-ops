package io.yak.ops.business.dataset.gateway.datasource;

import java.util.List;

/** Dataset-owned read-only catalog capability used as optional lineage evidence. */
public interface DatasetCatalogGateway {

  List<CatalogColumn> listColumns(
      long dataSourceId, String databaseName, String schemaName, String tableName);

  record CatalogColumn(String name, Integer ordinalPosition) {}
}
