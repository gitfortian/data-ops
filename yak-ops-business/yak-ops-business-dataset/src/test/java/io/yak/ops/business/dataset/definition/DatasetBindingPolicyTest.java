package io.yak.ops.business.dataset.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetBindingPolicyTest {

  @Test
  void acceptsOnlyCurrentOnlineSchemaFields() {
    DatasetReader reader = mock(DatasetReader.class);
    DatasetBindingPolicy policy = new DatasetBindingPolicy(reader);
    when(reader.require(21L)).thenReturn(detail(DatasetStatus.ONLINE));

    assertDoesNotThrow(() -> policy.validateAnalysisBinding(21L, List.of("field-amount")));
    assertThrows(
        IllegalArgumentException.class,
        () -> policy.validateAnalysisBinding(21L, List.of("missing-field")));
  }

  @Test
  void rejectsOfflineDataset() {
    DatasetReader reader = mock(DatasetReader.class);
    DatasetBindingPolicy policy = new DatasetBindingPolicy(reader);
    when(reader.require(21L)).thenReturn(detail(DatasetStatus.OFFLINE));

    assertThrows(
        IllegalArgumentException.class,
        () -> policy.validateAnalysisBinding(21L, List.of("field-amount")));
  }

  private DatasetDetail detail(DatasetStatus status) {
    Dataset dataset =
        new Dataset(21L, "sales", null, status, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            31L,
            21L,
            1,
            DatasetSourceType.SQL_QUERY,
            0L,
            0L,
            0,
            "ds-1",
            "select amount from sales",
            "[]",
            Instant.EPOCH);
    DatasetField field =
        new DatasetField(
            "field-amount",
            31L,
            "amount",
            "amount",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE,
            1);
    return new DatasetDetail(dataset, version, List.of(version), List.of(field));
  }
}
