package io.yak.ops.business.mdm.processing;

import io.yak.ops.business.mdm.domain.clean.MdmMatchField;
import io.yak.ops.business.mdm.domain.clean.MdmMatchType;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 去重发现的 SQL 表达式构建(服务端聚合,DB 侧 GROUP BY,遵循 home-overview-contract:
 * 禁止无界 list() 后内存统计)。attrCode 来自已校验的规则字段(实体属性编码,
 * 仅允许 [A-Za-z0-9_]),嵌入 ${} 前再次自校验,防止注入。
 *
 * 匹配键:EXACT 用原始值,FUZZY 用 LOWER(TRIM(value)) 规范化值;AND 组合条件用
 * CONCAT(值1, CHAR(1), 值2, ...) 拼接为单键(CHAR(1) 作为分隔符,Java 侧用同值
 * "\u0001" 回算),OR 条件按字段分别聚合。
 */
public final class DedupSql {

  private static final Pattern ATTR_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");
  private static final String FIELD_SEPARATOR = "\u0001";

  private DedupSql() {}

  /** 属性编码注入 SQL 前的防御性校验(与 52 属性编码口径一致)。 */
  public static boolean isValidAttrCode(String attrCode) {
    return attrCode != null && ATTR_CODE_PATTERN.matcher(attrCode).matches();
  }

  /** 单字段匹配键表达式(EXACT 原值 / FUZZY 规范化值)。 */
  public static String fieldKeyExpr(MdmMatchField field) {
    return valueExpr(field);
  }

  /** AND 组合键表达式:CONCAT(值1, CHAR(1), 值2, ...),字段序固定。 */
  public static String combinedKeyExpr(List<MdmMatchField> fields) {
    return fields.stream()
        .map(DedupSql::valueExpr)
        .collect(Collectors.joining(", CHAR(1), ", "CONCAT(", ")"));
  }

  /** 单字段非空条件(排除缺失/空值记录,避免 NULL 参与聚合)。 */
  public static String fieldValueCondition(MdmMatchField field) {
    String expr = valueExpr(field);
    return expr + " IS NOT NULL AND " + expr + " <> ''";
  }

  /** AND 组合非空条件:所有字段均非空才参与聚合。 */
  public static String combinedValueCondition(List<MdmMatchField> fields) {
    return fields.stream()
        .map(DedupSql::fieldValueCondition)
        .collect(Collectors.joining(" AND "));
  }

  /** Java 侧与 SQL 一致的回算键:EXACT 原值 / FUZZY 规范化;AND 用分隔符拼接。 */
  public static String javaKey(List<MdmMatchField> fields, java.util.Map<String, Object> attributes) {
    StringBuilder key = new StringBuilder();
    for (MdmMatchField field : fields) {
      Object raw = attributes.get(field.attrCode());
      if (raw == null) {
        return null;
      }
      String value = String.valueOf(raw);
      if (field.matchType() == MdmMatchType.FUZZY) {
        value = normalize(value);
      }
      if (value.isEmpty()) {
        return null;
      }
      if (key.length() > 0) {
        key.append(FIELD_SEPARATOR);
      }
      key.append(value);
    }
    return key.toString();
  }

  /** 规范化:trim + 小写(与 SQL LOWER(TRIM(...)) 一致)。 */
  public static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String valueExpr(MdmMatchField field) {
    if (!isValidAttrCode(field.attrCode())) {
      throw new IllegalArgumentException("非法属性编码: " + field.attrCode());
    }
    // JSON null 经 JSON_UNQUOTE(JSON_EXTRACT) 会落成字符串 'null' 逃过 IS NOT NULL,
    // 把缺值记录聚成假重复组;NULLIF 归一为真 SQL NULL,与 Java 侧 javaKey 的 null 口径一致。
    String extracted =
        "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(attributes, '$." + field.attrCode() + "')), 'null')";
    return field.matchType() == MdmMatchType.FUZZY ? "LOWER(TRIM(" + extracted + "))" : extracted;
  }
}
