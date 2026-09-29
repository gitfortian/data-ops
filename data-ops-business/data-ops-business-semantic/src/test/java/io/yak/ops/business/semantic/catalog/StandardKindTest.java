package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.StandardKind;

import io.yak.ops.business.semantic.api.Standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 类别判别与类别专有必填校验测试。 */
class StandardKindTest {

  @Test
  void fromStoredParsesKnownKinds() {
    assertEquals(Optional.of(StandardKind.NAMING), StandardKind.fromStored("NAMING"));
    assertEquals(Optional.of(StandardKind.SECURITY), StandardKind.fromStored("SECURITY"));
    assertEquals(Optional.empty(), StandardKind.fromStored("OTHER"));
    assertEquals(Optional.empty(), StandardKind.fromStored(null));
  }

  @Test
  void namingRequiresRuleExpr() {
    Standard.KindFields fields =
        new Standard.KindFields("TABLE", null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> StandardKind.NAMING.validateRequired(fields));
    assertEquals(SemanticErrorCode.KIND_FIELD_REQUIRED, exception.getErrorCode());
  }

  @Test
  void typeRequiresTypeCodeAndStdType() {
    Standard.KindFields missing =
        new Standard.KindFields(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    assertThrows(SemanticException.class, () -> StandardKind.TYPE.validateRequired(missing));
    Standard.KindFields complete =
        new Standard.KindFields(null, null, null, null, "amount", "DECIMAL(18,2)", null, null,
            null, null, null, null, null, null, null, null, null);
    assertTrue(() -> {
      StandardKind.TYPE.validateRequired(complete);
      return true;
    });
  }

  @Test
  void unitRequiresUnitCode() {
    Standard.KindFields fields =
        new Standard.KindFields(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    assertThrows(SemanticException.class, () -> StandardKind.UNIT.validateRequired(fields));
  }
}
