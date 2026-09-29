package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reads a bounded Quality projection for exactly one physical table and current Project. */
@Component
@ConditionalOnQualityEnabled
public class QualityAssetSectionSummaryReader {

  private final QualityTableAssetReader tableAssetReader;
  private final QualityMonitorReader monitorReader;
  private final QualityExecutionReader executionReader;

  public QualityAssetSectionSummaryReader(
      QualityTableAssetReader tableAssetReader,
      QualityMonitorReader monitorReader,
      QualityExecutionReader executionReader) {
    this.tableAssetReader = tableAssetReader;
    this.monitorReader = monitorReader;
    this.executionReader = executionReader;
  }

  public QualityAssetSectionSummary read(
      long dataSourceId, String database, String schema, String table) {
    boolean registered = tableAssetReader.isRegistered(dataSourceId, database, schema, table);
    List<TableMonitorSummary> matches = monitorReader.tableSummaries(
        dataSourceId, database, schema, table);
    TableMonitorSummary target = matches.stream().findFirst().orElse(null);
    Execution latest = executionReader
        .findLatestForTarget(dataSourceId, database, schema, table).orElse(null);

    return new QualityAssetSectionSummary(
        registered,
        target == null ? null : target.monitorId(),
        target == null ? 0 : target.monitorCount(),
        target == null ? 0 : target.enabledMonitorCount(),
        latest == null ? null : new QualityAssetSectionSummary.LatestExecution(
            latest.executionNo(),
            latest.executionStatus().name(),
            latest.checkResult() == null ? "UNKNOWN" : latest.checkResult().name(),
            latest.failedRules() + latest.errorRules(),
            latest.queuedAt(),
            latest.finishedAt()));
  }
}
