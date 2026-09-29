package io.yak.ops.business.mdm.identification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.identification.CandidateMatcher.CandidateHint;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 识别候选规则单元测试:表名分词与实体编码/名称匹配。 */
class CandidateMatcherTest {

  private static final List<MdmEntity> ENTITIES =
      List.of(
          entity(1L, "customer", "客户"),
          entity(2L, "item", "商品"),
          entity(3L, "supplier", "供应商"));

  @Test
  void matchesPrefixedTableByEntityCode() {
    List<CandidateHint> hints = CandidateMatcher.match("crm_customer", ENTITIES);
    assertEquals(1, hints.size());
    assertEquals("customer", hints.get(0).entityCode());
  }

  @Test
  void matchesPlainAndSuffixedTable() {
    assertTrue(CandidateMatcher.match("customer", ENTITIES).stream()
        .anyMatch(hint -> hint.entityCode().equals("customer")));
    // 复数表名
    assertTrue(CandidateMatcher.match("customers", ENTITIES).stream()
        .anyMatch(hint -> hint.entityCode().equals("customer")));
    // 数字后缀
    assertTrue(CandidateMatcher.match("customer_2024", ENTITIES).stream()
        .anyMatch(hint -> hint.entityCode().equals("customer")));
  }

  @Test
  void matchesCamelCaseTable() {
    assertTrue(CandidateMatcher.match("DimCustomer", ENTITIES).stream()
        .anyMatch(hint -> hint.entityCode().equals("customer")));
  }

  @Test
  void noMatchForBusinessTable() {
    assertEquals(0, CandidateMatcher.match("trade_order", ENTITIES).size());
  }

  @Test
  void noMatchForChineseEntityNameTable() {
    // 表名为拉丁,实体名"客户"为中文,不匹配(候选规则先规则后 AI)
    assertEquals(0, CandidateMatcher.match("kehumingcheng", ENTITIES).size());
  }

  private static MdmEntity entity(Long id, String code, String name) {
    return new MdmEntity(
        id, code, name, MdmEntityStatus.ACTIVE, null, null, "tester",
        LocalDateTime.now(), LocalDateTime.now());
  }
}
