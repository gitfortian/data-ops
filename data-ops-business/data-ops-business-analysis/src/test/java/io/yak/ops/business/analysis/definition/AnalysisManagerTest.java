package io.yak.ops.business.analysis.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.AnalysisDeletionGuard;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.repository.AnalysisRepository;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AnalysisManagerTest {

  @Test
  void createPersistsNormalizedDefinitionAndPublishesProjectScopedProjectionFact() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    AnalysisDefinitionNormalizer normalizer = mock(AnalysisDefinitionNormalizer.class);
    AnalysisReader reader = mock(AnalysisReader.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AnalysisManager manager = new AnalysisManager(
        repository, normalizer, reader, currentProject(23L), events, List.of());
    AnalysisSaveCommand command = command();
    AnalysisDefinition definition = definition();
    AnalysisAsset stored = asset(11L);

    when(normalizer.normalize(command)).thenReturn(definition);
    when(repository.insert(definition)).thenReturn(11L);
    when(reader.require(11L)).thenReturn(stored);

    AnalysisAsset created = manager.create(command);

    assertThat(created.id()).isEqualTo(11L);
    verify(events).publishEvent(AnalysisChangedEvent.refreshed(23L, 11L));
  }

  @Test
  void deleteGuardStillRestrictsReferencedAnalysis() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    AnalysisDefinitionNormalizer normalizer = mock(AnalysisDefinitionNormalizer.class);
    AnalysisReader reader = mock(AnalysisReader.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AnalysisDeletionGuard guard = mock(AnalysisDeletionGuard.class);
    AnalysisManager manager = new AnalysisManager(
        repository, normalizer, reader, currentProject(23L), events, List.of(guard));
    when(reader.require(7L)).thenReturn(asset(7L));
    doThrow(new IllegalStateException("still referenced"))
        .when(guard)
        .requireDeletable(7L);

    assertThatThrownBy(() -> manager.delete(7L))
        .isInstanceOf(IllegalStateException.class);
    verify(repository, never()).delete(7L);
  }

  private static CurrentProject currentProject(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "P" + projectId));
  }

  private static AnalysisSaveCommand command() {
    return new AnalysisSaveCommand(
        "区域销售",
        null,
        9L,
        AnalysisChartType.BAR,
        query(),
        new AnalysisVisualConfig(false, false, false, true));
  }

  private static AnalysisDefinition definition() {
    return new AnalysisDefinition(
        "区域销售",
        null,
        9L,
        AnalysisChartType.BAR,
        query(),
        new AnalysisVisualConfig(false, false, false, true));
  }

  private static AnalysisAsset asset(long id) {
    return new AnalysisAsset(
        id,
        "区域销售",
        null,
        9L,
        AnalysisChartType.BAR,
        query(),
        new AnalysisVisualConfig(false, false, false, true),
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private static AnalysisQuerySpec query() {
    return new AnalysisQuerySpec(
        List.of("region"),
        List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
        List.of(),
        List.of(),
        500,
        30);
  }
}
