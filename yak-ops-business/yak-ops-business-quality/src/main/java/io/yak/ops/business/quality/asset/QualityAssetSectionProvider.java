package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Quality-owned Asset Detail read adapter; all underlying facts remain Quality-owned. */
@Component
@ConditionalOnQualityEnabled
public class QualityAssetSectionProvider implements SectionProvider {

  private final QualityTableAssetReader tableAssetReader;
  private final QualityMonitorReader monitorReader;
  private final QualityExecutionReader executionReader;

  public QualityAssetSectionProvider(
      QualityTableAssetReader tableAssetReader,
      QualityMonitorReader monitorReader,
      QualityExecutionReader executionReader) {
    this.tableAssetReader = tableAssetReader;
    this.monitorReader = monitorReader;
    this.executionReader = executionReader;
  }

  @Override
  public SectionType sectionType() {
    return SectionType.QUALITY;
  }

  @Override
  public boolean supports(SectionContext context) {
    return "METADATA".equals(context.sourceType())
        && context.attributes().containsKey("dataSourceId");
  }

  @Override
  public SectionContract query(SectionContext context) {
    long dataSourceId = Long.parseLong(context.attributes().get("dataSourceId"));
    String database = context.attributes().get("databaseName");
    String schema = context.attributes().get("schemaName");
    String table = context.attributes().get("tableName");
    boolean registered = tableAssetReader.isRegistered(dataSourceId, database, schema, table);
    List<TableMonitorSummary> tableSummaries = monitorReader
        .tableSummaries(dataSourceId, database, schema).stream()
        .filter(summary -> table.equalsIgnoreCase(summary.tableName()))
        .toList();
    TableMonitorSummary targetSummary = tableSummaries.stream().findFirst().orElse(null);
    Execution latest = targetSummary == null || targetSummary.lastExecutionNo() == null
        ? null : executionReader.findSummary(targetSummary.lastExecutionNo()).orElse(null);

    Map<String, Object> values = new LinkedHashMap<>();
    values.put("registered", registered);
    values.put("monitorCount", targetSummary == null ? 0 : targetSummary.monitorCount());
    values.put("enabledMonitorCount", targetSummary == null ? 0 : targetSummary.enabledMonitorCount());
    values.put("latestExecution", latest == null ? Map.of("status", "NOT_RUN") : Map.of(
        "executionNo", latest.executionNo(),
        "lifecycleStatus", latest.executionStatus().name(),
        "result", latest.checkResult() == null ? "UNKNOWN" : latest.checkResult().name(),
        "issueCount", latest.failedRules() + latest.errorRules(),
        "queuedAt", latest.queuedAt() == null ? "" : latest.queuedAt().toString(),
        "finishedAt", latest.finishedAt() == null ? "" : latest.finishedAt().toString()));

    SectionStatus status = targetSummary != null ? SectionStatus.OK : SectionStatus.EMPTY;
    String reason = status == SectionStatus.EMPTY
        ? (registered ? "该物理表已纳入质量管理，尚未配置监控" : "该物理表尚未纳入质量管理")
        : null;
    Instant observedAt = Instant.now();
    String reference = latest == null ? context.assetKey() : latest.executionNo();
    return new QualityAssetSectionResult(
        status,
        new SectionMapSummary(values),
        reason,
        latest == null || latest.finishedAt() == null ? null
            : latest.finishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
        List.of(new SectionAction("查看质量监控", "/data-quality/table-config", context.assetKey()),
            new SectionAction("查看质量运行记录",
                latest == null ? "/data-quality/execution"
                    : "/data-quality/execution/" + latest.executionNo(), reference)),
        latest == null ? List.of()
            : List.of(new SectionEvidence("QUALITY", latest.executionNo(), observedAt)),
        new SectionProvenance("QUALITY", reference, observedAt),
        new SectionCapability(true, true, null));
  }
}
