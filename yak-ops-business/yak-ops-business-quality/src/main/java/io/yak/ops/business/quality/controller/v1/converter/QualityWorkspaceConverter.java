package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.ops.business.quality.domain.QualityDomain.OperationLog;
import io.yak.ops.business.quality.workspace.QualityWorkspaceReader.MonitorReport;
import io.yak.ops.business.quality.workspace.QualityWorkspaceReader.MonitorWorkspace;
import io.yak.ops.business.quality.workspace.QualityWorkspaceReader.OperationLogPage;
import io.yak.ops.common.bean.vo.quality.QualityWorkspaceVO;
import org.springframework.stereotype.Component;

@Component
public class QualityWorkspaceConverter {
  private final QualityMonitorConverter monitorConverter;

  public QualityWorkspaceConverter(QualityMonitorConverter monitorConverter) {
    this.monitorConverter = monitorConverter;
  }

  public QualityWorkspaceVO.MonitorWorkspace workspace(MonitorWorkspace value) {
    var stats = value.stats();
    return new QualityWorkspaceVO.MonitorWorkspace(
        monitorConverter.detail(value.monitor()),
        monitorConverter.settings(value.settings()),
        new QualityWorkspaceVO.Stats(
            stats.ruleCount(), stats.enabledRuleCount(), stats.executionCount(),
            stats.issueExecutionCount(), stats.latestExecutionTime()));
  }

  public QualityWorkspaceVO.MonitorReport report(MonitorReport value) {
    var overview = value.overview();
    return new QualityWorkspaceVO.MonitorReport(
        value.reportDate(),
        value.trendStart(),
        new QualityWorkspaceVO.ReportOverview(
            overview.totalRules(), overview.enabledRules(), overview.executedRules(),
            overview.issueRules(), overview.errorRules(), overview.passRate()),
        value.dimensions().stream().map(v -> new QualityWorkspaceVO.DimensionReport(
            v.dimension(), v.total(), v.passed(), v.notPassed(), v.errors(), v.passRate())).toList(),
        value.trend().stream().map(v -> new QualityWorkspaceVO.TrendPoint(
            v.date(), v.dimension(), v.total(), v.passed(), v.issues(), v.passRate())).toList(),
        value.columns().stream().map(v -> new QualityWorkspaceVO.ColumnReport(
            v.columnName(), v.dimension(), v.total(), v.passed(), v.issues(), v.passRate())).toList());
  }

  public QualityWorkspaceVO.OperationLogPage operationLogs(OperationLogPage page) {
    return new QualityWorkspaceVO.OperationLogPage(
        page.records().stream().map(this::operationLog).toList(),
        page.total(), page.current(), page.pageSize());
  }

  private QualityWorkspaceVO.OperationLogItem operationLog(OperationLog value) {
    return new QualityWorkspaceVO.OperationLogItem(
        value.id(), value.operator(), value.operationTime(), value.actionType(), value.content());
  }
}
