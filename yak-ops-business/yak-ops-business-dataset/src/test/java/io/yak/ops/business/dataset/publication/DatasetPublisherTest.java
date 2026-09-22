package io.yak.ops.business.dataset.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldIdentity;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetPublisherTest {

  @Test
  void publishSnapshotsCurrentTaskRevisionWithoutOwningTaskExecution() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset =
        taskAsset(11L, SourceAvailability.ONLINE, "SQL", 71L, 3);
    when(fixture.taskCatalog.get(11L)).thenReturn(asset);
    when(fixture.repository.insertDataset("sales", "sales dataset")).thenReturn(21L);
    when(fixture.schemaDiscovery.discover(21L, asset)).thenReturn(List.of());
    stubVersion(fixture.repository, 21L, 31L, 1, 11L, 71L, 3);

    DatasetDetail result =
        fixture.publisher.publish(
            new DatasetPublishCommand(11L, "sales", "sales dataset", List.of()));

    assertEquals(71L, result.currentVersion().sourceTaskRevisionId());
    assertEquals(3, result.currentVersion().sourceTaskRevisionNo());
    verify(fixture.repository).updateCurrentVersion(21L, 31L);
  }

  @Test
  void publishFromReleaseIsIdempotentForCurrentTaskRevision() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset =
        taskAsset(11L, SourceAvailability.ONLINE, "SQL", 71L, 3);
    Dataset dataset = dataset(21L, 31L);
    DatasetVersion version = version(31L, 21L, 1, 11L, 71L, 3);
    when(fixture.taskCatalog.get(11L)).thenReturn(asset);
    when(fixture.repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(dataset));
    when(fixture.repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(fixture.repository.listVersions(21L)).thenReturn(List.of(version));
    when(fixture.repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result =
        fixture.publisher.publishFromRelease(
            new DatasetPublishCommand(11L, "ignored", "ignored", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    verify(fixture.repository, never()).insertDataset("ignored", "ignored");
    verify(fixture.repository, never()).updateCurrentVersion(eq(21L), eq(31L));
  }

  @Test
  void publishFromReleaseAppendsVersionForNewTaskRevision() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset =
        taskAsset(11L, SourceAvailability.ONLINE, "SQL", 72L, 4);
    Dataset before = dataset(21L, 31L);
    Dataset after = dataset(21L, 32L);
    DatasetVersion version1 = version(31L, 21L, 1, 11L, 71L, 3);
    DatasetVersion version2 = version(32L, 21L, 2, 11L, 72L, 4);

    when(fixture.taskCatalog.get(11L)).thenReturn(asset);
    when(fixture.repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(before));
    when(fixture.repository.findDataset(21L))
        .thenReturn(Optional.of(before), Optional.of(before), Optional.of(after));
    when(fixture.repository.findVersion(31L)).thenReturn(Optional.of(version1));
    when(fixture.repository.findVersion(32L)).thenReturn(Optional.of(version2));
    when(fixture.repository.listVersions(21L))
        .thenReturn(List.of(version1), List.of(version2, version1));
    when(fixture.repository.listFields(31L)).thenReturn(List.of());
    when(fixture.repository.listFields(32L)).thenReturn(List.of());
    when(fixture.schemaDiscovery.discover(21L, asset)).thenReturn(List.of());
    when(fixture.repository.nextVersionNo(21L)).thenReturn(2);
    when(fixture.repository.appendVersion(
            argThat(
                draft ->
                    draft.datasetId() == 21L
                        && draft.versionNo() == 2
                        && draft.sourceTaskRevisionId() == 72L
                        && draft.sourceTaskRevisionNo() == 4)))
        .thenReturn(32L);

    DatasetDetail result =
        fixture.publisher.publishFromRelease(
            new DatasetPublishCommand(11L, null, null, List.of()));

    assertEquals(2, result.currentVersion().versionNo());
    assertEquals(4, result.currentVersion().sourceTaskRevisionNo());
    verify(fixture.repository).updateCurrentVersion(21L, 32L);
  }

  @Test
  void publishFromReleasePreservesStableFieldIdAcrossCustomizedVersion() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset =
        taskAsset(11L, SourceAvailability.ONLINE, "SQL", 72L, 4);
    Dataset before = dataset(21L, 31L);
    Dataset after = dataset(21L, 32L);
    DatasetVersion version1 = version(31L, 21L, 1, 11L, 71L, 3);
    DatasetVersion version2 = version(32L, 21L, 2, 11L, 72L, 4);
    DatasetField oldField =
        new DatasetField(
            "field-sales-amount",
            31L,
            "sales_amount",
            "销售额",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE,
            1);
    DatasetField newField =
        new DatasetField(
            "field-sales-amount",
            32L,
            "sales_amount",
            "销售金额",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE,
            1);

    when(fixture.taskCatalog.get(11L)).thenReturn(asset);
    when(fixture.repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(before));
    when(fixture.repository.findDataset(21L))
        .thenReturn(
            Optional.of(before),
            Optional.of(before),
            Optional.of(before),
            Optional.of(after));
    when(fixture.repository.findVersion(31L)).thenReturn(Optional.of(version1));
    when(fixture.repository.findVersion(32L)).thenReturn(Optional.of(version2));
    when(fixture.repository.listVersions(21L))
        .thenReturn(List.of(version1), List.of(version2, version1));
    when(fixture.repository.listFields(31L)).thenReturn(List.of(oldField));
    when(fixture.repository.listFields(32L)).thenReturn(List.of(newField));
    when(fixture.repository.nextVersionNo(21L)).thenReturn(2);
    when(fixture.repository.appendVersion(
            argThat(
                draft ->
                    draft.fields().size() == 1
                        && "field-sales-amount".equals(draft.fields().get(0).fieldId()))))
        .thenReturn(32L);

    DatasetDetail result =
        fixture.publisher.publishFromRelease(
            new DatasetPublishCommand(
                11L,
                null,
                null,
                List.of(
                    new DatasetFieldSpec(
                        null,
                        "sales_amount",
                        "销售金额",
                        DatasetFieldDataType.NUMBER,
                        true,
                        null,
                        DatasetFieldRole.MEASURE))));

    assertEquals("field-sales-amount", result.fields().get(0).fieldId());
    verify(fixture.repository)
        .appendVersion(
            argThat(
                draft ->
                    draft.fields().size() == 1
                        && "field-sales-amount".equals(draft.fields().get(0).fieldId())));
  }

  @Test
  void publishRejectsOfflineAsset() {
    Fixture fixture = fixture();
    when(fixture.taskCatalog.get(11L))
        .thenReturn(taskAsset(11L, SourceAvailability.NOT_ONLINE, "SQL", 71L, 3));

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                fixture.publisher.publish(
                    new DatasetPublishCommand(11L, "sales", null, List.of())));

    assertEquals("只有 ONLINE 的 TaskAsset 可以发布/更新 Dataset：11", error.getMessage());
  }

  @Test
  void publishRejectsNonSqlTask() {
    Fixture fixture = fixture();
    when(fixture.taskCatalog.get(11L))
        .thenReturn(taskAsset(11L, SourceAvailability.ONLINE, "SHELL", 71L, 3));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixture.publisher.publish(
                new DatasetPublishCommand(11L, "sales", null, List.of())));
  }

  private Fixture fixture() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetTaskCatalogGateway taskCatalog = mock(DatasetTaskCatalogGateway.class);
    DatasetSchemaDiscovery schemaDiscovery = mock(DatasetSchemaDiscovery.class);
    DatasetLineageRefreshPublisher lineagePublisher = mock(DatasetLineageRefreshPublisher.class);
    DatasetReader reader = new DatasetReader(repository);
    DatasetFieldNormalizer fieldNormalizer =
        new DatasetFieldNormalizer(repository, new DatasetFieldIdentity());
    DatasetVersionWriter versionWriter = new DatasetVersionWriter(repository, fieldNormalizer);
    DatasetPublisher publisher =
        new DatasetPublisher(
            repository,
            reader,
            taskCatalog,
            schemaDiscovery,
            fieldNormalizer,
            versionWriter,
            lineagePublisher);
    return new Fixture(repository, taskCatalog, schemaDiscovery, publisher);
  }

  private void stubVersion(
      DatasetRepository repository,
      long datasetId,
      long versionId,
      int versionNo,
      long assetId,
      long revisionId,
      int revisionNo) {
    Dataset dataset = dataset(datasetId, versionId);
    DatasetVersion version =
        version(versionId, datasetId, versionNo, assetId, revisionId, revisionNo);
    when(repository.nextVersionNo(datasetId)).thenReturn(versionNo);
    when(repository.appendVersion(argThat(draft -> draft.versionNo() == versionNo)))
        .thenReturn(versionId);
    when(repository.findDataset(datasetId)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(versionId)).thenReturn(Optional.of(version));
    when(repository.listVersions(datasetId)).thenReturn(List.of(version));
    when(repository.listFields(versionId)).thenReturn(List.of());
  }

  private Dataset dataset(long datasetId, long currentVersionId) {
    return new Dataset(
        datasetId,
        "sales",
        "sales dataset",
        DatasetStatus.ONLINE,
        currentVersionId,
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

  private DatasetTaskAssetSnapshot taskAsset(
      long assetId,
      SourceAvailability availability,
      String taskType,
      long revisionId,
      int revisionNo) {
    return new DatasetTaskAssetSnapshot(
        assetId,
        "sales.sql",
        "101",
        SourceOrigin.DATA_DEVELOPMENT,
        availability,
        taskType,
        revisionId,
        revisionNo);
  }

  private record Fixture(
      DatasetRepository repository,
      DatasetTaskCatalogGateway taskCatalog,
      DatasetSchemaDiscovery schemaDiscovery,
      DatasetPublisher publisher) {}
}
