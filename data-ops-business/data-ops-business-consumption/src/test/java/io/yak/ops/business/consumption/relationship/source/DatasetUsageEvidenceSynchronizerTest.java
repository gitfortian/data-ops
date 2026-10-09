package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetSuccessfulQueryAudit;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetUsageEvidenceSynchronizerTest {

  @Test
  void usesPersistedSuccessfulQueryEvidenceAndProjectScopedNormalization() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetQueryPerformance trace = mock(DatasetQueryPerformance.class);
    UsageNormalizationResult expected = UsageNormalizationResult.gap(
        "query:historic", "Historic query is missing stable attribution");
    when(project.requireProjectId()).thenReturn(42L);
    when(reader.recentPersisted(Set.of(101L), Set.of(DatasetQueryStatus.SUCCESS), 200))
        .thenReturn(List.of(trace));
    when(normalizer.normalize(42L, trace)).thenReturn(expected);
    DatasetUsageEvidenceSynchronizer service = new DatasetUsageEvidenceSynchronizer(
        reader, normalizer, project);

    assertEquals(List.of(expected), service.synchronizeRecentByProduct(101L, 999));

    verify(reader).recentPersisted(Set.of(101L), Set.of(DatasetQueryStatus.SUCCESS), 200);
    verify(normalizer).normalize(42L, trace);
  }

  @Test
  void exactOldVersionReadsPersistedProjectScopedVersionInsteadOfRecentDatasetWindow() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetQueryPerformance old = mock(DatasetQueryPerformance.class);
    UsageNormalizationResult gap =
        UsageNormalizationResult.gap("query:old", "missing historic subject");
    when(project.requireProjectId()).thenReturn(42L);
    when(reader.recentSuccessfulByDatasetAndVersion(101L, 9007199254740993L, 200))
        .thenReturn(List.of(old));
    when(normalizer.normalize(42L, old)).thenReturn(gap);

    var result = new DatasetUsageEvidenceSynchronizer(reader, normalizer, project)
        .synchronizeRecentByProductAndVersion(101L, 9007199254740993L, 999);

    assertEquals(List.of(gap), result);
    verify(reader).recentSuccessfulByDatasetAndVersion(101L, 9007199254740993L, 200);
    org.mockito.Mockito.verify(reader, org.mockito.Mockito.never()).recentPersisted(
        org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anySet(),
        org.mockito.ArgumentMatchers.anyInt());
    verify(normalizer).normalize(42L, old);
  }

  @Test
  void historicalPageAdvancesOnlyByLastDurableAuditIdAndCapsTo200() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetQueryPerformance old = mock(DatasetQueryPerformance.class);
    when(project.requireProjectId()).thenReturn(42L);
    List<DatasetSuccessfulQueryAudit> page = java.util.stream.LongStream.rangeClosed(1, 200)
        .mapToObj(id -> new DatasetSuccessfulQueryAudit(901L - id, old))
        .toList();
    when(reader.successfulPageByDatasetAndVersion(101L, 9007199254740993L, null, 200))
        .thenReturn(page);
    when(normalizer.normalize(42L, old)).thenReturn(
        UsageNormalizationResult.gap("query:old", "test marker"));
    var sync = new DatasetUsageEvidenceSynchronizer(reader, normalizer, project);

    var first = sync.recoverSuccessfulVersionPage(101L, 9007199254740993L, null, 500);

    assertEquals(200, first.results().size());
    assertEquals(701L, first.nextBeforeAuditId());
    assertEquals(false, first.exhausted());
    verify(reader).successfulPageByDatasetAndVersion(101L, 9007199254740993L, null, 200);

    when(reader.successfulPageByDatasetAndVersion(101L, 9007199254740993L, 701L, 200))
        .thenReturn(List.of(new DatasetSuccessfulQueryAudit(700L, old)));
    var second = sync.recoverSuccessfulVersionPage(101L, 9007199254740993L, 701L, 200);
    assertEquals(1, second.results().size());
    assertEquals(null, second.nextBeforeAuditId());
    assertEquals(true, second.exhausted());
  }

  @Test
  void unorderedOrRepeatedDatasetAuditCursorCannotNormalizeOrAdvance() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    DatasetQueryPerformance trace = mock(DatasetQueryPerformance.class);
    var sync = new DatasetUsageEvidenceSynchronizer(reader, normalizer, project);
    when(reader.successfulPageByDatasetAndVersion(101L, 99L, 900L, 3))
        .thenReturn(List.of(
            new DatasetSuccessfulQueryAudit(899L, trace),
            new DatasetSuccessfulQueryAudit(899L, trace)));

    assertThrows(IllegalStateException.class,
        () -> sync.recoverSuccessfulVersionPage(101L, 99L, 900L, 3));
    verify(normalizer, never()).normalize(any(Long.class), any(DatasetQueryPerformance.class));
  }

  @Test
  void wrongDatasetRecoveryCursorCannotNormalizeOrAdvance() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(reader.successfulPageByDatasetAndVersion(101L, 99L, 900L, 3))
        .thenReturn(List.of(new DatasetSuccessfulQueryAudit(901L,
            mock(DatasetQueryPerformance.class))));
    var sync = new DatasetUsageEvidenceSynchronizer(reader, normalizer, project);

    assertThrows(IllegalStateException.class,
        () -> sync.recoverSuccessfulVersionPage(101L, 99L, 900L, 3));
    verify(normalizer, never()).normalize(any(Long.class), any(DatasetQueryPerformance.class));
  }

  @Test
  void persistenceFailureMustReachConsumerImpactInsteadOfPretendingToBeEmpty() {
    DatasetQueryPerformanceReader reader = mock(DatasetQueryPerformanceReader.class);
    DatasetUsageEvidenceNormalizer normalizer = mock(DatasetUsageEvidenceNormalizer.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(reader.recentPersisted(Set.of(101L), Set.of(DatasetQueryStatus.SUCCESS), 50))
        .thenThrow(new IllegalStateException("persisted source logs unavailable"));
    DatasetUsageEvidenceSynchronizer service = new DatasetUsageEvidenceSynchronizer(
        reader, normalizer, project);

    assertThrows(IllegalStateException.class,
        () -> service.synchronizeRecentByProduct(101L, 50));
  }
}
