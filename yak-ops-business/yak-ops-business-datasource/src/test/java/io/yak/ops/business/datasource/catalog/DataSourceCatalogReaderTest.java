package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataSourceCatalogReaderTest {

  @Test
  void policyRejectsUnsafeReadBeforeDatasourceLookupOrGatewayCall() {
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    DataSourceCatalogGateway gateway = mock(DataSourceCatalogGateway.class);
    DataSourceProperties properties = mock(DataSourceProperties.class);
    CatalogReadPolicy policy = mock(CatalogReadPolicy.class);
    CatalogTableMatcher matcher = mock(CatalogTableMatcher.class);
    DataSourceCatalogReader reader =
        new DataSourceCatalogReader(
            dataSourceReader,
            gateway,
            properties,
            policy,
            matcher,
            new DataSourceCatalogMetadataCache(),
            mock(DataSourceCatalogDiagnostics.class));
    CatalogReadRequest request =
        new CatalogReadRequest(ReadMode.SQL, null, "DELETE FROM patient", List.of());
    doThrow(
            new DataSourceException(
                DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                "数据预览仅允许执行单条 SELECT 查询"))
        .when(policy)
        .validateReadOnly(request);

    assertThatThrownBy(() -> reader.preview(1L, request))
        .isInstanceOf(DataSourceException.class);

    verifyNoInteractions(dataSourceReader, gateway, properties, matcher);
  }

  @Test
  void repeatedTableSearchUsesVersionedMetadataCacheAndKeepsLimit() {
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    DataSourceCatalogGateway gateway = mock(DataSourceCatalogGateway.class);
    DataSourceProperties properties = new DataSourceProperties();
    properties.getCatalog().setConnectionTimeoutSeconds(7);
    properties.getCatalog().setMetadataCacheTtlSeconds(60);
    CatalogReadPolicy policy = mock(CatalogReadPolicy.class);
    CatalogTableMatcher matcher = mock(CatalogTableMatcher.class);
    DataSourceCatalogMetadataCache cache = new DataSourceCatalogMetadataCache();
    DataSourceCatalogDiagnostics diagnostics = new DataSourceCatalogDiagnostics(properties);
    DataSourceCatalogReader reader =
        new DataSourceCatalogReader(
            dataSourceReader,
            gateway,
            properties,
            policy,
            matcher,
            cache,
            diagnostics);

    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getId()).thenReturn(1L);
    when(definition.getUpdateTime()).thenReturn(LocalDateTime.of(2026, 8, 27, 10, 0));
    when(dataSourceReader.require(1L)).thenReturn(definition);
    when(gateway.listTables(eq(definition), any(CatalogTableQuery.class), eq(7)))
        .thenReturn(List.of());

    reader.searchTables(1L, null, null, "patient", 50);
    reader.searchTables(1L, null, null, "patient", 50);

    ArgumentCaptor<CatalogTableQuery> queryCaptor =
        ArgumentCaptor.forClass(CatalogTableQuery.class);
    verify(gateway).listTables(eq(definition), queryCaptor.capture(), eq(7));
    assertThat(queryCaptor.getValue().keyword()).isEqualTo("patient");
    assertThat(queryCaptor.getValue().limit()).isEqualTo(50);
    assertThat(cache.size()).isEqualTo(1);
    assertThat(reader.diagnostics().cacheMisses()).isEqualTo(1L);
    assertThat(reader.diagnostics().cacheHits()).isEqualTo(1L);
    assertThat(reader.diagnostics().operations())
        .singleElement()
        .satisfies(operation -> {
          assertThat(operation.operation()).isEqualTo("searchTables");
          assertThat(operation.total()).isEqualTo(1L);
        });
    verify(dataSourceReader, times(2)).require(1L);
  }
}
