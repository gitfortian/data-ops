package io.yak.ops.business.home.cockpit;

import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 首页头部只读聚合；只组合各业务域已经拥有的 read-side 事实。 */
@Component
public class HomeCockpitReader {

  private static final Logger LOG = LoggerFactory.getLogger(HomeCockpitReader.class);
  private static final int RANGE_DAYS = 7;

  private final ObjectProvider<DataSourceReader> dataSourceReaderProvider;
  private final ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider;
  private final ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider;
  private final ObjectProvider<QualityExecutionOverviewReader> qualityExecutionReaderProvider;

  public HomeCockpitReader(
      ObjectProvider<DataSourceReader> dataSourceReaderProvider,
      ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider,
      ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider,
      ObjectProvider<QualityExecutionOverviewReader> qualityExecutionReaderProvider) {
    this.dataSourceReaderProvider = dataSourceReaderProvider;
    this.offlineReaderProvider = offlineReaderProvider;
    this.workflowReaderProvider = workflowReaderProvider;
    this.qualityExecutionReaderProvider = qualityExecutionReaderProvider;
  }

  public CockpitResponse cockpit() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    LocalDateTime start = end.minusDays(RANGE_DAYS);

    HeaderStats header = new HeaderStats(
        dataSourceCount(),
        offlineRunningCount(start, end)
            + workflowRunningCount(start, end)
            + qualityRunningCount(start, end));
    return new CockpitResponse(header);
  }

  private long dataSourceCount() {
    DataSourceReader reader = dataSourceReaderProvider.getIfAvailable();
    if (reader == null) return 0L;
    try {
      DataSourceSummary summary = reader.summary();
      return summary.total();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页头部数据源摘要失败", exception);
      return 0L;
    }
  }

  private long offlineRunningCount(LocalDateTime start, LocalDateTime end) {
    OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
    if (reader == null) return 0L;
    try {
      return reader.metrics(start, end).runningCount();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页头部离线同步摘要失败", exception);
      return 0L;
    }
  }

  private long workflowRunningCount(LocalDateTime start, LocalDateTime end) {
    WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
    if (reader == null) return 0L;
    try {
      return reader.metrics(start, end).runningCount();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页头部工作流摘要失败", exception);
      return 0L;
    }
  }

  private long qualityRunningCount(LocalDateTime start, LocalDateTime end) {
    QualityExecutionOverviewReader reader = qualityExecutionReaderProvider.getIfAvailable();
    if (reader == null) return 0L;
    try {
      return reader.metrics(start, end).runningCount();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页头部质量运行摘要失败", exception);
      return 0L;
    }
  }

  public record CockpitResponse(HeaderStats header) {}

  public record HeaderStats(long dataSourceCount, long runningCount) {}
}
