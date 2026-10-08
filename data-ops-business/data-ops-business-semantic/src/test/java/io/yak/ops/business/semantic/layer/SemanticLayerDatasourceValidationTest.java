package io.yak.ops.business.semantic.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.domain.DataSourceReference;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.semantic.api.LayerStdBindingReader;
import io.yak.ops.business.semantic.api.LayerUsageReader;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SemanticLayerDatasourceValidationTest {
  private SemanticLayerRepository repository;
  private DataSourceReader datasource;
  private ObjectProvider<DataSourceReader> provider;
  private SemanticLayerService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    repository = mock(SemanticLayerRepository.class);
    datasource = mock(DataSourceReader.class);
    provider = mock(ObjectProvider.class);
    service = new SemanticLayerService(repository,
        mock(SemanticLayerTemplateRepository.class), mock(SemanticStandardRepository.class),
        mock(ObjectProvider.class), mock(ObjectProvider.class), mock(BusinessAuditService.class),
        provider);
  }

  @Test
  void createRejectsMissingDatasourceProviderBeforeInsert() {
    when(provider.getIfAvailable()).thenReturn(null);
    SemanticException e = assertThrows(SemanticException.class, this::create);
    assertThat(e.getErrorCode()).isEqualTo(SemanticErrorCode.LAYER_CONFIG_INVALID);
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsDatasourceNotFoundInCurrentProject() {
    when(provider.getIfAvailable()).thenReturn(datasource);
    when(datasource.requireReference(71L)).thenThrow(new IllegalStateException("not found in project"));
    assertThrows(IllegalStateException.class, this::create);
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsUnexpectedDatasourceIdentity() {
    when(provider.getIfAvailable()).thenReturn(datasource);
    when(datasource.requireReference(71L)).thenReturn(
        new DataSourceReference(72L, 4L, "other", null));
    SemanticException e = assertThrows(SemanticException.class, this::create);
    assertThat(e.getErrorCode()).isEqualTo(SemanticErrorCode.LAYER_CONFIG_INVALID);
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void updateRejectsInvalidDatasourceBeforeMutation() {
    when(repository.findById(12L)).thenReturn(Optional.of(layer()));
    when(provider.getIfAvailable()).thenReturn(datasource);
    when(datasource.requireReference(71L)).thenThrow(new IllegalStateException("unavailable"));
    assertThrows(IllegalStateException.class, () -> service.update(
        12L, "DWS", "dws_db", 71L, null, null, "Parquet", null, null, 1, false));
    verify(repository, never()).update(any());
  }

  @Test
  void existingProjectDatasourcePassesReferenceValidation() {
    when(provider.getIfAvailable()).thenReturn(datasource);
    when(datasource.requireReference(71L)).thenReturn(new DataSourceReference(71L, 4L, "valid", null));
    when(repository.existsByCode("DWS")).thenReturn(true);
    assertThrows(RuntimeException.class, this::create);
    verify(datasource).requireReference(71L);
    verify(repository, never()).insert(any(), any());
  }

  private WarehouseLayer create() {
    return service.create("DWS", "DWS", "dws_db", 71L, null, null, "Parquet",
        null, null, 1, false, "tester");
  }

  private WarehouseLayer layer() {
    return new WarehouseLayer(12L, "DWS", "DWS", "dws_db", 71L, null,
        null, "Parquet", null, null, 1, "ENABLED", false, false, "tester", null, null);
  }
}
