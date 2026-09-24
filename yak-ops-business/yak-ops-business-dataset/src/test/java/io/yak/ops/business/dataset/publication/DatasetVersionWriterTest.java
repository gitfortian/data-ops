package io.yak.ops.business.dataset.publication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldIdentity;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetVersionWriterTest {

  @Test
  void locksDatasetBeforeAllocatingAndAppendingNextVersion() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetFieldNormalizer normalizer =
        new DatasetFieldNormalizer(repository, new DatasetFieldIdentity());
    DatasetVersionWriter writer = new DatasetVersionWriter(repository, normalizer);
    Dataset dataset = new Dataset(
        21L,
        "sales",
        "sales dataset",
        DatasetStatus.ONLINE,
        31L,
        Instant.EPOCH,
        Instant.EPOCH);
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.nextVersionNo(21L)).thenReturn(2);
    when(repository.appendVersion(any())).thenReturn(32L);

    writer.appendNextQueryRevision(
        21L,
        11L,
        72L,
        4,
        List.of(new DatasetFieldSpec(null, "id", "id", null, false, null, null)));

    var order = inOrder(repository);
    order.verify(repository).lockDatasetForUpdate(21L);
    order.verify(repository).findDataset(21L);
    order.verify(repository).nextVersionNo(21L);
    order.verify(repository).appendVersion(any());
    order.verify(repository).updateCurrentVersion(21L, 32L);
  }
}
