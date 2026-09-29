package io.yak.ops.business.modeling.governance;

import io.yak.ops.business.semantic.api.StandardField;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 字段 ↔ 标准字段的匹配规则(38 导入校验与 44 派生继承共用同一实现)。
 *
 * <p>规则按优先级:① 字段名/编码精确 → ② 字段名相似度(≥0.6)→ ③ 注释包含候选名。
 * 命中返回标准字段与匹配方式;未命中返回 null(调用方按未治理处理)。
 *
 * <p>**故意不做"同类型即命中"**:标准字段库里大量字段同为 VARCHAR,按类型匹配会把
 * 任意字符列错标为"规范",而错标的治理状态是静默的;留成"未命中"可被沉淀/关联动作修正,
 * 代价远小于误标(治理动作的正确性优先于匹配率)。
 *
 * <p>规则只做"识别不规范",不做改名/改类型等标准化动作(DWD 重治理在 44 落地)。
 */
@Component
public class StandardFieldMatcher {

  /** 模糊匹配阈值(与前端历史口径一致)。 */
  private static final double FUZZY_THRESHOLD = 0.6;

  public static final String BY_EXACT = "exact";
  public static final String BY_FUZZY = "fuzzy";
  public static final String BY_COMMENT = "comment";

  /** 匹配结果:命中字段 + 匹配方式(exact/fuzzy/comment)。 */
  public record Match(Long stdFieldId, String stdFieldCode, String stdFieldName, String matchedBy) {}

  /**
   * 该匹配是否可直接落库为关联。只有**名称精确**与**注释包含**算数:名称相似(模糊)
   * 只作为建议交给用户确认——实测 "order_no" 与 "order_id" 相似度 0.71 会把订单号错关联成
   * 订单ID,而错关联是静默的;留成"建议 + 未治理"可由用户一键采纳或沉淀(适配冷启动)。
   */
  public static boolean isAuthoritative(String matchedBy) {
    return BY_EXACT.equals(matchedBy) || BY_COMMENT.equals(matchedBy);
  }

  /** 匹配一列;candidates 为空即未命中。 */
  public Match match(
      String columnName, String dataType, String comment, List<StandardField> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    String key = normalize(columnName);
    if (key.isEmpty()) {
      return null;
    }
    for (StandardField candidate : candidates) {
      if (key.equals(normalize(candidate.code())) || key.equals(normalize(candidate.name()))) {
        return of(candidate, BY_EXACT);
      }
    }
    StandardField best = null;
    double bestScore = 0;
    for (StandardField candidate : candidates) {
      String candidateKey =
          !normalize(candidate.code()).isEmpty()
              ? normalize(candidate.code())
              : normalize(candidate.name());
      if (candidateKey.isEmpty()) {
        continue;
      }
      double score = similarity(key, candidateKey);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    if (best != null && bestScore >= FUZZY_THRESHOLD) {
      return of(best, BY_FUZZY);
    }
    String remark = normalize(comment);
    if (remark.length() >= 2) {
      for (StandardField candidate : candidates) {
        String candidateName = normalize(candidate.name());
        if (candidateName.length() >= 2 && remark.contains(candidateName)) {
          return of(candidate, BY_COMMENT);
        }
      }
    }
    return null;
  }

  private static Match of(StandardField field, String matchedBy) {
    return new Match(field.id(), field.code(), field.name(), matchedBy);
  }

  /**
   * 规范化:小写 + 去符号,<b>保留中文</b>。
   *
   * <p>必须保留中文:若把中文一并剥离,"商品ID" 与 "订单ID" 都会退化成 "id",注释规则
   * 就会把中文注释的列错配到中文名的标准字段上(实测踩到)。
   */
  static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
  }

  /** Levenshtein 相似度(0~1)。 */
  static double similarity(String left, String right) {
    if (left.isEmpty() || right.isEmpty()) {
      return left.equals(right) ? 1 : 0;
    }
    int[][] matrix = new int[right.length() + 1][left.length() + 1];
    for (int i = 0; i <= left.length(); i++) {
      matrix[0][i] = i;
    }
    for (int j = 0; j <= right.length(); j++) {
      matrix[j][0] = j;
    }
    for (int j = 1; j <= right.length(); j++) {
      for (int i = 1; i <= left.length(); i++) {
        int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        matrix[j][i] =
            Math.min(
                Math.min(matrix[j][i - 1] + 1, matrix[j - 1][i] + 1),
                matrix[j - 1][i - 1] + cost);
      }
    }
    int distance = matrix[right.length()][left.length()];
    return 1 - (double) distance / Math.max(left.length(), right.length());
  }
}
