package io.yak.ops.business.semantic.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.domain.DataSourceReference;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.semantic.api.LayerStdBindingReader;
import io.yak.ops.business.semantic.api.LayerUsageReader;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** A datasource ID is not a valid layer binding until scoped Datasource confirms its identity. */
class SemanticLayerDataSourceValidationTest {

  private SemanticLayerRepository repository;
  private DataSourceReader dataSources;
  private ObjectProvider<DataSourceReader> provider;
  private SemanticLayerService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    repository = mock(SemanticLayerRepository.class);
    dataSources = mock(DataSourceReader.class);
    provider = mock(ObjectProvider.class);
    BusinessAuditService audit = mock(BusinessAuditService.class);
    when(audit.start(any(AuditOperationRequest.class))).thenReturn(mock(AuditOperationHandle.class));
    service = new SemanticLayerService(repository, mock(SemanticLayerTemplateRepository.class),
        mock(SemanticStandardRepository.class), mock(ObjectProvider.class),
        mock(ObjectProvider.class), audit, provider);
  }

  @Test
  void createRejectsUnknownOrForeignProjectDatasourceBeforeInsert() {
    when(provider.getIfAvailable()).thenReturn(dataSources);
    when(dataSources.requireReference(99L))
        .thenThrow(new DataSourceException(DataSourceErrorCode.NOT_FOUND));

    SemanticException failure = assertThrows(SemanticException.class, () -> create(99L));

    assertEquals(SemanticErrorCode.LAYER_CONFIG_INVALID, failure.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createFailsClosedWhenDatasourceModuleIsUnavailable() {
    when(provider.getIfAvailable()).thenReturn(null);

    SemanticException failure = assertThrows(SemanticException.class, () -> create(3L));

    assertEquals(SemanticErrorCode.LAYER_CONFIG_INVALID, failure.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createAcceptsExistingScopedDatasourceWithoutRequiringConnectionTest() {
    when(provider.getIfAvailable()).thenReturn(dataSources);
    when(dataSources.requireReference(3L))
        .thenReturn(new DataSourceReference(3L, 42L, "warehouse", null));
    when(repository.insert(any(), eq("tester"))).thenAnswer(invocation -> invocation.getArgument(0));

    WarehouseLayer created = create(3L);

    assertEquals(3L, created.datasourceId());
    verify(dataSources).requireReference(3L);
    verify(repository).insert(any(), eq("tester"));
  }

  @Test
  void updateRejectsDeletedOrCrossProjectDatasourceWithoutUpdating() {
    when(repository.findById(12L)).thenReturn(Optional.of(existing()));
    when(provider.getIfAvailable()).thenReturn(dataSources);
    when(dataSources.requireReference(99L))
        .thenThrow(new DataSourceException(DataSourceErrorCode.NOT_FOUND));

    SemanticException failure = assertThrows(SemanticException.class, () -> service.update(
        12L, "主题层", "dws_db", 99L, null, null, "Parquet", null, null, null, null));

    assertEquals(SemanticErrorCode.LAYER_CONFIG_INVALID, failure.getErrorCode());
    verify(repository, never()).update(any());
  }

  private WarehouseLayer create(Long dataSourceId) {
    return service.create("CUSTOM", "自定义层", "dws_db", dataSourceId,
        null, null, "Parquet", null, null, null, null, "tester");
  }

  private WarehouseLayer existing() {
    return new WarehouseLayer(12L, "CUSTOM", "主题层", "dws_db", 3L,
        null, null, "Parquet", null, null, 2, "ENABLED", true, false, "tester", null, null);
  }
}
