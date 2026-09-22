package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplateSpec;
import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Validates and normalizes writable custom-template semantics. */
@Component
@ConditionalOnQualityEnabled
public class CustomTemplatePolicy {

  public CustomTemplateSpec write(
      String code,
      CustomTemplateCommand.Save command,
      Long folderId,
      String operator) {
    CheckType type = command.checkType() == null ? CheckType.NUMERIC : command.checkType();
    CheckMethod method = command.checkMethod() == null ? CheckMethod.FIXED_VALUE : command.checkMethod();
    if (type != CheckType.NUMERIC || method != CheckMethod.FIXED_VALUE) {
      throw new IllegalArgumentException("当前版本仅支持数值型自定义 SQL 与固定值比较");
    }
    ComparisonOperator comparison = command.defaultOperator() == null
        ? ComparisonOperator.EQ
        : command.defaultOperator();
    BigDecimal threshold = command.defaultThreshold();
    if (threshold == null) throw new IllegalArgumentException("默认阈值不能为空");
    if (comparison == ComparisonOperator.BETWEEN && command.defaultThresholdEnd() == null) {
      throw new IllegalArgumentException("区间比较必须填写默认最大值");
    }
    String sql = normalizeSql(command.customSql());
    return new CustomTemplateSpec(
        code,
        requireText(command.name(), "模板名称不能为空"),
        text(command.description()),
        requireText(command.dimension(), "质量维度不能为空"),
        schema(comparison, threshold, command.defaultThresholdEnd(), sql),
        folderId,
        sql,
        flags(command.setFlag()),
        type,
        method,
        normalizeOperator(operator));
  }

  public static String newCode() {
    return "CUSTOM_SQL_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }

  public static Long folderId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  public static String normalizeOperator(String value) {
    String normalized = text(value);
    return normalized == null ? "system" : normalized;
  }

  public static String requireText(String value, String message) {
    String normalized = text(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private String schema(
      ComparisonOperator operator,
      BigDecimal threshold,
      BigDecimal end,
      String sql) {
    String fields = operator == ComparisonOperator.BETWEEN
        ? "[\"customSql\",\"operator\",\"threshold\",\"thresholdEnd\"]"
        : "[\"customSql\",\"operator\",\"threshold\"]";
    StringBuilder json = new StringBuilder("{\"fields\":").append(fields)
        .append(",\"defaultOperator\":\"").append(operator.name())
        .append("\",\"defaultThreshold\":").append(number(threshold));
    if (end != null) json.append(",\"defaultThresholdEnd\":").append(number(end));
    return json.append(",\"defaultSql\":\"").append(escape(sql)).append("\"}").toString();
  }

  private String normalizeSql(String value) {
    String sql = text(value);
    if (sql == null) throw new IllegalArgumentException("自定义 SQL 不能为空");
    if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
    sql = sql.replace("${tableName}", "${table}");
    if (!sql.regionMatches(true, 0, "SELECT", 0, 6)
        || sql.contains(";") || sql.contains("--") || sql.contains("/*")) {
      throw new IllegalArgumentException("自定义 SQL 仅允许执行单条只读 SELECT 查询");
    }
    return sql;
  }

  private String flags(String value) {
    String flags = text(value);
    if (flags == null) return null;
    if (flags.contains(";")) {
      throw new IllegalArgumentException("Set Flag 多条语句请使用英文逗号分隔，不要填写分号");
    }
    String result = String.join(",", Arrays.stream(flags.split(","))
        .map(String::trim)
        .filter(item -> !item.isEmpty())
        .toList());
    return result.isEmpty() ? null : result;
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private static String text(String value) {
    if (value == null) return null;
    String result = value.trim();
    return result.isEmpty() ? null : result;
  }
}
