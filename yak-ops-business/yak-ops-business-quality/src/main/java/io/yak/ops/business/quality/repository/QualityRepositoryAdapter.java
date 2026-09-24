package io.yak.ops.business.quality.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEvent;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEventSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionSpec;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetTarget;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.AlertEventRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.MonitorRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableAssetRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TemplateRow;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus Repository Adapter：负责 PO/查询投影与领域模型之间的转换。 */
@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityRepositoryAdapter implements
    QualityTemplateRepository,
    QualityTableAssetRepository,
    QualityMonitorRepository,
    QualityExecutionRepository,
    QualityAlertRepository {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final QualityCatalogDao catalogDao;
  private final QualityMonitorDao monitorDao;
  private final QualityExecutionDao executionDao;
  private final ObjectMapper objectMapper;

  @Override
  public List<Template> listTemplates(QualityQuery.Template query) {
    Map<String, Object> params = new LinkedHashMap<>();
    if (query != null) {
      putLike(params, "keyword", query.keyword());
      putText(params, "dimension", query.dimension());
      if (query.scope() != null) params.put("scope", query.scope().name());
    }
    params.put("customOnly", false);
    return catalogDao.selectTemplates(params).stream().map(this::template).toList();
  }

  @Override
  public Optional<Template> findTemplate(long id) {
    return Optional.ofNullable(catalogDao.selectTemplate(id, false)).map(this::template);
  }

  @Override
  public PageData<TableAsset> pageTableAssets(QualityQuery.TableAsset query) {
    Map<String, Object> params = tableAssetParams(query);
    long total = monitorDao.countTableAssets(params);
    params.put("limit", query.pageSize());
    params.put("offset", (query.current() - 1L) * query.pageSize());
    List<TableAsset> records = monitorDao.selectTableAssets(params).stream().map(this::tableAsset).toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public List<TableAssetTarget> listTableAssetTargets(long dataSourceId, String databaseName) {
    return monitorDao.selectTableAssetTargets(dataSourceId, blankToNull(databaseName)).stream()
        .map(po -> new TableAssetTarget(blankToNull(po.getDatabaseName()), blankToNull(po.getSchemaName()), po.getTableName()))
        .toList();
  }

  @Override
  public boolean existsTableAssetTarget(long dataSourceId, String databaseName, String schemaName, String tableName) {
    return monitorDao.existsTableAssetTarget(
        dataSourceId, blankToEmpty(databaseName), blankToEmpty(schemaName), tableName);
  }

  @Override
  public int registerTableAssets(List<TableAssetSpec> assets) {
    return monitorDao.upsertTableAssets(assets.stream().map(this::tableAssetPO).toList());
  }

  @Override public int countMonitorsForTableAsset(long assetId) { return monitorDao.countMonitorsForAsset(assetId); }
  @Override public boolean deleteTableAsset(long assetId) { return monitorDao.softDeleteTableAsset(assetId); }

  @Override
  public PageData<Monitor> pageMonitors(QualityQuery.Monitor query) {
    Map<String, Object> params = monitorParams(query);
    long total = monitorDao.countMonitors(params);
    params.put("limit", query.pageSize());
    params.put("offset", (query.current() - 1L) * query.pageSize());
    List<Monitor> records = monitorDao.selectMonitors(params).stream()
        .map(row -> monitor(row, List.of())).toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public Optional<Monitor> findMonitor(long id) {
    MonitorRow row = monitorDao.selectMonitor(id);
    return row == null ? Optional.empty() : Optional.of(monitor(row, listRules(id)));
  }

  @Override
  public List<TableMonitorSummary> tableSummaries(long dataSourceId, String databaseName, String schemaName) {
    return tableSummaries(dataSourceId, databaseName, schemaName, null);
  }

  @Override
  public List<TableMonitorSummary> tableSummaries(
      long dataSourceId, String databaseName, String schemaName, String tableName) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("dataSourceId", dataSourceId);
    if (tableName == null) {
      addNullableFilter(params, "database", databaseName);
      addNullableFilter(params, "schema", schemaName);
    } else {
      params.put("databaseFilter", true);
      params.put("databaseName", blankToEmpty(databaseName));
      params.put("schemaFilter", true);
      params.put("schemaName", blankToEmpty(schemaName));
      params.put("tableName", tableName);
    }
    return monitorDao.selectTableSummaries(params).stream().map(this::tableSummary).toList();
  }

  @Override
  public boolean existsMonitorForTarget(Long excludeId, long dataSourceId, String databaseName, String schemaName, String tableName) {
    return monitorDao.existsMonitorTarget(excludeId, dataSourceId, blankToNull(databaseName), blankToNull(schemaName), tableName);
  }

  @Override public long insertMonitor(MonitorSpec monitor) { return monitorDao.insertMonitor(monitorPO(null, monitor)); }
  @Override public boolean updateMonitor(long id, MonitorSpec monitor) { return monitorDao.updateMonitor(monitorPO(id, monitor)); }
  @Override public boolean deleteMonitor(long id) { return monitorDao.softDeleteMonitor(id); }

  @Override
  public MonitorSettings findMonitorSettings(long monitorId) {
    QualityMonitorSettingPO po = monitorDao.selectSetting(monitorId);
    return po == null ? defaultSettings() : settings(po);
  }

  @Override public void upsertMonitorSettings(long monitorId, MonitorSettingsSpec settings) { monitorDao.upsertSetting(settingPO(monitorId, settings)); }
  @Override public void insertAlertEvent(AlertEventSpec alert) { monitorDao.insertAlert(alertPO(alert)); }

  @Override
  public List<AlertEvent> recentAlertEvents(int limit) {
    return monitorDao.selectRecentAlertEvents(limit).stream().map(this::alertEvent).toList();
  }

  @Override
  public long countAlertEventsSince(LocalDateTime since) {
    return monitorDao.countAlertEventsSince(since);
  }

  @Override
  public void replaceRules(long monitorId, List<RuleSpec> rules) {
    int sort = 10;
    List<QualityRulePO> rows = new ArrayList<>();
    for (RuleSpec rule : rules) {
      rows.add(rulePO(monitorId, rule, sort));
      sort += 10;
    }
    monitorDao.replaceRules(monitorId, rows);
  }

  @Override public List<Rule> listRules(long monitorId) { return monitorDao.selectRules(monitorId).stream().map(this::rule).toList(); }
  @Override public void lockMonitor(long monitorId) { monitorDao.lockMonitor(monitorId); }
  @Override public boolean hasActiveExecution(long monitorId) { return executionDao.hasActive(monitorId); }

  @Override
  public long insertExecution(String executionNo, Monitor monitor, int totalRules, String operator,
      TriggerType triggerType, LocalDateTime queuedAt) {
    QualityExecutionPO po = new QualityExecutionPO();
    po.setExecutionNo(executionNo); po.setMonitorId(monitor.id()); po.setMonitorName(monitor.name());
    po.setDataSourceId(monitor.dataSourceId()); po.setDataSourceName(monitor.dataSourceName());
    po.setDatabaseName(monitor.databaseName()); po.setSchemaName(monitor.schemaName()); po.setTableName(monitor.tableName());
    po.setObjectName(objectName(monitor.databaseName(), monitor.schemaName(), monitor.tableName()));
    po.setTriggerType(triggerType.name()); po.setExecutionStatus("WAITING"); po.setCheckResult("RUNNING");
    po.setTotalRules(totalRules); po.setPassedRules(0); po.setFailedRules(0); po.setErrorRules(0);
    po.setOperatorName(operator); po.setQueuedAt(queuedAt);
    return executionDao.insertExecution(po);
  }

  @Override public boolean markExecutionRunning(long id, LocalDateTime startedAt) { return executionDao.markRunning(id, startedAt); }
  @Override public void insertRuleExecution(RuleExecutionSpec value) { executionDao.insertRuleExecution(ruleExecutionPO(value)); }
  @Override public boolean completeExecution(long id, CheckResult result, int passed, int failed, int errors, LocalDateTime finishedAt, long durationMs) { return executionDao.complete(id, result.name(), passed, failed, errors, finishedAt, durationMs); }
  @Override public boolean failExecution(long id, String errorMessage, LocalDateTime finishedAt, long durationMs) { return executionDao.fail(id, errorMessage, finishedAt, durationMs); }
  @Override public boolean updateMonitorResult(long monitorId, String executionNo, CheckResult result, LocalDateTime runTime) { return monitorDao.updateMonitorResult(monitorId, executionNo, result.name(), runTime); }

  @Override
  public PageData<Execution> pageExecutions(QualityQuery.Execution query) {
    Map<String, Object> params = executionParams(query);
    long total = executionDao.countExecutions(params);
    params.put("limit", query.pageSize());
    params.put("offset", (query.current() - 1L) * query.pageSize());
    List<Execution> records = executionDao.selectExecutions(params).stream()
        .map(po -> execution(po, List.of())).toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public Optional<Execution> findExecution(String executionNo) {
    QualityExecutionPO po = executionDao.selectByExecutionNo(executionNo);
    if (po == null) return Optional.empty();
    List<RuleExecution> rules = executionDao.selectRuleExecutions(po.getId()).stream().map(this::ruleExecution).toList();
    return Optional.of(execution(po, rules));
  }

  private Template template(TemplateRow row) {
    return new Template(row.getId(), row.getTemplateCode(), row.getTemplateName(), row.getDescription(),
        RuleType.valueOf(row.getRuleType()), RuleScope.valueOf(row.getRuleScope()), row.getQualityDimension(),
        row.getParameterSchemaJson(), Boolean.TRUE.equals(row.getBuiltin()), Boolean.TRUE.equals(row.getEnabled()),
        nvl(row.getRuleCount()), nvl(row.getSortOrder()));
  }

  private Monitor monitor(MonitorRow row, List<Rule> rules) {
    return new Monitor(row.getId(), row.getMonitorName(), row.getDescription(), row.getDataSourceId(), row.getDataSourceName(),
        row.getDatabaseName(), row.getSchemaName(), row.getTableName(), row.getWhereClause(), row.getOwner(),
        Boolean.TRUE.equals(row.getEnabled()), checkResult(row.getLastResult()), row.getLastExecutionNo(), row.getLastRunTime(),
        row.getCreatedAt(), row.getUpdatedAt(), nvl(row.getRuleCount()), rules);
  }

  private Rule rule(QualityRulePO po) {
    return new Rule(po.getId(), po.getMonitorId(), po.getTemplateId(), po.getTemplateCode(), po.getRuleName(),
        RuleType.valueOf(po.getRuleType()), RuleScope.valueOf(po.getRuleScope()), po.getQualityDimension(), po.getColumnName(),
        po.getComparisonOperator(), po.getThresholdValue(), po.getThresholdEnd(), readJsonList(po.getEnumValuesJson()),
        po.getCustomSql(), Boolean.TRUE.equals(po.getEnabled()), nvl(po.getSortOrder()));
  }

  private MonitorSettings settings(QualityMonitorSettingPO po) {
    return new MonitorSettings(enumValue(RunMode.class, po.getRunMode(), RunMode.MANUAL),
        enumValue(ScheduleFrequency.class, po.getScheduleFrequency()),
        po.getScheduleTime() == null ? null : po.getScheduleTime().format(TIME_FORMATTER),
        enumValue(ScheduleWeekday.class, po.getScheduleWeekday()), po.getCronExpression(), po.getNextRunTime(),
        enumValue(RuleFailureAction.class, po.getRuleFailureAction(), RuleFailureAction.CONTINUE),
        Boolean.TRUE.equals(po.getNotifyEnabled()), enumValue(NotifyChannel.class, po.getNotifyChannel(), NotifyChannel.MESSAGE),
        po.getNotifyTarget(), enumValue(AlertLevel.class, po.getAlertLevel(), AlertLevel.WARNING));
  }

  private MonitorSettings defaultSettings() {
    return new MonitorSettings(RunMode.MANUAL, null, null, null, null, null,
        RuleFailureAction.CONTINUE, false, NotifyChannel.MESSAGE, null, AlertLevel.WARNING);
  }

  private TableAsset tableAsset(TableAssetRow row) {
    return new TableAsset(row.getId(), row.getDataSourceId(), row.getDataSourceName(), blankToNull(row.getDatabaseName()),
        blankToNull(row.getSchemaName()), row.getTableName(), row.getTableType(), row.getRemarks(), row.getMonitorId(),
        row.getMonitorName(), nvl(row.getMonitorCount()), nvl(row.getRuleCount()), checkResult(row.getLastResult()),
        row.getLastRunTime(), row.getRegisteredBy(), row.getRegisteredAt());
  }

  private TableMonitorSummary tableSummary(TableMonitorSummaryRow row) {
    return new TableMonitorSummary(row.getTableName(), row.getMonitorId(), row.getMonitorName(),
        nvl(row.getMonitorCount()), nvl(row.getRuleCount()), nvl(row.getEnabledMonitorCount()),
        row.getLastExecutionNo(), checkResult(row.getLastResult()), row.getLastRunTime());
  }

  private AlertEvent alertEvent(AlertEventRow row) {
    return new AlertEvent(row.getId(), row.getMonitorId(), row.getMonitorName(), row.getExecutionNo(),
        checkResult(row.getCheckResult()),
        enumValue(AlertLevel.class, row.getAlertLevel(), AlertLevel.WARNING),
        enumValue(NotifyChannel.class, row.getNotifyChannel(), NotifyChannel.MESSAGE),
        row.getDeliveryStatus(), row.getAlertMessage(), row.getCreatedAt());
  }

  private Execution execution(QualityExecutionPO po, List<RuleExecution> rules) {
    return new Execution(po.getId(), po.getExecutionNo(), po.getMonitorId(), po.getMonitorName(), po.getDataSourceId(),
        po.getDataSourceName(), po.getDatabaseName(), po.getSchemaName(), po.getTableName(), po.getObjectName(),
        enumValue(TriggerType.class, po.getTriggerType(), TriggerType.MANUAL), ExecutionStatus.valueOf(po.getExecutionStatus()),
        checkResult(po.getCheckResult()), nvl(po.getTotalRules()), nvl(po.getPassedRules()), nvl(po.getFailedRules()),
        nvl(po.getErrorRules()), po.getOperatorName(), po.getQueuedAt(), po.getStartedAt(), po.getFinishedAt(),
        po.getDurationMs(), po.getErrorMessage(), rules);
  }

  private RuleExecution ruleExecution(QualityRuleExecutionPO po) {
    return new RuleExecution(po.getId(), po.getRuleId(), po.getRuleName(), po.getTemplateCode(), RuleType.valueOf(po.getRuleType()),
        po.getColumnName(), checkResult(po.getCheckResult()), po.getMetricValue(), po.getExpectedValue(), po.getExecutedSql(),
        po.getErrorMessage(), po.getDurationMs(), po.getCreatedAt());
  }

  private QualityMonitorPO monitorPO(Long id, MonitorSpec value) {
    QualityMonitorPO po = new QualityMonitorPO(); po.setId(id); po.setMonitorName(value.name()); po.setDescription(value.description());
    po.setDataSourceId(value.dataSourceId()); po.setDataSourceName(value.dataSourceName()); po.setDatabaseName(value.databaseName());
    po.setSchemaName(value.schemaName()); po.setTableName(value.tableName()); po.setWhereClause(value.whereClause());
    po.setOwner(value.owner()); po.setEnabled(value.enabled()); po.setDeleted(false); return po;
  }

  private QualityRulePO rulePO(long monitorId, RuleSpec value, int sortOrder) {
    QualityRulePO po = new QualityRulePO(); po.setMonitorId(monitorId); po.setTemplateId(value.templateId());
    po.setTemplateCode(value.templateCode()); po.setRuleName(value.name()); po.setRuleType(value.ruleType().name());
    po.setRuleScope(value.scope().name()); po.setQualityDimension(value.dimension()); po.setColumnName(value.columnName());
    po.setComparisonOperator(value.operator().symbol()); po.setThresholdValue(value.threshold()); po.setThresholdEnd(value.thresholdEnd());
    po.setEnumValuesJson(writeJson(value.enumValues())); po.setCustomSql(value.customSql()); po.setEnabled(value.enabled());
    po.setSortOrder(sortOrder); po.setDeleted(false); return po;
  }

  private QualityMonitorSettingPO settingPO(long monitorId, MonitorSettingsSpec value) {
    QualityMonitorSettingPO po = new QualityMonitorSettingPO(); po.setMonitorId(monitorId); po.setRunMode(value.runMode().name());
    po.setScheduleFrequency(value.scheduleFrequency() == null ? null : value.scheduleFrequency().name());
    po.setScheduleTime(value.scheduleTime() == null ? null : java.time.LocalTime.parse(value.scheduleTime(), TIME_FORMATTER));
    po.setScheduleWeekday(value.scheduleWeekday() == null ? null : value.scheduleWeekday().name()); po.setCronExpression(value.cronExpression());
    po.setNextRunTime(value.nextRunTime()); po.setRuleFailureAction(value.ruleFailureAction().name()); po.setNotifyEnabled(value.notifyEnabled());
    po.setNotifyChannel(value.notifyChannel().name()); po.setNotifyTarget(value.notifyTarget()); po.setAlertLevel(value.alertLevel().name()); return po;
  }

  private QualityTableAssetPO tableAssetPO(TableAssetSpec value) {
    QualityTableAssetPO po = new QualityTableAssetPO(); po.setDataSourceId(value.dataSourceId()); po.setDataSourceName(value.dataSourceName());
    po.setDatabaseName(blankToEmpty(value.databaseName())); po.setSchemaName(blankToEmpty(value.schemaName())); po.setTableName(value.tableName());
    po.setTableType(value.tableType()); po.setRemarks(value.remarks()); po.setRegisteredBy(value.registeredBy()); po.setDeleted(false); return po;
  }

  private QualityAlertEventPO alertPO(AlertEventSpec value) {
    QualityAlertEventPO po = new QualityAlertEventPO(); po.setMonitorId(value.monitorId()); po.setExecutionNo(value.executionNo());
    po.setCheckResult(value.checkResult().name()); po.setAlertLevel(value.alertLevel().name()); po.setNotifyChannel(value.notifyChannel().name());
    po.setNotifyTarget(value.notifyTarget()); po.setDeliveryStatus(value.deliveryStatus()); po.setAlertMessage(value.alertMessage());
    po.setErrorMessage(value.errorMessage()); po.setCreatedAt(value.createdAt()); return po;
  }

  private QualityRuleExecutionPO ruleExecutionPO(RuleExecutionSpec value) {
    QualityRuleExecutionPO po = new QualityRuleExecutionPO(); po.setExecutionId(value.executionId()); po.setRuleId(value.ruleId());
    po.setRuleName(value.ruleName()); po.setTemplateCode(value.templateCode()); po.setRuleType(value.ruleType().name()); po.setColumnName(value.columnName());
    po.setCheckResult(value.checkResult().name()); po.setMetricValue(value.metricValue()); po.setExpectedValue(value.expectedValue());
    po.setExecutedSql(value.executedSql()); po.setErrorMessage(value.errorMessage()); po.setDurationMs(value.durationMs()); return po;
  }

  private Map<String, Object> monitorParams(QualityQuery.Monitor query) {
    Map<String, Object> params = new LinkedHashMap<>(); putLike(params, "keyword", query.keyword()); params.put("dataSourceId", query.dataSourceId());
    params.put("databaseFilter", query.databaseFilter()); params.put("databaseName", blankToEmpty(query.databaseName()));
    params.put("schemaFilter", query.schemaFilter()); params.put("schemaName", blankToEmpty(query.schemaName())); putText(params, "tableName", query.tableName());
    params.put("enabled", query.enabled()); if (query.lastResult() != null) params.put("lastResult", query.lastResult().name()); return params;
  }

  private Map<String, Object> tableAssetParams(QualityQuery.TableAsset query) {
    Map<String, Object> params = new LinkedHashMap<>(); params.put("dataSourceId", query.dataSourceId());
    params.put("databaseFilter", query.databaseFilter()); params.put("databaseName", blankToEmpty(query.databaseName()));
    params.put("schemaFilter", query.schemaFilter()); params.put("schemaName", blankToEmpty(query.schemaName())); putLike(params, "keyword", query.keyword()); return params;
  }

  private Map<String, Object> executionParams(QualityQuery.Execution query) {
    Map<String, Object> params = new LinkedHashMap<>(); putLike(params, "keyword", query.keyword()); params.put("monitorId", query.monitorId());
    if (query.executionStatus() != null) params.put("executionStatus", query.executionStatus().name());
    if (query.checkResult() != null) params.put("checkResult", query.checkResult().name()); return params;
  }

  private void addNullableFilter(Map<String, Object> params, String prefix, String value) {
    if (value == null) { params.put(prefix + "Filter", false); return; }
    params.put(prefix + "Filter", true); params.put(prefix + "Name", blankToEmpty(value));
  }

  private void putLike(Map<String, Object> params, String key, String value) { if (hasText(value)) params.put(key, "%" + value.trim().toLowerCase() + "%"); }
  private void putText(Map<String, Object> params, String key, String value) { if (hasText(value)) params.put(key, value.trim()); }

  private String writeJson(List<String> values) {
    if (values == null || values.isEmpty()) return null;
    try { return objectMapper.writeValueAsString(values); }
    catch (JsonProcessingException e) { throw new IllegalArgumentException("枚举值无法序列化", e); }
  }

  private List<String> readJsonList(String json) {
    if (!hasText(json)) return List.of();
    try { return objectMapper.readValue(json, new TypeReference<>() {}); }
    catch (JsonProcessingException e) { throw new IllegalStateException("数据库中的枚举值配置无法解析", e); }
  }

  private static <T> PageData<T> pageData(List<T> records, long total, int current, int pageSize) {
    long pages = pageSize <= 0 ? 0L : (total + pageSize - 1L) / pageSize;
    return new PageData<>(records, total, pages, current, pageSize);
  }

  private static CheckResult checkResult(String value) { return hasText(value) ? CheckResult.valueOf(value) : CheckResult.NOT_RUN; }
  private static String objectName(String databaseName, String schemaName, String tableName) {
    List<String> parts = new ArrayList<>(); if (hasText(databaseName)) parts.add(databaseName.trim());
    if (hasText(schemaName) && !schemaName.trim().equals(databaseName)) parts.add(schemaName.trim()); parts.add(tableName); return String.join(".", parts);
  }
  private static boolean hasText(String value) { return value != null && !value.isBlank(); }
  private static String blankToNull(String value) { return hasText(value) ? value.trim() : null; }
  private static String blankToEmpty(String value) { String v = blankToNull(value); return v == null ? "" : v; }
  private static int nvl(Integer value) { return value == null ? 0 : value; }
  private static long nvl(Long value) { return value == null ? 0L : value; }
  private static <E extends Enum<E>> E enumValue(Class<E> type, String value) { return enumValue(type, value, null); }
  private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) { return hasText(value) ? Enum.valueOf(type, value) : fallback; }
}
