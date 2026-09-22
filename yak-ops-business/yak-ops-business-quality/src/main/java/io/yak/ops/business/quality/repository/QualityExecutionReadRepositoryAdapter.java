package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityExecutionReadRepositoryAdapter implements QualityExecutionReadRepository {

  private final QualityExecutionDao executionDao;

  @Override
  public PageData<Execution> page(QualityQuery.Execution query) {
    Map<String, Object> params = params(query);
    long total = executionDao.countExecutions(params);
    paginate(params, query.current(), query.pageSize());
    List<Execution> records = executionDao.selectExecutions(params).stream()
        .map(po -> execution(po, List.of()))
        .toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public PageData<Execution> page(QualityQuery.ExecutionWorkspace query) {
    Map<String, Object> params = params(query);
    long total = executionDao.countExecutionWorkspace(params);
    paginate(params, query.current(), query.pageSize());
    List<Execution> records = executionDao.selectExecutionWorkspace(params).stream()
        .map(po -> execution(po, List.of()))
        .toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public PageData<RuleExecutionWorkspaceItem> pageRules(QualityQuery.ExecutionWorkspace query) {
    Map<String, Object> params = params(query);
    long total = executionDao.countRuleExecutionWorkspace(params);
    paginate(params, query.current(), query.pageSize());
    List<RuleExecutionWorkspaceItem> records = executionDao.selectRuleExecutionWorkspace(params).stream()
        .map(this::ruleItem)
        .toList();
    return pageData(records, total, query.current(), query.pageSize());
  }

  @Override
  public Optional<Execution> find(String executionNo) {
    QualityExecutionPO po = executionDao.selectByExecutionNo(executionNo);
    if (po == null) return Optional.empty();
    List<RuleExecution> rules = executionDao.selectRuleExecutions(po.getId()).stream()
        .map(this::ruleExecution)
        .toList();
    return Optional.of(execution(po, rules));
  }

  @Override
  public Optional<Execution> findSummary(String executionNo) {
    return Optional.ofNullable(executionDao.selectByExecutionNo(executionNo))
        .map(po -> execution(po, List.of()));
  }

  private Map<String, Object> params(QualityQuery.Execution query) {
    Map<String, Object> params = new LinkedHashMap<>();
    putLike(params, "keyword", query.keyword());
    params.put("monitorId", query.monitorId());
    if (query.executionStatus() != null) {
      params.put("executionStatus", query.executionStatus().name());
    }
    if (query.checkResult() != null) {
      params.put("checkResult", query.checkResult().name());
    }
    return params;
  }

  private Map<String, Object> params(QualityQuery.ExecutionWorkspace query) {
    Map<String, Object> params = new LinkedHashMap<>();
    putLike(params, "keyword", query.keyword());
    putLike(params, "objectKeyword", query.objectKeyword());
    params.put("dataSourceId", query.dataSourceId());
    params.put("monitorId", query.monitorId());
    if (query.executionStatus() != null) params.put("executionStatus", query.executionStatus().name());
    if (query.checkResult() != null) params.put("checkResult", query.checkResult().name());
    if (query.triggerType() != null) params.put("triggerType", query.triggerType().name());
    params.put("hasIssues", query.hasIssues());
    params.put("queuedAfter", query.queuedAfter());
    params.put("queuedBefore", query.queuedBefore());
    List<String> ruleTypes = matchingRuleTypes(query);
    boolean filter = hasText(query.dimension()) || query.scope() != null;
    params.put("ruleTypeFilter", filter);
    params.put("ruleTypes", ruleTypes);
    return params;
  }

  private List<String> matchingRuleTypes(QualityQuery.ExecutionWorkspace query) {
    List<String> result = new ArrayList<>();
    for (RuleType type : RuleType.values()) {
      boolean dimension = !hasText(query.dimension())
          || type.dimension().equals(query.dimension().trim());
      boolean scope = query.scope() == null || type.scope() == query.scope();
      if (dimension && scope) result.add(type.name());
    }
    return result;
  }

  private Execution execution(QualityExecutionPO po, List<RuleExecution> rules) {
    return new Execution(po.getId(), po.getExecutionNo(), po.getMonitorId(), po.getMonitorName(),
        po.getDataSourceId(), po.getDataSourceName(), po.getDatabaseName(), po.getSchemaName(),
        po.getTableName(), po.getObjectName(), enumValue(TriggerType.class, po.getTriggerType(), TriggerType.MANUAL),
        ExecutionStatus.valueOf(po.getExecutionStatus()), checkResult(po.getCheckResult()),
        nvl(po.getTotalRules()), nvl(po.getPassedRules()), nvl(po.getFailedRules()),
        nvl(po.getErrorRules()), po.getOperatorName(), po.getQueuedAt(), po.getStartedAt(),
        po.getFinishedAt(), po.getDurationMs(), po.getErrorMessage(), rules);
  }

  private RuleExecutionWorkspaceItem ruleItem(RuleExecutionWorkspaceRow row) {
    RuleType type = RuleType.valueOf(row.getRuleType());
    return new RuleExecutionWorkspaceItem(row.getRuleExecutionId(), row.getRuleId(), row.getExecutionNo(),
        row.getMonitorId(), row.getMonitorName(), row.getDataSourceId(), row.getDataSourceName(),
        row.getDatabaseName(), row.getSchemaName(), row.getTableName(), row.getObjectName(),
        row.getRuleName(), row.getTemplateCode(), type, type.scope(), type.dimension(), row.getColumnName(),
        TriggerType.valueOf(row.getTriggerType()), ExecutionStatus.valueOf(row.getExecutionStatus()),
        checkResult(row.getRuleCheckResult()), row.getMetricValue(), row.getExpectedValue(),
        row.getOperatorName(), row.getQueuedAt(), row.getStartedAt(), row.getFinishedAt(),
        row.getRuleDurationMs(), row.getRuleErrorMessage());
  }

  private RuleExecution ruleExecution(QualityRuleExecutionPO po) {
    return new RuleExecution(po.getId(), po.getRuleId(), po.getRuleName(), po.getTemplateCode(),
        RuleType.valueOf(po.getRuleType()), po.getColumnName(), checkResult(po.getCheckResult()),
        po.getMetricValue(), po.getExpectedValue(), po.getExecutedSql(), po.getErrorMessage(),
        po.getDurationMs(), po.getCreatedAt());
  }

  private static void paginate(Map<String, Object> params, int current, int pageSize) {
    params.put("limit", pageSize);
    params.put("offset", (current - 1L) * pageSize);
  }

  private static <T> PageData<T> pageData(List<T> records, long total, int current, int pageSize) {
    long pages = pageSize <= 0 ? 0L : (total + pageSize - 1L) / pageSize;
    return new PageData<>(records, total, pages, current, pageSize);
  }

  private static void putLike(Map<String, Object> params, String key, String value) {
    if (hasText(value)) params.put(key, "%" + value.trim().toLowerCase() + "%");
  }

  private static CheckResult checkResult(String value) {
    return hasText(value) ? CheckResult.valueOf(value) : CheckResult.NOT_RUN;
  }

  private static int nvl(Integer value) { return value == null ? 0 : value; }
  private static boolean hasText(String value) { return value != null && !value.isBlank(); }
  private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
    return hasText(value) ? Enum.valueOf(type, value) : fallback;
  }
}
