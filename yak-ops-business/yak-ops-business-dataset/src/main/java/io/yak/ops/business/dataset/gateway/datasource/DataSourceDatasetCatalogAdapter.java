package io.yak.ops.business.dataset.gateway.datasource;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapts the optional typed Datasource catalog boundary to Dataset lineage evidence. */
@Component
public class DataSourceDatasetCatalogAdapter implements DatasetCatalogGateway {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataSourceDatasetCatalogAdapter.class);

  private final ObjectProvider<DataSourceCatalogReader> catalogReaderProvider;

  public DataSourceDatasetCatalogAdapter(
      ObjectProvider<DataSourceCatalogReader> catalogReaderProvider) {
    this.catalogReaderProvider = catalogReaderProvider;
  }

  @Override
  public List<CatalogColumn> listColumns(
      long dataSourceId, String databaseName, String schemaName, String tableName) {
    DataSourceCatalogReader catalogReader = catalogReaderProvider.getIfAvailable();
    if (catalogReader == null) {
      return List.of();
    }
    try {
      return catalogReader.listColumns(dataSourceId, databaseName, schemaName, tableName).stream()
          .map(column -> new CatalogColumn(column.name(), column.ordinalPosition()))
          .toList();
    } catch (RuntimeException exception) {
      LOGGER.debug(
          "Dataset lineage catalog lookup failed for datasource {} table {}.{}.{}: {}",
          dataSourceId,
          databaseName,
          schemaName,
          tableName,
          exception.getMessage());
      return List.of();
    }
  }
}
