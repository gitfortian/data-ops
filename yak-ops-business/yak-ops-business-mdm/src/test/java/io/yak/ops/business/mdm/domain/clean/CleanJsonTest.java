package io.yak.ops.business.mdm.domain.clean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 存量 rule_expr(含早期多余键)必须可解析,否则去重发现与规则列表整体 44023。 */
class CleanJsonTest {

  @Test
  void parsesLegacyExprWithUnknownKeys() {
    MdmCleanRuleExpr expr =
        CleanJson.parseExpr(
            "{\"and\": true, \"fields\": [{\"attrCode\": \"phone\", \"matchType\": \"EXACT\"}],"
                + " \"condition\": \"AND\"}");
    assertEquals(1, expr.fields().size());
    assertEquals("phone", expr.fields().get(0).attrCode());
    assertEquals(MdmMatchType.EXACT, expr.fields().get(0).matchType());
    assertEquals("AND", expr.condition());
    assertTrue(expr.isAnd());
  }

  @Test
  void roundTripsCurrentWriteShape() {
    MdmCleanRuleExpr expr =
        new MdmCleanRuleExpr(
            java.util.List.of(new MdmMatchField("phone", MdmMatchType.FUZZY)), "OR");
    assertEquals(expr, CleanJson.parseExpr(CleanJson.writeExpr(expr)));
  }
}
