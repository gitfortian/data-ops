package io.yak.ops.business.quality.monitor;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.repository.QualityTemplateRepository;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Normalizes rule commands against immutable template semantics. */
@Component
@ConditionalOnQualityEnabled
public class QualityRulePolicy {
  private final QualityTemplateRepository templateRepository;

  public QualityRulePolicy(QualityTemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  public List<RuleSpec> normalize(List<QualityMonitorCommand.Rule> commands) {
    if (commands == null || commands.isEmpty()) {
      throw new IllegalArgumentException("至少需要添加一条质量规则");
    }
    List<RuleSpec> result = new ArrayList<>();
    for (QualityMonitorCommand.Rule command : commands) {
      if (command.templateId() == null) throw new IllegalArgumentException("规则模板不能为空");
      Template template = templateRepository.findTemplate(command.templateId())
          .orElseThrow(() -> new IllegalArgumentException("规则模板不存在：" + command.templateId()));
      String columnName = QualityMonitorPolicy.trimToNull(command.columnName());
      if (template.scope() == RuleScope.COLUMN && columnName == null) {
        throw new IllegalArgumentException(template.name() + " 必须选择检查字段");
      }

      ComparisonOperator operator;
      BigDecimal threshold;
      BigDecimal thresholdEnd = command.thresholdEnd();
      List<String> enumValues = normalizeEnumValues(command.enumValues());
      String customSql = QualityMonitorPolicy.trimToNull(command.customSql());

      if (template.ruleType() == RuleType.COLUMN_RANGE) {
        operator = ComparisonOperator.EQ;
        threshold = required(command.threshold(), template.name() + " 缺少最小值");
        thresholdEnd = required(command.thresholdEnd(), template.name() + " 缺少最大值");
        if (threshold.compareTo(thresholdEnd) > 0) {
          throw new IllegalArgumentException(template.name() + " 最小值不能大于最大值");
        }
      } else if (template.ruleType() == RuleType.COLUMN_ENUM) {
        operator = ComparisonOperator.EQ;
        threshold = BigDecimal.ZERO;
        thresholdEnd = null;
        if (enumValues.isEmpty()) {
          throw new IllegalArgumentException(template.name() + " 至少需要一个允许值");
        }
      } else {
        operator = command.operator() == null
            ? defaultOperator(template.ruleType())
            : ComparisonOperator.fromValue(command.operator());
        threshold = command.threshold() == null
            ? defaultThreshold(template.ruleType())
            : command.threshold();
        if (operator == ComparisonOperator.BETWEEN && thresholdEnd == null) {
          throw new IllegalArgumentException(template.name() + " 缺少区间最大值");
        }
      }

      if (template.ruleType() == RuleType.CUSTOM_SQL) validateCustomSql(customSql);
      else customSql = null;

      result.add(new RuleSpec(
          template.id(), template.code(),
          QualityMonitorPolicy.requireText(command.name(), "规则名称不能为空"),
          template.ruleType(), template.scope(), template.dimension(), columnName,
          operator, threshold, thresholdEnd, enumValues, customSql,
          command.enabled() == null || command.enabled()));
    }
    return List.copyOf(result);
  }

  private void validateCustomSql(String sql) {
    if (sql == null) throw new IllegalArgumentException("自定义 SQL 不能为空");
    String normalized = sql.trim();
    if (normalized.endsWith(";")) {
      normalized = normalized.substring(0, normalized.length() - 1).trim();
    }
    if (!normalized.toUpperCase().startsWith("SELECT ") || normalized.contains(";")) {
      throw new IllegalArgumentException("自定义 SQL 仅允许执行单条 SELECT 查询");
    }
  }

  private List<String> normalizeEnumValues(List<String> values) {
    if (values == null) return List.of();
    return values.stream()
        .map(QualityMonitorPolicy::trimToNull)
        .filter(value -> value != null)
        .distinct()
        .toList();
  }

  private ComparisonOperator defaultOperator(RuleType ruleType) {
    return switch (ruleType) {
      case TABLE_ROW_COUNT -> ComparisonOperator.GT;
      case COLUMN_NOT_NULL, COLUMN_UNIQUE -> ComparisonOperator.GTE;
      case CUSTOM_SQL, COLUMN_RANGE, COLUMN_ENUM -> ComparisonOperator.EQ;
    };
  }

  private BigDecimal defaultThreshold(RuleType ruleType) {
    return switch (ruleType) {
      case TABLE_ROW_COUNT -> BigDecimal.ZERO;
      case COLUMN_NOT_NULL, COLUMN_UNIQUE -> BigDecimal.valueOf(100);
      case CUSTOM_SQL, COLUMN_RANGE, COLUMN_ENUM -> BigDecimal.ZERO;
    };
  }

  private static BigDecimal required(BigDecimal value, String message) {
    if (value == null) throw new IllegalArgumentException(message);
    return value;
  }
}
