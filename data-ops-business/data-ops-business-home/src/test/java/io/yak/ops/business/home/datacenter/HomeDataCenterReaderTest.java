package io.yak.ops.business.home.datacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeDataCenterReaderTest {

  @Test
  void shouldMergeDomainReadModelsWithoutAveragingAverages() {
    OfflineExecutionOverviewReader offline = mock(OfflineExecutionOverviewReader.class);
    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    QualityExecutionOverviewReader quality = mock(QualityExecutionOverviewReader.class);
    HomeDataCenterReader reader = reader(offline, workflow, quality);

    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDateTime bucket = yesterday.atStartOfDay();
    LocalDateTime offlineTime = yesterday.atTime(10, 0);
    LocalDateTime workflowTime = yesterday.atTime(20, 0);

    when(offline.overview(any(), any(), anyBoolean()))
        .thenReturn(
            new OfflineExecutionOverviewReader.Overview(
                new OfflineExecutionOverviewReader.Metrics(2, 1, 1, 3, 100, 1000, 2),
                List.of(new OfflineExecutionOverviewReader.TrendPoint(bucket, 2)),
                new OfflineExecutionOverviewReader.Execution(
                    "10", "离线任务", "SUCCEEDED", offlineTime, 500, "1001")),
            offlineEmpty());
    when(workflow.overview(any(), any(), anyBoolean()))
        .thenReturn(
            new WorkflowExecutionOverviewReader.Overview(
                new WorkflowExecutionOverviewReader.Metrics(3, 2, 1, 0, 0, 900, 3),
                List.of(new WorkflowExecutionOverviewReader.TrendPoint(bucket, 3)),
                new WorkflowExecutionOverviewReader.Execution(
                    "wf-1", "工作流", "SUCCESS", workflowTime, 300, "exec-1")),
            workflowEmpty());
    when(quality.overview(any(), any(), anyBoolean()))
        .thenReturn(
            new QualityExecutionOverviewReader.Overview(
                new QualityExecutionOverviewReader.Metrics(1, 1, 0, 2, 0, 100, 1),
                List.of(new QualityExecutionOverviewReader.TrendPoint(bucket, 1)),
                null),
            qualityEmpty());
    when(workflow.taskSummary(any(), any(), any()))
        .thenReturn(new WorkflowExecutionOverviewReader.TaskSummary(
            "wf-1", "工作流", workflowTime, 7, 6, 1, 300, "SUCCESS", "exec-1"));

    HomeDataCenterReader.OverviewResponse response = reader.overview("7d");

    assertThat(response.metrics().successCount()).isEqualTo(6);
    assertThat(response.metrics().runningCount()).isEqualTo(4);
    assertThat(response.metrics().failedCount()).isEqualTo(2);
    assertThat(response.metrics().scheduleCount()).isEqualTo(5);
    assertThat(response.metrics().processedRecords()).isEqualTo(100);
    assertThat(response.metrics().avgDurationMs()).isEqualTo(333);
    assertThat(response.trend().values()).contains(6L);
    assertThat(response.latestTask().taskType()).isEqualTo("WORKFLOW");
    assertThat(response.latestTask().runCount()).isEqualTo(7);
    assertThat(response.latestTask().exceptionCount()).isEqualTo(1);
    assertThat(response.latestTask().detailPath()).isEqualTo("/workflow/instances");
  }

  @Test
  void shouldKeepDataCenterAvailableWhenOneDomainReaderFails() {
    OfflineExecutionOverviewReader offline = mock(OfflineExecutionOverviewReader.class);
    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    QualityExecutionOverviewReader quality = mock(QualityExecutionOverviewReader.class);
    HomeDataCenterReader reader = reader(offline, workflow, quality);

    when(offline.overview(any(), any(), anyBoolean()))
        .thenThrow(new IllegalStateException("offline unavailable"));
    when(workflow.overview(any(), any(), anyBoolean()))
        .thenReturn(
            new WorkflowExecutionOverviewReader.Overview(
                new WorkflowExecutionOverviewReader.Metrics(2, 0, 0, 0, 0, 0, 0),
                List.of(),
                null),
            workflowEmpty());
    when(quality.overview(any(), any(), anyBoolean()))
        .thenReturn(qualityEmpty(), qualityEmpty());

    HomeDataCenterReader.OverviewResponse response = reader.overview("7d");

    assertThat(response.metrics().successCount()).isEqualTo(2);
    assertThat(response.metrics().failedCount()).isZero();
  }

  @Test
  void shouldPreserveLegacyScheduleDetailPaths() {
    OfflineExecutionOverviewReader offline = mock(OfflineExecutionOverviewReader.class);
    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    QualityExecutionOverviewReader quality = mock(QualityExecutionOverviewReader.class);
    HomeDataCenterReader reader = reader(offline, workflow, quality);

    LocalDateTime now = LocalDateTime.now();
    when(offline.schedules(anyInt())).thenReturn(List.of(
        new OfflineExecutionOverviewReader.ScheduleSummary(
            "11", "离线调度", "0 0 * * * ?", "ENABLED", now.minusDays(1), now.plusHours(2))));
    when(workflow.schedules(anyInt())).thenReturn(List.of(
        new WorkflowExecutionOverviewReader.ScheduleSummary(
            "schedule-1", "工作流调度", "0 0 * * * ?", "ONLINE", now.minusDays(1), now.plusHours(1))));

    HomeDataCenterReader.ScheduleResponse response = reader.schedules("7d");

    assertThat(response.items()).hasSize(2);
    assertThat(response.items().get(0).taskType()).isEqualTo("WORKFLOW");
    assertThat(response.items().get(0).detailPath()).isEqualTo("/workflow/schedules");
    assertThat(response.items().get(1).detailPath()).isEqualTo("/sync/batch-link-up/11/detail");
  }

  @SuppressWarnings("unchecked")
  private HomeDataCenterReader reader(
      OfflineExecutionOverviewReader offline,
      WorkflowExecutionOverviewReader workflow,
      QualityExecutionOverviewReader quality) {
    ObjectProvider<OfflineExecutionOverviewReader> offlineProvider = mock(ObjectProvider.class);
    ObjectProvider<WorkflowExecutionOverviewReader> workflowProvider = mock(ObjectProvider.class);
    ObjectProvider<QualityExecutionOverviewReader> qualityProvider = mock(ObjectProvider.class);
    when(offlineProvider.getIfAvailable()).thenReturn(offline);
    when(workflowProvider.getIfAvailable()).thenReturn(workflow);
    when(qualityProvider.getIfAvailable()).thenReturn(quality);
    return new HomeDataCenterReader(offlineProvider, workflowProvider, qualityProvider);
  }

  private OfflineExecutionOverviewReader.Overview offlineEmpty() {
    return new OfflineExecutionOverviewReader.Overview(
        new OfflineExecutionOverviewReader.Metrics(0, 0, 0, 0, 0, 0, 0), List.of(), null);
  }

  private WorkflowExecutionOverviewReader.Overview workflowEmpty() {
    return new WorkflowExecutionOverviewReader.Overview(
        new WorkflowExecutionOverviewReader.Metrics(0, 0, 0, 0, 0, 0, 0), List.of(), null);
  }

  private QualityExecutionOverviewReader.Overview qualityEmpty() {
    return new QualityExecutionOverviewReader.Overview(
        new QualityExecutionOverviewReader.Metrics(0, 0, 0, 0, 0, 0, 0), List.of(), null);
  }
}
