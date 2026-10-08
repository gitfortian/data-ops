package io.yak.ops.business.semantic.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.LayerStdBindingReader;
import io.yak.ops.business.semantic.api.LayerUsageReader;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** A recoverable Modeling model must not outlive its referenced Semantic layer. */
class SemanticLayerDeletionGuardTest {

  private SemanticLayerRepository repository;
  private ObjectProvider<LayerUsageReader> usageProvider;
  private LayerUsageReader usageReader;
  private SemanticLayerService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    repository = mock(SemanticLayerRepository.class);
    usageProvider = mock(ObjectProvider.class);
    usageReader = mock(LayerUsageReader.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(mock(AuditOperationHandle.class));
    when(repository.findById(12L)).thenReturn(Optional.of(customLayer()));
    service = new SemanticLayerService(repository, mock(SemanticLayerTemplateRepository.class),
        mock(SemanticStandardRepository.class), usageProvider, mock(ObjectProvider.class), auditService);
  }

  @Test
  void recycledModelsStillPreventPhysicalLayerDeletion() {
    when(usageProvider.getIfAvailable()).thenReturn(usageReader);
    // Active-only statistics can be zero while the recycle bin still references the layer.
    when(usageReader.countModelsByLayer()).thenReturn(Map.of());
    when(usageReader.countPersistedLayerReferences("DWS_EXAMPLE")).thenReturn(2L);

    SemanticException error = assertThrows(SemanticException.class, () -> service.delete(12L));

    assertThat(error.getErrorCode()).isEqualTo(SemanticErrorCode.LAYER_REFERENCED);
    verify(repository, never()).deleteById(12L);
    verify(usageReader).countPersistedLayerReferences("DWS_EXAMPLE");
    verify(usageReader, never()).countModelsByLayer();
  }

  @Test
  void missingModelingProviderDoesNotMeanZeroReferences() {
    when(usageProvider.getIfAvailable()).thenReturn(null);

    SemanticException error = assertThrows(SemanticException.class, () -> service.delete(12L));

    assertThat(error.getErrorCode()).isEqualTo(SemanticErrorCode.DELETE_FAILED);
    verify(repository, never()).deleteById(12L);
  }

  @Test
  void modelingLookupFailureIsNotTreatedAsNoReferences() {
    when(usageProvider.getIfAvailable()).thenReturn(usageReader);
    when(usageReader.countPersistedLayerReferences("DWS_EXAMPLE"))
        .thenThrow(new IllegalStateException("Modeling DB unavailable"));

    assertThrows(IllegalStateException.class, () -> service.delete(12L));
    verify(repository, never()).deleteById(12L);
  }

  @Test
  void unreferencedNonPresetLayerCanStillBeDeleted() {
    when(usageProvider.getIfAvailable()).thenReturn(usageReader);
    when(usageReader.countPersistedLayerReferences("DWS_EXAMPLE")).thenReturn(0L);
    when(repository.deleteById(12L)).thenReturn(true);

    service.delete(12L);

    verify(repository).deleteById(12L);
  }

  private WarehouseLayer customLayer() {
    return new WarehouseLayer(12L, "DWS_EXAMPLE", "主题层", "dws_db", 1L, null,
        null, "Parquet", null, "自定义", 2, "ENABLED", false, false, "admin", null, null);
  }
}
