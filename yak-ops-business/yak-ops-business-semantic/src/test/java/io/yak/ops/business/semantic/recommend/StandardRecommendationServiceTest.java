package io.yak.ops.business.semantic.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 规则推荐引擎单元测试(REQUIREMENTS ticket 39)。 */
class StandardRecommendationServiceTest {

  private SemanticStandardRepository repository;
  private StandardRecommendationService service;

  @BeforeEach
  void setUp() {
    repository = mock(SemanticStandardRepository.class);
    service = new StandardRecommendationService(repository);
    when(repository.listEnabledByKind(StandardKind.NAMING))
        .thenReturn(
            List.of(
                standard(
                    1L,
                    StandardKind.NAMING,
                    "field_snake",
                    "字段小写下划线",
                    fields(
                        "FIELD", null, "^[a-z][a-z0-9_]*$", null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null))));
    when(repository.listEnabledByKind(StandardKind.TYPE))
        .thenReturn(
            List.of(
                standard(
                    2L,
                    StandardKind.TYPE,
                    "amount",
                    "金额",
                    fields(
                        null, null, null, null, "AMOUNT", "DECIMAL(18,2)",
                        "{\"mysql\":[\"DECIMAL\",\"NUMERIC\"]}", null, null, null, null, null,
                        null, null, null, null, null))));
    when(repository.listEnabledByKind(StandardKind.CODE))
        .thenReturn(
            List.of(
                standard(
                    3L,
                    StandardKind.CODE,
                    "gender_male",
                    "性别-男",
                    fields(
                        null, null, null, null, null, null, null, "gender", "M", "男", null, null,
                        null, null, null, null, null))));
    when(repository.listEnabledByKind(StandardKind.UNIT))
        .thenReturn(
            List.of(
                standard(
                    4L,
                    StandardKind.UNIT,
                    "cny_yuan",
                    "元",
                    fields(
                        null, null, null, null, null, null, null, null, null, null, "CNY", "金额",
                        null, null, null, null, null))));
    when(repository.listEnabledByKind(StandardKind.CALIBER))
        .thenReturn(
            List.of(
                standard(
                    5L,
                    StandardKind.CALIBER,
                    "gmv",
                    "GMV",
                    fields(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        "gmv", null, null, null, null))));
    when(repository.listEnabledByKind(StandardKind.SECURITY))
        .thenReturn(
            List.of(
                standard(
                    6L,
                    StandardKind.SECURITY,
                    "pii_phone",
                    "手机号",
                    fields(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, "PII_PHONE", "保留前 3 后 4"))));
  }

  @Test
  void namingCheckMatchesSnakeCase() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("order_amount", "", ""));
    assertTrue(report.naming().evaluated());
    assertTrue(report.naming().matched());
    assertEquals(1L, report.naming().standardId());
  }

  @Test
  void namingCheckSuggestsStandardWhenNotMatched() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("OrderAmount", "", ""));
    assertTrue(report.naming().evaluated());
    assertFalse(report.naming().matched());
    assertEquals("field_snake", report.naming().code());
  }

  @Test
  void typeRecommendationHitsSourceMapping() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("pay_amount", "DECIMAL", ""));
    assertEquals(1, report.typeCandidates().size());
    assertEquals("amount", report.typeCandidates().get(0).code());
  }

  @Test
  void codeRecommendationHitsCodeSetInFieldName() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("user_gender", "", ""));
    assertEquals(1, report.codeCandidates().size());
    assertEquals(3L, report.codeCandidates().get(0).standardId());
  }

  @Test
  void metricFieldGetsDefaultUnitSuggestion() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("pay_money", "", "METRIC"));
    assertEquals(1, report.unitCandidates().size());
    assertEquals("cny_yuan", report.unitCandidates().get(0).code());
  }

  @Test
  void securityRecommendationHitsLevelKeyword() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("user_phone", "", ""));
    assertEquals(1, report.securityCandidates().size());
    assertEquals(6L, report.securityCandidates().get(0).standardId());
  }

  @Test
  void emptyInputYieldsEmptyReport() {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("", "", ""));
    assertFalse(report.naming().evaluated());
    assertTrue(report.typeCandidates().isEmpty());
    assertTrue(report.codeCandidates().isEmpty());
  }

  private static Standard standard(
      Long id, StandardKind kind, String code, String name, Standard.KindFields fields) {
    return new Standard(id, kind, code, name, StandardStatus.ENABLED, 1, 0, false, null, fields,
        "seed", null, null);
  }

  /** 顺序与 Standard.KindFields 完全一致,便于按需填位。 */
  private static Standard.KindFields fields(
      String scope,
      String layer,
      String ruleExpr,
      String example,
      String typeCode,
      String stdType,
      String sourceMapping,
      String codeSetCode,
      String codeValue,
      String codeLabel,
      String unitCode,
      String unitType,
      String caliberCode,
      String calRule,
      String businessDesc,
      String levelCode,
      String maskRule) {
    return new Standard.KindFields(scope, layer, ruleExpr, example, typeCode, stdType, sourceMapping,
        codeSetCode, codeValue, codeLabel, unitCode, unitType, caliberCode, calRule, businessDesc,
        levelCode, maskRule);
  }
}
