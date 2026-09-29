package io.yak.ops.business.dataset.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetReaderTest {

  @Test
  void catalogLoadsCurrentMetadataWithBulkRepositoryReads() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetReader reader = new DatasetReader(repository);
    Dataset versioned = dataset(21L, 31L, DatasetStatus.ONLINE);
    Dataset empty = dataset(22L, null, DatasetStatus.ONLINE);
    Dataset offline = dataset(23L, 32L, DatasetStatus.OFFLINE);
    DatasetVersion version = version(31L, 21L, 2);
    DatasetField field = field("amount", 31L);

    when(repository.listDatasets()).thenReturn(List.of(versioned, empty, offline));
    when(repository.listVersionsByIds(List.of(31L))).thenReturn(List.of(version));
    when(repository.listFieldsByVersionIds(List.of(31L))).thenReturn(List.of(field));

    List<DatasetCatalogEntry> result = reader.catalog(null, true);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).dataset()).isEqualTo(versioned);
    assertThat(result.get(0).currentVersion()).isEqualTo(version);
    assertThat(result.get(0).fields()).containsExactly(field);
    assertThat(result.get(1).dataset()).isEqualTo(empty);
    assertThat(result.get(1).currentVersion()).isNull();
    assertThat(result.get(1).fields()).isEmpty();
    verify(repository).listVersionsByIds(List.of(31L));
    verify(repository).listFieldsByVersionIds(List.of(31L));
    verify(repository, never()).findVersion(anyLong());
    verify(repository, never()).listFields(anyLong());
  }

  @Test
  void catalogUsesRequestedIdsWithoutLoadingTheWholeDatasetList() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetReader reader = new DatasetReader(repository);
    Dataset dataset = dataset(21L, null, DatasetStatus.ONLINE);
    when(repository.listDatasetsByIds(List.of(21L, 22L))).thenReturn(List.of(dataset));

    List<DatasetCatalogEntry> result = reader.catalog(List.of(21L, 22L, 21L));

    assertThat(result).extracting(entry -> entry.dataset().id()).containsExactly(21L);
    verify(repository).listDatasetsByIds(List.of(21L, 22L));
    verify(repository, never()).listDatasets();
  }

  private Dataset dataset(long datasetId, Long currentVersionId, DatasetStatus status) {
    return new Dataset(
        datasetId,
        "dataset-" + datasetId,
        null,
        status,
        currentVersionId,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private DatasetVersion version(long versionId, long datasetId, int versionNo) {
    return new DatasetVersion(
        versionId,
        datasetId,
        versionNo,
        DatasetSourceType.SQL_QUERY,
        0L,
        0L,
        0,
        "ds-1",
        "select 1",
        "[]",
        Instant.EPOCH);
  }

  private DatasetField field(String fieldId, long versionId) {
    return new DatasetField(
        fieldId,
        versionId,
        fieldId,
        fieldId,
        DatasetFieldDataType.NUMBER,
        true,
        null,
        DatasetFieldRole.MEASURE,
        1);
  }
}
