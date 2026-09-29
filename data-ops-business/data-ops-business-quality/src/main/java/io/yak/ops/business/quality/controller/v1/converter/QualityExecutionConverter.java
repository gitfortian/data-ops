package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.bean.dto.quality.QualityExecutionDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionVO;
import org.springframework.stereotype.Component;

@Component
public class QualityExecutionConverter {
  public QualityQuery.Execution query(QualityExecutionDTO.PageRequest request) {
    QualityExecutionDTO.PageRequest value = request == null ? new QualityExecutionDTO.PageRequest(1, 20, null, null, null, null) : request;
    return new QualityQuery.Execution(value.normalizedCurrent(), value.normalizedPageSize(), value.keyword(), value.monitorId(), value.executionStatus(), value.checkResult());
  }
  public QualityExecutionVO.Page page(PageData<Execution> page, QualityQuery.Execution query) { return new QualityExecutionVO.Page(page.records().stream().map(this::listItem).toList(), page.total(), query.current(), query.pageSize()); }
  public QualityExecutionVO.Status status(Execution value) { return new QualityExecutionVO.Status(value.executionNo(), value.executionStatus(), value.checkResult(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage()); }
  public QualityExecutionVO.Detail detail(Execution value) { return new QualityExecutionVO.Detail(value.executionNo(), value.monitorId(), value.monitorName(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.objectName(), value.executionStatus(), value.checkResult(), value.totalRules(), value.passedRules(), value.failedRules(), value.errorRules(), value.operator(), value.queuedAt(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage(), value.rules().stream().map(this::ruleExecution).toList()); }
  private QualityExecutionVO.ListItem listItem(Execution value) { return new QualityExecutionVO.ListItem(value.executionNo(), value.monitorId(), value.monitorName(), value.dataSourceName(), value.objectName(), value.executionStatus(), value.checkResult(), value.totalRules(), value.passedRules(), value.failedRules(), value.errorRules(), value.operator(), value.queuedAt(), value.startedAt(), value.finishedAt(), value.durationMs(), value.errorMessage()); }
  private QualityExecutionVO.RuleExecution ruleExecution(RuleExecution value) { return new QualityExecutionVO.RuleExecution(value.id(), value.ruleId(), value.ruleName(), value.templateCode(), value.ruleType(), value.columnName(), value.checkResult(), value.metricValue(), value.expectedValue(), value.executedSql(), value.errorMessage(), value.durationMs()); }
}
