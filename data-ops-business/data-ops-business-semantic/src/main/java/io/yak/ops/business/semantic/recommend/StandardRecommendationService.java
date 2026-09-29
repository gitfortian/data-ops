package io.yak.ops.business.semantic.recommend;

import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rule-based recommendation engine (REQUIREMENTS.md ticket 39): keyword /
 * regex matching against ENABLED standards only. Every rule degrades to "no
 * candidates" on no-match — recommendation never blocks a caller.
 *
 * <p>类型标准选择(53):按「字段名精确 → 关键词 → 字段名相似 → 特征型源类型」的优先级
 * 定位唯一候选,而不是把所有命中 source_mapping 的类型标准都塞给调用方取第一个——
 * 预置类型标准里大量标准(source_mapping 都含 varchar/char)会让任意 VARCHAR 列同时命中
 * 一整串,排序在前的「id」就把所有列套成了标识。关键词优先于相似度:实测
 * 「cust_name ↔ short_name」相似度 0.67,相似度优先会把名字列错配成简称,关键词语义更强。
 * 全部规则未命中 → 返回空,由调用方按「未套用 + 提示」处理(宁缺毋滥)。
 */
@Component
public class StandardRecommendationService implements StandardRecommendApi {

  private static final int MAX_CANDIDATES = 3;

  /** 模糊匹配阈值(与 StandardFieldMatcher 口径一致)。 */
  private static final double FUZZY_THRESHOLD = 0.6;

  /**
   * 字段名关键词 → 类型标准 type_code(53,优先级高于相似度)。
   *
   * <p>顺序即优先级:敏感/特定语义在前(如 idcard 必须先于 id),宽泛语义在后。
   * 命中关键词但项目里没有对应 type_code 的标准时跳过该规则,继续下一关键词。
   */
  private static final List<KeywordRule> TYPE_KEYWORD_RULES =
      List.of(
          new KeywordRule("idcard", "idcard"),
          new KeywordRule("mobile", "mobile"),
          new KeywordRule("phone", "mobile"),
          new KeywordRule("birthday", "date"),
          new KeywordRule("time", "time"),
          new KeywordRule("date", "date"),
          new KeywordRule("name", "name"),
          new KeywordRule("id", "id"),
          new KeywordRule("status", "status"),
          new KeywordRule("type", "type"),
          new KeywordRule("level", "type"),
          new KeywordRule("gender", "status"),
          new KeywordRule("sex", "status"),
          new KeywordRule("source", "type"),
          new KeywordRule("city", "name"),
          new KeywordRule("province", "name"),
          new KeywordRule("address", "name"),
          new KeywordRule("code", "code"),
          new KeywordRule("amount", "amount"),
          new KeywordRule("amt", "amount"),
          new KeywordRule("price", "price"),
          new KeywordRule("cnt", "count"),
          new KeywordRule("qty", "quantity"),
          new KeywordRule("quantity", "quantity"),
          new KeywordRule("email", "email"),
          new KeywordRule("flag", "flag"),
          new KeywordRule("ip", "ip"),
          new KeywordRule("url", "url"),
          new KeywordRule("remark", "remark"));

  /**
   * 特征型源类型 → 类型标准 type_code(53 规则 4):只有这些源类型能由「类型」本身给出明确
   * 语义(时间/日期族、布尔),其余(如 varchar/decimal)类型匹配毫无区分度,不做、留给未命中。
   */
  private static final Map<String, List<String>> DISTINCTIVE_TYPE_BY_SOURCE =
      Map.of(
          "DATETIME", List.of("time", "datetime"),
          "TIMESTAMP", List.of("time", "datetime", "timestamp"),
          "DATE", List.of("date", "datetime"),
          "BOOLEAN", List.of("flag"),
          "BIT", List.of("flag"));

  private final SemanticStandardRepository standardRepository;

  public StandardRecommendationService(SemanticStandardRepository standardRepository) {
    this.standardRepository = standardRepository;
  }

