package io.yak.ops.business.dataset.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.publication.DatasetPublishCommand;
import io.yak.ops.business.dataset.publication.DatasetPublisher;
import io.yak.ops.business.dataset.publication.DatasetVersionWriter;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldIdentity;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentDatasetManagerTest {

  @Test
  void datasetNodeCreatesStableIdentityBoundToExactSqlRevision() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset = taskAsset(11L, 71L, 3);
    Dataset dataset = dataset(21L, 31L, "sales");
    DatasetVersion version = version(31L, 21L, 1, 11L, 71L, 3);
    when(fixture.publisher.requirePublishableAsset(11L)).thenReturn(asset);
    when(fixture.repository.findDatasetByDevelopmentNodeId(501L)).thenReturn(Optional.empty());
    when(fixture.repository.insertDevelopmentNodeDataset(501L, "sales_dataset", "sales"))
        .thenReturn(21L);
    when(fixture.discovery.discover(21L, asset)).thenReturn(List.of());
    when(fixture.repository.nextVersionNo(21L)).thenReturn(1);
    when(fixture.repository.appendVersion(any(DatasetVersionDraft.class))).thenReturn(31L);
    when(fixture.repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(fixture.repository.listVersions(21L)).thenReturn(List.of(version));
    when(fixture.repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result =
        fixture.manager.saveTaskAsset(
            501L,
            new DatasetPublishCommand(11L, "sales_dataset", "sales", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    assertEquals(71L, result.currentVersion().sourceTaskRevisionId());
    assertEquals(3, result.currentVersion().sourceTaskRevisionNo());
    verify(fixture.repository).insertDevelopmentNodeDataset(501L, "sales_dataset", "sales");
    verify(fixture.repository).updateCurrentVersion(21L, 31L);
    verify(fixture.repository).appendVersion(any(DatasetVersionDraft.class));
  }

  @Test
  void savingSameRevisionAndSchemaOnlyUpdatesMutableMetadata() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset = taskAsset(11L, 71L, 3);
    Dataset dataset = dataset(21L, 31L, "before");
    DatasetVersion version = version(31L, 21L, 1, 11L, 71L, 3);
    when(fixture.publisher.requirePublishableAsset(11L)).thenReturn(asset);
    when(fixture.repository.findDatasetByDevelopmentNodeId(501L)).thenReturn(Optional.of(dataset));
    when(fixture.repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(fixture.repository.listVersions(21L)).thenReturn(List.of(version));
    when(fixture.repository.listFields(31L)).thenReturn(List.of());
    when(fixture.discovery.discover(21L, asset)).thenReturn(List.of());

    DatasetDetail result =
        fixture.manager.saveTaskAsset(
            501L,
            new DatasetPublishCommand(11L, "sales_dataset", "after", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    verify(fixture.repository).updateMetadata(21L, "sales_dataset", "after");
    verify(fixture.repository, never()).appendVersion(any(DatasetVersionDraft.class));
  }

  private Fixture fixture() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetPublisher publisher = mock(DatasetPublisher.class);
    DatasetSchemaDiscovery discovery = mock(DatasetSchemaDiscovery.class);
    DatasetLineageRefreshPublisher lineagePublisher = mock(DatasetLineageRefreshPublisher.class);
    DatasetReader reader = new DatasetReader(repository);
    DatasetFieldNormalizer normalizer =
        new DatasetFieldNormalizer(repository, new DatasetFieldIdentity());
    DatasetVersionWriter versionWriter = new DatasetVersionWriter(repository, normalizer);
    DevelopmentDatasetManager manager =
        new DevelopmentDatasetManager(
            repository,
            reader,
            publisher,
            discovery,
            normalizer,
            versionWriter,
            lineagePublisher);
    return new Fixture(repository, publisher, discovery, manager);
  }

  private Dataset dataset(long datasetId, long versionId, String description) {
    return new Dataset(
        datasetId,
        "sales_dataset",
        description,
        DatasetStatus.ONLINE,
        versionId,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private DatasetVersion version(
      long versionId,
      long datasetId,
      int versionNo,
      long assetId,
      long revisionId,
      int revisionNo) {
    return new DatasetVersion(
        versionId,
        datasetId,
        versionNo,
        DatasetSourceType.QUERY_REVISION,
        assetId,
        revisionId,
        revisionNo,
        "[]",
        Instant.EPOCH);
  }

  private DatasetTaskAssetSnapshot taskAsset(long assetId, long revisionId, int revisionNo) {
    return new DatasetTaskAssetSnapshot(
        assetId,
        "sales.sql",
        "101",
        SourceOrigin.DATA_DEVELOPMENT,
        SourceAvailability.ONLINE,
        "SQL",
        revisionId,
        revisionNo);
  }

  private record Fixture(
      DatasetRepository repository,
      DatasetPublisher publisher,
      DatasetSchemaDiscovery discovery,
      DevelopmentDatasetManager manager) {}
}
