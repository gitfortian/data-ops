package io.yak.ops.business.analysis.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.definition.AnalysisChangedEvent;
import io.yak.ops.business.analysis.definition.AnalysisReader;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AnalysisLineageRefreshListenerTest {

  @Test
  void refreshRestoresProjectAndReadsCommittedAnalysisSnapshot() {
    AnalysisReader reader = mock(AnalysisReader.class);
    AnalysisLineageSynchronizer lineage = mock(AnalysisLineageSynchronizer.class);
    RecordingProjectContextScope scope = new RecordingProjectContextScope();
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(reader, lineage, scope);
    AnalysisAsset asset = asset();
    when(reader.require(5L)).thenReturn(asset);

    listener.refresh(AnalysisChangedEvent.refreshed(23L, 5L));

    assertThat(scope.context.projectId()).isEqualTo(23L);
    verify(lineage).syncCurrent(asset);
  }

  @Test
  void projectionFailureDoesNotEscapeCommittedBusinessCall() {
    AnalysisReader reader = mock(AnalysisReader.class);
    AnalysisLineageSynchronizer lineage = mock(AnalysisLineageSynchronizer.class);
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(reader, lineage, new RecordingProjectContextScope());
    when(reader.require(5L)).thenThrow(new IllegalStateException("lineage unavailable"));

    assertThatCode(() -> listener.refresh(AnalysisChangedEvent.refreshed(23L, 5L)))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteClearsEvidenceInsideOwningProjectWithoutReadingDeletedAnalysis() {
    AnalysisReader reader = mock(AnalysisReader.class);
    AnalysisLineageSynchronizer lineage = mock(AnalysisLineageSynchronizer.class);
    RecordingProjectContextScope scope = new RecordingProjectContextScope();
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(reader, lineage, scope);

    listener.refresh(AnalysisChangedEvent.deleted(23L, 5L));

    assertThat(scope.context.projectId()).isEqualTo(23L);
    verify(lineage).clear(5L);
  }

  private static AnalysisAsset asset() {
    return new AnalysisAsset(
        5L,
        "A",
        null,
        9L,
        AnalysisChartType.METRIC,
        new AnalysisQuerySpec(
            List.of(),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            100,
            30),
        new AnalysisVisualConfig(false, false, false, false),
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private static final class RecordingProjectContextScope implements ProjectContextScope {
    private ProjectContext context;

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      this.context = context;
      return action.get();
    }
  }
}
