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

/**
 * 类型标准匹配优先级(53):字段名精确 → 关键词 → 相似度 → 特征型源类型 → 未命中不套用。
 * 验收基准 = crm_customer 逆向导入的 13 列期望表(契约 53)。
 */
class TypeStandardMatchingTest {

  private SemanticStandardRepository repository;
  private StandardRecommendationService service;

  @BeforeEach
  void setUp() {
    repository = mock(SemanticStandardRepository.class);
    service = new StandardRecommendationService(repository);
    when(repository.listEnabledByKind(StandardKind.NAMING)).thenReturn(List.of());
    when(repository.listEnabledByKind(StandardKind.CODE)).thenReturn(List.of());
    when(repository.listEnabledByKind(StandardKind.UNIT)).thenReturn(List.of());
    when(repository.listEnabledByKind(StandardKind.CALIBER)).thenReturn(List.of());
    when(repository.listEnabledByKind(StandardKind.SECURITY)).thenReturn(List.of());
  }

  private void types(Standard... standards) {
    when(repository.listEnabledByKind(StandardKind.TYPE)).thenReturn(List.of(standards));
  }

  private Standard type(Long id, String typeCode, String name) {
    return new Standard(
        id,
        StandardKind.TYPE,
        typeCode,
        name,
        StandardStatus.ENABLED,
        1,
        id.intValue(),
        false,
        null,
        new Standard.KindFields(
            null, null, null, null, typeCode, null, null, null, null, null, null, null, null,
            null, null, null, null),
        "seed",
        null,
        null);
  }

  private void assertType(String fieldName, String dataType, String expectedTypeCode) {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest(fieldName, dataType, ""));
    assertFalse(
        report.typeCandidates().isEmpty(), fieldName + " 应命中类型标准");
    assertEquals(expectedTypeCode, report.typeCandidates().get(0).code(), fieldName);
  }

  private void assertNoType(String fieldName, String dataType) {
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest(fieldName, dataType, ""));
    assertTrue(
        report.typeCandidates().isEmpty(), fieldName + " 不应套用任何类型标准");
  }

  @Test
  void benchmarkMatchesExpectedTypeStandards() {
    types(
        type(1L, "id", "标识"),
        type(2L, "code", "编码"),
        type(3L, "name", "名称"),
        type(4L, "mobile", "手机号"),
        type(5L, "idcard", "身份证"),
        type(6L, "status", "状态"),
        type(7L, "type", "类型"),
        type(8L, "level", "级别"),
        type(9L, "date", "日期"),
        type(10L, "time", "时间"),
        type(11L, "datetime", "日期时间"),
        type(12L, "flag", "标志"),
        type(13L, "amount", "金额"),
        type(14L, "serial_no", "流水号"),
        type(15L, "short_name", "简称"));

    assertType("cust_id", "BIGINT", "id");
    assertType("cust_name", "VARCHAR", "name");
    assertType("cust_mobile", "VARCHAR", "mobile");
    assertType("cust_idcard", "VARCHAR", "idcard");
    assertType("gender", "TINYINT", "status");
    assertType("birthday", "DATE", "date");
    assertType("reg_time", "DATETIME", "time");
    assertType("reg_source", "VARCHAR", "type");
    assertType("member_level", "VARCHAR", "type");
    assertType("city", "VARCHAR", "name");
    assertType("province", "VARCHAR", "name");
    assertType("status", "VARCHAR", "status");
    assertType("process_time", "DATETIME", "time");
  }

  @Test
  void unmatchedFieldGetsNoTypeStandard() {
    types(type(1L, "id", "标识"), type(3L, "name", "名称"));
    // 字段名与类型都无命中:不套用(不默认"标识",53 验收第 2 条)。
    assertNoType("zzz_column", "VARCHAR");
    assertNoType("cust_flag_xx", "VARCHAR");
  }

  @Test
  void distinctiveSourceTypeStillMatchesWhenNameHasNoSemantics() {
    types(
        type(10L, "time", "时间"),
        type(11L, "datetime", "日期时间"),
        type(9L, "date", "日期"),
        type(12L, "flag", "标志"),
        type(1L, "id", "标识"));
    // 字段名无关键词:由特征型源类型决定(规则 4)。
    assertType("created_at", "DATETIME", "time");
    assertType("born_on", "DATE", "date");
    assertType("is_deleted", "BOOLEAN", "flag");
    // 非特征型源类型(如 varchar/decimal)不做类型匹配,避免整串命中取错(53 根因)。
    assertNoType("misc_col", "VARCHAR");
    assertNoType("some_num", "DECIMAL");
  }

  @Test
  void datetimeTypeYieldsTemporalCandidatesInOrder() {
    types(
        type(10L, "time", "时间"),
        type(11L, "datetime", "日期时间"),
        type(9L, "date", "日期"),
        type(1L, "id", "标识"));
    StandardRecommendApi.RecommendationReport report =
        service.recommend(new StandardRecommendApi.RecommendRequest("created_at", "DATETIME", ""));
    assertEquals(
        List.of("time", "datetime"),
        report.typeCandidates().stream().map(StandardRecommendApi.StandardCandidate::code).toList());
  }

  @Test
  void keywordTargetMissingDegradesToNextRule() {
    // 项目标准库里没有 idcard,idcard 关键词规则降级,继续命中宽泛的 id(53 降级路径)。
    types(type(1L, "id", "标识"), type(3L, "name", "名称"));
    assertType("cust_idcard", "VARCHAR", "id");
  }

  @Test
  void fuzzyFallsBackWhenNoKeywordHit() {
    types(type(4L, "mobile", "手机号"), type(1L, "id", "标识"));
    // "moblie"(拼写错误)不含关键词 mobile,由相似度(0.67)兜底。
    assertType("moblie", "VARCHAR", "mobile");
  }

  @Test
  void exactTypeCodeWinsBeforeKeyword() {
    // 字段名就叫 type_code 时精确命中对应标准(如 level → 级别,而非关键词 level → 类型)。
    types(
        type(7L, "type", "类型"),
        type(8L, "level", "级别"),
        type(1L, "id", "标识"));
    assertType("level", "VARCHAR", "level");
    assertType("type", "VARCHAR", "type");
  }

  @Test
  void emptyLibraryYieldsNoTypeCandidates() {
    types();
    assertNoType("cust_id", "BIGINT");
  }
}
