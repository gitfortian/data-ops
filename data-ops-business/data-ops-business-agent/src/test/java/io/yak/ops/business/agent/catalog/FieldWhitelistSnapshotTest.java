package io.yak.ops.business.agent.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.ops.business.agent.gateway.DatasetCatalogGateway;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldWhitelistSnapshotTest {
  private final DatasetCatalogGateway catalog = mock(DatasetCatalogGateway.class);
  private final FieldWhitelistValidator validator = new FieldWhitelistValidator(catalog);
  private DatasetSummary.DatasetFields fields(int version) {
    return new DatasetSummary.DatasetFields(7, "sales", List.of(new DatasetSummary.FieldView(
        "region", "区域", "STRING", "DIMENSION", true, null)), version);
  }

  @Test void versionChangeCannotUsePreviouslyDiscoveredFields() {
    when(catalog.datasetOverview(7L)).thenReturn(fields(4));
    var error = assertThrows(IllegalArgumentException.class, () -> validator.requireSnapshot(7, fields(3), List.of("region")));
    assertTrue(error.getMessage().contains("DATASET_VERSION_CHANGED"));
  }

  @Test void missingDiscoveryAndUnknownFieldsFailBeforeExecution() {
    assertThrows(IllegalArgumentException.class, () -> validator.requireSnapshot(7, null, List.of()));
    verifyNoInteractions(catalog);
    when(catalog.datasetOverview(7L)).thenReturn(fields(3));
    assertThrows(IllegalArgumentException.class, () -> validator.requireSnapshot(7, fields(3), List.of("secret")));
    assertDoesNotThrow(() -> validator.requireSnapshot(7, fields(3), List.of("region")));
  }
}
