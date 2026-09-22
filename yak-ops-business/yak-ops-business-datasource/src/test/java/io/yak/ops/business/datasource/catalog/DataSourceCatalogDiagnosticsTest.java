package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import org.junit.jupiter.api.Test;

class DataSourceCatalogDiagnosticsTest {

  @Test
  void recordsSuccessFailureAndCacheLookupsWithoutSensitivePayloads() {
    DataSourceProperties properties = new DataSourceProperties();
    properties.getCatalog().setSlowOperationThresholdMillis(60_000L);
    DataSourceCatalogDiagnostics diagnostics = new DataSourceCatalogDiagnostics(properties);
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getId()).thenReturn(7L);
    when(definition.getDbType()).thenReturn(DataSourceDbType.MYSQL);

    assertThat(diagnostics.observe(definition, "preview", () -> "ok")).isEqualTo("ok");
    assertThatThrownBy(
            () ->
                diagnostics.observe(
                    definition,
                    "preview",
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);
    diagnostics.recordCacheLookup(true);
    diagnostics.recordCacheLookup(false);

    DataSourceCatalogDiagnostics.Snapshot snapshot = diagnostics.snapshot();
    assertThat(snapshot.cacheHits()).isEqualTo(1L);
    assertThat(snapshot.cacheMisses()).isEqualTo(1L);
    assertThat(snapshot.cacheHitRate()).isEqualTo(0.5D);
    assertThat(snapshot.operations())
        .singleElement()
        .satisfies(operation -> {
          assertThat(operation.operation()).isEqualTo("preview");
          assertThat(operation.total()).isEqualTo(2L);
          assertThat(operation.failures()).isEqualTo(1L);
          assertThat(operation.slow()).isZero();
        });
  }
}
