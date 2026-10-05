package io.yak.ops.business.quality.monitor;

import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.api.QualitySuggestionQueryApi;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import io.yak.ops.business.quality.repository.QualityTemplateRepository;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quality owns target/field/template validation; this read path has no command side effects. */
@Component
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
public class QualitySuggestionQueryAdapter implements QualitySuggestionQueryApi {
  private final QualityMonitorReader monitors;
  private final QualityTableAssetRepository tables;
  private final QualityTemplateRepository templates;
  private final QualityDataCatalogGateway catalog;
  private final QualityRulePolicy rules;
  private final ActionAuthorization authorization;

  @Override
  public Context require(long monitorId) {
    authorization.requirePermission(QualityPermissionCode.MONITOR_READ);
    authorization.requirePermission(QualityPermissionCode.TEMPLATE_READ);
    authorization.requirePermission(io.yak.ops.common.constant.datasource.DataSourcePermissionCode.READ);
    var snapshot = monitors.editableSnapshot(monitorId);
    var monitor = snapshot.monitor();
    if (!tables.existsTableAssetTarget(monitor.dataSourceId(), monitor.databaseName(),
        monitor.schemaName(), monitor.tableName())) throw new IllegalArgumentException("物理表未注册");
    var fields = catalog.listColumns(monitor.dataSourceId(), monitor.databaseName(),
        monitor.schemaName(), monitor.tableName());
    var allowed = templates.listTemplates(new QualityQuery.Template(null, null, null)).stream()
        .filter(t -> t.builtin() && t.enabled() && t.ruleType() != RuleType.CUSTOM_SQL)
        .map(t -> new Template(t.id(), t.code(), t.name(), t.ruleType().name(), t.scope().name(),
            t.ruleType().unit())).toList();
    return new Context(monitorId, monitor.name(), monitor.tableName(),
        snapshot.definition(),
        monitor.whereClause() != null && !monitor.whereClause().isBlank(),
        fields.stream().limit(200).map(c -> new Column(c.name(), c.type(),
            c.remarks() == null ? null : c.remarks().substring(0, Math.min(256, c.remarks().length())))).toList(),
        allowed, fields.size() > 200);
  }

  @Override
  public List<Candidate> validate(long monitorId, String expectedDefinition, List<Candidate> values) {
    Context context = require(monitorId);
    if (expectedDefinition == null || !expectedDefinition.equals(context.definition())) {
      throw new IllegalArgumentException("监控定义已改变，请重新生成建议");
    }
    if (values == null || values.isEmpty() || values.size() > 5) {
      throw new IllegalArgumentException("候选规则数量应为1到5条");
    }
    for (Candidate candidate : values) {
      if (candidate == null) throw new IllegalArgumentException("候选不能为空");
      for (var number : new java.math.BigDecimal[] {candidate.threshold(), candidate.thresholdEnd()}) {
        if (number != null && (number.precision() - number.scale() > 20 || Math.max(0, number.scale()) > 10)) {
          throw new IllegalArgumentException("阈值超出存储精度");
        }
      }
      var template = context.templates().stream().filter(t -> t.id() == candidate.templateId())
          .findFirst().orElseThrow(() -> new IllegalArgumentException("候选模板不可用"));
      if (candidate.name() == null || candidate.name().isBlank() || candidate.name().length() > 100) {
        throw new IllegalArgumentException("候选规则名称无效");
      }
      RuleType type = RuleType.valueOf(template.type());
      if (RuleScope.valueOf(template.scope()) == RuleScope.COLUMN) {
        var field = context.columns().stream().filter(c -> java.util.Objects.equals(c.name(), candidate.columnName()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("候选字段不存在或超出读取范围"));
        if (type == RuleType.COLUMN_RANGE && (field.type() == null
            || !field.type().toUpperCase(java.util.Locale.ROOT)
                .matches(".*(INT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL).*"))) {
          throw new IllegalArgumentException("范围规则需要数值字段");
        }
      } else if (candidate.columnName() != null && !candidate.columnName().isBlank()) {
        throw new IllegalArgumentException("表级规则不能指定字段");
      }
      if (candidate.threshold() == null || candidate.operator() == null) {
        throw new IllegalArgumentException("请确认比较方式和阈值");
      }
      if (("BETWEEN".equals(candidate.operator()) || type == RuleType.COLUMN_RANGE)
          && (candidate.thresholdEnd() == null || candidate.threshold().compareTo(candidate.thresholdEnd()) > 0)) {
        throw new IllegalArgumentException("区间边界无效");
      }
      if (type == RuleType.TABLE_ROW_COUNT
          && (candidate.threshold().signum() < 0 || candidate.threshold().stripTrailingZeros().scale() > 0
              || (candidate.thresholdEnd() != null && (candidate.thresholdEnd().signum() < 0
                  || candidate.thresholdEnd().stripTrailingZeros().scale() > 0)))) {
        throw new IllegalArgumentException("行数阈值应为非负整数");
      }
      if ((type == RuleType.COLUMN_NOT_NULL || type == RuleType.COLUMN_UNIQUE)
          && (candidate.threshold().signum() < 0 || candidate.threshold().compareTo(java.math.BigDecimal.valueOf(100)) > 0
              || (candidate.thresholdEnd() != null && (candidate.thresholdEnd().signum() < 0
                  || candidate.thresholdEnd().compareTo(java.math.BigDecimal.valueOf(100)) > 0)))) {
        throw new IllegalArgumentException("比例阈值单位为百分比，范围0到100");
      }
      if (candidate.enumValues() != null && (candidate.enumValues().size() > 50
          || candidate.enumValues().stream().anyMatch(v -> v == null || v.length() > 256))) {
        throw new IllegalArgumentException("枚举值超出限制");
      }
    }
    var normalized = rules.normalize(values.stream().map(v -> new QualityMonitorCommand.Rule(
        v.templateId(), v.name(), v.columnName(), v.operator(), v.threshold(), v.thresholdEnd(),
        v.enumValues(), null, false)).toList());
    return normalized.stream().map(r -> new Candidate(r.templateId(), r.name(), r.columnName(),
        r.operator().name(), r.threshold(), r.thresholdEnd(), r.enumValues())).toList();
  }

  /** Fresh physical-field check before entering the source-domain write transaction. */
  public void validateEditedFields(QualityMonitorCommand.Save command) {
    authorization.requirePermission(io.yak.ops.common.constant.datasource.DataSourcePermissionCode.READ);
    var normalized = rules.normalize(command.rules());
    var fields = catalog.listColumns(command.dataSourceId(), command.databaseName(),
        command.schemaName(), command.tableName());
    for (var rule : normalized) {
      if (rule.scope() == io.yak.ops.common.enums.quality.QualityEnums.RuleScope.COLUMN
          && fields.stream().noneMatch(c -> java.util.Objects.equals(c.name(), rule.columnName()))) {
        throw new IllegalArgumentException("物理字段已改变，请重新加载后审核规则");
      }
      if (rule.ruleType() == RuleType.COLUMN_RANGE && fields.stream().filter(c ->
          java.util.Objects.equals(c.name(), rule.columnName())).anyMatch(c -> c.type() == null
              || !c.type().toUpperCase(java.util.Locale.ROOT)
                  .matches(".*(INT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL).*"))) {
        throw new IllegalArgumentException("字段类型已改变，范围规则需要数值字段");
      }
    }
  }
}
