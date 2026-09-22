package io.yak.ops.business.mdm.identification;

import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 识别候选规则(先规则后 AI,AI 匹配范围外):表名分词(非字母数字分隔 + 驼峰)
 * 与项目实体编码/名称(拉丁)匹配;命中则标注匹配实体。规则可随后续 ticket 演进。
 */
public final class CandidateMatcher {

  private CandidateMatcher() {}

  /** 表名命中候选实体;未命中返回空列表。 */
  public static List<CandidateHint> match(String tableName, List<MdmEntity> entities) {
    if (tableName == null || entities == null || entities.isEmpty()) {
      return List.of();
    }
    Set<String> tokens = tokenize(tableName);
    List<CandidateHint> hints = new ArrayList<>();
    for (MdmEntity entity : entities) {
      if (matches(tokens, entity.code()) || matches(tokens, entity.name())) {
        hints.add(new CandidateHint(entity.id(), entity.code(), entity.name()));
      }
    }
    return hints;
  }

  private static boolean matches(Set<String> tokens, String target) {
    if (target == null) {
      return false;
    }
    String normalized = target.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return false;
    }
    // 单 token 精确(含复数 s)或目标含空白(多词名)时整词比对
    if (normalized.indexOf(' ') < 0) {
      return tokens.contains(normalized) || tokens.contains(normalized + "s");
    }
    return tokens.containsAll(tokenize(normalized));
  }

  private static Set<String> tokenize(String name) {
    Set<String> tokens = new HashSet<>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        // 驼峰边界:前字符为小写/数字且当前为大写时切分
        if (current.length() > 0
            && Character.isUpperCase(c)
            && Character.isLowerCase(name.charAt(i - 1))) {
          addToken(tokens, current);
        }
        current.append(Character.toLowerCase(c));
      } else {
        addToken(tokens, current);
      }
    }
    addToken(tokens, current);
    return tokens;
  }

  private static void addToken(Set<String> tokens, StringBuilder current) {
    if (current.length() > 0) {
      tokens.add(current.toString());
      current.setLength(0);
    }
  }

  /** 候选命中:实体 + 匹配依据(实体编码/名称)。 */
  public record CandidateHint(Long entityId, String entityCode, String entityName) {}
}
