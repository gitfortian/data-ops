package io.yak.ops.business.mdm.domain.clean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * 清洗相关 JSON 编解码(rule_expr / attributes / source_ids / merged_record_ids)。
 * 业务数据解析失败抛出 IllegalArgumentException,由服务层转译为业务异常;
 * 记录属性解析失败时调用方按"脏数据容错"处理(跳过该记录,不阻断清洗)。
 */
public final class CleanJson {

  // 宽容解析:存量 rule_expr 里混有手写/早期格式的多余键(如 "and": true),
  // 严格模式下会让整个清洗规则列表接口 44023;多余键按无意义忽略即可。
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CleanJson() {}

  public static MdmCleanRuleExpr parseExpr(String json) {
    try {
      return MAPPER.readValue(json, MdmCleanRuleExpr.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("规则表达式 JSON 不合法", exception);
    }
  }

  public static StandardizeExpr parseStandardizeExpr(String json) {
    try {
      return MAPPER.readValue(json, StandardizeExpr.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("标准化规则表达式 JSON 不合法", exception);
    }
  }

  public static CompleteExpr parseCompleteExpr(String json) {
    try {
      return MAPPER.readValue(json, CompleteExpr.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("补全规则表达式 JSON 不合法", exception);
    }
  }

  public static String writeExpr(MdmCleanRuleExpr expr) {
    return write(expr);
  }

  public static Map<String, Object> readObject(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception exception) {
      throw new IllegalArgumentException("JSON 解析失败", exception);
    }
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("JSON 序列化失败", exception);
    }
  }

  public static String writeLongs(List<Long> ids) {
    try {
      return MAPPER.writeValueAsString(ids);
    } catch (Exception exception) {
      throw new IllegalArgumentException("JSON 序列化失败", exception);
    }
  }

  public static List<Long> readLongs(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<List<Long>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }
}
