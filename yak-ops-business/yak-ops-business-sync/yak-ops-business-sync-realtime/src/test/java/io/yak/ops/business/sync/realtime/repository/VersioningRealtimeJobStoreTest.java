package io.yak.ops.business.sync.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.DomainMappingState;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.PublicationCandidate;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.PublicationSnapshot;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.StoredVersion;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VersioningRealtimeJobStoreTest {

  private static final long TASK_ID = 7L;
  private static final String SOURCE_DIGEST = "a".repeat(64);

  @Test
  void publishCreatesMappedImmutableVersionAndBindsReference() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    DefinitionRow current = definitionRow(spec("fixed-delay"));
    StoredVersion stored =
        new StoredVersion(
            101L,
            TASK_ID,
            1,
            current.definitionVersion(),
            "b".repeat(64),
            SOURCE_DIGEST,
            DomainMappingState.MAPPED,
            LocalDateTime.now());
    when(delegate.lockDefinition(TASK_ID)).thenReturn(current);
    when(versions.findOrCreate(any())).thenReturn(stored);

    store.publish(TASK_ID, current.definitionVersion(), SOURCE_DIGEST);

    ArgumentCaptor<PublicationCandidate> candidate =
        ArgumentCaptor.forClass(PublicationCandidate.class);
    verify(delegate).publish(TASK_ID, current.definitionVersion(), SOURCE_DIGEST);
    verify(versions).findOrCreate(candidate.capture());
    verify(versions)
        .bindPublishedReference(
            TASK_ID, stored.id(), current.definitionVersion(), SOURCE_DIGEST);

    assertThat(candidate.getValue().domainMappingState()).isEqualTo(DomainMappingState.MAPPED);
    assertThat(candidate.getValue().domainDefinition()).isNotNull();
    assertThat(candidate.getValue().definitionDigest()).isNotNull();
    assertThat(candidate.getValue().sourceConfigDigest()).isEqualTo(SOURCE_DIGEST);
    assertThat(candidate.getValue().compatibilityDefinition()).isEqualTo(current.spec());
  }

  @Test
  void legacyFailureRateStillPublishesButIsMarkedUnmapped() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    DefinitionRow current = definitionRow(spec("failure-rate"));
    StoredVersion stored =
        new StoredVersion(
            102L,
            TASK_ID,
            1,
            current.definitionVersion(),
            null,
            SOURCE_DIGEST,
            DomainMappingState.LEGACY_UNMAPPED,
            LocalDateTime.now());
    when(delegate.lockDefinition(TASK_ID)).thenReturn(current);
    when(versions.findOrCreate(any())).thenReturn(stored);

    store.publish(TASK_ID, current.definitionVersion(), SOURCE_DIGEST);

    ArgumentCaptor<PublicationCandidate> candidate =
        ArgumentCaptor.forClass(PublicationCandidate.class);
    verify(delegate).publish(TASK_ID, current.definitionVersion(), SOURCE_DIGEST);
    verify(versions).findOrCreate(candidate.capture());
    verify(versions)
        .bindPublishedReference(
            TASK_ID, stored.id(), current.definitionVersion(), SOURCE_DIGEST);

    assertThat(candidate.getValue().domainMappingState())
        .isEqualTo(DomainMappingState.LEGACY_UNMAPPED);
    assertThat(candidate.getValue().domainDefinition()).isNull();
    assertThat(candidate.getValue().definitionDigest()).isNull();
  }

  @Test
  void resolvesPublishedDefinitionThroughTaskReference() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    CdcPipelineSpec snapshotSpec = spec("fixed-delay");
    StoredVersion stored = storedVersion(201L, TASK_ID, 3, 8);
    when(versions.publishedDefinitionVersionId(TASK_ID)).thenReturn(Optional.of(stored.id()));
    when(versions.find(stored.id()))
        .thenReturn(Optional.of(new PublicationSnapshot(stored, 33L, snapshotSpec)));

    PublishedDefinitionRow published = store.publishedDefinition(TASK_ID).orElseThrow();

    assertThat(published.id()).isEqualTo(stored.id());
    assertThat(published.taskId()).isEqualTo(TASK_ID);
    assertThat(published.versionNo()).isEqualTo(3);
    assertThat(published.sourceDraftRevision()).isEqualTo(8);
    assertThat(published.spec()).isEqualTo(snapshotSpec);
    assertThat(published.runtimeEnvironmentId()).isEqualTo(33L);
    assertThat(published.sourceConfigDigest()).isEqualTo(SOURCE_DIGEST);
  }

  @Test
  void resolvesHistoricalVersionWithoutConsultingCurrentPublishedReference() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    StoredVersion v3 = storedVersion(203L, TASK_ID, 3, 8);
    CdcPipelineSpec snapshotSpec = spec("fixed-delay");
    when(versions.find(v3.id()))
        .thenReturn(Optional.of(new PublicationSnapshot(v3, 33L, snapshotSpec)));

    PublishedDefinitionRow historical = store.definitionVersion(TASK_ID, v3.id()).orElseThrow();

    assertThat(historical.id()).isEqualTo(v3.id());
    assertThat(historical.versionNo()).isEqualTo(3);
    assertThat(historical.spec()).isEqualTo(snapshotSpec);
  }

  @Test
  void brokenPublishedReferenceIsReportedInsteadOfPretendingTaskIsUnpublished() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    when(versions.publishedDefinitionVersionId(TASK_ID)).thenReturn(Optional.of(999L));
    when(versions.find(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.publishedDefinition(TASK_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("引用的 Published DefinitionVersion 不存在");
  }

  @Test
  void rejectsPublishedVersionThatBelongsToAnotherTask() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    StoredVersion foreign = storedVersion(301L, 99L, 1, 1);
    when(versions.publishedDefinitionVersionId(TASK_ID)).thenReturn(Optional.of(foreign.id()));
    when(versions.find(foreign.id()))
        .thenReturn(Optional.of(new PublicationSnapshot(foreign, 3L, spec("fixed-delay"))));

    assertThatThrownBy(() -> store.publishedDefinition(TASK_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不属于当前实时同步任务");
  }

  @Test
  void primaryVersioningStoreDelegatesExecutionReconcileCandidates() {
    RealtimeJobStoreAdapter delegate = mock(RealtimeJobStoreAdapter.class);
    DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    VersioningRealtimeJobStore store =
        new VersioningRealtimeJobStore(
            delegate, versions, new CdcPipelineSpecCompatibilityMapper());
    DeploymentRow execution = mock(DeploymentRow.class);
    when(delegate.reconcileCandidates()).thenReturn(List.of(execution));

    assertThat(store.reconcileCandidates()).containsExactly(execution);
    verify(delegate).reconcileCandidates();
  }

  private StoredVersion storedVersion(
      long id, long taskId, int versionNo, int sourceDraftRevision) {
    return new StoredVersion(
        id,
        taskId,
        versionNo,
        sourceDraftRevision,
        "b".repeat(64),
        SOURCE_DIGEST,
        DomainMappingState.MAPPED,
        LocalDateTime.now());
  }

  private DefinitionRow definitionRow(CdcPipelineSpec spec) {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        "test",
        spec,
        3L,
        "DRAFT",
        "STOPPED",
        "STOPPED",
        4,
        3,
        SOURCE_DIGEST,
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private CdcPipelineSpec spec(String restartStrategy) {
    return new CdcPipelineSpec(
        11L,
        22L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "ods_orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy(restartStrategy, 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
