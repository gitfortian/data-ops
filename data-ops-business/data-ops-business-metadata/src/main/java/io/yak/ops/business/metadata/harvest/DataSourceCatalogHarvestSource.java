package io.yak.ops.business.metadata.harvest;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link HarvestCatalogSource} 的实现：把数据源模块的目录读取适配成本模块的端口，返回类型一律换成
 * SPI 的 {@link DataSourceTable}/{@link DataSourceColumn}。
 *
 * <p>为什么不是直接调 SPI 的 {@code DataSourceCatalog}：那要自己拿数据源配置与凭据
 * （{@code DataSourceDefinition.connectionParams}），等于把连接口令搬进元数据模块。
 * 凭据的解析归数据源模块，本类只交出一个 id。
 *
 * <p>两个 reader 都是 {@code @ConditionalOnDataSourceEnabled}，平台关掉数据源能力时它们不在容器里，
 * 所以用 {@link ObjectProvider} 取：缺位时 {@link #available()} 为 false，采集记 FAILED 而不是
 * 启动期 NoSuchBeanDefinition 把整个应用带走。
 *
 * <p>{@link DataSourceCatalogReader#listTables} 走的是"整作用域列举"那条既有入口，不是
 * {@code searchTables}——后者带 200 条上限，是给交互式选择器用的，采集要的是完整在场集。
 * 一次调用一个 (database, schema)，分批口径见端口注释。
 */
@Component
public class DataSourceCatalogHarvestSource implements HarvestCatalogSource {

  private final ObjectProvider<DataSourceCatalogReader> catalogReaders;
  private final ObjectProvider<DataSourceReader> definitionReaders;

  public DataSourceCatalogHarvestSource(
      ObjectProvider<DataSourceCatalogReader> catalogReaders,
      ObjectProvider<DataSourceReader> definitionReaders) {
    this.catalogReaders = catalogReaders;
    this.definitionReaders = definitionReaders;
  }

  @Override
  public boolean available() {
    return catalogReaders.getIfAvailable() != null && definitionReaders.getIfAvailable() != null;
  }

  @Override
  public String databaseServiceName(long dataSourceId) {
    return definition(dataSourceId).getName();
  }

  @Override
  public String databaseType(long dataSourceId) {
    DataSourceDefinition definition = definition(dataSourceId);
    return definition.getDbType() == null ? null : definition.getDbType().name();
  }

  @Override
  public List<String> listDatabases(long dataSourceId) {
    return list(catalog().listDatabases(dataSourceId));
  }

  @Override
  public List<String> listSchemas(long dataSourceId, String database) {
    return list(catalog().listSchemas(dataSourceId, database));
  }

  @Override
  public List<DataSourceTable> listTables(long dataSourceId, String database, String schema) {
    return required(catalog().listTables(dataSourceId, database, schema, null)).stream()
        .map(DataSourceCatalogHarvestSource::toSpiTable)
        .toList();
  }

  @Override
  public List<DataSourceColumn> listColumns(
      long dataSourceId, String database, String schema, String table) {
    return list(catalog().listColumns(dataSourceId, database, schema, table)).stream()
        .map(DataSourceCatalogHarvestSource::toSpiColumn)
        .toList();
  }

  /**
   * 表清单<b>不能</b>把 null 当成空集：空集是"这个作用域确实没有表"，采集会据此登记零张表，
   * 下一轮 ticket 115 就把该作用域里所有在场表判成删除。读不出来就得留在场性判断之外。
   */
  private static List<CatalogTable> required(List<CatalogTable> tables) {
    if (tables == null) {
      throw new MetadataException(
          MetadataErrorCode.DATASOURCE_UNAVAILABLE, "表清单读取无结果，本轮该作用域不判在场性");
    }
    return tables;
  }

  private static DataSourceTable toSpiTable(CatalogTable table) {
    return new DataSourceTable(
        table.database(), table.schema(), table.name(), table.type(), table.remarks());
  }

  /** 两边的字段表逐一同形（列名/类型/JDBC 码/长度/小数位/可空/列序/主键/注释），这里只做类型换算。 */
  private static DataSourceColumn toSpiColumn(CatalogColumn column) {
    return new DataSourceColumn(
        column.name(),
        column.typeName(),
        column.jdbcType(),
        column.size(),
        column.scale(),
        column.nullable(),
        column.ordinalPosition(),
        column.primaryKey(),
        column.remarks());
  }

  private DataSourceCatalogReader catalog() {
    DataSourceCatalogReader reader = catalogReaders.getIfAvailable();
    if (reader == null) {
      throw new MetadataException(
          MetadataErrorCode.PROVIDER_UNAVAILABLE, "数据源目录读取能力未装配");
    }
    return reader;
  }

  private DataSourceDefinition definition(long dataSourceId) {
    DataSourceReader reader = definitionReaders.getIfAvailable();
    if (reader == null) {
      throw new MetadataException(
          MetadataErrorCode.PROVIDER_UNAVAILABLE, "数据源配置读取能力未装配");
    }
    return reader.require(dataSourceId);
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }
}
