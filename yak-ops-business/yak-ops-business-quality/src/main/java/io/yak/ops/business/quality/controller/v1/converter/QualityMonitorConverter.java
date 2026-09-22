package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEvent;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.business.quality.monitor.QualityMonitorCommand;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.common.bean.dto.quality.QualityMonitorDTO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QualityMonitorConverter {
  public QualityQuery.Monitor query(QualityMonitorDTO.PageRequest request) {
    QualityMonitorDTO.PageRequest value = request == null ? new QualityMonitorDTO.PageRequest(1, 20, null, null, null, null, null, null, null) : request;
    return new QualityQuery.Monitor(value.normalizedCurrent(), value.normalizedPageSize(), value.keyword(), value.dataSourceId(), value.databaseName(), value.databaseName() != null, value.schemaName(), value.schemaName() != null, value.tableName(), value.enabled(), value.lastResult());
  }

  public QualityMonitorCommand.Save command(QualityMonitorDTO.SaveRequest request) {
    QualityMonitorCommand.Settings settings = request.settings() == null ? null : new QualityMonitorCommand.Settings(
        request.settings().runMode(),
        request.settings().scheduleFrequency(),
        request.settings().scheduleTime(),
        request.settings().scheduleWeekday(),
        request.settings().cronExpression(),
        request.settings().ruleFailureAction(),
        request.settings().notifyEnabled(),
        request.settings().notifyChannel(),
        request.settings().notifyTarget(),
        request.settings().alertLevel(),
        request.settings().scheduleEnabled());
    List<QualityMonitorCommand.Rule> rules = request.rules().stream().map(rule -> new QualityMonitorCommand.Rule(rule.templateId(), rule.name(), rule.columnName(), rule.operator(), rule.threshold(), rule.thresholdEnd(), rule.enumValues(), rule.customSql(), rule.enabled())).toList();
    return new QualityMonitorCommand.Save(request.name(), request.description(), request.dataSourceId(), request.dataSourceName(), request.databaseName(), request.schemaName(), request.tableName(), request.whereClause(), request.owner(), request.enabled(), settings, rules);
  }

  public QualityMonitorVO.Page page(PageData<Monitor> page, QualityQuery.Monitor query) {
    return new QualityMonitorVO.Page(page.records().stream().map(this::listItem).toList(), page.total(), query.current(), query.pageSize());
  }

  public QualityMonitorVO.Detail detail(Monitor value) {
    return new QualityMonitorVO.Detail(value.id(), value.name(), value.description(), value.dataSourceId(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.whereClause(), value.owner(), value.enabled(), value.lastResult(), value.lastExecutionNo(), value.lastRunTime(), value.createTime(), value.updateTime(), value.rules().stream().map(this::rule).toList());
  }

  public QualityMonitorVO.Settings settings(MonitorSettings value) {
    return new QualityMonitorVO.Settings(
        value.runMode(),
        value.scheduleFrequency(),
        value.scheduleTime(),
        value.scheduleWeekday(),
        value.cronExpression(),
        value.nextRunTime(),
        value.ruleFailureAction(),
        value.notifyEnabled(),
        value.notifyChannel(),
        value.notifyTarget(),
        value.alertLevel(),
        value.runMode() == RunMode.SCHEDULE);
  }

  public List<QualityMonitorVO.TableSummary> tableSummaries(List<TableMonitorSummary> values) {
    return values.stream().map(value -> new QualityMonitorVO.TableSummary(value.tableName(), value.monitorId(), value.monitorName(), value.monitorCount(), value.ruleCount(), value.lastResult(), value.lastRunTime())).toList();
  }

  public QualityMonitorVO.AlertOverview alertOverview(QualityMonitorReader.AlertOverview value) {
    return new QualityMonitorVO.AlertOverview(value.last24hCount(),
        value.recent().stream().map(this::alertEvent).toList());
  }

  public QualityMonitorVO.AlertEvent alertEvent(AlertEvent value) {
    return new QualityMonitorVO.AlertEvent(value.id(), value.monitorId(), value.monitorName(),
        value.executionNo(), value.checkResult(), value.alertLevel(), value.notifyChannel(),
        value.deliveryStatus(), value.alertMessage(), value.createdAt());
  }

  public QualityMonitorVO.Run run(QualityExecutionReceipt receipt) {
    return new QualityMonitorVO.Run(receipt.executionNo(), receipt.executionStatus(), receipt.checkResult());
  }

  private QualityMonitorVO.ListItem listItem(Monitor value) {
    return new QualityMonitorVO.ListItem(value.id(), value.name(), value.description(), value.dataSourceId(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.owner(), value.enabled(), value.ruleCount(), value.lastResult(), value.lastExecutionNo(), value.lastRunTime(), value.createTime(), value.updateTime());
  }

  private QualityMonitorVO.Rule rule(Rule value) {
    return new QualityMonitorVO.Rule(value.id(), value.monitorId(), value.templateId(), value.templateCode(), value.name(), value.ruleType(), value.scope(), value.dimension(), value.columnName(), value.operator(), value.threshold(), value.thresholdEnd(), value.enumValues(), value.customSql(), value.enabled(), value.sortOrder());
  }
}
