package io.yak.ops.business.home.cockpit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeCockpitReaderTest {

  @Test
  void shouldBuildHeaderFromRequiredDomainReadSides() {
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    when(dataSourceReader.summary()).thenReturn(new DataSourceSummary(12, 10, 1, 1, 3));

    OfflineExecutionOverviewReader offline = mock(OfflineExecutionOverviewReader.class);
    when(offline.metrics(any(), any()))
        .thenReturn(new OfflineExecutionOverviewReader.Metrics(18, 2, 3, 8, 1000, 100, 2));

    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    when(workflow.metrics(any(), any()))
        .thenReturn(new WorkflowExecutionOverviewReader.Metrics(9, 1, 2, 0, 0, 0, 0));

    QualityExecutionOverviewReader qualityExecution = mock(QualityExecutionOverviewReader.class);
    when(qualityExecution.metrics(any(), any()))
        .thenReturn(new QualityExecutionOverviewReader.Metrics(4, 1, 1, 0, 0, 0, 0));

    HomeCockpitReader reader = new HomeCockpitReader(
        provider(dataSourceReader),
        provider(offline),
        provider(workflow),
        provider(qualityExecution));

    HomeCockpitReader.CockpitResponse response = reader.cockpit();

    assertThat(response.header().dataSourceCount()).isEqualTo(12);
    assertThat(response.header().runningCount()).isEqualTo(4);
    assertThat(response.header().dataSourceAvailable()).isTrue();
    assertThat(response.header().runningAvailable()).isTrue();
  }

  @Test
  void shouldKeepHeaderAvailableWhenOptionalReadSidesAreUnavailable() {
    HomeCockpitReader reader = new HomeCockpitReader(
        provider(null),
        provider(null),
        provider(null),
        provider(null));

    HomeCockpitReader.CockpitResponse response = reader.cockpit();

    assertThat(response.header().dataSourceCount()).isZero();
    assertThat(response.header().runningCount()).isZero();
    assertThat(response.header().dataSourceAvailable()).isFalse();
    assertThat(response.header().runningAvailable()).isFalse();
    assertThat(response.header().sources().get("workflow").unavailableReason()).isEqualTo("MODULE_DISABLED");
  }

  @Test
  void aFailedSourceDoesNotHideAnotherSourcesRealZero() {
    DataSourceReader datasource = mock(DataSourceReader.class);
    when(datasource.summary()).thenReturn(new DataSourceSummary(0, 0, 0, 0, 0));
    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    when(workflow.metrics(any(), any())).thenThrow(new IllegalStateException("unavailable"));
    HomeCockpitReader.HeaderStats header = new HomeCockpitReader(
        provider(datasource), provider(null), provider(workflow), provider(null)).cockpit().header();
    assertThat(header.dataSourceCount()).isZero();
    assertThat(header.dataSourceAvailable()).isTrue();
    assertThat(header.runningAvailable()).isFalse();
    assertThat(header.sources().get("workflow").unavailableReason()).isEqualTo("QUERY_UNAVAILABLE");
    assertThat(header.observedAt()).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }
}
