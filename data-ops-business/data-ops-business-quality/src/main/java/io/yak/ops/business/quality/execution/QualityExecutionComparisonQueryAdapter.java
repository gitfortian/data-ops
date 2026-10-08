package io.yak.ops.business.quality.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.api.QualityExecutionComparisonQueryApi;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Compares persisted evidence without consulting mutable monitor/rule definitions. */
@Component
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
public class QualityExecutionComparisonQueryAdapter implements QualityExecutionComparisonQueryApi {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final QualityExecutionReader executions;
  private final ActionAuthorization authorization;

  @Override public Comparison compare(String baselineExecutionNo, String currentExecutionNo) {
    authorization.requirePermission(QualityPermissionCode.EXECUTION_READ);
    authorization.requirePermission(QualityPermissionCode.MONITOR_READ);
    if (!validNo(baselineExecutionNo) || !validNo(currentExecutionNo)
        || baselineExecutionNo.equals(currentExecutionNo)) throw invalid();
    var before = executions.requireComparisonSummary(baselineExecutionNo);
    var after = executions.requireComparisonSummary(currentExecutionNo);
    requireFinished(before); requireFinished(after);
    if (!baselineExecutionNo.equals(before.executionNo()) || !currentExecutionNo.equals(after.executionNo())
        || !Objects.equals(before.monitorId(), after.monitorId())
        || !Objects.equals(before.dataSourceId(), after.dataSourceId())
        || !Objects.equals(empty(before.databaseName()), empty(after.databaseName()))
        || !Objects.equals(empty(before.schemaName()), empty(after.schemaName()))
        || !Objects.equals(before.tableName(), after.tableName())) throw invalid();
    Side baseline = side(before); Side current = side(after);
    var ids = new TreeSet<Long>();
    baseline.rules().forEach(rule -> ids.add(rule.ruleId()));
    current.rules().forEach(rule -> ids.add(rule.ruleId()));
    var alignment = new ArrayList<Alignment>();
    for (long id : ids) {
      Integer left = index(baseline.rules(), id), right = index(current.rules(), id);
      boolean matches = false;
      if (left != null && right != null) {
        var a = baseline.rules().get(left); var b = current.rules().get(right);
        matches = a.expectedValue() != null && b.expectedValue() != null
            && Objects.equals(a.templateCode(), b.templateCode()) && Objects.equals(a.ruleType(), b.ruleType())
            && Objects.equals(a.columnName(), b.columnName()) && Objects.equals(a.expectedValue(), b.expectedValue());
      }
      alignment.add(new Alignment(id, left, right, matches));
    }
    var result = new Comparison(baseline, current, List.copyOf(alignment));
    boundedJson(baseline, 10000); boundedJson(current, 10000); boundedJson(result, 24000);
    return result;
  }

  private Side side(Execution execution) {
    var source = executions.rulesForComparison(execution.id());
    if (source == null || source.size() > 21) throw invalid();
    var ids = new HashSet<Long>(); var rules = new ArrayList<Rule>();
    for (var row : source.stream().limit(20).toList()) {
      if (row.ruleId() == null || row.ruleId() <= 0 || !ids.add(row.ruleId())
          || row.ruleType() == null || row.checkResult() == null) throw invalid();
      rules.add(new Rule(row.ruleId(), text(row.ruleName(), 256, true), text(row.templateCode(), 128, false),
          row.ruleType().name(), text(row.columnName(), 128, true), row.checkResult().name(),
          text(row.metricValue(), 512, true), text(row.expectedValue(), 512, true)));
    }
    return new Side(execution.executionNo(), execution.monitorId(), text(execution.monitorName(), 256, true),
        text(execution.tableName(), 128, false), execution.executionStatus().name(), execution.checkResult().name(),
        execution.totalRules(), execution.passedRules(), execution.failedRules(), execution.errorRules(),
        execution.queuedAt(), execution.finishedAt(), List.copyOf(rules), source.size() > 20);
  }

  private static Integer index(List<Rule> rules, long id) {
    for (int i = 0; i < rules.size(); i++) if (rules.get(i).ruleId() == id) return i;
    return null;
  }

  private static void requireFinished(Execution value) {
    if (value == null || value.id() == null || value.id() <= 0 || value.monitorId() == null || value.monitorId() <= 0
        || value.dataSourceId() == null || value.dataSourceId() <= 0 || value.tableName() == null || value.tableName().isBlank()
        || value.executionStatus() == null || !List.of(ExecutionStatus.SUCCESS, ExecutionStatus.FAILED, ExecutionStatus.CANCELED).contains(value.executionStatus())
        || value.finishedAt() == null || value.checkResult() == null || value.checkResult() == CheckResult.RUNNING) throw invalid();
  }

  private static boolean validNo(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,128}"); }
  private static String empty(String value) { return value == null ? "" : value; }
  private static String text(String value, int max, boolean optional) {
    if (value == null && optional) return null;
    if (value == null || value.length() > max || (!optional && value.isBlank())) throw invalid();
    return value;
  }
  private static void boundedJson(Object value, int max) {
    try { if (JSON.writeValueAsString(value).length() > max) throw invalid(); }
    catch (JsonProcessingException failed) { throw invalid(); }
  }
  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("历史执行对或比较证据无效，请核对已结束的同一监控与目标，缩小证据范围");
  }
}
