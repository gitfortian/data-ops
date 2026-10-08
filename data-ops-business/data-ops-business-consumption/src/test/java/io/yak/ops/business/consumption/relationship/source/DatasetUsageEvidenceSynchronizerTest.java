package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
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
