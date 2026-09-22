package io.yak.ops.business.home.quality;

import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 首页数据质量只读聚合。 */
@Component
public class HomeQualityOverviewReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(HomeQualityOverviewReader.class);

  private final ObjectProvider<QualityOverviewReader> readerProvider;

  public HomeQualityOverviewReader(ObjectProvider<QualityOverviewReader> readerProvider) {
    this.readerProvider = readerProvider;
  }

  public OverviewResponse overview() {
    QualityOverviewReader reader = readerProvider.getIfAvailable();
    if (reader == null) return unavailable();
    try {
      return response(reader.overview());
    } catch (RuntimeException exception) {
      LOGGER.warn("加载首页数据质量总览失败", exception);
      return unavailable();
    }
  }

  private OverviewResponse response(QualityOverviewReader.Overview overview) {
    return new OverviewResponse(
        true,
        overview.rangeStart(),
        overview.rangeEnd(),
        overview.passRate(),
        overview.monitoredTableCount(),
        overview.enabledRuleCount(),
        overview.todayExecutionCount(),
        overview.todayIssueTableCount(),
        overview.recentIssueCount(),
        overview.dimensions().stream()
            .map(item -> new DimensionView(
                item.dimension(), item.total(), item.issues(), item.passRate()))
            .toList(),
        overview.recentIssues().stream()
            .map(item -> new IssueView(
                item.id(),
                item.executionNo(),
                item.monitorId(),
                item.monitorName(),
                item.objectName(),
                item.tableName(),
                item.ruleName(),
                item.dimension(),
                item.columnName(),
                item.checkResult(),
                item.queuedAt()))
            .toList());
  }

  private OverviewResponse unavailable() {
    return new OverviewResponse(
        false, null, null, null, null, null, null, null, null, List.of(), List.of());
  }

  public record OverviewResponse(
      boolean available,
      LocalDate rangeStart,
      LocalDate rangeEnd,
      Double passRate,
      Long monitoredTableCount,
      Long enabledRuleCount,
      Long todayExecutionCount,
      Long todayIssueTableCount,
      Long recentIssueCount,
      List<DimensionView> dimensions,
      List<IssueView> recentIssues) {
  }

  public record DimensionView(
      String dimension,
      long total,
      long issues,
      Double passRate) {
  }

  public record IssueView(
      String id,
      String executionNo,
      String monitorId,
      String monitorName,
      String objectName,
      String tableName,
      String ruleName,
      String dimension,
      String columnName,
      String checkResult,
      LocalDateTime queuedAt) {
  }
}
