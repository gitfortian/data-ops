package io.yak.ops.business.dataset.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetLineageSnapshotReaderTest {

  @Test
  void readsCurrentPersistedVersionAndSchemaWithoutDefinitionDependency() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetLineageSnapshotReader reader = new DatasetLineageSnapshotReader(repository);
    Dataset dataset =
        new Dataset(
            21L,
            "sales",
            null,
            DatasetStatus.ONLINE,
            31L,
            Instant.EPOCH,
            Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            31L,
            21L,
            3,
            DatasetSourceType.QUERY_REVISION,
            11L,
            71L,
            5,
            "[]",
            Instant.EPOCH);
    DatasetField field =
        new DatasetField(
            "amount",
            31L,
            "amount",
            "amount",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE,
            1);

    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(repository.listVersions(21L)).thenReturn(List.of(version));
    when(repository.listFields(31L)).thenReturn(List.of(field));

    var detail = reader.require(21L);

    assertThat(detail.currentVersion()).isEqualTo(version);
    assertThat(detail.fields()).containsExactly(field);
    assertThat(detail.versions()).containsExactly(version);
  }
}
