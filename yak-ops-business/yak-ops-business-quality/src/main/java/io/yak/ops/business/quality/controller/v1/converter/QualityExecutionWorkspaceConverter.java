package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.workspace.QualityExecutionLogProjector.StructuredLog;
import io.yak.ops.common.bean.dto.quality.QualityExecutionWorkspaceDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import org.springframework.stereotype.Component;

@Component
public class QualityExecutionWorkspaceConverter {
  public QualityQuery.ExecutionWorkspace query(QualityExecutionWorkspaceDTO.PageRequest request) {
    QualityExecutionWorkspaceDTO.PageRequest value = request == null ? new QualityExecutionWorkspaceDTO.PageRequest(1, 20, null, null, null, null, null, null, null, null, null, null, null, null) : request;
    return new QualityQuery.ExecutionWorkspace(value.normalizedCurrent(), value.normalizedPageSize(), value.keyword(), value.objectKeyword(), value.dataSourceId(), value.monitorId(), value.executionStatus(), value.checkResult(), value.triggerType(), value.hasIssues(), value.dimension(), value.scope(), value.queuedAfter(), value.queuedBefore());
  }
  public QualityExecutionWorkspaceVO.ExecutionPage page(PageData<Execution> page, QualityQuery.ExecutionWorkspace query) { return new QualityExecutionWorkspaceVO.ExecutionPage(page.records().stream().map(this::executionList).toList(), page.total(), query.current(), query.pageSize()); }
  public QualityExecutionWorkspaceVO.RuleExecutionPage pageRules(PageData<RuleExecutionWorkspaceItem> page, QualityQuery.ExecutionWorkspace query) { return new QualityExecutionWorkspaceVO.RuleExecutionPage(page.records().stream().map(this::ruleWorkspace).toList(), page.total(), query.current(), query.pageSize()); }
  public QualityExecutionWorkspaceVO.ExecutionDetail detail(Execution value) { return new QualityExecutionWorkspaceVO.ExecutionDetail(value.executionNo(), value.monitorId(), value.monitorName(), value.dataSourceId(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.objectName(), value.triggerType(), value.executionStatus(), value.checkResult(), value.totalRules(), value.passedRules(), value.failedRules(), value.errorRules(), value.operator(), value.queuedAt(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage(), value.rules().stream().map(this::ruleExecution).toList()); }
  public QualityExecutionWorkspaceVO.LogView logs(StructuredLog value) { return new QualityExecutionWorkspaceVO.LogView(value.executionNo(), value.lines().stream().map(line -> new QualityExecutionWorkspaceVO.LogLine(line.time(), line.level(), line.stage(), line.message())).toList()); }
  private QualityExecutionWorkspaceVO.ExecutionListItem executionList(Execution value) { return new QualityExecutionWorkspaceVO.ExecutionListItem(value.executionNo(), value.monitorId(), value.monitorName(), value.dataSourceId(), value.dataSourceName(), value.objectName(), value.triggerType(), value.executionStatus(), value.checkResult(), value.totalRules(), value.passedRules(), value.failedRules(), value.errorRules(), value.operator(), value.queuedAt(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage()); }
  private QualityExecutionWorkspaceVO.RuleExecutionListItem ruleWorkspace(RuleExecutionWorkspaceItem value) { return new QualityExecutionWorkspaceVO.RuleExecutionListItem(value.id(), value.ruleId(), value.executionNo(), value.monitorId(), value.monitorName(), value.dataSourceId(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.objectName(), value.ruleName(), value.templateCode(), value.ruleType(), value.scope(), value.dimension(), value.columnName(), value.triggerType(), value.executionStatus(), value.checkResult(), value.metricValue(), value.expectedValue(), value.operator(), value.queuedAt(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage()); }
  private QualityExecutionWorkspaceVO.RuleExecution ruleExecution(RuleExecution value) { return new QualityExecutionWorkspaceVO.RuleExecution(value.id(), value.ruleId(), value.ruleName(), value.templateCode(), value.ruleType(), value.ruleType().scope(), value.ruleType().dimension(), value.columnName(), value.checkResult(), value.metricValue(), value.expectedValue(), value.executedSql(), value.errorMessage(), value.durationMs(), value.createdAt()); }
}
