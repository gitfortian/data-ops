package io.yak.ops.business.quality.gateway.datasource;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import java.util.List;
import org.springframework.stereotype.Component;

/** Adapts the typed Datasource catalog boundary to Quality-owned values. */
@Component
@ConditionalOnQualityEnabled
public class DataSourceQualityCatalogAdapter implements QualityDataCatalogGateway {
  private final DataSourceCatalogReader catalogReader;

  public DataSourceQualityCatalogAdapter(DataSourceCatalogReader catalogReader) {
    this.catalogReader = catalogReader;
  }

  @Override
  public List<QualityPhysicalTable> listTables(
      long dataSourceId, String databaseName, String schemaName, String keyword) {
    return catalogReader.listTables(dataSourceId, databaseName, schemaName, keyword).stream()
        .map(table -> new QualityPhysicalTable(
            table.database(), table.schema(), table.name(), table.type(), table.remarks()))
        .toList();
  }

  @Override
  public String buildSqlTemplate(long dataSourceId, String tablePath) {
    return catalogReader.buildSqlTemplate(dataSourceId, tablePath);
  }

  @Override
  public QualityQueryResult preview(long dataSourceId, String sql) {
    var result = catalogReader.preview(
        dataSourceId,
        new CatalogReadRequest(ReadMode.SQL, null, sql, List.of()));
    return new QualityQueryResult(result.rows());
  }
}
