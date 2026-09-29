package io.yak.ops.business.metadata.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 目录端口的实现（ticket 114 的"唯一允许跨模块引用数据源内部包的类"）。
 *
 * <p>这里守的三件事都只有适配器能守：
 * ① 采集要的是<b>完整在场集</b>，走整作用域列举而不是带 200 条上限的交互式搜索——用错那条会让
 * 大目录"看起来只剩 200 张表"，并被下一轮的 GONE 判成删除了其余全部；
 * ② 数据源模块关掉时本类要<b>安静地不可用</b>，而不是在启动期抛 {@code NoSuchBeanDefinition}；
 * ③ 两边类型换算必须一个字段都不丢，丢了的那一半会表现为"结构指纹永远不变"这种查不下去的故障。
 */
class DataSourceCatalogHarvestSourceTest {

  private final DataSourceCatalogReader catalogReader = mock(DataSourceCatalogReader.class);
  private final DataSourceReader definitionReader = mock(DataSourceReader.class);

  @Test
  void availabilityNeedsBothReadersAndFailsQuietlyWithoutThem() {
    assertThat(source(with(catalogReader), with(definitionReader)).available()).isTrue();
    assertThat(source(empty(), with(definitionReader)).available()).isFalse();
    assertThat(source(with(catalogReader), empty()).available()).isFalse();
  }

  @Test
  void anAbsentReaderIsReportedAsAnUnavailableProviderNotAsAStackOverflowOfTypes() {
    DataSourceCatalogHarvestSource source = source(empty(), empty());

    assertThatThrownBy(() -> source.listDatabases(7L))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.PROVIDER_UNAVAILABLE);
    assertThatThrownBy(() -> source.databaseServiceName(7L))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.PROVIDER_UNAVAILABLE);
  }

  @Test
  void tablesAreListedForTheWholeScopeNeverTheInteractiveSearch() {
    DataSourceCatalogHarvestSource source = source(with(catalogReader), with(definitionReader));
    when(catalogReader.listTables(7L, "shop", "report", null))
        .thenReturn(List.of(new CatalogTable("shop", "report", "orders", "TABLE", "订单表")));

    List<DataSourceTable> tables = source.listTables(7L, "shop", "report");

    assertThat(tables).singleElement().extracting(DataSourceTable::getName).isEqualTo("orders");
    // searchTables 带 limit（默认 200），是交互式选择器的口径；采集按它列会在场集上撒谎。
    verify(catalogReader, never()).searchTables(anyLong(), any(), any(), any(), any());
  }

  @Test
  void tableAndColumnFieldsCrossTheBoundaryIntact() {
    DataSourceCatalogHarvestSource source = source(with(catalogReader), with(definitionReader));
    when(catalogReader.listTables(7L, "shop", null, null))
        .thenReturn(List.of(new CatalogTable("shop", " ", "Orders", "VIEW", "视图")));
    when(catalogReader.listColumns(7L, "shop", null, "Orders"))
        .thenReturn(List.of(new CatalogColumn("Amount", "decimal", 93, 18, 2, true, 3, false, "金额")));

    DataSourceTable table = source.listTables(7L, "shop", null).get(0);
    assertThat(table.getDatabase()).isEqualTo("shop");
    // 空白 schema 在对方记录构造时归一成 null，端口不替引擎发明一个空串。
    assertThat(table.getSchema()).isNull();
    assertThat(table.getName()).isEqualTo("Orders");
    assertThat(table.getType()).isEqualTo("VIEW");
    assertThat(table.getRemarks()).isEqualTo("视图");

    DataSourceColumn column = source.listColumns(7L, "shop", null, "Orders").get(0);
    assertThat(column.getName()).isEqualTo("Amount");
    assertThat(column.getTypeName()).isEqualTo("decimal");
    assertThat(column.getJdbcType()).isEqualTo(93);
    assertThat(column.getSize()).isEqualTo(18);
    assertThat(column.getScale()).isEqualTo(2);
    assertThat(column.isNullable()).isTrue();
    assertThat(column.getOrdinalPosition()).isEqualTo(3);
    assertThat(column.isPrimaryKey()).isFalse();
    assertThat(column.getRemarks()).isEqualTo("金额");
  }

  @Test
  void serviceIdentityComesFromTheDefinitionAndNothingElse() {
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getName()).thenReturn(" HIS 生产库 ");
    when(definition.getDbType()).thenReturn(DataSourceDbType.MYSQL);
    when(definitionReader.require(7L)).thenReturn(definition);
    DataSourceCatalogHarvestSource source = source(with(catalogReader), with(definitionReader));

    assertThat(source.databaseServiceName(7L)).isEqualTo(" HIS 生产库 ");
    assertThat(source.databaseType(7L)).isEqualTo("MYSQL");

    when(definition.getDbType()).thenReturn(null);
    // 方言拿不到时交 null 而不是猜一个：统计层按方言路由，猜错会把未验证的引擎标成已支持。
    assertThat(source.databaseType(7L)).isNull();
  }

  @Test
  void aNullReplyStaysHonestAboutWhatWasAndWasNotKnown() {
    DataSourceCatalogHarvestSource source = source(with(catalogReader), with(definitionReader));
    when(catalogReader.listDatabases(7L)).thenReturn(null);
    when(catalogReader.listSchemas(7L, "shop")).thenReturn(null);
    when(catalogReader.listColumns(7L, "shop", null, "orders")).thenReturn(null);

    // 这三处空集是诚实的：没有库/没有模式/读不到列（后者由采集方判成 PARTIAL）。
    assertThat(source.listDatabases(7L)).isEmpty();
    assertThat(source.listSchemas(7L, "shop")).isEmpty();
    assertThat(source.listColumns(7L, "shop", null, "orders")).isEmpty();

    // 表清单却不行：空集在这里等于"这个作用域确实一张表都没有"，下一轮会据此把全库判成删除。
    when(catalogReader.listTables(7L, "shop", null, null)).thenReturn(null);
    assertThatThrownBy(() -> source.listTables(7L, "shop", null))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.DATASOURCE_UNAVAILABLE);
  }

  private static DataSourceCatalogHarvestSource source(
      ObjectProvider<DataSourceCatalogReader> catalog, ObjectProvider<DataSourceReader> definitions) {
    return new DataSourceCatalogHarvestSource(catalog, definitions);
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> with(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> empty() {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }
}
