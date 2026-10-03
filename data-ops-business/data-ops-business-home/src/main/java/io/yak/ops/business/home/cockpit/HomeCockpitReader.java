package io.yak.ops.business.home.cockpit;

import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.ToLongFunction;
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

    CountObservation datasource = observe(dataSourceReaderProvider, reader -> reader.summary().total(), "datasource");
    CountObservation offline = observe(offlineReaderProvider, reader -> reader.metrics(start, end).runningCount(), "offline");
    CountObservation workflow = observe(workflowReaderProvider, reader -> reader.metrics(start, end).runningCount(), "workflow");
    CountObservation quality = observe(qualityExecutionReaderProvider, reader -> reader.metrics(start, end).runningCount(), "quality");
    HeaderStats header = new HeaderStats(datasource.value(), offline.value() + workflow.value() + quality.value(),
        datasource.available(), offline.available() && workflow.available() && quality.available(), end,
        Map.of("datasource", datasource, "offline", offline, "workflow", workflow, "quality", quality));
    return new CockpitResponse(header);
  }

  private <T> CountObservation observe(ObjectProvider<T> provider, ToLongFunction<T> query, String source) {
    try {
      T reader = provider.getIfAvailable();
      if (reader == null) return new CountObservation(0L, false, "MODULE_DISABLED");
      return new CountObservation(query.applyAsLong(reader), true, null);
    } catch (RuntimeException exception) {
      LOG.warn("Home cockpit source unavailable: {}", source, exception);
      return new CountObservation(0L, false, "QUERY_UNAVAILABLE");
    }
  }

  public record CockpitResponse(HeaderStats header) {}

  /** Legacy numeric fields retain their fallback; consumers must use availability to interpret them. */
  public record HeaderStats(long dataSourceCount, long runningCount,
      boolean dataSourceAvailable, boolean runningAvailable, LocalDateTime observedAt,
      Map<String, CountObservation> sources) {}

  public record CountObservation(long value, boolean available, String unavailableReason) {}
}
