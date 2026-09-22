package io.yak.ops.business.quality.dao.mapper;

import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.AlertEventRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ColumnReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.DimensionReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.FolderRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.MonitorRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.OperationLogRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ReportOverviewRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableAssetRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TemplateRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TrendPointRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.WorkspaceStatsRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 质量模块复杂只读查询 Mapper；普通单表 CRUD 仍由 BaseMapper 承担。 */
@Mapper
public interface QualityQueryMapper {
  List<TemplateRow> selectTemplates(Map<String, Object> params);
  TemplateRow selectTemplateById(@Param("id") long id, @Param("customOnly") boolean customOnly);
  long countSystemTemplates();
  List<FolderRow> selectFolders();

  long countMonitors(Map<String, Object> params);
  List<MonitorRow> selectMonitors(Map<String, Object> params);
  MonitorRow selectMonitor(@Param("projectId") long projectId, @Param("id") long id);
  List<QualityMonitorPO> selectScheduledMonitorsForRecovery();
  List<TableMonitorSummaryRow> selectTableSummaries(Map<String, Object> params);
  List<AlertEventRow> selectRecentAlertEvents(
      @Param("projectId") long projectId, @Param("limit") int limit);
  long countAlertEventsSince(
      @Param("projectId") long projectId, @Param("since") LocalDateTime since);

  long countTableAssets(Map<String, Object> params);
  List<TableAssetRow> selectTableAssets(Map<String, Object> params);
  int countMonitorsForAsset(
      @Param("projectId") long projectId,
      @Param("assetId") long assetId);

  long countExecutions(Map<String, Object> params);
  List<QualityExecutionPO> selectExecutions(Map<String, Object> params);
  long countExecutionWorkspace(Map<String, Object> params);
  List<QualityExecutionPO> selectExecutionWorkspace(Map<String, Object> params);
  long countRuleExecutionWorkspace(Map<String, Object> params);
  List<RuleExecutionWorkspaceRow> selectRuleExecutionWorkspace(Map<String, Object> params);

  WorkspaceStatsRow selectWorkspaceStats(
      @Param("projectId") long projectId,
      @Param("monitorId") long monitorId);
  ReportOverviewRow selectReportOverview(Map<String, Object> params);
  List<DimensionReportRow> selectDimensionReport(Map<String, Object> params);
  List<TrendPointRow> selectTrend(Map<String, Object> params);
  List<ColumnReportRow> selectColumnReport(Map<String, Object> params);
  long countOperationLogs(
      @Param("projectId") long projectId,
      @Param("monitorId") long monitorId);
  List<OperationLogRow> selectOperationLogs(Map<String, Object> params);
}
