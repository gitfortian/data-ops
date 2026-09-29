package io.yak.ops.business.quality.dao.impl;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.dao.mapper.QualityOverviewMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.AnalyticsStatsRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.DimensionRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.IssueRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.StatsRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.TrendRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ColumnReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.DimensionReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.OperationLogRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ReportOverviewRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TrendPointRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.WorkspaceStatsRow;
import io.yak.ops.core.project.CurrentProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityAnalyticsDaoImpl implements QualityAnalyticsDao {
  private final QualityQueryMapper queryMapper;
  private final QualityOverviewMapper overviewMapper;
  private final CurrentProject currentProject;

  @Override
  public WorkspaceStatsRow selectStats(long monitorId) {
    return queryMapper.selectWorkspaceStats(currentProjectId(), monitorId);
  }

  @Override
  public ReportOverviewRow selectOverview(Map<String, Object> params) {
    return queryMapper.selectReportOverview(scoped(params));
  }

  @Override
  public List<DimensionReportRow> selectDimensions(Map<String, Object> params) {
    return queryMapper.selectDimensionReport(scoped(params));
  }

  @Override
  public List<TrendPointRow> selectTrend(Map<String, Object> params) {
    return queryMapper.selectTrend(scoped(params));
  }

  @Override
  public List<ColumnReportRow> selectColumns(Map<String, Object> params) {
    return queryMapper.selectColumnReport(scoped(params));
  }

  @Override
  public long countOperationLogs(long monitorId) {
    return queryMapper.countOperationLogs(currentProjectId(), monitorId);
  }

  @Override
  public List<OperationLogRow> selectOperationLogs(Map<String, Object> params) {
    return queryMapper.selectOperationLogs(scoped(params));
  }

  @Override
  public StatsRow selectHomeOverviewStats(Map<String, Object> params) {
    return overviewMapper.selectStats(scoped(params));
  }

  @Override
  public AnalyticsStatsRow selectOverviewAnalyticsStats(Map<String, Object> params) {
    return overviewMapper.selectAnalyticsStats(scoped(params));
  }

  @Override
  public List<DimensionRow> selectHomeOverviewDimensions(Map<String, Object> params) {
    return overviewMapper.selectDimensions(scoped(params));
  }

  @Override
  public List<TrendRow> selectOverviewAnalyticsTrend(Map<String, Object> params) {
    return overviewMapper.selectAnalyticsTrend(scoped(params));
  }

  @Override
  public List<IssueRow> selectHomeOverviewIssues(Map<String, Object> params) {
    return overviewMapper.selectRecentIssues(scoped(params));
  }

  private Map<String, Object> scoped(Map<String, Object> params) {
    Map<String, Object> scoped = new LinkedHashMap<>();
    if (params != null) scoped.putAll(params);
    scoped.put("projectId", currentProjectId());
    return scoped;
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }
}
