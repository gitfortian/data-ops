package io.yak.ops.business.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.security.domain.DiscoverableField;
import io.yak.ops.business.security.dao.model.DsecDiscoveryRulePO;
import org.junit.jupiter.api.Test;

/** 敏感发现规则匹配纯函数单测:NAME/COMMENT/CONTENT/REGEX 匹配。 */
class DiscoveryServiceTest {

  private static DsecDiscoveryRulePO rule(String matchType, String pattern) {
    DsecDiscoveryRulePO po = new DsecDiscoveryRulePO();
    po.setMatchType(matchType);
    po.setPattern(pattern);
    return po;
  }

  private static DiscoverableField field(String column, String comment) {
    return new DiscoverableField(1L, "db", "tbl", column, comment);
  }

  @Test
  void nameMatchesColumnNameCaseInsensitive() {
    assertTrue(DiscoveryService.matches(rule("NAME", "phone"), field("MobilePhone", null)));
    assertFalse(DiscoveryService.matches(rule("NAME", "phone"), field("amount", "手机号")));
  }

  @Test
  void commentMatchesFieldComment() {
    assertTrue(DiscoveryService.matches(rule("COMMENT", "身份证"), field("id_no", "客户身份证号")));
    assertFalse(DiscoveryService.matches(rule("COMMENT", "身份证"), field("id_no", "订单号")));
  }

  @Test
  void contentMatchesEitherSide() {
    assertTrue(DiscoveryService.matches(rule("CONTENT", "email"), field("user_email", null)));
    assertTrue(DiscoveryService.matches(rule("CONTENT", "邮箱"), field("e", "用户邮箱")));
  }

  @Test
  void regexMatchesFindSemantic() {
    assertTrue(DiscoveryService.matches(rule("REGEX", "^1[3-9]\\d{9}$"), field("mobile", "13800000000")));
    assertFalse(DiscoveryService.matches(rule("REGEX", "^\\d+$"), field("name", "abc")));
  }

  @Test
  void blankPatternNeverMatches() {
    assertFalse(DiscoveryService.matches(rule("NAME", "  "), field("phone", "手机")));
  }

  @Test
  void unknownMatchTypeIsNoMatch() {
    assertFalse(DiscoveryService.matches(rule("WEIRD", "phone"), field("phone", null)));
  }

  @Test
  void objectKeyNormalisesMissingSegments() {
    assertEquals("COLUMN:7:db1:t1:c1",
        ClassificationService.objectKey("COLUMN", 7L, "db1", "t1", "c1"));
    assertEquals("TABLE:-:db1:t1:-",
        ClassificationService.objectKey("TABLE", null, "db1", "t1", null));
  }
}
