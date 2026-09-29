package io.yak.ops.business.quality.dao;

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
import java.util.List;
import java.util.Map;

/** 质量工作台与总览聚合查询数据访问边界。 */
public interface QualityAnalyticsDao {
  WorkspaceStatsRow selectStats(long monitorId);
  ReportOverviewRow selectOverview(Map<String, Object> params);
  List<DimensionReportRow> selectDimensions(Map<String, Object> params);
  List<TrendPointRow> selectTrend(Map<String, Object> params);
  List<ColumnReportRow> selectColumns(Map<String, Object> params);
  long countOperationLogs(long monitorId);
  List<OperationLogRow> selectOperationLogs(Map<String, Object> params);

  StatsRow selectHomeOverviewStats(Map<String, Object> params);
  AnalyticsStatsRow selectOverviewAnalyticsStats(Map<String, Object> params);
  List<DimensionRow> selectHomeOverviewDimensions(Map<String, Object> params);
  List<TrendRow> selectOverviewAnalyticsTrend(Map<String, Object> params);
  List<IssueRow> selectHomeOverviewIssues(Map<String, Object> params);
}