  @Override
  public RecommendationReport recommend(RecommendRequest request) {
    String rawFieldName = request == null || request.fieldName() == null ? "" : request.fieldName().trim();
    // 命名规则区分大小写(如 ^[a-z]...),关键词匹配用小写形态。
    String fieldNameLower = rawFieldName.toLowerCase(Locale.ROOT);
    String dataTypeUpper =
        request == null || request.dataType() == null
            ? ""
            : request.dataType().trim().toUpperCase(Locale.ROOT);
    String role = request == null || request.role() == null ? "" : request.role().toUpperCase(Locale.ROOT);

    List<Standard> naming = byKind(StandardKind.NAMING);
    List<Standard> types = byKind(StandardKind.TYPE);
    List<Standard> codes = byKind(StandardKind.CODE);
    List<Standard> units = byKind(StandardKind.UNIT);
    List<Standard> calibers = byKind(StandardKind.CALIBER);
    List<Standard> securities = byKind(StandardKind.SECURITY);

    return new RecommendationReport(
        checkNaming(rawFieldName, naming),
        typeCandidates(fieldNameLower, dataTypeUpper, types),
        codeCandidates(fieldNameLower, codes),
        unitCandidates(fieldNameLower, role, units),
        caliberCandidates(fieldNameLower, calibers),
        securityCandidates(fieldNameLower, securities));
  }

  /** 命名校验:scope=FIELD 或通用的 ENABLED 命名标准;命中给出 matched=true,全不中给出首个建议。 */
  private NamingCheck checkNaming(String fieldName, List<Standard> namingStandards) {
    List<Standard> applicable =
        namingStandards.stream()
            .filter(
                standard ->
                    standard.fields().scope() == null
                        || "FIELD".equals(standard.fields().scope()))
            .toList();
    if (fieldName.isEmpty() || applicable.isEmpty()) {
      return new NamingCheck(false, false, null, null, null, null);
    }
    for (Standard standard : applicable) {
      if (matchesRegex(standard.fields().ruleExpr(), fieldName)) {
        return new NamingCheck(true, true, standard.id(), standard.code(), standard.name(),
            standard.fields().ruleExpr());
      }
    }
    Standard suggestion = applicable.get(0);
    return new NamingCheck(true, false, suggestion.id(), suggestion.code(), suggestion.name(),
        suggestion.fields().ruleExpr());
  }

  /**
   * 类型标准候选(53 优先级):① 字段名精确命中 type_code/名称 → ② 字段名关键词 →
   * ③ 字段名相似度(≥0.6)→ ④ 特征型源类型映射 → ⑤ 未命中返回空(调用方按"未套用"处理)。
   */
  private List<StandardCandidate> typeCandidates(
      String fieldName, String dataType, List<Standard> types) {
    if (types == null || types.isEmpty()) {
      return List.of();
    }
    String key = normalize(fieldName);
    if (!key.isEmpty()) {
      // ① 精确:字段名 = type_code 或标准名(如字段名就叫 status)。
      for (Standard standard : types) {
        if (fields(standard) != null
            && (key.equals(normalize(standard.fields().typeCode()))
                || key.equals(normalize(standard.name())))) {
          return List.of(candidate(standard, "字段名精确命中类型标准"));
        }
      }
      // ② 关键词:按 TYPE_KEYWORD_RULES 顺序,命中即返回该关键词映射的标准。
      for (KeywordRule rule : TYPE_KEYWORD_RULES) {
        if (!key.contains(rule.keyword())) {
          continue;
        }
        Standard hit = byTypeCode(types, rule.typeCode());
        if (hit != null) {
          return List.of(candidate(hit, "字段名命中类型关键词:" + rule.keyword()));
        }
      }
      // ③ 相似度:全部候选取最接近者,达到阈值才返回(只返回最接近的一个,避免多候选取错)。
      Standard best = null;
      double bestScore = 0;
      for (Standard standard : types) {
        if (fields(standard) == null) {
          continue;
        }
        String candidateKey = normalize(standard.fields().typeCode());
        if (candidateKey.isEmpty()) {
          candidateKey = normalize(standard.name());
        }
        if (candidateKey.isEmpty()) {
          continue;
        }
        double score = similarity(key, candidateKey);
        if (score > bestScore) {
          bestScore = score;
          best = standard;
        }
      }
      if (best != null && bestScore >= FUZZY_THRESHOLD) {
        return List.of(candidate(best, "字段名相似"));
      }
    }
    // ④ 特征型源类型:只认时间/日期族与布尔,其余源类型不做类型匹配。
    List<String> typeCodes = DISTINCTIVE_TYPE_BY_SOURCE.get(normalizeDataType(dataType));
    if (typeCodes == null || typeCodes.isEmpty()) {
      return List.of();
    }
    List<StandardCandidate> candidates = new ArrayList<>();
    for (String typeCode : typeCodes) {
      Standard standard = byTypeCode(types, typeCode);
      if (standard != null) {
        candidates.add(candidate(standard, "字段类型命中源类型映射"));
      }
    }
    return candidates;
  }

  private List<StandardCandidate> codeCandidates(String fieldName, List<Standard> codes) {
    return fieldName.isEmpty()
        ? List.of()
        : codes.stream()
            .filter(
                standard ->
                    standard.fields().codeSetCode() != null
                        && fieldName.contains(standard.fields().codeSetCode().toLowerCase(Locale.ROOT)))
            .limit(MAX_CANDIDATES)
            .map(standard -> candidate(standard, "字段名命中码集编码"))
            .toList();
  }

  private List<StandardCandidate> unitCandidates(String fieldName, String role, List<Standard> units) {
    List<StandardCandidate> matched =
        fieldName.isEmpty()
            ? List.of()
            : units.stream()
                .filter(
                    standard ->
                        standard.fields().unitCode() != null
                            && fieldName.contains(standard.fields().unitCode().toLowerCase(Locale.ROOT)))
                .limit(MAX_CANDIDATES)
                .map(standard -> candidate(standard, "字段名命中单位编码"))
                .toList();
    if (!matched.isEmpty() || !"METRIC".equals(role)) {
      return matched;
    }
    // 度量字段无单位命中时默认建议金额类单位(REQUIREMENTS 39)。
    return units.stream()
        .filter(standard -> "金额".equals(standard.fields().unitType()))
        .findFirst()
        .map(standard -> List.of(candidate(standard, "度量字段默认建议金额单位")))
        .orElse(List.of());
  }

  private List<StandardCandidate> caliberCandidates(String fieldName, List<Standard> calibers) {
    return fieldName.isEmpty()
        ? List.of()
        : calibers.stream()
            .filter(
                standard ->
                    standard.fields().caliberCode() != null
                        && fieldName.contains(standard.fields().caliberCode().toLowerCase(Locale.ROOT)))
            .limit(MAX_CANDIDATES)
            .map(standard -> candidate(standard, "字段名命中口径编码"))
            .toList();
  }

  /** 安全推荐:等级编码关键词(PII_PHONE → phone)出现在字段名中即命中。 */
  private List<StandardCandidate> securityCandidates(String fieldName, List<Standard> securities) {
    if (fieldName.isEmpty()) {
      return List.of();
    }
    List<StandardCandidate> candidates = new ArrayList<>();
    for (Standard standard : securities) {
      String levelCode = standard.fields().levelCode();
      if (levelCode == null) {
        continue;
      }
      String[] segments = levelCode.toLowerCase(Locale.ROOT).split("_");
      // 跳过首段前缀(如 PII),其余段作为关键词。
      for (int index = 1; index < segments.length; index++) {
        if (segments[index].length() >= 3 && fieldName.contains(segments[index])) {
          candidates.add(candidate(standard, "字段名命中敏感关键词:" + segments[index]));
          break;
        }
      }
      if (candidates.size() >= MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  private List<Standard> byKind(StandardKind kind) {
    return standardRepository.listEnabledByKind(kind);
  }

  private static StandardCandidate candidate(Standard standard, String reason) {
    return new StandardCandidate(
        standard.id(),
        standard.kind().name(),
        standard.code(),
        standard.name(),
        standard.fields().codeSetCode(),
        standard.fields().ruleExpr(),
        reason);
  }

  private static boolean matchesRegex(String ruleExpr, String value) {
    if (ruleExpr == null || ruleExpr.isBlank()) {
      return false;
    }
    try {
      return value.matches(ruleExpr);
    } catch (RuntimeException ignored) {
      // 规则表达式不合法时视为不匹配,推荐不阻断。
      return false;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  /** 源类型规整:大写 + 去掉长度/精度后缀(如 datetime(6)、decimal(18,2) → decimal)。 */
  private static String normalizeDataType(String dataType) {
    return normalize(dataType).replaceAll("\\(.*", "").trim().toUpperCase(Locale.ROOT);
  }

  private static Standard.KindFields fields(Standard standard) {
    return standard == null ? null : standard.fields();
  }

  /** 按 type_code 在类型标准里定位(命中项目级标准;缺失时返回 null,调用方降级)。 */
  private static Standard byTypeCode(List<Standard> types, String typeCode) {
    if (typeCode == null || types == null) {
      return null;
    }
    for (Standard standard : types) {
      if (fields(standard) != null && typeCode.equals(normalize(standard.fields().typeCode()))) {
        return standard;
      }
    }
    return null;
  }

  /** Levenshtein 相似度(0~1,与 StandardFieldMatcher 口径一致)。 */
  private static double similarity(String left, String right) {
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

  /** 一条关键词规则:关键词 → 目标类型标准 type_code。 */
  private record KeywordRule(String keyword, String typeCode) {}
}
